package com.capstone.todo.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.capstone.todo.support.EvidenceLog;
import com.capstone.todo.support.RawHttp;
import com.capstone.todo.support.TestDataDir;
import com.capstone.todo.support.hooks.TestHooksConfig;
import com.capstone.todo.web.response.ApiResponses;
import com.capstone.todo.web.response.ErrorCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;

/**
 * T-12 failure paths on real Tomcat (T-ERR and T-LOG, filter side): a throwing filter behind the gateway gives one 500
 * envelope and one ERROR entry; a {@code sendError} hook reaches the gateway again as an ERROR dispatch with the same
 * request id (V-10, V-11); a failure thrown during the ERROR dispatch gives one 500 envelope and one entry; a client that
 * disappears mid-body is not a failure. The routes are answered by the test-only pass-through observer
 * ({@link StubRoutesConfig}); the error dispatch goes to the real {@code FallbackErrorController}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import({StubRoutesConfig.class, TestHooksConfig.class})
class GatewayFailureContractTest {

    private static final Path DATA_FILE = TestDataDir.newDataFile(GatewayFailureContractTest.class);
    private static final EvidenceLog EVIDENCE = new EvidenceLog();
    private static final List<String> SEEN = new CopyOnWriteArrayList<>();

    @DynamicPropertySource
    static void dataFile(DynamicPropertyRegistry registry) {
        registry.add("todo.data-file", DATA_FILE::toString);
    }

    /** Records, for every request that reaches it, the dispatcher type and the MDC request id at that moment. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Observer {
        @Bean
        FilterRegistrationBean<Filter> observingFilter() {
            Filter filter = (request, response, chain) -> {
                SEEN.add(request.getDispatcherType() + ":" + MDC.get("requestId"));
                chain.doFilter(request, response);
            };
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
            registration.setName("observingFilter");
            registration.addUrlPatterns("/*");
            registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR));
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
            return registration;
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ApiResponses responses;

    private final ListAppender<ILoggingEvent> log = new ListAppender<>();
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final Logger gatewayLogger = (Logger) LoggerFactory.getLogger("com.capstone.todo.web.gate.ApiGatewayFilter");
    private Level gatewayLevelBefore;

    @BeforeEach
    void captureLogs() {
        SEEN.clear();
        gatewayLevelBefore = gatewayLogger.getLevel();
        gatewayLogger.setLevel(Level.DEBUG);
        log.start();
        root.addAppender(log);
    }

    @AfterEach
    void releaseLogs() {
        root.detachAppender(log);
        gatewayLogger.setLevel(gatewayLevelBefore);
        log.stop();
    }

    @AfterAll
    static void writeEvidence() {
        EVIDENCE.writeTo("cp-b-failure-evidence.txt");
    }

    private List<ILoggingEvent> errors() {
        return log.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    }

    private void assertEnvelope500(RawHttp.Response response) {
        assertThat(response.status()).isEqualTo(500);
        assertThat(response.body()).isEqualTo(responses.fixedError(ErrorCode.INTERNAL_ERROR));
        assertThat(response.headers("X-Content-Type-Options")).containsExactly("nosniff");
        assertThat(response.headers("Content-Type")).containsExactly("application/json");
    }

    @Test
    void aFailureBehindTheGatewayIsOne500EnvelopeAndOneErrorEntryWithoutTheQuery() {
        RawHttp.Response response = RawHttp.request("GET", "/todos?q=SECRETQUERY")
                .header(TestHooksConfig.THROW_HEADER, "request").send(port);

        EVIDENCE.row("T-LOG (2)/T-ERR", "GET /todos?q=SECRETQUERY with a throwing filter behind the gateway",
                "500 INTERNAL_ERROR envelope, nosniff, exactly one ERROR entry (request id, method, route, class, no query)",
                response.status() + " " + response.bodyText() + " errors=" + errors().size());
        assertEnvelope500(response);
        assertThat(errors()).hasSize(1);
        ILoggingEvent entry = errors().get(0);
        assertThat(entry.getLoggerName()).isEqualTo("com.capstone.todo.web.gate.ApiGatewayFilter");
        assertThat(entry.getFormattedMessage()).contains("requestId=", "method=GET", "route=/todos")
                .doesNotContain("SECRETQUERY");
        assertThat(entry.getMDCPropertyMap()).containsKey("requestId");
        assertThat(entry.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
        assertThat(entry.getThrowableProxy().getMessage()).isEqualTo("test-induced failure");
    }

    @Test
    void aSendErrorBehindTheGatewayReachesTheGatewayAgainAsAnErrorDispatchWithTheSameRequestId_v10_v11() {
        RawHttp.Response response = RawHttp.request("GET", "/todos")
                .header(TestHooksConfig.SEND_ERROR_HEADER, "404").send(port);

        EVIDENCE.row("V-10/V-11", "GET /todos with a sendError(404) hook behind the gateway",
                "REQUEST then ERROR dispatch both see the gateway's MDC request id (same id); nosniff on the response",
                "seen=" + SEEN + " status=" + response.status() + " nosniff=" + response.header("X-Content-Type-Options")
                        + " type=" + response.header("Content-Type") + " body=" + response.bodyText());
        assertThat(SEEN).hasSize(2);
        assertThat(SEEN.get(0)).startsWith("REQUEST:").doesNotEndWith(":null");
        assertThat(SEEN.get(1)).startsWith("ERROR:").doesNotEndWith(":null");
        assertThat(SEEN.get(1).substring("ERROR:".length())).isEqualTo(SEEN.get(0).substring("REQUEST:".length()));
        assertThat(response.headers("X-Content-Type-Options")).containsExactly("nosniff");
        assertThat(errors()).isEmpty();
        // Since T-13 the error controller is ours: the answer is the envelope, not Boot's timestamp/path JSON.
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.body()).isEqualTo(responses.fixedError(ErrorCode.NOT_FOUND));
    }

    @Test
    void aFailureDuringTheErrorDispatchIsOne500EnvelopeAndOneErrorEntry() {
        RawHttp.Response response = RawHttp.request("GET", "/todos")
                .header(TestHooksConfig.SEND_ERROR_HEADER, "404").header(TestHooksConfig.THROW_HEADER, "error").send(port);

        EVIDENCE.row("T-ERR/T-LOG (3)", "GET /todos, sendError(404) hook, then a throw on the ERROR dispatch",
                "500 INTERNAL_ERROR envelope, nosniff, exactly one ERROR entry",
                response.status() + " " + response.bodyText() + " errors=" + errors().size() + " seen=" + SEEN);
        assertEnvelope500(response);
        assertThat(errors()).hasSize(1);
        assertThat(errors().get(0).getFormattedMessage()).contains("method=GET", "route=/todos");
    }

    @Test
    void aClientThatDisappearsMidBodyIsNotAFailure() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 5_000);
            String head = "POST /todos HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nContent-Type: application/json\r\n"
                    + "Content-Length: 1000\r\n\r\n{\"title\":";
            socket.getOutputStream().write(head.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(log.list).anyMatch(
                event -> event.getLevel() == Level.DEBUG
                        && event.getLoggerName().equals("com.capstone.todo.web.gate.ApiGatewayFilter")));
        Awaitility.await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(errors()).isEmpty());
        EVIDENCE.row("T-12 (client abort)", "POST /todos, Content-Length 1000, 10 bytes sent, socket closed",
                "no ERROR entry; a DEBUG entry from the gateway",
                "errors=" + errors().size() + " debug="
                        + log.list.stream().filter(e -> e.getLevel() == Level.DEBUG
                                && e.getLoggerName().endsWith("ApiGatewayFilter")).map(ILoggingEvent::getFormattedMessage).toList());

        RawHttp.Response next = RawHttp.request("GET", "/todos").send(port);
        assertThat(next.status()).isEqualTo(200);
    }
}
