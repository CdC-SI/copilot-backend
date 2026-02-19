package zas.admin.zec.backend.actions.converse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import zas.admin.zec.backend.actions.summarize.LlmOcrService;
import zas.admin.zec.backend.actions.upload.UploadService;
import zas.admin.zec.backend.agent.Agent;
import zas.admin.zec.backend.agent.AgentFactory;
import zas.admin.zec.backend.persistence.entity.AttachmentEntity;
import zas.admin.zec.backend.persistence.entity.ConversationTitleEntity;
import zas.admin.zec.backend.persistence.entity.MessageEntity;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.persistence.repository.ConversationRepository;
import zas.admin.zec.backend.persistence.repository.ConversationTitleRepository;
import zas.admin.zec.backend.rag.ChatStatus;
import zas.admin.zec.backend.rag.token.*;
import zas.admin.zec.backend.tools.ConversationMetaDataHolder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Slf4j
@Service
@Transactional
public class ConversationService {

    private static final String DELIM = "|";

    private final ConversationRepository conversationRepository;
    private final ConversationTitleRepository conversationTitleRepository;
    private final ConversationMetaDataHolder conversationMetaDataHolder;
    private final AttachmentRepository attachmentRepository;
    private final ChatClient chatClient;
    private final AgentFactory agentFactory;
    private final TaskExecutor taskExecutor;
    private final LlmOcrService ocrService;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationTitleRepository conversationTitleRepository,
                               ConversationMetaDataHolder conversationMetaDataHolder,
                               AttachmentRepository attachmentRepository,
                               AgentFactory agentFactory,
                               @Qualifier("internalChatModel") ChatModel chatModel,
                               @Qualifier("asyncExecutor") TaskExecutor taskExecutor,
                               LlmOcrService ocrService) {

        this.conversationRepository = conversationRepository;
        this.conversationTitleRepository = conversationTitleRepository;
        this.conversationMetaDataHolder = conversationMetaDataHolder;
        this.attachmentRepository = attachmentRepository;
        this.chatClient = ChatClient.create(chatModel);
        this.agentFactory = agentFactory;
        this.taskExecutor = taskExecutor;
        this.ocrService = ocrService;
    }

    public List<Source> getSourcesByMessageUuid(String conversationUuid, String messageUuid) {
        return conversationRepository.findByConversationIdAndMessageId(conversationUuid, messageUuid)
                .map(MessageEntity::getSources)
                .map(source -> Arrays.stream(source)
                        .map(this::fromSourceString)
                        .toList()
                )
                .orElse(List.of());
    }

    public List<ConversationTitle> getTitlesByUserId(String userId) {
        return conversationTitleRepository.findByUserIdOrderByTimestamp(userId)
                .stream()
                .map(title -> new ConversationTitle(title.getTitle(), title.getUserId(), title.getConversationId(), title.getTimestamp()))
                .toList();
    }

    public Conversation getByConversationIdAndUserId(String conversationId, String userId) {
        List<Message> messages = getConversationHistory(conversationId, userId, Limit.unlimited());
        List<Attachment> attachments = getAttachmentsForConversation(conversationId, userId);
        return new Conversation(conversationId, userId, messages, attachments);
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
        conversationMetaDataHolder.clearMetaData(conversationId);
        attachmentRepository.deleteByUserIdAndConversationId(userId, conversationId);
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

    @Transactional
    public ConversationAttachments attachFilesToConversation(String conversationId, String userId, List<MultipartFile> files) throws IOException {
        var convId = conversationId == null ? UUID.randomUUID().toString() : conversationId;

        List<Attachment> attachments = new ArrayList<>();
        for (MultipartFile file : files) {
            var bytes = file.getBytes();
            var entity = new AttachmentEntity();
            entity.setConversationId(convId);
            entity.setUserId(userId);
            entity.setFilename(file.getOriginalFilename());
            entity.setFileSize(file.getSize());
            entity.setFileBytes(bytes);
            entity.setContent(ocrService.ocrFile(bytes));

            var saved = attachmentRepository.save(entity);
            attachments.add(new Attachment(saved.getId(), saved.getFilename(), saved.getFileSize()));
        }

        return new ConversationAttachments(convId, attachments);
    }

    public UploadService.Doc getAttachment(String conversationId, Long attachmentId, String userUuid) {
        var entity = attachmentRepository.findByIdAndConversationIdAndUserId(attachmentId, conversationId, userUuid)
                .orElseThrow(() -> new NoSuchElementException("Attachment not found"));

        return new UploadService.Doc(entity.getFilename(), new ByteArrayResource(entity.getFileBytes()));
    }

    public List<Attachment> getAttachmentsForConversation(String conversationId, String userId) {
        return attachmentRepository.findAllByConversationIdAndUserId(conversationId, userId)
                .stream()
                .map(entity -> new Attachment(entity.getId(), entity.getFilename(), entity.getFileSize()))
                .toList();
    }

    public void deleteAttachment(Long attachmentId, String userId) {
        attachmentRepository.deleteByIdAndUserId(attachmentId, userId);
    }

    private Flux<Token> getTokenStream(Question question, String userId) {
        Token routingStatus = new StatusToken(ChatStatus.ROUTING, question.language());
        return Flux.just(routingStatus)
                .concatWith(getAgentAndHistoryStream(question, userId));
    }

    private Flux<Token> getAgentAndHistoryStream(Question question, String userId) {
        var agentFuture = fetchAgentAsync(question);
        var historyFuture = fetchConversationHistoryAsync(question, userId);
        Mono<Token> handoffFuture = Mono.fromFuture(agentFuture).map(agent -> new StatusToken(ChatStatus.AGENT_HANDOFF, question.language(), agent.getName()));
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

    private String toSourceString(Source src) {
        // Old format for strings that still have only link or FILE:link
        if (src.pageNumber() == null
            && src.subsection() == null
            && src.version() == null) {
            return src.type() == SourceType.FILE
                    ? "%s:%s".formatted(SourceType.FILE.name(), src.link())
                    : src.link();
        }

        // New extended format: TYPE|link|page|subsection|version|documentId   (URL-encoded parts)
        return String.join(DELIM,
                src.type().name(),
                src.link(),
                src.pageNumber(),
                src.subsection(),
                src.version(),
                src.documentId() == null ? "" : src.documentId()
                );
    }

    private Source fromSourceString(String raw) {
        /* -------- legacy strings -------- */
        if (!raw.contains(DELIM)) {                 // no “|” → old style
            if (raw.startsWith(SourceType.FILE.name() + ":")) {
                String link = raw.substring(SourceType.FILE.name().length() + 1);
                return new Source(SourceType.FILE, link);
            }
            return new Source(SourceType.URL, raw);
        }

        /* -------- new strings (pipe-separated) -------- */
        String[] parts = raw.split("\\|", -1);      // keep empty tail segments
        //       0       1      2         3          4          5
        //      TYPE | link | page | subsection | version | documentId
        SourceType type       = SourceType.valueOf(parts[0]);
        String link           = parts[1];
        String pageNumber     = parts.length > 2 ? parts[2] : null;
        String subsection     = parts.length > 3 ? parts[3] : null;
        String version        = parts.length > 4 ? parts[4] : null;
        String documentId     = parts.length > 5 ? parts[5] : generateLegacyDocId(parts);

        return new Source(documentId, type, link, pageNumber, subsection, version);
    }

    private String generateLegacyDocId(String[] sourceParts) {
        return String.join("#", Arrays.stream(sourceParts).filter(part -> part != null && !part.isEmpty()).toList());
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
