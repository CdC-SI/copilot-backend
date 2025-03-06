package zas.admin.zec.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import zas.admin.zec.backend.config.properties.ApplicationProperties;
import zas.admin.zec.backend.config.properties.FAQSearchProperties;
import zas.admin.zec.backend.config.properties.JwtProperties;
import zas.admin.zec.backend.config.properties.PyBackendProperties;

@Configuration
@EnableConfigurationProperties({ApplicationProperties.class, PyBackendProperties.class, JwtProperties.class, FAQSearchProperties.class})
public class WebClientConfig {
    private final PyBackendProperties pyBackendProperties;

    public WebClientConfig(PyBackendProperties pyBackendProperties) {
        this.pyBackendProperties = pyBackendProperties;
    }

    @Bean("pyBackendWebClient")
    public WebClient webClient(WebClient.Builder builder) {
        return builder
                .baseUrl(pyBackendProperties.getBaseUrl())
                .build();
    }
}
