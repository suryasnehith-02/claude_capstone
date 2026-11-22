# Repository profile - CAP-2

Every row cites its evidence as `path` or `path:line`. Use `UNKNOWN` when there is no evidence.

Discovery run: Stage 2, attempt 3 (solution-architect, design-review rework), 2026-11-20. This run refreshed the
attempt-2 profile. Method: Glob `**/{pom.xml,mvnw*,*.java,build.gradle*,.mvn/**}` (no files), listing of the
repository root, and read-only `git status --short`. Nothing was built, installed or executed. In particular,
`java -version`, `mvn -v` and the Maven wrapper were not run.

Changes since attempt 2:
- The tree is unchanged: still no `backend/`, `pom.xml`, `mvnw*`, `.mvn/` or `*.java`. `git status --short` still
  shows every top-level entry untracked.
- **Toolchain facts reported by the human** (`clarifications.md` row S2-ENV): Apache Maven 3.10.1 and Oracle JDK
  25.0.5.1, from pasted `mvn -v` output. No agent executed or verified this. Sections 2, 3 and 7 are updated.
- The planned version and build details follow `architecture.md` attempt 3 (section 4.1, 10.2, 14, 14.1).

## 1. Overview
- Greenfield (no source, no manifest): **yes**. No application source, manifest, lock file, CI, container,
  environment, migration or API contract file exists. Evidence: full-tree Glob (only `.claude/**`,
  `docs/framework/*.md`, `docs/sdlc/CAP-2/*.md`, `docs/codemie/analytics/*.json`, `scripts/validate-framework.sh`,
  `tests/hooks/run-tests.sh`, `.gitignore`, `.mcp.json.example`, `CLAUDE.md`, `.sdlc/**`). A Glob for
  `**/{pom.xml,mvnw*,*.java,build.gradle*,.mvn/**}` returned no files (attempt 3).
- Repository purpose (from docs): a technology-agnostic Claude Code framework that drives a Jira issue through 8
  SDLC stages to a GitHub PR. Evidence: `CLAUDE.md` (Agentic SDLC Framework section), `docs/framework/README.md:1-6`.
- Layout today: a single repository with the framework only. Planned: one Maven module in `backend/` beside the
  framework (S2-3; architecture.md D-17). Evidence: `docs/framework/README.md:30-41`; `clarifications.md` S2-3.
- Default and protected branches: current branch `Main` (`git branch --show-current`, attempt 2). `git log` reports
  "your current branch 'Main' does not have any commits yet", and `git status --short` shows every top-level entry
  untracked (re-checked in attempt 3). The session snapshot names `main` as the main branch. Protected globs
  (case-insensitive) are listed in `.claude/hooks/protected-branches.txt`: `main, master, develop, development, trunk,
  release/*, releases/*, hotfix/*`. `Main` matches `main`.

## 2. Technology inventory
| Module / path | Languages | Frameworks / major libraries | Evidence |
|---|---|---|---|
| `backend/` (**planned, does not exist yet**) | Java (decided). Recommended compile target `--release 21` on the installed JDK 25 (G2-2 to ratify) | Spring Boot (Spring MVC, embedded Tomcat) decided; one 4.x minor line to be pinned at G2 (candidate 4.1.x, unverified). Planned libraries: Jackson (via the web starter), Spring JDBC + HikariCP, xerial `sqlite-jdbc` (SQLite), `spring-boot-starter-test` (JUnit Jupiter, AssertJ, Spring Test). Surefire and Failsafe via the Boot parent | Decisions: `clarifications.md` S2-1 (Java + Spring Boot), S2-1b (Maven wrapper), S2-2 (embedded single-file DB), S2-3 (`backend/`). Product and version choices: `architecture.md` sections 4, 4.1, D-5, D-22. **No manifest exists to confirm any of this.** |
| `.claude/hooks/`, `scripts/`, `tests/hooks/` (framework tooling, not the product) | POSIX sh, awk | none ("Pure POSIX sh + awk/grep; no language runtime needed") | `scripts/validate-framework.sh:1-3`, `tests/hooks/run-tests.sh:1-2`, `.claude/hooks/json.awk` |
| `.claude/agents/`, `.claude/skills/`, `docs/` | Markdown (+ YAML frontmatter) | Claude Code agents/skills | `docs/framework/README.md:30-36` |
| `docs/codemie/analytics/*.json` | JSON data file (analytics export; not read: not relevant and may contain personal data) | n/a | file listing only |

Stated assumption A-ARCH-1 (from S2-1): tests use JUnit Jupiter as provided by the standard Spring Boot test starter.
Evidence is Spring Boot's documentation, not this repository. It is confirmed from `backend/pom.xml` and the pinned
Boot BOM once that file exists (`architecture.md` 14.1 V-25).

