# CAP-1: Add Todo frontend task management UI

<!-- Draft written by pr-manager (MODE draft, attempt 1). No commit, push or PR call has been made. Gate G7 is not reached.
     The repository has no PR template (Glob of `.github/**` in the working tree found nothing; the remote was not searched for a template), so the standard five sections are used.
     Every number below is copied from an artifact under docs/sdlc/CAP-1/; the source is named next to it. -->

**Proposed PR title** (43 characters): `CAP-1: Add Todo frontend task management UI`
**Head:** `feature/CAP-1-todo-frontend-ui`   **Base:** `main`
**This PR will NOT be merged automatically.** It is opened for human review and merged only by a human.

## Summary
Adds the Todo frontend for JIRA CAP-1, "Todo App - Frontend: Task management user interface": a browser UI in a new
`frontend/` directory (plain HTML, CSS and native ES modules, no build step, no npm, no dependency) that lists, adds,
edits, marks Completed or Pending, and deletes (after an in-page confirmation) todo tasks held by the CAP-2 backend.
Because the CAP-2 backend sends no CORS headers (CAP-2 decision S21), a small single-file Java program
(`frontend/server/FrontendServer.java`, JDK 21 or newer per `frontend/README.md`) serves the page on port 8000 and
proxies `/todos` to the backend on `127.0.0.1:8080`, so the page and the API share one origin; `backend/` is unchanged.
**Verification status is PARTIAL (source: `verification-report.md`, overall status line). No acceptance criterion is
fully agent-verified, because no agent can run the UI JavaScript.** Browser self-test results are reported by the human
and the real-backend checks are still open (see Test Evidence and Known Limitations).

