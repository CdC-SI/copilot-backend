package zas.admin.zec.backend.prompts;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt chargé par le {@link PromptRegistry}, dans une langue donnée.
 *
 * @param template texte brut du fichier (fins de ligne normalisées en {@code \n})
 * @param version  empreinte courte (SHA-256) du texte brut, tracée avec chaque tour
 */
public record Prompt(PromptId id, String language, String template, String version) {

    static final Pattern NAMED_VARIABLE = Pattern.compile("\\{\\{([a-z_]+)}}");

    /** Clé de traçabilité : {@code <clé>/<langue>} (ex. {@code agentic-system/fr}). */
    public String traceKey() {
        return id.key() + "/" + language;
    }

    public String render(String name, Object value) {
        Map<String, Object> variables = new HashMap<>();
        variables.put(name, value);
        return render(variables);
    }

    public String render(String name1, Object value1, String name2, Object value2) {
        Map<String, Object> variables = new HashMap<>();
        variables.put(name1, value1);
        variables.put(name2, value2);
        return render(variables);
    }

    /**
     * Substitue les variables {@code {{nom}}} en une seule passe (les valeurs ne sont pas réinterprétées). Les
     * variables fournies doivent correspondre exactement à celles déclarées par le {@link PromptId} ; une valeur
     * {@code null} est rendue {@code "null"}, comme le faisait {@code String.formatted}.
     */
    public String render(Map<String, ?> variables) {
        if (id.syntax() != PromptId.Syntax.NAMED) {
            throw new IllegalStateException("Le prompt " + id + " est rendu par Spring AI : utiliser template()");
        }
        if (!new TreeSet<>(variables.keySet()).equals(new TreeSet<>(id.variables()))) {
            throw new IllegalArgumentException("Variables du prompt %s : attendues %s, fournies %s"
                    .formatted(id, new TreeSet<>(id.variables()), new TreeSet<>(variables.keySet())));
        }
        Matcher matcher = NAMED_VARIABLE.matcher(template);
        StringBuilder rendered = new StringBuilder(template.length());
        while (matcher.find()) {
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(String.valueOf(variables.get(matcher.group(1)))));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    static Set<String> namedVariables(String template) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = NAMED_VARIABLE.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
