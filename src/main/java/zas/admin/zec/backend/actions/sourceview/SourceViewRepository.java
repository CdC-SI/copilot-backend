package zas.admin.zec.backend.actions.sourceview;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lecture d'un chunk ({@code vector_store}) ou d'un processus métier ({@code business_process}) par identifiant,
 * pour l'affichage d'une source citée.
 */
@Repository
public class SourceViewRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /** Règle d'accès des chunks : documents publics ({@code user_uuid} absent ou vide) et documents de l'utilisateur. */
    private static final String CHUNK_SQL = """
            SELECT id::text AS id, content, metadata::text AS metadata
            FROM vector_store
            WHERE id = ?
              AND (metadata ->> 'user_uuid' IS NULL OR metadata ->> 'user_uuid' = '' OR metadata ->> 'user_uuid' = ?)
            """;
    private static final String PROCESS_SQL = """
            SELECT id::text AS id, NULL::text AS content, metadata::text AS metadata
            FROM business_process
            WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public SourceViewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Ligne lue : texte du chunk ({@code null} pour un processus) et métadonnées. */
    public record StoredDocument(String id, String content, Map<String, Object> metadata) {}

    /** Chunk visible par l'utilisateur ; vide s'il n'existe pas ou s'il s'agit du document personnel d'un autre utilisateur. */
    public Optional<StoredDocument> findAccessibleChunk(UUID id, String userId) {
        return jdbcTemplate.query(CHUNK_SQL, SourceViewRepository::toStoredDocument, id, userId).stream().findFirst();
    }

    /** Version d'un processus métier (visible par tous les utilisateurs, comme dans {@code get_documents}). */
    public Optional<StoredDocument> findProcess(UUID id) {
        return jdbcTemplate.query(PROCESS_SQL, SourceViewRepository::toStoredDocument, id).stream().findFirst();
    }

    private static StoredDocument toStoredDocument(ResultSet rs, int rowNum) throws SQLException {
        String metadataJson = rs.getString("metadata");
        Map<String, Object> metadata = metadataJson != null ? JSON.readValue(metadataJson, MAP_TYPE) : new HashMap<>();
        return new StoredDocument(rs.getString("id"), rs.getString("content"), metadata);
    }
}
