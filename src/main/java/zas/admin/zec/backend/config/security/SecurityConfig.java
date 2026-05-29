package zas.admin.zec.backend.config.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import zas.admin.zec.backend.config.api.ApiKeyAuthenticationConverter;
import zas.admin.zec.backend.config.api.ApiKeyAuthenticationProvider;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.stream.StreamSupport;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ACTUATOR_PATH = "/actuator/**";
    private static final String PUBLIC_API_PATH = "/api/public/v1/**";
    private static final String API_PATH = "/api/**";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${zas.security.blue-token.public-key}")
    private String publicKeyPem;


    @Bean
    @Order(0)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(ACTUATOR_PATH)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain publicApiSecurityFilterChain(HttpSecurity http,
                                                            AuthenticationManager apiKeyAuthManager,
                                                            ApiKeyAuthenticationConverter converter) throws Exception {
        http.securityMatcher(PUBLIC_API_PATH)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated());

        AuthenticationFilter apiKeyFilter = new AuthenticationFilter(apiKeyAuthManager, converter);
        apiKeyFilter.setSuccessHandler((request, response, authentication) -> { });
        apiKeyFilter.setFailureHandler((request, response, exception) -> {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": {\"code\": \"unauthorized\", \"message\": \"Invalid or missing API key\"}}");
        });
        http.addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(API_PATH)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(API_PATH).authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder())
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
        resolver.setBearerTokenHeaderName("blue");
        return resolver;
    }

    @Bean
    AuthenticationManager apiKeyAuthManager(ApiKeyAuthenticationProvider provider) {
        ProviderManager providerManager = new ProviderManager(provider);
        providerManager.setEraseCredentialsAfterAuthentication(false);
        return providerManager;
    }

    @Bean
    Argon2PasswordEncoder apiKeyPasswordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    static AnnotationTemplateExpressionDefaults templateExpressionDefaults() {
        return new AnnotationTemplateExpressionDefaults();
    }

    private Converter<Jwt, ZasUser> jwtAuthenticationConverter() {
        return jwt -> {
            try {
                JsonNode subJson = objectMapper.readTree(jwt.getSubject());
                String userUID = subJson.path("userUID").asText(null);
                String firstname = subJson.path("firstname").asText(null);
                String lastname = subJson.path("lastname").asText(null);
                String visa = subJson.path("visa").asText(null);
                var authorities = StreamSupport.stream(subJson.path("roles").spliterator(), false)
                        .map(node -> new SimpleGrantedAuthority(node.asText()))
                        .toList();
                return new ZasUser(jwt, authorities, userUID, firstname, lastname, visa);
            } catch (Exception e) {
                throw new IllegalStateException("Impossible de parser le claim 'sub' du JWT", e);
            }
        };
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyPem.replaceAll("\\s+", ""));
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(spec);
            return NimbusJwtDecoder.withPublicKey(publicKey).signatureAlgorithm(SignatureAlgorithm.RS512).build();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de charger la clé publique RSA", e);
        }
    }
}
