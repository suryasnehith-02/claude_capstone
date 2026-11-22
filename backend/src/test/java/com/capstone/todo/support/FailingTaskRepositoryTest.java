package com.capstone.todo.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import com.capstone.todo.domain.Task;
import com.capstone.todo.domain.TaskStatus;

class FailingTaskRepositoryTest {

    private static final Task TASK = new Task(0, ScriptedIds.idNumber(1), "secret title", "", TaskStatus.PENDING,
            Instant.parse("2026-01-02T03:04:05.006Z"), Instant.parse("2026-01-02T03:04:05.006Z"));

    @Test
    void everyOperationFailsWithADataAccessExceptionThatHoldsNoTaskData() {
        FailingTaskRepository repository = new FailingTaskRepository();

        assertThatThrownBy(repository::findAllOrdered).isInstanceOf(DataAccessException.class)
                .hasMessage(FailingTaskRepository.FAILURE_MESSAGE);
        assertThatThrownBy(() -> repository.findById(TASK.id())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> repository.insert(TASK)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> repository.update(TASK)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> repository.delete(TASK.id())).isInstanceOf(DataAccessException.class)
                .hasMessageNotContaining("secret title");
        assertThat(repository.attempts()).isEqualTo(5);
    }

    @Test
    void aCustomFailureIsThrownEachTime() {
        FailingTaskRepository repository = new FailingTaskRepository(() -> new IllegalStateException("boom"));

        assertThatThrownBy(repository::findAllOrdered).isInstanceOf(IllegalStateException.class).hasMessage("boom");
        assertThatThrownBy(() -> repository.delete(TASK.id())).isInstanceOf(IllegalStateException.class);
        assertThat(repository.attempts()).isEqualTo(2);
    }
}
