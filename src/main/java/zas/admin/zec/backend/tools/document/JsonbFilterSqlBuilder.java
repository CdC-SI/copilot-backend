package zas.admin.zec.backend.tools.document;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Traduit les filtres fournis par le LLM ({@link MetadataFilterSpec}) en clause SQL JSONB paramétrée.
 *
 * <p>Sécurité : seules les clés de la liste blanche de la collection sont acceptées ; leur nom est
 * de plus contrôlé par une expression régulière stricte avant d'être inséré dans la requête. Toutes
 * les valeurs sont passées en paramètres liés ({@code ?}), jamais concaténées.</p>
 *
 * <p>Index : l'égalité (EQ, IN) sur une clé textuelle utilise l'opérateur de containment {@code @>}, servi par
 * l'index GIN {@code jsonb_path_ops} ; la valeur correspond à une chaîne, à un élément de tableau, ou au nombre
 * équivalent si elle est numérique. La clé spéciale {@code id} cible la clé primaire (UUID, EQ et IN uniquement).
 * Les autres opérateurs (CONTAINS, GTE, LTE) comparent {@code metadata ->> key} et ne sont pas indexés.</p>
 */
public final class JsonbFilterSqlBuilder {

    public static final String KEY_ID = "id";

    private static final Pattern SAFE_KEY = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern NUMBER = Pattern.compile("^-?[0-9]+(\\.[0-9]+)?$");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_FILTERS = 10;
    private static final int MAX_IN_VALUES = 50;
    private static final int MAX_VALUE_LENGTH = 500;

    private final Set<String> allowedKeys;
    private final Set<String> numericKeys;

    public JsonbFilterSqlBuilder(Collection<String> allowedKeys, Collection<String> numericKeys) {
        this.allowedKeys = Set.copyOf(allowedKeys);
        this.numericKeys = Set.copyOf(numericKeys);
    }

    /** Clause SQL (conditions combinées par AND, sans le mot-clé WHERE) et ses paramètres. */
    public record SqlFragment(String sql, List<Object> params) {}

    /** Erreur de filtre, dont le message est destiné au LLM pour qu'il corrige son appel. */
    public static final class InvalidFilterException extends RuntimeException {
        public InvalidFilterException(String message) {
            super(message);
        }
    }

    public SqlFragment build(List<MetadataFilterSpec> filters) {
        if (filters == null || filters.isEmpty()) {
            throw new InvalidFilterException("At least one filter is required.");
        }
        if (filters.size() > MAX_FILTERS) {
            throw new InvalidFilterException("Too many filters (max %d).".formatted(MAX_FILTERS));
        }
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (MetadataFilterSpec filter : filters) {
            conditions.add(condition(filter, params));
        }
        return new SqlFragment(String.join(" AND ", conditions), params);
    }

    public boolean hasFilterOn(List<MetadataFilterSpec> filters, String... keys) {
        if (filters == null) {
            return false;
        }
        Set<String> wanted = Set.of(keys);
        return filters.stream().anyMatch(f -> f != null && f.key() != null && wanted.contains(f.key().strip()));
    }

    private String condition(MetadataFilterSpec filter, List<Object> params) {
        if (filter == null || filter.key() == null || filter.op() == null) {
            throw new InvalidFilterException("Each filter needs a 'key' and an 'op'.");
        }
        String key = filter.key().strip();
        if (!allowedKeys.contains(key) || !SAFE_KEY.matcher(key).matches()) {
            throw new InvalidFilterException("Unknown key '%s'. Allowed keys: %s."
                    .formatted(key, String.join(", ", new TreeSet<>(allowedKeys))));
        }

        boolean numeric = numericKeys.contains(key);
        if (KEY_ID.equals(key)) {
            return idCondition(filter, params);
        }
        String textExpr = "(metadata ->> '" + key + "')";
        String numericExpr = "(CASE WHEN " + textExpr + " ~ '^-?[0-9]+(\\.[0-9]+)?$' THEN " + textExpr + "::numeric END)";

        return switch (filter.op()) {
            case EQ -> {
                String v = requireValue(filter);
                if (numeric) {
                    params.add(parseNumber(key, v));
                    yield numericExpr + " = ?";
                }
                yield containment(key, List.of(v), params);
            }
            case IN -> {
                List<String> values = requireValues(filter);
                if (numeric) {
                    StringJoiner placeholders = new StringJoiner(", ", "(", ")");
                    for (String v : values) {
                        params.add(parseNumber(key, v));
                        placeholders.add("?");
                    }
                    yield numericExpr + " IN " + placeholders;
                }
                yield containment(key, values, params);
            }
            case CONTAINS -> {
                String v = requireValue(filter);
                params.add("%" + escapeLike(v) + "%");
                yield textExpr + " ILIKE ? ESCAPE '\\'";
            }
            case GTE, LTE -> {
                String v = requireValue(filter);
                String operator = filter.op() == FilterOp.GTE ? " >= ?" : " <= ?";
                if (numeric) {
                    params.add(parseNumber(key, v));
                    yield numericExpr + operator;
                }
                params.add(v);
                yield textExpr + operator;
            }
        };
    }

