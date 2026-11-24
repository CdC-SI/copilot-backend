package zas.admin.zec.backend.actions.analyze;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zas.admin.zec.backend.actions.analyze.model.FeedbackReportConfig;
import zas.admin.zec.backend.persistence.entity.FeedbackReportConfigEntity;
import zas.admin.zec.backend.persistence.repository.FeedbackReportConfigRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackReportConfigServiceTest {

    private final FeedbackReportConfigRepository repository = mock(FeedbackReportConfigRepository.class);
    private final FeedbackReportConfigService service = new FeedbackReportConfigService(repository);

    @Test
    void get_splitsAndTrimsRecipients() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(entity("a@zas.ch; b@zas.ch")));

        var config = service.get();

        assertThat(config.enabled()).isTrue();
        assertThat(config.cronExpression()).isEqualTo("0 0 8 * * MON");
        assertThat(config.zoneId()).isEqualTo("Europe/Zurich");
        assertThat(config.recipients()).containsExactly("a@zas.ch", "b@zas.ch");
        assertThat(config.lookbackDays()).isEqualTo(7);
    }

    @Test
    void get_withBlankRecipients_returnsEmptyList() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(entity(" ")));

        assertThat(service.get().recipients()).isEmpty();
    }

    @Test
    void get_withoutConfigurationRow_throws() {
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(service::get).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void update_overwritesTheSingleRowAndJoinsRecipients() {
        var entity = entity("old@zas.ch");
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(entity));

        var result = service.update(new FeedbackReportConfig(false, "0 0 9 * * FRI", "UTC", List.of("x@zas.ch", "y@zas.ch"), 14));

        var captor = ArgumentCaptor.forClass(FeedbackReportConfigEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getRecipients()).isEqualTo("x@zas.ch;y@zas.ch");
        assertThat(captor.getValue().isEnabled()).isFalse();
        assertThat(captor.getValue().getCronExpression()).isEqualTo("0 0 9 * * FRI");
        assertThat(captor.getValue().getZoneId()).isEqualTo("UTC");
        assertThat(captor.getValue().getLookbackDays()).isEqualTo(14);
        assertThat(result.recipients()).containsExactly("x@zas.ch", "y@zas.ch");
    }

    @Test
    void config_validation_rejectsInvalidCronAndMissingFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            assertThat(validator.validate(new FeedbackReportConfig(true, "0 0 8 * * MON", "UTC", List.of("a@zas.ch"), 7))).isEmpty();
            assertThat(validator.validate(new FeedbackReportConfig(true, "pas un cron", "UTC", List.of("a@zas.ch"), 7)))
                    .extracting(v -> v.getMessage())
                    .containsExactly("L'expression cron est invalide");
            assertThat(validator.validate(new FeedbackReportConfig(true, null, "", List.of(), 0))).hasSize(4);
        }
    }

    static FeedbackReportConfigEntity entity(String recipients) {
        var entity = new FeedbackReportConfigEntity();
        entity.setId(1L);
        entity.setEnabled(true);
        entity.setCronExpression("0 0 8 * * MON");
        entity.setZoneId("Europe/Zurich");
        entity.setRecipients(recipients);
        entity.setLookbackDays(7);
        return entity;
    }
}
