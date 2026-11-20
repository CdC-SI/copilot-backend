-- Agent unique (F17) : les conversations « sans Corpus » (NO_RAG) basculent sur l'agent unique (COMPLETE).
-- Le type NO_RAG n'existe plus côté application.
UPDATE chat_title SET conversation_type = 'COMPLETE' WHERE conversation_type <> 'COMPLETE';
