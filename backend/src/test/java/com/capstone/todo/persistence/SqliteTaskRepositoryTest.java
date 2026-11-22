package com.capstone.todo.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.capstone.todo.config.TodoProperties.JournalMode;
import com.capstone.todo.domain.Task;
import com.capstone.todo.domain.TaskStatus;
import com.capstone.todo.support.TestDataDir;

/**
 * T-11 / U-REPO: {@link SqliteTaskRepository} on a real SQLite file in {@code TestDataDir} (AC-14, AC-22, AC-26,
 * AC-31, V-20, V-21, V-29, R-13). The pool is closed in {@code @AfterEach}; no file is deleted.
 *
 * <p>Source text stays ASCII: every special character is built from code point numbers.
 */
class SqliteTaskRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00.123Z");
    private static final List<String> EVIDENCE = new ArrayList<>();

    private final List<SqliteStore> opened = new ArrayList<>();

    @AfterEach
    void closeEveryPool() {
        opened.forEach(SqliteStore::close);
    }

    @AfterAll
    static void writeEvidence() throws IOException {
        Path out = TestDataDir.baseDirectory().getParent().resolve("t11-repo-evidence.txt");
        Files.write(out, EVIDENCE, StandardCharsets.UTF_8);
    }

    private SqliteStore store() {
        return store(JournalMode.DELETE);
    }

    private SqliteStore store(JournalMode mode) {
        SqliteStore store = SqliteStore.openInitialized(TestDataDir.newDataFile(SqliteTaskRepositoryTest.class), mode);
        opened.add(store);
        return store;
    }

    private SqliteStore reopen(SqliteStore closed, JournalMode mode) {
        SqliteStore store = SqliteStore.openInitialized(closed.file, mode);
        opened.add(store);
        return store;
    }

    private static Task task(String title, Instant createdAt) {
        return new Task(Task.UNASSIGNED_SEQ, UUID.randomUUID(), title, "", TaskStatus.PENDING, createdAt, createdAt);
    }

    private static Task task(UUID id, String title, String description, TaskStatus status, Instant createdAt,
            Instant updatedAt) {
        return new Task(Task.UNASSIGNED_SEQ, id, title, description, status, createdAt, updatedAt);
    }

    private static int count(SqliteStore store) {
        return store.jdbc.sql("SELECT count(*) FROM tasks").query(Integer.class).single();
    }

    // ---- insert and read ----

    @Test
    void anInsertedTaskIsReadBackWithEveryFieldAndASeqTheStoreAssigned() {
        SqliteStore store = store();
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-03-01T10:00:00.007Z");
        Instant updated = Instant.parse("2026-03-02T11:12:13.999Z");

        store.repository.insert(new Task(99, id, "Buy milk", "2 litres", TaskStatus.COMPLETED, created, updated));

        Task found = store.repository.findById(id).orElseThrow();
        assertThat(found.seq()).isEqualTo(1);
        assertThat(found.id()).isEqualTo(id);
        assertThat(found.title()).isEqualTo("Buy milk");
        assertThat(found.description()).isEqualTo("2 litres");
        assertThat(found.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(found.createdAt()).isEqualTo(created);
        assertThat(found.updatedAt()).isEqualTo(updated);
        assertThat(store.repository.findAllOrdered()).containsExactly(found);
    }

    @Test
    void anUnknownIdIsEmptyAndAnEmptyStoreListsNothing() {
        SqliteStore store = store();

        assertThat(store.repository.findById(UUID.randomUUID())).isEqualTo(Optional.empty());
        assertThat(store.repository.findAllOrdered()).isEmpty();
    }

    @Test
    void timestampsKeepMillisecondPrecisionAtTheEdges() {
        SqliteStore store = store();
        Task epoch = task("epoch", Instant.EPOCH);
        Task future = task("future", Instant.parse("2999-12-31T23:59:59.999Z"));

        store.repository.insert(epoch);
        store.repository.insert(future);

        assertThat(store.repository.findById(epoch.id()).orElseThrow().createdAt()).isEqualTo(Instant.EPOCH);
        assertThat(store.repository.findById(future.id()).orElseThrow().updatedAt())
                .isEqualTo(Instant.parse("2999-12-31T23:59:59.999Z"));
    }

    @Test
    void twoTasksWithTheSameTitleAndDescriptionAreBothStored() {
        SqliteStore store = store();

        store.repository.insert(task("same", T0));
        store.repository.insert(task("same", T0));

        assertThat(store.repository.findAllOrdered()).hasSize(2).extracting(Task::title).containsOnly("same");
    }

    @Test
    void aDuplicateIdIsRejectedAndNothingIsAdded() {
        SqliteStore store = store();
        Task first = task("first", T0);
        store.repository.insert(first);

        assertThatThrownBy(() -> store.repository.insert(task(first.id(), "again", "", TaskStatus.PENDING, T0, T0)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("UNIQUE");

        assertThat(count(store)).isEqualTo(1);
        assertThat(store.repository.findById(first.id()).orElseThrow().title()).isEqualTo("first");
    }

    // ---- ordering and seq (AC-26, D-8) ----

    @Test
    void tasksAreOrderedByCreatedAtNewestFirstAndTiesByTheLargerSeqFirst() {
        SqliteStore store = store();
        Task oldest = task("oldest", T0);
        Task tieA = task("tie A", T0.plusMillis(5));
        Task tieB = task("tie B", T0.plusMillis(5));
        Task tieC = task("tie C", T0.plusMillis(5));
        Task newest = task("newest", T0.plusSeconds(1));
        Task insertedLastButOld = task("inserted last, created earlier", T0.minusSeconds(60));
        for (Task task : List.of(oldest, tieA, newest, tieB, tieC, insertedLastButOld)) {
            store.repository.insert(task);
        }

        assertThat(store.repository.findAllOrdered()).extracting(Task::id).containsExactly(
                newest.id(), tieC.id(), tieB.id(), tieA.id(), oldest.id(), insertedLastButOld.id());
    }

    @Test
    void theOrderedQueryUsesTheIndexWithoutASortStep() {
        SqliteStore store = store();
        store.repository.insert(task("x", T0));

        List<String> plan = store.jdbc.sql("EXPLAIN QUERY PLAN SELECT seq, id FROM tasks"
                + " ORDER BY created_at DESC, seq DESC").query((rs, n) -> rs.getString("detail")).list();

        EVIDENCE.add("query plan: " + plan);
        assertThat(String.join(" | ", plan)).contains("idx_tasks_created_at_seq").doesNotContain("TEMP B-TREE");
    }

    @Test
    void aThousandTasksComeBackInOrder() {
        SqliteStore store = store();
        store.transaction.executeWithoutResult(status -> IntStream.range(0, 1_000)
                .forEach(i -> store.repository.insert(task("task " + i, T0.plusMillis(i / 3)))));

        List<Task> all = store.repository.findAllOrdered();

        assertThat(all).hasSize(1_000);
        for (int i = 1; i < all.size(); i++) {
            Task newer = all.get(i - 1);
            Task older = all.get(i);
            assertThat(newer.createdAt().isAfter(older.createdAt())
                    || (newer.createdAt().equals(older.createdAt()) && newer.seq() > older.seq())).isTrue();
        }
    }

    @Test
    void seqIsNeverReusedAfterADeleteOrAfterAReopen() {
        SqliteStore first = store();
        Task one = task("1", T0);
        Task two = task("2", T0);
        Task three = task("3", T0);
        for (Task task : List.of(one, two, three)) {
            first.repository.insert(task);
        }
        assertThat(first.repository.delete(three.id())).isTrue();
        assertThat(first.repository.delete(two.id())).isTrue();
        Task four = task("4", T0);
        first.repository.insert(four);
        assertThat(first.repository.findById(four.id()).orElseThrow().seq()).isEqualTo(4);
        assertThat(first.repository.delete(four.id())).isTrue();
        first.close();

        SqliteStore reopened = reopen(first, JournalMode.DELETE);
        Task five = task("5", T0);
        reopened.repository.insert(five);

        assertThat(reopened.repository.findById(five.id()).orElseThrow().seq()).isEqualTo(5);
        assertThat(reopened.repository.findById(one.id()).orElseThrow().seq()).isEqualTo(1);
    }

    @Test
    void seqContinuesEvenAfterEveryRowWasDeleted() {
        SqliteStore store = store();
        Task one = task("1", T0);
        store.repository.insert(one);
        store.repository.delete(one.id());
        Task two = task("2", T0);

        store.repository.insert(two);

        assertThat(store.repository.findById(two.id()).orElseThrow().seq()).isEqualTo(2);
    }

    // ---- update and delete ----

    @Test
    void anUpdateChangesTitleDescriptionStatusAndUpdatedAtOnly() {
        SqliteStore store = store();
        Task original = task(UUID.randomUUID(), "old", "old text", TaskStatus.PENDING, T0, T0);
        store.repository.insert(original);
        Task other = task("other", T0.plusSeconds(1));
        store.repository.insert(other);
        Instant later = T0.plusSeconds(30);

        // The values for seq and createdAt in the argument are ignored by design: the row keeps its own.
        boolean changed = store.repository.update(new Task(77, original.id(), "new", "new text", TaskStatus.COMPLETED,
                T0.minusSeconds(999), later));

        assertThat(changed).isTrue();
        Task stored = store.repository.findById(original.id()).orElseThrow();
        assertThat(stored.title()).isEqualTo("new");
        assertThat(stored.description()).isEqualTo("new text");
        assertThat(stored.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(stored.updatedAt()).isEqualTo(later);
        assertThat(stored.createdAt()).isEqualTo(T0);
        assertThat(stored.seq()).isEqualTo(1);
        assertThat(store.repository.findById(other.id()).orElseThrow().title()).isEqualTo("other");
    }

    @Test
    void anUpdateOfAnUnknownIdChangesNothingAndReturnsFalse() {
        SqliteStore store = store();
        store.repository.insert(task("kept", T0));

        boolean changed = store.repository.update(task(UUID.randomUUID(), "ghost", "", TaskStatus.PENDING, T0, T0));

        assertThat(changed).isFalse();
        assertThat(store.repository.findAllOrdered()).extracting(Task::title).containsExactly("kept");
    }

    @Test
    void aDeleteRemovesTheRowForGoodAndARepeatedDeleteReturnsFalse() {
        SqliteStore store = store();
        Task keep = task("keep", T0);
        Task gone = task("gone", T0);
        store.repository.insert(keep);
        store.repository.insert(gone);

        assertThat(store.repository.delete(gone.id())).isTrue();
        assertThat(store.repository.delete(gone.id())).isFalse();
        assertThat(store.repository.delete(UUID.randomUUID())).isFalse();

        assertThat(store.repository.findById(gone.id())).isEmpty();
        assertThat(count(store)).isEqualTo(1);
        assertThat(store.jdbc.sql("SELECT count(*) FROM tasks WHERE id = ?").param(gone.id().toString())
                .query(Integer.class).single()).isZero();
    }

    // ---- constraints ----

    @Test
    void theStatusColumnRejectsAnyValueButPendingAndCompleted() {
        SqliteStore store = store();
        String insert = "INSERT INTO tasks (id, title, description, status, created_at, updated_at)"
                + " VALUES (?, 't', '', ?, 0, 0)";

        for (String bad : new String[] {"pending", "COMPLETED", "Done", "", " Pending"}) {
            assertThatThrownBy(() -> store.jdbc.sql(insert).params(UUID.randomUUID().toString(), bad).update())
                    .as("status '%s'", bad).isInstanceOf(DataAccessException.class).hasMessageContaining("CHECK");
        }
        store.jdbc.sql(insert).params(UUID.randomUUID().toString(), "Pending").update();
        store.jdbc.sql(insert).params(UUID.randomUUID().toString(), "Completed").update();
        assertThat(count(store)).isEqualTo(2);
    }

    @Test
    void aNullInAnyRequiredColumnIsRejectedByTheSchema() {
        SqliteStore store = store();

        assertThatThrownBy(() -> store.jdbc.sql("INSERT INTO tasks (id, title, description, status, created_at,"
                + " updated_at) VALUES (?, NULL, '', 'Pending', 0, 0)").param(UUID.randomUUID().toString()).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("NOT NULL");
        assertThat(count(store)).isZero();
    }

    // ---- text round trip (R-13) ----

    private static String cp(int... codePoints) {
        return new String(codePoints, 0, codePoints.length);
    }

    private static String controls() {
        StringBuilder text = new StringBuilder();
        for (int c = 1; c < 32; c++) {
            text.appendCodePoint(c);
        }
        return text.toString();
    }

    static Stream<Arguments> texts() {
        String astral = cp(0x1F600);
        String nul = "a" + (char) 0 + "b";
        return Stream.of(
                Arguments.of("U+0000 in the middle", nul),
                Arguments.of("U+0000 first and last", (char) 0 + "x" + (char) 0),
                Arguments.of("only U+0000", String.valueOf((char) 0)),
                Arguments.of("two U+0000", "" + (char) 0 + (char) 0),
                Arguments.of("astral emoji", "grin " + astral + " done"),
                Arguments.of("astral at both ends", astral + "mid" + astral),
                Arguments.of("highest code point U+10FFFF", cp(0x10FFFF)),
                Arguments.of("lowest astral U+10000", cp(0x10000)),
                Arguments.of("U+0001 to U+001F controls", controls()),
                Arguments.of("non-ASCII BMP: Latin, CJK, Arabic, Hebrew",
                        cp(0x63, 0x61, 0x66, 0xE9, 0x20, 0x65E5, 0x672C, 0x8A9E, 0x20, 0x645, 0x631, 0x62D, 0x628,
                                0x627, 0x20, 0x5E9, 0x5DC, 0x5D5, 0x5DD)),
                Arguments.of("combining mark and ZWJ sequence",
                        cp(0x65, 0x301, 0x20, 0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467)),
                Arguments.of("U+FEFF, U+2028, U+2029, U+00A0",
                        cp(0xFEFF, 0x78, 0x2028, 0x79, 0x2029, 0x7A, 0xA0)),
                Arguments.of("SQL meta characters", "'); DROP TABLE tasks; --"),
                Arguments.of("quotes, percent, underscore, backslash, placeholder", "\" ' % _ \\ ? :name $1 {x}"),
                Arguments.of("newline, tab, carriage return", "a\nb\tc\r\nd"),
                Arguments.of("HTML and script", "<script>alert(1)</script> &amp; <b>x</b>"),
                Arguments.of("100 astral code points", astral.repeat(100)),
                Arguments.of("500 code points mixed with U+0000", ("a" + (char) 0 + astral + "e").repeat(125)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("texts")
    void textIsStoredAndReadBackExactlyAndAsStandardUtf8(String label, String text) {
        SqliteStore store = store();
        Task task = task(UUID.randomUUID(), text, text, TaskStatus.PENDING, T0, T0);

        store.repository.insert(task);

        Task byId = store.repository.findById(task.id()).orElseThrow();
        Task listed = store.repository.findAllOrdered().get(0);
        String expectedHex = HexFormat.of().withUpperCase().formatHex(text.getBytes(StandardCharsets.UTF_8));
        String storedHex = store.jdbc.sql("SELECT hex(CAST(title AS BLOB)) FROM tasks WHERE id = ?")
                .param(task.id().toString()).query(String.class).single();
        if (!text.equals(byId.title()) || !expectedHex.equals(storedHex)) {
            EVIDENCE.add("R-13 FAILED for '" + label + "': read back length " + byId.title().length() + " of "
                    + text.length() + ", stored hex " + abbreviate(storedHex) + ", expected hex "
                    + abbreviate(expectedHex));
        }
        assertThat(byId.title()).as("title by id").isEqualTo(text);
        assertThat(byId.description()).as("description by id").isEqualTo(text);
        assertThat(listed.title()).as("title in the list").isEqualTo(text);
        assertThat(storedHex).as("stored bytes are standard UTF-8").isEqualTo(expectedHex);
    }

    private static String abbreviate(String text) {
        return text.length() <= 80 ? text : text.substring(0, 80) + "...";
    }

    @Test
    void textWithAnEmbeddedNulIsFoundByIdAndUpdatedAndKeepsTheRestOfTheString() {
        SqliteStore store = store();
        String nul = "before" + (char) 0 + "after";
        Task task = task(UUID.randomUUID(), nul, "", TaskStatus.PENDING, T0, T0);
        store.repository.insert(task);

        store.repository.update(new Task(1, task.id(), nul + "!", nul, TaskStatus.PENDING, T0, T0.plusMillis(1)));

        Task stored = store.repository.findById(task.id()).orElseThrow();
        assertThat(stored.title()).isEqualTo(nul + "!");
        assertThat(stored.description()).isEqualTo(nul);
    }

    @Test
    void sqlMetaCharactersInTextNeverReachTheSqlStatement() {
        SqliteStore store = store();
        Task keep = task("keep", T0);
        store.repository.insert(keep);
        String attack = "x'); DELETE FROM tasks; --";
        Task hostile = task(UUID.randomUUID(), attack, attack, TaskStatus.PENDING, T0, T0);

        store.repository.insert(hostile);
        store.repository.update(new Task(0, hostile.id(), attack + "2", attack, TaskStatus.COMPLETED, T0, T0));

        assertThat(count(store)).isEqualTo(2);
        assertThat(store.repository.findById(keep.id())).isPresent();
        assertThat(store.repository.findById(hostile.id()).orElseThrow().title()).isEqualTo(attack + "2");
    }

    // ---- durability across a reopen (AC-14, AC-22) ----

    @ParameterizedTest(name = "{0}")
    @EnumSource(JournalMode.class)
    void everyCommittedTaskSurvivesClosingAndReopeningInEveryJournalMode(JournalMode mode) {
        SqliteStore first = store(mode);
        Task a = task("a", T0);
        Task b = task("b", T0.plusSeconds(1));
        first.repository.insert(a);
        first.repository.insert(b);
        first.repository.update(new Task(1, a.id(), "a2", "d", TaskStatus.COMPLETED, T0, T0.plusSeconds(5)));
        first.repository.delete(b.id());
        first.close();

        SqliteStore second = reopen(first, mode);

        assertThat(second.repository.findAllOrdered()).singleElement().satisfies(task -> {
            assertThat(task.id()).isEqualTo(a.id());
            assertThat(task.title()).isEqualTo("a2");
            assertThat(task.status()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(task.updatedAt()).isEqualTo(T0.plusSeconds(5));
        });
    }

    @Test
    void aDataFileWrittenInOneJournalModeOpensInAnotherAndKeepsItsData() {
        SqliteStore wal = store(JournalMode.WAL);
        Task kept = task("kept", T0);
        wal.repository.insert(kept);
        wal.close();

        SqliteStore asDelete = reopen(wal, JournalMode.DELETE);

        assertThat(asDelete.repository.findById(kept.id())).isPresent();
        assertThat(asDelete.jdbc.sql("PRAGMA journal_mode").query(String.class).single()).isEqualToIgnoringCase("delete");
    }

    // ---- transactions and the single connection (V-20, V-21) ----

    @Test
    void aTransactionThatEndsWithAnExceptionRollsEveryWriteBack() {
        SqliteStore store = store();
        Task existing = task("existing", T0);
        store.repository.insert(existing);

        assertThatThrownBy(() -> store.transaction.executeWithoutResult(status -> {
            store.repository.insert(task("new", T0));
            store.repository.update(new Task(1, existing.id(), "changed", "", TaskStatus.COMPLETED, T0, T0));
            assertThat(store.repository.findAllOrdered()).hasSize(2);
            throw new IllegalStateException("render failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(store.repository.findAllOrdered()).extracting(Task::title).containsExactly("existing");
        assertThat(store.repository.findById(existing.id()).orElseThrow().status()).isEqualTo(TaskStatus.PENDING);
        // whether the AUTOINCREMENT counter is rolled back with the insert is recorded, not assumed
        Task next = task("next", T0);
        store.repository.insert(next);
        EVIDENCE.add("seq after a rolled-back insert: " + store.repository.findById(next.id()).orElseThrow().seq());
    }

    @Test
    void aCommittedTransactionPersistsEveryWriteTogether() {
        SqliteStore store = store();

        store.transaction.executeWithoutResult(status -> {
            store.repository.insert(task("one", T0));
            store.repository.insert(task("two", T0.plusMillis(1)));
        });

        assertThat(store.repository.findAllOrdered()).extracting(Task::title).containsExactly("two", "one");
    }

    @Test
    void everyRepositoryCallInsideATransactionUsesThePoolsOnlyConnection() {
        SqliteStore store = store();
        Task task = task("t", T0);

        store.transaction.executeWithoutResult(status -> {
            ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager
                    .getResource(store.dataSource);
            Connection bound = holder.getConnection();
            store.repository.insert(task);
            store.repository.findById(task.id());
            store.repository.findAllOrdered();
            store.repository.update(new Task(1, task.id(), "u", "", TaskStatus.PENDING, T0, T0.plusMillis(1)));
            store.repository.delete(task.id());

            assertThat(DataSourceUtils.getConnection(store.dataSource)).isSameAs(bound);
            assertThat(store.dataSource.getHikariPoolMXBean().getTotalConnections()).isEqualTo(1);
            assertThat(store.dataSource.getHikariPoolMXBean().getActiveConnections()).isEqualTo(1);
            assertThat(store.dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
        });

        assertThat(store.dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
        assertThat(store.dataSource.getHikariPoolMXBean().getTotalConnections()).isEqualTo(1);
    }

    @Test
    void readsOutsideATransactionHandTheConnectionBack() {
        SqliteStore store = store();
        store.repository.insert(task("t", T0));

        for (int i = 0; i < 20; i++) {
            store.repository.findAllOrdered();
        }

        assertThat(store.dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
        assertThat(store.dataSource.getHikariPoolMXBean().getTotalConnections()).isEqualTo(1);
    }

    // ---- failures surface as DataAccessException ----

    @Test
    void aClosedPoolSurfacesAsADataAccessException() {
        SqliteStore store = store();
        store.close();

        assertThatThrownBy(() -> store.repository.findAllOrdered()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> store.repository.insert(task("x", T0))).isInstanceOf(DataAccessException.class);
    }

    @Test
    void aMissingTableSurfacesAsADataAccessException() {
        SqliteStore store = store();
        store.jdbc.sql("DROP TABLE tasks").update();

        assertThatThrownBy(() -> store.repository.insert(task("title that must not be echoed", T0)))
                .isInstanceOf(DataAccessException.class);
    }
}
