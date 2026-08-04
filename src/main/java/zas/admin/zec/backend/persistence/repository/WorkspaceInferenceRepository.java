package zas.admin.zec.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zas.admin.zec.backend.persistence.entity.WorkspaceInferenceEntity;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceInferenceRepository extends JpaRepository<WorkspaceInferenceEntity, UUID> {

    boolean existsByUserUuidAndConversationUuidAndQuestion(String userUuid, String conversationUuid, String question);

    Optional<WorkspaceInferenceEntity> findFirstByUserUuidAndConversationUuidAndQuestionOrderByTimestampDesc(
            String userUuid, String conversationUuid, String question);
}
