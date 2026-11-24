package zas.admin.zec.backend.config.security;

import io.jsonwebtoken.Jwts;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import zas.admin.zec.backend.actions.alert.AlertController;
import zas.admin.zec.backend.actions.alert.AlertService;
import zas.admin.zec.backend.actions.analyze.FeedbackKPIController;
import zas.admin.zec.backend.actions.analyze.FeedbackQueryService;
import zas.admin.zec.backend.actions.analyze.FeedbackReportConfigService;
import zas.admin.zec.backend.actions.api.PublicKeyController;
import zas.admin.zec.backend.actions.askfaq.FAQItemsController;
import zas.admin.zec.backend.actions.askfaq.FAQService;
import zas.admin.zec.backend.actions.authenticate.IdentityCheckController;
import zas.admin.zec.backend.actions.authenticate.IdentityCheckService;
import zas.admin.zec.backend.actions.authorize.Role;
import zas.admin.zec.backend.actions.authorize.User;
import zas.admin.zec.backend.actions.authorize.UserController;
import zas.admin.zec.backend.actions.authorize.UserService;
import zas.admin.zec.backend.actions.authorize.UserStatus;
import zas.admin.zec.backend.actions.catalog.CatalogController;
import zas.admin.zec.backend.actions.catalog.CatalogService;
import zas.admin.zec.backend.actions.catalog.CatalogYamlMapper;
import zas.admin.zec.backend.actions.converse.ConversationController;
import zas.admin.zec.backend.actions.converse.ConversationService;
import zas.admin.zec.backend.actions.convert.SumexInvoiceToXmlTemplateService;
import zas.admin.zec.backend.actions.convert.SumexInvoicesController;
import zas.admin.zec.backend.actions.parametrize.SettingController;
import zas.admin.zec.backend.actions.parametrize.SettingService;
import zas.admin.zec.backend.actions.rate.FeedbackController;
import zas.admin.zec.backend.actions.rate.FeedbackService;
import zas.admin.zec.backend.actions.source.SourceController;
import zas.admin.zec.backend.actions.source.SourceService;
import zas.admin.zec.backend.actions.sourcerequest.SourceRequestController;
import zas.admin.zec.backend.actions.sourcerequest.SourceRequestService;
import zas.admin.zec.backend.actions.sourceview.SourceViewController;
import zas.admin.zec.backend.actions.sourceview.SourceViewService;
import zas.admin.zec.backend.actions.summarize.SummarizeController;
import zas.admin.zec.backend.actions.summarize.SummarizeService;
import zas.admin.zec.backend.actions.summarize.jms.GaimeJmsService;
import zas.admin.zec.backend.actions.upload.DocumentController;
import zas.admin.zec.backend.actions.upload.DocumentRetentionConfigService;
import zas.admin.zec.backend.actions.upload.UploadService;
import zas.admin.zec.backend.actions.visualize.VisionService;
import zas.admin.zec.backend.actions.visualize.VisualizeController;
import zas.admin.zec.backend.actions.workspace.WorkspaceController;
import zas.admin.zec.backend.actions.workspace.WorkspaceService;
import zas.admin.zec.backend.config.ExceptionsHandler;
import zas.admin.zec.backend.config.api.ApiKeyAuthenticationProvider;
import zas.admin.zec.backend.config.api.ApiKeyAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import zas.admin.zec.backend.tools.SecurityLogging;
import zas.admin.zec.backend.tools.api.ApiKeyService;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Contrôle d'accès de chaque endpoint REST avec la vraie configuration de sécurité ({@link SecurityConfig} : chaîne de
 * filtres, jeton Blue JWT signé RS512, annotations {@code @Require*} évaluées par {@link AuthorizationLogic}) ; les
 * services des controllers sont simulés.
 *
 * <p>Pour un nouvel endpoint : l'ajouter à {@link #endpoints()} avec son niveau d'accès attendu ({@link Access}) ;
 * {@link #everyEndpointIsListed()} échoue tant qu'un endpoint des controllers importés n'y figure pas. Un nouveau
 * controller s'ajoute à {@link Config} (et ses dépendances en {@code @MockitoBean}) ;
 * {@link #everyApplicationController_isInTheTestedContext()} échoue tant qu'il n'y est pas.
 */
@SpringJUnitWebConfig(ControllerSecurityTest.Config.class)
class ControllerSecurityTest {

    private static final KeyPair BLUE_KEYS = rsaKeyPair();
    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, AuthorizationLogic.class, ExceptionsHandler.class,
            AlertController.class, PublicKeyController.class, FAQItemsController.class, WorkspaceController.class,
            IdentityCheckController.class, CatalogController.class, FeedbackKPIController.class,
            ConversationController.class, VisualizeController.class, UserController.class,
            SumexInvoicesController.class, FeedbackController.class, SourceViewController.class,
            SettingController.class, SourceController.class, SourceRequestController.class,
            SummarizeController.class, DocumentController.class})
    static class Config {
    }

    @DynamicPropertySource
    static void bluePublicKey(DynamicPropertyRegistry registry) {
        registry.add("zas.security.blue-token.public-key",
                () -> Base64.getEncoder().encodeToString(BLUE_KEYS.getPublic().getEncoded()));
    }

    @MockitoBean UserService userService;
    @MockitoBean SecurityLogging securityLogging;
    @MockitoBean ApiKeyAuthenticationProvider apiKeyAuthenticationProvider;
    @MockitoBean AlertService alertService;
    @MockitoBean ApiKeyService apiKeyService;
    @MockitoBean FAQService faqService;
    @MockitoBean WorkspaceService workspaceService;
    @MockitoBean IdentityCheckService identityCheckService;
    @MockitoBean CatalogService catalogService;
    @MockitoBean CatalogYamlMapper catalogYamlMapper;
    @MockitoBean FeedbackQueryService feedbackQueryService;
    @MockitoBean FeedbackReportConfigService feedbackReportConfigService;
    @MockitoBean ConversationService conversationService;
    @MockitoBean VisionService visionService;
    @MockitoBean SumexInvoiceToXmlTemplateService sumexTemplateService;
    @MockitoBean FeedbackService feedbackService;
    @MockitoBean SourceViewService sourceViewService;
    @MockitoBean SettingService settingService;
    @MockitoBean SourceService sourceService;
    @MockitoBean SourceRequestService sourceRequestService;
    @MockitoBean SummarizeService summarizeService;
    @MockitoBean GaimeJmsService gaimeJmsService;
    @MockitoBean UploadService uploadService;
    @MockitoBean DocumentRetentionConfigService documentRetentionConfigService;

    @Autowired WebApplicationContext context;
    @Autowired RequestMappingHandlerMapping handlerMapping;
    private MockMvc mvc;

    /** Profils d'appelants. Les profils « Blue » s'authentifient avec un vrai JWT signé (en-tête {@code blue}). */
    enum Persona {
        ANONYMOUS(null, null, false, false),
        /** Authentifié par Blue, sans compte ZIA. */
        UNREGISTERED("unregistered", null, false, true),
        USER("user", List.of(Role.USER), false, true),
        INTERNAL_USER("internal", List.of(Role.USER), true, true),
        EXPERT("expert", List.of(Role.USER, Role.EXPERT), false, true),
        TRANSLATOR("translator", List.of(Role.USER, Role.TRANSLATOR), false, true),
        ADMIN("admin", List.of(Role.USER, Role.ADMIN), false, true),
        INACTIVE_ADMIN("inactive-admin", List.of(Role.USER, Role.ADMIN), false, true),
        /** Utilisateur actif authentifié autrement que par un jeton Blue (pas un {@link ZasUser}). */
        NON_BLUE_USER("non-blue", List.of(Role.USER), false, false);

        final String username;
        final List<Role> roles;
        final boolean internal;
        final boolean blueToken;

        Persona(String username, List<Role> roles, boolean internal, boolean blueToken) {
            this.username = username;
            this.roles = roles;
            this.internal = internal;
            this.blueToken = blueToken;
        }

        boolean has(Role role) {
            return roles != null && this != INACTIVE_ADMIN && roles.contains(role);
        }
    }

    /** Niveau d'accès attendu d'un endpoint, c.-à-d. son annotation {@code @Require*} (ou aucune). */
    enum Access {
        AUTHENTICATED(p -> true),
        USER(p -> p.has(Role.USER)),
        ZAS_USER(p -> p.blueToken && p.has(Role.USER)),
        INTERNAL_USER(p -> p.internal),
        EXPERT(p -> p.has(Role.EXPERT) || p.has(Role.ADMIN)),
        TRANSLATOR(p -> p.has(Role.TRANSLATOR) || p.has(Role.ADMIN)),
        ADMIN(p -> p.has(Role.ADMIN));

        private final Predicate<Persona> allows;

        Access(Predicate<Persona> allows) {
            this.allows = allows;
        }
    }

    record Endpoint(String key, Access access, Supplier<AbstractMockHttpServletRequestBuilder<?>> request) {
        @Override
        public String toString() {
            return key + " -> " + access;
        }
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(userService.getByUsername(anyString())).thenThrow(new IllegalArgumentException("User not found"));
        when(userService.getUuid(anyString())).thenThrow(new IllegalArgumentException("User not found"));
        for (var persona : Persona.values()) {
            if (persona.roles == null) {
                continue;
            }
            var status = persona == Persona.INACTIVE_ADMIN ? UserStatus.INACTIVE : UserStatus.ACTIVE;
            doReturn(new User(persona.username, "Prénom", "Nom", status, persona.roles, List.of(), persona.internal))
                    .when(userService).getByUsername(persona.username);
            doReturn("uuid-" + persona.username).when(userService).getUuid(persona.username);
            when(userService.hasAccessToInternalDocuments("uuid-" + persona.username)).thenReturn(persona.internal);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void endpoint_isRestrictedToItsAccessLevel(Endpoint endpoint) {
        var softly = new SoftAssertions();
        for (var persona : Persona.values()) {
            var outcome = call(endpoint, persona);
            var description = "%s pour %s".formatted(endpoint.key(), persona);
            if (persona == Persona.ANONYMOUS) {
                softly.assertThat(outcome).as(description).isEqualTo("401");
            } else if (endpoint.access().allows.test(persona)) {
                // 400/405/415 : la requête n'a pas atteint le controller (corps ou paramètres invalides), le test serait muet.
                softly.assertThat(outcome).as(description).isNotIn("400", "401", "403", "405", "415").doesNotStartWith("error");
            } else {
                softly.assertThat(outcome).as(description).isEqualTo("403");
            }
        }
        softly.assertAll();
    }

    @Test
    void everyEndpointIsListed() {
        var mapped = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream().flatMap(pattern -> info.getMethodsCondition().getMethods().stream()
                        .map(method -> key(HttpMethod.valueOf(method.name()), pattern,
                                info.getParamsCondition().getExpressions().stream().map(Object::toString).toArray(String[]::new)))))
                .collect(Collectors.toCollection(TreeSet::new));
        var listed = endpoints().map(Endpoint::key).collect(Collectors.toCollection(TreeSet::new));

        assertThat(listed).as("endpoints listés").containsExactlyElementsOf(mapped);
    }

    @Test
    void everyApplicationController_isInTheTestedContext() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        var declared = scanner.findCandidateComponents("zas.admin.zec.backend").stream()
                .map(BeanDefinition::getBeanClassName)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(declared).as("controllers trouvés par le scan").contains(ConversationController.class.getName());
        assertThat(controllerClassNames()).as("controllers importés dans Config").containsAll(declared);
    }

    @Test
    void blueToken_signedWithAnotherKey_isRejected() throws Exception {
        var token = Jwts.builder().subject("{\"userUID\":\"admin\"}").signWith(rsaKeyPair().getPrivate(), Jwts.SIG.RS512).compact();

        var status = mvc.perform(request(HttpMethod.GET, "/api/alerts/all").header("blue", "Bearer " + token))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(401);
        verify(alertService, never()).getAllAlerts();
    }

    @Test
    void blueToken_expired_isRejected() throws Exception {
        var token = Jwts.builder().subject("{\"userUID\":\"admin\"}")
                .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .signWith(BLUE_KEYS.getPrivate(), Jwts.SIG.RS512).compact();

        var status = mvc.perform(request(HttpMethod.GET, "/api/alerts/all").header("blue", "Bearer " + token))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(401);
    }

    @Test
    void blueToken_claimsAreMappedToZasUser() throws Exception {
        var token = Jwts.builder()
                .subject("{\"userUID\":\"user\",\"firstname\":\"Jean\",\"lastname\":\"Dupont\",\"visa\":\"JDU\",\"roles\":[\"R1\"]}")
                .expiration(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                .signWith(BLUE_KEYS.getPrivate(), Jwts.SIG.RS512).compact();
        when(sourceRequestService.getUserRequests("JDU", null)).thenReturn(List.of());

        var status = mvc.perform(request(HttpMethod.GET, "/api/source-requests/my").header("blue", "Bearer " + token))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(200);
        // Le visa du jeton est le trigramme transmis au service.
        verify(sourceRequestService).getUserRequests("JDU", null);
    }

    @Test
    void publicApi_missingOrRejectedApiKey_returns401Json() throws Exception {
        when(apiKeyAuthenticationProvider.supports(ApiKeyAuthenticationToken.class)).thenReturn(true);
        when(apiKeyAuthenticationProvider.authenticate(any())).thenThrow(new BadCredentialsException("Invalid API key"));

        for (var builder : List.of(request(HttpMethod.GET, "/api/public/v1/anything"),
                request(HttpMethod.GET, "/api/public/v1/anything").header("X-API-Key", "ak_key.wrong"))) {
            var response = mvc.perform(builder).andReturn().getResponse();

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("\"code\": \"unauthorized\"");
        }
    }

    @Test
    void requireInternalUser_unregisteredUser_isForbidden() throws Exception {
        // Utilisateur authentifié par Blue sans compte ZIA : refus (403), pas d'erreur serveur.
        var endpoint = endpoints().filter(e -> e.access() == Access.INTERNAL_USER).findFirst().orElseThrow();

        var status = mvc.perform(withPersona(endpoint.request().get(), Persona.UNREGISTERED))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
        verify(uploadService, never()).download(any(), any());
    }

    /**
     * Statut HTTP de l'appel. Avec des services simulés, le controller peut échouer : une exception (ou un 5xx) n'est
     * acceptée comme « sécurité franchie » que si elle provient de la méthode du controller elle-même ; une erreur
     * levée avant (filtre, {@link AuthorizationLogic}) est rapportée telle quelle et fait échouer le test.
     */
    private String call(Endpoint endpoint, Persona persona) {
        try {
            var result = mvc.perform(withPersona(endpoint.request().get(), persona)).andReturn();
            var status = result.getResponse().getStatus();
            if (status >= 500 && result.getResolvedException() != null) {
                return failure(result.getResolvedException());
            }
            return String.valueOf(status);
        } catch (Exception e) {
            return failure(e);
        }
    }

    private String failure(Throwable error) {
        var controllers = controllerClassNames();
        for (var cause = error; cause != null; cause = cause.getCause()) {
            if (Arrays.stream(cause.getStackTrace()).anyMatch(frame -> controllers.contains(frame.getClassName()))) {
                return "controller-error";
            }
        }
        return "error " + error;
    }

    private Set<String> controllerClassNames() {
        return handlerMapping.getHandlerMethods().values().stream()
                .map(method -> ClassUtils.getUserClass(method.getBeanType()).getName())
                .collect(Collectors.toSet());
    }

    private static AbstractMockHttpServletRequestBuilder<?> withPersona(AbstractMockHttpServletRequestBuilder<?> builder, Persona persona) {
        if (persona == Persona.ANONYMOUS) {
            return builder;
        }
        if (persona.blueToken) {
            return builder.header("blue", "Bearer " + blueToken(persona.username));
        }
        return builder.with(authentication(new TestingAuthenticationToken(persona.username, null, "ROLE_USER")));
    }

    private static String blueToken(String username) {
        return Jwts.builder()
                .subject("{\"userUID\":\"%s\",\"firstname\":\"Prénom\",\"lastname\":\"Nom\",\"visa\":\"VIS\",\"roles\":[]}".formatted(username))
                .expiration(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                .signWith(BLUE_KEYS.getPrivate(), Jwts.SIG.RS512)
                .compact();
    }

    static Stream<Endpoint> endpoints() {
        var list = new ArrayList<Endpoint>();
        // Alertes
        list.add(get("/api/alerts/active", Access.USER));
        list.add(get("/api/alerts/all", Access.ADMIN));
        list.add(json(HttpMethod.POST, "/api/alerts", Access.ADMIN, "{\"level\":\"INFO\",\"textFr\":\"Maintenance\"}"));
        list.add(json(HttpMethod.PUT, "/api/alerts/{id}", Access.ADMIN, "{\"expiresAt\":\"2030-01-01T00:00:00\"}", 1));
        list.add(call(HttpMethod.DELETE, "/api/alerts/{id}", Access.ADMIN, 1));
        // Clés d'API
        list.add(json(HttpMethod.POST, "/api/api-keys", Access.ADMIN,
                "{\"clientId\":\"5f1f8c3e-0000-4000-8000-000000000001\",\"label\":\"test\",\"expiresAt\":\"2030-01-01\"}"));
        // FAQ
        list.add(new Endpoint(key(HttpMethod.GET, "/api/faq-items"), Access.AUTHENTICATED,
                () -> request(HttpMethod.GET, "/api/faq-items").param("question", "rente")));
        list.add(json(HttpMethod.POST, "/api/faq-items", Access.EXPERT,
                "{\"text\":\"Question\",\"answer\":\"Réponse\",\"url\":\"https://faq\",\"language\":\"fr\"}"));
        // Workspaces
        list.add(get("/api/workspaces", Access.USER));
        list.add(get("/api/workspaces/{name}", Access.USER, "ws"));
        list.add(json(HttpMethod.POST, "/api/workspaces", Access.ADMIN, "{\"name\":\"ws\"}"));
        list.add(json(HttpMethod.PUT, "/api/workspaces/{name}", Access.ADMIN, "{\"description\":\"d\"}", "ws"));
        list.add(call(HttpMethod.DELETE, "/api/workspaces/{name}", Access.ADMIN, "ws"));
        // Vérification d'identité
        list.add(json(HttpMethod.POST, "/api/identity-check/start", Access.ADMIN, "{\"dateOfBirth\":\"1980-01-01\"}"));
        list.add(get("/api/identity-check/status", Access.ADMIN));
        // Catalogue
        list.add(upload("/api/admin/catalog/import", Access.ADMIN,
                new MockMultipartFile("file", "catalog.yaml", "application/yaml", "sources: []".getBytes(StandardCharsets.UTF_8))));
        list.add(get("/api/admin/catalog/export", Access.ADMIN));
        // KPI des feedbacks (classe @RequireAdmin)
        list.add(get("/api/feedback/stats", Access.ADMIN));
        list.add(get("/api/feedback/messages", Access.ADMIN));
        list.add(get("/api/feedback/sources", Access.ADMIN));
        list.add(json(HttpMethod.PATCH, "/api/feedback/messages/{id}/status", Access.ADMIN, "{\"status\":\"TREATED\"}", 1));
        list.add(json(HttpMethod.PATCH, "/api/feedback/sources/{id}/status", Access.ADMIN, "{\"status\":\"TREATED\"}", 1));
        list.add(get("/api/feedback/report-config", Access.ADMIN));
        list.add(json(HttpMethod.PUT, "/api/feedback/report-config", Access.ADMIN,
                "{\"enabled\":true,\"cronExpression\":\"0 0 8 * * MON\",\"zoneId\":\"Europe/Zurich\",\"recipients\":[\"a@zas.ch\"],\"lookbackDays\":7}"));
        // Conversations (classe @RequireUser)
        list.add(get("/api/conversations/workspaces", Access.USER));
        list.add(get("/api/conversations/titles", Access.USER));
        list.add(json(HttpMethod.PUT, "/api/conversations/titles/{conversationId}", Access.USER, "{\"new_title\":\"Titre\"}", "c1"));
        list.add(get("/api/conversations/{conversationId}", Access.USER, "c1"));
        list.add(json(HttpMethod.POST, "/api/conversations/init", Access.USER, "[]"));
        list.add(json(HttpMethod.PUT, "/api/conversations/{conversationId}", Access.USER, "[]", "c1"));
        list.add(call(HttpMethod.DELETE, "/api/conversations/{conversationId}", Access.USER, "c1"));
        list.add(new Endpoint(key(HttpMethod.POST, "/api/conversations"), Access.USER,
                () -> multipart("/api/conversations").param("query", "Bonjour")));
        list.add(call(HttpMethod.POST, "/api/conversations/{conversationId}/generations/{messageId}/stop", Access.USER, "c1", "m1"));
        list.add(json(HttpMethod.POST, "/api/conversations/{conversationId}/approvals/{approvalId}", Access.USER,
                "{\"decision\":\"APPROVE\"}", "c1", "a1"));
        list.add(get("/api/conversations/{conversationId}/attachments/{attachmentId}", Access.USER, "c1", 1));
        list.add(upload("/api/conversations/attachments", Access.USER, pdf("files")));
        list.add(get("/api/conversations/{conversationId}/attachments", Access.USER, "c1"));
        list.add(call(HttpMethod.DELETE, "/api/conversations/attachments/{attachmentId}", Access.USER, 1));
        // Feedbacks des réponses et des sources
        list.add(new Endpoint(key(HttpMethod.POST, "/api/conversations/feedbacks", "type=answer"), Access.AUTHENTICATED,
                () -> jsonRequest(HttpMethod.POST, "/api/conversations/feedbacks", "{\"conversationId\":\"c1\",\"messageId\":\"m1\",\"isPositive\":true}")
                        .param("type", "answer")));
        list.add(new Endpoint(key(HttpMethod.GET, "/api/conversations/feedbacks", "conversationId", "messageId", "type=source"),
                Access.EXPERT, () -> request(HttpMethod.GET, "/api/conversations/feedbacks")
                .param("type", "source").param("conversationId", "c1").param("messageId", "m1")));
        list.add(new Endpoint(key(HttpMethod.POST, "/api/conversations/feedbacks", "type=source"), Access.EXPERT,
                () -> jsonRequest(HttpMethod.POST, "/api/conversations/feedbacks",
                        "{\"conversationId\":\"c1\",\"messageId\":\"m1\",\"documentId\":\"d1\",\"isPositive\":false}").param("type", "source")));
        // Vision
        list.add(upload("/api/visualize/structure", Access.ADMIN, pdf("file")));
        list.add(upload("/api/visualize/classify", Access.ADMIN, pdf("file")));
        list.add(new Endpoint(key(HttpMethod.POST, "/api/visualize/translate"), Access.TRANSLATOR,
                () -> multipart("/api/visualize/translate").file(pdf("file")).param("language", "de")));
        // Pas d'annotation @Require* : seule l'authentification est exigée (contrairement aux autres endpoints de vision).
        list.add(upload("/api/visualize/sumex", Access.ADMIN, pdf("file")));
        // Utilisateurs
        list.add(get("/api/users", Access.ADMIN));
        list.add(call(HttpMethod.PUT, "/api/users/{username}/validate", Access.ADMIN, "bob"));
        list.add(call(HttpMethod.PUT, "/api/users/{username}/reactivate", Access.ADMIN, "bob"));
        list.add(call(HttpMethod.PUT, "/api/users/{username}/deactivate", Access.ADMIN, "bob"));
        list.add(json(HttpMethod.PUT, "/api/users/{username}/roles", Access.ADMIN, "{\"roles\":[\"USER\"]}", "bob"));
        list.add(call(HttpMethod.PUT, "/api/users/{username}/internalize", Access.ADMIN, "bob"));
        list.add(call(HttpMethod.PUT, "/api/users/{username}/externalize", Access.ADMIN, "bob"));
        list.add(get("/api/users/authenticated", Access.AUTHENTICATED));
        list.add(json(HttpMethod.POST, "/api/users", Access.AUTHENTICATED,
                "{\"firstName\":\"Jean\",\"lastName\":\"Dupont\",\"organizations\":[\"ZAS\"]}"));
        // Factures Sumex
        list.add(json(HttpMethod.POST, "/api/sumex-invoices/convert", Access.ADMIN, "{}"));
        // Consultation des sources citées (classe @RequireUser)
        list.add(get("/api/source-documents/{id}", Access.USER, "d1"));
        list.add(get("/api/source-documents/{id}/file", Access.USER, "d1"));
        // Paramètres
        list.add(new Endpoint(key(HttpMethod.GET, "/api/settings"), Access.AUTHENTICATED,
                () -> request(HttpMethod.GET, "/api/settings").param("type", "SOURCE")));
        list.add(new Endpoint(key(HttpMethod.GET, "/api/settings/tags"), Access.AUTHENTICATED,
                () -> request(HttpMethod.GET, "/api/settings/tags").param("sources", "a,b")));
        // Sources
        list.add(get("/api/sources", Access.USER));
        list.add(get("/api/sources/{name}", Access.USER, "src"));
        list.add(json(HttpMethod.POST, "/api/sources", Access.ADMIN, "{\"name\":\"src\"}"));
        list.add(json(HttpMethod.PUT, "/api/sources/{name}", Access.ADMIN, "{\"description\":\"d\"}", "src"));
        list.add(call(HttpMethod.DELETE, "/api/sources/{name}", Access.ADMIN, "src"));
        // Demandes de sources
        list.add(json(HttpMethod.POST, "/api/source-requests", Access.ZAS_USER, "{\"sourceName\":\"src\",\"description\":\"d\"}"));
        list.add(get("/api/source-requests/my", Access.ZAS_USER));
        list.add(get("/api/source-requests", Access.ADMIN));
        list.add(get("/api/source-requests/by-status/{status}", Access.ADMIN, "WAITING"));
        list.add(get("/api/source-requests/{id}", Access.ZAS_USER, 1));
        list.add(call(HttpMethod.DELETE, "/api/source-requests/{id}", Access.ZAS_USER, 1));
        list.add(get("/api/source-requests/admin/{id}", Access.ADMIN, 1));
        list.add(json(HttpMethod.PATCH, "/api/source-requests/{id}/status", Access.ADMIN, "{\"status\":\"PROCESSING\"}", 1));
        // Synthèses (classe @RequireAdmin)
        list.add(call(HttpMethod.POST, "/api/summaries/{navs}", Access.ADMIN, "7560000000002"));
        list.add(get("/api/summaries", Access.ADMIN));
        list.add(get("/api/summaries/{id}", Access.ADMIN, 1));
        list.add(call(HttpMethod.PUT, "/api/summaries/{id}", Access.ADMIN, 1));
        list.add(call(HttpMethod.POST, "/api/summaries/{id}/open-references", Access.ADMIN, 1));
        // Documents
        list.add(new Endpoint(key(HttpMethod.GET, "/api/documents"), Access.INTERNAL_USER,
                () -> request(HttpMethod.GET, "/api/documents").param("filename", "guide.pdf")));
        list.add(get("/api/documents/user-docs", Access.USER));
        list.add(new Endpoint(key(HttpMethod.DELETE, "/api/documents/user-docs"), Access.USER,
                () -> request(HttpMethod.DELETE, "/api/documents/user-docs").param("filename", "a.pdf")));
        list.add(new Endpoint(key(HttpMethod.PUT, "/api/documents/user-docs/reactivate"), Access.USER,
                () -> request(HttpMethod.PUT, "/api/documents/user-docs/reactivate").param("filename", "a.pdf")));
        list.add(get("/api/documents/retention-config", Access.ADMIN));
        list.add(json(HttpMethod.PUT, "/api/documents/retention-config", Access.ADMIN,
                "{\"daysBeforeArchival\":30,\"daysBeforeDeletion\":60}"));
        list.add(upload("/api/documents/upload", Access.USER, pdf("multipartFile")));
        list.add(upload("/api/documents/upload-admin", Access.ADMIN, pdf("documents")));
        return list.stream();
    }

    private static MockMultipartFile pdf(String part) {
        return new MockMultipartFile(part, "a.pdf", "application/pdf", PDF);
    }

    private static Endpoint get(String pattern, Access access, Object... uriVars) {
        return call(HttpMethod.GET, pattern, access, uriVars);
    }

    private static Endpoint call(HttpMethod method, String pattern, Access access, Object... uriVars) {
        return new Endpoint(key(method, pattern), access, () -> request(method, pattern, uriVars));
    }

    private static Endpoint json(HttpMethod method, String pattern, Access access, String body, Object... uriVars) {
        return new Endpoint(key(method, pattern), access, () -> jsonRequest(method, pattern, body, uriVars));
    }

    private static Endpoint upload(String pattern, Access access, MockMultipartFile file) {
        return new Endpoint(key(HttpMethod.POST, pattern), access, () -> multipart(pattern).file(file));
    }

    private static MockHttpServletRequestBuilder jsonRequest(HttpMethod method, String pattern, String body, Object... uriVars) {
        return request(method, pattern, uriVars).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String key(HttpMethod method, String pattern, String... params) {
        var sortedParams = new TreeSet<>(Arrays.asList(params));
        return method.name() + " " + pattern + (sortedParams.isEmpty() ? "" : " " + sortedParams);
    }

    private static KeyPair rsaKeyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
