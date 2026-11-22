# Repository profile - CAP-1

Every row cites its evidence as `path` or `path:line`. Use `UNKNOWN` when there is no evidence.

Discovery runs:
- **Stage 2, attempt 1** (solution-architect, 2026-11-22). This created a new profile for CAP-1. It reuses facts from
  `docs/sdlc/CAP-2/repo-profile.md` only where they were re-checked against the current tree, because that profile was
  written before `backend/` existed and is out of date. Method: a listing of the repository root and `backend/`; Glob
  of `backend/**`, `scripts/**`, `tests/**`, `docs/**` and `.github/**`; reads of the cited files; read-only
  `git status`, `git log` and `git branch`.
- **Stage 2, attempt 2** (solution-architect, 2026-11-22). This is a refresh, not a rewrite. The human's decisions
  S2-1..S2-3 (`docs/sdlc/CAP-1/clarifications.md`, "Stage 2 clarifications, round 1") were applied, and
  `.claude/settings.json:44-55` was re-checked: it is unchanged and still allows no `java` command. No other cited
  file changed. No runtime command was run in this attempt.
- **Stage 2, attempt 3, revised after design review round 1** (solution-architect, 2026-11-22). A small rework to keep
  this profile in line with the revised `architecture.md` (design review findings DR-1..DR-11 applied; DR-7 concerns the
  proxy test B). Only the permission note in section 3 ("Tool permissions", the working-directory statement, R-11) was
  changed. Every other fact, provenance line and caveat is unchanged. No command was run and no other file was read
  again for this change.

**Runtime version checks were refused in attempt 1.** The SDLC hook (`.claude/hooks/pre-implementation-check.sh:166`,
message "solution-architect is read-only in the shell") blocked `java -version`, `node --version`, `python --version`
and `python3 --version`. None of them ran, and I did not try to get around the block. The runtime facts in section 3
therefore come from recorded executions in earlier artifacts or from the human, not from these runs.

## 1. Overview
- Greenfield (no source, no manifest): **no**. The repository holds the framework and one product module, `backend/`.
  **There is no `frontend/` directory and no HTML, CSS or JavaScript product file** (root listing; Glob). CAP-1 starts
  the frontend from nothing inside an existing repository.
- Repository purpose (from the docs): a technology-agnostic Claude Code SDLC framework, plus the Todo app product it
  builds (the CAP-2 backend is merged). Evidence: `CLAUDE.md`, `docs/changelog.md:9-14`.
- Layout: framework files (`.claude/`, `docs/framework/`, `scripts/`, `tests/hooks/`) plus one Maven module,
  `backend/`. There is no root build manifest, so this is not a multi-module build. Evidence: root listing,
  `backend/pom.xml`, `backend/README.md:3`.
- Other top-level entries:
  - `.codemie/` and `docs/codemie/analytics/*.json`: analytics exports. Not read, because they are not relevant and may hold personal data.
  - `.mcp.json.example`.
  - `.sdlc/`: gitignored (`.gitignore:2`); not read.
- Default and protected branches:
  - The current branch is `Main` (git). The remote default is `origin/main` (`git branch -a`: `remotes/origin/HEAD -> origin/main`).
  - Protected globs are listed in `.claude/hooks/protected-branches.txt`: `main, master, develop, development, trunk, release/*, releases/*, hotfix/*`, matched case-insensitively.
  - `Main` matches `main`, so Stage 5 needs a feature branch.

## 2. Technology inventory
| Module / path | Languages | Frameworks / major libraries | Evidence |
|---|---|---|---|
| `backend/` (CAP-2, merged) | Java, compiled for release 21, built and tested on JDK 25.0.5.1 | Spring Boot 4.1.3 (Spring MVC, embedded Tomcat), Spring JDBC, sqlite-jdbc (SQLite file), Jackson (`tools.jackson.databind`). Tests use Surefire and Failsafe | `backend/README.md:3-5`, `backend/pom.xml`, `docs/changelog.md:9-12`, `backend/src/main/java/com/capstone/todo/validation/TaskRequestValidator.java:16` |
| `frontend/` (CAP-1, **does not exist yet**) | UI: plain HTML, CSS and JavaScript (native ES modules), with no build step (H-C1, human decision Q-1). Server and proxy: one Java file, `frontend/server/FrontendServer.java`, run by the JDK 25 source launcher (**human decision S2-1**) | No framework and no dependency. The server uses only JDK modules (`jdk.httpserver`, `java.net.http`) | `docs/sdlc/CAP-1/requirements.md` 7.1, 7.1.1; `docs/sdlc/CAP-1/clarifications.md` Q-1, Q-2, S2-1; `architecture.md` D-1 |
| `.claude/hooks/`, `scripts/`, `tests/hooks/` (framework tooling) | POSIX sh, awk | none | `scripts/validate-framework.sh`, `tests/hooks/run-tests.sh` |
| `.claude/agents/`, `.claude/skills/`, `docs/` | Markdown | Claude Code agents and skills | `CLAUDE.md` |

