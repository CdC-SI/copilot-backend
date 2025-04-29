package zas.admin.zec.backend.actions.converse;

import jakarta.annotation.Nullable;

import java.util.List;

public record FAQMessage(
        String lang,
        String message,
        String source,
        @Nullable Long faqItemId,
        @Nullable List<String> sources
) {
}
