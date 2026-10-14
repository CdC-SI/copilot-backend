package zas.admin.zec.backend.tools.document;

/**
 * Collection interrogée par {@code get_documents}.
 * <ul>
 *   <li>{@link #DOCUMENTATION} : chunks de documentation ({@code vector_store}).</li>
 *   <li>{@link #BUSINESS_PROCESS} : processus métier internes versionnés ({@code business_process}).</li>
 * </ul>
 */
public enum DocumentCollection {
    DOCUMENTATION,
    BUSINESS_PROCESS;

    /** Valeur (minuscule) utilisée dans les métadonnées et les balises exposées au LLM. */
    public String key() {
        return name().toLowerCase();
    }

    public static DocumentCollection fromKey(String key) {
        if (key == null) {
            return DOCUMENTATION;
        }
        for (var c : values()) {
            if (c.key().equalsIgnoreCase(key) || c.name().equalsIgnoreCase(key)) {
                return c;
            }
        }
        return DOCUMENTATION;
    }
}
