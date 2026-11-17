package zas.admin.zec.backend.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import zas.admin.zec.backend.config.properties.ToolApprovalProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Approbation humaine des appels de tools (T1). Pour un tool listé dans {@code zia.tools.approval.required-tools},
 * l'appel est suspendu ({@link #await}) jusqu'à la décision de l'utilisateur ({@link #decide}), l'expiration du délai
 * ou l'arrêt de la génération ({@link #cancelAll}). Une demande n'est accessible qu'à son propriétaire
 * (utilisateur et conversation).
 *
 * <p>L'attente bloque le thread d'exécution du tool : Spring AI exécute les tools d'un flux sur
 * {@code Schedulers.boundedElastic()}.</p>
 */
@Slf4j
@Component
public class ToolApprovalService {

    public enum Decision { APPROVED, REJECTED, EXPIRED, CANCELLED }

    /** Demande en attente, telle qu'annoncée au frontend. */
    public record PendingApproval(String approvalId, Instant expiresAt) {}

    private record Pending(String userId, String conversationId, Instant expiresAt,
                           CompletableFuture<Decision> decision) {
        boolean belongsTo(String userId, String conversationId) {
            return Objects.equals(this.userId, userId) && Objects.equals(this.conversationId, conversationId);
        }
    }

    private final Set<String> requiredTools;
    private final Duration timeout;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public ToolApprovalService(ToolApprovalProperties properties) {
        this.requiredTools = Set.copyOf(properties.requiredTools());
        this.timeout = properties.timeout();
        if (!requiredTools.isEmpty()) {
            log.info("Approbation requise pour les tools {} (délai {})", requiredTools, timeout);
        }
    }

    public boolean requiresApproval(String tool) {
        return requiredTools.contains(tool);
    }

    /** Ouvre une demande d'approbation ; la décision est attendue par {@link #await}. */
    public PendingApproval open(String userId, String conversationId) {
        String approvalId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(timeout);
        pending.put(approvalId, new Pending(userId, conversationId, expiresAt, new CompletableFuture<>()));
        return new PendingApproval(approvalId, expiresAt);
    }

    /**
     * Attend la décision (bloquant), au plus le délai configuré. Le premier verdict l'emporte : une décision arrivée
     * après l'expiration est ignorée. La demande est retirée dans tous les cas.
     */
    public Decision await(String approvalId) {
        Pending request = pending.get(approvalId);
        if (request == null) {
            return Decision.CANCELLED;
        }
        try {
            long remainingMillis = Duration.between(Instant.now(), request.expiresAt()).toMillis();
            if (remainingMillis <= 0) {
                request.decision().complete(Decision.EXPIRED);
                return request.decision().join();
            }
            return request.decision().get(remainingMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            request.decision().complete(Decision.EXPIRED);
            return request.decision().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            request.decision().complete(Decision.CANCELLED);
            return request.decision().join();
        } catch (ExecutionException e) {
            return Decision.CANCELLED;
        } finally {
            pending.remove(approvalId);
        }
    }

    /**
     * Enregistre la décision de l'utilisateur.
     *
     * @return {@code false} si la demande est inconnue, déjà tranchée (expirée, annulée) ou d'un autre utilisateur
     */
    public boolean decide(String userId, String conversationId, String approvalId, boolean approved) {
        Pending request = pending.get(approvalId);
        if (request == null || !request.belongsTo(userId, conversationId)) {
            return false;
        }
        synchronized (request) {
            if (Instant.now().compareTo(request.expiresAt()) >= 0) {
                request.decision().complete(Decision.EXPIRED);
                return false;
            }
            return request.decision().complete(approved ? Decision.APPROVED : Decision.REJECTED);
        }
    }

    /** Arrêt de la génération (Stop, déconnexion) : les demandes en attente de la conversation sont annulées. */
    public void cancelAll(String userId, String conversationId) {
        pending.values().stream()
                .filter(request -> request.belongsTo(userId, conversationId))
                .forEach(request -> request.decision().complete(Decision.CANCELLED));
    }
}
