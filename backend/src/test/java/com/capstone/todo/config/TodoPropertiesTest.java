package com.capstone.todo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import com.capstone.todo.config.TodoProperties.JournalMode;
import com.capstone.todo.support.TestDataDir;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TodoPropertiesTest {

    /** Every key of architecture.md 7.5 (names as they exist in Spring Boot 4.1.3, see application.properties). */
    private static final Map<String, String> PLANNED_KEYS = Map.ofEntries(
            Map.entry("server.address", "127.0.0.1"),
            Map.entry("server.port", "8080"),
            Map.entry("todo.data-file", "./data/todo.db"),
            Map.entry("todo.store.journal-mode", "DELETE"),
            Map.entry("spring.web.error.include-stacktrace", "never"),
            Map.entry("spring.web.error.include-message", "never"),
            Map.entry("spring.web.error.whitelabel.enabled", "false"),
            Map.entry("spring.mvc.problemdetails.enabled", "false"),
            Map.entry("spring.mvc.formcontent.filter.enabled", "false"),
            Map.entry("spring.mvc.hiddenmethod.filter.enabled", "false"),
            Map.entry("spring.servlet.multipart.enabled", "false"),
            Map.entry("spring.web.resources.add-mappings", "false"),
            Map.entry("spring.servlet.encoding.enabled", "false"),
            Map.entry("spring.sql.init.mode", "never"));

    private static TodoProperties bind(Map<String, Object> properties) {
        return new Binder(new MapConfigurationPropertySource(properties)).bindOrCreate("todo", TodoProperties.class);
    }

    @Test
    void defaultsApplyWhenNothingIsConfigured() {
        TodoProperties properties = bind(Map.of());

        assertThat(properties.dataFile()).isEqualTo("./data/todo.db");
        assertThat(properties.store().journalMode()).isEqualTo(JournalMode.DELETE);
        assertThat(TodoProperties.SYNCHRONOUS).isEqualTo("FULL");
    }

    @Test
    void relativeDataFileResolvesToAnAbsoluteNormalisedPath() {
        TodoProperties properties = bind(Map.of());

        Path resolved = properties.dataFilePath();

        assertThat(resolved).isAbsolute();
        assertThat(resolved).isEqualTo(Path.of("").toAbsolutePath().resolve("data").resolve("todo.db").normalize());
        assertThat(resolved.toString()).doesNotContain("..");
    }

    @Test
    void absoluteDataFileIsKeptAndDotSegmentsAreNormalised() {
        Path directory = TestDataDir.create(TodoPropertiesTest.class);
        String configured = directory.resolve("sub").resolve("..").resolve("my.db").toString();

        TodoProperties properties = bind(Map.of("todo.data-file", configured));

        assertThat(properties.dataFilePath()).isEqualTo(directory.resolve("my.db"));
    }

    @Test
    void prepareDataFileCreatesTheMissingParentDirectoriesButNotTheFile() {
        Path directory = TestDataDir.create(TodoPropertiesTest.class);
        Path file = directory.resolve("a").resolve("b").resolve("todo.db");
        TodoProperties properties = bind(Map.of("todo.data-file", file.toString()));

        Path prepared = properties.prepareDataFile();

        assertThat(prepared).isEqualTo(file);
        assertThat(file.getParent()).isDirectory();
        assertThat(file).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankDataFileFailsBinding(String value) {
        assertThatThrownBy(() -> bind(Map.of("todo.data-file", value)))
                .isInstanceOf(BindException.class)
                .hasStackTraceContaining("todo.data-file must not be blank");
    }

    @Test
    void dataFileWithAnIllegalCharacterFailsBinding() {
        assertThatThrownBy(() -> bind(Map.of("todo.data-file", "bad\u0000name.db")))
                .isInstanceOf(BindException.class)
                .hasStackTraceContaining("todo.data-file is not a valid path");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DELETE", "TRUNCATE", "WAL"})
    void allowedJournalModesAreAccepted(String mode) {
        assertThat(bind(Map.of("todo.store.journal-mode", mode)).store().journalMode())
                .isEqualTo(JournalMode.valueOf(mode));
    }

    @Test
    void journalModeBindingIsCaseInsensitive() {
        assertThat(bind(Map.of("todo.store.journal-mode", "wal")).store().journalMode())
                .isEqualTo(JournalMode.WAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMORY", "OFF", "PERSIST", "nonsense"})
    void unknownJournalModeFailsBindingWithAMessageNamingTheKey(String mode) {
        assertThatThrownBy(() -> bind(Map.of("todo.store.journal-mode", mode)))
                .isInstanceOf(BindException.class)
                .hasStackTraceContaining("todo.store.journal-mode");
    }

    /** Observed with Spring Boot 4.1.3: an empty value counts as "not set", so the default DELETE applies. */
    @Test
    void emptyJournalModeFallsBackToTheDefault() {
        assertThat(bind(Map.of("todo.store.journal-mode", "")).store().journalMode()).isEqualTo(JournalMode.DELETE);
    }

    /** An environment whose "systemEnvironment" source (the name Boot's relaxed env-var mapping keys on) is fake. */
    private static TodoProperties bindFromEnvironmentVariables(Map<String, Object> variables) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        return Binder.get(environment).bindOrCreate("todo", TodoProperties.class);
    }

    @Test
    void dataFileCanBeSetFromTheEnvironmentVariableTodoDataFile() {
        Path file = TestDataDir.newDataFile(TodoPropertiesTest.class);

        TodoProperties properties = bindFromEnvironmentVariables(Map.of("TODO_DATA_FILE", file.toString()));

        assertThat(properties.dataFilePath()).isEqualTo(file);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TODO_STORE_JOURNAL_MODE", "TODO_STORE_JOURNALMODE"})
    void journalModeFromTheEnvironment(String variable) {
        TodoProperties properties = bindFromEnvironmentVariables(Map.of(variable, "TRUNCATE"));

        assertThat(properties.store().journalMode()).isEqualTo(JournalMode.TRUNCATE);
    }

    @Test
    void applicationPropertiesHoldEveryPlannedKeyAndNoDatasourceKey() throws IOException {
        Properties loaded = new Properties();
        try (InputStream in = TodoPropertiesTest.class.getResourceAsStream("/application.properties")) {
            assertThat(in).isNotNull();
            loaded.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        Map<String, String> actual = new HashMap<>();
        loaded.forEach((k, v) -> actual.put((String) k, (String) v));

        assertThat(actual).containsExactlyInAnyOrderEntriesOf(PLANNED_KEYS);
        assertThat(actual.keySet()).noneMatch(key -> key.startsWith("spring.datasource"));
        assertThat(actual.values()).noneMatch(value -> value.toLowerCase().contains("password"));
    }

    @Test
    void everyFrameworkKeyInApplicationPropertiesExistsAndIsNotDeprecatedInSpringBoot() throws IOException {
        Set<String> known = new HashSet<>();
        Set<String> deprecated = new HashSet<>();
        JsonMapper mapper = JsonMapper.builder().build();
        List<URL> resources = Collections.list(
                TodoPropertiesTest.class.getClassLoader().getResources("META-INF/spring-configuration-metadata.json"));
        assertThat(resources).isNotEmpty();
        for (URL resource : resources) {
            try (InputStream in = resource.openStream()) {
                JsonNode root = mapper.readTree(in);
                for (JsonNode property : root.path("properties")) {
                    String name = property.path("name").asString();
                    known.add(name);
                    if (property.has("deprecation")) {
                        deprecated.add(name);
                    }
                }
            }
        }

        Set<String> frameworkKeys = new HashSet<>(PLANNED_KEYS.keySet());
        frameworkKeys.removeIf(key -> key.startsWith("todo."));

        assertThat(frameworkKeys).isNotEmpty().allSatisfy(key -> {
            assertThat(known).as("%s exists in Spring Boot's configuration metadata", key).contains(key);
            assertThat(deprecated).as("%s is not deprecated", key).doesNotContain(key);
        });
    }
}
