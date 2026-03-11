package zas.admin.zec.backend.actions.sourcerequest;

/**
 * Exception levée lorsqu'une demande de source n'est pas trouvée.
 */
public class SourceRequestNotFoundException extends RuntimeException {
    public SourceRequestNotFoundException(String message) {
        super(message);
    }
}
