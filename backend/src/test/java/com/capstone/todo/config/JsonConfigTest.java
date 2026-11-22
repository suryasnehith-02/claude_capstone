package com.capstone.todo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.capstone.todo.web.response.ApiResponses;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * U-JSON, mapper side (architecture D-14, V-22, DR-18): every read feature and every limit of the dedicated mapper
 * with its expected value, each proved by behaviour.
 */
class JsonConfigTest {

    private final JsonMapper mapper = JsonConfig.newApiJsonMapper();

    /** One probe document per {@link JsonReadFeature}: valid only if that feature is on. */
    private static final Map<String, String> PROBES = probes();

    private static Map<String, String> probes() {
        Map<String, String> probes = new LinkedHashMap<>();
        probes.put("ALLOW_JAVA_COMMENTS", "{/*c*/\"a\":1}");
        probes.put("ALLOW_YAML_COMMENTS", "{#c\n\"a\":1}");
        probes.put("ALLOW_SINGLE_QUOTES", "{'a':1}");
        probes.put("ALLOW_UNQUOTED_PROPERTY_NAMES", "{a:1}");
        probes.put("ALLOW_TRAILING_COMMA", "{\"a\":1,}");
        probes.put("ALLOW_NON_NUMERIC_NUMBERS", "{\"a\":NaN}");
        probes.put("ALLOW_LEADING_ZEROS_FOR_NUMBERS", "{\"a\":007}");
        probes.put("ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS", "{\"a\":+1}");
        probes.put("ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS", "{\"a\":.5}");
        probes.put("ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS", "{\"a\":5.}");
        probes.put("ALLOW_MISSING_VALUES", "[1,,2]");
        probes.put("ALLOW_UNESCAPED_CONTROL_CHARS", "{\"a\":\"x\ty\"}");
        probes.put("ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER", "{\"a\":\"\\q\"}");
        probes.put("ALLOW_RS_CONTROL_CHAR", "{\u001e\"a\":1}");
        return probes;
    }

    static Stream<Arguments> readFeatureProbes() {
        return PROBES.entrySet().stream().map(e -> Arguments.of(e.getKey(), e.getValue()));
    }

    // ---- read features: all explicit, all off ----

    @Test
    void theProbeTableCoversEveryReadFeatureOfThePinnedJackson() {
        List<String> names = Stream.of(JsonReadFeature.values()).map(Enum::name).toList();

        assertThat(PROBES.keySet()).containsExactlyInAnyOrderElementsOf(names);
    }

    @Test
    void everyReadFeatureIsSetExplicitlyToOff() {
        assertThat(JsonConfig.READ_FEATURES.keySet()).containsExactlyInAnyOrder(JsonReadFeature.values());
        assertThat(JsonConfig.READ_FEATURES.values()).containsOnly(Boolean.FALSE);
    }

    @ParameterizedTest(name = "{0} is off")
    @MethodSource("readFeatureProbes")
    void eachReadFeatureIsOff(String feature, String probe) {
        JsonReadFeature readFeature = JsonReadFeature.valueOf(feature);
        JsonMapper permissive = JsonMapper.builder().enable(readFeature).build();

        assertThatCode(() -> permissive.readTree(probe))
                .as("the probe for %s must be valid when the feature is on", feature).doesNotThrowAnyException();
        assertThatThrownBy(() -> mapper.readTree(probe)).as(feature).isInstanceOf(JacksonException.class);
    }

    // ---- duplicates and trailing tokens ----

    @Test
    void duplicateKeysResolveLastOccurrenceWins() {
        JsonNode node = mapper.readTree("{\"title\":\"first\",\"title\":\"second\",\"o\":{\"x\":1,\"x\":2}}");

        assertThat(node.get("title").asString()).isEqualTo("second");
        assertThat(node.get("o").get("x").asInt()).isEqualTo(2);
        assertThat(JsonConfig.DUPLICATE_KEYS_FAIL).isFalse();
    }

