package zas.admin.zec.backend.tools.api;

import java.time.Instant;

public record GeneratedKey(String keyId, String secret, String label, Instant expiresAt) {
}
