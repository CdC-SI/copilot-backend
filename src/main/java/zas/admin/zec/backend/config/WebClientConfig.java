package zas.admin.zec.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableConfigurationProperties({PyBackendProperties.class, JwtProperties.class})
public class WebClientConfig {
    private static final Logger logger = LoggerFactory.getLogger(WebClientConfig.class);
    private final PyBackendProperties pyBackendProperties;

    public WebClientConfig(PyBackendProperties pyBackendProperties) {
        this.pyBackendProperties = pyBackendProperties;
    }

    @Bean("pyBackendWebClient")
    public WebClient webClient(WebClient.Builder builder) {
        String baseUrl = pyBackendProperties.getBaseUrl();
        //String baseUrl = "http://host.docker.internal:";
        logger.info("Creating WebClient with base URL: {}", baseUrl);
        return builder
                .baseUrl(baseUrl)
                .build();
    }
}
