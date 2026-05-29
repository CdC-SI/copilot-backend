package zas.admin.zec.backend.config;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class AsyncProbeConfig {

    private static final Log logger = LogFactory.getLog(AsyncProbeConfig.class);

    @Bean
    FilterRegistrationBean<Filter> asyncProbeBeforeAll() {
        return probe("probe-before-all", Ordered.HIGHEST_PRECEDENCE);
    }

    @Bean
    FilterRegistrationBean<Filter> asyncProbeBeforeRequestContext() {
        return probe("probe-before-request-context", -106);
    }

    @Bean
    FilterRegistrationBean<Filter> asyncProbeBeforeCorrelation() {
        return probe("probe-before-correlation", -105);
    }

    @Bean
    FilterRegistrationBean<Filter> asyncProbeBeforeSecurity() {
        return probe("probe-before-security", -101);
    }

    @Bean
    FilterRegistrationBean<Filter> asyncProbeBeforeUserMdc() {
        return probe("probe-before-user-mdc", 1000);
    }

    @Bean
    FilterRegistrationBean<Filter> asyncProbeAfterUserMdc() {
        return probe("probe-after-user-mdc", Ordered.LOWEST_PRECEDENCE);
    }

    private FilterRegistrationBean<Filter> probe(String name, int order) {
        var registration = new FilterRegistrationBean<Filter>();

        registration.setName(name);
        registration.setOrder(order);
        registration.setAsyncSupported(true);
        registration.addUrlPatterns("/*");

        registration.setFilter((request, response, chain) -> {
            var httpRequest = (HttpServletRequest) request;

            logger.info(String.format("[%s] before chain asyncSupported=%s, attr=%s, uri=%s%n",
                    name,
                    httpRequest.isAsyncSupported(),
                    httpRequest.getAttribute("org.apache.catalina.ASYNC_SUPPORTED"),
                    httpRequest.getRequestURI())
            );

            chain.doFilter(request, response);
        });

        return registration;
    }
}