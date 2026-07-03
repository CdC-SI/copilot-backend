package zas.admin.zec.backend.actions.source;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO représentant une Source. Les {@code contents} sont dérivés de {@code vector_store}
 * et ne sont renseignés que dans la vue de détail.
 */
public record SourceDto(
        Long id,
        String name,
        String description,
        List<String> hypotheticalQuestions,
        List<SourceContentDto> contents,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
