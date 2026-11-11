package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zas.admin.zec.backend.persistence.entity.FeedbackReportConfigEntity;

import java.util.Optional;

public interface FeedbackReportConfigRepository extends JpaRepository<FeedbackReportConfigEntity, Long> {

    Optional<FeedbackReportConfigEntity> findFirstByOrderByIdAsc();
}
