package zas.admin.zec.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "proxy")
public record ProxyProperties(boolean enabled, String host, Integer port, String nonProxyHosts) {}
