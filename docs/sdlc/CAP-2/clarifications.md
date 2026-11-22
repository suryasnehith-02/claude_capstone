# Clarifications - CAP-2

Stage 1, round 1. Asked by the Orchestrator via AskUserQuestion (headers S1 Q1-S1 Q3). Answers given by the human.

## Bulk decision (S1 Q1)
The human chose **Accept all defaults** for Q1-Q22. Each suggested default below is therefore recorded as an explicit assumption, decided by the human (not guessed by an agent). Q19 and Q21 were asked individually.

| # | Question | Decision (decided by human) |
|---|---|---|
| 1 | Limits: title 1-100, description 0-500; inclusive? unit? | Confirmed as stated, inclusive, counted in Unicode code points after trim |
| 2 | Paths, prefix, trailing slash | As proposed, no prefix, no trailing-slash tolerance |
| 3 | Id format | UUID in canonical text, opaque to clients |
| 4 | Status representation in JSON | Exact strings "Pending" / "Completed", case-sensitive |
| 5 | PATCH status body/response | Body `{"status":"<Pending|Completed>"}`; 200 with full task; VALIDATION_ERROR on field "status" |
| 6 | PUT full or partial | Full replace; title required; omitted description is cleared |
| 7 | Absent description | Key always present, "" when none; omitted / null / "" on input all mean none |
| 8 | Whitespace rules | Trim title and description; title limit checked after trim; whitespace-only description becomes empty |
| 9 | Unknown fields and server-owned fields on update | Ignore all (including status in POST/PUT and a differing id in the body) |
| 10 | Timestamp format and no-op updates | ISO-8601 UTC "Z" with milliseconds; every successful PUT/PATCH bumps updatedAt strictly later |
| 11 | Ordering ties | createdAt descending, ties: later-created first; updates never reorder |
| 12 | Content-type handling | 415 when a body is present and type is not application/json (charset allowed; missing type counts as unsupported); GET/DELETE ignore it; empty body with JSON type is 400 INVALID_REQUEST_BODY |
| 13 | Non-object JSON, `{}`, AC-3 on PATCH | Non-object is INVALID_REQUEST_BODY; `{}` is VALIDATION_ERROR; AC-3 applies to PATCH |
| 14 | Error precedence | 415, INVALID_ID, INVALID_REQUEST_BODY, VALIDATION_ERROR, then TODO_NOT_FOUND |
| 15 | Error codes fixed? Unknown routes, 405, 413 | Codes fixed; add NOT_FOUND, METHOD_NOT_ALLOWED, PAYLOAD_TOO_LARGE in the same format; messages are not a machine contract |
| 16 | Validation details | Report all failing fields; `field` is the JSON property name; wording aligned with CAP-1 messages; wrong type is VALIDATION_ERROR, no coercion |
| 17 | Duplicate titles | Allowed |
| 18 | Concurrent updates | Last write wins |
| 19 | Durability | **Asked individually (S1 Q3): Durable** - tasks survive a service restart |
| 20 | NFR targets | About 1,000 tasks; p95 <= 500 ms; reject oversized bodies; server-side failure logs without secrets; no availability target |
| 21 | CORS | **Asked individually (S1 Q2): No CORS needed** (same origin or proxy; no CORS headers) |
| 22 | Output encoding | Store and return text verbatim; client is responsible for safe display |

# Stage 2 clarifications (round 1)
Asked by the Orchestrator after the solution-architect returned NEEDS_CLARIFICATION (headers S2 Q1-S2 Q4). The first ask was declined by the human, who then stated the stack in free text ("Continue backend with java springboot"); the remaining items were then asked again and answered. All decided by the human.

