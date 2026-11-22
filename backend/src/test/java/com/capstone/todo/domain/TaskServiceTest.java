package com.capstone.todo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.capstone.todo.support.FailingTaskRepository;
import com.capstone.todo.support.FixedClock;
import com.capstone.todo.support.InMemoryTaskRepository;
import com.capstone.todo.support.ScriptedIds;

/**
 * T-10: the service against {@link InMemoryTaskRepository} and a hand-written transaction manager that records its
 * events together with the state of the write lock, and restores the store on a rollback or a failed commit.
 */
class TaskServiceTest {

    private static final Instant START = Instant.parse("2026-03-01T10:00:00.123456789Z");
    private static final Instant START_MS = Instant.parse("2026-03-01T10:00:00.123Z");

    private final FixedClock clock = new FixedClock(START);
    private final InMemoryTaskRepository repository = new InMemoryTaskRepository();
    private final RecordingTransactionManager transactions = new RecordingTransactionManager(repository);
    private final TaskService service = new TaskService(repository, new TransactionTemplate(transactions), clock,
            ScriptedIds.sequential());
    private final Function<Task, Task> identity = task -> task;

    @BeforeEach
    void connectTheLockProbe() {
        transactions.lockProbe = service::isWriteLockHeldByCurrentThread;
    }

    // ---- create ----

    @Test
    void createStoresAPendingTaskWithServerFieldsAndReturnsTheRenderedResult() {
        String rendered = service.create(new CreateTask("Buy milk", "2 litres"),
                task -> task.id() + "|" + task.title() + "|" + task.status().label());

        assertThat(rendered).isEqualTo(ScriptedIds.idNumber(1) + "|Buy milk|Pending");
        assertThat(repository.contents()).singleElement().satisfies(task -> {
            assertThat(task.id()).isEqualTo(ScriptedIds.idNumber(1));
            assertThat(task.title()).isEqualTo("Buy milk");
            assertThat(task.description()).isEqualTo("2 litres");
            assertThat(task.status()).isEqualTo(TaskStatus.PENDING);
        });
    }

    @Test
    void createSetsCreatedAtAndUpdatedAtToTheClockTruncatedToMilliseconds() {
        Task task = service.create(new CreateTask("a", ""), identity);

        assertThat(task.createdAt()).isEqualTo(START_MS);
        assertThat(task.updatedAt()).isEqualTo(START_MS);
    }

    @Test
    void createWithAnEmptyDescriptionStoresAnEmptyDescriptionNotNull() {
        Task task = service.create(new CreateTask("a", ""), identity);

        assertThat(task.description()).isEmpty();
        assertThat(repository.contents().get(0).description()).isEmpty();
    }

