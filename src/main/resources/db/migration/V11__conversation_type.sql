-- Le type de conversation (COMPLETE / NO_RAG) est déterminé une seule fois, à la création
-- de la conversation (1re question), puis reste immuable pendant toute sa durée de vie.
-- chat_title est la seule table comportant une ligne unique par conversation : c'est donc
-- l'endroit naturel pour stocker ce type.
-- DEFAULT 'COMPLETE' préserve le comportement actuel pour toutes les conversations existantes.
ALTER TABLE chat_title ADD COLUMN conversation_type TEXT NOT NULL DEFAULT 'COMPLETE';
