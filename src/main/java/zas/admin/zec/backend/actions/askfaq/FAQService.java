package zas.admin.zec.backend.actions.askfaq;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import zas.admin.zec.backend.config.properties.FAQSearchProperties;
import zas.admin.zec.backend.persistence.FAQItemRepository;
import zas.admin.zec.backend.tools.FAQItemMapper;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Service
public class FAQService {

    private final FAQItemRepository faqItemRepository;
    private final FAQSearchProperties faqSearchProperties;
    private final FAQItemMapper faqItemMapper;
    private final EmbeddingModel embeddingModel;
    private final FAQCache faqCache;

    public FAQService(FAQItemRepository faqItemRepository, FAQSearchProperties faqSearchProperties,
                      FAQItemMapper faqItemMapper, EmbeddingModel embeddingModel, FAQCache faqCache) {

        this.faqItemRepository = faqItemRepository;
        this.faqSearchProperties = faqSearchProperties;
        this.faqItemMapper = faqItemMapper;
        this.embeddingModel = embeddingModel;
        this.faqCache = faqCache;
    }

    public List<FAQItem> getExistingFAQItemsByMatchingQuestion(String question) {
        var byWordSimilarity = getExistingFAQItemsByWordSimilarity(question);
        if (faqSearchProperties.minResultBeforeSemanticSearch() <= byWordSimilarity.size()) {
            return byWordSimilarity;
        } else {
            var itemsFromCache = faqCache.get(question);
            if (itemsFromCache != null) {
                return itemsFromCache;
            }

            var bySemanticSimilarity = getExistingFAQItemsBySemanticSimilarity(question);
            faqCache.put(question, bySemanticSimilarity);
            return Stream.concat(byWordSimilarity.stream(), bySemanticSimilarity.stream())
                    .distinct()
                    .toList();
        }
    }

    private List<FAQItem> getExistingFAQItemsByWordSimilarity(String question) {
        var properties = faqSearchProperties.trigramMatching();
        var byWordSimilarity = faqItemRepository.findByWordSimilarity(
                question, properties.threshold(), properties.limit());

        return byWordSimilarity
                .stream()
                .map(faqItemMapper::map)
                .toList();
    }

    private List<FAQItem> getExistingFAQItemsBySemanticSimilarity(String question) {
        var properties = faqSearchProperties.semanticMatching();
        var questionEmbedding = getFAQItemTextEmbedding(question);
        var nearestByTextEmbedding = faqItemRepository.findNearestsByTextEmbedding(
                questionEmbedding, properties.limit());

        return nearestByTextEmbedding
                .stream()
                .map(faqItemMapper::map)
                .toList();
    }

    private String getFAQItemTextEmbedding(String question) {
        return Arrays.toString(embeddingModel.embed(question));
    }
}
