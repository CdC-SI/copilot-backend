package zas.admin.zec.backend.actions.source;

/**
 * Contenu (document ou url) d'une source, dérivé des métadonnées {@code title} / {@code url}
 * des chunks présents dans {@code vector_store}.
 */
public record SourceContentDto(String title, String url) {}
