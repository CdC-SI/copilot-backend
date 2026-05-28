package zas.admin.zec.backend.config;

import ch.admin.zas.jweb.webstarter.observability.CorrelationIdFilter;
import ch.admin.zas.jweb.webstarter.observability.UserMdcFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class AsyncFilterConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration(CorrelationIdFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setAsyncSupported(true);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<UserMdcFilter> userMdcFilterRegistration(UserMdcFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setAsyncSupported(true);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> disableDuplicateCorsFilter(CorsFilter corsFilter) {
        var registration = new FilterRegistrationBean<>(corsFilter);
        registration.setEnabled(false);
        return registration;
    }
}
