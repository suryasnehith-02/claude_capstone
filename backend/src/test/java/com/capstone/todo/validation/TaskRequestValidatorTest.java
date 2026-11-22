package com.capstone.todo.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.capstone.todo.config.JsonConfig;
import com.capstone.todo.domain.CreateTask;
import com.capstone.todo.domain.ReplaceTask;
import com.capstone.todo.domain.SetStatus;
import com.capstone.todo.domain.TaskStatus;
import com.capstone.todo.web.response.ApiException;
import com.capstone.todo.web.response.ErrorCode;
import com.capstone.todo.web.response.ErrorDetail;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validator tables per field and per operation (7.4, AC-2, AC-8, AC-11, AC-18, AC-20, AC-21, AC-23, AC-28 to AC-30).
 * The file is ASCII only: texts with special characters are built from numbers.
 */
class TaskRequestValidatorTest {

    private static final JsonMapper MAPPER = JsonConfig.newApiJsonMapper();
    private static final UUID ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private final TaskRequestValidator validator = new TaskRequestValidator();

    private static JsonNode json(String text) {
        return MAPPER.readTree(text);
    }

    private static String cps(int... codePoints) {
        StringBuilder text = new StringBuilder();
        for (int codePoint : codePoints) {
            text.appendCodePoint(codePoint);
        }
        return text.toString();
    }

    private static String quoted(String text) {
        return MAPPER.writeValueAsString(text);
    }

    private CreateTask create(String body) {
        return validator.create(json(body));
    }

    private static List<ErrorDetail> details(Supplier<?> action) {
        return thrown(action).details();
    }

    private static ApiException thrown(Supplier<?> action) {
        try {
            action.get();
        } catch (ApiException e) {
            assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
            return e;
        }
        throw new AssertionError("expected a VALIDATION_ERROR");
    }

    private static ErrorDetail detail(String field, String message) {
        return new ErrorDetail(field, message);
    }

    // ----- title -----

    @Test
    void aValidCreateKeepsTitleAndDescription() {
        CreateTask task = create("{\"title\":\"Buy milk\",\"description\":\"2 litres\"}");

        assertThat(task).isEqualTo(new CreateTask("Buy milk", "2 litres"));
    }

    @Test
    void titleIsTrimmedAndInternalWhitespaceIsKept() {
        assertThat(create("{\"title\":\"  a  b \\t c  \"}").title()).isEqualTo("a  b \t c");
        String padded = cps(0x00A0, 0x3000, 0x0085, 0x2007, 0x202F) + "x" + cps(0x2007, 0x202F, 0x3000);
        assertThat(create("{\"title\":" + quoted(padded) + "}").title()).isEqualTo("x");
    }

    @Test
    void titleOfExactlyOneHundredCodePointsIsAcceptedAndOneHundredAndOneIsRejected() {
        assertThat(create("{\"title\":\"" + "a".repeat(100) + "\"}").title()).hasSize(100);

        assertThat(details(() -> create("{\"title\":\"" + "a".repeat(101) + "\"}")))
                .containsExactly(detail("title", TaskRequestValidator.TITLE_TOO_LONG));
    }

    @Test
    void titleIsCountedInCodePointsNotUtf16Units() {
        String face = cps(0x1F600);

        assertThat(create("{\"title\":" + quoted(face.repeat(100)) + "}").title()).isEqualTo(face.repeat(100));
        assertThat(details(() -> create("{\"title\":" + quoted(face.repeat(101)) + "}")))
                .containsExactly(detail("title", TaskRequestValidator.TITLE_TOO_LONG));
    }

    @Test
    void titleOfOneHundredCodePointsWithSurroundingPaddingIsValid() {
        String padding = cps(0x3000, 0x0020, 0x00A0);

        assertThat(create("{\"title\":" + quoted(padding + "a".repeat(100) + padding) + "}").title())
                .isEqualTo("a".repeat(100));
        assertThat(details(() -> create("{\"title\":" + quoted(padding + "a".repeat(101) + padding) + "}")))
                .containsExactly(detail("title", TaskRequestValidator.TITLE_TOO_LONG));
    }

    @Test
    void titleLengthIsCheckedAfterTrimmingNotBefore() {
        assertThat(create("{\"title\":\"" + " ".repeat(300) + "a" + " ".repeat(300) + "\"}").title()).isEqualTo("a");
    }

