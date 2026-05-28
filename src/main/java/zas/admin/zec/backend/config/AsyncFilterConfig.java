package zas.admin.zec.backend.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.DelegatingFilterProxy;

@Configuration
public class AsyncFilterConfig {

    @Bean
    public FilterRegistrationBean<DelegatingFilterProxy> asyncSecurityFilter() {
        var bean = new FilterRegistrationBean<>(new DelegatingFilterProxy("springSecurityFilterChain"));
        bean.setAsyncSupported(true);
        bean.setOrder(Integer.MIN_VALUE + 1);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
