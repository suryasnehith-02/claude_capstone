package com.capstone.todo.domain.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class UuidIdGeneratorTest {

    private static final Pattern LOWERCASE_CANONICAL =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    @Test
    void defaultIdsAreLowercaseCanonicalUuidsAndDistinct() {
        IdGenerator generator = new UuidIdGenerator();
        Set<UUID> seen = new HashSet<>();

        for (int i = 0; i < 1_000; i++) {
            UUID id = generator.next();

            assertThat(id.toString()).matches(LOWERCASE_CANONICAL);
            assertThat(id.version()).isEqualTo(4);
            assertThat(seen.add(id)).isTrue();
        }
    }
}
