package com.capstone.todo.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class FixedClockTest {

    private static final Instant START = Instant.parse("2026-01-02T03:04:05.006Z");

    @Test
    void staysPutUntilTold() {
        FixedClock clock = new FixedClock(START);

        assertThat(clock.instant()).isEqualTo(START);
        assertThat(clock.instant()).isEqualTo(START);
        assertThat(clock.millis()).isEqualTo(START.toEpochMilli());
    }

    @Test
    void advancesAndSets() {
        FixedClock clock = new FixedClock(START);

        clock.advance(Duration.ofMillis(5));
        assertThat(clock.instant()).isEqualTo(START.plusMillis(5));

        clock.set(START.minusSeconds(1));
        assertThat(clock.instant()).isEqualTo(START.minusSeconds(1));
    }

    @Test
    void isAlwaysUtc() {
        FixedClock clock = new FixedClock(START);

        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
        assertThat(clock.withZone(ZoneOffset.UTC)).isSameAs(clock);
        assertThatThrownBy(() -> clock.withZone(ZoneId.of("Asia/Calcutta")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
