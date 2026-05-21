package zas.admin.zec.backend.tools;

import com.deepl.api.DeepLClient;
import com.deepl.api.DeepLException;
import com.deepl.api.TextResult;
import com.deepl.api.TextTranslationOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslationServiceTest {

    @Mock
    private DeepLClient deepLClient;

    private TranslationService translationService;

    private TranslationService createService(DeepLClient client, String defaultLang) throws Exception {
        // Use reflection to inject mock since constructor creates its own client
        var service = new TranslationService(new zas.admin.zec.backend.config.properties.DeepLProperties("fake-key", defaultLang));
        Field field = TranslationService.class.getDeclaredField("deepLClient");
        field.setAccessible(true);
        field.set(service, client);
        return service;
    }

    @Test
    @DisplayName("Translate returns translated text on success")
    void translate_returnsTranslatedText() throws Exception {
        TextResult textResult = mock(TextResult.class);
        when(textResult.getText()).thenReturn("Bonjour");
        when(deepLClient.translateText(eq("Hello"), isNull(), eq("fr"), any(TextTranslationOptions.class)))
                .thenReturn(textResult);

        translationService = createService(deepLClient, "fr");

        String result = translationService.translate("Hello", "fr");
        assertEquals("Bonjour", result);
    }

    @Test
    @DisplayName("Translate uses default language when targetLang is null")
    void translate_usesDefaultLang_whenTargetLangIsNull() throws Exception {
        TextResult textResult = mock(TextResult.class);
        when(textResult.getText()).thenReturn("Traduit");
        when(deepLClient.translateText(eq("Text"), isNull(), eq("fr"), any(TextTranslationOptions.class)))
                .thenReturn(textResult);

        translationService = createService(deepLClient, "fr");

        String result = translationService.translate("Text", null);
        assertEquals("Traduit", result);
    }

    @Test
    @DisplayName("Translate returns original text on DeepLException")
    void translate_returnsOriginalText_onException() throws Exception {
        when(deepLClient.translateText(anyString(), isNull(), anyString(), any(TextTranslationOptions.class)))
                .thenThrow(new DeepLException("API error"));

        translationService = createService(deepLClient, "fr");

        String result = translationService.translate("Original", "de");
        assertEquals("Original", result);
    }
}

