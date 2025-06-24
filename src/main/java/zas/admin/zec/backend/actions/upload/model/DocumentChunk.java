package zas.admin.zec.backend.actions.upload.model;

import jakarta.annotation.Nullable;

public record DocumentChunk(
        String text,
        @Nullable String url,
        @Nullable String source,
        @Nullable String userUuid,
        @Nullable String language
) {}
