package zas.admin.zec.backend.config.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity-check")
public record IdentityCheckProperties(@NotNull String baseUrl, @NotNull String callBackUrl) { }
