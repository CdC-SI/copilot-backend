-- Processus métier BPMN (export BPanda converti en JSON par le script d'extraction BPMN -> JSON).
-- id      : identifiant technique généré, sans signification métier.
-- bpmn_id : identifiant du processus dans le XML BPMN (unique, cible des callActivity).
-- content : JSON complet du processus (nom, bpanda_id, validité, état, nœuds, arcs), injecté
--           tel quel dans le contexte du LLM par ProcessService.
CREATE TABLE business_process (
    id      UUID  PRIMARY KEY DEFAULT gen_random_uuid(),
    bpmn_id TEXT  NOT NULL UNIQUE,
    content JSONB NOT NULL
);

-- Jointure document -> processus : le paramètre processId des liens BPanda présents dans les
-- outgoing_links des documents vaut le bpanda_id du processus.
CREATE INDEX idx_business_process_bpanda ON business_process ((content ->> 'bpanda_id'));
