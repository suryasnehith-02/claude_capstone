package com.capstone.todo.web.request;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import com.capstone.todo.web.response.ApiException;
import com.capstone.todo.web.response.ErrorCode;

/**
 * Stage P4 (architecture D-13): the one place an id is read from the request.
 *
 * <p>The input is the raw, undecoded path segment. It is percent-decoded exactly once, strictly: a {@code %} must be
 * followed by two hexadecimal digits, the decoded bytes must be valid UTF-8, and {@code +} stays a plus. The result
 * must then match the canonical 8-4-4-4-12 form, in either letter case, and is returned in lowercase. So
 * {@code %61} is accepted where an {@code a} may stand, while {@code %2530} decodes once to {@code %30} and is
 * rejected. {@link UUID#fromString(String)} is not the check: it accepts forms such as {@code 1-1-1-1-1}. Any
 * version or variant is accepted, because ids are opaque.
 *
 * <p>Every failure is an INVALID_ID {@link ApiException} that carries none of the input.
 */
public final class IdParser {

    private static final Pattern CANONICAL = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private IdParser() {
    }

    /**
     * @param rawSegment the raw id segment of the request URI; null (a route without an id) is also INVALID_ID
     * @return the id, which prints as lowercase canonical text
     * @throws ApiException INVALID_ID
     */
    public static UUID parse(String rawSegment) {
        if (rawSegment == null) {
            throw invalid();
        }
        String decoded = decodeOnce(rawSegment);
        if (!CANONICAL.matcher(decoded).matches()) {
            throw invalid();
        }
        return UUID.fromString(decoded.toLowerCase(Locale.ROOT));
    }

    private static String decodeOnce(String raw) {
        byte[] bytes = new byte[raw.length()];
        int count = 0;
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '%') {
                int high = i + 1 < raw.length() ? hexValue(raw.charAt(i + 1)) : -1;
                int low = i + 2 < raw.length() ? hexValue(raw.charAt(i + 2)) : -1;
                if (high < 0 || low < 0) {
                    throw invalid();
                }
                bytes[count++] = (byte) (high << 4 | low);
                i += 3;
            } else if (c < 0x80) {
                bytes[count++] = (byte) c;
                i++;
            } else {
                // A raw non-ASCII character can never become a canonical UUID.
                throw invalid();
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, 0, count))
                    .toString();
        } catch (CharacterCodingException e) {
            throw invalid();
        }
    }

    private static int hexValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.INVALID_ID);
    }
}
