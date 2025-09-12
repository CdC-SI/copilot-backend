package zas.admin.zec.backend.actions.authenticate;

import java.util.UUID;

public record IdentityCheckResponse(UUID vipsTransactionId, String endpoint) {
}
