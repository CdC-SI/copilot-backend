package zas.admin.zec.backend.actions.analyze;

import org.springframework.stereotype.Service;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;

import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentCatalogService {

    private final DocumentRepository documentRepository;

    public DocumentCatalogService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    record Doc(String title, String url) {}
    Optional<Doc> findById(String documentId) {
        try {
            return documentRepository.findById(UUID.fromString(documentId))
                .map(doc -> new Doc(doc.getMetadata().getOrDefault("title", ""), doc.getMetadata().getOrDefault("url", "")));
        } catch (IllegalArgumentException e) {
            //Legacy IDs are not UUIDs
            return Optional.empty();
        }
    }
}
