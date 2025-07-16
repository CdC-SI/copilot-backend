package zas.admin.zec.backend.tools;

import com.deepl.api.DeepLClient;
import com.deepl.api.DeepLException;
import com.deepl.api.TextTranslationOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import zas.admin.zec.backend.config.properties.DeepLProperties;

import java.util.Optional;

@Slf4j
@Service
public class TranslationService {

    private final DeepLClient deepLClient;
    private final String defaultTargetLang;

    public TranslationService(DeepLProperties deepLProperties) {
        this.deepLClient = new DeepLClient(deepLProperties.authKey());
        this.defaultTargetLang = deepLProperties.defaultTargetLang();
    }

    public String translate(String text, String targetLang) {
        try {
            TextTranslationOptions options = new TextTranslationOptions().setPreserveFormatting(true);
            targetLang = Optional.ofNullable(targetLang).orElse(defaultTargetLang);

            return deepLClient
                    .translateText(text, null, targetLang, options)
                    .getText();

        } catch (DeepLException e) {
            log.error(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.warn("Something went wrong with translation, returning original text.");
        return text;
    }
}
