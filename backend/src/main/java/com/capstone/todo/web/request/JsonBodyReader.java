package com.capstone.todo.web.request;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.capstone.todo.validation.UnicodeText;
import com.capstone.todo.web.response.ApiException;
import com.capstone.todo.web.response.ErrorCode;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stage P5 (architecture D-11, D-14): turns the raw body bytes into a Jackson tree whose root is a JSON object.
 *
 * <p>Order of the checks: strict UTF-8 decoding (malformed, overlong and encoded-surrogate bytes are rejected, the
 * platform charset is never used); an explicit check that the text does not start with U+FEFF; a scan of every
 * string value and member name for an unpaired surrogate; {@code readTree} on the shared API mapper, whose
 * {@code FAIL_ON_TRAILING_TOKENS} rejects a second value; an object root. The mapper's explicit read features and
 * limits (nesting, number length, string, name and document length) decide what is well-formed JSON.
 *
 * <p>The surrogate scan reads the tokens, not the finished tree, so a value that a later duplicate key overwrites
 * is checked too. Duplicate keys themselves are not an error: the last one wins.
 *
 * <p>Every failure, including every {@link JacksonException}, becomes the same INVALID_REQUEST_BODY
 * {@link ApiException}. It carries no cause and no input, and nothing is logged here (the client's mistake is not a
 * server fault).
 */
@Component
public class JsonBodyReader {

    private static final char BYTE_ORDER_MARK = (char) 0xFEFF;

    private final JsonMapper mapper;

    public JsonBodyReader(@Qualifier("apiJsonMapper") JsonMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @param body the request body bytes; null or empty is not a document
     * @return the root object
     * @throws ApiException INVALID_REQUEST_BODY
     */
    public JsonNode read(byte[] body) {
        if (body == null || body.length == 0) {
            throw invalid();
        }
        String text = decodeStrictly(body);
        if (text.charAt(0) == BYTE_ORDER_MARK) {
            throw invalid();
        }
        try {
            rejectUnpairedSurrogates(text);
            JsonNode root = mapper.readTree(text);
            if (root == null || !root.isObject()) {
                throw invalid();
            }
            return root;
        } catch (JacksonException e) {
            throw invalid();
        }
    }

    private static String decodeStrictly(byte[] body) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body))
                    .toString();
        } catch (CharacterCodingException e) {
            throw invalid();
        }
    }

    private void rejectUnpairedSurrogates(String text) {
        try (JsonParser parser = mapper.createParser(text)) {
            for (JsonToken token = parser.nextToken(); token != null; token = parser.nextToken()) {
                if (token == JsonToken.PROPERTY_NAME || token == JsonToken.VALUE_STRING) {
                    if (UnicodeText.hasUnpairedSurrogate(parser.getString())) {
                        throw invalid();
                    }
                }
            }
        }
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.INVALID_REQUEST_BODY);
    }
}
