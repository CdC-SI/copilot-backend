-- retrieved_docs : colonne legacy jamais alimentée (toujours NULL).
ALTER TABLE chat_history DROP COLUMN IF EXISTS retrieved_docs;

-- Trace du tour de l'agent, sur le message assistant :
--   {"turn_status": "answer|followup|refusal|unknown", "used": [...], "used_current": [...], "used_reused": [...],
--    "used_unknown": [...], "sources": [{id, collection, title, ...}, ...]}
-- sources : documents consultés (RAGTool + DocumentTool + pièces jointes lues), y compris lorsqu'ils ne sont pas
-- affichés (question de suivi, documents non utilisés). Liste compacte réinjectée dans le contexte des tours suivants
-- (<retrieved_sources>) et utilisée pour afficher les sources de la réponse finale.
-- used : identifiants déclarés par l'agent dans <turn_meta used="..."> (documents sur lesquels la réponse s'appuie),
-- classés en used_current (consultés pendant ce tour), used_reused (tours antérieurs) et used_unknown (inconnus).
-- Clés absentes si l'agent n'a rien déclaré.
ALTER TABLE chat_history ADD COLUMN consulted_sources JSONB;

-- Appels de tools du tour, sur le message assistant (débogage et évaluations automatisées) :
--   [{"seq": 1, "step": 1, "tool": "search_social_insurance_documentation", "arguments": {"query": "..."},
--     "status": "ok|no_result|invalid_arguments|budget_exhausted|error", "duration_ms": 812,
--     "result_chars": 5400, "document_ids": ["...", ...]}, ...]
-- Liste vide : aucun tool appelé (conversation agentique) ; NULL : messages utilisateur, autres modes, historique.
-- Les arguments reprennent des reformulations de la question : même sensibilité que chat_history.message,
-- supprimés avec la conversation.
ALTER TABLE chat_history ADD COLUMN tool_calls JSONB;

-- Tokens consommés par le tour, sur le message assistant (évaluations : coût, prefix caching) :
--   {"llm_calls": 3, "prompt_tokens": 21000, "completion_tokens": 900, "cached_tokens": 15000 | null,
--    "agent_calls": [{"purpose": "agent", "prompt_tokens": ..., "completion_tokens": ..., "cached_tokens": ...}, ...],
--    "auxiliary_calls": [{"purpose": "workspace_inference", ...}]}
-- cached_tokens : null si le serveur ne le fournit pas (vLLM : --enable-prompt-tokens-details).
ALTER TABLE chat_history ADD COLUMN usage JSONB;
