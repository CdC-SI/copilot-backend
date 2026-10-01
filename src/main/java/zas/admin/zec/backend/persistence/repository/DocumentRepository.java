package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import zas.admin.zec.backend.persistence.entity.DocumentEntity;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    /**
     * Une ligne par tag : les tags stockés en tableau JSON sont dépliés, une valeur scalaire (format historique,
     * ex. {@code "a,b"} pour la FAQ) ou une clé absente ({@code null}) est renvoyée telle quelle.
     */
    String TAGS_FROM = """
        FROM vector_store v
        CROSS JOIN LATERAL (
            SELECT json_array_elements_text(v.metadata -> 'tags') WHERE json_typeof(v.metadata -> 'tags') = 'array'
            UNION ALL
            SELECT v.metadata ->> 'tags' WHERE json_typeof(v.metadata -> 'tags') IS DISTINCT FROM 'array'
        ) AS t(tag)
        """;

    /**
     * Projection d'un contenu (document ou url) d'une source, dérivé des métadonnées des chunks.
     */
    interface SourceContentProjection {
        String getTitle();
        String getUrl();
    }

    /**
     * Contenus d'une source restreints à ceux accessibles à l'utilisateur : soit rattachés à
     * l'utilisateur ({@code metadata->>'user_uuid'} égal à {@code userId}), soit publics
     * ({@code user_uuid} nul, vide ou absent des métadonnées).
     */
    @Query(value = """
        SELECT DISTINCT metadata ->> 'title' AS title, metadata ->> 'url' AS url
        FROM vector_store
        WHERE metadata ->> 'source' = :source
        AND (
            metadata ->> 'user_uuid' = :userId
            OR metadata ->> 'user_uuid' IS NULL
            OR metadata ->> 'user_uuid' = ''
        )
        """, nativeQuery = true)
    List<SourceContentProjection> findDistinctContentsBySourceAndUser(String source, String userId);

    @Query(value = """
        SELECT DISTINCT metadata ->> 'title' AS title
        FROM vector_store
        WHERE metadata ->> 'source' = :source
        AND metadata ->> 'title' IS NOT NULL
        """, nativeQuery = true)
    List<String> findDistinctTitlesBySource(String source);

    @Modifying
    @Query(value = """
        DELETE FROM vector_store
        WHERE metadata ->> 'source' = :source
        """, nativeQuery = true)
    int deleteBySource(String source);

    @Query(value = """
        SELECT DISTINCT t.tag AS tag
        """ + TAGS_FROM + """
        WHERE v.metadata ->> 'organizations' IS NULL OR v.metadata ->> 'organizations' = ''
        """, nativeQuery = true)
    List<String> findPublicTags();

    @Query(value = """
        SELECT DISTINCT t.tag AS tag
        """ + TAGS_FROM + """
        WHERE v.metadata ->> 'source' IN :sources
        AND (v.metadata ->> 'organizations' IS NULL OR v.metadata ->> 'organizations' = '')
        """, nativeQuery = true)
    List<String> findPublicTagsBySources(List<String> sources);

    @Query(value = """
        SELECT DISTINCT t.tag AS tag
        """ + TAGS_FROM, nativeQuery = true)
    List<String> findAllTags();

    @Query(value = """
        SELECT DISTINCT t.tag AS tag
        """ + TAGS_FROM + """
        WHERE v.metadata ->> 'source' IN :sources
        """, nativeQuery = true)
    List<String> findTagsBySources(List<String> sources);

    @Query(value = """
        SELECT DISTINCT metadata ->> 'source' AS source
        FROM vector_store
        WHERE metadata ->> 'organizations' IS NULL OR metadata ->> 'organizations' = ''
        """, nativeQuery = true)
    List<String> findPublicSources();

    @Query(value = """
        SELECT DISTINCT metadata ->> 'source' AS source
        FROM vector_store
        """, nativeQuery = true)
    List<String> findAllSources();

    @Query(value = """
        SELECT *
        FROM vector_store
        WHERE metadata ->> 'answer_id' = :answerId
        LIMIT 1
        """, nativeQuery = true)
    DocumentEntity findByAnswerId(String answerId);
}
