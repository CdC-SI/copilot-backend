package zas.admin.zec.backend.actions.converse;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Factory sélectionnant la {@link ChatService} à utiliser pour une {@link Question} donnée
 * (pattern Strategy + Factory). Chaque stratégie déclare, via {@link ChatService#supports(Question)},
 * si elle doit traiter la question ; la première stratégie supportante est retenue.
 *
 * <p>Ouverte à l'extension : ajouter une nouvelle stratégie ne nécessite qu'un nouveau
 * {@code @Service} implémentant {@link ChatService}, sans modifier cette factory.</p>
 */
@Component
public class ChatServiceFactory {

    private final List<ChatService> chatServices;

    public ChatServiceFactory(List<ChatService> chatServices) {
        this.chatServices = chatServices;
    }

    public ChatService resolve(ConversationType conversationType) {
        return chatServices.stream()
                .filter(service -> service.supports(conversationType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucun ChatService ne supporte ce type de conversation (%s)".formatted(conversationType)));
    }
}
