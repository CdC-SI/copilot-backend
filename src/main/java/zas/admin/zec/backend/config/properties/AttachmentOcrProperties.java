package zas.admin.zec.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuration de l'extraction du texte des pièces jointes de conversation.
 * <p>
 * Les PDF sont envoyés au pdf-ocr-pipeline (API asynchrone {@code /jobs} en {@code mode=extract}, cf.
 * {@link EmbeddingServiceProperties} pour l'URL, les délais de retry, le bail et la taille des lots) ;
 * les images restent traitées par l'OCR LLM.
 *
 * @param engine       moteur d'extraction des PDF : {@code pipeline} (défaut) ou {@code llm} (OCR LLM page par page)
 * @param maxPages     nombre maximal de pages d'un PDF joint, vérifié à l'upload
 * @param pollInterval intervalle entre deux interrogations du statut d'un job (et fréquence du poller)
 * @param jobTimeout   durée maximale d'un job avant passage en erreur
 */
@ConfigurationProperties(prefix = "attachment-ocr")
public record AttachmentOcrProperties(
        @DefaultValue("pipeline") Engine engine,
        @DefaultValue("1000") int maxPages,
        @DefaultValue("5s") Duration pollInterval,
        @DefaultValue("1h") Duration jobTimeout
) {

    public enum Engine { PIPELINE, LLM }

    public boolean usePipeline() {
        return engine == Engine.PIPELINE;
    }
}
