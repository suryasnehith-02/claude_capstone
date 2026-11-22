package com.capstone.todo.persistence;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.capstone.todo.config.TodoProperties;

/**
 * Prepares the SQLite file before the web server accepts a request (architecture C10, D-6). Boot's
 * {@code spring.sql.init} is not used.
 *
 * <ul>
 * <li>{@code PRAGMA user_version}: 0 means a new file, so the schema is created and the version set to
 * {@value #SCHEMA_VERSION} in one transaction; {@value #SCHEMA_VERSION} is accepted as it is; any other value fails
 * startup, because this code does not know that layout.</li>
 * <li>{@code journal_mode} and {@code synchronous} are read back from the live connection and must equal the
 * configured values ({@code synchronous} is always FULL); a difference fails startup, so a response is never sent
 * for a write that was not made with the promised durability.</li>
 * <li>One INFO line gives the absolute data-file path and the journal mode (D-20); nothing else is logged.</li>
 * </ul>
 *
 * Any failure is thrown, so the application does not start.
 */
@Component
public class SchemaInitializer implements InitializingBean {

    /** The schema version this code creates and understands. */
    public static final int SCHEMA_VERSION = 1;

    /** The value {@code PRAGMA synchronous} reports for FULL. */
    private static final int SYNCHRONOUS_FULL = 2;

    static final String CREATE_TABLE = """
            CREATE TABLE tasks (
                seq         INTEGER PRIMARY KEY AUTOINCREMENT,
                id          TEXT    NOT NULL UNIQUE,
                title       TEXT    NOT NULL,
                description TEXT    NOT NULL,
                status      TEXT    NOT NULL CHECK (status IN ('Pending','Completed')),
                created_at  INTEGER NOT NULL,
                updated_at  INTEGER NOT NULL
            )""";

    static final String CREATE_INDEX =
            "CREATE INDEX idx_tasks_created_at_seq ON tasks (created_at DESC, seq DESC)";

    private static final Logger LOG = LoggerFactory.getLogger(SchemaInitializer.class);

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final TodoProperties properties;

    public SchemaInitializer(JdbcClient jdbc, TransactionTemplate transaction, TodoProperties properties) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        initialize();
    }

    /**
     * Checks the pragmas, creates or accepts the schema, and logs the startup line.
     *
     * @throws IllegalStateException when a pragma differs from the configured value or the schema version is unknown
     * @throws org.springframework.dao.DataAccessException when the store cannot be read or written
     */
    public void initialize() {
        verifyPragmas();
        int version = jdbc.sql("PRAGMA user_version").query(Integer.class).single();
        if (version == 0) {
            createSchema();
        } else if (version != SCHEMA_VERSION) {
            throw new IllegalStateException("The data file has schema version " + version
                    + " but this application understands only version " + SCHEMA_VERSION);
        }
        Path file = properties.dataFilePath();
        LOG.info("Task store ready: data file {}, journal mode {}", file, properties.store().journalMode());
    }

    private void createSchema() {
        transaction.executeWithoutResult(status -> {
            jdbc.sql(CREATE_TABLE).update();
            jdbc.sql(CREATE_INDEX).update();
            jdbc.sql("PRAGMA user_version = " + SCHEMA_VERSION).update();
        });
    }

    private void verifyPragmas() {
        String expectedMode = properties.store().journalMode().name();
        String journalMode = jdbc.sql("PRAGMA journal_mode").query(String.class).single();
        if (!expectedMode.equalsIgnoreCase(journalMode)) {
            throw new IllegalStateException("SQLite reports journal_mode " + journalMode + " but "
                    + "todo.store.journal-mode is " + expectedMode);
        }
        int synchronous = jdbc.sql("PRAGMA synchronous").query(Integer.class).single();
        if (synchronous != SYNCHRONOUS_FULL) {
            throw new IllegalStateException("SQLite reports synchronous " + synchronous + " but "
                    + TodoProperties.SYNCHRONOUS + " (" + SYNCHRONOUS_FULL + ") is required");
        }
    }
}
