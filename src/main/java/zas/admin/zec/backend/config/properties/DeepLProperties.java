package zas.admin.zec.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "deepl")
public record DeepLProperties(String authKey, String defaultTargetLang) {}
