package com.capstone.todo.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.capstone.todo.domain.Task;
import com.capstone.todo.domain.TaskStatus;
import com.capstone.todo.support.InMemoryTaskRepository;
import com.capstone.todo.web.PrecedenceCases.Case;
import com.capstone.todo.web.PrecedenceSequencer.Result;
import com.capstone.todo.web.response.ErrorCode;

/**
 * CP-A: the error-precedence table at unit level (FR-12, AC-25, architecture 7.2 and 10.1). For every pair of stages
 * (i before j, P1 to P7) one request violates both, and the test asserts that i wins, that the stage-j-only variant
 * of the same request gives j (so the request really violates both), that no stage before P7 touched the repository,
 * and that the stored data is unchanged.
 *
 * <p>The requests go through {@link PrecedenceSequencer}, a test-only stand-in for the filter, controller and
 * service, over the real RouteTable, BodyGate and TodoRequestPipeline. The actual result of every row is written to
 * {@code target/cp-a-evidence.txt} after the run, so the table in the implementation report is copied from output,
 * not from the expectations.
 */
class PrecedenceTableTest {

    private static final UUID EXISTING = UUID.fromString(PrecedenceCases.EXISTING_ID);
    private static final Instant THEN = Instant.parse("2026-01-01T00:00:00Z");

    private static final List<String> EVIDENCE = Collections.synchronizedList(new ArrayList<>());

    private static InMemoryTaskRepository seededRepository() {
        InMemoryTaskRepository repository = new InMemoryTaskRepository();
        Task task = new Task(1, EXISTING, "Existing", "", TaskStatus.PENDING, THEN, THEN);
        repository.restore(new InMemoryTaskRepository.Snapshot(List.of(task), 2));
        return repository;
    }

    static Stream<Arguments> pairRows() {
        return PrecedenceCases.pairs().stream().map(row -> Arguments.of(row.pair(), row));
    }

    static Stream<Arguments> extraRows() {
        return PrecedenceCases.extras().stream().map(row -> Arguments.of(row.pair(), row));
    }

    private static String describe(Result result) {
        return result.status() + " " + result.code() + (result.allow() == null ? "" : " Allow: " + result.allow())
                + ", " + result.repositoryCalls() + " repository call" + (result.repositoryCalls() == 1 ? "" : "s");
    }

    private static int statusOf(String code) {
        return "OK".equals(code) ? 200 : ErrorCode.valueOf(code).status();
    }

    private static void assertResult(Result actual, String code, String allow, int calls) {
        assertThat(actual.code()).isEqualTo(code);
        assertThat(actual.status()).isEqualTo(statusOf(code));
        assertThat(actual.allow()).isEqualTo(allow);
        assertThat(actual.repositoryCalls()).isEqualTo(calls);
    }

    private static void runRow(String testName, Case row) {
        InMemoryTaskRepository repository = seededRepository();
        Result actual = new PrecedenceSequencer(repository).run(row.request().toRequest());

        String later = "";
        Result laterActual = null;
        InMemoryTaskRepository laterRepository = null;
        if (row.laterOnly() != null) {
            laterRepository = seededRepository();
            laterActual = new PrecedenceSequencer(laterRepository).run(row.laterOnly().toRequest());
            later = row.laterOnly().describe() + " -> " + describe(laterActual);
        }
        EVIDENCE.add(String.join(" | ", row.pair(), row.request().describe(), row.expected(), describe(actual),
                later.isEmpty() ? "-" : later, testName));

        assertResult(actual, row.expected(), row.allow(), row.repositoryCalls());
        if (row.laterOnly() != null) {
            assertResult(laterActual, row.laterExpected(), null, row.laterCalls());
        }
        // No stage before P7 reached the store, and nothing was stored or changed (the delete row aside, which is
        // a success and removes the seeded task).
        if (!"OK".equals(row.expected())) {
            assertThat(repository.contents()).isEqualTo(seededRepository().contents());
            assertThat(repository.callLog()).hasSizeLessThanOrEqualTo(1);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pairRows")
    void theEarlierStageWinsAndNothingIsStored(String pair, Case row) {
        runRow("PrecedenceTableTest.theEarlierStageWinsAndNothingIsStored[" + pair + "]", row);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("extraRows")
    void requestsWithThreeOrMoreViolationsOrNoneBehaveAsDocumented(String pair, Case row) {
        runRow("PrecedenceTableTest.requestsWithThreeOrMoreViolationsOrNoneBehaveAsDocumented[" + pair + "]", row);
    }

    // ---- the table itself is complete and consistent ----------------------------------------------------------

    private static Set<String> codesOf(int stage) {
        return switch (stage) {
            case 1 -> Set.of("NOT_FOUND", "METHOD_NOT_ALLOWED");
            case 2 -> Set.of("PAYLOAD_TOO_LARGE");
            case 3 -> Set.of("UNSUPPORTED_MEDIA_TYPE");
            case 4 -> Set.of("INVALID_ID");
            case 5 -> Set.of("INVALID_REQUEST_BODY");
            case 6 -> Set.of("VALIDATION_ERROR");
            case 7 -> Set.of("TODO_NOT_FOUND");
            default -> throw new IllegalArgumentException("no such stage " + stage);
        };
    }

    @Test
    void thereIsExactlyOneRowForEveryPairOfStagesAndItNamesTheEarlierStageAsWinner() {
        Set<String> seen = new HashSet<>();
        for (Case row : PrecedenceCases.pairs()) {
            assertThat(row.pair()).matches("P[1-7]>P[1-7]");
            int earlier = row.pair().charAt(1) - '0';
            int later = row.pair().charAt(4) - '0';
            assertThat(earlier).isLessThan(later);
            assertThat(seen.add(row.pair())).as("duplicate row " + row.pair()).isTrue();
            assertThat(codesOf(earlier)).contains(row.expected());
            assertThat(row.laterOnly()).as("a stage-j-only variant for " + row.pair()).isNotNull();
            assertThat(codesOf(later)).contains(row.laterExpected());
            assertThat(row.repositoryCalls()).as("a P1-P6 winner never reads storage").isZero();
        }
        assertThat(seen).hasSize(21);
    }

    // ---- the two examples AC-25 gives in words ------------------------------------------------------------------

    @Test
    void anInvalidIdWithAnInvalidBodyGivesInvalidId() {
        InMemoryTaskRepository repository = seededRepository();

        Result result = new PrecedenceSequencer(repository).run(
                PrecedenceCases.Shape.of("PUT", "/todos/" + PrecedenceCases.BAD_ID)
                        .types("application/json").body("{bad").toRequest());

        assertResult(result, "INVALID_ID", null, 0);
    }

    @Test
    void anInvalidBodyWithAWellFormedMissingIdGivesInvalidRequestBodyAndP7IsNotReached() {
        InMemoryTaskRepository repository = seededRepository();

        Result result = new PrecedenceSequencer(repository).run(
                PrecedenceCases.Shape.of("PUT", "/todos/" + PrecedenceCases.MISSING_ID)
                        .types("application/json").body("{bad").toRequest());

        assertResult(result, "INVALID_REQUEST_BODY", null, 0);
        assertThat(repository.callLog()).isEmpty();
    }

    @AfterAll
    static void writeTheEvidenceFile() throws IOException {
        Path file = Path.of("target", "cp-a-evidence.txt");
        Files.createDirectories(file.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("pair | request | expected winner | actual | stage-j-only variant -> actual | test");
        lines.addAll(EVIDENCE);
        Files.write(file, lines, StandardCharsets.UTF_8);
    }
}
