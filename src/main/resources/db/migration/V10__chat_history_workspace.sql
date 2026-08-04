-- Le workspace utilisé pour répondre (fourni explicitement par l'utilisateur, ou inféré par
-- RAGTool) est désormais mémorisé au niveau du message lui-même plutôt qu'au niveau de la
-- conversation : deux questions d'une même conversation peuvent légitimement viser des workspaces
-- différents. Nullable : aucune information de workspace n'est disponible si le tool de recherche
-- documentaire n'a pas été invoqué pour ce message (ex. question portant uniquement sur une pièce
-- jointe).
ALTER TABLE chat_history ADD COLUMN workspace TEXT;
