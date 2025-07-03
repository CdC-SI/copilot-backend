package zas.admin.zec.backend.actions.askfaq;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import zas.admin.zec.backend.config.properties.FAQSearchProperties;
import zas.admin.zec.backend.persistence.entity.FAQItemEntity;
import zas.admin.zec.backend.persistence.entity.PublicDocumentEntity;
import zas.admin.zec.backend.persistence.entity.SourceEntity;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;
import zas.admin.zec.backend.persistence.repository.FAQItemRepository;
import zas.admin.zec.backend.persistence.repository.SourceRepository;
import zas.admin.zec.backend.tools.EntityMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class FAQService {

    private final FAQItemRepository faqItemRepository;
    private final SourceRepository sourceRepository;
    private final DocumentRepository documentRepository;
    private final FAQSearchProperties faqSearchProperties;
    private final EntityMapper entityMapper;
    private final EmbeddingModel embeddingModel;
    private final FAQCache faqCache;

    public FAQService(FAQItemRepository faqItemRepository,
                      SourceRepository sourceRepository,
                      DocumentRepository documentRepository,
                      FAQSearchProperties faqSearchProperties,
                      EntityMapper entityMapper,
                      @Qualifier("publicEmbeddingModel") EmbeddingModel embeddingModel,
                      FAQCache faqCache) {

        this.faqItemRepository = faqItemRepository;
        this.sourceRepository = sourceRepository;
        this.documentRepository = documentRepository;
        this.faqSearchProperties = faqSearchProperties;
        this.entityMapper = entityMapper;
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
        var sourceOpt = sourceRepository.findByUrl("faq");
        SourceEntity source;
        if (sourceOpt.isEmpty()) {
            source = new SourceEntity();
            source.setUrl("faq");
            source = sourceRepository.save(source);
        } else {
            source = sourceOpt.get();
        }

        PublicDocumentEntity answer = new PublicDocumentEntity();
        answer.setSource(source);
        answer.setUrl(faqItemLight.url());
        answer.setLanguage(faqItemLight.language());
        answer.setText(faqItemLight.answer());
        answer = documentRepository.save(answer);

        FAQItemEntity faqItemEntity = new FAQItemEntity();
        faqItemEntity.setSource(source);
        faqItemEntity.setAnswer(answer);
        faqItemEntity.setUrl(faqItemLight.url());
        faqItemEntity.setLanguage(faqItemLight.language());
        faqItemEntity.setText(faqItemLight.text());

        return entityMapper.map(faqItemRepository.save(faqItemEntity));
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
        documentRepository.save(byId.getAnswer());

        return entityMapper.map(faqItemRepository.save(byId));
    }

    private List<FAQItem> getExistingFAQItemsByWordSimilarity(String question) {
        var properties = faqSearchProperties.trigramMatching();
        var byWordSimilarity = faqItemRepository.findByWordSimilarity(
                question, properties.threshold(), properties.limit());

        return byWordSimilarity
                .stream()
                .map(entityMapper::map)
                .toList();
    }

    private List<FAQItem> getExistingFAQItemsBySemanticSimilarity(String question) {
        var properties = faqSearchProperties.semanticMatching();
        var questionEmbedding = getFAQItemTextEmbedding(question);
        var nearestByTextEmbedding = faqItemRepository.findNearestsByTextEmbedding(
                questionEmbedding, properties.limit());

        return nearestByTextEmbedding
                .stream()
                .map(entityMapper::map)
                .toList();
    }

    private String getFAQItemTextEmbedding(String question) {
        return Arrays.toString(embeddingModel.embed(question));
    }
}
