# Branch plan - CAP-2

Mode: prepare (before Stage 5), attempt 1. Written by pr-manager. Covered by gate G4 together with `impl-plan.md`.
Everything below marked "read" was read-only (git, GitHub MCP get_me / list_branches / get_file_contents); nothing was pushed or created on GitHub.

## 1. Branches
| Item | Value |
|---|---|
| Base branch (local) | `Main` at `7e63ced960b4412cffd4520c8c9c4fa08e4f2ad4` ("Add Agentic SDLC framework"), the only commit in the repository (`git rev-list --count HEAD` = 1, `git log --all` shows one commit) |
| Base branch (remote default expected) | `main` (GitHub default; the empty-repo error from the API names `heads/main`). The remote has no branches, so the base does not exist there yet |
| Feature branch | `feature/CAP-2-todo-backend-task-apis` (46 characters, lowercase, no spaces) |
| Remote | `origin` = `https://github.com/suryasnehith-02/claude_capstone` (from `git remote -v`); authenticated GitHub user: `suryasnehith-02` (id 167758630) |
| Protected-branch check | not in `.claude/hooks/protected-branches.txt`: **yes** (list: main, master, develop, development, trunk, release/*, releases/*, hotfix/*). The feature branch matches none. Local `Main` DOES match `main` (case-insensitive) and is protected |
| Branch exists already (local / remote) | before: local no (only `Main`); remote no (`list_branches` returned `[]`). Now: local branch created, remote none |
| Currently checked out | `feature/CAP-2-todo-backend-task-apis`, at `7e63ced`, created with `git switch -c` (exit 0). `Main` untouched and still at `7e63ced` |

### 1.1 Remote findings (read-only)
- `get_me`: authenticated as `suryasnehith-02`; the repo owner in the remote URL is the same account.
- `list_branches` on `suryasnehith-02/claude_capstone`: `[]`.
- `get_file_contents` path `/`: `409 Git Repository is empty`.
- Conclusion: the repository exists and is **empty**. It shares no history with local `Main`, and there is no remote base branch (`main`) for a PR to target. Default branch name on GitHub will be whatever the first push creates; the local branch is `Main` (capital M), so a push of `Main` would create a remote branch `Main`, not `main`, unless the human pushes it as `main` (for example `git push origin Main:main`; human-run, not done by any agent).
- The Stage 8 PR cannot be created until the remote has a base branch whose history contains `7e63ced`. The feature branch is based on `7e63ced`, so once the human pushes the base, the PR base is valid.

## 2. Commit strategy
- Message style (evidence: `git log` has one commit, "Add Agentic SDLC framework", imperative, no prefix; no CONTRIBUTING file; plan 1.3 B-6): `CAP-2: <imperative summary>`, first line at most 72 characters, ending with the attribution trailer in force for the session.
- One commit per implementation wave of impl-plan section 3 (waves 1-8; wave 0 is human prerequisites, not a task), then one commit for the SDLC artifacts and changelog.
- Commits, push and PR happen **only in Stage 8 after G7**. No agent commits or pushes before then (hooks deny it). Files are staged by explicit path only, never `git add -A` or `.`.

| # | Scope (tasks) | Message |
|---|---|---|
| 1 | Wave 1: T-1 | `CAP-2: Add Maven POM and wrapper for backend` |
| 2 | Wave 2: T-2, T-3 | `CAP-2: Add application skeleton, properties and test support` |
| 3 | Wave 3: T-4, T-5 | `CAP-2: Add error catalog, JSON mapper and domain model` |
| 4 | Wave 4: T-6, T-7, T-8, T-10, T-11 | `CAP-2: Add validation, request parsing, route table, service and SQLite store` |
| 5 | Wave 5: T-9, T-12 | `CAP-2: Add request pipeline and API gateway filter` |
| 6 | Wave 6: T-13 | `CAP-2: Add task controller and error handling` |
| 7 | Wave 7: T-14, T-15 | `CAP-2: Add durability and cross-cutting contract tests` |
| 8 | Wave 8: T-16, T-17 | `CAP-2: Add performance suite and backend README` |
| 9 | Artifacts | `CAP-2: Add SDLC artifacts and changelog entry` |

Caveats (see Open items): `backend/pom.xml` is modified in T-12 and T-14 and only its final state exists at Stage 8; interactive staging (`git add -p`) is not available, so it is committed once, in its final state, in commit 1, and later waves' commits are not guaranteed to build on their own. If the human prefers, collapse to a single commit `CAP-2: Add Todo backend task management REST APIs`.

## 3. Files
- Included (scope manifest, impl-plan section 4; `M/` = `backend/src/main/java/com/capstone/todo/`, `T/` = `backend/src/test/java/com/capstone/todo/`, `R/` = `backend/src/main/resources/`):

| Path | Action | Task(s) | Commit |
|---|---|---|---|
| `backend/pom.xml` | create (modify in T-12 websocket exclusion, T-14 Failsafe) | T-1, T-12, T-14 | 1 |
| `backend/.gitignore`, `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/maven-wrapper.properties` | create (wrapper files generated, list unverified) | T-1 | 1 |
| `R/application.properties` | create | T-3 | 2 |
| `M/TodoApplication.java`, `M/config/{TodoProperties,JsonConfig,ClockConfig,PersistenceConfig,TomcatConfig,WebConfig}.java` | create | T-3, T-4, T-5, T-11, T-12 | 2, 3, 4, 5 |
| `M/web/{ApiRoutes,TodoController,GateAttributeArgumentResolver,ApiExceptionHandler,FallbackErrorController}.java`, `M/web/gate/*`, `M/web/request/*`, `M/web/response/*` | create | T-4, T-7, T-8, T-9, T-12, T-13 | 3, 4, 5, 6 |
| `M/validation/*`, `M/domain/*`, `M/domain/support/*`, `M/persistence/*` | create | T-5, T-6, T-10, T-11 | 3, 4 |
| `T/**` (unit, contract, durability, performance, support), `T/BuildSanityTest.java` | create | T-2..T-16 | 2-8 |
| `backend/README.md` | create | T-17 | 8 |
| `docs/sdlc/CAP-2/*.md` (requirements, architecture, design-review, impl-plan, repo-profile, clarifications, approvals, open-issues, branch-plan, implementation-report, code-review, verification-report, pr-description) | create | all stages | 9 |
| Changelog (see section 4) | create | Stage 8 draft | 9 |

  No file is deleted. The root `.gitignore`, `.claude/` and other framework files are not touched.
- Excluded (never staged):
  - `.sdlc/` (gitignored runtime state and audit log), `.claude/settings.local.json` (gitignored).
  - `backend/target/`, `backend/data/` (generated; ignored by `backend/.gitignore` once T-1 creates it; until then protected only by explicit-path staging).
  - `docs/codemie/` (three `analytics/*.json` files, may hold personal data; S5-1: not committed).
  - `.claude/settings.json`: currently modified in the working tree (see Open items); not in the CAP-2 manifest, so not part of this PR unless the human says otherwise.
  - Secrets, `.env*`, key files, editor and OS junk, and anything not in the manifest.

### 3.1 Working tree at preparation time (read-only `git status --short`)
| State | Path |
|---|---|
| tracked, modified | `.claude/settings.json` (`git diff --stat`: 1 deletion; the only change is removal of a duplicated, mis-indented `"Bash(git rev-parse *)"` line in `permissions.allow`; the Maven allow-list lines are already part of base commit `7e63ced`) |
| untracked | `docs/codemie/`, `docs/sdlc/` (both intentional, per S5-1) |
| staged | none |
Nothing else is modified or untracked. The base commit tracks 60 files; none under `docs/sdlc/`, `docs/codemie/`, `backend/` or `.sdlc/`.

## 4. Changelog approach
- Existing convention / file: none (no changelog file among the 60 tracked files; no CONTRIBUTING file).
- Planned entry location: create `docs/changelog.md` with an `Unreleased` section and one entry referencing CAP-2, in Stage 8 draft; committed in commit 9. Subject to the human's agreement at the PR draft.

## 5. Pull request
- Target base branch: `main` on `origin` (name to be confirmed once the human has pushed the base; see Open items 1 and 2). Head: `feature/CAP-2-todo-backend-task-apis`.
- Draft or ready: ready for review
- Reviewers: none unless specified
- Merge: never automatic; performed by the human
- Proposed title (<= 70 characters): `CAP-2: Add Todo backend task management REST APIs`

## 6. Open items
1. **Remote is empty (blocker for Stage 8, not for Stage 5 coding).** The human must push the base commit to `origin` before the PR can be created, for example `git push origin Main:main` (human-run; no agent may push, and the hooks deny agent pushes). Until then there is no PR base and `list_branches` stays `[]`.
2. **Base branch name.** Local `Main` vs GitHub default `main`: the human decides which name the remote base gets. Recommended `main` (lowercase). After the push, the pr-manager re-reads `list_branches` and confirms the base before Stage 8. If the human instead pushes `Main`, the PR base becomes `Main` and this plan changes.
3. **Uncommitted `.claude/settings.json` change.** A harmless duplicate-line removal, carried over unchanged onto the feature branch. It is a framework file outside the manifest. The human decides: commit it to the base first (preferred, on `Main`, human-run), discard it, or leave it uncommitted. The pr-manager will not stage it.
4. **Per-wave commits and `backend/pom.xml`.** See section 2 caveats; the human may prefer a single commit.
5. **Changelog file** (`docs/changelog.md`) creation to be confirmed at the Stage 8 draft.
6. **Orchestrator to record** the feature branch name in `.sdlc/state/CAP-2.json` (plan B-7) so developer edits can pass the branch check after G4. The pr-manager does not write state.
7. Not covered here, still owed at G4 (not pr-manager items): sqlite-jdbc version, acceptance of Maven Central downloads, `mvn wrapper:wrapper` human run (plan 1.2).
