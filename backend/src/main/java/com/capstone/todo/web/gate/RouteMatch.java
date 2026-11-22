package com.capstone.todo.web.gate;

import java.util.Objects;

/**
 * The route a request matched, as recorded by {@code RouteTable} (P1) for the later stages.
 *
 * @param route        one of the {@code ApiRoutes} templates, for example {@code /todos/{id}}; this is also the only
 *                     form of the route that may be logged
 * @param rawIdSegment the id segment exactly as it appears in the request URI (not decoded); null for {@code /todos}
 */
public record RouteMatch(String route, String rawIdSegment) {

    public RouteMatch {
        Objects.requireNonNull(route, "route");
    }

    /** Prints the route template only: the raw id comes from the client and must not reach a log line. */
    @Override
    public String toString() {
        return "RouteMatch[" + route + "]";
    }
}
