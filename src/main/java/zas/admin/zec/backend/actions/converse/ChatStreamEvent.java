package zas.admin.zec.backend.actions.converse;

/**
 * Événement du flux de réponse (T1), transmis en SSE sous la forme {@code event: <type>} + {@code data: <json de data>}.
 * <p>
 * Ordre garanti : {@code start} en premier, puis {@code status} / {@code text_delta} / {@code source} /
 * {@code suggestion} / {@code workspace}, et enfin un seul événement terminal : {@code done} (émis uniquement après
 * l'enregistrement de l'échange en base) ou {@code error} (rien n'est enregistré). Le flux peut rester ouvert après
 * {@code done}, le temps de générer le titre d'une nouvelle conversation.
 *
 * @param type nom de l'événement SSE
 * @param data contenu sérialisé en JSON
 */
public record ChatStreamEvent(String type, Object data) {

    public static final String START = "start";
    public static final String STATUS = "status";
    public static final String TEXT_DELTA = "text_delta";
    public static final String SOURCE = "source";
    public static final String SUGGESTION = "suggestion";
    public static final String WORKSPACE = "workspace";
    public static final String DONE = "done";
    public static final String ERROR = "error";

    /** Code d'erreur générique : la génération ou l'enregistrement de la réponse a échoué. */
    public static final String GENERATION_FAILED = "GENERATION_FAILED";

    public record Start(String conversationId, String messageId) {}

    /** {@code code} : identifiant stable de l'étape (tag de {@code ChatStatus}) ; {@code label} : texte localisé. */
    public record Status(String code, String label) {}

    public record TextDelta(String text) {}

    public record Suggestion(String text) {}

    public record Workspace(String name) {}

    public record Done(String messageId, boolean interrupted) {}

    public record Error(String code, String message) {}

    public static ChatStreamEvent start(String conversationId, String messageId) {
        return new ChatStreamEvent(START, new Start(conversationId, messageId));
    }

    public static ChatStreamEvent status(String code, String label) {
        return new ChatStreamEvent(STATUS, new Status(code, label));
    }

    public static ChatStreamEvent textDelta(String text) {
        return new ChatStreamEvent(TEXT_DELTA, new TextDelta(text));
    }

    public static ChatStreamEvent source(Source source) {
        return new ChatStreamEvent(SOURCE, source);
    }

    public static ChatStreamEvent suggestion(String text) {
        return new ChatStreamEvent(SUGGESTION, new Suggestion(text));
    }

    public static ChatStreamEvent workspace(String name) {
        return new ChatStreamEvent(WORKSPACE, new Workspace(name));
    }

    public static ChatStreamEvent done(String messageId, boolean interrupted) {
        return new ChatStreamEvent(DONE, new Done(messageId, interrupted));
    }

    public static ChatStreamEvent error(String code, String message) {
        return new ChatStreamEvent(ERROR, new Error(code, message));
    }
}
