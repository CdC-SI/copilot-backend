package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import zas.admin.zec.backend.persistence.entity.InternalDocumentEntity;

import java.util.List;
import java.util.UUID;

public interface InternalDocumentRepository extends JpaRepository<InternalDocumentEntity, UUID> {

    @Query(value = """
        SELECT DISTINCT metadata ->> 'tags' AS tag
        FROM internal_documents
        WHERE metadata ->> 'organizations' IS NULL OR metadata ->> 'organizations' = ''
        """, nativeQuery = true)
    List<String> findPublicTags();

    @Query(value = """
        SELECT DISTINCT metadata ->> 'tags' AS tag
        FROM internal_documents
        WHERE metadata ->> 'source' IN :sources
        AND (metadata ->> 'organizations' IS NULL OR metadata ->> 'organizations' = '')
        """, nativeQuery = true)
    List<String> findPublicTagsBySources(List<String> sources);

    @Query(value = """
        SELECT DISTINCT metadata ->> 'tags' AS tag
        FROM internal_documents
        """, nativeQuery = true)
    List<String> findAllTags();

    @Query(value = """
        SELECT DISTINCT metadata ->> 'tags' AS tag
        FROM internal_documents
        WHERE metadata ->> 'source' IN :sources
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
