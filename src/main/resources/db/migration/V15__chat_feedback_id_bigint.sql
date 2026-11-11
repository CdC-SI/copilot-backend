-- Aligne le type de la colonne id de chat_feedback sur BIGINT, comme source_feedback,
-- afin que MessageFeedbackEntity et SourceFeedbackEntity partagent le même type d'identifiant (Long).
ALTER TABLE chat_feedback
    ALTER COLUMN id TYPE BIGINT;
ALTER SEQUENCE chat_feedback_id_seq AS BIGINT;
