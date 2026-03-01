package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import zas.admin.zec.backend.actions.sourcerequest.RequestStatus;
import zas.admin.zec.backend.persistence.entity.SourceRequestEntity;

import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'accès aux demandes de sources.
 */
public interface SourceRequestRepository extends JpaRepository<SourceRequestEntity, Long> {

    /**
     * Récupère toutes les demandes d'un utilisateur, triées par date de création décroissante.
     *
     * @param requesterUsername le nom d'utilisateur du demandeur
     * @return la liste des demandes de l'utilisateur
     */
    List<SourceRequestEntity> findByRequesterUsernameOrderByCreatedAtDesc(String requesterUsername);

    /**
     * Récupère toutes les demandes d'un utilisateur avec limitation, triées par date de création décroissante.
     *
     * @param requesterUsername le nom d'utilisateur du demandeur
     * @param limit le nombre maximum de résultats
     * @return la liste des demandes de l'utilisateur
     */
    List<SourceRequestEntity> findByRequesterUsernameOrderByCreatedAtDesc(String requesterUsername, Limit limit);

    /**
     * Récupère toutes les demandes ayant un statut donné, triées par date de création décroissante.
     *
     * @param status le statut recherché
     * @return la liste des demandes avec ce statut
     */
    List<SourceRequestEntity> findByStatusOrderByCreatedAtDesc(RequestStatus status);

    /**
     * Récupère toutes les demandes, triées par date de création décroissante.
     *
     * @return la liste de toutes les demandes
     */
    List<SourceRequestEntity> findAllByOrderByCreatedAtDesc();

    /**
     * Récupère toutes les demandes avec limitation, triées par date de création décroissante.
     *
     * @param limit le nombre maximum de résultats
     * @return la liste de toutes les demandes
     */
    List<SourceRequestEntity> findAllByOrderByCreatedAtDesc(Limit limit);

    /**
     * Récupère une demande par son ID et le nom d'utilisateur du demandeur.
     * Utilisé pour vérifier que l'utilisateur est bien le propriétaire de la demande.
     *
     * @param id l'identifiant de la demande
     * @param requesterUsername le nom d'utilisateur du demandeur
     * @return la demande si trouvée et appartenant à l'utilisateur
     */
    Optional<SourceRequestEntity> findByIdAndRequesterUsername(Long id, String requesterUsername);

    void deleteByIdAndRequesterUsername(Long id, String userName);
}
