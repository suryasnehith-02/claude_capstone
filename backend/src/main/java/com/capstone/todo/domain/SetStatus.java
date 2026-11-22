package com.capstone.todo.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * A validated status change (PATCH).
 *
 * @param id     the task to change
 * @param status the new status
 */
public record SetStatus(UUID id, TaskStatus status) {

    public SetStatus {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
    }
}