## 3. Commands
No product command exists or has been run. The Maven commands below are **PLANNED** in `architecture.md`
section 10.2. They have no provenance in a repository file yet, so they **must not be executed** until `backend/pom.xml`
and `backend/mvnw` exist and this profile is refreshed (S4/S5), and the human has granted permission (G2-7, **not
given**). Framework-only commands do not build or test CAP-2.

| Purpose | Command | Working dir | Provenance (file:line) | Confidence |
|---|---|---|---|---|
| Create the wrapper (one time) | PLANNED, proposed default: `mvn wrapper:wrapper` run by the human (or an agent command the human approves) with the installed Maven 3.10.1, after `backend/pom.xml` exists | `backend/` | `architecture.md` G2-8 (proposal only) | none (unverified) |
| Install dependencies | PLANNED: resolved by `./mvnw -B verify` (needs Maven Central access) | `backend/` | `architecture.md` 10.2 (plan only); no `pom.xml` yet | none (unverified) |
| Build / compile | PLANNED: `./mvnw -B verify` (Git Bash) / `mvnw.cmd -B verify` (cmd) | `backend/` | `architecture.md` 10.2 (plan only) | none (unverified) |
| Unit tests | PLANNED: `./mvnw -B test` (single class: `-Dtest=<ClassName>`). Framework only: `sh tests/hooks/run-tests.sh` | `backend/`; framework at repo root | `architecture.md` 10.2 (plan only); `tests/hooks/run-tests.sh:5` (framework) | none (product); high (framework) |
| Integration tests | PLANNED: contract tests (Surefire) and the child-JVM durability test (Failsafe, after `package`) within `./mvnw -B verify`; performance with `./mvnw -B verify -Pperformance` | `backend/` | `architecture.md` 10, 10.1, 10.2 (plan only) | none (unverified) |
| Lint / static analysis | UNKNOWN for the product (no tool chosen). Framework only: `sh scripts/validate-framework.sh [--complete] [repo-root]` | repo root | `scripts/validate-framework.sh:4` (framework) | high (framework only) |
| Format | UNKNOWN (no formatter chosen or configured) | - | none | - |
| Type check | Covered by Java compilation in `./mvnw -B verify` (PLANNED) | `backend/` | `architecture.md` 10.2 (plan only) | none (unverified) |
| Security / dependency audit | UNKNOWN (no tool chosen; would need network) | - | none | - |
| Run locally | PLANNED: from `backend/`, `./mvnw spring-boot:run` or `java --enable-native-access=ALL-UNNAMED -jar target/todo-backend-<version>.jar` | `backend/` (required: relative default data path) | `architecture.md` 10.2, section 8 (plan only) | none (unverified) |

Toolchain facts:
- Reported by the human, **not verified by any agent** (`clarifications.md` S2-ENV): Apache Maven 3.10.1 (home
  `C:\Users\NarlaSuryaSnehith\Downloads\apache-maven-3.10.1`); Java 25.0.5.1, Oracle Corporation
  (`C:\Program Files\Java\jdk-25.0.5.1`); default locale `en_IN`, platform encoding UTF-8, time zone Asia/Calcutta;
  Windows 11 amd64.
- UNKNOWN: whether the agents' Git Bash shell resolves the same `java`/`mvn` (`JAVA_HOME`, `PATH`), and network access
  to Maven Central. Not inspected, because that would require running programs.
- Permission for agents to run `mvn` or `./mvnw`: **not given** (`.claude/settings.json:44-48` allows read-only git only; G2-7).

## 4. Conventions
- Source layout and naming: no application source exists. Planned: the Maven standard layout in `backend/`,
  package `com.capstone.todo` with sub-packages `config`, `web.gate`, `web.request`, `web`, `web.response`,
  `validation`, `domain`, `domain.support`, `persistence` (`architecture.md` section 6, D-18, G2 to ratify).
  Framework convention: per-issue artifacts in `docs/sdlc/<KEY>/`, runtime state in `.sdlc/` (gitignored). Evidence:
  `CLAUDE.md` ("State and artifacts"), `.gitignore:1-2`.
- Error handling and logging: none in the product yet. Planned: a single error catalog and envelope, plus SLF4J/Logback
  error-only logging with one logging rule (`architecture.md` D-15, D-19, D-20). Framework scripts use `ok`/`bad`
  counters and exit codes (`scripts/validate-framework.sh:12-14`).
