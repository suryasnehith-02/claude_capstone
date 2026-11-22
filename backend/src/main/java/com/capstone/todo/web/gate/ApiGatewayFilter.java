package com.capstone.todo.web.gate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.filter.OncePerRequestFilter;

import com.capstone.todo.web.response.ApiException;
import com.capstone.todo.web.response.ApiResponses;
import com.capstone.todo.web.response.ErrorCode;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * C13, the first servlet filter and the error boundary (architecture D-1, D-12, D-19, D-20, section 7.2).
 *
 * <p><b>REQUEST dispatch.</b> Sets the MDC request id (also stored as a request attribute) and
 * {@code X-Content-Type-Options: nosniff}, then runs stage P1 ({@link RouteTable}) and stages P2 and P3
 * ({@link BodyGate}) in that order. The first failure is written and the chain is not called. A request that passes
 * gets its {@link RouteMatch} and {@link RawBody} as request attributes ({@link #ROUTE_ATTRIBUTE},
 * {@link #RAW_BODY_ATTRIBUTE}); later code reads them from there and never reads the input stream again.
 *
 * <p><b>Rejections are written with {@code setStatus} plus bytes and never with {@code sendError} (DR-16).</b> A
 * {@code sendError} would start an ERROR dispatch for every P1 to P3 rejection. HEAD gets the status and headers but
 * no body, whatever the container would do.
 *
 * <p><b>ERROR dispatch (boundary-only mode).</b> No P1 to P3. The request id is restored from the attribute, nosniff
 * is set and the chain runs inside the same try/catch, except when the method is not one of the methods of the route
 * the failed request was sent to (TRACE, for example, which the REQUEST dispatch always rejects): then the
 * precomputed body for the dispatched status is written and the chain, and with it
 * {@code HttpServlet.doTrace}, is not called (DR-16). {@code shouldNotFilterErrorDispatch} is false and a nested
 * ERROR dispatch gets the same treatment (DR-19).
 *
 * <p><b>Failures.</b> Any {@link Throwable} that escapes the chain is logged once at ERROR (D-20 rule 2) with the
 * request id, method, route template and the exception, never the request URI, query, body or raw id; then, if the
 * response is not committed, it is replaced by the 500 envelope. A client that went away is logged at DEBUG only.
 * The MDC is restored in {@code finally}.
 *
 * <p>Nothing here reads a request parameter or the request writer, so no character-encoding filter is needed.
 */
public final class ApiGatewayFilter extends OncePerRequestFilter {

    /** Request attribute holding the {@link RouteMatch} of a request that passed P1. */
    public static final String ROUTE_ATTRIBUTE = RouteMatch.class.getName();

    /** Request attribute holding the {@link RawBody} of a request that passed P2 and P3. */
    public static final String RAW_BODY_ATTRIBUTE = RawBody.class.getName();

    /** Request attribute holding the request id (a UUID) chosen on the REQUEST dispatch. */
    public static final String REQUEST_ID_ATTRIBUTE = ApiGatewayFilter.class.getName() + ".requestId";

    /**
     * Request attribute holding the method token of the original request, set on the REQUEST dispatch. Tomcat reports
     * every error dispatch as a GET (seen at T-13: a PUT whose container error was dispatched read {@code GET}), so
     * the log entries written on that path read the method from here.
     */
    public static final String METHOD_ATTRIBUTE = ApiGatewayFilter.class.getName() + ".method";

    /**
     * Request attribute set to {@code true} when this filter itself saw that the client went away (the body could not
     * be read, or an abort type came out of the chain). Tomcat then reports the same request on the ERROR dispatch with
     * a bare {@link java.io.EOFException}, which is not recognisable as an abort by its type alone; the error controller
     * reads this attribute instead of treating every {@code EOFException} as a client that left (CR-1).
     */
    public static final String CLIENT_ABORT_ATTRIBUTE = ApiGatewayFilter.class.getName() + ".clientAbort";

    /** The MDC key of the request id. */
    public static final String MDC_REQUEST_ID = "requestId";

    public static final String NOSNIFF_HEADER = "X-Content-Type-Options";
    public static final String NOSNIFF_VALUE = "nosniff";

    private static final Logger LOG = LoggerFactory.getLogger(ApiGatewayFilter.class);
    private static final Pattern PLAIN_METHOD = Pattern.compile("[A-Za-z]{1,16}");

    private final RouteTable routes;
    private final BodyGate bodyGate;
    private final ApiResponses responses;

    public ApiGatewayFilter(RouteTable routes, BodyGate bodyGate, ApiResponses responses) {
        this.routes = Objects.requireNonNull(routes, "routes");
        this.bodyGate = Objects.requireNonNull(bodyGate, "bodyGate");
        this.responses = Objects.requireNonNull(responses, "responses");
    }

    /** The ERROR dispatch must pass through the boundary too (DR-4, V-10). */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            boundaryOnly(request, response, chain);
        } else {
            gateway(request, response, chain);
        }
    }

    /** A nested ERROR dispatch (an error forwarded from inside this filter's own chain) runs boundary-only (DR-19). */
    @Override
    protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        boundaryOnly(request, response, chain);
    }

    private void gateway(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
        String previousId = MDC.get(MDC_REQUEST_ID);
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        request.setAttribute(METHOD_ATTRIBUTE, request.getMethod());
        MDC.put(MDC_REQUEST_ID, requestId);
        try {
            response.setHeader(NOSNIFF_HEADER, NOSNIFF_VALUE);

            RouteTable.Outcome outcome = routes.resolve(request.getMethod(), request.getRequestURI());
            if (outcome instanceof RouteTable.Outcome.Rejected rejected) {
                writeError(request, response, rejected.errorCode(), rejected.allow());
                return;
            }
            RouteMatch match = ((RouteTable.Outcome.Matched) outcome).match();
            request.setAttribute(ROUTE_ATTRIBUTE, match);

            RawBody body;
            try {
                body = bodyGate.check(request);
            } catch (ApiException rejected) {
                writeError(request, response, rejected.errorCode(), null);
                return;
            } catch (IOException unreadable) {
                // The container could not deliver the body: the client went away or sent broken framing. Not a
                // server failure, so no 500 and no ERROR entry; the answer, if anyone is still there, is a 400.
                request.setAttribute(CLIENT_ABORT_ATTRIBUTE, Boolean.TRUE);
                LOG.debug("Request body could not be read: requestId={} cause={}", requestId,
                        unreadable.getClass().getName());
                if (!response.isCommitted()) {
                    writeError(request, response, ErrorCode.INVALID_REQUEST_BODY, null);
                }
                return;
            }
            request.setAttribute(RAW_BODY_ATTRIBUTE, body);

            chain.doFilter(new FixedEncodingRequest(request), response);
        } catch (Throwable failure) {
            fail(request, response, failure);
        } finally {
            restoreMdc(previousId);
        }
    }

    private void boundaryOnly(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
        String previousId = MDC.get(MDC_REQUEST_ID);
        String requestId = requestIdOf(request);
        MDC.put(MDC_REQUEST_ID, requestId);
        try {
            response.setHeader(NOSNIFF_HEADER, NOSNIFF_VALUE);

            String allow = null;
            if (request.getAttribute(ROUTE_ATTRIBUTE) == null) {
                // No REQUEST dispatch of this filter recorded a route, so the method has not been checked against
                // one: look at the request the container is reporting on.
                RouteTable.Outcome outcome = routes.resolve(request.getMethod(), originalRequestUri(request));
                if (outcome instanceof RouteTable.Outcome.Rejected rejected) {
                    if (rejected.errorCode() == ErrorCode.METHOD_NOT_ALLOWED) {
                        allow = rejected.allow();
                    }
                    answerDispatchedStatus(request, response, allow);
                    return;
                }
            }
            chain.doFilter(new FixedEncodingRequest(request), response);
        } catch (Throwable failure) {
            fail(request, response, failure);
        } finally {
            restoreMdc(previousId);
        }
    }

    /**
     * Content-Type parameters are ignored and every body is UTF-8 (decision S2-4), so downstream code is shown UTF-8 as
     * the request's character encoding whatever the client wrote after {@code charset=}. Without this, Spring's
     * response writer decodes the request path with that name and an illegal one ({@code charset=} with no value)
     * fails with an {@code IllegalCharsetNameException}, which became a 500 for any request (found at CP-B).
     */
    private static final class FixedEncodingRequest extends HttpServletRequestWrapper {

        FixedEncodingRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getCharacterEncoding() {
            return StandardCharsets.UTF_8.name();
        }

        @Override
        public void setCharacterEncoding(String encoding) {
            // Ignored: the encoding is fixed (S2-4).
        }
    }

    /**
     * Boundary-only answer for a method that no route accepts (DR-16): the precomputed body for the dispatched status,
     * written directly, so the chain and {@code HttpServlet.doTrace} are never reached.
     */
    private void answerDispatchedStatus(HttpServletRequest request, HttpServletResponse response, String allow)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        ErrorCode code = ErrorCode.forContainerStatus(status instanceof Integer s ? s : response.getStatus());
        if (code == ErrorCode.INTERNAL_ERROR) {
            Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE);
            LOG.error("Request answered with an internal error by the error boundary: requestId={} method={} "
                    + "route={} exceptionClass={}", MDC.get(MDC_REQUEST_ID), loggableMethod(request),
                    loggableRoute(request), exception instanceof Class<?> type ? type.getName() : "(none)");
        }
        writeError(request, response, code, code == ErrorCode.METHOD_NOT_ALLOWED ? allow : null);
    }

    private void fail(HttpServletRequest request, HttpServletResponse response, Throwable failure) {
        if (isClientAbort(failure)) {
            request.setAttribute(CLIENT_ABORT_ATTRIBUTE, Boolean.TRUE);
            LOG.debug("Client went away: requestId={} cause={}", MDC.get(MDC_REQUEST_ID),
                    failure.getClass().getName());
            return;
        }
        LOG.error("Request failed: requestId={} method={} route={}", MDC.get(MDC_REQUEST_ID),
                loggableMethod(request), loggableRoute(request), failure);
        if (response.isCommitted()) {
            return;
        }
        try {
            response.reset();
            writeError(request, response, ErrorCode.INTERNAL_ERROR, null);
        } catch (IOException | RuntimeException secondary) {
            // The 500 itself could not be written (for example the client is gone). The original failure is logged.
            LOG.debug("The 500 response could not be written: requestId={} cause={}", MDC.get(MDC_REQUEST_ID),
                    secondary.getClass().getName());
        }
    }

    /** Status, headers and the fixed body of {@code code}; no body for HEAD; the response is committed afterwards. */
    private void writeError(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String allow)
            throws IOException {
        byte[] body = responses.fixedError(code);
        response.setStatus(code.status());
        response.setHeader(NOSNIFF_HEADER, NOSNIFF_VALUE);
        if (allow != null) {
            response.setHeader("Allow", allow);
        }
        response.setContentType(ApiResponses.CONTENT_TYPE);
        response.setContentLength(body.length);
        if (!"HEAD".equals(request.getMethod())) {
            response.getOutputStream().write(body);
        }
        response.flushBuffer();
    }

    private static String requestIdOf(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (existing instanceof String id) {
            return id;
        }
        String created = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, created);
        return created;
    }

    /** The URI of the request the container reports on; during an error dispatch the request URI is the error path. */
    private static String originalRequestUri(HttpServletRequest request) {
        Object original = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return original instanceof String uri ? uri : request.getRequestURI();
    }

    /** The route template of the request, or {@code (none)}; the only form of the route that may be logged. */
    public static String loggableRoute(HttpServletRequest request) {
        return request.getAttribute(ROUTE_ATTRIBUTE) instanceof RouteMatch match ? match.route() : "(none)";
    }

    /** The method token is client text: only a plain letters-only token is logged. */
    public static String loggableMethod(HttpServletRequest request) {
        String method = request.getAttribute(METHOD_ATTRIBUTE) instanceof String original ? original : request.getMethod();
        return method != null && PLAIN_METHOD.matcher(method).matches() ? method : "(other)";
    }

    private static void restoreMdc(String previousId) {
        if (previousId == null) {
            MDC.remove(MDC_REQUEST_ID);
        } else {
            MDC.put(MDC_REQUEST_ID, previousId);
        }
    }

    /**
     * A write or read that failed because the client closed the connection: the failure itself, or its direct cause,
     * is Tomcat's {@link ClientAbortException} or Spring's {@link AsyncRequestNotUsableException}. Nothing deeper in
     * the cause chain counts, and neither does an {@link java.io.EOFException} on its own: a server-side failure
     * that merely wraps one (a store or file read, for example) is a failure and gets the 500 envelope.
     */
    public static boolean isClientAbort(Throwable failure) {
        return failure != null && (isAbortType(failure) || isAbortType(failure.getCause()));
    }

    /** Whether this filter recorded on the request that the client went away. */
    public static boolean clientWentAway(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(CLIENT_ABORT_ATTRIBUTE));
    }

    private static boolean isAbortType(Throwable candidate) {
        return candidate instanceof ClientAbortException || candidate instanceof AsyncRequestNotUsableException;
    }
}
