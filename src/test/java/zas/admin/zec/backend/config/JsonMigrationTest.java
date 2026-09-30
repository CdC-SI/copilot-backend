package zas.admin.zec.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.json.JsonMapper;
import zas.admin.zec.backend.actions.converse.ConversationTitleUpdate;
import zas.admin.zec.backend.actions.summarize.jms.GaimeJmsService;
import zas.admin.zec.backend.actions.upload.model.DocumentToUpload;
import zas.admin.zec.backend.actions.upload.strategy.EmbeddedDocUploadStrategy;
import zas.admin.zec.backend.actions.upload.validation.UploadException;
import zas.admin.zec.backend.persistence.entity.DocumentEntity;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;
import zas.admin.zec.backend.persistence.repository.QuestionRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static zas.admin.zec.backend.actions.summarize.jms.JmsConstants.GAIME_MESSAGE;
import static zas.admin.zec.backend.actions.summarize.jms.JmsConstants.OPEN_DOCUMENTS_ACTION;

class JsonMigrationTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void titleAcceptsBothExistingJsonNames() {
        for (String name : List.of("newTitle", "new_title")) {
            assertEquals("title", mapper.readValue("{\"" + name + "\":\"title\"}",
                    ConversationTitleUpdate.class).newTitle());
        }
    }

    @Test
    void jmsKeepsItsPrefixPayloadAndNullOmission() {
        var jms = mock(JmsTemplate.class);
        var message = new AtomicReference<String>();
        doAnswer(invocation -> {
            message.set(invocation.getArgument(0));
            return null;
        }).when(jms).convertAndSend(anyString());

        new GaimeJmsService(jms, mapper).sendOpenDocumentsMessage("ABC", List.of("doc-1"));

        assertTrue(message.get().startsWith(GAIME_MESSAGE));
        var payload = mapper.readTree(message.get().substring(GAIME_MESSAGE.length()));
        assertEquals(OPEN_DOCUMENTS_ACTION, payload.path("action").asText());
        assertEquals("ABC", payload.path("visa").asText());
        assertEquals("doc-1", payload.path("objTokens").get(0).asText());
        assertFalse(payload.has("objToken"));
        assertFalse(payload.has("folderId"));
    }

    @Test
    void csvStillAcceptsSingleQuotedMetadata() {
        var documents = mock(DocumentRepository.class);
        var saved = new AtomicReference<DocumentEntity>();
        when(documents.saveAll(anyList())).thenAnswer(invocation -> {
            List<DocumentEntity> batch = invocation.getArgument(0);
            saved.set(batch.getFirst());
            return batch;
        });
        var strategy = new EmbeddedDocUploadStrategy(documents, mock(QuestionRepository.class));

        strategy.upload(csv("text,{'language':'fr'},\"0.1,0.2\""));

        assertEquals("fr", saved.get().getMetadata().get("language"));
        assertArrayEquals(new float[]{0.1f, 0.2f}, saved.get().getEmbedding());
    }

    @Test
    void invalidCsvJsonStillRaisesUploadException() {
        var strategy = new EmbeddedDocUploadStrategy(mock(DocumentRepository.class), mock(QuestionRepository.class));

        assertThrows(UploadException.class, () -> strategy.upload(csv("text,not-json,0.1")));
    }

    private static DocumentToUpload csv(String row) {
        byte[] bytes = ("content,metadata,embedding\n" + row).getBytes(StandardCharsets.UTF_8);
        return new DocumentToUpload(new MockMultipartFile("file", "data.csv", "text/csv", bytes));
    }
}
