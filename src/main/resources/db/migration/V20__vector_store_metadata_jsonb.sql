-- Passage de vector_store.metadata de JSON à JSONB.
-- Aucune ré-indexation des embeddings n'est nécessaire : la table est réécrite et les index existants
-- (HNSW, BM25) reconstruits par PostgreSQL, mais contenu et vecteurs restent inchangés.
-- JSONB permet l'opérateur de containment (@>) et les index GIN utilisés par DocumentTool ; les filtres
-- PgVector / BM25 (metadata::jsonb @@ jsonpath) deviennent sans conversion.

ALTER TABLE vector_store
    ALTER COLUMN metadata TYPE jsonb USING metadata::jsonb;

-- Sert les égalités de DocumentTool (EQ / IN traduits en metadata @> {...}) : doc_id, process_id, doc_type...
CREATE INDEX IF NOT EXISTS idx_vector_store_metadata_gin
    ON vector_store USING gin (metadata jsonb_path_ops);
