package zas.admin.zec.backend.actions.summarize;

import ch.admin.zas.gaime.dao.domain.doc.DocumentSearchVO;
import ch.admin.zas.gaime.dao.domain.doc.DocumentService;
import ch.admin.zas.gaime.dao.domain.doc.DocumentVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.persistence.entity.SummaryTaskEntity;
import zas.admin.zec.backend.persistence.repository.SummaryTaskRepository;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service dédié au traitement asynchrone des synthèses.
 * Séparé du SummarizeService pour permettre l'utilisation correcte du proxy Spring avec @Async.
 */
@Slf4j
@Service
public class SummarizeAsyncProcessor {

    private final LlmOcrService llmOcrService;
    private final LlmStudySynthesisService llmStudySynthesisService;
    private final DocumentService documentService;
    private final SummaryTaskRepository summaryTaskRepository;

    public SummarizeAsyncProcessor(LlmOcrService llmOcrService,
                                   LlmStudySynthesisService llmStudySynthesisService,
                                   DocumentService documentService,
                                   SummaryTaskRepository summaryTaskRepository) {
        this.llmOcrService = llmOcrService;
        this.llmStudySynthesisService = llmStudySynthesisService;
        this.documentService = documentService;
        this.summaryTaskRepository = summaryTaskRepository;
    }

    /**
     * Traite la synthèse de manière asynchrone.
     * Met à jour la tâche avec le résultat ou l'erreur.
     *
     * @param taskId L'ID de la tâche
     */
    @Async
    @Transactional
    public void processSummarization(Long taskId) {
        log.info("Démarrage du traitement asynchrone pour la tâche ID: {}", taskId);

        SummaryTaskEntity task = summaryTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Tâche introuvable: " + taskId));

        try {
            String navs = task.getNavs();

            // Récupération des documents
            log.debug("Récupération des documents pour NAVS: {}", navs);
            List<DocumentVO> docs = documentService.getDocuments(DocumentSearchVO.builder()
                    .folderId(navs)
                    .formNum("030")
                    .build(), DocumentVO.class);

            if (docs.isEmpty()) {
                log.warn("Aucun document trouvé pour NAVS: {}", navs);
                task.setStatus(SummaryTaskStatus.ERREUR);
                task.setErrorMessage("Aucun document trouvé pour ce numéro AVS");
                summaryTaskRepository.save(task);
                return;
            }

            // Extraction du texte des documents
            log.debug("Extraction du texte de {} document(s)", docs.size());
            List<String> documentContents = docs.stream()
                    .map(this::extractTextFromDocument)
                    .toList();

            // Génération de la synthèse
            log.debug("Génération de la synthèse pour la tâche ID: {}", taskId);
            String summary = llmStudySynthesisService.summarizeStudies(documentContents);

            // Mise à jour de la tâche avec succès
            task.setStatus(SummaryTaskStatus.TERMINEE);
            task.setSummaryMarkdown(summary);
            task.setReferences(docs.stream().map(DocumentVO::getObjectToken).collect(Collectors.toList()));
            summaryTaskRepository.save(task);

            log.info("Synthèse terminée avec succès pour la tâche ID: {} (NAVS: {})", taskId, navs);

        } catch (Exception e) {
            log.error("Erreur lors du traitement de la tâche ID: {}", taskId, e);

            task.setStatus(SummaryTaskStatus.ERREUR);
            task.setErrorMessage("Erreur: " + e.getMessage());
            summaryTaskRepository.save(task);
        }
    }

    private String extractTextFromDocument(DocumentVO document) {
        try {
            byte[] documentContent = documentService.getDocumentContent(document);
            return llmOcrService.ocrPdf(documentContent);
        } catch (IOException e) {
            log.warn("Impossible d'extraire le texte du document: {}", document.getObjectToken(), e);
            return "Pas de contenu disponible pour ce document : " + document.getObjectToken();
        }
    }
}

