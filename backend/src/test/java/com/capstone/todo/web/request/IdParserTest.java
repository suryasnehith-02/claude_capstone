package com.capstone.todo.web.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.capstone.todo.web.response.ApiException;
import com.capstone.todo.web.response.ErrorCode;

/** U-ID: strict single percent-decode, canonical form, lowercase (D-13, AC-6, AC-15). ASCII only. */
class IdParserTest {

    private static final String LOWER = "123e4567-e89b-12d3-a456-426614174000";
    private static final String UPPER = "123E4567-E89B-12D3-A456-426614174000";

    private static void assertInvalid(String raw) {
        assertThatThrownBy(() -> IdParser.parse(raw))
                .as("raw segment %s", raw)
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_ID);
                    assertThat(e.details()).isEmpty();
                });
    }

    @Test
    void aLowercaseCanonicalIdIsAccepted() {
        assertThat(IdParser.parse(LOWER)).isEqualTo(UUID.fromString(LOWER));
        assertThat(IdParser.parse(LOWER).toString()).isEqualTo(LOWER);
    }

    @Test
    void anUppercaseOrMixedCaseIdIsAcceptedAndNormalisedToLowercase() {
        assertThat(IdParser.parse(UPPER).toString()).isEqualTo(LOWER);
        assertThat(IdParser.parse("123e4567-E89B-12d3-A456-426614174000").toString()).isEqualTo(LOWER);
    }

    @Test
    void anyVersionAndVariantIsAcceptedBecauseIdsAreOpaque() {
        assertThat(IdParser.parse("00000000-0000-0000-0000-000000000000").toString())
                .isEqualTo("00000000-0000-0000-0000-000000000000");
        assertThat(IdParser.parse("FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF").toString())
                .isEqualTo("ffffffff-ffff-ffff-ffff-ffffffffffff");
        assertThat(IdParser.parse("123e4567-e89b-92d3-f456-426614174000").toString())
                .isEqualTo("123e4567-e89b-92d3-f456-426614174000");
    }

    @Test
    void aPercentEncodedHexLetterIsDecodedOnceAndAcceptedInLowerOrUpperCase() {
        assertThat(IdParser.parse("123e4567-e89b-12d3-%61456-426614174000").toString())
                .isEqualTo("123e4567-e89b-12d3-a456-426614174000");
        assertThat(IdParser.parse("123e4567-e89b-12d3-%41456-426614174000").toString())
                .isEqualTo("123e4567-e89b-12d3-a456-426614174000");
        assertThat(IdParser.parse("%31%32%33e4567-e89b-12d3-a456-426614174000").toString()).isEqualTo(LOWER);
        assertThat(IdParser.parse("123e4567-e89b-12d3-a456-4266141740%30%30").toString()).isEqualTo(LOWER);
        for (char letter : new char[] {'a', 'b', 'c', 'd', 'e', 'f'}) {
            String lower = "%" + Integer.toHexString(letter);
            String upper = lower.toUpperCase();
            String expected = "123e4567-e89b-12d3-" + letter + "456-426614174000";
            assertThat(IdParser.parse("123e4567-e89b-12d3-" + lower + "456-426614174000").toString())
                    .isEqualTo(expected);
            assertThat(IdParser.parse("123e4567-e89b-12d3-" + upper + "456-426614174000").toString())
                    .isEqualTo(expected);
        }
    }

    @Test
    void anEncodedHyphenIsDecodedOnceAndAcceptedBecauseTheResultIsCanonical() {
        assertThat(IdParser.parse("123e4567%2De89b-12d3-a456-426614174000").toString()).isEqualTo(LOWER);
    }

    @ParameterizedTest(name = "decoded once only: {0}")
    @ValueSource(strings = {
            "%2530e4567-e89b-12d3-a456-426614174000",
            "123e4567-e89b-12d3-a456-4266141740%2530",
            "%25",
            "%252d",
            "123e4567%252De89b-12d3-a456-426614174000",
            "%2561%2561%2561%2561%2561%2561%2561%2561-e89b-12d3-a456-426614174000"})
    void aSecondLevelOfEncodingIsNeverDecoded(String raw) {
        assertInvalid(raw);
    }

    @ParameterizedTest(name = "malformed percent escape: {0}")
    @ValueSource(strings = {
            "%zz3e4567-e89b-12d3-a456-426614174000",
            "%",
            "123e4567-e89b-12d3-a456-42661417400%",
            "123e4567-e89b-12d3-a456-4266141740%0",
            "%0",
            "%G0e4567-e89b-12d3-a456-426614174000",
            "%0Ge4567-e89b-12d3-a456-426614174000",
            "% 3e4567-e89b-12d3-a456-426614174000",
            "%-1e4567-e89b-12d3-a456-426614174000",
            "%+1e4567-e89b-12d3-a456-426614174000"})
    void aPercentNotFollowedByTwoHexDigitsIsInvalid(String raw) {
        assertInvalid(raw);
    }

    @ParameterizedTest(name = "invalid UTF-8 after decoding: {0}")
    @ValueSource(strings = {
            "%FF3e4567-e89b-12d3-a456-426614174000",
            "%ff3e4567-e89b-12d3-a456-426614174000",
            "%80",
            "%C3",
            "%C0%AF",
            "%C0%80",
            "%ED%A0%80",
            "%F4%90%80%80",
            "%C3%28",
            "123e4567-e89b-12d3-a456-426614174000%FF"})
    void bytesThatAreNotValidUtf8AreInvalid(String raw) {
        assertInvalid(raw);
    }

    @ParameterizedTest(name = "valid UTF-8 but not a UUID: {0}")
    @ValueSource(strings = {
            "%C3%A9",
            "%EF%BC%91%EF%BC%91%EF%BC%91%EF%BC%91%EF%BC%91%EF%BC%91%EF%BC%91%EF%BC%91-e89b-12d3-a456-426614174000",
            "%F0%9F%98%80"})
    void validUtf8ThatIsNotAnAsciiUuidIsInvalid(String raw) {
        assertInvalid(raw);
    }

    @ParameterizedTest(name = "not canonical: {0}")
    @ValueSource(strings = {
            "",
            "not-a-uuid",
            "1-1-1-1-1",
            "1-2-3-4-5",
            "123e4567-e89b-12d3-a456-42661417400",
            "123e4567-e89b-12d3-a456-4266141740000",
            "123e4567e89b12d3a456426614174000",
            "123e4567-e89b-12d3-a456-42661417400g",
            "gggggggg-gggg-gggg-gggg-gggggggggggg",
            "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz",
            "123e4567-e89b-12d3-a456_426614174000",
            "123e4567-e89b-12d3-a456-426614174000-",
            "-123e4567-e89b-12d3-a456-426614174000",
            "123e4567-e89b-12d3-a456-426614174000-0000",
            "{123e4567-e89b-12d3-a456-426614174000}",
            "urn:uuid:123e4567-e89b-12d3-a456-426614174000",
            "+123e4567-e89b-12d3-a456-426614174000",
            "123e4567+e89b-12d3-a456-426614174000",
            "123e4567-e89b-12d3-a456-426614174000+",
            "0x23e4567-e89b-12d3-a456-426614174000",
            "123e4567-e89b-12d3-a456-4266141740 0",
            "123e4567-e89b-12d3-a456-426614174000.",
            "123e4567-e89b-12d3-a456-426614174000;x=1",
            "123e4567-e89b-12d3-a456-426614174000?x=1",
            "123e4567-e89b-12d3-a456-426614174000/",
            "a/b",
            "a%2Fb",
            "a%5Cb",
            "..",
            "%2e%2e"})
    void anythingThatIsNotTheCanonicalFormIsInvalid(String raw) {
        assertInvalid(raw);
    }

    @ParameterizedTest(name = "padding or control character after decoding: {0}")
    @ValueSource(strings = {
            "%20123e4567-e89b-12d3-a456-426614174000",
            "123e4567-e89b-12d3-a456-426614174000%20",
            "123e4567-e89b-12d3-a456-426614174000%0A",
            "123e4567-e89b-12d3-a456-426614174000%0D%0A",
            "123e4567-e89b-12d3-a456-426614174000%09",
            "123e4567-e89b-12d3-a456-4266141740%00",
            "%0A123e4567-e89b-12d3-a456-426614174000",
            "123e4567-e89b-12d3-a456-426614174000%2F",
            "123e4567-e89b-12d3-a456-426614174000%C2%A0"})
    void aPaddedOrControlCharacterIdIsInvalid(String raw) {
        assertInvalid(raw);
    }

    @Test
    void aRawNonAsciiCharacterIsInvalidNotAnException() {
        assertInvalid("123e4567-e89b-12d3-a456-42661417400" + (char) 0xFF11);
        assertInvalid("123e4567-e89b-12d3-a456-42661417400" + (char) 0x00E9);
        assertInvalid(new String(Character.toChars(0x1F600)));
        assertInvalid(String.valueOf((char) 0xD800));
    }

    @ParameterizedTest
    @NullSource
    void aMissingSegmentIsInvalid(String raw) {
        assertInvalid(raw);
    }

    @Test
    void theErrorCarriesOnlyTheCatalogMessageAndNoStackTrace() {
        String raw = "secret-value-%zz";

        assertThatThrownBy(() -> IdParser.parse(raw))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(ErrorCode.INVALID_ID.defaultMessage());
                    assertThat(e.getMessage()).doesNotContain("secret");
                    assertThat(e.getStackTrace()).isEmpty();
                    assertThat(e.getCause()).isNull();
                });
    }

    @Test
    void aVeryLongSegmentIsInvalidWithoutFailingInAnyOtherWay() {
        assertInvalid("a".repeat(100_000));
        assertInvalid("%61".repeat(100_000));
    }
}
