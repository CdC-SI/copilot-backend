package zas.admin.zec.backend.rag.retriever;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.pgvector.PgVectorFilterExpressionConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Recherche de documents via BM25 full-text search sur PostgreSQL
 * en utilisant l'extension <a href="https://github.com/timescale/pg_textsearch">pg_textsearch</a>
 * de Timescale.
 * <p>
 * Utilise l'opérateur {@code <@>} qui exploite un index BM25 natif sur la colonne {@code content}
 * de la table {@code vector_store}. L'opérateur retourne un score BM25 <strong>négatif</strong>
 * (plus bas = meilleure correspondance) ; le score est inversé avant d'être affecté au document.
 * <p>
 * Le filtrage sur les métadonnées réutilise le {@link PgVectorFilterExpressionConverter}
 * de Spring AI pour générer les clauses jsonpath identiques à celles du VectorStore.
 */
@Slf4j
public class BM25DocumentRetriever implements DocumentRetriever {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final int topK;
    private final Supplier<Filter.Expression> filterExpressionSupplier;
    private final FilterExpressionConverter filterExpressionConverter = new PgVectorFilterExpressionConverter();

    public BM25DocumentRetriever(JdbcTemplate jdbcTemplate, int topK,
                                 Supplier<Filter.Expression> filterExpressionSupplier) {
        this.jdbcTemplate = jdbcTemplate;
        this.topK = topK;
        this.filterExpressionSupplier = filterExpressionSupplier;
    }

    @Override
    @NonNull
    public List<Document> retrieve(@NonNull Query query) {
        log.debug("BM25 full-text search (pg_textsearch) for query: {}", query.text());

        // Construire le filtre jsonpath sur les métadonnées (même logique que PgVectorStore)
        Filter.Expression filterExpression = filterExpressionSupplier.get();
        String nativeFilterExpression = (filterExpression != null)
                ? filterExpressionConverter.convertExpression(filterExpression) : "";

        String jsonPathFilter = "";
        if (StringUtils.hasText(nativeFilterExpression)) {
            jsonPathFilter = " AND metadata::jsonb @@ '" + nativeFilterExpression + "'::jsonpath ";
        }

        // Requête SQL utilisant l'opérateur <@> de pg_textsearch.
        // L'opérateur retourne un score BM25 négatif (plus bas = meilleure correspondance).
        // L'index BM25 n'est auto-détecté que dans ORDER BY, pas dans WHERE ;
        // on filtre les résultats sans correspondance (score = 0) côté Java.
        String whereClause = StringUtils.hasText(jsonPathFilter)
                ? "WHERE 1=1 " + jsonPathFilter
                : "";

        String sql = """
                SELECT id, content, metadata, content <@> to_bm25query(?, 'idx_vector_store_content_bm25') AS score
                FROM vector_store
                %s
                ORDER BY score
                LIMIT ?
                """.formatted(whereClause);

        log.debug("BM25 SQL: {}", sql);

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            try {
                String id = rs.getString("id");
                String content = rs.getString("content");
                String metadataJson = rs.getString("metadata");
                double score = rs.getDouble("score");

                Map<String, Object> metadata = metadataJson != null
                        ? OBJECT_MAPPER.readValue(metadataJson, MAP_TYPE)
                        : Map.of();

                return Document.builder()
                        .id(id)
                        .text(content)
                        .metadata(metadata)
                        .score(-score)
                        .build();

            } catch (Exception e) {
                log.error("Erreur lors du parsing du document BM25", e);
                throw new IllegalStateException("Erreur parsing document BM25", e);
            }
        }, query.text(), topK)
                .stream()
                .filter(doc -> doc.getScore() != null && doc.getScore() > 1)
                .toList();
    }
}