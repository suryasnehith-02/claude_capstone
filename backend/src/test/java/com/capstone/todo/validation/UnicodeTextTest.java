package com.capstone.todo.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * U-TXT: the White_Space set of D-11, code-point counting and unpaired-surrogate detection. This file contains no
 * non-ASCII character: every such text is built from numbers, so no file encoding or editor can change a test.
 */
class UnicodeTextTest {

    private static final int NBSP = 0x00A0;
    private static final int FIGURE_SPACE = 0x2007;
    private static final int NARROW_NBSP = 0x202F;
    private static final int IDEOGRAPHIC_SPACE = 0x3000;
    private static final int NEL = 0x0085;
    private static final int BOM = 0xFEFF;
    private static final int FILE_SEPARATOR = 0x001C;
    private static final int UNIT_SEPARATOR = 0x001F;
    private static final int GRINNING_FACE = 0x1F600;

    /** The 25 White_Space code points of D-11, written out one by one so the test does not reuse the production logic. */
    private static final int[] WHITE_SPACE = {
            0x0009, 0x000A, 0x000B, 0x000C, 0x000D,
            0x0020, 0x0085, 0x00A0, 0x1680,
            0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008, 0x2009, 0x200A,
            0x2028, 0x2029, 0x202F, 0x205F, 0x3000};

    /** A string from code points. */
    private static String cps(int... codePoints) {
        StringBuilder text = new StringBuilder();
        for (int codePoint : codePoints) {
            text.appendCodePoint(codePoint);
        }
        return text.toString();
    }

    /** A string from raw UTF-16 units, so that a lone surrogate can be built. */
    private static String units(int... units) {
        StringBuilder text = new StringBuilder();
        for (int unit : units) {
            text.append((char) unit);
        }
        return text.toString();
    }

    /** {@code padding + "a" + padding}. */
    private static String around(int padding) {
        String pad = cps(padding);
        return pad + "a" + pad;
    }

    static IntStream whiteSpace() {
        return IntStream.of(WHITE_SPACE);
    }

    static Stream<Arguments> notWhiteSpace() {
        return Stream.of(
                Arguments.of(0xFEFF, "zero width no-break space (BOM)"),
                Arguments.of(0x001C, "file separator"),
                Arguments.of(0x001D, "group separator"),
                Arguments.of(0x001E, "record separator"),
                Arguments.of(0x001F, "unit separator"),
                Arguments.of(0x0000, "NUL"),
                Arguments.of(0x180E, "Mongolian vowel separator (not White_Space since Unicode 6.3)"),
                Arguments.of(0x200B, "zero width space"),
                Arguments.of(0x200C, "zero width non-joiner"),
                Arguments.of(0x2060, "word joiner"),
                Arguments.of(0x00AD, "soft hyphen"),
                Arguments.of(0x0008, "backspace"),
                Arguments.of(0x000E, "shift out"),
                Arguments.of(0x0084, "C1 control before NEL"),
                Arguments.of(0x0086, "C1 control after NEL"),
                Arguments.of(0x00A1, "inverted exclamation mark"),
                Arguments.of(0x2027, "hyphenation point"),
                Arguments.of(0x202A, "left-to-right embedding"),
                Arguments.of(0x205E, "vertical four dots"),
                Arguments.of(0x2061, "function application"),
                Arguments.of(0x3001, "ideographic comma"),
                Arguments.of(0x1F600, "astral emoji"));
    }

    @Test
    void exactlyTwentyFiveCodePointsAreWhiteSpaceAcrossTheWholeUnicodeRange() {
        List<Integer> found = new ArrayList<>();
        for (int cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
            if (UnicodeText.isWhiteSpace(cp)) {
                found.add(cp);
            }
        }

        assertThat(WHITE_SPACE).hasSize(25);
        assertThat(found).containsExactlyElementsOf(IntStream.of(WHITE_SPACE).boxed().toList());
    }

    @ParameterizedTest(name = "white space #{index} is trimmed from both ends")
    @MethodSource("whiteSpace")
    void everyWhiteSpaceCodePointIsTrimmedFromBothEnds(int codePoint) {
        String padding = cps(codePoint);

        assertThat(UnicodeText.trim(padding + "a b" + padding)).isEqualTo("a b");
        assertThat(UnicodeText.trim(padding + padding + "a" + padding + padding)).isEqualTo("a");
        assertThat(UnicodeText.trim(padding)).isEmpty();
        assertThat(UnicodeText.trim(padding + padding)).isEmpty();
    }

    @ParameterizedTest(name = "{1} is kept")
    @MethodSource("notWhiteSpace")
    void codePointsOutsideTheSetAreKept(int codePoint, String name) {
        String kept = cps(codePoint);

        assertThat(UnicodeText.isWhiteSpace(codePoint)).isFalse();
        assertThat(UnicodeText.trim(kept + "a" + kept)).isEqualTo(kept + "a" + kept);
        assertThat(UnicodeText.trim(kept)).isEqualTo(kept);
        assertThat(UnicodeText.length(UnicodeText.trim(kept))).isEqualTo(1);
    }

