package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zas.admin.zec.backend.persistence.entity.BusinessProcessEntity;

import java.util.UUID;

/**
 * Accès CRUD à {@code business_process}. Les recherches par métadonnées (DocumentTool) passent par
 * {@code BusinessProcessLookup} (SQL JSONB paramétré), pas par des requêtes dérivées.
 */
public interface BusinessProcessRepository extends JpaRepository<BusinessProcessEntity, UUID> {
}
