package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zas.admin.zec.backend.persistence.entity.ApiClientEntity;

import java.util.UUID;

public interface ApiClientRepository extends JpaRepository<ApiClientEntity, UUID> {
}
