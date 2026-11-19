package zas.admin.zec.backend.tools.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.pgvector.PgVectorFilterExpressionConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Recherche exacte par métadonnées dans les chunks de documentation ({@code vector_store}).
 *
 * <p>Règle d'accès (documents personnels des autres utilisateurs exclus) exprimée par {@link #accessFilter}, converti en jsonpath par le {@link PgVectorFilterExpressionConverter}. Les résultats sont triés par document parent
 * puis par index de chunk, ce qui facilite la lecture de chunks voisins.</p>
 */
@Slf4j
@Component
public class VectorStoreLookup implements DocumentLookup {

    static final List<String> FILTERABLE_KEYS = List.of(
            JsonbFilterSqlBuilder.KEY_ID, "doc_id", "chunk_index", "title", "source", "url",
            "doc_type", "process_id", "bpmn_id", "lang", "version");
    private static final List<String> NUMERIC_KEYS = List.of("chunk_index");
    private static final String META_USER_UUID = "user_uuid";

    private static final String CHUNK_INDEX_ORDER =
            "(CASE WHEN metadata ->> 'chunk_index' ~ '^[0-9]+$' THEN (metadata ->> 'chunk_index')::int END)";

    private final JdbcTemplate jdbcTemplate;
    private final JsonbFilterSqlBuilder sqlBuilder = new JsonbFilterSqlBuilder(FILTERABLE_KEYS, NUMERIC_KEYS);
    private final FilterExpressionConverter accessConverter = new PgVectorFilterExpressionConverter();

    public VectorStoreLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DocumentCollection collection() {
        return DocumentCollection.DOCUMENTATION;
    }

    @Override
    public List<String> filterableKeys() {
        return FILTERABLE_KEYS;
    }

    @Override
    public LookupResult find(LookupRequest request) {
        var fragment = sqlBuilder.build(request.filters());
        String accessClause = accessConverter.convertExpression(
                accessFilter(request.userId()));

        String sql = """
                SELECT id::text AS id, %s AS content, metadata::text AS metadata
                FROM vector_store
                WHERE (%s) AND (%s)
                ORDER BY metadata ->> 'doc_id' NULLS LAST, %s NULLS LAST, id
                LIMIT ?
                """.formatted(request.includeContent() ? "content" : "NULL::text",
                fragment.sql(), accessClause, CHUNK_INDEX_ORDER);

        List<Object> params = new ArrayList<>(fragment.params());
        params.add(request.limit() + 1);

        log.debug("DocumentTool vector_store SQL: {} params={}", sql, params);
        List<Document> rows = jdbcTemplate.query(sql,
                (rs, i) -> DocumentRows.toDocument(rs, DocumentCollection.DOCUMENTATION),
                params.toArray());

        boolean truncated = rows.size() > request.limit();
        return new LookupResult(truncated ? rows.subList(0, request.limit()) : rows, truncated, null);
    }

    /**
     * Documents visibles : documents publics ({@code user_uuid} vide) et documents propres à l'utilisateur. Tous les
     * utilisateurs étant internes, la métadonnée {@code organizations} n'est plus filtrée.
     */
    static Filter.Expression accessFilter(String userId) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        return builder.group(builder.or(builder.eq(META_USER_UUID, userId), builder.eq(META_USER_UUID, ""))).build();
    }
}
