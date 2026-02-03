package zas.admin.zec.backend.actions.summarize.jms;

/**
 * Constantes pour les messages JMS GAIME.
 */
public class JmsConstants {

    private JmsConstants() {
        // Classe utilitaire
    }

    /**
     * Préfixe du message JMS GAIME
     */
    public static final String GAIME_MESSAGE = "GAIME=";

    /**
     * Action pour ouvrir un dossier
     */
    public static final String OPEN_FOLDER_ACTION = "listFolderDocuments";

    /**
     * Action pour ouvrir un document unique
     */
    public static final String OPEN_DOCUMENT_ACTION = "viewDocument";

    /**
     * Action pour ouvrir plusieurs documents
     */
    public static final String OPEN_DOCUMENTS_ACTION = "viewDocuments";
}

