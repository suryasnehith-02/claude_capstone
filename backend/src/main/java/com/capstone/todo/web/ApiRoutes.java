package com.capstone.todo.web;

/**
 * The only place the route literals are written (architecture DR-13). {@code RouteTable} matches the raw request URI
 * against these templates and the controller annotations use the same constants, so the two cannot drift apart.
 * They are compile-time constants so that annotations can use them.
 */
public final class ApiRoutes {

    public static final String TODOS = "/todos";
    public static final String TODO_ITEM = "/todos/{id}";
    public static final String TODO_STATUS = "/todos/{id}/status";

    /**
     * Where the servlet container sends a status it reports itself. It is not an API route: the route table rejects
     * it as a request, and only an error dispatch reaches the handler mapped here.
     */
    public static final String ERROR = "/error";

    private ApiRoutes() {
    }
}
