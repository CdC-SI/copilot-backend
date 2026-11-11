package zas.admin.zec.backend.config.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * Configuration du service externe d'embedding (API asynchrone {@code /jobs}).
 *
 * @param baseUrl           URL de base du service
 * @param pollInterval      intervalle entre deux interrogations du statut d'un job (et fréquence du poller)
 * @param retryDelay        délai avant nouvel essai après une erreur transitoire (5xx, réseau, timeout)
 * @param lease             bail posé sur un document pendant son traitement (évite le double traitement entre replicas)
 * @param batchSize         nombre maximal de documents traités par tick du poller
 * @param maxSubmitAttempts nombre maximal de soumissions d'un même document (resoumission après job_lost / result_expired)
 * @param jobTimeout        durée maximale d'un job avant passage en erreur
 * @param requestTimeout    timeout de chaque appel HTTP
 * @param maxResponseSize   taille maximale d'une réponse en mémoire (le résultat contient tous les embeddings)
 */
@ConfigurationProperties(prefix = "embedding-service")
public record EmbeddingServiceProperties(
        @NotNull String baseUrl,
        @DefaultValue("3s") Duration pollInterval,
        @DefaultValue("30s") Duration retryDelay,
        @DefaultValue("5m") Duration lease,
        @DefaultValue("10") int batchSize,
        @DefaultValue("3") int maxSubmitAttempts,
        @DefaultValue("6h") Duration jobTimeout,
        @DefaultValue("60s") Duration requestTimeout,
        @DefaultValue("64MB") DataSize maxResponseSize
) { }

