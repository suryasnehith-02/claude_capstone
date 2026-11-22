package com.capstone.todo.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.capstone.todo.web.gate.ApiGatewayFilter;
import com.capstone.todo.web.response.ApiResponses;
import com.capstone.todo.web.response.ErrorCode;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The target of the servlet container's error dispatch (architecture D-19). Its presence makes Boot's
 * {@code BasicErrorController} (JSON with a timestamp, a path and a message, or the whitelabel page) back off, so
 * the only error body this service can produce on that path is the envelope.
 *
 * <p>The container's status is mapped through {@link ErrorCode#forContainerStatus}: 404, 405, 413 and 415 keep their
 * catalog codes; <b>any other status, 4xx or 5xx, becomes 500 INTERNAL_ERROR</b> (G2-5 (h), DR-17), because the
 * catalog has no other code for it and no status is ever passed through. Each such 500 is logged once at ERROR, here,
 * by the component that writes it (D-20 rule 3), with the class (never the message) of the exception the container
 * reports, if any. The mapped four are client errors and are not logged.
 *
 * <p>The body is one of the fixed byte arrays prepared at startup, so this handler has no serialisation failure path.
 * Nothing the container reports (message, exception, request URI) is echoed. {@code nosniff} is set here again
 * although the gateway filter already set it on the error dispatch. The path is reachable only through that dispatch:
 * the gateway rejects {@code /error} as an ordinary request with 404.
 */
@Controller
@RequestMapping(ApiRoutes.ERROR)
public class FallbackErrorController implements ErrorController {

    private static final Logger LOG = LoggerFactory.getLogger(FallbackErrorController.class);

    private final ApiResponses responses;

    public FallbackErrorController(ApiResponses responses) {
        this.responses = responses;
    }

    @RequestMapping
    public ResponseEntity<byte[]> error(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        ErrorCode code = status instanceof Integer dispatched
                ? ErrorCode.forContainerStatus(dispatched) : ErrorCode.INTERNAL_ERROR;
        if (code == ErrorCode.INTERNAL_ERROR) {
            String exceptionClass = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable failure
                    ? failure.getClass().getName() : "(none)";
            if (clientWentAway(request)) {
                // Tomcat reports a request whose client went away as an error dispatch with a bare EOFException, which
                // is not told from a server-side EOFException by its type; the gateway recorded what it saw (CR-1).
                LOG.debug("Client went away: requestId={} cause={}", MDC.get(ApiGatewayFilter.MDC_REQUEST_ID),
                        exceptionClass);
            } else {
                LOG.error("Request answered with an internal error by the error controller: requestId={} "
                        + "method={} route={} exceptionClass={}", MDC.get(ApiGatewayFilter.MDC_REQUEST_ID),
                        ApiGatewayFilter.loggableMethod(request), ApiGatewayFilter.loggableRoute(request),
                        exceptionClass);
            }
        }
        return ResponseEntity.status(HttpStatus.valueOf(code.status()))
                .header(ApiGatewayFilter.NOSNIFF_HEADER, ApiGatewayFilter.NOSNIFF_VALUE)
                .header(HttpHeaders.CONTENT_TYPE, ApiResponses.CONTENT_TYPE)
                .body(responses.fixedError(code));
    }

    /** The client went away if the gateway recorded it, or if the container reports one of the abort types itself. */
    private static boolean clientWentAway(HttpServletRequest request) {
        return ApiGatewayFilter.clientWentAway(request)
                || request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable failure
                        && ApiGatewayFilter.isClientAbort(failure);
    }
}
