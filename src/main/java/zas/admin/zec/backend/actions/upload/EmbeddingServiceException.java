package zas.admin.zec.backend.actions.upload;

import lombok.Getter;

import java.util.Set;

/**
 * Erreur renvoyée par le service externe d'embedding.
 * {@code httpStatus} vaut {@code null} pour les erreurs réseau / timeout (considérées transitoires).
 */
@Getter
public class EmbeddingServiceException extends RuntimeException {

    public static final String CODE_JOB_LOST = "job_lost";
    public static final String CODE_RESULT_EXPIRED = "result_expired";

    private static final Set<Integer> TRANSIENT_CLIENT_ERRORS = Set.of(401, 403, 408, 409, 429);

    private final Integer httpStatus;
    private final String code;

    public EmbeddingServiceException(Integer httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public EmbeddingServiceException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = null;
        this.code = null;
    }

    /** Le job n'existe plus côté service (redémarrage, TTL du résultat écoulé, id inconnu) : il faut resoumettre. */
    public boolean isJobGone() {
        return httpStatus != null && (httpStatus == 404 || httpStatus == 410);
    }

    /** Le résultat n'est pas encore disponible ({@code 409} sur {@code /result}). */
    public boolean isNotReady() {
        return httpStatus != null && httpStatus == 409;
    }

    /** Erreur client définitive (document invalide, trop volumineux...) : inutile de réessayer. */
    public boolean isPermanent() {
        return httpStatus != null && httpStatus >= 400 && httpStatus < 500
                && !TRANSIENT_CLIENT_ERRORS.contains(httpStatus) && !isJobGone();
    }
}
