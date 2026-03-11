package zas.admin.zec.backend.actions.sourcerequest;

import java.time.LocalDateTime;

/**
 * DTO représentant une demande de source complète.
 */
public record SourceRequestDto(
    Long id,
    String sourceName,
    String description,
    String requesterUsername,
    RequestStatus status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
