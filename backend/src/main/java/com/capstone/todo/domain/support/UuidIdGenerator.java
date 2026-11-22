package com.capstone.todo.domain.support;

import java.util.UUID;

/** Random (version 4) ids. {@link UUID#toString()} is lowercase canonical form. */
public final class UuidIdGenerator implements IdGenerator {

    @Override
    public UUID next() {
        return UUID.randomUUID();
    }
}
