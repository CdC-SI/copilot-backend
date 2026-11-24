package zas.admin.zec.backend.persistence.entity;

import org.junit.jupiter.api.Test;
import zas.admin.zec.backend.actions.sourcerequest.RequestStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Callbacks JPA @PrePersist / @PreUpdate des entités : horodatage et valeurs par défaut.
 */
class EntityLifecycleCallbacksTest {

    private static final LocalDateTime PAST = LocalDateTime.of(2020, 1, 1, 0, 0);

    @Test
    void source_onCreateAndOnUpdate_stampDatesAndDefaultQuestions() {
        var source = new SourceEntity();
        source.onCreate();
        assertThat(source.getCreatedAt()).isNotNull();
        assertThat(source.getUpdatedAt()).isNotNull();
        assertThat(source.getHypotheticalQuestions()).isEmpty();

        source.setCreatedAt(PAST);
        source.setUpdatedAt(PAST);
        source.setHypotheticalQuestions(null);
        source.onUpdate();
        assertThat(source.getCreatedAt()).isEqualTo(PAST);
        assertThat(source.getUpdatedAt()).isAfter(PAST);
        assertThat(source.getHypotheticalQuestions()).isEmpty();

        source.setHypotheticalQuestions(new String[]{"q"});
        source.onUpdate();
        assertThat(source.getHypotheticalQuestions()).containsExactly("q");
    }

    @Test
    void workspace_onCreateAndOnUpdate_stampDatesAndKeepExistingQuestions() {
        var workspace = new WorkspaceEntity();
        workspace.setHypotheticalQuestions(new String[]{"q"});
        workspace.onCreate();
        assertThat(workspace.getCreatedAt()).isNotNull();
        assertThat(workspace.getHypotheticalQuestions()).containsExactly("q");

        workspace.setUpdatedAt(PAST);
        workspace.setHypotheticalQuestions(null);
        workspace.onUpdate();
        assertThat(workspace.getUpdatedAt()).isAfter(PAST);
        assertThat(workspace.getHypotheticalQuestions()).isEmpty();

        var fresh = new WorkspaceEntity();
        fresh.onCreate();
        assertThat(fresh.getHypotheticalQuestions()).isEmpty();
    }

    @Test
    void sourceRequest_onCreate_defaultsStatusToWaitingWithoutOverriding() {
        var request = new SourceRequestEntity();
        request.onCreate();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.WAITING);
        assertThat(request.getCreatedAt()).isNotNull();

        var processing = new SourceRequestEntity();
        processing.setStatus(RequestStatus.PROCESSING);
        processing.onCreate();
        assertThat(processing.getStatus()).isEqualTo(RequestStatus.PROCESSING);

        processing.setUpdatedAt(PAST);
        processing.onUpdate();
        assertThat(processing.getUpdatedAt()).isAfter(PAST);
    }

    @Test
    void summaryTask_onCreateAndOnUpdate_stampDates() {
        var task = new SummaryTaskEntity();
        task.onCreate();
        assertThat(task.getCreatedAt()).isNotNull();
        assertThat(task.getUpdatedAt()).isNotNull();

        task.setUpdatedAt(PAST);
        task.onUpdate();
        assertThat(task.getUpdatedAt()).isAfter(PAST);
    }
}
