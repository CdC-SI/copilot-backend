package zas.admin.zec.backend.config;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.List;

/**
 * Point unique de création des {@link ChatClient} : un {@link ChatClient.Builder} par modèle, porteur des
 * réglages communs (observations Micrometer, advisors par défaut). Les services injectent le builder qualifié
 * et appellent {@code build()} ; aucun {@code ChatClient.create(...)} ailleurs.
 *
 * <p>Les builders sont en scope prototype : chaque consommateur reçoit sa propre instance (un builder est mutable).</p>
 */
@Configuration
public class ChatClientConfig {

    /** Builder pour le modèle de chat interne (agent, outils, tâches auxiliaires). */
    public static final String INTERNAL_CHAT_CLIENT_BUILDER = "internalChatClientBuilder";
    /** Builder pour le modèle de vision (OCR, extraction structurée depuis des images). */
    public static final String VISION_CHAT_CLIENT_BUILDER = "visionChatClientBuilder";

    @Bean(name = INTERNAL_CHAT_CLIENT_BUILDER)
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ChatClient.Builder internalChatClientBuilder(@Qualifier("internalChatModel") ChatModel internalChatModel,
                                                        ObjectProvider<ObservationRegistry> observationRegistry) {
        return builder(internalChatModel, observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));
    }

    @Bean(name = VISION_CHAT_CLIENT_BUILDER)
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ChatClient.Builder visionChatClientBuilder(@Qualifier("visionModel") ChatModel visionModel,
                                                      ObjectProvider<ObservationRegistry> observationRegistry) {
        return builder(visionModel, observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));
    }

    /** Builder configuré avec les réglages communs ; réutilisable dans les tests avec un modèle simulé. */
    public static ChatClient.Builder builder(ChatModel chatModel, ObservationRegistry observationRegistry) {
        return ChatClient.builder(chatModel, observationRegistry, null, null)
                .defaultAdvisors(defaultAdvisors());
    }

    /**
     * Advisors appliqués à tous les appels. Journalisation des requêtes et réponses au niveau DEBUG
     * uniquement (logger {@code org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor}).
     * Points d'extension prévus : mémoire conversationnelle (B2), garde-fous (T5).
     */
    static List<Advisor> defaultAdvisors() {
        return List.of(SimpleLoggerAdvisor.builder().build());
    }
}
