package com.capstone.todo.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import com.capstone.todo.config.PersistenceConfig;
import com.capstone.todo.config.TodoProperties;
import com.capstone.todo.config.TodoProperties.JournalMode;
import com.capstone.todo.domain.Task;
import com.capstone.todo.domain.TaskStatus;
import com.capstone.todo.support.TestDataDir;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * T-11: the startup checks of {@link SchemaInitializer} against real SQLite files in {@code TestDataDir} (V-29,
 * AC-14, AC-22, NFR-7). Every pool is closed in {@code @AfterEach}; no file is ever deleted.
 */
@ExtendWith(OutputCaptureExtension.class)
class SchemaInitializerTest {

    private static final List<String> EVIDENCE = new ArrayList<>();

    private final List<SqliteStore> opened = new ArrayList<>();
    private final List<HikariDataSource> pools = new ArrayList<>();

    @AfterEach
    void closeEveryPool() {
        opened.forEach(SqliteStore::close);
        pools.forEach(HikariDataSource::close);
    }

    @AfterAll
    static void writeEvidence() throws IOException {
        Path out = TestDataDir.baseDirectory().getParent().resolve("t11-schema-evidence.txt");
        Files.write(out, EVIDENCE, StandardCharsets.UTF_8);
    }

    private SqliteStore open(Path file, JournalMode mode) {
        SqliteStore store = SqliteStore.open(file, mode);
        opened.add(store);
        return store;
    }

    private SqliteStore openInitialized(Path file, JournalMode mode) {
        SqliteStore store = SqliteStore.openInitialized(file, mode);
        opened.add(store);
        return store;
    }

    private static Path newFile() {
        return TestDataDir.newDataFile(SchemaInitializerTest.class);
    }

    private static String text(JdbcClient jdbc, String sql) {
        return jdbc.sql(sql).query(String.class).single();
    }

    private static int number(JdbcClient jdbc, String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }

    private static Task task(String title) {
        Instant now = Instant.parse("2026-03-01T10:00:00.123Z");
        return new Task(Task.UNASSIGNED_SEQ, UUID.randomUUID(), title, "", TaskStatus.PENDING, now, now);
    }

    // ---- a new file ----

    @Test
    void aNewFileGetsTheSchemaTheIndexAndUserVersionOne() {
        SqliteStore store = openInitialized(newFile(), JournalMode.DELETE);

        assertThat(number(store.jdbc, "PRAGMA user_version")).isEqualTo(1);
        assertThat(store.jdbc.sql("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")
                .query(String.class).list()).containsExactly("tasks");
        assertThat(store.jdbc.sql("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'tasks'"
                + " AND name NOT LIKE 'sqlite_%'").query(String.class).list())
                .containsExactly("idx_tasks_created_at_seq");
        assertThat(store.jdbc.sql("SELECT sql FROM sqlite_master WHERE name = 'tasks'").query(String.class).single())
                .contains("AUTOINCREMENT", "CHECK (status IN ('Pending','Completed'))");
    }

    @Test
    void anExistingEmptyFileOfZeroBytesIsAcceptedAsNew() throws IOException {
        Path file = newFile();
        Files.write(file, new byte[0]);

        SqliteStore store = openInitialized(file, JournalMode.DELETE);

        assertThat(number(store.jdbc, "PRAGMA user_version")).isEqualTo(1);
    }

    // ---- an existing file ----

    @Test
    void versionOneIsAcceptedAgainAndTheDataSurvivesAReopen() {
        Path file = newFile();
        SqliteStore first = openInitialized(file, JournalMode.DELETE);
        first.repository.insert(task("kept across restart"));
        first.close();

        SqliteStore second = openInitialized(file, JournalMode.DELETE);
        second.initializer.initialize();

        assertThat(second.repository.findAllOrdered()).extracting(Task::title).containsExactly("kept across restart");
        assertThat(number(second.jdbc, "PRAGMA user_version")).isEqualTo(1);
    }

