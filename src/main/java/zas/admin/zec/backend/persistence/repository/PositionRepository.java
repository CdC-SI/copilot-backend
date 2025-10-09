package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zas.admin.zec.backend.persistence.entity.tariff.PositionEntity;

public interface PositionRepository extends JpaRepository<PositionEntity, String> {
}
