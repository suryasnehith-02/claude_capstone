package com.capstone.todo.web;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.todo.domain.TaskService;
import com.capstone.todo.web.gate.RawBody;
import com.capstone.todo.web.gate.RouteMatch;
import com.capstone.todo.web.request.TodoRequestPipeline;
import com.capstone.todo.web.response.ApiResponses;

/**
 * The six operations (architecture C-Controller, FR-1 to FR-6). Each method runs stages P4 to P6 through
 * {@link TodoRequestPipeline}, then the service (P7), and returns the bytes of the response.
 *
 * <p>Nothing here is left to Spring's own binding. The mapping templates come from {@link ApiRoutes} and exist only so
 * that the dispatcher servlet picks the method; the {@code {id}} in them is never read here: the id comes from the
 * {@link RouteMatch} the gateway filter recorded (raw, decoded once by the pipeline), and the body from the
 * {@link RawBody}, both handed over by {@link GateAttributeArgumentResolver}. No media type is declared on a mapping,
 * so the gateway's checks are the only ones, and the controller never touches the request stream.
 *
 * <p>The responses are built by {@link ApiResponses}, the only producer of bodies, and sent as
 * {@code application/json} set up front, so content negotiation never runs (an Accept header changes nothing and
 * there is no 406). The mutating calls pass a render function to the service: the bytes are built inside the
 * transaction, before the commit, so a rendering failure rolls the change back (D-23). A 204 has no body and no
 * Content-Type.
 */
@RestController
public class TodoController {

    private final TodoRequestPipeline pipeline;
    private final TaskService service;
    private final ApiResponses responses;

    public TodoController(TodoRequestPipeline pipeline, TaskService service, ApiResponses responses) {
        this.pipeline = pipeline;
        this.service = service;
        this.responses = responses;
    }

    @PostMapping(ApiRoutes.TODOS)
    public ResponseEntity<byte[]> create(RawBody body) {
        byte[] json = service.create(pipeline.create(body), responses::task);
        return json(HttpStatus.CREATED, json);
    }

    @GetMapping(ApiRoutes.TODOS)
    public ResponseEntity<byte[]> list() {
        return json(HttpStatus.OK, responses.tasks(service.list()));
    }

    @GetMapping(ApiRoutes.TODO_ITEM)
    public ResponseEntity<byte[]> get(RouteMatch route) {
        UUID id = pipeline.id(route);
        return json(HttpStatus.OK, responses.task(service.get(id)));
    }

    @PutMapping(ApiRoutes.TODO_ITEM)
    public ResponseEntity<byte[]> replace(RouteMatch route, RawBody body) {
        byte[] json = service.replace(pipeline.replace(route, body), responses::task);
        return json(HttpStatus.OK, json);
    }

    @DeleteMapping(ApiRoutes.TODO_ITEM)
    public ResponseEntity<byte[]> delete(RouteMatch route) {
        service.delete(pipeline.id(route));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(ApiRoutes.TODO_STATUS)
    public ResponseEntity<byte[]> setStatus(RouteMatch route, RawBody body) {
        byte[] json = service.setStatus(pipeline.setStatus(route, body), responses::task);
        return json(HttpStatus.OK, json);
    }

    private static ResponseEntity<byte[]> json(HttpStatus status, byte[] body) {
        return ResponseEntity.status(status).header(HttpHeaders.CONTENT_TYPE, ApiResponses.CONTENT_TYPE).body(body);
    }
}
