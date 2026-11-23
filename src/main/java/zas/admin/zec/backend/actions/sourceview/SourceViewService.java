package zas.admin.zec.backend.actions.sourceview;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.actions.sourceview.SourceView.SourceDisplay;
import zas.admin.zec.backend.actions.sourceview.SourceView.SourceOrigin;
import zas.admin.zec.backend.actions.sourceview.SourceViewRepository.StoredDocument;
import zas.admin.zec.backend.actions.upload.model.SourceDocumentType;
import zas.admin.zec.backend.persistence.entity.AttachmentEntity;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;
import zas.admin.zec.backend.tools.ConversationAttachmentTool;
import zas.admin.zec.backend.tools.MetadataFormatter;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static zas.admin.zec.backend.persistence.MetadataValues.getString;

/**
 * Résolution d'une source citée ({@code documentId} des sources d'une réponse) pour le panneau des sources :
 * métadonnées, extrait et fichier d'origine, avec contrôle d'accès.
 *
 * <p>Un identifiant désigne, dans cet ordre :</p>
 * <ul>
 *   <li>un chunk de {@code vector_store} : public ({@code user_uuid} absent ou vide) ou document personnel de
 *       l'utilisateur. Le fichier d'origine est le {@code temp_source_document} de même nom ({@code title}) et de même
 *       propriétaire ; un document public n'est servi qu'aux utilisateurs ayant accès aux documents internes ;</li>
 *   <li>une pièce jointe d'une conversation de l'utilisateur ({@link ConversationAttachmentTool#documentId}) ;</li>
 *   <li>une version de processus métier ({@code business_process}).</li>
 * </ul>
 * Une source inconnue ou appartenant à un autre utilisateur donne la même erreur ({@link SourceViewNotFoundException}).
 */
@Slf4j
@Service
public class SourceViewService {

    static final int MAX_EXCERPT_LENGTH = 6000;
    static final String STATE_PERSONAL_UPLOADS = "personal.uploads";

    private final SourceViewRepository repository;
    private final TempSourceDocumentRepository documentRepository;
    private final AttachmentRepository attachmentRepository;
    private final UserService userService;

    public SourceViewService(SourceViewRepository repository,
                             TempSourceDocumentRepository documentRepository,
                             AttachmentRepository attachmentRepository,
                             UserService userService) {
        this.repository = repository;
        this.documentRepository = documentRepository;
        this.attachmentRepository = attachmentRepository;
        this.userService = userService;
    }

    /** Fichier d'origine d'une source. */
    public record SourceFile(String filename, MediaType mediaType, byte[] content) {

        public boolean isPdf() {
            return MediaType.APPLICATION_PDF.equals(mediaType);
        }
    }

    private record Resolved(SourceView view, Supplier<Optional<SourceFile>> file) {}

    @Transactional(readOnly = true)
    public SourceView view(String id, String userId) {
        return resolve(id, userId).view();
    }

    /** @throws SourceViewNotFoundException si la source est inconnue, inaccessible ou sans fichier servi */
    @Transactional(readOnly = true)
    public SourceFile file(String id, String userId) {
        return resolve(id, userId).file().get()
                .orElseThrow(() -> new SourceViewNotFoundException("No file for source " + id));
    }

