-- Les pièces jointes existantes ont déjà leur contenu OCR : statut PROCESSED par défaut.
-- Le contenu devient nullable pour les nouvelles pièces jointes en attente d'OCR (PENDING).

ALTER TABLE attachment
    ALTER COLUMN content DROP NOT NULL;

ALTER TABLE attachment
    ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'PROCESSED';

