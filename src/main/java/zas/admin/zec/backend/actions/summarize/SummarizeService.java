package zas.admin.zec.backend.actions.summarize;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.persistence.entity.SummaryTaskEntity;
import zas.admin.zec.backend.persistence.repository.SummaryTaskRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class SummarizeService {

    private static final String MSG_TASK_NOT_FOUND = "Tâche introuvable avec l'ID: ";
    private static final String MSG_TASK_NOT_COMPLETED = "La tâche n'est pas encore terminée. Statut actuel: ";
    private static final String MSG_EXISTING_SUMMARY = "Une synthèse terminée existe déjà pour ce numéro AVS";
    private static final String MSG_TASK_STARTED = "Traitement de synthèse démarré";
    private static final String MSG_TASK_RESTARTED = "Traitement de synthèse relancé";

    private final SummaryTaskRepository summaryTaskRepository;
    private final SummarizeAsyncProcessor asyncProcessor;

    public SummarizeService(SummaryTaskRepository summaryTaskRepository,
                            SummarizeAsyncProcessor asyncProcessor) {
        this.summaryTaskRepository = summaryTaskRepository;
        this.asyncProcessor = asyncProcessor;
    }

    /**
     * Démarre une synthèse pour le numéro AVS donné.
     * Si une synthèse terminée existe déjà, la retourne sans relancer le traitement.
     * Sinon, crée une nouvelle tâche et lance le traitement asynchrone.
     *
     * @param navs Le numéro AVS
     * @return L'ID de la tâche créée ou existante
     */
    @Transactional
    public SummaryTaskCreatedResponse startSummarization(String navs) {
        log.info("Demande de synthèse pour NAVS: {}", navs);

        // Vérifier si une synthèse terminée existe déjà
        Optional<SummaryTaskEntity> existingTask = summaryTaskRepository.findByNavsAndStatus(navs, SummaryTaskStatus.TERMINEE);
        if (existingTask.isPresent()) {
            log.info("Synthèse déjà terminée pour NAVS: {} (task ID: {})", navs, existingTask.get().getId());
            return new SummaryTaskCreatedResponse(
                    existingTask.get().getId(),
                    navs,
                    SummaryTaskStatus.TERMINEE,
                    MSG_EXISTING_SUMMARY
            );
        }

        // Créer une nouvelle tâche
        SummaryTaskEntity task = new SummaryTaskEntity();
        task.setNavs(navs);
        task.setStatus(SummaryTaskStatus.EN_COURS);
        task = summaryTaskRepository.save(task);

        log.info("Nouvelle tâche de synthèse créée avec ID: {} pour NAVS: {}", task.getId(), navs);

        // Lancer le traitement asynchrone
        asyncProcessor.processSummarization(task.getId());

        return new SummaryTaskCreatedResponse(
                task.getId(),
                navs,
                SummaryTaskStatus.EN_COURS,
                MSG_TASK_STARTED
        );
    }

    /**
     * Relance l'analyse d'une tâche existante.
     * Repasse le statut à EN_COURS et relance le traitement asynchrone.
     *
     * @param taskId L'ID de la tâche à relancer
     * @return La réponse avec le nouveau statut
     * @throws TaskNotFoundException Si la tâche n'existe pas
     */
    @Transactional
    public SummaryTaskCreatedResponse restartSummarization(Long taskId) {
        log.info("Demande de relance de la tâche ID: {}", taskId);

        SummaryTaskEntity task = summaryTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(MSG_TASK_NOT_FOUND + taskId));

        // Réinitialiser le statut et effacer les anciennes données
        task.setCreatedAt(LocalDateTime.now());
        task.setStatus(SummaryTaskStatus.EN_COURS);
        task.setSummaryMarkdown(null);
        task.setErrorMessage(null);
        task = summaryTaskRepository.save(task);

        log.info("Tâche ID: {} repassée en EN_COURS, relance du traitement asynchrone", taskId);

        // Relancer le traitement asynchrone
        asyncProcessor.processSummarization(taskId);

        return new SummaryTaskCreatedResponse(
                task.getId(),
                task.getNavs(),
                SummaryTaskStatus.EN_COURS,
                MSG_TASK_RESTARTED
        );
    }

    /**
     * Récupère toutes les tâches de synthèse, triées par date de création décroissante.
     *
     * @return Liste des tâches
     */
    public List<SummaryTaskResponse> getAllTasks() {
        return summaryTaskRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toTaskResponse)
                .toList();
    }

    /**
     * Récupère le détail d'une tâche terminée avec son résumé.
     *
     * @param taskId L'ID de la tâche
     * @return Le détail de la synthèse
     * @throws TaskNotFoundException Si la tâche n'existe pas
     * @throws TaskNotCompletedException Si la tâche n'est pas terminée
     */
    public SummaryDetailResponse getSummaryDetail(Long taskId) {
        SummaryTaskEntity task = summaryTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(MSG_TASK_NOT_FOUND + taskId));

        if (task.getStatus() != SummaryTaskStatus.TERMINEE) {
            throw new TaskNotCompletedException(MSG_TASK_NOT_COMPLETED + task.getStatus());
        }

        return new SummaryDetailResponse(
                task.getId(),
                task.getNavs(),
                task.getSummaryMarkdown(),
                task.getUpdatedAt()
        );
    }

    /**
     * Récupère les références d'une tâche terminée.
     *
     * @param taskId L'ID de la tâche
     * @return La liste des références (objTokens)
     * @throws TaskNotFoundException Si la tâche n'existe pas
     * @throws TaskNotCompletedException Si la tâche n'est pas terminée
     */
    @Transactional
    public List<String> getTaskReferences(Long taskId) {
        log.debug("Récupération des références pour la tâche ID: {}", taskId);

        SummaryTaskEntity task = summaryTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(MSG_TASK_NOT_FOUND + taskId));

        if (task.getStatus() != SummaryTaskStatus.TERMINEE) {
            throw new TaskNotCompletedException(MSG_TASK_NOT_COMPLETED + task.getStatus());
        }

        List<String> references = task.getReferences();
        if (references == null || references.isEmpty()) {
            log.warn("Aucune référence trouvée pour la tâche ID: {}", taskId);
            return List.of();
        }

        log.info("Récupération de {} référence(s) pour la tâche ID: {}", references.size(), taskId);
        return references;
    }

    private SummaryTaskResponse toTaskResponse(SummaryTaskEntity task) {
        return new SummaryTaskResponse(
                task.getId(),
                task.getNavs(),
                task.getStatus(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
