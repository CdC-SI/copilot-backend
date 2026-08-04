package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zas.admin.zec.backend.persistence.entity.DocumentRetentionConfigEntity;

import java.util.Optional;

public interface DocumentRetentionConfigRepository extends JpaRepository<DocumentRetentionConfigEntity, Long> {

    Optional<DocumentRetentionConfigEntity> findFirstByOrderByIdAsc();

}
