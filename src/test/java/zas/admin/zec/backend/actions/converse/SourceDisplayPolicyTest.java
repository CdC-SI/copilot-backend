package zas.admin.zec.backend.actions.converse;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.rag.ConsultedSource;
import zas.admin.zec.backend.rag.TurnMetaStreamFilter.TurnMeta;
import zas.admin.zec.backend.rag.TurnStatus;
import zas.admin.zec.backend.rag.TurnTrace;
import zas.admin.zec.backend.rag.token.SourceToken;
import zas.admin.zec.backend.rag.token.Token;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scénarios d'affichage des sources et de mémoire inter-tours :
 * réponse directe, question de suivi, réponse finale après suivi, refus, réutilisation d'une réponse précédente.
 */
class SourceDisplayPolicyTest {

    private static final String UUID_A = "11111111-1111-1111-1111-111111111111";

    private static ConsultedSource doc(String id) {
        return new ConsultedSource(id, "documentation", null, "Doc " + id, null, "1", null, null, null,
                "parent-" + id, "0", List.of());
    }

    private static ConsultedSource process(String id) {
        return new ConsultedSource(id, "business_process", "business_process", "Processus " + id,
                "https://bpanda/" + id, null, null, "2026.03.02", "Brouillon", "BP-" + id, null, List.of());
    }

    private static Message user(String text) {
        return new Message(null, null, null, null, "fr", text, "USER", null, null, LocalDateTime.now(), null);
    }

    private static Message assistant(String text, TurnTrace trace, List<Source> shown) {
        return new Message(null, null, null, null, "fr", text, "LLM", shown, List.of(), LocalDateTime.now(), null, trace);
    }

    private static List<String> ids(List<SourceToken> tokens) {
        return tokens.stream().map(SourceToken::documentId).toList();
    }

    private static Optional<TurnMeta> meta(TurnStatus status, String... reuse) {
        return Optional.of(new TurnMeta(status, List.of(reuse)));
    }

    @Test
    void answerDisplaysConsultedSourcesOfTheTurnWithoutDuplicates() {
        var outcome = SourceDisplayPolicy.decide(meta(TurnStatus.ANSWER), List.of(doc("a"), process("p"), doc("a")),
                List.of(), "c");

        assertEquals(List.of("a", "p"), ids(outcome.displayed()));
        assertEquals(TurnStatus.ANSWER, outcome.trace().status());
        assertEquals(2, outcome.trace().sources().size());
    }

    @Test
    void followupHidesSourcesButKeepsThemInTrace() {
        var outcome = SourceDisplayPolicy.decide(meta(TurnStatus.FOLLOWUP), List.of(doc("a")), List.of(), "c");

        assertTrue(outcome.displayed().isEmpty());
        assertEquals(TurnStatus.FOLLOWUP, outcome.trace().status());
        assertEquals("a", outcome.trace().sources().getFirst().id());
    }

    @Test
    void finalAnswerAfterFollowupsDisplaysAccumulatedSources() {
        var history = List.of(
                user("q0"), assistant("old answer", new TurnTrace(TurnStatus.ANSWER, List.of(doc("old"))), List.of()),
                user("q1"), assistant("précisez ?", new TurnTrace(TurnStatus.FOLLOWUP, List.of(doc("a"))), List.of()),
                user("q2"), assistant("et encore ?", new TurnTrace(TurnStatus.FOLLOWUP, List.of(process("p"))), List.of()),
                user("q3"));

        var outcome = SourceDisplayPolicy.decide(meta(TurnStatus.ANSWER), List.of(doc("b")), history, "c");

        assertEquals(List.of("b", "a", "p"), ids(outcome.displayed()));
        // La trace du tour ne contient que les documents consultés pendant ce tour.
        assertEquals(List.of("b"), outcome.trace().sources().stream().map(ConsultedSource::id).toList());
    }

    @Test
    void refusalDisplaysNothing() {
        var history = List.of(user("q1"), assistant("?", new TurnTrace(TurnStatus.FOLLOWUP, List.of(doc("a"))), List.of()));
        var outcome = SourceDisplayPolicy.decide(meta(TurnStatus.REFUSAL), List.of(doc("b")), history, "c");

        assertTrue(outcome.displayed().isEmpty());
        assertEquals(TurnStatus.REFUSAL, outcome.trace().status());
    }

    @Test
    void directAnswerReusingPreviousSourcesDisplaysOnlyKnownOnes() {
        var legacyShown = new Source(UUID_A, SourceType.URL, "https://www.ahv-iv.ch/x", "3", null, null);
        var history = List.of(
                user("q1"), assistant("a1", null, List.of(legacyShown)),
                user("q2"), assistant("a2", new TurnTrace(TurnStatus.ANSWER, List.of(process("p"))), List.of()),
                user("q3"));

        var outcome = SourceDisplayPolicy.decide(meta(TurnStatus.ANSWER, "p", UUID_A, "invented"), List.of(), history, "c");

        assertEquals(List.of("p", UUID_A), ids(outcome.displayed()));
        assertTrue(outcome.trace().sources().isEmpty());
    }

    // ---- sources affichées sans doublons ----

    private static ConsultedSource description(String id, String processId) {
        return new ConsultedSource(id, "documentation", "business_process", "Processus " + processId,
                "https://bpanda/" + processId, null, null, null, null, processId, "0", List.of());
    }

    private static ConsultedSource processRow(String id, String processId, String version, String title) {
        return new ConsultedSource(id, "business_process", "business_process", title,
                "https://bpanda/" + processId, null, null, version, "Validé", processId, null, List.of());
    }