JavaScript tooling in the repository: **none**. There is no `package.json`, lock file, `node_modules`, test runner,
bundler, linter or formatter configuration (root listing; Glob). There is no Python manifest either. The decided
design needs none of these (architecture section 4).

## 3. Commands
| Purpose | Command | Working dir | Provenance (file:line) | Confidence |
|---|---|---|---|---|
| Install dependencies | Backend: Maven resolves them from Maven Central. Frontend: none (JDK only, no packages; S2-1) | `backend/` | `backend/README.md:19`; clarifications S2-1 | high (backend), high (frontend: none by decision) |
| Build / compile | Backend: `./mvnw -B verify`. Frontend: **none**, because there is no build step (H-C1). The Java file compiles in memory when it is launched | `backend/` | `backend/README.md:26`; requirements 7.1 H-C1; clarifications S2-1 | high |
| Unit tests | Backend: `./mvnw -B test`. Frontend UI: PLANNED self-test page at `http://127.0.0.1:8000/tests/`, **run by the human, results HUMAN-REPORTED** (S2-3). It has no executable provenance yet | `backend/`; browser | `backend/README.md:24`; clarifications S2-3; architecture 10.1 | high (backend), none (frontend: the file does not exist yet) |
| Integration tests | Backend: Failsafe through `./mvnw -B verify`. Frontend: PLANNED `java server/FrontendServerTest.java` (architecture 10.1). An agent runs it only if the S2-2 entries are present; otherwise the human runs it (HUMAN-REPORTED) or it is NOT_RUN | `backend/`; `frontend/` | `backend/README.md:26`; clarifications S2-2 | high (backend), none (frontend: the file does not exist yet) |
| End-to-end | Frontend: PLANNED manual checklist in `frontend/README.md`, **run by the human, results HUMAN-REPORTED** (S2-3) | browser plus both processes | clarifications S2-3; architecture 10.1 | none (the file does not exist yet) |
| Lint / static analysis | Product: none (`backend/README.md:31`). Framework only: `sh scripts/validate-framework.sh` | repo root | `backend/README.md:31`, `scripts/validate-framework.sh` | high |
| Format | none | - | `backend/README.md:31` | high |
| Type check | Backend: Java compilation. Frontend: none (plain JS) | - | - | high |
| Security / dependency audit | none (and NOT_RUN by H-2 for CAP-1) | - | `backend/README.md:31`; clarifications.md H-2 | high |
| Run locally (backend) | `java --enable-native-access=ALL-UNNAMED -jar target/todo-backend-0.0.1-SNAPSHOT.jar --todo.data-file=<absolute path>/todo.db`, after `./mvnw -B verify`. `./mvnw spring-boot:run` is documented but was never run | `backend/` | `backend/README.md:35-40`, `:27` | high (the JAR form was verified in a child JVM), low (`spring-boot:run`) |
| Run locally (frontend) | **Decided (S2-1):** `java server/FrontendServer.java [--port=8000] [--backend-port=8080]`. If `java` is not on `PATH`, use `"$JAVA_HOME/bin/java" server/FrontendServer.java` (Git Bash) or `"%JAVA_HOME%\bin\java" server\FrontendServer.java` (cmd); the README must document both. It must not be run until the file exists and the S2-2 permission is in place (agents) | `frontend/` | clarifications S2-1; architecture 7.3 | medium: decided by the human, but the file does not exist yet and `java` on `PATH` is unconfirmed |

