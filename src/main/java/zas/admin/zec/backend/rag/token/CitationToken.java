package zas.admin.zec.backend.rag.token;

import java.util.Map;

/**
 * Première citation d'une source dans la réponse en cours (F2a) : émise juste avant le texte qui contient son marqueur
 * {@code [^n]}, pour que le frontend affiche la pastille au fil du flux. Les sources de fin de tour
 * ({@link SourceToken}) restent la liste qui fait foi.
 *
 * @param number numéro affiché ({@code [^n]})
 * @param source source citée, numérotée ({@link SourceToken#citation()})
 */
public record CitationToken(int number, SourceToken source) implements Token {

    public static final String KEY_NUMBER = "number";

    @Override
    public String content() {
        return "<citation><n>%d</n>%s</citation>".formatted(number, source.content());
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of(KEY_NUMBER, String.valueOf(number));
    }
}
