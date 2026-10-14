package zas.admin.zec.backend.tools.document;

import org.springframework.ai.document.Document;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import zas.admin.zec.backend.tools.MetadataFormatter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** Conversion d'une ligne {@code (id, content, metadata)} en {@link Document} Spring AI. */
final class DocumentRows {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private DocumentRows() {}

    static Document toDocument(ResultSet rs, DocumentCollection collection) throws SQLException {
        String metadataJson = rs.getString("metadata");
        Map<String, Object> metadata = new HashMap<>();
        if (metadataJson != null) {
            // Spring AI refuse les valeurs nulles dans les métadonnées (ex. "purpose": null).
            JSON.readValue(metadataJson, MAP_TYPE).forEach((k, v) -> {
                if (k != null && v != null) {
                    metadata.put(k, v);
                }
            });
        }
        metadata.put(MetadataFormatter.META_COLLECTION, collection.key());
        String content = rs.getString("content");
        return Document.builder()
                .id(rs.getString("id"))
                .text(content != null ? content : "")
                .metadata(metadata)
                .build();
    }
}
