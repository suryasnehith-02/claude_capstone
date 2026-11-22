package com.capstone.todo.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;

import com.capstone.todo.config.JsonConfig;
import com.capstone.todo.support.LogCapture;
import com.capstone.todo.web.gate.ApiGatewayFilter;
import com.capstone.todo.web.gate.RouteMatch;
import com.capstone.todo.web.response.ApiResponses;
import com.capstone.todo.web.response.ErrorCode;

import jakarta.servlet.RequestDispatcher;

/** The container-status mapping of the error controller and its one-entry rule (D-19, D-20 rule 3, DR-17, G2-5 (h)). */
class FallbackErrorControllerTest {

    private static final String LOGGER = FallbackErrorController.class.getName();

    private final ApiResponses responses = new ApiResponses(JsonConfig.newApiJsonMapper());
    private final FallbackErrorController controller = new FallbackErrorController(responses);

    @BeforeEach
    void requestId() {
        MDC.put(ApiGatewayFilter.MDC_REQUEST_ID, "req-9");
    }

    @AfterEach
    void clearRequestId() {
        MDC.remove(ApiGatewayFilter.MDC_REQUEST_ID);
    }

    private static MockHttpServletRequest dispatched(Object status) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ApiRoutes.ERROR);
        request.setAttribute(ApiGatewayFilter.ROUTE_ATTRIBUTE, new RouteMatch(ApiRoutes.TODOS, null));
        if (status != null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        }
        return request;
    }

    private void assertEnvelope(ResponseEntity<byte[]> entity, ErrorCode code) {
        assertThat(entity.getStatusCode().value()).isEqualTo(code.status());
        assertThat(entity.getHeaders().get(HttpHeaders.CONTENT_TYPE)).containsExactly("application/json");
        assertThat(entity.getHeaders().get("X-Content-Type-Options")).containsExactly("nosniff");
        assertThat(entity.getBody()).isEqualTo(responses.fixedError(code));
    }

    @Test
    void itIsTheApplicationsErrorControllerAtTheErrorPathOnly() {
        assertThat(ErrorController.class.isAssignableFrom(FallbackErrorController.class)).isTrue();
        assertThat(FallbackErrorController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly(ApiRoutes.ERROR);
        assertThat(ApiRoutes.ERROR).isEqualTo("/error");
    }

    @ParameterizedTest
    @CsvSource({"404,NOT_FOUND", "405,METHOD_NOT_ALLOWED", "413,PAYLOAD_TOO_LARGE", "415,UNSUPPORTED_MEDIA_TYPE"})
    void theFourMappedStatusesKeepTheirCatalogCodeAndAreNotLogged(int status, ErrorCode code) {
        try (LogCapture log = new LogCapture(LOGGER)) {
            ResponseEntity<byte[]> entity = controller.error(dispatched(status));

            assertEnvelope(entity, code);
            assertThat(log.events()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 200, 204, 301, 302, 304, 400, 401, 402, 403, 406, 407, 408, 409, 410, 411, 412, 414,
            416, 417, 418, 421, 422, 423, 424, 426, 428, 429, 431, 451, 499, 500, 501, 502, 503, 504, 505, 507, 511,
            599, 0, -1, 99999})
    void anyOtherStatusIsAnInternalErrorLoggedOnce(int status) {
        try (LogCapture log = new LogCapture(LOGGER)) {
            ResponseEntity<byte[]> entity = controller.error(dispatched(status));

            assertEnvelope(entity, ErrorCode.INTERNAL_ERROR);
            assertThat(log.events()).hasSize(1);
            assertThat(log.errors().get(0).getFormattedMessage())
                    .contains("requestId=req-9", "method=GET", "route=/todos", "exceptionClass=(none)");
            assertThat(log.errors().get(0).getLoggerName()).isEqualTo(LOGGER);
        }
    }

    @Test
    void aMissingOrMalformedStatusIsAnInternalErrorLoggedOnce() {
        for (Object status : new Object[] {null, "404", 404L, 404.0, new int[] {404}}) {
            try (LogCapture log = new LogCapture(LOGGER)) {
                ResponseEntity<byte[]> entity = controller.error(dispatched(status));

                assertEnvelope(entity, ErrorCode.INTERNAL_ERROR);
                assertThat(log.errors()).as(String.valueOf(status)).hasSize(1);
            }
        }
    }

    @Test
    void nothingTheContainerReportsIsEchoedOrLogged() {
        MockHttpServletRequest request = dispatched(500);
        request.setAttribute(RequestDispatcher.ERROR_MESSAGE, "SECRET-MESSAGE");
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/todos/SECRET-RAW-ID");
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("SECRET-EXCEPTION-TEXT"));
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE, IllegalStateException.class);
        request.setQueryString("q=SECRET-QUERY");
        try (LogCapture log = new LogCapture(LOGGER)) {
            ResponseEntity<byte[]> entity = controller.error(request);

            assertEnvelope(entity, ErrorCode.INTERNAL_ERROR);
            assertThat(new String(entity.getBody(), StandardCharsets.UTF_8)).doesNotContain("SECRET");
            assertThat(log.errors()).hasSize(1);
            assertThat(log.errors().get(0).getFormattedMessage())
                    .contains("exceptionClass=java.lang.IllegalStateException").doesNotContain("SECRET");
            assertThat(log.errors().get(0).getThrowableProxy()).as("the class, not the exception").isNull();
        }
    }

    @Test
    void aMappedStatusWithAnExceptionAttributeStaysMappedAndQuiet() {
        MockHttpServletRequest request = dispatched(404);
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("SECRET"));
        try (LogCapture log = new LogCapture(LOGGER)) {
            assertEnvelope(controller.error(request), ErrorCode.NOT_FOUND);
            assertThat(log.events()).isEmpty();
        }
    }

    @Test
    void aClientThatWentAwayIsLoggedAtDebugOnly() {
        MockHttpServletRequest request = dispatched(500);
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new ClientAbortException());
        try (LogCapture log = new LogCapture(LOGGER)) {
            ResponseEntity<byte[]> entity = controller.error(request);

            assertEnvelope(entity, ErrorCode.INTERNAL_ERROR);
            assertThat(log.errors()).isEmpty();
            assertThat(log.debugs()).hasSize(1);
        }
    }

    /** CR-1: a bare EOFException on the error dispatch is a client abort only if the gateway recorded one. */
    @Test
    void aBareEofExceptionIsAClientAbortOnlyWhenTheGatewayRecordedTheAbort() {
        MockHttpServletRequest unrecorded = dispatched(500);
        unrecorded.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new java.io.EOFException("unexpected end"));
        try (LogCapture log = new LogCapture(LOGGER)) {
            ResponseEntity<byte[]> entity = controller.error(unrecorded);

            assertEnvelope(entity, ErrorCode.INTERNAL_ERROR);
            assertThat(log.errors()).hasSize(1);
            assertThat(log.errors().get(0).getFormattedMessage()).contains("exceptionClass=java.io.EOFException");
            assertThat(log.debugs()).isEmpty();
        }

        MockHttpServletRequest recorded = dispatched(500);
        recorded.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new java.io.EOFException("unexpected end"));
        recorded.setAttribute(ApiGatewayFilter.CLIENT_ABORT_ATTRIBUTE, Boolean.TRUE);
        try (LogCapture log = new LogCapture(LOGGER)) {
            ResponseEntity<byte[]> entity = controller.error(recorded);

            assertEnvelope(entity, ErrorCode.INTERNAL_ERROR);
            assertThat(log.errors()).isEmpty();
            assertThat(log.debugs()).hasSize(1);
        }
    }

    @Test
    void theLoggedMethodIsTheOriginalOneBecauseTomcatReportsAnErrorDispatchAsGet() {
        MockHttpServletRequest request = dispatched(503);
        request.setAttribute(ApiGatewayFilter.METHOD_ATTRIBUTE, "PUT");
        try (LogCapture log = new LogCapture(LOGGER)) {
            controller.error(request);

            assertThat(log.errors()).hasSize(1);
            assertThat(log.errors().get(0).getFormattedMessage()).contains("method=PUT ").doesNotContain("method=GET");
        }
    }

    @Test
    void theBodiesAreTheFixedBytesAndEveryCallGetsItsOwnCopy() {
        ResponseEntity<byte[]> first = controller.error(dispatched(404));
        first.getBody()[0] = 0;
        ResponseEntity<byte[]> second = controller.error(dispatched(404));

        assertThat(second.getBody()).isEqualTo(responses.fixedError(ErrorCode.NOT_FOUND));
    }
}
