package zas.admin.zec.backend.actions.converse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.persistence.ConversationRepository;
import zas.admin.zec.backend.persistence.ConversationTitleEntity;
import zas.admin.zec.backend.persistence.ConversationTitleRepository;
import zas.admin.zec.backend.persistence.MessageEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
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
    public void update(String userUuid, String conversationId, List<Message> messages) {
        conversationTitleRepository.findByUserIdAndConversationId(userUuid, conversationId)
                .ifPresent(title -> {
                    for (Message message : messages) {
                        save(message, title.getUserId(), title.getConversationId());
                    }
                });
    }

    public void delete(String userId, String conversationId) {
        conversationRepository.deleteByUserIdAndConversationId(userId, conversationId);
        conversationTitleRepository.deleteByUserIdAndConversationId(userId, conversationId);
    }

    public void renameConversation(String userId, String conversationId, String newTitle) {
        conversationTitleRepository.findByUserIdAndConversationId(userId, conversationId)
                .ifPresent(title -> {
                    title.setTitle(newTitle);
                    title.setTimestamp(LocalDateTime.now());
                    conversationTitleRepository.save(title);
                });
    }

    private void save(Message message, String userId, String conversationId) {
        var entity = new MessageEntity();
        entity.setUserId(userId);
        entity.setConversationId(conversationId);
        entity.setMessageId(UUID.randomUUID().toString());
        entity.setRole(message.role().equals("USER") ? "user" : "assistant");
        entity.setMessage(message.message());
        entity.setLanguage(message.language());
        entity.setTimestamp(message.timestamp());
        entity.setFaqId(message.faqItemId());
        entity.setSources(Objects.isNull(message.sources())
                ? new String[0]
                : message.sources().toArray(String[]::new));

        conversationRepository.save(entity);
    }
}
