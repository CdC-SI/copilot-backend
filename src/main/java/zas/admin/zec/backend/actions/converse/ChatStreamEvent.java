package zas.admin.zec.backend.actions.converse;

import zas.admin.zec.backend.rag.token.ApprovalRequiredToken;
import zas.admin.zec.backend.rag.token.ToolCallToken;
import zas.admin.zec.backend.rag.token.ToolResultToken;

import java.util.Map;

/**
 * Événement du flux de réponse (T1), transmis en SSE sous la forme {@code event: <type>} + {@code data: <json de data>}.
 * <p>
 * Ordre garanti : {@code start} en premier, puis {@code status} / {@code text_delta} / {@code citation} /
 * {@code source} /
 * {@code suggestion} / {@code workspace} / {@code tool_call} / {@code tool_result} / {@code approval_required}, et
 * enfin un seul événement terminal : {@code done} (émis uniquement après
 * l'enregistrement de l'échange en base) ou {@code error} (rien n'est enregistré). Le flux peut rester ouvert après
 * {@code done}, le temps de générer le titre d'une nouvelle conversation.
 * <p>
 * Chaque {@code tool_call} est suivi d'un {@code tool_result} de même {@code id} (sauf interruption du flux). Entre les
 * deux, {@code approval_required} suspend l'exécution jusqu'à la décision de l'utilisateur
 * ({@code POST /api/conversations/{conversationId}/approvals/{approvalId}}), l'expiration ou l'arrêt.
 *
 * @param type nom de l'événement SSE
 * @param data contenu sérialisé en JSON
 */
public record ChatStreamEvent(String type, Object data) {

    public static final String START = "start";
    public static final String STATUS = "status";
    public static final String TEXT_DELTA = "text_delta";
    public static final String SOURCE = "source";
    public static final String CITATION = "citation";
    public static final String SUGGESTION = "suggestion";
    public static final String WORKSPACE = "workspace";
    public static final String TOOL_CALL = "tool_call";
    public static final String TOOL_RESULT = "tool_result";
    public static final String APPROVAL_REQUIRED = "approval_required";
    public static final String DONE = "done";
    public static final String ERROR = "error";

    /** Code d'erreur générique : la génération ou l'enregistrement de la réponse a échoué. */
    public static final String GENERATION_FAILED = "GENERATION_FAILED";

    public record Start(String conversationId, String messageId) {}

    /** {@code code} : identifiant stable de l'étape (tag de {@code ChatStatus}) ; {@code label} : texte localisé. */
    public record Status(String code, String label) {}

    public record TextDelta(String text) {}

    /**
     * Première citation d'une source dans la réponse (F2a), émise avant le {@code text_delta} qui contient son marqueur
     * {@code [^n]}. Les événements {@code source} de fin de tour restent la liste qui fait foi.
     */
    public record Citation(int n, Source source) {}

    public record Suggestion(String text) {}

    public record Workspace(String name) {}

    /** {@code arguments} : arguments fournis par le LLM, valeurs textuelles tronquées. */
    public record ToolCall(String id, String name, Map<String, Object> arguments) {}

    /**
     * {@code status} : {@code ok}, {@code no_result}, {@code invalid_arguments}, {@code budget_exhausted},
     * {@code error}, {@code rejected}, {@code expired} ou {@code cancelled}.
     */
    public record ToolResult(String id, String name, String status, long durationMs, int documentCount) {}

    /** {@code expiresAt} : instant ISO-8601 (UTC) au-delà duquel l'appel est refusé d'office. */
    public record ApprovalRequired(String id, String approvalId, String name, Map<String, Object> arguments,
                                   String expiresAt) {}

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

    public static ChatStreamEvent citation(int number, Source source) {
        return new ChatStreamEvent(CITATION, new Citation(number, source));
    }

    public static ChatStreamEvent suggestion(String text) {
        return new ChatStreamEvent(SUGGESTION, new Suggestion(text));
    }

    public static ChatStreamEvent workspace(String name) {
        return new ChatStreamEvent(WORKSPACE, new Workspace(name));
    }

    public static ChatStreamEvent toolCall(ToolCallToken token) {
        return new ChatStreamEvent(TOOL_CALL, new ToolCall(token.callId(), token.tool(), token.arguments()));
    }

    public static ChatStreamEvent toolResult(ToolResultToken token) {
        return new ChatStreamEvent(TOOL_RESULT, new ToolResult(token.callId(), token.tool(), token.status(),
                token.durationMs(), token.documentCount()));
    }

    public static ChatStreamEvent approvalRequired(ApprovalRequiredToken token) {
        return new ChatStreamEvent(APPROVAL_REQUIRED, new ApprovalRequired(token.callId(), token.approvalId(),
                token.tool(), token.arguments(), token.expiresAt().toString()));
    }

    public static ChatStreamEvent done(String messageId, boolean interrupted) {
        return new ChatStreamEvent(DONE, new Done(messageId, interrupted));
    }

    public static ChatStreamEvent error(String code, String message) {
        return new ChatStreamEvent(ERROR, new Error(code, message));
    }
}
