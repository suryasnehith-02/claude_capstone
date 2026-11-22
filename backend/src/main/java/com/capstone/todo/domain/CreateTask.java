package com.capstone.todo.domain;

import java.util.Objects;

/**
 * A validated create request. The validator has already trimmed the text and applied the length rules.
 *
 * @param title       1 to 100 code points
 * @param description 0 to 500 code points, "" when the client sent none
 */
public record CreateTask(String title, String description) {

    public CreateTask {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
    }
}
