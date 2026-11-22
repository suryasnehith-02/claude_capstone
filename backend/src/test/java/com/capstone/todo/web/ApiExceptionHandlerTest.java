package com.capstone.todo.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.EOFException;
import java.lang.reflect.Method;
import java.util.List;

import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.capstone.todo.config.JsonConfig;
import com.capstone.todo.domain.TaskNotFoundException;
import com.capstone.todo.support.LogCapture;
import com.capstone.todo.web.gate.ApiGatewayFilter;
import com.capstone.todo.web.gate.RouteMatch;
import com.capstone.todo.web.response.ApiException;
import com.capstone.todo.web.response.ApiResponses;
import com.capstone.todo.web.response.ErrorCode;
import com.capstone.todo.web.response.ErrorDetail;

import ch.qos.logback.classic.spi.ILoggingEvent;

/** The exception-to-envelope mapping and its single-entry logging rule (D-19, D-20 rule 1). */
class ApiExceptionHandlerTest {

    private static final String LOGGER = ApiExceptionHandler.class.getName();

    private final ApiResponses responses = new ApiResponses(JsonConfig.newApiJsonMapper());
    private final ApiExceptionHandler handler = new ApiExceptionHandler(responses);

    @BeforeEach
    void requestId() {
        MDC.put(ApiGatewayFilter.MDC_REQUEST_ID, "req-1");
    }

    @AfterEach
    void clearRequestId() {
        MDC.remove(ApiGatewayFilter.MDC_REQUEST_ID);
    }

