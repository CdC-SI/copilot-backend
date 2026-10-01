package zas.admin.zec.backend.persistence;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Lecture tolérante des métadonnées ({@code Map<String, Object>}) des entities et des {@code Document} Spring AI,
 * dont les valeurs peuvent être des types JSON natifs (chaîne, nombre, booléen, tableau).
 */
public final class MetadataValues {

    private static final String LIST_SEPARATOR = ",";

    private MetadataValues() {}

    /**
     * @return la valeur convertie en chaîne : chaîne telle quelle, nombre/booléen via {@code String.valueOf},
     * tableau avec ses éléments joints par {@code ","} ; {@code null} si la clé est absente ou la valeur nulle.
     */
    public static String getString(Map<String, ?> metadata, String key) {
        return getString(metadata, key, null);
    }

    public static String getString(Map<String, ?> metadata, String key, String defaultValue) {
        if (metadata == null) {
            return defaultValue;
        }
        return switch (metadata.get(key)) {
            case null -> defaultValue;
            case String s -> s;
            case Collection<?> c -> c.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.joining(LIST_SEPARATOR));
            case Object o -> String.valueOf(o);
        };
    }
}
