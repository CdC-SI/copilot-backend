-- Extraction du texte des pièces jointes PDF via l'API asynchrone du pdf-ocr-pipeline (POST /jobs mode=extract
-- + polling + GET /result), sur le modèle de V16 pour temp_source_document.
-- job_id          : identifiant du job côté pipeline (NULL tant que non soumis / à resoumettre).
-- job_submitted_at: début de la tentative de soumission courante (référence du timeout global du job).
-- job_attempts    : nombre de soumissions effectuées (borne les resoumissions après job_lost / result_expired).
-- next_poll_at    : prochaine échéance de traitement par le poller (sert aussi de bail entre replicas).
--                   NULL pour les pièces jointes traitées par l'OCR LLM (images) : le poller les ignore.

ALTER TABLE attachment
    ADD COLUMN job_id VARCHAR(255),
    ADD COLUMN job_submitted_at TIMESTAMP(6),
    ADD COLUMN job_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN next_poll_at TIMESTAMP(6);

-- Les PDF restés PENDING (OCR LLM interrompu par un redémarrage) sont repris par le poller.
UPDATE attachment
SET next_poll_at = NOW()
WHERE status = 'PENDING'
  AND substring(file_bytes FROM 1 FOR 5) = '\x255044462d'::bytea;

CREATE INDEX idx_attachment_pending_poll
    ON attachment (next_poll_at)
    WHERE status = 'PENDING' AND next_poll_at IS NOT NULL;
