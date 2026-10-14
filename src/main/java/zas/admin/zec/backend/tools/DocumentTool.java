package zas.admin.zec.backend.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.rag.ChatStatus;
import zas.admin.zec.backend.tools.document.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tool Spring AI de récupération <b>exacte</b> de documents par métadonnées, complémentaire à la
 * recherche sémantique de {@link RAGTool}. Permet à l'agent de :
 * <ul>
 *   <li>suivre un lien sortant ({@code outgoing_links}) d'un document retourné par la recherche ;</li>
 *   <li>lire les chunks voisins d'un chunk ({@code doc_id} + {@code chunk_index}) ;</li>
 *   <li>relire un document consulté lors d'un tour précédent ({@code <retrieved_sources>}) par son {@code id} ;</li>
 *   <li>consulter un processus métier (dernière version par défaut, ou une version précise).</li>
 * </ul>
 *
 * <p>La description du tool est constante (listes de clés statiques) afin de préserver le prefix caching.
 * Les règles d'accès sont appliquées par chaque {@link DocumentLookup} ; les documents retournés sont
 * déposés dans {@link ToolContextKeys#CTX_RETRIEVED_DOCUMENTS} comme pour {@link RAGTool}.</p>
 */
@Slf4j
@Component
public class DocumentTool {

    public static final String TOOL_NAME = "get_documents";

    /** Nombre maximal de documents par appel (limite le volume injecté dans le contexte du LLM). */
    public static final int MAX_DOCUMENTS_PER_CALL = 5;

    private static final String DEFAULT_LANGUAGE = "fr";

    static final String NO_RESULT = """
            <no_document_found>
            No document matches these filters (or the user is not allowed to access it). \
            Check the keys and values, or use search_social_insurance_documentation instead.
            </no_document_found>
            """;

    private final Map<DocumentCollection, DocumentLookup> lookups;
    private final UserService userService;

    public DocumentTool(List<DocumentLookup> lookups, UserService userService) {
        this.lookups = lookups.stream().collect(Collectors.toMap(DocumentLookup::collection, Function.identity(),
                (a, b) -> a, () -> new EnumMap<>(DocumentCollection.class)));
        this.userService = userService;
    }

    @Tool(name = TOOL_NAME, description = """
            Récupère des documents EXACTS par leurs métadonnées (pas de recherche sémantique). \
            À utiliser pour : suivre un lien sortant (outgoing_links) d'un document déjà retourné ; \
            lire les chunks voisins d'un chunk (doc_id + chunk_index) ; relire un document listé dans \
            <retrieved_sources> par son id ; consulter un processus métier interne (étapes, rôles, \
            décisions) ou ses métadonnées (version, état, validité). \
            Collections et clés filtrables : \
            DOCUMENTATION (chunks de documentation) : id, doc_id, chunk_index (numérique), title, source, url, \
            doc_type, process_id, bpmn_id, lang, version. \
            BUSINESS_PROCESS (processus métier versionnés) : id, bpmn_id, process_id, process_name, \
            process_state, bpanda_version, bpanda_id, valid_from, valid_to, lang. \
            Opérateurs : EQ (égalité), IN (une des valeurs de 'values'), CONTAINS (sous-chaîne, insensible \
            à la casse), GTE / LTE (numérique pour chunk_index ; dates ISO 'YYYY-MM-DDTHH:mm' pour valid_from/valid_to). \
            Les filtres sont combinés par ET ; au moins un filtre est requis. \
            """ + "Au plus " + MAX_DOCUMENTS_PER_CALL + " documents par appel : pour en lire davantage, affinez les "
            + "filtres (ex. chunk_index) ou faites un nouvel appel. " + """
            Pour BUSINESS_PROCESS, seule la dernière version (en vigueur) de chaque processus est retournée, \
            sauf filtre sur bpanda_version, valid_from, valid_to ou id. bpmn_id et process_id sont stables d'une \
            version à l'autre : un filtre sur l'un d'eux retourne la version en vigueur. \
            Exemples : lien vers un processus -> collection=BUSINESS_PROCESS, filters=[{key:process_id, op:EQ, \
            value:"CSC AF-61-2-408"}] ; sous-processus appelé (version en vigueur) -> filters=[{key:bpmn_id, op:EQ, value:<called_bpmn_id>}] ; \
            lien vers un document -> collection=DOCUMENTATION, filters=[{key:doc_id, op:EQ, value:<target_doc_id>}] ; \
            chunks voisins -> filters=[{key:doc_id, op:EQ, value:...}, {key:chunk_index, op:IN, values:["4","6"]}]. \
            Ne pas utiliser pour une question générale : utiliser d'abord search_social_insurance_documentation.""")
    public String getDocuments(
            @ToolParam(description = "Collection interrogée : DOCUMENTATION ou BUSINESS_PROCESS.")
            DocumentCollection collection,
            @ToolParam(description = "Filtres sur les métadonnées (combinés par ET). Au moins un filtre.")
            List<MetadataFilterSpec> filters,
            @ToolParam(description = """
                    Ce qui est retourné : METADATA (métadonnées seules, ex. question sur la version ou l'état \
                    d'un processus), CONTENT (contenu seul) ou BOTH (contenu et métadonnées).""")
            ReturnType returnType,
            @ToolParam(description = "Nombre maximal de documents retournés (défaut et maximum : "
                    + MAX_DOCUMENTS_PER_CALL + ").", required = false)
            Integer limit,
            ToolContext toolContext) {

        Map<String, Object> context = toolContext != null ? toolContext.getContext() : Map.of();
        if (!ToolCallBudget.tryAcquire(context, TOOL_NAME)) {
            return ToolCallBudget.EXHAUSTED_MESSAGE;
        }

        String language = asString(context.get(ToolContextKeys.CTX_LANGUAGE), DEFAULT_LANGUAGE);
        String userId = asString(context.get(ToolContextKeys.CTX_USER_ID), "");
        ToolContextKeys.emitStatus(context, ChatStatus.DOCUMENT_LOOKUP, language);

        DocumentCollection target = collection != null ? collection : DocumentCollection.DOCUMENTATION;
        ReturnType type = returnType != null ? returnType : ReturnType.BOTH;
        int effectiveLimit = effectiveLimit(limit);

        DocumentLookup lookup = lookups.get(target);
        if (lookup == null) {
            return "<error>Unsupported collection '%s'.</error>".formatted(target);
        }

        boolean hasInternalAccess = !userId.isBlank() && userService.hasAccessToInternalDocuments(userId);
        DocumentLookup.LookupResult result;
        try {
            result = lookup.find(new DocumentLookup.LookupRequest(
                    filters, effectiveLimit, userId, hasInternalAccess, language, type.includesContent()));
        } catch (JsonbFilterSqlBuilder.InvalidFilterException e) {
            log.debug("DocumentTool : filtre invalide {} ({})", filters, e.getMessage());
            return "<error>%s</error>".formatted(e.getMessage());
        }

        log.debug("DocumentTool {} filters={} returnType={} -> {} document(s)",
                target, filters, type, result.documents().size());

        depositDocuments(context, result.documents());
        return format(result, type);
    }

    private int effectiveLimit(Integer requested) {
        if (requested == null || requested < 1) {
            return MAX_DOCUMENTS_PER_CALL;
        }
        return Math.min(requested, MAX_DOCUMENTS_PER_CALL);
    }

    static String format(DocumentLookup.LookupResult result, ReturnType type) {
        String nl = System.lineSeparator();
        var sb = new StringBuilder();
        if (result.notice() != null) {
            sb.append("<notice>").append(result.notice()).append("</notice>").append(nl);
        }
        if (result.documents().isEmpty()) {
            return sb.append(NO_RESULT).toString();
        }
        sb.append(result.documents().stream()
                .map(document -> MetadataFormatter.format(document, type.includesContent(), type.includesMetadata()))
                .collect(Collectors.joining(nl)));
        if (result.truncated()) {
            sb.append(nl).append("<notice>More documents match these filters (at most %d per call): refine the filters, e.g. chunk_index, or make another call for the next ones.</notice>"
                    .formatted(MAX_DOCUMENTS_PER_CALL));
        }
        return sb.toString();
    }

    private static void depositDocuments(Map<String, Object> context, List<Document> documents) {
        if (context.get(ToolContextKeys.CTX_RETRIEVED_DOCUMENTS) instanceof Collection<?> sink) {
            @SuppressWarnings("unchecked")
            var documentSink = (Collection<Document>) sink;
            documentSink.addAll(documents);
        }
    }

    private static String asString(Object value, String defaultValue) {
        return value instanceof String s && !s.isBlank() ? s : defaultValue;
    }
}
