package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zas.admin.zec.backend.persistence.entity.WorkspaceEntity;

import java.util.List;
import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, Long> {

    Optional<WorkspaceEntity> findByName(String name);

    boolean existsByName(String name);

    List<WorkspaceEntity> findAllByOrderByNameAsc();

    @Query("""
            SELECT s.name FROM WorkspaceEntity w
            JOIN w.sources s
            WHERE w.name = :workspaceName
            """)
    List<String> findSourceNamesByWorkspaceName(@Param("workspaceName") String workspaceName);
}
