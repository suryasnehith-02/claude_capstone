package com.capstone.todo.web;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.capstone.todo.support.GateRequest;

/**
 * The CP-A table as data (plain request shapes, no mocks), so the same rows can later be replayed over real HTTP
 * (T-12, T-13). One row per pair of stages (earlier, later) that one request can violate together, P1 to P7, plus
 * rows that violate three or more stages and rows that violate none.
 *
 * <p>Stages: P1 route and method (404, 405), P2 size (413), P3 media type (415), P4 id (INVALID_ID),
 * P5 body (INVALID_REQUEST_BODY), P6 fields (VALIDATION_ERROR), P7 task missing (TODO_NOT_FOUND).
 *
 * <p>Each row also names a {@code laterOnly} request that repairs the earlier violation and nothing else, with the
 * result it must give. That proves the row really violates both stages, so "the earlier stage wins" is not a trivial
 * outcome of the request only violating one of them.
 */
public final class PrecedenceCases {

    public static final String EXISTING_ID = "0b8f6f4e-3a35-4d0c-9d41-0d9f3c2a7b11";
    public static final String MISSING_ID = "5d1d1f0a-7d6c-4f0e-8a56-6f1b2f3c4d5e";
    public static final String BAD_ID = "not-a-uuid";

    private static final String JSON = "application/json";
    private static final String TEXT = "text/plain";
    private static final int OVER_LIMIT = 65_537;
    private static final String VALID_PUT = "{\"title\":\"Buy milk\",\"description\":\"two litres\"}";
    private static final String VALID_PATCH = "{\"status\":\"Completed\"}";

    private PrecedenceCases() {
    }

    /** What a client sends. */
    public record Shape(String method, String uri, List<String> contentTypes, byte[] body, boolean chunked) {

        static Shape of(String method, String uri) {
            return new Shape(method, uri, List.of(), new byte[0], false);
        }

        Shape types(String... values) {
            return new Shape(method, uri, List.of(values), body, chunked);
        }

        Shape body(String text) {
            return new Shape(method, uri, contentTypes, text.getBytes(StandardCharsets.UTF_8), chunked);
        }

        /** The text followed by spaces up to exactly {@code size} bytes. */
        Shape padded(String text, int size) {
            byte[] bytes = new byte[size];
            Arrays.fill(bytes, (byte) ' ');
            byte[] start = text.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(start, 0, bytes, 0, start.length);
            return new Shape(method, uri, contentTypes, bytes, chunked);
        }

        Shape asChunked() {
            return new Shape(method, uri, contentTypes, body, true);
        }

        /** The request as the gate sees it: Content-Length is the body size, or absent when chunked. */
        public GateRequest toRequest() {
            return new GateRequest(method, uri)
                    .declaring(chunked ? -1 : body.length)
                    .body(body)
                    .contentTypes(contentTypes.toArray(new String[0]));
        }

        /** One line for the report. */
        public String describe() {
            StringBuilder text = new StringBuilder(method).append(' ').append(uri);
            if (!contentTypes.isEmpty()) {
                text.append(", Content-Type ").append(String.join(" + ", contentTypes));
            }
            if (body.length > 0) {
                text.append(", ").append(String.format(Locale.ROOT, "%,d", body.length)).append("-byte body");
                if (body.length <= 60) {
                    text.append(" ").append(new String(body, StandardCharsets.UTF_8));
                } else {
                    text.append(" starting ")
                            .append(new String(body, 0, 20, StandardCharsets.UTF_8).stripTrailing())
                            .append(" and padded with spaces");
                }
                text.append(chunked ? " (chunked)" : "");
            } else if (!chunked) {
                text.append(", no body");
            }
            return text.toString();
        }
    }

    /**
     * One row.
     *
     * @param pair            for example {@code P2>P5}: the earlier and the later stage the request violates
     * @param request         the request that violates both
     * @param expected        the winner: an error code name, or {@code OK}
     * @param allow           the expected Allow header for METHOD_NOT_ALLOWED, else null
     * @param repositoryCalls storage calls expected: 0 for any P1-P6 result
     * @param laterOnly       the same request with the earlier violation repaired, or null when no stage is repaired
     * @param laterExpected   what {@code laterOnly} must give
     * @param laterCalls      storage calls expected for {@code laterOnly}
     */
    public record Case(String pair, Shape request, String expected, String allow, int repositoryCalls,
            Shape laterOnly, String laterExpected, int laterCalls) {
    }

    private static Shape post(String uri) {
        return Shape.of("POST", uri);
    }

    private static Shape put(String id) {
        return Shape.of("PUT", "/todos/" + id);
    }

    private static Shape patch(String id) {
        return Shape.of("PATCH", "/todos/" + id + "/status");
    }

    private static Case row(String pair, Shape request, String expected, Shape laterOnly, String laterExpected) {
        return new Case(pair, request, expected, null, 0, laterOnly, laterExpected,
                "TODO_NOT_FOUND".equals(laterExpected) ? 1 : 0);
    }

