package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zas.admin.zec.backend.actions.summarize.SummaryTaskStatus;
import zas.admin.zec.backend.persistence.entity.SummaryTaskEntity;

import java.util.List;
import java.util.Optional;

public interface SummaryTaskRepository extends JpaRepository<SummaryTaskEntity, Long> {

    Optional<SummaryTaskEntity> findByNavs(String navs);

    Optional<SummaryTaskEntity> findByNavsAndStatus(String navs, SummaryTaskStatus status);

    List<SummaryTaskEntity> findAllByOrderByCreatedAtDesc();

    Optional<SummaryTaskEntity> findByIdAndStatus(Long id, SummaryTaskStatus status);
}

