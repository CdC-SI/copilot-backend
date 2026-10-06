-- Migration vers l'API asynchrone du service d'embedding (POST /jobs + polling + GET /result).
-- job_id          : identifiant du job côté service externe (NULL tant que non soumis / à resoumettre).
-- job_submitted_at: début de la tentative de soumission courante (référence du timeout global du job).
-- job_attempts    : nombre de soumissions effectuées (borne les resoumissions après job_lost / result_expired).
-- next_poll_at    : prochaine échéance de traitement par le poller (sert aussi de bail entre replicas).

ALTER TABLE temp_source_document
    ADD COLUMN job_id VARCHAR(255),
    ADD COLUMN job_submitted_at TIMESTAMP(6),
    ADD COLUMN job_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN next_poll_at TIMESTAMP(6);

-- Les documents restés PENDING (appel legacy interrompu) sont repris par le poller.
UPDATE temp_source_document
SET next_poll_at = NOW()
WHERE status = 'PENDING';

CREATE INDEX idx_temp_source_document_pending_poll
    ON temp_source_document (next_poll_at)
    WHERE status = 'PENDING';
