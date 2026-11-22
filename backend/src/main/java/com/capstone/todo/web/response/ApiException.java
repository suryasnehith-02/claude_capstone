package com.capstone.todo.web.response;

import java.util.List;
import java.util.Objects;

/**
 * A request failure with a catalog code. Carries no stack trace (it is control flow for 4xx, never logged) and
 * only the catalog's default message, so no request value can leak through it.
 */
public final class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final transient List<ErrorDetail> details;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, List.of());
    }

    public ApiException(ErrorCode errorCode, List<ErrorDetail> details) {
        super(Objects.requireNonNull(errorCode, "errorCode").defaultMessage(), null, false, false);
        Objects.requireNonNull(details, "details");
        if (errorCode.hasDetails() && details.isEmpty()) {
            throw new IllegalArgumentException(errorCode + " needs at least one detail");
        }
        if (!errorCode.hasDetails() && !details.isEmpty()) {
            throw new IllegalArgumentException(errorCode + " does not carry details");
        }
        this.errorCode = errorCode;
        this.details = List.copyOf(details);
    }

    /** A VALIDATION_ERROR listing every failing field. */
    public static ApiException validation(List<ErrorDetail> details) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, details);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** Failing fields in order; empty unless the code is VALIDATION_ERROR. */
    public List<ErrorDetail> details() {
        return details;
    }
}
