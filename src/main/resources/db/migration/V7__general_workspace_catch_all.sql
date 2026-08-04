-- Le workspace GENERAL devient le workspace "catch-all" : il ne doit plus filtrer par source.
-- Une liste de sources vide fait déjà que RAGTool ne filtre pas du tout (voir buildExpression),
-- il suffit donc de retirer ses liens workspace_source pour qu'il couvre toute la documentation.
DELETE FROM workspace_source
WHERE workspace_id = (SELECT id FROM workspace WHERE name = 'GENERAL');

-- Description/hypothetical_questions plus explicites pour guider l'inférence LLM du workspace
-- (RAGTool retombe sur ce workspace quand l'inférence est incertaine).
UPDATE workspace
SET description = 'Workspace général et par défaut : couvre l''ensemble de la documentation, '
    || 'toutes thématiques confondues. À utiliser quand la question ne correspond clairement '
    || 'à aucun workspace thématique spécifique, ou lorsqu''elle est trop générale/transverse '
    || 'pour être rattachée à un domaine précis.',
    hypothetical_questions = '{}',
    updated_at = NOW()
WHERE name = 'GENERAL';
