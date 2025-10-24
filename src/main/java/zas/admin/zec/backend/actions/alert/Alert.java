package zas.admin.zec.backend.actions.alert;

import jakarta.annotation.Nullable;

import java.time.LocalDateTime;

public record Alert(
        @Nullable Long id,
        AlertLevel level,
        @Nullable String textFr,
        @Nullable String textDe,
        @Nullable String textIt,
        @Nullable LocalDateTime expiresAt
) {}