    @Test
    void twoTasksWithTheSameTitleAndTheSameMillisecondAreBothStored() {
        Task first = service.create(new CreateTask("same", ""), identity);
        Task second = service.create(new CreateTask("same", ""), identity);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.createdAt()).isEqualTo(first.createdAt());
        assertThat(repository.contents()).hasSize(2);
    }

    // ---- list and get ----

    @Test
    void listIsEmptyWhenNothingIsStored() {
        assertThat(service.list()).isEmpty();
    }

    @Test
    void listOrdersByCreatedAtNewestFirstAndBreaksTiesWithTheLargerSeq() {
        Task first = service.create(new CreateTask("first", ""), identity);
        clock.advance(Duration.ofMillis(5));
        Task second = service.create(new CreateTask("second", ""), identity);
        Task third = service.create(new CreateTask("third", ""), identity);
        clock.set(START.minusSeconds(60));
        Task fourth = service.create(new CreateTask("fourth, created by a clock that stepped back", ""), identity);

        assertThat(service.list()).extracting(Task::id)
                .containsExactly(third.id(), second.id(), first.id(), fourth.id());
    }

    @Test
    void getReturnsTheStoredTask() {
        Task created = service.create(new CreateTask("a", "b"), identity);

        Task found = service.get(created.id());

        assertThat(found.title()).isEqualTo("a");
        assertThat(found.description()).isEqualTo("b");
    }

    @Test
    void getOfAnUnknownIdIsTaskNotFound() {
        assertThatThrownBy(() -> service.get(ScriptedIds.idNumber(99))).isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void readsTakeNeitherTheLockNorATransaction() {
        Task created = service.create(new CreateTask("a", ""), identity);
        transactions.events.clear();
        List<String> lockStateAtEachCall = new ArrayList<>();
        repository.observeCalls(name -> lockStateAtEachCall.add(name + " lock=" + service.isWriteLockHeldByCurrentThread()));

        service.list();
        service.get(created.id());

        assertThat(lockStateAtEachCall).containsExactly("findAllOrdered lock=false", "findById lock=false");
        assertThat(transactions.events).isEmpty();
    }

    // ---- replace ----

    @Test
    void replaceChangesTitleAndDescriptionAndKeepsStatusIdSeqAndCreatedAt() {
        Task created = service.create(new CreateTask("old", "old text"), identity);
        service.setStatus(new SetStatus(created.id(), TaskStatus.COMPLETED), identity);
        clock.advance(Duration.ofSeconds(10));

        Task replaced = service.replace(new ReplaceTask(created.id(), "new", "new text"), identity);

        assertThat(replaced.title()).isEqualTo("new");
        assertThat(replaced.description()).isEqualTo("new text");
        assertThat(replaced.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(replaced.id()).isEqualTo(created.id());
        assertThat(replaced.createdAt()).isEqualTo(created.createdAt());
        assertThat(replaced.updatedAt()).isEqualTo(START_MS.plusSeconds(10));
        assertThat(repository.contents().get(0).seq()).isEqualTo(1);
        assertThat(repository.contents().get(0)).isEqualTo(replaced.withSeq(1));
    }

    @Test
    void aPutWithAnEmptyDescriptionClearsTheDescription() {
        Task created = service.create(new CreateTask("t", "something"), identity);

        Task replaced = service.replace(new ReplaceTask(created.id(), "t", ""), identity);

        assertThat(replaced.description()).isEmpty();
        assertThat(service.get(created.id()).description()).isEmpty();
    }

    @Test
    void tenNoOpPutsWithAFrozenClockEachRaiseUpdatedAtByExactlyOneMillisecond() {
        Task created = service.create(new CreateTask("t", "d"), identity);
        Instant previous = created.updatedAt();

        for (int i = 0; i < 10; i++) {
            Task replaced = service.replace(new ReplaceTask(created.id(), "t", "d"), identity);

            assertThat(replaced.updatedAt()).isEqualTo(previous.plusMillis(1));
            assertThat(replaced.createdAt()).isEqualTo(created.createdAt());
            previous = replaced.updatedAt();
        }
        assertThat(service.get(created.id()).updatedAt()).isEqualTo(START_MS.plusMillis(10));
    }

    @Test
    void aClockThatStepsBackNeverMakesUpdatedAtGoBackwards() {
        Task created = service.create(new CreateTask("t", ""), identity);
        clock.set(START.minusSeconds(3600));

        Task replaced = service.replace(new ReplaceTask(created.id(), "t2", ""), identity);

        assertThat(replaced.updatedAt()).isEqualTo(created.updatedAt().plusMillis(1));
    }

    @Test
    void aClockThatMovedMoreThanOneMillisecondAheadWins() {
        Task created = service.create(new CreateTask("t", ""), identity);
        clock.advance(Duration.ofMillis(2));

        Task replaced = service.replace(new ReplaceTask(created.id(), "t", ""), identity);

        assertThat(replaced.updatedAt()).isEqualTo(created.updatedAt().plusMillis(2));
    }

    @Test
    void aClockExactlyOneMillisecondAheadGivesTheSameResultAsTheFloor() {
        Task created = service.create(new CreateTask("t", ""), identity);
        clock.advance(Duration.ofMillis(1));

        Task replaced = service.replace(new ReplaceTask(created.id(), "t", ""), identity);

        assertThat(replaced.updatedAt()).isEqualTo(created.updatedAt().plusMillis(1));
    }

    @Test
    void twoUpdatesInSequenceLeaveTheSecondOne() {
        Task created = service.create(new CreateTask("t", ""), identity);

        service.replace(new ReplaceTask(created.id(), "first", "1"), identity);
        Task second = service.replace(new ReplaceTask(created.id(), "second", "2"), identity);

        assertThat(service.get(created.id()).title()).isEqualTo("second");
        assertThat(service.get(created.id()).description()).isEqualTo("2");
        assertThat(second.updatedAt()).isEqualTo(created.updatedAt().plusMillis(2));
    }

    // ---- set status ----

    @Test
    void setStatusMovesInBothDirectionsAndChangesNothingElse() {
        Task created = service.create(new CreateTask("t", "d"), identity);
        clock.advance(Duration.ofSeconds(1));

        Task done = service.setStatus(new SetStatus(created.id(), TaskStatus.COMPLETED), identity);
        clock.advance(Duration.ofSeconds(1));
        Task again = service.setStatus(new SetStatus(created.id(), TaskStatus.PENDING), identity);

        assertThat(done.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(done.title()).isEqualTo("t");
        assertThat(done.description()).isEqualTo("d");
        assertThat(done.createdAt()).isEqualTo(created.createdAt());
        assertThat(done.updatedAt()).isEqualTo(START_MS.plusSeconds(1));
        assertThat(again.status()).isEqualTo(TaskStatus.PENDING);
        assertThat(again.updatedAt()).isEqualTo(START_MS.plusSeconds(2));
    }

    @Test
    void settingTheStatusItAlreadyHasStillRaisesUpdatedAt() {
        Task created = service.create(new CreateTask("t", ""), identity);
        Instant previous = created.updatedAt();

        for (int i = 0; i < 10; i++) {
            Task patched = service.setStatus(new SetStatus(created.id(), TaskStatus.PENDING), identity);

            assertThat(patched.status()).isEqualTo(TaskStatus.PENDING);
            assertThat(patched.updatedAt()).isEqualTo(previous.plusMillis(1));
            previous = patched.updatedAt();
        }
    }

    // ---- not found (P7) and delete ----

    @Test
    void replaceAndSetStatusOfAnUnknownIdAreTaskNotFoundAndWriteNothing() {
        service.create(new CreateTask("kept", ""), identity);
        List<Task> before = repository.contents();
        int callsBefore = repository.calls();
        UUID missing = ScriptedIds.idNumber(99);

        assertThatThrownBy(() -> service.replace(new ReplaceTask(missing, "x", ""), identity))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> service.setStatus(new SetStatus(missing, TaskStatus.COMPLETED), identity))
                .isInstanceOf(TaskNotFoundException.class);

        assertThat(repository.contents()).isEqualTo(before);
        assertThat(repository.callLog().subList(callsBefore, repository.calls())).containsExactly("findById", "findById");
        assertThat(service.isWriteLockHeldByCurrentThread()).isFalse();
        assertThat(transactions.events.get(transactions.events.size() - 1)).isEqualTo("rollback");
    }

    @Test
    void deleteRemovesTheTaskForGoodAndARepeatedDeleteIsTaskNotFound() {
        Task keep = service.create(new CreateTask("keep", ""), identity);
        Task gone = service.create(new CreateTask("gone", ""), identity);

        service.delete(gone.id());

        assertThat(service.list()).extracting(Task::id).containsExactly(keep.id());
        assertThatThrownBy(() -> service.get(gone.id())).isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> service.delete(gone.id())).isInstanceOf(TaskNotFoundException.class);
        assertThat(service.list()).extracting(Task::id).containsExactly(keep.id());
        assertThat(service.isWriteLockHeldByCurrentThread()).isFalse();
    }

    @Test
    void deleteOfAnUnknownIdIsTaskNotFound() {
        assertThatThrownBy(() -> service.delete(ScriptedIds.idNumber(5))).isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void aTaskCreatedAfterADeleteGetsANewSeqAndNeverTheOldOne() {
        Task first = service.create(new CreateTask("a", ""), identity);
        service.delete(first.id());

        service.create(new CreateTask("b", ""), identity);

        assertThat(repository.contents().get(0).seq()).isEqualTo(2);
    }

    // ---- render before commit, rollback (D-23) ----

    @Test
    void theRenderFunctionRunsAfterTheWriteAndBeforeTheCommit() {
        Task created = service.create(new CreateTask("t", ""), identity);
        transactions.events.clear();
        repository.observeCalls(name -> transactions.events.add("repository " + name));

        service.replace(new ReplaceTask(created.id(), "t2", ""), task -> {
            transactions.events.add("render");
            return task;
        });

        assertThat(transactions.events).containsExactly("begin lock=true", "repository findById",
                "repository update", "render", "commit lock=true");
    }

    @Test
    void aRenderFailureOnCreateRollsBackAndTheStoreIsUnchanged() {
        service.create(new CreateTask("kept", ""), identity);
        List<Task> before = repository.contents();
        RuntimeException failure = new IllegalStateException("render failed");

        assertThatThrownBy(() -> service.create(new CreateTask("lost", ""), failing(failure))).isSameAs(failure);

        assertThat(repository.contents()).isEqualTo(before);
        assertThat(service.list()).extracting(Task::title).containsExactly("kept");
        assertThat(transactions.events.get(transactions.events.size() - 1)).isEqualTo("rollback");
        assertThat(service.isWriteLockHeldByCurrentThread()).isFalse();
    }

    @Test
    void aRenderFailureOnReplaceRollsBackAndTheOldRowIsUnchanged() {
        Task created = service.create(new CreateTask("old", "old"), identity);
        clock.advance(Duration.ofSeconds(5));
        List<Task> before = repository.contents();
        RuntimeException failure = new IllegalStateException("render failed");

        assertThatThrownBy(() -> service.replace(new ReplaceTask(created.id(), "new", "new"), failing(failure)))
                .isSameAs(failure);

        assertThat(repository.contents()).isEqualTo(before);
        assertThat(service.get(created.id()).updatedAt()).isEqualTo(created.updatedAt());
        assertThat(service.isWriteLockHeldByCurrentThread()).isFalse();
    }

    @Test
    void aRenderFailureOnSetStatusRollsBackAndTheOldRowIsUnchanged() {
        Task created = service.create(new CreateTask("t", ""), identity);
        List<Task> before = repository.contents();
        RuntimeException failure = new IllegalStateException("render failed");

        assertThatThrownBy(() -> service.setStatus(new SetStatus(created.id(), TaskStatus.COMPLETED), failing(failure)))
                .isSameAs(failure);

        assertThat(repository.contents()).isEqualTo(before);
        assertThat(service.get(created.id()).status()).isEqualTo(TaskStatus.PENDING);
    }

    @Test
    void aFailedCommitLeavesTheStoreUnchangedAndPropagates() {
        Task created = service.create(new CreateTask("t", ""), identity);
        List<Task> before = repository.contents();
        transactions.failNextCommit = true;

        assertThatThrownBy(() -> service.replace(new ReplaceTask(created.id(), "u", ""), identity))
                .isInstanceOf(DataAccessException.class);

        assertThat(repository.contents()).isEqualTo(before);
        assertThat(service.isWriteLockHeldByCurrentThread()).isFalse();
        // and the service still works afterwards
        assertThat(service.replace(new ReplaceTask(created.id(), "v", ""), identity).title()).isEqualTo("v");
    }

    @Test
    void aStoreFailurePropagatesRollsBackAndReleasesTheLock() {
        FailingTaskRepository failing = new FailingTaskRepository();
        RecordingTransactionManager manager = new RecordingTransactionManager(null);
        TaskService failingService = new TaskService(failing, new TransactionTemplate(manager), clock,
                ScriptedIds.sequential());
        manager.lockProbe = failingService::isWriteLockHeldByCurrentThread;

        assertThatThrownBy(() -> failingService.create(new CreateTask("t", ""), identity))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThatThrownBy(() -> failingService.delete(ScriptedIds.idNumber(1)))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThatThrownBy(failingService::list).isInstanceOf(DataAccessException.class);

        assertThat(manager.events).containsExactly("begin lock=true", "rollback", "begin lock=true", "rollback");
        assertThat(failingService.isWriteLockHeldByCurrentThread()).isFalse();
        assertThat(failingService.writeLock().isLocked()).isFalse();
    }

    // ---- the write lock (D-7) ----

    @Test
    void theLockIsHeldBeforeTheTransactionBeginsBeforeAnyRepositoryCallAndUntilAfterTheCommit() {
        Task created = service.create(new CreateTask("t", ""), identity);
        transactions.events.clear();
        List<String> repositoryCalls = new ArrayList<>();
        repository.observeCalls(name -> repositoryCalls.add(name + " lock=" + service.isWriteLockHeldByCurrentThread()));

        service.create(new CreateTask("u", ""), identity);
        service.replace(new ReplaceTask(created.id(), "v", ""), identity);
        service.setStatus(new SetStatus(created.id(), TaskStatus.COMPLETED), identity);
        service.delete(created.id());

        assertThat(repositoryCalls).containsExactly("insert lock=true", "findById lock=true", "update lock=true",
                "findById lock=true", "update lock=true", "delete lock=true");
        assertThat(transactions.events).containsExactly(
                "begin lock=true", "commit lock=true", "begin lock=true", "commit lock=true",
                "begin lock=true", "commit lock=true", "begin lock=true", "commit lock=true");
        assertThat(service.isWriteLockHeldByCurrentThread()).isFalse();
    }

    @Test
    void theWriteLockIsOneFairReentrantLock() {
        assertThat(service.writeLock().isFair()).isTrue();
    }

    @Test
    void aMutationWaitsForTheLockBeforeItTouchesTheStore() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            service.writeLock().lock();
            Future<Task> pending;
            try {
                pending = executor.submit(() -> service.create(new CreateTask("t", ""), identity));
                Thread.sleep(300);

                assertThat(pending.isDone()).isFalse();
                assertThat(repository.calls()).isZero();
                assertThat(transactions.events).isEmpty();
            } finally {
                service.writeLock().unlock();
            }

            assertThat(pending.get(10, TimeUnit.SECONDS).title()).isEqualTo("t");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentUpdatesOfOneTaskAreSerialisedLastWriteWinsAndUpdatedAtNeverRepeats() throws Exception {
        Task created = service.create(new CreateTask("start", ""), identity);
        int threads = 8;
        int perThread = 25;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Instant> seen = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                int thread = t;
                futures.add(executor.submit(() -> {
                    go.await();
                    for (int i = 0; i < perThread; i++) {
                        Task replaced = service.replace(new ReplaceTask(created.id(), "t" + thread + "-" + i, ""),
                                identity);
                        seen.add(replaced.updatedAt());
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        Set<Instant> distinct = new HashSet<>(seen);
        assertThat(distinct).hasSize(threads * perThread);
        assertThat(service.get(created.id()).updatedAt())
                .isEqualTo(created.updatedAt().plusMillis((long) threads * perThread));
        assertThat(transactions.maxOpenTransactions).isEqualTo(1);
        assertThat(service.isWriteLockHeldByCurrentThread()).isFalse();
    }

    // ---- helpers ----

    private static Function<Task, Task> failing(RuntimeException failure) {
        return task -> {
            throw failure;
        };
    }

    /**
     * A hand-written {@link PlatformTransactionManager}: records "begin", "commit" and "rollback" with the lock state
     * at that moment, and models the store's atomicity by restoring a snapshot on rollback or when it fails a commit.
     */
    static final class RecordingTransactionManager implements PlatformTransactionManager {

        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        volatile BooleanSupplier lockProbe = () -> false;
        volatile boolean failNextCommit;
        volatile int maxOpenTransactions;
        private final InMemoryTaskRepository store;
        private InMemoryTaskRepository.Snapshot snapshot;
        private int open;

        RecordingTransactionManager(InMemoryTaskRepository store) {
            this.store = store;
        }

        @Override
        public synchronized TransactionStatus getTransaction(TransactionDefinition definition) {
            events.add("begin lock=" + lockProbe.getAsBoolean());
            open++;
            maxOpenTransactions = Math.max(maxOpenTransactions, open);
            snapshot = store == null ? null : store.snapshot();
            return new SimpleTransactionStatus(true);
        }

        @Override
        public synchronized void commit(TransactionStatus status) {
            open--;
            if (failNextCommit) {
                failNextCommit = false;
                restore();
                events.add("commit-failed");
                throw new DataAccessResourceFailureException("simulated commit failure");
            }
            events.add("commit lock=" + lockProbe.getAsBoolean());
        }

        @Override
        public synchronized void rollback(TransactionStatus status) {
            open--;
            restore();
            events.add("rollback");
        }

        private void restore() {
            if (store != null) {
                store.restore(snapshot);
            }
        }
    }
}
