package zas.admin.zec.backend.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.actions.workspace.WorkspaceService;
import zas.admin.zec.backend.config.properties.RetrievingProperties;
import zas.admin.zec.backend.rag.reranker.DocumentReranker;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/** Appel du tool de recherche sans requête (argument omis par le LLM). */
class RAGToolTest {

    @Test
    void missingQueryReturnsAnErrorMessageInsteadOfThrowing() {
        var vectorStore = mock(VectorStore.class);
        var tool = new RAGTool(mock(ChatModel.class), vectorStore, mock(UserService.class), mock(DocumentReranker.class),
                mock(RetrievingProperties.class), mock(JdbcTemplate.class), mock(SourceResolver.class),
                mock(WorkspaceService.class), mock(WorkspaceInferenceMonitoringService.class));

        assertEquals(RAGTool.MISSING_QUERY, tool.searchSocialInsuranceDocumentation(null, new ToolContext(new HashMap<>())));
        assertEquals(RAGTool.MISSING_QUERY, tool.searchSocialInsuranceDocumentation("  ", new ToolContext(new HashMap<>())));
        verifyNoInteractions(vectorStore);
    }
}
