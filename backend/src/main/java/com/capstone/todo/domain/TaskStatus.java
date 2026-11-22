package com.capstone.todo.domain;

import java.util.Optional;

/** Task status. The wire form is exactly {@code Pending} or {@code Completed}, case-sensitive, never coerced. */
public enum TaskStatus {
    PENDING("Pending"),
    COMPLETED("Completed");

    private final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    /** The text used in the API and in the store. */
    public String label() {
        return label;
    }

    /** Parses the exact label; anything else (other case, whitespace, null) gives an empty result. */
    public static Optional<TaskStatus> fromLabel(String text) {
        for (TaskStatus status : values()) {
            if (status.label.equals(text)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
