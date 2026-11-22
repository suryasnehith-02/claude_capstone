package com.capstone.todo.web.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.capstone.todo.config.JsonConfig;
import com.capstone.todo.domain.Task;
import com.capstone.todo.domain.TaskStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ApiResponsesTest {

    private static final UUID ID1 = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID ID2 = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final Instant T0 = Instant.parse("2026-01-02T03:04:05.006Z");

    private final JsonMapper mapper = JsonConfig.newApiJsonMapper();
    private final ApiResponses responses = new ApiResponses(mapper);

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static Task task(UUID id, String title, String description) {
        return new Task(5, id, title, description, TaskStatus.PENDING, T0, T0);
    }

    /** Golden bodies: the exact bytes of every error without details (message text per ErrorCode). */
    private static final Map<ErrorCode, String> GOLDEN = Map.of(
            ErrorCode.INVALID_REQUEST_BODY,
            "{\"error\":{\"code\":\"INVALID_REQUEST_BODY\",\"message\":\"Request body must be a single well-formed JSON object\"}}",
            ErrorCode.INVALID_ID,
            "{\"error\":{\"code\":\"INVALID_ID\",\"message\":\"Task id must be a UUID\"}}",
            ErrorCode.TODO_NOT_FOUND,
            "{\"error\":{\"code\":\"TODO_NOT_FOUND\",\"message\":\"Task not found\"}}",
            ErrorCode.NOT_FOUND,
            "{\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"No resource matches this path\"}}",
            ErrorCode.METHOD_NOT_ALLOWED,
            "{\"error\":{\"code\":\"METHOD_NOT_ALLOWED\",\"message\":\"Method not allowed for this path\"}}",
            ErrorCode.PAYLOAD_TOO_LARGE,
            "{\"error\":{\"code\":\"PAYLOAD_TOO_LARGE\",\"message\":\"Request body is too large\"}}",
            ErrorCode.UNSUPPORTED_MEDIA_TYPE,
            "{\"error\":{\"code\":\"UNSUPPORTED_MEDIA_TYPE\",\"message\":\"Content-Type must be application/json\"}}",
            ErrorCode.INTERNAL_ERROR,
            "{\"error\":{\"code\":\"INTERNAL_ERROR\",\"message\":\"Internal server error\"}}");

    @Test
    void everyErrorWithoutDetailsMatchesItsGoldenBodyAndHasNoDetailsMember() {
        assertThat(GOLDEN).hasSize(ErrorCode.values().length - 1);
        GOLDEN.forEach((code, golden) -> {
            assertThat(text(responses.error(code, List.of()))).as(code.name()).isEqualTo(golden);
            assertThat(text(responses.error(new ApiException(code)))).as(code.name()).isEqualTo(golden);
        });
    }

    @Test
    void validationErrorListsEveryDetailInOrder() {
        byte[] body = responses.error(ApiException.validation(List.of(
                new ErrorDetail("title", "Title is required"),
                new ErrorDetail("description", "Description must be a string"),
                new ErrorDetail("status", "Status must be Pending or Completed"))));

        assertThat(text(body)).isEqualTo("{\"error\":{\"code\":\"VALIDATION_ERROR\","
                + "\"message\":\"Request validation failed\",\"details\":["
                + "{\"field\":\"title\",\"message\":\"Title is required\"},"
                + "{\"field\":\"description\",\"message\":\"Description must be a string\"},"
                + "{\"field\":\"status\",\"message\":\"Status must be Pending or Completed\"}]}}");
    }

    @Test
    void fixedBodiesEqualTheDynamicOutputForEveryCodeWithoutDetails() {
        for (ErrorCode code : ErrorCode.values()) {
            if (code.hasDetails()) {
                continue;
            }
            assertThat(responses.fixedError(code)).as(code.name()).isEqualTo(responses.error(code, List.of()));
            assertThat(text(responses.fixedError(code))).as(code.name()).isEqualTo(GOLDEN.get(code));
        }
    }

    @Test
    void fixedBodyIsACopyAndValidationErrorHasNoFixedBody() {
        byte[] first = responses.fixedError(ErrorCode.NOT_FOUND);
        first[0] = 'X';

        assertThat(text(responses.fixedError(ErrorCode.NOT_FOUND))).isEqualTo(GOLDEN.get(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> responses.fixedError(ErrorCode.VALIDATION_ERROR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detailsRulesAreEnforcedByTheWriter() {
        assertThatThrownBy(() -> responses.error(ErrorCode.VALIDATION_ERROR, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> responses.error(ErrorCode.INVALID_ID, List.of(new ErrorDetail("id", "x"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyListIsAnEmptyDataArray() {
        assertThat(text(responses.tasks(List.of()))).isEqualTo("{\"data\":[]}");
    }

    @Test
    void listKeepsTheGivenOrder() {
        byte[] body = responses.tasks(List.of(task(ID2, "second", ""), task(ID1, "first", "")));

        JsonNode data = mapper.readTree(body).get("data");
        assertThat(data).hasSize(2);
        assertThat(data.get(0).get("id").asString()).isEqualTo(ID2.toString());
        assertThat(data.get(1).get("id").asString()).isEqualTo(ID1.toString());
    }

    @Test
    void htmlCharactersNonAsciiAndAstralCharactersAreWrittenVerbatim() {
        String title = "<b>&\"'</b>";
        String description = "café 日本語 😀 a/b";

        byte[] body = responses.task(task(ID1, title, description));

        String json = text(body);
        assertThat(json).contains("\"title\":\"<b>&\\\"'</b>\"");
        assertThat(json).contains("\"description\":\"café 日本語 😀 a/b\"");
        assertThat(json).doesNotContain("\\u003c", "\\u0026", "\\u00e9", "\\ud83d", "\\/");
        JsonNode parsed = mapper.readTree(body).get("data");
        assertThat(parsed.get("title").asString()).isEqualTo(title);
        assertThat(parsed.get("description").asString()).isEqualTo(description);
    }

    @Test
    void controlCharactersAreEscapedAndRoundTrip() {
        String title = "a\u0000b\nc\t\"d\\e\u001f";

        byte[] body = responses.task(task(ID1, title, ""));

        assertThat(text(body)).contains("a\\u0000b\\nc\\t\\\"d\\\\e\\u001F");
        assertThat(mapper.readTree(body).get("data").get("title").asString()).isEqualTo(title);
    }

    @Test
    void hostLocaleAndTimeZoneDoNotChangeAnyBody() {
        Task task = task(ID1, "t", "d");
        List<ErrorDetail> details = List.of(new ErrorDetail("title", "Title is required"));
        byte[] taskBody = responses.task(task);
        byte[] listBody = responses.tasks(List.of(task));
        byte[] errorBody = responses.error(ErrorCode.VALIDATION_ERROR, details);

        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-SA-u-nu-arab"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));

            ApiResponses fresh = new ApiResponses(JsonConfig.newApiJsonMapper());

            assertThat(fresh.task(task)).isEqualTo(taskBody);
            assertThat(fresh.tasks(List.of(task))).isEqualTo(listBody);
            assertThat(fresh.error(ErrorCode.VALIDATION_ERROR, details)).isEqualTo(errorBody);
            assertThat(fresh.fixedError(ErrorCode.INTERNAL_ERROR)).isEqualTo(responses.fixedError(ErrorCode.INTERNAL_ERROR));
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    @Test
    void theContentTypeConstantHasNoCharset() {
        assertThat(ApiResponses.CONTENT_TYPE).isEqualTo("application/json");
    }
}
