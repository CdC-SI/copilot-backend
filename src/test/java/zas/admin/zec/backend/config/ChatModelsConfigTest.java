package zas.admin.zec.backend.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import zas.admin.zec.backend.config.properties.InternalChatModelProperties;
import zas.admin.zec.backend.config.properties.LlmResilienceProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ChatModelsConfigTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final BlockingQueue<StubResponse> responses = new LinkedBlockingQueue<>();
    private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
    private HttpServer server;
    private ChatModelsConfig config;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                requests.add(new Request(exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        mapper.readTree(exchange.getRequestBody().readAllBytes())));
                var response = responses.poll();
                if (response == null) {
                    exchange.sendResponseHeaders(500, -1);
                    return;
                }
                byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", response.contentType());
                exchange.sendResponseHeaders(response.status(), bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        config = config(baseUrl, new LlmResilienceProperties.Fallback(null, null, null, null));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private static ChatModelsConfig config(String baseUrl, LlmResilienceProperties.Fallback fallback) {
        return config(baseUrl, fallback, ObservationRegistry.NOOP);
    }

    private static ChatModelsConfig config(String baseUrl, LlmResilienceProperties.Fallback fallback,
                                           ObservationRegistry observationRegistry) {
        var beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("observationRegistry", observationRegistry);
        return new ChatModelsConfig(new InternalChatModelProperties(
                "test-key", "vision-model", baseUrl, "chat-model", baseUrl + "/",
                "embedding-model", baseUrl, 1024),
                new LlmResilienceProperties(Duration.ofSeconds(10), Duration.ofSeconds(10), 2,
                        Duration.ofMillis(1), Duration.ofMillis(5),
                        new LlmResilienceProperties.CircuitBreaker(50, 20, 10, Duration.ofSeconds(30)), fallback),
                beanFactory.getBeanProvider(MeterRegistry.class),
                beanFactory.getBeanProvider(ObservationRegistry.class));
    }

    private static final String STREAMED_ANSWER = """
            data: {"id":"chat-2","object":"chat.completion.chunk","created":1,"model":"chat-model","choices":[{"index":0,"delta":{"role":"assistant","content":"answer"},"finish_reason":null}]}

            data: {"id":"chat-2","object":"chat.completion.chunk","created":1,"model":"chat-model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

            data: [DONE]

            """;

    private static final String SERVER_ERROR = """
            {"error":{"message":"overloaded","type":"server_error"}}
            """;

    @Test
    void internalModelCallsAreObservedWithTheApplicationRegistry() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        List<String> observed = new CopyOnWriteArrayList<>();
        var observationRegistry = ObservationRegistry.create();
        observationRegistry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public void onStart(Observation.Context context) {
                observed.add(context.getName());
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });
        responses.add(new StubResponse("text/event-stream", STREAMED_ANSWER));

        String result = ChatClientConfig.builder(config(baseUrl, new LlmResilienceProperties.Fallback(null, null, null,
                        null), observationRegistry).internalChatModel(), observationRegistry).build()
                .prompt("hello").stream().content().collectList()
                .map(parts -> String.join("", parts)).block(Duration.ofSeconds(15));

        assertEquals("answer", result);
        assertTrue(observed.contains("gen_ai.client.operation"), "observation du modèle : " + observed);
        assertTrue(observed.contains("spring.ai.chat.client"), "observation du ChatClient : " + observed);
    }

    @Test
    void streamingRetriesServerErrorsOnceWithoutClientRetries() throws InterruptedException {
        responses.add(new StubResponse(503, "application/json", SERVER_ERROR));
        responses.add(new StubResponse("text/event-stream", STREAMED_ANSWER));

        String result = ChatClient.create(config.internalChatModel()).prompt("hello")
                .stream().content().collectList()
                .map(parts -> String.join("", parts)).block(Duration.ofSeconds(15));

        assertEquals("answer", result);
        assertEquals("chat-model", nextRequest().body().path("model").asText());
        assertEquals("chat-model", nextRequest().body().path("model").asText());
        assertNull(requests.poll(200, TimeUnit.MILLISECONDS), "pas de tentative supplémentaire du client OpenAI");
    }

    @Test
    void streamingFallsBackToTheFallbackModelWhenThePrimaryKeepsFailing() throws InterruptedException {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        var withFallback = config(baseUrl, new LlmResilienceProperties.Fallback("fallback-model", baseUrl,
                "fallback-key", ""));
        responses.add(new StubResponse(500, "application/json", SERVER_ERROR));
        responses.add(new StubResponse(502, "application/json", SERVER_ERROR));
        responses.add(new StubResponse("text/event-stream", STREAMED_ANSWER));

        String result = ChatClient.create(withFallback.internalChatModel()).prompt("hello")
                .stream().content().collectList()
                .map(parts -> String.join("", parts)).block(Duration.ofSeconds(15));

        assertEquals("answer", result);
        assertEquals("chat-model", nextRequest().body().path("model").asText());
        assertEquals("chat-model", nextRequest().body().path("model").asText());
        var fallbackRequest = nextRequest();
        assertEquals("fallback-model", fallbackRequest.body().path("model").asText());
        assertTrue(fallbackRequest.body().path("reasoning_effort").isMissingNode());
    }

    @Test
    void clientErrorsAreNotRetried() throws InterruptedException {
        responses.add(new StubResponse(400, "application/json", """
                {"error":{"message":"bad request","type":"invalid_request_error"}}
                """));

        var model = config.internalChatModel();
        assertThrows(RuntimeException.class, () -> ChatClient.create(model).prompt("hello")
                .stream().content().blockLast(Duration.ofSeconds(15)));

        nextRequest();
        assertNull(requests.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void structuredOutputPreservesVisionDefaultsAndUsesJackson3() throws InterruptedException {
        responses.add(new StubResponse("application/json", """
                {"id":"chat-1","object":"chat.completion","created":1,"model":"vision-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"{\\"name\\":\\"hello\\"}"},
                 "finish_reason":"stop"}]}
                """));
        var schema = """
                {"type":"object","properties":{"name":{"type":"string"}},"required":["name"],
                 "additionalProperties":false}
                """;

        JsonNode result = ChatClient.create(config.visionModel()).prompt("extract")
                .options(OpenAiChatOptions.builder().outputSchema(schema))
                .call().entity(JsonNode.class);

        assertEquals("hello", result.path("name").asText());
        var request = nextRequest();
        assertEquals("/v1/chat/completions", request.path());
        assertEquals("Bearer test-key", request.authorization());
        assertEquals("vision-model", request.body().path("model").asText());
        assertEquals(0.0, request.body().path("temperature").asDouble());
        assertEquals(15360, request.body().path("max_completion_tokens")
                .asInt(request.body().path("max_tokens").asInt()));
        assertEquals("json_schema", request.body().path("response_format").path("type").asText());
    }

    @Test
    void embeddingUsesExistingEndpointAndModel() throws InterruptedException {
        responses.add(new StubResponse("application/json", """
                {"object":"list","data":[{"object":"embedding","index":0,"embedding":[0.1,0.2]}],
                 "model":"embedding-model","usage":{"prompt_tokens":1,"total_tokens":1}}
                """));

        float[] result = config.internalEmbeddingModel().embed("hello");

        assertArrayEquals(new float[]{0.1f, 0.2f}, result);
        var request = nextRequest();
        assertEquals("/v1/embeddings", request.path());
        assertEquals("Bearer test-key", request.authorization());
        assertEquals("embedding-model", request.body().path("model").asText());
    }

    @Test
    void streamingExecutesToolsAndPreservesServerSideContext() throws InterruptedException {
        responses.add(new StubResponse("text/event-stream", """
                data: {"id":"chat-1","object":"chat.completion.chunk","created":1,"model":"chat-model","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call-1","type":"function","function":{"name":"lookup","arguments":"{\\"query\\":\\"hello\\"}"}}]},"finish_reason":null}]}

                data: {"id":"chat-1","object":"chat.completion.chunk","created":1,"model":"chat-model","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}

                data: [DONE]

                """));
        responses.add(new StubResponse("text/event-stream", """
                data: {"id":"chat-2","object":"chat.completion.chunk","created":1,"model":"chat-model","choices":[{"index":0,"delta":{"role":"assistant","content":"answer"},"finish_reason":null}]}

                data: {"id":"chat-2","object":"chat.completion.chunk","created":1,"model":"chat-model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

                """));
        var tool = new LookupTool();

        String result = ChatClient.create(config.internalChatModel()).prompt("hello")
                .tools(tool).toolContext(Map.of("userId", "user-test"))
                .stream().content().collectList()
                .map(parts -> String.join("", parts)).block(Duration.ofSeconds(15));

        assertEquals("answer", result);
        assertEquals("hello:user-test", tool.received);
        var first = nextRequest();
        var second = nextRequest();
        assertEquals("/v1/chat/completions", first.path());
        assertEquals(first.path(), second.path());
        assertEquals("chat-model", first.body().path("model").asText());
        assertEquals(16384, first.body().path("max_completion_tokens")
                .asInt(first.body().path("max_tokens").asInt()));
        assertEquals("low", first.body().path("reasoning_effort").asText());
        assertFalse(first.body().path("tools").toString().contains("userId"));
        assertTrue(second.body().path("messages").toString().contains("tool-result"));
    }

    private Request nextRequest() throws InterruptedException {
        var request = requests.poll(5, TimeUnit.SECONDS);
        assertNotNull(request);
        return request;
    }

    private record StubResponse(int status, String contentType, String body) {
        StubResponse(String contentType, String body) {
            this(200, contentType, body);
        }
    }

    private record Request(String path, String authorization, JsonNode body) {}

    static class LookupTool {
        private String received;

        @Tool(description = "Look up a test document")
        public String lookup(@ToolParam(description = "Search query") String query, ToolContext context) {
            received = query + ":" + context.getContext().get("userId");
            return "tool-result";
        }
    }
}
