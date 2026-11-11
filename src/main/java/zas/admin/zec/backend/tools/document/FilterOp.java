package zas.admin.zec.backend.tools.document;

/** Opérateurs de filtre sur les métadonnées acceptés par {@code get_documents}. */
public enum FilterOp {
    /** Égalité exacte. */
    EQ,
    /** Égal à l'une des valeurs de {@code values}. */
    IN,
    /** Contient la sous-chaîne (insensible à la casse). */
    CONTAINS,
    /** Supérieur ou égal (numérique pour les clés numériques, lexicographique sinon : dates ISO). */
    GTE,
    /** Inférieur ou égal (numérique pour les clés numériques, lexicographique sinon : dates ISO). */
    LTE
}
