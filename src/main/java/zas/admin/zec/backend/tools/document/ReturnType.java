package zas.admin.zec.backend.tools.document;

/** Partie du document retournée par {@code get_documents}. */
public enum ReturnType {
    /** Métadonnées uniquement (bloc {@code <metadata>}). */
    METADATA,
    /** Contenu uniquement. */
    CONTENT,
    /** Contenu et métadonnées. */
    BOTH;

    public boolean includesContent() {
        return this != METADATA;
    }

    public boolean includesMetadata() {
        return this != CONTENT;
    }
}
