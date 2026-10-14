-- retrieved_docs : colonne legacy jamais alimentée (toujours NULL).
ALTER TABLE chat_history DROP COLUMN IF EXISTS retrieved_docs;

-- Trace du tour de l'agent, sur le message assistant :
--   {"turn_status": "answer|followup|refusal|unknown", "sources": [{id, collection, title, ...}, ...]}
-- sources : documents consultés (RAGTool + DocumentTool), y compris lorsqu'ils ne sont pas affichés
-- (question de suivi). Liste compacte réinjectée dans le contexte des tours suivants
-- (<retrieved_sources>) et utilisée pour afficher les sources de la réponse finale.
ALTER TABLE chat_history ADD COLUMN consulted_sources JSONB;
