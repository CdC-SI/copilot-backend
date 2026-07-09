package zas.admin.zec.backend.tools;

import org.springframework.ai.document.Document;
import reactor.core.publisher.Sinks;
import zas.admin.zec.backend.rag.ChatStatus;
import zas.admin.zec.backend.rag.token.StatusToken;
import zas.admin.zec.backend.rag.token.Token;

import java.util.Collection;
import java.util.Map;

/**
 * Constantes de clés partagées entre tous les tools Spring AI et leurs appelants pour alimenter
 * le {@link org.springframework.ai.chat.model.ToolContext}.
 *
 * <p>Deux familles de clés :</p>
 * <ul>
 *   <li><b>CTX_*</b> — données contextuelles non fournies par le LLM (identité, langue, workspace…)</li>
 *   <li><b>Sinks</b> — canaux réactifs partagés pour remonter des informations au flux principal
 *       pendant l'exécution des tools :
 *       <ul>
 *         <li>{@link #CTX_RETRIEVED_DOCUMENTS} : collection de {@link Document} pour reconstruire
 *             les sources.</li>
 *         <li>{@link #CTX_STATUS_SINK} : sink de {@link Token} pour émettre des
 *             {@link StatusToken} en temps réel avant/pendant le traitement d'un tool.</li>
 *       </ul>
 *   </li>
 * </ul>
 */
public final class ToolContextKeys {

    /** Identifiant de l'utilisateur courant — non fourni par le LLM. */
    public static final String CTX_USER_ID = "userId";

    /** Code de langue de l'interface (fr / de / it) — non fourni par le LLM. */
    public static final String CTX_LANGUAGE = "language";

    /** Workspace courant — non fourni par le LLM. */
    public static final String CTX_WORKSPACE = "workspace";

    /** Identifiant de la conversation courante — non fourni par le LLM. */
    public static final String CTX_CONVERSATION_ID = "conversationId";

    /**
     * Clé d'une {@link Collection} mutable de {@link Document} fournie par l'appelant.
     * Les tools y déposent les documents récupérés afin que l'appelant puisse reconstruire
     * les {@link zas.admin.zec.backend.rag.token.SourceToken} une fois la génération terminée.
     */
    public static final String CTX_RETRIEVED_DOCUMENTS = "retrievedDocuments";

    /**
     * Clé d'un {@link java.util.concurrent.atomic.AtomicReference}{@code <}{@link String}{@code >}
     * fourni par l'appelant. {@link zas.admin.zec.backend.tools.RAGTool} y dépose le nom du
     * workspace effectivement utilisé (connu dès le départ, ou inféré s'il était absent du
     * contexte), afin que l'appelant puisse reconstruire un
     * {@link zas.admin.zec.backend.rag.token.WorkspaceToken} une fois la génération terminée.
     */
    public static final String CTX_RESOLVED_WORKSPACE = "resolvedWorkspace";

    /**
     * Clé d'un {@link Sinks.Many}{@code <}{@link Token}{@code >} fourni par l'appelant.
     * Les tools y émettent des {@link StatusToken} <em>avant</em> leur traitement afin de
     * notifier le frontend en temps réel (retrieval, OCR, tool-calling…) via le flux SSE.
     *
     * <p>L'appelant est responsable de compléter le sink ({@link Sinks.Many#tryEmitComplete()})
     * une fois le flux de texte terminé.</p>
     */
    public static final String CTX_STATUS_SINK = "statusSink";

    private ToolContextKeys() {
        // Classe utilitaire — pas d'instanciation.
    }

    /**
     * Émet un {@link StatusToken} dans le sink de statut du contexte, si présent.
     *
     * <p>Sans effet si {@link #CTX_STATUS_SINK} est absent, ou si le sink est déjà terminé
     * ({@link Sinks.EmitResult} non propagée).</p>
     *
     * @param context contexte du tool ({@link org.springframework.ai.chat.model.ToolContext#getContext()})
     * @param status  statut à émettre
     * @param lang    code langue (fr / de / it) pour le message localisé
     * @param args    arguments optionnels de formatage (ex. nom du tool pour {@link ChatStatus#TOOL_USE})
     */
    @SuppressWarnings("unchecked")
    public static void emitStatus(Map<String, Object> context, ChatStatus status, String lang, Object... args) {
        if (context.get(CTX_STATUS_SINK) instanceof Sinks.Many<?> rawSink) {
            var sink = (Sinks.Many<Token>) rawSink;
            sink.tryEmitNext(new StatusToken(status, lang, args));
        }
    }
}

