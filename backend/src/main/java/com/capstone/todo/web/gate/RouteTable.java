package com.capstone.todo.web.gate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.capstone.todo.web.ApiRoutes;
import com.capstone.todo.web.response.ApiException;
import com.capstone.todo.web.response.ErrorCode;

/**
 * Stage P1 (architecture D-12, FR-11): the exact route and method table, matched on the raw request URI.
 *
 * <p>The URI is the raw, undecoded one ({@code HttpServletRequest.getRequestURI()}); a query string is cut off and
 * ignored. Literal segments are compared as they are written, so a percent-encoded literal such as {@code /%74odos}
 * is no match. An empty segment, a trailing slash, {@code //} and a {@code ;param} anywhere in the path are no match
 * either, and the match is case-sensitive. The id segment is kept raw in the {@link RouteMatch}; decoding it is stage
 * P4 and not done here.
 *
 * <p>A path that matches no route gives NOT_FOUND for every method. A matched route with a method outside its own
 * set gives METHOD_NOT_ALLOWED with that route's {@code Allow} value: the method token is compared exactly, so HEAD,
 * OPTIONS, TRACE, lower-case tokens and unknown tokens are all rejected.
 *
 * <p>The table reads nothing from storage and holds no state, so a single instance can be shared.
 */
public final class RouteTable {

    /** What the table decided. */
    public sealed interface Outcome {

        /** The path and method are defined. */
        record Matched(RouteMatch match) implements Outcome {
        }

        /**
         * The request stops at P1.
         *
         * @param errorCode NOT_FOUND or METHOD_NOT_ALLOWED
         * @param allow     the route's {@code Allow} header value for METHOD_NOT_ALLOWED; null for NOT_FOUND
         */
        record Rejected(ErrorCode errorCode, String allow) implements Outcome {

            public Rejected {
                Objects.requireNonNull(errorCode, "errorCode");
            }

            /** The same rejection as the exception the later stages throw (the {@code Allow} value is not carried). */
            public ApiException toException() {
                return new ApiException(errorCode);
            }
        }
    }

    private record Definition(String template, List<String> methods) {

        String allow() {
            return String.join(", ", methods);
        }
    }

    private static final Definition COLLECTION = new Definition(ApiRoutes.TODOS, List.of("GET", "POST"));
    private static final Definition ITEM = new Definition(ApiRoutes.TODO_ITEM, List.of("GET", "PUT", "DELETE"));
    private static final Definition STATUS = new Definition(ApiRoutes.TODO_STATUS, List.of("PATCH"));

    private static final Outcome NOT_FOUND = new Outcome.Rejected(ErrorCode.NOT_FOUND, null);

    private static final String LITERAL_TODOS = ApiRoutes.TODOS.substring(1);
    private static final String LITERAL_STATUS = "status";

    /**
     * @param method     the request method token exactly as received
     * @param requestUri the raw request URI; a query string, if one is still attached, is ignored
     */
    public Outcome resolve(String method, String requestUri) {
        Objects.requireNonNull(method, "method");
        String path = withoutQuery(requestUri);
        if (path == null || !path.startsWith("/") || path.indexOf(';') >= 0) {
            return NOT_FOUND;
        }
        String[] segments = path.split("/", -1);
        // segments[0] is the empty text before the leading slash.
        if (segments.length < 2 || !LITERAL_TODOS.equals(segments[1])) {
            return NOT_FOUND;
        }
        Definition definition;
        String rawId = null;
        if (segments.length == 2) {
            definition = COLLECTION;
        } else if (segments.length == 3 && !segments[2].isEmpty()) {
            definition = ITEM;
            rawId = segments[2];
        } else if (segments.length == 4 && !segments[2].isEmpty() && LITERAL_STATUS.equals(segments[3])) {
            definition = STATUS;
            rawId = segments[2];
        } else {
            return NOT_FOUND;
        }
        if (!definition.methods().contains(method)) {
            return new Outcome.Rejected(ErrorCode.METHOD_NOT_ALLOWED, definition.allow());
        }
        return new Outcome.Matched(new RouteMatch(definition.template(), rawId));
    }

    /** Each defined route template with the {@code Allow} value of its methods, in declaration order. */
    public Map<String, String> allowByRoute() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Definition definition : List.of(COLLECTION, ITEM, STATUS)) {
            result.put(definition.template(), definition.allow());
        }
        return result;
    }

    private static String withoutQuery(String requestUri) {
        if (requestUri == null) {
            return null;
        }
        int query = requestUri.indexOf('?');
        return query < 0 ? requestUri : requestUri.substring(0, query);
    }
}
