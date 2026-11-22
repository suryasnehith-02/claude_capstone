package com.capstone.todo.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.capstone.todo.config.JsonConfig;
import com.capstone.todo.support.RawHttp;
import com.capstone.todo.support.TestDataDir;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T-B1, the one case no other class holds: a maximal task (title of 100 and description of 500 code points, every one
 * written as a JSON surrogate-pair escape) in a request of exactly 65,536 bytes. The text must come back as the
 * characters themselves (AC-33), unchanged on create, get, list, replace and status change, and must be stored (AC-28).
 * The other T-B1 cases are proved elsewhere: a body of exactly 65,536 bytes and one byte more
 * ({@code CrudContractTest}, {@code GatewayContractTest}), a query string on every route, an uppercase id
 * ({@code CrudContractTest}, {@code PrecedenceContractTest} T-P4b), 1,000-digit numbers, nesting 1,000 and a
 * 60,000-character member name ({@code PrecedenceContractTest} T-P5).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BoundaryContractTest {

    private static final Path DATA_FILE = TestDataDir.newDataFile(BoundaryContractTest.class);
    private static final JsonMapper JSON = JsonConfig.newApiJsonMapper();
    private static final String ESCAPED_PAIR = "\\ud83d\\ude00";
    private static final String ASTRAL = new String(Character.toChars(0x1F600));

    @DynamicPropertySource
    static void dataFile(DynamicPropertyRegistry registry) {
        registry.add("todo.data-file", DATA_FILE::toString);
    }

    @LocalServerPort
    private int port;

    private RawHttp.Response send(String method, String target, byte[] body) {
        RawHttp.Request request = RawHttp.request(method, target);
        if (body != null) {
            request.header("Content-Type", "application/json").body(body);
        }
        return request.send(port);
    }

    private static byte[] paddedTo65536(String json) {
        byte[] bytes = new byte[65_536];
        Arrays.fill(bytes, (byte) ' ');
        byte[] text = json.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(text, 0, bytes, 0, text.length);
        return bytes;
    }

    private static void assertMaximal(JsonNode task) {
        assertThat(task.get("title").asString()).isEqualTo(ASTRAL.repeat(100));
        assertThat(task.get("description").asString()).isEqualTo(ASTRAL.repeat(500));
    }

    @Test
    void aMaximalTaskWrittenAsSurrogateEscapesInA65536ByteRequestComesBackAsTheCharactersThemselves_tb1() {
        String maximal = "{\"title\":\"" + ESCAPED_PAIR.repeat(100) + "\",\"description\":\"" + ESCAPED_PAIR.repeat(500) + "\"}";
        byte[] request = paddedTo65536(maximal);
        assertThat(request).hasSize(65_536);

        RawHttp.Response created = send("POST", "/todos", request);

        assertThat(created.status()).as(created.bodyText()).isEqualTo(201);
        assertThat(created.bodyText()).contains(ASTRAL).doesNotContain("\\ud83d").doesNotContain("\\ude00");
        JsonNode task = JSON.readTree(created.bodyText()).get("data");
        assertMaximal(task);
        String id = task.get("id").asString();

        RawHttp.Response fetched = send("GET", "/todos/" + id, null);
        assertThat(fetched.body()).as("GET returns the stored task as POST returned it").isEqualTo(created.body());
        RawHttp.Response list = send("GET", "/todos", null);
        String createdTask = created.bodyText().substring("{\"data\":".length(), created.bodyText().length() - 1);
        assertThat(list.bodyText()).isEqualTo("{\"data\":[" + createdTask + "]}");

        RawHttp.Response replaced = send("PUT", "/todos/" + id, paddedTo65536(maximal));
        assertThat(replaced.status()).as(replaced.bodyText()).isEqualTo(200);
        assertMaximal(JSON.readTree(replaced.bodyText()).get("data"));
        RawHttp.Response completed = send("PATCH", "/todos/" + id + "/status",
                "{\"status\":\"Completed\"}".getBytes(StandardCharsets.UTF_8));
        assertThat(completed.status()).isEqualTo(200);
        JsonNode afterStatus = JSON.readTree(completed.bodyText()).get("data");
        assertMaximal(afterStatus);
        assertThat(afterStatus.get("status").asString()).isEqualTo("Completed");
        assertMaximal(JSON.readTree(send("GET", "/todos/" + id, null).bodyText()).get("data"));
    }
}