    @ParameterizedTest(name = "title {0} is required")
    @ValueSource(strings = {"{}", "{\"title\":null}", "{\"title\":\"\"}", "{\"title\":\" \"}",
            "{\"title\":\"\\t\\n\\r \"}", "{\"description\":\"only a description\"}"})
    void anAbsentNullEmptyOrWhitespaceOnlyTitleIsRequired(String body) {
        assertThat(details(() -> create(body))).containsExactly(detail("title", TaskRequestValidator.TITLE_REQUIRED));
    }

    @Test
    void aTitleOfOnlyUnicodeWhiteSpaceIsRequired() {
        for (int codePoint : new int[] {0x0009, 0x000B, 0x000C, 0x0085, 0x00A0, 0x1680, 0x2000, 0x2007, 0x200A,
                0x2028, 0x2029, 0x202F, 0x205F, 0x3000}) {
            String body = "{\"title\":" + quoted(cps(codePoint, codePoint)) + "}";

            assertThat(details(() -> create(body)))
                    .as("U+%04X", codePoint)
                    .containsExactly(detail("title", TaskRequestValidator.TITLE_REQUIRED));
        }
    }

    @Test
    void codePointsThatAreNotWhiteSpaceAreKeptAndMakeATitleValid() {
        for (int codePoint : new int[] {0xFEFF, 0x001C, 0x001F, 0x180E, 0x200B, 0x2060}) {
            String title = cps(codePoint);

            assertThat(create("{\"title\":" + quoted(title) + "}").title()).as("U+%04X", codePoint).isEqualTo(title);
        }
    }

    @ParameterizedTest(name = "title {0} is a type error")
    @ValueSource(strings = {"5", "5.5", "0", "true", "false", "[]", "[\"a\"]", "{}", "{\"a\":1}"})
    void aTitleThatIsNotAStringIsATypeErrorWithoutCoercion(String value) {
        assertThat(details(() -> create("{\"title\":" + value + "}")))
                .containsExactly(detail("title", TaskRequestValidator.TITLE_TYPE));
    }

    // ----- description -----

    @Test
    void descriptionIsTrimmedAndInternalWhitespaceIsKept() {
        assertThat(create("{\"title\":\"t\",\"description\":\"  a   b  \"}").description()).isEqualTo("a   b");
    }

    @ParameterizedTest(name = "description {0} becomes an empty string")
    @ValueSource(strings = {"{\"title\":\"t\"}", "{\"title\":\"t\",\"description\":null}",
            "{\"title\":\"t\",\"description\":\"\"}", "{\"title\":\"t\",\"description\":\"   \"}",
            "{\"title\":\"t\",\"description\":\"\\t\\n \"}"})
    void anAbsentNullEmptyOrWhitespaceOnlyDescriptionBecomesAnEmptyString(String body) {
        assertThat(create(body)).isEqualTo(new CreateTask("t", ""));
    }

    @Test
    void aDescriptionOfOnlyUnicodeWhiteSpaceBecomesAnEmptyString() {
        String body = "{\"title\":\"t\",\"description\":" + quoted(cps(0x00A0, 0x2007, 0x3000, 0x0085, 0x202F)) + "}";

        assertThat(create(body).description()).isEmpty();
    }

    @Test
    void descriptionOfExactlyFiveHundredCodePointsIsAcceptedAndFiveHundredAndOneIsRejected() {
        assertThat(create("{\"title\":\"t\",\"description\":\"" + "d".repeat(500) + "\"}").description())
                .hasSize(500);

        assertThat(details(() -> create("{\"title\":\"t\",\"description\":\"" + "d".repeat(501) + "\"}")))
                .containsExactly(detail("description", TaskRequestValidator.DESCRIPTION_TOO_LONG));
    }

    @Test
    void descriptionIsCountedInCodePointsAfterTrimming() {
        String face = cps(0x1F600);
        String padding = cps(0x3000, 0x0020);

        assertThat(create("{\"title\":\"t\",\"description\":" + quoted(padding + face.repeat(500) + padding) + "}")
                .description()).isEqualTo(face.repeat(500));
        assertThat(details(() -> create("{\"title\":\"t\",\"description\":" + quoted(face.repeat(501)) + "}")))
                .containsExactly(detail("description", TaskRequestValidator.DESCRIPTION_TOO_LONG));
    }

