package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import zas.admin.zec.backend.persistence.entity.DocumentEntity;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    /**
     * Projection d'un contenu (document ou url) d'une source, dérivé des métadonnées des chunks.
     */
    interface SourceContentProjection {
        String getTitle();
        String getUrl();
    }

    @Query(value = """
        SELECT DISTINCT metadata ->> 'title' AS title, metadata ->> 'url' AS url
        FROM vector_store
        WHERE metadata ->> 'source' = :source
        """, nativeQuery = true)
    List<SourceContentProjection> findDistinctContentsBySource(String source);

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
        SELECT DISTINCT metadata ->> 'tags' AS tag
        FROM vector_store
        WHERE metadata ->> 'organizations' IS NULL OR metadata ->> 'organizations' = ''
        """, nativeQuery = true)
    List<String> findPublicTags();

    @Query(value = """
        SELECT DISTINCT metadata ->> 'tags' AS tag
        FROM vector_store
        WHERE metadata ->> 'source' IN :sources
        AND (metadata ->> 'organizations' IS NULL OR metadata ->> 'organizations' = '')
        """, nativeQuery = true)
    List<String> findPublicTagsBySources(List<String> sources);

    @Query(value = """
        SELECT DISTINCT metadata ->> 'tags' AS tag
        FROM vector_store
        """, nativeQuery = true)
    List<String> findAllTags();

    @Query(value = """
        SELECT DISTINCT metadata ->> 'tags' AS tag
        FROM vector_store
        WHERE metadata ->> 'source' IN :sources
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
