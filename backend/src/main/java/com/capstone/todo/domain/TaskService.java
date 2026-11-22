package com.capstone.todo.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.capstone.todo.domain.support.IdGenerator;

/**
 * The six use cases (architecture C8). Stage P7 of the precedence chain: a missing task is a
 * {@link TaskNotFoundException}.
 *
 * <p><b>Writes (D-7).</b> Every mutation takes one fair write lock first and only then opens a transaction through a
 * {@link TransactionTemplate} (never a declarative annotation), so the lock is always held before the pool's single
 * connection is requested and the two can never deadlock. Inside the transaction the service loads, computes, writes
 * and renders; the commit comes last, and the lock is released after it. Reads take no lock.
 *
 * <p><b>Render before commit (D-23).</b> The mutating methods take a function that turns the stored {@link Task} into
 * the caller's response. It runs inside the transaction, after the write and before the commit, so a rendering
 * failure rolls the write back. The domain stays free of web types because the function is a plain JDK type.
 *
 * <p><b>Time (D-9).</b> The clock is truncated to milliseconds. A new task has {@code createdAt == updatedAt == now}.
 * A change (also one that changes nothing) sets {@code updatedAt = max(now, previous updatedAt + 1 ms)}, so it is
 * strictly increasing even if the clock stands still or steps back. {@code createdAt} and the id never change.
 *
 * <p>Texts arrive validated and trimmed, with "" for a missing description (the commands reject null), and are
 * stored as given. Failures of the store or of the render function propagate unchanged.
 */
@Service
public class TaskService {

    private final TaskRepository repository;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final IdGenerator ids;
    private final ReentrantLock writeLock = new ReentrantLock(true);

    public TaskService(TaskRepository repository, TransactionTemplate transaction, Clock clock, IdGenerator ids) {
        this.repository = repository;
        this.transaction = transaction;
        this.clock = clock;
        this.ids = ids;
    }

    /** Creates a Pending task and returns what {@code render} makes of it. */
    public <R> R create(CreateTask command, Function<Task, R> render) {
        return write(() -> {
            Instant now = now();
            Task task = new Task(Task.UNASSIGNED_SEQ, ids.next(), command.title(), command.description(),
                    TaskStatus.PENDING, now, now);
            repository.insert(task);
            return render.apply(task);
        });
    }

    /** All tasks, newest first; no lock. */
    public List<Task> list() {
        return repository.findAllOrdered();
    }

    /** @throws TaskNotFoundException when no task has the id; no lock */
    public Task get(UUID id) {
        return repository.findById(id).orElseThrow(TaskNotFoundException::new);
    }

    /**
     * Replaces title and description (status, id and createdAt are kept).
     *
     * @throws TaskNotFoundException when no task has the id
     */
    public <R> R replace(ReplaceTask command, Function<Task, R> render) {
        return write(() -> {
            Task stored = load(command.id());
            Task changed = new Task(stored.seq(), stored.id(), command.title(), command.description(),
                    stored.status(), stored.createdAt(), nextUpdatedAt(stored));
            return save(changed, render);
        });
    }

    /**
     * Sets the status; setting the status it already has still counts as a change for {@code updatedAt}.
     *
     * @throws TaskNotFoundException when no task has the id
     */
    public <R> R setStatus(SetStatus command, Function<Task, R> render) {
        return write(() -> {
            Task stored = load(command.id());
            Task changed = new Task(stored.seq(), stored.id(), stored.title(), stored.description(),
                    command.status(), stored.createdAt(), nextUpdatedAt(stored));
            return save(changed, render);
        });
    }

    /**
     * Deletes the task for good.
     *
     * @throws TaskNotFoundException when no task has the id (a repeated delete)
     */
    public void delete(UUID id) {
        write(() -> {
            if (!repository.delete(id)) {
                throw new TaskNotFoundException();
            }
            return null;
        });
    }

    /** Whether the calling thread holds the write lock (lets the transaction-wiring test prove lock-before-connection). */
    public boolean isWriteLockHeldByCurrentThread() {
        return writeLock.isHeldByCurrentThread();
    }

    ReentrantLock writeLock() {
        return writeLock;
    }

    private <R> R write(Supplier<R> work) {
        writeLock.lock();
        try {
            return transaction.execute(status -> work.get());
        } finally {
            writeLock.unlock();
        }
    }

    private Task load(UUID id) {
        return repository.findById(id).orElseThrow(TaskNotFoundException::new);
    }

    private <R> R save(Task changed, Function<Task, R> render) {
        if (!repository.update(changed)) {
            // The row vanished between the load and the update: impossible under the lock, but never acknowledge it.
            throw new TaskNotFoundException();
        }
        return render.apply(changed);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private Instant nextUpdatedAt(Task previous) {
        Instant now = now();
        Instant floor = previous.updatedAt().plusMillis(1);
        return now.isBefore(floor) ? floor : now;
    }
}
