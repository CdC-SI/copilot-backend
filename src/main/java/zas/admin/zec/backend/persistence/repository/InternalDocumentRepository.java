package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import zas.admin.zec.backend.persistence.entity.InternalDocumentEntity;

import java.util.List;
import java.util.UUID;

public interface InternalDocumentRepository extends JpaRepository<InternalDocumentEntity, UUID> {

    @Query(value = """
    SELECT DISTINCT tag
    FROM (
        -- cas tableau
        SELECT json_array_elements_text(metadata -> 'tags') AS tag
        FROM internal_documents
        WHERE (metadata ->> 'organizations' IS NULL OR metadata ->> 'organizations' = '')
          AND json_typeof(metadata -> 'tags') = 'array'
        UNION
        -- cas string
        SELECT metadata ->> 'tags' AS tag
        FROM internal_documents
        WHERE (metadata ->> 'organizations' IS NULL OR metadata ->> 'organizations' = '')
          AND json_typeof(metadata -> 'tags') = 'string'
    ) t
    WHERE tag IS NOT NULL AND btrim(tag) <> ''
    ORDER BY tag
    """, nativeQuery = true)
    List<String> findPublicTags();

    @Query(value = """
    SELECT DISTINCT tag
    FROM (
        -- cas tableau
        SELECT json_array_elements_text(metadata -> 'tags') AS tag
        FROM internal_documents
        WHERE metadata ->> 'source' IN (:sources)
          AND (metadata ->> 'organizations' IS NULL OR metadata ->> 'organizations' = '')
          AND json_typeof(metadata -> 'tags') = 'array'
        UNION
        -- cas string
        SELECT metadata ->> 'tags' AS tag
        FROM internal_documents
        WHERE metadata ->> 'source' IN (:sources)
          AND (metadata ->> 'organizations' IS NULL OR metadata ->> 'organizations' = '')
          AND json_typeof(metadata -> 'tags') = 'string'
    ) t
    WHERE tag IS NOT NULL AND btrim(tag) <> ''
    ORDER BY tag
    """, nativeQuery = true)
    List<String> findPublicTagsBySources(List<String> sources);

    @Query(value = """
        SELECT tag
        FROM (
               -- cas tableau
               SELECT DISTINCT json_array_elements_text(metadata -> 'tags') AS tag
               FROM internal_documents
               WHERE json_typeof(metadata -> 'tags') = 'array'
               UNION
               -- cas string
               SELECT DISTINCT metadata ->> 'tags' AS tag
               FROM internal_documents
               WHERE json_typeof(metadata -> 'tags') = 'string'
           ) t
        WHERE tag IS NOT NULL AND btrim(tag) <> ''
        ORDER BY tag
        """, nativeQuery = true)
    List<String> findAllTags();

    @Query(value = """
    SELECT DISTINCT tag
    FROM (
        -- cas tableau
        SELECT json_array_elements_text(metadata -> 'tags') AS tag
        FROM internal_documents
        WHERE metadata ->> 'source' IN (:sources)
          AND json_typeof(metadata -> 'tags') = 'array'
        UNION
        -- cas string
        SELECT metadata ->> 'tags' AS tag
        FROM internal_documents
        WHERE metadata ->> 'source' IN (:sources)
          AND json_typeof(metadata -> 'tags') = 'string'
    ) t
    WHERE tag IS NOT NULL AND btrim(tag) <> ''
    ORDER BY tag
    """, nativeQuery = true)
    List<String> findTagsBySources(List<String> sources);

    @Query(value = """
        SELECT DISTINCT metadata ->> 'source' AS source
        FROM internal_documents
        WHERE metadata ->> 'organizations' IS NULL OR metadata ->> 'organizations' = ''
        """, nativeQuery = true)
    List<String> findPublicSources();

    @Query(value = """
        SELECT DISTINCT metadata ->> 'source' AS source
        FROM internal_documents
        """, nativeQuery = true)
    List<String> findAllSources();
}
