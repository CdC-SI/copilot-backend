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

    public List<ConversationTitle> getTitlesByUserId(String userId) {
        return conversationTitleRepository.findByUserId(userId)
                .stream()
                .map(title -> new ConversationTitle(title.getTitle(), title.getUserId(), title.getConversationId(), title.getTimestamp()))
                .toList();
    }

    public List<Message> getByConversationIdAndUserId(String conversationId, String userId) {
        return conversationRepository.findByConversationIdAndUserId(conversationId, userId)
                .stream()
                .map(message -> new Message(
                        message.getMessageId(),
                        message.getUserId(),
                        message.getConversationId(),
                        message.getFaqId(),
                        message.getLanguage(),
                        message.getMessage(),
                        getRole(message),
                        List.of(message.getSources()),
                        message.getTimestamp()
                ))
                .toList();
    }

    private String getRole(MessageEntity message) {
        if (message.getRole().equals("user")) return "USER";
        return message.getFaqId() == null ? "LLM" : "FAQ";
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
