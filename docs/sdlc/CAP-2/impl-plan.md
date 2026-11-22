# Implementation plan - CAP-2: Todo App - Backend: Task management REST APIs

Status: FOR APPROVAL (G3; kickoff gate G4 covers this file and `branch-plan.md`)
Inputs: requirements.md (G1), architecture.md and design-review.md (G2, round 2 APPROVE), repo-profile.md (attempt 3),
clarifications.md (Clar. #1-#22, S2-1..S2-4, S2-ENV, S3-1..S3-3), approvals.md (G1, G2).

Everything about Spring, Jackson, Tomcat, SQLite, Maven, Surefire/Failsafe and JDK 25 is the architecture's understanding of
public behaviour and is **UNVERIFIED** until the named test or build output proves it (architecture.md 14.1, V-1..V-30).
No build or test command has ever run here; every command is **PLANNED** (architecture.md 10.2) with no file provenance
(repo-profile.md section 3). The planner has no shell and no web access and executed or looked up nothing.

## 1. Overview
Scope: implement the approved CAP-2 backend in one Maven module `backend/` (Java, Spring Boot 4.1.x provisional, Spring MVC
on embedded Tomcat, SQLite via sqlite-jdbc and Spring JDBC): six operations, one error catalog, precedence pipeline P1-P7
(`ApiGatewayFilter` P1-P3, `TodoRequestPipeline` P4-P6, `TaskService` P7), and the section 10 test suites. No framework
file is touched.

Size: **17 tasks**: P0 6, P1 10, P2 1; size S 3, M 14, L 0. **8 waves**. 3 reviewer checkpoints on the error-precedence
pipeline (CP-A, CP-B, CP-C). No task is blocked by an open requirement question; **every task is blocked at start by the
human actions in 1.2 and 1.3**. Coverage: 13/13 FR, 10/10 NFR, 36/36 AC (section 5).

### 1.1 Spring Boot line applied (DR-20, S3-1)
- Human decision (S3-1, G2): Boot 4.1.x, `--release 21`, compiled on the installed JDK 25; 4.0.x is the fallback if 4.1.x
  fails the selection rule (architecture.md 4.1): newest 4.x minor that (1) has had a patch release after `.0`, (2) has 6+
  months of open-source support left, (3) lists Java 25.
- **Line applied by this plan: 4.1.x, PROVISIONAL.** Criteria (1), (2), (3) are UNVERIFIED (no web access, no Maven run).
  No exact patch version is invented here.
- Tie-break (DR-20): (2) is measured on the **date the human approves G4**. If no 4.x line meets all three, take the newest
  line meeting (1) and (3), record its support end date and show it to the human. If 4.1.x fails (1) or (3) use 4.0.x; if
  both fail return BLOCKED.
- The Orchestrator shows "4.1.x provisional" at G4 and obtains the exact patch from the human (Spring Boot project page).
  T-1 writes it into the POM, T-2 records the outcome. Same for sqlite-jdbc: latest 3.x, pinned, version recorded in the report.

### 1.2 Maven and network permission (G2-7 NOT GRANTED)
- **Permission for agents to run `mvn` or `./mvnw` and to download from Maven Central has NOT been granted** (`approvals.md`
  G2: "NOT approved or implied"; `.claude/settings.json:44-48` allows read-only git only). S2-ENV (Maven 3.10.1, JDK
  25.0.5.1) is a human report, not permission. **It must be obtained from the human before Stage 5 starts.** Only the human edits
  `.claude/settings.json`; no agent message authorises it.
- The developer agent never runs installs or network commands and only runs commands with profile provenance; the profile
  lists Maven as PLANNED. So the G4 record in `approvals.md` must name the commands in 1.2.1 as authorised and accept
  Maven Central downloads, and the Orchestrator's delegation message must repeat it.
- Tasks needing Maven and network: T-1 (wrapper, first `./mvnw -v` fetches the Maven distribution) and T-2..T-17 (compile,
  dependency download, test, package). Without permission Stage 5 may write code but must report each check `NOT_RUN`.

| 1.2.1 Commands to authorise (PLANNED, run in `backend/`) | First used |
|---|---|
| `mvn -v` (before the wrapper exists) | T-1 |
| `mvn wrapper:wrapper` (G2-8: human-run, or one explicitly approved agent command; flags confirmed then, V-30) | T-1 |
| `./mvnw -v` (Git Bash) / `mvnw.cmd -v` | T-1 |
| `./mvnw -B test`, `./mvnw -B test -Dtest=<ClassName>` | T-2 on |
| `./mvnw -B verify` | T-14, T-17 |
| `./mvnw -B verify -Pperformance` | T-16 |
| `./mvnw spring-boot:run` (manual smoke) | T-17 |

Not authorised, not planned: `clean` (deletes `target/`, needs its own approval), global installs, lint, format, audit.

### 1.3 Branch needs: what the pr-manager `prepare` step must do (for human approval at G4)
Facts read: branch `Main` matches protected glob `main` (`.claude/hooks/protected-branches.txt:3`, case-insensitive); no
commits (repo-profile.md section 1); all top-level entries untracked; `.git/config` has remote `origin` (URL not read);
what the remote holds is **UNKNOWN**. Hook limits (`pre-implementation-check.sh`): `git add`, `commit`, `push` denied to every
agent before Stage 8; `git config` and remote edits denied; branch creation only by the pr-manager in stages 4, 5, 8;
developer edits need G1-G4 valid and the current branch equal to the recorded, non-protected workflow branch. So **the base
commit on `Main` can only be made by the human**; this plan does not do it.

| # | Step | Who | Notes |
|---|---|---|---|
| B-1 | Decide base commit content. Default: framework only (`CLAUDE.md`, `.gitignore`, `.mcp.json.example`, `.claude/` except gitignored `settings.local.json`, `docs/framework/`, `scripts/`, `tests/`); `docs/sdlc/CAP-2/` stays untracked and goes in the feature branch PR; `docs/codemie/analytics/*.json` (not read, may hold personal data) not committed unless the human says so | human | `prepare` returns NEEDS_APPROVAL/BLOCKED on a repo with no commits |
| B-2 | Create the base commit on `Main` | human | agents may not commit on a protected branch |
| B-3 | Check remote default branch name (`main` vs local `Main`) and whether it is empty (pr-manager: `get_me`, `list_branches`, `get_file_contents`). If no remote `main` or unrelated history, the human pushes the base so a PR base with shared history exists | human, then pr-manager reads | UNKNOWN today |
| B-4 | `prepare`: propose branch name, default `feature/CAP-2-todo-backend-task-apis` (lowercase, under 60 chars, not protected, not existing locally or remotely) | pr-manager | no naming history |
| B-5 | `prepare`: after approval `git switch -c <name>` from the base commit; no commit | pr-manager | never on `Main` |
| B-6 | `prepare`: write `docs/sdlc/CAP-2/branch-plan.md` (Branches, Commit strategy, Files). Proposed: message `CAP-2: <imperative summary>`, one commit per wave of section 3, `docs/sdlc/CAP-2/` in the same PR, exclude `.sdlc/`, `backend/target/`, `backend/data/`, secrets; stage by explicit path; commits happen only in Stage 8 after G7 | pr-manager | strategy only |
| B-7 | Orchestrator records the branch in workflow state; G4 covers `impl-plan.md` and `branch-plan.md` (`gates.conf:9`) | Orchestrator, human | developer edits blocked until then |

### 1.4 Carried items (S3-3, S3-2) and where they live
| Item | Task / test |
|---|---|
| DR-16: never `sendError`; boundary-only mode short-circuits non-route methods; T-ERR TRACE case | T-12 code, unit tests, source scan; T-15 contract case |
| DR-17 / G2-5 (h): unmapped container status becomes 500 INTERNAL_ERROR, logged once | T-13 code; T-15 T-ERR asserts one unmapped 4xx and one unmapped 5xx |
| DR-18 / G2-5 (b): member-name limit 65,536, document length and token count explicit, number length 1,000 | T-4 mapper; T-7 U-JSON (60,000-char member name); T-15 T-P5, T-B1 |
| DR-19: container-added filters outside T-RT; nested ERROR dispatch | T-12 (override, V-9, V-10, WsFilter check); T-13 T-RT (a) |
| DR-20: tie-break and concrete line | 1.1, T-1, T-2 |
| DR-21: Failsafe `classesDirectory`, `finalName`, executions, `*IT` naming | T-1 skeleton, T-14 proof (V-28) |

## 2. Tasks
Shorthand: `M/` = `backend/src/main/java/com/capstone/todo/`, `T/` = `backend/src/test/java/com/capstone/todo/`,
`R/` = `backend/src/main/resources/`. Nothing exists under `backend/` today (Glob), so every path is new. All test commands
are PLANNED. All tasks: Blocked = yes until 1.2 and 1.3 are done (stated once here, not repeated).

### T-1: Maven POM, wrapper and toolchain check
- Description, steps in order. (1) First permitted step: record `mvn -v` verbatim (S2-ENV is unverified; also whether `JAVA_HOME`
  is JDK 25 for the agent shell). (2) Write `backend/pom.xml`: parent `spring-boot-starter-parent` at the 1.1 line and patch,
  `com.capstone` / `todo-backend`, `java.version` 21 (compiler-release mapping unverified, V-30; else set
  `maven.compiler.release`), dependencies `spring-boot-starter-web`, `spring-boot-starter-jdbc`, pinned `sqlite-jdbc`,
  `spring-boot-starter-test` (test). No validation, security, actuator, devtools, JPA, Flyway. Plugins: Boot plugin and
  Surefire with `--enable-native-access=ALL-UNNAMED`, Surefire `excludedGroups` `performance`, Failsafe skeleton with
  `integration-test` and `verify` executions bound, `*IT` naming, `classesDirectory` and the JAR name passed as a system
  property (DR-21 a-d, names unverified), profile `performance`. (3) Write `backend/.gitignore` (`target/`, `data/`).
  (4) **HUMAN PAUSE (G2-8):** the developer returns NEEDS_APPROVAL; the human (or one explicitly approved agent command)
  runs `mvn wrapper:wrapper` once in `backend/`; network needed; whether it needs a POM is unverified (V-30). (5) Record `./mvnw -v`.
- Satisfies: enabling; primary NFR-4, NFR-5, NFR-9, NFR-1 (dependency choices that keep them achievable)
- Depends on: kickoff 1.2, 1.3, exact Boot patch from the human
- Priority / size: P0 / M
- Affected files: create `backend/pom.xml`, `backend/.gitignore`, `backend/mvnw`, `backend/mvnw.cmd`,
  `backend/.mvn/wrapper/maven-wrapper.properties` (last three generated; list unverified)
- Acceptance criteria:
  - [ ] `mvn -v` and `./mvnw -v` outputs pasted verbatim in `implementation-report.md`, differences from S2-ENV stated.
  - [ ] `maven-wrapper.properties` pins the Maven version `mvn -v` reported.
  - [ ] Dependencies are exactly those listed; each is in the kickoff table (section 4). No `spring.datasource.*` anywhere.
  - [ ] If Boot 4.x or JDK 25 is unsupported (V-26) the developer returns BLOCKED naming it; the target is not changed silently.
- Test requirements: integration: `./mvnw -v`, then `./mvnw -B test` exits 0 once T-3 exists. Failure cases: no network or
  proxy (R-11) -> `NOT_RUN` plus NEEDS_APPROVAL; wrapper command fails -> report output, do not hand-write the wrapper without approval.
- Documentation: report section "Toolchain".

### T-2: Boot line resolution record and build-level V-items
- Description: apply the 1.1 rule and record, in `implementation-report.md` section "Version resolution": line, exact patch,
  versions Maven actually resolved (Tomcat, Jackson, HikariCP, JUnit, Spring Framework), support evidence the human supplied
  for criteria (1)-(3) (else `UNVERIFIED`), the G4 date, JNI / `Unsafe` / agent warnings on JDK 25 (V-26, V-27), whether
  `java.version` set the compiler release (V-30), JUnit generation (V-25). Add a table of the real property, class and
  Jackson feature names that T-3..T-15 must use, where they differ from architecture.md 7.5 and D-14. If the rule fails,
  switch to 4.0.x (one POM property), re-run, record why.
- Satisfies: enabling for FR-11, FR-12, NFR-1; records V-25, V-26, V-27, V-30
- Depends on: T-1
- Priority / size: P0 / S
- Affected files: create `T/BuildSanityTest.java`
- Acceptance criteria:
  - [ ] Each of V-25, V-26, V-27, V-30 has an outcome row (confirmed / differs / not observable) with an output excerpt.
  - [ ] Every criterion not supplied by the human is marked UNVERIFIED; every name difference lists affected tasks.
- Test requirements: unit: `BuildSanityTest` asserts the running JVM feature version (reported, expected 25) and passes under Surefire. Stderr
  warnings are recorded, never asserted empty.
- Documentation: report only.

### T-3: Application skeleton, properties and test support
- Description: `TodoApplication`; `TodoProperties` (`todo.data-file` default `./data/todo.db` resolved to an absolute path;
  `todo.store.journal-mode` default `DELETE`, allowed `DELETE`/`TRUNCATE`/`WAL`; `synchronous` fixed `FULL`, not
  configurable); `application.properties` with every key of architecture.md 7.5 (names per T-2) and no secrets, no
  `spring.datasource.*`. Test support in `T/support/`: `TestDataDir` (fresh directory per context under
  `backend/target/test-data/<class>-<random>/`, never deleted), raw-socket HTTP helper (exact headers, no or two Content-Type,
  chunked, `Expect: 100-continue`, `%2F`, HEAD, TRACE, lowercase method), HTTP/1.1 `HttpClient` helper,
  `FailingTaskRepository`, `InMemoryTaskRepository`, fixed `Clock`, scripted `IdGenerator`, and a test-only
  `@TestConfiguration` (throwing filter ordered after C13, `sendError` endpoint) imported only by T-ERR and T-LOG, never T-RT.
  Hand-written fakes, no Mockito.
- Satisfies: FR-10, NFR-1, NFR-9, NFR-7 (startup log in T-11)
- Depends on: T-1 (T-2 for names)
- Priority / size: P0 / M
- Affected files: create `M/TodoApplication.java`, `M/config/TodoProperties.java`, `R/application.properties`,
  `T/support/{TestDataDir,RawHttp,TestClients,FailingTaskRepository,InMemoryTaskRepository,FixedClock,ScriptedIds}.java`,
  `T/support/hooks/TestHooksConfig.java`, `T/config/TodoPropertiesTest.java`, `T/support/TestDataDirTest.java`
- Acceptance criteria:
  - [ ] Every planned key is present; none of `spring.datasource.*`; unknown journal mode fails startup with a message free of secrets.
  - [ ] `TestDataDir` yields a distinct directory per context and deletes nothing.
- Test requirements: unit: defaults, absolute-path resolution, journal mode accept/reject, `TODO_DATA_FILE` relaxed binding
  (recorded if the pinned line differs). Edge: empty or relative path, missing parent directory (created).
- Documentation: none.

### T-4: Error catalog, response writer and dedicated JSON mapper (C12, JsonConfig)
- Description: `ErrorCode` (nine codes of Appendix A.4: code, status, default message), `ApiException`, `ApiResponses`
  (`{data}`/`{error}` bytes; `details` only on VALIDATION_ERROR; precomputed fixed bodies for the filter and the error
  controller), `TaskJson` (field order id, title, description, status, createdAt, updatedAt; pattern
  `uuuu-MM-dd'T'HH:mm:ss.SSS'Z'`, `Locale.ROOT`, UTC; `description` always present; `seq` never emitted). `JsonConfig`: `apiJsonMapper`
  with every read feature explicit (D-14): comments, single quotes, unquoted names, trailing commas, NaN, leading zeros,
  control characters off; duplicates last-wins; trailing tokens fail; limits nesting 1,000, number length 1,000, string
  length 65,536, **member-name length 65,536, document length and token count explicit (DR-18, S3-2)**. Output: no HTML or
  non-ASCII escaping (D-15). Jackson names per T-2 (V-22, V-23).
- Satisfies: FR-9, NFR-1, NFR-2, NFR-10, AC-12, AC-13, AC-19, AC-20, AC-21, AC-27, AC-33
- Depends on: T-2, T-3
- Priority / size: P1 / M
- Affected files: create `M/web/response/{ErrorCode,ApiException,ApiResponses,TaskJson}.java`, `M/config/JsonConfig.java`,
  `T/web/response/{ApiResponsesTest,TaskJsonTest,ErrorCodeTest}.java`, `T/config/JsonConfigTest.java`
- Acceptance criteria:
  - [ ] Each code has the Appendix A.4 status; golden strings for all error bodies; fixed bodies equal dynamic output.
  - [ ] A timestamp with 0 ms prints `.000`; default locale `en_IN` and zone Asia/Calcutta do not change output.
  - [ ] `<b>&"'</b>`, non-ASCII and astral characters are written verbatim (AC-33).
  - [ ] The test lists every mapper read feature and limit with its expected value.
- Test requirements: unit U-RESP and the writer side of U-JSON (locale and zone changed in the test and restored in `finally`).
  Edge: empty list `{"data":[]}`; escaped U+0000 round-trip text.
- Documentation: none.

### T-5: Domain model, repository port, clock and id generator
- Description: `Task`, `TaskStatus` (exact `Pending`/`Completed`, no coercion), `CreateTask`, `ReplaceTask`, `SetStatus`,
  `TaskNotFoundException`, `TaskRepository` (`findAllOrdered`, `findById`, `insert`, `update`, `delete`), `Clock` bean,
  `IdGenerator` (default `UUID.randomUUID`, lowercase). `domain` imports nothing from `web` or `persistence`; no `@Transactional`.
- Satisfies: FR-1, FR-2, NFR-3
- Depends on: T-3
- Priority / size: P1 / S
- Affected files: create `M/domain/{Task,TaskStatus,CreateTask,ReplaceTask,SetStatus,TaskNotFoundException,TaskRepository}.java`,
  `M/domain/support/{IdGenerator,UuidIdGenerator}.java`, `M/config/ClockConfig.java`, `T/domain/ArchitectureRulesTest.java`
- Acceptance criteria:
  - [ ] U-ARCH passes: no `@Transactional` in `domain`; no `domain` signature uses a `web` or `persistence` type.
  - [ ] Default ids are lowercase canonical UUIDs.
- Test requirements: unit U-ARCH (class scanning; also proves it fails on a bad fixture in test scope). Edge: status parse rejects `pending`.
- Documentation: none.

### T-6: Unicode text rules and request validators (C7)
- Description: `UnicodeText` (trim of the 25 White_Space code points of D-11, never `String.strip`; `codePointCount`), and
  `TaskRequestValidator` over the Jackson tree per 7.4 (title, description, status; type errors; all failures collected in order
  title, description, status; absent/null description becomes ""; unknown members never read; CAP-1 wording).
- Satisfies: FR-1, FR-4, FR-6, FR-7, FR-8, NFR-10, AC-2, AC-4, AC-8, AC-11, AC-18, AC-20, AC-21, AC-23, AC-28, AC-29, AC-30, AC-33
- Depends on: T-4, T-5
- Priority / size: P1 / M
- Affected files: create `M/validation/{UnicodeText,TaskRequestValidator}.java`, `T/validation/{UnicodeTextTest,TaskRequestValidatorTest}.java`
- Acceptance criteria:
  - [ ] U+00A0, U+2007 behaviour, U+3000, U+0085 trimmed as D-11 lists; U+FEFF and U+001C kept; astral characters counted as one.
  - [ ] Title 100 accepted, 101 rejected; description 500 / 501; whitespace-only description becomes "".
  - [ ] `{}`, wrong types and mixed failures give every detail together with `field` equal to the property name.
  - [ ] id, createdAt, updatedAt, status on POST/PUT and unknown members never cause an error.
- Test requirements: unit U-TXT and validator tables per field and per operation. Edge: number, boolean, array, object as title; `null` title; `"Pending"` vs `"pending"`.
- Documentation: none.

### T-7: Id parser and strict JSON body reader (C5, C6)
- Description: `IdParser` (D-13: one strict percent-decode of the raw segment, `+` literal, UTF-8 validated, regex, lowercase;
  `UUID.fromString` is not the check) and `JsonBodyReader` (strict UTF-8 `REPORT`, explicit U+FEFF check, `readTree`, no trailing
  token, object root, post-parse unpaired-surrogate scan of every string and member name, limits per T-4; every Jackson
  exception becomes INVALID_REQUEST_BODY and is not logged).
- Satisfies: FR-3, FR-7, FR-8, AC-3, AC-6, AC-15, AC-17, AC-18, AC-23
- Depends on: T-4
- Priority / size: P1 / M
- Affected files: create `M/web/request/{IdParser,JsonBodyReader}.java`, `T/web/request/{IdParserTest,JsonBodyReaderTest}.java`
- Acceptance criteria:
  - [ ] `%61` accepted and lowercased; `%2530` INVALID_ID; `%zz`, `%FF`, `1-1-1-1-1`, non-hex 36 chars INVALID_ID; uppercase accepted.
  - [ ] BOM, trailing token, empty or whitespace-only body, array/string/number/null root, invalid UTF-8, `"\ud800"` in an ignored member all give INVALID_REQUEST_BODY.
  - [ ] Duplicate keys resolve last-wins; 1,000 digits and nesting 1,000 pass; 1,001 digits and nesting 1,001 fail; a **60,000-character member name passes** (DR-18).
- Test requirements: unit U-ID and U-JSON tables at and over each boundary (V-22, V-24). Edge: lone low surrogate in a member name.
- Documentation: none.

### T-8: Route table, route constants and body gate (C3, C4, ApiRoutes)
- Description: `ApiRoutes` constants (`/todos`, `/todos/{id}`, `/todos/{id}/status`), `RouteTable` (exact match on the raw URI,
  query ignored; trailing slash, `//`, `;param`, encoded literals give no match; route `Allow` values; any other method token incl.
  HEAD, OPTIONS, TRACE, lowercase gives 405), `RouteMatch(route, rawIdSegment)`, `BodyGate` (POST/PUT/PATCH only: Content-Length
  over 65,536 gives 413 unread; else counted read capped at 65,537; body present when Content-Length above 0 or at least one byte;
  Content-Type via `MediaType.parseMediaType`, exactly one header, `application/json` case-insensitive, parameters ignored, else
  415; `RawBody(bytes, present)`).
- Satisfies: FR-11, FR-12, FR-13, NFR-6, AC-16, AC-24, AC-25
- Depends on: T-3, T-4
- Priority / size: P1 / M
- Affected files: create `M/web/ApiRoutes.java`, `M/web/gate/{RouteTable,RouteMatch,BodyGate,RawBody}.java`,
  `T/web/gate/{RouteTableTest,BodyGateTest}.java` (BodyGate with `MockHttpServletRequest`)
- Acceptance criteria:
  - [ ] Every row of T-P1a and T-P1b yields the expected 404 or 405 with the route's `Allow` at unit level.
  - [ ] 65,536 bytes accepted, 65,537 rejected (Content-Length and chunked); GET/DELETE bodies never read.
  - [ ] Two Content-Type headers, none, unparsable, `*+json`, form, multipart give 415 only when a body is present; no body skips P3.
- Test requirements: unit tables RouteTable and BodyGate. Edge: `Content-Length: 0` with no type -> not 415 (A-11).
- Documentation: none.

### T-9: Request pipeline P4-P6 and the precedence table (CP-A)
- Description: `TodoRequestPipeline` with `create`, `replace`, `setStatus`, `id`: each runs P4 then P5 then P6 in that fixed order
  and returns a typed command; the one place P4-P6 are ordered; no storage access.
- Satisfies: FR-12, FR-7, NFR-3, AC-25
- Depends on: T-6, T-7, T-8
- Priority / size: P0 / M
- Affected files: create `M/web/request/TodoRequestPipeline.java`, `T/web/request/TodoRequestPipelineTest.java`
- Acceptance criteria:
  - [ ] **CP-A:** a table-driven test has one case per pair of stages (i before j) that one request can violate (P1..P7), asserting i wins; the
        report lists the table so a reviewer can check it before any server wiring exists.
  - [ ] An invalid id with an invalid body gives INVALID_ID; invalid body with a missing id gives INVALID_REQUEST_BODY; the repository fake records zero calls for any P1-P6 failure.
- Test requirements: unit with `InMemoryTaskRepository` call counter. Edge: PATCH with `{}`.
- Documentation: none.

### T-10: Task service (C8)
- Description: create, list, get, replace, setStatus, delete; one fair `ReentrantLock` taken before the transaction; a
  `TransactionTemplate` (never `@Transactional`) runs load, compute, save and the render function (D-23) before commit;
  `updatedAt = max(now, previous + 1 ms)` including no-op PUT/PATCH; Pending default; description normalisation;
  `TaskNotFoundException` is P7.
- Satisfies: FR-1..FR-6, NFR-3, AC-1, AC-4, AC-5, AC-6, AC-7, AC-8, AC-9, AC-10, AC-11, AC-27, AC-29, AC-31, AC-32
- Depends on: T-5 (T-4 for render type use only through a `Function`)
- Priority / size: P1 / M
- Affected files: create `M/domain/TaskService.java`, `T/domain/TaskServiceTest.java`
- Acceptance criteria:
  - [ ] updatedAt strictly increases across ten no-op PUTs with a frozen clock; createdAt never changes.
  - [ ] A render failure rolls back; the fake store is unchanged. Repeat DELETE gives TODO_NOT_FOUND.
  - [ ] The lock is held before any repository call (the test records lock state at each call).
- Test requirements: unit with a hand-written transaction manager fake and `InMemoryTaskRepository`. Edge: equal-createdAt tasks, two updates in sequence last wins.
- Documentation: none.

### T-11: SQLite persistence (C10, PersistenceConfig)
- Description: `PersistenceConfig` (D-22: one `HikariDataSource` built in code over `SQLiteDataSource` with `SQLiteConfig`, pool 1, connection
  timeout 5,000 ms, busy timeout 5 s; one `DataSourceTransactionManager`; `TransactionTemplate`; `JdbcClient`), `SchemaInitializer`
  (`user_version` 0 creates and sets 1, 1 is OK, other fails startup; reads back `journal_mode` and `synchronous` and fails if different; INFO line with absolute path and journal mode),
  `SqliteTaskRepository` (parameterised SQL; `ORDER BY created_at DESC, seq DESC`; hard delete; index; CHECK on status).
- Satisfies: FR-10, FR-2, NFR-3, NFR-4, NFR-7 (startup lines), AC-14, AC-22 (restart on reopen), AC-26, AC-31
- Depends on: T-3, T-5
- Priority / size: P1 / M
- Affected files: create `M/config/PersistenceConfig.java`, `M/persistence/{SchemaInitializer,SqliteTaskRepository}.java`,
  `T/persistence/{SqliteTaskRepositoryTest,SchemaInitializerTest}.java`
- Acceptance criteria:
  - [ ] Pragmas read back equal the configured values for DELETE, TRUNCATE and WAL (V-29); `synchronous` is FULL.
  - [ ] Ordering, AUTOINCREMENT no-reuse across delete and reopen, text round-trip incl. U+0000 and astral characters (R-13; failure is reported, not stripped).
  - [ ] Unknown `user_version` fails startup; existing data survives reopen.
- Test requirements: repository tests on a file in `TestDataDir`, DataSource closed in `@AfterEach` (U-REPO, V-20). Edge: file that is not a database, missing parent, read-only directory, journal-mode fallback values.
- Documentation: none.

### T-12: Gateway filter, Tomcat and web configuration (C13, C2 TomcatConfig/WebConfig) - CP-B
- Description: `ApiGatewayFilter` (first filter; REQUEST and ERROR dispatch; MDC request id and request attribute; `nosniff` always;
  P1-P3 on REQUEST only; writes rejections with `setStatus` plus bytes **and never `sendError`** (DR-16); HEAD gets no body;
  catches any `Throwable`, 500 envelope if uncommitted, logs once per D-20 rule 2, clears MDC). **Boundary-only mode on ERROR
  dispatch (DR-16, DR-19):** if the method is not one of the dispatched route's methods (for example TRACE) write the precomputed
  body for the dispatched status and do not call `chain.doFilter`; also override `shouldNotFilterErrorDispatch()` (false) and
  `doFilterNestedErrorDispatch` (DR-19). `TomcatConfig` (`allowTrace=true`, `encodedSolidusHandling=passthrough`,
  `ErrorReportValve` report and server info off; `encodedReverseSolidusHandling` only if present, V-3). `WebConfig`
  (`FilterRegistrationBean`: `Ordered.HIGHEST_PRECEDENCE`, REQUEST and ERROR, `/*`; Accept ignored). Check the filter chain for
  container-added filters (for example Tomcat `WsFilter`, V-9/DR-19): if `tomcat-embed-websocket` is present and not needed, exclude it in the
  POM and record it; otherwise record the observed order.
- Satisfies: FR-9, FR-11, FR-12, FR-13, NFR-1, NFR-2, NFR-6, NFR-7, NFR-9, AC-12, AC-13, AC-16, AC-24, AC-25, AC-34, AC-36
- Depends on: T-3, T-4, T-8
- Priority / size: P0 / M
- Affected files: create `M/web/gate/ApiGatewayFilter.java`, `M/config/{TomcatConfig,WebConfig}.java`,
  `T/web/gate/ApiGatewayFilterTest.java` (unit, `MockHttpServletRequest`), `T/contract/GatewayContractTest.java` (real server, test-only stub
  handler for pass-through); modify `backend/pom.xml` only for a websocket exclusion if needed
- Acceptance criteria:
  - [ ] **CP-B:** T-P1a, T-P1b (incl. TRACE with a distinctive header on defined and `/nope` paths; HEAD without body; no `Access-Control-*`),
        T-P2, T-P2b (100-continue), T-P3 (two Content-Type headers, form, multipart, `merge-patch+json`), T-RT (b)(c) pass against real Tomcat,
        each asserting code, envelope, `nosniff` and that nothing changed. These prove V-1, V-4, V-5, V-6, V-8, V-9, V-10, V-11, V-14, V-15, V-16, V-17.
  - [ ] Unit: ERROR dispatch with method TRACE is answered without calling the chain; a nested ERROR dispatch is handled (DR-19).
  - [ ] Structural: a test scans `backend/src/main/java` and fails on `sendError`, `@RequestBody`, `@PathVariable`, `@Valid`, `consumes`, `produces`,
        `@CrossOrigin`, `@ResponseStatus` (7.2 rules).
  - [ ] T-RT (b): C13 first, no registration with order less than or equal, character-encoding filter absent; the report states that
        container-added filters are outside this proof.
  - [ ] If `allowTrace=true` does not route TRACE to the filter in the pinned Tomcat, the developer stops (NEEDS_APPROVAL: TRACE moves to G2-6), does not silently change behaviour.
- Test requirements: integration (real Tomcat, `RANDOM_PORT`, `@DirtiesContext(AFTER_CLASS)`, TestDataDir) and unit as above. Failure cases: client abort not treated as failure; throwing downstream filter yields one 500 and one ERROR log entry.
- Documentation: report notes for V-1..V-17 outcomes.

### T-13: Controller, argument resolver, exception mapping and error controller - CP-C
- Description: `TodoController` (six operations on `ApiRoutes` constants; no `consumes`, `produces`, `@PathVariable`, `@RequestBody`; `ResponseEntity<byte[]>`
  with preset `application/json`; 204 without body or Content-Type; passes a C12 render function to the service),
  `GateAttributeArgumentResolver`, `ApiExceptionHandler` (not extending `ResponseEntityExceptionHandler`; logs each 500 once, no 4xx),
  `FallbackErrorController` at `/error` (maps 404, 405, 413, 415; **any other status becomes 500 INTERNAL_ERROR, logged once (G2-5 (h), DR-17)**;
  fixed bytes, `nosniff`, no attributes echoed). R-16 mitigation if V-19 shows Spring rejecting a raw segment C3 accepted.
- Satisfies: FR-1..FR-9, FR-12, NFR-1, NFR-2, NFR-7, AC-1..AC-11, AC-13, AC-15, AC-17, AC-18, AC-19, AC-20, AC-21, AC-23, AC-25, AC-28..AC-31
- Depends on: T-9, T-10, T-11, T-12
- Priority / size: P0 / M
- Affected files: create `M/web/{TodoController,GateAttributeArgumentResolver,ApiExceptionHandler,FallbackErrorController}.java`,
  `T/contract/{PrecedenceContractTest,CrudContractTest,RouteTableMappingTest}.java`
- Acceptance criteria:
  - [ ] **CP-C:** T-P1a..T-P7 pass on one real server, including T-P4 (`%2F`, `%5C`, `%zz`, `%FF`, `%2530`; container 400 recorded under G2-6 where V-3 says so),
        T-P4b, T-P5, T-P6, T-P7, with `GET /todos` identical before and after each rejected request. Proves V-2, V-3, V-7, V-18, V-19.
  - [ ] T-RT (a): `RouteTable` equals the API handler mappings (`/error` excluded).
  - [ ] Every operation's success and AC-1..AC-11 cases pass; the `{id}` in the mapping is never read by controller code.
- Test requirements: integration on real Tomcat with `InMemoryTaskRepository` first and SQLite second; unit for the resolver and advice. Edge: missing attribute -> 500 once; 404 for an unknown id after valid body.
- Documentation: report records the CP-C evidence.

### T-14: Concurrency, transaction wiring and durability (Surefire and Failsafe)
- Description: T-TX (recording DataSource wrapper: write lock always held before `getConnection`; no request waits for `connectionTimeout`; concurrent update pair
  last-write-wins, AC-32), durability (a) in-JVM: start context, mutate, close, assert Hikari closed, restart on the same file, compare; durability (b)
  `*IT` child JVM run by Failsafe after `package`: `java.home`/`bin/java`, `--enable-native-access=ALL-UNNAMED`, `-jar target/todo-backend-<version>.jar`, fixed free port,
  test-data file, `destroyForcibly()`, restart, compare (AC-22). DR-21 mechanics: `classesDirectory`, JAR name as system property, executions bound, `*IT` excluded from Surefire.
- Satisfies: FR-10, NFR-3, NFR-4, AC-14, AC-22, AC-32
- Depends on: T-13
- Priority / size: P1 / M
- Affected files: create `T/contract/TransactionWiringContractTest.java`, `T/durability/{InJvmDurabilityTest,ForcedKillDurabilityIT}.java`; modify `backend/pom.xml` (Failsafe settings)
- Acceptance criteria:
  - [ ] Created, changed and deleted tasks are identical after in-JVM restart and after a forced kill; deleted tasks stay absent.
  - [ ] `./mvnw -B verify` output shows the `ForcedKillDurabilityIT` in the Failsafe summary (the S7 report must show it) (V-28).
  - [ ] T-TX shows lock before connection for every mutation (V-20, V-21).
- Test requirements: as above. Failure cases: port already used, JAR missing (clear failure, not skip), second DataSource detected by T-TX.
- Documentation: report notes the real Failsafe settings.

### T-15: Cross-cutting contract tests
- Description: T-HDR (Content-Type exactly `application/json`, no charset; `nosniff`; 204 without Content-Type), T-ACC (`Accept: text/html`, `application/xml`,
  V-12), T-CORS, T-B1 (exactly 65,536 bytes; maximal task with surrogate escapes; uppercase id; query string on all routes; 1,000-digit number and nesting 1,000 in an ignored member; 60,000-char member name),
  T-P5 limit cases, T-ERR (failing repository on every operation; test-only `sendError` with 404, 500 and **one unmapped 4xx and one unmapped 5xx** asserting status 500 INTERNAL_ERROR (G2-5 (h));
  failure during ERROR dispatch; **TRACE request with the `sendError` hook: envelope, no echoed header (DR-16)**; no `trace`, `exception`, `path`, `timestamp`), T-LOG (advice, escape and error-controller
  paths: exactly one ERROR entry each with request id, method, route template, exception class and without title, query or raw id; none for 4xx).
- Satisfies: FR-9, NFR-1, NFR-2, NFR-6, NFR-7, NFR-9, NFR-10, AC-12, AC-13, AC-19, AC-24, AC-27, AC-28, AC-33, AC-34, AC-36
- Depends on: T-13 (T-14 not required)
- Priority / size: P1 / M
- Affected files: create `T/contract/{HeadersContractTest,NegotiationContractTest,CorsContractTest,BoundaryContractTest,ErrorContractTest,LoggingContractTest}.java`
- Acceptance criteria:
  - [ ] Each test class passes with the matrix expectations of architecture.md 10.1; the T-ERR TRACE case and both unmapped statuses are asserted explicitly.
  - [ ] Log capture uses Boot's `OutputCaptureExtension` (package per V-25).
- Test requirements: integration; failing-repository bean and `TestHooksConfig` only here. Edge: failure thrown while handling a failure produces one entry per distinct exception.
- Documentation: none.

### T-16: Performance suite
- Description: JUnit test tagged `performance`: per operation reseed to 1,000 tasks (DELETE from 1,200; POST grows by at most 200), 200 sequential timed requests, no warm-up exclusion;
  assert p95 at most 500 ms, report max; print machine, JDK and journal mode.
- Satisfies: NFR-5, AC-35
- Depends on: T-13
- Priority / size: P1 / M
- Affected files: create `T/performance/PerformanceTest.java`
- Acceptance criteria:
  - [ ] `./mvnw -B verify -Pperformance` prints p95 and max for the six operations with machine, JDK and journal mode; default run excludes it.
  - [ ] If p95 fails or max outliers appear on Windows, the report applies the R-10 fallback order (TRUNCATE, then WAL; both FULL) as a configuration change and records which mode ran.
- Test requirements: performance tag only; run result is evidence for S7, not a pass until executed.
- Documentation: none.

### T-17: Run instructions and documentation
- Description: `backend/README.md` (working directory rule `backend/`, absolute `--todo.data-file`, `TODO_DATA_FILE`, `--enable-native-access=ALL-UNNAMED`, journal-mode fallback, loopback default, single instance,
  local disk only, G2-6 limits, NFR-8 explicitly has no target and nothing built for it); final V-1..V-30 outcome table in `implementation-report.md`; manual smoke with `./mvnw spring-boot:run` if authorised.
- Satisfies: NFR-8, NFR-4 (operations notes), NFR-9
- Depends on: T-14, T-15, T-16
- Priority / size: P2 / S
- Affected files: create `backend/README.md`
- Acceptance criteria:
  - [ ] Every command in the README is one of 1.2.1 or is marked unverified.
  - [ ] The V table has an outcome for each of the 30 items.
- Test requirements: none beyond the checks above (documentation).
- Documentation: this task.

## 3. Execution order
| Wave | Tasks | Parallel | Notes |
|---|---|---|---|
| 0 | kickoff prerequisites 1.2, 1.3 | human | G2-7, base commit, branch, exact Boot patch; not a task |
| 1 | T-1 | no | includes the G2-8 human pause for `mvn wrapper:wrapper` |
| 2 | T-2, T-3 | yes (T-2 names first) | version record; skeleton and test support |
| 3 | T-4, T-5 | yes | catalog and mapper; domain types |
| 4 | T-6, T-7, T-8, T-10, T-11 | yes, all independent | pure units and persistence |
| 5 | T-9, T-12 | yes | **CP-A** after T-9 (unit precedence table); **CP-B** after T-12 (P1-P3 on real Tomcat) |
| 6 | T-13 | no | **CP-C** (P1-P7 on real server, SQLite) |
| 7 | T-14, T-15 | yes | durability and cross-cutting |
| 8 | T-16, T-17 | yes (T-17 last) | performance and docs |
Checkpoint suggestion: the Orchestrator may pause after CP-A, CP-B and CP-C so the human or a reviewer reads the evidence in
`implementation-report.md` before later waves; precedence defects are cheapest to fix there. No dependency cycle exists (T-1 -> T-2/T-3 -> T-4/T-5 -> T-6..T-8,T-10,T-11 -> T-9,T-12 -> T-13 -> T-14..T-16 -> T-17).

## 4. Scope manifest
| Path | Action | Task |
|---|---|---|
| `backend/pom.xml` | create (modify in T-12 if websocket exclusion, T-14 Failsafe) | T-1, T-12, T-14 |
| `backend/.gitignore`, `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/maven-wrapper.properties` | create | T-1 |
| `backend/src/main/resources/application.properties` | create | T-3 |
| `M/TodoApplication.java`, `M/config/{TodoProperties,JsonConfig,ClockConfig,PersistenceConfig,TomcatConfig,WebConfig}.java` | create | T-3, T-4, T-5, T-11, T-12 |
| `M/web/{ApiRoutes,TodoController,GateAttributeArgumentResolver,ApiExceptionHandler,FallbackErrorController}.java`, `M/web/gate/*`, `M/web/request/*`, `M/web/response/*` | create | T-4, T-7, T-8, T-9, T-12, T-13 |
| `M/validation/*`, `M/domain/*`, `M/domain/support/*`, `M/persistence/*` | create | T-5, T-6, T-10, T-11 |
| `T/**` (unit, contract, durability, performance, support), `T/BuildSanityTest.java` | create | T-2..T-16 |
| `backend/README.md` | create | T-17 |
| `docs/sdlc/CAP-2/implementation-report.md` | create (developer) | all |
| `backend/target/`, `backend/data/` | generated, ignored, never committed or deleted by tests | T-3, T-14 |
No file is deleted. The root `.gitignore`, `.claude/` and framework files are not touched.

### Needs explicit approval at kickoff
| Item | Type | Task | Reason |
|---|---|---|---|
| Permission to run `mvn`/`./mvnw` (list 1.2.1) and download from Maven Central (G2-7) | config / external access | T-1..T-17 | NOT granted; human edits `.claude/settings.json` or approves prompts |
| `mvn wrapper:wrapper` run and wrapper files | new tool run, build config | T-1 | G2-8, human pause |
| Dependencies: Spring Boot 4.1.x starters (web, jdbc, test), sqlite-jdbc (native, pinned), Surefire and Failsafe plugin config | new dependency | T-1 | G2-1, G2-2; exact versions to be supplied by the human |
| Maven coordinates and package | config | T-1 | G2-4 ratified |
| Base commit on `Main`, remote check, feature branch, `branch-plan.md` | branch / repository state | kickoff | section 1.3 |
| POM exclusion of `tomcat-embed-websocket` if present and unneeded | dependency change | T-12 | only if needed; recorded |
| Journal mode fallback to TRUNCATE or WAL | config | T-16 | only after the performance or R-10 evidence; recorded |
| Use of `org.sqlite.tmpdir` | config | T-11 | only if native-library extraction is blocked (R-10 a) |
| `mvn clean` | deletion | none | not planned; needs own approval |
| New HTTP API (six operations) | public interface (new) | T-13 | fixed by requirements Appendix A; informational |

## 5. Traceability
| Requirement | Tasks |
|---|---|
| FR-1 | T-5, T-6, T-10, T-13 |
| FR-2 | T-5, T-10, T-11, T-13 |
| FR-3 | T-7, T-10, T-13 |
| FR-4 | T-6, T-10, T-13 |
| FR-5 | T-10, T-13 |
| FR-6 | T-6, T-10, T-13 |
| FR-7 | T-6, T-7, T-9, T-13 |
| FR-8 | T-6, T-7, T-13 |
| FR-9 | T-4, T-12, T-13, T-15 |
| FR-10 | T-3, T-11, T-14 |
| FR-11 | T-8, T-12 |
| FR-12 | T-8, T-9, T-12, T-13 |
| FR-13 | T-8, T-12 |
| NFR-1 | T-1, T-3, T-4, T-12, T-13, T-15 |
| NFR-2 | T-4, T-12, T-13, T-15 |
| NFR-3 | T-5, T-9, T-10, T-11, T-14 |
| NFR-4 | T-1, T-11, T-14, T-17 |
| NFR-5 | T-1, T-16 |
| NFR-6 | T-8, T-12, T-15 |
| NFR-7 | T-3, T-11, T-12, T-13, T-15 |
| NFR-8 | T-17 (no target; nothing built) |
| NFR-9 | T-1, T-3, T-12, T-15, T-17 |
| NFR-10 | T-4, T-6, T-15 |
| AC-1 | T-10, T-13 |
| AC-2 | T-6, T-13 |
| AC-3 | T-7, T-13 |
| AC-4 | T-6, T-10 |
| AC-5 | T-10, T-13 |
| AC-6 | T-7, T-10, T-13 |
| AC-7 | T-10, T-13 |
| AC-8 | T-6, T-10, T-13 |
| AC-9 | T-10, T-13 |
| AC-10 | T-10, T-13 |
| AC-11 | T-6, T-10, T-13 |
| AC-12 | T-4, T-12, T-15 |
| AC-13 | T-4, T-12, T-13, T-15 |
| AC-14 | T-11, T-14 |
| AC-15 | T-7, T-13 |
| AC-16 | T-8, T-12 |
| AC-17 | T-7, T-13 |
| AC-18 | T-6, T-7, T-13 |
| AC-19 | T-4, T-13, T-15 |
| AC-20 | T-4, T-6, T-13 |
| AC-21 | T-4, T-6, T-13 |
| AC-22 | T-11, T-14 |
| AC-23 | T-6, T-7, T-13 |
| AC-24 | T-8, T-12, T-15 |
| AC-25 | T-8, T-9, T-12, T-13 |
| AC-26 | T-11, T-13 |
| AC-27 | T-4, T-10, T-15 |
| AC-28 | T-6, T-13, T-15 |
| AC-29 | T-6, T-10, T-13 |
| AC-30 | T-6, T-13 |
| AC-31 | T-10, T-11, T-13 |
| AC-32 | T-10, T-14 |
| AC-33 | T-4, T-6, T-15 |
| AC-34 | T-12, T-15 |
| AC-35 | T-16 |
| AC-36 | T-12, T-15 |
| Orphan requirements | none (13 FR, 10 NFR, 36 AC all mapped) |
| Orphan tasks | none; T-1 and T-2 are enabling tasks mapped to the requirements they make achievable or verifiable |

## 6. Test strategy
- Levels and tools: JUnit Jupiter, AssertJ, Spring Test and Boot test support from `spring-boot-starter-test` (A-ARCH-1, confirmed at T-2, V-25; provenance
  architecture.md section 4, S2-1; **no repository file confirms it yet**). JDK `HttpClient` (HTTP/1.1) and a raw-socket helper for contract tests.
  Layers: unit (no Spring), repository (SQLite file in `backend/target/test-data/`), contract (`@SpringBootTest` `RANDOM_PORT`, real Tomcat,
  `@DirtiesContext(AFTER_CLASS)`), durability (in-JVM and Failsafe child JVM), performance (tag `performance`). Hand-written fakes, no Mockito (V-27).
  Windows-safe lifecycle: test databases are never deleted by tests; contexts and DataSources are closed explicitly (DR-9). No lint, format or audit tool (none chosen; repo-profile.md section 7).
- All commands PLANNED, none run, none have repository provenance until T-1 creates the POM and the G4 record authorises them (1.2).
- Behaviours to verify (architecture.md 14.1), each is a test or build record:

| V | Proven in | V | Proven in | V | Proven in |
|---|---|---|---|---|---|
| V-1 | T-12 T-P1b | V-11 | T-12 T-ERR unit and contract | V-21 | T-14 T-TX |
| V-2 | T-13 T-P4 | V-12 | T-15 T-ACC | V-22 | T-4, T-7 U-JSON |
| V-3 | T-13 T-P4 | V-13 | T-13, T-15 T-ERR | V-23 | T-4 U-RESP |
| V-4 | T-12 T-P1a | V-14 | T-12 T-RT (c) | V-24 | T-7 |
| V-5 | T-12 T-P2 | V-15 | T-12 T-RT (c) | V-25 | T-2 |
| V-6 | T-12 T-P1b | V-16 | T-12 T-P3 | V-26 | T-2, T-14 |
| V-7 | T-13 T-ERR | V-17 | T-12 T-P1a | V-27 | T-2 |
| V-8 | T-12 T-P2, T-P2b | V-18 | T-13 | V-28 | T-14 |
| V-9 | T-12 T-RT (b) | V-19 | T-13 T-RT (a), T-P4 | V-29 | T-11 |
| V-10 | T-12 | V-20 | T-11, T-14 | V-30 | T-1, T-2 |

- Acceptance criterion -> demonstration:

| AC | Test or check | Task |
|---|---|---|
| AC-1, AC-4, AC-5, AC-7, AC-9, AC-10 | CrudContractTest, TaskServiceTest | T-10, T-13 |
| AC-2, AC-8, AC-11, AC-20, AC-28..AC-30 | validator tables, T-P6, T-B1 | T-6, T-13, T-15 |
| AC-3, AC-17, AC-23 | U-JSON, T-P5 | T-7, T-13 |
| AC-6, AC-15 | U-ID, T-P4, T-P4b, T-P7 | T-7, T-13 |
| AC-12, AC-13, AC-19, AC-21, AC-27, AC-33 | U-RESP, T-HDR, T-ERR | T-4, T-15 |
| AC-14, AC-22, AC-26, AC-31 | repository tests, durability (a) and (b) | T-11, T-14 |
| AC-16, AC-24, AC-25 | T-P1a..T-P3 matrix, pipeline pair table (CP-A, CP-B, CP-C) | T-8, T-9, T-12, T-13 |
| AC-18 | validator and T-B1 ignored-member cases | T-6, T-15 |
| AC-29 | PUT without description / title | T-6, T-10 |
| AC-32 | T-TX concurrent pair | T-14 |
| AC-34 | T-CORS | T-15 |
| AC-35 | performance suite | T-16 |
| AC-36 | T-LOG | T-15 |

## 7. Definition of done
- [ ] All tasks implemented and their acceptance criteria met (or explicitly pending with a reason).
- [ ] Tests added and passing, with commands, exit codes and output excerpts recorded in `implementation-report.md`; verification-report.md at Stage 7 shows
      the Failsafe `*IT` and the performance run. Nothing is reported as passed unless executed.
- [ ] CP-A, CP-B, CP-C evidence recorded; V-1..V-30 each has an outcome; DR-16..DR-21 and G2-5 (b), (h) items each shown fixed or failed.
- [ ] `backend/README.md` written; no unresolved critical review finding; no secrets; `backend/target/` and `backend/data/` not committed.

## 8. Blocked tasks and risks
| Task / risk | Unblocking condition / mitigation | Owner |
|---|---|---|
| All tasks: G2-7 not granted | human grants permission before Stage 5 and G4 records the command list (1.2) | human, Orchestrator |
| All tasks: no base commit, no feature branch, protected `Main` | section 1.3 B-1..B-7 | human, pr-manager |
| T-1 step 4: wrapper creation | human runs `mvn wrapper:wrapper` or approves one agent command (G2-8) | human |
| T-1: exact Boot patch and sqlite-jdbc version | human supplies at G4 (1.1) | human |
| Profile provenance: the developer may run only profile-listed commands; the profile has none | G4 record authorises 1.2.1; refresh of `repo-profile.md` after T-1 is not writable by the developer (agent-paths.conf:15 denies it); the verification agent discovers commands from `pom.xml` and `mvnw` | Orchestrator |
| R-1, V-1: TRACE override fails | T-12 stops; TRACE moves to G2-6 with human approval | human |
| R-5, V-26: Boot or sqlite-jdbc not supported on JDK 25 | G2-2 option B/C or accept warning; BLOCKED, not silent change | human |
| R-10: antivirus and journal deletion on Windows | journal fallback order in T-16 | developer, human |
| R-13: U+0000 or astral round-trip failure | report, no silent stripping | developer |
| R-16: Spring rejects a raw segment C3 accepted | mitigation in architecture.md R-16, test T-P4 | developer |
| Requirements header still `DRAFT` (DR-14) | Orchestrator's decision on an addendum; plan treats G1 as approved | Orchestrator |

## 9. Rollout and rollback
New service; nothing existing depends on it. Rollout: start from `backend/` with `./mvnw spring-boot:run` or
`java --enable-native-access=ALL-UNNAMED -jar target/todo-backend-<version>.jar` (PLANNED, unverified); data file
`backend/data/todo.db` by default. Rollback: stop the service and revert the feature branch; the data file is kept and never deleted without approval. A
journal-mode change is a property change. No feature flags, migrations or CI changes.