    private static Case rowAllow(String pair, Shape request, String expected, String allow, Shape laterOnly,
            String laterExpected) {
        return new Case(pair, request, expected, allow, 0, laterOnly, laterExpected,
                "TODO_NOT_FOUND".equals(laterExpected) ? 1 : 0);
    }

    /** The 21 rows: every pair (i before j) of P1..P7, in order. */
    public static List<Case> pairs() {
        return List.of(
                row("P1>P2", post("/todos/").types(JSON).padded(VALID_PUT, OVER_LIMIT), "NOT_FOUND",
                        post("/todos").types(JSON).padded(VALID_PUT, OVER_LIMIT), "PAYLOAD_TOO_LARGE"),
                row("P1>P3", post("/todos/").types(TEXT).body(VALID_PUT), "NOT_FOUND",
                        post("/todos").types(TEXT).body(VALID_PUT), "UNSUPPORTED_MEDIA_TYPE"),
                rowAllow("P1>P4", Shape.of("PATCH", "/todos/" + BAD_ID).types(JSON).body(VALID_PATCH),
                        "METHOD_NOT_ALLOWED", "GET, PUT, DELETE",
                        Shape.of("GET", "/todos/" + BAD_ID), "INVALID_ID"),
                row("P1>P5", post("/todos/").types(JSON).body("{bad"), "NOT_FOUND",
                        post("/todos").types(JSON).body("{bad"), "INVALID_REQUEST_BODY"),
                rowAllow("P1>P6", Shape.of("PUT", "/todos").types(JSON).body("{}"), "METHOD_NOT_ALLOWED",
                        "GET, POST", post("/todos").types(JSON).body("{}"), "VALIDATION_ERROR"),
                row("P1>P7", Shape.of("GET", "/todos/" + MISSING_ID + "/"), "NOT_FOUND",
                        Shape.of("GET", "/todos/" + MISSING_ID), "TODO_NOT_FOUND"),

                row("P2>P3", put(MISSING_ID).types(TEXT).padded(VALID_PUT, OVER_LIMIT), "PAYLOAD_TOO_LARGE",
                        put(MISSING_ID).types(TEXT).body(VALID_PUT), "UNSUPPORTED_MEDIA_TYPE"),
                row("P2>P4", put(BAD_ID).types(JSON).padded(VALID_PUT, OVER_LIMIT).asChunked(), "PAYLOAD_TOO_LARGE",
                        put(BAD_ID).types(JSON).body(VALID_PUT), "INVALID_ID"),
                row("P2>P5", post("/todos").types(JSON).padded("{bad", OVER_LIMIT), "PAYLOAD_TOO_LARGE",
                        post("/todos").types(JSON).body("{bad"), "INVALID_REQUEST_BODY"),
                row("P2>P6", post("/todos").types(JSON).padded("{}", OVER_LIMIT), "PAYLOAD_TOO_LARGE",
                        post("/todos").types(JSON).body("{}"), "VALIDATION_ERROR"),
                row("P2>P7", put(MISSING_ID).types(JSON).padded(VALID_PUT, OVER_LIMIT), "PAYLOAD_TOO_LARGE",
                        put(MISSING_ID).types(JSON).body(VALID_PUT), "TODO_NOT_FOUND"),

                row("P3>P4", put(BAD_ID).types(TEXT).body(VALID_PUT), "UNSUPPORTED_MEDIA_TYPE",
                        put(BAD_ID).types(JSON).body(VALID_PUT), "INVALID_ID"),
                row("P3>P5", post("/todos").types(TEXT).body("{bad"), "UNSUPPORTED_MEDIA_TYPE",
                        post("/todos").types(JSON).body("{bad"), "INVALID_REQUEST_BODY"),
                row("P3>P6", post("/todos").types(TEXT).body("{}"), "UNSUPPORTED_MEDIA_TYPE",
                        post("/todos").types(JSON).body("{}"), "VALIDATION_ERROR"),
                row("P3>P7", put(MISSING_ID).types(JSON, JSON).body(VALID_PUT), "UNSUPPORTED_MEDIA_TYPE",
                        put(MISSING_ID).types(JSON).body(VALID_PUT), "TODO_NOT_FOUND"),

                row("P4>P5", put(BAD_ID).types(JSON).body("{bad"), "INVALID_ID",
                        put(MISSING_ID).types(JSON).body("{bad"), "INVALID_REQUEST_BODY"),
                row("P4>P6", put(BAD_ID).types(JSON).body("{}"), "INVALID_ID",
                        put(MISSING_ID).types(JSON).body("{}"), "VALIDATION_ERROR"),
                row("P4>P7", Shape.of("DELETE", "/todos/" + BAD_ID), "INVALID_ID",
                        Shape.of("DELETE", "/todos/" + MISSING_ID), "TODO_NOT_FOUND"),

                row("P5>P6", put(EXISTING_ID).types(JSON).body("{\"title\":5,\"description\":true"),
                        "INVALID_REQUEST_BODY",
                        put(EXISTING_ID).types(JSON).body("{\"title\":5,\"description\":true}"), "VALIDATION_ERROR"),
                row("P5>P7", put(MISSING_ID).types(JSON).body("{bad"), "INVALID_REQUEST_BODY",
                        put(MISSING_ID).types(JSON).body(VALID_PUT), "TODO_NOT_FOUND"),

                row("P6>P7", put(MISSING_ID).types(JSON).body("{}"), "VALIDATION_ERROR",
                        put(MISSING_ID).types(JSON).body(VALID_PUT), "TODO_NOT_FOUND"));
    }

