package com.capstone.todo.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TestDataDirTest {

    @Test
    void everyCallYieldsADistinctExistingEmptyDirectoryUnderTargetTestData() throws Exception {
        Path first = TestDataDir.create(TestDataDirTest.class);
        Path second = TestDataDir.create(TestDataDirTest.class);

        assertThat(first).isNotEqualTo(second);
        assertThat(first).isDirectory();
        assertThat(second).isDirectory();
        assertThat(first.getParent()).isEqualTo(TestDataDir.baseDirectory());
        assertThat(first.getFileName().toString()).startsWith("TestDataDirTest-");
        assertThat(first.getParent().getParent().getFileName().toString()).isEqualTo("target");
        try (var entries = Files.list(first)) {
            assertThat(entries.count()).isZero();
        }
    }

    @Test
    void newDataFileIsInsideAFreshDirectoryAndDoesNotExistYet() {
        Path file = TestDataDir.newDataFile(TestDataDirTest.class);

        assertThat(file.getFileName().toString()).isEqualTo("todo.db");
        assertThat(file.getParent()).isDirectory();
        assertThat(file).doesNotExist();
        assertThat(file).isAbsolute();
    }

    @Test
    void createsNewDirectoriesWithoutDeletingEarlierOnes() throws Exception {
        Path earlier = TestDataDir.create(TestDataDirTest.class);
        Path marker = earlier.resolve("marker.txt");
        Files.writeString(marker, "kept", StandardCharsets.UTF_8);

        TestDataDir.create(TestDataDirTest.class);
        TestDataDir.newDataFile(TestDataDirTest.class);

        assertThat(earlier).isDirectory();
        assertThat(marker).hasContent("kept");
    }
}
