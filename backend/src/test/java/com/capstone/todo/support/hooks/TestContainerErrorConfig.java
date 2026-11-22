package com.capstone.todo.support.hooks;

import java.io.IOException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.tomcat.TomcatContextCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;

import jakarta.servlet.ServletException;

/**
 * A container-level failure hook for the DR-16 case (T-ERR, TRACE with {@code sendError}). The filter hooks of
 * {@link TestHooksConfig} sit behind the gateway filter, which rejects TRACE before any later filter runs, so they can
 * never turn a TRACE request into an ERROR dispatch. This hook is a Tomcat valve in the context pipeline, in front of
 * the whole filter chain: when the request carries {@value #HEADER} it calls {@code sendError} with that status and
 * does not invoke the rest of the chain, so the container's error dispatch for that request (whatever its method)
 * starts without the gateway's REQUEST dispatch having run. Without the header the valve does nothing.
 *
 * <p>Import it explicitly and only where needed.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestContainerErrorConfig {

    public static final String HEADER = "X-Test-Container-Error";

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> containerErrorCustomizer() {
        return factory -> factory.addContextCustomizers(containerErrorValve());
    }

    private static TomcatContextCustomizer containerErrorValve() {
        return context -> context.getPipeline().addValve(new ValveBase() {
            @Override
            public void invoke(Request request, Response response) throws IOException, ServletException {
                String status = request.getHeader(HEADER);
                if (status == null) {
                    getNext().invoke(request, response);
                } else {
                    response.sendError(Integer.parseInt(status));
                }
            }
        });
    }
}
