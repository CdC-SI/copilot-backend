package zas.admin.zec.backend.actions.converse;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Limit;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import zas.admin.zec.backend.actions.summarize.LlmOcrService;
import zas.admin.zec.backend.actions.upload.UploadService;
import zas.admin.zec.backend.actions.upload.validation.UploadErrorCode;
import zas.admin.zec.backend.actions.upload.validation.UploadException;
import zas.admin.zec.backend.actions.workspace.WorkspaceService;
import zas.admin.zec.backend.config.properties.AttachmentOcrProperties;
import zas.admin.zec.backend.persistence.entity.AttachmentEntity;
import zas.admin.zec.backend.persistence.entity.ConversationTitleEntity;
import zas.admin.zec.backend.persistence.entity.MessageEntity;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.persistence.repository.ConversationRepository;
import zas.admin.zec.backend.persistence.repository.ConversationTitleRepository;
import zas.admin.zec.backend.rag.TurnTrace;
import zas.admin.zec.backend.rag.token.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Slf4j
@Service
@Transactional
public class ConversationService {

    private static final String DELIM = "|";
    private static final int PLACEHOLDER_TITLE_MAX_LENGTH = 100;

    private final ConversationRepository conversationRepository;
    private final ConversationTitleRepository conversationTitleRepository;
    private final AttachmentRepository attachmentRepository;
    private final ChatClient chatClient;
    private final ChatServiceFactory chatServiceFactory;
    private final LlmOcrService ocrService;
    private final WorkspaceService workspaceService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final AttachmentOcrProperties attachmentOcrProperties;
    private final GenerationRegistry generationRegistry;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationTitleRepository conversationTitleRepository,
                               AttachmentRepository attachmentRepository,
                               ChatServiceFactory chatServiceFactory,
                               @Qualifier("internalChatModel") ChatModel chatModel,
                               LlmOcrService ocrService,
                               WorkspaceService workspaceService,
                               ApplicationEventPublisher eventPublisher,
                               TransactionTemplate transactionTemplate,
                               AttachmentOcrProperties attachmentOcrProperties,
                               GenerationRegistry generationRegistry) {

        this.conversationRepository = conversationRepository;
        this.conversationTitleRepository = conversationTitleRepository;
        this.attachmentRepository = attachmentRepository;
        this.chatClient = ChatClient.create(chatModel);
        this.chatServiceFactory = chatServiceFactory;
        this.ocrService = ocrService;
        this.workspaceService = workspaceService;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.attachmentOcrProperties = attachmentOcrProperties;
        this.generationRegistry = generationRegistry;
    }

    public List<Source> getSourcesByMessageUuid(String conversationUuid, String messageUuid) {
        return conversationRepository.findByConversationIdAndMessageId(conversationUuid, messageUuid)
                .map(MessageEntity::getSources)
                .map(source -> Arrays.stream(source)
                        .map(ConversationService::fromSourceString)
                        .toList()
                )
                .orElse(List.of());
    }

    public List<ConversationTitle> getTitlesByUserId(String userId) {
        return conversationTitleRepository.findByUserIdOrderByTimestamp(userId)
                .stream()
                .map(title -> new ConversationTitle(title.getTitle(), title.getUserId(), title.getConversationId(), title.getConversationType(), title.getTimestamp()))
                .toList();
    }

    public Conversation getByConversationIdAndUserId(String conversationId, String userId) {
        // Une réponse interrompue en cours d'enregistrement fait partie de l'historique renvoyé.
        generationRegistry.awaitSaved(userId, conversationId);
        List<Message> messages = getConversationHistory(conversationId, userId, Limit.unlimited());
        List<Attachment> attachments = getAttachmentsForConversation(conversationId, userId);
        return new Conversation(conversationId, userId, messages, attachments);
    }

    public void initConversation(String userId, String conversationId, List<FAQMessage> messages) {
        for (var message : messages) {
            save(message, userId, conversationId);
        }

        // Flux FAQ : pas de notion de ragEnabled, conversation toujours de type COMPLETE.
        if (insertPlaceholderTitle(userId, conversationId, messages.get(0).message(), ConversationType.COMPLETE)) {
            generateConversationTitle(messages.get(0).message(), messages.get(1).message(), userId, conversationId,
                    messages.get(0).lang());
        }
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
        cancelPendingJobs(userId, attachmentRepository.findPendingJobIdsByConversation(userId, conversationId));
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

    /**
     * Flux de réponse typé (T1) : {@code start}, contenu, puis {@code done} une fois l'échange enregistré (ou
     * {@code error}). Le titre d'une nouvelle conversation est généré après {@code done}, avant la fin du flux.
     */
    public Flux<ChatStreamEvent> streamAnswer(Question question, String userId) {
        var timestamp = LocalDateTime.now();
        String assistantMessageId = UUID.randomUUID().toString();
        // F3b : une seule génération à la fois par conversation ; les enregistrements en cours sont attendus avant de
        // relire l'historique. Refus (409) levé ici, avant l'ouverture du flux.
        var generation = generationRegistry.begin(assistantMessageId, userId, question.conversationId());
        try {
            return streamAnswer(question, userId, timestamp, generation);
        } catch (RuntimeException e) {
            generationRegistry.finish(generation);
            throw e;
        }
    }

    private Flux<ChatStreamEvent> streamAnswer(Question question, String userId, LocalDateTime timestamp,
                                               GenerationRegistry.Generation generation) {
        String assistantMessageId = generation.messageId();
        // Structures thread-safe : relues par le thread d'annulation pendant que le flux de tokens écrit.
        StringBuffer assistantMessage = new StringBuffer();
        Set<Source> sources = ConcurrentHashMap.newKeySet();
        Set<String> suggestions = ConcurrentHashMap.newKeySet();
        // Garantit un seul enregistrement de l'échange (fin normale, Stop OU déconnexion) ; pris aussi en cas d'erreur
        // pour qu'aucun enregistrement n'ait lieu (l'éventuel échange remplacé est alors conservé).
        AtomicBoolean persisted = new AtomicBoolean(false);

        // Type de la conversation : figé à sa création (1re question), immuable ensuite.
        // Pour une conversation existante, on réutilise le type persisté ; pour une nouvelle
        // conversation, on conserve l'usage actuel du flag ragEnabled.
        ConversationType conversationType = resolveConversationType(question, userId);

        // Régénération / édition : la réponse à remplacer doit être la dernière de la conversation (409 sinon).
        // Le dernier échange est exclu du contexte, puis supprimé à l'enregistrement du nouvel échange.
        ReplacedTurn replacedTurn = question.replaceMessageId() == null ? null
                : replacedTurn(getConversationHistory(question.conversationId(), userId, Limit.unlimited()),
                question.replaceMessageId());
        List<String> replacedMessageIds = replacedTurn == null ? List.of() : replacedTurn.messageIds();

        // Le workspace de ce message : celui fourni explicitement par l'utilisateur s'il y en a
        // un, sinon celui inféré par RAGTool (WorkspaceToken) s'il a été invoqué, sinon null (le
        // tool de recherche documentaire n'a pas été appelé pour ce message). La question et la
        // réponse d'un même échange partagent toujours le même workspace.
        AtomicReference<String> workspace = new AtomicReference<>(
                question.workspace() != null && !question.workspace().isBlank() ? question.workspace() : null);

        // Trace du tour (statut déclaré + documents consultés), émise en interne par l'agent.
        AtomicReference<TurnTrace> turnTrace = new AtomicReference<>();
        // Réponse servant à générer le titre, renseignée après l'enregistrement d'une nouvelle conversation.
        AtomicReference<String> titleToGenerate = new AtomicReference<>();

        return getTokenStream(question, userId, conversationType, replacedTurn)
                // Stop (endpoint) : la production s'arrête (annulation de l'appel LLM) et le flux continue
                // normalement vers l'enregistrement de la réponse partielle, puis done {interrupted: true}.
                .takeUntilOther(generation.stopSignal())
                .flatMap(token -> switch (token) {
                    case StatusToken statusToken -> Flux.just(ChatStreamEvent.status(statusToken.tag(), statusToken.label()));
                    case TurnTraceToken turnTraceToken -> {
                        turnTrace.set(turnTraceToken.trace());
                        yield Flux.<ChatStreamEvent>empty();
                    }
                    case SuggestionToken suggestionToken -> {
                        if (suggestions.add(suggestionToken.suggestion())) {
                            yield Flux.just(ChatStreamEvent.suggestion(suggestionToken.suggestion()));
                        }
                        yield Flux.<ChatStreamEvent>empty();
                    }
                    case SourceToken sourceToken -> {
                        var source = Source.fromToken(sourceToken);
                        if (sources.add(source)) {
                            yield Flux.just(ChatStreamEvent.source(source));
                        }
                        yield Flux.<ChatStreamEvent>empty();
                    }
                    case WorkspaceToken workspaceToken -> {
                        workspace.set(workspaceToken.name());
                        yield Flux.just(ChatStreamEvent.workspace(workspaceToken.name()));
                    }
                    case TextToken textToken -> {
                        assistantMessage.append(textToken.content());
                        yield Flux.just(ChatStreamEvent.textDelta(textToken.content()));
                    }
                })
                .startWith(ChatStreamEvent.start(question.conversationId(), assistantMessageId))
                .concatWith(Mono.defer(() -> {
                    if (!persisted.compareAndSet(false, true)) {
                        return Mono.<ChatStreamEvent>empty();
                    }
                    boolean interrupted = generation.stopRequested();
                    var exchange = new StreamedExchange(assistantMessage.toString(), List.copyOf(sources),
                            List.copyOf(suggestions), workspace.get(), turnTrace.get());
                    // suppressCancel : une déconnexion pendant l'enregistrement ne l'annule pas.
                    // done n'est émis qu'après le commit (et la sortie du registre).
                    return Mono.fromFuture(saveAsync(generation, () -> saveExchange(question, userId, assistantMessageId,
                                    exchange, timestamp, conversationType, interrupted, replacedMessageIds)), true)
                            .map(newConversation -> {
                                titleToGenerate.set(newConversation ? exchange.answer() : null);
                                return ChatStreamEvent.done(assistantMessageId, interrupted);
                            });
                }))
                .onErrorResume(err -> {
                    persisted.set(true);
                    generationRegistry.finish(generation);
                    log.error(err.getMessage(), err);
                    return Flux.just(ChatStreamEvent.error(ChatStreamEvent.GENERATION_FAILED, err.getMessage()));
                })
                // Après done : génération du titre d'une nouvelle conversation, le flux se termine ensuite
                // (le front recharge alors les titres). Une erreur ici est journalisée sans affecter la réponse.
                .concatWith(Mono.defer(() -> {
                    var answer = titleToGenerate.getAndSet(null);
                    if (answer == null) {
                        return Mono.<ChatStreamEvent>empty();
                    }
                    return Mono.fromFuture(generateTitleAsync(question, userId, answer), true)
                            .then(Mono.<ChatStreamEvent>empty());
                }))
                // Déconnexion du client (navigation, abort) : l'annulation remonte jusqu'à l'appel LLM ; la question
                // et la réponse partielle déjà streamée sont enregistrées (réponse marquée interrompue), une seule fois.
                .doOnCancel(() -> {
                    if (!persisted.compareAndSet(false, true)) {
                        return;
                    }
                    var exchange = new StreamedExchange(assistantMessage.toString(), List.copyOf(sources),
                            List.copyOf(suggestions), workspace.get(), turnTrace.get());
                    saveAsync(generation, () -> saveExchange(question, userId, assistantMessageId, exchange, timestamp,
                            conversationType, true, replacedMessageIds))
                            .thenCompose(newConversation -> newConversation
                                    ? generateTitleAsync(question, userId, exchange.answer())
                                    : CompletableFuture.completedFuture(null))
                            .whenComplete((ignored, err) -> {
                                if (err != null) {
                                    log.error("Enregistrement de la réponse interrompue impossible (conversation {})",
                                            question.conversationId(), err);
                                }
                            });
                });
    }

    /** Arrête la génération en cours (Stop) ; {@code false} si elle est inconnue, terminée ou d'un autre utilisateur. */
    public boolean stopGeneration(String userId, String conversationId, String messageId) {
        return generationRegistry.stop(messageId, userId, conversationId);
    }

    /** Enregistre l'échange de façon asynchrone ; la génération quitte le registre une fois l'enregistrement terminé. */
    private CompletableFuture<Boolean> saveAsync(GenerationRegistry.Generation generation, Supplier<Boolean> saveExchange) {
        generationRegistry.saving(generation);
        return callAsync(saveExchange).whenComplete((ignored, err) -> generationRegistry.finish(generation));
    }

    private CompletableFuture<Void> generateTitleAsync(Question question, String userId, String answer) {
        return persistAsync(() -> generateConversationTitle(question.query(), answer, userId, question.conversationId(),
                question.language()))
                .exceptionally(err -> {
                    log.error("Génération du titre de la conversation {} impossible", question.conversationId(), err);
                    return null;
                });
    }
    /** Contenu streamé d'un échange (texte de la réponse et métadonnées), figé au moment de l'enregistrement. */
    private record StreamedExchange(String answer, List<Source> sources, List<String> suggestions,
                                    String workspace, TurnTrace turnTrace) {}

    private static CompletableFuture<Void> persistAsync(Runnable task) {
        return CompletableFuture.runAsync(task, runnable -> Schedulers.boundedElastic().schedule(runnable));
    }

    private static <T> CompletableFuture<T> callAsync(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, runnable -> Schedulers.boundedElastic().schedule(runnable));
    }

    /**
     * Détermine le {@link ConversationType} applicable à la question courante : si la
     * conversation existe déjà (ligne {@code chat_title} présente), son type persisté est
     * réutilisé tel quel (immutable) ; sinon (nouvelle conversation), le type est dérivé du
     * flag {@link Question#ragEnabled()} fourni par le client, comme actuellement.
     */
    private ConversationType resolveConversationType(Question question, String userId) {
        return conversationTitleRepository.findByUserIdAndConversationId(userId, question.conversationId())
                .map(ConversationTitleEntity::getConversationType)
                .orElseGet(() -> question.ragEnabled() != null && !question.ragEnabled()
                        ? ConversationType.NO_RAG
                        : ConversationType.COMPLETE);
    }

    /**
     * Persiste immédiatement les pièces jointes (bytes + métadonnées) en statut {@link AttachmentStatus#PENDING},
     * puis déclenche l'OCR de façon asynchrone via un {@link AttachmentUploadedEvent} traité
     * après commit par {@link AttachmentAsyncProcessor}.
     *
     * <p><strong>Cycle de vie MultipartFile :</strong> les bytes sont lus et commités en base <em>avant</em>
     * le retour de cette méthode. L'async ne reçoit que l'ID de l'entité et ne touche jamais au
     * {@code MultipartFile}, dont le stockage temporaire peut être libéré dès le 202.</p>
     *
     * @throws UploadException si aucun fichier n'est fourni, si un fichier est vide, illisible ou d'un type
     *                         non pris en charge par l'OCR (PDF ou image), si un PDF dépasse le nombre maximal de
     *                         pages ({@code attachment-ocr.max-pages}), ou si la persistance échoue
     */
    public ConversationAttachments attachFilesToConversation(String conversationId, String userId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new UploadException(null, UploadErrorCode.NO_FILE, "No attachment provided");
        }
        var convId = conversationId == null ? UUID.randomUUID().toString() : conversationId;

        // Lire et valider tous les fichiers AVANT la transaction : échec rapide si un fichier est illisible,
        // non pris en charge par l'OCR ou dépasse le nombre de pages autorisé, et garantit que le MultipartFile
        // n'est plus accédé après le retour HTTP 202.
        var filesData = new ArrayList<FileData>(files.size());
        for (var file : files) {
            filesData.add(readAttachment(file));
        }

        // Transaction : persister les entités PENDING — commit garanti avant le dispatch async.
        record PersistResult(Long id, String filename, long size, boolean pipeline) {}
        List<PersistResult> results;
        try {
            results = transactionTemplate.execute(status -> {
                var saved = new ArrayList<PersistResult>(filesData.size());
                for (var data : filesData) {
                    var entity = new AttachmentEntity();
                    entity.setConversationId(convId);
                    entity.setUserId(userId);
                    entity.setFilename(data.filename());
                    entity.setFileSize(data.size());
                    entity.setFileBytes(data.bytes());
                    entity.setStatus(AttachmentStatus.PENDING);
                    // next_poll_at renseigné : la pièce jointe est prise en charge par le poller du pipeline,
                    // y compris si le backend redémarre avant la soumission
                    entity.setNextPollAt(data.pipeline() ? LocalDateTime.now() : null);
                    // content est null : sera renseigné après l'extraction (pipeline) ou l'OCR LLM
                    var persisted = attachmentRepository.save(entity);
                    saved.add(new PersistResult(persisted.getId(), data.filename(), data.size(), data.pipeline()));
                }
                return saved;
            });
        } catch (DataAccessException e) {
            throw new UploadException(null, UploadErrorCode.UPLOAD_FAILED, "Error while saving attachments", e);
        }

        // Publier un événement par pièce jointe : l'extraction async est déclenchée AFTER_COMMIT
        // (jamais le MultipartFile, uniquement l'ID), garantissant la visibilité de la ligne.
        results.forEach(r -> eventPublisher.publishEvent(new AttachmentUploadedEvent(r.id(), r.pipeline())));

        var attachments = results.stream()
                .map(r -> new Attachment(r.id(), r.filename(), r.size(), AttachmentStatus.PENDING, null))
                .toList();
        return new ConversationAttachments(convId, attachments);
    }

    /**
     * Retourne le statut agrégé des pièces jointes d'une conversation, à destination de l'endpoint de polling.
     */
    public AttachmentUploadResponse getAttachmentsStatus(String conversationId, String userId) {
        var attachments = attachmentRepository.findAllByConversationIdAndUserId(conversationId, userId)
                .stream()
                .map(entity -> toAttachment(entity))
                .toList();
        return AttachmentUploadResponse.fromAttachments(new ConversationAttachments(conversationId, attachments));
    }

    public UploadService.Doc getAttachment(String conversationId, Long attachmentId, String userUuid) {
        var entity = attachmentRepository.findByIdAndConversationIdAndUserId(attachmentId, conversationId, userUuid)
                .orElseThrow(() -> new NoSuchElementException("Attachment not found"));

        return new UploadService.Doc(entity.getFilename(), MediaType.APPLICATION_OCTET_STREAM, new ByteArrayResource(entity.getFileBytes()));
    }

    public List<Attachment> getAttachmentsForConversation(String conversationId, String userId) {
        return attachmentRepository.findAllByConversationIdAndUserId(conversationId, userId)
                .stream()
                .map(entity -> toAttachment(entity))
                .toList();
    }

    private record FileData(String filename, long size, byte[] bytes, boolean pipeline) {}

    private FileData readAttachment(MultipartFile file) {
        var filename = file.getOriginalFilename();
        byte[] bytes;
        boolean pdf;
        try {
            bytes = file.getBytes();
            if (bytes.length == 0) {
                throw new UploadException(filename, UploadErrorCode.FILE_CONTENT_INVALID, "Empty attachment");
            }
            if (!ocrService.isSupported(bytes)) {
                throw new UploadException(filename, UploadErrorCode.FILE_TYPE_UNSUPPORTED, "Attachment type not supported by OCR");
            }
            pdf = ocrService.isPdf(bytes);
        } catch (IOException e) {
            throw new UploadException(filename, UploadErrorCode.FILE_READ_ERROR, "Failed to read attachment bytes", e);
        }
        if (pdf) {
            checkPageCount(filename, bytes);
        }
        return new FileData(filename, file.getSize(), bytes, pdf && attachmentOcrProperties.usePipeline());
    }

    private void checkPageCount(String filename, byte[] pdf) {
        int pages;
        try (var document = Loader.loadPDF(pdf)) {
            pages = document.getNumberOfPages();
        } catch (IOException e) {
            // PDF corrompu ou chiffré : illisible aussi bien par le pipeline que par l'OCR LLM
            throw new UploadException(filename, UploadErrorCode.FILE_CONTENT_INVALID, "Unreadable PDF attachment", e);
        }
        if (pages > attachmentOcrProperties.maxPages()) {
            throw new UploadException(filename, UploadErrorCode.TOO_MANY_PAGES,
                    "PDF attachment has " + pages + " pages (max " + attachmentOcrProperties.maxPages() + ")");
        }
    }

    private void cancelPendingJobs(String userId, List<String> jobIds) {
        if (!jobIds.isEmpty()) {
            eventPublisher.publishEvent(new AttachmentJobsCancelledEvent(userId, jobIds));
        }
    }

    private static Attachment toAttachment(AttachmentEntity entity) {
        return new Attachment(entity.getId(), entity.getFilename(), entity.getFileSize(), entity.getStatus(), entity.getErrorCode());
    }

    public void deleteAttachment(Long attachmentId, String userId) {
        cancelPendingJobs(userId, attachmentRepository.findPendingJobIds(attachmentId, userId));
        attachmentRepository.deleteByIdAndUserId(attachmentId, userId);
    }

    private Flux<Token> getTokenStream(Question question, String userId, ConversationType conversationType,
                                       ReplacedTurn replacedTurn) {
        Mono<List<Message>> history = replacedTurn != null
                ? Mono.just(replacedTurn.contextHistory())
                : Mono.fromCallable(() -> getConversationHistory(question.conversationId(), userId, Limit.unlimited()))
                        .subscribeOn(Schedulers.boundedElastic());
        return history.flatMapMany(messages -> chatServiceFactory.resolve(conversationType).answer(question, userId, messages));
    }

    /** Dernier échange remplacé par une régénération : historique de contexte (sans cet échange) et messages à supprimer. */
    record ReplacedTurn(List<Message> contextHistory, List<String> messageIds) {}

    /**
     * Valide que {@code replaceMessageId} désigne une réponse du dernier échange de l'historique (sinon 409
     * {@code TURN_NOT_REPLACEABLE} : réponse inconnue, d'un autre utilisateur ou plus la dernière).
     */
    static ReplacedTurn replacedTurn(List<Message> history, String replaceMessageId) {
        int start = lastExchangeStart(history);
        boolean replaceable = start >= 0 && history.subList(start, history.size()).stream()
                .anyMatch(message -> replaceMessageId.equals(message.messageId()) && !"USER".equals(message.role()));
        if (!replaceable) {
            throw new ConversationConflictException(ConversationConflictException.TURN_NOT_REPLACEABLE);
        }
        return new ReplacedTurn(List.copyOf(history.subList(0, start)), lastExchangeMessageIds(history));
    }

    /** Index du dernier message utilisateur de l'historique (début du dernier échange), {@code -1} s'il n'y en a pas. */
    static int lastExchangeStart(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("USER".equals(history.get(i).role())) {
                return i;
            }
        }
        return -1;
    }

    /** Identifiants des messages du dernier échange : dernière question et réponse(s) qui la suivent. */
    static List<String> lastExchangeMessageIds(List<Message> history) {
        int start = lastExchangeStart(history);
        if (start < 0) {
            return List.of();
        }
        return history.subList(start, history.size()).stream()
                .map(Message::messageId)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Enregistre l'échange (question + réponse, éventuellement partielle si {@code interrupted}) et, pour une nouvelle
     * conversation, son titre provisoire. En régénération ({@link Question#replaceMessageId()}), le dernier échange,
     * validé avant le streaming, est supprimé dans la même transaction ; la suppression est limitée aux messages de
     * l'utilisateur.
     *
     * @return {@code true} si la conversation vient d'être créée (son titre reste à générer)
     */
    private boolean saveExchange(Question question, String userId, String assistantMessageId, StreamedExchange exchange,
                                 LocalDateTime userMessageTimestamp, ConversationType conversationType, boolean interrupted,
                                 List<String> replacedMessageIds) {

        var userMessage = new Message(UUID.randomUUID().toString(), userId, question.conversationId(), null,
                question.language(), question.query(), "USER", null, null, userMessageTimestamp, exchange.workspace());
        var assistantMessage = new Message(assistantMessageId, userId, question.conversationId(), null,
                question.language(), exchange.answer(), "LLM", exchange.sources(), exchange.suggestions(),
                LocalDateTime.now(), exchange.workspace(), exchange.turnTrace(), interrupted);

        transactionTemplate.executeWithoutResult(status -> {
            if (!replacedMessageIds.isEmpty()) {
                conversationRepository.deleteByUserIdAndConversationIdAndMessageIdIn(userId, question.conversationId(),
                        replacedMessageIds);
            }
            save(userMessage, userId, question.conversationId());
            save(assistantMessage, userId, question.conversationId());
        });
        return insertPlaceholderTitle(userId, question.conversationId(), question.query(), conversationType);
    }

    /**
     * Insère de façon atomique le titre provisoire (la question) d'une nouvelle conversation, avec son type, figé à la
     * création : une question de suivi posée pendant la génération du titre retrouve la conversation sans second titre.
     *
     * @return {@code true} si la conversation vient d'être créée
     */
    private boolean insertPlaceholderTitle(String userId, String conversationId, String query, ConversationType conversationType) {
        return conversationTitleRepository.insertIfAbsent(userId, conversationId, placeholderTitle(query),
                LocalDateTime.now(), conversationType.name()) > 0;
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
                                .map(ConversationService::fromSourceString)
                                .toList(),
                        List.of(message.getSuggestions()),
                        message.getTimestamp(),
                        message.getWorkspace(),
                        message.getConsultedSources() != null ? TurnTrace.fromMap(message.getConsultedSources()) : null,
                        message.isInterrupted()
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
                    .map(ConversationService::toSourceString)
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
                    .map(ConversationService::toSourceString)
                    .distinct()
                    .toArray(String[]::new));
        entity.setSuggestions(Objects.isNull(message.suggestions())
                ? new String[0]
                : message.suggestions().toArray(String[]::new));
        entity.setWorkspace(message.workspace());
        entity.setInterrupted(message.interrupted());
        entity.setConsultedSources(message.turnTrace() != null ? message.turnTrace().toMap() : null);
        entity.setToolCalls(message.turnTrace() != null ? message.turnTrace().toolCallsAsMaps() : null);
        entity.setUsage(message.turnTrace() != null && message.turnTrace().usage() != null
                ? message.turnTrace().usage().toMap() : null);

        conversationRepository.save(entity);
    }

    static String toSourceString(Source src) {
        // Old format for strings that still have only link or FILE:link
        if (src.type() != SourceType.BUSINESS_PROCESS
            && src.pageNumber() == null
            && src.subsection() == null
            && src.version() == null) {
            return src.type() == SourceType.FILE
                    ? "%s:%s".formatted(SourceType.FILE.name(), src.link())
                    : src.link();
        }

        // New extended format: TYPE|link|page|subsection|version|documentId|title
        return String.join(DELIM,
                src.type().name(),
                sanitize(src.link()),
                sanitize(src.pageNumber()),
                sanitize(src.subsection()),
                sanitize(src.version()),
                src.documentId() == null ? "" : src.documentId(),
                sanitize(src.title())
                );
    }

    /**
     * Le séparateur "|" ne doit pas apparaître dans les champs ; {@code null} est écrit comme une chaîne vide
     * (et non "null", que {@link String#join} produirait).
     */
    private static String sanitize(String value) {
        return value == null ? "" : value.replace(DELIM, "/");
    }

    /** Champ optionnel relu : absent, vide ou "null" (lignes écrites avant correction) → {@code null}. */
    private static String optionalPart(String[] parts, int index) {
        if (parts.length <= index || parts[index].isEmpty() || "null".equals(parts[index])) {
            return null;
        }
        return parts[index];
    }

    static Source fromSourceString(String raw) {
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
        //       0       1      2         3          4          5          6
        //      TYPE | link | page | subsection | version | documentId | title
        SourceType type       = SourceType.valueOf(parts[0]);
        String link           = parts[1];
        String pageNumber     = optionalPart(parts, 2);
        String subsection     = optionalPart(parts, 3);
        String version        = optionalPart(parts, 4);
        String documentId     = parts.length > 5 ? parts[5] : generateLegacyDocId(parts);
        String title          = optionalPart(parts, 6);

        return new Source(documentId, type, link, pageNumber, subsection, version, title);
    }

    private static String generateLegacyDocId(String[] sourceParts) {
        return String.join("#", Arrays.stream(sourceParts).filter(part -> part != null && !part.isEmpty()).toList());
    }

    /**
     * Remplace le titre provisoire d'une nouvelle conversation (inséré par {@link #saveExchange}) par le titre généré
     * par le LLM. En cas d'échec, le titre provisoire est conservé.
     */
    private void generateConversationTitle(String initialQuery, String initialResponse, String userId, String conversationId,
                                            String language) {
        String title;
        try {
            title = chatClient.prompt()
                    .system(ConversationPrompts.getConversationTitlePrompt(language)
                            .formatted(initialResponse))
                    .user(initialQuery)
                    .call()
                    .content();
        } catch (RuntimeException e) {
            log.error("Génération du titre de la conversation {} impossible, titre provisoire conservé", conversationId, e);
            return;
        }
        if (title == null || title.isBlank()) {
            return;
        }

        conversationTitleRepository.findByUserIdAndConversationId(userId, conversationId)
                .ifPresent(entity -> {
                    entity.setTitle(title);
                    conversationTitleRepository.save(entity);
                });
    }

    private static String placeholderTitle(String query) {
        var title = query == null ? "" : query.strip();
        return title.length() > PLACEHOLDER_TITLE_MAX_LENGTH
                ? title.substring(0, PLACEHOLDER_TITLE_MAX_LENGTH) + "…"
                : title;
    }

    public List<String> getWorkspaces() {
        return workspaceService.getAllNames();
    }
}
