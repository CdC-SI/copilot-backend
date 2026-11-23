package zas.admin.zec.backend.actions.upload;

/** Aucun document accessible à l'utilisateur ne porte ce nom (réponse 404). */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String filename) {
        super("Document not found: " + filename);
    }
}
