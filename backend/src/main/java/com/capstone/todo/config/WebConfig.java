package com.capstone.todo.config;

import java.util.EnumSet;
import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.capstone.todo.web.GateAttributeArgumentResolver;
import com.capstone.todo.web.gate.ApiGatewayFilter;
import com.capstone.todo.web.gate.BodyGate;
import com.capstone.todo.web.gate.RouteTable;
import com.capstone.todo.web.response.ApiResponses;

import jakarta.servlet.DispatcherType;

/**
 * Registers the gateway filter and switches off content negotiation by Accept (architecture C2, D-1, D-15, D-19).
 *
 * <p>The filter is registered here and is deliberately not a Spring bean of its own: a {@code Filter} bean would also
 * be registered by Boot, a second time, with the default order and dispatcher types. It is first in Boot's list
 * ({@link Ordered#HIGHEST_PRECEDENCE}), runs for the REQUEST and the ERROR dispatch and covers every URL.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    public static final String GATEWAY_FILTER_NAME = "apiGatewayFilter";

    @Bean
    FilterRegistrationBean<ApiGatewayFilter> apiGatewayFilterRegistration(ApiResponses responses) {
        FilterRegistrationBean<ApiGatewayFilter> registration =
                new FilterRegistrationBean<>(new ApiGatewayFilter(new RouteTable(), new BodyGate(), responses));
        registration.setName(GATEWAY_FILTER_NAME);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR));
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * An Accept header never changes the answer and never gives a 406, which is not in the error catalog. Spring 7
     * refuses to start with no negotiation strategy at all, so JSON is the fixed default; every response in this
     * service is JSON anyway (the content type is preset by the writer).
     */
    /** The controller gets the route match and the raw body from the gateway's request attributes (D-13). */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new GateAttributeArgumentResolver());
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.ignoreAcceptHeader(true).defaultContentType(MediaType.APPLICATION_JSON);
    }
}
