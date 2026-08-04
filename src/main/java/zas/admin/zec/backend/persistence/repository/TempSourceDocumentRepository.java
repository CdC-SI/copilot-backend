package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import zas.admin.zec.backend.actions.upload.model.AvailabilityStatus;
import zas.admin.zec.backend.persistence.entity.TempSourceDocumentEntity;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TempSourceDocumentRepository extends JpaRepository<TempSourceDocumentEntity, Long> {

    Optional<TempSourceDocumentEntity> findByFileName(String fileName);
    Optional<TempSourceDocumentEntity> findByFileNameAndUserUuid(String filename, String userUuid);
    List<TempSourceDocumentEntity> findAllByUserUuid(String userUuid);

    List<TempSourceDocumentEntity> findAllByAvailabilityStatusAndUserUuidNotNullAndUploadedAtBefore(
            AvailabilityStatus availabilityStatus, LocalDateTime uploadedAtThreshold);

    List<TempSourceDocumentEntity> findAllByAvailabilityStatusAndUserUuidNotNullAndArchivedAtBefore(
            AvailabilityStatus availabilityStatus, LocalDateTime archivedAtThreshold);

    @Modifying
    int deleteByFileNameIn(Collection<String> fileNames);

}
