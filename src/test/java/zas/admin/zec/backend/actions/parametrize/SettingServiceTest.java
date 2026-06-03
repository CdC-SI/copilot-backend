package zas.admin.zec.backend.actions.parametrize;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zas.admin.zec.backend.actions.authorize.UserService;
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
    private UserService userService;
    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private SettingService settingService;

    @Test
    @DisplayName("getSettings returns project version")
    void getSettings_returnsProjectVersion() {
        when(properties.version()).thenReturn("2.0.0");

        List<String> result = settingService.getSettings(SettingType.PROJECT_VERSION, null);

        assertEquals(List.of("2.0.0"), result);
    }

    @Test
    @DisplayName("getSettings returns enum values for LLM_MODEL")
    void getSettings_returnsEnumValues() {
        List<String> result = settingService.getSettings(SettingType.LLM_MODEL, null);

        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("getPublicSettings for SOURCE delegates to documentRepository")
    void getPublicSettings_source() {
        when(documentRepository.findPublicSources()).thenReturn(List.of("src1", "src2"));

        List<String> result = settingService.getPublicSettings(SettingType.SOURCE);

        assertEquals(List.of("src1", "src2"), result);
    }

    @Test
    @DisplayName("getPublicTags with empty sources returns all public tags")
    void getPublicTags_emptySourcesReturnsAll() {
        when(documentRepository.findPublicTags()).thenReturn(List.of("tag1"));

        List<String> result = settingService.getPublicTags(List.of());

        assertEquals(List.of("tag1"), result);
    }

    @Test
    @DisplayName("getPublicTags with sources filters by sources")
    void getPublicTags_withSources() {
        when(documentRepository.findPublicTagsBySources(List.of("src1"))).thenReturn(List.of("tagA"));

        List<String> result = settingService.getPublicTags(List.of("src1"));

        assertEquals(List.of("tagA"), result);
    }

    @Test
    @DisplayName("getSources returns all sources for internal user")
    void getSources_returnsAll_forInternalUser() {
        when(userService.getUuid("admin")).thenReturn("uuid-1");
        when(userService.hasAccessToInternalDocuments("uuid-1")).thenReturn(true);
        when(documentRepository.findAllSources()).thenReturn(List.of("internal", "public"));

        List<String> result = settingService.getSettings(SettingType.SOURCE, "admin");

        assertEquals(List.of("internal", "public"), result);
    }

    @Test
    @DisplayName("getSources returns only public for external user")
    void getSources_returnsPublicOnly_forExternalUser() {
        when(userService.getUuid("ext")).thenReturn("uuid-2");
        when(userService.hasAccessToInternalDocuments("uuid-2")).thenReturn(false);
        when(documentRepository.findPublicSources()).thenReturn(List.of("public"));

        List<String> result = settingService.getSettings(SettingType.SOURCE, "ext");

        assertEquals(List.of("public"), result);
    }
}

