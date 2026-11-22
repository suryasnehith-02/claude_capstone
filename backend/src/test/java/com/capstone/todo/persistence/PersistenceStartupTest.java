package com.capstone.todo.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.capstone.todo.TodoApplication;
import com.capstone.todo.config.TodoProperties.JournalMode;
import com.capstone.todo.support.TestDataDir;

/**
 * T-11: an application that cannot trust its store does not start (AC-14, D-6, NFR-4). Each case starts the real
 * application class (no web server) on a file in {@code TestDataDir} and expects the start itself to fail.
 */
class PersistenceStartupTest {

    private static ConfigurableApplicationContext start(Path file, String... extraArguments) {
        String[] arguments = new String[extraArguments.length + 2];
        arguments[0] = "--todo.data-file=" + file;
        arguments[1] = "--spring.main.banner-mode=off";
        System.arraycopy(extraArguments, 0, arguments, 2, extraArguments.length);
        return new SpringApplicationBuilder(TodoApplication.class).web(WebApplicationType.NONE).run(arguments);
    }

    private static String messagesOf(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            messages.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append(" | ");
        }
        return messages.toString();
    }

    @Test
    void aNewFileStartsTheApplicationAndCreatesTheSchema() {
        Path file = TestDataDir.newDataFile(PersistenceStartupTest.class);

        try (ConfigurableApplicationContext context = start(file)) {
            assertThat(context.isRunning()).isTrue();
            assertThat(file).isRegularFile();
        }
    }

    @Test
    void aMissingParentDirectoryIsCreatedAtStartup() {
        Path file = TestDataDir.create(PersistenceStartupTest.class).resolve("a").resolve("b").resolve("todo.db");

        try (ConfigurableApplicationContext context = start(file)) {
            assertThat(context.isRunning()).isTrue();
            assertThat(file.getParent()).isDirectory();
        }
    }

    @Test
    void anUnknownSchemaVersionStopsTheApplicationFromStarting() {
        Path file = TestDataDir.newDataFile(PersistenceStartupTest.class);
        try (SqliteStore store = SqliteStore.open(file, JournalMode.DELETE)) {
            store.jdbc.sql("PRAGMA user_version = 5").update();
        }

        Throwable failure = catchThrowable(() -> start(file).close());

        assertThat(failure).isNotNull();
        assertThat(messagesOf(failure)).contains("schema version 5");
    }

    @Test
    void aFileThatIsNotADatabaseStopsTheApplicationFromStarting() throws Exception {
        Path file = TestDataDir.newDataFile(PersistenceStartupTest.class);
        Files.writeString(file, "This is plain text, not a SQLite database. ".repeat(40));

        Throwable failure = catchThrowable(() -> start(file).close());

        assertThat(failure).isNotNull();
        assertThat(messagesOf(failure)).doesNotContain("This is plain text");
    }

    @Test
    void anUnknownJournalModeStopsTheApplicationFromStartingAndNamesTheProperty() {
        Path file = TestDataDir.newDataFile(PersistenceStartupTest.class);

        Throwable failure = catchThrowable(() -> start(file, "--todo.store.journal-mode=MEMORY").close());

        assertThat(failure).isNotNull();
        assertThat(messagesOf(failure)).contains("todo.store.journal-mode");
        assertThat(file).doesNotExist();
    }

    @Test
    void theJournalModePropertyIsAppliedToTheRealStore() {
        Path file = TestDataDir.newDataFile(PersistenceStartupTest.class);

        try (ConfigurableApplicationContext context = start(file, "--todo.store.journal-mode=TRUNCATE")) {
            String mode = context.getBean(org.springframework.jdbc.core.simple.JdbcClient.class)
                    .sql("PRAGMA journal_mode").query(String.class).single();

            assertThat(mode).isEqualToIgnoringCase("truncate");
        }
    }
}
