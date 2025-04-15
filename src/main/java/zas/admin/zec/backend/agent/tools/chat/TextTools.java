package zas.admin.zec.backend.agent.tools.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import zas.admin.zec.backend.actions.converse.Message;
import zas.admin.zec.backend.tools.TranslationService;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class TextTools {

    public static final String HISTORY = "history";
    private final TranslationService translationService;

    public TextTools(TranslationService translationService) {
        this.translationService = translationService;
    }

    @Tool(
            name = "translate",
            description = "Translate part or all the current conversation to a different language."
    )
    String translate(
            @ToolParam(description = "Target language’s code according to ISO 639-1") String targetLanguage,
            @ToolParam(description = "Number of messages in the conversation history to translate, -1 means all messages.") Integer numberOfMessagesToTranslate,
            ToolContext toolContext) {

        log.info("Translate tool called with target language: {}, number of messages to translate: {}", targetLanguage, numberOfMessagesToTranslate);
        targetLanguage = handleLanguageWithVariants(targetLanguage);
        List<Message> history = (List<Message>) toolContext.getContext().get(HISTORY);
        if (numberOfMessagesToTranslate == -1 || numberOfMessagesToTranslate > history.size()) {
            numberOfMessagesToTranslate = history.size();
        }

        String toTranslate = history.subList(history.size() - numberOfMessagesToTranslate, history.size())
                .stream()
                .map(message -> "%s : %s".formatted(message.role(), message.message()))
                .collect(Collectors.joining("<br>"));

        return translationService.translate(toTranslate, targetLanguage);
    }

    private String handleLanguageWithVariants(String targetLanguage) {
        return switch (targetLanguage) {
            case "en" -> "en-gb";
            case "pt" -> "pt-br";
            case "zh" -> "zh-hans";
            default -> targetLanguage;
        };
    }
}