- JIRA: [CAP-1](https://agentic-workflow-123.atlassian.net/browse/CAP-1) (site `agentic-workflow-123.atlassian.net`; companion story CAP-2 is the merged backend, PR #1)
- Requirements: 11 FR, 7 NFR, 23 AC (source: `approvals.md` G1 row)

### How to run it (two processes; source `frontend/README.md`)
1. Start the backend first (`backend/README.md`; it listens on `127.0.0.1:8080`).
2. Start the frontend server second: `java server/FrontendServer.java` from `frontend/` (or `java frontend/server/FrontendServer.java` from the repository root). It listens on port 8000, binds to `127.0.0.1` only and needs `java` on the PATH (or `JAVA_HOME`).
3. Open `http://127.0.0.1:8000/` (not the file on disk). Self-test page: `http://127.0.0.1:8000/tests/`.
There is no CORS problem by design (same origin through the proxy), no authentication, and the server is loopback only: anyone who can reach port 8000 on the machine can use and change the tasks (README, "No authentication, loopback only").
The standalone server start was NOT run by any agent (see Known Limitations).

## Changes Made
All 23 files under `frontend/` are new and `docs/changelog.md` gains one entry; nothing is deleted and no existing source file is modified (source: `branch-plan.md` section 3, `verification-report.md` S-6, Glob of `frontend/**/*`). Task ids are from `impl-plan.md`.

| Area | Change | Files |
|---|---|---|
| Static server and proxy (T-1, T-2) | Single-file JDK program: serves `frontend/public`, forwards `/todos` to the backend (status, body and content type passed through; answers 502 when the backend is down), finds `public/` from `frontend/` or from the repository root, refuses to start if `public/index.html` is missing. Black-box test against an in-process stub backend | `frontend/server/FrontendServer.java`, `frontend/server/FrontendServerTest.java` |
| Page and styles (T-5) | One screen: add form always visible, task list, one message area (ARIA live regions), delete confirmation `<dialog>` markup | `frontend/public/index.html`, `frontend/public/css/styles.css` |
| Browser modules (T-3 to T-9) | `validation.js` (code-point length rules after trimming), `api.js` (fetch client, response classification, 10 s abort), `messages.js` (message per outcome), `announcer.js` (live-region announcements), `dialog.js` (confirm dialog), `view.js` (DOM rendering with `textContent`), `app.js` (state and flows), `main.js` (bootstrap) | `frontend/public/js/{validation,api,messages,announcer,dialog,view,app,main}.js` |
| Browser self-test page (T-3 to T-8) | In-browser test harness, fakes and test groups; runs without the backend and prints `N passed, M failed` plus a paste block | `frontend/public/tests/{index.html,harness.js,fakes.js,validation.test.js,api.test.js,messages.test.js,announcer.test.js,dialog.test.js,app.test.js,app-edit-delete.test.js}` |
| Docs (T-9) | How to run, the browser self-test instructions, the manual checklist against the real backend, and a "What is not verified" section | `frontend/README.md` |
| SDLC artifacts | Stage artifacts and workflow record for CAP-1 (12 files in the directory at the time of this draft, including this one) | `docs/sdlc/CAP-1/{requirements,architecture,repo-profile,design-review,impl-plan,branch-plan,clarifications,approvals,implementation-report,code-review,verification-report,pr-description}.md` |
| Changelog | One CAP-1 entry under `Unreleased`; the CAP-2 entry is untouched | `docs/changelog.md` (modified) |

File count: 23 under `frontend/`, 12 under `docs/sdlc/CAP-1/`, 1 changelog = 36 paths. `git diff --stat main...HEAD` is empty and `git log main..HEAD` is empty at the time of this draft: nothing is committed yet; the work is in the working tree.

### Commit strategy
- **One commit**, message `CAP-1: Add Todo frontend task management UI`, ending with the attribution trailer. Source: `branch-plan.md` section 2 and the G4 row of `approvals.md` (the tasks are interdependent, so a per-wave commit would not run on its own).
- Staged by explicit path only: `frontend/` (the 23 files), `docs/sdlc/CAP-1/` (the artifacts), `docs/changelog.md`. Never `git add -A` or `git add .`. The commit, the push of the feature branch and the PR are done only in Stage 8 `publish`, after gate G7 and after the human approves each call.

### Files excluded from this PR (never staged)
- `.claude/settings.json`: a human edit made before this work (the diff removes one duplicated `"Bash(git rev-parse *)"` line; `verification-report.md` V-4 shows `1 deletion`); not part of CAP-1.
- `docs/codemie/`: 4 untracked local analytics files that may hold personal data; not gitignored, so they must be kept out by explicit-path staging.
- `.sdlc/` (gitignored workflow state and audit log), `.claude/settings.local.json` (gitignored).
- `backend/`: unchanged (`verification-report.md` V-3 and V-4: no tracked diff and no untracked path under `backend/`).
- secrets, `.env*`, key files, editor and OS junk.

### Branch and remote state (read-only check for this draft)
- Local: `git branch --show-current` prints `feature/CAP-1-todo-frontend-ui`; `git log --oneline -n 3` shows `9d2bc51` (Merge pull request #1 ... CAP-2), `cacc351`, `7e63ced`, so the branch has no commit of its own and equals `main` at `9d2bc51`. `git status --short` shows ` M .claude/settings.json`, `?? docs/codemie/`, `?? docs/sdlc/CAP-1/`, `?? frontend/`; nothing is staged.
- Remote `suryasnehith-02/claude_capstone` (GitHub `list_branches`): `main` at `9d2bc51a685e45a87ff1c799b3805f9a6a9cd6bb`, `feature/CAP-2-todo-backend-task-apis` at `cacc351879ce3a4bf8589a0d1113a241de0b5846`. **`feature/CAP-1-todo-frontend-ui` does not exist on the remote**, so the PR base exists and the head must be pushed in `publish`.

## Test Evidence
Overall verification status: **PARTIAL** (source: `docs/sdlc/CAP-1/verification-report.md`, round 1, attempt 1; accepted as PARTIAL, with the real-backend checks still pending, by the human at gate G6 in `approvals.md`).

### What an agent executed, and what it did not
- **No agent has ever executed the UI JavaScript** (no JavaScript engine, no browser). Source: `verification-report.md`, "Read this first". Consequently **0 of 23 acceptance criteria are fully agent-verified** (`verification-report.md` section 1).
- The only code an agent executed is the Java server, through its black-box proxy test against a stub backend (not the real backend, no browser code), plus 8 static text checks.
- Everything about UI behaviour rests on the human's reported self-test runs (HUMAN-REPORTED, not agent-verified), or is PENDING HUMAN, or is NOT_RUN.

### Agent-executed (source: `verification-report.md` section 4; all run from the repository root)
| Check | Command | Exit | Result |
|---|---|---|---|
| V-1 Java runtime | `java -version` | 0 | `java version "25.0.5.1" 2026-11-17 LTS` (informational) |
| V-2 Branch | `git branch --show-current` | 0 | `feature/CAP-1-todo-frontend-ui` (informational) |
| V-3 Working tree | `git status --short` | 0 | only the four paths listed above; no path under `backend/` (informational) |
| V-4 Diff stats | `git diff --stat` | 0 | only `.claude/settings.json` (1 deletion) is a tracked change (informational) |
| V-5 Proxy and static-server test | `java frontend/server/FrontendServerTest.java` | 0 | **`32 passed, 0 failed, 1 skipped`**. The one skip is the case "server refuses to start without public/index.html" (skipped whenever `index.html` exists; finding CR-2) |
| S-1..S-8 Static checks (Grep/Read) | no shell; see report section 4 | n/a | 8 of 8 passed. They show how the files read and prove nothing runs: no `innerHTML`/`outerHTML`/`insertAdjacentHTML`/`document.write`/`eval(`/`new Function` under `frontend/public`; no non-ASCII character under `frontend/`; imports resolve; `index.html` loads `js/main.js` as a module; the 21 DOM ids used by the JS exist in `index.html`; the file list equals the 23-file scope manifest; the README test command and Java version match what was run; no `maxlength` attribute (D-6) |

Total: 13 AGENT-EXECUTED PASS (5 commands, 8 static checks), 0 FAIL (`verification-report.md` section 1). V-1 to V-4 are informational, not product checks. The proxy test covers 32 cases: static GET/404/405, `/todosx` not proxied, GET/POST/PUT/PATCH/DELETE on `/todos`, `/todos/abc-123` and `/todos/abc-123/status`, pass-through of 200/201/204/400/404/405/415/500 envelopes and of `400 text/html`, and `502 BAD_GATEWAY` when the backend is down.

### Human-reported, NOT agent-verified (source: `clarifications.md`, `verification-report.md` section 6)
| Browser | Result | Notes |
|---|---|---|
| VS Code Electron browser, `Code/1.147.0 Chrome/150.0.7871.250 Electron/43.8.9`, first run | `263 passed, 7 failed` | All 7 failures were in the real delete-confirmation `<dialog>` (five `dialog: confirmDelete` cases and two `delete with the real dialog` cases) |
| Fix | the developer hardened `frontend/public/js/dialog.js` (one idempotent `finish`; resolution from the Cancel/Delete click, from a state watcher on the `open` attribute, and from the native `close` event) | **The root cause of the original failures was never proven; developer confidence is LOW** (`implementation-report.md` section 12), so the fix is defensive: the symptom is gone in two browsers, the cause is not understood. Five tests were added |
| Microsoft Edge 158 (`Chrome/158.0.0.0 Safari/537.36 Edg/158.0.0.0`), after the fix | **`275 passed, 0 failed`** | The pasted block was cut off; only the summary line is complete |
| VS Code Electron browser (`Chrome/150.0.7871.250 Electron/43.8.9`), after the fix | **`275 passed, 0 failed`** | The per-test paste listed every test by name, all PASS; **that paste is not stored in the repo** (`clarifications.md` keeps only the summary lines and the dialog and delete test names), so the per-test names reach the verification report through the Orchestrator's message and cannot be checked |

Both browsers are Chromium-based. The self-test page runs against fake responses, with no backend.

### PENDING HUMAN (the manual checklist in `frontend/README.md` section 4 has NOT been reported; still open)
1. AC-23 real-backend flow: list, add, edit, status, delete, **no CORS error in the console**, requests going to `http://127.0.0.1:8000/todos...`.
2. AC-3 real load failure with the backend stopped, then Retry with the backend started.
3. A real delete against the real backend (dialog, Cancel, Delete, "Task deleted").
4. Keyboard-only walk-through (Tab order, visible focus, Enter on an empty title, dialog Tab trap, real Escape key).
5. Screen-reader check (best effort).

### Acceptance criteria (source: `verification-report.md` section 6.1)
"Self-test" means HUMAN-REPORTED PASS on the self-test page (`275 passed, 0 failed` in the two browsers above), which is not an agent execution. "Pending" means the real-backend, keyboard or screen-reader part is PENDING HUMAN. No row below is an agent execution of the UI.

| AC | Result | Evidence |
|---|---|---|
| AC-1 | self-test + pending (real backend list) | `app part A ... view and initial load`; checklist |
| AC-2 | self-test only | `view and initial load` |
| AC-3 | self-test (fake API) + agent-executed proxy 502 + pending (real load failure and Retry) | `proxy answers 502 BAD_GATEWAY when the backend is down` (V-5) |
| AC-4 | self-test + pending (real add) | `app part A ... add` |
| AC-5 | self-test only (Enter on empty title via keyboard is pending) | `validation`, `add` groups |
| AC-6 | self-test only | `validation`, `add`, `edit` groups |
| AC-7 | self-test + pending (real edit) | `app part B ... edit` |
| AC-8 | self-test only | `edit` (zero recorded requests on cancel) |
| AC-9 | self-test in two browsers + pending (real delete, real Escape key) | `dialog: confirmDelete` (5 cases), `delete with the real dialog`; the area where the 7 failures were |
| AC-10 | self-test + pending (real status change) | `status change` |
| AC-11 | self-test only (fake API) | failure branches of `add`, `status change`, `edit`, `delete` |
| AC-12 | self-test only | `404 handling and orphan draft` |
| AC-13 | self-test only | no-duplicate-request groups |
| AC-14 | self-test (live-region content in the sandbox) + pending (screen reader) | `announcer` group; best effort |
| AC-15 | self-test only | `messages`, `api` classification groups |
| AC-16 | self-test only | `validation: named vectors` |
| AC-17 | self-test only | `validation`, `add` groups |
| AC-18 | self-test only | `view and initial load` |
| AC-19 | self-test only | `status change`, `keyed reconcile` |
| AC-20 | self-test (UI) + agent-executed (proxy passes `400 text/html` through unchanged) | `api: classification, unexpected 2xx`; V-5 |
| AC-21 | self-test + static check S-1 (supporting only) + pending (real task `<b>x</b><script>1</script>`) | functional display only; no security testing (H-2) |
| AC-22 | self-test only | `404 handling and orphan draft` |
| AC-23 | **PENDING HUMAN** (agent-executed for the stub-proxy half and for "backend unchanged") | V-5 proxy cases; V-3/V-4; no agent started the backend and nobody has reported the browser console |

Counts as stated in the report: 14 ACs rest on the self-test page alone (AC-2, 5, 6, 8, 11, 12, 13, 15, 16, 17, 18, 19, 22, and AC-20 with an agent-executed proxy half); 8 have a self-test pass AND a PENDING HUMAN part (AC-1, 3, 4, 7, 9, 10, 14, 21); 1 is PENDING HUMAN (AC-23). Agent-executed for a whole AC: 0 of 23. The Orchestrator's G6 row says it as "22 of 23 rest on the human's self-test page in whole or part; nine ACs have such a [pending] part".

Requirements (section 6.2): 11 FR (3 self-test only, 8 with a PENDING HUMAN part); NFR-1 PENDING HUMAN (server half agent-executed against a stub, `backend/` unchanged); NFR-2 display as text self-test + static check, security aspect NOT_RUN BY H-2; NFR-3 self-test sandbox tests + keyboard/screen reader PENDING HUMAN (WCAG 2.1 AA best effort, not certified); NFR-4 two browsers only; NFR-5 English, wording checked on the self-test page; NFR-6 abort logic through a fake timer, the 10 s limit NOT_RUN BY H-2; NFR-7 NOT_RUN BY H-2.

## Known Limitations
### Pending human checks (open; accepted as open at G6)
- AC-23 whole real-backend flow with no CORS error; AC-3 real load failure and Retry; a real delete; keyboard-only use; screen reader. Nine ACs have such a part (`approvals.md` G6). The self-test page does not need the backend, so none of these has been seen against the real CAP-2 backend by anyone.

### NOT_RUN by the human's directive H-2 (a verification gap, not a waiver and not a claim that the requirement is met)
- Security testing, including the security aspect of NFR-2 (functional safe display, AC-21, was kept). Hostile or malformed input against the server, path traversal and the proxy dot-segment handling (CR-4) were not tested; path containment (`FrontendServer.java:250-255`) is not exercised by the proxy test.
- Speed and performance (NFR-7), response times and load.
- The 10 s request-timeout limit (NFR-6) as a timing claim; only the abort logic was tested, with a fake timer, on the self-test page.

### Other NOT_RUN (source: `verification-report.md` section 10)
- Execution of any UI JavaScript by an agent.
- `java server/FrontendServer.java` standalone and the `frontend/`-relative form of the test command (only the repository-root form was run); both `JAVA_HOME` forms in the README; starting the backend (`./mvnw spring-boot:run` or the packaged JAR).
- Proxy test case "server refuses to start without public/index.html": SKIPPED by the test.
- The 504 gateway-timeout path, malformed percent paths and a 413 oversize body through the proxy: no test covers them.
- Browsers other than Microsoft Edge 158 and the VS Code Electron browser (Chrome 150): standalone Chrome, Firefox and Safari were not reported. Both tested browsers are Chromium-based, so there is no evidence for Firefox or Safari.
- Build, lint, type check and dependency audit: none is defined in the repository (no CI, no manifest; the README says no build step and no dependency).

### Known behaviour limit, accepted
- **Tomcat HTML 400.** For a malformed percent sequence in a path (for example `/todos/%zz`), the backend's Tomcat answers its own `400 text/html` page and the frontend server passes it through unchanged (proxy case `proxy passes through 400 text/html`, PASS); the UI then shows the generic error "Something went wrong. Please try again." (`frontend/README.md`). How the frontend server itself treats a malformed percent sequence in a static path was not exercised.

### Open code-review findings (source: `code-review.md`, round 3 verdict PASS: 0 critical, 0 major, 5 minor, 6 nit open; `waived_critical` 0)
The review was static (a reading review; the reviewer executed nothing). Resolved earlier: CR-1 (README promised a root start the server lacked), CR-5 (focus after Retry) and CR-6 (pending text in the accessible name), both resolved at test level and HUMAN-REPORTED, not agent-verified; CR-7 is superseded (`nextClose` removed).

| Id | Severity | Wording from code-review.md | Status |
|---|---|---|---|
| CR-2 | minor | Skip guards for files that now exist can only hide a regression; the refuse-to-start case is skipped whenever `index.html` exists, so the T-1 refuse rule is never exercised in a normal run (`FrontendServerTest.java`). | open (human decision: keep open) |
| CR-3 | minor | Proxy fidelity cases use no percent-escaped path; only `Content-Type` is checked among request headers; `Accept` forwarding, "no other header copied", `Cache-Control: no-store` and HEAD are not asserted. | open (human decision: keep open) |
| CR-4 | minor | Proxy rule is a prefix test on the raw path, and the raw path is appended to the backend base without dot-segment handling; latent exposure of a future backend endpoint through port 8000 (code reading only, no testing per H-2). | open (human decision: keep open) |
| CR-8 | minor | Test B run instructions only appear as a side note in "What is not verified" in `frontend/README.md`; no run section and no paste-back instruction. | open (human decision: keep open) |
| CR-14 | minor | `dialog.js` resolves `confirmDelete` through three routes into one idempotent `finish` (Cancel/Delete click handlers, a `MutationObserver` on `open`, the native `close` event), while `architecture.md` (C-11, I-7, section 8.2, R-5, DR-11) says it resolves once, in the `close` handler. Code review noted `architecture.md` was not amended. | **accepted deviation: the human accepted it at G5** (`approvals.md` G5 row); whether `architecture.md` was amended afterwards was not checked for this draft |
| CR-9 | nit | No body size cap, no `Host`/`Origin` check, lexical containment (symlinks followed) in the server. | open |
| CR-10 | nit | `pending` set and cleared without `try`/`finally` in `app.js`; a stuck row if a DOM bug throws between set and clear. | open |
| CR-11 | nit | `FIELD_ORDER` and the field-message join exist twice (`app.js`, `messages.js`). | open |
| CR-12 | nit | `focusAddSubmit` and `hasRow` are exported and unused. | open |
| CR-13 | nit | `#boot-note` in `index.html` hard-codes `http://127.0.0.1:8000/`. | open |
| CR-15 | nit | The native `close` handler is only exercised by one synthetic `new Event('close')`; the `MutationObserver` has no handle and no `dispose()`. | open |

### Environment and versions (as reported; none verified independently by me)
- `java -version` printed `25.0.5.1` in the agent shell (V-1, agent-executed); `frontend/README.md` says JDK 21 or newer is needed (the server uses virtual threads). Only JDK 25.0.5.1 was run; JDK 21 was not.
- Browser versions are those printed in the human's pasted user-agent strings (Edge 158, Chrome 150 inside VS Code `Code/1.147.0` Electron 43.8.9); no agent saw them.
- Two processes, backend first (port 8080) and the frontend server second (port 8000); `java` must be on the PATH or `JAVA_HOME` used; no authentication; loopback only. These come from `frontend/README.md` and `approvals.md`; the standalone start was not run.
- `.claude/settings.json` differs in the working tree from `HEAD`; it is excluded from this PR.

## Reviewer Checklist
- [ ] **Decide whether merging with the real-backend checks still pending is acceptable** (AC-23 no CORS error, AC-3 real load failure and Retry, real delete, keyboard-only use, screen reader). Nobody has seen the UI against the real backend; verification is PARTIAL
- [ ] **Run the manual checklist in `frontend/README.md`** (section 3 self-test page, about 2 minutes; section 4 against the real backend with both processes up, about 10 minutes) and paste the results; anything not done stays NOT_RUN
- [ ] Requirements and acceptance criteria are met (see `docs/sdlc/CAP-1/requirements.md`): no AC is agent-verified; 14 rest only on the human's self-test page, 8 also have a pending part, AC-23 is pending
- [ ] Design matches `docs/sdlc/CAP-1/architecture.md` (design review round 2: APPROVE; the CR-14 deviation in `dialog.js` was accepted at G5; check whether `architecture.md` should be amended)
- [ ] Tests are meaningful and cover failure paths (32-case Java proxy test with 1 skip, CR-2 and CR-3 open; 275-test browser self-test page that no agent has run; the 7 first-run dialog failures were fixed defensively and the cause was never proven)
- [ ] Security-sensitive code paths reviewed by a human, because no security testing was done (H-2): the proxy and path handling in `FrontendServer.java` (CR-4, CR-9), safe display with `textContent` in `view.js`/`dialog.js` (static check S-1 only), loopback-only binding and no authentication
- [ ] The Tomcat HTML 400 pass-through for malformed percent paths is acceptable
- [ ] Documentation and changelog updated (`frontend/README.md`, `docs/changelog.md`; CR-8 notes the test run instructions are thin)
- [ ] No unexpected files in the diff: exactly 23 files under `frontend/`, the CAP-1 artifacts and `docs/changelog.md`; nothing from `.sdlc/`, `docs/codemie/`, `backend/`; `.claude/settings.json` not changed by this PR
- [ ] Migration / rollback considered: n/a for deployment (new directory, no data or schema; no existing file is modified except the changelog; `backend/` unchanged). Rollback is reverting the PR; the frontend writes no files

## Workflow record
| Item | Value (source: `approvals.md`, `.sdlc/state/CAP-1.json`) |
|---|---|
| G1 requirements | approved |
| G2 architecture (design review round 2: APPROVE, 0 critical, 0 major) | approved |
| G3 implementation plan (9 tasks T-1..T-9) | approved |
| G4 implementation kickoff (branch, scope, authorised commands, plus the repo-root extension) | approved |
| G5 code review (round 3 PASS; 0 critical, 0 major, 5 minor, 6 nit open; CR-14 accepted) | approved |
| G6 verification (PARTIAL accepted, real-backend checks still pending) | approved |
| G7 final approval before the PR | **not reached** (state `gate_g7: not_reached`); the PR is not created until it is approved |
| Waived critical findings | **0** (`waived_critical: 0`; `open_critical: 0`, `open_major: 0`) |
| Tasks | 9 of 9 done (`tasks_done: 9`) |
| Human directives | H-1 keep it fast; H-2 no security, speed or performance testing; H-3 no gate or stage skipped |
| Jira / GitHub writes | none by this stage (read-only `list_branches`; no branch, commit, push or PR call) |

## Related artifacts
All under `docs/sdlc/CAP-1/`: [requirements.md](requirements.md) - [architecture.md](architecture.md) - [design-review.md](design-review.md) - [impl-plan.md](impl-plan.md) - [implementation-report.md](implementation-report.md) - [code-review.md](code-review.md) - [verification-report.md](verification-report.md) - [approvals.md](approvals.md) - [clarifications.md](clarifications.md) - [repo-profile.md](repo-profile.md) - [branch-plan.md](branch-plan.md). Manual checklist: [`frontend/README.md`](../../../frontend/README.md).

🤖 Generated with [Claude Code](https://claude.com/claude-code)
