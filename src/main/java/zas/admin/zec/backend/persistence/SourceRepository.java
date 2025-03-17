package zas.admin.zec.backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SourceRepository extends JpaRepository<SourceEntity, Integer> {
    Optional<SourceEntity> findByUrl(String url);
}
