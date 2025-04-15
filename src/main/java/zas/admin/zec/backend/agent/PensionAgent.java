package zas.admin.zec.backend.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import zas.admin.zec.backend.actions.converse.Message;
import zas.admin.zec.backend.actions.converse.Question;
import zas.admin.zec.backend.agent.tools.pension.PensionTools;
import zas.admin.zec.backend.rag.token.SourceToken;
import zas.admin.zec.backend.rag.token.TextToken;
import zas.admin.zec.backend.rag.token.Token;

import java.util.List;
import java.util.Map;

@Service
public class PensionAgent implements Agent {

    private final ChatClient client;

    public PensionAgent(ChatModel model) {
        this.client = ChatClient.create(model);
    }

    @Override
    public String getName() {
        return "PENSION_AGENT";
    }

    @Override
    public AgentType getType() {
        return AgentType.PENSION_AGENT;
    }

    @Override
    public Flux<Token> processQuestion(Question question, String userId, List<Message> conversationHistory) {
        Flux<TextToken> clientTokens = client
                .prompt()
                .user(question.query())
                .tools(new PensionTools())
                .toolContext(Map.of(PensionTools.LANG, question.language()))
                .stream()
                .content()
                .map(TextToken::new);

        return Flux.concat(clientTokens, getPensionCalculationSource(question.language()));
    }

    private Flux<SourceToken> getPensionCalculationSource(String language) {
        return switch (language) {
            case "fr" -> Flux.just(new SourceToken("https://www.eak.admin.ch/eak/fr/home/dokumentation/pensionierung/reform-ahv21/kuerzungssaetze-bei-vorbezug.html"));
            case "it" -> Flux.just(new SourceToken("https://www.eak.admin.ch/eak/it/home/dokumentation/pensionierung/reform-ahv21/kuerzungssaetze-bei-vorbezug.html"));
            default -> Flux.just(new SourceToken("https://www.eak.admin.ch/eak/de/home/dokumentation/pensionierung/reform-ahv21/kuerzungssaetze-bei-vorbezug.html"));
        };
    }
}
