package zas.admin.zec.backend.config;

import ch.admin.zas.jweb.webstarter.observability.CorrelationIdFilter;
import ch.admin.zas.jweb.webstarter.observability.UserMdcFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
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

    @Bean
    ApplicationRunner dumpFilterRegistrations(ApplicationContext context) {
        return args -> context.getBeansOfType(AbstractFilterRegistrationBean.class)
                .forEach((name, registration) -> {
                    System.out.printf(
                            "FilterRegistrationBean bean=%s, filterName=%s, filter=%s, async=%s, dispatcherTypes=%s, order=%s, urls=%s%n",
                            name,
                            registration.getFilterName(),
                            registration.getFilter() != null ? registration.getFilter().getClass().getName() : null,
                            registration.isAsyncSupported(),
                            registration.determineDispatcherTypes(),
                            registration.getOrder(),
                            registration.getUrlPatterns()
                    );
                });
    }
}
