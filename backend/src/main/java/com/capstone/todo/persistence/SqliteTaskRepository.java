package com.capstone.todo.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.capstone.todo.domain.Task;
import com.capstone.todo.domain.TaskRepository;
import com.capstone.todo.domain.TaskStatus;

/**
 * The SQLite adapter of {@link TaskRepository} (architecture C10). Every statement is parameterised; no text from a
 * request is ever part of the SQL. Timestamps are stored as epoch milliseconds. Failures surface as Spring's
 * unchecked {@code DataAccessException}; atomicity comes from the caller's transaction, which shares the one
 * connection (D-22).
 */
@Component
public class SqliteTaskRepository implements TaskRepository {

    private static final String COLUMNS = "seq, id, title, description, status, created_at, updated_at";

    private static final RowMapper<Task> TASKS = SqliteTaskRepository::toTask;

    private final JdbcClient jdbc;

    public SqliteTaskRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Task> findAllOrdered() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM tasks ORDER BY created_at DESC, seq DESC")
                .query(TASKS)
                .list();
    }

    @Override
    public Optional<Task> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM tasks WHERE id = ?")
                .param(id.toString())
                .query(TASKS)
                .optional();
    }

    @Override
    public void insert(Task task) {
        jdbc.sql("INSERT INTO tasks (id, title, description, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")
                .params(task.id().toString(), task.title(), task.description(), task.status().label(),
                        task.createdAt().toEpochMilli(), task.updatedAt().toEpochMilli())
                .update();
    }

    @Override
    public boolean update(Task task) {
        int rows = jdbc.sql("UPDATE tasks SET title = ?, description = ?, status = ?, updated_at = ? WHERE id = ?")
                .params(task.title(), task.description(), task.status().label(), task.updatedAt().toEpochMilli(),
                        task.id().toString())
                .update();
        return rows > 0;
    }

    @Override
    public boolean delete(UUID id) {
        int rows = jdbc.sql("DELETE FROM tasks WHERE id = ?").param(id.toString()).update();
        return rows > 0;
    }

    private static Task toTask(ResultSet row, int rowNumber) throws SQLException {
        Optional<TaskStatus> status = TaskStatus.fromLabel(row.getString("status"));
        UUID id;
        try {
            id = UUID.fromString(row.getString("id"));
        } catch (IllegalArgumentException e) {
            throw new DataRetrievalFailureException("A stored task has an id that is not a UUID");
        }
        if (status.isEmpty()) {
            throw new DataRetrievalFailureException("A stored task has an unknown status");
        }
        return new Task(row.getLong("seq"), id, row.getString("title"), row.getString("description"), status.get(),
                Instant.ofEpochMilli(row.getLong("created_at")), Instant.ofEpochMilli(row.getLong("updated_at")));
    }
}
