package com.capstone.todo.web.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    /** Requirements Appendix A.4: code to HTTP status. */
    private static final Map<String, Integer> CATALOG = catalog();

    private static Map<String, Integer> catalog() {
        Map<String, Integer> catalog = new LinkedHashMap<>();
        catalog.put("VALIDATION_ERROR", 400);
        catalog.put("INVALID_REQUEST_BODY", 400);
        catalog.put("INVALID_ID", 400);
        catalog.put("TODO_NOT_FOUND", 404);
        catalog.put("NOT_FOUND", 404);
        catalog.put("METHOD_NOT_ALLOWED", 405);
        catalog.put("PAYLOAD_TOO_LARGE", 413);
        catalog.put("UNSUPPORTED_MEDIA_TYPE", 415);
        catalog.put("INTERNAL_ERROR", 500);
        return catalog;
    }

    @Test
    void thereAreExactlyTheNineCodesOfAppendixA4WithTheirStatuses() {
        Map<String, Integer> actual = new LinkedHashMap<>();
        for (ErrorCode code : ErrorCode.values()) {
            actual.put(code.code(), code.status());
        }

        assertThat(actual).isEqualTo(CATALOG);
        assertThat(ErrorCode.values()).hasSize(9);
    }

    @Test
    void everyDefaultMessageIsPlainEnglishWithoutInternalDetail() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.defaultMessage()).isNotBlank().doesNotContain("\n", "Exception", "at com.", "{", "}");
        }
        assertThat(ErrorCode.values()).extracting(ErrorCode::defaultMessage).doesNotHaveDuplicates();
    }

    @Test
    void aContainerStatusKeepsItsCodeOnlyForFourStatusesAndEverythingElseIsInternalError() {
        assertThat(ErrorCode.forContainerStatus(404)).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(ErrorCode.forContainerStatus(405)).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED);
        assertThat(ErrorCode.forContainerStatus(413)).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE);
        assertThat(ErrorCode.forContainerStatus(415)).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        for (int status : new int[] {0, 200, 400, 401, 403, 406, 408, 409, 411, 414, 429, 500, 501, 503, 999}) {
            assertThat(ErrorCode.forContainerStatus(status)).as("status %d", status).isEqualTo(ErrorCode.INTERNAL_ERROR);
        }
    }

    @Test
    void onlyValidationErrorHasDetails() {
        List<ErrorCode> withDetails = List.of(ErrorCode.values()).stream()
                .filter(ErrorCode::hasDetails).collect(Collectors.toList());

        assertThat(withDetails).containsExactly(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void apiExceptionCarriesTheCatalogMessageAndNoStackTrace() {
        ApiException exception = new ApiException(ErrorCode.INVALID_ID);

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_ID);
        assertThat(exception).hasMessage(ErrorCode.INVALID_ID.defaultMessage());
        assertThat(exception.details()).isEmpty();
        assertThat(exception.getStackTrace()).isEmpty();
    }

    @Test
    void apiExceptionKeepsDetailsOnlyForValidationErrorAndInOrder() {
        List<ErrorDetail> details = List.of(new ErrorDetail("title", "Title is required"),
                new ErrorDetail("status", "Status is required"));

        ApiException validation = ApiException.validation(details);

        assertThat(validation.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(validation.details()).containsExactlyElementsOf(details);
        assertThatThrownBy(() -> new ApiException(ErrorCode.VALIDATION_ERROR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiException(ErrorCode.INVALID_ID, details))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void errorDetailRejectsNulls() {
        assertThatThrownBy(() -> new ErrorDetail(null, "m")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ErrorDetail("f", null)).isInstanceOf(NullPointerException.class);
    }
}
