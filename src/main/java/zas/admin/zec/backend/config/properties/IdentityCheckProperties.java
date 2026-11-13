package zas.admin.zec.backend.config.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "identity-check")
public record IdentityCheckProperties(@NotNull String baseUrl, @NotNull String callBackUrl, Keystore keystore) {

    public IdentityCheckProperties {
        keystore = keystore != null ? keystore : new Keystore(null, null, null);
    }

    /**
     * Client keystore (mutual TLS) used to call the identity-check API. Expected outside the classpath
     * (e.g. {@code file:/mnt/secrets/identity-check.p12}); only applied when {@code location} is set.
     */
    public record Keystore(String location, String password, @DefaultValue("PKCS12") String type) {

        public Keystore {
            type = StringUtils.hasText(type) ? type : "PKCS12";
        }

        public boolean isConfigured() {
            return StringUtils.hasText(location);
        }
    }
}
