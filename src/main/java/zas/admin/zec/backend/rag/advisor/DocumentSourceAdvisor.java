package zas.admin.zec.backend.rag.advisor;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS;

public class DocumentSourceAdvisor implements StreamAroundAdvisor {

    private static final String DEFAULT_USER_TEXT_ADVISE = """
            Here are the sources of the documents used to answer the question, they have to be added at the end of the generated answer.
            Without any additional text, just the sources.:
            
            {document_sources}
            """;

    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        // This can be executed by both blocking and non-blocking Threads
        // E.g. a command line or Tomcat blocking Thread implementation
        // or by a WebFlux dispatch in a non-blocking manner.
        Flux<AdvisedResponse> advisedResponses =
                // @formatter:off
                Mono.just(advisedRequest)
                        .publishOn(Schedulers.boundedElastic())
                        .map(this::before)
                        .flatMapMany(chain::nextAroundStream);
        // @formatter:on

        return advisedResponses.map(ar -> {
            if (onFinishReason().test(ar)) {
                ar = after(ar);
            }
            return ar;
        });
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 1;
    }

    private AdvisedRequest before(AdvisedRequest request) {
        var context = new HashMap<>(request.adviseContext());

        // 1. Advise the system text.
        String advisedUserText = request.userText() + System.lineSeparator() + DEFAULT_USER_TEXT_ADVISE;

        // 3. Get the documents from the context.
        List<Document> documents = (List<Document>) context.get(RETRIEVED_DOCUMENTS);
        String documentSources = documents.stream()
                .map(doc -> {
                    var url = doc.getMetadata().get("url");
                    return "<source><a href='%s'>%s</a></source>".formatted(url, url);
                })
                .collect(Collectors.joining(System.lineSeparator()));

        // 4. Advise the user parameters.
        Map<String, Object> advisedUserParams = new HashMap<>(request.userParams());
        advisedUserParams.put("document_sources", documentSources);

        AdvisedRequest advisedRequest = AdvisedRequest.from(request)
                .userText(advisedUserText)
                .userParams(advisedUserParams)
                .adviseContext(context)
                .build();

        return advisedRequest;
    }

    private AdvisedResponse after(AdvisedResponse advisedResponse) {
        ChatResponse.Builder chatResponseBuilder = ChatResponse.builder().from(advisedResponse.response());
        chatResponseBuilder.metadata(RETRIEVED_DOCUMENTS, advisedResponse.adviseContext().get(RETRIEVED_DOCUMENTS));
        return new AdvisedResponse(chatResponseBuilder.build(), advisedResponse.adviseContext());
    }

    private Predicate<AdvisedResponse> onFinishReason() {
        return advisedResponse -> advisedResponse.response()
                .getResults()
                .stream()
                .filter(result -> result != null && result.getMetadata() != null
                        && StringUtils.hasText(result.getMetadata().getFinishReason()))
                .findFirst()
                .isPresent();
    }
}
