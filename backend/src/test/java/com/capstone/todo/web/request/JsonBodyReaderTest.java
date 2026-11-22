package com.capstone.todo.web.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.capstone.todo.config.JsonConfig;
import com.capstone.todo.web.response.ApiException;
import com.capstone.todo.web.response.ErrorCode;

import tools.jackson.databind.JsonNode;

/**
 * U-JSON (reader side): strict UTF-8, BOM, trailing tokens, object root, unpaired surrogates, duplicate keys and the
 * limits at and over each boundary (D-11, D-14, AC-3, AC-17, AC-23, V-22, V-24). ASCII only: every special character
 * is an escape in the JSON text or is built from numbers.
 */
@ExtendWith(OutputCaptureExtension.class)
class JsonBodyReaderTest {

    private final JsonBodyReader reader = new JsonBodyReader(JsonConfig.newApiJsonMapper());

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private JsonNode read(String text) {
        return reader.read(utf8(text));
    }

    private void assertRejected(byte[] body) {
        assertThatThrownBy(() -> reader.read(body)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST_BODY);
            assertThat(e.details()).isEmpty();
            assertThat(e.getCause()).isNull();
            assertThat(e.getStackTrace()).isEmpty();
            assertThat(e.getMessage()).isEqualTo(ErrorCode.INVALID_REQUEST_BODY.defaultMessage());
        });
    }

    private void assertRejected(String text) {
        assertRejected(utf8(text));
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    // ----- accepted documents -----

    @Test
    void anObjectIsReturnedAsATree() {
        JsonNode root = read("{\"title\":\"Buy milk\",\"n\":[1,2],\"o\":{\"x\":null}}");

        assertThat(root.isObject()).isTrue();
        assertThat(root.get("title").asString()).isEqualTo("Buy milk");
        assertThat(root.get("n").size()).isEqualTo(2);
        assertThat(root.get("o").get("x").isNull()).isTrue();
    }

    @ParameterizedTest(name = "accepted: {0}")
    @ValueSource(strings = {"{}", " {} ", "\n\t\r {\"a\":1}\n\n", "{\"a\":\"\"}", "{\"a\":\"\\u0000\"}", "{\"a\":-0.5e+10}",
            "{\"a\":true,\"b\":false,\"c\":null}", "{\"a\":\"\\ud83d\\ude00\"}", "{\"\\ud83d\\ude00\":1}",
            "{\"a\":\"\\u00e9\\u3000\\u2028\"}", "{\"a\":\"/\\/\"}", "{\"a\":[[],{},[{}]]}"})
    void wellFormedObjectsAreAccepted(String text) {
        assertThat(read(text).isObject()).isTrue();
    }

    @Test
    void textSentAsRawUtf8IsDecodedAndAnAstralCharacterIsOnePair() {
        String face = new String(Character.toChars(0x1F600));
        String accented = new String(Character.toChars(0x00E9));
        String body = "{\"title\":\"" + face + accented + "\"," + "\"" + face + "\":1}";

        JsonNode root = read(body);

        assertThat(root.get("title").asString()).isEqualTo(face + accented);
        assertThat(root.has(face)).isTrue();
    }

    @Test
    void duplicateKeysResolveToTheLastOccurrence() {
        assertThat(read("{\"title\":\"first\",\"title\":\"second\"}").get("title").asString()).isEqualTo("second");
        assertThat(read("{\"title\":\"first\",\"title\":5}").get("title").asInt()).isEqualTo(5);
        assertThat(read("{\"a\":{\"b\":1,\"b\":2},\"a\":{\"b\":3,\"b\":4}}").get("a").get("b").asInt()).isEqualTo(4);
    }

    // ----- empty or missing documents -----

    @Test
    void aNullOrEmptyBodyIsRejected() {
        assertRejected((byte[]) null);
        assertRejected(new byte[0]);
    }

    @ParameterizedTest(name = "whitespace-only body #{index}")
    @ValueSource(strings = {" ", "  \n\t\r ", "\n", "\r\n\r\n", "\t"})
    void aWhitespaceOnlyBodyIsRejected(String text) {
        assertRejected(text);
    }

    // ----- byte order mark and strict UTF-8 -----

    @Test
    void aLeadingByteOrderMarkIsRejected() {
        assertRejected(concat(bytes(0xEF, 0xBB, 0xBF), utf8("{\"title\":\"x\"}")));
        assertRejected(concat(bytes(0xEF, 0xBB, 0xBF), utf8("{}")));
        assertRejected(bytes(0xEF, 0xBB, 0xBF));
    }

    @Test
    void aByteOrderMarkInsideAStringIsKeptNotRejected() {
        JsonNode root = read("{\"a\":\"x\\ufeffy\"}");

        assertThat(root.get("a").asString()).isEqualTo("x" + (char) 0xFEFF + "y");
        assertThat(reader.read(concat(utf8("{\"a\":\"x"), concat(bytes(0xEF, 0xBB, 0xBF), utf8("y\"}")))).get("a")
                .asString()).isEqualTo("x" + (char) 0xFEFF + "y");
    }

    @Test
    void aByteOrderMarkAfterWhitespaceIsAlsoRejectedByTheParser() {
        assertRejected(concat(utf8(" "), concat(bytes(0xEF, 0xBB, 0xBF), utf8("{}"))));
    }

    static Stream<Arguments> invalidUtf8() {
        return Stream.of(
                Arguments.of("lone continuation byte", bytes('{', '"', 'a', '"', ':', '"', 0x80, '"', '}')),
                Arguments.of("0xFF", bytes('{', '"', 'a', '"', ':', '"', 0xFF, '"', '}')),
                Arguments.of("0xFE", bytes('{', '"', 'a', '"', ':', '"', 0xFE, '"', '}')),
                Arguments.of("truncated two-byte sequence", bytes('{', '"', 'a', '"', ':', '"', 0xC3, '"', '}')),
                Arguments.of("truncated at the end", bytes('{', '}', 0xE2, 0x82)),
                Arguments.of("overlong slash", bytes('{', '"', 'a', '"', ':', '"', 0xC0, 0xAF, '"', '}')),
                Arguments.of("overlong NUL", bytes('{', '"', 'a', '"', ':', '"', 0xC0, 0x80, '"', '}')),
                Arguments.of("overlong three-byte", bytes('{', '"', 'a', '"', ':', '"', 0xE0, 0x80, 0xAF, '"', '}')),
                Arguments.of("encoded high surrogate", bytes('{', '"', 'a', '"', ':', '"', 0xED, 0xA0, 0x80, '"', '}')),
                Arguments.of("encoded low surrogate", bytes('{', '"', 'a', '"', ':', '"', 0xED, 0xB0, 0x80, '"', '}')),
                Arguments.of("encoded surrogate pair (CESU-8)", bytes('{', '"', 'a', '"', ':', '"',
                        0xED, 0xA0, 0xBD, 0xED, 0xB8, 0x80, '"', '}')),
                Arguments.of("above U+10FFFF", bytes('{', '"', 'a', '"', ':', '"', 0xF4, 0x90, 0x80, 0x80, '"', '}')),
                Arguments.of("five-byte lead", bytes('{', '"', 'a', '"', ':', '"', 0xF8, 0x88, 0x80, 0x80, 0x80, '"', '}')),
                Arguments.of("invalid byte in a member name", bytes('{', '"', 0xFF, '"', ':', '1', '}')),
                Arguments.of("invalid byte after the object", bytes('{', '}', ' ', 0xFF)),
                Arguments.of("UTF-16 little endian text", bytes('{', 0, '}', 0)),
                Arguments.of("Latin-1 e acute", bytes('{', '"', 'a', '"', ':', '"', 0xE9, '"', '}')));
    }

    @ParameterizedTest(name = "invalid UTF-8: {0}")
    @MethodSource("invalidUtf8")
    void bytesThatAreNotValidUtf8AreRejected(String name, byte[] body) {
        assertRejected(body);
    }

    @Test
    void validMultiByteSequencesAreAccepted() {
        byte[] body = bytes('{', '"', 'a', '"', ':', '"', 0xC3, 0xA9, 0xE2, 0x82, 0xAC, 0xF0, 0x9F, 0x98, 0x80, '"', '}');

        String text = reader.read(body).get("a").asString();

        assertThat(text.codePointCount(0, text.length())).isEqualTo(3);
        assertThat(text.codePointAt(0)).isEqualTo(0x00E9);
        assertThat(text.codePointAt(1)).isEqualTo(0x20AC);
        assertThat(text.codePointAt(2)).isEqualTo(0x1F600);
    }

    // ----- syntax, trailing tokens and root type -----

    @ParameterizedTest(name = "trailing or malformed: {0}")
    @ValueSource(strings = {"{} {}", "{}{}", "{}x", "{},", "{} []", "{} 1", "{} \n null", "{}}", "{}\u0000", "{} // c",
            "{} /* c */", "{\"a\":1} {\"a\":2}", "{\"a\":1}\n\n{\"b\":2}", "{\"a\":1}]"})
    void aSecondTokenAfterTheRootIsRejected(String text) {
        assertRejected(text);
    }

    @ParameterizedTest(name = "root is not an object: {0}")
    @ValueSource(strings = {"[]", "[{}]", "[1,2]", "\"text\"", "\"\"", "5", "-1", "0.5", "true", "false", "null",
            "[null]", "\"{}\""})
    void aRootThatIsNotAnObjectIsRejected(String text) {
        assertRejected(text);
    }

    @ParameterizedTest(name = "malformed: {0}")
    @ValueSource(strings = {"{", "}", "{bad", "{\"a\"", "{\"a\":", "{\"a\":1", "{\"a\":1,", "{\"a\":1,}", "{,}",
            "{\"a\":}", "{\"a\" 1}", "{\"a\":1 \"b\":2}", "{\"a\":[1,2}", "{\"a\":[1,,2]}", "{\"a\":[,]}",
            "{'a':1}", "{\"a\":'x'}", "{a:1}", "{\"a\":NaN}", "{\"a\":Infinity}", "{\"a\":-Infinity}",
            "{\"a\":01}", "{\"a\":+1}", "{\"a\":.5}", "{\"a\":1.}", "{\"a\":0x10}", "{\"a\":1e}", "{\"a\":--1}",
            "{\"a\":tru}", "{\"a\":True}", "{\"a\":nul}", "{\"a\":undefined}",
            "{\"a\":1} // comment", "{/* c */\"a\":1}", "{\"a\":1 # c\n}", "{\"a\":1 // c\n}",
            "{\"a\":\"x\ty\"}", "{\"a\":\"x\ny\"}", "{\"a\":\"x\u0000y\"}", "{\"a\":\"x\u001fy\"}",
            "{\"a\":\"\\x41\"}", "{\"a\":\"\\'\"}", "{\"a\":\"\\u12\"}", "{\"a\":\"\\uzzzz\"}", "{\"a\":\"\\",
            "{\"a\":\"unterminated}", "{\"a\":\"x\"\"}", "{\"\u0000\":1}", "{\"c\td\":2}"})
    void malformedJsonIsRejected(String text) {
        assertRejected(text);
    }

    @Test
    void aTabOrNewlineBetweenTokensIsFine() {
        assertThat(read("{\n\t\"a\"\r\n:\t1\n}").get("a").asInt()).isEqualTo(1);
    }

    // ----- unpaired surrogates -----

    @ParameterizedTest(name = "unpaired surrogate: {0}")
    @ValueSource(strings = {
            "{\"a\":\"\\ud800\"}", "{\"a\":\"\\udbff\"}", "{\"a\":\"\\udc00\"}", "{\"a\":\"\\udfff\"}",
            "{\"title\":\"\\udc00\"}", "{\"title\":\"x\\ud800\"}", "{\"title\":\"\\ud800x\"}",
            "{\"a\":\"\\ud800\\ud800\"}", "{\"a\":\"\\udc00\\ud800\"}", "{\"a\":\"\\ud800\\u0041\"}",
            "{\"a\":\"\\ud83d\\ude00\\ude00\"}", "{\"a\":\"\\ud83d\\ud83d\\ude00\"}",
            "{\"\\ud800\":1}", "{\"\\udc00\":1}", "{\"x\\udc00\":1}", "{\"a\":{\"\\udc00\":1}}",
            "{\"a\":[\"\\ud800\"]}", "{\"a\":[[[{\"b\":\"\\udc00\"}]]]}", "{\"a\":{\"b\":{\"c\":\"\\ud800\"}}}",
            "{\"ignored\":\"\\ud800\",\"title\":\"ok\"}", "{\"title\":\"ok\",\"ignored\":\"\\ud800\"}"})
    void anUnpairedSurrogateInAnyStringOrMemberNameIsRejected(String text) {
        assertRejected(text);
    }

    @ParameterizedTest(name = "overwritten duplicate: {0}")
    @ValueSource(strings = {
            "{\"a\":\"\\ud800\",\"a\":\"x\"}", "{\"a\":\"\\udc00\",\"a\":1}", "{\"a\":{\"b\":\"\\ud800\"},\"a\":2}",
            "{\"\\ud800\":1,\"\\ud800\":2}"})
    void aValueThatALaterDuplicateKeyOverwritesIsStillChecked(String text) {
        assertRejected(text);
    }

    @Test
    void aWellFormedEscapedPairInAnIgnoredMemberIsAccepted() {
        assertThatCode(() -> read("{\"ignored\":\"\\ud83d\\ude00\",\"title\":\"ok\"}")).doesNotThrowAnyException();
    }

    // ----- limits at and over the boundary (V-22, V-24) -----

    @Test
    void aNumberOf1000DigitsPassesAndOf1001DigitsFails() {
        assertThat(read("{\"a\":" + "7".repeat(1_000) + "}").get("a").isNumber()).isTrue();
        assertThat(read("{\"a\":[" + "7".repeat(1_000) + "]}").isObject()).isTrue();
        assertThat(read("{\"a\":-" + "7".repeat(999) + "}").isObject()).isTrue();

        assertRejected("{\"a\":" + "7".repeat(1_001) + "}");
        assertRejected("{\"a\":[" + "7".repeat(1_001) + "]}");
        assertRejected("{\"a\":" + "7".repeat(1_001) + ",\"title\":\"x\"}");
    }

    private static String nestedInMember(int depth) {
        return "{\"a\":" + "[".repeat(depth - 1) + "]".repeat(depth - 1) + "}";
    }

    @Test
    void nestingOf1000PassesAndNestingOf1001Fails() {
        assertThat(read(nestedInMember(1_000)).isObject()).isTrue();
        assertRejected(nestedInMember(1_001));
    }

    @Test
    void nestedObjectsFollowTheSameLimit() {
        String atLimit = "{\"a\":".repeat(999) + "{}" + "}".repeat(999);
        String overLimit = "{\"a\":".repeat(1_000) + "{}" + "}".repeat(1_000);

        assertThat(read(atLimit).isObject()).isTrue();
        assertRejected(overLimit);
    }

    @Test
    void aMemberNameOf60000CharactersPasses() {
        String name = "n".repeat(60_000);

        JsonNode root = read("{\"" + name + "\":1,\"title\":\"x\"}");

        assertThat(root.has(name)).isTrue();
        assertThat(root.get("title").asString()).isEqualTo("x");
        assertThat(read("{\"title\":\"x\",\"a\":{\"" + name + "\":[]}}").isObject()).isTrue();
    }

    @Test
    void aStringOf60000CharactersPasses() {
        assertThat(read("{\"a\":\"" + "v".repeat(60_000) + "\"}").get("a").asString()).hasSize(60_000);
    }

    @Test
    void aBodyOfExactly65536BytesPassesAndOf65537BytesFails() {
        String prefix = "{\"a\":\"";
        String suffix = "\"}";
        int fixed = prefix.length() + suffix.length();

        assertThat(read(prefix + "x".repeat(65_536 - fixed) + suffix).get("a").asString()).hasSize(65_536 - fixed);
        assertRejected(prefix + "x".repeat(65_537 - fixed) + suffix);
        assertRejected("{}" + " ".repeat(65_535));
        assertThat(read("{}" + " ".repeat(65_534)).isObject()).isTrue();
    }

    @Test
    void aLongMemberNameIsAlsoCheckedForSurrogatesAtItsEnd() {
        assertRejected("{\"" + "n".repeat(60_000) + "\\udc00\":1}");
    }

    // ----- no logging, no state -----

    @Test
    void rejectedBodiesAreNotLogged(CapturedOutput output) {
        LoggerFactory.getLogger("probe").error("capture-works");
        String before = output.getAll();
        assertThat(before).contains("capture-works");

        assertRejected("{bad");
        assertRejected("[]");
        assertRejected("{\"a\":\"\\ud800\"}");
        assertRejected(bytes(0xFF));
        assertRejected("{\"a\":" + "7".repeat(1_001) + "}");

        assertThat(output.getAll()).isEqualTo(before);
    }

    @Test
    void theRejectionNeverEchoesTheRequestContent() {
        assertThatThrownBy(() -> read("{\"password\":\"hunter2\",\"x\":}")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getMessage()).doesNotContain("hunter2").doesNotContain("password"));
    }

    @Test
    void theSameReaderCanBeUsedRepeatedlyAndAFailureLeavesNoState() {
        assertRejected("{bad");
        assertThat(read("{\"a\":1}").get("a").asInt()).isEqualTo(1);
        assertRejected("[]");
        assertThat(read("{\"a\":2}").get("a").asInt()).isEqualTo(2);
    }
}
