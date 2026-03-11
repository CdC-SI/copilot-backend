package zas.admin.zec.backend.actions.summarize.jms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static zas.admin.zec.backend.actions.summarize.jms.JmsConstants.GAIME_MESSAGE;
import static zas.admin.zec.backend.actions.summarize.jms.JmsConstants.OPEN_DOCUMENTS_ACTION;

/**
 * Service pour envoyer des messages JMS vers l'application GAIME sur un topic.
 */
@Slf4j
@Service
public class GaimeJmsService {

    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;

    public GaimeJmsService(JmsTemplate jmsTemplate, ObjectMapper objectMapper) {
        this.jmsTemplate = jmsTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Envoie un message JMS sur le topic pour afficher plusieurs documents.
     *
     * @param visa Le visa de l'utilisateur
     * @param objTokens La liste des tokens de documents à afficher
     * @throws JsonProcessingException Si la sérialisation JSON échoue
     */
    public void sendOpenDocumentsMessage(String visa, List<String> objTokens) throws JsonProcessingException {
        log.info("Envoi message JMS sur topic pour afficher {} documents (visa: {})", objTokens.size(), visa);

        OpenInfo openInfo = new OpenInfo();
        openInfo.setAction(OPEN_DOCUMENTS_ACTION);
        openInfo.setVisa(visa);
        openInfo.setObjTokens(objTokens);

        String jsonMessage = objectMapper.writeValueAsString(openInfo);
        String fullMessage = GAIME_MESSAGE + jsonMessage;

        log.debug("Message JMS topic prepared: action={}, tokensCount={}", openInfo.getAction(), objTokens.size());

        jmsTemplate.convertAndSend(fullMessage);

        log.info("Message JMS envoyé avec succès sur topic");
    }
}

