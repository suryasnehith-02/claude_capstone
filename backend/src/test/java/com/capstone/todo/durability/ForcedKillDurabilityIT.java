package com.capstone.todo.durability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.capstone.todo.config.JsonConfig;
import com.capstone.todo.support.EvidenceLog;
import com.capstone.todo.support.RawHttp;
import com.capstone.todo.support.TestDataDir;

import tools.jackson.databind.json.JsonMapper;

/**
 * Durability (b), forced kill (T-14, AC-22, NFR-4, V-28): Failsafe runs this after {@code package}. It starts the
 * packaged executable JAR in a child JVM (the JDK that runs the test, with {@code --enable-native-access=ALL-UNNAMED}),
 * creates, changes and deletes tasks over HTTP, kills the child with {@code destroyForcibly()} (no shutdown hook, no
 * pool close), starts a second child on the same file and compares. The JAR path comes from the system property
 * {@code todo.packaged-jar} that the POM passes to Failsafe; a missing JAR fails the test, it is never skipped.
 */
class ForcedKillDurabilityIT {

    private static final JsonMapper JSON = JsonConfig.newApiJsonMapper();
    private static final EvidenceLog EVIDENCE = new EvidenceLog();
    private static final long START_TIMEOUT_SECONDS = 90;

    private static Path packagedJar() {
        String property = System.getProperty("todo.packaged-jar");
        assertThat(property).as("system property todo.packaged-jar (set by the POM for Failsafe)").isNotBlank();
        Path jar = Path.of(property);
        assertThat(jar).as("the packaged JAR must exist; run ./mvnw -B verify, which packages before the integration test")
                .isRegularFile();
        return jar;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static Path javaLauncher() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
    }

    /** A child JVM running the packaged JAR; its output goes to a log file next to the data file. */
    private record Child(Process process, int port, Path log) {

        static Child start(Path jar, Path dataFile, String name) throws IOException {
            int port = freePort();
            Path log = dataFile.getParent().resolve(name + ".log");
            Process process = new ProcessBuilder(javaLauncher().toString(), "--enable-native-access=ALL-UNNAMED", "-jar",
                    jar.toString(), "--server.port=" + port, "--todo.data-file=" + dataFile)
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            return new Child(process, port, log);
        }

        void awaitReady() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(START_TIMEOUT_SECONDS);
            while (System.nanoTime() < deadline) {
                assertThat(process.isAlive()).as("the child JVM exited before it was ready; its output:%n%s", output()).isTrue();
                try {
                    if (RawHttp.request("GET", "/todos").send(port).status() == 200) {
                        return;
                    }
                } catch (RuntimeException notYet) {
                    // The port is not open yet.
                }
                Thread.sleep(250);
            }
            throw new AssertionError("the child JVM was not ready after " + START_TIMEOUT_SECONDS + " s; its output:\n" + output());
        }

        String output() {
            try {
                return Files.readString(log);
            } catch (IOException e) {
                return "(no output: " + e.getMessage() + ")";
            }
        }

        RawHttp.Response send(String method, String target, String body) {
            RawHttp.Request request = RawHttp.request(method, target);
            if (body != null) {
                request.header("Content-Type", "application/json").body(body);
            }
            return request.send(port);
        }

        void kill() throws InterruptedException {
            process.destroyForcibly();
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("the killed child JVM ends").isTrue();
        }
    }

    private static String idOf(RawHttp.Response created) {
        assertThat(created.status()).as(created.bodyText()).isEqualTo(201);
        return JSON.readTree(created.bodyText()).get("data").get("id").asString();
    }

    @Test
    void createdChangedAndDeletedTasksSurviveAForcedKillOfTheServer_ac22() throws Exception {
        Path jar = packagedJar();
        Path dataFile = TestDataDir.newDataFile(ForcedKillDurabilityIT.class);
        List<Child> started = new java.util.ArrayList<>();
        try {
            Child first = Child.start(jar, dataFile, "child-1");
            started.add(first);
            first.awaitReady();
            String keptId = idOf(first.send("POST", "/todos", "{\"title\":\"kept\",\"description\":\"unchanged\"}"));
            String changedId = idOf(first.send("POST", "/todos",
                    "{\"title\":\"caf\\u00e9 \\ud83d\\ude00 <b>&</b>\",\"description\":\"d\"}"));
            String deletedId = idOf(first.send("POST", "/todos", "{\"title\":\"to delete\"}"));
            assertThat(first.send("PUT", "/todos/" + changedId, "{\"title\":\"changed title\",\"description\":\"changed\"}")
                    .status()).isEqualTo(200);
            assertThat(first.send("PATCH", "/todos/" + changedId + "/status", "{\"status\":\"Completed\"}").status())
                    .isEqualTo(200);
            assertThat(first.send("DELETE", "/todos/" + deletedId, null).status()).isEqualTo(204);
            String listBefore = first.send("GET", "/todos", null).bodyText();
            assertThat(listBefore).contains(keptId, changedId).doesNotContain(deletedId);

            first.kill();

            Child second = Child.start(jar, dataFile, "child-2");
            started.add(second);
            second.awaitReady();
            String listAfter = second.send("GET", "/todos", null).bodyText();

            assertThat(listAfter).as("the list after the kill equals the list before").isEqualTo(listBefore);
            assertThat(second.send("GET", "/todos/" + deletedId, null).status()).as("a deleted task stays absent").isEqualTo(404);
            assertThat(second.send("GET", "/todos/" + changedId, null).bodyText()).contains("changed title",
                    "\"status\":\"Completed\"");
            EVIDENCE.row("T-14 durability (b)", "child JVM from " + jar.getFileName() + ": create x3, PUT, PATCH, DELETE; "
                    + "destroyForcibly(); second child on the same file",
                    "list byte-identical; deleted id 404",
                    "list identical (" + listBefore.length() + " bytes); deleted id 404; child 1 exit value "
                            + first.process().exitValue());
            second.kill();
        } finally {
            for (Child child : started) {
                if (child.process().isAlive()) {
                    child.process().destroyForcibly();
                }
            }
            EVIDENCE.writeTo("t14-forcedkill-evidence.txt");
        }
    }
}
