package com.capstone.todo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.capstone.todo.domain.support.IdGenerator;
import com.capstone.todo.domain.support.UuidIdGenerator;

class ClockConfigTest {

    @Test
    void theClockIsUtcAndTheIdGeneratorIsTheUuidOne() {
        ClockConfig config = new ClockConfig();

        assertThat(config.clock().getZone()).isEqualTo(ZoneOffset.UTC);
        assertThat(config.idGenerator()).isInstanceOf(UuidIdGenerator.class);
    }

    @Test
    void bothBeansAreRegisteredInASpringContext() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ClockConfig.class)) {
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
            assertThat(context.getBean(IdGenerator.class)).isNotNull();
        }
    }
}
