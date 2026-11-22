package com.capstone.todo.web.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.capstone.todo.config.JsonConfig;
import com.capstone.todo.domain.CreateTask;
import com.capstone.todo.domain.ReplaceTask;
import com.capstone.todo.domain.SetStatus;
import com.capstone.todo.domain.TaskStatus;
import com.capstone.todo.validation.TaskRequestValidator;
import com.capstone.todo.web.ApiRoutes;
import com.capstone.todo.web.gate.RawBody;
import com.capstone.todo.web.gate.RouteMatch;
import com.capstone.todo.web.response.ApiException;
import com.capstone.todo.web.response.ErrorCode;

import tools.jackson.databind.JsonNode;

/**
 * T-9: the pipeline runs P4, then P5, then P6, returns the typed command and has no storage access. The two collaborators
 * are wrapped so the tests can see which of them ran. The whole stage-pair table (CP-A) is {@code PrecedenceTableTest}.
 */
class TodoRequestPipelineTest {

    private static final String ID = "0b8f6f4e-3a35-4d0c-9d41-0d9f3c2a7b11";
    private static final String VALID_BODY = "{\"title\":\"  Buy milk  \",\"description\":\" two litres \"}";
    private static final String STATUS_BODY = "{\"status\":\"Completed\"}";

    /** Records that a stage ran. */
    private final List<String> ran = new ArrayList<>();

    private final TodoRequestPipeline pipeline = new TodoRequestPipeline(
            new JsonBodyReader(JsonConfig.newApiJsonMapper()) {
                @Override
                public JsonNode read(byte[] body) {
                    ran.add("P5");
                    return super.read(body);
                }
            },
            new TaskRequestValidator() {
                @Override
                public CreateTask create(JsonNode body) {
                    ran.add("P6");
                    return super.create(body);
                }

                @Override
                public ReplaceTask replace(UUID id, JsonNode body) {
                    ran.add("P6");
                    return super.replace(id, body);
                }

                @Override
                public SetStatus setStatus(UUID id, JsonNode body) {
                    ran.add("P6");
                    return super.setStatus(id, body);
                }
            });

    private static RawBody body(String text) {
        return new RawBody(text.getBytes(StandardCharsets.UTF_8), true);
    }

    private static RouteMatch item(String rawId) {
        return new RouteMatch(ApiRoutes.TODO_ITEM, rawId);
    }

