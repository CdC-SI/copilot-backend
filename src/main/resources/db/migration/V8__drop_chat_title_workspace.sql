-- Le workspace n'est plus figé pour toute une conversation : une question peut être liée à un
-- workspace, mais la question suivante de la même conversation peut tout à fait viser un autre
-- workspace. Le choix du workspace ne provient donc plus que de la Question elle-même (si elle en
-- porte un) ou, à défaut, d'une inférence LLM refaite à chaque question (voir RAGTool). La colonne
-- n'a donc plus lieu d'être mémorisée sur la conversation (chat_title).
ALTER TABLE chat_title DROP COLUMN workspace;
