package zas.admin.zec.backend.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zas.admin.zec.backend.persistence.entity.WorkspaceInferenceEntity;
import zas.admin.zec.backend.persistence.repository.WorkspaceInferenceRepository;

import java.time.LocalDateTime;

/**
 * Journalise l'inférence de workspace réalisée par {@link RAGTool} (conversations COMPLETE), afin
 * d'aider à améliorer la classification des workspaces pour les questions futures.
 *
 * <p>Deux cas :</p>
 * <ul>
 *   <li>{@link #recordInference} — appelé quand l'utilisateur n'a pas spécifié de workspace et que
 *       {@code RAGTool} en a inféré un. Enregistre {@code (userId, conversationId, question,
 *       inferredWorkspace)}.</li>
 *   <li>{@link #recordCorrection} — appelé quand la question porte un workspace explicite (signe
 *       que l'utilisateur a corrigé l'inférence précédente et relance la même question). Retrouve la
 *       ligne initiale et renseigne le workspace corrigé.</li>
 * </ul>
 *
 * <p>Le matching se fait sur {@code (userId, conversationId, question)} où {@code question} est la
 * <em>question originale de l'utilisateur</em> (stable entre l'inférence et la relance), et non la
 * requête reformulée par le LLM.</p>
 *
 * <p>Toutes les opérations sont défensives : elles ne doivent jamais interrompre le flux de
 * conversation, d'où les guard clauses et la capture des exceptions.</p>
 */
@Slf4j
@Service
public class WorkspaceInferenceMonitoringService {

    private final WorkspaceInferenceRepository workspaceInferenceRepository;

    public WorkspaceInferenceMonitoringService(WorkspaceInferenceRepository workspaceInferenceRepository) {
        this.workspaceInferenceRepository = workspaceInferenceRepository;
    }

    /**
     * Enregistre une inférence de workspace. Sans effet si une ligne existe déjà pour le même
     * triplet {@code (userId, conversationId, question)}, afin que la recherche de correction reste
     * déterministe.
     */
    public void recordInference(String userId, String conversationId, String question, String inferredWorkspace) {
        if (isBlank(userId) || isBlank(conversationId) || isBlank(question) || isBlank(inferredWorkspace)) {
            return;
        }
        try {
            if (workspaceInferenceRepository.existsByUserUuidAndConversationUuidAndQuestion(userId, conversationId, question)) {
                return;
            }
            var entity = new WorkspaceInferenceEntity();
            entity.setUserUuid(userId);
            entity.setConversationUuid(conversationId);
            entity.setQuestion(question);
            entity.setInferredWorkspace(inferredWorkspace);
            entity.setTimestamp(LocalDateTime.now());
            workspaceInferenceRepository.save(entity);
        } catch (Exception e) {
            log.warn("Échec de l'enregistrement de l'inférence de workspace (user={}, conversation={})",
                    userId, conversationId, e);
        }
    }

    /**
     * Retrouve la ligne d'inférence initiale pour {@code (userId, conversationId, question)} et y
     * renseigne le workspace corrigé fourni par l'utilisateur. Sans effet si aucune ligne n'est
     * trouvée.
     */
    public void recordCorrection(String userId, String conversationId, String question, String correctedWorkspace) {
        if (isBlank(userId) || isBlank(conversationId) || isBlank(question) || isBlank(correctedWorkspace)) {
            return;
        }
        try {
            workspaceInferenceRepository
                    .findFirstByUserUuidAndConversationUuidAndQuestionOrderByTimestampDesc(userId, conversationId, question)
                    .ifPresent(entity -> {
                        entity.setCorrectedWorkspace(correctedWorkspace);
                        workspaceInferenceRepository.save(entity);
                    });
        } catch (Exception e) {
            log.warn("Échec de l'enregistrement de la correction de workspace (user={}, conversation={})",
                    userId, conversationId, e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
