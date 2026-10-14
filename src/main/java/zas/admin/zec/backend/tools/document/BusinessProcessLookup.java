package zas.admin.zec.backend.tools.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Recherche exacte par métadonnées dans les processus métier ({@code business_process}).
 *
 * <ul>
 *   <li><b>Accès</b> : processus internes ZAS, réservés aux utilisateurs ayant accès aux documents internes.</li>
 *   <li><b>Version</b> : sauf filtre explicite sur une version ({@code id}, {@code bpanda_version},
 *       {@code valid_from}, {@code valid_to}), seule la dernière version de chaque processus est retournée —
 *       de préférence celle en vigueur ({@code valid_from <= now < valid_to}), sinon la plus récente
 *       ({@code valid_from} décroissant). Les dates sont au format ISO {@code YYYY-MM-DDTHH:mm}, comparables
 *       lexicographiquement. {@code bpmn_id} est stable d'une version et d'une traduction à l'autre : un filtre
 *       sur {@code bpmn_id} (sous-processus appelé) retourne donc la version en vigueur, comme {@code process_id}.
 *       Idem pour {@code bpanda_id} (s'il identifie une seule version, cette version est la seule candidate).</li>
 *   <li><b>Langue</b> : la version prime sur la langue. La langue de l'utilisateur ne départage que les
 *       traductions d'une même version ; si la version retenue n'existe pas dans sa langue, elle est retournée
 *       dans une autre langue (avec un avertissement). Un filtre {@code lang} explicite restreint la recherche.</li>
 * </ul>
 */
@Slf4j
@Component
public class BusinessProcessLookup implements DocumentLookup {

    static final List<String> FILTERABLE_KEYS = List.of(
            JsonbFilterSqlBuilder.KEY_ID, "bpmn_id", "process_id", "process_name", "process_state",
            "bpanda_version", "bpanda_id", "valid_from", "valid_to", "lang");
    private static final String[] VERSION_KEYS =
            {JsonbFilterSqlBuilder.KEY_ID, "bpanda_version", "valid_from", "valid_to"};
    private static final DateTimeFormatter ISO_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    static final String NO_ACCESS_NOTICE =
            "Business processes are internal documents: the current user is not allowed to access them.";
    static final String LATEST_ONLY_NOTICE =
            "Only the latest version (in force if any) of each process is returned. "
                    + "Filter on bpanda_version, valid_from or valid_to to obtain another version.";
    static final String OTHER_LANGUAGE_NOTICE =
            "Some results are not in the user's language (%s): the most recent version takes precedence over "
                    + "the language. Answer in the user's language.";

    private final JdbcTemplate jdbcTemplate;
    private final JsonbFilterSqlBuilder sqlBuilder = new JsonbFilterSqlBuilder(FILTERABLE_KEYS, List.of());

    public BusinessProcessLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DocumentCollection collection() {
        return DocumentCollection.BUSINESS_PROCESS;
    }

    @Override
    public List<String> filterableKeys() {
        return FILTERABLE_KEYS;
    }

    @Override
    public LookupResult find(LookupRequest request) {
        if (!request.hasInternalAccess()) {
            return new LookupResult(List.of(), false, NO_ACCESS_NOTICE);
        }

        var fragment = sqlBuilder.build(request.filters());
        boolean versionPinned = sqlBuilder.hasFilterOn(request.filters(), VERSION_KEYS);
        String language = request.language() != null ? request.language().strip() : "";

        List<Document> rows = query(request, fragment, versionPinned, language);
        boolean truncated = rows.size() > request.limit();
        List<Document> documents = truncated ? rows.subList(0, request.limit()) : rows;

        List<String> notices = new ArrayList<>();
        if (!versionPinned) {
            notices.add(LATEST_ONLY_NOTICE);
        }
        if (!language.isEmpty() && documents.stream().anyMatch(d -> !language.equals(d.getMetadata().get("lang")))) {
            notices.add(OTHER_LANGUAGE_NOTICE.formatted(language));
        }
        return new LookupResult(documents, truncated, notices.isEmpty() ? null : String.join(" ", notices));
    }

    /**
     * Une ligne par processus (dernière version) ou, si une version est demandée, par processus et version.
     * La langue de l'utilisateur ne sert qu'à départager les traductions d'une même version.
     */
    private List<Document> query(LookupRequest request, JsonbFilterSqlBuilder.SqlFragment fragment,
                                 boolean versionPinned, String language) {
        String contentColumn = request.includeContent() ? "content::text" : "NULL::text";
        String userLanguageFirst = "COALESCE((metadata ->> 'lang') = ?, FALSE) DESC";

        List<Object> params = new ArrayList<>(fragment.params());
        String sql;
        if (versionPinned) {
            sql = """
                    SELECT id, content, metadata FROM (
                        SELECT DISTINCT ON (metadata ->> 'process_id', metadata ->> 'bpanda_version')
                               id::text AS id, %s AS content, metadata::text AS metadata,
                               metadata ->> 'process_id' AS process_id, metadata ->> 'valid_from' AS valid_from
                        FROM business_process
                        WHERE %s
                        ORDER BY metadata ->> 'process_id', metadata ->> 'bpanda_version', %s, id
                    ) versions
                    ORDER BY process_id, valid_from DESC NULLS LAST, id
                    LIMIT ?
                    """.formatted(contentColumn, fragment.sql(), userLanguageFirst);
            params.add(language);
        } else {
            String now = LocalDateTime.now().format(ISO_MINUTES);
            sql = """
                    SELECT id, content, metadata FROM (
                        SELECT DISTINCT ON (metadata ->> 'process_id')
                               id::text AS id, %s AS content, metadata::text AS metadata,
                               metadata ->> 'process_name' AS process_name
                        FROM business_process
                        WHERE %s
                        ORDER BY metadata ->> 'process_id',
                                 COALESCE(metadata ->> 'valid_from' <= ?
                                          AND COALESCE(metadata ->> 'valid_to', '9999') > ?, FALSE) DESC,
                                 metadata ->> 'valid_from' DESC NULLS LAST,
                                 %s, id
                    ) latest
                    ORDER BY process_name NULLS LAST, id
                    LIMIT ?
                    """.formatted(contentColumn, fragment.sql(), userLanguageFirst);
            params.add(now);
            params.add(now);
            params.add(language);
        }
        params.add(request.limit() + 1);

        log.debug("DocumentTool business_process SQL: {} params={}", sql, params);
        return jdbcTemplate.query(sql,
                (rs, i) -> DocumentRows.toDocument(rs, DocumentCollection.BUSINESS_PROCESS),
                params.toArray());
    }
}
