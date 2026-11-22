package com.capstone.todo.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.capstone.todo.domain.CreateTask;
import com.capstone.todo.domain.ReplaceTask;
import com.capstone.todo.domain.SetStatus;
import com.capstone.todo.domain.TaskStatus;
import com.capstone.todo.web.response.ApiException;
import com.capstone.todo.web.response.ErrorDetail;

import tools.jackson.databind.JsonNode;

/**
 * Stage P6 (architecture 7.4, D-10): hand-written checks on the parsed Jackson tree. A number, boolean, array or
 * object where a string is expected is a type error, so nothing is coerced. Every failing field is reported, in the
 * order title, description, status, with {@code field} equal to the JSON property name. Members that are not read
 * (including id, createdAt, updatedAt, and status on create or update) are never looked at, so they never cause
 * an error (FR-8).
 *
 * <p>The message texts are English wording and not a contract (AC-20). The input is the root object that
 * {@code JsonBodyReader} (P5) has already accepted; messages never contain request values.
 */
@Component
public class TaskRequestValidator {

    public static final int TITLE_MAX_CODE_POINTS = 100;
    public static final int DESCRIPTION_MAX_CODE_POINTS = 500;

    static final String TITLE = "title";
    static final String DESCRIPTION = "description";
    static final String STATUS = "status";

    static final String TITLE_REQUIRED = "Title is required";
    static final String TITLE_TYPE = "Title must be a string";
    static final String TITLE_TOO_LONG = "Title must be " + TITLE_MAX_CODE_POINTS + " characters or fewer";
    static final String DESCRIPTION_TYPE = "Description must be a string";
    static final String DESCRIPTION_TOO_LONG =
            "Description must be " + DESCRIPTION_MAX_CODE_POINTS + " characters or fewer";
    static final String STATUS_REQUIRED = "Status is required";
    static final String STATUS_INVALID = "Status must be Pending or Completed";

    /** POST: reads title and description. */
    public CreateTask create(JsonNode body) {
        List<ErrorDetail> failures = new ArrayList<>();
        String title = title(body, failures);
        String description = description(body, failures);
        throwIfAny(failures);
        return new CreateTask(title, description);
    }

    /** PUT: reads title and description; the id comes from the path. */
    public ReplaceTask replace(UUID id, JsonNode body) {
        List<ErrorDetail> failures = new ArrayList<>();
        String title = title(body, failures);
        String description = description(body, failures);
        throwIfAny(failures);
        return new ReplaceTask(id, title, description);
    }

    /** PATCH: reads status only. */
    public SetStatus setStatus(UUID id, JsonNode body) {
        List<ErrorDetail> failures = new ArrayList<>();
        TaskStatus status = status(body, failures);
        throwIfAny(failures);
        return new SetStatus(id, status);
    }

    private static String title(JsonNode body, List<ErrorDetail> failures) {
        JsonNode node = member(body, TITLE);
        if (node == null) {
            return failed(failures, TITLE, TITLE_REQUIRED);
        }
        if (!node.isString()) {
            return failed(failures, TITLE, TITLE_TYPE);
        }
        String trimmed = UnicodeText.trim(node.asString());
        if (trimmed.isEmpty()) {
            return failed(failures, TITLE, TITLE_REQUIRED);
        }
        if (UnicodeText.length(trimmed) > TITLE_MAX_CODE_POINTS) {
            return failed(failures, TITLE, TITLE_TOO_LONG);
        }
        return trimmed;
    }

    private static String description(JsonNode body, List<ErrorDetail> failures) {
        JsonNode node = member(body, DESCRIPTION);
        if (node == null) {
            return "";
        }
        if (!node.isString()) {
            return failed(failures, DESCRIPTION, DESCRIPTION_TYPE);
        }
        String trimmed = UnicodeText.trim(node.asString());
        if (UnicodeText.length(trimmed) > DESCRIPTION_MAX_CODE_POINTS) {
            return failed(failures, DESCRIPTION, DESCRIPTION_TOO_LONG);
        }
        return trimmed;
    }

    private static TaskStatus status(JsonNode body, List<ErrorDetail> failures) {
        JsonNode node = member(body, STATUS);
        if (node == null) {
            failed(failures, STATUS, STATUS_REQUIRED);
            return null;
        }
        if (!node.isString()) {
            failed(failures, STATUS, STATUS_INVALID);
            return null;
        }
        return TaskStatus.fromLabel(node.asString()).orElseGet(() -> {
            failed(failures, STATUS, STATUS_INVALID);
            return null;
        });
    }

    /** The member, or null when it is absent or JSON null. */
    private static JsonNode member(JsonNode body, String name) {
        JsonNode node = body.get(name);
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node;
    }

    private static String failed(List<ErrorDetail> failures, String field, String message) {
        failures.add(new ErrorDetail(field, message));
        return null;
    }

    private static void throwIfAny(List<ErrorDetail> failures) {
        if (!failures.isEmpty()) {
            throw ApiException.validation(failures);
        }
    }
}
