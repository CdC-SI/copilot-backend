package zas.admin.zec.backend.actions.parametrize;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zas.admin.zec.backend.config.properties.ApplicationProperties;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingServiceTest {

    @Mock
    private ApplicationProperties properties;
    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private SettingService settingService;

    @Test
    @DisplayName("getSettings returns project version")
    void getSettings_returnsProjectVersion() {
        when(properties.version()).thenReturn("2.0.0");

        List<String> result = settingService.getSettings(SettingType.PROJECT_VERSION);

        assertEquals(List.of("2.0.0"), result);
    }

    @Test
    @DisplayName("getSettings returns enum values for LLM_MODEL")
    void getSettings_returnsEnumValues() {
        List<String> result = settingService.getSettings(SettingType.LLM_MODEL);

        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("getSettings returns every source, whatever the organizations metadata")
    void getSettings_returnsAllSources() {
        when(documentRepository.findAllSources()).thenReturn(List.of("internal", "public"));

        List<String> result = settingService.getSettings(SettingType.SOURCE);

        assertEquals(List.of("internal", "public"), result);
    }

    @Test
    @DisplayName("getSettings for TAG returns every tag")
    void getSettings_returnsAllTags() {
        when(documentRepository.findAllTags()).thenReturn(List.of("tag1", "tag2"));

        assertEquals(List.of("tag1", "tag2"), settingService.getSettings(SettingType.TAG));
    }

    @Test
    @DisplayName("getTags with sources filters by sources only")
    void getTags_withSources() {
        when(documentRepository.findTagsBySources(List.of("src1"))).thenReturn(List.of("tagA"));

        assertEquals(List.of("tagA"), settingService.getTags(List.of("src1")));
    }
}
