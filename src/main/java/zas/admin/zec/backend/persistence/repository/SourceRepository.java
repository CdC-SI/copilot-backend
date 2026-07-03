package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zas.admin.zec.backend.persistence.entity.SourceEntity;

import java.util.List;
import java.util.Optional;

public interface SourceRepository extends JpaRepository<SourceEntity, Long> {

    Optional<SourceEntity> findByName(String name);

    boolean existsByName(String name);

    List<SourceEntity> findAllByOrderByNameAsc();
}
