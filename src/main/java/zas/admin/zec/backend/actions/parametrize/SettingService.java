package zas.admin.zec.backend.actions.parametrize;

import org.springframework.stereotype.Service;
import zas.admin.zec.backend.config.properties.ApplicationProperties;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;

import java.util.List;
import java.util.stream.Stream;

@Service
public class SettingService {

    private final ApplicationProperties properties;
    private final DocumentRepository documentRepository;

    public SettingService(ApplicationProperties properties, DocumentRepository documentRepository) {
        this.properties = properties;
        this.documentRepository = documentRepository;
    }

    /** Valeurs du paramètre ; sources et tags sont les mêmes pour tous les utilisateurs (tous internes). */
    public List<String> getSettings(SettingType type) {
        return switch (type) {
            case SOURCE -> documentRepository.findAllSources();
            case TAG -> getTags(List.of());
            case PROJECT_VERSION -> List.of(properties.version());
            case LLM_MODEL -> getSettings(LLMModel.class);
            case RESPONSE_STYLE -> getSettings(ResponseStyle.class);
            case RESPONSE_FORMAT -> getSettings(ResponseFormat.class);
            case AUTHORIZED_COMMAND -> getSettings(Command.class);
            case ORGANIZATION -> getSettings(Organization.class);
        };
    }

    public List<String> getTags(List<String> sources) {
        return sources.isEmpty()
                ? documentRepository.findAllTags()
                : documentRepository.findTagsBySources(sources);
    }

    private List<String> getSettings(Class<? extends Enum<?>> enumClass) {
        return Stream.of(enumClass.getEnumConstants()).map(Enum::name).toList();
    }
}