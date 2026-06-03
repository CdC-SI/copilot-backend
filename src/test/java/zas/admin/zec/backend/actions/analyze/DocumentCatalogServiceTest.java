package zas.admin.zec.backend.actions.analyze;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zas.admin.zec.backend.persistence.entity.DocumentEntity;
import zas.admin.zec.backend.persistence.repository.DocumentRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentCatalogServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private DocumentCatalogService documentCatalogService;

    @Test
    @DisplayName("findById returns doc when found")
    void findById_returnsDoc_whenFound() {
        UUID id = UUID.randomUUID();
        DocumentEntity entity = mock(DocumentEntity.class);
        when(entity.getMetadata()).thenReturn(Map.of("title", "My Doc", "url", "http://example.com"));
        when(documentRepository.findById(id)).thenReturn(Optional.of(entity));

        var result = documentCatalogService.findById(id.toString());

        assertTrue(result.isPresent());
        assertEquals("My Doc", result.get().title());
        assertEquals("http://example.com", result.get().url());
    }

    @Test
    @DisplayName("findById returns empty for invalid UUID")
    void findById_returnsEmpty_forInvalidUuid() {
        var result = documentCatalogService.findById("not-a-uuid");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findById returns empty when not found")
    void findById_returnsEmpty_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findById(id)).thenReturn(Optional.empty());

        var result = documentCatalogService.findById(id.toString());

        assertTrue(result.isEmpty());
    }
}

