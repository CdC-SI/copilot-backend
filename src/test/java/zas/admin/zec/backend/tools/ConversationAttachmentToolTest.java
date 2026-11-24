package zas.admin.zec.backend.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Sinks;
import zas.admin.zec.backend.actions.converse.AttachmentStatus;
import zas.admin.zec.backend.persistence.entity.AttachmentEntity;
import zas.admin.zec.backend.persistence.repository.AttachmentRepository;
import zas.admin.zec.backend.rag.CitationRefs;
import zas.admin.zec.backend.rag.token.StatusToken;
import zas.admin.zec.backend.rag.token.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tools de pièces jointes : liste, lecture (choix du fichier), dépôt du document lu et référence de citation. */
class ConversationAttachmentToolTest {

    private final AttachmentRepository repository = mock(AttachmentRepository.class);
    private final ConversationAttachmentTool tool = new ConversationAttachmentTool(repository);
    private final List<Document> retrieved = new ArrayList<>();
    private final CitationRefs citationRefs = new CitationRefs();
    private final List<Token> emitted = new CopyOnWriteArrayList<>();

    private ToolContext context() {
        Sinks.Many<Token> sink = Sinks.many().unicast().onBackpressureBuffer();
        sink.asFlux().subscribe(emitted::add);
        Map<String, Object> context = new HashMap<>();
        context.put(ToolContextKeys.CTX_CONVERSATION_ID, "conv");
        context.put(ToolContextKeys.CTX_USER_ID, "user");
        context.put(ToolContextKeys.CTX_LANGUAGE, "de");
        context.put(ToolContextKeys.CTX_STATUS_SINK, sink);
        context.put(ToolContextKeys.CTX_RETRIEVED_DOCUMENTS, retrieved);
        context.put(ToolContextKeys.CTX_CITATION_REFS, citationRefs);
        return new ToolContext(context);
    }

    private void attachments(AttachmentEntity... entities) {
        when(repository.findAllByConversationIdAndUserId("conv", "user")).thenReturn(List.of(entities));
    }

    private static AttachmentEntity attachment(long id, String filename, AttachmentStatus status, String content) {
        var entity = new AttachmentEntity();
        entity.setId(id);
        entity.setFilename(filename);
        entity.setStatus(status);
        entity.setContent(content);
        return entity;
    }

    @Test
    void listShowsTheFilenamesWithTheirStatusAndEmitsAStatus() {
        attachments(attachment(1, "rapport.pdf", AttachmentStatus.PROCESSED, "x"),
                attachment(2, "scan.png", AttachmentStatus.PENDING, null));

        String result = tool.listConversationAttachments(context());

        assertEquals("Fichiers attachés à cette conversation :\n- rapport.pdf [Traitement terminé]\n"
                + "- scan.png [En cours de traitement OCR]", result);
        assertInstanceOf(StatusToken.class, emitted.getFirst());
    }

    @Test
    void withoutConversationContextNothingIsRead() {
        assertEquals("Aucune pièce jointe disponible (contexte de conversation manquant).",
                tool.listConversationAttachments(null));
        assertEquals("Impossible de récupérer la pièce jointe : contexte de conversation manquant.",
                tool.getAttachmentContent("a.pdf", new ToolContext(Map.of(ToolContextKeys.CTX_USER_ID, "user"))));
        verifyNoInteractions(repository);
    }

    @Test
    void conversationWithoutAttachments() {
        attachments();

        assertEquals("Aucune pièce jointe n'est attachée à cette conversation.", tool.listConversationAttachments(context()));
        assertEquals("Aucune pièce jointe n'est attachée à cette conversation.", tool.getAttachmentContent(null, context()));
    }

    @Test
    void exhaustedBudgetStopsBothTools() {
        Map<String, Object> context = Map.of(ToolContextKeys.CTX_TOOL_CALL_BUDGET, new AtomicInteger(0));

        assertEquals(ToolCallBudget.EXHAUSTED_MESSAGE, tool.listConversationAttachments(new ToolContext(context)));
        assertEquals(ToolCallBudget.EXHAUSTED_MESSAGE, tool.getAttachmentContent(null, new ToolContext(context)));
        verifyNoInteractions(repository);
    }

    @Test
    void singleProcessedAttachmentIsReadDepositedAndGivenACitationRef() {
        attachments(attachment(7, "rapport.pdf", AttachmentStatus.PROCESSED, "Contenu OCR"));
        String id = ConversationAttachmentTool.documentId(7L);

        String result = tool.getAttachmentContent(" ", context());

        assertEquals("<attachment id=\"%s\" ref=\"1\" filename=\"rapport.pdf\">%nContenu OCR%n</attachment>".formatted(id),
                result);
        assertEquals(1, retrieved.size());
        var document = retrieved.getFirst();
        assertEquals(id, document.getId());
        assertEquals("Contenu OCR", document.getText());
        assertEquals("rapport.pdf", document.getMetadata().get("title"));
        assertEquals("personal.uploads", document.getMetadata().get("state"));
        assertEquals(MetadataFormatter.COLLECTION_ATTACHMENT, document.getMetadata().get(MetadataFormatter.META_COLLECTION));
        assertEquals(Map.of(1, id), citationRefs.table());
    }

    @Test
    void documentIdIsStablePerAttachment() {
        assertEquals(ConversationAttachmentTool.documentId(7L), ConversationAttachmentTool.documentId(7L));
        assertNotEquals(ConversationAttachmentTool.documentId(7L), ConversationAttachmentTool.documentId(8L));
    }

    @Test
    void pendingOrFailedAttachmentIsNotDeposited() {
        attachments(attachment(1, "scan.png", AttachmentStatus.PENDING, null),
                attachment(2, "broken.pdf", AttachmentStatus.FAILED, null));

        assertTrue(tool.getAttachmentContent("scan", context()).contains("encore en cours de traitement OCR"));
        assertTrue(tool.getAttachmentContent("BROKEN", context()).contains("a échoué"));
        assertTrue(retrieved.isEmpty());
        assertTrue(citationRefs.table().isEmpty());
    }

    @Test
    void filenameHintIsMatchedCaseInsensitivelyAndPartially() {
        attachments(attachment(1, "Rapport-2025.pdf", AttachmentStatus.PROCESSED, "A"),
                attachment(2, "Rapport-2026.pdf", AttachmentStatus.PROCESSED, "B"),
                attachment(3, "facture.pdf", AttachmentStatus.PROCESSED, "C"));

        assertTrue(tool.getAttachmentContent("  rapport-2026 ", context()).contains("filename=\"Rapport-2026.pdf\">"));
        assertEquals("Plusieurs fichiers correspondent à « rapport » : Rapport-2025.pdf, Rapport-2026.pdf. "
                + "Précisez le nom exact et appelez à nouveau ce tool.", tool.getAttachmentContent("rapport", context()));
        assertEquals("Aucun fichier nommé « budget.xlsx » n'est attaché à cette conversation.",
                tool.getAttachmentContent("budget.xlsx", context()));
        assertTrue(tool.getAttachmentContent(null, context()).startsWith(
                "Plusieurs fichiers sont attachés à cette conversation : Rapport-2025.pdf, Rapport-2026.pdf, facture.pdf."));
        assertEquals(List.of(ConversationAttachmentTool.documentId(2L)), retrieved.stream().map(Document::getId).toList());
    }
}
