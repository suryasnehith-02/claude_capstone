package com.capstone.todo.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects the rows of an evidence table while a contract test runs and writes them to
 * {@code target/<name>}, so the implementation report copies what the server did, not what the test expected.
 * The file is rewritten by every run (it is under the ignored {@code target/} directory).
 */
public final class EvidenceLog {

    private final List<String> rows = Collections.synchronizedList(new ArrayList<>());

    /** One tab-separated row: test, request shape, expected, actual. */
    public void row(String test, String shape, String expected, String actual) {
        rows.add(String.join("\t", test, shape, expected, actual));
    }

    /** A free-text observation line. */
    public void note(String text) {
        rows.add("NOTE\t" + text);
    }

    public int size() {
        return rows.size();
    }

    public void writeTo(String fileName) {
        Path file = TestDataDir.baseDirectory().getParent().resolve(fileName);
        try {
            synchronized (rows) {
                Files.write(file, rows, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
