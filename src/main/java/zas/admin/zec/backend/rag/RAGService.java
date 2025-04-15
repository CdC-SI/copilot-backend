package zas.admin.zec.backend.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import zas.admin.zec.backend.actions.converse.Message;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.rag.retriever.DocumentGatherer;
import zas.admin.zec.backend.rag.token.SourceToken;
import zas.admin.zec.backend.rag.token.StatusToken;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;
import zas.admin.zec.backend.rag.validation.SourceValidator;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
public class RAGService implements IRAGService {

    private final DocumentGatherer gatherer;
    private final SourceValidator sourceValidator;
    private final ChatModel chatModel;

    public RAGService(DocumentGatherer gatherer, SourceValidator sourceValidator, ChatModel chatModel) {
        this.gatherer = gatherer;
        this.sourceValidator = sourceValidator;
        this.chatModel = chatModel;
    }

    @Override
    public Flux<Token> streamAnswer(Question question, List<Message> conversationHistory) {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Flux<Token> statusToken = Flux.just(new StatusToken(RAGStatus.RETRIEVAL, question.language()));
        Flux<Token> retrievalAndChatTokens = Mono.fromCallable(() -> gatherer.retrieveRelatedDocuments(question))
                .flatMapMany(Flux::fromIterable)
                .filterWhen(doc -> Mono.fromCallable(() -> !question.sourceValidation() || sourceValidator.isValidSource(question, doc)))
                .collectList()
                .flatMapMany(docs -> {
                    Flux<Token> sourceTokens = Flux.fromIterable(docs)
                            .map(doc -> new SourceToken(doc.url()));

                    Prompt prompt = buildRequestPrompt(question, conversationHistory, docs);

                    Flux<Token> chatTokens = chatModel.stream(prompt)
                            .flatMap(chatResponse -> Flux.fromIterable(chatResponse.getResults()))
                            .map(generation -> new TextToken(generation.getOutput().getText()));

                    return Flux.concat(chatTokens, sourceTokens);
                });

        return Flux.merge(statusToken, retrievalAndChatTokens);
    }

    private Prompt buildRequestPrompt(Question question, List<Message> conversationHistory, List<Document> relatedDocuments) {
        var formattedHistory = conversationHistory.stream()
                .map(message -> "%s - %s%n%s%n%n".formatted(
                        message.timestamp(),
                        message.role(),
                        message.message()
                ))
                .collect(Collectors.joining());

        var formattedContext = IntStream.range(0, relatedDocuments.size())
                .mapToObj(i -> "<doc_%d>%s</doc_%d>%n".formatted(i + 1, relatedDocuments.get(i).text(), i + 1))
                .collect(Collectors.joining());

        var formattedCompleteness = RAGPrompts.getResponseCompletion(question.language(), question.responseFormat());

        var formattedResponseStyle = RAGPrompts.getResponseFormat(question.language(), question.responseStyle())
                .formatted(formattedCompleteness);

        var systemMessage = RAGPrompts.getRagSystemPrompt(question.language())
                .formatted(formattedHistory, formattedContext, formattedResponseStyle);

        return new Prompt(
                List.of(new SystemMessage(systemMessage), new UserMessage(question.query())),
                buildChatOptions(question)
        );
    }

    private ChatOptions buildChatOptions(Question question) {
        return ChatOptions.builder()
                .topP(question.topP())
                .maxTokens(question.maxOutputTokens())
                .model(question.llmModel())
                .temperature(question.temperature())
                .build();
    }
}