    /** Clé primaire : comparaison en UUID (index de clé primaire), EQ et IN uniquement. */
    private static String idCondition(MetadataFilterSpec filter, List<Object> params) {
        return switch (filter.op()) {
            case EQ -> {
                params.add(parseUuid(requireValue(filter)));
                yield "id = ?";
            }
            case IN -> {
                StringJoiner placeholders = new StringJoiner(", ", "(", ")");
                for (String v : requireValues(filter)) {
                    params.add(parseUuid(v));
                    placeholders.add("?");
                }
                yield "id IN " + placeholders;
            }
            default -> throw new InvalidFilterException("Key 'id' only supports the operators EQ and IN.");
        };
    }

    /**
     * Égalité par containment ({@code metadata @> {"key": value}}), servie par l'index GIN. Pour chaque valeur :
     * chaîne, élément d'un tableau, et nombre équivalent si la valeur est numérique (conditions combinées par OR).
     */
    private static String containment(String key, List<String> values, List<Object> params) {
        StringJoiner alternatives = new StringJoiner(" OR ", "(", ")");
        for (String value : values) {
            List<Object> candidates = new ArrayList<>(List.of(value, List.of(value)));
            if (NUMBER.matcher(value).matches()) {
                candidates.add(new BigDecimal(value));
            }
            for (Object candidate : candidates) {
                params.add(JSON.writeValueAsString(Map.of(key, candidate)));
                alternatives.add("metadata @> ?::jsonb");
            }
        }
        return alternatives.toString();
    }

    private static List<String> requireValues(MetadataFilterSpec filter) {
        List<String> values = filter.values();
        if (values == null || values.isEmpty()) {
            throw new InvalidFilterException("Operator IN on '%s' requires a non-empty 'values' list.".formatted(filter.key()));
        }
        if (values.size() > MAX_IN_VALUES) {
            throw new InvalidFilterException("Too many values for IN (max %d).".formatted(MAX_IN_VALUES));
        }
        List<String> stripped = new ArrayList<>(values.size());
        for (String v : values) {
            checkLength(v);
            stripped.add(v.strip());
        }
        return stripped;
    }

    private static UUID parseUuid(String v) {
        String candidate = v.strip();
        if (!UUID_PATTERN.matcher(candidate).matches()) {
            throw new InvalidFilterException(
                    "Key 'id' expects a document id (UUID, as listed in <retrieved_sources>), got '%s'.".formatted(v));
        }
        return UUID.fromString(candidate);
    }

    private static String requireValue(MetadataFilterSpec filter) {
        String v = filter.value();
        if (v == null || v.isBlank()) {
            throw new InvalidFilterException("Operator %s on '%s' requires a 'value'.".formatted(filter.op(), filter.key()));
        }
        checkLength(v);
        return v.strip();
    }

    private static void checkLength(String v) {
        if (v == null || v.length() > MAX_VALUE_LENGTH) {
            throw new InvalidFilterException("Filter values must be non-null and at most %d characters.".formatted(MAX_VALUE_LENGTH));
        }
    }

    private static java.math.BigDecimal parseNumber(String key, String v) {
        try {
            return new java.math.BigDecimal(v.strip());
        } catch (NumberFormatException e) {
            throw new InvalidFilterException("Key '%s' expects a numeric value, got '%s'.".formatted(key, v));
        }
    }

    private static String escapeLike(String v) {
        return v.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