    private static MockHttpServletRequest requestOn(String method, String route, String rawId) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/todos/" + rawId);
        request.setQueryString("q=SECRET-QUERY");
        request.setAttribute(ApiGatewayFilter.ROUTE_ATTRIBUTE, new RouteMatch(route, rawId));
        return request;
    }

    private void assertEnvelope(ResponseEntity<byte[]> entity, ErrorCode code, byte[] body) {
        assertThat(entity.getStatusCode().value()).isEqualTo(code.status());
        assertThat(entity.getHeaders().get(HttpHeaders.CONTENT_TYPE)).containsExactly("application/json");
        assertThat(entity.getBody()).isEqualTo(body);
    }

    @Test
    void itDoesNotExtendSpringsResponseEntityExceptionHandler() {
        assertThat(ResponseEntityExceptionHandler.class.isAssignableFrom(ApiExceptionHandler.class)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = "VALIDATION_ERROR", mode = EnumSource.Mode.EXCLUDE)
    void everyDetailFreeApiExceptionKeepsItsCodeAndStatusAndIsNotLogged(ErrorCode code) {
        try (LogCapture log = new LogCapture(LOGGER)) {
            ResponseEntity<byte[]> entity = handler.apiFailure(new ApiException(code));

            assertEnvelope(entity, code, responses.fixedError(code));
            assertThat(log.events()).isEmpty();
        }
    }

    @Test
    void aValidationErrorCarriesEveryDetailInOrderAndIsNotLogged() {
        List<ErrorDetail> details = List.of(new ErrorDetail("title", "Title is required"),
                new ErrorDetail("description", "Description must be 500 characters or fewer"));
        try (LogCapture log = new LogCapture(LOGGER)) {
            ResponseEntity<byte[]> entity = handler.apiFailure(ApiException.validation(details));

            assertEnvelope(entity, ErrorCode.VALIDATION_ERROR, responses.error(ErrorCode.VALIDATION_ERROR, details));
            assertThat(new String(entity.getBody(), java.nio.charset.StandardCharsets.UTF_8))
                    .contains("\"details\":[{\"field\":\"title\"").contains("{\"field\":\"description\"");
            assertThat(log.events()).isEmpty();
        }
    }

    @Test
    void aMissingTaskIsTodoNotFoundAndIsNotLogged() {
        try (LogCapture log = new LogCapture(LOGGER)) {
            ResponseEntity<byte[]> entity = handler.taskNotFound();

            assertEnvelope(entity, ErrorCode.TODO_NOT_FOUND, responses.fixedError(ErrorCode.TODO_NOT_FOUND));
            assertThat(log.events()).isEmpty();
        }
    }

    @Test
    void springsNotFoundFailuresAreTheSafetyNetForNotFound() {
        try (LogCapture log = new LogCapture(LOGGER)) {
            ResponseEntity<byte[]> noHandler = handler.noHandler();

            assertEnvelope(noHandler, ErrorCode.NOT_FOUND, responses.fixedError(ErrorCode.NOT_FOUND));
            assertThat(log.events()).isEmpty();
        }
    }

    @Test
    void springsMethodFailureIsMethodNotAllowedWithItsAllowHeader() {
        try (LogCapture log = new LogCapture(LOGGER)) {
            ResponseEntity<byte[]> entity = handler.methodNotSupported(
                    new HttpRequestMethodNotSupportedException("TRACE", List.of("GET", "POST")));

            assertEnvelope(entity, ErrorCode.METHOD_NOT_ALLOWED, responses.fixedError(ErrorCode.METHOD_NOT_ALLOWED));
            assertThat(entity.getHeaders().getAllow()).containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
            assertThat(log.events()).isEmpty();
        }
    }

    @Test
    void springsMediaTypeFailureIsUnsupportedMediaType() {
        try (LogCapture log = new LogCapture(LOGGER)) {
            ResponseEntity<byte[]> entity = handler.mediaTypeNotSupported();

            assertEnvelope(entity, ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    responses.fixedError(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
            assertThat(log.events()).isEmpty();
        }
    }

    @Test
    void anUnexpectedFailureIsOneInternalErrorAndExactlyOneErrorEntryWithoutRequestData() {
        MockHttpServletRequest request = requestOn("PUT", ApiRoutes.TODO_ITEM, "SECRET-RAW-ID");
        try (LogCapture log = new LogCapture(LOGGER)) {
            ResponseEntity<byte[]> entity = handler.unexpected(new IllegalStateException("store exploded"), request);

            assertEnvelope(entity, ErrorCode.INTERNAL_ERROR, responses.fixedError(ErrorCode.INTERNAL_ERROR));
            assertThat(log.events()).hasSize(1);
            ILoggingEvent entry = log.errors().get(0);
            assertThat(entry.getLoggerName()).isEqualTo(LOGGER);
            assertThat(entry.getFormattedMessage()).contains("requestId=req-1", "method=PUT", "route=/todos/{id}")
                    .doesNotContain("SECRET");
            assertThat(entry.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
        }
    }

    @Test
    void theBodyOfTheInternalErrorIsGenericWhateverTheFailureSaid() {
        MockHttpServletRequest request = requestOn("GET", ApiRoutes.TODO_ITEM, "x");
        try (LogCapture log = new LogCapture(LOGGER)) {
            ResponseEntity<byte[]> entity = handler.unexpected(
                    new RuntimeException("jdbc:sqlite:C:/secret/path/todo.db is broken"), request);

            assertThat(new String(entity.getBody(), java.nio.charset.StandardCharsets.UTF_8))
                    .isEqualTo("{\"error\":{\"code\":\"INTERNAL_ERROR\",\"message\":\"Internal server error\"}}")
                    .doesNotContain("sqlite").doesNotContain("secret");
        }
    }

    @Test
    void aRequestWithoutARouteIsLoggedWithNoRouteAndAMethodTokenThatIsNotPlainLettersIsHidden() {
        MockHttpServletRequest request = new MockHttpServletRequest("G3T<script>", "/anything");
        try (LogCapture log = new LogCapture(LOGGER)) {
            handler.unexpected(new RuntimeException("x"), request);

            assertThat(log.errors()).hasSize(1);
            assertThat(log.errors().get(0).getFormattedMessage()).contains("method=(other)", "route=(none)")
                    .doesNotContain("script");
        }
    }

    @Test
    void aClientThatWentAwayIsLoggedAtDebugOnlyAndTheEnvelopeIsStillReturned() {
        MockHttpServletRequest request = requestOn("POST", ApiRoutes.TODOS, "x");
        try (LogCapture log = new LogCapture(LOGGER)) {
            ResponseEntity<byte[]> entity = handler.unexpected(new RuntimeException(new ClientAbortException()), request);

            assertEnvelope(entity, ErrorCode.INTERNAL_ERROR, responses.fixedError(ErrorCode.INTERNAL_ERROR));
            assertThat(log.errors()).isEmpty();
            assertThat(log.debugs()).hasSize(1);
        }
    }

    /** CR-1: a store failure that happens to be caused by an EOFException is a failure, never "the client left". */
    @Test
    void aDataAccessFailureCausedByAnEofExceptionIsOneErrorEntryAndTheInternalErrorEnvelope() {
        MockHttpServletRequest request = requestOn("PUT", ApiRoutes.TODO_ITEM, "x");
        try (LogCapture log = new LogCapture(LOGGER)) {
            ResponseEntity<byte[]> entity = handler.unexpected(
                    new DataAccessResourceFailureException("store read failed", new EOFException("unexpected end")),
                    request);

            assertEnvelope(entity, ErrorCode.INTERNAL_ERROR, responses.fixedError(ErrorCode.INTERNAL_ERROR));
            assertThat(log.errors()).hasSize(1);
            assertThat(log.debugs()).isEmpty();
            assertThat(log.errors().get(0).getThrowableProxy().getClassName())
                    .isEqualTo(DataAccessResourceFailureException.class.getName());
        }
    }

    /** Spring's own resolver decides which method answers which exception; ask it, not the annotations. */
    @Test
    void springPicksTheExpectedMethodForEachExceptionType() {
        ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(ApiExceptionHandler.class);

        assertThat(chosen(resolver, new ApiException(ErrorCode.INVALID_ID))).isEqualTo("apiFailure");
        assertThat(chosen(resolver, new TaskNotFoundException())).isEqualTo("taskNotFound");
        assertThat(chosen(resolver, new NoHandlerFoundException("GET", "/x", HttpHeaders.EMPTY))).isEqualTo("noHandler");
        assertThat(chosen(resolver, new NoResourceFoundException(HttpMethod.GET, "x", "x"))).isEqualTo("noHandler");
        assertThat(chosen(resolver, new HttpRequestMethodNotSupportedException("TRACE")))
                .isEqualTo("methodNotSupported");
        assertThat(chosen(resolver,
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON))))
                .isEqualTo("mediaTypeNotSupported");
        // Everything else, including the exceptions Spring would answer with 400 or 406 by itself, is a 500.
        assertThat(chosen(resolver, new IllegalStateException("x"))).isEqualTo("unexpected");
        assertThat(chosen(resolver, new java.io.UncheckedIOException(new java.io.IOException("x")))).isEqualTo("unexpected");
        assertThat(chosen(resolver, new HttpMediaTypeNotAcceptableException("x"))).isEqualTo("unexpected");
        assertThat(chosen(resolver, new HttpMessageNotReadableException("x", new MockHttpInputMessage(new byte[0]))))
                .isEqualTo("unexpected");
    }

    private static String chosen(ExceptionHandlerMethodResolver resolver, Exception failure) {
        Method method = resolver.resolveMethodByThrowable(failure);
        return method == null ? null : method.getName();
    }
}
