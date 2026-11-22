package com.capstone.todo.support;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessResourceFailureException;

import com.capstone.todo.domain.Task;
import com.capstone.todo.domain.TaskRepository;

/**
 * A {@link TaskRepository} whose every operation fails, for forced-500 tests (AC-13, AC-36). The default failure
 * is a {@code DataAccessException}, like the SQLite adapter's. The message is distinctive but contains no task data.
 */
public final class FailingTaskRepository implements TaskRepository {

    public static final String FAILURE_MESSAGE = "simulated store failure";

    private final Supplier<? extends RuntimeException> failure;
    private final AtomicInteger attempts = new AtomicInteger();

    public FailingTaskRepository() {
        this(() -> new DataAccessResourceFailureException(FAILURE_MESSAGE));
    }

    public FailingTaskRepository(Supplier<? extends RuntimeException> failure) {
        this.failure = failure;
    }

    @Override
    public List<Task> findAllOrdered() {
        throw fail();
    }

    @Override
    public Optional<Task> findById(UUID id) {
        throw fail();
    }

    @Override
    public void insert(Task task) {
        throw fail();
    }

    @Override
    public boolean update(Task task) {
        throw fail();
    }

    @Override
    public boolean delete(UUID id) {
        throw fail();
    }

    /** Number of calls made so far (each one failed). */
    public int attempts() {
        return attempts.get();
    }

    private RuntimeException fail() {
        attempts.incrementAndGet();
        return failure.get();
    }
}
