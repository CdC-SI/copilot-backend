package zas.admin.zec.backend.agent.advisors;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.agent.tools.IIStep;
import zas.admin.zec.backend.tools.ConversationMetaDataHolder;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class IIAdvisor implements StreamAroundAdvisor {

    private final ConversationMetaDataHolder holder;
    private final String conversationId;

    /**
     * Constructs an instance of the IIAdvisor class with the specified conversation metadata holder,
     * conversation ID, and chat model.
     *
     * @param holder the conversation metadata holder used for managing metadata associated with the conversation
     * @param conversationId1 the ID of the conversation for which this advisor is created
     */
    public IIAdvisor(ConversationMetaDataHolder holder, String conversationId1) {
        this.holder = holder;
        this.conversationId = conversationId1;
    }

    /**
     * Processes an advised request through a stream advisor chain, managing the interaction logic
     * and dynamically applying pre-processing and post-processing transformations to the request and response.
     * This method ensures specific handling depending on the conversation's current step ("etape").
     *
     * @param advisedRequest the input request containing conversation details, messages, and other metadata
     * @param chain the chain of advisors responsible for orchestrating the flow of advised requests and responses
     * @return a reactive stream (Flux) of advised responses after applying processing rules, such as pre-handling
     *         with {@code before}, streaming through the advisor chain, and post-handling with {@code after}
     */
    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        Flux<AdvisedResponse> advisedResponses = chain.nextAroundStream(advisedRequest);

        return advisedResponses.map(ar -> {
            if (onFinishReason().test(ar)) {
                ar = after(ar);
            }
            return ar;
        });
    }

    /**
     * Retrieves the name of the advisor, which is the simple name of the class implementing this method.
     *
     * @return the class's simple name as a {@code String}
     */
    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Determines the execution order of this advisor among multiple advisors.
     * A lower value indicates higher priority in execution order.
     *
     * @return an integer representing the order of this advisor
     */
    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * Processes the response after the main conversational logic has been handled.
     * Depending on the current step (`etape`) of the conversation, it may modify the response
     * or return it as is. Specifically, if the step corresponds to "2" or certain variations,
     * it handles the response differently by checking the current agent in use and generating
     * a specific structure. If no modifications are needed, the original response is returned.
     *
     * @param advisedResponse the response object containing information processed by the conversation logic
     * @return an {@code AdvisedResponse} instance, either modified or as originally provided,
     *         depending on the conversation's current step
     */
    private AdvisedResponse after(AdvisedResponse advisedResponse) {
        Optional<IIStep> step = holder.getStep(conversationId);

        if (step.isPresent() && step.get().equals(IIStep.CALCUL)) {
            return AdvisedResponse.builder()
                    .adviseContext(advisedResponse.adviseContext())
                    .response(new ChatResponse(List.of(new Generation(
                            new AssistantMessage(""),
                            ChatGenerationMetadata.builder().metadata("suggestion", "ii-salary").build()))))
                    .build();
        }

        return toMarkdown(advisedResponse);
    }

    /**
     * Creates a predicate that evaluates whether a given {@code AdvisedResponse} contains at least one result
     * with non-null metadata and a non-empty "finishReason" value. The predicate ensures that the response
     * and its related data are checked for completeness and validity regarding the "finishReason".
     *
     * @return a {@code Predicate<AdvisedResponse>} that returns {@code true} if at least one result in the
     *         provided {@code AdvisedResponse} matches the criteria, otherwise {@code false}
     */
    private Predicate<AdvisedResponse> onFinishReason() {
        return advisedResponse -> {
            assert advisedResponse.response() != null;
            return advisedResponse.response()
                    .getResults()
                    .stream()
                    .anyMatch(result -> result != null && result.getMetadata() != null
                            && StringUtils.hasText(result.getMetadata().getFinishReason()));
        };
    }

    /**
     * Transforms an {@code AdvisedResponse} instance by converting its response text into Markdown format.
     * This method utilizes {@code stringToMD} to convert the response content and rebuilds
     * the {@code AdvisedResponse} with the updated content.
     *
     * @param advisedResponse the input {@code AdvisedResponse} object containing the response text to be transformed
     * @return a new {@code AdvisedResponse} instance with the response text converted to Markdown format
     */
    private AdvisedResponse toMarkdown(AdvisedResponse advisedResponse) {
        return AdvisedResponse.builder()
                .adviseContext(advisedResponse.adviseContext())
                .response(new ChatResponse(
                        List.of(
                                new Generation(
                                        new AssistantMessage(
                                                stringToMD(advisedResponse.response().getResult().getOutput().getText()))))))
                .build();
    }

    /**
     * Converts a given string into a Markdown-compatible format by replacing certain characters.
     * Specifically, it replaces occurrences of "\n" with a line break ("<br>")
     * and removes double quotes from the string.
     *
     * @param content the input string to be converted
     * @return the transformed string with Markdown-compatible formatting
     */
    private String stringToMD(String content) {
        return content.replace("\\n", "<br>")
                .replace("\"", "");
    }
}
