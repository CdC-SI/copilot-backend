package zas.admin.zec.backend.conversations;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final ConversationTitleRepository conversationTitleRepository;

    public ConversationService(ConversationRepository conversationRepository, ConversationTitleRepository conversationTitleRepository) {
        this.conversationRepository = conversationRepository;
        this.conversationTitleRepository = conversationTitleRepository;
    }

    public void initConversation(String userId, String conversationId, List<Message> messages) {
        for (var message : messages) {
            save(message, userId, conversationId);
        }

        var title = new ConversationTitleEntity();
        title.setUserId(userId);
        title.setConversationId(conversationId);
        title.setTitle(messages.getFirst().message().substring(0, Math.min(25, messages.getFirst().message().length())));
        title.setTimestamp(LocalDateTime.now());

        conversationTitleRepository.save(title);
    }
    public void save(Message message, String userId, String conversationId) {
        var entity = new MessageEntity();
        entity.setUserId(userId);
        entity.setConversationId(conversationId);
        entity.setMessageId(UUID.randomUUID().toString());
        entity.setRole(message.role().equals("USER") ? "user" : "assistant");
        entity.setMessage(message.message());
        entity.setLanguage(message.language());
        entity.setTimestamp(message.timestamp());
        entity.setUrl(message.url());
        entity.setFaqId(message.faqItemId());

        conversationRepository.save(entity);
    }
}
