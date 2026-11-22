# Implementation report - CAP-1

Branch: feature/CAP-1-todo-frontend-ui (checked with `git branch --show-current`)    Round: 4 (waves 1 to 4, complete)    Tasks: T-1..T-9

Nothing was committed, pushed or staged by any agent. **The UI JavaScript (every file under `frontend/public/`) has never been executed by any agent**: no JavaScript engine was available, and every browser result is HUMAN-REPORTED later (PENDING). The only code an agent has executed is the Java proxy and static-server test (test B) against `FrontendServer.java`; the Grep static checks below only read text and do not show that any file parses or runs.

## 1. Task status
| Task | Status | Notes |
|---|---|---|
| T-1 | done | `frontend/server/FrontendServer.java`; executed through test B (wave-1 agent run and the re-run in section 4) |
| T-2 | done | `frontend/server/FrontendServerTest.java`; last run `31 passed, 0 failed, 1 skipped`, exit 0 (section 4) |
| T-3 | done (written, NOT_RUN: awaiting the human's browser run) | harness, fakes, validation, tests |
| T-4 | done (written, NOT_RUN) | `api.js`, `messages.js`, tests |
| T-5 | done (written, NOT_RUN) | page shell, CSS, announcer, dialog, tests |
| T-6 | done (written, NOT_RUN) | `view.js`; tested through `app.test.js` and `app-edit-delete.test.js` as the plan says (no separate view test file in the scope manifest) |
| T-7 | done (written, NOT_RUN) | `app.js` part A; `app.test.js` |
| T-8 | done (written, NOT_RUN) | `app.js` part B; `app-edit-delete.test.js` |
| T-9 | done (written; `main.js` NOT_RUN in a browser) | `main.js`, `frontend/README.md`, this report, final test B run, static import checks |

Totals: 9 done, 0 pending, 0 blocked. "Done" for T-3..T-9 means written and statically checked, not run.

## 2. Files changed
| Path | Action | Task | In scope manifest? |
|---|---|---|---|
| `frontend/server/FrontendServer.java` | created (wave 1) | T-1 | yes |
| `frontend/server/FrontendServerTest.java` | created (wave 1) | T-2 | yes |
| `frontend/public/js/validation.js` | created | T-3 | yes |
| `frontend/public/tests/harness.js` | created | T-3 | yes |
| `frontend/public/tests/fakes.js` | created (T-3), extended with `createFakeApi`, `ok`, `fail` (T-7) | T-3, T-7 | yes |
| `frontend/public/tests/validation.test.js` | created | T-3 | yes |
| `frontend/public/tests/index.html` | created (T-3); import list extended (T-4, T-5, T-7, T-8) | T-3 | yes |
| `frontend/public/js/api.js` | created | T-4 | yes |
| `frontend/public/js/messages.js` | created | T-4 | yes |
| `frontend/public/tests/api.test.js` | created | T-4 | yes |
| `frontend/public/tests/messages.test.js` | created | T-4 | yes |
| `frontend/public/index.html` | created | T-5 | yes |
| `frontend/public/css/styles.css` | created | T-5 | yes |
| `frontend/public/js/announcer.js` | created | T-5 | yes |
| `frontend/public/js/dialog.js` | created | T-5 | yes |
| `frontend/public/tests/announcer.test.js` | created | T-5 | yes |
| `frontend/public/tests/dialog.test.js` | created | T-5 | yes |
| `frontend/public/js/view.js` | created | T-6 | yes |
| `frontend/public/js/app.js` | created (T-7), extended (T-8) | T-7, T-8 | yes |
| `frontend/public/tests/app.test.js` | created | T-7 (and T-6 view checks) | yes |
| `frontend/public/tests/app-edit-delete.test.js` | created | T-8 | yes |
| `frontend/public/js/main.js` | created (wave 4) | T-9 | yes |
| `frontend/README.md` | created (wave 4) | T-9 | yes |
| `docs/sdlc/CAP-1/implementation-report.md` | created (wave 1), updated (waves 2, 3, 4) | T-9 | yes |

23 files under `frontend/` (21 before wave 4, plus `main.js` and `README.md`), 24 paths with this report. `backend/` and `.claude/` were not edited by this agent. No dependency, no build file, no CDN, no deletion. A Grep for a non-ASCII byte over all of `frontend/` found no match after the wave-4 files were written (AGENT-EXECUTED, Grep tool).

## 3. Tests added or updated
Test B (`FrontendServerTest.java`, wave 1, unchanged in wave 4): static cases, 15 proxy cases (5 methods on 3 paths), 9 pass-through cases (200, 201, 204 without a body, 400 envelope, 404, 405, 415, 500, `400 text/html`), `/todosx` not proxied, 502 `BAD_GATEWAY` with the stub stopped, and a "refuses to start without `public/index.html`" case. It does not need a browser and runs against an in-process stub, not the real backend. Result of the final run: section 4.

Wave 2 tests (all run in the browser at `/tests/`; none executed by an agent; NOT_RUN until the human pastes the `N passed, M failed` line, then HUMAN-REPORTED):
| Test file | Covers | Level |
|---|---|---|
| `validation.test.js` | 25 backend White_Space code points (list and exhaustive U+0000..U+FFFF comparison), strips each at both ends, 11 not-whitespace code points kept; named vectors: U+FEFF kept, U+0085 stripped, U+001C..U+001F kept, U+180E kept, U+3000 stripped, emoji counts 1 (100/101 title, 500/501 description); lone surrogates, null/undefined; boundaries 100/101 and 500/501; messages word for word; trimmed values returned (AC-5, AC-6, AC-16, AC-17). Also holds the harness meta-test (counters, async, hooks, timeout) and fakes sanity tests | unit |
| `api.test.js` | every 7.1 request (method, path, headers, body, encoded id, base path, abort signal); every 7.2 step: network, timeout before headers, timeout during `text()`, 204 delete, 204 elsewhere, wrong code, non-JSON/empty 2xx, 14 wrong shapes, 400 VALIDATION_ERROR with filtered/missing/malformed details, 404 TODO_NOT_FOUND, 11 other code/status pairs, non-2xx without envelope incl. `400 text/html`; never rejects; timer armed before fetch and cleared after (AC-3, AC-11, AC-15, AC-20; the abort logic of NFR-6, not the 10 s timing) | unit with fake fetch and fake timer |
| `messages.test.js` | one row per cell of the 7.2 table, field order, other-field sentence, refresh flag, no code/status/raw text in any message (AC-11, AC-12 mapping, AC-15, FR-9) | unit, table-driven |
| `announcer.test.js` | live regions, success/error routing, identical repeat, DR-13a sequence number, `clear()`, textContent only; plus the `index.html` markup contract (ids and order, live-region roles, `tabindex="-1"` on `#list-heading` and `#orphan-heading`, data: favicon, module script `js/main.js`, no inline script) (AC-14, AC-2, AC-21) | unit and markup |
| `dialog.test.js` | text via textContent, initial focus on Cancel, Cancel/Delete/Escape-simulation/close each resolve once, re-entry rules (DR-12), Tab trap, missing `returnFocus`, `showModal` throwing (AC-9 dialog part, case (g)) | unit in a sandbox mount |

Wave 3 tests (browser, NOT_RUN until the human pastes the result):
| Test file | Covers | Level |
|---|---|---|
| `app.test.js` | View (loading text, order, row content, Completed vs Pending by text, AC-21 literal text, empty state, keyed reconcile). Load: AC-1, AC-2, AC-3, AC-18. Add: case (d), AC-5, AC-6, AC-4, AC-17, AC-13, case (e), DR-3. Status: AC-19, case (e). Ordering: case (c), stale generation, DR-13b, DR-1 | unit and integration |
| `app-edit-delete.test.js` | Edit (AC-7, AC-8, AC-15, case (e)). 404: cases (a), (b), DR-14a, case (f). Delete: DR-14c, DR-12, AC-13, AC-9, real dialog | unit and integration |

Wave 4: no new test file. `main.js` has no unit test (it only wires two imports); it is exercised only by the real page (checklist section 4 of the README), HUMAN-REPORTED when reported.

Not tested, by design: the 10 s timing (NOT_RUN by H-2; only the abort logic is tested with a hand-fired fake timer), security, speed, performance (H-2), the real Escape key and native `close` order (UNVERIFIED, R-5), the real `fetch` through the proxy against the real backend (checklist D), the 504 path.

## 4. Commands executed
Every command the agents ran for CAP-1 stage 5, with exit codes. Nothing else was run (no Maven, npm, pip, install, curl, kill or git write).

| # | Wave / agent | Command (working directory: repo root) | Exit | Result | Label |
|---|---|---|---|---|---|
| 1 | wave 1, developer | `java -version` | 0 | `java version "25.0.5.1"` (the human's G4 extension row records `java version 25.0.5.1 2026-11-17 LTS`) | AGENT-EXECUTED |
| 2 | wave 1, developer | `java server/FrontendServerTest.java` | 1 | `Could not find or load main class server.FrontendServerTest.java` (wrong working directory, repository root) | AGENT-EXECUTED (failed, no test result) |
| 3 | after G4 extension, another agent in this workflow | `java frontend/server/FrontendServerTest.java` | not recorded here | `30 passed, 0 failed, 2 skipped` (this agent did not see that run; which two cases skipped is not recorded here, but run 5 shows that `/js/main.js` was one of the cases that needed a file created in wave 4) | reported by the Orchestrator, not re-observed by this agent |
| 4 | wave 4, this agent | `git branch --show-current` | 0 | `feature/CAP-1-todo-frontend-ui` | AGENT-EXECUTED |
| 5 | wave 4, this agent | `java frontend/server/FrontendServerTest.java; echo "EXIT=$?"` | 0 | see below | AGENT-EXECUTED |

Run 5 verbatim (the last line is the summary; `EXIT=0`):
```
SKIPPED server refuses to start without public/index.html (public/index.html exists)
PASS static GET / returns text/html
PASS static GET /js/main.js returns text/javascript
PASS static GET /tests redirects to /tests/
PASS static missing file returns 404
PASS static POST returns 405
PASS /todosx is not proxied
PASS proxy GET /todos            (and the same PASS lines for /todos/abc-123 and /todos/abc-123/status)
PASS proxy POST, PUT, PATCH, DELETE on the same three paths (12 more PASS lines)
PASS proxy passes through 200 json, 201 json, 204 no body, 400 envelope, 404 envelope, 405 envelope,
     415 envelope, 500 envelope, 400 text/html (9 PASS lines)
PASS proxy answers 502 BAD_GATEWAY when the backend is down
31 passed, 0 failed, 1 skipped
EXIT=0
```
Verbatim summary line: `31 passed, 0 failed, 1 skipped`. The exit was clean (0). It matches the figure derived from the code in advance (31/0/1). `static GET /js/main.js returns text/javascript` PASSES now that `main.js` exists (that case skips while the file is missing, per its guard in `FrontendServerTest.java:145-146`).

Why 1 skipped remains: the case `server refuses to start without public/index.html` can only run when `public/index.html` is missing, and the test never changes files, so with the real `index.html` present it always prints SKIPPED. The plan's wording "final run shows `0 skipped`" cannot be met by this case as written (see section 6, deviations). It is not a failure and the exit code is 0.

Static checks (AGENT-EXECUTED with the Grep tool and Read; they read text only and do not show that anything parses or runs):
- `index.html` line 59 is `<script type="module" src="js/main.js"></script>` (Read), and `announcer.test.js` asserts that attribute. `main.js` exists.
- Grep `from ['"]|import\(|import ['"]` in `frontend/public/`: 7 import lines in the app (`app.js` x5, `main.js` x2) plus the test-file imports and the dynamic `import(path)` in `tests/index.html`. Resolved by reading the exports (`^export ` Grep and Read):
  - `main.js` imports `createApiClient` from `./api.js` (exported, `api.js:100`) and `createApp` from `./app.js` (exported, `app.js:38`). Both used with the real names: `createApp({ root: document.getElementById('app'), api: createApiClient() })`. `createApp` starts the first load itself (`autoStart` defaults to true, `app.js:46,483`); `main.js` then sets `hidden` on `#boot-note` (styles.css has `[hidden] { display: none !important; }`).
  - `app.js` imports `createView` (`view.js:21`), `createAnnouncer` (`announcer.js:12`), `createConfirmDialog` (`dialog.js:16`), and namespaces of `validation.js` and `messages.js` (it uses `validation.validateTask` and `messages.outcomeFor`, `MESSAGES`: all exported). It calls `api.listTasks`, `createTask`, `setStatus`, `replaceTask`, `deleteTask`: all five exist in the object returned by `createApiClient` (`api.js:145-151`).
  - `view.js`, `api.js`, `messages.js`, `validation.js`, `announcer.js`, `dialog.js`, `harness.js`, `fakes.js` import nothing.
  - Test files import only existing names: `harness.js` exports `describe, it, assert, beforeEach, afterEach, createRunner, run, renderReport`; `fakes.js` exports `settle, jsonResponse, errorResponse, task, createFakeFetch, createFakeTimer, createManualDefer, ok, fail, createFakeApi, mountSandbox`; `validation.js` exports `WHITE_SPACE_CODE_POINTS, codePointLength, isWhiteSpace, trimText, validateTask` (the multi-line import in `validation.test.js:8-14` was read directly).
  - `tests/index.html` imports `it, run, renderReport` from `./harness.js` and dynamically imports 7 files, all of which exist (Glob).
- Grep `innerHTML|outerHTML|insertAdjacentHTML|document\.write|eval\(` in `frontend/public/js/`: no match (all files, including `main.js`). This is a static text search, not a security test.
- Grep for a non-ASCII byte in `frontend/`: no match.
- `git diff --stat -- backend/`: NOT run in wave 4 (the delegation allowed only `git branch --show-current`); it is a checklist item for the human. For reference only, the git status the Orchestrator showed at the start lists no path under `backend/` (not a check by this agent).

## 5. Documentation updated
- `frontend/README.md` (new): working-directory rule, start order (backend, then `java server/FrontendServer.java` with `--port` and `--backend-port`, Git Bash and cmd `JAVA_HOME` forms), the URLs `http://127.0.0.1:8000/` and `/tests/`, what to paste back, the manual checklist with tick boxes (AC-23 flow and no CORS error, AC-3 stop and Retry, AC-21 with a real task, Enter on an empty title, keyboard-only walk-through, dialog focus trap and Escape, screen reader best effort, browsers used, favicon note, `git diff --stat -- backend/`) and "What is not verified". Every command in it is marked run or not run.
- Header comments naming the source of each rule in the earlier files.
- No change to `backend/README.md`, the changelog (Stage 8) or `docs/`.

## 6. Deviations from the plan
| Deviation | Reason | Approved by |
|---|---|---|
| The plan's DoD says the final test B run "shows `0 skipped`"; it shows `1 skipped` | Wave 1 added a case "refuses to start without `public/index.html`" that is skipped whenever the file exists (the test never alters files). With `index.html` and `main.js` both present every other case ran and passed | pending (small; no code change made) |
| Wave 1 deviations (T-2 finds `frontend/` by `server/FrontendServer.java`; extra "refuses to start" case; `.json` MIME; HEAD without `Content-Length`) | see wave-1 text | pending (small) |
| Harness meta-test and fakes sanity tests are in `validation.test.js`, and the `index.html` markup contract tests are in `announcer.test.js` | The scope manifest has no `harness.test.js` or page-markup test file | pending (small) |
| `confirmDelete` and the injected `confirm` take one object `{id, title, returnFocus}` (I-7 shows `confirm(title)`) | DR-12 needs the task id; the caller supplies the focus target | pending (interface detail inside C-11, no external interface) |
| `index.html` has a visible `#boot-note` paragraph outside `#app`; `main.js` hides it after `createApp` returns | R-8: `<noscript>` cannot help when JS is on but modules fail under `file://`. If an import or `createApp` fails, the note stays visible | pending (small) |
| `main.js` does not call `load` itself | `createApp` starts `load('initial')` by default (`autoStart`), so a second call would send two requests | n/a |
| `api.js` result objects carry extra `status`, `code`, `details`; `api.js` sends only `title` and `description`, no trimming | Asked for by the wave-2 delegation; trimming is `validateTask`; `messages.js` ignores the extras (a test proves no message contains them) | pending (small) |
| `pending` is a `Map<id | 'add', kind>` not a `Set` | The view derives pending text and the Edit lock from the kind | n/a (internal state) |
| `createApp` takes extra injectable collaborators (`view`, `validation`, `messages`, `autoStart`) and returns `{ready, load, view, getState}` | Dependency injection for the self-test page | n/a (I-8 extended) |
| A dirty re-run of `initial` or `retry` keeps its reason for the failure announcement but does not clear the message on success | DR-13b; clearing would wipe the "Task added" announcement | pending (small) |
| No `view.test.js`; `fakes.js` gained `createFakeApi`, `ok`, `fail` | Not in the scope manifest; the plan assigns view tests to the two app test files; `fakes.js` is in the manifest | n/a |
| The `git diff --stat -- backend/` and `git diff` checks in the T-9 acceptance criteria were not run | The wave-4 delegation authorised only `git branch --show-current`; the check is on the human's checklist | pending (the Orchestrator may run it, `Bash(git diff *)` exists per the plan) |

## 7. Review and verification findings addressed (rework rounds)
Code review (Stage 6): CR-1, CR-5, CR-6, CR-7 addressed, see the section "Rework after code review" at the end of this report. CR-2, CR-3, CR-4, CR-8 and the nits are left open by the human's decision. Carry-overs implemented and written (code and tests; NOT_RUN): DR-1, DR-2, DR-3, DR-4, DR-12, DR-13a, DR-13b, DR-14a, DR-14b, DR-14c, each with a named test in `announcer.test.js`, `dialog.test.js`, `app.test.js` or `app-edit-delete.test.js`.

## 8. Evidence labels by acceptance group
| Group | What exists | Label |
|---|---|---|
| Proxy and static server: NFR-1 (server half), AC-23 (stub, partial), AC-3 (502 path), AC-20 (`400 text/html` passes through unchanged) | test B, run 5: `31 passed, 0 failed, 1 skipped`, exit 0, against a stub backend | AGENT-EXECUTED (functional, Java only) |
| Static text checks: imports and exports resolve, no `innerHTML`-family call, ASCII only, `index.html` loads `js/main.js` as a module | Grep and Read (section 4) | AGENT-EXECUTED (reading only; does not prove parsing or running) |
| AC-1..AC-22 (UI behaviour), the abort logic of NFR-6, the `index.html` markup contract | self-test page `/tests/` written (T-3..T-8) | NOT_RUN by agents; HUMAN-REPORTED PENDING (the human pastes `N passed, M failed`, the paste block and the browser) |
| AC-23 real backend (no CORS error), AC-3 with the backend stopped then started, AC-21 with a real task, AC-14 keyboard and screen reader, NFR-3, NFR-4 browsers used, `git diff --stat -- backend/` | checklist in `frontend/README.md` | HUMAN-REPORTED PENDING; unreported items are NOT_RUN |
| Security (NFR-2 security aspect), speed, performance (NFR-7), the 10 s timing claim (NFR-6) | nothing designed or run | NOT_RUN by decision H-2 |

## 9. Definition of done check (impl-plan section 7; the security, speed and performance items are excluded by H-2)
- [x] T-1..T-9 implemented (written). Where an acceptance criterion needs a browser it is handed to the human run with exact steps in `frontend/README.md`; none is verified by an agent.
- [~] Test B result recorded with its true label (AGENT-EXECUTED, output and exit code verbatim: section 4). The plan's "shows `0 skipped`" is NOT met: `1 skipped` by design (section 6).
- [ ] Self-test page A: PENDING the human's pasted `N passed, M failed` line (needs `0 failed`).
- [ ] Checklist D: PENDING, item by item, with browsers and versions (NFR-4).
- [ ] DR-12, DR-13, DR-14: a named test exists for each (written); the passing human report is PENDING.
- [~] Static search found no `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write` or `eval(` in `frontend/public/js/` (AGENT-EXECUTED, Grep). `git diff --stat -- backend/` empty: NOT run in this round (section 6); on the human's checklist.
- [x] `frontend/README.md` and this report written; the report names what was and was not run.
- [ ] No unresolved critical review finding: Stage 6 has not happened. No commit or push by any agent: true so far.

## 10. Known limitations and the unverified list
Nothing below was verified by anyone unless it is labelled in sections 4 and 8.
- No agent has executed any JavaScript of this feature. UNVERIFIED: that every JS file parses; that the self-test page runs to the end; that `main.js` starts the app in a real page and hides `#boot-note`; the fake `fetch` abort ordering; `<dialog>` `close`/`cancel` order and focus in each browser (R-5); the `importNode` copy of `<main id="app">` in the sandbox (it fetches `../index.html` through the server); top-level `await import()` in `tests/index.html` (needs a current browser); that the harness's 5 s per-test limit never triggers on a slow machine; that moving a focused node blurs it as the restore code assumes; that `requestSubmit()` and `click()` behave in the tests as written on inert or disabled elements; that a programmatic `focus()` after re-enabling is honoured in every browser; that `document.activeElement` is meaningful when the test page is not focused; that two `settle()` calls are enough after a dialog `close` event; the DR-14a judgement that "active element is outside the app" means the edit row was dropped.
- The real backend: no agent has run the backend or the frontend server against it. UNVERIFIED: AC-23 with the real backend (no CORS error, same origin), AC-3 with the real backend stopped, `./mvnw spring-boot:run` (never run per `backend/README.md`), the frontend server standalone (`java server/FrontendServer.java` was not run: it would hold port 8000), the `JAVA_HOME` forms, and the `--port` and `--backend-port` options outside test B (test B passes both options to the child JVM).
- Test B limits: stub backend only; the 504 path and the 30 s limit are not tested; malformed percent sequences in a path were not exercised, and the Tomcat `400 text/html` page for them is passed through (documented in the README, only the stubbed `400 text/html` is tested); path traversal and other hostile input were not tested (H-2).
- NOT_RUN by H-2: security, speed, performance, the 10 s timing, NFR-7.
- Browser support (Chrome, Edge, Firefox, Safari, latest two versions) and WCAG 2.1 AA are targets, not results; only browsers the human lists will be recorded. Screen-reader behaviour is best effort and unverified.
- The README's two-process model, no authentication and loopback-only statements are design facts (loopback binding is in `FrontendServer.java`; test B reached it on `127.0.0.1`); no test tried another interface.
- The default announcer `defer` test uses a real 300 ms wait for a 50 ms timer (not a speed claim).
- Nothing was committed, pushed or staged.

## 11. Rework after code review (Stage 5 rework, human decision S6 Q1: fix CR-1, CR-5, CR-6, CR-7 only)
Branch checked with `git branch --show-current`: `feature/CAP-1-todo-frontend-ui`. CR-2, CR-3, CR-4, CR-8 and the nits were not touched.

| Finding | Change (file, lines after the change) | Test | Label |
|---|---|---|---|
| CR-1 README said the server starts from the repo root, the server exited 1 there | `FrontendServer.java:82-88` uses new `findWebRoot()` (`:114-122`): tries `./public`, then `./frontend/public`, first one holding `index.html` wins; if none, one-line message naming both locations, exit 1 (refuse-to-start kept; the message still contains "run from the frontend/ directory"). Header comment updated. `frontend/README.md:14-20` rewritten to match. | New case in `FrontendServerTest.java` ("server started from the repository root finds frontend/public": child JVM with working directory = repo root running `frontend/server/FrontendServer.java`, expects the start-up line and `GET /` 200 with the index page) and an overload `launch(workDir, script, ...)`; all existing cases unchanged. | AGENT-EXECUTED (Java) |
| CR-5 focus lost after Retry | `app.js:165-167` (failure: focus `#retry`), `:192-196` (success: focus `#list-heading`; the orphan case keeps its own target), `:200-205` `focusLost()` (focus on the body, outside the app, or still on the hidden Retry button). Only for `reason === 'retry'` and only when focus was lost, so a user who moved elsewhere is not disturbed. `view.js:461-462` adds `focusRetry`, `isRetryFocused`. | `app.test.js:151-185`: success, failure, and "does not steal focus" cases | written, NOT_RUN (browser, HUMAN-REPORTED) |
| CR-6 pending text hidden by `aria-label` | `view.js:99-108`: the toggle and Delete `aria-label` is now built from the visible text ("Updating...: <title>", "Deleting...: <title>"), so the name contains the visible label and the title; `aria-busy` on the row was already set (`view.js:110-114`) | `app.test.js:230-244` (label before, during, after; `aria-busy`; other row unchanged); `app-edit-delete.test.js` delete test also asserts "Deleting...: Title" and `aria-busy` | written, NOT_RUN |
| CR-7 two `setTimeout(0)` waits for `close` | `fakes.js:7-13` new `nextClose(dialogEl)` (promise resolved by the dialog's `close` event, registered before the action); `dialog.test.js` (10 tests) and `app-edit-delete.test.js:513, 526, 538` now `await closed; await settle();`. The remaining single `settle()` only drains promise continuations queued by the already-fired close handler; it no longer waits for the event. Test intent unchanged. | the same tests | written, NOT_RUN |

Static checks by Grep over `frontend/`: no `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write` or `eval(` in `public/js`; no non-ASCII byte anywhere under `frontend/` (0 matches); every new name resolves (`nextClose` exported at `fakes.js:11` and imported at `dialog.test.js:6`, `app-edit-delete.test.js:7`; `focusRetry`, `isRetryFocused`, `focusListHeading` exported by `view.js` and used at `app.js:166, 196, 204`). No double `settle()` remains in `frontend/public/tests`. These are text checks, not a parse.

Commands run in this rework (repo root; Bash otherwise limited to what the G4 extension allows):
| # | Command | Exit | Result | Label |
|---|---|---|---|---|
| 6 | `git branch --show-current` | 0 | `feature/CAP-1-todo-frontend-ui` | AGENT-EXECUTED |
| 7 | `java frontend/server/FrontendServerTest.java; echo "EXIT=$?"` | 0 | `32 passed, 0 failed, 1 skipped`; the new root-start case PASSES; the one skip is `server refuses to start without public/index.html` (index exists, as before); previous result was `31 passed, 0 failed, 1 skipped` | AGENT-EXECUTED |

Not run: the server standalone (it would leave a port held; the test starts and stops its own child, and the root-start case destroys its child in `finally`), any JavaScript, Maven, npm.

Unverified after this rework (UNVERIFIED until the human's browser run of `/tests/` page A and checklist D):
- Every JS change above and its tests: that the files parse, that the three CR-5 tests and the CR-6 test pass, and that `nextClose` plus one `settle()` is enough in each browser (the close event is awaited deterministically, but the order of the real `close` event relative to the app's continuations was not observed).
- CR-5: whether a browser leaves `document.activeElement` on a just-hidden or disabled button until its next rendering step; `focusLost()` covers both outcomes by reading, not by running. Real screen-reader and keyboard behaviour after Retry.
- CR-6: how each screen reader reads "Updating...: <title>" (checklist D); the label text format is a choice within the review's recommendation.
- CR-1: the root-start case shows the root lookup works with the test's child JVM on Windows; the non-root `./public` lookup is covered by every other server case (they start from `frontend/`). A start from some other directory (neither `public` nor `frontend/public`) was not run (the existing refuse case is skipped while `index.html` exists).
- README line 14-20 states only what the test shows; `java frontend/server/FrontendServer.java` typed by hand at the root was not run.

## 12. Rework after the first browser run (7 failures in VS Code's Electron browser, Chrome 150)
Branch checked: `feature/CAP-1-todo-frontend-ui`. Nothing below has been executed (no JavaScript engine, no browser); the human re-runs `/tests/`. CR-2, CR-3, CR-4, CR-8 and the nits were not touched. No change outside `frontend/public/` and this report.

**Diagnosis (by reading; confidence: LOW on the exact mechanism, MEDIUM on "not a plain logic error in the happy path").**
- `dialog.js` (before) attached its single `close` listener at construction, before `showModal`, never removed it, read `returnValue` correctly, cleared `current` first in `finish`, and the buttons called `close('cancel'|'confirm')`. On paper the close path resolves once. Nothing in it throws.
- The evidence does not fit one simple cause. Failures 1-5 mean `await closed` returned (otherwise the harness would report a 5 s timeout, not an assertion) yet the dialog's own `close` listener had not cleared or resolved the pending confirmation; failure 5 (`showModal` called once) shows `current` was still set. But the very same flow passed in other tests of the same file ("returnValue is reset ...", "DR-12 same task", "missing returnFocus"), so the outcome depends on timing/order, not on the code path. The app tests that pass with the Escape path while the Cancel path fails point the same way. I could not find, by reading, a defect that explains this; the likely family is that this Chromium delivers the dialog `close` event as a queued/frame-timed task in an order or at a time the tests and the single listener did not tolerate (environment difference), made visible by a design that depended on that one event.
- Decision: product risk cannot be excluded (the real Delete button depends on the same single event), so the product was hardened to not depend on it; the tests were changed so they no longer depend on its timing either. Whether the 7 failures were caused by the product, the CR-7 waits or the browser is therefore NOT proven. If any of the 7 still fails after this change, the human should open DevTools in that browser and report: (a) whether `confirmDelete`'s promise resolves at all after clicking Delete in the real app, (b) the value of `document.getElementById('confirm-dialog').returnValue` and `.open` right after the click, (c) any red console error at that moment.

**Change (`frontend/public/js/dialog.js`).**
- `finish(confirmed)` (`:28`) is the single idempotent resolver (clears `current` first, returns focus to `returnFocus`, then resolves).
- `onClosed()` (`:42`): ignores a notification while the dialog is open again (stale event of an earlier opening), otherwise `finish(returnValue === 'confirm')`.
- `closeWith(value)` (`:49`): the Cancel and Delete buttons call `dialog.close(value)` and then `onClosed()` at once, so the promise resolves inside the click and never waits for an event.
- Three notification paths now lead to the same `finish`: the buttons, a `MutationObserver` on the `open` attribute (`:57`, a microtask, covers Escape and a programmatic `close()`), and the native `close` event (kept). Whichever comes first wins.
- `confirmDelete` (`:70-71`): if a confirmation is still pending but the dialog is already closed, it is finished first (false unless `returnValue` is 'confirm'), then a new opening starts, so a late event can never block the next confirmation. DR-11/DR-12 kept: resolve once, same id returns the same promise, another id resolves false at once, one `showModal` per opening, focus returns to the caller's element. `cancel` is still neither listened to nor prevented (the browser follows it with `close()`, which the observer sees); this is the one place where the delegation's "resolve false on cancel" is met through the close that follows, not by resolving inside `cancel` (resolving there would leave the dialog open if another listener prevented the cancel).

**Tests changed (no test removed).**
- `fakes.js`: `nextClose` removed (unused now); `settle` kept.
- `dialog.test.js`: the Cancel, Delete, Escape, close-without-cancel, returnValue-reset, showModal-count, DR-12 (both) and missing-returnFocus tests now `await` the `confirmDelete` promise itself (a result that never arrives ends in the harness timeout naming the test), then one `settle()` to check "exactly once". Escape test also asserts focus returns to the opener. New test: "a new confirmDelete right after a close() opens a fresh confirmation, the old one resolves false" (also feeds a late synthetic `close` event and checks it does not decide the second confirmation).
- `app-edit-delete.test.js` (real-dialog block): the three tests no longer wait for an event; the dialog resolves inside the click, and one `settle()` drains the controller's continuations.

**Static checks (Grep).** No `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write` or `eval(` under `frontend/public`; 0 non-ASCII bytes under `frontend`; no `nextClose` reference left; imports of `dialog.test.js` and `app-edit-delete.test.js` resolve to existing exports (`mountSandbox`, `settle`, ...). Text checks, not a parse.

**Unverified.** Everything above: that the files parse, that the 7 tests now pass in that Electron browser and in other Chromium/Firefox/Safari builds, that `MutationObserver` on `open` fires for Escape in each browser, that focus lands on `returnFocus` after the browser's own focus restore for Escape (the observer runs after the browser restores focus; not observed). Commands run: `git branch --show-current` (exit 0).
