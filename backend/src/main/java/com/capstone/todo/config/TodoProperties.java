package com.capstone.todo.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings under the {@code todo} prefix.
 *
 * <p>{@code synchronous} is deliberately not a setting: it is always {@link #SYNCHRONOUS} (architecture D-6).
 *
 * @param dataFile path of the SQLite database file; relative paths are resolved against the working directory
 * @param store    storage settings
 */
@ConfigurationProperties(prefix = "todo")
public record TodoProperties(
        @DefaultValue("./data/todo.db") String dataFile,
        @DefaultValue Store store) {

    /** SQLite {@code synchronous} pragma value; fixed, not configurable. */
    public static final String SYNCHRONOUS = "FULL";

    public TodoProperties {
        if (dataFile == null || dataFile.isBlank()) {
            throw new IllegalArgumentException("todo.data-file must not be blank");
        }
        try {
            Path.of(dataFile);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("todo.data-file is not a valid path");
        }
    }

    /**
     * @param journalMode SQLite journal mode; allowed values are the {@link JournalMode} constants
     */
    public record Store(@DefaultValue("DELETE") JournalMode journalMode) {
    }

    public enum JournalMode {
        DELETE, TRUNCATE, WAL
    }

    /** The data file as an absolute, normalised path. Touches nothing on disk. */
    public Path dataFilePath() {
        return Path.of(dataFile).toAbsolutePath().normalize();
    }

    /** Like {@link #dataFilePath()}, but first creates the missing parent directories. */
    public Path prepareDataFile() {
        Path file = dataFilePath();
        Path parent = file.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot create the parent directory of todo.data-file", e);
            }
        }
        return file;
    }
}
