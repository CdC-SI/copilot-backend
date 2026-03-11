package zas.admin.zec.backend.actions.sourcerequest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.persistence.entity.SourceRequestEntity;
import zas.admin.zec.backend.persistence.repository.SourceRequestRepository;

import java.util.List;

/**
 * Service de gestion des demandes de sources.
 */
@Slf4j
@Service
public class SourceRequestService {

    private final SourceRequestRepository sourceRequestRepository;

    public SourceRequestService(SourceRequestRepository sourceRequestRepository) {
        this.sourceRequestRepository = sourceRequestRepository;
    }

    /**
     * Crée une nouvelle demande de source.
     *
     * @param request les informations de la demande
     * @param requesterUsername le nom d'utilisateur du demandeur
     * @return la demande créée
     */
    @Transactional
    public SourceRequestDto createRequest(CreateSourceRequest request, String requesterUsername) {
        log.info("Creating source request '{}' for user '{}'", request.sourceName(), requesterUsername);

        var entity = new SourceRequestEntity();
        entity.setSourceName(request.sourceName());
        entity.setDescription(request.description());
        entity.setRequesterUsername(requesterUsername);
        entity.setStatus(RequestStatus.WAITING);

        var saved = sourceRequestRepository.save(entity);
        log.info("Source request created with ID: {}", saved.getId());

        return toDto(saved);
    }

    /**
     * Récupère toutes les demandes de sources (admin uniquement).
     *
     * @param limit limite optionnelle du nombre de résultats
     * @return la liste de toutes les demandes
     */
    public List<SourceRequestDto> getAllRequests(Integer limit) {
        log.debug("Fetching all source requests with limit: {}", limit);

        List<SourceRequestEntity> entities = limit != null && limit > 0
                ? sourceRequestRepository.findAllByOrderByCreatedAtDesc(Limit.of(limit))
                : sourceRequestRepository.findAllByOrderByCreatedAtDesc();

        return entities.stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Récupère les demandes d'un utilisateur spécifique.
     *
     * @param requesterUsername le nom d'utilisateur du demandeur
     * @param limit limite optionnelle du nombre de résultats
     * @return la liste des demandes de l'utilisateur
     */
    public List<SourceRequestDto> getUserRequests(String requesterUsername, Integer limit) {
        log.debug("Fetching source requests for user '{}' with limit: {}", requesterUsername, limit);

        List<SourceRequestEntity> entities = limit != null && limit > 0
                ? sourceRequestRepository.findByRequesterUsernameOrderByCreatedAtDesc(requesterUsername, Limit.of(limit))
                : sourceRequestRepository.findByRequesterUsernameOrderByCreatedAtDesc(requesterUsername);

        return entities.stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Récupère les demandes ayant un statut spécifique.
     *
     * @param status le statut recherché
     * @return la liste des demandes avec ce statut
     */
    public List<SourceRequestDto> getRequestsByStatus(RequestStatus status) {
        log.debug("Fetching source requests with status: {}", status);

        return sourceRequestRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Récupère une demande par son ID.
     * L'utilisateur doit être propriétaire de la demande ou admin.
     *
     * @param id l'identifiant de la demande
     * @param requesterUsername le nom d'utilisateur (pour vérification de propriété)
     * @return la demande
     * @throws SourceRequestNotFoundException si la demande n'existe pas ou n'appartient pas à l'utilisateur
     */
    public SourceRequestDto getById(Long id, String requesterUsername) {
        log.debug("Fetching source request {} for user '{}'", id, requesterUsername);

        return sourceRequestRepository.findByIdAndRequesterUsername(id, requesterUsername)
                .map(this::toDto)
                .orElseThrow(() -> new SourceRequestNotFoundException("Source request not found or access denied: " + id));
    }

    public void deleteById(Long id, String userName) {
        sourceRequestRepository.deleteByIdAndRequesterUsername(id, userName);
    }

    /**
     * Récupère une demande par son ID (admin uniquement, sans vérification de propriété).
     *
     * @param id l'identifiant de la demande
     * @return la demande
     * @throws SourceRequestNotFoundException si la demande n'existe pas
     */
    public SourceRequestDto getByIdAdmin(Long id) {
        log.debug("Admin fetching source request {}", id);

        return sourceRequestRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new SourceRequestNotFoundException("Source request not found: " + id));
    }

    /**
     * Met à jour le statut d'une demande (admin uniquement).
     * Vérifie que la transition de statut est valide.
     *
     * @param id l'identifiant de la demande
     * @param newStatus le nouveau statut
     * @return la demande mise à jour
     * @throws SourceRequestNotFoundException si la demande n'existe pas
     * @throws InvalidStatusTransitionException si la transition n'est pas valide
     */
    @Transactional
    public SourceRequestDto updateStatus(Long id, RequestStatus newStatus) {
        log.info("Updating status of source request {} to {}", id, newStatus);

        var entity = sourceRequestRepository.findById(id)
                .orElseThrow(() -> new SourceRequestNotFoundException("Source request not found: " + id));

        if (!entity.getStatus().canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(
                    "Invalid status transition from " + entity.getStatus() + " to " + newStatus);
        }

        entity.setStatus(newStatus);
        var saved = sourceRequestRepository.save(entity);

        log.info("Source request {} status updated to {}", id, newStatus);
        return toDto(saved);
    }

    /**
     * Convertit une entité en DTO.
     */
    private SourceRequestDto toDto(SourceRequestEntity entity) {
        return new SourceRequestDto(
                entity.getId(),
                entity.getSourceName(),
                entity.getDescription(),
                entity.getRequesterUsername(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
