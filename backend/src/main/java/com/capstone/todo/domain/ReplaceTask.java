package com.capstone.todo.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * A validated full update (PUT). Status and createdAt are never taken from the client.
 *
 * @param id          the task to change
 * @param title       1 to 100 code points
 * @param description 0 to 500 code points, "" when the client sent none
 */
public record ReplaceTask(UUID id, String title, String description) {

    public ReplaceTask {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
    }
}