    @Test
    void trailingTokensFailAndTrailingWhitespaceIsFine() {
        assertThat(JsonConfig.TRAILING_TOKENS_FAIL).isTrue();
        assertThatThrownBy(() -> mapper.readTree("{} {}")).isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> mapper.readTree("{}x")).isInstanceOf(JacksonException.class);
        assertThatThrownBy(() -> mapper.readTree("{},")).isInstanceOf(JacksonException.class);
        assertThatCode(() -> mapper.readTree("{}  \n\t ")).doesNotThrowAnyException();
    }

    // ---- limits, as configured ----

    @Test
    void everyLimitHasItsExpectedValue() {
        StreamReadConstraints constraints = mapper.tokenStreamFactory().streamReadConstraints();

        assertThat(constraints.getMaxNestingDepth()).isEqualTo(1_000);
        assertThat(constraints.getMaxNumberLength()).isEqualTo(1_000);
        assertThat(constraints.getMaxStringLength()).isEqualTo(65_536);
        assertThat(constraints.getMaxNameLength()).isEqualTo(65_536);
        assertThat(constraints.getMaxDocumentLength()).isEqualTo(65_536L);
        assertThat(constraints.getMaxTokenCount()).isEqualTo(65_536L);
        assertThat(JsonConfig.MAX_NESTING_DEPTH).isEqualTo(1_000);
        assertThat(JsonConfig.MAX_NUMBER_LENGTH).isEqualTo(1_000);
        assertThat(JsonConfig.MAX_STRING_LENGTH).isEqualTo(65_536);
        assertThat(JsonConfig.MAX_NAME_LENGTH).isEqualTo(65_536);
        assertThat(JsonConfig.MAX_DOCUMENT_LENGTH).isEqualTo(65_536L);
        assertThat(JsonConfig.MAX_TOKEN_COUNT).isEqualTo(65_536L);
    }

    // ---- limits, by behaviour ----

    @Test
    void nestingOf1000PassesAndNestingOf1001Fails() {
        assertThatCode(() -> mapper.readTree(nested(1_000))).doesNotThrowAnyException();
        assertThatThrownBy(() -> mapper.readTree(nested(1_001))).isInstanceOf(StreamConstraintsException.class);
    }

    @Test
    void aNumberOf1000DigitsPassesAndOf1001DigitsFails() {
        assertThatCode(() -> mapper.readTree("{\"n\":" + "7".repeat(1_000) + "}")).doesNotThrowAnyException();
        assertThatThrownBy(() -> mapper.readTree("{\"n\":" + "7".repeat(1_001) + "}"))
                .isInstanceOf(StreamConstraintsException.class);
    }

    @Test
    void aMemberNameOf60000CharactersPasses() {
        String name = "n".repeat(60_000);

        JsonNode node = mapper.readTree("{\"" + name + "\":1}");

        assertThat(node.get(name).asInt()).isEqualTo(1);
    }

    @Test
    void aStringOf60000CharactersPasses() {
        String value = "v".repeat(60_000);

        assertThat(mapper.readTree("{\"a\":\"" + value + "\"}").get("a").asString()).isEqualTo(value);
    }

    @Test
    void aDocumentOfExactly65536CharactersPassesAndOneMoreFails() {
        String fixed = "{\"a\":\"\"}";
        String atLimit = "{\"a\":\"" + "x".repeat(65_536 - fixed.length()) + "\"}";
        String overLimit = "{\"a\":\"" + "x".repeat(65_537 - fixed.length()) + "\"}";

        assertThat(atLimit).hasSize(65_536);
        assertThat(overLimit).hasSize(65_537);
        assertThatCode(() -> mapper.readTree(atLimit)).doesNotThrowAnyException();
        assertThatThrownBy(() -> mapper.readTree(overLimit)).isInstanceOf(StreamConstraintsException.class);
    }

    @Test
    void jacksonsOwnDefaultsWouldRejectTheseInputsSoTheExplicitLimitsMatter() {
        JsonMapper defaults = JsonMapper.builder().build();

        assertThatThrownBy(() -> defaults.readTree("{\"" + "n".repeat(60_000) + "\":1}"))
                .isInstanceOf(StreamConstraintsException.class);
        assertThatThrownBy(() -> defaults.readTree(nested(1_000))).isInstanceOf(StreamConstraintsException.class);
        assertThatCode(() -> mapper.readTree("{\"" + "n".repeat(60_000) + "\":1}")).doesNotThrowAnyException();
        assertThatCode(() -> mapper.readTree(nested(1_000))).doesNotThrowAnyException();
    }

    // ---- wiring ----

    @Test
    void theMapperIsTheBeanNamedApiJsonMapperAndApiResponsesUsesIt() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(JsonConfig.class, ApiResponses.class)) {
            assertThat(context.getBeansOfType(JsonMapper.class)).containsOnlyKeys("apiJsonMapper");
            assertThat(context.getBean(ApiResponses.class)).isNotNull();
        }
    }

    private static String nested(int depth) {
        return "[".repeat(depth) + "]".repeat(depth);
    }
}
