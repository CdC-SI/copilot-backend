package zas.admin.zec.backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
