package zas.admin.zec.backend.actions.parametrize;

import org.springframework.stereotype.Service;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.config.properties.ApplicationProperties;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;
import zas.admin.zec.backend.persistence.repository.InternalDocumentRepository;
import zas.admin.zec.backend.persistence.repository.SourceRepository;

import java.util.List;
import java.util.stream.Stream;

@Service
public class SettingService {

    private final ApplicationProperties properties;
    private final UserService userService;
    private final DocumentRepository documentRepository;
    private final SourceRepository sourceRepository;
    private final InternalDocumentRepository internalDocumentRepository;

    public SettingService(ApplicationProperties properties, UserService userService,
                          DocumentRepository documentRepository, SourceRepository sourceRepository,
                          InternalDocumentRepository internalDocumentRepository) {

        this.properties = properties;
        this.userService = userService;
        this.documentRepository = documentRepository;
        this.sourceRepository = sourceRepository;
        this.internalDocumentRepository = internalDocumentRepository;
    }

    public List<String> getPublicSettings(SettingType type) {
        return switch (type) {
            case SOURCE -> getPublicSources();
            case TAG -> getPublicTags();
            default -> getSettings(type, null);
        };
    }

    public List<String> getSettings(SettingType type, String currentUser) {
        return switch (type) {
            case SOURCE -> getSources(currentUser);
            case TAG -> getTags(currentUser);
            case PROJECT_VERSION -> List.of(properties.version());
            case LLM_MODEL -> getSettings(LLMModel.class);
            case RETRIEVAL_METHOD -> getSettings(RetrievalMethod.class);
            case RESPONSE_STYLE -> getSettings(ResponseStyle.class);
            case RESPONSE_FORMAT -> getSettings(ResponseFormat.class);
            case AUTHORIZED_COMMAND -> getSettings(Command.class);
            case ORGANIZATION -> getSettings(Organization.class);
        };
    }

    private List<String> getPublicTags() {
        return internalDocumentRepository.findPublicTags();
    }

    private List<String> getTags(String currentUser) {
        var userId = userService.getUuid(currentUser);
        return userService.hasAccessToInternalDocuments(userId)
                ? internalDocumentRepository.findTagsByOrganization("ZAS")
                : getPublicTags();
    }

    private List<String> getPublicSources() {
        return internalDocumentRepository.findPublicSources();
    }

    private List<String> getSources(String currentUser) {
        var userId = userService.getUuid(currentUser);
        return userService.hasAccessToInternalDocuments(userId)
                ? internalDocumentRepository.findSourcesByOrganization("ZAS")
                : getPublicSources();
    }

    private List<String> getSettings(Class<? extends Enum<?>> enumClass) {
        return Stream.of(enumClass.getEnumConstants()).map(Enum::name).toList();
    }
}