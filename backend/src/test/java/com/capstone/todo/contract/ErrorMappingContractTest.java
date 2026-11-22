package com.capstone.todo.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.capstone.todo.support.EvidenceLog;
import com.capstone.todo.support.FailingTaskRepository;
import com.capstone.todo.support.LogCapture;
import com.capstone.todo.support.RawHttp;
import com.capstone.todo.support.TestDataDir;
import com.capstone.todo.support.hooks.TestHooksConfig;
import com.capstone.todo.web.ApiExceptionHandler;
import com.capstone.todo.web.FallbackErrorController;
import com.capstone.todo.web.gate.ApiGatewayFilter;
import com.capstone.todo.web.response.ApiResponses;
import com.capstone.todo.web.response.ErrorCode;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;

/**
 * Unexpected failures and the container's error dispatch on real Tomcat with the real controller, advice and error
 * controller (T-ERR, T-LOG paths (1) and (3), AC-13, DR-17, G2-5 (h), V-18). The store is a repository whose every call
 * fails, and the test hooks can send a container error with any status. Each case asserts the exact envelope, the
 * headers and the number of ERROR entries across the whole JVM log, and that no entry holds request data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(TestHooksConfig.class)
class ErrorMappingContractTest {

    private static final Path DATA_FILE = TestDataDir.newDataFile(ErrorMappingContractTest.class);
    private static final EvidenceLog EVIDENCE = new EvidenceLog();
    private static final String ID = "0b8f6f4e-3a35-4d0c-9d41-0d9f3c2a7b11";
    private static final String SECRET_TITLE = "SECRET-TITLE-9f3a";
    private static final String SECRET_QUERY = "SECRETQUERY-77";

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingStore {
        @Bean
        @Primary
        FailingTaskRepository failingRepository() {
            return new FailingTaskRepository();
        }
    }

    @DynamicPropertySource
    static void dataFile(DynamicPropertyRegistry registry) {
        registry.add("todo.data-file", DATA_FILE::toString);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ApiResponses responses;

    @Autowired
    private FailingTaskRepository failing;

    @AfterAll
    static void writeEvidence() {
        EVIDENCE.writeTo("cp-c-error-evidence.txt");
    }

    private static final String ADVICE = ApiExceptionHandler.class.getName();
    private static final String FALLBACK = FallbackErrorController.class.getName();

    private void assertEnvelope(RawHttp.Response response, ErrorCode code) {
        assertThat(response.status()).isEqualTo(code.status());
        assertThat(response.body()).isEqualTo(responses.fixedError(code));
        assertThat(response.headers("Content-Type")).containsExactly("application/json");
        assertThat(response.headers("X-Content-Type-Options")).containsExactly("nosniff");
        assertThat(response.headers()).noneMatch(h -> h[0].toLowerCase(Locale.ROOT).startsWith("access-control-"));
        String text = response.bodyText();
        assertThat(text).doesNotContain("\"trace\"").doesNotContain("\"exception\"").doesNotContain("\"path\"")
                .doesNotContain("\"timestamp\"").doesNotContain("\"status\"").doesNotContain("at com.");
    }

    private static void assertOneEntryWithoutRequestData(LogCapture log, String logger, String method, String route) {
        assertThat(log.errors()).as("exactly one ERROR entry in the whole log").hasSize(1);
        ILoggingEvent entry = log.errors().get(0);
        assertThat(entry.getLoggerName()).isEqualTo(logger);
        assertThat(entry.getFormattedMessage()).contains("requestId=", "route=" + route, "method=" + method + " ");
        assertThat(entry.getFormattedMessage()).doesNotContain(SECRET_TITLE).doesNotContain(SECRET_QUERY).doesNotContain(ID)
                .doesNotContain("?");
        // The whole event as a log would print it: message, MDC and the throwable with its message, causes and stack.
        assertThat(LogCapture.render(entry)).doesNotContain(SECRET_TITLE).doesNotContain(SECRET_QUERY).doesNotContain(ID);
        assertThat(entry.getMDCPropertyMap()).containsKey(ApiGatewayFilter.MDC_REQUEST_ID);
    }

    // ---------------------------------------------------------------- T-ERR and T-LOG (1): the advice path

    @Test
    void aFailingStoreGivesOneInternalErrorEnvelopeAndOneLogEntryOnEveryOperation_ac13() {
        record Call(String method, String target, String body, String route) {
        }
        List<Call> calls = List.of(
                new Call("POST", "/todos?q=" + SECRET_QUERY, "{\"title\":\"" + SECRET_TITLE + "\"}", "/todos"),
                new Call("GET", "/todos?q=" + SECRET_QUERY, null, "/todos"),
                new Call("GET", "/todos/" + ID + "?q=" + SECRET_QUERY, null, "/todos/{id}"),
                new Call("PUT", "/todos/" + ID + "?q=" + SECRET_QUERY, "{\"title\":\"" + SECRET_TITLE + "\"}", "/todos/{id}"),
                new Call("DELETE", "/todos/" + ID + "?q=" + SECRET_QUERY, null, "/todos/{id}"),
                new Call("PATCH", "/todos/" + ID + "/status?q=" + SECRET_QUERY, "{\"status\":\"Completed\"}",
                        "/todos/{id}/status"));
        for (Call call : calls) {
            int attemptsBefore = failing.attempts();
            try (LogCapture log = new LogCapture(ADVICE)) {
                RawHttp.Request request = RawHttp.request(call.method(), call.target());
                if (call.body() != null) {
                    request.header("Content-Type", "application/json").body(call.body());
                }
                RawHttp.Response response = request.send(port);

                EVIDENCE.row("T-ERR/T-LOG (1)", call.method() + " " + call.route() + " with a failing store",
                        "500 INTERNAL_ERROR envelope, one ERROR entry",
                        response.status() + " " + response.bodyText() + " entries=" + log.errors().size() + " "
                                + (log.errors().isEmpty() ? "" : log.errors().get(0).getFormattedMessage()));
                assertEnvelope(response, ErrorCode.INTERNAL_ERROR);
                assertOneEntryWithoutRequestData(log, ADVICE, call.method(), call.route());
                assertThat(log.errors().get(0).getFormattedMessage()).contains("method=" + call.method());
                IThrowableProxy logged = log.errors().get(0).getThrowableProxy();
                assertThat(logged.getClassName()).isEqualTo("org.springframework.dao.DataAccessResourceFailureException");
                // Nothing between the store and the log added text to the failure: no wrapper, no extra message.
                assertThat(logged.getMessage()).isEqualTo(FailingTaskRepository.FAILURE_MESSAGE);
                assertThat(logged.getCause()).isNull();
            }
            assertThat(failing.attempts()).as(call.method() + " " + call.route() + " reached the store").isGreaterThan(attemptsBefore);
        }
    }

    @Test
    void clientErrorsAreNeverLoggedAndNeverReachTheStore() {
        int attemptsBefore = failing.attempts();
        try (LogCapture log = new LogCapture(ADVICE, FALLBACK)) {
            RawHttp.Response badId = RawHttp.request("GET", "/todos/not-a-uuid").send(port);
            RawHttp.Response badBody = RawHttp.request("POST", "/todos").header("Content-Type", "application/json")
                    .body("{bad").send(port);
            RawHttp.Response invalid = RawHttp.request("POST", "/todos").header("Content-Type", "application/json")
                    .body("{}").send(port);
            RawHttp.Response noRoute = RawHttp.request("GET", "/nope").send(port);
            RawHttp.Response wrongMethod = RawHttp.request("DELETE", "/todos").send(port);
            RawHttp.Response wrongType = RawHttp.request("POST", "/todos").header("Content-Type", "text/plain").body("x")
                    .send(port);

            assertEnvelope(badId, ErrorCode.INVALID_ID);
            assertEnvelope(badBody, ErrorCode.INVALID_REQUEST_BODY);
            assertThat(invalid.status()).isEqualTo(400);
            assertEnvelope(noRoute, ErrorCode.NOT_FOUND);
            assertEnvelope(wrongMethod, ErrorCode.METHOD_NOT_ALLOWED);
            assertEnvelope(wrongType, ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            assertThat(log.errors()).isEmpty();
            assertThat(log.events().stream().filter(e -> e.getLoggerName().equals(ADVICE) || e.getLoggerName().equals(FALLBACK)))
                    .isEmpty();
        }
        assertThat(failing.attempts()).as("P1 to P6 never touch the store").isEqualTo(attemptsBefore);
    }

    // ---------------------------------------------------------------- T-ERR and T-LOG (3): the error controller path

    private RawHttp.Response sendErrorFromBehindTheGateway(String method, String target, String body, int status) {
        RawHttp.Request request = RawHttp.request(method, target).header(TestHooksConfig.SEND_ERROR_HEADER,
                String.valueOf(status));
        if (body != null) {
            request.header("Content-Type", "application/json").body(body);
        }
        return request.send(port);
    }

    @Test
    void theFourMappedContainerStatusesAreAnsweredWithTheirCatalogCodeAndAreNotLogged_v18() {
        for (ErrorCode code : List.of(ErrorCode.NOT_FOUND, ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.PAYLOAD_TOO_LARGE,
                ErrorCode.UNSUPPORTED_MEDIA_TYPE)) {
            try (LogCapture log = new LogCapture(ADVICE, FALLBACK)) {
                RawHttp.Response response = sendErrorFromBehindTheGateway("GET", "/todos?q=" + SECRET_QUERY, null, code.status());

                EVIDENCE.row("T-ERR/V-18", "GET /todos, sendError(" + code.status() + ") hook",
                        code.status() + " " + code.code() + " envelope from FallbackErrorController, no log",
                        response.status() + " " + response.bodyText() + " entries=" + log.events().size());
                assertEnvelope(response, code);
                assertThat(log.errors()).isEmpty();
            }
        }
    }

    @Test
    void anyOtherContainerStatusIsAnInternalErrorLoggedOnceByTheErrorController_dr17_g2_5h() {
        for (int status : new int[] {400, 401, 403, 406, 409, 418, 422, 429, 500, 501, 502, 503, 504}) {
            try (LogCapture log = new LogCapture(ADVICE, FALLBACK)) {
                RawHttp.Response response = sendErrorFromBehindTheGateway("GET", "/todos?q=" + SECRET_QUERY, null, status);

                EVIDENCE.row("T-ERR/T-LOG (3)/DR-17", "GET /todos, sendError(" + status + ") hook",
                        "500 INTERNAL_ERROR envelope, one ERROR entry from FallbackErrorController",
                        response.status() + " " + response.bodyText() + " entries=" + log.errors().size() + " "
                                + (log.errors().isEmpty() ? "" : log.errors().get(0).getFormattedMessage()));
                assertEnvelope(response, ErrorCode.INTERNAL_ERROR);
                assertOneEntryWithoutRequestData(log, FALLBACK, "GET", "/todos");
            }
        }
    }

    @Test
    void theErrorDispatchOfABodyOperationIsTheSameEnvelopeAndOneEntry() {
        try (LogCapture log = new LogCapture(ADVICE, FALLBACK)) {
            RawHttp.Response mapped = sendErrorFromBehindTheGateway("POST", "/todos", "{\"title\":\"" + SECRET_TITLE + "\"}", 404);
            RawHttp.Response unmapped = sendErrorFromBehindTheGateway("PUT", "/todos/" + ID,
                    "{\"title\":\"" + SECRET_TITLE + "\"}", 503);

            assertEnvelope(mapped, ErrorCode.NOT_FOUND);
            assertEnvelope(unmapped, ErrorCode.INTERNAL_ERROR);
            assertThat(log.errors()).hasSize(1);
            assertThat(log.errors().get(0).getLoggerName()).isEqualTo(FALLBACK);
            assertThat(log.errors().get(0).getFormattedMessage()).contains("method=PUT", "route=/todos/{id}")
                    .doesNotContain(SECRET_TITLE).doesNotContain(ID);
            EVIDENCE.row("T-LOG (3)", "PUT /todos/<id>, sendError(503) hook",
                    "one entry with the original method (Tomcat's error dispatch reports GET)",
                    log.errors().get(0).getFormattedMessage());
        }
    }
}
