package zas.admin.zec.backend.actions.askfaq;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import zas.admin.zec.backend.config.properties.FAQSearchProperties;
import zas.admin.zec.backend.persistence.DocumentEntity;
import zas.admin.zec.backend.persistence.FAQItemEntity;
import zas.admin.zec.backend.persistence.FAQItemRepository;
import zas.admin.zec.backend.persistence.SourceEntity;
import zas.admin.zec.backend.tools.FAQItemMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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

    public FAQItem save(FAQItemLight faqItem) {
        return faqItem.id() != null
                ? update(faqItem)
                : create(faqItem);
    }

    private FAQItem create(FAQItemLight faqItemLight) {
        SourceEntity source = new SourceEntity();
        source.setUrl(faqItemLight.url());

        DocumentEntity answer = new DocumentEntity();
        answer.setSource(source);
        answer.setUrl(faqItemLight.url());
        answer.setLanguage(faqItemLight.language());
        answer.setText(faqItemLight.answer());

        FAQItemEntity faqItemEntity = new FAQItemEntity();
        faqItemEntity.setSource(source);
        faqItemEntity.setAnswer(answer);
        faqItemEntity.setUrl(faqItemLight.url());
        faqItemEntity.setLanguage(faqItemLight.language());
        faqItemEntity.setText(faqItemLight.text());

        return faqItemMapper.map(faqItemRepository.save(faqItemEntity));
    }

    private FAQItem update(FAQItemLight faqItemLight) {
        FAQItemEntity byId = faqItemRepository.findById(Objects.requireNonNull(faqItemLight.id()))
                .orElseThrow(() -> new IllegalArgumentException("No FAQItem found for id : " + faqItemLight.id()));

        byId.setText(faqItemLight.text());
        byId.setUrl(faqItemLight.url());
        byId.setLanguage(faqItemLight.language());
        byId.getAnswer().setText(faqItemLight.answer());
        byId.getAnswer().setUrl(faqItemLight.url());
        byId.getAnswer().setLanguage(faqItemLight.language());
        byId.getSource().setUrl(faqItemLight.url());

        return faqItemMapper.map(faqItemRepository.save(byId));
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
