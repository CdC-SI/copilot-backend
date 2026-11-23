package zas.admin.zec.backend.actions.sourceview;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.actions.sourceview.SourceView.SourceDisplay;
import zas.admin.zec.backend.actions.sourceview.SourceView.SourceOrigin;
import zas.admin.zec.backend.actions.sourceview.SourceViewRepository.StoredDocument;
import zas.admin.zec.backend.persistence.entity.AttachmentEntity;
import zas.admin.zec.backend.persistence.entity.TempSourceDocumentEntity;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;
import zas.admin.zec.backend.tools.ConversationAttachmentTool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceViewServiceTest {

    private static final String USER = "user-a";
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private SourceViewRepository repository;
    @Mock
    private TempSourceDocumentRepository documentRepository;
    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private UserService userService;

    private SourceViewService service;

    @BeforeEach
    void setUp() {
        service = new SourceViewService(repository, documentRepository, attachmentRepository, userService);
    }

    private static Map<String, Object> meta(String... keyValues) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put(keyValues[i], keyValues[i + 1]);
        }
        return m;
    }

    private void chunk(String content, Map<String, Object> metadata) {
        when(repository.findAccessibleChunk(ID, USER)).thenReturn(Optional.of(new StoredDocument(ID.toString(), content, metadata)));
    }

    private static TempSourceDocumentEntity file(String name, byte[] content) {
        var entity = new TempSourceDocumentEntity();
        entity.setFileName(name);
        entity.setContent(content);
        return entity;
    }

    @Test
    void urlChunk_isShownAsUrlWithoutFile() {
        chunk("Texte du chunk", meta("title", "Directive", "url", "https://example.org/d.pdf", "page_num", "4",
                "subsection", "2.1", "version", "2024", "state", "in_force", "user_uuid", ""));

        var view = service.view(ID.toString(), USER);

        assertThat(view.origin()).isEqualTo(SourceOrigin.DOCUMENT);
        assertThat(view.display()).isEqualTo(SourceDisplay.URL);
        assertThat(view.url()).isEqualTo("https://example.org/d.pdf");
        assertThat(view.title()).isEqualTo("Directive");
        assertThat(view.pageNumber()).isEqualTo("4");
        assertThat(view.subsection()).isEqualTo("2.1");
        assertThat(view.version()).isEqualTo("2024");
        assertThat(view.state()).isEqualTo("in_force");
        assertThat(view.excerpt()).isEqualTo("Texte du chunk");
        assertThat(view.excerptTruncated()).isFalse();
        assertThat(view.fileAvailable()).isFalse();
        assertThatThrownBy(() -> service.file(ID.toString(), USER)).isInstanceOf(SourceViewNotFoundException.class);
    }

    @Test
    void publicPdfChunk_isShownAsPdfForInternalUser() {
        chunk("Extrait", meta("title", "guide.pdf", "user_uuid", ""));
        when(userService.hasAccessToInternalDocuments(USER)).thenReturn(true);
        when(documentRepository.findPublicIdsByFileName("guide.pdf")).thenReturn(List.of(9L, 2L));
        when(documentRepository.findById(9L)).thenReturn(Optional.of(file("guide.pdf", new byte[]{1, 2})));

        var view = service.view(ID.toString(), USER);
        var served = service.file(ID.toString(), USER);

        assertThat(view.display()).isEqualTo(SourceDisplay.PDF);
        assertThat(view.fileAvailable()).isTrue();
        assertThat(served.filename()).isEqualTo("guide.pdf");
        assertThat(served.mediaType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(served.isPdf()).isTrue();
        assertThat(served.content()).containsExactly(1, 2);
        verify(documentRepository, never()).findIdsByFileNameAndUserUuid(anyString(), anyString());
    }

    @Test
    void publicChunk_withoutInternalAccess_showsExcerptOnly() {
        chunk("Extrait", meta("title", "guide.pdf"));
        when(userService.hasAccessToInternalDocuments(USER)).thenReturn(false);

        var view = service.view(ID.toString(), USER);

        assertThat(view.display()).isEqualTo(SourceDisplay.EXCERPT);
        assertThat(view.fileAvailable()).isFalse();
        assertThatThrownBy(() -> service.file(ID.toString(), USER)).isInstanceOf(SourceViewNotFoundException.class);
        verify(documentRepository, never()).findPublicIdsByFileName(any());
    }

    @Test
    void personalChunk_usesOwnersDocumentWithoutInternalCheck() {
        chunk("Extrait", meta("title", "notes.docx", "user_uuid", USER));
        when(documentRepository.findIdsByFileNameAndUserUuid("notes.docx", USER)).thenReturn(List.of(5L));
        when(documentRepository.findById(5L)).thenReturn(Optional.of(file("notes.docx", new byte[]{7})));

        var view = service.view(ID.toString(), USER);
        var served = service.file(ID.toString(), USER);

        assertThat(view.display()).isEqualTo(SourceDisplay.DOWNLOAD);
        assertThat(view.fileAvailable()).isTrue();
        assertThat(served.isPdf()).isFalse();
        assertThat(served.mediaType().toString())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        verify(userService, never()).hasAccessToInternalDocuments(any());
        verify(documentRepository, never()).findPublicIdsByFileName(any());
    }

    @Test
    void chunkWithoutStoredFile_showsExcerpt() {
        chunk("Extrait", meta("title", "absent.pdf", "user_uuid", USER));
        when(documentRepository.findIdsByFileNameAndUserUuid("absent.pdf", USER)).thenReturn(List.of());

        assertThat(service.view(ID.toString(), USER).display()).isEqualTo(SourceDisplay.EXCERPT);
    }

    @Test
    void storedFileWithoutContent_isNotServed() {
        chunk("Extrait", meta("title", "vide.pdf", "user_uuid", USER));
        when(documentRepository.findIdsByFileNameAndUserUuid("vide.pdf", USER)).thenReturn(List.of(3L));
        when(documentRepository.findById(3L)).thenReturn(Optional.of(file("vide.pdf", null)));

        assertThatThrownBy(() -> service.file(ID.toString(), USER)).isInstanceOf(SourceViewNotFoundException.class);
    }

    @Test
    void processDescriptionChunk_isShownAsProcess() {
        chunk("Description", meta("doc_type", "business_process", "process_name", "Affiliation",
                "bpanda_link", "https://bpanda/p/1", "title", "ignored"));

        var view = service.view(ID.toString(), USER);

        assertThat(view.origin()).isEqualTo(SourceOrigin.BUSINESS_PROCESS);
        assertThat(view.display()).isEqualTo(SourceDisplay.PROCESS);
        assertThat(view.title()).isEqualTo("Affiliation");
        assertThat(view.url()).isEqualTo("https://bpanda/p/1");
    }

    @Test
    void longExcerpt_isTruncated() {
        chunk("x".repeat(SourceViewService.MAX_EXCERPT_LENGTH + 10), meta("url", "https://example.org"));

        var view = service.view(ID.toString(), USER);

        assertThat(view.excerpt()).hasSize(SourceViewService.MAX_EXCERPT_LENGTH);
        assertThat(view.excerptTruncated()).isTrue();
    }

    @Test
    void attachmentOfUser_isResolvedFromItsDocumentId() {
        String id = ConversationAttachmentTool.documentId(42L);
        var attachment = new AttachmentEntity();
        attachment.setId(42L);
        attachment.setFilename("piece.pdf");
        attachment.setContent("Texte extrait");
        attachment.setFileBytes(new byte[]{9});
        when(repository.findAccessibleChunk(UUID.fromString(id), USER)).thenReturn(Optional.empty());
        when(attachmentRepository.findIdsByUserId(USER)).thenReturn(List.of(41L, 42L));
        when(attachmentRepository.findById(42L)).thenReturn(Optional.of(attachment));

        var view = service.view(id, USER);
        var served = service.file(id, USER);

        assertThat(view.origin()).isEqualTo(SourceOrigin.ATTACHMENT);
        assertThat(view.display()).isEqualTo(SourceDisplay.PDF);
        assertThat(view.title()).isEqualTo("piece.pdf");
        assertThat(view.excerpt()).isEqualTo("Texte extrait");
        assertThat(view.state()).isEqualTo(SourceViewService.STATE_PERSONAL_UPLOADS);
        assertThat(view.fileAvailable()).isTrue();
        assertThat(served.content()).containsExactly(9);
    }

    @Test
    void attachmentOfAnotherUser_isNotFound() {
        String id = ConversationAttachmentTool.documentId(42L);
        when(repository.findAccessibleChunk(UUID.fromString(id), USER)).thenReturn(Optional.empty());
        when(attachmentRepository.findIdsByUserId(USER)).thenReturn(List.of(7L));
        when(repository.findProcess(UUID.fromString(id))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.view(id, USER)).isInstanceOf(SourceViewNotFoundException.class);
        verify(attachmentRepository, never()).findById(any());
    }

    @Test
    void businessProcess_isShownWithBpandaLink() {
        when(repository.findAccessibleChunk(ID, USER)).thenReturn(Optional.empty());
        when(attachmentRepository.findIdsByUserId(USER)).thenReturn(List.of());
        when(repository.findProcess(ID)).thenReturn(Optional.of(new StoredDocument(ID.toString(), null,
                meta("process_name", "Rentes", "bpanda_link", "https://bpanda/p/2", "bpanda_version", "3",
                        "process_state", "released", "purpose", "But du processus"))));

        var view = service.view(ID.toString(), USER);

        assertThat(view.origin()).isEqualTo(SourceOrigin.BUSINESS_PROCESS);
        assertThat(view.display()).isEqualTo(SourceDisplay.PROCESS);
        assertThat(view.title()).isEqualTo("Rentes");
        assertThat(view.url()).isEqualTo("https://bpanda/p/2");
        assertThat(view.version()).isEqualTo("3");
        assertThat(view.state()).isEqualTo("released");
        assertThat(view.excerpt()).isEqualTo("But du processus");
        assertThat(view.fileAvailable()).isFalse();
    }

    @Test
    void unknownId_isNotFound() {
        when(repository.findAccessibleChunk(ID, USER)).thenReturn(Optional.empty());
        when(attachmentRepository.findIdsByUserId(USER)).thenReturn(List.of());
        when(repository.findProcess(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.view(ID.toString(), USER)).isInstanceOf(SourceViewNotFoundException.class);
    }

    @Test
    void invalidId_isNotFound() {
        assertThatThrownBy(() -> service.view("not-a-uuid", USER)).isInstanceOf(SourceViewNotFoundException.class);
        assertThatThrownBy(() -> service.view(null, USER)).isInstanceOf(SourceViewNotFoundException.class);
    }
}
