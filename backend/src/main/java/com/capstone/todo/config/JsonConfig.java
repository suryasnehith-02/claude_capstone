package com.capstone.todo.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The dedicated mapper of the API (architecture D-14, D-15). Every read feature is set explicitly, on or off,
 * and never left to the Jackson version default; Boot's {@code spring.jackson.*} properties do not affect it.
 *
 * <p>The limits are the body cap of 65,536 bytes (D-2) for the document, the string and the member name, so none
 * of them triggers before the size check; nesting and number length are 1,000 (G2-5 (b), DR-18). The token limit
 * is the document limit too: every token needs at least one character.
 */
@Configuration(proxyBeanMethods = false)
public class JsonConfig {

    public static final int MAX_NESTING_DEPTH = 1_000;
    public static final int MAX_NUMBER_LENGTH = 1_000;
    public static final int MAX_STRING_LENGTH = 65_536;
    public static final int MAX_NAME_LENGTH = 65_536;
    public static final long MAX_DOCUMENT_LENGTH = 65_536L;
    public static final long MAX_TOKEN_COUNT = 65_536L;

    /**
     * Every {@link JsonReadFeature} and its setting. All are off: no comments, single quotes, unquoted names,
     * trailing commas, missing values, NaN or Infinity, leading zeros, leading plus, leading or trailing decimal
     * point, unescaped control characters, or backslash-escaping of any character.
     */
    public static final Map<JsonReadFeature, Boolean> READ_FEATURES = readFeatures();

    /** Tree-level settings: duplicate keys do not fail (last occurrence wins) and trailing tokens fail. */
    public static final boolean DUPLICATE_KEYS_FAIL = false;
    public static final boolean TRAILING_TOKENS_FAIL = true;

    @Bean
    public JsonMapper apiJsonMapper() {
        return newApiJsonMapper();
    }

    /** Builds the mapper; also used directly by unit tests that need no Spring context. */
    public static JsonMapper newApiJsonMapper() {
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxNestingDepth(MAX_NESTING_DEPTH)
                .maxNumberLength(MAX_NUMBER_LENGTH)
                .maxStringLength(MAX_STRING_LENGTH)
                .maxNameLength(MAX_NAME_LENGTH)
                .maxDocumentLength(MAX_DOCUMENT_LENGTH)
                .maxTokenCount(MAX_TOKEN_COUNT)
                .build();
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(constraints)
                .build();

        JsonMapper.Builder builder = JsonMapper.builder(factory);
        for (Map.Entry<JsonReadFeature, Boolean> setting : READ_FEATURES.entrySet()) {
            builder.configure(setting.getKey(), setting.getValue());
        }
        return builder
                .configure(StreamReadFeature.STRICT_DUPLICATE_DETECTION, false)
                .configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, DUPLICATE_KEYS_FAIL)
                .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, TRAILING_TOKENS_FAIL)
                .configure(JsonWriteFeature.ESCAPE_NON_ASCII, false)
                .configure(JsonWriteFeature.ESCAPE_FORWARD_SLASHES, false)
                .configure(SerializationFeature.INDENT_OUTPUT, false)
                .build();
    }

    private static Map<JsonReadFeature, Boolean> readFeatures() {
        Map<JsonReadFeature, Boolean> features = new EnumMap<>(JsonReadFeature.class);
        for (JsonReadFeature feature : JsonReadFeature.values()) {
            features.put(feature, Boolean.FALSE);
        }
        return Collections.unmodifiableMap(features);
    }
}
