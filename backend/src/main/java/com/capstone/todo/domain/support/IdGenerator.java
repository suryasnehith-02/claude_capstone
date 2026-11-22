package com.capstone.todo.domain.support;

import java.util.UUID;

/** Source of task ids. Tests replace it with a scripted one. */
public interface IdGenerator {

    UUID next();
}
