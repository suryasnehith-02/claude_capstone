package com.capstone.todo.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.capstone.todo.config.JsonConfig;
import com.capstone.todo.domain.Task;
import com.capstone.todo.domain.TaskRepository;
import com.capstone.todo.domain.TaskStatus;
import com.capstone.todo.support.EvidenceLog;
import com.capstone.todo.support.RawHttp;
import com.capstone.todo.support.TestDataDir;
import com.capstone.todo.web.PrecedenceCases;
import com.capstone.todo.web.PrecedenceCases.Case;
import com.capstone.todo.web.PrecedenceCases.Shape;
import com.capstone.todo.web.response.ApiResponses;
import com.capstone.todo.web.response.ErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * CP-C: the error-precedence table over real HTTP (FR-12, AC-25, architecture 7.2 and 10.1, T-P1a to T-P7). Real
 * Tomcat, the real gateway filter, controller, service and a real SQLite file; no stub and no fake store. The system
 * clock is the real one (nothing here depends on a timestamp value).
 *
 * <p>Every row of {@link PrecedenceCases} (the 21 pair rows and the 24 extra rows) is sent as raw HTTP. For a pair row
 * the "stage j only" request is sent as well, to show the request really violates both stages. Every rejection
 * asserts status, code, the fixed envelope, {@code nosniff}, no {@code Access-Control-*} header, and that
 * {@code GET /todos} and the stored tasks are identical before and after the request. What the server really answered
 * is written to {@code target/cp-c-evidence.txt} after the run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PrecedenceContractTest {

    private static final Path DATA_FILE = TestDataDir.newDataFile(PrecedenceContractTest.class);
    private static final EvidenceLog EVIDENCE = new EvidenceLog();
    private static final JsonMapper JSON = JsonConfig.newApiJsonMapper();

    private static final String EXISTING_ID = PrecedenceCases.EXISTING_ID;
    private static final String MISSING_ID = PrecedenceCases.MISSING_ID;
    private static final String OTHER_ID = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d";
    private static final String APPLICATION_JSON = "application/json";

    private static final Pattern VALIDATION_ENVELOPE = Pattern.compile("\\{\"error\":\\{\"code\":\"VALIDATION_ERROR\","
            + "\"message\":\"Request validation failed\",\"details\":\\[\\{\"field\":\"[a-z]+\",\"message\":\"[^\"]+\"\\}"
            + "(?:,\\{\"field\":\"[a-z]+\",\"message\":\"[^\"]+\"\\})*\\]\\}\\}");

    @DynamicPropertySource
    static void dataFile(DynamicPropertyRegistry registry) {
        registry.add("todo.data-file", DATA_FILE::toString);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ApiResponses responses;

    @Autowired
    private TaskRepository repository;

    /** Two stored tasks, so "nothing changed" compares something: EXISTING_ID and OTHER_ID. */
    @BeforeEach
    void seedTwoTasks() {
        repository.findAllOrdered().forEach(task -> repository.delete(task.id()));
        Instant first = Instant.parse("2026-01-01T00:00:00.000Z");
        Instant second = Instant.parse("2026-01-02T00:00:00.000Z");
        repository.insert(new Task(Task.UNASSIGNED_SEQ, UUID.fromString(EXISTING_ID), "Existing task", "kept",
                TaskStatus.PENDING, first, first));
        repository.insert(new Task(Task.UNASSIGNED_SEQ, UUID.fromString(OTHER_ID), "Other task", "",
                TaskStatus.COMPLETED, second, second));
    }

    @AfterAll
    static void writeTheEvidenceFile() {
        EVIDENCE.writeTo("cp-c-evidence.txt");
    }

    // ---------------------------------------------------------------- helpers

    private RawHttp.Response send(Shape shape) {
        RawHttp.Request request = RawHttp.request(shape.method(), shape.uri());
        for (String type : shape.contentTypes()) {
            request.header("Content-Type", type);
        }
        if (shape.chunked()) {
            request.chunked(shape.body());
        } else if (shape.body().length > 0) {
            request.body(shape.body());
        }
        return request.send(port);
    }

    private RawHttp.Response get(String target) {
        return RawHttp.request("GET", target).send(port);
    }

    private String listText() {
        RawHttp.Response response = get("/todos");
        assertThat(response.status()).isEqualTo(200);
        return response.bodyText();
    }

    private static Shape shape(String method, String uri, String contentType, byte[] body) {
        return new Shape(method, uri, contentType == null ? List.of() : List.of(contentType), body, false);
    }

    private static Shape json(String method, String uri, String body) {
        return shape(method, uri, APPLICATION_JSON, body.getBytes(StandardCharsets.UTF_8));
    }

    private static Shape bare(String method, String uri) {
        return shape(method, uri, null, new byte[0]);
    }

    private static String actualCode(RawHttp.Response response) {
        String type = response.header("Content-Type");
        if (type == null || !type.startsWith(APPLICATION_JSON)) {
            return "non-envelope (Content-Type " + type + ")";
        }
        try {
            JsonNode error = JSON.readTree(response.bodyText()).get("error");
            return error == null ? "JSON without an error member" : error.get("code").asString();
        } catch (RuntimeException unparsable) {
            return "unparsable body";
        }
    }

    private static String fields(RawHttp.Response response) {
        List<String> names = new ArrayList<>();
        JSON.readTree(response.bodyText()).get("error").get("details").forEach(d -> names.add(d.get("field").asString()));
        return String.join(",", names);
    }

    private void assertCommonHeaders(RawHttp.Response response, String label) {
        assertThat(response.headers("X-Content-Type-Options")).as(label + ": nosniff").containsExactly("nosniff");
        assertThat(response.headers()).as(label + ": no CORS header")
                .noneMatch(h -> h[0].toLowerCase(Locale.ROOT).startsWith("access-control-"));
    }

    /**
     * Sends the request and asserts it is rejected with the given code: status, the exact fixed envelope (for
     * VALIDATION_ERROR the envelope shape, and the exact details when {@code details} gives field and message pairs),
     * the headers, and that {@code GET /todos} and the stored tasks are identical before and after. Returns what the
     * server answered, for the evidence table.
     */
    private String rejection(String label, Supplier<RawHttp.Response> request, String expected, String allow,
            String... details) {
        String listBefore = listText();
        List<Task> storedBefore = repository.findAllOrdered();

        RawHttp.Response response = request.get();

        String listAfter = listText();
        List<Task> storedAfter = repository.findAllOrdered();
        ErrorCode code = ErrorCode.valueOf(expected);
        assertThat(response.status()).as(label + ": " + response.bodyText()).isEqualTo(code.status());
        assertThat(response.headers("Content-Type")).as(label + ": Content-Type").containsExactly(APPLICATION_JSON);
        assertCommonHeaders(response, label);
        assertThat(response.headers("Allow")).as(label + ": Allow")
                .isEqualTo(allow == null ? List.of() : List.of(allow));
        if (code == ErrorCode.VALIDATION_ERROR) {
            assertThat(response.bodyText()).as(label + ": validation envelope").matches(VALIDATION_ENVELOPE);
            if (details.length > 0) {
                StringBuilder exact = new StringBuilder("{\"error\":{\"code\":\"VALIDATION_ERROR\",")
                        .append("\"message\":\"Request validation failed\",\"details\":[");
                for (int i = 0; i < details.length; i += 2) {
                    exact.append(i == 0 ? "" : ",").append("{\"field\":\"").append(details[i]).append("\",\"message\":\"")
                            .append(details[i + 1]).append("\"}");
                }
                assertThat(response.bodyText()).as(label + ": details").isEqualTo(exact.append("]}}").toString());
            }
        } else {
            assertThat(response.body()).as(label + ": fixed envelope").isEqualTo(responses.fixedError(code));
        }
        assertThat(actualCode(response)).as(label + ": code").isEqualTo(expected);
        assertThat(listAfter).as(label + ": GET /todos identical before and after").isEqualTo(listBefore);
        assertThat(storedAfter).as(label + ": stored tasks identical before and after").isEqualTo(storedBefore);

        return response.status() + " " + expected
                + (allow == null ? "" : ", Allow: " + allow)
                + (code == ErrorCode.VALIDATION_ERROR ? " [" + fields(response) + "]" : "");
    }

    private String rejection(Shape shape, String expected, String allow, String... details) {
        return rejection(shape.method() + " " + shape.uri(), () -> send(shape), expected, allow, details);
    }

    /** Runs every request through {@link #rejection} and logs one evidence row for the group. */
    private void group(String test, String label, String expected, List<Shape> shapes, String allow,
            String... details) {
        Set<String> actuals = new LinkedHashSet<>();
        for (Shape shape : shapes) {
            actuals.add(rejection(shape, expected, allow, details));
        }
        EVIDENCE.row(test, label, expected + (allow == null ? "" : " (Allow: " + allow + ")"),
                shapes.size() + " of " + shapes.size() + ": " + String.join(" | ", actuals));
    }

    /** One rejected request, one evidence row. */
    private void single(String test, String label, Shape shape, String expected, String allow, String... details) {
        String actual = rejection(shape, expected, allow, details);
        EVIDENCE.row(test, shape.method() + " " + shape.uri() + ", " + label, expected, actual);
    }

    /**
     * Tomcat answers a malformed percent sequence in the path with its own 400 before any filter (G2-6, S5-10): the
     * answer is outside the envelope. Only the status and "nothing changed" are asserted; the rest is recorded.
     */
    private String containerRejection(String label, Shape shape) {
        String listBefore = listText();
        List<Task> storedBefore = repository.findAllOrdered();

        RawHttp.Response response = send(shape);

        assertThat(response.status()).as(label + ": " + response.bodyText()).isEqualTo(400);
        assertThat(response.bodyText()).as(label + ": the quiet error page names no server or version (V-7)")
                .doesNotContainIgnoringCase("apache tomcat").doesNotContain("11.0");
        assertThat(listText()).as(label + ": GET /todos identical before and after").isEqualTo(listBefore);
        assertThat(repository.findAllOrdered()).as(label + ": stored tasks identical").isEqualTo(storedBefore);
        return "container 400, G2-6 (Content-Type " + response.header("Content-Type") + ", " + response.body().length
                + " bytes, " + (actualCode(response).startsWith("non-envelope") ? "not an envelope" : "an envelope") + ")";
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] all = new byte[first.length + second.length];
        System.arraycopy(first, 0, all, 0, first.length);
        System.arraycopy(second, 0, all, first.length, second.length);
        return all;
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static int successStatus(String method) {
        return switch (method) {
            case "POST" -> 201;
            case "DELETE" -> 204;
            default -> 200;
        };
    }

    /** A request that violates nothing: asserts the success status and shape and returns what the server answered. */
    private String success(Shape shape) {
        String label = shape.method() + " " + shape.uri();
        int tasksBefore = JSON.readTree(listText()).get("data").size();
        RawHttp.Response response = send(shape);

        assertThat(response.status()).as(label + ": " + response.bodyText()).isEqualTo(successStatus(shape.method()));
        assertCommonHeaders(response, label);
        if (response.status() == 204) {
            assertThat(response.body()).as(label + ": no body").isEmpty();
            assertThat(response.headers("Content-Type")).as(label + ": no Content-Type").isEmpty();
        } else {
            assertThat(response.headers("Content-Type")).as(label + ": Content-Type").containsExactly(APPLICATION_JSON);
            assertThat(response.bodyText()).as(label + ": data envelope").startsWith("{\"data\":");
            assertThat(response.bodyText()).as(label + ": no error").doesNotContain("\"error\"");
        }
        int tasksAfter = JSON.readTree(listText()).get("data").size();
        assertThat(tasksAfter).as(label + ": task count")
                .isEqualTo(shape.method().equals("POST") ? tasksBefore + 1 : shape.method().equals("DELETE") ? tasksBefore - 1
                        : tasksBefore);
        return response.status() + " OK";
    }

    // ---------------------------------------------------------------- the 21 pair rows and the 24 extra rows

    static Stream<Arguments> pairRows() {
        return PrecedenceCases.pairs().stream().map(row -> Arguments.of(row.pair(), row));
    }

    static Stream<Arguments> extraRows() {
        return PrecedenceCases.extras().stream().map(row -> Arguments.of(row.pair(), row));
    }

    private void replay(String test, Case row) {
        String expected = "OK".equals(row.expected()) ? "OK (" + successStatus(row.request().method()) + ")"
                : row.expected() + (row.allow() == null ? "" : " (Allow: " + row.allow() + ")");
        String actual = "OK".equals(row.expected()) ? success(row.request())
                : rejection(row.request(), row.expected(), row.allow());
        if (row.laterOnly() != null) {
            String later = rejection(row.laterOnly(), row.laterExpected(), null);
            actual += "; stage-j-only: " + row.laterOnly().describe() + " -> " + later;
        }
        EVIDENCE.row(test, row.request().describe(), expected, actual);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pairRows")
    void theEarlierStageWinsOverRealHttpAndNothingChanges(String pair, Case row) {
        replay("PrecedenceContractTest.theEarlierStageWinsOverRealHttpAndNothingChanges[" + pair + "]", row);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("extraRows")
    void requestsWithThreeOrMoreViolationsOrNoneBehaveAsDocumentedOverRealHttp(String label, Case row) {
        replay("PrecedenceContractTest.requestsWithThreeOrMoreViolationsOrNoneBehaveAsDocumentedOverRealHttp[" + label + "]",
                row);
    }

    @Test
    void thePairRowsAreAllTwentyOneAndTheExtraRowsAreAllTwentyFour() {
        assertThat(PrecedenceCases.pairs()).hasSize(21);
        assertThat(PrecedenceCases.extras()).hasSize(24);
    }

    // ---------------------------------------------------------------- T-P4: the id (P4)

    private static final String TEST = "PrecedenceContractTest.";

    private static List<Shape> idShapes(String id) {
        return List.of(
                bare("GET", "/todos/" + id),
                bare("DELETE", "/todos/" + id),
                json("PUT", "/todos/" + id, "{bad"),
                json("PUT", "/todos/" + id, "{\"title\":\"ok\"}"),
                json("PATCH", "/todos/" + id + "/status", "{bad"),
                json("PATCH", "/todos/" + id + "/status", "{}"));
    }

    private static final String SIX = "GET, DELETE, PUT {bad, PUT valid, PATCH {bad, PATCH {} on ";

    @Test
    void anInvalidIdIsInvalidIdOnEveryIdOperationWhateverElseIsWrongWithTheRequest_tp4() {
        List<String> invalid = List.of(
                "not-a-uuid",
                "a%2Fb", "a%2fb", "%2F", "%2F%2F",
                "a%5Cb", "%5C",
                "%2530b8f6f4e-3a35-4d0c-9d41-0d9f3c2a7b11", "%252d",
                "1-1-1-1-1",
                "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz",
                "0b8f6f4e-3a35-4d0c-9d41-0d9f3c2a7b1%67",
                "0b8f6f4e3a354d0c9d410d9f3c2a7b11",
                "0b8f6f4e-3a35-4d0c-9d41-0d9f3c2a7b11x",
                "a%20b", "%2e%2e", "%2Fetc%2Fpasswd");
        for (String id : invalid) {
            group(TEST + "anInvalidIdIsInvalidIdOnEveryIdOperationWhateverElseIsWrongWithTheRequest_tp4",
                    SIX + "/todos/" + id, "INVALID_ID", idShapes(id), null);
        }
    }

    @Test
    void aMalformedPercentSequenceIsTomcatsOwn400OutsideTheEnvelopeAndNothingChanges_g2_6() {
        List<String> malformed = List.of("%zz", "%FF", "%", "a%00b", "%C0%AF", "%E2%82");
        for (String id : malformed) {
            Set<String> actuals = new LinkedHashSet<>();
            List<Shape> shapes = idShapes(id);
            for (Shape shape : shapes) {
                actuals.add(containerRejection(shape.method() + " " + shape.uri(), shape));
            }
            EVIDENCE.row(TEST + "aMalformedPercentSequenceIsTomcatsOwn400OutsideTheEnvelopeAndNothingChanges_g2_6",
                    SIX + "/todos/" + id, "container 400, G2-6 (status 400 only)",
                    shapes.size() + " of " + shapes.size() + ": " + String.join(" | ", actuals));
        }
    }

    // ---------------------------------------------------------------- T-P4b: a valid id spelled another way

    @Test
    void aValidIdSpelledWithEncodedHexLettersOrInUppercaseFindsTheSameTask_tp4b() {
        String canonical = get("/todos/" + EXISTING_ID).bodyText();
        assertThat(canonical).startsWith("{\"data\":{\"id\":\"" + EXISTING_ID + "\"");
        List<String> spellings = new ArrayList<>();
        spellings.add(EXISTING_ID.toUpperCase(Locale.ROOT));
        spellings.add(EXISTING_ID.toUpperCase(Locale.ROOT).replace("-", "%2D"));
        String allEncoded = EXISTING_ID;
        for (char letter : "abcdef".toCharArray()) {
            assertThat(EXISTING_ID).contains(String.valueOf(letter));
            String lower = String.format(Locale.ROOT, "%%%02x", (int) letter);
            String upper = String.format(Locale.ROOT, "%%%02x", (int) Character.toUpperCase(letter));
            spellings.add(EXISTING_ID.replaceFirst(String.valueOf(letter), lower));
            spellings.add(EXISTING_ID.replaceFirst(String.valueOf(letter), upper));
            allEncoded = allEncoded.replace(String.valueOf(letter), lower);
        }
        spellings.add(allEncoded);

        for (String spelling : spellings) {
            RawHttp.Response response = get("/todos/" + spelling);
            assertThat(response.status()).as(spelling + ": " + response.bodyText()).isEqualTo(200);
            assertThat(response.bodyText()).as(spelling).isEqualTo(canonical);
        }
        EVIDENCE.row(TEST + "aValidIdSpelledWithEncodedHexLettersOrInUppercaseFindsTheSameTask_tp4b",
                "GET /todos/<" + EXISTING_ID + " in uppercase; with %2D hyphens; each of a-f written %61..%66 and %41..%46 once; all letters %xx>",
                "200, the body of GET /todos/" + EXISTING_ID,
                spellings.size() + " of " + spellings.size() + ": 200, body identical to the canonical spelling");

        String viaEncoded = spellings.get(2);
        RawHttp.Response put = send(json("PUT", "/todos/" + viaEncoded, "{\"title\":\"renamed\"}"));
        assertThat(put.status()).isEqualTo(200);
        assertThat(put.bodyText()).startsWith("{\"data\":{\"id\":\"" + EXISTING_ID + "\",\"title\":\"renamed\"");
        RawHttp.Response patch = send(json("PATCH", "/todos/" + spellings.get(0) + "/status", "{\"status\":\"Completed\"}"));
        assertThat(patch.status()).isEqualTo(200);
        assertThat(patch.bodyText()).contains("\"id\":\"" + EXISTING_ID + "\"").contains("\"status\":\"Completed\"");
        assertThat(JSON.readTree(listText()).get("data").size()).as("still two tasks").isEqualTo(2);
        EVIDENCE.row(TEST + "aValidIdSpelledWithEncodedHexLettersOrInUppercaseFindsTheSameTask_tp4b",
                "PUT /todos/" + viaEncoded + " and PATCH /todos/" + spellings.get(0) + "/status",
                "200 on the same task, no second task", "200, 200, id " + EXISTING_ID + ", two tasks in the list");

        RawHttp.Response delete = send(bare("DELETE", "/todos/" + spellings.get(spellings.size() - 1)));
        assertThat(delete.status()).isEqualTo(204);
        assertThat(rejection(bare("GET", "/todos/" + EXISTING_ID), "TODO_NOT_FOUND", null))
                .isEqualTo("404 TODO_NOT_FOUND");
        EVIDENCE.row(TEST + "aValidIdSpelledWithEncodedHexLettersOrInUppercaseFindsTheSameTask_tp4b",
                "DELETE /todos/" + spellings.get(spellings.size() - 1) + ", then GET /todos/" + EXISTING_ID,
                "204, then 404 TODO_NOT_FOUND", "204, then 404 TODO_NOT_FOUND");
    }

    // ---------------------------------------------------------------- T-P5: the body (P5)

    private record Body(String label, byte[] bytes) {
    }

    private static final byte[] BOM = bytes(0xEF, 0xBB, 0xBF);

    private static String nestedInMember(int depth) {
        return "{\"title\":\"x\",\"a\":" + "[".repeat(depth - 1) + "]".repeat(depth - 1) + "}";
    }

    private static List<Body> notOneWellFormedJsonObject() {
        return List.of(
                new Body("{bad", utf8("{bad")),
                new Body("unterminated {\"title\":\"x\"", utf8("{\"title\":\"x\"")),
                new Body("empty body with a JSON Content-Type", new byte[0]),
                new Body("whitespace only", utf8("  \r\n\t ")),
                new Body("{} {}", utf8("{} {}")),
                new Body("{\"title\":\"x\"} trailing token", utf8("{\"title\":\"x\"} {\"title\":\"y\"}")),
                new Body("{\"title\":\"x\"},", utf8("{\"title\":\"x\"},")),
                new Body("[]", utf8("[]")),
                new Body("[{\"title\":\"x\"}] (array root)", utf8("[{\"title\":\"x\"}]")),
                new Body("\"x\" (string root)", utf8("\"x\"")),
                new Body("5 (number root)", utf8("5")),
                new Body("null root", utf8("null")),
                new Body("true root", utf8("true")),
                new Body("leading BOM before {\"title\":\"x\"}", concat(BOM, utf8("{\"title\":\"x\"}"))),
                new Body("invalid UTF-8: 0xFF in a value", concat(utf8("{\"title\":\""), concat(bytes(0xFF), utf8("\"}")))),
                new Body("invalid UTF-8: truncated E2 82", concat(utf8("{\"title\":\""), concat(bytes(0xE2, 0x82), utf8("\"}")))),
                new Body("invalid UTF-8: overlong C0 AF", concat(utf8("{\"title\":\""), concat(bytes(0xC0, 0xAF), utf8("\"}")))),
                new Body("invalid UTF-8: encoded surrogate ED A0 80",
                        concat(utf8("{\"title\":\""), concat(bytes(0xED, 0xA0, 0x80), utf8("\"}")))),
                new Body("unpaired surrogate \\ud800 in an ignored member", utf8("{\"title\":\"x\",\"a\":\"\\ud800\"}")),
                new Body("unpaired surrogate \\ud800 in a member name", utf8("{\"\\ud800\":1,\"title\":\"x\"}")),
                new Body("lone low surrogate in the title", utf8("{\"title\":\"\\udc00\"}")),
                new Body("unpaired surrogate in a member that a later duplicate overwrites",
                        utf8("{\"a\":\"\\ud800\",\"a\":\"x\",\"title\":\"x\"}")),
                new Body("1,001-digit number in an ignored member", utf8("{\"title\":\"x\",\"a\":" + "7".repeat(1_001) + "}")),
                new Body("nesting 1,001 in an ignored member", utf8(nestedInMember(1_001))),
                new Body("single quotes", utf8("{'title':'x'}")),
                new Body("trailing comma", utf8("{\"title\":\"x\",}")),
                new Body("NaN", utf8("{\"title\":NaN}")),
                new Body("a comment", utf8("{\"title\":\"x\"/*c*/}")),
                new Body("a raw tab inside a string", bytes('{', '"', 't', 'i', 't', 'l', 'e', '"', ':', '"', 'a', 9, 'b', '"', '}')));
    }

    @Test
    void aBodyThatIsNotOneWellFormedJsonObjectIsInvalidRequestBodyOnEveryBodyOperation_tp5() {
        for (Body body : notOneWellFormedJsonObject()) {
            List<Shape> shapes = List.of(
                    shape("PUT", "/todos/" + MISSING_ID, APPLICATION_JSON, body.bytes()),
                    shape("PATCH", "/todos/" + MISSING_ID + "/status", APPLICATION_JSON, body.bytes()),
                    shape("POST", "/todos", APPLICATION_JSON, body.bytes()));
            group(TEST + "aBodyThatIsNotOneWellFormedJsonObjectIsInvalidRequestBodyOnEveryBodyOperation_tp5",
                    "PUT /todos/<missing id>, PATCH /todos/<missing id>/status, POST /todos, JSON type, body: " + body.label(),
                    "INVALID_REQUEST_BODY", shapes, null);
        }
    }

    @Test
    void noBodyAtAllIsInvalidRequestBodyAndNotAMediaTypeError_a11_tp5() {
        List<Supplier<RawHttp.Response>> sends = List.of(
                () -> RawHttp.request("PUT", "/todos/" + MISSING_ID).header("Content-Length", "0").send(port),
                () -> RawHttp.request("POST", "/todos").header("Content-Length", "0").send(port),
                () -> RawHttp.request("PATCH", "/todos/" + MISSING_ID + "/status").header("Content-Length", "0")
                        .header("Content-Type", "text/plain").send(port),
                () -> RawHttp.request("PUT", "/todos/" + MISSING_ID).send(port));
        List<String> labels = List.of("PUT, Content-Length: 0, no Content-Type", "POST, Content-Length: 0, no Content-Type",
                "PATCH /status, Content-Length: 0, Content-Type text/plain", "PUT, no Content-Length, no body");
        for (int i = 0; i < sends.size(); i++) {
            String actual = rejection(labels.get(i), sends.get(i), "INVALID_REQUEST_BODY", null);
            EVIDENCE.row(TEST + "noBodyAtAllIsInvalidRequestBodyAndNotAMediaTypeError_a11_tp5", labels.get(i),
                    "INVALID_REQUEST_BODY (A-11, not 415)", actual);
        }
    }

    @Test
    void theLimitsAreAcceptedAtTheBoundaryAndRejectedOneOver_tp5() {
        String title = "\"title\":\"x\"";
        List<Body> accepted = List.of(
                new Body("1,000-digit number in an ignored member", utf8("{" + title + ",\"a\":" + "7".repeat(1_000) + "}")),
                new Body("nesting 1,000 in an ignored member", utf8(nestedInMember(1_000))),
                new Body("60,000-character member name", utf8("{" + title + ",\"" + "n".repeat(60_000) + "\":1}")),
                new Body("60,000-character string in an ignored member",
                        utf8("{" + title + ",\"a\":\"" + "v".repeat(60_000) + "\"}")),
                new Body("an escaped valid surrogate pair in an ignored member", utf8("{" + title + ",\"a\":\"\\ud83d\\ude00\"}")),
                new Body("null, boolean and object ignored members",
                        utf8("{" + title + ",\"a\":null,\"b\":true,\"c\":{\"d\":[1,2.5,\"s\"]}}")));
        for (Body body : accepted) {
            String created = success(shape("POST", "/todos", APPLICATION_JSON, body.bytes()));
            String passedToP7 = rejection(shape("PUT", "/todos/" + MISSING_ID, APPLICATION_JSON, body.bytes()),
                    "TODO_NOT_FOUND", null);
            EVIDENCE.row(TEST + "theLimitsAreAcceptedAtTheBoundaryAndRejectedOneOver_tp5",
                    "POST /todos and PUT /todos/<missing id>, body: " + body.label(),
                    "POST 201; PUT passes P5 and P6 and ends at P7: 404 TODO_NOT_FOUND",
                    "POST " + created + "; PUT " + passedToP7);
        }
    }

    @Test
    void duplicateKeysTheLastValueWinsOnEveryBodyOperation_tp5() {
        RawHttp.Response created = send(json("POST", "/todos", "{\"title\":\"first\",\"title\":\"last\",\"description\":\"a\","
                + "\"description\":\"b\"}"));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.bodyText()).contains("\"title\":\"last\"").contains("\"description\":\"b\"")
                .doesNotContain("first");
        RawHttp.Response replaced = send(json("PUT", "/todos/" + EXISTING_ID,
                "{\"title\":\"one\",\"title\":\"two\"}"));
        assertThat(replaced.status()).isEqualTo(200);
        assertThat(replaced.bodyText()).contains("\"title\":\"two\"");
        RawHttp.Response status = send(json("PATCH", "/todos/" + EXISTING_ID + "/status",
                "{\"status\":\"Bogus\",\"status\":\"Completed\"}"));
        assertThat(status.status()).isEqualTo(200);
        assertThat(status.bodyText()).contains("\"status\":\"Completed\"");
        EVIDENCE.row(TEST + "duplicateKeysTheLastValueWinsOnEveryBodyOperation_tp5",
                "POST {title first, title last}; PUT {title one, title two}; PATCH {status Bogus, status Completed}",
                "201 title last; 200 title two; 200 status Completed", "201, 200, 200 with the last values");
    }

    // ---------------------------------------------------------------- T-P6: the fields (P6)

    private static String quoted(String text) {
        return JSON.writeValueAsString(text);
    }

    private record Invalid(String label, String body, String... details) {
    }

    private static final String TITLE_REQUIRED = "Title is required";
    private static final String TITLE_STRING = "Title must be a string";
    private static final String TITLE_LONG = "Title must be 100 characters or fewer";
    private static final String DESCRIPTION_STRING = "Description must be a string";
    private static final String DESCRIPTION_LONG = "Description must be 500 characters or fewer";

    private static String astral(int count) {
        return new String(Character.toChars(0x1F600)).repeat(count);
    }

    private static List<Invalid> invalidTitleOrDescription() {
        String nbsp = "\u00A0";
        return List.of(
                new Invalid("{}", "{}", "title", TITLE_REQUIRED),
                new Invalid("title null", "{\"title\":null}", "title", TITLE_REQUIRED),
                new Invalid("title empty", "{\"title\":\"\"}", "title", TITLE_REQUIRED),
                new Invalid("title spaces only", "{\"title\":\"   \"}", "title", TITLE_REQUIRED),
                new Invalid("title NBSP and ideographic space only", "{\"title\":" + quoted(nbsp + "\u3000" + nbsp) + "}",
                        "title", TITLE_REQUIRED),
                new Invalid("title is a number", "{\"title\":5}", "title", TITLE_STRING),
                new Invalid("title is a boolean", "{\"title\":true}", "title", TITLE_STRING),
                new Invalid("title is an array", "{\"title\":[\"a\"]}", "title", TITLE_STRING),
                new Invalid("title is an object", "{\"title\":{\"a\":1}}", "title", TITLE_STRING),
                new Invalid("title of 101 characters", "{\"title\":\"" + "a".repeat(101) + "\"}", "title", TITLE_LONG),
                new Invalid("title of 101 astral code points", "{\"title\":" + quoted(astral(101)) + "}", "title", TITLE_LONG),
                new Invalid("title of 101 characters padded with NBSP", "{\"title\":" + quoted(nbsp + "a".repeat(101) + nbsp)
                        + "}", "title", TITLE_LONG),
                new Invalid("description is a number", "{\"title\":\"ok\",\"description\":5}", "description", DESCRIPTION_STRING),
                new Invalid("description is a boolean", "{\"title\":\"ok\",\"description\":false}", "description",
                        DESCRIPTION_STRING),
                new Invalid("description is an array", "{\"title\":\"ok\",\"description\":[]}", "description", DESCRIPTION_STRING),
                new Invalid("description is an object", "{\"title\":\"ok\",\"description\":{}}", "description",
                        DESCRIPTION_STRING),
                new Invalid("description of 501 characters", "{\"title\":\"ok\",\"description\":\"" + "d".repeat(501) + "\"}",
                        "description", DESCRIPTION_LONG),
                new Invalid("description of 501 astral code points",
                        "{\"title\":\"ok\",\"description\":" + quoted(astral(501)) + "}", "description", DESCRIPTION_LONG),
                new Invalid("title and description both wrong type", "{\"title\":5,\"description\":true}", "title",
                        TITLE_STRING, "description", DESCRIPTION_STRING),
                new Invalid("title blank and description too long",
                        "{\"title\":\" \",\"description\":\"" + "d".repeat(501) + "\"}", "title", TITLE_REQUIRED, "description",
                        DESCRIPTION_LONG),
                new Invalid("title too long and description too long, description first in the document",
                        "{\"description\":\"" + "d".repeat(501) + "\",\"title\":\"" + "t".repeat(101) + "\"}", "title", TITLE_LONG,
                        "description", DESCRIPTION_LONG));
    }

    @Test
    void everyFieldRuleGivesValidationErrorWithItsDetailsBeforeTheStoreIsLookedAt_tp6() {
        for (Invalid invalid : invalidTitleOrDescription()) {
            Shape put = json("PUT", "/todos/" + MISSING_ID, invalid.body());
            single(TEST + "everyFieldRuleGivesValidationErrorWithItsDetailsBeforeTheStoreIsLookedAt_tp6",
                    invalid.label() + " (the id is well formed and missing: P6 wins over P7)", put, "VALIDATION_ERROR", null,
                    invalid.details());
            rejection(json("POST", "/todos", invalid.body()), "VALIDATION_ERROR", null, invalid.details());
        }
    }

    @Test
    void theLimitsAreCodePointsAfterTrimmingAndTheValuesAtTheLimitPassP6_tp6() {
        String nbsp = "\u00A0";
        List<Body> atTheLimit = List.of(
                new Body("title of 100 characters", utf8("{\"title\":\"" + "a".repeat(100) + "\"}")),
                new Body("title of 100 astral code points (400 bytes)", utf8("{\"title\":" + quoted(astral(100)) + "}")),
                new Body("title of 100 characters padded with NBSP and U+3000",
                        utf8("{\"title\":" + quoted(nbsp + "\u3000" + "a".repeat(100) + "\u3000" + nbsp) + "}")),
                new Body("description of 500 characters", utf8("{\"title\":\"ok\",\"description\":\"" + "d".repeat(500) + "\"}")),
                new Body("description of 500 astral code points",
                        utf8("{\"title\":\"ok\",\"description\":" + quoted(astral(500)) + "}")),
                new Body("description of 500 characters padded with NBSP",
                        utf8("{\"title\":\"ok\",\"description\":" + quoted(nbsp + "d".repeat(500) + nbsp) + "}")),
                new Body("description null", utf8("{\"title\":\"ok\",\"description\":null}")),
                new Body("description spaces only", utf8("{\"title\":\"ok\",\"description\":\"   \"}")),
                new Body("id, status, createdAt, updatedAt and unknown members present",
                        utf8("{\"title\":\"ok\",\"id\":5,\"status\":\"bogus\",\"createdAt\":1,\"updatedAt\":[],\"extra\":{}}")));
        for (Body body : atTheLimit) {
            String created = success(shape("POST", "/todos", APPLICATION_JSON, body.bytes()));
            String passedToP7 = rejection(shape("PUT", "/todos/" + MISSING_ID, APPLICATION_JSON, body.bytes()),
                    "TODO_NOT_FOUND", null);
            EVIDENCE.row(TEST + "theLimitsAreCodePointsAfterTrimmingAndTheValuesAtTheLimitPassP6_tp6",
                    "POST /todos and PUT /todos/<missing id>, body: " + body.label(),
                    "POST 201; PUT passes P6 and ends at P7: 404 TODO_NOT_FOUND", "POST " + created + "; PUT " + passedToP7);
        }
    }

    @Test
    void theStatusMustBeExactlyPendingOrCompletedAndIsCheckedBeforeTheStoreIsLookedAt_tp6() {
        String required = "Status is required";
        String wrong = "Status must be Pending or Completed";
        List<Invalid> invalid = List.of(
                new Invalid("{}", "{}", "status", required),
                new Invalid("status null", "{\"status\":null}", "status", required),
                new Invalid("only unknown members", "{\"other\":\"Completed\"}", "status", required),
                new Invalid("pending (lower case)", "{\"status\":\"pending\"}", "status", wrong),
                new Invalid("completed (lower case)", "{\"status\":\"completed\"}", "status", wrong),
                new Invalid("COMPLETED", "{\"status\":\"COMPLETED\"}", "status", wrong),
                new Invalid("leading space", "{\"status\":\" Pending\"}", "status", wrong),
                new Invalid("trailing space", "{\"status\":\"Pending \"}", "status", wrong),
                new Invalid("empty", "{\"status\":\"\"}", "status", wrong),
                new Invalid("Done", "{\"status\":\"Done\"}", "status", wrong),
                new Invalid("a number", "{\"status\":5}", "status", wrong),
                new Invalid("a boolean", "{\"status\":true}", "status", wrong),
                new Invalid("an array", "{\"status\":[\"Pending\"]}", "status", wrong),
                new Invalid("an object", "{\"status\":{\"a\":1}}", "status", wrong));
        for (Invalid body : invalid) {
            single(TEST + "theStatusMustBeExactlyPendingOrCompletedAndIsCheckedBeforeTheStoreIsLookedAt_tp6",
                    body.label() + " (missing id)", json("PATCH", "/todos/" + MISSING_ID + "/status", body.body()),
                    "VALIDATION_ERROR", null, body.details());
        }
        for (String valid : List.of("Pending", "Completed")) {
            single(TEST + "theStatusMustBeExactlyPendingOrCompletedAndIsCheckedBeforeTheStoreIsLookedAt_tp6",
                    "status \"" + valid + "\" passes P6 and ends at P7",
                    json("PATCH", "/todos/" + MISSING_ID + "/status", "{\"status\":\"" + valid + "\"}"), "TODO_NOT_FOUND", null);
        }
    }

    // ---------------------------------------------------------------- T-P7: the task (P7)

    @Test
    void aWellFormedMissingIdIsTodoNotFoundOnlyWhenEveryEarlierStagePassed_tp7() {
        List<Shape> valid = List.of(
                bare("GET", "/todos/" + MISSING_ID),
                json("PUT", "/todos/" + MISSING_ID, "{\"title\":\"valid\",\"description\":\"valid\"}"),
                json("PATCH", "/todos/" + MISSING_ID + "/status", "{\"status\":\"Completed\"}"),
                json("PATCH", "/todos/" + MISSING_ID + "/status", "{\"status\":\"Pending\"}"),
                bare("DELETE", "/todos/" + MISSING_ID));
        group(TEST + "aWellFormedMissingIdIsTodoNotFoundOnlyWhenEveryEarlierStagePassed_tp7",
                "GET, PUT valid, PATCH Completed, PATCH Pending, DELETE on /todos/<missing id>", "TODO_NOT_FOUND", valid, null);
        for (String upper : List.of(MISSING_ID.toUpperCase(Locale.ROOT), MISSING_ID.replaceFirst("d", "%64"))) {
            group(TEST + "aWellFormedMissingIdIsTodoNotFoundOnlyWhenEveryEarlierStagePassed_tp7",
                    "the same five requests with the missing id spelled " + upper, "TODO_NOT_FOUND",
                    List.of(bare("GET", "/todos/" + upper), bare("DELETE", "/todos/" + upper),
                            json("PUT", "/todos/" + upper, "{\"title\":\"valid\"}"),
                            json("PATCH", "/todos/" + upper + "/status", "{\"status\":\"Completed\"}")), null);
        }
    }

    @Test
    void aRepeatedDeleteIsTodoNotFoundAndANeverStoredIdIsTheSame_tp7() {
        RawHttp.Response first = send(bare("DELETE", "/todos/" + EXISTING_ID));
        assertThat(first.status()).isEqualTo(204);
        assertThat(first.body()).isEmpty();
        assertThat(first.headers("Content-Type")).isEmpty();
        assertCommonHeaders(first, "first delete");

        group(TEST + "aRepeatedDeleteIsTodoNotFoundAndANeverStoredIdIsTheSame_tp7",
                "DELETE /todos/" + EXISTING_ID + " again, three times, then GET, PUT and PATCH of the deleted id",
                "TODO_NOT_FOUND",
                List.of(bare("DELETE", "/todos/" + EXISTING_ID), bare("DELETE", "/todos/" + EXISTING_ID),
                        bare("DELETE", "/todos/" + EXISTING_ID), bare("GET", "/todos/" + EXISTING_ID),
                        json("PUT", "/todos/" + EXISTING_ID, "{\"title\":\"back\"}"),
                        json("PATCH", "/todos/" + EXISTING_ID + "/status", "{\"status\":\"Completed\"}")), null);
        EVIDENCE.row(TEST + "aRepeatedDeleteIsTodoNotFoundAndANeverStoredIdIsTheSame_tp7",
                "DELETE /todos/" + EXISTING_ID + " (first time)", "204, no body, no Content-Type", "204, no body, no Content-Type");
        assertThat(JSON.readTree(listText()).get("data").size()).as("only the other task is left").isEqualTo(1);
    }

    @Test
    void aPathParameterOnAnIdRouteIsNotFoundAndNothingChanges_s5_9() {
        List<Shape> shapes = List.of(
                bare("GET", "/todos/" + EXISTING_ID + ";x=1"),
                bare("DELETE", "/todos/" + EXISTING_ID + ";x=1"),
                json("PUT", "/todos/" + EXISTING_ID + ";x=1", "{\"title\":\"changed\"}"),
                json("PATCH", "/todos/" + EXISTING_ID + ";x=1/status", "{\"status\":\"Completed\"}"),
                json("PATCH", "/todos/" + EXISTING_ID + "/status;x=1", "{\"status\":\"Completed\"}"),
                bare("GET", "/todos/" + MISSING_ID + ";x=1"),
                bare("GET", "/todos/not-a-uuid;x=1"),
                bare("GET", "/todos;x=1"));
        group(TEST + "aPathParameterOnAnIdRouteIsNotFoundAndNothingChanges_s5_9",
                "GET, DELETE, PUT, PATCH with ;x=1 after the id or after /status, and on a missing id, a bad id and /todos",
                "NOT_FOUND", shapes, null);
        single(TEST + "aPathParameterOnAnIdRouteIsNotFoundAndNothingChanges_s5_9",
                "an encoded semicolon (%3B) is part of the id, not a path parameter",
                bare("GET", "/todos/" + EXISTING_ID + "%3Bx=1"), "INVALID_ID", null);
    }
}
