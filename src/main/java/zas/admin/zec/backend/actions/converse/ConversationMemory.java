package zas.admin.zec.backend.actions.converse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;
import zas.admin.zec.backend.config.ChatClientConfig;
import zas.admin.zec.backend.config.properties.MemoryProperties;
import zas.admin.zec.backend.observability.TurnUsageEvent;
import zas.admin.zec.backend.persistence.repository.ConversationSummaryRepository;
import zas.admin.zec.backend.persistence.repository.ConversationSummaryRepository.ConversationSummary;
import zas.admin.zec.backend.prompts.Prompt;
import zas.admin.zec.backend.prompts.PromptId;
import zas.admin.zec.backend.prompts.PromptRegistry;
import zas.admin.zec.backend.rag.TurnUsage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Mémoire conversationnelle (B2) : {@code chat_history} reste la source de vérité, cette classe ne fait que choisir la
 * partie de l'historique envoyée au LLM.
 *
 * <ul>
 *   <li><b>Fenêtre</b> ({@link #window}) : l'historique rendu est limité à un budget de tokens (estimation JTokkit),
 *       par tours complets (une question et ses réponses), en retirant les plus anciens ; le dernier tour est toujours
 *       conservé.</li>
 *   <li><b>Résumé</b> ({@link #compactAsync}) : après un tour, si l'historique non résumé dépasse le budget, les tours
 *       les plus anciens sont résumés (incrémentalement, à partir du résumé précédent) jusqu'à ne conserver qu'environ
 *       {@link MemoryProperties#compactionRatio()} du budget en tours complets. Le résumé, persisté
 *       ({@code conversation_summary}), est préfixé au premier message conservé dans un bloc
 *       {@code <conversation_summary>}. L'hystérésis garde le préfixe du prompt stable entre deux compactions (prefix
 *       caching).</li>
 * </ul>
 *
 * <p>Le dernier tour n'est jamais résumé : une régénération (qui supprime le dernier échange) n'invalide donc pas le
 * résumé. Un résumé dont le dernier message couvert n'existe plus est ignoré.</p>
 */
@Slf4j
@Component
public class ConversationMemory {

    static final String SUMMARY_PURPOSE = "conversation_summary";

    private final MemoryProperties properties;
    private final ConversationSummaryRepository summaries;
    private final ChatClient chatClient;
    private final PromptRegistry prompts;
    private final ApplicationEventPublisher eventPublisher;
    private final TokenCountEstimator estimator = new JTokkitTokenCountEstimator();
    /** Conversations dont le résumé est en cours de génération (une seule compaction à la fois par conversation). */
    private final Set<String> compacting = ConcurrentHashMap.newKeySet();

    public ConversationMemory(MemoryProperties properties,
                              ConversationSummaryRepository summaries,
                              @Qualifier(ChatClientConfig.INTERNAL_CHAT_CLIENT_BUILDER) ChatClient.Builder chatClientBuilder,
                              PromptRegistry prompts,
                              ApplicationEventPublisher eventPublisher) {
        this.properties = properties;
        this.summaries = summaries;
        this.chatClient = chatClientBuilder.build();
        this.prompts = prompts;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Historique à envoyer au LLM : résumé éventuel et tours les plus récents dans la limite du budget.
     *
     * @param history        historique complet de la conversation
     * @param rendered       rendu de {@code history} par la stratégie appelante (même taille, même ordre)
     * @param promptVersions versions des prompts du tour, complétées par celle du résumé utilisé ({@code null} : non tracé)
     */
    public List<org.springframework.ai.chat.messages.Message> window(String userId, String conversationId,
                                                                     List<Message> history,
                                                                     List<org.springframework.ai.chat.messages.Message> rendered,
                                                                     Map<String, String> promptVersions) {
        if (history.isEmpty() || history.size() != rendered.size()) {
            return rendered;
        }
        List<Integer> starts = turnStarts(history);
        List<Integer> tokens = turnTokens(starts, rendered);
        var summary = usableSummary(userId, conversationId, history, starts);
        String block = summary.map(s -> summaryBlock(s.summary().summary())).orElse("");
        int summarizedTurns = summary.map(UsableSummary::summarizedTurns).orElse(0);

        int first = firstTurn(tokens, summarizedTurns, block.isEmpty() ? 0 : estimate(block),
                properties.historyTokenBudget());
        if (first > summarizedTurns) {
            log.debug("Conversation {} : {} tour(s) hors budget retiré(s) du contexte", conversationId, first - summarizedTurns);
        }
        List<org.springframework.ai.chat.messages.Message> messages =
                new ArrayList<>(rendered.subList(starts.get(first), rendered.size()));
        if (!block.isEmpty()) {
            if (messages.getFirst() instanceof UserMessage user) {
                messages.set(0, new UserMessage(AbstractChatService.withContextBlock(block, user.getText())));
            } else {
                messages.addFirst(new UserMessage(block));
            }
            recordSummaryPrompt(summary.get().summary(), promptVersions);
        }
        return messages;
    }

    /**
     * Met à jour le résumé de la conversation en arrière-plan si nécessaire (à appeler une fois l'échange enregistré).
     * Les erreurs sont journalisées : la mémoire retombe alors sur la simple fenêtre.
     */
    public void compactAsync(String userId, String conversationId, String language, Supplier<List<Message>> history) {
        if (!properties.summaryEnabled() || conversationId == null || conversationId.isBlank()) {
            return;
        }
        String key = userId + "|" + conversationId;
        if (!compacting.add(key)) {
            return;
        }
        Schedulers.boundedElastic().schedule(() -> {
            try {
                compact(userId, conversationId, language, history.get());
            } catch (RuntimeException e) {
                log.warn("Résumé de la conversation {} impossible", conversationId, e);
            } finally {
                compacting.remove(key);
            }
        });
    }

    /** Supprime le résumé d'une conversation supprimée. */
    public void forget(String userId, String conversationId) {
        summaries.delete(userId, conversationId);
    }

    /** @return {@code true} si un nouveau résumé a été enregistré */
    boolean compact(String userId, String conversationId, String language, List<Message> history) {
        if (history.isEmpty()) {
            return false;
        }
        List<Integer> starts = turnStarts(history);
        List<Integer> tokens = turnTokens(starts, AbstractChatService.toPromptMessages(history));
        var previous = usableSummary(userId, conversationId, history, starts);
        int summarizedTurns = previous.map(UsableSummary::summarizedTurns).orElse(0);
        int summaryTokens = previous.map(s -> estimate(summaryBlock(s.summary().summary()))).orElse(0);

        int budget = properties.historyTokenBudget();
        if (summaryTokens + sum(tokens, summarizedTurns, tokens.size()) <= budget) {
            return false;
        }
        int end = compactionEnd(tokens, summarizedTurns, properties.compactionTarget(), budget);
        if (end <= summarizedTurns) {
            return false;
        }

        Prompt prompt = prompts.get(PromptId.CONVERSATION_SUMMARY, language);
        String request = prompt.render(
                "previous_summary", previous.map(s -> s.summary().summary()).orElse(""),
                "turns", transcript(history.subList(starts.get(summarizedTurns), starts.get(end))));
        ChatResponse response = chatClient.prompt().user(request).call().chatResponse();
        String text = response != null && response.getResult() != null && response.getResult().getOutput().getText() != null
                ? response.getResult().getOutput().getText().strip() : "";
        var call = TurnUsage.LlmCall.from(SUMMARY_PURPOSE, response);
        if (call != null) {
            eventPublisher.publishEvent(new TurnUsageEvent(new TurnUsage(List.of(), List.of(call)), null));
        }
        String coveredMessageId = history.get(starts.get(end) - 1).messageId();
        if (text.isEmpty() || coveredMessageId == null) {
            return false;
        }
        summaries.save(new ConversationSummary(userId, conversationId, coveredMessageId, text,
                prompt.traceKey() + "@" + prompt.version(),
                call != null ? (int) call.promptTokens() : null, call != null ? (int) call.completionTokens() : null,
                LocalDateTime.now()));
        log.debug("Conversation {} : {} tour(s) résumé(s)", conversationId, end);
        return true;
    }

    /** Premier tour conservé : les plus anciens (après ceux déjà résumés) sont retirés tant que le budget est dépassé. */
    static int firstTurn(List<Integer> turnTokens, int summarizedTurns, int summaryTokens, int budget) {
        int first = summarizedTurns;
        int total = summaryTokens + sum(turnTokens, first, turnTokens.size());
        while (total > budget && first < turnTokens.size() - 1) {
            total -= turnTokens.get(first++);
        }
        return first;
    }

    /**
     * Fin (exclue) des tours à résumer : le moins possible pour que les tours restants tiennent dans {@code target},
     * au plus {@code maxTokens} résumés en une fois (rattrapage progressif d'un long historique), jamais le dernier tour.
     */
    static int compactionEnd(List<Integer> turnTokens, int summarizedTurns, int target, int maxTokens) {
        int last = turnTokens.size() - 1;
        int end = summarizedTurns;
        int remaining = sum(turnTokens, end, turnTokens.size());
        int summarized = 0;
        while (remaining > target && end < last
                && (end == summarizedTurns || summarized + turnTokens.get(end) <= maxTokens)) {
            summarized += turnTokens.get(end);
            remaining -= turnTokens.get(end++);
        }
        return end;
    }

    /** Index du premier message de chaque tour (un tour commence à une question ; les messages initiaux sans question sont rattachés au premier). */
    static List<Integer> turnStarts(List<Message> history) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        boolean questionSeen = false;
        for (int i = 0; i < history.size(); i++) {
            if ("USER".equals(history.get(i).role())) {
                if (questionSeen) {
                    starts.add(i);
                }
                questionSeen = true;
            }
        }
        return starts;
    }

    static String summaryBlock(String summary) {
        return "<conversation_summary>\n" + summary + "\n</conversation_summary>";
    }

    static String transcript(List<Message> messages) {
        List<String> parts = new ArrayList<>(messages.size());
        for (Message message : messages) {
            String tag = "USER".equals(message.role()) ? "user" : "assistant";
            parts.add("<" + tag + ">\n" + message.message().strip() + "\n</" + tag + ">");
        }
        return String.join("\n", parts);
    }

    private List<Integer> turnTokens(List<Integer> starts,
                                     List<org.springframework.ai.chat.messages.Message> rendered) {
        List<Integer> tokens = new ArrayList<>(starts.size());
        for (int t = 0; t < starts.size(); t++) {
            int end = t + 1 < starts.size() ? starts.get(t + 1) : rendered.size();
            int turn = 0;
            for (var message : rendered.subList(starts.get(t), end)) {
                turn += estimate(message.getText());
            }
            tokens.add(turn);
        }
        return tokens;
    }

    private record UsableSummary(ConversationSummary summary, int summarizedTurns) {}

    /** Résumé persisté dont le dernier message couvert figure encore dans l'historique (sinon ignoré). */
    private Optional<UsableSummary> usableSummary(String userId, String conversationId, List<Message> history,
                                                  List<Integer> starts) {
        if (!properties.summaryEnabled() || conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }
        Optional<ConversationSummary> summary;
        try {
            summary = summaries.find(userId, conversationId);
        } catch (RuntimeException e) {
            log.warn("Lecture du résumé de la conversation {} impossible", conversationId, e);
            return Optional.empty();
        }
        return summary.flatMap(s -> {
            for (int i = 0; i < history.size(); i++) {
                if (s.coveredMessageId().equals(history.get(i).messageId())) {
                    int covered = i;
                    int turns = (int) starts.stream().filter(start -> start <= covered).count();
                    // Le dernier tour n'est jamais résumé (un résumé plus récent que l'historique est ignoré).
                    return turns < starts.size() ? Optional.of(new UsableSummary(s, turns)) : Optional.empty();
                }
            }
            return Optional.empty();
        });
    }

    private static void recordSummaryPrompt(ConversationSummary summary, Map<String, String> promptVersions) {
        int at = summary.promptVersion().lastIndexOf('@');
        if (promptVersions != null && at > 0) {
            promptVersions.put(summary.promptVersion().substring(0, at), summary.promptVersion().substring(at + 1));
        }
    }

    private int estimate(String text) {
        return text == null || text.isEmpty() ? 0 : estimator.estimate(text);
    }

    private static int sum(List<Integer> values, int from, int to) {
        int total = 0;
        for (int i = from; i < to; i++) {
            total += values.get(i);
        }
        return total;
    }
}
