package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zas.admin.zec.backend.persistence.entity.DocumentEntity;

import java.util.List;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Integer> {

    @Query(value = """
              SELECT DISTINCT d.tags
              FROM document d
              WHERE d.user_uuid = :userId
                OR (d.user_uuid IS NULL AND d.organizations && :organizations)
                OR (d.user_uuid IS NULL AND (d.organizations IS NULL OR cardinality(d.organizations) = 0))
              """,
            nativeQuery = true
    )
    List<String> findTags(@Param("userId") String userId, @Param("organizations") String[] organizations);

    @Query(value = """
              SELECT DISTINCT d.source_id
              FROM document d
              WHERE d.user_uuid = :userId
                OR (d.user_uuid IS NULL AND d.organizations && :organizations)
                OR (d.user_uuid IS NULL AND (d.organizations IS NULL OR cardinality(d.organizations) = 0))
              """,
            nativeQuery = true
    )
    List<Integer> findSourceIds(@Param("userId") String userId, @Param("organizations") String[] organizations);

    @Query(
            value = """
                    SELECT *
                    FROM document d
                    ORDER BY d.text_embedding <=> CAST(:textEmbedding AS vector(1536))
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<DocumentEntity> findNearestsByTextEmbedding(
            @Param("textEmbedding") String textEmbedding,
            @Param("limit") int limit
    );
}
