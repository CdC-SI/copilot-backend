package zas.admin.zec.backend.actions.converse;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import zas.admin.zec.backend.LocalConfig;
import zas.admin.zec.backend.rag.ChatStatus;
import zas.admin.zec.backend.tools.RAGTool;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contrat SSE de bout en bout (B7d) : application complète (sécurité Blue, PostgreSQL migré par Flyway), LLM
 * compatible OpenAI et zia-rag simulés par des serveurs HTTP embarqués scriptés. Vérifie la séquence d'événements du
 * flux de réponse, la résolution des citations ({@code [ref]} → {@code [^n]}), la persistance de l'échange et l'arrêt
 * d'une génération (Stop).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.url=",
        "spring.flyway.user=",
        "spring.flyway.password=",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.ai.chat.internal.api-key=test-key",
        "spring.ai.chat.internal.chat-model=test-chat",
        "spring.ai.chat.internal.vision-model=test-vision",
        "spring.ai.chat.internal.embedding-model=test-embedding",
        "embedding-service.base-url=http://127.0.0.1:1",
        "spring.activemq.broker-url=tcp://127.0.0.1:1",
        "spring.activemq.user=test",
        "spring.activemq.password=test",
        "management.health.jms.enabled=false",
        "management.health.mail.enabled=false",
        "proxy.enabled=false",
        "ai.agent.ii-data-folder=.",
        "nuxeo.url=http://127.0.0.1:1",
        "nuxeo.username=test",
        "nuxeo.password=test",
        "zia.memory.summary-enabled=false",
        "logging.level.org.springframework=INFO"
})
@Import(LocalConfig.class)
class ConversationStreamIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Utilisateur créé par {@code init-users.sql} (callback Flyway de {@link LocalConfig}). */
    private static final String USERNAME = "ZAS101340";
    private static final String USER_UUID = "12c81aae-3883-4840-8196-e8b18877ac59";

    private static final String CHUNK_1 = "6f1c2c4e-1b7e-4a53-9c1a-3f0a1d2b0001";
    private static final String CHUNK_2 = "6f1c2c4e-1b7e-4a53-9c1a-3f0a1d2b0002";
    private static final String CHUNK_1_URL = "https://www.ahv-iv.ch/p/3.01.f";

    private static final KeyPair BLUE_KEYS = rsaKeyPair();
    private static final LlmStub LLM = new LlmStub();
    private static final ZiaRagStub ZIA_RAG = new ZiaRagStub();

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newHttpClient();
    private final String blueToken = blueToken();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("zas.security.blue-token.public-key",
                () -> Base64.getEncoder().encodeToString(BLUE_KEYS.getPublic().getEncoded()));
        registry.add("spring.ai.chat.internal.chat-base-url", LLM::baseUrl);
        registry.add("spring.ai.chat.internal.vision-base-url", LLM::baseUrl);
        registry.add("spring.ai.chat.internal.embedding-base-url", LLM::baseUrl);
        registry.add("rag.zia-rag.base-url", ZIA_RAG::baseUrl);
    }

    @BeforeEach
    void resetStubs() {
        LLM.reset();
        ZIA_RAG.reset();
    }

    @AfterEach
    void releaseStubs() {
        LLM.releaseAll();
    }

    @AfterAll
    static void stopStubs() {
        LLM.stop();
        ZIA_RAG.stop();
    }

    @Test
    void answerWithRetrievalStreamsTheContractAndPersistsCitations() throws Exception {
        LLM.script(out -> out.toolCall("call-1", RAGTool.TOOL_NAME, "{\"query\":\"Dès quel âge la rente AVS est-elle versée ?\"}")
                .finish("tool_calls"));
        // Marqueur [1] coupé entre deux morceaux : le backend le retient puis le remplace par [^1].
        LLM.script(out -> out.text("La rente de vieillesse est versée dès 65 ans [")
                .text("1].\n")
                .text("<turn_meta status=\"answer\" used=\"" + CHUNK_1 + "\"/>")
                .finish("stop"));

        var stream = ask("À quel âge reçoit-on la rente AVS ?", null);
        var events = stream.readAll();
        var names = events.stream().map(SseEvent::name).toList();

        // Séquence : start → tool_call / tool_result → citation puis text_delta [^1] → source (citée) → done.
        assertEquals(ChatStreamEvent.START, names.getFirst(), names.toString());
        assertEquals(ChatStreamEvent.DONE, names.getLast(), names.toString());
        assertEquals(1, Collections.frequency(names, ChatStreamEvent.DONE));
        assertFalse(names.contains(ChatStreamEvent.ERROR), names.toString());
        int toolCall = names.indexOf(ChatStreamEvent.TOOL_CALL);
        int toolResult = names.indexOf(ChatStreamEvent.TOOL_RESULT);
        int firstText = names.indexOf(ChatStreamEvent.TEXT_DELTA);
        int lastText = names.lastIndexOf(ChatStreamEvent.TEXT_DELTA);
        int citation = names.indexOf(ChatStreamEvent.CITATION);
        int source = names.indexOf(ChatStreamEvent.SOURCE);
        assertTrue(0 < toolCall && toolCall < toolResult && toolResult < firstText, names.toString());
        // Étapes annoncées par RAGTool entre l'appel et son résultat : inférence du workspace puis recherche.
        var statuses = events.subList(toolCall + 1, toolResult).stream()
                .filter(e -> e.name().equals(ChatStreamEvent.STATUS))
                .map(e -> e.data().path("code").asText() + "|" + e.data().path("label").asText())
                .toList();
        assertEquals(List.of(
                ChatStatus.WORKSPACE_INFERENCE.tag() + "|" + ChatStatus.WORKSPACE_INFERENCE.text("fr"),
                ChatStatus.RETRIEVAL.tag() + "|" + ChatStatus.RETRIEVAL.text("fr")), statuses);
        assertEquals(2, Collections.frequency(names, ChatStreamEvent.STATUS), names.toString());
        int markerText = indexOf(events, e -> e.name().equals(ChatStreamEvent.TEXT_DELTA)
                && e.data().path("text").asText().contains("[^1]"));
        assertTrue(firstText <= citation && citation < markerText, names.toString());
        assertTrue(lastText < source && source < names.size() - 1, names.toString());
        assertEquals(1, Collections.frequency(names, ChatStreamEvent.CITATION));
        assertEquals(1, Collections.frequency(names, ChatStreamEvent.SOURCE), "seules les sources citées sont affichées");
        // Workspace résolu par RAGTool, annoncé après les sources de fin de tour.
        int workspace = names.indexOf(ChatStreamEvent.WORKSPACE);
        assertTrue(source < workspace && workspace < names.size() - 1, names.toString());
        assertFalse(events.get(workspace).data().path("name").asText().isBlank());

        var start = events.getFirst().data();
        String conversationId = start.path("conversationId").asText();
        String messageId = start.path("messageId").asText();
        assertFalse(conversationId.isBlank());
        assertFalse(messageId.isBlank());

        var call = events.get(toolCall).data();
        // id : numéro d'ordre de l'appel dans le tour (pas l'id fourni par le LLM).
        assertEquals("1", call.path("id").asText());
        assertEquals(RAGTool.TOOL_NAME, call.path("name").asText());
        assertEquals("Dès quel âge la rente AVS est-elle versée ?", call.path("arguments").path("query").asText());
        var result = events.get(toolResult).data();
        assertEquals("1", result.path("id").asText());
        assertEquals(RAGTool.TOOL_NAME, result.path("name").asText());
        assertEquals("ok", result.path("status").asText());
        assertEquals(2, result.path("documentCount").asInt());

        var cited = events.get(citation).data();
        assertEquals(1, cited.path("n").asInt());
        assertCitedSource(cited.path("source"));
        assertCitedSource(events.get(source).data());

        String answer = text(events);
        assertEquals("La rente de vieillesse est versée dès 65 ans [^1].\n", answer);

        var done = events.getLast().data();
        assertEquals(messageId, done.path("messageId").asText());
        assertFalse(done.path("interrupted").asBoolean());

        // zia-rag : requête du LLM, utilisateur et jeton Blue transmis.
        var retrieval = ZIA_RAG.requests.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertNotNull(retrieval);
        assertEquals("Dès quel âge la rente AVS est-elle versée ?", retrieval.body().path("question").asText());
        assertEquals(USER_UUID, retrieval.body().path("userId").asText());
        assertEquals("Bearer " + blueToken, retrieval.blue());

        // LLM : le résultat du tool porte les références des extraits ; la réponse finale les cite.
        assertEquals(2, LLM.streamRequests.size());
        String toolMessage = messages(LLM.streamRequests.get(1)).stream()
                .filter(m -> "tool".equals(m.path("role").asText()))
                .map(m -> m.path("content").asText())
                // Résultat de tool sérialisé en JSON par Spring AI (DefaultToolCallResultConverter) : chaîne entre guillemets.
                .map(content -> content.startsWith("\"") ? JSON.readTree(content).asText() : content)
                .findFirst().orElseThrow();
        assertTrue(Pattern.compile("<document id=\"" + CHUNK_1 + "\"[^>]*ref=\"1\"").matcher(toolMessage).find(), toolMessage);
        assertTrue(Pattern.compile("<document id=\"" + CHUNK_2 + "\"[^>]*ref=\"2\"").matcher(toolMessage).find(), toolMessage);
        assertTrue(toolMessage.contains("Les personnes ayant atteint l'âge de référence"), toolMessage);

        // Persistance : marqueur canonique, source citée numérotée, table des références dans la trace.
        var saved = savedMessage(messageId);
        assertEquals(answer, saved.message());
        assertFalse(saved.message().contains("[1]"));
        assertFalse(saved.interrupted());
        assertEquals(1, saved.sources().length, Arrays.toString(saved.sources()));
        assertTrue(saved.sources()[0].startsWith("URL|" + CHUNK_1_URL + "|"), saved.sources()[0]);
        assertTrue(saved.sources()[0].endsWith("|" + CHUNK_1 + "||1"), saved.sources()[0]);
        var trace = saved.consultedSources();
        assertEquals("answer", trace.path("turn_status").asText());
        assertEquals(CHUNK_1, trace.path("citation_refs").path("1").asText());
        assertEquals(CHUNK_2, trace.path("citation_refs").path("2").asText());
        var citedTrace = trace.path("citations").get(0);
        assertEquals(1, citedTrace.path("n").asInt());
        assertEquals(1, citedTrace.path("ref").asInt());
        assertEquals(CHUNK_1, citedTrace.path("id").asText());
        var savedCall = saved.toolCalls().get(0);
        assertEquals(RAGTool.TOOL_NAME, savedCall.path("tool").asText());
        assertEquals("ok", savedCall.path("status").asText());

        // Relecture par l'API : la source garde son numéro de citation.
        var conversation = getJson("/api/conversations/" + conversationId);
        var assistant = conversation.path("messages").get(1);
        assertEquals(answer, assistant.path("message").asText());
        assertEquals(1, assistant.path("sources").get(0).path("citation").asInt());
        assertEquals(CHUNK_1, assistant.path("sources").get(0).path("documentId").asText());

        // Tour suivant : le marqueur est retiré de l'historique envoyé au LLM, les sources consultées sont rappelées.
        LLM.script(out -> out.text("Oui, l'âge de référence est le même pour les femmes.")
                .text("\n<turn_meta status=\"followup\" used=\"\"/>")
                .finish("stop"));
        var followUp = ask("Est-ce pareil pour les femmes ?", conversationId).readAll();
        assertEquals(ChatStreamEvent.DONE, followUp.getLast().name(), followUp.toString());
        var history = messages(LLM.streamRequests.get(2));
        String previousAnswer = history.stream()
                .filter(m -> "assistant".equals(m.path("role").asText()))
                .map(m -> m.path("content").asText())
                .findFirst().orElseThrow();
        assertTrue(previousAnswer.contains("versée dès 65 ans."), previousAnswer);
        assertFalse(previousAnswer.contains("[^1]") || previousAnswer.contains("[1]"), previousAnswer);
        String currentQuestion = history.getLast().path("content").asText();
        assertTrue(currentQuestion.contains("<retrieved_sources turn="), currentQuestion);
        assertTrue(currentQuestion.contains(CHUNK_1), currentQuestion);
        assertTrue(currentQuestion.endsWith("Est-ce pareil pour les femmes ?"), currentQuestion);
    }

    @Test
    void stopEndsTheStreamWithAnInterruptedDoneAndPersistsThePartialAnswer() throws Exception {
        var release = new CountDownLatch(1);
        LLM.script(out -> {
            out.text("Début de la réponse");
            // Le LLM reste actif sans produire de texte (commentaires SSE de maintien) ; voir aussi
            // stopIsPromptWhenTheLlmIsSilent pour un LLM sans aucun octet.
            while (!LLM.await(release, Duration.ofMillis(50))) {
                out.comment();
            }
            out.text(" jamais transmise.").finish("stop");
        }, release);

        var stream = ask("Explique-moi les cotisations AVS.", null);
        var start = stream.next(ChatStreamEvent.START).data();
        String conversationId = start.path("conversationId").asText();
        String messageId = start.path("messageId").asText();
        assertEquals("Début de la réponse", stream.next(ChatStreamEvent.TEXT_DELTA).data().path("text").asText());

        assertEquals(202, stop(conversationId, messageId));

        var done = stream.next(ChatStreamEvent.DONE).data();
        assertEquals(messageId, done.path("messageId").asText());
        assertTrue(done.path("interrupted").asBoolean());
        var remaining = stream.readAll();
        assertTrue(remaining.isEmpty(), "rien après done : " + remaining);
        release.countDown();

        var saved = savedMessage(messageId);
        assertTrue(saved.interrupted());
        assertEquals("Début de la réponse", saved.message());
        assertEquals(0, saved.sources().length);
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from chat_history where conversation_uuid = ? and role = 'user'",
                Integer.class, conversationId));
        // La génération est terminée : un second Stop ne la trouve plus.
        assertEquals(404, stop(conversationId, messageId));
    }

    @Test
    void stopIsPromptWhenTheLlmIsSilent() throws Exception {
        var release = new CountDownLatch(1);
        LLM.script(out -> {
            out.text("Début de la réponse");
            // LLM muet (aucun octet, pas même de commentaire SSE) : le thread du SDK reste bloqué en lecture. Le Stop
            // ne doit pas attendre la fermeture du flux (prochain octet ou timeout de lecture).
            LLM.await(release, Duration.ofSeconds(60));
            out.text(" jamais transmise.").finish("stop");
        }, release);

        var stream = ask("Explique-moi les rentes AI.", null);
        var start = stream.next(ChatStreamEvent.START).data();
        String conversationId = start.path("conversationId").asText();
        String messageId = start.path("messageId").asText();
        assertEquals("Début de la réponse", stream.next(ChatStreamEvent.TEXT_DELTA).data().path("text").asText());

        long stopStarted = System.nanoTime();
        assertEquals(202, stop(conversationId, messageId));
        var stopDuration = Duration.ofNanos(System.nanoTime() - stopStarted);
        assertTrue(stopDuration.compareTo(Duration.ofSeconds(5)) < 0, "Stop trop long : " + stopDuration);

        var done = stream.next(ChatStreamEvent.DONE).data();
        var doneAfter = Duration.ofNanos(System.nanoTime() - stopStarted);
        assertTrue(doneAfter.compareTo(Duration.ofSeconds(5)) < 0, "done trop tardif : " + doneAfter);
        assertEquals(messageId, done.path("messageId").asText());
        assertTrue(done.path("interrupted").asBoolean());
        assertTrue(stream.readAll().isEmpty());
        release.countDown();

        var saved = savedMessage(messageId);
        assertTrue(saved.interrupted());
        assertEquals("Début de la réponse", saved.message());
    }

    private static void assertCitedSource(JsonNode source) {
        assertEquals(CHUNK_1, source.path("documentId").asText());
        assertEquals("URL", source.path("type").asText());
        assertEquals(CHUNK_1_URL, source.path("link").asText());
        assertEquals("3", source.path("pageNumber").asText());
        assertEquals(1, source.path("citation").asInt());
    }

    private static int indexOf(List<SseEvent> events, Predicate<SseEvent> predicate) {
        for (int i = 0; i < events.size(); i++) {
            if (predicate.test(events.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String text(List<SseEvent> events) {
        var sb = new StringBuilder();
        events.stream().filter(e -> e.name().equals(ChatStreamEvent.TEXT_DELTA))
                .forEach(e -> sb.append(e.data().path("text").asText()));
        return sb.toString();
    }

    private static List<JsonNode> messages(JsonNode request) {
        List<JsonNode> messages = new ArrayList<>();
        request.path("messages").forEach(messages::add);
        return messages;
    }

    // ---- appels HTTP de l'application ----

    private SseStream ask(String query, String conversationId) {
        String boundary = "it-" + UUID.randomUUID();
        var body = new StringBuilder();
        part(body, boundary, "query", query);
        part(body, boundary, "language", "fr");
        if (conversationId != null) {
            part(body, boundary, "conversationId", conversationId);
        }
        body.append("--").append(boundary).append("--\r\n");
        var request = request("/api/conversations")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        return new SseStream(http, request);
    }

    private static void part(StringBuilder body, String boundary, String name, String value) {
        body.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n")
                .append("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
                .append(value).append("\r\n");
    }

    private int stop(String conversationId, String messageId) throws Exception {
        return http.send(request("/api/conversations/" + conversationId + "/generations/" + messageId + "/stop")
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private JsonNode getJson(String path) throws Exception {
        var response = http.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return JSON.readTree(response.body());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                        + environment.getRequiredProperty("local.server.port") + "/zia" + path))
                .header("blue", "Bearer " + blueToken)
                .timeout(Duration.ofSeconds(60));
    }

    private SavedMessage savedMessage(String messageId) {
        return jdbcTemplate.queryForObject("""
                        select message, sources, consulted_sources::text as trace, tool_calls::text as tools, interrupted
                        from chat_history where message_uuid = ?""",
                (rs, i) -> new SavedMessage(rs.getString("message"), (String[]) rs.getArray("sources").getArray(),
                        readTree(rs.getString("trace")), readTree(rs.getString("tools")), rs.getBoolean("interrupted")),
                messageId);
    }

    private static JsonNode readTree(String json) {
        return json == null ? JSON.missingNode() : JSON.readTree(json);
    }

    private record SavedMessage(String message, String[] sources, JsonNode consultedSources, JsonNode toolCalls,
                                boolean interrupted) {}

    private static String blueToken() {
        return Jwts.builder()
                .subject("{\"userUID\":\"%s\",\"firstname\":\"Test\",\"lastname\":\"Test\",\"visa\":\"TST\",\"roles\":[]}"
                        .formatted(USERNAME))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(BLUE_KEYS.getPrivate(), Jwts.SIG.RS512)
                .compact();
    }

    private static KeyPair rsaKeyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- lecture du flux SSE ----

    record SseEvent(String name, JsonNode data) {}

    /** Flux SSE lu en tâche de fond : les événements sont consommés dans l'ordre, avec délai maximal. */
    static final class SseStream {

        private static final SseEvent END = new SseEvent("<end>", null);

        private final BlockingQueue<SseEvent> events = new LinkedBlockingQueue<>();
        private final CompletableFuture<Integer> status = new CompletableFuture<>();

        SseStream(HttpClient http, HttpRequest request) {
            Thread.ofVirtual().start(() -> {
                try {
                    var response = http.send(request, HttpResponse.BodyHandlers.ofLines());
                    status.complete(response.statusCode());
                    String name = null;
                    var data = new StringBuilder();
                    for (var it = response.body().iterator(); it.hasNext(); ) {
                        String line = it.next();
                        if (line.isEmpty()) {
                            if (name != null || !data.isEmpty()) {
                                events.add(new SseEvent(name, JSON.readTree(data.toString())));
                            }
                            name = null;
                            data.setLength(0);
                        } else if (line.startsWith("event:")) {
                            name = field(line, "event:");
                        } else if (line.startsWith("data:")) {
                            if (!data.isEmpty()) {
                                data.append('\n');
                            }
                            data.append(field(line, "data:"));
                        }
                    }
                } catch (Exception e) {
                    // Le statut HTTP peut déjà être connu : l'erreur de lecture le remplace pour que readAll() échoue.
                    status.obtrudeException(e);
                } finally {
                    events.add(END);
                }
            });
        }

        private static String field(String line, String prefix) {
            String value = line.substring(prefix.length());
            return value.startsWith(" ") ? value.substring(1) : value;
        }

        /** Prochain événement {@code name} ; les événements intermédiaires sont ignorés. */
        SseEvent next(String name) throws Exception {
            while (true) {
                var event = take();
                assertNotSame(END, event, "flux terminé avant l'événement " + name);
                if (name.equals(event.name())) {
                    return event;
                }
            }
        }

        /** Événements restants jusqu'à la fin du flux. */
        List<SseEvent> readAll() throws Exception {
            List<SseEvent> all = new ArrayList<>();
            for (var event = take(); event != END; event = take()) {
                all.add(event);
            }
            assertEquals(200, status.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
            return all;
        }

        private SseEvent take() throws InterruptedException {
            var event = events.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertNotNull(event, "aucun événement SSE reçu en " + TIMEOUT);
            return event;
        }
    }

    // ---- LLM compatible OpenAI simulé ----

    @FunctionalInterface
    interface StreamScript {
        void write(ChunkWriter out) throws IOException;
    }

    /**
     * Serveur {@code /v1/chat/completions} : chaque requête en streaming consomme le script suivant ; les appels non
     * streamés (titre, inférence du workspace) reçoivent une réponse fixe. Les requêtes sont enregistrées.
     */
    static final class LlmStub {

        private final HttpServer server;
        private final BlockingQueue<StreamScript> scripts = new LinkedBlockingQueue<>();
        private final List<CountDownLatch> latches = new CopyOnWriteArrayList<>();
        final List<JsonNode> streamRequests = new CopyOnWriteArrayList<>();
        final List<JsonNode> otherRequests = new CopyOnWriteArrayList<>();

        LlmStub() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            // Un script peut bloquer (Stop) : les autres appels (titre) sont servis en parallèle.
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/", this::handle);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void script(StreamScript script, CountDownLatch... releases) {
            latches.addAll(List.of(releases));
            scripts.add(script);
        }

        /** @return {@code true} si le latch est ouvert avant {@code timeout} */
        boolean await(CountDownLatch latch, Duration timeout) {
            try {
                return latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return true;
            }
        }

        void reset() {
            scripts.clear();
            streamRequests.clear();
            otherRequests.clear();
        }

        void releaseAll() {
            latches.forEach(CountDownLatch::countDown);
            latches.clear();
        }

        void stop() {
            releaseAll();
            server.stop(0);
        }

        private void handle(HttpExchange exchange) throws IOException {
            try (exchange) {
                var body = JSON.readTree(exchange.getRequestBody().readAllBytes());
                if (!exchange.getRequestURI().getPath().endsWith("/chat/completions")) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                if (!body.path("stream").asBoolean()) {
                    otherRequests.add(body);
                    byte[] bytes = """
                            {"id":"chat-x","object":"chat.completion","created":1,"model":"test-chat",
                             "choices":[{"index":0,"message":{"role":"assistant","content":"Titre de test"},
                             "finish_reason":"stop"}],
                             "usage":{"prompt_tokens":5,"completion_tokens":2,"total_tokens":7}}
                            """.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                    return;
                }
                streamRequests.add(body);
                StreamScript script = scripts.poll();
                if (script == null) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                try {
                    script.write(new ChunkWriter(exchange.getResponseBody()));
                } catch (IOException ignored) {
                    // Client parti (Stop) : rien à faire.
                }
            }
        }
    }

    /** Écrit les morceaux {@code chat.completion.chunk} d'une réponse en streaming, envoyés immédiatement. */
    static final class ChunkWriter {

        private final OutputStream out;

        ChunkWriter(OutputStream out) {
            this.out = out;
        }

        ChunkWriter text(String text) throws IOException {
            var chunk = chunk();
            delta(chunk).put("content", text);
            return send(chunk);
        }

        ChunkWriter toolCall(String id, String name, String arguments) throws IOException {
            var chunk = chunk();
            ArrayNode calls = delta(chunk).putArray("tool_calls");
            var call = calls.addObject();
            call.put("index", 0);
            call.put("id", id);
            call.put("type", "function");
            call.putObject("function").put("name", name).put("arguments", arguments);
            return send(chunk);
        }

        ChunkWriter finish(String reason) throws IOException {
            var chunk = chunk();
            delta(chunk);
            ((ObjectNode) chunk.path("choices").get(0)).put("finish_reason", reason);
            chunk.putObject("usage").put("prompt_tokens", 100).put("completion_tokens", 20).put("total_tokens", 120);
            send(chunk);
            write("data: [DONE]\n\n");
            return this;
        }

        /** Commentaire SSE (ignoré par le client) : la connexion reste active sans contenu. */
        void comment() throws IOException {
            write(": ping\n\n");
        }

        private static ObjectNode chunk() {
            var chunk = JSON.createObjectNode();
            chunk.put("id", "chat-it").put("object", "chat.completion.chunk").put("created", 1).put("model", "test-chat");
            var choice = chunk.putArray("choices").addObject();
            choice.put("index", 0);
            choice.putNull("finish_reason");
            return chunk;
        }

        private static ObjectNode delta(ObjectNode chunk) {
            var delta = ((ObjectNode) chunk.path("choices").get(0)).putObject("delta");
            delta.put("role", "assistant");
            return delta;
        }

        private ChunkWriter send(ObjectNode chunk) throws IOException {
            write("data: " + JSON.writeValueAsString(chunk) + "\n\n");
            return this;
        }

        private void write(String data) throws IOException {
            out.write(data.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    // ---- zia-rag simulé ----

    record RetrievalRequest(String blue, JsonNode body) {}

    /** Serveur {@code POST /api/v1/retrieval/documents} : renvoie deux extraits, enregistre les requêtes. */
    static final class ZiaRagStub {

        private static final String DOCUMENTS = """
                [{"id":"%s","text":"Les personnes ayant atteint l'âge de référence (65 ans) ont droit à une rente de vieillesse.",
                  "metadata":{"title":"Rentes de vieillesse","url":"%s","page_num":"3","doc_id":"doc-avs-3.01",
                              "chunk_index":"0","source":"ahv-iv.ch","language":"fr"},"score":0.91},
                 {"id":"%s","text":"La rente peut être anticipée d'un ou deux ans.",
                  "metadata":{"title":"Rentes de vieillesse","url":"%s","page_num":"4","doc_id":"doc-avs-3.01",
                              "chunk_index":"1","source":"ahv-iv.ch","language":"fr"},"score":0.73}]
                """.formatted(CHUNK_1, CHUNK_1_URL, CHUNK_2, CHUNK_1_URL);

        private final HttpServer server;
        final BlockingQueue<RetrievalRequest> requests = new LinkedBlockingQueue<>();

        ZiaRagStub() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            server.createContext("/api/v1/retrieval/documents", exchange -> {
                try (exchange) {
                    requests.add(new RetrievalRequest(exchange.getRequestHeaders().getFirst("Blue"),
                            JSON.readTree(exchange.getRequestBody().readAllBytes())));
                    byte[] bytes = DOCUMENTS.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void reset() {
            requests.clear();
        }

        void stop() {
            server.stop(0);
        }
    }
}
