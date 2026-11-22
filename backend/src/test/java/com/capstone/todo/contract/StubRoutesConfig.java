package com.capstone.todo.contract;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import com.capstone.todo.web.gate.ApiGatewayFilter;
import com.capstone.todo.web.gate.RawBody;
import com.capstone.todo.web.gate.RouteMatch;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * TEST-ONLY pass-through observer for the gateway contract tests (T-12, CP-B). It answers every request that got past
 * the gateway filter (every method on the three API route templates) with 200 and reports, in headers, what the filter
 * handed over: the route template, the raw id segment, and the length and presence of the body.
 *
 * <p>At CP-B this was a {@code @Controller} mapped to the three templates. Once the real {@code TodoController}
 * exists (T-13) two handlers for the same mapping would fail the context with "Ambiguous mapping", so the stub is now a
 * servlet filter ordered right behind the gateway and the failure hooks ({@code Ordered.HIGHEST_PRECEDENCE + 20}), on the
 * REQUEST dispatch only. It never calls the chain, so the real controller and the store are not reached by the classes
 * that import this configuration; what it can show is exactly what the gateway handed over, which is all the CP-B
 * rows needed. Everything that depends on Spring's handler mapping or on the real controller (raw ids reaching a
 * {@code {id}} mapping, an illegal charset name in front of Spring's response writer, Accept) is covered by
 * {@code CrudContractTest} against the real controller.
 *
 * <p>Import it only where the real controller must stay out of the way.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StubRoutesConfig {

    /** Requests that reached the stub, as {@code METHOD route}. */
    public static final class StubRoutes {

        private final List<String> calls = new CopyOnWriteArrayList<>();

        void record(String call) {
            calls.add(call);
        }

        public List<String> calls() {
            return List.copyOf(calls);
        }
    }

    @Bean
    StubRoutes stubRoutes() {
        return new StubRoutes();
    }

    @Bean
    FilterRegistrationBean<Filter> stubRoutesFilter(StubRoutes stub) {
        Filter filter = (request, response, chain) -> {
            HttpServletRequest http = (HttpServletRequest) request;
            HttpServletResponse out = (HttpServletResponse) response;
            RouteMatch match = (RouteMatch) request.getAttribute(ApiGatewayFilter.ROUTE_ATTRIBUTE);
            RawBody body = (RawBody) request.getAttribute(ApiGatewayFilter.RAW_BODY_ATTRIBUTE);
            stub.record(http.getMethod() + " " + match.route());
            byte[] answer = "{\"stub\":true}".getBytes(StandardCharsets.UTF_8);
            out.setStatus(200);
            out.setContentType("application/json");
            out.setHeader("X-Stub-Route", match.route());
            out.setHeader("X-Stub-Id", match.rawIdSegment() == null ? "" : match.rawIdSegment());
            out.setHeader("X-Stub-Body-Length", String.valueOf(body.bytes().length));
            out.setHeader("X-Stub-Body-Present", String.valueOf(body.present()));
            out.setContentLength(answer.length);
            out.getOutputStream().write(answer);
            out.flushBuffer();
        };
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("stubRoutesFilter");
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
