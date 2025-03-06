package zas.admin.zec.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "application")
public record ApplicationProperties(
        @NotNull String name,
        @NotNull String version) {}
