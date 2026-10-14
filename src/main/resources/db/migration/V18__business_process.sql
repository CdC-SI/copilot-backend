-- Processus métier internes (BPMN), un processus versionné par ligne, non chunké.
-- metadata : bpmn_id, process_id, process_name, process_state, purpose, bpanda_version, valid_from,
--            valid_to, bpanda_id, bpanda_link, n_nodes, n_arcs, lang, organizations
-- content  : définition du processus (nodes, arcs).
-- valid_from / valid_to au format ISO-8601 'YYYY-MM-DDTHH:mm' (ordre lexicographique = ordre chronologique).
-- bpmn_id est stable : identique pour toutes les versions et traductions d'un processus (non unique).
-- Une version est identifiée par (process_id, bpanda_version, lang).

CREATE TABLE business_process (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    content    JSONB        NOT NULL,
    metadata   JSONB        NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_business_process_version
    ON business_process ((metadata ->> 'process_id'), (metadata ->> 'bpanda_version'), (metadata ->> 'lang'));

-- Sert les égalités de DocumentTool (EQ / IN traduits en metadata @> {...}) : process_id, bpmn_id, lang...
CREATE INDEX idx_business_process_metadata_gin
    ON business_process USING gin (metadata jsonb_path_ops);