    private Resolved resolve(String id, String userId) {
        UUID uuid = parse(id);
        var chunk = repository.findAccessibleChunk(uuid, userId);
        if (chunk.isPresent()) {
            return fromChunk(chunk.get(), userId);
        }
        var attachment = findAttachment(uuid.toString(), userId);
        if (attachment.isPresent()) {
            return fromAttachment(uuid.toString(), attachment.get());
        }
        return repository.findProcess(uuid)
                .map(SourceViewService::fromProcess)
                .orElseThrow(() -> new SourceViewNotFoundException("Source not found: " + id));
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new SourceViewNotFoundException("Invalid source id: " + id);
        }
    }

    private Resolved fromChunk(StoredDocument chunk, String userId) {
        Map<String, Object> m = chunk.metadata();
        if (MetadataFormatter.DOC_TYPE_BUSINESS_PROCESS.equals(getString(m, MetadataFormatter.META_DOC_TYPE))) {
            var view = view(chunk.id(), SourceOrigin.BUSINESS_PROCESS, SourceDisplay.PROCESS,
                    firstNonBlank(getString(m, "process_name"), getString(m, "title")),
                    firstNonBlank(getString(m, "bpanda_link"), getString(m, "url")),
                    m, chunk.content(), false);
            return new Resolved(view, Optional::empty);
        }

        String url = getString(m, "url");
        if (url != null && !url.isBlank()) {
            var view = view(chunk.id(), SourceOrigin.DOCUMENT, SourceDisplay.URL, getString(m, "title"), url, m,
                    chunk.content(), false);
            return new Resolved(view, Optional::empty);
        }

        String filename = getString(m, "title");
        String owner = getString(m, "user_uuid");
        Optional<Long> fileId = findFileId(filename, owner != null && !owner.isBlank() ? owner : null, userId);
        SourceDisplay display = fileId.isEmpty() ? SourceDisplay.EXCERPT
                : isPdf(filename) ? SourceDisplay.PDF : SourceDisplay.DOWNLOAD;
        var view = view(chunk.id(), SourceOrigin.DOCUMENT, display, filename, null, m, chunk.content(), fileId.isPresent());
        return new Resolved(view, () -> fileId.flatMap(documentRepository::findById)
                .filter(document -> document.getContent() != null)
                .map(document -> new SourceFile(filename, SourceDocumentType.mediaTypeOf(filename), document.getContent())));
    }

    /**
     * Fichier d'origine d'un chunk : document personnel du propriétaire du chunk (l'utilisateur lui-même, la requête
     * des chunks l'ayant vérifié), sinon document public, réservé aux utilisateurs ayant accès aux documents internes.
     */
    private Optional<Long> findFileId(String filename, String owner, String userId) {
        if (filename == null || filename.isBlank()) {
            return Optional.empty();
        }
        if (owner != null) {
            return documentRepository.findIdsByFileNameAndUserUuid(filename, owner).stream().findFirst();
        }
        if (!userService.hasAccessToInternalDocuments(userId)) {
            return Optional.empty();
        }
        return documentRepository.findPublicIdsByFileName(filename).stream().findFirst();
    }

    private Optional<AttachmentEntity> findAttachment(String documentId, String userId) {
        return attachmentRepository.findIdsByUserId(userId).stream()
                .filter(attachmentId -> ConversationAttachmentTool.documentId(attachmentId).equals(documentId))
                .findFirst()
                .flatMap(attachmentRepository::findById);
    }

    private static Resolved fromAttachment(String id, AttachmentEntity attachment) {
        String filename = attachment.getFilename();
        var excerpt = excerpt(attachment.getContent());
        var view = new SourceView(id, SourceOrigin.ATTACHMENT, isPdf(filename) ? SourceDisplay.PDF : SourceDisplay.DOWNLOAD,
                filename, null, null, null, null, STATE_PERSONAL_UPLOADS, excerpt.text(), excerpt.truncated(), true);
        return new Resolved(view, () -> Optional.of(
                new SourceFile(filename, SourceDocumentType.mediaTypeOf(filename), attachment.getFileBytes())));
    }

    private static Resolved fromProcess(StoredDocument process) {
        Map<String, Object> m = process.metadata();
        var excerpt = excerpt(getString(m, "purpose"));
        var view = new SourceView(process.id(), SourceOrigin.BUSINESS_PROCESS, SourceDisplay.PROCESS,
                getString(m, "process_name"), getString(m, "bpanda_link"), null, null,
                getString(m, "bpanda_version"), getString(m, "process_state"),
                excerpt.text(), excerpt.truncated(), false);
        return new Resolved(view, Optional::empty);
    }

    private static SourceView view(String id, SourceOrigin origin, SourceDisplay display, String title, String url,
                                   Map<String, Object> m, String content, boolean fileAvailable) {
        var excerpt = excerpt(content);
        return new SourceView(id, origin, display, title, url, getString(m, "page_num"), getString(m, "subsection"),
                getString(m, "version"), getString(m, "state"), excerpt.text(), excerpt.truncated(), fileAvailable);
    }

    private record Excerpt(String text, boolean truncated) {}

    private static Excerpt excerpt(String text) {
        if (text == null || text.isBlank()) {
            return new Excerpt(null, false);
        }
        return text.length() > MAX_EXCERPT_LENGTH
                ? new Excerpt(text.substring(0, MAX_EXCERPT_LENGTH), true)
                : new Excerpt(text, false);
    }

    private static boolean isPdf(String filename) {
        return SourceDocumentType.PDF.matches(filename);
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
