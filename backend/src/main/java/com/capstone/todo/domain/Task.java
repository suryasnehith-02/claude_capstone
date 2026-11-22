package com.capstone.todo.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * A task as stored. Timestamps always have millisecond precision (the store keeps epoch milliseconds).
 *
 * @param seq       insertion sequence assigned by the store, used only as the ordering tie-break and never
 *                  exposed; {@link #UNASSIGNED_SEQ} before the first insert
 * @param id        server-generated identifier, immutable
 * @param title     trimmed, 1 to 100 code points
 * @param description trimmed, 0 to 500 code points, never null
 * @param status    {@link TaskStatus}
 * @param createdAt set on creation, immutable
 * @param updatedAt set on every successful change
 */
public record Task(
        long seq,
        UUID id,
        String title,
        String description,
        TaskStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static final long UNASSIGNED_SEQ = 0L;

    public Task {
        if (seq < 0) {
            throw new IllegalArgumentException("seq must not be negative");
        }
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(status, "status");
        createdAt = Objects.requireNonNull(createdAt, "createdAt").truncatedTo(ChronoUnit.MILLIS);
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt").truncatedTo(ChronoUnit.MILLIS);
    }

    /** The same task with the sequence number the store assigned. */
    public Task withSeq(long newSeq) {
        return new Task(newSeq, id, title, description, status, createdAt, updatedAt);
    }
}
