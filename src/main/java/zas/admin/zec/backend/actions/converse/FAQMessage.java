package zas.admin.zec.backend.actions.converse;

import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;

public record FAQMessage(
        String lang,
        String message,
        String source,
        @Nullable UUID faqItemId,
        @Nullable List<Source> sources
) {
}
