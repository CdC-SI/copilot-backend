-- F3 : réponse interrompue par l'utilisateur (bouton « Stop ») : la question et la réponse partielle déjà
-- streamée sont enregistrées, le message assistant étant marqué interrupted = true.
ALTER TABLE chat_history ADD COLUMN IF NOT EXISTS interrupted BOOLEAN NOT NULL DEFAULT FALSE;
