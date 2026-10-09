-- Processus BPMN (bpmn_id) utilisés pour produire une réponse du LLM. Seul le texte final de la
-- réponse est persisté, pas les résultats de tools : sans cette colonne, le processus chargé à un
-- tour serait perdu au tour suivant (ex. réponse à une question de clarification). RAGChatService
-- relit cette colonne sur le dernier message assistant pour réinjecter le processus. Nullable :
-- aucun processus pour la grande majorité des messages.
ALTER TABLE chat_history ADD COLUMN processes TEXT[];
