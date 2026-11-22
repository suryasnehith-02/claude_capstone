package com.capstone.todo.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Hands out a fresh data directory for each Spring context or repository test, under
 * {@code backend/target/test-data/<class>-<random>/}.
 *
 * <p>Nothing is ever deleted here or by the tests that use it (Windows keeps SQLite files locked until the
 * DataSource is closed, and deleting user data needs approval); {@code target/} is ignored by git.
 */
public final class TestDataDir {

    private static final String FILE_NAME = "todo.db";

    private TestDataDir() {
    }

    /** Creates a new, empty directory unique to this call. */
    public static Path create(Class<?> testClass) {
        try {
            Path base = baseDirectory();
            Files.createDirectories(base);
            return Files.createTempDirectory(base, testClass.getSimpleName() + "-");
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create a test data directory", e);
        }
    }

    /** Creates a new directory and returns the (not yet existing) database file path inside it. */
    public static Path newDataFile(Class<?> testClass) {
        return create(testClass).resolve(FILE_NAME);
    }

    /** {@code <module>/target/test-data}; Surefire sets {@code basedir}, the working directory is the fallback. */
    public static Path baseDirectory() {
        return Path.of(System.getProperty("basedir", "."))
                .resolve("target")
                .resolve("test-data")
                .toAbsolutePath()
                .normalize();
    }
}