    @ParameterizedTest(name = "description {0} is a type error")
    @ValueSource(strings = {"5", "5.5", "true", "false", "[]", "[\"a\"]", "{}", "{\"a\":1}"})
    void aDescriptionThatIsNotAStringIsATypeError(String value) {
        assertThat(details(() -> create("{\"title\":\"t\",\"description\":" + value + "}")))
                .containsExactly(detail("description", TaskRequestValidator.DESCRIPTION_TYPE));
    }

    // ----- collected failures and field names -----

    @Test
    void everyFailureIsReportedTogetherInTheOrderTitleThenDescription() {
        assertThat(details(() -> create("{\"title\":5,\"description\":true}"))).containsExactly(
                detail("title", TaskRequestValidator.TITLE_TYPE),
                detail("description", TaskRequestValidator.DESCRIPTION_TYPE));
        assertThat(details(() -> create("{\"description\":\"" + "d".repeat(501) + "\"}"))).containsExactly(
                detail("title", TaskRequestValidator.TITLE_REQUIRED),
                detail("description", TaskRequestValidator.DESCRIPTION_TOO_LONG));
        assertThat(details(() -> create("{\"description\":\"" + "d".repeat(501) + "\",\"title\":\""
                + "t".repeat(101) + "\"}"))).containsExactly(
                        detail("title", TaskRequestValidator.TITLE_TOO_LONG),
                        detail("description", TaskRequestValidator.DESCRIPTION_TOO_LONG));
    }

    @Test
    void theFieldIsTheJsonPropertyNameAndTheMessageNeverContainsTheRequestValue() {
        String secretTitle = "S3cretTitle" + "x".repeat(100);
        String secretDescription = "S3cretDescription" + "y".repeat(500);

        List<ErrorDetail> failures = details(() -> create("{\"title\":\"" + secretTitle + "\",\"description\":\""
                + secretDescription + "\"}"));

        assertThat(failures).extracting(ErrorDetail::field).containsExactly("title", "description");
        assertThat(failures).extracting(ErrorDetail::message)
                .noneMatch(message -> message.contains("S3cret"));
    }

    @Test
    void theThrownExceptionIsAValidationErrorWithoutAStackTrace() {
        ApiException e = thrown(() -> create("{}"));

        assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(e.getStackTrace()).isEmpty();
    }

    // ----- operations: replace (PUT) -----

    @Test
    void replaceReadsTitleAndDescriptionAndCarriesTheIdFromThePath() {
        ReplaceTask task = validator.replace(ID, json("{\"title\":\" New \",\"description\":\" text \"}"));

        assertThat(task).isEqualTo(new ReplaceTask(ID, "New", "text"));
    }

    @Test
    void replaceWithoutADescriptionGivesAnEmptyDescription() {
        assertThat(validator.replace(ID, json("{\"title\":\"New\"}"))).isEqualTo(new ReplaceTask(ID, "New", ""));
        assertThat(validator.replace(ID, json("{\"title\":\"New\",\"description\":null}")).description()).isEmpty();
    }

    @Test
    void replaceWithAnEmptyObjectFailsOnTheTitleOnly() {
        assertThat(details(() -> validator.replace(ID, json("{}"))))
                .containsExactly(detail("title", TaskRequestValidator.TITLE_REQUIRED));
    }

    @Test
    void replaceCollectsTheSameFailuresAsCreate() {
        assertThat(details(() -> validator.replace(ID, json("{\"title\":[],\"description\":{}}")))).containsExactly(
                detail("title", TaskRequestValidator.TITLE_TYPE),
                detail("description", TaskRequestValidator.DESCRIPTION_TYPE));
    }

    // ----- operations: status (PATCH) -----

    @ParameterizedTest(name = "status {0}")
    @MethodSource("validStatuses")
    void exactlyPendingAndCompletedAreAccepted(String json, TaskStatus expected) {
        assertThat(validator.setStatus(ID, json("{\"status\":" + json + "}"))).isEqualTo(new SetStatus(ID, expected));
    }

    static Stream<Arguments> validStatuses() {
        return Stream.of(Arguments.of("\"Pending\"", TaskStatus.PENDING),
                Arguments.of("\"Completed\"", TaskStatus.COMPLETED));
    }

