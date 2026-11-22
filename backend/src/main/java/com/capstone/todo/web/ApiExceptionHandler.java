package com.capstone.todo.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.capstone.todo.domain.TaskNotFoundException;
import com.capstone.todo.web.gate.ApiGatewayFilter;
import com.capstone.todo.web.response.ApiException;
import com.capstone.todo.web.response.ApiResponses;
import com.capstone.todo.web.response.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Turns what the controller path throws into the error envelope (architecture D-19). It deliberately does not extend
 * Spring's {@code ResponseEntityExceptionHandler}: that class would answer with its own bodies and codes (and
 * ProblemDetail), and none of its cases may reach a client.
 *
 * <p>Mapped: {@link ApiException} to its own code (and details), {@link TaskNotFoundException} to TODO_NOT_FOUND,
 * and, as a safety net that should never trigger because the gateway filter has already decided the route and the
 * media type, Spring's own not-found, method and media-type failures to NOT_FOUND, METHOD_NOT_ALLOWED and
 * UNSUPPORTED_MEDIA_TYPE. Everything else is an unexpected failure: it is logged once at ERROR (D-20 rule 1: this class
 * converts it, so this class logs it) and answered with INTERNAL_ERROR, which carries no detail. The 4xx are never
 * logged. A client that went away (its own exception types only, see {@link ApiGatewayFilter#isClientAbort}) is
 * logged at DEBUG only; the INTERNAL_ERROR answer is still returned, and writing it to a gone client fails harmlessly.
 *
 * <p>The log entry holds the request id, the method (only if it is plain letters), the route template and the
 * exception with its stack, and never the URI, the query, the body or the raw id (NFR-7).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ApiResponses responses;

    public ApiExceptionHandler(ApiResponses responses) {
        this.responses = responses;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<byte[]> apiFailure(ApiException failure) {
        return answer(failure.errorCode(), responses.error(failure), HttpHeaders.EMPTY);
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<byte[]> taskNotFound() {
        return fixed(ErrorCode.TODO_NOT_FOUND);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<byte[]> noHandler() {
        return fixed(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<byte[]> methodNotSupported(HttpRequestMethodNotSupportedException failure) {
        return answer(ErrorCode.METHOD_NOT_ALLOWED, responses.fixedError(ErrorCode.METHOD_NOT_ALLOWED),
                failure.getHeaders());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<byte[]> mediaTypeNotSupported() {
        return fixed(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<byte[]> unexpected(Exception failure, HttpServletRequest request) {
        if (ApiGatewayFilter.isClientAbort(failure)) {
            LOG.debug("Client went away: requestId={} cause={}", MDC.get(ApiGatewayFilter.MDC_REQUEST_ID),
                    failure.getClass().getName());
            // Writing to a client that is gone fails harmlessly, and the gateway filter already tolerates that.
            return fixed(ErrorCode.INTERNAL_ERROR);
        }
        LOG.error("Request failed: requestId={} method={} route={}", MDC.get(ApiGatewayFilter.MDC_REQUEST_ID),
                ApiGatewayFilter.loggableMethod(request), ApiGatewayFilter.loggableRoute(request), failure);
        return fixed(ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<byte[]> fixed(ErrorCode code) {
        return answer(code, responses.fixedError(code), HttpHeaders.EMPTY);
    }

    private static ResponseEntity<byte[]> answer(ErrorCode code, byte[] body, HttpHeaders extra) {
        return ResponseEntity.status(HttpStatus.valueOf(code.status())).headers(extra)
                .header(HttpHeaders.CONTENT_TYPE, ApiResponses.CONTENT_TYPE).body(body);
    }
}
