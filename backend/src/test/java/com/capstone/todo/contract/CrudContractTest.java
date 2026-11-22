package com.capstone.todo.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.capstone.todo.config.JsonConfig;
import com.capstone.todo.domain.Task;
import com.capstone.todo.domain.TaskRepository;
import com.capstone.todo.domain.TaskStatus;
import com.capstone.todo.support.FixedClock;
import com.capstone.todo.support.RawHttp;
import com.capstone.todo.support.TestDataDir;
import com.capstone.todo.web.ApiExceptionHandler;
import com.capstone.todo.web.response.ApiResponses;
import com.capstone.todo.web.response.ErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The six operations against the real controller, real Tomcat and a real SQLite file (T-13, success and error cases
 * of AC-1, AC-4 to AC-11, AC-15, AC-17 to AC-21, AC-23, AC-27 to AC-31; T-HDR, T-ACC and T-CORS on every operation).
 * The clock is a test clock that only moves when a test says so, so every timestamp and every tie is exact.
 *
 * <p>The error precedence table over HTTP (every pair of stages, T-P1a to T-P7) is the next delegation's
 * {@code PrecedenceContractTest}; here an error is checked only for the code, the exact envelope, the headers and that
 * the stored tasks did not change.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CrudContractTest {

    private static final Path DATA_FILE = TestDataDir.newDataFile(CrudContractTest.class);
    private static final Instant START = Instant.parse("2026-03-04T05:06:07.008Z");
    private static final String KNOWN_ID = "0b8f6f4e-3a35-4d0c-9d41-0d9f3c2a7b11";
    private static final String MISSING_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    private static final JsonMapper JSON = JsonConfig.newApiJsonMapper();

    private static final String STR = "\"(?:[^\"\\\\]|\\\\.)*\"";
    private static final String TS = "\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z\"";
    private static final String TASK = "\\{\"id\":\"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\""
            + ",\"title\":" + STR + ",\"description\":" + STR + ",\"status\":\"(?:Pending|Completed)\""
            + ",\"createdAt\":" + TS + ",\"updatedAt\":" + TS + "\\}";
    private static final Pattern ONE_TASK = Pattern.compile("\\{\"data\":" + TASK + "\\}");
    private static final Pattern TASK_LIST = Pattern.compile("\\{\"data\":\\[(?:" + TASK + "(?:," + TASK + ")*)?\\]\\}");

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean
        @Primary
        FixedClock testClock() {
            return new FixedClock(START);
        }
    }

    @DynamicPropertySource
    static void dataFile(DynamicPropertyRegistry registry) {
        registry.add("todo.data-file", DATA_FILE::toString);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ApiResponses responses;

    @Autowired
    private TaskRepository repository;

    @Autowired
    private FixedClock clock;

    @BeforeEach
    void emptyStoreAndResetClock() {
        repository.findAllOrdered().forEach(task -> repository.delete(task.id()));
        clock.set(START);
    }

    // ---------------------------------------------------------------- helpers

    private record TaskView(String id, String title, String description, String status, String createdAt,
            String updatedAt) {

        Instant created() {
            return Instant.parse(createdAt);
        }

        Instant updated() {
            return Instant.parse(updatedAt);
        }
    }

    private RawHttp.Response call(String method, String target, String contentType, String body) {
        RawHttp.Request request = RawHttp.request(method, target);
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        if (body != null) {
            request.body(body);
        }
        return request.send(port);
    }

    private RawHttp.Response json(String method, String target, String body) {
        return call(method, target, "application/json", body);
    }

    private RawHttp.Response get(String target) {
        return call("GET", target, null, null);
    }

    private static JsonNode tree(RawHttp.Response response) {
        return JSON.readTree(response.bodyText());
    }

    private static TaskView view(JsonNode task) {
        return new TaskView(task.get("id").asString(), task.get("title").asString(), task.get("description").asString(),
                task.get("status").asString(), task.get("createdAt").asString(), task.get("updatedAt").asString());
    }

    private void assertJsonHeaders(RawHttp.Response response) {
        assertThat(response.headers("Content-Type")).as("Content-Type").containsExactly("application/json");
        assertThat(response.headers("X-Content-Type-Options")).as("nosniff").containsExactly("nosniff");
        assertThat(response.headers()).as("no CORS header").noneMatch(h -> h[0].toLowerCase(Locale.ROOT).startsWith("access-control-"));
    }

    /** Asserts a success answer with a single task in the exact shape and returns it. */
    private TaskView oneTask(RawHttp.Response response, int status) {
        assertThat(response.status()).as(response.bodyText()).isEqualTo(status);
        assertJsonHeaders(response);
        assertThat(response.bodyText()).as("body shape").matches(ONE_TASK);
        return view(tree(response).get("data"));
    }

    private List<TaskView> tasks(RawHttp.Response response) {
        assertThat(response.status()).as(response.bodyText()).isEqualTo(200);
        assertJsonHeaders(response);
        assertThat(response.bodyText()).as("body shape").matches(TASK_LIST);
        List<TaskView> result = new ArrayList<>();
        tree(response).get("data").forEach(node -> result.add(view(node)));
        return result;
    }

    private TaskView create(String body) {
        return oneTask(json("POST", "/todos", body), 201);
    }

    private TaskView create(String title, String description) {
        return create("{\"title\":" + quote(title) + ",\"description\":" + quote(description) + "}");
    }

    /** Creates a task with this title and no description. */
    private TaskView createTitled(String title) {
        return create("{\"title\":" + quote(title) + "}");
    }

    private TaskView fetch(String id) {
        return oneTask(get("/todos/" + id), 200);
    }

    private static String quote(String text) {
        return JSON.writeValueAsString(text);
    }

    private void assertError(RawHttp.Response response, ErrorCode code) {
        assertThat(response.status()).as(response.bodyText()).isEqualTo(code.status());
        assertJsonHeaders(response);
        assertThat(response.body()).isEqualTo(responses.fixedError(code));
    }

    private void assertValidation(RawHttp.Response response, String... fieldAndMessage) {
        StringBuilder expected = new StringBuilder(
                "{\"error\":{\"code\":\"VALIDATION_ERROR\",\"message\":\"Request validation failed\",\"details\":[");
        for (int i = 0; i < fieldAndMessage.length; i += 2) {
            expected.append(i == 0 ? "" : ",").append("{\"field\":\"").append(fieldAndMessage[i]).append("\",\"message\":\"")
                    .append(fieldAndMessage[i + 1]).append("\"}");
        }
        expected.append("]}}");
        assertThat(response.status()).isEqualTo(400);
        assertJsonHeaders(response);
        assertThat(response.bodyText()).isEqualTo(expected.toString());
    }

    private String listText() {
        RawHttp.Response response = get("/todos");
        assertThat(response.status()).isEqualTo(200);
        return response.bodyText();
    }

    private void seedKnownTask() {
        repository.insert(new Task(Task.UNASSIGNED_SEQ, UUID.fromString(KNOWN_ID), "seed", "", TaskStatus.PENDING, START, START));
    }

    private static String repeat(String unit, int times) {
        return unit.repeat(times);
    }

    private static String astral() {
        return new String(Character.toChars(0x1F600));
    }

    // ---------------------------------------------------------------- AC-1, AC-4, AC-19, AC-21, AC-27: create

    @Test
    void createReturns201WithTheTrimmedTaskInTheDataEnvelopeAndTheTaskIsListed_ac1_ac19_ac27() {
        RawHttp.Response response = json("POST", "/todos", "{\"title\":\"  Buy milk  \",\"description\":\"  2 litres  \"}");

        TaskView task = oneTask(response, 201);

        assertThat(task.title()).isEqualTo("Buy milk");
        assertThat(task.description()).isEqualTo("2 litres");
        assertThat(task.status()).isEqualTo("Pending");
        assertThat(task.createdAt()).isEqualTo("2026-03-04T05:06:07.008Z");
        assertThat(task.updatedAt()).isEqualTo(task.createdAt());
        assertThat(task.id()).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
        assertThat(tasks(get("/todos"))).containsExactly(task);
        assertThat(fetch(task.id())).isEqualTo(task);
    }

    @Test
    void aZeroMillisecondPartIsStillPrintedWithThreeDigits_ac27() {
        clock.set(Instant.parse("2026-12-31T23:59:59.000Z"));

        TaskView task = create("{\"title\":\"x\"}");

        assertThat(task.createdAt()).isEqualTo("2026-12-31T23:59:59.000Z");
        assertThat(task.updatedAt()).isEqualTo("2026-12-31T23:59:59.000Z");
    }

    @Test
    void idStatusAndTimestampsInTheBodyAreIgnoredOnCreate_ac4() {
        RawHttp.Response response = json("POST", "/todos", "{\"title\":\"t\",\"id\":\"" + MISSING_ID
                + "\",\"status\":\"Completed\",\"createdAt\":\"2000-01-01T00:00:00.000Z\","
                + "\"updatedAt\":\"2000-01-01T00:00:00.000Z\",\"extra\":{\"nested\":[1,2,3]}}");

        TaskView task = oneTask(response, 201);

        assertThat(task.id()).isNotEqualTo(MISSING_ID);
        assertThat(task.status()).isEqualTo("Pending");
        assertThat(task.createdAt()).isEqualTo("2026-03-04T05:06:07.008Z");
        assertThat(task.updatedAt()).isEqualTo("2026-03-04T05:06:07.008Z");
    }

    @Test
    void anAbsentNullEmptyOrWhitespaceOnlyDescriptionIsAlwaysTheEmptyStringOnEveryRead_ac21() {
        List<String> bodies = List.of("{\"title\":\"a\"}", "{\"title\":\"b\",\"description\":null}",
                "{\"title\":\"c\",\"description\":\"\"}", "{\"title\":\"d\",\"description\":\" \\t\\n \"}");
        for (String body : bodies) {
            TaskView created = create(body);

            assertThat(created.description()).as(body).isEmpty();
            assertThat(fetch(created.id()).description()).as(body).isEmpty();
        }
        assertThat(tasks(get("/todos"))).hasSize(4).allSatisfy(task -> assertThat(task.description()).isEmpty());
    }

    @Test
    void twoTasksWithTheSameTitleAreBothCreatedWithDistinctIds_ac31() {
        TaskView first = create("{\"title\":\"same\"}");
        TaskView second = create("{\"title\":\"same\"}");

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(tasks(get("/todos"))).extracting(TaskView::id).containsExactlyInAnyOrder(first.id(), second.id());
    }

    // ---------------------------------------------------------------- AC-5: list

    @Test
    void anEmptyStoreListsAnEmptyArray_ac5() {
        RawHttp.Response response = get("/todos");

        assertThat(response.status()).isEqualTo(200);
        assertJsonHeaders(response);
        assertThat(response.bodyText()).isEqualTo("{\"data\":[]}");
    }

    @Test
    void theListIsNewestFirstAndAnUpdateDoesNotMoveATask_ac5() {
        TaskView oldest = create("{\"title\":\"oldest\"}");
        clock.advance(Duration.ofSeconds(1));
        TaskView middle = create("{\"title\":\"middle\"}");
        clock.advance(Duration.ofSeconds(1));
        TaskView newest = create("{\"title\":\"newest\"}");
        clock.advance(Duration.ofSeconds(1));

        assertThat(tasks(get("/todos"))).extracting(TaskView::id).containsExactly(newest.id(), middle.id(), oldest.id());

        oneTask(json("PATCH", "/todos/" + oldest.id() + "/status", "{\"status\":\"Completed\"}"), 200);
        oneTask(json("PUT", "/todos/" + middle.id(), "{\"title\":\"edited\"}"), 200);

        assertThat(tasks(get("/todos"))).extracting(TaskView::id).containsExactly(newest.id(), middle.id(), oldest.id());
    }

    @Test
    void tasksCreatedInTheSameMillisecondAreOrderedByInsertionNewestFirst_d8() {
        List<String> created = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            created.add(create("{\"title\":\"t" + i + "\"}").id());
        }

        List<String> expected = new ArrayList<>(created);
        java.util.Collections.reverse(expected);
        assertThat(tasks(get("/todos"))).extracting(TaskView::id).containsExactlyElementsOf(expected);
        assertThat(tasks(get("/todos"))).extracting(TaskView::createdAt).containsOnly("2026-03-04T05:06:07.008Z");
    }

    // ---------------------------------------------------------------- AC-6, AC-15: get by id

    @Test
    void getReturnsTheTaskAndAnUnknownIdIsTodoNotFound_ac6() {
        TaskView task = create("{\"title\":\"find me\",\"description\":\"here\"}");

        assertThat(fetch(task.id())).isEqualTo(task);
        assertError(get("/todos/" + MISSING_ID), ErrorCode.TODO_NOT_FOUND);
    }

    @Test
    void anInvalidIdOnEveryIdOperationIsInvalidIdAndNothingChanges_ac6_ac15() {
        TaskView task = create("{\"title\":\"keep\"}");
        String before = listText();

        for (String bad : List.of("not-a-uuid", "1-1-1-1-1", "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz", "0b8f6f4e3a354d0c9d410d9f3c2a7b11")) {
            assertError(get("/todos/" + bad), ErrorCode.INVALID_ID);
            assertError(json("PUT", "/todos/" + bad, "{\"title\":\"x\"}"), ErrorCode.INVALID_ID);
            assertError(json("PATCH", "/todos/" + bad + "/status", "{\"status\":\"Completed\"}"), ErrorCode.INVALID_ID);
            assertError(call("DELETE", "/todos/" + bad, null, null), ErrorCode.INVALID_ID);
        }
        assertThat(listText()).isEqualTo(before);
        assertThat(fetch(task.id())).isEqualTo(task);
    }

    @Test
    void anUppercaseAndAPercentEncodedHexSpellingFindTheSameTask_g2_5f() {
        seedKnownTask();
        TaskView task = fetch(KNOWN_ID);

        assertThat(fetch(KNOWN_ID.toUpperCase(Locale.ROOT))).isEqualTo(task);
        assertThat(fetch(KNOWN_ID.replaceFirst("b", "%62"))).isEqualTo(task);
        assertThat(fetch(KNOWN_ID.replace("f", "%66").replace("a", "%61"))).isEqualTo(task);
        assertThat(fetch(KNOWN_ID.replace("-", "%2D"))).isEqualTo(task);
    }

    @Test
    void anEncodedSlashAnEncodedBackslashAndADoubleEncodedPercentReachTheIdCheckAndAreInvalidId_v2_v19() {
        for (String bad : List.of("a%2Fb", "a%5Cb", "%2530b8f6f4e-3a35-4d0c-9d41-0d9f3c2a7b11", "%2f", "%2F%2F",
                "a%20b", "%2e%2e")) {
            assertError(get("/todos/" + bad), ErrorCode.INVALID_ID);
            assertError(call("DELETE", "/todos/" + bad, null, null), ErrorCode.INVALID_ID);
            assertError(json("PUT", "/todos/" + bad, "{\"title\":\"x\"}"), ErrorCode.INVALID_ID);
            assertError(json("PATCH", "/todos/" + bad + "/status", "{\"status\":\"Pending\"}"), ErrorCode.INVALID_ID);
        }
    }

    // ---------------------------------------------------------------- AC-7, AC-8, AC-29, AC-27: replace

    @Test
    void putReplacesTitleAndDescriptionAndKeepsStatusAndCreatedAt_ac7() {
        TaskView task = create("old", "old description");
        clock.advance(Duration.ofSeconds(5));
        oneTask(json("PATCH", "/todos/" + task.id() + "/status", "{\"status\":\"Completed\"}"), 200);
        clock.advance(Duration.ofSeconds(5));

        TaskView updated = oneTask(json("PUT", "/todos/" + task.id(),
                "{\"title\":\"  new  \",\"description\":\"  new description  \"}"), 200);

        assertThat(updated.id()).isEqualTo(task.id());
        assertThat(updated.title()).isEqualTo("new");
        assertThat(updated.description()).isEqualTo("new description");
        assertThat(updated.status()).isEqualTo("Completed");
        assertThat(updated.createdAt()).isEqualTo(task.createdAt());
        assertThat(updated.updatedAt()).isEqualTo("2026-03-04T05:06:17.008Z");
        assertThat(updated.updated()).isAfter(task.updated());
        assertThat(fetch(task.id())).isEqualTo(updated);
    }

    @Test
    void aPutWithoutADescriptionClearsItAndAPutWithoutATitleIsRejectedAndChangesNothing_ac29() {
        TaskView task = create("title", "a description");

        TaskView cleared = oneTask(json("PUT", "/todos/" + task.id(), "{\"title\":\"title\"}"), 200);
        assertThat(cleared.description()).isEmpty();
        assertThat(fetch(task.id()).description()).isEmpty();

        String before = listText();
        assertValidation(json("PUT", "/todos/" + task.id(), "{\"description\":\"only\"}"), "title", "Title is required");
        assertValidation(json("PUT", "/todos/" + task.id(), "{\"title\":null}"), "title", "Title is required");
        assertThat(listText()).isEqualTo(before);
    }

    @Test
    void everySuccessfulPutAndPatchMovesUpdatedAtStrictlyForwardEvenWhenNothingChangesAndTheClockStandsStill_ac27() {
        TaskView task = create("same", "same");
        Instant previous = task.updated();

        for (int i = 1; i <= 3; i++) {
            TaskView afterPut = oneTask(json("PUT", "/todos/" + task.id(), "{\"title\":\"same\",\"description\":\"same\"}"), 200);
            assertThat(afterPut.updated()).as("no-op PUT " + i).isEqualTo(previous.plusMillis(1)).isAfter(previous);
            previous = afterPut.updated();

            TaskView afterPatch = oneTask(json("PATCH", "/todos/" + task.id() + "/status", "{\"status\":\"Pending\"}"), 200);
            assertThat(afterPatch.updated()).as("no-op PATCH " + i).isEqualTo(previous.plusMillis(1)).isAfter(previous);
            previous = afterPatch.updated();
            assertThat(afterPatch.createdAt()).isEqualTo(task.createdAt());
        }
        assertThat(previous).isEqualTo(START.plusMillis(6));
    }

    @Test
    void whenTheClockMovesForwardUpdatedAtFollowsItAndWhenItStepsBackUpdatedAtStillMovesForward_ac27() {
        TaskView task = create("t", "d");

        clock.set(Instant.parse("2026-03-04T05:06:30.500Z"));
        TaskView later = oneTask(json("PUT", "/todos/" + task.id(), "{\"title\":\"t\",\"description\":\"d\"}"), 200);
        assertThat(later.updatedAt()).isEqualTo("2026-03-04T05:06:30.500Z");

        clock.set(Instant.parse("2020-01-01T00:00:00.000Z"));
        TaskView back = oneTask(json("PATCH", "/todos/" + task.id() + "/status", "{\"status\":\"Completed\"}"), 200);
        assertThat(back.updatedAt()).isEqualTo("2026-03-04T05:06:30.501Z");
        assertThat(back.createdAt()).isEqualTo(task.createdAt());
    }

    @Test
    void invalidValuesOnPutAreRejectedWithTheCap1WordingAndTheStoredTaskIsUnchanged_ac8_ac20() {
        TaskView task = create("keep", "keep");
        String before = listText();

        assertValidation(json("PUT", "/todos/" + task.id(), "{\"title\":\"   \"}"), "title", "Title is required");
        assertValidation(json("PUT", "/todos/" + task.id(), "{\"title\":\"" + repeat("a", 101) + "\"}"), "title",
                "Title must be 100 characters or fewer");
        assertValidation(json("PUT", "/todos/" + task.id(), "{\"title\":5}"), "title", "Title must be a string");
        assertValidation(json("PUT", "/todos/" + task.id(), "{\"title\":\"ok\",\"description\":\"" + repeat("d", 501) + "\"}"),
                "description", "Description must be 500 characters or fewer");
        assertValidation(json("PUT", "/todos/" + task.id(), "{\"title\":\"ok\",\"description\":false}"), "description",
                "Description must be a string");

        assertThat(listText()).isEqualTo(before);
        assertThat(fetch(task.id())).isEqualTo(task);
    }

    @Test
    void putToAnUnknownIdIsTodoNotFoundButAnInvalidBodyForThatIdIsStillA400_ac8_ac25() {
        assertError(json("PUT", "/todos/" + MISSING_ID, "{\"title\":\"valid\"}"), ErrorCode.TODO_NOT_FOUND);
        assertValidation(json("PUT", "/todos/" + MISSING_ID, "{}"), "title", "Title is required");
        assertError(json("PUT", "/todos/" + MISSING_ID, "{bad"), ErrorCode.INVALID_REQUEST_BODY);
        assertThat(listText()).isEqualTo("{\"data\":[]}");
    }

    @Test
    void anIdAndCreatedAtInTheBodyOfAPutOrPatchAreIgnoredAndTheStoredOnesStay_ac18() {
        TaskView task = create("t", "d");
        clock.advance(Duration.ofSeconds(1));

        TaskView put = oneTask(json("PUT", "/todos/" + task.id(), "{\"title\":\"t2\",\"id\":\"" + MISSING_ID
                + "\",\"createdAt\":\"1999-01-01T00:00:00.000Z\",\"status\":\"Completed\",\"updatedAt\":\"x\"}"), 200);
        assertThat(put.id()).isEqualTo(task.id());
        assertThat(put.createdAt()).isEqualTo(task.createdAt());
        assertThat(put.status()).isEqualTo("Pending");

        TaskView patch = oneTask(json("PATCH", "/todos/" + task.id() + "/status", "{\"status\":\"Completed\",\"id\":\""
                + MISSING_ID + "\",\"title\":\"ignored\",\"createdAt\":\"1999-01-01T00:00:00.000Z\"}"), 200);
        assertThat(patch.id()).isEqualTo(task.id());
        assertThat(patch.title()).isEqualTo("t2");
        assertThat(patch.createdAt()).isEqualTo(task.createdAt());
        assertThat(tasks(get("/todos"))).hasSize(1);
        assertError(get("/todos/" + MISSING_ID), ErrorCode.TODO_NOT_FOUND);
    }

    // ---------------------------------------------------------------- AC-9: delete

    @Test
    void deleteIs204WithNoBodyAndNoContentTypeAndTheTaskIsGone_ac9() {
        TaskView doomed = createTitled("doomed");
        TaskView stays = createTitled("stays");

        RawHttp.Response response = call("DELETE", "/todos/" + doomed.id(), null, null);

        assertThat(response.status()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        assertThat(response.headers("Content-Type")).as("no Content-Type on a 204").isEmpty();
        assertThat(response.headers("X-Content-Type-Options")).containsExactly("nosniff");
        assertError(get("/todos/" + doomed.id()), ErrorCode.TODO_NOT_FOUND);
        assertThat(tasks(get("/todos"))).containsExactly(stays);
    }

    @Test
    void deletingAnUnknownOrAlreadyDeletedIdIsTodoNotFound_ac9_a5() {
        TaskView task = createTitled("once");

        assertThat(call("DELETE", "/todos/" + task.id(), null, null).status()).isEqualTo(204);
        assertError(call("DELETE", "/todos/" + task.id(), null, null), ErrorCode.TODO_NOT_FOUND);
        assertError(call("DELETE", "/todos/" + MISSING_ID, null, null), ErrorCode.TODO_NOT_FOUND);
    }

    // ---------------------------------------------------------------- AC-10, AC-11, AC-30: status

    @Test
    void statusMovesBetweenPendingAndCompletedAndASameValueIsStill200WithALaterUpdatedAt_ac10() {
        TaskView task = create("t", "d");
        clock.advance(Duration.ofSeconds(1));

        TaskView done = oneTask(json("PATCH", "/todos/" + task.id() + "/status", "{\"status\":\"Completed\"}"), 200);
        assertThat(done.status()).isEqualTo("Completed");
        assertThat(done.updatedAt()).isEqualTo("2026-03-04T05:06:08.008Z");
        assertThat(done.title()).isEqualTo("t");
        assertThat(done.description()).isEqualTo("d");
        assertThat(done.createdAt()).isEqualTo(task.createdAt());

        TaskView again = oneTask(json("PATCH", "/todos/" + task.id() + "/status", "{\"status\":\"Completed\"}"), 200);
        assertThat(again.status()).isEqualTo("Completed");
        assertThat(again.updated()).isAfter(done.updated());

        clock.advance(Duration.ofSeconds(1));
        TaskView back = oneTask(json("PATCH", "/todos/" + task.id() + "/status", "{\"status\":\"Pending\"}"), 200);
        assertThat(back.status()).isEqualTo("Pending");
        assertThat(back.updated()).isAfter(again.updated());
        assertThat(fetch(task.id())).isEqualTo(back);
    }

    @Test
    void aMissingOrWrongStatusIsAValidationErrorOnFieldStatusAndTheTaskIsUnchanged_ac11_ac30() {
        TaskView task = createTitled("t");
        String before = listText();

        for (String body : List.of("{}", "{\"status\":null}", "{\"other\":\"Completed\"}")) {
            assertValidation(json("PATCH", "/todos/" + task.id() + "/status", body), "status", "Status is required");
        }
        for (String body : List.of("{\"status\":\"pending\"}", "{\"status\":\"COMPLETED\"}", "{\"status\":\"completed\"}",
                "{\"status\":\" Pending\"}", "{\"status\":\"Pending \"}", "{\"status\":\"\"}", "{\"status\":\"Done\"}",
                "{\"status\":5}", "{\"status\":true}", "{\"status\":[\"Pending\"]}", "{\"status\":{\"a\":1}}")) {
            assertValidation(json("PATCH", "/todos/" + task.id() + "/status", body), "status",
                    "Status must be Pending or Completed");
        }
        assertThat(listText()).isEqualTo(before);
        assertThat(fetch(task.id())).isEqualTo(task);
    }

    @Test
    void aStatusUpdateForAnUnknownIdIsTodoNotFound_ac11() {
        assertError(json("PATCH", "/todos/" + MISSING_ID + "/status", "{\"status\":\"Completed\"}"), ErrorCode.TODO_NOT_FOUND);
    }

    // ---------------------------------------------------------------- AC-20, AC-28: validation details and limits

    @Test
    void everyFailingFieldIsListedTogetherInTheOrderTitleThenDescription_ac20() {
        assertValidation(json("POST", "/todos", "{\"title\":5,\"description\":true}"), "title", "Title must be a string",
                "description", "Description must be a string");
        assertValidation(json("POST", "/todos", "{\"title\":\"  \",\"description\":\"" + repeat("d", 501) + "\"}"), "title",
                "Title is required", "description", "Description must be 500 characters or fewer");
        assertValidation(json("POST", "/todos", "{\"description\":\"" + repeat("d", 501) + "\",\"title\":\""
                + repeat("t", 101) + "\"}"), "title", "Title must be 100 characters or fewer", "description",
                "Description must be 500 characters or fewer");
        assertValidation(json("POST", "/todos", "{}"), "title", "Title is required");
        assertValidation(json("POST", "/todos", "{\"title\":\"ok\",\"description\":[]}"), "description",
                "Description must be a string");
        assertThat(listText()).isEqualTo("{\"data\":[]}");
    }

    @Test
    void detailsExistOnlyOnAValidationError_ac20() {
        for (RawHttp.Response response : List.of(get("/todos/" + MISSING_ID), get("/todos/bad"),
                json("POST", "/todos", "{bad"), call("POST", "/todos", "text/plain", "x"), call("GET", "/nope", null, null),
                call("PUT", "/todos", null, null))) {
            assertThat(response.bodyText()).doesNotContain("details");
        }
    }

    @Test
    void theLimitsAreCountedInCodePointsAfterTrimming_ac28() {
        String pad = " \u00A0\u3000\\t ";
        assertThat(create("{\"title\":\"" + repeat("a", 100) + "\",\"description\":\"" + repeat("b", 500) + "\"}").title())
                .hasSize(100);
        assertThat(create("{\"title\":\"" + pad + repeat("a", 100) + pad + "\"}").title()).hasSize(100);
        assertValidation(json("POST", "/todos", "{\"title\":\"" + repeat("a", 101) + "\"}"), "title",
                "Title must be 100 characters or fewer");
        assertValidation(json("POST", "/todos", "{\"title\":\"x\",\"description\":\"" + repeat("b", 501) + "\"}"),
                "description", "Description must be 500 characters or fewer");

        // 100 astral characters are 200 UTF-16 units and 400 UTF-8 bytes but 100 code points.
        String hundredAstral = repeat(astral(), 100);
        TaskView astralTask = create("{\"title\":\"" + hundredAstral + "\",\"description\":\"" + repeat(astral(), 500) + "\"}");
        assertThat(astralTask.title()).isEqualTo(hundredAstral);
        assertThat(astralTask.description().codePointCount(0, astralTask.description().length())).isEqualTo(500);
        assertValidation(json("POST", "/todos", "{\"title\":\"" + repeat(astral(), 101) + "\"}"), "title",
                "Title must be 100 characters or fewer");

        // The same characters written as surrogate-pair escapes count the same.
        assertThat(create("{\"title\":\"" + repeat("\\ud83d\\ude00", 100) + "\"}").title()).isEqualTo(hundredAstral);
    }

    @Test
    void textIsReturnedVerbatimWithoutHtmlEscapingOrNonAsciiEscaping_ac33() {
        String title = "<b>&\"'</b> caf\u00E9 \u65E5\u672C\u8A9E " + astral() + " /slash";
        RawHttp.Response response = json("POST", "/todos", "{\"title\":" + quote(title) + "}");

        TaskView task = oneTask(response, 201);

        assertThat(task.title()).isEqualTo(title);
        assertThat(response.bodyText()).contains("<b>&\\\"'</b>").contains("caf\u00E9").contains(astral()).contains("/slash");
        assertThat(fetch(task.id()).title()).isEqualTo(title);
    }

    // ---------------------------------------------------------------- AC-17, AC-23: bodies that are not objects

    @Test
    void aBodyThatIsNotOneJsonObjectIsInvalidRequestBodyOnEveryBodyOperation_ac3_ac17_ac23() {
        TaskView task = createTitled("t");
        String before = listText();
        List<String> bad = List.of("{bad", "", "   ", "[]", "[{\"title\":\"x\"}]", "\"x\"", "5", "null", "true",
                "{\"title\":\"x\"} {}", "{\"title\":\"x\"},", "{'title':'x'}", "{\"title\":\"x\",}", "{\"title\":\"\\ud800\"}");
        for (String body : bad) {
            assertError(json("POST", "/todos", body), ErrorCode.INVALID_REQUEST_BODY);
            assertError(json("PUT", "/todos/" + task.id(), body), ErrorCode.INVALID_REQUEST_BODY);
            assertError(json("PATCH", "/todos/" + task.id() + "/status", body), ErrorCode.INVALID_REQUEST_BODY);
        }
        assertError(call("POST", "/todos", "application/json", null), ErrorCode.INVALID_REQUEST_BODY);
        assertThat(listText()).isEqualTo(before);
    }

    @Test
    void anEmptyObjectIsAValidationErrorAndNotABodyError_ac23() {
        TaskView task = createTitled("t");

        assertValidation(json("POST", "/todos", "{}"), "title", "Title is required");
        assertValidation(json("PUT", "/todos/" + task.id(), "{}"), "title", "Title is required");
        assertValidation(json("PATCH", "/todos/" + task.id() + "/status", "{}"), "status", "Status is required");
    }

    @Test
    void aBomInvalidUtf8AndDuplicateKeysAreHandledByTheBodyRules() {
        assertError(RawHttp.request("POST", "/todos").header("Content-Type", "application/json")
                .body(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '{', '}'}).send(port), ErrorCode.INVALID_REQUEST_BODY);
        assertError(RawHttp.request("POST", "/todos").header("Content-Type", "application/json")
                .body(new byte[] {'{', '"', 't', 'i', 't', 'l', 'e', '"', ':', '"', (byte) 0xFF, '"', '}'}).send(port),
                ErrorCode.INVALID_REQUEST_BODY);
        assertThat(create("{\"title\":\"first\",\"title\":\"last\"}").title()).isEqualTo("last");
    }

    // ---------------------------------------------------------------- headers, Accept, CORS, query strings

    private record Operation(String name, BiFunction<String, String[], RawHttp.Response> run, int status,
            boolean hasBody) {
    }

    /** Sends one operation with extra request headers (alternating names and values) against the given task id. */
    private RawHttp.Response send(String method, String target, String body, String... headers) {
        RawHttp.Request request = RawHttp.request(method, target);
        if (body != null) {
            request.header("Content-Type", "application/json").body(body);
        }
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        return request.send(port);
    }

    private List<Operation> operations() {
        return List.of(
                new Operation("POST /todos", (id, h) -> send("POST", "/todos", "{\"title\":\"x\"}", h), 201, true),
                new Operation("GET /todos", (id, h) -> send("GET", "/todos", null, h), 200, true),
                new Operation("GET /todos/{id}", (id, h) -> send("GET", "/todos/" + id, null, h), 200, true),
                new Operation("PUT /todos/{id}", (id, h) -> send("PUT", "/todos/" + id, "{\"title\":\"y\"}", h), 200, true),
                new Operation("PATCH /todos/{id}/status",
                        (id, h) -> send("PATCH", "/todos/" + id + "/status", "{\"status\":\"Completed\"}", h), 200, true),
                new Operation("DELETE /todos/{id}", (id, h) -> send("DELETE", "/todos/" + id, null, h), 204, false));
    }

    @Test
    void everyOperationAnswersTheSameForAnyAcceptHeaderAndAnOriginHeaderAddsNoCorsHeaders_tacc_tcors_thdr() {
        List<String[]> variants = List.of(new String[0], new String[] {"Accept", "text/html"},
                new String[] {"Accept", "application/xml"}, new String[] {"Accept", "image/png, text/plain;q=0.1"},
                new String[] {"Accept", "*/*"}, new String[] {"Origin", "https://example.test"},
                new String[] {"Origin", "https://example.test", "Accept", "text/html"});
        for (Operation operation : operations()) {
            for (String[] headers : variants) {
                TaskView target = createTitled("target");
                RawHttp.Response response = operation.run().apply(target.id(), headers);
                String where = operation.name() + " with " + String.join(" ", headers);

                assertThat(response.status()).as(where).isEqualTo(operation.status());
                assertThat(response.headers("X-Content-Type-Options")).as(where).containsExactly("nosniff");
                assertThat(response.headers()).as(where)
                        .noneMatch(h -> h[0].toLowerCase(Locale.ROOT).startsWith("access-control-"));
                assertThat(response.header("Vary")).as(where + " Vary").isNull();
                if (operation.hasBody()) {
                    assertThat(response.headers("Content-Type")).as(where).containsExactly("application/json");
                    assertThat(response.bodyText()).as(where).startsWith("{\"data\":");
                } else {
                    assertThat(response.headers("Content-Type")).as(where).isEmpty();
                    assertThat(response.body()).as(where).isEmpty();
                }
            }
        }
    }

    @Test
    void errorsAnswerTheSameForAnyAcceptHeader_tacc() {
        for (String accept : List.of("text/html", "application/xml", "image/png")) {
            assertError(send("GET", "/todos/" + MISSING_ID, null, "Accept", accept), ErrorCode.TODO_NOT_FOUND);
            assertError(send("GET", "/todos/bad", null, "Accept", accept), ErrorCode.INVALID_ID);
            assertError(send("POST", "/todos", "{bad", "Accept", accept), ErrorCode.INVALID_REQUEST_BODY);
            assertValidation(send("POST", "/todos", "{}", "Accept", accept), "title", "Title is required");
        }
    }

    @Test
    void aQueryStringIsIgnoredOnEveryRoute_tb1() {
        TaskView task = createTitled("t");

        assertThat(tasks(get("/todos?x=1&y=%2F&=&z"))).containsExactly(task);
        assertThat(fetch(task.id() + "?x=1")).isEqualTo(task);
        assertThat(oneTask(json("POST", "/todos?x=1", "{\"title\":\"q\"}"), 201).title()).isEqualTo("q");
        assertThat(oneTask(json("PUT", "/todos/" + task.id() + "?x=1", "{\"title\":\"p\"}"), 200).title()).isEqualTo("p");
        assertThat(oneTask(json("PATCH", "/todos/" + task.id() + "/status?x=1", "{\"status\":\"Completed\"}"), 200).status())
                .isEqualTo("Completed");
        assertThat(call("DELETE", "/todos/" + task.id() + "?x=1", null, null).status()).isEqualTo(204);
        assertError(get("/todos/" + task.id() + "?x=1"), ErrorCode.TODO_NOT_FOUND);
    }

    @Test
    void aContentTypeCharsetThatIsEmptyBogusOrIllegalNeverBecomesA500OnTheRealController_foundAtCpB() {
        for (String type : List.of("application/json; charset=", "application/json; charset=bogus",
                "application/json;charset=utf-16", "APPLICATION/JSON", "application/json; charset=\"\"",
                "application/json; charset=a b")) {
            assertThat(oneTask(call("POST", "/todos", type, "{\"title\":\"c\"}"), 201).title()).as(type).isEqualTo("c");
        }
        for (String type : List.of("application/json; charset=", "text/plain; charset=", "text/plain; charset=a b")) {
            assertThat(tasks(call("GET", "/todos", type, null))).as(type).hasSize(6);
            assertThat(call("DELETE", "/todos/" + MISSING_ID, type, null).status()).as(type).isEqualTo(404);
        }
    }

    @Test
    void aGetOrDeleteBodyIsNeverReadWhateverItsTypeAndSize_a8() {
        TaskView task = createTitled("t");
        byte[] fat = new byte[70_000];
        java.util.Arrays.fill(fat, (byte) 'x');

        RawHttp.Response list = RawHttp.request("GET", "/todos").header("Content-Type", "text/plain").body(fat).send(port);
        assertThat(tasks(list)).containsExactly(task);
        RawHttp.Response delete = RawHttp.request("DELETE", "/todos/" + task.id()).header("Content-Type", "text/plain")
                .body(fat).send(port);
        assertThat(delete.status()).isEqualTo(204);
    }

    @Test
    void aBodyOfExactlyTheLimitIsAcceptedAndOneByteMoreIsRejected_tb1() {
        byte[] atLimit = new byte[65_536];
        java.util.Arrays.fill(atLimit, (byte) ' ');
        byte[] json = "{\"title\":\"big\"}".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(json, 0, atLimit, 0, json.length);
        byte[] overLimit = new byte[65_537];
        java.util.Arrays.fill(overLimit, (byte) ' ');
        System.arraycopy(json, 0, overLimit, 0, json.length);

        assertThat(oneTask(RawHttp.request("POST", "/todos").header("Content-Type", "application/json").body(atLimit)
                .send(port), 201).title()).isEqualTo("big");
        assertError(RawHttp.request("POST", "/todos").header("Content-Type", "application/json").body(overLimit)
                .send(port), ErrorCode.PAYLOAD_TOO_LARGE);
        assertThat(tasks(get("/todos"))).hasSize(1);
    }

    // ---------------------------------------------------------------- wiring facts the contract depends on

    @Test
    void theApiMapperIsTheOnlyJsonMapperInTheRunningApplication_w1() {
        assertThat(context.getBeansOfType(JsonMapper.class)).containsOnlyKeys("apiJsonMapper");
    }

    @Test
    void noProblemDetailsAdviceAndNoSecondExceptionHandlerAreRegistered_v13() {
        assertThat(context.getBeansOfType(ResponseEntityExceptionHandler.class)).isEmpty();
        assertThat(context.getBeansWithAnnotation(RestControllerAdvice.class).values())
                .hasSize(1).first().isInstanceOf(ApiExceptionHandler.class);
    }

    @Test
    void theCreatedTaskIsReallyInTheSqliteFile() {
        TaskView task = create("durable-ish", "d");

        assertThat(repository.findById(UUID.fromString(task.id()))).hasValueSatisfying(stored -> {
            assertThat(stored.title()).isEqualTo("durable-ish");
            assertThat(stored.createdAt()).isEqualTo(START);
            assertThat(stored.seq()).isPositive();
        });
        assertThat(DATA_FILE).exists();
    }
}
