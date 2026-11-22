# Branch plan - CAP-1

Mode: prepare (before Stage 5), attempt 1. Written by pr-manager. Inputs: `impl-plan.md` (G3 approved, section 4 scope manifest, section 8 K-1), `approvals.md` (G1-G3), CAP-2 `branch-plan.md`.
Everything below marked "read" was read-only (git, GitHub MCP get_me / list_branches). The only mutating action was the local `git switch -c`. Nothing was fetched, staged, committed or pushed, and nothing was written on GitHub.

## 1. Branches
| Item | Value |
|---|---|
| Base branch (local) | `Main` (capital M) at `9d2bc51a685e45a87ff1c799b3805f9a6a9cd6bb` ("Merge pull request #1 from suryasnehith-02/feature/CAP-2-todo-backend-task-apis"). `git rev-parse Main origin/main` gives the same SHA for both |
| Base branch (remote default) | `main` at `9d2bc51a685e45a87ff1c799b3805f9a6a9cd6bb` (`list_branches`; `protected: false` on the GitHub side, but protected by `.claude/hooks/protected-branches.txt`) |
| Feature branch | `feature/CAP-1-todo-frontend-ui` (30 characters, lowercase, no spaces; follows the `feature/<KEY>-<slug>` convention of `feature/CAP-2-todo-backend-task-apis`) |
| Remote | `origin` = `https://github.com/suryasnehith-02/claude_capstone`; authenticated GitHub user `suryasnehith-02` (id 167758630) |
| Protected-branch check | not in `.claude/hooks/protected-branches.txt`: **yes** (list: main, master, develop, development, trunk, release/*, releases/*, hotfix/*). The feature branch matches none. Local `Main` matches `main` case-insensitively and is protected; it was not touched |
| Branch exists already (local / remote) | before: local no (`git branch -a -vv` showed only `Main` and `feature/CAP-2-todo-backend-task-apis`); remote no (`list_branches` returned only `main` and `feature/CAP-2-todo-backend-task-apis`). Now: local yes (created), remote none |
| Currently checked out | `feature/CAP-1-todo-frontend-ui` at `9d2bc51`, created with `git switch -c feature/CAP-1-todo-frontend-ui` (exit 0, "Switched to a new branch"). `Main` is still at `9d2bc51` |

### 1.1 Remote findings (read-only)
- `get_me`: `suryasnehith-02`, the same account as the remote URL owner.
- `list_branches` on `suryasnehith-02/claude_capstone`: `main` (`9d2bc51...`) and `feature/CAP-2-todo-backend-task-apis` (`cacc351...`). No `feature/CAP-1-todo-frontend-ui`.
- Local `Main` equals `origin/main` (`9d2bc51`). The remote-tracking ref also agrees with the GitHub API result (`main` = `9d2bc51`), so it is not stale. No fetch was done.
- Pre-existing, left alone: local and remote `feature/CAP-2-todo-backend-task-apis` still exist at `cacc351` (the merged CAP-2 branch). No agent deletes branches; the human may delete them.
- The PR base `main` exists and contains `9d2bc51`, so Stage 8 can target it.

## 2. Commit strategy
- Message style (evidence: `git log`: `CAP-2: Add Todo backend task management REST APIs`, `Add Agentic SDLC framework`; no CONTRIBUTING file): `CAP-1: <imperative summary>`, first line at most 72 characters, ending with the attribution trailer in force for the session.
- Proposed: ONE commit (the human chose a single commit for CAP-2; he may change this at G4). The frontend tasks are interdependent and no per-wave commit would build or run on its own.
- Commits, push and PR happen **only in Stage 8 after G7**. No agent commits or pushes before then (hooks deny it). Files are staged by explicit path only, never `git add -A` or `git add .`; `git status` is checked before committing.

| # | Scope (tasks) | Message |
|---|---|---|
| 1 | T-1..T-9 (all of `frontend/`), `docs/sdlc/CAP-1/` artifacts, changelog entry | `CAP-1: Add Todo frontend task management UI` |

## 3. Files
- Included (scope manifest, `impl-plan.md` section 4; all created, none deleted):
  - `frontend/server/FrontendServer.java`, `frontend/server/FrontendServerTest.java` (T-1, T-2)
  - `frontend/public/index.html`, `frontend/public/css/styles.css` (T-5)
  - `frontend/public/js/{validation,api,messages,announcer,dialog,view,app,main}.js` (T-3 to T-9)
  - `frontend/public/tests/{index.html,harness.js,fakes.js,validation.test.js,api.test.js,messages.test.js,announcer.test.js,dialog.test.js,app.test.js,app-edit-delete.test.js}` (T-3 to T-8)
  - `frontend/README.md` (T-9)
  - `docs/sdlc/CAP-1/*.md` (requirements, architecture, design-review, impl-plan, repo-profile, clarifications, approvals, branch-plan, implementation-report, code-review, verification-report, pr-description, and any other artifact in that directory)
  - `docs/changelog.md` (modify; Stage 8)
- Commit-time check: any file under `frontend/` not in the list above is unexpected and is flagged, not staged.
- Excluded (never staged):
  - `.sdlc/` (gitignored runtime state and audit log), `.claude/settings.local.json` (gitignored).
  - `.claude/settings.json`: modified in the working tree, the human's cosmetic edit, not in the manifest.
  - `docs/codemie/`: untracked analytics files that may hold personal data; never committed.
  - Anything generated or logged under `frontend/` (the frontend writes no files by design), secrets, `.env*`, key files, editor and OS junk, `backend/**` (AC-23: `git diff --stat -- backend/` must be empty), and anything not in the manifest.

### 3.1 Working tree at preparation time (read-only `git status --short`, before and after the switch; identical)
| State | Path |
|---|---|
| tracked, modified | `.claude/settings.json` (`git diff --stat`: 1 deletion; Git warns about LF to CRLF conversion) |
| untracked | `docs/codemie/` (never commit), `docs/sdlc/CAP-1/` (this workflow's artifacts; go into the CAP-1 PR) |
| staged | none |

### 3.2 Dirty-tree handling
The `github-pr-management` skill says to stop when unrelated uncommitted changes exist. The G3 row of `approvals.md` records the human's (not objected to) default: leave the unrelated items untouched and carry them over with `git switch -c`; stage explicit paths only at Stage 8. This was followed: `git switch -c` carried the three items over unchanged, and no item was staged, stashed, reverted or committed.

## 4. Changelog approach
- Existing convention / file: `docs/changelog.md`, created by the CAP-2 PR (last touched in `cacc351`). The pr-manager re-reads it in `draft` mode and follows its format and category headings.
- Planned entry location: one CAP-1 line under `Unreleased` in `docs/changelog.md`, written at Stage 8 `draft` (not by the developer); released sections are not edited. Committed in the single commit.

## 5. Pull request
- Target base branch: `main` on `origin` (exists, `9d2bc51`). Head: `feature/CAP-1-todo-frontend-ui`.
- Draft or ready: ready for review
- Reviewers: none unless specified
- Merge: never automatic; performed by the human
- Proposed title (<= 70 characters): `CAP-1: Add Todo frontend task management UI`

## 6. Open items
1. **Branch name and single-commit strategy** are proposals; the human approves them at G4 and may change either. If renamed, the local branch must be renamed by a new `prepare` step.
2. **Orchestrator to record** the feature branch name in `.sdlc/state/CAP-1.json` so developer edits can pass the branch check after G4. The pr-manager does not write state.
3. **`.claude/settings.json` edit** stays uncommitted and unstaged on the feature branch. If the human wants it kept, he commits it himself (preferably separately); the pr-manager will not stage it.
4. **`docs/codemie/`** stays untracked and is never committed. The human may add it to a gitignore or remove it (an agent must not).
5. **Stale CAP-2 branches** (`feature/CAP-2-todo-backend-task-apis`, local and remote) remain; cleanup is the human's decision.
6. **Pre-Stage 5 permission items** are not pr-manager items but are still owed at G4: the three `java` entries in the human's `.claude/settings.local.json` (otherwise test B is human-run or NOT_RUN).
7. The commit, push and PR are not prepared yet. They need `gate_g7: approved` in `.sdlc/state/CAP-1.json` and the human's approval of each mutating call.
