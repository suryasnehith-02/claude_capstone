package com.capstone.todo.config;

import org.apache.catalina.Host;
import org.apache.catalina.Valve;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.tomcat.TomcatContextCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat settings the contract depends on (architecture D-12, D-19, section 7.5).
 *
 * <ul>
 *   <li>{@code allowTrace=true}: with Tomcat's default (false) the connector answers TRACE itself, before any filter,
 *       with its own {@code Allow} list and no envelope. With true, TRACE reaches the gateway filter, which always
 *       rejects it at P1 (and never lets it reach the dispatcher servlet, so nothing is echoed).</li>
 *   <li>{@code encodedSolidusHandling=passthrough} and {@code encodedReverseSolidusHandling=passthrough}: an encoded
 *       slash or backslash in an id reaches the application and becomes INVALID_ID instead of a container 400.</li>
 *   <li>The {@code ErrorReportValve} writes no report and no server info: Tomcat's own HTML error page would leak the
 *       version and is not the envelope.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class TomcatConfig {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> apiTomcatCustomizer() {
        return factory -> {
            TomcatConnectorCustomizer connector = c -> {
                c.setAllowTrace(true);
                c.setEncodedSolidusHandling("passthrough");
                c.setEncodedReverseSolidusHandling("passthrough");
            };
            factory.addConnectorCustomizers(connector);
            factory.addContextCustomizers(quietErrorReportValve());
        };
    }

    /**
     * Puts a configured {@code ErrorReportValve} into the host's pipeline. Tomcat adds its default one when the host
     * starts only if none of that class is there, so this one replaces it instead of sitting next to it.
     */
    static TomcatContextCustomizer quietErrorReportValve() {
        return context -> {
            if (!(context.getParent() instanceof Host host)) {
                throw new IllegalStateException("The Tomcat context has no host, so the error report valve cannot be set");
            }
            // In a first run the host held two valves, both with the quiet settings, so this customizer is applied
            // more than once: an existing valve is configured instead of adding another.
            for (Valve existing : host.getPipeline().getValves()) {
                if (existing instanceof ErrorReportValve report) {
                    quiet(report);
                    return;
                }
            }
            ErrorReportValve valve = new ErrorReportValve();
            quiet(valve);
            host.getPipeline().addValve(valve);
        };
    }

    private static void quiet(ErrorReportValve valve) {
        valve.setShowReport(false);
        valve.setShowServerInfo(false);
    }
}
