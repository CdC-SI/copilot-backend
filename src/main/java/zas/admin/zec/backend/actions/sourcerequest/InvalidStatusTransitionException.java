package zas.admin.zec.backend.actions.sourcerequest;

/**
 * Exception levée lorsqu'une transition de statut est invalide.
 */
public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
