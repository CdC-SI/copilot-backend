package zas.admin.zec.backend.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import zas.admin.zec.backend.tools.SecurityLogging;

import java.io.IOException;

@Slf4j
@Component
public class AuthenticationAuditFilter extends OncePerRequestFilter implements WebMvcConfigurer {

    private final SecurityLogging securityLogging;

    public AuthenticationAuditFilter(SecurityLogging securityLogging) {
        this.securityLogging = securityLogging;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);
        int status = response.getStatus();
        if (status == 401) {
            securityLogging.logAuthenticationFailure("Authentication failed for " + request.getRequestURI(), request.getMethod());
        } else if (status == 403) {
            securityLogging.logAuthenticationFailure("Access denied for " + request.getRequestURI(), request.getMethod());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().contains("/actuator");
    }


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.isAuthenticated()) {
                    securityLogging.logAuthenticationSuccess(authentication.getName(), request.getMethod());
                }
                return true;
            }
        }).addPathPatterns("/api/**").excludePathPatterns("/actuator/**");
    }


    @Bean
    FilterRegistrationBean<AuthenticationAuditFilter> authenticationAuditFilterRegistration(AuthenticationAuditFilter filter) {
        FilterRegistrationBean<AuthenticationAuditFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Integer.MIN_VALUE);
        registration.setName("authenticationAuditFilter");
        return registration;
    }
}