    @ParameterizedTest(name = "status {0} is invalid")
    @ValueSource(strings = {"\"pending\"", "\"PENDING\"", "\"completed\"", "\"COMPLETED\"", "\"Pending \"",
            "\" Pending\"", "\"Done\"", "\"\"", "\" \"", "\"Pend\"", "\"Pending\\u0000\"", "5", "0", "true", "false",
            "[]", "[\"Pending\"]", "{}", "{\"status\":\"Pending\"}"})
    void anyOtherStatusIsInvalidWithoutCoercionOrTrimming(String value) {
        assertThat(details(() -> validator.setStatus(ID, json("{\"status\":" + value + "}"))))
                .containsExactly(detail("status", TaskRequestValidator.STATUS_INVALID));
    }

    @ParameterizedTest(name = "body {0}")
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"title\":\"x\"}", "{\"Status\":\"Pending\"}"})
    void anAbsentOrNullStatusIsRequired(String body) {
        assertThat(details(() -> validator.setStatus(ID, json(body))))
                .containsExactly(detail("status", TaskRequestValidator.STATUS_REQUIRED));
    }

    @Test
    void patchReadsOnlyTheStatusSoABadTitleOrDescriptionIsIgnored() {
        assertThat(validator.setStatus(ID, json("{\"status\":\"Completed\",\"title\":5,\"description\":[]}")))
                .isEqualTo(new SetStatus(ID, TaskStatus.COMPLETED));
    }

    // ----- ignored members (FR-8, AC-18) -----

    @ParameterizedTest(name = "ignored members {0}")
    @ValueSource(strings = {
            "\"id\":5", "\"id\":\"not-a-uuid\"", "\"id\":null", "\"createdAt\":[]", "\"updatedAt\":{}",
            "\"createdAt\":\"yesterday\"", "\"status\":5", "\"status\":\"bogus\"", "\"status\":null",
            "\"status\":\"Completed\"", "\"extra\":{\"a\":[1,2,{\"b\":null}]}", "\"seq\":1", "\"x\":true",
            "\"\":1", "\"Title\":5", "\"TITLE\":\"upper case name\""})
    void membersThatAreNotReadNeverCauseAnErrorOnCreateAndReplace(String member) {
        String body = "{\"title\":\"ok\",\"description\":\"d\"," + member + "}";

        assertThat(create(body)).isEqualTo(new CreateTask("ok", "d"));
        assertThat(validator.replace(ID, json(body))).isEqualTo(new ReplaceTask(ID, "ok", "d"));
    }

    @ParameterizedTest(name = "ignored members {0}")
    @ValueSource(strings = {"\"id\":5", "\"createdAt\":[]", "\"updatedAt\":{}", "\"extra\":\"x\"", "\"title\":5",
            "\"description\":true"})
    void membersThatAreNotReadNeverCauseAnErrorOnStatusUpdate(String member) {
        assertThat(validator.setStatus(ID, json("{\"status\":\"Pending\"," + member + "}")))
                .isEqualTo(new SetStatus(ID, TaskStatus.PENDING));
    }

    @Test
    void duplicateKeysAreSeenAsTheLastValueBecauseTheTreeHoldsOnlyOne() {
        assertThat(create("{\"title\":\"first\",\"title\":\"second\"}").title()).isEqualTo("second");
        assertThat(details(() -> create("{\"title\":\"first\",\"title\":5}")))
                .containsExactly(detail("title", TaskRequestValidator.TITLE_TYPE));
    }

    @Test
    void textIsReturnedAsSubmittedAfterTrimmingOnlyWithNoEscapingOrSanitising() {
        CreateTask task = create("{\"title\":\" <b>&\\\"'</b> \",\"description\":\"<script>alert(1)</script>\"}");

        assertThat(task.title()).isEqualTo("<b>&\"'</b>");
        assertThat(task.description()).isEqualTo("<script>alert(1)</script>");
    }

    @Test
    void theConstantsAreTheLimitsOfTheRequirements() {
        assertThat(TaskRequestValidator.TITLE_MAX_CODE_POINTS).isEqualTo(100);
        assertThat(TaskRequestValidator.DESCRIPTION_MAX_CODE_POINTS).isEqualTo(500);
        assertThatThrownBy(() -> validator.create(json("{}"))).isInstanceOf(ApiException.class);
    }
}
