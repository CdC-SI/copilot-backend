package zas.admin.zec.backend.actions.sourcerequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import zas.admin.zec.backend.persistence.entity.SourceRequestEntity;
import zas.admin.zec.backend.persistence.repository.SourceRequestRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceRequestServiceTest {

    @Mock
    private SourceRequestRepository sourceRequestRepository;

    @InjectMocks
    private SourceRequestService sourceRequestService;

    private SourceRequestEntity createEntity(Long id, String sourceName, RequestStatus status) {
        var entity = new SourceRequestEntity();
        entity.setId(id);
        entity.setSourceName(sourceName);
        entity.setDescription("desc");
        entity.setRequesterUsername("user1");
        entity.setStatus(status);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    @Test
    @DisplayName("createRequest saves entity with WAITING status")
    void createRequest_savesWithWaitingStatus() {
        var request = new CreateSourceRequest("Source A", "Description A");
        var saved = createEntity(1L, "Source A", RequestStatus.WAITING);
        when(sourceRequestRepository.save(any())).thenReturn(saved);

        SourceRequestDto result = sourceRequestService.createRequest(request, "user1");

        assertEquals("Source A", result.sourceName());
        assertEquals(RequestStatus.WAITING, result.status());

        ArgumentCaptor<SourceRequestEntity> captor = ArgumentCaptor.forClass(SourceRequestEntity.class);
        verify(sourceRequestRepository).save(captor.capture());
        assertEquals(RequestStatus.WAITING, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("getAllRequests returns all with no limit")
    void getAllRequests_returnsAll() {
        when(sourceRequestRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(createEntity(1L, "S1", RequestStatus.WAITING)));

        List<SourceRequestDto> result = sourceRequestService.getAllRequests(null);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getAllRequests applies limit when provided")
    void getAllRequests_appliesLimit() {
        when(sourceRequestRepository.findAllByOrderByCreatedAtDesc(any(Limit.class)))
                .thenReturn(List.of(createEntity(1L, "S1", RequestStatus.WAITING)));

        List<SourceRequestDto> result = sourceRequestService.getAllRequests(5);

        assertEquals(1, result.size());
        verify(sourceRequestRepository).findAllByOrderByCreatedAtDesc(Limit.of(5));
    }

    @Test
    @DisplayName("getById throws when not found")
    void getById_throwsWhenNotFound() {
        when(sourceRequestRepository.findByIdAndRequesterUsername(99L, "user1")).thenReturn(Optional.empty());

        assertThrows(SourceRequestNotFoundException.class,
                () -> sourceRequestService.getById(99L, "user1"));
    }

    @Test
    @DisplayName("updateStatus throws on invalid transition")
    void updateStatus_throwsOnInvalidTransition() {
        var entity = createEntity(1L, "S1", RequestStatus.INTEGRATED);
        when(sourceRequestRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertThrows(InvalidStatusTransitionException.class,
                () -> sourceRequestService.updateStatus(1L, RequestStatus.WAITING));
    }

    @Test
    @DisplayName("updateStatus succeeds on valid transition")
    void updateStatus_succeedsOnValidTransition() {
        var entity = createEntity(1L, "S1", RequestStatus.WAITING);
        when(sourceRequestRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(sourceRequestRepository.save(any())).thenReturn(entity);

        SourceRequestDto result = sourceRequestService.updateStatus(1L, RequestStatus.PROCESSING);

        assertEquals(RequestStatus.PROCESSING, entity.getStatus());
        assertEquals(RequestStatus.PROCESSING, result.status());
    }

    @Test
    @DisplayName("updateStatus throws when the request does not exist")
    void updateStatus_throwsWhenNotFound() {
        when(sourceRequestRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(SourceRequestNotFoundException.class,
                () -> sourceRequestService.updateStatus(1L, RequestStatus.PROCESSING));
        verify(sourceRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("getUserRequests is limited to the requester, with or without limit")
    void getUserRequests_filtersByRequester() {
        when(sourceRequestRepository.findByRequesterUsernameOrderByCreatedAtDesc("JDU"))
                .thenReturn(List.of(createEntity(1L, "S1", RequestStatus.WAITING), createEntity(2L, "S2", RequestStatus.INTEGRATED)));
        when(sourceRequestRepository.findByRequesterUsernameOrderByCreatedAtDesc("JDU", Limit.of(1)))
                .thenReturn(List.of(createEntity(1L, "S1", RequestStatus.WAITING)));

        assertEquals(2, sourceRequestService.getUserRequests("JDU", null).size());
        assertEquals(2, sourceRequestService.getUserRequests("JDU", 0).size());
        assertEquals(List.of("S1"), sourceRequestService.getUserRequests("JDU", 1).stream().map(SourceRequestDto::sourceName).toList());
    }

    @Test
    @DisplayName("getRequestsByStatus maps every entity")
    void getRequestsByStatus_mapsEntities() {
        when(sourceRequestRepository.findByStatusOrderByCreatedAtDesc(RequestStatus.PROCESSING))
                .thenReturn(List.of(createEntity(3L, "S3", RequestStatus.PROCESSING)));

        var result = sourceRequestService.getRequestsByStatus(RequestStatus.PROCESSING);

        assertEquals(1, result.size());
        assertEquals("user1", result.getFirst().requesterUsername());
        assertEquals(3L, result.getFirst().id());
    }

    @Test
    @DisplayName("getById returns the request owned by the user")
    void getById_returnsOwnedRequest() {
        when(sourceRequestRepository.findByIdAndRequesterUsername(1L, "user1"))
                .thenReturn(Optional.of(createEntity(1L, "S1", RequestStatus.WAITING)));

        assertEquals("S1", sourceRequestService.getById(1L, "user1").sourceName());
    }

    @Test
    @DisplayName("getByIdAdmin ignores ownership and throws when missing")
    void getByIdAdmin_ignoresOwnership() {
        when(sourceRequestRepository.findById(1L)).thenReturn(Optional.of(createEntity(1L, "S1", RequestStatus.WAITING)));
        when(sourceRequestRepository.findById(2L)).thenReturn(Optional.empty());

        assertEquals("S1", sourceRequestService.getByIdAdmin(1L).sourceName());
        assertThrows(SourceRequestNotFoundException.class, () -> sourceRequestService.getByIdAdmin(2L));
    }

    @Test
    @DisplayName("deleteById only deletes requests of the user")
    void deleteById_restrictsToRequester() {
        sourceRequestService.deleteById(1L, "JDU");

        verify(sourceRequestRepository).deleteByIdAndRequesterUsername(1L, "JDU");
    }

    @Test
    @DisplayName("status transitions: INTEGRATED is terminal, same status is always allowed")
    void requestStatus_transitions() {
        assertTrue(RequestStatus.WAITING.canTransitionTo(RequestStatus.INTEGRATED));
        assertTrue(RequestStatus.PROCESSING.canTransitionTo(RequestStatus.WAITING));
        assertTrue(RequestStatus.INTEGRATED.canTransitionTo(RequestStatus.INTEGRATED));
        assertFalse(RequestStatus.INTEGRATED.canTransitionTo(RequestStatus.PROCESSING));
    }
}

