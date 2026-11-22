package com.capstone.todo.validation;

/**
 * Text rules of architecture D-11: trimming by the Unicode {@code White_Space} property, counting in code points,
 * and detection of unpaired surrogates.
 *
 * <p>{@link String#strip()} and {@link String#trim()} are deliberately not used: {@code strip()} follows
 * {@link Character#isWhitespace(int)}, which misses U+00A0, U+2007 and U+202F and includes U+001C to U+001F;
 * {@code trim()} removes every code point up to U+0020 and nothing above it.
 */
public final class UnicodeText {

    private UnicodeText() {
    }

    /**
     * True for the 25 code points with the Unicode {@code White_Space} property: U+0009 to U+000D, U+0020, U+0085,
     * U+00A0, U+1680, U+2000 to U+200A, U+2028, U+2029, U+202F, U+205F and U+3000. U+FEFF and U+001C to U+001F are
     * not White_Space.
     */
    public static boolean isWhiteSpace(int codePoint) {
        return (codePoint >= 0x0009 && codePoint <= 0x000D)
                || codePoint == 0x0020
                || codePoint == 0x0085
                || codePoint == 0x00A0
                || codePoint == 0x1680
                || (codePoint >= 0x2000 && codePoint <= 0x200A)
                || codePoint == 0x2028
                || codePoint == 0x2029
                || codePoint == 0x202F
                || codePoint == 0x205F
                || codePoint == 0x3000;
    }

    /** Removes leading and trailing White_Space code points; internal whitespace is kept. */
    public static String trim(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isWhiteSpace(text.charAt(start))) {
            start++;
        }
        while (end > start && isWhiteSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    /** Length in Unicode code points (an astral character counts once). */
    public static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    /** True if the text has a high surrogate without a following low surrogate, or a low surrogate without a preceding high one. */
    public static boolean hasUnpairedSurrogate(String text) {
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < length && Character.isLowSurrogate(text.charAt(i + 1))) {
                    i++;
                } else {
                    return true;
                }
            } else if (Character.isLowSurrogate(c)) {
                return true;
            }
        }
        return false;
    }
}