- Test layout, naming, fixtures: none in the product yet. Planned: `backend/src/test/java/com/capstone/todo/**` with
  unit, contract, durability and performance tests; test databases under `backend/target/test-data/`
  (`architecture.md` section 10). Framework: `tests/hooks/run-tests.sh:1-26` builds a throwaway temp project and
  asserts allow/deny paths.
- Formatter / linter rules that apply: none found (no `.editorconfig`, linter or formatter configuration in the tree).
- Commit message and branch naming style: UNKNOWN. There are no commits (`git log`: "does not have any commits yet").
  Framework test fixtures use `feature/<key>-<slug>` branch names (`tests/hooks/run-tests.sh:22`); this is a framework
  convention, low confidence for this repo.
- Documentation locations and changelog convention: framework docs in `docs/framework/`; SDLC artifacts in
  `docs/sdlc/<KEY>/`; no CHANGELOG file (pr-manager may create `changelog*`, `.claude/hooks/agent-paths.conf:18`).

## 5. CI / CD
- Pipelines: none (no CI/CD definition file anywhere in the tree).
- Required checks before merge: UNKNOWN.

## 6. External services and configuration
- Services, databases, queues, APIs the code talks to: none today. Planned: one local SQLite database file
  (`todo.data-file`, default `./data/todo.db` relative to the working directory, i.e. `backend/data/todo.db` when
  started from `backend/`) through sqlite-jdbc (`architecture.md` D-5, D-22, section 8). Build time needs Maven
  Central. The framework expects MCP servers `atlassian` and `github` (`CLAUDE.md`, "MCP servers";
  `.mcp.json.example`). These are not used by the product.
- Configuration mechanism: none today. Planned: `backend/src/main/resources/application.properties` and Spring
  relaxed binding (for example the environment variable `TODO_DATA_FILE`, `todo.store.journal-mode`, `server.port`,
  `server.address`). Names only; no secrets exist (`architecture.md` 7.5). Secret-bearing files: none found (`.env*`,
  `*.pem`, `*.key` absent). `.claude/settings.local.json` exists and is gitignored (`.gitignore:5`); it was not read.
- Tool permissions relevant to later stages: `.claude/settings.json:44-48` allows only read-only git commands in the
  shell. `./mvnw` and `mvn` are not allowed, and `git commit`/`git push` are "ask" (`.claude/settings.json:66-67`).
  Source writes require stage 5, gates G1-G4 and a non-protected branch (`.claude/hooks/agent-paths.conf:9,15`).

## 7. Open questions
| # | Question | Why it matters | Blocks which stage |
|---|---|---|---|
| 1 | Ratify the JDK target: `--release 21` on the installed JDK 25 (recommended), `--release 25`, or `--release 17`. Is the human-reported JDK 25.0.5.1 the one on `JAVA_HOME`/`PATH` for the agents? | Only JDK 25 is reported installed (S2-ENV, unverified); Boot 4.x / sqlite-jdbc support for JDK 25 is unverified (architecture.md 4.1, G2-2, 14.1 V-26) | S4, S5, S7 |
| 2 | May the developer and verification agents run `./mvnw` (section 3) and `mvn` (wrapper creation), and download from Maven Central? | `.claude/settings.json` allows no product commands; without this nothing can be built or verified (G2-7, still not given) | S5, S7 |
| 3 | Wrapper creation: accept the default (the human, or an approved agent command, runs `mvn wrapper:wrapper` once in `backend/` with Maven 3.10.1 after the POM exists)? | The wrapper is the agreed build entry point (S2-1b) but needs a tool run and network (G2-8) | S5 |
| 4 | Ratify the Spring Boot 4.x minor line (candidate 4.1.x, unverified), sqlite-jdbc 3.x and the SQLite choice | New dependencies; every override depends on the line (G2-1, G2-2, architecture.md 14.1) | S4 |
| 5 | Is a dependency-vulnerability audit or a formatter/linter wanted? None is planned | Commands stay UNKNOWN otherwise | S4 (optional) |
| 6 | Branch: `Main` matches the protected `main` glob and has no commits. A base commit and a feature branch are needed before S5. | Hooks require a non-protected branch for source edits | S5 (Orchestrator / pr-manager) |
| 7 | Refresh this profile once `backend/pom.xml` and the wrapper exist, so that commands gain file provenance, and record the `./mvnw -v` output | Commands are PLANNED until then; S2-ENV is unverified | S4/S5 |

Confidence summary: section 2 = high that no product technology is present yet, and medium for the planned stack
(human decision, no manifest yet). Section 3 = high that no product command exists; planned commands are unverified;
toolchain facts are human-reported (medium, unverified); framework commands high. Section 4 = low (no product code,
no git history). Section 5 = high (no CI files exist).
