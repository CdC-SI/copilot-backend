package zas.admin.zec.backend.tools;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zas.admin.zec.backend.persistence.entity.tariff.PositionEntity;
import zas.admin.zec.backend.persistence.repository.PositionRepository;

import java.util.stream.Collectors;

@Service
public class TariffService {

    private final PositionRepository positionRepository;

    private static final String INFORMATION_TEMPLATE = "<information>\n            %s\n          </information>";
    private static final String TARIFF_POSITION_NOT_FOUND_TEMPLATE = """
            <id>Position : %s</id>
                  <note>Position inconnue du système.</note>""";

    public TariffService(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    @Transactional
    public String getTariffPositionInfos(String positionId) {
        return positionRepository
                .findById(positionId)
                .map(this::buildInfos)
                .orElse(String.format(TARIFF_POSITION_NOT_FOUND_TEMPLATE, positionId));
    }

    private String buildInfos(PositionEntity position) {
        var subchapter = position.getSubchapter();
        var chapter = subchapter.getChapter();

        var positionSummary = position.getSummary();
        var positionDescription = position.getFulltext().isBlank()
                ? ""
                : String.format(INFORMATION_TEMPLATE, position.getFulltext());

        var subchapterSummary = subchapter.getSummary();
        var subchapterDescription = subchapter.getFulltext().isBlank()
                ? ""
                : String.format(INFORMATION_TEMPLATE, subchapter.getFulltext());

        var chapterSummary = chapter.getSummary();
        var chapterDescription = chapter.getFulltext().isBlank()
                ? ""
                : String.format(INFORMATION_TEMPLATE, chapter.getFulltext());

        var template = """
                <id>{chapter} - {subchapter} - {position} : {positionId}</id>
                      <informations>
                          {chapterDescription}
                          {subchapterDescription}
                          {positionDescription}
                      </informations>
                """
                    .replace("{chapter}", chapterSummary)
                    .replace("{subchapter}", subchapterSummary)
                    .replace("{position}", positionSummary)
                    .replace("{positionId}", position.getId())
                    .replace("{chapterDescription}", chapterDescription)
                    .replace("{subchapterDescription}", subchapterDescription)
                    .replace("{positionDescription}", positionDescription);

        return template.lines()
                .filter(line -> !line.isBlank())
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
