package zas.admin.zec.backend.actions.sourceview;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import zas.admin.zec.backend.actions.converse.AttachmentStatus;
import zas.admin.zec.backend.actions.upload.model.EmbeddingStatus;
import zas.admin.zec.backend.persistence.entity.AttachmentEntity;
import zas.admin.zec.backend.persistence.entity.TempSourceDocumentEntity;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.persistence.repository.TempSourceDocumentRepository;
import zas.admin.zec.backend.tools.document.AbstractDocumentLookupIT;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Requêtes de l'accès aux sources (règle d'accès des chunks, aperçu des pièces jointes, fichiers) sur une vraie base pgvector. */
class SourceViewRepositoryIT extends AbstractDocumentLookupIT {

    private static final UUID PUBLIC = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID PUBLIC_NO_OWNER_KEY = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    private static final UUID PERSONAL_A = UUID.fromString("a0000000-0000-0000-0000-000000000003");
    private static final UUID PERSONAL_B = UUID.fromString("a0000000-0000-0000-0000-000000000004");
    private static final UUID PROCESS = UUID.fromString("a0000000-0000-0000-0000-000000000005");

    @Autowired
    private SourceViewRepository repository;
    @Autowired
    private AttachmentRepository attachmentRepository;
    @Autowired
    private TempSourceDocumentRepository documentRepository;

    @BeforeEach
    void insertData() {
        chunk(PUBLIC, "Public", Map.of("title", "guide.pdf", "user_uuid", "", "page_num", "2"));
        chunk(PUBLIC_NO_OWNER_KEY, "Public ancien", Map.of("title", "ancien.pdf"));
        chunk(PERSONAL_A, "Perso A", Map.of("title", "a.pdf", "user_uuid", "user-a"));
        chunk(PERSONAL_B, "Perso B", Map.of("title", "b.pdf", "user_uuid", "user-b"));
        jdbcTemplate.update("INSERT INTO business_process (id, content, metadata) VALUES (?::uuid, ?::jsonb, ?::jsonb)",
                PROCESS.toString(), "{}", json(Map.of("process_name", "Rentes", "bpanda_link", "https://bpanda/p")));
    }

    private void chunk(UUID id, String content, Map<String, String> metadata) {
        jdbcTemplate.update("INSERT INTO vector_store (id, content, metadata) VALUES (?::uuid, ?, ?::jsonb)",
                id.toString(), content, json(metadata));
    }

    @Test
    void publicChunks_areVisibleToAnyUser() {
        var chunk = repository.findAccessibleChunk(PUBLIC, "user-a");
        assertThat(chunk).isPresent();
        assertThat(chunk.get().id()).isEqualTo(PUBLIC.toString());
        assertThat(chunk.get().content()).isEqualTo("Public");
        assertThat(chunk.get().metadata()).containsEntry("title", "guide.pdf").containsEntry("page_num", "2");

        assertThat(repository.findAccessibleChunk(PUBLIC_NO_OWNER_KEY, "user-b")).isPresent();
    }

    @Test
    void personalChunks_areVisibleToTheirOwnerOnly() {
        assertThat(repository.findAccessibleChunk(PERSONAL_A, "user-a")).isPresent();
        assertThat(repository.findAccessibleChunk(PERSONAL_B, "user-a")).isEmpty();
        assertThat(repository.findAccessibleChunk(PERSONAL_A, null)).isEmpty();
    }

    @Test
    void unknownChunk_isEmpty() {
        assertThat(repository.findAccessibleChunk(UUID.randomUUID(), "user-a")).isEmpty();
    }

    @Test
    void process_isFoundById() {
        var process = repository.findProcess(PROCESS);
        assertThat(process).isPresent();
        assertThat(process.get().content()).isNull();
        assertThat(process.get().metadata()).containsEntry("process_name", "Rentes");

        assertThat(repository.findProcess(PUBLIC)).isEmpty();
    }

    @Test
    void attachmentPreview_readsTextAndFileHeaderOnly() {
        byte[] bytes = new byte[2048];
        Arrays.fill(bytes, (byte) 'x');
        System.arraycopy("%PDF-1.7".getBytes(), 0, bytes, 0, 8);
        var attachment = new AttachmentEntity();
        attachment.setUserId("user-a");
        attachment.setConversationId("conv-1");
        attachment.setFilename("scan");
        attachment.setFileSize((long) bytes.length);
        attachment.setFileBytes(bytes);
        attachment.setContent("Texte OCR");
        attachment.setStatus(AttachmentStatus.PROCESSED);
        Long id = attachmentRepository.saveAndFlush(attachment).getId();

        var preview = attachmentRepository.findPreviewById(id);

        assertThat(preview).isPresent();
        assertThat(preview.get().getId()).isEqualTo(id);
        assertThat(preview.get().getFilename()).isEqualTo("scan");
        assertThat(preview.get().getContent()).isEqualTo("Texte OCR");
        assertThat(preview.get().getHeader()).hasSize(512).startsWith("%PDF-1.7".getBytes());
        assertThat(attachmentRepository.findIdsByUserId("user-a")).contains(id);
        assertThat(attachmentRepository.findIdsByUserId("user-b")).doesNotContain(id);
    }

    @Test
    void documentIds_skipDocumentsWithoutFile_andSeparateOwners() {
        Long withoutFile = saveDocument("doc.pdf", "user-a", null);
        Long own = saveDocument("doc.pdf", "user-a", new byte[]{1});
        Long other = saveDocument("doc.pdf", "user-b", new byte[]{2});
        Long publicDoc = saveDocument("doc.pdf", null, new byte[]{3});

        assertThat(documentRepository.findIdsByFileNameAndUserUuid("doc.pdf", "user-a")).containsExactly(own)
                .doesNotContain(withoutFile, other);
        assertThat(documentRepository.findPublicIdsByFileName("doc.pdf")).containsExactly(publicDoc);
    }

    private Long saveDocument(String fileName, String owner, byte[] content) {
        var document = new TempSourceDocumentEntity();
        document.setFileName(fileName);
        document.setUserUuid(owner);
        document.setContent(content);
        document.setStatus(EmbeddingStatus.PROCESSED);
        return documentRepository.saveAndFlush(document).getId();
    }
}