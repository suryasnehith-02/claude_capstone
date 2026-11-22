package com.capstone.todo.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.capstone.todo.domain.support.IdGenerator;
import com.capstone.todo.domain.support.UuidIdGenerator;

/** The two seams that make time and ids deterministic in tests (architecture C11). */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public IdGenerator idGenerator() {
        return new UuidIdGenerator();
    }
}
