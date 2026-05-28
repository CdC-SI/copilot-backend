package zas.admin.zec.backend.config;

import ch.admin.zas.jweb.webstarter.observability.CorrelationIdFilter;
import ch.admin.zas.jweb.webstarter.observability.UserMdcFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsyncFilterConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration(CorrelationIdFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setAsyncSupported(true);
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(
                DispatcherType.REQUEST,
                DispatcherType.ASYNC,
                DispatcherType.ERROR
        );
        return registration;
    }

    @Bean
    public FilterRegistrationBean<UserMdcFilter> userMdcFilterRegistration(UserMdcFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setAsyncSupported(true);
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(
                DispatcherType.REQUEST,
                DispatcherType.ASYNC,
                DispatcherType.ERROR
        );
        return registration;
    }
}
