package zas.admin.zec.backend.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zas.admin.zec.backend.persistence.entity.tariff.ChapterEntity;
import zas.admin.zec.backend.persistence.entity.tariff.PositionEntity;
import zas.admin.zec.backend.persistence.entity.tariff.SubChapterEntity;
import zas.admin.zec.backend.persistence.repository.PositionRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TariffServiceTest {

    @Mock
    private PositionRepository positionRepository;

    @InjectMocks
    private TariffService tariffService;

    @Test
    @DisplayName("Returns formatted info when position exists with fulltext")
    void getTariffPositionInfos_returnsFormattedInfo_whenPositionExists() {
        ChapterEntity chapter = mock(ChapterEntity.class);
        when(chapter.getSummary()).thenReturn("Chapter Summary");
        when(chapter.getFulltext()).thenReturn("Chapter details");

        SubChapterEntity subchapter = mock(SubChapterEntity.class);
        when(subchapter.getSummary()).thenReturn("Subchapter Summary");
        when(subchapter.getFulltext()).thenReturn("Subchapter details");
        when(subchapter.getChapter()).thenReturn(chapter);

        PositionEntity position = mock(PositionEntity.class);
        when(position.getId()).thenReturn("POS123");
        when(position.getSummary()).thenReturn("Position Summary");
        when(position.getFulltext()).thenReturn("Position details");
        when(position.getSubchapter()).thenReturn(subchapter);

        when(positionRepository.findById("POS123")).thenReturn(Optional.of(position));

        String result = tariffService.getTariffPositionInfos("POS123");

        assertTrue(result.contains("Chapter Summary"));
        assertTrue(result.contains("Subchapter Summary"));
        assertTrue(result.contains("Position Summary"));
        assertTrue(result.contains("POS123"));
    }

    @Test
    @DisplayName("Returns not found message when position does not exist")
    void getTariffPositionInfos_returnsNotFound_whenPositionDoesNotExist() {
        when(positionRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        String result = tariffService.getTariffPositionInfos("UNKNOWN");

        assertTrue(result.contains("UNKNOWN"));
        assertTrue(result.contains("Position inconnue"));
    }

    @Test
    @DisplayName("Omits empty fulltext sections")
    void getTariffPositionInfos_omitsEmptyFulltext() {
        ChapterEntity chapter = mock(ChapterEntity.class);
        when(chapter.getSummary()).thenReturn("Ch");
        when(chapter.getFulltext()).thenReturn("");

        SubChapterEntity subchapter = mock(SubChapterEntity.class);
        when(subchapter.getSummary()).thenReturn("Sub");
        when(subchapter.getFulltext()).thenReturn("");
        when(subchapter.getChapter()).thenReturn(chapter);

        PositionEntity position = mock(PositionEntity.class);
        when(position.getId()).thenReturn("P1");
        when(position.getSummary()).thenReturn("Pos");
        when(position.getFulltext()).thenReturn("");
        when(position.getSubchapter()).thenReturn(subchapter);

        when(positionRepository.findById("P1")).thenReturn(Optional.of(position));

        String result = tariffService.getTariffPositionInfos("P1");

        assertFalse(result.contains("<information>"));
    }
}