    @ParameterizedTest(name = "user_version {0}")
    @ValueSource(ints = {2, 7, 100, -1, Integer.MAX_VALUE})
    void anyOtherUserVersionFailsStartupAndChangesNothing(int version) {
        SqliteStore store = open(newFile(), JournalMode.DELETE);
        store.jdbc.sql("PRAGMA user_version = " + version).update();

        assertThatThrownBy(store.initializer::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("schema version " + version)
                .hasMessageContaining("only version 1");

        assertThat(number(store.jdbc, "PRAGMA user_version")).isEqualTo(version);
        assertThat(store.jdbc.sql("SELECT count(*) FROM sqlite_master").query(Integer.class).single()).isZero();
    }

    // ---- pragmas (V-29) ----

    @ParameterizedTest(name = "{0}")
    @EnumSource(JournalMode.class)
    void theConfiguredJournalModeAndSynchronousFullAreReadBackFromTheConnection(JournalMode mode) throws IOException {
        Path file = newFile();
        SqliteStore store = openInitialized(file, mode);

        String journalMode = text(store.jdbc, "PRAGMA journal_mode");
        int synchronous = number(store.jdbc, "PRAGMA synchronous");
        int busyTimeout = number(store.jdbc, "PRAGMA busy_timeout");
        String foreignKeys = text(store.jdbc, "PRAGMA foreign_keys");
        store.repository.insert(task("x"));
        store.close();

        assertThat(journalMode).isEqualToIgnoringCase(mode.name());
        assertThat(synchronous).isEqualTo(2);
        assertThat(busyTimeout).isEqualTo(PersistenceConfig.BUSY_TIMEOUT_MS);
        EVIDENCE.add("V-29 " + mode + ": journal_mode=" + journalMode + ", synchronous=" + synchronous
                + " (2 = FULL), busy_timeout=" + busyTimeout + ", foreign_keys=" + foreignKeys
                + "; files beside the database after the pool was closed: " + namesIn(file.getParent()));
    }

    private static List<String> namesIn(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void aJournalModeThatDiffersFromTheConfiguredOneFailsStartup() {
        Path file = newFile();
        HikariDataSource deleteMode = PersistenceConfig.newDataSource(file, JournalMode.DELETE);
        pools.add(deleteMode);
        PersistenceConfig config = new PersistenceConfig();
        TodoProperties claimsWal = new TodoProperties(file.toString(), new TodoProperties.Store(JournalMode.WAL));
        SchemaInitializer initializer = new SchemaInitializer(config.jdbcClient(deleteMode),
                config.transactionTemplate(config.transactionManager(deleteMode)), claimsWal);

        assertThatThrownBy(initializer::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("journal_mode")
                .hasMessageContaining("delete")
                .hasMessageContaining("WAL");
    }

    @Test
    void aSynchronousSettingOtherThanFullFailsStartup() {
        Path file = newFile();
        SQLiteConfig sqlite = new SQLiteConfig();
        sqlite.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        SQLiteDataSource source = new SQLiteDataSource(sqlite);
        source.setUrl("jdbc:sqlite:" + file);
        HikariConfig pool = new HikariConfig();
        pool.setDataSource(source);
        pool.setMaximumPoolSize(1);
        HikariDataSource normal = new HikariDataSource(pool);
        pools.add(normal);
        PersistenceConfig config = new PersistenceConfig();
        TodoProperties properties = new TodoProperties(file.toString(), new TodoProperties.Store(JournalMode.DELETE));
        SchemaInitializer initializer = new SchemaInitializer(config.jdbcClient(normal),
                config.transactionTemplate(config.transactionManager(normal)), properties);

        assertThatThrownBy(initializer::initialize)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("synchronous 1")
                .hasMessageContaining("FULL");
    }

    // ---- the startup log line (NFR-7) ----

    @ParameterizedTest(name = "{0}")
    @EnumSource(JournalMode.class)
    void exactlyOneInfoLineGivesTheAbsolutePathAndTheJournalMode(JournalMode mode, CapturedOutput output) {
        Path file = newFile();
        String before = output.getAll();

        openInitialized(file, mode);

        String added = output.getAll().substring(before.length());
        // Logback's plain format prints the full logger name; after a Spring Boot context ran in the same JVM, Boot's
        // format abbreviates it (c.c.todo.persistence.SchemaInitializer). Both end with the simple class name.
        List<String> lines = added.lines().filter(l -> l.contains("SchemaInitializer")).toList();
        assertThat(lines).singleElement().satisfies(line -> {
            assertThat(line).contains("INFO");
            assertThat(line).contains(file.toAbsolutePath().normalize().toString());
            assertThat(line).contains("journal mode " + mode.name());
        });
        assertThat(Path.of(file.toString()).isAbsolute()).isTrue();
        EVIDENCE.add("log line (" + mode + "): " + lines.get(0));
    }

    @Test
    void aRelativeDataFileIsLoggedAsItsAbsolutePath(CapturedOutput output) {
        Path file = newFile();
        Path relative = Path.of("").toAbsolutePath().relativize(file);
        SqliteStore store = open(file, JournalMode.DELETE);
        TodoProperties properties = new TodoProperties(relative.toString(), new TodoProperties.Store(JournalMode.DELETE));

        new SchemaInitializer(store.jdbc, store.transaction, properties).initialize();

        assertThat(relative.isAbsolute()).isFalse();
        assertThat(output.getAll()).contains(file.toAbsolutePath().normalize().toString());
    }

    @Test
    void nothingIsLoggedWhenStartupFails(CapturedOutput output) {
        SqliteStore store = open(newFile(), JournalMode.DELETE);
        store.jdbc.sql("PRAGMA user_version = 9").update();
        String before = output.getAll();

        assertThatThrownBy(store.initializer::initialize).isInstanceOf(IllegalStateException.class);

        assertThat(output.getAll().substring(before.length())).doesNotContain("Task store ready");
    }

    // ---- files that cannot be used ----

    @Test
    void aFileThatIsNotADatabaseFailsStartup() throws IOException {
        Path file = newFile();
        Files.writeString(file, "This is plain text, not a SQLite database. ".repeat(40), StandardCharsets.UTF_8);

        Throwable failure = catchThrowable(() -> openInitialized(file, JournalMode.DELETE));

        assertThat(failure).isNotNull().isInstanceOf(RuntimeException.class);
        EVIDENCE.add("not a database: " + failure.getClass().getName() + " / cause chain "
                + causeChain(failure));
        assertThat(String.valueOf(failure.getMessage())).doesNotContain("This is plain text");
    }

    private static String causeChain(Throwable failure) {
        StringBuilder chain = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            chain.append(t.getClass().getSimpleName()).append(" -> ");
        }
        return chain.toString();
    }

    @Test
    void aMissingParentDirectoryFailsTheDirectOpenButIsCreatedFromTheProperties() {
        Path root = TestDataDir.create(SchemaInitializerTest.class);
        Path file = root.resolve("missing").resolve("deeper").resolve("todo.db");

        Throwable direct = catchThrowable(() -> openInitialized(file, JournalMode.DELETE));
        assertThat(direct).isNotNull();
        EVIDENCE.add("missing parent, direct open: " + causeChain(direct));

        TodoProperties properties = new TodoProperties(file.toString(), new TodoProperties.Store(JournalMode.DELETE));
        HikariDataSource pool = new PersistenceConfig().dataSource(properties);
        pools.add(pool);
        assertThat(file.getParent()).isDirectory();
        PersistenceConfig config = new PersistenceConfig();
        new SchemaInitializer(config.jdbcClient(pool), config.transactionTemplate(config.transactionManager(pool)),
                properties).initialize();
        assertThat(file).isRegularFile();
    }

    @Test
    void aReadOnlyFileFailsStartupInsteadOfRunningWithoutDurability() throws IOException {
        Path file = newFile();
        Files.write(file, new byte[0]);
        Assumptions.assumeTrue(file.toFile().setReadOnly(), "this file system cannot mark a file read-only");
        try {
            Throwable failure = catchThrowable(
                    () -> openInitialized(file, JournalMode.DELETE));

            assertThat(failure).isNotNull();
            EVIDENCE.add("read-only file: " + causeChain(failure));
        } finally {
            file.toFile().setWritable(true);
        }
    }

    @Test
    void aReadOnlyDirectoryFailsStartupWhereTheFileSystemEnforcesIt() throws IOException {
        Path directory = TestDataDir.create(SchemaInitializerTest.class);
        Path file = directory.resolve("todo.db");
        Assumptions.assumeTrue(directory.toFile().setReadOnly(), "this file system cannot mark a directory read-only");
        try {
            boolean enforced;
            try {
                Files.createFile(directory.resolve("probe"));
                enforced = false;
            } catch (IOException e) {
                enforced = true;
            }
            Assumptions.assumeTrue(enforced, "this file system does not enforce read-only directories (Windows)");

            Throwable failure = catchThrowable(
                    () -> openInitialized(file, JournalMode.DELETE));

            assertThat(failure).isNotNull();
            EVIDENCE.add("read-only directory: " + causeChain(failure));
        } finally {
            directory.toFile().setWritable(true);
        }
    }
}
