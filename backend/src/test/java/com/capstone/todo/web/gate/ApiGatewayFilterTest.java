package com.capstone.todo.web.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import com.capstone.todo.config.JsonConfig;
import com.capstone.todo.support.GateRequest;
import com.capstone.todo.web.response.ApiResponses;
import com.capstone.todo.web.response.ErrorCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;

/**
 * C13 at unit level (T-12): what the filter writes, what it passes on, what it logs and what it leaves behind. Real
 * Tomcat behaviour (raw URI, TRACE, HEAD, chunking, 100-continue, filter order) is in the contract tests.
 */
class ApiGatewayFilterTest {

    private static final String ID = "0b8f6f4e-3a35-4d0c-9d41-0d9f3c2a7b11";
    private static final byte[] VALID_BODY = "{\"title\":\"x\"}".getBytes(StandardCharsets.UTF_8);

    private final ApiResponses responses = new ApiResponses(JsonConfig.newApiJsonMapper());
    private final ApiGatewayFilter filter = new ApiGatewayFilter(new RouteTable(), new BodyGate(), responses);
    private final ListAppender<ILoggingEvent> log = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ApiGatewayFilter.class);
    private Level levelBefore;

    @BeforeEach
    void captureLog() {
        MDC.clear();
        levelBefore = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        log.start();
        logger.addAppender(log);
    }

    @AfterEach
    void releaseLog() {
        logger.detachAppender(log);
        logger.setLevel(levelBefore);
        log.stop();
        MDC.clear();
    }

    /** A response that fails the test on any {@code sendError} (DR-16). */
    private static final class NoSendErrorResponse extends MockHttpServletResponse {
        @Override
        public void sendError(int status) {
            throw new AssertionError("sendError(" + status + ") was called");
        }

        @Override
        public void sendError(int status, String message) {
            throw new AssertionError("sendError(" + status + ") was called");
        }
    }

    private static final class Calls implements FilterChain {
        final AtomicInteger count = new AtomicInteger();

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            count.incrementAndGet();
        }
    }

    private MockHttpServletResponse run(GateRequest request, FilterChain chain) throws Exception {
        MockHttpServletResponse response = new NoSendErrorResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private void assertEnvelope(MockHttpServletResponse response, ErrorCode code) throws IOException {
        assertThat(response.getStatus()).isEqualTo(code.status());
        assertThat(response.getContentAsByteArray()).isEqualTo(responses.fixedError(code));
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getHeader("Content-Length")).isEqualTo(String.valueOf(responses.fixedError(code).length));
        assertThat(response.getHeaderNames()).noneMatch(name -> name.toLowerCase().startsWith("access-control-"));
        assertThat(response.isCommitted()).isTrue();
    }

    private List<ILoggingEvent> errorEntries() {
        return log.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    }

    // ------------------------------------------------------------------ P1

    @Test
    void anUndefinedPathIsNotFoundForAnyMethodAndTheChainIsNotCalled() throws Exception {
        for (String method : List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE", "FOO", "get")) {
            Calls chain = new Calls();
            GateRequest request = new GateRequest(method, "/nope");

            MockHttpServletResponse response = run(request, chain);

            assertEnvelope(response, ErrorCode.NOT_FOUND);
            assertThat(chain.count).as(method).hasValue(0);
            assertThat(response.getHeader("Allow")).isNull();
        }
    }

    @Test
    void aWrongMethodOnADefinedPathGivesTheRoutesOwnAllowHeader() throws Exception {
        record Case(String method, String uri, String allow) {
        }
        for (Case c : List.of(
                new Case("DELETE", "/todos", "GET, POST"), new Case("TRACE", "/todos", "GET, POST"),
                new Case("OPTIONS", "/todos/" + ID, "GET, PUT, DELETE"), new Case("get", "/todos/" + ID, "GET, PUT, DELETE"),
                new Case("POST", "/todos/" + ID, "GET, PUT, DELETE"), new Case("FOO", "/todos/" + ID + "/status", "PATCH"),
                new Case("PUT", "/todos/" + ID + "/status", "PATCH"))) {
            Calls chain = new Calls();

            MockHttpServletResponse response = run(new GateRequest(c.method(), c.uri()), chain);

            assertEnvelope(response, ErrorCode.METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).as(c.toString()).isEqualTo(c.allow());
            assertThat(chain.count).as(c.toString()).hasValue(0);
        }
    }

    @Test
    void headGetsTheStatusAndHeadersButNoBodyOnDefinedAndUndefinedPaths() throws Exception {
        MockHttpServletResponse defined = run(new GateRequest("HEAD", "/todos"), new Calls());
        MockHttpServletResponse undefined = run(new GateRequest("HEAD", "/nope"), new Calls());

        assertThat(defined.getStatus()).isEqualTo(405);
        assertThat(defined.getHeader("Allow")).isEqualTo("GET, POST");
        assertThat(defined.getContentAsByteArray()).isEmpty();
        assertThat(defined.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(defined.isCommitted()).isTrue();
        assertThat(undefined.getStatus()).isEqualTo(404);
        assertThat(undefined.getContentAsByteArray()).isEmpty();
        assertThat(undefined.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void theQueryStringNeverChangesTheRouteAndAPathWithParametersIsNotFound() throws Exception {
        Calls passed = new Calls();
        assertThat(run(new GateRequest("GET", "/todos?x=1&y=/nope"), passed).getStatus()).isEqualTo(200);
        assertThat(passed.count).hasValue(1);

        assertEnvelope(run(new GateRequest("GET", "/todos;x=1"), new Calls()), ErrorCode.NOT_FOUND);
        assertEnvelope(run(new GateRequest("GET", "/%74odos"), new Calls()), ErrorCode.NOT_FOUND);
        assertEnvelope(run(new GateRequest("GET", "/error"), new Calls()), ErrorCode.NOT_FOUND);
        assertEnvelope(run(new GateRequest("GET", "/"), new Calls()), ErrorCode.NOT_FOUND);
    }

    // ------------------------------------------------------------------ P1, P2, P3 order and rejection

    @Test
    void p1BeatsP2AndP3AndNoBodyByteIsRead() throws Exception {
        byte[] big = new byte[70_000];
        GateRequest request = new GateRequest("POST", "/todos/").declaring(big.length).body(big)
                .contentTypes("text/plain");

        MockHttpServletResponse response = run(request, new Calls());

        assertEnvelope(response, ErrorCode.NOT_FOUND);
        assertThat(request.streamRequested()).isFalse();
    }

    @Test
    void p2BeatsP3AndAnOversizedContentLengthIsRejectedWithoutReadingTheBody() throws Exception {
        GateRequest request = new GateRequest("PATCH", "/todos/not-a-uuid/status").declaring(65_537)
                .body(new byte[65_537]).contentTypes("text/plain");

        MockHttpServletResponse response = run(request, new Calls());

        assertEnvelope(response, ErrorCode.PAYLOAD_TOO_LARGE);
        assertThat(request.streamRequested()).isFalse();
    }

    @Test
    void aChunkedBodyOverTheLimitIsRejectedAfterReadingExactlyTheCap() throws Exception {
        GateRequest request = new GateRequest("PATCH", "/todos/not-a-uuid/status").declaring(-1)
                .bodyFrom(new GateRequest.Endless()).contentTypes("text/plain");

        MockHttpServletResponse response = run(request, new Calls());

        assertEnvelope(response, ErrorCode.PAYLOAD_TOO_LARGE);
        assertThat(request.consumed()).isEqualTo(65_537);
    }

    @Test
    void p3RejectsTwoContentTypesNoneFormMultipartAndMergePatchWhenABodyIsPresent() throws Exception {
        List<String[]> shapes = List.of(new String[] {"text/plain"}, new String[] {},
                new String[] {"application/json", "application/json"},
                new String[] {"application/x-www-form-urlencoded"}, new String[] {"multipart/form-data; boundary=x"},
                new String[] {"application/merge-patch+json"}, new String[] {"not a media type"});
        for (String[] shape : shapes) {
            Calls chain = new Calls();
            GateRequest request = new GateRequest("PUT", "/todos/not-a-uuid").declaring(4)
                    .body("{bad".getBytes(StandardCharsets.UTF_8)).contentTypes(shape);

            MockHttpServletResponse response = run(request, chain);

            assertEnvelope(response, ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            assertThat(chain.count).as(String.join(" | ", shape)).hasValue(0);
        }
    }

    @Test
    void aBogusCharsetIsAcceptedBecauseEveryContentTypeParameterIsIgnored() throws Exception {
        Calls chain = new Calls();
        GateRequest request = new GateRequest("POST", "/todos").declaring(VALID_BODY.length).body(VALID_BODY)
                .contentTypes("application/json; charset=bogus");

        MockHttpServletResponse response = run(request, chain);

        assertThat(chain.count).hasValue(1);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ pass-through

    @Test
    void aPassedRequestReachesTheChainWithItsRouteBodyAndRequestIdAndNoStreamIsReadAgain() throws Exception {
        GateRequest request = new GateRequest("PUT", "/todos/" + ID + "?q=1").declaring(VALID_BODY.length)
                .body(VALID_BODY).contentTypes("application/json");
        AtomicReference<String> mdcInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            mdcInChain.set(MDC.get("requestId"));
            assertThat(res.isCommitted()).isFalse();
            assertThat(((MockHttpServletResponse) res).getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        };

        MockHttpServletResponse response = run(request, chain);

        RouteMatch match = (RouteMatch) request.getAttribute(ApiGatewayFilter.ROUTE_ATTRIBUTE);
        RawBody body = (RawBody) request.getAttribute(ApiGatewayFilter.RAW_BODY_ATTRIBUTE);
        String requestId = (String) request.getAttribute(ApiGatewayFilter.REQUEST_ID_ATTRIBUTE);
        assertThat(match.route()).isEqualTo("/todos/{id}");
        assertThat(match.rawIdSegment()).isEqualTo(ID);
        assertThat(body.present()).isTrue();
        assertThat(body.bytes()).isEqualTo(VALID_BODY);
        assertThat(request.consumed()).isEqualTo(VALID_BODY.length);
        assertThat(UUID.fromString(requestId)).isNotNull();
        assertThat(request.getAttribute(ApiGatewayFilter.METHOD_ATTRIBUTE)).isEqualTo("PUT");
        assertThat(mdcInChain.get()).isEqualTo(requestId);
        assertThat(MDC.get("requestId")).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void downstreamCodeIsShownUtf8AsTheRequestEncodingWhateverTheClientWrote() throws Exception {
        GateRequest request = new GateRequest("POST", "/todos").declaring(VALID_BODY.length).body(VALID_BODY)
                .contentTypes("application/json; charset=");
        request.setCharacterEncoding("no-such-charset");
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            seen.set(req.getCharacterEncoding());
            req.setCharacterEncoding("ISO-8859-1");
            assertThat(req.getCharacterEncoding()).as("setting it has no effect").isEqualTo("UTF-8");
            assertThat(req.getAttribute(ApiGatewayFilter.RAW_BODY_ATTRIBUTE)).isNotNull();
        };

        run(request, chain);

        assertThat(seen.get()).isEqualTo("UTF-8");
    }

    @Test
    void aGetPassesWithAnAbsentBodyAndItsBodyIsNeverRead() throws Exception {
        GateRequest request = new GateRequest("GET", "/todos").declaring(70_000).body(new byte[70_000])
                .contentTypes("text/plain");
        Calls chain = new Calls();

        run(request, chain);

        assertThat(chain.count).hasValue(1);
        assertThat(((RawBody) request.getAttribute(ApiGatewayFilter.RAW_BODY_ATTRIBUTE)).present()).isFalse();
        assertThat(request.streamRequested()).isFalse();
    }

    @Test
    void everyRequestGetsItsOwnRequestId() throws Exception {
        GateRequest first = new GateRequest("GET", "/todos");
        GateRequest second = new GateRequest("GET", "/todos");

        run(first, new Calls());
        run(second, new Calls());

        assertThat(first.getAttribute(ApiGatewayFilter.REQUEST_ID_ATTRIBUTE))
                .isNotEqualTo(second.getAttribute(ApiGatewayFilter.REQUEST_ID_ATTRIBUTE));
    }

    // ------------------------------------------------------------------ failures

    private static FilterChain throwing(Throwable failure) {
        return (req, res) -> {
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            if (failure instanceof ServletException servlet) {
                throw servlet;
            }
            throw (IOException) failure;
        };
    }

    @Test
    void aFailureFromTheChainBecomesOne500EnvelopeAndOneErrorEntryWithoutRequestContent() throws Exception {
        GateRequest request = new GateRequest("PUT", "/todos/" + ID + "?secret=QUERYVALUE")
                .declaring(VALID_BODY.length).body(VALID_BODY).contentTypes("application/json");
        MDC.put("requestId", "outer-id");

        MockHttpServletResponse response = run(request, throwing(new IllegalStateException("boom")));

        assertEnvelope(response, ErrorCode.INTERNAL_ERROR);
        List<ILoggingEvent> errors = errorEntries();
        assertThat(errors).hasSize(1);
        ILoggingEvent entry = errors.get(0);
        String requestId = (String) request.getAttribute(ApiGatewayFilter.REQUEST_ID_ATTRIBUTE);
        assertThat(entry.getFormattedMessage()).contains(requestId, "method=PUT", "route=/todos/{id}")
                .doesNotContain(ID, "QUERYVALUE", "secret", "\"title\"");
        assertThat(entry.getMDCPropertyMap()).containsEntry("requestId", requestId);
        assertThat(entry.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
        assertThat(MDC.get("requestId")).as("the previous value is put back").isEqualTo("outer-id");
    }

    @Test
    void anErrorAndACheckedExceptionAreCaughtToo() throws Exception {
        for (Throwable failure : List.of(new LinkageError("x"), new ServletException("y"), new IOException("z"))) {
            log.list.clear();
            MockHttpServletResponse response = run(new GateRequest("GET", "/todos"), throwing(failure));

            assertEnvelope(response, ErrorCode.INTERNAL_ERROR);
            assertThat(errorEntries()).as(failure.getClass().getSimpleName()).hasSize(1);
        }
    }

    @Test
    void aFailureAfterTheResponseIsCommittedIsLoggedOnceAndNothingIsWrittenOnTop() throws Exception {
        FilterChain chain = (req, res) -> {
            res.getOutputStream().write("partial".getBytes(StandardCharsets.UTF_8));
            res.flushBuffer();
            throw new IllegalStateException("late");
        };

        MockHttpServletResponse response = run(new GateRequest("GET", "/todos"), chain);

        assertThat(response.getContentAsString()).isEqualTo("partial");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(errorEntries()).hasSize(1);
    }

    @Test
    void aHalfWrittenUncommittedResponseIsReplacedByTheEnvelope() throws Exception {
        FilterChain chain = (req, res) -> {
            ((MockHttpServletResponse) res).setStatus(201);
            ((MockHttpServletResponse) res).setHeader("X-Leftover", "1");
            res.getOutputStream().write("half".getBytes(StandardCharsets.UTF_8));
            throw new IllegalStateException("mid-way");
        };

        MockHttpServletResponse response = run(new GateRequest("GET", "/todos"), chain);

        assertEnvelope(response, ErrorCode.INTERNAL_ERROR);
        assertThat(response.getHeader("X-Leftover")).isNull();
    }

    @Test
    void headOnTheErrorDispatchGetsTheStatusAndHeadersButNoBody() throws Exception {
        GateRequest request = new GateRequest("HEAD", "/todos/" + ID);
        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/todos/" + ID);

        MockHttpServletResponse response = run(request, new Calls());

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    // ------------------------------------------------------------------ client abort

    @Test
    void aClientAbortFromTheChainIsNotAFailureNoErrorEntryAndNo500() throws Exception {
        for (Throwable abort : List.of(new ClientAbortException("Broken pipe"),
                new ServletException("wrapped", new ClientAbortException("Broken pipe")),
                new AsyncRequestNotUsableException("ServletOutputStream failed to write"))) {
            log.list.clear();
            GateRequest request = new GateRequest("GET", "/todos");
            MockHttpServletResponse response = run(request, throwing(abort));

            assertThat(ApiGatewayFilter.clientWentAway(request)).as(abort.toString()).isTrue();
            assertThat(errorEntries()).as(abort.toString()).isEmpty();
            assertThat(response.getContentAsByteArray()).isEmpty();
            assertThat(log.list).anyMatch(event -> event.getLevel() == Level.DEBUG);
            assertThat(MDC.get("requestId")).isNull();
        }
    }

    @Test
    void aBodyThatCannotBeReadIsNotAServerFailureAndIsAnsweredWithInvalidRequestBody() throws Exception {
        GateRequest request = new GateRequest("POST", "/todos").declaring(-1)
                .bodyFrom(new java.io.InputStream() {
                    @Override
                    public int read() throws IOException {
                        throw new ClientAbortException("Broken pipe");
                    }
                }).contentTypes("application/json");
        Calls chain = new Calls();

        MockHttpServletResponse response = run(request, chain);

        assertEnvelope(response, ErrorCode.INVALID_REQUEST_BODY);
        assertThat(chain.count).hasValue(0);
        assertThat(errorEntries()).isEmpty();
        assertThat(ApiGatewayFilter.clientWentAway(request)).as("recorded for the error dispatch").isTrue();
    }

    /** CR-1: an EOFException on its own or anywhere in a cause chain is a server-side failure, not a client abort. */
    @Test
    void aFailureThatMerelyWrapsAnEofExceptionIsAFailureAnswered500AndLoggedOnceAtError() throws Exception {
        for (Throwable failure : List.of(new java.io.EOFException("eof"),
                new RuntimeException("store", new java.io.EOFException("eof")),
                new RuntimeException("outer", new IllegalStateException("middle", new java.io.EOFException("eof"))))) {
            log.list.clear();

            GateRequest request = new GateRequest("GET", "/todos");
            MockHttpServletResponse response = run(request, throwing(failure));

            assertEnvelope(response, ErrorCode.INTERNAL_ERROR);
            assertThat(errorEntries()).as(failure.toString()).hasSize(1);
            assertThat(ApiGatewayFilter.clientWentAway(request)).isFalse();
        }
    }

    @Test
    void onlyTheContainerAndSpringAbortTypesAtTheTopOrDirectCauseAreAClientAbort() {
        ClientAbortException tomcatAbort = new ClientAbortException("Broken pipe");
        AsyncRequestNotUsableException springAbort = new AsyncRequestNotUsableException("not usable");

        assertThat(ApiGatewayFilter.isClientAbort(tomcatAbort)).isTrue();
        assertThat(ApiGatewayFilter.isClientAbort(springAbort)).isTrue();
        assertThat(ApiGatewayFilter.isClientAbort(new RuntimeException(tomcatAbort))).isTrue();
        assertThat(ApiGatewayFilter.isClientAbort(new RuntimeException(springAbort))).isTrue();
        // Two levels down is not "the direct cause".
        assertThat(ApiGatewayFilter.isClientAbort(new RuntimeException(new RuntimeException(tomcatAbort)))).isFalse();
        // A look-alike name is not enough, and neither is an EOFException or a plain IOException.
        assertThat(ApiGatewayFilter.isClientAbort(new IOException("plain"))).isFalse();
        assertThat(ApiGatewayFilter.isClientAbort(new java.io.EOFException("eof"))).isFalse();
        assertThat(ApiGatewayFilter.isClientAbort(new RuntimeException(new java.io.EOFException("eof")))).isFalse();
        assertThat(ApiGatewayFilter.isClientAbort(new RuntimeException("x"))).isFalse();
        assertThat(ApiGatewayFilter.isClientAbort(null)).isFalse();
    }

    // ------------------------------------------------------------------ ERROR dispatch, boundary-only mode

    private GateRequest errorDispatch(String method, String originalUri, int status) {
        GateRequest request = new GateRequest(method, "/error");
        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, originalUri);
        return request;
    }

    @Test
    void anErrorDispatchWithTraceIsAnsweredWithoutCallingTheChain() throws Exception {
        record Case(String uri, int status, ErrorCode code, String allow) {
        }
        for (Case c : List.of(new Case("/todos", 405, ErrorCode.METHOD_NOT_ALLOWED, "GET, POST"),
                new Case("/todos/" + ID, 500, ErrorCode.INTERNAL_ERROR, null),
                new Case("/todos/" + ID + "/status", 404, ErrorCode.NOT_FOUND, null),
                new Case("/nope", 404, ErrorCode.NOT_FOUND, null),
                new Case("/todos", 415, ErrorCode.UNSUPPORTED_MEDIA_TYPE, null),
                new Case("/todos", 413, ErrorCode.PAYLOAD_TOO_LARGE, null),
                new Case("/todos", 400, ErrorCode.INTERNAL_ERROR, null))) {
            Calls chain = new Calls();
            GateRequest request = errorDispatch("TRACE", c.uri(), c.status());
            request.addHeader("X-Distinctive", "ECHO-ME-NOT");

            MockHttpServletResponse response = run(request, chain);

            assertEnvelope(response, c.code());
            assertThat(response.getHeader("Allow")).as(c.toString()).isEqualTo(c.allow());
            assertThat(chain.count).as(c.toString()).hasValue(0);
            assertThat(response.getContentAsString()).doesNotContain("ECHO-ME-NOT");
            assertThat(response.getHeaderNames()).noneMatch(name -> name.equalsIgnoreCase("X-Distinctive"));
            assertThat(MDC.get("requestId")).isNull();
        }
    }

    @Test
    void anUnmappedStatusInTheShortCircuitIsLoggedOnceAtError() throws Exception {
        run(errorDispatch("TRACE", "/todos", 400), new Calls());

        assertThat(errorEntries()).hasSize(1);
        assertThat(errorEntries().get(0).getFormattedMessage()).contains("method=TRACE");
    }

    @Test
    void aMappedStatusInTheShortCircuitIsNotLogged() throws Exception {
        run(errorDispatch("TRACE", "/todos", 405), new Calls());

        assertThat(errorEntries()).isEmpty();
    }

    @Test
    void anErrorDispatchOfARoutedRequestRunsTheChainWithNosniffAndTheRestoredRequestId() throws Exception {
        GateRequest request = errorDispatch("GET", "/todos", 500);
        request.setAttribute(ApiGatewayFilter.ROUTE_ATTRIBUTE, new RouteMatch("/todos", null));
        request.setAttribute(ApiGatewayFilter.REQUEST_ID_ATTRIBUTE, "id-from-the-request-dispatch");
        AtomicReference<String> mdcInChain = new AtomicReference<>();
        AtomicReference<String> nosniffInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            mdcInChain.set(MDC.get("requestId"));
            nosniffInChain.set(((MockHttpServletResponse) res).getHeader("X-Content-Type-Options"));
        };

        run(request, chain);

        assertThat(mdcInChain.get()).isEqualTo("id-from-the-request-dispatch");
        assertThat(nosniffInChain.get()).isEqualTo("nosniff");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void anErrorDispatchWithAnAllowedMethodAndNoRecordedRouteStillReachesTheChain() throws Exception {
        GateRequest request = errorDispatch("GET", "/todos/" + ID, 404);
        Calls chain = new Calls();

        run(request, chain);

        assertThat(chain.count).hasValue(1);
        assertThat(request.getAttribute(ApiGatewayFilter.REQUEST_ID_ATTRIBUTE)).isNotNull();
    }

    @Test
    void anErrorDispatchRunsNeitherP1NorTheBodyGate() throws Exception {
        GateRequest request = errorDispatch("POST", "/todos", 500).declaring(70_000).body(new byte[70_000])
                .contentTypes("text/plain");
        request.setAttribute(ApiGatewayFilter.ROUTE_ATTRIBUTE, new RouteMatch("/todos", null));
        Calls chain = new Calls();

        MockHttpServletResponse response = run(request, chain);

        assertThat(chain.count).hasValue(1);
        assertThat(request.streamRequested()).isFalse();
        assertThat(request.getAttribute(ApiGatewayFilter.RAW_BODY_ATTRIBUTE)).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void aFailureDuringTheErrorDispatchIsOneLogEntryAndThe500Envelope() throws Exception {
        GateRequest request = errorDispatch("GET", "/todos", 500);
        request.setAttribute(ApiGatewayFilter.ROUTE_ATTRIBUTE, new RouteMatch("/todos", null));

        MockHttpServletResponse response = run(request, throwing(new IllegalStateException("in the error controller")));

        assertEnvelope(response, ErrorCode.INTERNAL_ERROR);
        assertThat(errorEntries()).hasSize(1);
        assertThat(errorEntries().get(0).getFormattedMessage()).contains("route=/todos");
    }

    @Test
    void anErrorDispatchResponseThatIsAlreadyCommittedIsLeftAlone() throws Exception {
        GateRequest request = errorDispatch("TRACE", "/todos", 405);
        MockHttpServletResponse response = new NoSendErrorResponse();
        response.getOutputStream().write("sent".getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();

        filter.doFilter(request, response, new Calls());

        assertThat(response.getContentAsString()).isEqualTo("sent");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ nested ERROR dispatch (DR-19)

    @Test
    void aNestedErrorDispatchRunsBoundaryOnlyModeInsteadOfBeingSkipped() throws Exception {
        GateRequest request = new GateRequest("GET", "/todos");
        MockHttpServletResponse response = new NoSendErrorResponse();
        Calls innerChain = new Calls();
        AtomicReference<String> mdcSeenByInner = new AtomicReference<>();
        FilterChain outerChain = (req, res) -> {
            // The error is forwarded from inside the chain: the same request object re-enters the filter as an ERROR
            // dispatch, for a request the table does not know (the route attribute is cleared to model that).
            request.removeAttribute(ApiGatewayFilter.ROUTE_ATTRIBUTE);
            request.setDispatcherType(DispatcherType.ERROR);
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/nope");
            filter.doFilter(request, res, (inReq, inRes) -> {
                mdcSeenByInner.set(MDC.get("requestId"));
                innerChain.doFilter(inReq, inRes);
            });
        };

        filter.doFilter(request, response, outerChain);

        assertThat(innerChain.count).as("the nested dispatch was answered by the boundary").hasValue(0);
        assertEnvelope(response, ErrorCode.NOT_FOUND);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void theNestedErrorDispatchKeepsTheOuterRequestIdInTheMdcAfterItReturns() throws Exception {
        GateRequest request = new GateRequest("GET", "/todos");
        AtomicReference<String> idAfterNested = new AtomicReference<>();
        FilterChain outerChain = (req, res) -> {
            request.setDispatcherType(DispatcherType.ERROR);
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
            filter.doFilter(request, res, new Calls());
            idAfterNested.set(MDC.get("requestId"));
        };

        run(request, outerChain);

        assertThat(idAfterNested.get()).isEqualTo(request.getAttribute(ApiGatewayFilter.REQUEST_ID_ATTRIBUTE));
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void theErrorDispatchIsNotSkippedByTheOncePerRequestBase() throws Exception {
        // shouldNotFilterErrorDispatch() is protected: observe it through behaviour. If the base class skipped the
        // ERROR dispatch, the TRACE would go straight to the chain and no 405 envelope would be written.
        Calls chain = new Calls();

        MockHttpServletResponse response = run(errorDispatch("TRACE", "/todos", 405), chain);

        assertThat(chain.count).hasValue(0);
        assertThat(response.getStatus()).isEqualTo(405);
    }
}
