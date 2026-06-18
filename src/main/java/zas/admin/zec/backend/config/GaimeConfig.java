package zas.admin.zec.backend.config;

import ch.admin.zas.gaime.dao.domain.doc.DocumentService;
import ch.admin.zas.gaime.dao.nuxeo.NuxeoConfig;
import ch.admin.zas.gaime.dao.nuxeo.service.DocumentServiceImpl;
import ch.admin.zas.gaime.dao.nuxeo.service.NuxeoSessionService;
import jakarta.jms.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.core.JmsTemplate;

@EnableJms
@Configuration
@ComponentScan(value = "ch.admin.zas.gaime.dao", excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = NuxeoConfig.class))
public class GaimeConfig {

    @Value("${spring.jms.template.default-destination}")
    private String defaultDestination;

    @Bean
    public DocumentService documentService() {
        return new DocumentServiceImpl();
    }

    @Bean
    public NuxeoSessionService nuxeoSessionService() {
        return () -> "dummy-session-token";
    }

    /**
     * Configuration du JmsTemplate pour envoyer des messages JMS sur un topic.
     * Le template est configuré pour envoyer des messages en texte brut (String).
     *
     * @param connectionFactory La factory de connexion JMS
     * @return Le JmsTemplate configuré pour les topics
     */
    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate jmsTemplate = new JmsTemplate(connectionFactory);
        jmsTemplate.setDefaultDestinationName(defaultDestination);
        jmsTemplate.setPubSubDomain(true); // Active le mode topic (pub/sub)
        // Pas de MessageConverter : on envoie du texte brut
        return jmsTemplate;
    }
}
