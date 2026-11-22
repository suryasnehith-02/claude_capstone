package com.capstone.todo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TaskTest {

    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final Instant T0 = Instant.parse("2026-01-02T03:04:05.006Z");

    private static Task task(Instant createdAt, Instant updatedAt) {
        return new Task(Task.UNASSIGNED_SEQ, ID, "title", "", TaskStatus.PENDING, createdAt, updatedAt);
    }

    @Test
    void timestampsAreTruncatedToMilliseconds() {
        Task task = task(Instant.parse("2026-01-02T03:04:05.006789Z"), Instant.parse("2026-01-02T03:04:05.0069999Z"));

        assertThat(task.createdAt()).isEqualTo(T0);
        assertThat(task.updatedAt()).isEqualTo(T0);
    }

    @Test
    void withSeqChangesOnlyTheSequence() {
        Task original = task(T0, T0.plusMillis(1));

        Task assigned = original.withSeq(7);

        assertThat(assigned.seq()).isEqualTo(7);
        assertThat(assigned).isEqualTo(new Task(7, ID, "title", "", TaskStatus.PENDING, T0, T0.plusMillis(1)));
        assertThat(original.seq()).isEqualTo(Task.UNASSIGNED_SEQ);
    }

    @Test
    void nullAndNegativeValuesAreRejected() {
        assertThatThrownBy(() -> new Task(-1, ID, "t", "", TaskStatus.PENDING, T0, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Task(0, null, "t", "", TaskStatus.PENDING, T0, T0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Task(0, ID, null, "", TaskStatus.PENDING, T0, T0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Task(0, ID, "t", null, TaskStatus.PENDING, T0, T0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Task(0, ID, "t", "", null, T0, T0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Task(0, ID, "t", "", TaskStatus.PENDING, null, T0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Task(0, ID, "t", "", TaskStatus.PENDING, T0, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void commandsRejectNulls() {
        assertThatThrownBy(() -> new CreateTask(null, "")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CreateTask("t", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReplaceTask(null, "t", "")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReplaceTask(ID, null, "")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReplaceTask(ID, "t", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SetStatus(null, TaskStatus.PENDING)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SetStatus(ID, null)).isInstanceOf(NullPointerException.class);
        assertThat(new CreateTask("t", "").description()).isEmpty();
    }

    @Test
    void notFoundExceptionHasAFixedMessageAndNoStackTrace() {
        TaskNotFoundException exception = new TaskNotFoundException();

        assertThat(exception).hasMessage("Task not found");
        assertThat(exception.getStackTrace()).isEmpty();
    }
}
