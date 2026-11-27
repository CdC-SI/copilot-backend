package zas.admin.zec.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Flux SSE des réponses du chat.
 *
 * @param heartbeatInterval intervalle du commentaire SSE envoyé tant que le flux est ouvert : la gateway coupe une
 *                          connexion sans données au bout de 30 s, alors que le flux peut rester silencieux plus
 *                          longtemps (attente d'une approbation, premier token du LLM, outil lent). Doit rester
 *                          nettement inférieur à ce délai.
 */
@ConfigurationProperties(prefix = "zia.conversation.stream")
public record ConversationStreamProperties(@DefaultValue("15s") Duration heartbeatInterval) {

    public ConversationStreamProperties {
        if (heartbeatInterval == null || heartbeatInterval.isNegative() || heartbeatInterval.isZero()) {
            throw new IllegalArgumentException("zia.conversation.stream.heartbeat-interval doit être positif");
        }
    }
}