    private static RouteMatch status(String rawId) {
        return new RouteMatch(ApiRoutes.TODO_STATUS, rawId);
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.errorCode()).isEqualTo(code));
    }

    // ---- typed commands -----------------------------------------------------------------------------------

    @Test
    void createReturnsTheValidatedCommand() {
        CreateTask command = pipeline.create(body(VALID_BODY));

        assertThat(command).isEqualTo(new CreateTask("Buy milk", "two litres"));
        assertThat(ran).containsExactly("P5", "P6");
    }

    @Test
    void replaceReturnsTheCommandWithTheDecodedLowercaseId() {
        ReplaceTask command = pipeline.replace(item(ID.toUpperCase()), body(VALID_BODY));

        assertThat(command).isEqualTo(new ReplaceTask(UUID.fromString(ID), "Buy milk", "two litres"));
        assertThat(command.id().toString()).isEqualTo(ID);
        assertThat(ran).containsExactly("P5", "P6");
    }

    @Test
    void setStatusReturnsTheCommand() {
        SetStatus command = pipeline.setStatus(status(ID), body(STATUS_BODY));

        assertThat(command).isEqualTo(new SetStatus(UUID.fromString(ID), TaskStatus.COMPLETED));
        assertThat(ran).containsExactly("P5", "P6");
    }

    @Test
    void idReturnsTheParsedIdAndNeverReadsABody() {
        assertThat(pipeline.id(item(ID))).isEqualTo(UUID.fromString(ID));
        assertThat(pipeline.id(item("%30b8f6f4e-3a35-4d0c-9d41-0d9f3c2a7b11")).toString()).isEqualTo(ID);
        assertThat(ran).isEmpty();
    }

    // ---- the fixed order P4, P5, P6 ----------------------------------------------------------------------------

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {VALID_BODY, "{bad", "{}", "[]", "", "{\"title\":5}"})
    void anInvalidIdWinsOverAnyBodyAndTheBodyIsNeverRead(String text) {
        assertCode(() -> pipeline.replace(item("not-a-uuid"), body(text)), ErrorCode.INVALID_ID);
        assertCode(() -> pipeline.setStatus(status("not-a-uuid"), body(text)), ErrorCode.INVALID_ID);
        assertThat(ran).isEmpty();
    }

    @Test
    void aMalformedBodyForAWellFormedIdIsAnInvalidRequestBodyAndNoFieldIsValidated() {
        assertCode(() -> pipeline.replace(item(ID), body("{bad")), ErrorCode.INVALID_REQUEST_BODY);
        assertCode(() -> pipeline.setStatus(status(ID), body("[]")), ErrorCode.INVALID_REQUEST_BODY);
        assertCode(() -> pipeline.create(body("{\"title\":5,")), ErrorCode.INVALID_REQUEST_BODY);

        assertThat(ran).containsExactly("P5", "P5", "P5");
    }

    @Test
    void aWellFormedBodyThatFailsFieldRulesIsAValidationErrorAfterP5Passed() {
        assertCode(() -> pipeline.replace(item(ID), body("{}")), ErrorCode.VALIDATION_ERROR);
        assertCode(() -> pipeline.create(body("{\"title\":5,\"description\":true}")), ErrorCode.VALIDATION_ERROR);

        assertThat(ran).containsExactly("P5", "P6", "P5", "P6");
    }

    @Test
    void patchWithAnEmptyObjectIsAValidationErrorOnStatus() {
        assertThatThrownBy(() -> pipeline.setStatus(status(ID), body("{}")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(e.details()).hasSize(1);
                    assertThat(e.details().get(0).field()).isEqualTo("status");
                });
    }

    @Test
    void anAbsentBodyIsAnInvalidRequestBodyForEveryBodyOperation() {
        RawBody none = RawBody.absent();

        assertCode(() -> pipeline.create(none), ErrorCode.INVALID_REQUEST_BODY);
        assertCode(() -> pipeline.replace(item(ID), none), ErrorCode.INVALID_REQUEST_BODY);
        assertCode(() -> pipeline.setStatus(status(ID), none), ErrorCode.INVALID_REQUEST_BODY);
    }

    @Test
    void anInvalidIdIsReportedEvenWithAnAbsentBody() {
        assertCode(() -> pipeline.replace(item("x"), RawBody.absent()), ErrorCode.INVALID_ID);
    }

    @Test
    void aRouteWithoutAnIdIsAnInvalidIdWhereAnIdIsNeeded() {
        assertCode(() -> pipeline.id(new RouteMatch(ApiRoutes.TODOS, null)), ErrorCode.INVALID_ID);
    }

    @Test
    void theIdIsReadFromTheRawSegmentOnlyAndDecodedExactlyOnce() {
        assertCode(() -> pipeline.id(item("%2530b8f6f4e-3a35-4d0c-9d41-0d9f3c2a7b11")), ErrorCode.INVALID_ID);
        assertCode(() -> pipeline.id(item("a%2Fb")), ErrorCode.INVALID_ID);
    }

    // ---- no storage access ---------------------------------------------------------------------------------

    @Test
    void thePipelineHasNoStoreOrServiceDependency() {
        Constructor<?>[] constructors = TodoRequestPipeline.class.getConstructors();
        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterTypes())
                .containsExactly(JsonBodyReader.class, TaskRequestValidator.class);
        for (Field field : TodoRequestPipeline.class.getDeclaredFields()) {
            assertThat(field.getType()).isIn(JsonBodyReader.class, TaskRequestValidator.class);
        }
    }

    @Test
    void anErrorCarriesNoRequestValue() {
        assertThatThrownBy(() -> pipeline.replace(item("secret-id-value"), body("{\"title\":\"secret-title\"")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getMessage()).doesNotContain("secret");
                    assertThat(e.getCause()).isNull();
                });
    }
}
