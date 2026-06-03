package zas.admin.zec.backend.actions.summarize;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zas.admin.zec.backend.persistence.entity.SummaryTaskEntity;
import zas.admin.zec.backend.persistence.repository.SummaryTaskRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SummarizeServiceTest {

    @Mock
    private SummaryTaskRepository summaryTaskRepository;
    @Mock
    private SummarizeAsyncProcessor asyncProcessor;

    @InjectMocks
    private SummarizeService summarizeService;

    @Test
    @DisplayName("startSummarization returns existing task when already completed")
    void startSummarization_returnsExisting_whenAlreadyCompleted() {
        SummaryTaskEntity existing = new SummaryTaskEntity();
        existing.setId(1L);
        existing.setNavs("756.1234.5678.90");
        existing.setStatus(SummaryTaskStatus.TERMINEE);

        when(summaryTaskRepository.findByNavsAndStatus("756.1234.5678.90", SummaryTaskStatus.TERMINEE))
                .thenReturn(Optional.of(existing));

        SummaryTaskCreatedResponse result = summarizeService.startSummarization("756.1234.5678.90");

        assertEquals(1L, result.id());
        assertEquals(SummaryTaskStatus.TERMINEE, result.status());
        verify(asyncProcessor, never()).processSummarization(any());
    }

    @Test
    @DisplayName("startSummarization creates new task and triggers async processing")
    void startSummarization_createsNewTask() {
        when(summaryTaskRepository.findByNavsAndStatus(anyString(), any())).thenReturn(Optional.empty());

        SummaryTaskEntity saved = new SummaryTaskEntity();
        saved.setId(2L);
        saved.setNavs("756.0000.0000.00");
        saved.setStatus(SummaryTaskStatus.EN_COURS);
        when(summaryTaskRepository.save(any())).thenReturn(saved);

        SummaryTaskCreatedResponse result = summarizeService.startSummarization("756.0000.0000.00");

        assertEquals(2L, result.id());
        assertEquals(SummaryTaskStatus.EN_COURS, result.status());
        verify(asyncProcessor).processSummarization(2L);
    }

    @Test
    @DisplayName("restartSummarization throws when task not found")
    void restartSummarization_throwsWhenNotFound() {
        when(summaryTaskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(TaskNotFoundException.class, () -> summarizeService.restartSummarization(99L));
    }

    @Test
    @DisplayName("restartSummarization resets task and relaunches")
    void restartSummarization_resetsAndRelaunches() {
        SummaryTaskEntity task = new SummaryTaskEntity();
        task.setId(1L);
        task.setNavs("navs");
        task.setStatus(SummaryTaskStatus.ERREUR);
        task.setSummaryMarkdown("old");

        when(summaryTaskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(summaryTaskRepository.save(any())).thenReturn(task);

        SummaryTaskCreatedResponse result = summarizeService.restartSummarization(1L);

        assertEquals(SummaryTaskStatus.EN_COURS, task.getStatus());
        assertNull(task.getSummaryMarkdown());
        verify(asyncProcessor).processSummarization(1L);
    }

    @Test
    @DisplayName("getSummaryDetail throws when task not completed")
    void getSummaryDetail_throwsWhenNotCompleted() {
        SummaryTaskEntity task = new SummaryTaskEntity();
        task.setId(1L);
        task.setStatus(SummaryTaskStatus.EN_COURS);

        when(summaryTaskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThrows(TaskNotCompletedException.class, () -> summarizeService.getSummaryDetail(1L));
    }

    @Test
    @DisplayName("getSummaryDetail returns detail when completed")
    void getSummaryDetail_returnsDetail() {
        SummaryTaskEntity task = new SummaryTaskEntity();
        task.setId(1L);
        task.setNavs("navs");
        task.setStatus(SummaryTaskStatus.TERMINEE);
        task.setSummaryMarkdown("# Summary");
        task.setUpdatedAt(LocalDateTime.now());

        when(summaryTaskRepository.findById(1L)).thenReturn(Optional.of(task));

        SummaryDetailResponse result = summarizeService.getSummaryDetail(1L);

        assertEquals("# Summary", result.summaryMarkdown());
    }

    @Test
    @DisplayName("getTaskReferences returns empty list when no references")
    void getTaskReferences_returnsEmpty_whenNull() {
        SummaryTaskEntity task = new SummaryTaskEntity();
        task.setId(1L);
        task.setStatus(SummaryTaskStatus.TERMINEE);
        task.setReferences(null);

        when(summaryTaskRepository.findById(1L)).thenReturn(Optional.of(task));

        List<String> result = summarizeService.getTaskReferences(1L);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getAllTasks returns mapped list")
    void getAllTasks_returnsMappedList() {
        SummaryTaskEntity task = new SummaryTaskEntity();
        task.setId(1L);
        task.setNavs("navs");
        task.setStatus(SummaryTaskStatus.EN_COURS);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        when(summaryTaskRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(task));

        List<SummaryTaskResponse> result = summarizeService.getAllTasks();

        assertEquals(1, result.size());
        assertEquals("navs", result.get(0).navs());
    }
}