    @Test
    void theSpecifiedExamplesBehaveAsDecided() {
        assertThat(UnicodeText.trim(" a ")).isEqualTo("a");
        assertThat(UnicodeText.trim(around(NBSP))).isEqualTo("a");
        assertThat(UnicodeText.trim(around(FIGURE_SPACE))).isEqualTo("a");
        assertThat(UnicodeText.trim(around(NARROW_NBSP))).isEqualTo("a");
        assertThat(UnicodeText.trim(around(IDEOGRAPHIC_SPACE))).isEqualTo("a");
        assertThat(UnicodeText.trim(around(NEL))).isEqualTo("a");
        assertThat(UnicodeText.trim(around(BOM))).isEqualTo(around(BOM));
        assertThat(UnicodeText.trim(around(FILE_SEPARATOR))).isEqualTo(around(FILE_SEPARATOR));
        assertThat(UnicodeText.trim(around(UNIT_SEPARATOR))).isEqualTo(around(UNIT_SEPARATOR));
    }

    @Test
    void whyStringStripAndTrimAreNotUsed() {
        // strip() follows Character.isWhitespace: it keeps U+00A0, U+2007 and U+202F ...
        assertThat(around(NBSP).strip()).isEqualTo(around(NBSP));
        assertThat(around(FIGURE_SPACE).strip()).isEqualTo(around(FIGURE_SPACE));
        assertThat(around(NARROW_NBSP).strip()).isEqualTo(around(NARROW_NBSP));
        // ... and removes U+001C to U+001F, which are not White_Space.
        assertThat(around(FILE_SEPARATOR).strip()).isEqualTo("a");
        // trim() removes only up to U+0020.
        assertThat(around(IDEOGRAPHIC_SPACE).trim()).isEqualTo(around(IDEOGRAPHIC_SPACE));

        assertThat(UnicodeText.trim(around(NBSP))).isEqualTo("a");
        assertThat(UnicodeText.trim(around(FIGURE_SPACE))).isEqualTo("a");
        assertThat(UnicodeText.trim(around(FILE_SEPARATOR))).isEqualTo(around(FILE_SEPARATOR));
    }

    @Test
    void internalWhitespaceIsPreservedAndOnlyTheEndsAreTrimmed() {
        String ideographic = cps(IDEOGRAPHIC_SPACE);

        assertThat(UnicodeText.trim("  a \t b" + ideographic + "c  ")).isEqualTo("a \t b" + ideographic + "c");
        assertThat(UnicodeText.trim("\n  mixed  \r")).isEqualTo("mixed");
        assertThat(UnicodeText.trim("a")).isEqualTo("a");
        assertThat(UnicodeText.trim("")).isEmpty();
    }

    @Test
    void astralCharactersNextToWhiteSpaceSurviveTrimming() {
        String face = cps(GRINNING_FACE);
        String ideographic = cps(IDEOGRAPHIC_SPACE);

        assertThat(UnicodeText.trim(" " + face + " ")).isEqualTo(face);
        assertThat(UnicodeText.trim(ideographic + face + face + ideographic)).isEqualTo(face + face);
    }

    @Test
    void lengthCountsCodePointsNotUtf16Units() {
        String face = cps(GRINNING_FACE);

        assertThat(UnicodeText.length("")).isZero();
        assertThat(UnicodeText.length("abc")).isEqualTo(3);
        assertThat(UnicodeText.length(face)).isEqualTo(1);
        assertThat(face.length()).isEqualTo(2);
        assertThat(UnicodeText.length("a" + face + "b")).isEqualTo(3);
        assertThat(UnicodeText.length(face.repeat(100))).isEqualTo(100);
        assertThat(UnicodeText.length(cps('e', 0x0301))).isEqualTo(2);
        assertThat(UnicodeText.length(cps(BOM))).isEqualTo(1);
    }

    static Stream<String> wellFormed() {
        String face = cps(GRINNING_FACE);
        return Stream.of("", "abc", face, "a" + face + "b" + face, face + face, units(0xD83D, 0xDE00));
    }

    @ParameterizedTest(name = "paired or no surrogate #{index}")
    @MethodSource("wellFormed")
    void wellFormedTextHasNoUnpairedSurrogate(String text) {
        assertThat(UnicodeText.hasUnpairedSurrogate(text)).isFalse();
    }

    static Stream<String> unpaired() {
        String face = cps(GRINNING_FACE);
        return Stream.of(
                units(0xD800),
                units(0xDC00),
                units(0xDBFF),
                units(0xDFFF),
                "a" + units(0xD800),
                units(0xD800) + "a",
                units(0xDC00) + "a",
                "a" + units(0xDC00),
                units(0xDC00, 0xD800),
                units(0xD800) + face,
                face + units(0xDE00),
                units(0xD83D) + face + units(0xD83D),
                units(0xD800, 0xD800, 0xDC00));
    }

    @ParameterizedTest(name = "unpaired surrogate #{index}")
    @MethodSource("unpaired")
    void anUnpairedHighOrLowSurrogateIsDetectedAnywhere(String text) {
        assertThat(UnicodeText.hasUnpairedSurrogate(text)).isTrue();
    }
}
