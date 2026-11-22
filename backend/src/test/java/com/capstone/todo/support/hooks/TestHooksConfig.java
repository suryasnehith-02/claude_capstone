package com.capstone.todo.support.hooks;

import java.util.EnumSet;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Failure hooks for the error-envelope and logging contract tests (T-ERR, T-LOG). Import it explicitly with
 * {@code @Import}; it must never be imported by the route-table test (T-RT), because it adds filters.
 *
 * <p>Both hooks sit after the gateway filter (which uses {@link Ordered#HIGHEST_PRECEDENCE}), so a request
 * reaches them only if it passed the gateway's checks, and they act only when their header is present:
 * <ul>
 *   <li>{@value #THROW_HEADER}: {@code request} throws a RuntimeException on the REQUEST dispatch;
 *       {@code error} throws on the ERROR dispatch.</li>
 *   <li>{@value #SEND_ERROR_HEADER}: the value is a status code passed to {@code sendError}
 *       (REQUEST dispatch only), which produces an ERROR dispatch to the error controller.</li>
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestHooksConfig {

    public static final String THROW_HEADER = "X-Test-Throw";
    public static final String SEND_ERROR_HEADER = "X-Test-Send-Error";

    @Bean
    FilterRegistrationBean<Filter> testThrowingFilter() {
        Filter filter = (request, response, chain) -> {
            String mode = ((HttpServletRequest) request).getHeader(THROW_HEADER);
            String dispatch = request.getDispatcherType() == DispatcherType.ERROR ? "error" : "request";
            if (dispatch.equals(mode)) {
                throw new IllegalStateException("test-induced failure");
            }
            chain.doFilter(request, response);
        };
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("testThrowingFilter");
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    FilterRegistrationBean<Filter> testSendErrorFilter() {
        Filter filter = (request, response, chain) -> {
            String status = ((HttpServletRequest) request).getHeader(SEND_ERROR_HEADER);
            if (status != null) {
                ((HttpServletResponse) response).sendError(Integer.parseInt(status));
                return;
            }
            chain.doFilter(request, response);
        };
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("testSendErrorFilter");
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 11);
        return registration;
    }
}
