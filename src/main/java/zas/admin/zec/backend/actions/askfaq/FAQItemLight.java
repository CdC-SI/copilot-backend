package zas.admin.zec.backend.actions.askfaq;

import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;

public record FAQItemLight(
        @Nullable UUID id,
        String text,
        String answer,
        String url,
        String language,
        @Nullable List<String> tags) {}
