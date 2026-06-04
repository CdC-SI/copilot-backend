package zas.admin.zec.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration de la documentation OpenAPI / Swagger UI.
 *
 * <p>Swagger UI : {@code /swagger-ui.html} — Spec JSON : {@code /v3/api-docs}.</p>
 *
 * <p>Deux schémas d'authentification sont déclarés :</p>
 * <ul>
 *     <li>{@code blueToken} : header {@code blue} portant le JWT (API interne {@code /api/**}).</li>
 *     <li>{@code apiKey} : header {@code X-API-Key} (API publique {@code /api/public/v1/**}).</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:copilot-backend}")
    private String applicationName;

    @Bean
    public OpenAPI copilotOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ZIA Backend API")
                        .version("2.2.0")
                        .description("""
                                API du backend ZIA, \
                                assistant pour les assurances sociales suisses."""))
                .components(new Components()
                        .addSecuritySchemes("blueToken", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("blue")
                                .description("JWT interne transmis via le header 'blue'."))
                        .addSecuritySchemes("apiKey", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("Clé d'API publique au format 'ak_<keyId>.<secret>'.")));
    }
}

