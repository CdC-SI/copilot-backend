package zas.admin.zec.backend.actions.converse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import zas.admin.zec.backend.agent.Agent;
import zas.admin.zec.backend.agent.AgentFactory;
import zas.admin.zec.backend.persistence.entity.ConversationTitleEntity;
import zas.admin.zec.backend.persistence.entity.MessageEntity;
import zas.admin.zec.backend.persistence.repository.ConversationRepository;
import zas.admin.zec.backend.persistence.repository.ConversationTitleRepository;
import zas.admin.zec.backend.rag.RAGStatus;
import zas.admin.zec.backend.rag.token.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Slf4j
@Service
@Transactional
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationTitleRepository conversationTitleRepository;
    private final ChatClient chatClient;
    private final AgentFactory agentFactory;
    private final TaskExecutor taskExecutor;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationTitleRepository conversationTitleRepository,
                               @Qualifier("internalChatModel") ChatModel chatModel, AgentFactory agentFactory,
                               @Qualifier("asyncExecutor") TaskExecutor taskExecutor) {

        this.conversationRepository = conversationRepository;
        this.conversationTitleRepository = conversationTitleRepository;
        this.chatClient = ChatClient.create(chatModel);
        this.agentFactory = agentFactory;
        this.taskExecutor = taskExecutor;
    }

    public List<ConversationTitle> getTitlesByUserId(String userId) {
        return conversationTitleRepository.findByUserIdOrderByTimestamp(userId)
                .stream()
                .map(title -> new ConversationTitle(title.getTitle(), title.getUserId(), title.getConversationId(), title.getTimestamp()))
                .toList();
    }

    public List<Message> getByConversationIdAndUserId(String conversationId, String userId) {
        return getConversationHistory(conversationId, userId, Limit.unlimited());
    }

    public void initConversation(String userId, String conversationId, List<FAQMessage> messages) {
        for (var message : messages) {
            save(message, userId, conversationId);
        }

        generateConversationTitle(messages.get(0).message(), messages.get(1).message(), userId, conversationId, messages.get(0).lang());
    }

    public void update(String userUuid, String conversationId, List<FAQMessage> messages) {
        conversationTitleRepository.findByUserIdAndConversationId(userUuid, conversationId)
                .ifPresent(title -> {
                    for (var message : messages) {
                        save(message, title.getUserId(), title.getConversationId());
                    }
                });
    }

    public void delete(String userId, String conversationId) {
        conversationRepository.deleteByUserIdAndConversationId(userId, conversationId);
        conversationTitleRepository.deleteByUserIdAndConversationId(userId, conversationId);
    }

    public void renameConversation(String userId, String conversationId, String newTitle) {
        conversationTitleRepository.findByUserIdAndConversationId(userId, conversationId)
                .ifPresent(title -> {
                    title.setTitle(newTitle);
                    title.setTimestamp(LocalDateTime.now());
                    conversationTitleRepository.save(title);
                });
    }

    public Flux<String> streamAnswer(Question question, String userId) {
        var timestamp = LocalDateTime.now();
        String assistantMessageId = UUID.randomUUID().toString();
        StringBuilder assistantMessage = new StringBuilder();
        Set<Source> sources = new HashSet<>();
        Set<String> suggestions = new HashSet<>();

        return getTokenStream(question, userId)
                .flatMap(token -> switch (token) {
                    case StatusToken statusToken -> Flux.just(statusToken.content());
                    case SuggestionToken suggestionToken -> {
                        if (suggestions.add(suggestionToken.suggestion())) {
                            yield Flux.just(suggestionToken.content());
                        }
                        yield Flux.empty();
                    }
                    case SourceToken sourceToken -> {
                        if (sources.add(Source.fromToken(sourceToken))) {
                            yield Flux.just(sourceToken.content());
                        }
                        yield Flux.empty();
                    }
                    case TextToken textToken -> {
                        assistantMessage.append(textToken.content());
                        yield Flux.just(textToken.content());
                    }
                })
                .concatWithValues("<message_uuid>%s</message_uuid>".formatted(assistantMessageId))
                .concatWith(
                        Mono.fromRunnable(() -> saveExchange(question, userId, assistantMessageId, assistantMessage.toString(),
                                        sources, suggestions, timestamp))
                                .subscribeOn(Schedulers.boundedElastic())
                                .then(Mono.empty()))
                .onErrorResume(err -> {
                    log.error(err.getMessage(), err);
                    return Flux.just("<error>%s</error>".formatted(err.getMessage()));
                });
    }

    private Flux<Token> getTokenStream(Question question, String userId) {
        Token routingStatus = new StatusToken(RAGStatus.ROUTING, question.language());
        return Flux.just(routingStatus)
                .concatWith(getAgentAndHistoryStream(question, userId));
    }

    private Flux<Token> getAgentAndHistoryStream(Question question, String userId) {
        var agentFuture = fetchAgentAsync(question);
        var historyFuture = fetchConversationHistoryAsync(question, userId);
        Mono<Token> handoffFuture = Mono.fromFuture(agentFuture).map(agent -> new StatusToken(RAGStatus.AGENT_HANDOFF, question.language(), agent.getName()));
        var combined = agentFuture.thenCombine(historyFuture,
                (agent, history) -> agent.processQuestion(question, userId, history));

        // Return combined Flux
        return handoffFuture.concatWith(Mono.fromFuture(combined).flatMapMany(Function.identity()));
    }

    private CompletableFuture<Agent> fetchAgentAsync(Question question) {
        return CompletableFuture.supplyAsync(
                () -> agentFactory.selectAppropriateAgent(question),
                taskExecutor
        );
    }

    private CompletableFuture<List<Message>> fetchConversationHistoryAsync(Question question, String userId) {
        return CompletableFuture.supplyAsync(
                () -> getConversationHistory(
                        question.conversationId(),
                        userId,
                        Limit.unlimited()
                ),
                taskExecutor
        );
    }

    private Boolean questionIsOffTopic(Question question) {
        var systemPrompt = ConversationPrompts.getTopicCheckPrompt(question.language())
                .formatted(question.query());

        record Bool(boolean value) {}
        var offTopicValue = chatClient.prompt()
                .options(ChatOptions.builder()
                        .topP(0.95)
                        .maxTokens(512)
                        .model(question.llmModel())
                        .temperature(0D)
                        .build())
                .messages(new SystemMessage(systemPrompt))
                .call()
                .entity(Bool.class);

        return offTopicValue != null && !offTopicValue.value();
    }

    private void saveExchange(Question question, String userId, String assistantMessageId, String answer, Set<Source> sources,
                              Set<String> suggestions, LocalDateTime userMessageTimestamp) {

        var userMessage = new Message(UUID.randomUUID().toString(), userId, question.conversationId(), null,
                question.language(), question.query(), "USER", null, null, userMessageTimestamp);
        var assistantMessage = new Message(assistantMessageId, userId, question.conversationId(), null,
                question.language(), answer, "LLM", sources.stream().toList(), suggestions.stream().toList(), LocalDateTime.now());

        save(userMessage, userId, question.conversationId());
        save(assistantMessage, userId, question.conversationId());
        generateConversationTitle(question.query(), answer, userId, question.conversationId(), question.language());
    }

    private List<Message> getConversationHistory(String conversationId, String userId, Limit limit) {
        return conversationRepository.findByConversationIdAndUserIdOrderByTimestamp(conversationId, userId, limit)
                .stream()
                .map(message -> new Message(
                        message.getMessageId(),
                        message.getUserId(),
                        message.getConversationId(),
                        message.getFaqId(),
                        message.getLanguage(),
                        message.getMessage(),
                        getRole(message),
                        Arrays.stream(message.getSources())
                                .map(this::fromSourceString)
                                .toList(),
                        List.of(message.getSuggestions()),
                        message.getTimestamp()
                ))
                .toList();
    }

    private String getRole(MessageEntity message) {
        if (message.getRole().equals("user")) return "USER";
        return message.getFaqId() == null ? "LLM" : "FAQ";
    }

    private void save(FAQMessage message, String userId, String conversationId) {
        var entity = new MessageEntity();
        entity.setUserId(userId);
        entity.setConversationId(conversationId);
        entity.setMessageId(UUID.randomUUID().toString());
        entity.setRole(message.source().equals("USER") ? "user" : "assistant");
        entity.setMessage(message.message());
        entity.setLanguage(message.lang());
        entity.setTimestamp(LocalDateTime.now());
        entity.setFaqId(message.faqItemId());
        entity.setSuggestions(new String[0]);
        entity.setSources(Objects.isNull(message.sources())
                ? new String[0]
                : message.sources()
                    .stream()
                    .map(this::toSourceString)
                    .distinct()
                    .toArray(String[]::new));

        conversationRepository.save(entity);
    }

    private void save(Message message, String userId, String conversationId) {
        var entity = new MessageEntity();
        entity.setUserId(userId);
        entity.setConversationId(conversationId);
        entity.setMessageId(Objects.isNull(message.messageId()) ? UUID.randomUUID().toString() : message.messageId());
        entity.setRole(message.role().equals("USER") ? "user" : "assistant");
        entity.setMessage(message.message());
        entity.setLanguage(message.language());
        entity.setTimestamp(message.timestamp());
        entity.setFaqId(message.faqItemId());
        entity.setSources(Objects.isNull(message.sources())
                ? new String[0]
                : message.sources()
                    .stream()
                    .map(this::toSourceString)
                    .distinct()
                    .toArray(String[]::new));
        entity.setSuggestions(Objects.isNull(message.suggestions())
                ? new String[0]
                : message.suggestions().toArray(String[]::new));

        conversationRepository.save(entity);
    }

    private String toSourceString(Source source) {
        return source.type() == SourceType.FILE
                ? "%s:%s".formatted(SourceType.FILE.name(), source.link())
                : source.link();
    }

    private Source fromSourceString(String source) {
        if (source.startsWith(SourceType.FILE.name())) {
            return new Source(SourceType.FILE, source.substring(SourceType.FILE.name().length() + 1));
        }
        return new Source(SourceType.URL, source);
    }

    private void generateConversationTitle(String initialQuery, String initialResponse, String userId, String conversationId, String language) {
        if (conversationTitleRepository.findByUserIdAndConversationId(userId, conversationId).isEmpty()) {
            var title = chatClient.prompt()
                    .system(ConversationPrompts.getConversationTitlePrompt(language)
                            .formatted(initialResponse))
                    .user(initialQuery)
                    .call()
                    .content();

            var entity = new ConversationTitleEntity();
            entity.setUserId(userId);
            entity.setConversationId(conversationId);
            entity.setTitle(title);
            entity.setTimestamp(LocalDateTime.now());

            conversationTitleRepository.save(entity);
        }
    }
}
