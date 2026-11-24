package zas.admin.zec.backend.tools;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zas.admin.zec.backend.persistence.entity.WorkspaceInferenceEntity;
import zas.admin.zec.backend.persistence.repository.WorkspaceInferenceRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Journal des inférences de workspace et de leurs corrections par l'utilisateur. */
class WorkspaceInferenceMonitoringServiceTest {

    private static final String QUESTION = "Quel est le montant de la rente ?";

    private final WorkspaceInferenceRepository repository = mock(WorkspaceInferenceRepository.class);
    private final WorkspaceInferenceMonitoringService service = new WorkspaceInferenceMonitoringService(repository);

    @Test
    void inferenceIsRecordedOncePerQuestion() {
        when(repository.existsByUserUuidAndConversationUuidAndQuestion("u1", "c1", QUESTION)).thenReturn(false, true);

        service.recordInference("u1", "c1", QUESTION, "AVS");
        service.recordInference("u1", "c1", QUESTION, "RH");

        var captor = ArgumentCaptor.forClass(WorkspaceInferenceEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("u1", captor.getValue().getUserUuid());
        assertEquals("c1", captor.getValue().getConversationUuid());
        assertEquals(QUESTION, captor.getValue().getQuestion());
        assertEquals("AVS", captor.getValue().getInferredWorkspace());
        assertNotNull(captor.getValue().getTimestamp());
    }

    @Test
    void incompleteInferenceOrCorrectionIsIgnored() {
        service.recordInference(null, "c1", QUESTION, "AVS");
        service.recordInference("u1", " ", QUESTION, "AVS");
        service.recordInference("u1", "c1", "", "AVS");
        service.recordInference("u1", "c1", QUESTION, null);
        service.recordCorrection(null, "c1", QUESTION, "RH");
        service.recordCorrection("u1", null, QUESTION, "RH");
        service.recordCorrection("u1", "c1", null, "RH");
        service.recordCorrection("u1", "c1", QUESTION, " ");

        verifyNoInteractions(repository);
    }

    @Test
    void correctionUpdatesTheInitialInference() {
        var entity = new WorkspaceInferenceEntity();
        entity.setInferredWorkspace("AVS");
        when(repository.findFirstByUserUuidAndConversationUuidAndQuestionOrderByTimestampDesc("u1", "c1", QUESTION))
                .thenReturn(Optional.of(entity));

        service.recordCorrection("u1", "c1", QUESTION, "RH");

        assertEquals("RH", entity.getCorrectedWorkspace());
        verify(repository).save(entity);
    }

    @Test
    void correctionWithoutInitialInferenceIsIgnored() {
        when(repository.findFirstByUserUuidAndConversationUuidAndQuestionOrderByTimestampDesc("u1", "c1", QUESTION))
                .thenReturn(Optional.empty());

        service.recordCorrection("u1", "c1", QUESTION, "RH");

        verify(repository, never()).save(any());
    }

    @Test
    void databaseErrorsNeverInterruptTheConversation() {
        when(repository.existsByUserUuidAndConversationUuidAndQuestion(any(), any(), any()))
                .thenThrow(new IllegalStateException("db down"));
        when(repository.findFirstByUserUuidAndConversationUuidAndQuestionOrderByTimestampDesc(any(), any(), any()))
                .thenThrow(new IllegalStateException("db down"));

        assertDoesNotThrow(() -> service.recordInference("u1", "c1", QUESTION, "AVS"));
        assertDoesNotThrow(() -> service.recordCorrection("u1", "c1", QUESTION, "RH"));
    }
}
