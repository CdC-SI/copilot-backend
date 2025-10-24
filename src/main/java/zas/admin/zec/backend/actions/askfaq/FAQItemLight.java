package zas.admin.zec.backend.actions.askfaq;

import jakarta.annotation.Nullable;

import java.util.List;

public record FAQItemLight(
        @Nullable Integer id,
        String text,
        String answer,
        String url,
        String language,
        @Nullable List<String> tags) {}
