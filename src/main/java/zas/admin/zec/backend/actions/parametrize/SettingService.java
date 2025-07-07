package zas.admin.zec.backend.actions.parametrize;

import org.springframework.stereotype.Service;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.config.properties.ApplicationProperties;
import zas.admin.zec.backend.persistence.repository.InternalDocumentRepository;

import java.util.List;
import java.util.stream.Stream;

@Service
public class SettingService {

    private final ApplicationProperties properties;
    private final UserService userService;
    private final InternalDocumentRepository internalDocumentRepository;

    public SettingService(ApplicationProperties properties,
                          UserService userService,
                          InternalDocumentRepository internalDocumentRepository) {

        this.properties = properties;
        this.userService = userService;
        this.internalDocumentRepository = internalDocumentRepository;
    }

    public List<String> getPublicSettings(SettingType type) {
        return switch (type) {
            case SOURCE -> getPublicSources();
            case TAG -> getPublicTags(List.of());
            default -> getSettings(type, null);
        };
    }

    public List<String> getSettings(SettingType type, String currentUser) {
        return switch (type) {
            case SOURCE -> getSources(currentUser);
            case TAG -> getTags(currentUser, List.of());
            case PROJECT_VERSION -> List.of(properties.version());
            case LLM_MODEL -> getSettings(LLMModel.class);
            case RETRIEVAL_METHOD -> getSettings(RetrievalMethod.class);
            case RESPONSE_STYLE -> getSettings(ResponseStyle.class);
            case RESPONSE_FORMAT -> getSettings(ResponseFormat.class);
            case AUTHORIZED_COMMAND -> getSettings(Command.class);
            case ORGANIZATION -> getSettings(Organization.class);
        };
    }

    public List<String> getPublicTags(List<String> sources) {
        return sources.isEmpty()
            ? internalDocumentRepository.findPublicTags()
            : internalDocumentRepository.findPublicTagsBySources(sources);
    }

    public List<String> getTags(String currentUser, List<String> sources) {
        var userId = userService.getUuid(currentUser);
        if (!userService.hasAccessToInternalDocuments(userId)) {
            return getPublicTags(sources);
        }
        return sources.isEmpty()
                ? internalDocumentRepository.findAllTags()
                : internalDocumentRepository.findTagsBySources(sources);
    }

    private List<String> getPublicSources() {
        return internalDocumentRepository.findPublicSources();
    }

    private List<String> getSources(String currentUser) {
        var userId = userService.getUuid(currentUser);
        return userService.hasAccessToInternalDocuments(userId)
                ? internalDocumentRepository.findAllSources()
                : getPublicSources();
    }

    private List<String> getSettings(Class<? extends Enum<?>> enumClass) {
        return Stream.of(enumClass.getEnumConstants()).map(Enum::name).toList();
    }
}