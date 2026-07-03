package zas.admin.zec.backend.actions.workspace;

/**
 * Levée lorsqu'une source référencée dans une requête de workspace n'existe pas.
 */
public class UnknownSourceException extends RuntimeException {
    public UnknownSourceException(String message) {
        super(message);
    }
}
