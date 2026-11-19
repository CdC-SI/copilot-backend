package zas.admin.zec.backend.rag.remote;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Recherche documentaire de {@code RAGTool}, déléguée à zia-rag : seuls la question, le filtre de sources et
 * l'utilisateur sont transmis ; nombre de documents, mode de recherche, reranking et seuils sont les valeurs par
 * défaut de zia-rag ({@code zia.rag.retrieval.*}).
 */
@Component
public class ZiaRagDocumentRetriever {

    static final String META_SOURCE = "source";

    private final ZiaRagClient client;

    public ZiaRagDocumentRetriever(ZiaRagClient client) {
        this.client = client;
    }

    /**
     * Documents pertinents pour la question, limités aux sources données (toutes si vide) : documents publics et
     * privés de {@code userId}. {@code bearer} : jeton Blue de l'utilisateur.
     */
    public List<Document> retrieve(String question, Collection<String> sources, String userId, String bearer) {
        String user = userId == null || userId.isBlank() ? null : userId;
        return client.retrieve(new ZiaRagClient.Request(question, config(sources), user), bearer);
    }

    static ZiaRagClient.Config config(Collection<String> sources) {
        return CollectionUtils.isEmpty(sources)
                ? null : new ZiaRagClient.Config(Map.of(META_SOURCE, List.copyOf(sources)));
    }
}
