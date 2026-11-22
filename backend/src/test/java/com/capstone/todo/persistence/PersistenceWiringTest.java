package com.capstone.todo.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.sqlite.SQLiteDataSource;

import com.capstone.todo.config.PersistenceConfig;
import com.capstone.todo.domain.CreateTask;
import com.capstone.todo.domain.ReplaceTask;
import com.capstone.todo.domain.SetStatus;
import com.capstone.todo.domain.Task;
import com.capstone.todo.domain.TaskNotFoundException;
import com.capstone.todo.domain.TaskRepository;
import com.capstone.todo.domain.TaskService;
import com.capstone.todo.domain.TaskStatus;
import com.capstone.todo.support.TestDataDir;
import com.zaxxer.hikari.HikariDataSource;

/**
 * T-11 / V-20: the real application context (no web server) on a real SQLite file. Boot's own DataSource, transaction
 * manager, template and JDBC client back off in favour of the ones in {@code PersistenceConfig} (D-22), the schema is
 * created before anything else uses the store, and the real {@link TaskService} works end to end on it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext
class PersistenceWiringTest {

    private static final Path DATA_FILE = TestDataDir.newDataFile(PersistenceWiringTest.class);

    @DynamicPropertySource
    static void dataFile(DynamicPropertyRegistry registry) {
        registry.add("todo.data-file", DATA_FILE::toString);
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Autowired
    private TaskService service;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void exactlyOneDataSourceExistsAndItIsTheOnePoolOfOneConnectionBuiltInCode() {
        assertThat(context.getBeansOfType(DataSource.class)).hasSize(1);
        HikariDataSource pool = context.getBean(HikariDataSource.class);

        assertThat(pool.getMaximumPoolSize()).isEqualTo(1);
        assertThat(pool.getMinimumIdle()).isEqualTo(1);
        assertThat(pool.getConnectionTimeout()).isEqualTo(5_000);
        assertThat(pool.getPoolName()).isEqualTo("todo-sqlite");
        assertThat(pool.getDataSource()).isInstanceOf(SQLiteDataSource.class);
        assertThat(((SQLiteDataSource) pool.getDataSource()).getUrl()).isEqualTo("jdbc:sqlite:" + DATA_FILE);
    }

    @Test
    void exactlyOneTransactionManagerTemplateAndJdbcClientExistAndShareThatDataSource() {
        assertThat(context.getBeansOfType(PlatformTransactionManager.class)).hasSize(1);
        assertThat(context.getBeansOfType(TransactionTemplate.class)).hasSize(1);
        assertThat(context.getBeansOfType(JdbcClient.class)).hasSize(1);
        DataSourceTransactionManager manager = context.getBean(DataSourceTransactionManager.class);

        assertThat(manager.getDataSource()).isSameAs(context.getBean(DataSource.class));
        assertThat(context.getBean(TransactionTemplate.class).getTransactionManager()).isSameAs(manager);
    }

    @Test
    void noSpringDatasourcePropertyIsInUse() {
        assertThat(environment.getProperty("spring.datasource.url")).isNull();
        assertThat(environment.getProperty("spring.datasource.hikari.maximum-pool-size")).isNull();
    }

    @Test
    void theSchemaWasCreatedAtStartupAndTheConfigurationBeansComeFromPersistenceConfig() {
        assertThat(jdbc.sql("PRAGMA user_version").query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("PRAGMA journal_mode").query(String.class).single()).isEqualToIgnoringCase("delete");
        assertThat(jdbc.sql("PRAGMA synchronous").query(Integer.class).single()).isEqualTo(2);
        assertThat(context.getBean(TaskRepository.class)).isInstanceOf(SqliteTaskRepository.class);
        assertThat(context.getBeanNamesForType(PersistenceConfig.class)).hasSize(1);
    }

    @Test
    void theRealServiceRunsTheSixOperationsOnTheRealStore() {
        Task created = service.create(new CreateTask("end to end", "text"), task -> task);
        Task ignoredSeq = service.get(created.id());
        assertThat(ignoredSeq.title()).isEqualTo("end to end");
        assertThat(ignoredSeq.status()).isEqualTo(TaskStatus.PENDING);

        Instant previous = created.updatedAt();
        for (int i = 0; i < 10; i++) {
            Task replaced = service.replace(new ReplaceTask(created.id(), "end to end", "text"), task -> task);
            assertThat(replaced.updatedAt()).isAfter(previous);
            assertThat(replaced.createdAt()).isEqualTo(created.createdAt());
            previous = replaced.updatedAt();
        }
        Task done = service.setStatus(new SetStatus(created.id(), TaskStatus.COMPLETED), task -> task);
        assertThat(done.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(done.updatedAt()).isAfter(previous);
        assertThat(service.list()).extracting(Task::id).contains(created.id());

        service.delete(created.id());

        assertThatThrownBy(() -> service.get(created.id())).isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> service.delete(created.id())).isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> service.replace(new ReplaceTask(created.id(), "x", ""), task -> task))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void aRenderFailureRollsTheRealStoreBackAndTheLockIsFreeAfterwards() {
        List<Task> before = service.list();
        UUID id = service.create(new CreateTask("kept", ""), task -> task).id();
        List<Task> withKept = service.list();
        RuntimeException failure = new IllegalStateException("render failed");

        assertThatThrownBy(() -> service.create(new CreateTask("lost", ""), task -> {
            throw failure;
        })).isSameAs(failure);
        assertThatThrownBy(() -> service.replace(new ReplaceTask(id, "changed", "changed"), task -> {
            throw failure;
        })).isSameAs(failure);
        assertThatThrownBy(() -> service.setStatus(new SetStatus(id, TaskStatus.COMPLETED), task -> {
            throw failure;
        })).isSameAs(failure);

        assertThat(service.list()).isEqualTo(withKept);
        assertThat(service.list()).hasSize(before.size() + 1);
        assertThat(service.isWriteLockHeldByCurrentThread()).isFalse();
        service.delete(id);
    }
}
