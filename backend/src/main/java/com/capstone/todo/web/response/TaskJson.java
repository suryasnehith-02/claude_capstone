package com.capstone.todo.web.response;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.capstone.todo.domain.Task;

import tools.jackson.core.JsonGenerator;

/**
 * The JSON form of a task: members in the order id, title, description, status, createdAt, updatedAt.
 * {@code description} is always present and {@code seq} is never written.
 *
 * <p>Timestamps use an explicit pattern with {@link Locale#ROOT} and UTC, so the host locale and zone have no
 * effect, and a zero millisecond part prints as {@code .000} (architecture D-9).
 */
public final class TaskJson {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

    private TaskJson() {
    }

    /** Formats an instant as {@code 2026-01-02T03:04:05.006Z}. */
    public static String format(Instant instant) {
        return TIMESTAMP.format(instant);
    }

    /** Writes one task object to the generator. */
    public static void write(JsonGenerator generator, Task task) {
        generator.writeStartObject();
        generator.writeStringProperty("id", task.id().toString());
        generator.writeStringProperty("title", task.title());
        generator.writeStringProperty("description", task.description());
        generator.writeStringProperty("status", task.status().label());
        generator.writeStringProperty("createdAt", format(task.createdAt()));
        generator.writeStringProperty("updatedAt", format(task.updatedAt()));
        generator.writeEndObject();
    }
}
