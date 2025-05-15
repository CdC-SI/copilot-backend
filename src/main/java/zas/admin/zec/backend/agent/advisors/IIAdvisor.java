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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import zas.admin.zec.backend.agent.AgentType;
import zas.admin.zec.backend.tools.ConversationMetaDataHolder;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class IIAdvisor implements StreamAroundAdvisor {

    private final ConversationMetaDataHolder holder;
    private final String conversationId;

    public IIAdvisor(ConversationMetaDataHolder holder, String conversationId1) {
        this.holder = holder;
        this.conversationId = conversationId1;
    }

    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        Flux<AdvisedResponse> advisedResponses =
                Mono.just(advisedRequest)
                        .publishOn(Schedulers.boundedElastic())
                        .map(this::before)
                        .flatMapMany(chain::nextAroundStream);

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
        return 0;
    }

    private AdvisedRequest before(AdvisedRequest advisedRequest) {
        // Set the agent in use for future requests
        holder.setCurrentAgentInUse(conversationId, AgentType.II_AGENT);
        return advisedRequest;
    }

    private AdvisedResponse after(AdvisedResponse advisedResponse) {
        Optional<AgentType> currentAgentInUse = holder.getCurrentAgentInUse(conversationId);
        if (currentAgentInUse.isPresent()) {
            return AdvisedResponse.builder()
                    .adviseContext(advisedResponse.adviseContext())
                    .response(new ChatResponse(List.of(new Generation(
                            new AssistantMessage(""),
                            ChatGenerationMetadata.builder().metadata("suggestion", "ii-salary").build()))))
                    .build();
        }
        return advisedResponse;
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
