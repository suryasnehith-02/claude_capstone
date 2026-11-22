package com.capstone.todo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TaskStatusTest {

    @Test
    void exactLabelsParse() {
        assertThat(TaskStatus.fromLabel("Pending")).contains(TaskStatus.PENDING);
        assertThat(TaskStatus.fromLabel("Completed")).contains(TaskStatus.COMPLETED);
    }

    @Test
    void labelsAreTheWireText() {
        assertThat(TaskStatus.PENDING.label()).isEqualTo("Pending");
        assertThat(TaskStatus.COMPLETED.label()).isEqualTo("Completed");
        assertThat(TaskStatus.values()).hasSize(2);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"pending", "completed", "PENDING", "COMPLETED", " Pending", "Pending ", "Pending\n", "",
            "Done", "In Progress", "Pendıng"})
    void everythingElseIsRejectedWithoutCoercion(String text) {
        assertThat(TaskStatus.fromLabel(text)).isEmpty();
    }
}
