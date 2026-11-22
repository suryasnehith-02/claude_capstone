package com.capstone.todo.web.response;

import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.capstone.todo.domain.Task;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.json.JsonMapper;

/**
 * The only producer of response bodies: {@code {"data": ...}} and {@code {"error": {...}}} as UTF-8 bytes,
 * written with the dedicated {@code apiJsonMapper}. Bodies that carry no per-request data are precomputed at
 * construction, so the gateway filter and the error controller have no serialisation failure path of their own.
 */
@Component
public final class ApiResponses {

    /** The only content type of a response body: no charset parameter (RFC 8259 defines none). */
    public static final String CONTENT_TYPE = "application/json";

    private final JsonMapper mapper;
    private final Map<ErrorCode, byte[]> fixedErrors = new EnumMap<>(ErrorCode.class);

    public ApiResponses(@Qualifier("apiJsonMapper") JsonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        for (ErrorCode code : ErrorCode.values()) {
            if (!code.hasDetails()) {
                fixedErrors.put(code, error(code, List.of()));
            }
        }
    }

    /** {@code {"data": <task>}}. */
    public byte[] task(Task task) {
        return write(generator -> {
            generator.writeStartObject();
            generator.writeName("data");
            TaskJson.write(generator, task);
            generator.writeEndObject();
        });
    }

    /** {@code {"data": [<task>, ...]}}; an empty list gives {@code {"data":[]}}. */
    public byte[] tasks(List<Task> tasks) {
        return write(generator -> {
            generator.writeStartObject();
            generator.writeName("data");
            generator.writeStartArray();
            for (Task task : tasks) {
                TaskJson.write(generator, task);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        });
    }

    /** The error body of an {@link ApiException}: its code, default message and, for VALIDATION_ERROR, details. */
    public byte[] error(ApiException exception) {
        return error(exception.errorCode(), exception.details());
    }

    /**
     * {@code {"error": {"code", "message", "details"?}}}. {@code details} is written only for VALIDATION_ERROR, which
     * must have at least one; every other code must have none.
     */
    public byte[] error(ErrorCode code, List<ErrorDetail> details) {
        if (code.hasDetails() == details.isEmpty()) {
            throw new IllegalArgumentException(
                    code.hasDetails() ? code + " needs at least one detail" : code + " does not carry details");
        }
        return write(generator -> {
            generator.writeStartObject();
            generator.writeName("error");
            generator.writeStartObject();
            generator.writeStringProperty("code", code.code());
            generator.writeStringProperty("message", code.defaultMessage());
            if (code.hasDetails()) {
                generator.writeName("details");
                generator.writeStartArray();
                for (ErrorDetail detail : details) {
                    generator.writeStartObject();
                    generator.writeStringProperty("field", detail.field());
                    generator.writeStringProperty("message", detail.message());
                    generator.writeEndObject();
                }
                generator.writeEndArray();
            }
            generator.writeEndObject();
            generator.writeEndObject();
        });
    }

    /**
     * The precomputed body of a code that has no details (every code except VALIDATION_ERROR). Returns a copy, so
     * a caller cannot change the shared bytes.
     */
    public byte[] fixedError(ErrorCode code) {
        byte[] body = fixedErrors.get(code);
        if (body == null) {
            throw new IllegalArgumentException(code + " has no fixed body");
        }
        return body.clone();
    }

    private byte[] write(Consumer<JsonGenerator> content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(256);
        try (JsonGenerator generator = mapper.createGenerator(out)) {
            content.accept(generator);
        }
        return out.toByteArray();
    }
}
