package com.capstone.todo.persistence;

import java.nio.file.Path;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.capstone.todo.config.PersistenceConfig;
import com.capstone.todo.config.TodoProperties;
import com.capstone.todo.config.TodoProperties.JournalMode;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Test helper: the real persistence beans of one SQLite file, built by hand with the production factory methods.
 * {@link #close()} closes the pool (Windows keeps the file locked until then).
 */
final class SqliteStore implements AutoCloseable {

    final Path file;
    final TodoProperties properties;
    final HikariDataSource dataSource;
    final JdbcClient jdbc;
    final TransactionTemplate transaction;
    final SchemaInitializer initializer;
    final SqliteTaskRepository repository;

    private SqliteStore(Path file, JournalMode journalMode) {
        this.file = file;
        this.properties = new TodoProperties(file.toString(), new TodoProperties.Store(journalMode));
        this.dataSource = PersistenceConfig.newDataSource(file, journalMode);
        PersistenceConfig config = new PersistenceConfig();
        this.jdbc = config.jdbcClient(dataSource);
        this.transaction = config.transactionTemplate(config.transactionManager(dataSource));
        this.initializer = new SchemaInitializer(jdbc, transaction, properties);
        this.repository = new SqliteTaskRepository(jdbc);
    }

    /** Opens the pool (and with it the file) without creating the schema. */
    static SqliteStore open(Path file, JournalMode journalMode) {
        return new SqliteStore(file, journalMode);
    }

    /** Opens the pool and runs the {@link SchemaInitializer}. */
    static SqliteStore openInitialized(Path file, JournalMode journalMode) {
        SqliteStore store = new SqliteStore(file, journalMode);
        try {
            store.initializer.initialize();
        } catch (RuntimeException | Error e) {
            store.close();
            throw e;
        }
        return store;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
