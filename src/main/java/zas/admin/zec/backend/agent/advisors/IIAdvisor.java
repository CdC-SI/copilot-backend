package zas.admin.zec.backend.agent.advisors;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
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

public class IIAdvisor implements StreamAdvisor {

    private final ConversationMetaDataHolder holder;
    private final String conversationId;

    public IIAdvisor(ConversationMetaDataHolder holder, String conversationId1) {
        this.holder = holder;
        this.conversationId = conversationId1;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
        Flux<ChatClientResponse> advisedResponses = chain.nextStream(chatClientRequest);

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

    private ChatClientResponse after(ChatClientResponse chatClientResponse) {
        Optional<IIStep> step = holder.getStep(conversationId);

        if (step.isPresent() && step.get().equals(IIStep.CALCUL)) {
            return ChatClientResponse.builder()
                    .context(chatClientResponse.context())
                    .chatResponse(
                            new ChatResponse(List.of(new Generation(
                                    new AssistantMessage(""),
                                    ChatGenerationMetadata.builder().metadata("suggestion", "ii-salary").build()))))
                    .build();
        }

        return toMarkdown(chatClientResponse);
    }

    /**
     * Creates a predicate that evaluates whether a given {@code AdvisedResponse} contains at least one result
     * with non-null metadata and a non-empty "finishReason" value. The predicate ensures that the response
     * and its related data are checked for completeness and validity regarding the "finishReason".
     *
     * @return a {@code Predicate<AdvisedResponse>} that returns {@code true} if at least one result in the
     *         provided {@code AdvisedResponse} matches the criteria, otherwise {@code false}
     */
    private Predicate<ChatClientResponse> onFinishReason() {
        return chatClientResponse -> {
            assert chatClientResponse.chatResponse() != null;
            return chatClientResponse.chatResponse()
                    .getResults()
                    .stream()
                    .anyMatch(result -> result != null && result.getMetadata() != null
                            && StringUtils.hasText(result.getMetadata().getFinishReason()));
        };
    }

    private ChatClientResponse toMarkdown(ChatClientResponse chatClientResponse) {
        return ChatClientResponse.builder()
                .context(chatClientResponse.context())
                .chatResponse(new ChatResponse(
                        List.of(
                                new Generation(
                                        new AssistantMessage(
                                                stringToMD(chatClientResponse.chatResponse().getResult().getOutput().getText()))))))
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
