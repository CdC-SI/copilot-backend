package zas.admin.zec.backend.config.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableMethodSecurity
public class PublicApiSecurityConfig {

    private static final String ACTUATOR_PATH = "/actuator/**";
    private static final String PUBLIC_API_PATH = "/api/public/v1/**";


    @Bean
    @Order(0)
    SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .securityMatcher(ACTUATOR_PATH)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    @Bean
    @Order(1)
    SecurityFilterChain publicApiSecurityFilterChain(HttpSecurity http,
                                                     AuthenticationManager apiKeyAuthManager,
                                                     ApiKeyAuthenticationConverter converter) throws Exception {

        http.securityMatcher(new AntPathRequestMatcher(PUBLIC_API_PATH));
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(c -> c.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.STATELESS));

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated());

        AuthenticationFilter apiKeyFilter = getAuthenticationFilter(apiKeyAuthManager, converter);

        http.addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    AuthenticationManager apiKeyAuthManager(ApiKeyAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    Argon2PasswordEncoder apiKeyPasswordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    private static AuthenticationFilter getAuthenticationFilter(AuthenticationManager apiKeyAuthManager, ApiKeyAuthenticationConverter converter) {
        AuthenticationFilter apiKeyFilter = new AuthenticationFilter(apiKeyAuthManager, converter);
        apiKeyFilter.setSuccessHandler((request, response, authentication) -> {
            // Do nothing on success, just proceed to the controller
        });
        apiKeyFilter.setFailureHandler((request, response, exception) -> {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": {\"code\": \"unauthorized\", \"message\": \"Invalid or missing API key\"}}");
        });
        return apiKeyFilter;
    }

    @Bean
    static AnnotationTemplateExpressionDefaults templateExpressionDefaults() {
        return new AnnotationTemplateExpressionDefaults();
    }
}
