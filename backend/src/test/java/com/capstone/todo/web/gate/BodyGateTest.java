package com.capstone.todo.web.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.capstone.todo.support.GateRequest;
import com.capstone.todo.web.response.ApiException;
import com.capstone.todo.web.response.ErrorCode;

/**
 * P2 and P3 at unit level (T-P2, T-P3, D-2, D-3, D-4, A-11, AC-16, AC-24, NFR-6). The request is a
 * {@link GateRequest} (a {@code MockHttpServletRequest}) whose body stream counts what was read, so "unread" and
 * "read at most 65,537 bytes" are asserted rather than assumed. Chunked bodies (no Content-Length) are modelled by a declared length of -1.
 * Real Tomcat (chunking, 100-continue, two real Content-Type headers) is for T-12.
 */
class BodyGateTest {

    private static final int LIMIT = BodyGate.MAX_BODY_BYTES;
    private static final String JSON = "application/json";

    private final BodyGate gate = new BodyGate();

    private static GateRequest todosRequest(String method) {
        return new GateRequest(method, "/todos");
    }

    private static byte[] bytesOfSize(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) ' ');
        byte[] start = "{\"title\":\"x\"}".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(start, 0, bytes, 0, Math.min(start.length, size));
        return bytes;
    }

    private static void assertRejected(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.errorCode()).isEqualTo(code);
            assertThat(e.details()).isEmpty();
            assertThat(e.getCause()).isNull();
            assertThat(e.getMessage()).isEqualTo(code.defaultMessage());
        });
    }

    private void assertCode(GateRequest request, ErrorCode code) {
        assertRejected(() -> {
            try {
                gate.check(request);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }, code);
    }

    // ---- GET, DELETE and every other method: never read ---------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "DELETE", "HEAD", "OPTIONS", "TRACE", "get", "post", "FOO"})
    void aMethodWithoutABodyRuleIsNeverReadAndNeverRejected(String method) throws IOException {
        GateRequest request = todosRequest(method).declaring(70_000).body(bytesOfSize(70_000))
                .contentTypes("text/plain", "text/plain");

        RawBody body = gate.check(request);

        assertThat(body.present()).isFalse();
        assertThat(body.bytes()).isEmpty();
        assertThat(request.streamRequested()).isFalse();
        assertThat(request.consumed()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "DELETE"})
    void aGetOrDeleteWithAnEndlessBodyReturnsAtOnce(String method) throws IOException {
        GateRequest request = todosRequest(method).bodyFrom(new GateRequest.Endless());

        assertThat(gate.check(request).present()).isFalse();
        assertThat(request.consumed()).isZero();
    }

    // ---- P2: size ---------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}, Content-Length 65,536")
    @ValueSource(strings = {"POST", "PUT", "PATCH"})
    void aBodyOfExactlyTheLimitIsAcceptedWithAContentLength(String method) throws IOException {
        byte[] sent = bytesOfSize(LIMIT);
        GateRequest request = todosRequest(method).declaring(LIMIT).body(sent).contentTypes(JSON);

        RawBody body = gate.check(request);

        assertThat(body.present()).isTrue();
        assertThat(body.bytes()).isEqualTo(sent);
    }

    @ParameterizedTest(name = "{0}, chunked 65,536")
    @ValueSource(strings = {"POST", "PUT", "PATCH"})
    void aChunkedBodyOfExactlyTheLimitIsAccepted(String method) throws IOException {
        byte[] sent = bytesOfSize(LIMIT);
        GateRequest request = todosRequest(method).declaring(-1).body(sent).contentTypes(JSON);

        RawBody body = gate.check(request);

        assertThat(body.present()).isTrue();
        assertThat(body.bytes()).isEqualTo(sent);
    }

    @ParameterizedTest(name = "{0}, Content-Length 65,537")
    @ValueSource(strings = {"POST", "PUT", "PATCH"})
    void aContentLengthOverTheLimitIsRejectedWithoutReadingAByte(String method) {
        GateRequest request = todosRequest(method).declaring(LIMIT + 1L).body(bytesOfSize(LIMIT + 1))
                .contentTypes(JSON);

        assertCode(request, ErrorCode.PAYLOAD_TOO_LARGE);

        assertThat(request.streamRequested()).isFalse();
        assertThat(request.consumed()).isZero();
    }

    @Test
    void aHugeContentLengthIsRejectedWithoutReadingAByte() {
        GateRequest request = todosRequest("POST").declaring(Long.MAX_VALUE).bodyFrom(new GateRequest.Endless())
                .contentTypes(JSON);

        assertCode(request, ErrorCode.PAYLOAD_TOO_LARGE);

        assertThat(request.streamRequested()).isFalse();
        assertThat(request.consumed()).isZero();
    }

    @ParameterizedTest(name = "{0}, chunked 65,537")
    @ValueSource(strings = {"POST", "PUT", "PATCH"})
    void aChunkedBodyOneByteOverTheLimitIsRejected(String method) {
        GateRequest request = todosRequest(method).declaring(-1).body(bytesOfSize(LIMIT + 1)).contentTypes(JSON);

        assertCode(request, ErrorCode.PAYLOAD_TOO_LARGE);
    }

    @Test
    void anEndlessChunkedBodyIsReadNoFurtherThanTheCap() {
        GateRequest request = todosRequest("POST").declaring(-1).bodyFrom(new GateRequest.Endless()).contentTypes(JSON);

        assertCode(request, ErrorCode.PAYLOAD_TOO_LARGE);

        assertThat(request.consumed()).isEqualTo(LIMIT + 1L);
    }

    @Test
    void aContentLengthThatUnderstatesTheBodyDoesNotLetMoreThanTheLimitThrough() {
        GateRequest request = todosRequest("PUT").declaring(100).bodyFrom(new GateRequest.Endless()).contentTypes(JSON);

        assertCode(request, ErrorCode.PAYLOAD_TOO_LARGE);

        assertThat(request.consumed()).isEqualTo(LIMIT + 1L);
    }

    @Test
    void anOversizedBodyIsRejectedBeforeItsMediaTypeIsLookedAt() {
        assertCode(todosRequest("POST").declaring(LIMIT + 1L).contentTypes("text/plain"),
                ErrorCode.PAYLOAD_TOO_LARGE);
        assertCode(todosRequest("POST").declaring(LIMIT + 1L), ErrorCode.PAYLOAD_TOO_LARGE);
        assertCode(todosRequest("PATCH").declaring(-1).body(bytesOfSize(LIMIT + 1)).contentTypes("text/plain",
                "text/plain"), ErrorCode.PAYLOAD_TOO_LARGE);
    }

    // ---- P3: media type ---------------------------------------------------------------------------------

    static Stream<String> acceptedMediaTypes() {
        return Stream.of(
                "application/json", "APPLICATION/JSON", "Application/Json", "application/JSON",
                "application/json; charset=utf-8", "application/json;charset=UTF-8",
                "application/json; charset=\"utf-8\"", "application/json ; charset=iso-8859-1",
                "application/json;charset=utf-16", "application/json; foo=bar", "application/json;",
                // S2-4: every parameter is ignored, a charset name is never resolved
                "application/json; charset=bogus", "application/json; charset=", "application/json;charset=",
                "application/json; charset=utf-16", "application/json; charset=no-such-charset",
                "APPLICATION/JSON; CHARSET=BOGUS", "application/json ; charset=utf-8", "application/json  ;  charset=x",
                "  application/json  ; q=1", "application/json;;", "application/json; ; ;", "application/json; =",
                "application/json; charset=\"unterminated", "application/json; a=b; a=c");
    }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("acceptedMediaTypes")
    void applicationJsonInAnyCaseWithAnyParametersIsAccepted(String contentType) throws IOException {
        byte[] sent = bytesOfSize(20);

        for (String method : new String[] {"POST", "PUT", "PATCH"}) {
            RawBody body = gate.check(todosRequest(method).declaring(sent.length).body(sent)
                    .contentTypes(contentType));
            assertThat(body.present()).isTrue();
            assertThat(body.bytes()).isEqualTo(sent);
        }
    }

    static Stream<Arguments> rejectedMediaTypes() {
        return Stream.of(
                Arguments.of("text/plain", new String[] {"text/plain"}),
                Arguments.of("text/plain; charset=utf-8", new String[] {"text/plain; charset=utf-8"}),
                Arguments.of("form", new String[] {"application/x-www-form-urlencoded"}),
                Arguments.of("multipart", new String[] {"multipart/form-data; boundary=x"}),
                Arguments.of("merge-patch+json", new String[] {"application/merge-patch+json"}),
                Arguments.of("vnd+json", new String[] {"application/vnd.api+json"}),
                Arguments.of("ld+json", new String[] {"application/ld+json"}),
                Arguments.of("xml", new String[] {"application/xml"}),
                Arguments.of("json-seq", new String[] {"application/json-seq"}),
                Arguments.of("jsonx", new String[] {"application/jsonx"}),
                Arguments.of("text/json", new String[] {"text/json"}),
                Arguments.of("wildcard subtype", new String[] {"application/*"}),
                Arguments.of("wildcard", new String[] {"*/*"}),
                Arguments.of("octet-stream", new String[] {"application/octet-stream"}),
                Arguments.of("no slash", new String[] {"json"}),
                Arguments.of("no subtype", new String[] {"application/"}),
                Arguments.of("no type", new String[] {"/json"}),
                Arguments.of("empty value", new String[] {""}),
                Arguments.of("blank value", new String[] {"   "}),
                Arguments.of("garbage", new String[] {"\u0001\u0002"}),
                Arguments.of("not a media type", new String[] {"not a media type"}),
                Arguments.of("two headers, both json", new String[] {JSON, JSON}),
                Arguments.of("two headers, json and charset", new String[] {JSON, "application/json; charset=utf-8"}),
                Arguments.of("two headers, json then text", new String[] {JSON, "text/plain"}),
                Arguments.of("two headers, text then json", new String[] {"text/plain", JSON}),
                Arguments.of("three headers", new String[] {JSON, JSON, JSON}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedMediaTypes")
    void anyOtherMediaTypeOrMoreThanOneHeaderIsUnsupportedWhenABodyIsPresent(String label, String[] contentTypes) {
        for (String method : new String[] {"POST", "PUT", "PATCH"}) {
            assertCode(todosRequest(method).declaring(20).body(bytesOfSize(20)).contentTypes(contentTypes),
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
    }

    /** S2-4: only the text before the first ';' is looked at, so a parameter cannot rescue another type. */
    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"text/plain; charset=utf-8", "text/plain; application/json", "application/merge-patch+json;"
            + " charset=utf-8", "application/jsonx; charset=utf-8", "not a media type; charset=utf-8",
            "; application/json", ";application/json;", "application/json/x; charset=utf-8",
            "application; charset=utf-8", "application/ json", "application /json", "application/json,text/plain"})
    void aParameterNeverMakesAnotherTypeOrAnInvalidValueAcceptable(String contentType) {
        assertCode(todosRequest("POST").declaring(20).body(bytesOfSize(20)).contentTypes(contentType),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void anUnknownOrEmptyCharsetIsNotAnErrorAndTheBodyIsStillReadAsBytes() throws IOException {
        byte[] sent = new byte[] {(byte) 0xC3, (byte) 0xA9, '{', '}'};

        for (String contentType : new String[] {"application/json; charset=bogus", "application/json; charset=",
                "application/json; charset=utf-16"}) {
            RawBody body = gate.check(todosRequest("POST").declaring(sent.length).body(sent)
                    .contentTypes(contentType));
            assertThat(body.bytes()).isEqualTo(sent);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"POST", "PUT", "PATCH"})
    void aBodyWithNoContentTypeIsUnsupported(String method) {
        assertCode(todosRequest(method).declaring(20).body(bytesOfSize(20)), ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertCode(todosRequest(method).declaring(-1).body(bytesOfSize(20)), ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void aBodyCountsAsPresentFromTheContentLengthAloneEvenIfNoByteArrives() {
        // Content-Length 5 but the stream is empty: present by D-4's first rule, so the type check applies.
        assertCode(todosRequest("POST").declaring(5), ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertCode(todosRequest("POST").declaring(5).contentTypes("text/plain"),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void aBodyWithJsonTypeButNoBytesIsPresentWithEmptyBytesAndLeftToP5() throws IOException {
        RawBody body = gate.check(todosRequest("POST").declaring(5).contentTypes(JSON));

        assertThat(body.present()).isTrue();
        assertThat(body.bytes()).isEmpty();
    }

    @Test
    void aChunkedBodyIsPresentFromItsFirstByte() {
        assertCode(todosRequest("PUT").declaring(-1).body(new byte[] {'{'}), ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    // ---- no body: P3 is skipped (A-11) ---------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"POST", "PUT", "PATCH"})
    void contentLengthZeroWithNoTypeIsNotUnsupported(String method) throws IOException {
        RawBody body = gate.check(todosRequest(method).declaring(0));

        assertThat(body.present()).isFalse();
        assertThat(body.bytes()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"POST", "PUT", "PATCH"})
    void noBodyAtAllSkipsTheMediaTypeCheckWhateverTheHeaders(String method) throws IOException {
        for (String[] types : new String[][] {{}, {"text/plain"}, {JSON, JSON}, {"garbage"}, {JSON}}) {
            RawBody noLength = gate.check(todosRequest(method).declaring(-1).contentTypes(types));
            RawBody zeroLength = gate.check(todosRequest(method).declaring(0).contentTypes(types));
            assertThat(noLength.present()).isFalse();
            assertThat(zeroLength.present()).isFalse();
            assertThat(noLength.bytes()).isEmpty();
            assertThat(zeroLength.bytes()).isEmpty();
        }
    }

    // ---- the result -----------------------------------------------------------------------------------

    @Test
    void theBodyBytesAreReturnedExactlyAndInOrder() throws IOException {
        byte[] sent = ("{\"title\":\"caf" + (char) 233 + (char) 0xD83D + (char) 0xDE00 + "\"}")
                .getBytes(StandardCharsets.UTF_8);

        RawBody body = gate.check(todosRequest("POST").declaring(sent.length).body(sent).contentTypes(JSON));

        assertThat(body.bytes()).isEqualTo(sent);
        assertThat(body.present()).isTrue();
    }

    @Test
    void theRawBodyPrintsItsSizeAndNeverItsContent() {
        RawBody body = new RawBody("secret title".getBytes(StandardCharsets.UTF_8), true);

        assertThat(body.toString()).contains("12 bytes").doesNotContain("secret");
        assertThat(RawBody.absent().present()).isFalse();
        assertThat(RawBody.absent().bytes()).isEmpty();
        assertThatThrownBy(() -> new RawBody(null, false)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void aFailingStreamSurfacesAsAnIoExceptionAndNotAsAnApiError() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("client went away");
            }
        };

        assertThatThrownBy(() -> gate.check(todosRequest("POST").declaring(-1).bodyFrom(broken)
                .contentTypes(JSON))).isInstanceOf(IOException.class);
    }
}
