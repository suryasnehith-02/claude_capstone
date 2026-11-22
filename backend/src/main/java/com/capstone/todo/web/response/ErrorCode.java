package com.capstone.todo.web.response;

/**
 * The error catalog (requirements Appendix A.4). Codes and statuses are a fixed contract; the default
 * messages are English text and not a machine contract. Messages never contain request values.
 */
public enum ErrorCode {
    VALIDATION_ERROR(400, "Request validation failed"),
    INVALID_REQUEST_BODY(400, "Request body must be a single well-formed JSON object"),
    INVALID_ID(400, "Task id must be a UUID"),
    TODO_NOT_FOUND(404, "Task not found"),
    NOT_FOUND(404, "No resource matches this path"),
    METHOD_NOT_ALLOWED(405, "Method not allowed for this path"),
    PAYLOAD_TOO_LARGE(413, "Request body is too large"),
    UNSUPPORTED_MEDIA_TYPE(415, "Content-Type must be application/json"),
    INTERNAL_ERROR(500, "Internal server error");

    private final int status;
    private final String defaultMessage;

    ErrorCode(int status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    /** The machine-readable code written to the client (equal to the constant name). */
    public String code() {
        return name();
    }

    /** HTTP status. */
    public int status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    /**
     * The code for a status the servlet container dispatched to the error path (DR-17, G2-5 (h)): 404, 405, 413 and
     * 415 keep their codes; every other status, including 400 and 500, becomes INTERNAL_ERROR (and so HTTP 500).
     */
    public static ErrorCode forContainerStatus(int status) {
        return switch (status) {
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> INTERNAL_ERROR;
        };
    }

    /** Only VALIDATION_ERROR carries a {@code details} array. */
    public boolean hasDetails() {
        return this == VALIDATION_ERROR;
    }
}
