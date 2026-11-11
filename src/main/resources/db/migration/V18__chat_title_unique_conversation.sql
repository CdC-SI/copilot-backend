-- Une conversation ne doit avoir qu'un seul titre. La ligne chat_title est désormais créée de façon
-- idempotente (INSERT ... ON CONFLICT DO NOTHING) avant la génération du titre par le LLM, afin
-- qu'une question de suivi posée pendant cette génération ne crée pas de second titre.

-- Suppression des doublons existants : on conserve la ligne la plus ancienne de chaque conversation.
DELETE FROM chat_title duplicate
USING chat_title original
WHERE duplicate.user_uuid = original.user_uuid
  AND duplicate.conversation_uuid = original.conversation_uuid
  AND duplicate.id > original.id;

ALTER TABLE chat_title
    ADD CONSTRAINT uk_chat_title_user_conversation UNIQUE (user_uuid, conversation_uuid);
