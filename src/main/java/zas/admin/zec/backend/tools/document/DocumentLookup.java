package zas.admin.zec.backend.tools.document;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Recherche exacte par métadonnées dans une collection donnée (cf. {@link DocumentCollection}).
 * Les implémentations appliquent systématiquement les règles d'accès (documents internes, documents
 * personnels) en plus des filtres fournis par le LLM.
 */
public interface DocumentLookup {

    DocumentCollection collection();

    /** Clés de métadonnées interrogeables (liste blanche), exposées dans la description du tool. */
    List<String> filterableKeys();

    LookupResult find(LookupRequest request);

    /**
     * @param filters                 filtres fournis par le LLM
     * @param limit                   nombre maximal de résultats
     * @param userId                  utilisateur courant
     * @param hasInternalAccess       accès aux documents internes (ZAS)
     * @param language                langue de l'utilisateur (préférence par défaut si pertinente)
     * @param includeContent          charger le contenu (sinon seules les métadonnées sont lues)
     */
    record LookupRequest(List<MetadataFilterSpec> filters, int limit, String userId,
                         boolean hasInternalAccess, String language, boolean includeContent) {}

    /**
     * @param documents résultats (métadonnées complètes ; marqueur de collection renseigné)
     * @param truncated d'autres résultats existent au-delà de la limite
     * @param notice    information complémentaire à transmettre au LLM (peut être {@code null})
     */
    record LookupResult(List<Document> documents, boolean truncated, String notice) {}
}
