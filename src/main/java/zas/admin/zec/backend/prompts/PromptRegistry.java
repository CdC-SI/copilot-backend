package zas.admin.zec.backend.prompts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Registre des prompts LLM, chargés au démarrage depuis le classpath ({@code prompts/<langue>/<clé>.txt}).
 *
 * <p>Le chargement échoue au démarrage si un prompt manque dans une langue ou si ses variables ne correspondent
 * pas à celles déclarées par le {@link PromptId}. Chaque prompt porte une version (empreinte du texte) tracée avec
 * le tour qui l'a utilisé ({@code TurnTrace.prompts}).</p>
 */
@Slf4j
@Component
public class PromptRegistry {

    public static final List<String> LANGUAGES = List.of("fr", "de", "it");
    /** Langue utilisée pour toute langue non prise en charge (comportement historique). */
    public static final String DEFAULT_LANGUAGE = "de";

    private static final int VERSION_LENGTH = 12;

    private final Map<String, Map<PromptId, Prompt>> prompts;

    public PromptRegistry() {
        this("prompts");
    }

    PromptRegistry(String location) {
        Map<String, Map<PromptId, Prompt>> loaded = new HashMap<>();
        List<String> errors = new ArrayList<>();
        for (String language : LANGUAGES) {
            Map<PromptId, Prompt> byId = new EnumMap<>(PromptId.class);
            for (PromptId id : PromptId.values()) {
                String path = location + "/" + language + "/" + id.key() + ".txt";
                String template = read(path);
                if (template == null) {
                    errors.add(path + " : fichier absent");
                    continue;
                }
                errors.addAll(validate(path, id, template));
                byId.put(id, new Prompt(id, language, template, version(template)));
            }
            loaded.put(language, Map.copyOf(byId));
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Prompts invalides :\n- " + String.join("\n- ", errors));
        }
        this.prompts = Map.copyOf(loaded);
        log.info("{} prompts chargés ({} langues)", PromptId.values().length, LANGUAGES.size());
    }

    /** Prompt dans la langue demandée ({@value #DEFAULT_LANGUAGE} si non prise en charge ou {@code null}). */
    public Prompt get(PromptId id, String language) {
        return prompts.get(resolveLanguage(language)).get(id);
    }

    static String resolveLanguage(String language) {
        return language != null && LANGUAGES.contains(language) ? language : DEFAULT_LANGUAGE;
    }

    static List<String> validate(String path, PromptId id, String template) {
        List<String> errors = new ArrayList<>();
        Set<String> expected = new TreeSet<>(id.variables());
        Set<String> found = Prompt.namedVariables(template);
        if (!found.equals(expected)) {
            errors.add("%s : variables %s, attendues %s".formatted(path, found, expected));
        }
        if (Prompt.NAMED_VARIABLE.matcher(template).replaceAll("").contains("{{")) {
            errors.add(path + " : variable {{...}} mal formée");
        }
        return errors;
    }

    private static String read(String path) {
        try (InputStream in = PromptRegistry.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            // Indépendant de la configuration git (core.autocrlf) du poste qui a construit l'application.
            return text.replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du prompt " + path + " impossible", e);
        }
    }

    static String version(String template) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(template.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, VERSION_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
