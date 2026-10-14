package zas.admin.zec.backend.actions.converse;

import lombok.extern.slf4j.Slf4j;
import zas.admin.zec.backend.rag.ConsultedSource;
import zas.admin.zec.backend.rag.TurnMetaStreamFilter.TurnMeta;
import zas.admin.zec.backend.rag.TurnStatus;
import zas.admin.zec.backend.rag.TurnTrace;
import zas.admin.zec.backend.rag.token.SourceToken;
import zas.admin.zec.backend.tools.MetadataFormatter;
import zas.admin.zec.backend.tools.document.DocumentCollection;

import java.util.*;

/**
 * Décide des sources affichées en fin de tour selon le statut déclaré par l'agent ({@code <turn_meta>}).
 *
 * <ul>
 *   <li>{@link TurnStatus#ANSWER} : sources consultées pendant le tour + sources consultées pendant les
 *       questions de suivi immédiatement précédentes + sources réutilisées ({@code reuse}) validées.</li>
 *   <li>{@link TurnStatus#FOLLOWUP} : aucune source affichée (elles sont conservées dans la trace du tour).</li>
 *   <li>{@link TurnStatus#REFUSAL} : aucune source affichée.</li>
 *   <li>{@link TurnStatus#UNKNOWN} (balise absente / invalide) : comportement historique, sources du tour.</li>
 * </ul>
 *
 * <p>Les identifiants {@code reuse} fournis par le LLM ne sont jamais pris tels quels : seuls ceux présents
 * dans les sources consultées ou affichées de la conversation (ou du tour courant) sont retenus.</p>
 *
 * <p>Sources affichées sans doublons ({@link #displayed}) : la description d'un processus (chunk de documentation)
 * est masquée lorsque le processus lui-même est affiché, une seule traduction est conservée par version de
 * processus, et les entrées identiques à l'écran (type, lien, page, section, version, titre) sont fusionnées.
 * La trace du tour conserve, elle, tous les documents consultés.</p>
 */
@Slf4j
final class SourceDisplayPolicy {

    record Outcome(TurnStatus status, List<SourceToken> displayed, TurnTrace trace) {}

    private SourceDisplayPolicy() {}

    static Outcome decide(Optional<TurnMeta> meta, List<ConsultedSource> currentTurn, List<Message> history,
                          String conversationId) {

        List<ConsultedSource> current = dedupe(currentTurn);
        TurnStatus status = meta.map(TurnMeta::status).orElse(TurnStatus.UNKNOWN);
        if (meta.isEmpty()) {
            log.warn("Balise <turn_meta> absente (conversation {}) : affichage des sources du tour", conversationId);
        } else if (status == TurnStatus.UNKNOWN) {
            log.warn("Balise <turn_meta> sans statut valide (conversation {}) : affichage des sources du tour", conversationId);
        }

        List<SourceToken> displayed = switch (status) {
            case FOLLOWUP, REFUSAL -> List.of();
            case UNKNOWN -> displayed(current);
            case ANSWER -> answerSources(current, history, meta.map(TurnMeta::reuseIds).orElse(List.of()), conversationId);
        };
        return new Outcome(status, displayed, new TurnTrace(status, current));
    }

    private static List<SourceToken> answerSources(List<ConsultedSource> current, List<Message> history,
                                                   List<String> reuseIds, String conversationId) {
        List<ConsultedSource> sources = new ArrayList<>(current);
        sources.addAll(pendingFollowupSources(history));

        if (!reuseIds.isEmpty()) {
            Map<String, ConsultedSource> known = knownSources(history);
            current.forEach(s -> known.putIfAbsent(s.id(), s));
            for (String id : reuseIds) {
                ConsultedSource source = known.get(id);
                if (source != null) {
                    sources.add(source);
                } else {
                    log.warn("Source réutilisée inconnue ignorée : '{}' (conversation {})", id, conversationId);
                }
            }
        }
        return displayed(sources);
    }

    /** Sources à afficher, dans l'ordre de consultation et sans doublons (cf. javadoc de la classe). */
    static List<SourceToken> displayed(List<ConsultedSource> sources) {
        List<ConsultedSource> unique = dedupe(sources);
        Set<String> openedProcesses = new HashSet<>();
        for (ConsultedSource s : unique) {
            if (isProcess(s) && s.parentId() != null) {
                openedProcesses.add(s.parentId());
            }
        }

        Set<String> processVersions = new HashSet<>();
        Map<String, SourceToken> byDisplay = new LinkedHashMap<>();
        for (ConsultedSource s : unique) {
            if (isProcessDescription(s) && openedProcesses.contains(s.parentId())) {
                continue;
            }
            if (isProcess(s) && s.parentId() != null && !processVersions.add(s.parentId() + "|" + s.version())) {
                continue;
            }
            SourceToken token = s.toSourceToken();
            byDisplay.putIfAbsent(displayKey(token), token);
        }
        return List.copyOf(byDisplay.values());
    }

    /** Ligne de {@code business_process} (processus ouvert via get_documents). */
    private static boolean isProcess(ConsultedSource s) {
        return DocumentCollection.BUSINESS_PROCESS.key().equals(s.collection());
    }

    /** Chunk de documentation décrivant un processus ({@code doc_type = business_process}). */
    private static boolean isProcessDescription(ConsultedSource s) {
        return MetadataFormatter.DOC_TYPE_BUSINESS_PROCESS.equals(s.docType()) && !isProcess(s);
    }

    private static String displayKey(SourceToken token) {
        Map<String, String> m = token.metadata();
        return String.join("|", token.type().name(), m.get(SourceToken.KEY_URL), m.get(SourceToken.KEY_FILENAME),
                m.get(SourceToken.KEY_PAGE_NUMBER), m.get(SourceToken.KEY_SUBSECTION), m.get(SourceToken.KEY_VERSION),
                m.get(SourceToken.KEY_TITLE));
    }

    /** Sources consultées pendant les questions de suivi consécutives précédant ce tour. */
    static List<ConsultedSource> pendingFollowupSources(List<Message> history) {
        Deque<List<ConsultedSource>> pending = new ArrayDeque<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            Message m = history.get(i);
            if ("USER".equals(m.role())) {
                continue;
            }
            TurnTrace trace = m.turnTrace();
            if (trace == null || trace.status() != TurnStatus.FOLLOWUP) {
                break;
            }
            pending.addFirst(trace.sources());
        }
        return pending.stream().flatMap(List::stream).toList();
    }

    private static Map<String, ConsultedSource> knownSources(List<Message> history) {
        Map<String, ConsultedSource> known = new HashMap<>();
        for (Message m : history) {
            if ("USER".equals(m.role())) {
                continue;
            }
            if (m.turnTrace() != null) {
                m.turnTrace().sources().forEach(s -> known.putIfAbsent(s.id(), s));
            }
            if (m.sources() != null) {
                m.sources().stream()
                        .filter(s -> s.documentId() != null && !s.documentId().isBlank())
                        .forEach(s -> known.putIfAbsent(s.documentId(), RetrievedSourcesRenderer.fromLegacy(s)));
            }
        }
        return known;
    }

    private static List<ConsultedSource> dedupe(List<ConsultedSource> sources) {
        Map<String, ConsultedSource> unique = new LinkedHashMap<>();
        for (ConsultedSource s : sources) {
            if (s.id() != null) {
                unique.putIfAbsent(s.id(), s);
            }
        }
        return List.copyOf(unique.values());
    }
}
