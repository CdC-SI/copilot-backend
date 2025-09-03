package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zas.admin.zec.backend.persistence.entity.ApiClientKeyEntity;

import java.util.Optional;
import java.util.UUID;

public interface ApiClientKeyRepository extends JpaRepository<ApiClientKeyEntity, UUID> {
    Optional<ApiClientKeyEntity> findByKeyId(String keyId);
    Optional<ApiClientKeyEntity> findActiveByKeyId(String keyId);
}
