package com.capstone.todo.support;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.capstone.todo.domain.support.IdGenerator;

/**
 * An {@link IdGenerator} that returns the ids a test scripted, in order, and fails clearly when they run out.
 * {@link #sequential()} yields {@code 00000000-0000-4000-8000-000000000001}, {@code ...0002}, and so on.
 */
public final class ScriptedIds implements IdGenerator {

    private final Deque<UUID> remaining;
    private final AtomicInteger issued = new AtomicInteger();
    private final boolean endless;
    private long counter;

    public ScriptedIds(UUID... ids) {
        this.remaining = new ArrayDeque<>(List.of(ids));
        this.endless = false;
    }

    private ScriptedIds() {
        this.remaining = new ArrayDeque<>();
        this.endless = true;
    }

    /** Endless, predictable ids. */
    public static ScriptedIds sequential() {
        return new ScriptedIds();
    }

    /** The id with the given number, in the sequential pattern. */
    public static UUID idNumber(long number) {
        return UUID.fromString(String.format("00000000-0000-4000-8000-%012x", number));
    }

    @Override
    public synchronized UUID next() {
        issued.incrementAndGet();
        if (endless) {
            return idNumber(++counter);
        }
        UUID id = remaining.pollFirst();
        if (id == null) {
            throw new IllegalStateException("ScriptedIds ran out of scripted ids");
        }
        return id;
    }

    /** Number of ids requested so far. */
    public int issued() {
        return issued.get();
    }
}
