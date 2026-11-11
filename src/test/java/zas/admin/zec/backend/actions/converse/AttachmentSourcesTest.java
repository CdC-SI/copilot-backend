package zas.admin.zec.backend.actions.converse;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import zas.admin.zec.backend.persistence.entity.AttachmentEntity;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository.AttachmentSummary;
import zas.admin.zec.backend.rag.ConsultedSource;
import zas.admin.zec.backend.rag.TurnStatus;
import zas.admin.zec.backend.rag.TurnTrace;
import zas.admin.zec.backend.rag.TurnMetaStreamFilter.TurnMeta;
import zas.admin.zec.backend.tools.ConversationAttachmentTool;
import zas.admin.zec.backend.tools.MetadataFormatter;
import zas.admin.zec.backend.tools.ToolContextKeys;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pièces jointes de la conversation : annoncées par {@code <conversation_attachments>}, lues via
 * {@code get_attachment_content}, puis traitées comme des sources consultées à identifiant stable.
 */
class AttachmentSourcesTest {

    private static AttachmentEntity attachment(long id, String filename, AttachmentStatus status) {
        var entity = new AttachmentEntity();
        entity.setId(id);
        entity.setFilename(filename);
        entity.setStatus(status);
        entity.setContent(status == AttachmentStatus.PROCESSED ? "contenu de " + filename : null);
        return entity;
    }

    private static AttachmentSummary summary(long id, String filename, AttachmentStatus status) {
        return new AttachmentSummary() {
            public Long getId() { return id; }
            public String getFilename() { return filename; }
            public AttachmentStatus getStatus() { return status; }
        };
    }

    private static ToolContext context(List<Document> sink) {
        return new ToolContext(Map.of(
                ToolContextKeys.CTX_CONVERSATION_ID, "conv",
                ToolContextKeys.CTX_USER_ID, "user",
                ToolContextKeys.CTX_LANGUAGE, "fr",
                ToolContextKeys.CTX_RETRIEVED_DOCUMENTS, sink));
    }

    private static ConversationAttachmentTool tool(AttachmentEntity... attachments) {
        var repository = mock(AttachmentRepository.class);
        when(repository.findAllByConversationIdAndUserId("conv", "user")).thenReturn(List.of(attachments));
        return new ConversationAttachmentTool(repository);
    }

    @Test
    void readingAnAttachmentDepositsItWithAStableId() {
        var tool = tool(attachment(12, "decompte_2025.pdf", AttachmentStatus.PROCESSED));
        List<Document> sink = new CopyOnWriteArrayList<>();

        String first = tool.getAttachmentContent("decompte", context(sink));
        tool.getAttachmentContent(null, context(sink));

        assertTrue(first.contains("contenu de decompte_2025.pdf"));
        assertTrue(first.startsWith("<attachment id=\"" + ConversationAttachmentTool.documentId(12L) + "\" filename="), first);
        assertEquals(2, sink.size());
        assertEquals(sink.get(0).getId(), sink.get(1).getId());
        assertEquals(ConversationAttachmentTool.documentId(12L), sink.getFirst().getId());
        assertNotEquals(ConversationAttachmentTool.documentId(13L), ConversationAttachmentTool.documentId(12L));
        assertEquals(MetadataFormatter.COLLECTION_ATTACHMENT, MetadataFormatter.collectionKey(sink.getFirst()));
    }

    @Test
    void pendingAttachmentIsNotDeposited() {
        var tool = tool(attachment(12, "lettre.pdf", AttachmentStatus.PENDING));
        List<Document> sink = new CopyOnWriteArrayList<>();

        String result = tool.getAttachmentContent(null, context(sink));

        assertTrue(result.contains("en cours de traitement"));
        assertTrue(sink.isEmpty());
    }

    @Test
    void attachmentIsDisplayedOnceAsFileSourceAndRenderedWithItsFilename() {
        var tool = tool(attachment(12, "decompte_2025.pdf", AttachmentStatus.PROCESSED));
        List<Document> sink = new CopyOnWriteArrayList<>();
        tool.getAttachmentContent(null, context(sink));
        tool.getAttachmentContent(null, context(sink));

        var consulted = sink.stream().map(ConsultedSource::from).toList();
        var outcome = SourceDisplayPolicy.decide(Optional.of(new TurnMeta(TurnStatus.ANSWER, List.of())),
                consulted, List.of(), "conv");

        assertEquals(1, outcome.displayed().size());
        var token = outcome.displayed().getFirst();
        assertEquals(SourceType.FILE, token.type());
        assertEquals("decompte_2025.pdf", token.metadata().get("filename"));
        assertEquals("personal.uploads", token.metadata().get("version"));

        String block = RetrievedSourcesRenderer.render(1, outcome.trace().sources());
        assertTrue(block.contains("collection=attachment; filename=\"decompte_2025.pdf\""), block);
        assertFalse(block.contains("title="), block);
    }

    @Test
    void attachmentReadInTheTurnIsDisplayedEvenIfLeftOutOfUsed() {
        var attachment = new ConsultedSource(ConversationAttachmentTool.documentId(12L), MetadataFormatter.COLLECTION_ATTACHMENT,
                null, "decompte_2025.pdf", null, null, null, null, "personal.uploads", null, null, List.of());
        var directive = new ConsultedSource("d", "documentation", null, "Directive", "https://x/d", "12", null, null,
                null, "TEST-DIR", "0", List.of());

        var outcome = SourceDisplayPolicy.decide(Optional.of(new TurnMeta(TurnStatus.ANSWER, List.of("d"))),
                List.of(attachment, directive), List.of(), "conv");

        assertEquals(List.of("d", attachment.id()), outcome.displayed().stream().map(t -> t.documentId()).toList());
    }

    @Test
    void attachmentIdFromAPreviousTurnCanBeReused() {
        String id = ConversationAttachmentTool.documentId(12L);
        var previous = new ConsultedSource(id, MetadataFormatter.COLLECTION_ATTACHMENT, null, "decompte_2025.pdf",
                null, null, null, null, "personal.uploads", null, null, List.of());
        var history = List.of(
                new Message(null, null, null, null, "fr", "q1", "USER", null, null, null, null),
                new Message(null, null, null, null, "fr", "a1", "LLM", List.of(), List.of(), null, null,
                        new TurnTrace(TurnStatus.ANSWER, List.of(previous))));

        var outcome = SourceDisplayPolicy.decide(Optional.of(new TurnMeta(TurnStatus.ANSWER, List.of(id))),
                List.of(), history, "conv");

        assertEquals(List.of(id), outcome.displayed().stream().map(t -> t.documentId()).toList());
    }

    @Test
    void attachmentsBlockListsFilesWithTheirStatus() {
        String block = ConversationAttachmentsRenderer.render(List.of(
                summary(1, "decompte_2025.pdf", AttachmentStatus.PROCESSED),
                summary(2, "lettre \"urgente\".pdf", AttachmentStatus.PENDING),
                summary(3, "scan.pdf", AttachmentStatus.FAILED)));

        assertEquals("""
                <conversation_attachments>
                - filename="decompte_2025.pdf"; status=ready
                - filename="lettre 'urgente'.pdf"; status=processing
                - filename="scan.pdf"; status=failed
                </conversation_attachments>""", block);
        assertEquals("", ConversationAttachmentsRenderer.render(List.of()));
    }
}
