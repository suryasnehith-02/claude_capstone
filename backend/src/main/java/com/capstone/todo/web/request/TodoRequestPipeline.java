package com.capstone.todo.web.request;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.capstone.todo.domain.CreateTask;
import com.capstone.todo.domain.ReplaceTask;
import com.capstone.todo.domain.SetStatus;
import com.capstone.todo.validation.TaskRequestValidator;
import com.capstone.todo.web.gate.RawBody;
import com.capstone.todo.web.gate.RouteMatch;

import tools.jackson.databind.JsonNode;

/**
 * Stages P4, P5 and P6 (architecture 7.2, D-1): the one place where the order of the request-shape checks is written.
 * Each operation runs the id check (P4, only for routes that have an id), then the JSON body check (P5), then the
 * field validation (P6), and the first failure is thrown as an {@code ApiException}; nothing after it runs. An invalid
 * id is therefore reported even when the body is also bad, and a malformed body is reported before any field is
 * validated.
 *
 * <p>The pipeline never touches storage, so a request that fails here changes nothing (NFR-3), and whether the task
 * exists is stage P7, decided later by the service. P1 to P3 are decided before this class is reached (route and body
 * gate), which is what lets it assume a matched route and a body that has already passed size and media type.
 */
@Component
public class TodoRequestPipeline {

    private final JsonBodyReader bodyReader;
    private final TaskRequestValidator validator;

    public TodoRequestPipeline(JsonBodyReader bodyReader, TaskRequestValidator validator) {
        this.bodyReader = bodyReader;
        this.validator = validator;
    }

    /** POST /todos: no id, so P5 then P6. */
    public CreateTask create(RawBody body) {
        JsonNode root = bodyReader.read(body.bytes());
        return validator.create(root);
    }

    /** PUT /todos/{id}: P4, P5, P6. */
    public ReplaceTask replace(RouteMatch route, RawBody body) {
        UUID id = IdParser.parse(route.rawIdSegment());
        JsonNode root = bodyReader.read(body.bytes());
        return validator.replace(id, root);
    }

    /** PATCH /todos/{id}/status: P4, P5, P6. */
    public SetStatus setStatus(RouteMatch route, RawBody body) {
        UUID id = IdParser.parse(route.rawIdSegment());
        JsonNode root = bodyReader.read(body.bytes());
        return validator.setStatus(id, root);
    }

    /** GET and DELETE /todos/{id}: P4 only; any body is ignored. */
    public UUID id(RouteMatch route) {
        return IdParser.parse(route.rawIdSegment());
    }
}
