package com.capstone.todo.web.response;

import java.util.Objects;

/**
 * One failing field of a VALIDATION_ERROR.
 *
 * @param field   the property name, for example {@code title}
 * @param message English reason; not a machine contract
 */
public record ErrorDetail(String field, String message) {

    public ErrorDetail {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(message, "message");
    }
}
