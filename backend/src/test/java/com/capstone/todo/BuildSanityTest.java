package com.capstone.todo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Build-level sanity check (CAP-2 T-2): proves that tests run under Surefire on the installed JDK.
 * JVM warnings printed on stderr are recorded in the implementation report, never asserted empty.
 */
class BuildSanityTest {

    @Test
    void runsOnTheInstalledJdk() {
        int feature = Runtime.version().feature();
        System.out.println("BuildSanityTest: running JVM feature version = " + feature
                + ", java.version = " + System.getProperty("java.version"));
        assertThat(feature).isEqualTo(25);
    }

    @Test
    void classesAreCompiledForRelease21() throws Exception {
        String resource = BuildSanityTest.class.getName().replace('.', '/') + ".class";
        try (var in = BuildSanityTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).isNotNull();
            byte[] header = in.readNBytes(8);
            int major = ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
            System.out.println("BuildSanityTest: class file major version = " + major + " (65 = Java 21)");
            assertThat(major).isEqualTo(65);
        }
    }
}
