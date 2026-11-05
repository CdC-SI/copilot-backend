package zas.admin.zec.backend.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Processus métier joints d'office au contexte de l'agent (bloc {@code <linked_processes>}) : processus liés aux
 * documents retournés par la recherche, ou processus en cours après une question de suivi.
 *
 * @param enabled      interrupteur (désactivé : seul le bloc {@code <linked_sources>} signale les processus liés)
 * @param maxProcesses nombre maximal de processus joints par recherche ou par tour de suite
 * @param maxChars     taille cumulée maximale (caractères) des processus joints ; au-delà, un processus n'est pas joint
 */
@Validated
@ConfigurationProperties(prefix = "rag.linked-processes")
public record LinkedProcessProperties(
        @NotNull Boolean enabled,
        @NotNull @Min(1) Integer maxProcesses,
        @NotNull @Min(1) Integer maxChars) {}
