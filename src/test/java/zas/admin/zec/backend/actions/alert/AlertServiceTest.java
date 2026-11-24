package zas.admin.zec.backend.actions.alert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zas.admin.zec.backend.persistence.entity.AlertEntity;
import zas.admin.zec.backend.persistence.repository.AlertRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Service et controller des alertes, testés ensemble avec un dépôt simulé.
 */
class AlertServiceTest {

    private final AlertRepository repository = mock(AlertRepository.class);
    private final AlertService service = new AlertService(repository);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(i -> {
            AlertEntity entity = i.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(42L);
            }
            return entity;
        });
        mvc = MockMvcBuilders.standaloneSetup(new AlertController(service)).build();
    }

    @Test
    void create_withoutTextsAndExpiration_appliesDefaults() {
        var created = service.create(new Alert(null, AlertLevel.WARNING, null, "Wartung", null, null));

        assertThat(created.id()).isEqualTo(42L);
        assertThat(created.textFr()).isEqualTo("alert.default.text");
        assertThat(created.textDe()).isEqualTo("Wartung");
        assertThat(created.textIt()).isEqualTo("alert.default.text");
        // Expiration par défaut : maintenant + 2 heures.
        assertThat(created.expiresAt()).isCloseTo(LocalDateTime.now().plusHours(2), within(1, java.time.temporal.ChronoUnit.MINUTES));
    }

    @Test
    void create_keepsProvidedExpiration() {
        var expiresAt = LocalDateTime.of(2030, 1, 1, 8, 0);

        var created = service.create(new Alert(null, AlertLevel.INFO, "fr", "de", "it", expiresAt));

        assertThat(created.expiresAt()).isEqualTo(expiresAt);
        assertThat(created.level()).isEqualTo(AlertLevel.INFO);
    }

    @Test
    void getActiveAlerts_queriesNonExpiredAlerts() {
        when(repository.findByExpiresAtAfter(any())).thenReturn(List.of(entity(1L)));

        assertThat(service.getActiveAlerts()).extracting(Alert::id).containsExactly(1L);
        var captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).findByExpiresAtAfter(captor.capture());
        assertThat(captor.getValue()).isCloseTo(LocalDateTime.now(), within(1, java.time.temporal.ChronoUnit.MINUTES));
    }

    @Test
    void reactivate_updatesExpirationOfExistingAlert() {
        var entity = entity(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        var newDate = LocalDateTime.of(2031, 5, 1, 0, 0);

        service.reactivate(1L, newDate);

        assertThat(entity.getExpiresAt()).isEqualTo(newDate);
        verify(repository).save(entity);
    }

    @Test
    void reactivate_unknownAlert_isIgnored() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        service.reactivate(9L, LocalDateTime.now());

        verify(repository, never()).save(any());
    }

    @Test
    void controller_endpoints() throws Exception {
        when(repository.findAll()).thenReturn(List.of(entity(1L), entity(2L)));
        when(repository.findByExpiresAtAfter(any())).thenReturn(List.of(entity(1L)));
        when(repository.findById(1L)).thenReturn(Optional.of(entity(1L)));

        mvc.perform(get("/api/alerts/all")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/alerts/active")).andExpect(status().isOk()).andExpect(jsonPath("$[0].textFr").value("Maintenance"));
        mvc.perform(post("/api/alerts").contentType(MediaType.APPLICATION_JSON).content("{\"level\":\"INFO\",\"textFr\":\"Hello\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.textDe").value("alert.default.text"));
        mvc.perform(put("/api/alerts/1").contentType(MediaType.APPLICATION_JSON).content("{\"expiresAt\":\"2031-01-01T00:00:00\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/alerts/1")).andExpect(status().isNoContent());

        verify(repository).deleteById(1L);
    }

    private static AlertEntity entity(Long id) {
        var entity = new AlertEntity();
        entity.setId(id);
        entity.setLevel(AlertLevel.INFO);
        entity.setTextFr("Maintenance");
        entity.setExpiresAt(LocalDateTime.now().plusHours(1));
        return entity;
    }
}
