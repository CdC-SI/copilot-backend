package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zas.admin.zec.backend.persistence.entity.InternalDocumentEntity;

import java.util.UUID;

public interface InternalDocumentRepository extends JpaRepository<InternalDocumentEntity, UUID> {
}
