package zas.admin.zec.backend.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Budget d'appels de tools par tour (RAGTool, DocumentTool, pièces jointes). Le nombre de documents par appel de
 * {@code get_documents} est une constante ({@code DocumentTool.MAX_DOCUMENTS_PER_CALL}), citée dans la description
 * du tool.
 *
 * @param maxToolCallsPerTurn  nombre maximal d'appels de tools (tous tools confondus) par tour
 */
@Validated
@ConfigurationProperties(prefix = "rag.document-tool")
public record DocumentToolProperties(
        @NotNull @Min(1) Integer maxToolCallsPerTurn) {}
