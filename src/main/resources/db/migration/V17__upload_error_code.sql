-- Cause d'échec du traitement asynchrone (embedding des documents personnels, OCR des pièces jointes),
-- renseignée uniquement lorsque le statut passe à FAILED et renvoyée au client (cf. UploadErrorCode).

ALTER TABLE temp_source_document
    ADD COLUMN error_code VARCHAR(64);

ALTER TABLE attachment
    ADD COLUMN error_code VARCHAR(64);
