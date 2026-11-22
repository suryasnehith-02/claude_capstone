package com.capstone.todo.web;

import java.io.IOException;
import java.util.UUID;

import com.capstone.todo.config.JsonConfig;
import com.capstone.todo.domain.ReplaceTask;
import com.capstone.todo.domain.SetStatus;
import com.capstone.todo.domain.TaskNotFoundException;
import com.capstone.todo.support.GateRequest;
import com.capstone.todo.support.InMemoryTaskRepository;
import com.capstone.todo.validation.TaskRequestValidator;
import com.capstone.todo.web.gate.BodyGate;
import com.capstone.todo.web.gate.RawBody;
import com.capstone.todo.web.gate.RouteMatch;
import com.capstone.todo.web.gate.RouteTable;
import com.capstone.todo.web.request.JsonBodyReader;
import com.capstone.todo.web.request.TodoRequestPipeline;
import com.capstone.todo.web.response.ApiException;

/**
 * Test-only stand-in for what the servlet filter (T-12), the controller and the service (T-13, T-10) will do, so the
 * documented order P1 to P7 can be exercised before any server exists. It calls the real {@link RouteTable}, the real
 * {@link BodyGate} and the real {@link TodoRequestPipeline} in the documented order and then does P7 itself with the
 * given repository (a lookup that raises {@link TaskNotFoundException}, which is all P7 is for this purpose).
 *
 * <p>It is not the production wiring: the real filter and the real service must be proven against it later (T-12
 * CP-B on real Tomcat, T-13 CP-C). What it does prove is that the units agree on the order and that a request
 * rejected before P7 never reaches the repository.
 */
final class PrecedenceSequencer {

    /** What the client would see: status, error code or {@code OK}, the Allow value, and the storage calls made. */
    record Result(int status, String code, String allow, int repositoryCalls) {
    }

    private final RouteTable routeTable = new RouteTable();
    private final BodyGate bodyGate = new BodyGate();
    private final TodoRequestPipeline pipeline = new TodoRequestPipeline(
            new JsonBodyReader(JsonConfig.newApiJsonMapper()), new TaskRequestValidator());
    private final InMemoryTaskRepository repository;

    PrecedenceSequencer(InMemoryTaskRepository repository) {
        this.repository = repository;
    }

    Result run(GateRequest request) {
        try {
            return decide(request);
        } catch (ApiException e) {
            return new Result(e.errorCode().status(), e.errorCode().code(), null, repository.calls());
        } catch (TaskNotFoundException e) {
            return new Result(404, "TODO_NOT_FOUND", null, repository.calls());
        }
    }

    private Result decide(GateRequest request) {
        // P1 (filter): route and method.
        RouteTable.Outcome outcome = routeTable.resolve(request.getMethod(), request.getRequestURI());
        if (outcome instanceof RouteTable.Outcome.Rejected rejected) {
            return new Result(rejected.errorCode().status(), rejected.errorCode().code(), rejected.allow(),
                    repository.calls());
        }
        RouteMatch route = ((RouteTable.Outcome.Matched) outcome).match();

        // P2 and P3 (filter): size, then media type.
        RawBody body;
        try {
            body = bodyGate.check(request);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        // P4 to P6 (controller, through the pipeline), then P7 (service).
        switch (request.getMethod() + " " + route.route()) {
            case "POST " + ApiRoutes.TODOS -> pipeline.create(body);
            case "GET " + ApiRoutes.TODOS -> repository.findAllOrdered();
            case "GET " + ApiRoutes.TODO_ITEM -> requireTask(pipeline.id(route));
            case "PUT " + ApiRoutes.TODO_ITEM -> {
                ReplaceTask command = pipeline.replace(route, body);
                requireTask(command.id());
            }
            case "DELETE " + ApiRoutes.TODO_ITEM -> {
                if (!repository.delete(pipeline.id(route))) {
                    throw new TaskNotFoundException();
                }
            }
            case "PATCH " + ApiRoutes.TODO_STATUS -> {
                SetStatus command = pipeline.setStatus(route, body);
                requireTask(command.id());
            }
            default -> throw new IllegalStateException("RouteTable matched a route this stand-in does not know");
        }
        return new Result(200, "OK", null, repository.calls());
    }

    private void requireTask(UUID id) {
        repository.findById(id).orElseThrow(TaskNotFoundException::new);
    }
}
