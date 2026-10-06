package zas.admin.zec.backend.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zas.admin.zec.backend.actions.upload.model.AvailabilityStatus;
import zas.admin.zec.backend.persistence.entity.TempSourceDocumentEntity;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TempSourceDocumentRepository extends JpaRepository<TempSourceDocumentEntity, Long> {

    Optional<TempSourceDocumentEntity> findByFileName(String fileName);
    Optional<TempSourceDocumentEntity> findByFileNameAndUserUuid(String filename, String userUuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM TempSourceDocumentEntity d WHERE d.fileName = :filename AND d.userUuid = :userUuid")
    Optional<TempSourceDocumentEntity> findByFileNameAndUserUuidForUpdate(@Param("filename") String filename,
                                                                            @Param("userUuid") String userUuid);
    List<TempSourceDocumentEntity> findAllByUserUuid(String userUuid);

    List<TempSourceDocumentEntity> findAllByAvailabilityStatusAndUserUuidNotNullAndUploadedAtBefore(
            AvailabilityStatus availabilityStatus, LocalDateTime uploadedAtThreshold);

    List<TempSourceDocumentEntity> findAllByAvailabilityStatusAndUserUuidNotNullAndArchivedAtBefore(
            AvailabilityStatus availabilityStatus, LocalDateTime archivedAtThreshold);

    @Modifying
    int deleteByFileNameIn(Collection<String> fileNames);

    /**
     * Verrouille (sans attendre les lignes déjà verrouillées par une autre instance) les documents
     * en attente d'embedding dont l'échéance de polling est atteinte.
     */
    @Query(value = """
        SELECT id FROM temp_source_document
        WHERE status = 'PENDING'
          AND (next_poll_at IS NULL OR next_poll_at <= :now)
        ORDER BY next_poll_at NULLS FIRST
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Long> lockDueForEmbedding(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Query(value = """
        SELECT id FROM temp_source_document
        WHERE id = :id
          AND status = 'PENDING'
          AND (next_poll_at IS NULL OR next_poll_at <= :now)
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<Long> lockIfDueForEmbedding(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE TempSourceDocumentEntity d SET d.nextPollAt = :until WHERE d.id IN :ids")
    int leaseUntil(@Param("ids") Collection<Long> ids, @Param("until") LocalDateTime until);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM TempSourceDocumentEntity d WHERE d.id = :id")
    Optional<TempSourceDocumentEntity> findByIdForUpdate(@Param("id") Long id);

}
