-- Monitoring de l'inférence de workspace pour les conversations COMPLETE.
-- Chaque ligne journalise une inférence réalisée par RAGTool lorsque l'utilisateur n'a PAS
-- spécifié de workspace (workspace inféré). Si l'utilisateur corrige ensuite le workspace et
-- relance la MÊME question, la colonne corrected_workspace est renseignée : c'est le signal que
-- l'inférence initiale n'était pas satisfaisante. Ces données servent à améliorer l'inférence
-- des questions futures.
CREATE TABLE workspace_inference (
    id                  UUID PRIMARY KEY,
    user_uuid           TEXT      NOT NULL,
    conversation_uuid   TEXT      NOT NULL,
    question            TEXT      NOT NULL,
    inferred_workspace  TEXT      NOT NULL,
    corrected_workspace TEXT,
    timestamp           TIMESTAMP NOT NULL
);

-- La recherche de la ligne initiale (cas correction) se fait sur (user, conversation, question).
CREATE INDEX idx_workspace_inference_lookup
    ON workspace_inference (user_uuid, conversation_uuid, question);
