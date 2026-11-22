package com.capstone.todo.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.capstone.todo.domain.Task;
import com.capstone.todo.domain.TaskStatus;

class InMemoryTaskRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-01-02T03:04:05.006Z");

    private final InMemoryTaskRepository repository = new InMemoryTaskRepository();

    private static Task task(long number, Instant createdAt) {
        return new Task(Task.UNASSIGNED_SEQ, ScriptedIds.idNumber(number), "title " + number, "", TaskStatus.PENDING,
                createdAt, createdAt);
    }

    @Test
    void emptyStoreHasNoTasks() {
        assertThat(repository.findAllOrdered()).isEmpty();
        assertThat(repository.findById(ScriptedIds.idNumber(1))).isEmpty();
    }

    @Test
    void insertAssignsAnIncreasingSeqAndFindByIdReturnsTheStoredTask() {
        repository.insert(task(1, T0));
        repository.insert(task(2, T0));

        assertThat(repository.findById(ScriptedIds.idNumber(1))).get().extracting(Task::seq).isEqualTo(1L);
        assertThat(repository.findById(ScriptedIds.idNumber(2))).get().extracting(Task::seq).isEqualTo(2L);
    }

    @Test
    void ordersNewestCreatedAtFirstAndTiesByLargerSeqFirst() {
        repository.insert(task(1, T0));
        repository.insert(task(2, T0.plusMillis(5)));
        repository.insert(task(3, T0));
        repository.insert(task(4, T0.plusMillis(5)));

        assertThat(repository.findAllOrdered()).extracting(Task::id).containsExactly(
                ScriptedIds.idNumber(4), ScriptedIds.idNumber(2), ScriptedIds.idNumber(3), ScriptedIds.idNumber(1));
    }

    @Test
    void seqIsNeverReusedAfterADelete() {
        repository.insert(task(1, T0));
        repository.insert(task(2, T0));
        assertThat(repository.delete(ScriptedIds.idNumber(2))).isTrue();
        repository.insert(task(3, T0));

        assertThat(repository.findById(ScriptedIds.idNumber(3))).get().extracting(Task::seq).isEqualTo(3L);
    }

    @Test
    void duplicateIdIsRejected() {
        repository.insert(task(1, T0));

        assertThatThrownBy(() -> repository.insert(task(1, T0.plusMillis(1))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.contents()).hasSize(1);
    }

    @Test
    void updateChangesContentAndUpdatedAtButKeepsSeqAndCreatedAt() {
        repository.insert(task(1, T0));
        Task original = repository.findById(ScriptedIds.idNumber(1)).orElseThrow();

        boolean updated = repository.update(new Task(99, original.id(), "new", "text", TaskStatus.COMPLETED,
                T0.plusSeconds(60), T0.plusSeconds(61)));

        assertThat(updated).isTrue();
        Task stored = repository.findById(original.id()).orElseThrow();
        assertThat(stored.seq()).isEqualTo(original.seq());
        assertThat(stored.createdAt()).isEqualTo(T0);
        assertThat(stored.updatedAt()).isEqualTo(T0.plusSeconds(61));
        assertThat(stored.title()).isEqualTo("new");
        assertThat(stored.description()).isEqualTo("text");
        assertThat(stored.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void updateAndDeleteOfAnUnknownIdReportFalse() {
        assertThat(repository.update(task(1, T0))).isFalse();
        assertThat(repository.delete(ScriptedIds.idNumber(1))).isFalse();
    }

    @Test
    void deleteRemovesTheTaskAndARepeatDeleteReportsFalse() {
        repository.insert(task(1, T0));

        assertThat(repository.delete(ScriptedIds.idNumber(1))).isTrue();
        assertThat(repository.delete(ScriptedIds.idNumber(1))).isFalse();
        assertThat(repository.findAllOrdered()).isEmpty();
    }

    @Test
    void countsAndLogsEveryCallAndReportsThemToTheObserver() {
        List<String> observed = new ArrayList<>();
        repository.observeCalls(observed::add);

        repository.insert(task(1, T0));
        repository.findById(ScriptedIds.idNumber(1));
        repository.findAllOrdered();
        repository.update(task(1, T0));
        repository.delete(ScriptedIds.idNumber(1));

        List<String> expected = List.of("insert", "findById", "findAllOrdered", "update", "delete");
        assertThat(repository.calls()).isEqualTo(5);
        assertThat(repository.callLog()).isEqualTo(expected);
        assertThat(observed).isEqualTo(expected);
    }

    @Test
    void snapshotsContentsAndRestoreDoNotCountAsCalls() {
        repository.insert(task(1, T0));
        InMemoryTaskRepository.Snapshot before = repository.snapshot();
        repository.insert(task(2, T0));
        repository.delete(ScriptedIds.idNumber(1));
        int callsBefore = repository.calls();

        repository.restore(before);

        assertThat(repository.contents()).extracting(Task::id).containsExactly(ScriptedIds.idNumber(1));
        assertThat(repository.calls()).isEqualTo(callsBefore);
        repository.insert(task(3, T0));
        assertThat(repository.findById(ScriptedIds.idNumber(3))).get().extracting(Task::seq).isEqualTo(2L);
    }

    @Test
    void returnedListsAreImmutableCopies() {
        repository.insert(task(1, T0));
        List<Task> listed = repository.findAllOrdered();

        assertThatThrownBy(() -> listed.add(task(2, T0))).isInstanceOf(UnsupportedOperationException.class);
        repository.insert(task(2, T0));
        assertThat(listed).hasSize(1);
    }

    @Test
    void usedFromManyThreadsKeepsEveryTask() throws Exception {
        List<Thread> threads = new ArrayList<>();
        for (int worker = 0; worker < 8; worker++) {
            int base = worker * 100;
            Thread thread = new Thread(() -> {
                for (int i = 1; i <= 100; i++) {
                    repository.insert(task(base + i, T0));
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(repository.contents()).hasSize(800);
        assertThat(repository.contents().stream().map(Task::seq).distinct().count()).isEqualTo(800);
        assertThat(repository.findById(UUID.fromString("00000000-0000-4000-8000-000000000001"))).isPresent();
    }
}
