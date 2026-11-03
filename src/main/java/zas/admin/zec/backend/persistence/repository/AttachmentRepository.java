package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zas.admin.zec.backend.actions.converse.AttachmentStatus;
import zas.admin.zec.backend.actions.upload.validation.UploadErrorCode;
import zas.admin.zec.backend.persistence.entity.AttachmentEntity;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<AttachmentEntity, Long> {
    Optional<AttachmentEntity> findByIdAndConversationIdAndUserId(Long attachmentId, String conversationId, String userId);
    List<AttachmentEntity> findAllByConversationIdAndUserId(String conversationId, String userId);
    List<AttachmentEntity> findByConversationIdAndUserId(String conversationId, String userId);
    void deleteByIdAndUserId(Long attachmentId, String userId);
    void deleteByUserIdAndConversationId(String userId, String conversationId);

    /** Liste légère des pièces jointes (sans contenu OCR ni octets du fichier), dans l'ordre d'upload. */
    List<AttachmentSummary> findSummariesByConversationIdAndUserIdOrderByIdAsc(String conversationId, String userId);

    /** Projection : seules ces colonnes sont lues. */
    interface AttachmentSummary {
        Long getId();
        String getFilename();
        AttachmentStatus getStatus();
    }

    // --- Extraction asynchrone des PDF via le pdf-ocr-pipeline (cf. AttachmentJobProcessor) ---
    // Les lectures / écritures du traitement ne chargent jamais file_bytes (jusqu'à 20 Mo) hors soumission.

    /**
     * Verrouille (sans attendre les lignes déjà verrouillées par une autre instance) les pièces jointes
     * en attente d'extraction par le pipeline dont l'échéance de polling est atteinte.
     * Les pièces jointes sans {@code next_poll_at} (OCR LLM) sont ignorées.
     */
    @Query(value = """
        SELECT id FROM attachment
        WHERE status = 'PENDING'
          AND next_poll_at IS NOT NULL
          AND next_poll_at <= :now
        ORDER BY next_poll_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Long> lockDueForPipeline(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Query(value = """
        SELECT id FROM attachment
        WHERE id = :id
          AND status = 'PENDING'
          AND next_poll_at IS NOT NULL
          AND next_poll_at <= :now
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<Long> lockIfDueForPipeline(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE AttachmentEntity a SET a.nextPollAt = :until WHERE a.id IN :ids")
    int leaseUntil(@Param("ids") Collection<Long> ids, @Param("until") LocalDateTime until);

    @Query("""
        SELECT a.id AS id, a.jobId AS jobId, a.userId AS userId, a.filename AS filename,
               a.jobAttempts AS jobAttempts, a.jobSubmittedAt AS jobSubmittedAt
        FROM AttachmentEntity a
        WHERE a.id = :id AND a.status = zas.admin.zec.backend.actions.converse.AttachmentStatus.PENDING
        """)
    Optional<AttachmentJob> findPendingJob(@Param("id") Long id);

    @Query("SELECT a.fileBytes FROM AttachmentEntity a WHERE a.id = :id")
    Optional<byte[]> findFileBytesById(@Param("id") Long id);

    /** Jobs pipeline en cours d'une pièce jointe (annulation à la suppression). */
    @Query("""
        SELECT a.jobId FROM AttachmentEntity a
        WHERE a.id = :id AND a.userId = :userId AND a.jobId IS NOT NULL
          AND a.status = zas.admin.zec.backend.actions.converse.AttachmentStatus.PENDING
        """)
    List<String> findPendingJobIds(@Param("id") Long id, @Param("userId") String userId);

    /** Jobs pipeline en cours des pièces jointes d'une conversation (annulation à la suppression). */
    @Query("""
        SELECT a.jobId FROM AttachmentEntity a
        WHERE a.userId = :userId AND a.conversationId = :conversationId AND a.jobId IS NOT NULL
          AND a.status = zas.admin.zec.backend.actions.converse.AttachmentStatus.PENDING
        """)
    List<String> findPendingJobIdsByConversation(@Param("userId") String userId,
                                                 @Param("conversationId") String conversationId);

    /** Replanifie une pièce jointe toujours en attente et associée au job attendu. */
    @Modifying
    @Query("""
        UPDATE AttachmentEntity a SET a.nextPollAt = :at
        WHERE a.id = :id AND a.status = zas.admin.zec.backend.actions.converse.AttachmentStatus.PENDING
          AND (a.jobId = :jobId OR (:jobId IS NULL AND a.jobId IS NULL))
        """)
    int reschedulePending(@Param("id") Long id, @Param("jobId") String jobId, @Param("at") LocalDateTime at);

    /** Échec transitoire de soumission : démarre le chronomètre du timeout et replanifie. */
    @Modifying
    @Query("""
        UPDATE AttachmentEntity a
        SET a.jobSubmittedAt = COALESCE(a.jobSubmittedAt, :now), a.nextPollAt = :at
        WHERE a.id = :id AND a.status = zas.admin.zec.backend.actions.converse.AttachmentStatus.PENDING
          AND a.jobId IS NULL
        """)
    int markSubmitRetry(@Param("id") Long id, @Param("now") LocalDateTime now, @Param("at") LocalDateTime at);

    /** Enregistre le job soumis, uniquement si la pièce jointe est toujours en attente et non soumise. */
    @Modifying
    @Query("""
        UPDATE AttachmentEntity a
        SET a.jobId = :jobId, a.jobSubmittedAt = :now, a.jobAttempts = a.jobAttempts + 1, a.nextPollAt = :at
        WHERE a.id = :id AND a.status = zas.admin.zec.backend.actions.converse.AttachmentStatus.PENDING
          AND a.jobId IS NULL
        """)
    int recordSubmission(@Param("id") Long id, @Param("jobId") String jobId,
                         @Param("now") LocalDateTime now, @Param("at") LocalDateTime at);

    /** Job perdu côté pipeline : remise à zéro pour resoumission immédiate. */
    @Modifying
    @Query("""
        UPDATE AttachmentEntity a SET a.jobId = NULL, a.jobSubmittedAt = NULL, a.nextPollAt = :now
        WHERE a.id = :id AND a.status = zas.admin.zec.backend.actions.converse.AttachmentStatus.PENDING
          AND a.jobId = :jobId
        """)
    int resetJob(@Param("id") Long id, @Param("jobId") String jobId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
        UPDATE AttachmentEntity a
        SET a.status = zas.admin.zec.backend.actions.converse.AttachmentStatus.FAILED,
            a.errorCode = :errorCode, a.nextPollAt = NULL
        WHERE a.id = :id AND a.status = zas.admin.zec.backend.actions.converse.AttachmentStatus.PENDING
          AND (a.jobId = :jobId OR (:jobId IS NULL AND a.jobId IS NULL))
        """)
    int markFailed(@Param("id") Long id, @Param("jobId") String jobId, @Param("errorCode") UploadErrorCode errorCode);

    @Modifying
    @Query("""
        UPDATE AttachmentEntity a
        SET a.status = zas.admin.zec.backend.actions.converse.AttachmentStatus.PROCESSED,
            a.content = :content, a.nextPollAt = NULL
        WHERE a.id = :id AND a.status = zas.admin.zec.backend.actions.converse.AttachmentStatus.PENDING
          AND a.jobId = :jobId
        """)
    int markProcessed(@Param("id") Long id, @Param("jobId") String jobId, @Param("content") String content);

    /** Projection de l'état du job pipeline d'une pièce jointe (sans contenu ni octets). */
    interface AttachmentJob {
        Long getId();
        String getJobId();
        String getUserId();
        String getFilename();
        int getJobAttempts();
        LocalDateTime getJobSubmittedAt();
    }
}