| # | Question | Decision (decided by human) |
|---|---|---|
| S2-1 | Language / framework / test runner | **Java with Spring Boot** (stated by the human in free text). JUnit-based tests assumed as the Spring Boot default; the architect must confirm from the framework's standard test starter and record it as a stated assumption. |
| S2-1b | Build tool | **Maven** (Maven wrapper checked in). JDK version and whether agents may run build/test commands were NOT stated: JDK version to be discovered from the machine/evidence in later stages; permission to run Maven commands in Stages 5 and 7 must be obtained from the human (settings) before those stages. |
| S2-2 | Durable storage | **Embedded transactional single-file database** (architect's O1). Specific product (e.g. H2 file mode vs SQLite) left to the architect to justify. |
| S2-3 | Application location | **backend/** sub-directory of the repository |
| S2-ENV | Installed toolchain (reported by the human during Stage 3, pasted `mvn -v` output; NOT executed or verified by any agent) | Apache Maven 3.10.1 (home `C:\Users\NarlaSuryaSnehith\Downloads\apache-maven-3.10.1`); Java 25.0.5.1, Oracle Corporation (`C:\Program Files\Java\jdk-25.0.5.1`); default locale en_IN, platform encoding UTF-8, time zone Asia/Calcutta; Windows 11 amd64. This is information only. It does NOT grant permission for agents to run Maven or download dependencies (still open, G2-7), and it does not decide the JDK compile target recommended in architecture.md section 4.1 (G2-2: JDK 21 target; the installed JDK 25 is newer than the recommendation; Spring Boot 4.x / sqlite-jdbc compatibility with JDK 25 is unverified). |
| S2-4 | Edge cases beyond Clar. #1-#22 | **Accept all five**: uppercase UUIDs accepted and normalised to lowercase (D-13); Content-Type parameters incl. charset ignored, body always UTF-8 (D-4); HEAD and OPTIONS return 405 (D-12); query string ignored for routing (D-12); unpaired surrogate escapes in JSON give 400 INVALID_REQUEST_BODY (D-11) |

# Stage 3 pre-gate decisions (before G2)
Asked by the Orchestrator after design review round 2 returned APPROVE (headers S3 Q1-S3 Q3). Answers given by the human. These are recorded here and in approvals.md; architecture.md was NOT edited, so the reviewed hash stays valid. The implementation planner (Stage 4) must apply them.

| # | Question | Decision (decided by human) |
|---|---|---|
| S3-1 | Spring Boot line and JDK target (G2-2) | **Boot 4.1.x, `--release 21`**, compiled on the installed JDK 25. Candidate line is still subject to the architecture's selection rule at Stage 4 (4.0.x is the stated fallback if 4.1.x does not meet it). JDK 25 support by the line and libraries is UNVERIFIED and can only be checked when Stage 7 runs on the human's machine; tests run on JDK 25 only, so "runs on Java 21" is unverified. |
| S3-2 | G2-5 (h) and (b) | **Accept both**: (h) a container-level error status with no catalog code becomes 500 INTERNAL_ERROR, logged once; (b) Jackson member-name length limit raised to the body cap (65,536) so an ignored member cannot cause a 400; number-length limit stays 1,000 (DR-18). |
| S3-3 | DR-16..DR-21 timing | **Carry to the Stage 4 plan** as tasks or test requirements (never call sendError; boundary-only mode short-circuits non-route methods; T-ERR TRACE case; filter-order and nested ERROR dispatch notes; version-rule tie-break and a concrete line; Failsafe setup in V-28). architecture.md stays as reviewed. |

# Pre-implementation prerequisites (after G3)
Asked by the Orchestrator after G3 was approved (headers S5 Q1-S5 Q3). Answers given by the human. These are INTENTIONS stated by the human; none of them is done yet and none counts as completed until the Orchestrator can see the result.

| # | Question | Decision (decided by human) | Status |
|---|---|---|---|
| S5-1 | Base commit on protected `Main` (plan B-1, B-2) | **Framework only**, created by the human: `CLAUDE.md`, `.gitignore`, `.mcp.json.example`, `.claude/` except gitignored `settings.local.json`, `docs/framework/`, `scripts/`, `tests/`. `docs/sdlc/CAP-2/` stays untracked (goes into the feature-branch PR). `docs/codemie/analytics/*.json` is NOT committed. | Not yet done (repo has no commits as of G3) |
| S5-2 | GitHub remote check (plan B-3) | **pr-manager checks read-only** (`get_me`, `list_branches`, `get_file_contents`) after the base commit exists; no writes, no push. | Pending base commit |
| S5-3 | Maven permission (G2-7) | **Human will allow the command list**: `mvn -v`, `mvn wrapper:wrapper` (human-run), `./mvnw -v`, `./mvnw -B test` (also `-Dtest=<Class>`), `./mvnw -B verify`, `./mvnw -B verify -Pperformance`, `./mvnw spring-boot:run`, and Maven Central downloads. Not authorised: `clean`, global installs, lint, format, audit. The human edits `.claude/settings.json`; no agent does. | Not yet done; Orchestrator will read `.claude/settings.json` to confirm before G4 |
| S5-4 | Exact Spring Boot 4.1.x patch and sqlite-jdbc version | Not yet given. To be supplied by the human at G4 (plan 1.1). | See update below |

## Update after the human reported the prerequisites done
Observed by the Orchestrator (read-only checks of the repository):
- **S5-1 base commit:** done. `.git/refs/heads/Main` exists and `.git/HEAD` points to `refs/heads/Main`. The human's `git status` output (pasted) showed only framework files staged, with `docs/codemie/` and `docs/sdlc/` untracked. The commit hash was not read.
- **S5-3 Maven permission:** done in `.claude/settings.json` `permissions.allow` (lines 49-55): `mvn -v`, `./mvnw -v`, `./mvnw -B test`, `./mvnw -B test -Dtest=*`, `./mvnw -B verify`, `./mvnw -B verify -Pperformance`, `./mvnw spring-boot:run`. `mvn wrapper:wrapper`, `clean`, global installs, lint, format and audit are NOT in the list. This is permission to run commands; it does not by itself say Maven Central downloads are accepted: that acceptance is to be stated at G4.
- **S5-4 Boot version, evidence pasted by the human (text from the Spring Boot system-requirements page; not fetched or verified by any agent):** Spring Boot **4.1.3** requires at least Java 17 and is compatible up to and including Java 27; Spring Framework 7.0.11 or above; Maven 3.6.3 or later (also Gradle 8.14+/9.x); embedded Tomcat 11.0.x (Servlet 6.1) or Jetty 12.1.x.
  - Selection rule (impl-plan 1.1): criterion (1) a patch release after .0 exists: supported by the version 4.1.3 itself (inference). Criterion (3) lists Java 25: satisfied by "compatible up to and including Java 27" on the pasted text. Criterion (2) six or more months of open-source support remaining: **NOT evidenced** by the pasted text (it gave no support end date); the human has to confirm it on the G4 date.
  - Line pinned for the plan: Spring Boot **4.1.3** (human-supplied), `--release 21`, compiled on the installed JDK 25 (S3-1).
- **sqlite-jdbc version: NOT provided yet.** Needed before T-1 writes the POM; to be given at G4.

## Human answers to S5 Q4-Q6 (version, downloads, support window)
Given by the human in a free-text message after the Orchestrator asked. Facts below are the human's report; no agent fetched or verified them (no web access).

| # | Question | Decision / information (from the human) |
|---|---|---|
| S5-4 (final) | sqlite-jdbc version | **`org.xerial:sqlite-jdbc:3.54.1.0`**, reported by the human as the latest published 3.x on Maven Central as of 2026-11-21 (source cited by the human: Maven Central version history). Not verified by any agent; T-1 must record the version Maven actually resolves, and a failed resolution is reported, not worked around. |
| S5-5 | Maven Central downloads (needed by G4) | **ACCEPTED** by the human: Maven Central is the dependency repository; Maven may download the Maven distribution, Spring Boot 4.1.3, `org.xerial:sqlite-jdbc` and test dependencies, plus Maven plugins, during builds. The human also states "the configured Maven settings permit the required repository access"; the Orchestrator has not seen any Maven settings file, so that part is UNVERIFIED and a blocked or proxied download is a T-1 NEEDS_APPROVAL case. This is a G4 input, not the G4 approval. |
| S5-7 | Developer wave 1 questions (Orchestrator defaults, NOT human decisions; the human may redirect) | (1) Human must run `mvn wrapper:wrapper` once in `backend/` and paste the output: **pending, human action**. (2) `mvn -v` failed in the agent shell (exit 127, `mvn: command not found`; JAVA_HOME and MAVEN_HOME are set but are directory names, not version checks): accepted default = no PATH change; the first toolchain record is `./mvnw -v` after the wrapper exists. (3) Starter names (`spring-boot-starter-web`, `spring-boot-starter-test`) kept as in the plan; T-2 records any 4.x difference; if resolution fails the developer reports the exact error. The Orchestrator reads a renamed equivalent of an already approved starter (same purpose) as within G4 scope only if the report lists old name, new name and evidence; any other new dependency needs approval. (4) Project version `0.0.1-SNAPSHOT` kept. (5) `performance` profile sets sentinel `none-excluded` so nothing is excluded: confirmed. |
| S5-8 | Developer wave 2 questions (Orchestrator defaults, NOT human decisions; the human may redirect) | (1) Keep `spring-boot-starter-web` (resolves in 4.1.3; its POM says deprecated in favour of `spring-boot-starter-webmvc`); no switch. Recorded as a deprecation risk for Stage 6. (2) The three T-3 support files `InMemoryTaskRepository`, `FailingTaskRepository`, `ScriptedIds` move to the T-5 wave (they need the T-5 types); they stay inside `T/support/**` (in scope). (3) `TestHooksConfig` kept as designed (two header-triggered filters); T-15 may extend it and decides the TRACE-with-sendError case. |
| S5-9 | Developer wave 4a part 2 questions (Orchestrator decisions applying earlier HUMAN decisions; the human may redirect) | (1) `;` in the id segment (`/todos/<id>;x=1`): **404 NOT_FOUND**, the literal reading of D-12 and plan T-8 ("`;param` gives no match"); the Stage 3 reviewer to confirm. (2) `Content-Type: application/json; charset=<unknown or empty>`: **accepted, NOT 415**. S2-4 (human, ratified at G2): "Content-Type parameters including charset are ignored, body always UTF-8". The developer's 415 came from Spring's strict `MediaType.parseMediaType`; the type and subtype must be parsed by hand, with all parameters ignored; a value with no valid type/subtype (for example `not a media type`) is still 415. Rework item R-CT in the next delegation. (3) T-13's `PrecedenceContractTest` **replays all 21 pair rows of `PrecedenceCases` over real HTTP**, since 12 pairs have no row in architecture 10.1 (CP-C). |
| S5-10 | Developer T-12 / CP-B questions (Orchestrator decisions; the human may redirect) | (1) `tomcat-embed-websocket`: **leave it, no POM change** (its `WsFilter` runs after the gateway filter and does not affect CP-B); recorded for Stage 6. (2) Malformed percent sequences in the path (`%zz`, `%FF`, `%`, `a%00b`, `%C0%AF`, `%E2%82`): **accepted as Tomcat's own HTML 400 outside the envelope under G2-6** (human-ratified at G2: "container-level rejections outside the error envelope", with these sequences named as candidates depending on V-3); `%2F`, `%5C`, `%20` reach the application. This is a visible limit on AC-12 / AC-15 and must be repeated at G5, G6 and in the PR description. (3) An unreadable request body (client abort, broken framing): **400 INVALID_REQUEST_BODY, logged at DEBUG only**: accepted as the default; not a human decision. |
| S6-1 | Code review findings to fix before G5 (human, 2026-11-22) | **Fix CR-1, CR-5, CR-6, CR-7** in one developer rework pass; no dependency changes; the nits CR-9..CR-16 stay open and go into the PR notes; then a short re-review of the changed code and Stage 7. |
| S6-2 | Dependency findings CR-2 (deprecated `spring-boot-starter-web`), CR-3 (`tomcat-embed-websocket`), CR-4 (wrapper without checksum) (human) | **Keep as is, recorded human risk acceptance** (not a waiver of a critical finding; these are minor): no POM dependency change; the three risks are listed in the PR description. This also turns Orchestrator default S5-10 (1) (keep the websocket module) into a human decision. |
| S6-3 | CR-8 T-15 deviations (human) | **Ratified both**: Logback `LogCapture` instead of `OutputCaptureExtension`; four planned T-15 test files not created because the cases exist in other tests. To be recorded in the G5 row. |
| S8-1 | Commit strategy (human, 2026-11-22; resolves branch-plan BP-4) | **Single commit** `CAP-2: Add Todo backend task management REST APIs`, staged by explicit path; supersedes the 9-commit plan in `branch-plan.md` section 2 (that file is covered by the G4 hash and is NOT edited; this row and `pr-description.md` carry the change). |
| S8-2 | Remote base branch name (human; resolves BP-2) | **`main`**. The human pushes the base commit `7e63ced` himself, for example `git push origin Main:main` (agents cannot push). The PR base is `main`. Not done yet at the time of this row. |
| S8-3 | Uncommitted `.claude/settings.json` edit (human; resolves BP-3) | **Leave uncommitted**; excluded from the commit and the PR. |
| S5-6 | Support rule (2) for Spring Boot 4.1.x (6+ months of open-source support left) | **Confirmed by the human**: 4.1.x initial release May 2026, end of open-source support June 2027, enterprise support to June 2028 (source cited: Spring Boot 4 support timeline). From 2026-11-21 to June 2027 is about 6.3 to 7.3 months depending on the day in June; the margin over 6 months is small. Not verified by any agent. All three selection criteria for 4.1.x are therefore satisfied on the human's evidence, and **Spring Boot 4.1.3 is pinned** (4.0.x fallback not needed). |