Runtime facts, each with its provenance:
| Runtime | Status | Evidence |
|---|---|---|
| JDK 25.0.5.1 (Oracle, `C:\Program Files\Java\jdk-25.0.5.1`), Windows 11 amd64 | **Confirmed by a recorded agent execution**: `./mvnw -v`, exit 0, run on this JDK at CAP-2 Stage 7 | `docs/sdlc/CAP-2/verification-report.md:48-60` (V-1 output); `backend/README.md:5,23` |
| `java` launcher on the shell `PATH` (as opposed to the Maven wrapper finding it through `JAVA_HOME`) | **UNCONFIRMED: java on PATH unconfirmed; the README documents the JAVA_HOME form.** `java -version` was refused by the hook in attempt 1, and the human did not paste its output at S2-1 (it was optional). The backend's `java ... -jar` command was verified only inside a child JVM started by a test (`backend/README.md:35-36`), never from `PATH` | attempt 1; clarifications S2-1; `backend/README.md:35-36` |
| `jdk.httpserver` (`com.sun.net.httpserver`) and `java.net.http` modules | Expected in a full Oracle JDK 25 image. **Not checked** (`java --list-modules` was not run and is not in the S2-2 permission list). The first start of C-1 would reveal a missing module (architecture R-2) | none in the repo; architecture R-2 |
| Node.js | **UNKNOWN / not confirmed.** The check was refused, and the repo has no evidence. The decided design does not need it | clarifications.md Q-2 ("NOT confirmed") |
| Python | **UNKNOWN / not confirmed.** The check was refused, and the repo has no evidence. The decided design does not need it | clarifications.md Q-2 ("NOT confirmed") |
| Web browser (any of Chrome, Edge, Firefox, Safari) | **UNKNOWN** to the agents. The human runs the browser tests (S2-3) and reports the browsers and versions he used; that report is HUMAN-REPORTED evidence for NFR-4 | requirements NFR-4; clarifications S2-3 |

Tool permissions:
- **Shared settings.** `.claude/settings.json:44-55` (re-checked in attempt 2, unchanged) allows these commands for
  agents:
  - read-only git commands: `git status *`, `git diff *`, `git log *`, `git branch --show-current` and `git rev-parse *`;
  - these Maven commands: `mvn -v`, `./mvnw -v`, `./mvnw -B test[ -Dtest=*]`, `./mvnw -B verify[ -Pperformance]` and `./mvnw spring-boot:run`.

  **It allows no `java ...` command.** `.claude/settings.json` shows as modified in `git status`. I read it and did not
  change it.
- **Human's local settings (S2-2).** The human intends to add `Bash(java -version)`,
  `Bash(java server/FrontendServer.java*)` and `Bash(java server/FrontendServerTest.java)` to his own
  `.claude/settings.local.json`.
  - That file is gitignored (`.gitignore:5`) and was **not read** by this agent.
  - The entries are **not yet confirmed present**. The Orchestrator reads the file before Stage 5 and does not edit it.
  - If they are missing, the frontend server start and the proxy test are human-run (HUMAN-REPORTED) or NOT_RUN.
  - The entries match only the exact command text (architecture R-11). **Revised in attempt 3:** the server command
    (`java server/FrontendServer.java`) still needs `frontend/` as the working directory. The proxy test B
    (`java server/FrontendServerTest.java`) now discovers the frontend directory itself, so it works from `frontend/` or
    from the repository root (architecture 10.1 row B rule (1), C-13).
  - Agent shells reset to the repository root between calls, so the form an agent can run (for example with a preceding
    `cd frontend`) is likely not to match the permission entry. R-11 likelihood is therefore **HIGH**.
  - The optional repository-root permission entry was answered **NO** by the Orchestrator (review Q-1). S2-2 stays as
    decided. B is human-run (HUMAN-REPORTED) or NOT_RUN when the agent's command form does not match the entry. This is
    the expected default, not an exception.

## 4. Conventions
- Source layout and naming: the backend uses the Maven standard layout and the package `com.capstone.todo.*`, with the
  sub-packages `config`, `web`, `web.gate`, `web.request`, `web.response`, `validation`, `domain` and `persistence`
  (Glob `backend/src/main/java/**`). There are no frontend conventions yet. The planned layout is in architecture 6.1.
