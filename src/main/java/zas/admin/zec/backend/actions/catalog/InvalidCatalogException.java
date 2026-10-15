package zas.admin.zec.backend.actions.catalog;

import java.util.List;

/**
 * Levée lorsqu'un fichier de catalogue est invalide (syntaxe, contraintes, références).
 * Porte l'ensemble des erreurs détectées ; aucune modification n'est appliquée.
 */
public class InvalidCatalogException extends RuntimeException {

    private final List<String> errors;

    public InvalidCatalogException(List<String> errors) {
        super("Fichier de catalogue invalide : " + String.join(" ; ", errors));
        this.errors = List.copyOf(errors);
    }

    public InvalidCatalogException(String error) {
        this(List.of(error));
    }

    public List<String> getErrors() {
        return errors;
    }
}
