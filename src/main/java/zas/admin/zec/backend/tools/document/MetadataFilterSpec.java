package zas.admin.zec.backend.tools.document;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * Condition sur une clé de métadonnées, fournie par le LLM à {@code get_documents}.
 *
 * @param key    clé de métadonnées (liste blanche par collection, cf. description du tool)
 * @param op     opérateur
 * @param value  valeur pour EQ / CONTAINS / GTE / LTE
 * @param values valeurs pour IN
 */
public record MetadataFilterSpec(
        @ToolParam(description = "Clé de métadonnées (voir la liste des clés autorisées par collection).")
        @JsonPropertyDescription("Clé de métadonnées (voir la liste des clés autorisées par collection).")
        String key,
        @ToolParam(description = "Opérateur : EQ, IN, CONTAINS, GTE ou LTE.")
        @JsonPropertyDescription("Opérateur : EQ, IN, CONTAINS, GTE ou LTE.")
        FilterOp op,
        @ToolParam(description = "Valeur pour EQ, CONTAINS, GTE, LTE.", required = false)
        @JsonPropertyDescription("Valeur pour EQ, CONTAINS, GTE, LTE.")
        String value,
        @ToolParam(description = "Liste de valeurs pour IN.", required = false)
        @JsonPropertyDescription("Liste de valeurs pour IN.")
        List<String> values) {
}