- Text rules the UI must mirror:
  - The backend trims by the 25 Unicode `White_Space` code points and counts Unicode code points, deliberately avoiding
    Java `strip()` and `trim()`. Evidence: `backend/src/main/java/com/capstone/todo/validation/UnicodeText.java:3-51`.
  - Its field messages are "Title is required", "Title must be 100 characters or fewer" and "Description must be 500
    characters or fewer" (`TaskRequestValidator.java:38-43`). They use the same wording as requirements 7.3.
- Response content type: `application/json` with no charset parameter (`backend/src/main/java/com/capstone/todo/web/response/ApiResponses.java:26`).
- Error handling and logging: there is a single error envelope and catalogue (`web/response/ErrorCode.java`,
  `ApiExceptionHandler.java`). Server failures are logged once, without titles or ids (`backend/README.md:107-108`).
- Test layout: `backend/src/test/java/com/capstone/todo/**`, grouped by kind (`contract/`, `domain/`, `persistence/`,
  `durability/`, `support/`). The raw-HTTP helpers are in `support/RawHttp.java` (Glob).
- Formatter and linter rules: none (no config files; `backend/README.md:31`).
- Commit message style: `<KEY>: <Imperative summary>`, for example `cacc351 CAP-2: Add Todo backend task management REST APIs`.
  Branch style: `feature/<KEY>-<slug>`, for example `feature/CAP-2-todo-backend-task-apis`. Merges go through GitHub
  PRs (`9d2bc51 Merge pull request #1`). Evidence: `git log`, `git branch -a`.
- Documentation: each module has its own README (`backend/README.md`). New work goes under `Unreleased` in the
  changelog `docs/changelog.md` (`docs/changelog.md:3-6`). SDLC artifacts go in `docs/sdlc/<KEY>/`.

## 5. CI / CD
- Pipelines: none (Glob `.github/**` found nothing; `docs/sdlc/CAP-2/verification-report.md:38`).
- Required checks before merge: UNKNOWN.

## 6. External services and configuration
- The frontend talks to the CAP-2 backend at `127.0.0.1:8080`: that is the default, it is loopback only and there is no
  authentication. The backend sends no CORS headers and answers OPTIONS with 405. Evidence: `backend/README.md:45-46,100-101`.
- Backend configuration: the properties and environment variables `TODO_DATA_FILE`, `TODO_STORE_JOURNAL_MODE`,
  `SERVER_PORT` and `SERVER_ADDRESS` (names only; `backend/README.md:50-55`).
- Secret-bearing files: none found (no `.env*`, `*.pem` or `*.key` in the listings). `.claude/settings.local.json` is
  gitignored (`.gitignore:5`) and was not read.
- Write permissions per agent: in Stage 5, after G1-G4 and on a non-protected branch, the developer may write anything
  outside the SDLC control files (`.claude/hooks/agent-paths.conf:15`). `frontend/` is therefore writable in Stage 5.

## 7. Open questions
| # | Question | Status | Blocks which stage |
|---|---|---|---|
| 1 | Is `java` on `PATH` in the human's terminal and in the agents' Git Bash? | **Open, not blocking.** Java on `PATH` is unconfirmed, and the README documents the `JAVA_HOME` form (S2-1). An agent can check it with `java -version` once the S2-2 entry exists | S5, S7 (runner of B only) |
| 2 | Will agents have `java` permissions? | **Decided (S2-2): the human intends to add three entries to his own `.claude/settings.local.json`; not yet done.** The Orchestrator verifies this before Stage 5. If the entries are missing, B is human-run (HUMAN-REPORTED) or NOT_RUN | S5, S7 (runner of B only) |
| 3 | Who runs the browser test page and the checklist? | **Decided (S2-3): the human runs both and pastes the results (HUMAN-REPORTED).** Which browsers are installed is reported by the human with his results | none |
| 4 | Node.js and Python are UNKNOWN | Not needed by the decided design; listed for completeness only | none |

Confidence summary:
- Section 2: high (manifests, README and human decisions).
- Section 3: high for the backend. The frontend commands are decided (S2-1..S2-3), but none of the files exists yet.
  JDK 25 is high (a recorded execution). `java` on `PATH` is unconfirmed. Node.js, Python and the browser are UNKNOWN
  to the agents.
- Section 4: high for the backend and git, none for the frontend.
- Section 5: high (no CI).
