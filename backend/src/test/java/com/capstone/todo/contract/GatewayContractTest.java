package com.capstone.todo.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.accept.HeaderContentNegotiationStrategy;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.filter.FormContentFilter;
import org.springframework.web.filter.HiddenHttpMethodFilter;

import com.capstone.todo.config.WebConfig;
import com.capstone.todo.domain.Task;
import com.capstone.todo.domain.TaskRepository;
import com.capstone.todo.domain.TaskStatus;
import com.capstone.todo.support.EvidenceLog;
import com.capstone.todo.support.RawHttp;
import com.capstone.todo.support.TestDataDir;
import com.capstone.todo.web.gate.ApiGatewayFilter;
import com.capstone.todo.web.response.ApiResponses;
import com.capstone.todo.web.response.ErrorCode;

/**
 * CP-B: the gateway filter, Tomcat and Spring MVC configuration against a real Tomcat on a random port, with a
 * test-only stub handler behind the filter (T-12; matrix rows T-P1a, T-P1b, T-P2, T-P2b, T-P3, T-RT (b) and (c), T-HDR
 * for the rejection responses). Every rejection is asserted for status, the fixed envelope bytes, {@code nosniff}, the
 * exact content type, no {@code Access-Control-*} header, that the stub was not reached and that the stored tasks are
 * unchanged. What the server really did is written to {@code target/cp-b-evidence.txt}.
 *
 * <p>Not covered here (T-13 and later, with the real controller and store): P4 to P7, the error controller, the
 * Accept header against a real controller, logging of the advice path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(StubRoutesConfig.class)
class GatewayContractTest {

    private static final Path DATA_FILE = TestDataDir.newDataFile(GatewayContractTest.class);
    private static final EvidenceLog EVIDENCE = new EvidenceLog();
    private static final String ID = "0b8f6f4e-3a35-4d0c-9d41-0d9f3c2a7b11";
    private static final String DISTINCTIVE = "ECHO-ME-NOT-9f3a";
    private static final byte[] VALID_TASK = "{\"title\":\"x\"}".getBytes(StandardCharsets.UTF_8);

    @DynamicPropertySource
    static void dataFile(DynamicPropertyRegistry registry) {
        registry.add("todo.data-file", DATA_FILE::toString);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ApiResponses responses;

    @Autowired
    private TaskRepository repository;

    @Autowired
    private StubRoutesConfig.StubRoutes stub;

    @AfterAll
    static void writeEvidence() {
        EVIDENCE.writeTo("cp-b-evidence.txt");
    }

    @BeforeEach
    void seedOneTask() {
        if (repository.findAllOrdered().isEmpty()) {
            Instant now = Instant.parse("2026-01-01T00:00:00Z");
            repository.insert(new Task(Task.UNASSIGNED_SEQ, UUID.fromString(ID), "seed", "", TaskStatus.PENDING, now, now));
        }
    }

    // ---------------------------------------------------------------- helpers

    private record Snapshot(List<Task> tasks, List<String> stubCalls) {
    }

    private Snapshot snapshot() {
        return new Snapshot(repository.findAllOrdered(), stub.calls());
    }

    private static byte[] filled(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) 'x');
        return bytes;
    }

    private static String describe(RawHttp.Response response) {
        String type = response.header("Content-Type");
        String allow = response.header("Allow");
        return response.status() + (allow == null ? "" : " Allow=" + allow) + (type == null ? "" : " " + type)
                + " body=" + response.body().length + "B";
    }

    /** Sends the request and asserts the rejection contract; returns the response for extra assertions. */
    private RawHttp.Response rejected(String test, String shape, RawHttp.Request request, ErrorCode code, String allow) {
        Snapshot before = snapshot();
        RawHttp.Response response = request.send(port);
        EVIDENCE.row(test, shape, code.status() + " " + code.code() + (allow == null ? "" : " Allow=" + allow),
                describe(response) + " " + new String(response.body(), StandardCharsets.UTF_8));

        assertThat(response.status()).as(test + " " + shape).isEqualTo(code.status());
        if (shape.startsWith("HEAD ")) {
            assertThat(response.body()).as("HEAD has no body").isEmpty();
        } else {
            assertThat(response.body()).as(test + " " + shape + " body").isEqualTo(responses.fixedError(code));
        }
        assertThat(response.headers("X-Content-Type-Options")).as("nosniff").containsExactly("nosniff");
        assertThat(response.headers("Content-Type")).as("content type").containsExactly("application/json");
        assertThat(response.headers("Allow")).as("Allow").isEqualTo(allow == null ? List.of() : List.of(allow));
        assertThat(response.headers()).noneMatch(h -> h[0].toLowerCase().startsWith("access-control-"));
        assertThat(snapshot()).as("nothing reached the handler and nothing changed").isEqualTo(before);
        return response;
    }

    /** Sends the request and asserts that the stub was reached exactly once and the filter's headers are there. */
    private RawHttp.Response passed(String test, String shape, RawHttp.Request request) {
        Snapshot before = snapshot();
        RawHttp.Response response = request.send(port);
        EVIDENCE.row(test, shape, "200 from the stub handler",
                describe(response) + " route=" + response.header("X-Stub-Route") + " id=" + response.header("X-Stub-Id")
                        + " bodyLen=" + response.header("X-Stub-Body-Length"));

        assertThat(response.status()).as(test + " " + shape).isEqualTo(200);
        assertThat(response.headers("X-Content-Type-Options")).containsExactly("nosniff");
        assertThat(response.headers("Content-Type")).containsExactly("application/json");
        assertThat(stub.calls()).hasSize(before.stubCalls().size() + 1);
        assertThat(repository.findAllOrdered()).isEqualTo(before.tasks());
        return response;
    }

    private RawHttp.Request post(String target, String contentType, byte[] body) {
        RawHttp.Request request = RawHttp.request("POST", target).body(body);
        return contentType == null ? request : request.header("Content-Type", contentType);
    }

    // ---------------------------------------------------------------- T-P1a

    @Test
    void p1aUndefinedPathsAreNotFoundWithTheEnvelope() {
        byte[] seventyKb = filled(70_000);
        rejected("T-P1a", "POST /todos/ text/plain 70000B", post("/todos/", "text/plain", seventyKb), ErrorCode.NOT_FOUND, null);
        rejected("T-P1a", "GET //todos", RawHttp.request("GET", "//todos"), ErrorCode.NOT_FOUND, null);
        rejected("T-P1a", "GET /todos;x=1", RawHttp.request("GET", "/todos;x=1"), ErrorCode.NOT_FOUND, null);
        rejected("T-P1a", "GET /%74odos", RawHttp.request("GET", "/%74odos"), ErrorCode.NOT_FOUND, null);
        rejected("T-P1a", "PATCH /todos/<id>/%73tatus",
                RawHttp.request("PATCH", "/todos/" + ID + "/%73tatus").header("Content-Type", "application/json")
                        .body("{\"status\":\"Completed\"}"), ErrorCode.NOT_FOUND, null);
        rejected("T-P1a", "GET /error", RawHttp.request("GET", "/error"), ErrorCode.NOT_FOUND, null);
        rejected("T-P1a", "GET /", RawHttp.request("GET", "/"), ErrorCode.NOT_FOUND, null);
        rejected("T-P1a/V-17", "GET /favicon.ico", RawHttp.request("GET", "/favicon.ico"), ErrorCode.NOT_FOUND, null);
        rejected("T-P1a/V-17", "GET /static/app.js", RawHttp.request("GET", "/static/app.js"), ErrorCode.NOT_FOUND, null);
        rejected("T-P1a", "GET /todos/<id>/status/", RawHttp.request("GET", "/todos/" + ID + "/status/"),
                ErrorCode.NOT_FOUND, null);
        rejected("T-P1a", "GET /todos/", RawHttp.request("GET", "/todos/"), ErrorCode.NOT_FOUND, null);
    }

    @Test
    void v4TheRequestUriIsRawAndKeepsParametersAndLosesTheQuery() {
        RawHttp.Response withQuery = passed("V-4", "GET /todos?x=/nope%2Fy&z=1", RawHttp.request("GET", "/todos?x=/nope%2Fy&z=1"));
        RawHttp.Response rawId = passed("V-4", "GET /todos/%61bc-raw", RawHttp.request("GET", "/todos/%61bc-raw"));
        RawHttp.Response encodedSlash = passed("V-2", "GET /todos/a%2Fb", RawHttp.request("GET", "/todos/a%2Fb"));
        RawHttp.Response encodedBackslash = passed("V-3", "GET /todos/a%5Cb", RawHttp.request("GET", "/todos/a%5Cb"));
        RawHttp.Response doubleEncoded = passed("V-4", "GET /todos/%2530abc", RawHttp.request("GET", "/todos/%2530abc"));

        assertThat(withQuery.header("X-Stub-Route")).isEqualTo("/todos");
        assertThat(rawId.header("X-Stub-Id")).as("raw, undecoded").isEqualTo("%61bc-raw");
        assertThat(encodedSlash.header("X-Stub-Id")).as("%2F passes through (V-2)").isEqualTo("a%2Fb");
        assertThat(encodedBackslash.header("X-Stub-Id")).as("%5C passes through (V-3)").isEqualTo("a%5Cb");
        assertThat(doubleEncoded.header("X-Stub-Id")).isEqualTo("%2530abc");
        // The semicolon case in p1a (404) shows Tomcat keeps ";x=1" in getRequestURI().
    }

    @Test
    void v3PercentSequencesTomcatMayRefuseAreRecordedNotAsserted() {
        List<String> targets = List.of("/todos/%zz", "/todos/%FF", "/todos/%", "/todos/a%00b", "/todos/%C0%AF",
                "/todos/a%20b", "/todos/%E2%82");
        for (String target : targets) {
            RawHttp.Response response = RawHttp.request("GET", target).send(port);
            EVIDENCE.row("V-3 (observed)", "GET " + target, "recorded, not asserted",
                    describe(response) + " " + new String(response.body(), StandardCharsets.UTF_8)
                            + " stubId=" + response.header("X-Stub-Id"));
            assertThat(response.status()).isBetween(200, 499);
        }
    }

    // ---------------------------------------------------------------- T-P1b

    @Test
    void p1bEveryOtherMethodTokenOnADefinedPathIsMethodNotAllowedWithTheRoutesAllow() {
        record Route(String path, String allow, List<String> notAllowed) {
        }
        List<Route> routes = List.of(
                new Route("/todos", "GET, POST", List.of("OPTIONS", "HEAD", "TRACE", "get", "FOO", "PUT", "PATCH", "DELETE")),
                new Route("/todos/" + ID, "GET, PUT, DELETE", List.of("OPTIONS", "HEAD", "TRACE", "get", "FOO", "POST", "PATCH")),
                new Route("/todos/" + ID + "/status", "PATCH", List.of("OPTIONS", "HEAD", "TRACE", "get", "FOO", "GET", "POST", "PUT", "DELETE")));
        for (Route route : routes) {
            for (String method : route.notAllowed()) {
                rejected("T-P1b", method + " " + route.path(), RawHttp.request(method, route.path()),
                        ErrorCode.METHOD_NOT_ALLOWED, route.allow());
            }
        }
    }

    @Test
    void p1bDeleteOnTheCollectionWithAFatBodyIsStillMethodNotAllowed() {
        rejected("T-P1b", "DELETE /todos text/plain 70000B", RawHttp.request("DELETE", "/todos")
                .header("Content-Type", "text/plain").body(filled(70_000)), ErrorCode.METHOD_NOT_ALLOWED, "GET, POST");
    }

    @Test
    void p1bAPreflightLooksLikeAnyOtherOptionsAndCarriesNoCorsHeaders() {
        RawHttp.Response response = rejected("T-P1b/T-CORS", "OPTIONS /todos with Origin and Access-Control-Request-Method",
                RawHttp.request("OPTIONS", "/todos").header("Origin", "https://example.test")
                        .header("Access-Control-Request-Method", "POST"), ErrorCode.METHOD_NOT_ALLOWED, "GET, POST");

        assertThat(response.headers()).noneMatch(h -> h[0].toLowerCase().startsWith("access-control-"));
        assertThat(response.header("Vary")).as("no CORS Vary either").isNull();
    }

    @Test
    void p1bTraceOnADefinedPathIsRejectedInTheEnvelopeAndNeverEchoed_v1() {
        RawHttp.Response response = rejected("T-P1b/V-1", "TRACE /todos with X-Distinctive",
                RawHttp.request("TRACE", "/todos").header("X-Distinctive", DISTINCTIVE), ErrorCode.METHOD_NOT_ALLOWED,
                "GET, POST");

        assertThat(response.bodyText()).doesNotContain(DISTINCTIVE).doesNotContain("X-Distinctive");
        assertThat(response.headers()).noneMatch(h -> h[0].contains("Distinctive") || h[1].contains(DISTINCTIVE));
        assertThat(response.header("Content-Type")).isNotEqualTo("message/http");

        RawHttp.Response item = rejected("T-P1b/V-1", "TRACE /todos/<id> with X-Distinctive",
                RawHttp.request("TRACE", "/todos/" + ID).header("X-Distinctive", DISTINCTIVE),
                ErrorCode.METHOD_NOT_ALLOWED, "GET, PUT, DELETE");
        assertThat(item.bodyText()).doesNotContain(DISTINCTIVE);
    }

    @Test
    void p1bTraceAndHeadOnAnUndefinedPathAreNotFound() {
        RawHttp.Response trace = rejected("T-P1b/V-1", "TRACE /nope with X-Distinctive",
                RawHttp.request("TRACE", "/nope").header("X-Distinctive", DISTINCTIVE), ErrorCode.NOT_FOUND, null);
        assertThat(trace.bodyText()).doesNotContain(DISTINCTIVE);
        rejected("T-P1b", "HEAD /nope", RawHttp.request("HEAD", "/nope"), ErrorCode.NOT_FOUND, null);
    }

    @Test
    void p1bHeadGetsStatusAndHeadersButNoBody_v6() {
        RawHttp.Response response = rejected("T-P1b/V-6", "HEAD /todos", RawHttp.request("HEAD", "/todos"),
                ErrorCode.METHOD_NOT_ALLOWED, "GET, POST");

        assertThat(response.body()).isEmpty();
        EVIDENCE.note("V-6: HEAD /todos answered with status 405, Content-Length header = "
                + response.header("Content-Length") + ", no body bytes received");
    }

    // ---------------------------------------------------------------- T-P2, T-P2b

    @Test
    void p2ABodyOfExactlyTheLimitPassesAndOneByteMoreIsPayloadTooLarge() {
        RawHttp.Response limit = passed("T-P2/T-B1", "POST /todos application/json 65536B Content-Length",
                post("/todos", "application/json", paddedJson(65_536)));
        assertThat(limit.header("X-Stub-Body-Length")).isEqualTo("65536");
        assertThat(limit.header("X-Stub-Body-Present")).isEqualTo("true");

        rejected("T-P2", "POST /todos application/json 65537B Content-Length",
                post("/todos", "application/json", paddedJson(65_537)), ErrorCode.PAYLOAD_TOO_LARGE, null);
    }

    private static byte[] paddedJson(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) ' ');
        byte[] json = "{\"title\":\"x\"}".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(json, 0, bytes, 0, json.length);
        return bytes;
    }

    @Test
    void p2AnOversizedPatchIsRejectedBeforeItsMediaTypeAndItsIdAreLookedAt_contentLengthAndChunked_v5() {
        byte[] oversize = filled(65_537);
        RawHttp.Response declared = rejected("T-P2", "PATCH /todos/not-a-uuid/status text/plain 65537B Content-Length",
                RawHttp.request("PATCH", "/todos/not-a-uuid/status").header("Content-Type", "text/plain").body(oversize),
                ErrorCode.PAYLOAD_TOO_LARGE, null);
        RawHttp.Response chunked = rejected("T-P2", "PATCH /todos/not-a-uuid/status text/plain 65537B chunked (2 chunks)",
                RawHttp.request("PATCH", "/todos/not-a-uuid/status").header("Content-Type", "text/plain")
                        .chunked(Arrays.copyOfRange(oversize, 0, 40_000), Arrays.copyOfRange(oversize, 40_000, 65_537)),
                ErrorCode.PAYLOAD_TOO_LARGE, null);

        EVIDENCE.note("V-5: after the early 413 the Connection header was '" + declared.header("Connection")
                + "' (Content-Length body) and '" + chunked.header("Connection") + "' (chunked); the client received "
                + "the full envelope in both cases");
    }

    @Test
    void p2AChunkedBodyAtTheLimitPassesAndOneByteMoreIsRejected() {
        byte[] atLimit = paddedJson(65_536);
        RawHttp.Response ok = passed("T-P2/T-B1", "POST /todos application/json 65536B chunked",
                RawHttp.request("POST", "/todos").header("Content-Type", "application/json")
                        .chunked(Arrays.copyOfRange(atLimit, 0, 30_000), Arrays.copyOfRange(atLimit, 30_000, 65_536)));
        assertThat(ok.header("X-Stub-Body-Length")).isEqualTo("65536");

        rejected("T-P2", "POST /todos application/json 65537B chunked",
                RawHttp.request("POST", "/todos").header("Content-Type", "application/json")
                        .chunked(paddedJson(65_537)), ErrorCode.PAYLOAD_TOO_LARGE, null);
    }

    @Test
    void p2AnUnderstatedLengthCannotSmuggleMoreThanTheCapIntoTheFilter() {
        // Content-Length says 10 but the connection carries more; Tomcat only hands over 10 bytes, so the gate sees 10.
        RawHttp.Response response = RawHttp.request("POST", "/todos").header("Content-Type", "application/json")
                .header("Content-Length", "10").body(paddedJson(70_000)).send(port);
        EVIDENCE.row("T-P2 (observed)", "POST /todos Content-Length: 10 but 70000B sent", "recorded",
                describe(response) + " bodyLen=" + response.header("X-Stub-Body-Length"));

        assertThat(response.status()).isIn(200, 400, 413, 415);
        if (response.status() == 200) {
            assertThat(response.header("X-Stub-Body-Length")).isEqualTo("10");
        }
    }

    /** Raw socket: headers first, the body is withheld, and the first thing the server says is recorded. */
    private record WithheldBody(boolean interimSent, int status, byte[] body, String connection, long millis) {
    }

    private WithheldBody expectContinueWithoutSendingTheBody(String target, int declaredLength, String contentType) {
        long started = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 5_000);
            socket.setSoTimeout(10_000);
            String head = "POST " + target + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nConnection: close\r\n"
                    + "Content-Type: " + contentType + "\r\nContent-Length: " + declaredLength
                    + "\r\nExpect: 100-continue\r\n\r\n";
            socket.getOutputStream().write(head.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[4096];
            try {
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    received.write(buffer, 0, n);
                }
            } catch (SocketTimeoutException timeout) {
                // whatever arrived is parsed below
            }
            String text = received.toString(StandardCharsets.ISO_8859_1);
            boolean interim = text.startsWith("HTTP/1.1 100");
            String finalPart = interim ? text.substring(text.indexOf("\r\n\r\n") + 4) : text;
            int headEnd = finalPart.indexOf("\r\n\r\n");
            String finalHead = finalPart.substring(0, headEnd);
            int status = Integer.parseInt(finalHead.substring(9, 12));
            String connection = null;
            for (String line : finalHead.split("\r\n")) {
                if (line.toLowerCase().startsWith("connection:")) {
                    connection = line.substring(11).trim();
                }
            }
            byte[] body = finalPart.substring(headEnd + 4).getBytes(StandardCharsets.ISO_8859_1);
            return new WithheldBody(interim, status, body, connection, (System.nanoTime() - started) / 1_000_000);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void p2AnOversizedContentLengthWithExpect100ContinueIsRejectedWithoutTheBody_v8() {
        Snapshot before = snapshot();

        WithheldBody result = expectContinueWithoutSendingTheBody("/todos", 70_000, "application/json");

        EVIDENCE.row("T-P2/V-8", "POST /todos Expect: 100-continue, Content-Length 70000, body withheld",
                "413 PAYLOAD_TOO_LARGE envelope; whether 100 Continue was sent first is recorded",
                result.status() + " interim100=" + result.interimSent() + " Connection=" + result.connection()
                        + " in " + result.millis() + " ms, " + new String(result.body(), StandardCharsets.UTF_8));
        assertThat(result.status()).isEqualTo(413);
        assertThat(result.body()).isEqualTo(responses.fixedError(ErrorCode.PAYLOAD_TOO_LARGE));
        assertThat(snapshot()).isEqualTo(before);
        EVIDENCE.note("V-8: for an oversized Content-Length the server sent 100 Continue before the 413: "
                + result.interimSent());
    }

    @Test
    void p2bASmallValidBodyWithExpect100ContinueIsPassed_v8() {
        RawHttp.Response response = passed("T-P2b/V-8", "POST /todos Expect: 100-continue, small JSON body",
                RawHttp.request("POST", "/todos").header("Content-Type", "application/json").body(VALID_TASK)
                        .expectContinue());

        assertThat(response.header("X-Stub-Body-Length")).isEqualTo(String.valueOf(VALID_TASK.length));
    }

    // ---------------------------------------------------------------- T-P3

    @Test
    void p3EveryOtherMediaTypeShapeIsUnsupportedMediaTypeBeforeTheBodyIsLookedAt_v16() {
        byte[] bad = "{bad".getBytes(StandardCharsets.UTF_8);
        String target = "/todos/not-a-uuid";
        rejected("T-P3", "PUT text/plain {bad", RawHttp.request("PUT", target).header("Content-Type", "text/plain").body(bad),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE, null);
        rejected("T-P3", "PUT no Content-Type {bad", RawHttp.request("PUT", target).body(bad),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE, null);
        rejected("T-P3/V-16", "PUT two Content-Type headers (application/json, application/json) {bad",
                RawHttp.request("PUT", target).header("Content-Type", "application/json")
                        .header("Content-Type", "application/json").body(bad), ErrorCode.UNSUPPORTED_MEDIA_TYPE, null);
        rejected("T-P3/V-14", "PUT application/x-www-form-urlencoded a=b",
                RawHttp.request("PUT", target).header("Content-Type", "application/x-www-form-urlencoded").body("a=b"),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE, null);
        rejected("T-P3/V-16", "PUT multipart/form-data",
                RawHttp.request("PUT", target).header("Content-Type", "multipart/form-data; boundary=zz")
                        .body("--zz\r\nContent-Disposition: form-data; name=\"a\"\r\n\r\nb\r\n--zz--\r\n"),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE, null);
        rejected("T-P3", "PUT application/merge-patch+json {bad",
                RawHttp.request("PUT", target).header("Content-Type", "application/merge-patch+json").body(bad),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE, null);
        rejected("T-P3", "PUT Content-Type: not a media type {bad",
                RawHttp.request("PUT", target).header("Content-Type", "not a media type").body(bad),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE, null);
        rejected("T-P3", "POST /todos application/jsonx", post("/todos", "application/jsonx", VALID_TASK),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE, null);
        rejected("T-P3", "PATCH status text/plain", RawHttp.request("PATCH", "/todos/" + ID + "/status")
                .header("Content-Type", "text/plain").body("{\"status\":\"Completed\"}"),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE, null);
    }

    @Test
    void p3EveryParameterIsIgnoredSoABogusCharsetIsAccepted_s2_4() {
        for (String type : List.of("application/json; charset=bogus", "application/json; charset=", "APPLICATION/JSON",
                "application/json;charset=utf-16", "application/json ; a=b")) {
            RawHttp.Response response = passed("T-P3 (S2-4)", "POST /todos Content-Type: " + type,
                    post("/todos", type, VALID_TASK));
            assertThat(response.header("X-Stub-Body-Length")).isEqualTo(String.valueOf(VALID_TASK.length));
        }
    }

    @Test
    void anIllegalCharsetNameInAContentTypeNeverBecomesA500OnAnyMethod_foundAtCpB() {
        // Found at CP-B: "charset=" (empty) made Spring's response writer fail with IllegalCharsetNameException when it
        // decoded the request path with the request's character encoding.
        for (String type : List.of("application/json; charset=", "text/plain; charset=", "application/json; charset=a b",
                "application/json; charset=\"", "application/json; charset=\"\"")) {
            passed("T-P3 (S2-4)", "GET /todos Content-Type: " + type, RawHttp.request("GET", "/todos").header("Content-Type", type));
            passed("T-P3 (S2-4)", "DELETE /todos/<id> Content-Type: " + type,
                    RawHttp.request("DELETE", "/todos/" + ID).header("Content-Type", type));
        }
    }

    @Test
    void p3IsSkippedWhenThereIsNoBody() {
        RawHttp.Response noType = passed("T-P3 (A-11)", "POST /todos Content-Length: 0, no Content-Type",
                RawHttp.request("POST", "/todos").header("Content-Length", "0"));
        assertThat(noType.header("X-Stub-Body-Present")).isEqualTo("false");
        RawHttp.Response textType = passed("T-P3 (A-11)", "PUT /todos/<id> Content-Length: 0, text/plain",
                RawHttp.request("PUT", "/todos/" + ID).header("Content-Type", "text/plain").header("Content-Length", "0"));
        assertThat(textType.header("X-Stub-Body-Present")).isEqualTo("false");
    }

    @Test
    void aGetOrDeleteBodyIsNeverReadWhateverItsTypeAndSize() {
        RawHttp.Response get = passed("T-P3/A-8", "GET /todos text/plain 70000B",
                RawHttp.request("GET", "/todos").header("Content-Type", "text/plain").body(filled(70_000)));
        assertThat(get.header("X-Stub-Body-Present")).isEqualTo("false");
        RawHttp.Response delete = passed("T-P3/A-8", "DELETE /todos/<id> text/plain 70000B",
                RawHttp.request("DELETE", "/todos/" + ID).header("Content-Type", "text/plain").body(filled(70_000)));
        assertThat(delete.header("X-Stub-Body-Present")).isEqualTo("false");
    }

    @Test
    void theAcceptHeaderNeverChangesAPassedRequest() {
        passed("T-ACC (stub)", "GET /todos Accept: text/html", RawHttp.request("GET", "/todos").header("Accept", "text/html"));
        passed("T-ACC (stub)", "GET /todos Accept: application/xml",
                RawHttp.request("GET", "/todos").header("Accept", "application/xml"));
    }

    // ---------------------------------------------------------------- T-RT (b), (c) and the configuration

    private record Registration(String name, int order, String filterClass) {
    }

    private List<Registration> bootFilterRegistrationsInOrder() {
        List<Registration> result = new ArrayList<>();
        for (ServletContextInitializer initializer : new ServletContextInitializerBeans(context)) {
            if (initializer instanceof FilterRegistrationBean<?> registration) {
                result.add(new Registration(registration.getFilterName() == null ? "?" : registration.getFilterName(),
                        registration.getOrder(), registration.getFilter().getClass().getName()));
            }
        }
        return result;
    }

    @Test
    void rtBTheGatewayIsFirstAmongBootsRegistrationsWithNoTieAndNoCharacterEncodingFilter_v9() {
        List<Registration> registrations = bootFilterRegistrationsInOrder();
        EVIDENCE.row("T-RT (b)/V-9", "Boot filter registrations in sorted order", "gateway first, no order <= its order",
                registrations.toString());

        assertThat(registrations).isNotEmpty();
        assertThat(registrations.get(0).filterClass()).isEqualTo(ApiGatewayFilter.class.getName());
        assertThat(registrations.get(0).order()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        assertThat(registrations.subList(1, registrations.size())).allSatisfy(other -> assertThat(other.order())
                .as(other.toString()).isGreaterThan(Ordered.HIGHEST_PRECEDENCE));
        assertThat(registrations).noneMatch(r -> r.filterClass().contains("CharacterEncodingFilter"));
        assertThat(context.getBeansOfType(CharacterEncodingFilter.class)).isEmpty();
    }

    @Test
    void rtCTheFormContentAndHiddenMethodFiltersAreAbsent_v14_v15() {
        assertThat(context.getBeansOfType(FormContentFilter.class)).isEmpty();
        assertThat(context.getBeansOfType(HiddenHttpMethodFilter.class)).isEmpty();
        assertThat(bootFilterRegistrationsInOrder()).noneMatch(r -> r.filterClass().contains("FormContentFilter")
                || r.filterClass().contains("HiddenHttpMethodFilter"));
    }

    private Tomcat tomcat() {
        return ((TomcatWebServer) ((WebServerApplicationContext) context).getWebServer()).getTomcat();
    }

    @Test
    void theRealTomcatFilterChainIsRecordedWithAnyFilterTheContainerAddedItself_v9() {
        StandardContext servletContext = (StandardContext) tomcat().getHost().findChildren()[0];
        List<String> chain = new ArrayList<>();
        for (FilterMap map : servletContext.findFilterMaps()) {
            chain.add(map.getFilterName() + " [" + servletContext.findFilterDef(map.getFilterName()).getFilterClass()
                    + "] urls=" + Arrays.toString(map.getURLPatterns()) + " dispatchers="
                    + Arrays.toString(map.getDispatcherNames()));
        }
        EVIDENCE.row("T-RT (b)/V-9/DR-19", "Tomcat StandardContext.findFilterMaps() in order", "gateway first; container-added "
                + "filters (WsFilter) are outside the proof", String.join(" || ", chain));

        assertThat(chain).isNotEmpty();
        assertThat(chain.get(0)).startsWith(WebConfig.GATEWAY_FILTER_NAME).contains("urls=[/*]");
        FilterMap gateway = servletContext.findFilterMaps()[0];
        assertThat(gateway.getDispatcherNames()).containsExactlyInAnyOrder("REQUEST", "ERROR");
        assertThat(chain).noneMatch(entry -> entry.contains("CharacterEncodingFilter"));
    }

    @Test
    void theConnectorPassesTraceAndEncodedSlashesAndTheErrorReportValveIsQuiet_v1_v2_v3_v7() {
        Connector connector = tomcat().getConnector();
        List<Valve> reportValves = Arrays.stream(tomcat().getHost().getPipeline().getValves())
                .filter(valve -> valve instanceof ErrorReportValve).toList();
        EVIDENCE.row("T-ERR (config)/V-1/V-2/V-3/V-7", "live Tomcat connector and host valves",
                "allowTrace true, solidus passthrough, reverse solidus passthrough, one quiet ErrorReportValve",
                "allowTrace=" + connector.getAllowTrace() + " encodedSolidus=" + connector.getEncodedSolidusHandling()
                        + " encodedReverseSolidus=" + connector.getEncodedReverseSolidusHandling() + " reportValves="
                        + reportValves.stream().map(v -> ((ErrorReportValve) v).isShowReport() + "/"
                                + ((ErrorReportValve) v).isShowServerInfo()).toList());

        assertThat(connector.getAllowTrace()).isTrue();
        assertThat(connector.getEncodedSolidusHandling()).isEqualTo("passthrough");
        assertThat(connector.getEncodedReverseSolidusHandling()).isEqualTo("passthrough");
        assertThat(reportValves).hasSize(1);
        assertThat(((ErrorReportValve) reportValves.get(0)).isShowReport()).isFalse();
        assertThat(((ErrorReportValve) reportValves.get(0)).isShowServerInfo()).isFalse();
    }

    @Test
    void theContentNegotiationManagerHasNoAcceptHeaderStrategy_v12() {
        ContentNegotiationManager manager = context.getBean("mvcContentNegotiationManager", ContentNegotiationManager.class);

        EVIDENCE.row("V-12", "mvcContentNegotiationManager strategies", "no HeaderContentNegotiationStrategy",
                manager.getStrategies().toString());
        assertThat(manager.getStrategies()).noneMatch(HeaderContentNegotiationStrategy.class::isInstance);
    }

    @Test
    void noCorsHeaderAppearsOnAPassedRequestEither() {
        RawHttp.Response response = passed("T-CORS (stub)", "GET /todos with Origin",
                RawHttp.request("GET", "/todos").header("Origin", "https://example.test"));

        assertThat(response.headers()).noneMatch(h -> h[0].toLowerCase().startsWith("access-control-"));
    }
}
