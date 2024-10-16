package zas.admin.zec.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(PyBackendProperties.class)
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
