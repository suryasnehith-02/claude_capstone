package com.capstone.todo.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import com.capstone.todo.config.JsonConfig;
import com.capstone.todo.config.PersistenceConfig;
import com.capstone.todo.domain.TaskService;
import com.capstone.todo.support.EvidenceLog;
import com.capstone.todo.support.RawHttp;
import com.capstone.todo.support.TestDataDir;
import com.zaxxer.hikari.HikariDataSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T-TX (T-14, V-20, V-21, AC-32, D-7, D-22) on real Tomcat and a real SQLite file. A recording wrapper replaces the
 * pool bean and notes, for every {@code getConnection}, whether the calling thread held the service's write lock and how
 * long the call waited. It shows that every mutation holds the lock before it asks for the pool's only connection, that
 * no request waits for the connection timeout under concurrent load, that every store call goes through the one
 * DataSource, and that two concurrent updates of one task end with the later write, never with a mix.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransactionWiringContractTest {

    private static final Path DATA_FILE = TestDataDir.newDataFile(TransactionWiringContractTest.class);
    private static final EvidenceLog EVIDENCE = new EvidenceLog();
    private static final JsonMapper JSON = JsonConfig.newApiJsonMapper();
    private static final String MISSING_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7";

    /** What one {@code getConnection} saw: the thread, whether it held the write lock, and how long the call took. */
    private record Call(String thread, boolean writeLockHeld, long waitedMillis) {
    }

    private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    private static volatile BooleanSupplier lockProbe = () -> false;

    /** The pool's wrapper: delegates everything, records each connection request, closes the pool it wraps. */
    static final class RecordingDataSource extends DelegatingDataSource implements AutoCloseable {

        RecordingDataSource(HikariDataSource pool) {
            super(pool);
        }

        @Override
        public Connection getConnection() throws SQLException {
            boolean held = lockProbe.getAsBoolean();
            long start = System.nanoTime();
            try {
                return super.getConnection();
            } finally {
                CALLS.add(new Call(Thread.currentThread().getName(), held, (System.nanoTime() - start) / 1_000_000));
            }
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new UnsupportedOperationException("the store never asks for credentials");
        }

        @Override
        public void close() throws Exception {
            ((AutoCloseable) getTargetDataSource()).close();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Recording {
        @Bean
        static BeanPostProcessor recordingDataSourcePostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    return bean instanceof HikariDataSource pool ? new RecordingDataSource(pool) : bean;
                }
            };
        }
    }

    @DynamicPropertySource
    static void dataFile(DynamicPropertyRegistry registry) {
        registry.add("todo.data-file", DATA_FILE::toString);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private TaskService service;

    @BeforeEach
    void startRecording() {
        lockProbe = service::isWriteLockHeldByCurrentThread;
        CALLS.clear();
    }

    @AfterAll
    static void writeEvidence() {
        lockProbe = () -> false;
        EVIDENCE.writeTo("t14-tx-evidence.txt");
    }

    // ---------------------------------------------------------------- helpers

    private RawHttp.Response send(String method, String target, String body) {
        RawHttp.Request request = RawHttp.request(method, target);
        if (body != null) {
            request.header("Content-Type", "application/json").body(body);
        }
        return request.send(port);
    }

    private static JsonNode data(RawHttp.Response response) {
        return JSON.readTree(response.bodyText()).get("data");
    }

    private String createTask(String title) {
        RawHttp.Response response = send("POST", "/todos", "{\"title\":\"" + title + "\"}");
        assertThat(response.status()).as(response.bodyText()).isEqualTo(201);
        return data(response).get("id").asString();
    }

    // ---------------------------------------------------------------- lock before connection (V-21)

    @Test
    void everyMutationHoldsTheWriteLockBeforeItAsksForTheConnectionAndAReadHoldsNone_v21() {
        record Step(String name, String method, String target, String body, int status, boolean mutation) {
        }
        String id = createTask("seed");
        List<Step> steps = List.of(
                new Step("POST /todos", "POST", "/todos", "{\"title\":\"a\"}", 201, true),
                new Step("PUT /todos/{id}", "PUT", "/todos/" + id, "{\"title\":\"b\"}", 200, true),
                new Step("PATCH /todos/{id}/status", "PATCH", "/todos/" + id + "/status", "{\"status\":\"Completed\"}", 200,
                        true),
                new Step("PUT unknown id (rolled back)", "PUT", "/todos/" + MISSING_ID, "{\"title\":\"b\"}", 404, true),
                new Step("PATCH unknown id (rolled back)", "PATCH", "/todos/" + MISSING_ID + "/status",
                        "{\"status\":\"Completed\"}", 404, true),
                new Step("DELETE unknown id (rolled back)", "DELETE", "/todos/" + MISSING_ID, null, 404, true),
                new Step("DELETE /todos/{id}", "DELETE", "/todos/" + id, null, 204, true),
                new Step("GET /todos", "GET", "/todos", null, 200, false),
                new Step("GET /todos/{id} (unknown)", "GET", "/todos/" + MISSING_ID, null, 404, false));

        for (Step step : steps) {
            CALLS.clear();
            RawHttp.Response response = send(step.method(), step.target(), step.body());
            List<Call> calls = List.copyOf(CALLS);

            assertThat(response.status()).as(step.name()).isEqualTo(step.status());
            assertThat(calls).as(step.name() + " reached the store through the recording DataSource").isNotEmpty();
            assertThat(calls).as(step.name()).allSatisfy(call -> assertThat(call.writeLockHeld())
                    .as("write lock held at getConnection of " + step.name()).isEqualTo(step.mutation()));
            EVIDENCE.row("T-TX/V-21", step.method() + " " + step.name(),
                    step.mutation() ? "write lock held at every getConnection" : "no write lock at getConnection",
                    response.status() + " calls=" + calls.size() + " lockHeld="
                            + calls.stream().map(call -> String.valueOf(call.writeLockHeld())).toList());
        }
    }

    @Test
    void noRequestWaitsForTheConnectionTimeoutUnderConcurrentLoad_v21() throws Exception {
        int threads = 8;
        int rounds = 10;
        List<Integer> expected = List.of(201, 200, 200, 200, 200, 204);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<List<Integer>>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                String label = "w" + t;
                Callable<List<Integer>> worker = () -> {
                    List<Integer> statuses = new ArrayList<>();
                    for (int r = 0; r < rounds; r++) {
                        RawHttp.Response created = send("POST", "/todos", "{\"title\":\"" + label + "-" + r + "\"}");
                        String id = data(created).get("id").asString();
                        statuses.add(created.status());
                        statuses.add(send("PUT", "/todos/" + id, "{\"title\":\"" + label + "-changed\"}").status());
                        statuses.add(send("PATCH", "/todos/" + id + "/status", "{\"status\":\"Completed\"}").status());
                        statuses.add(send("GET", "/todos/" + id, null).status());
                        statuses.add(send("GET", "/todos", null).status());
                        statuses.add(send("DELETE", "/todos/" + id, null).status());
                    }
                    return statuses;
                };
                futures.add(pool.submit(worker));
            }
            long requests = 0;
            for (Future<List<Integer>> future : futures) {
                List<Integer> statuses = get(future);
                requests += statuses.size();
                for (int i = 0; i < statuses.size(); i++) {
                    assertThat(statuses.get(i)).as("request " + i).isEqualTo(expected.get(i % expected.size()));
                }
            }

            long slowest = CALLS.stream().mapToLong(Call::waitedMillis).max().orElseThrow();
            assertThat(CALLS).as("every request used the store").hasSizeGreaterThanOrEqualTo((int) requests);
            assertThat(slowest).as("the slowest getConnection, far below the connection timeout")
                    .isLessThan(PersistenceConfig.CONNECTION_TIMEOUT_MS / 2);
            assertThat(CALLS.stream().filter(call -> !call.writeLockHeld()).count())
                    .as("connections asked for without the lock are the reads only (2 of 6 requests)")
                    .isEqualTo(requests / 3);
            EVIDENCE.row("T-TX/V-21 load", threads + " threads x " + rounds + " rounds x 6 operations",
                    "all statuses as expected, slowest getConnection < " + PersistenceConfig.CONNECTION_TIMEOUT_MS / 2 + " ms",
                    requests + " requests, getConnection calls=" + CALLS.size() + ", slowest=" + slowest + " ms");
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private static <T> T get(Future<T> future) throws Exception {
        return future.get(120, TimeUnit.SECONDS);
    }

    // ---------------------------------------------------------------- one DataSource (V-20)

    @Test
    void exactlyOneDataSourceAndOneTransactionManagerExistAndTheRecorderIsTheOneTheStoreUses_v20() {
        assertThat(context.getBeansOfType(DataSource.class)).hasSize(1).allSatisfy(
                (name, source) -> assertThat(source).isInstanceOf(RecordingDataSource.class));
        assertThat(context.getBeansOfType(PlatformTransactionManager.class)).hasSize(1);

        createTask("through the recorder");

        assertThat(CALLS).as("a second DataSource would leave this empty").isNotEmpty();
    }

    // ---------------------------------------------------------------- AC-32: concurrent updates of one task

    @Test
    void twoConcurrentUpdatesOfOneTaskEndWithTheLaterWriteAndNeverAMix_ac32() throws Exception {
        String id = createTask("start");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 25; round++) {
                String bodyA = "{\"title\":\"A-" + round + "\",\"description\":\"dA-" + round + "\"}";
                String bodyB = "{\"title\":\"B-" + round + "\",\"description\":\"dB-" + round + "\"}";
                CyclicBarrier together = new CyclicBarrier(2);
                Future<RawHttp.Response> a = pool.submit(() -> {
                    together.await(10, TimeUnit.SECONDS);
                    return send("PUT", "/todos/" + id, bodyA);
                });
                Future<RawHttp.Response> b = pool.submit(() -> {
                    together.await(10, TimeUnit.SECONDS);
                    return send("PUT", "/todos/" + id, bodyB);
                });
                RawHttp.Response responseA = get(a);
                RawHttp.Response responseB = get(b);

                assertThat(responseA.status()).as(responseA.bodyText()).isEqualTo(200);
                assertThat(responseB.status()).as(responseB.bodyText()).isEqualTo(200);
                String updatedA = data(responseA).get("updatedAt").asString();
                String updatedB = data(responseB).get("updatedAt").asString();
                assertThat(updatedA).as("two serialised updates never share an updatedAt").isNotEqualTo(updatedB);

                RawHttp.Response stored = send("GET", "/todos/" + id, null);
                RawHttp.Response later = updatedA.compareTo(updatedB) > 0 ? responseA : responseB;
                assertThat(data(stored)).as("round " + round + ": the stored task is exactly the later write")
                        .isEqualTo(data(later));
            }
            EVIDENCE.row("T-TX/AC-32", "25 rounds of two concurrent PUTs (different title and description) on one task",
                    "both 200, distinct updatedAt, stored task equals the response with the larger updatedAt",
                    "25 of 25 rounds");
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
