package zas.admin.zec.backend.actions.sourcerequest;

import jakarta.validation.constraints.NotNull;

/**
 * Requête pour mettre à jour le statut d'une demande de source.
 */
public record UpdateStatusRequest(
    @NotNull(message = "Le statut est obligatoire")
    RequestStatus status
) {}
