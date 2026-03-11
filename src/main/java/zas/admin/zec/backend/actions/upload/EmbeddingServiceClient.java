package zas.admin.zec.backend.actions.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import zas.admin.zec.backend.actions.upload.model.EmbeddingChunkResponse;
import zas.admin.zec.backend.actions.upload.model.EmbeddingRequest;
import zas.admin.zec.backend.actions.upload.model.EmbeddingServiceResponse;
import zas.admin.zec.backend.config.properties.EmbeddingServiceProperties;

import java.util.List;

/**
 * Client HTTP pour le service externe d'embedding de documents.
 * Envoie un document en base64 et reçoit les chunks avec leurs embeddings pré-calculés.
 */
@Slf4j
@Service
public class EmbeddingServiceClient {

    private final WebClient webClient;

    public EmbeddingServiceClient(@Qualifier("clientBuilderForInternalCalls") WebClient.Builder clientBuilder,
                                  EmbeddingServiceProperties properties) {
        this.webClient = clientBuilder
                .baseUrl(properties.baseUrl())
                .build();
    }

    private record VLLMWrapper (List<EmbeddingRequest> instances) {}

    /**
     * Appelle le service externe d'embedding pour traiter un document.
     *
     * @param request la requête contenant le document en base64, l'UUID utilisateur et le titre
     * @return la liste des chunks avec contenu, embedding et métadonnées
     */
    public List<EmbeddingChunkResponse> embed(EmbeddingRequest request) {
        log.info("Appel du service externe d'embedding pour le document : {}", request.docTitle());

        EmbeddingServiceResponse response = webClient.post()
                .uri("/v1/models/user-pdf-preprocessing:predict")
                .bodyValue(new VLLMWrapper(List.of(request)))
                .retrieve()
                .bodyToMono(EmbeddingServiceResponse.class)
                .block();

        var chunks = response != null && response.documents() != null
                ? response.documents()
                : List.<EmbeddingChunkResponse>of();

        log.info("Service externe d'embedding : {} chunks reçus pour le document {}",
                chunks.size(), request.docTitle());

        return chunks;
    }
}