    private static ConsultedSource chunk(String id, String url, String page) {
        return new ConsultedSource(id, "documentation", null, "Mémento", url, page, "Section", null, null,
                "D", id, List.of());
    }

    @Test
    void processDescriptionIsHiddenWhenTheProcessItselfIsShown() {
        var outcome = SourceDisplayPolicy.decide(meta(TurnStatus.ANSWER),
                List.of(description("d", "BP-1"), processRow("r", "BP-1", "2026.03.02", "Processus BP-1"),
                        description("d2", "BP-2")),
                List.of(), "c");

        assertEquals(List.of("r", "d2"), ids(outcome.displayed()));
        assertEquals(3, outcome.trace().sources().size());
    }

    @Test
    void oneTranslationPerProcessVersionButEveryVersion() {
        var outcome = SourceDisplayPolicy.decide(meta(TurnStatus.ANSWER), List.of(
                processRow("fr-v2", "BP-1", "2026.03.02", "Traiter les décès"),
                processRow("de-v2", "BP-1", "2026.03.02", "Todesfälle bearbeiten"),
                processRow("fr-v1", "BP-1", "2025.01.01", "Traiter les décès")), List.of(), "c");

        assertEquals(List.of("fr-v2", "fr-v1"), ids(outcome.displayed()));
    }

    @Test
    void entriesIdenticalOnScreenAreMerged() {
        var outcome = SourceDisplayPolicy.decide(meta(TurnStatus.ANSWER), List.of(
                chunk("c1", "https://x/memento", "3"), chunk("c2", "https://x/memento", "3"),
                chunk("c3", "https://x/memento", "4")), List.of(), "c");

        assertEquals(List.of("c1", "c3"), ids(outcome.displayed()));
    }

    @Test
    void reusedProcessHidesTheDescriptionReadDuringTheTurn() {
        var history = List.of(
                user("q1"), assistant("a1", new TurnTrace(TurnStatus.ANSWER,
                        List.of(processRow("r", "BP-1", "2026.03.02", "Processus BP-1"))), List.of()),
                user("q2"));

        var outcome = SourceDisplayPolicy.decide(meta(TurnStatus.ANSWER, "r"), List.of(description("d", "BP-1")),
                history, "c");

        assertEquals(List.of("r"), ids(outcome.displayed()));
    }

    @Test
    void missingTagFallsBackToDisplayingTurnSources() {
        var outcome = SourceDisplayPolicy.decide(Optional.empty(), List.of(doc("a")), List.of(), "c");
        assertEquals(List.of("a"), ids(outcome.displayed()));
        assertEquals(TurnStatus.UNKNOWN, outcome.trace().status());
    }

    @Test
    void traceRoundTripsThroughJsonbMap() {
        var trace = new TurnTrace(TurnStatus.FOLLOWUP, List.of(doc("a"), process("p")));
        Map<String, Object> map = trace.toMap();
        assertEquals("followup", map.get("turn_status"));
        assertEquals(trace, TurnTrace.fromMap(map));
    }

    // ---- mémoire inter-tours (<retrieved_sources>) ----

    private static final AbstractChatService SERVICE = new AbstractChatService() {
        @Override
        public boolean supports(ConversationType conversationType) {
            return false;
        }

        @Override
        public Flux<Token> answer(Question question, String userId, List<Message> conversationHistory) {
            return Flux.empty();
        }
    };

    @Test
    void retrievedSourcesArePrependedToTheFollowingUserMessage() {
        var withLink = new ConsultedSource("a", "documentation", null, "Mémento \"AVS\"", null, "2", "Art. 3", null, null,
                "D1", "4", List.of(Map.of("type", "business_process", "title", "Décès", "process_id", "BP-1"),
                Map.of("type", "url", "title", "OFSP", "url", "https://www.bag.admin.ch")));
        var history = List.of(
                user("q1"), assistant("a1", new TurnTrace(TurnStatus.ANSWER, List.of(withLink)), List.of()),
                user("q2"), assistant("a2", new TurnTrace(TurnStatus.FOLLOWUP, List.of(process("p"))), List.of()));

        var messages = SERVICE.toPromptMessages(history);
        String secondUser = messages.get(2).getText();

        assertEquals("q1", messages.get(0).getText());
        assertTrue(secondUser.startsWith("<retrieved_sources turn=\"1\">\n- id=a; collection=documentation; "
                + "title=\"Mémento 'AVS'\"; doc_id=D1; chunk_index=4; page=2; section=\"Art. 3\"; outgoing_links=["
                + "business_process:\"Décès\" process_id=BP-1, url:\"OFSP\" unresolved url=https://www.bag.admin.ch]\n"
                + "</retrieved_sources>\n\nq2"), secondUser);

        String current = SERVICE.retrievedSourcesForCurrentTurn(history);
        assertTrue(current.startsWith("<retrieved_sources turn=\"2\">\n- id=p; collection=business_process; type=business_process;"));
        // Rendu déterministe (prefix caching) : le même historique produit le même texte.
        assertEquals(secondUser, SERVICE.toPromptMessages(history).get(2).getText());
    }

    @Test
    void noBlockWithoutConsultedSources() {
        var history = List.of(user("q1"), assistant("a1", null, null));
        assertEquals("", SERVICE.retrievedSourcesForCurrentTurn(history));
        assertEquals("q1", SERVICE.toPromptMessages(List.of(user("q1"))).getFirst().getText());
    }
}
