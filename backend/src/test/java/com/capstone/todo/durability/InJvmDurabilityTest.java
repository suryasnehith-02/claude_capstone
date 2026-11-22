package com.capstone.todo.durability;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.capstone.todo.TodoApplication;
import com.capstone.todo.config.JsonConfig;
import com.capstone.todo.support.EvidenceLog;
import com.capstone.todo.support.RawHttp;
import com.capstone.todo.support.TestDataDir;
import com.zaxxer.hikari.HikariDataSource;

import tools.jackson.databind.json.JsonMapper;

/**
 * Durability (a), in one JVM (T-14, AC-22, NFR-4): start the real application on a SQLite file, create, change and
 * delete tasks over HTTP, close the context (the pool must be closed), start a second context on the same file and
 * compare. Created and changed tasks are byte-identical in the list; a deleted task stays absent. The forced-kill
 * variant is {@code ForcedKillDurabilityIT}.
 */
class InJvmDurabilityTest {

    private static final JsonMapper JSON = JsonConfig.newApiJsonMapper();
    private static final EvidenceLog EVIDENCE = new EvidenceLog();

    private static ConfigurableApplicationContext start(Path file) {
        return new SpringApplicationBuilder(TodoApplication.class).run("--todo.data-file=" + file, "--server.port=0",
                "--spring.main.banner-mode=off");
    }

    private static int portOf(ConfigurableApplicationContext context) {
        String port = context.getEnvironment().getProperty("local.server.port");
        assertThat(port).as("local.server.port").isNotNull();
        return Integer.parseInt(port);
    }

    private static RawHttp.Response send(int port, String method, String target, String body) {
        RawHttp.Request request = RawHttp.request(method, target);
        if (body != null) {
            request.header("Content-Type", "application/json").body(body);
        }
        return request.send(port);
    }

    private static String idOf(RawHttp.Response created) {
        assertThat(created.status()).as(created.bodyText()).isEqualTo(201);
        return JSON.readTree(created.bodyText()).get("data").get("id").asString();
    }

    @Test
    void createdChangedAndDeletedTasksAreIdenticalAfterTheContextIsClosedAndStartedAgain_ac22() {
        Path file = TestDataDir.newDataFile(InJvmDurabilityTest.class);
        String listBefore;
        String changedId;
        String deletedId;
        HikariDataSource firstPool;

        try (ConfigurableApplicationContext first = start(file)) {
            int port = portOf(first);
            firstPool = first.getBean(HikariDataSource.class);
            String keptId = idOf(send(port, "POST", "/todos", "{\"title\":\"kept\",\"description\":\"unchanged\"}"));
            changedId = idOf(send(port, "POST", "/todos",
                    "{\"title\":\"caf\\u00e9 \\ud83d\\ude00 <b>&</b>\",\"description\":\"d\"}"));
            deletedId = idOf(send(port, "POST", "/todos", "{\"title\":\"to delete\"}"));
            assertThat(send(port, "PUT", "/todos/" + changedId, "{\"title\":\"changed title\",\"description\":\"changed\"}")
                    .status()).isEqualTo(200);
            assertThat(send(port, "PATCH", "/todos/" + changedId + "/status", "{\"status\":\"Completed\"}").status())
                    .isEqualTo(200);
            assertThat(send(port, "DELETE", "/todos/" + deletedId, null).status()).isEqualTo(204);

            RawHttp.Response list = send(port, "GET", "/todos", null);
            assertThat(list.status()).isEqualTo(200);
            listBefore = list.bodyText();
            assertThat(listBefore).contains(keptId, changedId).doesNotContain(deletedId).contains("\"status\":\"Completed\"");
        }
        assertThat(firstPool.isClosed()).as("the pool is closed when the context closes").isTrue();

        try (ConfigurableApplicationContext second = start(file)) {
            int port = portOf(second);
            RawHttp.Response list = send(port, "GET", "/todos", null);

            assertThat(list.bodyText()).as("the list after the restart equals the list before").isEqualTo(listBefore);
            assertThat(send(port, "GET", "/todos/" + deletedId, null).status()).as("a deleted task stays absent").isEqualTo(404);
            assertThat(send(port, "DELETE", "/todos/" + deletedId, null).status()).isEqualTo(404);
            assertThat(send(port, "GET", "/todos/" + changedId, null).bodyText()).contains("changed title",
                    "\"status\":\"Completed\"");
            EVIDENCE.row("T-14 durability (a)", "create x3, PUT, PATCH, DELETE; close context; start on the same file",
                    "pool closed; list byte-identical; deleted id 404", "list identical (" + listBefore.length()
                            + " bytes); deleted id 404; first pool closed=" + firstPool.isClosed());
        }
        EVIDENCE.writeTo("t14-durability-evidence.txt");
    }
}
