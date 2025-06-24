package zas.admin.zec.backend.rag.validation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.rag.PublicDocument;

public class SourceValidator {

    private static final String SOURCE_VALIDATION_SYSTEM_MESSAGE_FR = """
        # Tâche
        Votre tâche consiste à valider la source d'information pour répondre à la question posée par l'utilisateur. Vous devez déterminer :
            - si la source est pertinente et contient l'information nécessaire pour répondre à la question.
            - si la source est partielle (ne contient pas toutes les informations nécessaires) ou complète.
            - la raison de la validation de la source.
        
        Soyez extrêmement strict dans votre validation. Validez une source seulement si vous pourriez citer des passages exacts de la source pour répondre à la question.
        Vous pouvez également consulter les sujets associés à la source pour aider à valider la source, mais votre décision doit principalement reposer sur le contenu de la source elle-même.
        
        # Format de réponse
        UniqueSourceValidation(
            isPartial: bool, # True si la source contient des informations partielles, False sinon
            isValid: bool # True si la source est valide, False sinon
            reason: str # Raison de la validation de la source (une phrase courte)
        )
        
        # Sujets
        %s
        
        # Source
        %s
        
        # Question
        %s
        """;

    private final ChatClient chatClient;

    public SourceValidator(ChatModel chatModel) {
        this.chatClient = ChatClient.create(chatModel);
    }

    public Boolean isValidSource(Question question, PublicDocument source) {
        var validation = chatClient.prompt()
                .system(systemMessage(question, source))
                .user(question.query())
                .call()
                .entity(UniqueSourceValidation.class);

        return validation != null && validation.isValid();
    }

    public Boolean isValidSource(String query, String lang, Document doc) {
        var validation = chatClient.prompt()
                .system(systemMessage(query, lang, doc))
                .user(query)
                .call()
                .entity(UniqueSourceValidation.class);

        return validation != null && validation.isValid();
    }

    private String systemMessage(Question question, PublicDocument source) {
        return switch (question.language()) {
            default -> SOURCE_VALIDATION_SYSTEM_MESSAGE_FR.formatted(
                    source.tags() == null ? "" : String.join(",", source.tags()),
                    source.text(),
                    question.query());
        };
    }

    private String systemMessage(String query, String lang, Document doc) {
        return switch (lang) {
            default -> SOURCE_VALIDATION_SYSTEM_MESSAGE_FR.formatted(
                    doc.getMetadata().get("tags") == null ? "" : doc.getMetadata().get("tags"),
                    doc.getText(),
                    query);
        };
    }
}
