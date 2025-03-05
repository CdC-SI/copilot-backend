package zas.admin.zec.backend.actions.askfaq;

import jakarta.annotation.Nullable;

public record FAQItemLight(@Nullable Integer id, String text, String answer, String url, String language) {}
