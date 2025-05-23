package zas.admin.zec.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "ai.agent")
public record AIAgentProperties(
        @NotNull String iiDataFolder) {}
