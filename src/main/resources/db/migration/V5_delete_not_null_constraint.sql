-- user_uuid peut être null pour les documents sources ajoutés par les admins.

ALTER TABLE temp_source_document
    ALTER COLUMN user_uuid DROP NOT NULL;