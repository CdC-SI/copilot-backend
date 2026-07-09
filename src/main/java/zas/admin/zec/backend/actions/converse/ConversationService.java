package zas.admin.zec.backend.actions.converse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import zas.admin.zec.backend.actions.summarize.LlmOcrService;
import zas.admin.zec.backend.actions.upload.UploadService;
import zas.admin.zec.backend.actions.workspace.WorkspaceService;
import zas.admin.zec.backend.persistence.entity.AttachmentEntity;
import zas.admin.zec.backend.persistence.entity.ConversationTitleEntity;
import zas.admin.zec.backend.persistence.entity.MessageEntity;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.persistence.repository.ConversationRepository;
import zas.admin.zec.backend.persistence.repository.ConversationTitleRepository;
import zas.admin.zec.backend.rag.token.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@Transactional
public class ConversationService {

    private static final String DELIM = "|";

    private final ConversationRepository conversationRepository;
    private final ConversationTitleRepository conversationTitleRepository;
    private final AttachmentRepository attachmentRepository;
    private final ChatClient chatClient;
    private final RAGChatService ragChatService;
    private final LlmOcrService ocrService;
    private final WorkspaceService workspaceService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;


    public ConversationService(ConversationRepository conversationRepository,
                               ConversationTitleRepository conversationTitleRepository,
                               AttachmentRepository attachmentRepository,
                               RAGChatService ragChatService,
                               @Qualifier("internalChatModel") ChatModel chatModel,
                               LlmOcrService ocrService,
                               WorkspaceService workspaceService,
                               ApplicationEventPublisher eventPublisher,
                               TransactionTemplate transactionTemplate) {

        this.conversationRepository = conversationRepository;
        this.conversationTitleRepository = conversationTitleRepository;
        this.attachmentRepository = attachmentRepository;
        this.chatClient = ChatClient.create(chatModel);
        this.ragChatService = ragChatService;
        this.ocrService = ocrService;
        this.workspaceService = workspaceService;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
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

        // Le workspace ne provient que de la Question elle-même si elle en porte un ; une
        // conversation n'est pas figée sur un seul workspace (une question suivante peut viser un
        // tout autre workspace). À défaut, RAGTool infère lui-même le workspace le plus pertinent
        // pour cette question précise, à chaque appel.
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
                    case WorkspaceToken workspaceToken -> Flux.just(workspaceToken.content());
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

    /**
     * Persiste immédiatement les pièces jointes (bytes + métadonnées) en statut {@link AttachmentStatus#PENDING},
     * puis déclenche l'OCR de façon asynchrone via un {@link AttachmentUploadedEvent} traité
     * après commit par {@link AttachmentAsyncProcessor}.
     *
     * <p><strong>Cycle de vie MultipartFile :</strong> les bytes sont lus et commités en base <em>avant</em>
     * le retour de cette méthode. L'async ne reçoit que l'ID de l'entité et ne touche jamais au
     * {@code MultipartFile}, dont le stockage temporaire peut être libéré dès le 202.</p>
     */
    public ConversationAttachments attachFilesToConversation(String conversationId, String userId, List<MultipartFile> files) throws IOException {
        var convId = conversationId == null ? UUID.randomUUID().toString() : conversationId;

        // Lire tous les bytes AVANT la transaction : échec rapide si un fichier est illisible,
        // et garantit que le MultipartFile n'est plus accédé après le retour HTTP 202.
        record FileData(String filename, long size, byte[] bytes) {}
        var filesData = new ArrayList<FileData>(files.size());
        for (var file : files) {
            filesData.add(new FileData(file.getOriginalFilename(), file.getSize(), file.getBytes()));
        }

        // Transaction : persister les entités PENDING — commit garanti avant le dispatch async.
        record PersistResult(Long id, String filename, long size) {}
        List<PersistResult> results = transactionTemplate.execute(status -> {
            var saved = new ArrayList<PersistResult>(filesData.size());
            for (var data : filesData) {
                var entity = new AttachmentEntity();
                entity.setConversationId(convId);
                entity.setUserId(userId);
                entity.setFilename(data.filename());
                entity.setFileSize(data.size());
                entity.setFileBytes(data.bytes());
                entity.setStatus(AttachmentStatus.PENDING);
                // content est null : sera renseigné par AttachmentAsyncProcessor après l'OCR
                var persisted = attachmentRepository.save(entity);
                saved.add(new PersistResult(persisted.getId(), data.filename(), data.size()));
            }
            return saved;
        });

        // Publier un événement par pièce jointe : l'OCR async est déclenché AFTER_COMMIT
        // (jamais le MultipartFile, uniquement l'ID), garantissant la visibilité de la ligne.
        results.forEach(r -> eventPublisher.publishEvent(new AttachmentUploadedEvent(r.id())));

        var attachments = results.stream()
                .map(r -> new Attachment(r.id(), r.filename(), r.size(), AttachmentStatus.PENDING))
                .toList();
        return new ConversationAttachments(convId, attachments);
    }

    /**
     * Retourne le statut agrégé des pièces jointes d'une conversation, à destination de l'endpoint de polling.
     */
    public AttachmentUploadResponse getAttachmentsStatus(String conversationId, String userId) {
        var attachments = attachmentRepository.findAllByConversationIdAndUserId(conversationId, userId)
                .stream()
                .map(entity -> new Attachment(entity.getId(), entity.getFilename(), entity.getFileSize(), entity.getStatus()))
                .toList();
        return AttachmentUploadResponse.fromAttachments(new ConversationAttachments(conversationId, attachments));
    }

    public UploadService.Doc getAttachment(String conversationId, Long attachmentId, String userUuid) {
        var entity = attachmentRepository.findByIdAndConversationIdAndUserId(attachmentId, conversationId, userUuid)
                .orElseThrow(() -> new NoSuchElementException("Attachment not found"));

        return new UploadService.Doc(entity.getFilename(), new ByteArrayResource(entity.getFileBytes()));
    }

    public List<Attachment> getAttachmentsForConversation(String conversationId, String userId) {
        return attachmentRepository.findAllByConversationIdAndUserId(conversationId, userId)
                .stream()
                .map(entity -> new Attachment(entity.getId(), entity.getFilename(), entity.getFileSize(), entity.getStatus()))
                .toList();
    }

    public void deleteAttachment(Long attachmentId, String userId) {
        attachmentRepository.deleteByIdAndUserId(attachmentId, userId);
    }

    private Flux<Token> getTokenStream(Question question, String userId) {
        return Mono.fromCallable(() -> getConversationHistory(question.conversationId(), userId, Limit.unlimited()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(history -> ragChatService.answer(question, userId, history));
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

    public List<String> getWorkspaces() {
        return workspaceService.getAllNames();
    }
}
