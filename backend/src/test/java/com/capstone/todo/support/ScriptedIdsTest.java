package com.capstone.todo.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ScriptedIdsTest {

    @Test
    void returnsTheScriptedIdsInOrderThenFailsClearly() {
        UUID first = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID second = UUID.fromString("22222222-2222-4222-8222-222222222222");
        ScriptedIds ids = new ScriptedIds(first, second);

        assertThat(ids.next()).isEqualTo(first);
        assertThat(ids.next()).isEqualTo(second);
        assertThatThrownBy(ids::next).isInstanceOf(IllegalStateException.class).hasMessageContaining("ran out");
        assertThat(ids.issued()).isEqualTo(3);
    }

    @Test
    void sequentialIdsAreEndlessLowercaseAndCanonical() {
        ScriptedIds ids = ScriptedIds.sequential();

        assertThat(ids.next().toString()).isEqualTo("00000000-0000-4000-8000-000000000001");
        assertThat(ids.next().toString()).isEqualTo("00000000-0000-4000-8000-000000000002");
        assertThat(ScriptedIds.idNumber(255).toString()).isEqualTo("00000000-0000-4000-8000-0000000000ff");
        assertThat(ids.issued()).isEqualTo(2);
    }
}
