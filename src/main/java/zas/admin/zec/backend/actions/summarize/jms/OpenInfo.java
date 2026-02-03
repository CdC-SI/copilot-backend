package zas.admin.zec.backend.actions.summarize.jms;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO pour les informations d'ouverture de documents via JMS.
 * Structure attendue par l'application tierce qui reçoit le message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenInfo {

    /**
     * Action à effectuer : "viewDocuments" pour afficher plusieurs documents
     */
    private String action;

    /**
     * Token d'un document unique (utilisé pour viewDocument)
     */
    private String objToken;

    /**
     * ID du dossier (utilisé pour listFolderDocuments)
     */
    private String folderId;

    /**
     * Visa de l'utilisateur
     */
    private String visa;

    /**
     * Liste des tokens de documents à afficher (utilisé pour viewDocuments)
     */
    private List<String> objTokens;
}

