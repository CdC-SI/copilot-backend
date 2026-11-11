package zas.admin.zec.backend.actions.catalog;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Lecture / écriture du format YAML de catalogue ({@link CatalogFile}).
 *
 * <p>La lecture est sécurisée : {@link SafeConstructor} (aucune instanciation de classe arbitraire),
 * alias limités, clés dupliquées refusées. Seul {@code null} est résolu implicitement : tous les
 * autres scalaires restent des chaînes (pas de conversion de {@code yes}/{@code no} en booléen
 * ni de dates), puis Jackson les convertit vers les types des records.</p>
 */
@Component
public class CatalogYamlMapper {

    private static final int MAX_ALIASES = 20;
    private static final int CODE_POINT_LIMIT = 2 * 1024 * 1024;

    private final Validator validator;
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public CatalogYamlMapper(Validator validator) {
        this.validator = validator;
    }

    /**
     * Parse et valide (contraintes Jakarta) un fichier de catalogue.
     *
     * @throws InvalidCatalogException si le YAML est malformé ou ne respecte pas le format
     */
    public CatalogFile parse(InputStream input) {
        Object raw;
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            raw = newLoader().load(reader);
        } catch (YAMLException e) {
            throw new InvalidCatalogException("YAML invalide : " + e.getMessage());
        } catch (java.io.IOException e) {
            throw new InvalidCatalogException("Lecture du fichier impossible : " + e.getMessage());
        }

        if (!(raw instanceof Map<?, ?>)) {
            throw new InvalidCatalogException("Le fichier doit contenir un objet YAML racine (version, sources, workspaces)");
        }

        CatalogFile catalog;
        try {
            catalog = jsonMapper.convertValue(raw, CatalogFile.class);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new InvalidCatalogException("Structure invalide : " + describe(e));
        }

        validate(catalog);
        return catalog;
    }

    /**
     * Sérialise un catalogue en YAML (style bloc, champs vides omis, descriptions multilignes en {@code |}).
     */
    public String write(CatalogFile catalog) {
        var root = new LinkedHashMap<String, Object>();
        root.put("version", catalog.version());
        root.put("sources", catalog.sources().stream().map(CatalogYamlMapper::toMap).toList());
        root.put("workspaces", catalog.workspaces().stream().map(CatalogYamlMapper::toMap).toList());
        return newDumper().dump(root);
    }

    private void validate(CatalogFile catalog) {
        var errors = new ArrayList<String>();
        validator.validate(catalog).stream()
                .sorted(Comparator.comparing((ConstraintViolation<CatalogFile> v) -> v.getPropertyPath().toString()))
                .forEach(v -> errors.add(v.getPropertyPath() + " : " + v.getMessage()));

        if (catalog.version() != null && catalog.version() != CatalogFile.CURRENT_VERSION) {
            errors.add("version : version de format non supportée (" + catalog.version()
                    + "), attendue : " + CatalogFile.CURRENT_VERSION);
        }
        if (catalog.sources().isEmpty() && catalog.workspaces().isEmpty()) {
            errors.add("Le fichier ne contient aucune source ni aucun workspace");
        }
        if (!errors.isEmpty()) {
            throw new InvalidCatalogException(errors);
        }
    }

    private static String describe(Exception e) {
        if (e instanceof JacksonException je) {
            var path = je.getPath().stream()
                    .map(ref -> ref.getPropertyName() != null ? ref.getPropertyName() : "[" + ref.getIndex() + "]")
                    .reduce("", (acc, part) -> part.startsWith("[") || acc.isEmpty() ? acc + part : acc + "." + part);
            return (path.isEmpty() ? "" : path + " : ") + je.getOriginalMessage();
        }
        return e.getMessage();
    }

    private static Map<String, Object> toMap(CatalogFile.SourceEntry source) {
        var map = new LinkedHashMap<String, Object>();
        map.put("name", source.name());
        putIfPresent(map, "description", source.description());
        putIfNotEmpty(map, "hypotheticalQuestions", source.hypotheticalQuestions());
        return map;
    }

    private static Map<String, Object> toMap(CatalogFile.WorkspaceEntry workspace) {
        var map = new LinkedHashMap<String, Object>();
        map.put("name", workspace.name());
        putIfPresent(map, "description", workspace.description());
        putIfNotEmpty(map, "hypotheticalQuestions", workspace.hypotheticalQuestions());
        putIfNotEmpty(map, "sources", workspace.sources());
        return map;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static void putIfNotEmpty(Map<String, Object> map, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            map.put(key, values);
        }
    }

    private static Yaml newLoader() {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(MAX_ALIASES);
        options.setCodePointLimit(CODE_POINT_LIMIT);
        var dumperOptions = new DumperOptions();
        return new Yaml(new SafeConstructor(options), new Representer(dumperOptions), dumperOptions, options, new NullOnlyResolver());
    }

    private static Yaml newDumper() {
        var options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setIndicatorIndent(2);
        options.setIndentWithIndicator(true);
        options.setWidth(120);
        options.setAllowUnicode(true);
        return new Yaml(new MultilineRepresenter(options), options);
    }

    /** Ne résout implicitement que {@code null} : tous les autres scalaires restent des chaînes. */
    private static final class NullOnlyResolver extends Resolver {
        @Override
        protected void addImplicitResolvers() {
            addImplicitResolver(Tag.NULL, NULL, "~nN\0");
            addImplicitResolver(Tag.NULL, EMPTY, null);
        }
    }

    /** Écrit les chaînes multilignes en style littéral ({@code |}) pour un fichier lisible. */
    private static final class MultilineRepresenter extends Representer {
        MultilineRepresenter(DumperOptions options) {
            super(options);
            Represent defaultString = this.representers.get(String.class);
            this.representers.put(String.class, data -> {
                var value = (String) data;
                return value.contains("\n")
                        ? representScalar(Tag.STR, value, DumperOptions.ScalarStyle.LITERAL)
                        : defaultString.representData(data);
            });
        }
    }
}
