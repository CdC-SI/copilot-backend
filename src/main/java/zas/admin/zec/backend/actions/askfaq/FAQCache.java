package zas.admin.zec.backend.actions.askfaq;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import zas.admin.zec.backend.tools.HashUtils;

import java.time.Duration;
import java.util.List;

@Component
public final class FAQCache {

    private final Cache<String, List<FAQItem>> cache;

    private FAQCache() {
        cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(30))
                .build();
    }

    public void put(String question, List<FAQItem> faqItems) {
        var key = HashUtils.sha256(question);
        cache.put(key, faqItems);
    }

    public List<FAQItem> get(String question) {
        var key = HashUtils.sha256(question);
        return cache.getIfPresent(key);
    }
}
