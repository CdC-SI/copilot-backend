package zas.admin.zec.backend.actions.sourceview;

/**
 * Vue d'une source citée, affichée dans le panneau des sources du chat.
 *
 * @param id               identifiant de la source ({@code documentId} des sources d'une réponse)
 * @param origin           nature de la source
 * @param display          mode d'affichage proposé par le panneau
 * @param title            titre affiché (nom du fichier, titre du document ou nom du processus)
 * @param url              page d'origine (source web) ou lien BPanda (processus), sinon {@code null}
 * @param pageNumber       page(s) du passage, sinon {@code null}
 * @param subsection       section du passage, sinon {@code null}
 * @param version          version du document ou du processus, sinon {@code null}
 * @param state            état (ex. en vigueur, archivé, {@code personal.uploads}), sinon {@code null}
 * @param excerpt          texte du passage (chunk, contenu extrait de la pièce jointe, objet du processus), sinon {@code null}
 * @param excerptTruncated {@code true} si {@code excerpt} a été tronqué
 * @param fileAvailable    {@code true} si le fichier est servi par {@code GET /api/source-documents/{id}/file}
 */
public record SourceView(
        String id,
        SourceOrigin origin,
        SourceDisplay display,
        String title,
        String url,
        String pageNumber,
        String subsection,
        String version,
        String state,
        String excerpt,
        boolean excerptTruncated,
        boolean fileAvailable) {

    /** Nature d'une source. */
    public enum SourceOrigin {
        /** Chunk de la documentation ou d'un document personnel ({@code vector_store}). */
        DOCUMENT,
        /** Pièce jointe d'une conversation de l'utilisateur. */
        ATTACHMENT,
        /** Processus métier ({@code business_process}) ou sa description. */
        BUSINESS_PROCESS
    }

    /** Mode d'affichage d'une source. */
    public enum SourceDisplay {
        /** Visionneuse PDF ouverte sur le fichier ({@code /file}), à la page du passage. */
        PDF,
        /** Extrait et bouton de téléchargement du fichier ({@code /file}). */
        DOWNLOAD,
        /** Extrait et lien vers la page d'origine ({@code url}). */
        URL,
        /** Lien vers le processus dans BPanda ({@code url}). */
        PROCESS,
        /** Extrait seul (fichier d'origine indisponible). */
        EXCERPT
    }
}
