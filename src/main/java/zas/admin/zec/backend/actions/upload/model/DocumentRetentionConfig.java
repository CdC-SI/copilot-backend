package zas.admin.zec.backend.actions.upload.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DocumentRetentionConfig(
        @NotNull @Positive Integer daysBeforeArchival,
        @NotNull @Positive Integer daysBeforeDeletion
) {}
