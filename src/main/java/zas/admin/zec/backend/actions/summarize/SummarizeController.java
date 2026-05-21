package zas.admin.zec.backend.actions.summarize;

import ch.admin.zas.common.security.users.ZasUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zas.admin.zec.backend.actions.summarize.jms.GaimeJmsService;
import zas.admin.zec.backend.config.security.RequireAdmin;

import java.util.List;
import java.util.Map;

@Slf4j
@RequireAdmin
@RestController
@RequestMapping("/api/summaries")
public class SummarizeController {

    private static final String KEY_ERROR = "error";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_TASK_ID = "taskId";
    private static final String KEY_REFERENCES_COUNT = "referencesCount";
    private static final String MSG_UNAUTHORIZED = "Utilisateur non authentifié";
    private static final String MSG_NO_REFERENCES = "Aucune référence disponible pour cette tâche";
    private static final String MSG_SEND_SUCCESS = "Demande d'affichage envoyée avec succès";
    private static final String MSG_SEND_ERROR = "Erreur lors de l'envoi du message";
    private static final String ERR_TASK_NOT_FOUND = "Tâche introuvable";
    private static final String ERR_TASK_NOT_COMPLETED = "Tâche non terminée";

    private final SummarizeService summarizeService;
    private final GaimeJmsService gaimeJmsService;

    public SummarizeController(SummarizeService summarizeService, GaimeJmsService gaimeJmsService) {
        this.summarizeService = summarizeService;
        this.gaimeJmsService = gaimeJmsService;
    }

    /**
     * Démarre une tâche de synthèse pour un numéro AVS.
     * Si une synthèse terminée existe déjà, retourne cette tâche sans relancer le traitement.
     *
     * @param navs Le numéro AVS
     * @return La tâche créée ou existante
     */
    @PostMapping("/{navs}")
    public ResponseEntity<SummaryTaskCreatedResponse> createSummary(@PathVariable String navs) {
        log.info("Demande de création de synthèse pour NAVS: {}", navs);

        SummaryTaskCreatedResponse response = summarizeService.startSummarization(navs);

        // Si la tâche existait déjà terminée, on retourne 200 OK, sinon 201 CREATED
        HttpStatus status = response.status() == SummaryTaskStatus.TERMINEE ? HttpStatus.OK : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Récupère la liste de toutes les tâches de synthèse.
     *
     * @return Liste des tâches avec leur statut
     */
    @GetMapping
    public ResponseEntity<List<SummaryTaskResponse>> getAllTasks() {
        log.debug("Récupération de toutes les tâches de synthèse");
        List<SummaryTaskResponse> tasks = summarizeService.getAllTasks();
        return ResponseEntity.ok(tasks);
    }

    /**
     * Récupère le détail d'une synthèse terminée (avec le contenu Markdown).
     * Retourne une erreur 409 si la tâche n'est pas encore terminée.
     *
     * @param id L'ID de la tâche
     * @return Le détail de la synthèse avec le contenu Markdown
     */
    @GetMapping("/{id}")
    public ResponseEntity<SummaryDetailResponse> getSummaryDetail(@PathVariable Long id) {
        log.debug("Récupération du détail de la tâche ID: {}", id);

        try {
            SummaryDetailResponse detail = summarizeService.getSummaryDetail(id);
            return ResponseEntity.ok(detail);
        } catch (TaskNotFoundException e) {
            log.warn("Tâche introuvable: {}", e.getMessage());
            throw e; // Sera géré par @ControllerAdvice
        } catch (TaskNotCompletedException e) {
            log.warn("Tâche non terminée: {}", e.getMessage());
            throw e; // Sera géré par @ControllerAdvice
        }
    }

    /**
     * Relance l'analyse d'une tâche existante.
     * Repasse le statut à EN_COURS et relance le traitement asynchrone.
     *
     * @param id L'ID de la tâche à relancer
     * @return La tâche avec son nouveau statut EN_COURS
     */
    @PutMapping("/{id}")
    public ResponseEntity<SummaryTaskCreatedResponse> restartSummary(@PathVariable Long id) {
        log.info("Demande de relance de la tâche ID: {}", id);

        SummaryTaskCreatedResponse response = summarizeService.restartSummarization(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Affiche les références d'une tâche terminée en envoyant un message JMS.
     * Les références seront affichées dans l'application tierce GAIME.
     *
     * @param id L'ID de la tâche
     * @return Un message de confirmation
     */
    @PostMapping("/{id}/open-references")
    public ResponseEntity<Map<String, Object>> openReferences(@PathVariable Long id, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof ZasUser user)) {
            log.error("Utilisateur non authentifié ou de type incorrect");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Map.of(KEY_ERROR, MSG_UNAUTHORIZED)
            );
        }

        log.info("Demande d'affichage des références pour la tâche ID: {} (visa: {})", id, user.getTrigramme());
        try {
            List<String> references = summarizeService.getTaskReferences(id);

            if (references.isEmpty()) {
                log.warn("Aucune référence à afficher pour la tâche ID: {}", id);
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body(
                        Map.of(
                                KEY_MESSAGE, MSG_NO_REFERENCES,
                                KEY_TASK_ID, id
                        )
                );
            }

            gaimeJmsService.sendOpenDocumentsMessage(user.getTrigramme(), references);

            log.info("Message JMS envoyé avec succès pour {} référence(s)", references.size());
            return ResponseEntity.ok(
                    Map.of(
                            KEY_MESSAGE, MSG_SEND_SUCCESS,
                            KEY_TASK_ID, id,
                            KEY_REFERENCES_COUNT, references.size()
                    )
            );

        } catch (TaskNotFoundException | TaskNotCompletedException e) {
            log.warn("Tâche introuvable ou non terminée : {}", e.getMessage());
            throw e;
        } catch (JsonProcessingException e) {
            log.error("Erreur lors de la sérialisation du message JMS", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    Map.of(
                            KEY_ERROR, MSG_SEND_ERROR,
                            KEY_MESSAGE, e.getMessage()
                    )
            );
        }
    }

    /**
     * Gestion des exceptions spécifiques aux tâches de synthèse.
     */
    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleTaskNotFound(TaskNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(KEY_ERROR, ERR_TASK_NOT_FOUND, KEY_MESSAGE, e.getMessage()));
    }

    @ExceptionHandler(TaskNotCompletedException.class)
    public ResponseEntity<Map<String, String>> handleTaskNotCompleted(TaskNotCompletedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(KEY_ERROR, ERR_TASK_NOT_COMPLETED, KEY_MESSAGE, e.getMessage()));
    }
}
