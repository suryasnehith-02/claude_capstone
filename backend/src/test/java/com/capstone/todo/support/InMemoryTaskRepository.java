package com.capstone.todo.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.capstone.todo.domain.Task;
import com.capstone.todo.domain.TaskRepository;

/**
 * A hand-written, thread-safe {@link TaskRepository} that behaves like the SQLite adapter on the points tests
 * rely on: it assigns {@code seq} on insert and never reuses it, orders by {@code createdAt} then {@code seq}
 * (newest first), rejects a duplicate id, and keeps {@code seq} and {@code createdAt} on update.
 *
 * <p>It counts every call and can report each call to an observer (for example to record lock state), and it can
 * take and restore a {@link Snapshot} so a transaction manager fake can model a rollback.
 */
public final class InMemoryTaskRepository implements TaskRepository {

    /** The complete state of the store at one moment. */
    public record Snapshot(List<Task> tasks, long nextSeq) {
    }

    private final Map<UUID, Task> tasks = new LinkedHashMap<>();
    private long nextSeq = 1;
    private int calls;
    private final List<String> callLog = new ArrayList<>();
    private Consumer<String> observer = name -> { };

    @Override
    public synchronized List<Task> findAllOrdered() {
        record("findAllOrdered");
        List<Task> ordered = new ArrayList<>(tasks.values());
        ordered.sort(Comparator.comparing(Task::createdAt).thenComparingLong(Task::seq).reversed());
        return List.copyOf(ordered);
    }

    @Override
    public synchronized Optional<Task> findById(UUID id) {
        record("findById");
        return Optional.ofNullable(tasks.get(id));
    }

    @Override
    public synchronized void insert(Task task) {
        record("insert");
        if (tasks.containsKey(task.id())) {
            throw new IllegalStateException("duplicate id");
        }
        tasks.put(task.id(), task.withSeq(nextSeq++));
    }

    @Override
    public synchronized boolean update(Task task) {
        record("update");
        Task stored = tasks.get(task.id());
        if (stored == null) {
            return false;
        }
        tasks.put(task.id(), new Task(stored.seq(), stored.id(), task.title(), task.description(), task.status(),
                stored.createdAt(), task.updatedAt()));
        return true;
    }

    @Override
    public synchronized boolean delete(UUID id) {
        record("delete");
        return tasks.remove(id) != null;
    }

    /** Number of repository calls so far (reads and writes). */
    public synchronized int calls() {
        return calls;
    }

    /** Names of the calls so far, in order ({@code findAllOrdered}, {@code findById}, {@code insert}, ...). */
    public synchronized List<String> callLog() {
        return List.copyOf(callLog);
    }

    /** Reports the name of every future call to {@code observer}, before the call does its work. */
    public synchronized void observeCalls(Consumer<String> observer) {
        this.observer = observer;
    }

    /** The stored tasks in insertion order, without counting as a call. */
    public synchronized List<Task> contents() {
        return List.copyOf(tasks.values());
    }

    /** Captures the state, without counting as a call. */
    public synchronized Snapshot snapshot() {
        return new Snapshot(List.copyOf(tasks.values()), nextSeq);
    }

    /** Puts a captured state back (a rolled-back transaction), without counting as a call. */
    public synchronized void restore(Snapshot snapshot) {
        tasks.clear();
        for (Task task : snapshot.tasks()) {
            tasks.put(task.id(), task);
        }
        nextSeq = snapshot.nextSeq();
    }

    private void record(String name) {
        observer.accept(name);
        calls++;
        callLog.add(name);
    }
}
