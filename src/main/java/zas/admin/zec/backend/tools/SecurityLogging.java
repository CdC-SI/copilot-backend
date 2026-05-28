package zas.admin.zec.backend.tools;

import ch.admin.zas.jweb.securityevents.advice.RequestDataProvider;
import ch.admin.zas.jweb.securityevents.core.SecurityEventsLogger;
import ch.admin.zas.jweb.securityevents.core.utils.OPDOOperation;
import ch.admin.zas.jweb.securityevents.core.utils.PersonalData;
import ch.admin.zas.jweb.securityevents.event.OPDOEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static ch.admin.zas.jweb.securityevents.core.EventMetaData.FAILURE;
import static ch.admin.zas.jweb.securityevents.core.EventMetaData.SUCCESS;

@Component
public class SecurityLogging {
    final SecurityEventsLogger logger = SecurityEventsLogger.getLogger();

    private final ApplicationEventPublisher applicationEventPublisher;
    private final BuildProperties buildProperties;
    private final RequestDataProvider requestDataProvider;
    private final String applicationEnvironment;

    public SecurityLogging(ApplicationEventPublisher applicationEventPublisher, BuildProperties buildProperties, RequestDataProvider requestDataProvider,
                           @Value("${application.environment:LOCAL}") String applicationEnvironment) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.buildProperties = buildProperties;
        this.requestDataProvider = requestDataProvider;
        this.applicationEnvironment = applicationEnvironment;
    }

    public void log(String operationMessage, OPDOOperation operation, List<? extends PersonalData> personalData) {
        OPDOEvent event = createOPDOEvent(operationMessage, operation, personalData);
        publishEvent(event);
    }

    public void log(String operationMessage, OPDOOperation operation, Object... args) {
        String formattedMessage = String.format(operationMessage.replace("{}", "%s"), args);
        OPDOEvent event = createOPDOEvent(formattedMessage, operation, List.of());
        publishEvent(event);
    }

    protected OPDOEvent createOPDOEvent(String operationMessage, OPDOOperation operation, List<? extends PersonalData> personalData) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return OPDOEvent
                .withEventSourceAndDescription(this, operationMessage)
                .applicationName(buildProperties.getName())
                .applicationNumber("77")
                .applicationVersion(buildProperties.getVersion())
                .applicationEnvironment(applicationEnvironment)
                .username(auth.getName())
                .userRoles(auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(",")))
                .responsePersonalData((List) personalData)
                .operation(operation);
    }

    public void logPrivilegeModification(String eventAction, String destinationUsername) {
        logger.privilegeModification(builder -> builder
                .clientIpAddress(getClientIp().orElse(""))
                .eventAction(eventAction)
                .targetUserName(destinationUsername)
                .eventOutcome(SUCCESS)
                .username(getUserName()));
    }

    public void logAuthenticationSuccess(String username, String httpMethod) {
        logger.authenticationSucceeded(builder -> builder
                .clientIpAddress(getClientIp().orElse(""))
                .httpMethod(httpMethod)
                .username(username));
    }

    private Optional<String> getClientIp() {
        return requestDataProvider.getClientIp();
    }

    private Optional<String> getCalledUrl() {
        return requestDataProvider.getCalledURI();
    }

    private String getUserName() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "anonymous";
        }

        return auth.getName();
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        logAuthenticationSuccess(event.getAuthentication().getName(), "");
    }

    public void logAuthenticationFailure(String reason, String httpMethod) {
        logger.authenticationFailed(builder -> builder
                .clientIpAddress(getClientIp().orElse(""))
                .httpMethod(httpMethod)
                .username(getUserName()));
    }

    public void logSensitiveOperation(String eventAction, boolean success) {
        logger.sensitiveOperation(builder -> builder
                .clientIpAddress(getClientIp().orElse(""))
                .eventAction(eventAction)
                .eventOutcome(success ? SUCCESS : FAILURE)
                .requestUrl(getCalledUrl().orElse(""))
                .username(getUserName()));
    }

    protected void publishEvent(OPDOEvent opdoEvent) {
        applicationEventPublisher.publishEvent(opdoEvent);
    }
}
