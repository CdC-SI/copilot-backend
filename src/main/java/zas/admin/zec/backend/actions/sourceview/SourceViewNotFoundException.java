package zas.admin.zec.backend.actions.sourceview;

/** Source inconnue ou inaccessible à l'utilisateur (réponse 404, sans distinguer les deux cas). */
public class SourceViewNotFoundException extends RuntimeException {

    public SourceViewNotFoundException(String message) {
        super(message);
    }
}
