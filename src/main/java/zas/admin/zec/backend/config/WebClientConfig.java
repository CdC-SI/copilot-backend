package zas.admin.zec.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;
import zas.admin.zec.backend.config.properties.*;

@Configuration
@EnableAsync
@EnableJpaAuditing
@EnableConfigurationProperties({ApplicationProperties.class, PyBackendProperties.class, JwtProperties.class,
        FAQSearchProperties.class, RerankingProperties.class, DeepLProperties.class})
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

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .proxy(proxy -> proxy
                        .type(ProxyProvider.Proxy.HTTP)
                        .host(System.getProperty("https.proxyHost"))
                        .port(Integer.parseInt(System.getProperty("https.proxyPort")))
                        .nonProxyHosts(System.getProperty("https.nonProxyHosts"))
                );

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
