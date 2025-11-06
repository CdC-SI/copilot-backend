package zas.admin.zec.backend.config.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.retrieving.query-compresser")
public record QueryCompresserProperties(@NotNull Boolean enabled) {}
