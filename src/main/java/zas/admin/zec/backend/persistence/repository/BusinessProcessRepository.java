package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zas.admin.zec.backend.persistence.entity.BusinessProcessEntity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BusinessProcessRepository extends JpaRepository<BusinessProcessEntity, UUID> {

    List<BusinessProcessEntity> findByBpmnIdIn(Collection<String> bpmnIds);

    /** Jointure document -> processus : le processId des liens BPanda vaut le {@code bpanda_id} du processus. */
    @Query(value = """
        SELECT * FROM business_process
        WHERE content ->> 'bpanda_id' IN (:bpandaIds)
        """, nativeQuery = true)
    List<BusinessProcessEntity> findByBpandaIdIn(@Param("bpandaIds") Collection<String> bpandaIds);

    /** Noms de tous les processus, sans charger leur JSON complet (catalogue et recherche par nom). */
    @Query(value = """
        SELECT bpmn_id AS bpmnId, content ->> 'process_name' AS processName
        FROM business_process
        ORDER BY content ->> 'process_name'
        """, nativeQuery = true)
    List<ProcessNameView> findAllNames();

    interface ProcessNameView {
        String getBpmnId();
        String getProcessName();
    }
}
