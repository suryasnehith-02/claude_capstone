package com.capstone.todo.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage port (architecture C9). Every mutation is atomic and durable when the enclosing transaction commits.
 * Store failures surface as unchecked exceptions (a {@code DataAccessException} for the SQLite adapter).
 */
public interface TaskRepository {

    /** All tasks, newest {@code createdAt} first, ties broken by the larger {@code seq} first. */
    List<Task> findAllOrdered();

    Optional<Task> findById(UUID id);

    /** Stores a new task. The store assigns {@code seq}; the value in {@code task} is ignored. */
    void insert(Task task);

    /** Replaces title, description, status and updatedAt of the task with the same id; never changes seq or createdAt. */
    boolean update(Task task);

    /** Deletes the task (hard delete). Returns whether a task was removed. */
    boolean delete(UUID id);
}
