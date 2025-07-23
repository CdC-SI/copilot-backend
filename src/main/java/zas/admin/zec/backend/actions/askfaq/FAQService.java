package zas.admin.zec.backend.actions.askfaq;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.config.properties.FAQSearchProperties;
import zas.admin.zec.backend.persistence.entity.DocumentEntity;
import zas.admin.zec.backend.persistence.entity.QuestionEntity;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;
import zas.admin.zec.backend.persistence.repository.QuestionRepository;
import zas.admin.zec.backend.tools.EntityMapper;

import java.util.*;
import java.util.stream.Stream;

@Service
public class FAQService {

    private final DocumentRepository documentRepository;
    private final QuestionRepository questionRepository;
    private final FAQSearchProperties faqSearchProperties;
    private final EmbeddingModel embeddingModel;
    private final FAQCache faqCache;

    public FAQService(DocumentRepository documentRepository,
                      QuestionRepository questionRepository,
                      FAQSearchProperties faqSearchProperties,
                      @Qualifier("publicEmbeddingModel") EmbeddingModel embeddingModel,
                      FAQCache faqCache) {

        this.documentRepository = documentRepository;
        this.questionRepository = questionRepository;
        this.faqSearchProperties = faqSearchProperties;
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

    @Transactional
    public FAQItem save(FAQItemLight faqItem) {
        return faqItem.id() != null
                ? update(faqItem)
                : create(faqItem);
    }

    private FAQItem create(FAQItemLight faqItemLight) {
        var answerId = UUID.randomUUID().toString();
        var tags = faqItemLight.tags() != null
                ? String.join(",", faqItemLight.tags())
                : "";

        DocumentEntity answer = new DocumentEntity();
        answer.setContent(faqItemLight.answer());
        answer.setEmbedding(embeddingModel.embed(faqItemLight.answer()));
        answer.setMetadata(Map.of(
                "answer_id", answerId,
                "source", "knowledge_base",
                "url", faqItemLight.url(),
                "language", faqItemLight.language(),
                "tags", tags
        ));


        QuestionEntity question = new QuestionEntity();
        question.setContent(faqItemLight.text());
        question.setEmbedding(embeddingModel.embed(faqItemLight.text()));
        question.setMetadata(Map.of(
                "answer_id", answerId,
                "source", "knowledge_base",
                "url", faqItemLight.url(),
                "language", faqItemLight.language(),
                "tags", tags
        ));

        return EntityMapper.map(questionRepository.save(question), documentRepository.save(answer));
    }

    private FAQItem update(FAQItemLight faqItemLight) {
        var tags = faqItemLight.tags() != null
                ? String.join(",", faqItemLight.tags())
                : "";

        QuestionEntity question = questionRepository.findById(Objects.requireNonNull(faqItemLight.id()))
                .orElseThrow(() -> new IllegalArgumentException("No FAQItem found for id : " + faqItemLight.id()));
        var answerId = question.getMetadata().get("answer_id");
        question.setContent(faqItemLight.text());
        question.setEmbedding(embeddingModel.embed(faqItemLight.text()));
        question.setMetadata(Map.of(
                "answer_id", answerId,
                "source", "knowledge_base",
                "url", faqItemLight.url(),
                "language", faqItemLight.language(),
                "tags", tags
        ));

        DocumentEntity answer = documentRepository.findByAnswerId(answerId);
        answer.setContent(faqItemLight.text());
        answer.setEmbedding(embeddingModel.embed(faqItemLight.answer()));
        answer.setMetadata(Map.of(
                "answer_id", answerId,
                "source", "knowledge_base",
                "url", faqItemLight.url(),
                "language", faqItemLight.language(),
                "tags", tags
        ));

        return EntityMapper.map(questionRepository.save(question), documentRepository.save(answer));
    }

    private List<FAQItem> getExistingFAQItemsByWordSimilarity(String query) {
        var properties = faqSearchProperties.trigramMatching();
        var byWordSimilarity = questionRepository.findByWordSimilarity(
                query, properties.threshold(), properties.limit());

        return questionsToFAQItems(byWordSimilarity);
    }

    private List<FAQItem> getExistingFAQItemsBySemanticSimilarity(String query) {
        var properties = faqSearchProperties.semanticMatching();
        var questionEmbedding = getFAQItemTextEmbedding(query);
        var nearestByTextEmbedding = questionRepository.findNearestByTextEmbedding(
                questionEmbedding, properties.limit());

        return questionsToFAQItems(nearestByTextEmbedding);
    }

    private List<FAQItem> questionsToFAQItems(List<QuestionEntity> questions) {
        return questions.stream()
                .map(question -> {
                    var answerId = question.getMetadata().get("answer_id");
                    var answer = documentRepository.findByAnswerId(answerId);
                    if (answer == null) {
                        return null;
                    }
                    return EntityMapper.map(question, answer);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private String getFAQItemTextEmbedding(String question) {
        return Arrays.toString(embeddingModel.embed(question));
    }
}
