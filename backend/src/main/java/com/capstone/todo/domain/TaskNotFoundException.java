package com.capstone.todo.domain;

/**
 * No task has the requested id (stage P7). The message is fixed: request values never go into exception messages.
 */
public class TaskNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TaskNotFoundException() {
        super("Task not found", null, false, false);
    }
}
