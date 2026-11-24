package zas.admin.zec.backend.tools;

import ch.admin.zas.jweb.securityevents.advice.RequestDataProvider;
import ch.admin.zas.jweb.securityevents.core.utils.OPDOOperation;
import ch.admin.zas.jweb.securityevents.event.OPDOEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Événements de sécurité OPDO (accès aux données personnelles) et opérations sensibles. */
class SecurityLoggingTest {

    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final RequestDataProvider requestData = mock(RequestDataProvider.class);
    private final SecurityLogging securityLogging = new SecurityLogging(publisher, buildProperties(), requestData, "TEST");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static BuildProperties buildProperties() {
        var properties = new Properties();
        properties.setProperty("name", "copilot-backend");
        properties.setProperty("version", "5.0.0");
        return new BuildProperties(properties);
    }

    private OPDOEvent publishedEvent() {
        var captor = ArgumentCaptor.forClass(OPDOEvent.class);
        verify(publisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    void opdoEventCarriesTheApplicationTheUserAndThePersonalData() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("jdoe", null, "ROLE_USER", "ROLE_ADMIN"));
        var personalData = OpdoPersonalData.builder().field("firstName", "John").field("lastName", "Doe").build();

        securityLogging.log("Accès au contenu d'une conversation", OPDOOperation.READING, List.of(personalData));

        var event = publishedEvent();
        assertEquals("Accès au contenu d'une conversation", event.getDescription());
        assertEquals(OPDOOperation.READING, event.getOperation());
        assertEquals("copilot-backend", event.getApplicationName());
        assertEquals("77", event.getApplicationNumber());
        assertEquals("5.0.0", event.getApplicationVersion());
        assertEquals("TEST", event.getApplicationEnvironment());
        assertEquals("jdoe", event.getUsername());
        assertEquals("ROLE_USER,ROLE_ADMIN", event.getUserRoles());
        assertEquals("firstName=John, lastName=Doe", event.getResponsePersonalData().iterator().next().print());
    }

    @Test
    void formattedMessageReplacesPlaceholdersWithoutPersonalData() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("jdoe", null));

        securityLogging.log("Suppression du document {} ({})", OPDOOperation.DELETION, "memento.pdf", 3);

        var event = publishedEvent();
        assertEquals("Suppression du document memento.pdf (3)", event.getDescription());
        assertEquals(OPDOOperation.DELETION, event.getOperation());
        assertEquals(0, event.getResponsePersonalData().size());
    }

    @Test
    void sensitiveOperationsAndPrivilegeModificationsAreLoggedWithOrWithoutAuthentication() {
        when(requestData.getClientIp()).thenReturn(Optional.of("10.0.0.1"));
        when(requestData.getCalledURI()).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> securityLogging.logSensitiveOperation("api-key-created", true));
        assertDoesNotThrow(() -> securityLogging.logPrivilegeModification("role-added", "alice"));

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("jdoe", null));
        assertDoesNotThrow(() -> securityLogging.logSensitiveOperation("api-key-revoked", false));

        verify(requestData, org.mockito.Mockito.times(3)).getClientIp();
    }
}