    /** Requests that violate three or more stages, one stage only, or none. */
    public static List<Case> extras() {
        List<Case> rows = new ArrayList<>();
        // Three or more stages at once: the earliest still wins.
        rows.add(rowAllow("P1..P6", Shape.of("PATCH", "/todos/" + BAD_ID).types(TEXT).padded("{bad", OVER_LIMIT),
                "METHOD_NOT_ALLOWED", "GET, PUT, DELETE", null, null));
        rows.add(row("P2..P6", patch(BAD_ID).types(TEXT).padded("{bad", OVER_LIMIT), "PAYLOAD_TOO_LARGE", null,
                null));
        rows.add(row("P3..P6", patch(BAD_ID).types(TEXT).body("{bad"), "UNSUPPORTED_MEDIA_TYPE", null, null));
        rows.add(row("P4..P6", patch(BAD_ID).types(JSON).body("{bad"), "INVALID_ID", null, null));
        rows.add(row("P5..P7", patch(MISSING_ID).types(JSON).body("{bad"), "INVALID_REQUEST_BODY", null, null));
        // The documented T-P rows with one violation plus P7.
        rows.add(row("P6..P7 PATCH {}", patch(MISSING_ID).types(JSON).body("{}"), "VALIDATION_ERROR", null, null));
        rows.add(row("P6..P7 PATCH {} (existing id)", patch(EXISTING_ID).types(JSON).body("{}"), "VALIDATION_ERROR",
                null, null));
        // A-11: no body at all is not a 415, it is a 400 from P5, and P7 is not reached.
        rows.add(row("P3 skipped (A-11)", put(MISSING_ID), "INVALID_REQUEST_BODY", null, null));
        rows.add(row("P3 skipped, wrong type", put(MISSING_ID).types(TEXT), "INVALID_REQUEST_BODY", null, null));
        // Bodies of GET and DELETE are never read: no 413, no 415, nothing from P2 to P6.
        rows.add(oneCall("P7 only (GET with body)",
                Shape.of("GET", "/todos/" + MISSING_ID).types(TEXT).padded(VALID_PUT, 70_000), "TODO_NOT_FOUND"));
        rows.add(oneCall("none (DELETE with body)",
                Shape.of("DELETE", "/todos/" + EXISTING_ID).types(TEXT).padded("{bad", 70_000), "OK"));
        // P7 alone, on every operation that has an id (T-P7).
        rows.add(oneCall("P7 only (GET)", Shape.of("GET", "/todos/" + MISSING_ID), "TODO_NOT_FOUND"));
        rows.add(oneCall("P7 only (PUT)", put(MISSING_ID).types(JSON).body(VALID_PUT), "TODO_NOT_FOUND"));
        rows.add(oneCall("P7 only (PATCH)", patch(MISSING_ID).types(JSON).body(VALID_PATCH), "TODO_NOT_FOUND"));
        rows.add(oneCall("P7 only (DELETE)", Shape.of("DELETE", "/todos/" + MISSING_ID), "TODO_NOT_FOUND"));
        // No violation: every operation succeeds and the stand-in reaches the store where it should.
        rows.add(new Case("none (POST)", post("/todos").types(JSON).body(VALID_PUT), "OK", null, 0, null, null, 0));
        rows.add(oneCall("none (GET list)", Shape.of("GET", "/todos"), "OK"));
        rows.add(oneCall("none (GET item)", Shape.of("GET", "/todos/" + EXISTING_ID), "OK"));
        rows.add(oneCall("none (PUT)", put(EXISTING_ID).types(JSON).body(VALID_PUT), "OK"));
        rows.add(oneCall("none (PATCH)", patch(EXISTING_ID).types(JSON).body(VALID_PATCH), "OK"));
        rows.add(oneCall("none (DELETE)", Shape.of("DELETE", "/todos/" + EXISTING_ID), "OK"));
        // The wire-level variants the matrix lists for T-P2 and T-P3.
        rows.add(row("P2 (T-P2 chunked)", patch(BAD_ID).types(TEXT).padded("x", OVER_LIMIT).asChunked(),
                "PAYLOAD_TOO_LARGE", null, null));
        rows.add(row("P3 (T-P3 unparsable)", put(BAD_ID).types("not a media type").body("{bad"),
                "UNSUPPORTED_MEDIA_TYPE", null, null));
        rows.add(row("P3 (T-P3 merge-patch)", put(BAD_ID).types("application/merge-patch+json").body("{bad"),
                "UNSUPPORTED_MEDIA_TYPE", null, null));
        return List.copyOf(rows);
    }

    private static Case oneCall(String pair, Shape request, String expected) {
        return new Case(pair, request, expected, null, 1, null, null, 0);
    }
}
