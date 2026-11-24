package zas.admin.zec.backend.actions.askfaq;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zas.admin.zec.backend.config.properties.FAQSearchProperties;
import zas.admin.zec.backend.config.properties.SemanticMatchingProperties;
import zas.admin.zec.backend.config.properties.TrigramMatchingProperties;
import zas.admin.zec.backend.persistence.entity.DocumentEntity;
import zas.admin.zec.backend.persistence.entity.QuestionEntity;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;
import zas.admin.zec.backend.persistence.repository.QuestionRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FAQServiceTest {

    private static final float[] EMBEDDING = {0.1f, 0.2f};

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final FAQSearchProperties properties = new FAQSearchProperties(2,
            new TrigramMatchingProperties(5, 0.4), new SemanticMatchingProperties(3, "cosine"));
    private FAQService service;

    @BeforeEach
    void setUp() throws Exception {
        var ctor = FAQCache.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        service = new FAQService(documentRepository, questionRepository, properties, embeddingModel, ctor.newInstance());
        when(embeddingModel.embed(anyString())).thenReturn(EMBEDDING);
        when(questionRepository.save(any())).thenAnswer(i -> withId(i.getArgument(0)));
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void search_enoughWordMatches_skipsSemanticSearch() {
        when(questionRepository.findByWordSimilarity("rente", 0.4, 5)).thenReturn(List.of(question("a1", "Q1"), question("a2", "Q2")));
        when(documentRepository.findByAnswerId(anyString())).thenAnswer(i -> answer("Réponse " + i.getArgument(0)));

        var result = service.getExistingFAQItemsByMatchingQuestion("rente");

        assertThat(result).extracting(FAQItem::text).containsExactly("Q1", "Q2");
        assertThat(result.getFirst().answer().text()).isEqualTo("Réponse a1");
        assertThat(result.getFirst().language()).isEqualTo("fr");
        verify(questionRepository, never()).findNearestByTextEmbedding(anyString(), anyInt());
    }

    @Test
    void search_fewWordMatches_addsSemanticMatchesWithoutDuplicatesAndCachesThem() {
        var shared = question("a1", "Q1");
        when(questionRepository.findByWordSimilarity(anyString(), anyDouble(), anyInt()))
                .thenReturn(List.of(shared))
                .thenReturn(List.of(question("a2", "Q2")));
        when(questionRepository.findNearestByTextEmbedding(Arrays.toString(EMBEDDING), 3))
                .thenReturn(List.of(shared, question("a3", "Q3"), question("orphan", "sans réponse")));
        when(documentRepository.findByAnswerId("a1")).thenReturn(answer("R1"));
        when(documentRepository.findByAnswerId("a2")).thenReturn(answer("R2"));
        when(documentRepository.findByAnswerId("a3")).thenReturn(answer("R3"));

        var result = service.getExistingFAQItemsByMatchingQuestion("rente de vieillesse");

        // Les questions sans réponse sont ignorées, les doublons fusionnés.
        assertThat(result).extracting(FAQItem::text).containsExactly("Q1", "Q3");

        var cached = service.getExistingFAQItemsByMatchingQuestion("rente de vieillesse");
        verify(questionRepository, times(1)).findNearestByTextEmbedding(anyString(), anyInt());
        // Cache : les résultats sémantiques sont réutilisés et toujours fusionnés avec la recherche par mots, rejouée.
        assertThat(cached).extracting(FAQItem::text).containsExactly("Q2", "Q1", "Q3");
    }

    @Test
    void save_withoutId_createsQuestionAndAnswerSharingAnAnswerId() {
        var item = service.save(new FAQItemLight(null, "Quand ?", "Demain", "https://faq", "fr", Arrays.asList("rente", null)));

        var question = ArgumentCaptor.forClass(QuestionEntity.class);
        var answer = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(questionRepository).save(question.capture());
        verify(documentRepository).save(answer.capture());
        assertThat(question.getValue().getMetadata().get("answer_id")).isEqualTo(answer.getValue().getMetadata().get("answer_id"));
        assertThat(answer.getValue().getMetadata()).containsEntry("source", "knowledge_base").containsEntry("tags", List.of("rente"));
        assertThat(answer.getValue().getEmbedding()).isEqualTo(EMBEDDING);
        assertThat(item.text()).isEqualTo("Quand ?");
        assertThat(item.answer().text()).isEqualTo("Demain");
        assertThat(item.url()).isEqualTo("https://faq");
    }

    @Test
    void save_withId_updatesExistingQuestionAndAnswer() {
        var id = UUID.randomUUID();
        var existing = question("a1", "Ancienne");
        when(questionRepository.findById(id)).thenReturn(Optional.of(existing));
        var existingAnswer = answer("Ancienne réponse");
        when(documentRepository.findByAnswerId("a1")).thenReturn(existingAnswer);

        var item = service.save(new FAQItemLight(id, "Nouvelle", "Nouvelle réponse", "https://u", "de", null));

        assertThat(existing.getContent()).isEqualTo("Nouvelle");
        assertThat(existing.getMetadata()).containsEntry("answer_id", "a1").containsEntry("language", "de").containsEntry("tags", List.of());
        assertThat(existingAnswer.getContent()).isEqualTo("Nouvelle réponse");
        assertThat(item.language()).isEqualTo("de");
    }

    @Test
    void save_withUnknownId_throws() {
        var id = UUID.randomUUID();
        when(questionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(new FAQItemLight(id, "Q", "R", "u", "fr", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void controller_searchesAndSaves() throws Exception {
        when(questionRepository.findByWordSimilarity(anyString(), anyDouble(), anyInt())).thenReturn(List.of(question("a1", "Q1"), question("a2", "Q2")));
        when(documentRepository.findByAnswerId(anyString())).thenReturn(answer("R"));
        var mvc = MockMvcBuilders.standaloneSetup(new FAQItemsController(service)).build();

        mvc.perform(get("/api/faq-items").param("question", "rente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].text").value("Q2"))
                .andExpect(jsonPath("$[0].answer.text").value("R"));
        mvc.perform(post("/api/faq-items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Q\",\"answer\":\"R\",\"url\":\"u\",\"language\":\"it\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("it"));
        mvc.perform(get("/api/faq-items")).andExpect(status().isBadRequest());
    }

    private static QuestionEntity question(String answerId, String content) {
        var entity = new QuestionEntity();
        entity.setId(UUID.randomUUID());
        entity.setContent(content);
        entity.setMetadata(Map.of("answer_id", answerId, "language", "fr", "url", "https://faq/" + answerId));
        return entity;
    }

    private static DocumentEntity answer(String content) {
        var entity = new DocumentEntity();
        entity.setContent(content);
        entity.setMetadata(Map.of("language", "fr", "url", "https://faq"));
        return entity;
    }

    private static QuestionEntity withId(QuestionEntity entity) {
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }
        return entity;
    }
}
