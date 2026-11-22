package com.capstone.todo.config;

import java.nio.file.Path;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Persistence wiring (architecture D-22): exactly one {@link DataSource}, one transaction manager on it, and the
 * {@link TransactionTemplate} and {@link JdbcClient} built from those two, all in code. No {@code spring.datasource.*}
 * property is used, so Boot's own DataSource and transaction-manager auto-configuration back off.
 *
 * <p>The pool has one connection and a connection timeout of {@value #CONNECTION_TIMEOUT_MS} ms: the write lock in
 * the service is always taken before a connection is requested, and a misconfiguration would show as a 500 after 5
 * seconds, not as a hang. Every connection gets the journal mode, {@code synchronous=FULL} and the busy timeout from
 * the {@link SQLiteConfig} (D-6).
 */
@Configuration(proxyBeanMethods = false)
public class PersistenceConfig {

    /** How long a request waits for the pool's only connection before the store call fails (D-22). */
    public static final long CONNECTION_TIMEOUT_MS = 5_000;

    /** How long SQLite waits for a lock held by someone else (D-6). */
    public static final int BUSY_TIMEOUT_MS = 5_000;

    /** The pool is deliberately this small: SQLite has one writer and the service serialises writes anyway. */
    public static final int POOL_SIZE = 1;

    private static final String POOL_NAME = "todo-sqlite";

    @Bean(destroyMethod = "close")
    public HikariDataSource dataSource(TodoProperties properties) {
        return newDataSource(properties.prepareDataFile(), properties.store().journalMode());
    }

    @Bean
    public DataSourceTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public TransactionTemplate transactionTemplate(DataSourceTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    public JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }

    /**
     * Builds the pool over a SQLite file. The file's parent directory must exist; the connection is opened (and the
     * pragmas applied) at once, so an unusable file fails here.
     */
    public static HikariDataSource newDataSource(Path file, TodoProperties.JournalMode journalMode) {
        SQLiteConfig sqlite = new SQLiteConfig();
        sqlite.setJournalMode(SQLiteConfig.JournalMode.valueOf(journalMode.name()));
        sqlite.setSynchronous(SQLiteConfig.SynchronousMode.valueOf(TodoProperties.SYNCHRONOUS));
        sqlite.setBusyTimeout(BUSY_TIMEOUT_MS);
        SQLiteDataSource source = new SQLiteDataSource(sqlite);
        source.setUrl("jdbc:sqlite:" + file);

        HikariConfig pool = new HikariConfig();
        pool.setPoolName(POOL_NAME);
        pool.setDataSource(source);
        pool.setMaximumPoolSize(POOL_SIZE);
        pool.setMinimumIdle(POOL_SIZE);
        pool.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        return new HikariDataSource(pool);
    }
}
