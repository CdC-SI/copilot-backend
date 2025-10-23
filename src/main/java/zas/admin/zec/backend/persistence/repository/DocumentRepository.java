package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import zas.admin.zec.backend.persistence.entity.DocumentEntity;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

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
