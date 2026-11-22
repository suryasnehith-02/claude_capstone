package com.capstone.todo.web.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.capstone.todo.web.ApiRoutes;
import com.capstone.todo.web.gate.RouteTable.Outcome;
import com.capstone.todo.web.response.ApiException;
import com.capstone.todo.web.response.ErrorCode;

/**
 * P1 at unit level (T-P1a, T-P1b, D-12, FR-11, AC-24): the table is decided on the raw URI and the method token only.
 * Real Tomcat (does the TRACE token or the raw URI even arrive here?) is for T-12.
 */
class RouteTableTest {

    private static final String ID = "0b8f6f4e-3a35-4d0c-9d41-0d9f3c2a7b11";

    /** Every method token the tests try, defined or not. */
    private static final List<String> TOKENS = List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE", "CONNECT",
            "get", "post", "Get", "FOO", "GET ", " GET", "");

    private final RouteTable table = new RouteTable();

    private static void assertMatched(Outcome outcome, String route, String rawId) {
        assertThat(outcome).isInstanceOfSatisfying(Outcome.Matched.class, matched -> {
            assertThat(matched.match().route()).isEqualTo(route);
            assertThat(matched.match().rawIdSegment()).isEqualTo(rawId);
        });
    }

    private static void assertRejected(Outcome outcome, ErrorCode code, String allow) {
        assertThat(outcome).isInstanceOfSatisfying(Outcome.Rejected.class, rejected -> {
            assertThat(rejected.errorCode()).isEqualTo(code);
            assertThat(rejected.allow()).isEqualTo(allow);
        });
    }

    // ---- defined routes and methods -------------------------------------------------------------------------

    static Stream<Arguments> definedRequests() {
        return Stream.of(
                Arguments.of("POST", "/todos", ApiRoutes.TODOS, null),
                Arguments.of("GET", "/todos", ApiRoutes.TODOS, null),
                Arguments.of("GET", "/todos/" + ID, ApiRoutes.TODO_ITEM, ID),
                Arguments.of("PUT", "/todos/" + ID, ApiRoutes.TODO_ITEM, ID),
                Arguments.of("DELETE", "/todos/" + ID, ApiRoutes.TODO_ITEM, ID),
                Arguments.of("PATCH", "/todos/" + ID + "/status", ApiRoutes.TODO_STATUS, ID));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("definedRequests")
    void theSixOperationsMatchTheirRoute(String method, String uri, String route, String rawId) {
        assertMatched(table.resolve(method, uri), route, rawId);
    }

    @Test
    void theRouteTemplatesAreTheApiRoutesConstants() {
        assertThat(ApiRoutes.TODOS).isEqualTo("/todos");
        assertThat(ApiRoutes.TODO_ITEM).isEqualTo("/todos/{id}");
        assertThat(ApiRoutes.TODO_STATUS).isEqualTo("/todos/{id}/status");
    }

    @Test
    void theAllowValuesAreThoseOfAppendixA() {
        assertThat(table.allowByRoute()).containsExactly(
                Map.entry(ApiRoutes.TODOS, "GET, POST"),
                Map.entry(ApiRoutes.TODO_ITEM, "GET, PUT, DELETE"),
                Map.entry(ApiRoutes.TODO_STATUS, "PATCH"));
    }

    // ---- id segment stays raw -----------------------------------------------------------------------------

    @ParameterizedTest(name = "raw id [{0}]")
    @ValueSource(strings = {
            "not-a-uuid", "0B8F6F4E-3A35-4D0C-9D41-0D9F3C2A7B11", "%30b8f6f4e-3a35-4d0c-9d41-0d9f3c2a7b11",
            "a%2Fb", "a%5Cb", "%2530b8f6f4e-3a35-4d0c-9d41-0d9f3c2a7b11", "%zz", "%FF", "1-1-1-1-1",
            "a+b", "a%20b", "x.y", "%", "caf" + (char) 233})
    void theIdSegmentIsKeptExactlyAsWritten(String rawId) {
        assertMatched(table.resolve("GET", "/todos/" + rawId), ApiRoutes.TODO_ITEM, rawId);
        assertMatched(table.resolve("PATCH", "/todos/" + rawId + "/status"), ApiRoutes.TODO_STATUS, rawId);
    }

    @Test
    void anEncodedSlashInTheIdDoesNotSplitTheSegment() {
        assertMatched(table.resolve("GET", "/todos/%2F"), ApiRoutes.TODO_ITEM, "%2F");
        assertMatched(table.resolve("PATCH", "/todos/a%2Fb/status"), ApiRoutes.TODO_STATUS, "a%2Fb");
    }

    // ---- the query string is ignored ----------------------------------------------------------------------

    static Stream<Arguments> requestsWithAQuery() {
        return Stream.of(
                Arguments.of("GET", "/todos?x=1", ApiRoutes.TODOS, null),
                Arguments.of("POST", "/todos?", ApiRoutes.TODOS, null),
                Arguments.of("GET", "/todos?a=b/c;d=e", ApiRoutes.TODOS, null),
                Arguments.of("GET", "/todos/" + ID + "?x=1", ApiRoutes.TODO_ITEM, ID),
                Arguments.of("DELETE", "/todos/" + ID + "?x=%2F&y=//", ApiRoutes.TODO_ITEM, ID),
                Arguments.of("PATCH", "/todos/" + ID + "/status?x=1", ApiRoutes.TODO_STATUS, ID));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("requestsWithAQuery")
    void aQueryStringNeverChangesTheMatch(String method, String uri, String route, String rawId) {
        assertMatched(table.resolve(method, uri), route, rawId);
    }

    @Test
    void aQueryStringDoesNotRescueAnUndefinedPath() {
        assertRejected(table.resolve("GET", "/nope?x=/todos"), ErrorCode.NOT_FOUND, null);
        assertRejected(table.resolve("GET", "/todos/?x=1"), ErrorCode.NOT_FOUND, null);
    }

    // ---- no match: 404 for every method -----------------------------------------------------------------

    static Stream<String> undefinedPaths() {
        return Stream.of(
                "/todos/", "//todos", "/todos//", "/todos//status", "/todos/" + ID + "/", "/todos/" + ID + "/status/",
                "/todos/" + ID + "//status", "/todos/" + ID + "/other", "/todos/" + ID + "/Status",
                "/todos/" + ID + "/STATUS", "/todos/a/b/c", "/todos/" + ID + "/status/x",
                "/todos;x=1", "/todos/;x=1", "/todos/" + ID + ";x=1", "/todos/" + ID + "/status;x=1",
                "/todos;x=1/" + ID, "/todos/" + ID + ";x=1/status",
                "/%74odos", "/%74odos/" + ID, "/t%6Fdos", "/todos/" + ID + "/%73tatus", "/todos/" + ID + "/stat%75s",
                "/TODOS", "/Todos", "/Todos/" + ID,
                "/error", "/", "/nope", "/api/todos", "/v1/todos", "/todo", "/todoss", "/todos.json", "/todos%2F",
                "/todos%2Fx", "", "todos", "*", "http://host/todos", "//host/todos", "/ todos", "/todos ");
    }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("undefinedPaths")
    void aPathThatMatchesNoRouteIsNotFoundForEveryMethod(String uri) {
        for (String method : TOKENS) {
            assertRejected(table.resolve(method, uri), ErrorCode.NOT_FOUND, null);
        }
    }

    @Test
    void aMissingUriIsNotFound() {
        assertRejected(table.resolve("GET", null), ErrorCode.NOT_FOUND, null);
    }

    @Test
    void aMissingMethodIsAProgrammingError() {
        assertThatThrownBy(() -> table.resolve(null, "/todos")).isInstanceOf(NullPointerException.class);
    }

    // ---- 405 with the route's Allow -----------------------------------------------------------------------

    static Stream<Arguments> definedRoutesWithTheirMethods() {
        return Stream.of(
                Arguments.of("/todos", ApiRoutes.TODOS, List.of("GET", "POST"), "GET, POST"),
                Arguments.of("/todos/" + ID, ApiRoutes.TODO_ITEM, List.of("GET", "PUT", "DELETE"),
                        "GET, PUT, DELETE"),
                Arguments.of("/todos/" + ID + "/status", ApiRoutes.TODO_STATUS, List.of("PATCH"), "PATCH"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("definedRoutesWithTheirMethods")
    void everyTokenOutsideTheRoutesSetIsMethodNotAllowedWithThatRoutesAllow(
            String uri, String route, List<String> allowed, String allow) {
        for (String method : TOKENS) {
            Outcome outcome = table.resolve(method, uri);
            if (allowed.contains(method)) {
                assertMatched(outcome, route, uri.equals("/todos") ? null : ID);
            } else {
                assertRejected(outcome, ErrorCode.METHOD_NOT_ALLOWED, allow);
            }
        }
    }

    static Stream<Arguments> t_p1b_methodRows() {
        return Stream.of("OPTIONS", "HEAD", "TRACE", "get", "FOO").flatMap(method -> Stream.of(
                Arguments.of(method, "/todos", "GET, POST"),
                Arguments.of(method, "/todos/" + ID, "GET, PUT, DELETE"),
                Arguments.of(method, "/todos/" + ID + "/status", "PATCH")));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("t_p1b_methodRows")
    void tP1bRowsGiveMethodNotAllowedWithTheRoutesAllow(String method, String uri, String allow) {
        assertRejected(table.resolve(method, uri), ErrorCode.METHOD_NOT_ALLOWED, allow);
    }

    @Test
    void deleteOnTheCollectionIsMethodNotAllowedWhateverBodyItCarries() {
        // The body is never looked at by the table (T-P1b: DELETE /todos with a 70 KB body).
        assertRejected(table.resolve("DELETE", "/todos"), ErrorCode.METHOD_NOT_ALLOWED, "GET, POST");
    }

    @Test
    void headAndTraceOnAnUndefinedPathAreNotFound() {
        assertRejected(table.resolve("HEAD", "/nope"), ErrorCode.NOT_FOUND, null);
        assertRejected(table.resolve("TRACE", "/nope"), ErrorCode.NOT_FOUND, null);
        assertRejected(table.resolve("OPTIONS", "/nope"), ErrorCode.NOT_FOUND, null);
    }

    @Test
    void aMethodNotAllowedOnTheStatusRouteListsOnlyPatch() {
        assertRejected(table.resolve("PUT", "/todos/" + ID + "/status"), ErrorCode.METHOD_NOT_ALLOWED, "PATCH");
        assertRejected(table.resolve("GET", "/todos/" + ID + "/status"), ErrorCode.METHOD_NOT_ALLOWED, "PATCH");
    }

    // ---- outcome helpers ----------------------------------------------------------------------------------

    @Test
    void aRejectionConvertsToTheApiExceptionOfItsCode() {
        Outcome.Rejected notFound = new Outcome.Rejected(ErrorCode.NOT_FOUND, null);
        Outcome.Rejected notAllowed = new Outcome.Rejected(ErrorCode.METHOD_NOT_ALLOWED, "PATCH");
        assertThat(notFound.toException()).isInstanceOf(ApiException.class)
                .extracting(ApiException::errorCode).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(notAllowed.toException().errorCode()).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @Test
    void theRouteMatchNeverPrintsTheRawId() {
        RouteMatch match = new RouteMatch(ApiRoutes.TODO_ITEM, "secret-raw-id");
        assertThat(match.toString()).contains("/todos/{id}").doesNotContain("secret-raw-id");
        assertThatThrownBy(() -> new RouteMatch(null, "x")).isInstanceOf(NullPointerException.class);
    }
}
