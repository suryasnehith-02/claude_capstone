# Implementation plan - CAP-1

Status: FOR APPROVAL (G4)        Inputs: requirements.md (G1 approved), architecture.md (G2 approved), design-review.md (round 2, APPROVE), repo-profile.md, clarifications.md (H-1..H-3, S2-1..S2-3), approvals.md

Convention: JDK and browser behaviour the repository cannot show is marked **UNVERIFIED**. Nothing here asks for security, speed or performance checks (H-2). Evidence labels (AGENT-EXECUTED, HUMAN-REPORTED, NOT_RUN) are those of architecture 10.2.

## 1. Overview
- Scope: a new `frontend/` directory only: one Java source-launch file (static server plus `/todos` proxy, port 8000), a JDK-only proxy test, a plain HTML/CSS/native-ES-module UI, a browser self-test page, and a README with run steps and the manual checklist. `backend/` is not touched (AC-23). The only file the developer writes outside `frontend/` is `docs/sdlc/CAP-1/implementation-report.md`. `docs/changelog.md` is written later by the pr-manager.
- Tasks: 9 (T-1..T-9). By priority: P0 x 6 (T-1, T-2, T-5, T-6, T-7, T-8), P1 x 3 (T-3, T-4, T-9). By size: M x 9 (T-7 is the largest; a split line is given there).
- Waves: 4 developer waves plus one human pause (section 3). Wave 1 is the proxy server and its test, the only part an agent could verify, and only if the `java` permissions exist (section 3.1).
- Blocked tasks: none blocked by an open question. External prerequisites (section 8): the feature branch from the pr-manager `prepare` step (no source edit is possible before it) and the human's `java` permission entries (they decide only who runs test B).
- Design-review carry-overs: DR-12 (T-5, T-8), DR-13 (T-5, T-7), DR-14 (T-6, T-7, T-8). Each is an explicit acceptance criterion and test below.

## 2. Tasks

### T-1: Frontend server and proxy (`FrontendServer.java`)
- Description: Implement C-1 exactly as architecture 7.3, I-1 and I-2: static handler for `frontend/public/**` and a transparent proxy for `/todos` and `/todos/*` to `127.0.0.1:<backend-port>`. One file, JDK only (`com.sun.net.httpserver`, `java.net.http`, `java.nio.file`).
- Satisfies: NFR-1, AC-23 (transport half), AC-3 (502 when the backend is down), AC-20 (pass-through of a non-JSON 400). Serves the app for every other FR/AC.
- Depends on: none (branch from `prepare` must exist; section 8)
- Priority / size: P0 / M
- Blocked: no
- Affected files: create `frontend/server/FrontendServer.java`
- Acceptance criteria:
  - [ ] Run from `frontend/` as `java server/FrontendServer.java [--port=<n>] [--backend-port=<n>]` it binds `127.0.0.1` only (defaults 8000 and 8080) and prints exactly one line `Frontend at http://127.0.0.1:<port>/ - proxying /todos to http://127.0.0.1:<backend-port>` (test B waits for it).
  - [ ] Missing `./public/index.html` exits non-zero with a one-line "run from the frontend/ directory" message; a port in use exits non-zero naming the port and `--port`.
  - [ ] Static: `/` serves `index.html`; MIME map of 7.3 (`.js` is `text/javascript; charset=utf-8`); `Cache-Control: no-store`; `/tests` gets 301 with `Location: /tests/`; a missing file, non-regular file, `InvalidPathException` or a path outside the root gives 404 `text/plain`; GET and HEAD only, other methods 405; the leading `/` is stripped before resolving; `getPath()` is used (no `+` decoding).
  - [ ] Proxy: routed only for exactly `/todos` or prefix `/todos/` (`/todosx` is a static 404); same method token, raw path and raw query; only `Content-Type` and `Accept` copied; request body bytes unchanged (empty -> `noBody()`); response status, `Content-Type` and body bytes unchanged; 204 or empty body uses `sendResponseHeaders(status, -1)`, otherwise the exact length, never 0.
  - [ ] Failures, in this catch order: `HttpConnectTimeoutException` -> 502 `BAD_GATEWAY`; `HttpTimeoutException` (30 s) -> 504 `GATEWAY_TIMEOUT`; `IOException` -> 502; `InterruptedException` -> 502 with the interrupt flag restored; `IllegalArgumentException` -> 502; other `RuntimeException` -> 502. An error response is sent only if headers are not yet sent; `exchange.close()` in `finally`; one stderr line `proxy: <METHOD> <route kind> failed: <exception class>` with no ids, bodies or titles. (That `HttpConnectTimeoutException` extends `HttpTimeoutException` is **UNVERIFIED** from the repo; JDK API.)
  - [ ] No file is written, no dependency, no build step.
- Test requirements:
  - Unit: none separate (black-box tested by T-2).
  - Integration: T-2 is the verification of T-1; there is no other agent-run check.
  - Edge and failure cases: backend down; directory without a trailing slash; malformed percent path (JDK behaviour **UNVERIFIED**; observed result recorded in the implementation report if exercised, not promised); the 30 s/504 path is not tested (architecture 10.1 "Not run").
- Documentation: a header comment with the run command and options. Run docs are in T-9.

### T-2: Proxy and static-server black-box test (`FrontendServerTest.java`)
- Description: Implement C-13 with the seven robustness rules of architecture 10.1 row B: finds `frontend/` itself (cwd if `public/index.html` exists, else `./frontend`, else exit 2); free ports via `ServerSocket(0)`; child JVM from `System.getProperty("java.home")/bin/java` with `server/FrontendServer.java --port=<p> --backend-port=<s>`, `ProcessBuilder.directory(frontendDir)`, `redirectErrorStream(true)`; a reader thread drains output and signals the start-up line (wait up to 60 s, else FAIL with the captured output); 10 s per request; 120 s watchdog; destroy, wait 5 s, `destroyForcibly()` in `finally`; an in-process stub `HttpServer` that records method, raw path, query, `Content-Type` and body bytes and sends scripted responses. No command-line arguments. Prints PASS or FAIL per case and a final `N passed, M failed, K skipped` line; exits non-zero on any failure.
- Satisfies: NFR-1, AC-23 (stub, partial), AC-3 (502), AC-20 (stubbed `400 text/html`)
- Depends on: T-1
- Priority / size: P0 / M
- Blocked: no (writing it is never blocked; who runs it depends on section 3.1)
- Affected files: create `frontend/server/FrontendServerTest.java`
- Acceptance criteria:
  - [ ] All cases of 10.1 row B: `/` returns `text/html`; `/js/main.js` returns 200 `text/javascript`; `/tests` returns 301 `Location: /tests/`; a missing file 404; POST to a static path 405; GET, POST, PUT, PATCH and DELETE on `/todos`, `/todos/{id}`, `/todos/{id}/status` reach the stub with identical method, raw path, query, `Content-Type` and body bytes; stub responses 200, 201, 204 (no body), 400 envelope, 404, 405, 415, 500 and `400 text/html` return with status, content type and bytes unchanged; `/todosx` is not proxied; with the stub stopped the answer is a 502 JSON body with code `BAD_GATEWAY`.
  - [ ] Ordering note: in Wave 1 the real `public/index.html` and `public/js/main.js` do not exist yet (they arrive in T-5 and T-9). The two cases that need them print `SKIPPED (file not yet created)` and do not fail the run. The final run after Wave 4 must show `0 skipped`. The test never creates files under `frontend/public/`.
  - [ ] Works from `frontend/` and from the repository root; takes no arguments (the S2-2 permission entry matches only the bare command).
  - [ ] Exit code 0 only when every non-skipped case passes; the child JVM and the stub are always stopped.
- Test requirements:
  - Unit: n/a (this is the test).
  - Integration: it is the integration test of T-1.
  - Edge and failure cases: backend down; readiness line never printed (FAIL with output, not a hang); free-port race (60 s wait turns it into FAIL).
- Documentation: usage line in the file header.
- Evidence: AGENT-EXECUTED only if the entries exist and the agent's command form matches (section 3.1); otherwise HUMAN-REPORTED or NOT_RUN. The developer must not work around a refused command.

### T-3: Self-test harness, fakes, and the validation module
- Description: Create the dependency-free browser test infrastructure (C-12) and C-6. Harness: `describe`, `it`, async support, assert helpers, `N passed, M failed` line, one line per test, `<pre id="results">` paste block. Fakes: a fake `fetch` that records requests and can hold and release responses; a fake timer (`setTimer`/`clearTimer`, fired manually); a manual announcer flush (`defer` queue); a sandbox mount helper. `validation.js`: the 25 backend `White_Space` code points copied from `UnicodeText.java:21-33`, code-point counting, and `validateTask` (I-5) with the three messages word for word.
- Satisfies: FR-7, AC-5, AC-6, AC-16, AC-17, NFR-5
- Depends on: none (branch)
- Priority / size: P1 / M
- Blocked: no
- Affected files: create `frontend/public/tests/index.html`, `frontend/public/tests/harness.js`, `frontend/public/tests/fakes.js`, `frontend/public/tests/validation.test.js`, `frontend/public/js/validation.js`
- Acceptance criteria:
  - [ ] `tests/index.html` has `<link rel="icon" href="data:,">`, imports the test modules (later tasks add their own import line), shows `N passed, M failed` and fills `<pre id="results">` with a paste-ready summary that names failed tests.
  - [ ] Boundaries: title 100 accepted and 101 rejected; description 500 accepted and 501 rejected; an empty or whitespace-only title gives "Title is required"; over-length gives "Title must be 100 characters or fewer" or "Description must be 500 characters or fewer"; returned values are the trimmed ones.
  - [ ] Named vectors where JS built-ins differ from the backend exist as named tests: U+FEFF kept, U+0085 stripped, U+001C..U+001F kept, U+180E kept, U+3000 stripped, an astral emoji counts as 1. The vector table is one list that can be compared with `UnicodeText.java`.
  - [ ] `validation.js` imports nothing; no `maxlength` anywhere (D-6, 6.2).
- Test requirements:
  - Unit: `validation.test.js` as above (browser; HUMAN-REPORTED when run).
  - Integration: a harness meta-test that proves the pass/fail counters (without leaving a failing test in the suite).
  - Edge and failure cases: a lone surrogate gets no client rule (the backend answers `INVALID_REQUEST_BODY`; architecture 7.2 step 4); null or undefined input behaves as empty.
- Documentation: header comment in `validation.js` naming `UnicodeText.java:21-33`.

### T-4: API client and outcome mapping (`api.js`, `messages.js`)
- Description: C-5 per 7.1 and 7.2 and I-4: requests built exactly as the 7.1 table (relative paths, `encodeURIComponent(id)`, headers, trimmed JSON bodies), 10 000 ms `AbortController` timer armed before `fetch` and cleared in `finally` only after `response.text()` settles, and the five-step classification into a never-rejecting `Result`. C-7 per the 7.2 table and I-6: `outcomeFor(operation, result)` with word-for-word 7.3 messages, field errors, the `refresh` flag, and the field-error alert text (messages in field order plus "Please check your input and try again." when another field is named).
- Satisfies: FR-8, FR-9, NFR-6 (abort logic only; the 10 s limit is NOT_RUN by H-2), NFR-5, AC-11, AC-12 (mapping), AC-15, AC-20, AC-3
- Depends on: T-3 (harness and fakes)
- Priority / size: P1 / M
- Blocked: no
- Affected files: create `frontend/public/js/api.js`, `frontend/public/js/messages.js`, `frontend/public/tests/api.test.js`, `frontend/public/tests/messages.test.js`; modify `frontend/public/tests/index.html` (import lines)
- Acceptance criteria:
  - [ ] Every operation's method, path, headers and body match 7.1 (delete has no `Content-Type`; edit sends both fields; status body is exactly `{"status":"Completed"}` or `{"status":"Pending"}`).
  - [ ] Every 7.2 classification step is reached: network and timeout (before headers and during `text()`), 204 on delete, 204 elsewhere = unexpected, wrong code for the operation, non-JSON 2xx, wrong shape, 400 `VALIDATION_ERROR` with `details` filtered to string `field` and `message`, 404 `TODO_NOT_FOUND`, other codes and statuses, and a non-2xx without an envelope (including `400 text/html`).
  - [ ] `outcomeFor` covers every cell of the 7.2 mapping table; codes, status numbers and the raw `error.message` never appear in any message.
  - [ ] `api.js`, `messages.js` import nothing.
- Test requirements:
  - Unit: `api.test.js` (fake fetch and fake timer, recorded requests) and `messages.test.js` (table-driven, one row per cell).
  - Integration: none beyond T-7.
  - Edge and failure cases: `details` missing or malformed; `status` field in `details`; a 2xx with an empty body.
- Documentation: header comments naming the 7.2 steps.

### T-5: Page shell, styles, announcer and dialog
- Description: C-2, C-3, C-10, C-11. `index.html` per 6.2: `<h1>`, two live regions, add form (no `maxlength`), list section, `#list-heading` with `tabindex="-1"`, `#retry`, `#orphan` panel with its heading carrying `tabindex="-1"` (DR-14b), `<dialog id="confirm-dialog">`, `<noscript>` note, `data:` favicon. `styles.css`: visible `:focus-visible` outline, Completed badge plus strike-through, bordered message kinds, `white-space: pre-wrap` for descriptions, visually-hidden helper. `announcer.js` (I-9) with a **sequence number**: every `announce` increments it and the deferred callback writes only if its number is still current, whatever `defer` is injected (DR-13a). `dialog.js` (I-7): one resolution on `close` from `returnValue === 'confirm'`, `returnValue` reset before opening, initial focus on Cancel, Tab trap, caller-supplied focus return, title via `textContent`, and re-entry that returns the pending promise **only for the same task id**; a different id resolves `false` at once (DR-12).
- Satisfies: FR-2, FR-5, FR-6 (style), NFR-2 (display style), NFR-3, NFR-5, AC-2, AC-9 (dialog), AC-14, AC-21
- Depends on: T-3 (harness for the unit tests); the markup itself has no dependency
- Priority / size: P0 / M
- Blocked: no
- Affected files: create `frontend/public/index.html`, `frontend/public/css/styles.css`, `frontend/public/js/announcer.js`, `frontend/public/js/dialog.js`, `frontend/public/tests/announcer.test.js`, `frontend/public/tests/dialog.test.js`; modify `frontend/public/tests/index.html`
- Acceptance criteria:
  - [ ] The element ids and the order in 6.2 exist; `index.html` contains no `innerHTML`-style code and loads `js/main.js` as `type="module"`.
  - [ ] Announcer: both regions are emptied first; success goes to `#msg-status`, error to `#msg-alert`; an identical repeated message is written again; with a queue-style `defer`, a newer `announce` makes an older queued write a no-op (DR-13a).
  - [ ] Dialog: Escape and Cancel each resolve `false` once; Delete resolves `true` once; a second `confirmDelete` for the same id while open returns the same promise; for a different id it resolves `false` and does not change the open dialog (DR-12); focus returns to the caller's target.
  - [ ] CSS: Completed is conveyed by text and style, not colour alone; focus outline visible on every control.
- Test requirements:
  - Unit: `announcer.test.js` (sequence number, repeat text, region clearing) and `dialog.test.js` (test A case (g): one resolution on Escape and on Delete; same-promise re-entry; different-id re-entry).
  - Integration: none beyond T-7 and T-8.
  - Edge and failure cases: `close` without a prior `cancel`; `showModal` is called once per opening.
  - Native `<dialog>` Escape/`close` order is **UNVERIFIED** by anyone (R-5); covered by the human-run page in the browsers he uses.
- Documentation: none beyond header comments.

### T-6: View (`view.js`)
- Description: C-9: keyed reconcile by `data-id`, never rebuilding the row in edit mode, restoring focus and selection if moving a node blurs it; row view and edit-mode markup per 6.2; list status (loading, empty "No tasks yet", load error with Retry); orphan panel (heading "Your unsaved text", read-only title and description, Dismiss); pending text and `disabled` derived from `pending`; accessible names `aria-label` such as "Delete: <title>" and stable `data-action`; text only through `textContent`, `createElement`, `setAttribute`, `disabled`, `hidden`.
- Satisfies: FR-1, FR-4, FR-6, FR-11, NFR-2, NFR-3, AC-1, AC-19, AC-21, AC-22 (panel)
- Depends on: T-5
- Priority / size: P0 / M
- Blocked: no
- Affected files: create `frontend/public/js/view.js`
- Acceptance criteria:
  - [ ] A row shows title, description only when non-empty (line breaks kept by CSS), a "Pending" or "Completed" badge, and the buttons "Edit", "Mark as completed" or "Mark as pending", "Delete".
  - [ ] Reconcile reuses nodes by id in backend order without re-sorting; an edit-mode row keeps its input elements, values, focus and caret through a reconcile.
  - [ ] Pending rows keep disabled state and the texts "Saving...", "Updating...", "Deleting..." across a reconcile; "Adding..." for the form.
  - [ ] The orphan heading has `tabindex="-1"` (DR-14b).
  - [ ] `grep` for `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write`, `eval` in `frontend/public/js/` finds nothing (agent-run text search; the Stage 6 check).
- Test requirements:
  - Unit/integration: exercised through `app.test.js` and `app-edit-delete.test.js` (T-7, T-8), including test A cases (f) and AC-21 (a task titled `<b>x</b><script>1</script>` renders as literal text, and `textContent` equals the stored string).
  - Edge and failure cases: description empty; very long unbroken title (renders, no assertion on layout).
- Documentation: header comment naming the keyed-reconcile rule (D-4).

### T-7: Controller part A (`app.js`: load, add, status) and bootstrap hooks
- Description: C-8 per 8 and 8.1, first half: state (`tasks`, `loadStatus`, `loadGen`, `dirtyDuringLoad`, `pending`, `editing`, `fieldErrors`, `orphan`); `createApp` (I-8) with injected `api`, `confirm`, `announcer` and a `ready` promise; `load(reason)` for `initial`, `retry`, `refresh` with the generation and dirty rules; add flow; status flow; the synchronous duplicate guard; the rule that results are applied by id after a list is applied; focus rules after success and failure, set after re-enabling controls; field-error text also written to `#msg-alert`. **DR-13b:** a dirty-rule re-run keeps the original reason (`initial` or `retry`) for the failure announcement and for clearing the message, so a failed re-run is announced in `#msg-alert`; `#list-status` is additionally given `role="status"` as a fallback.
- Satisfies: FR-1, FR-2, FR-3, FR-6, FR-7, FR-8, FR-10, FR-11, NFR-3, AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-10, AC-11, AC-13, AC-14, AC-15, AC-17, AC-18, AC-19
- Depends on: T-3, T-4, T-5, T-6
- Priority / size: P0 / M (largest task). Split line if one delegation stalls: T-7a load/refresh/ordering, T-7b add and status flows.
- Blocked: no
- Affected files: create `frontend/public/js/app.js`, `frontend/public/tests/app.test.js`; modify `frontend/public/tests/index.html`
- Acceptance criteria:
  - [ ] Initial load success renders in backend order; empty list shows "No tasks yet" with the add form visible; load failure shows the generic message and Retry and never "No tasks yet"; Retry sends `GET /todos` and clears the message on success (AC-1, AC-2, AC-3, AC-18).
  - [ ] Add: invalid input sends no request, shows the field error beside the field, writes the same text to `#msg-alert`, and focuses the first invalid field; valid input sends the trimmed values once, unshifts the returned task, clears the form, announces "Task added" and focuses the title input.
  - [ ] Status: toggle sends one `PATCH`, replaces the task in place, announces "Task marked as completed" or "Task marked as pending", and focus stays on that row's toggle.
  - [ ] A double `click()` on Add, a toggle or Save records exactly one request (guard checked before any `await`, `pending` set before `fetch`); other tasks stay usable while one is pending.
  - [ ] A held refresh, an add that resolves, then the held list without the new task: the new task stays and a second `GET /todos` is recorded (case (c)).
  - [ ] If the first load's dirty re-run fails, the generic message is announced in `#msg-alert` and Retry shows (DR-13b).
  - [ ] Every failure branch (add, status) keeps the list and typed text and returns focus to the remembered target after controls are re-enabled (case (e), add and status part).
  - [ ] Any result other than a confirmed one never changes `tasks` (FR-10).
- Test requirements:
  - Unit/integration (browser, HUMAN-REPORTED): `app.test.js` with the sandbox mount, fake API, manual announcer flush; named cases (c), (d) (invalid add from the title input: after the flush `#msg-alert` holds "Title is required" and focus is on the title input), (e) for add/status, AC-13 double click, AC-19, DR-13b.
  - Edge and failure cases: timeout, network error, unexpected body, 400 `VALIDATION_ERROR` with a `status` field on a status change, 404 on add (generic).
- Documentation: header comment naming the state and the ordering rule.

### T-8: Controller part B (`app.js`: edit, delete, 404 and orphan handling)
- Description: C-8 second half per 8.1 Edit and Delete and 8.4. Edit: one row at a time, draft updated on every `input`, Cancel and Escape without a request, Save with the guard and validation, other rows' Edit buttons disabled while a save is pending. Delete: guard, `await confirm(title)`, then **re-check after the await** (DR-12): if `pending` already has the id or the id is no longer in `tasks`, return without sending; keep a `confirming` flag so a second Delete click is ignored while the dialog is open. 404 handling: "This task no longer exists" in `#msg-alert` kept through the refresh; for an edit save, the draft goes to the orphan panel; focus to the orphan heading (edit case) or `#list-heading`. **DR-14:** (a) when a refresh finds that the open edit's task is gone, the draft goes to the orphan panel and "This task no longer exists" is announced if no message is currently showing; (c) after a successful delete, focus goes to the next row's Edit, else the previous row's, else `#list-heading`, and if the row is already absent because a refresh removed it first, to `#list-heading`.
- Satisfies: FR-4, FR-5, FR-8, FR-9, FR-10, FR-11, NFR-3, AC-6, AC-7, AC-8, AC-9, AC-11, AC-12, AC-13, AC-22
- Depends on: T-7
- Priority / size: P0 / M
- Blocked: no
- Affected files: modify `frontend/public/js/app.js`; create `frontend/public/tests/app-edit-delete.test.js`; modify `frontend/public/tests/index.html`
- Acceptance criteria:
  - [ ] Edit pre-fills from `tasks`; Cancel or Escape sends no request and leaves the task unchanged (the fake API records zero calls); Save sends both trimmed fields, shows the returned values and "Task updated", and focuses that row's Edit button.
  - [ ] Delete: Cancel or Escape sends no request and focus returns to that row's Delete; Delete sends one `DELETE`, removes the row only on 204, announces "Task deleted" and applies the focus rule; two `confirmDelete` calls while the dialog is open send one `DELETE` (DR-12 test, extension of case (g)).
  - [ ] 404 on edit: message stays after the refresh succeeds and the orphan panel shows the draft (case (a)); the refresh failing leaves the message and shows the generic text with Retry in `#list-status` (case (b)); 404 on status or delete: message, refresh, focus on `#list-heading`.
  - [ ] A refresh while another row is in edit mode keeps the same input element, its value and focus (case (f)).
  - [ ] DR-14a: a refresh that drops the task being edited orphans the draft and announces the message when none is showing; DR-14b: focus lands on the orphan heading; DR-14c: delete focus fallback when the row is absent.
  - [ ] Failure branches of edit and delete keep the row and typed text and restore focus (case (e), edit/delete part); AC-11 holds for all four actions.
- Test requirements:
  - Unit/integration (browser, HUMAN-REPORTED): `app-edit-delete.test.js` covers cases (a), (b), (e) edit/delete, (f), (g) extension, DR-12, DR-14a, b and c, AC-7, AC-8, AC-9, AC-12, AC-22.
  - Edge and failure cases: 404 while a save is in flight; a validation error on edit with a `title` entry and a `status` entry (field message plus general message); timeout on delete.
- Documentation: none beyond header comments.

### T-9: Bootstrap, README with checklist, and implementation report
- Description: C-4 `main.js` (real `window.fetch` and `setTimeout`, mounts `#app`); C-14 `frontend/README.md`: start order (backend first, per `backend/README.md`), the start command in the plain form and both `JAVA_HOME` forms (Git Bash `"$JAVA_HOME/bin/java" server/FrontendServer.java`, cmd `"%JAVA_HOME%\bin\java" server\FrontendServer.java`; same for the test), the URL `http://127.0.0.1:8000/` and `/tests/`, how to run tests B and A and what to paste back (the `N passed, M failed` line, the `<pre id="results">` block, the browsers and versions), the roughly 10-minute manual checklist (AC-23, AC-3 with the backend stopped then started, AC-21 with a real task, keyboard-only walk-through including Enter on an empty title, best-effort screen reader, `git diff --stat -- backend/` empty, a note that an unrelated console 404 is not a CORS error), and a "not verified" section (security, speed, performance by H-2; 10 s timing; WCAG not certified; browsers beyond those used). Then `docs/sdlc/CAP-1/implementation-report.md`.
- Satisfies: NFR-1, NFR-2 (display check in checklist), NFR-4, NFR-7 (documented NOT_RUN), AC-21, AC-23
- Depends on: T-1, T-2, T-7, T-8
- Priority / size: P1 / M
- Blocked: no
- Affected files: create `frontend/public/js/main.js`, `frontend/README.md`, `docs/sdlc/CAP-1/implementation-report.md`
- Acceptance criteria:
  - [ ] `main.js` only wires `createApiClient` and `createApp`; `index.html` and `tests/index.html` load without a console error from a missing module (checked by the human run).
  - [ ] README contains every item listed above, with the exact start command text `java server/FrontendServer.java` and the exact test command text `java server/FrontendServerTest.java`.
  - [ ] The implementation report lists each task done or pending, files changed, commands actually run with exit codes (or "not run, permission missing"), and states which results are AGENT-EXECUTED, which are awaiting the human, and that nothing was committed.
  - [ ] Agent text search of `frontend/public/js/` for the five forbidden terms (T-6) finds none; `git diff --stat -- backend/` is empty (both AGENT-EXECUTED, allowed by `Bash(git diff *)`).
  - [ ] Test B final run (if permitted) shows `0 skipped`.
- Test requirements:
  - Unit: none.
  - Integration: final run of B; the search and diff checks above.
  - Edge and failure cases: README steps checked by reading against `backend/README.md:10-15,35-40` for the backend start order.
- Documentation: the README is the documentation. The changelog is the pr-manager's (Stage 8).

## 3. Execution order
| Wave | Tasks | Can run in parallel | Notes |
|---|---|---|---|
| 1 | T-1, then T-2 | no (T-2 needs T-1) | Proxy first. End of wave: run test B (section 3.1). Static cases that need T-5/T-9 files print SKIPPED |
| 2 | T-3, then T-4 and T-5 | T-4 and T-5 are independent after T-3 | Harness first, then pure logic and the page shell |
| 3 | T-6, then T-7, then T-8 | no | T-6 needs T-5 markup; T-7 needs T-3..T-6; T-8 extends `app.js` |
| 4 | T-9 | after T-1, T-2, T-7, T-8 | Bootstrap, README, report, final B run, text search |
| Pause H-A (human, not a task) | Browser self-test page A and checklist D | - | Section 3.2 |

No dependency cycle (T-1 -> T-2; T-3 -> T-4, T-5; T-5 -> T-6; T-3, T-4, T-5, T-6 -> T-7 -> T-8; T-1, T-2, T-7, T-8 -> T-9). One developer delegation per wave is intended. Waves 2 and 3 are the heaviest; if one stalls, the Orchestrator re-delegates the remaining task ids only.

### 3.1 Commands: needed, permitted, and fallback
Evidence: `.claude/settings.json:28-56` allows no `java` command; the human's `.claude/settings.local.json` exists but its contents were not read here, and the S2-2 entries were NOT present at G2 (approvals.md G2 row).

| Command | Needed for | Allowed today | Entry the human must add at G4 (own `.claude/settings.local.json`) |
|---|---|---|---|
| `java -version` | confirm `java` is on `PATH` (R-1) | no | `Bash(java -version)` |
| `java server/FrontendServer.java*` | starting the server (agents do not need to start it; test B starts its own child) | no | `Bash(java server/FrontendServer.java*)` |
| `java server/FrontendServerTest.java` | test B | no | `Bash(java server/FrontendServerTest.java)` |
| `git diff --stat -- backend/`, `git status *`, `git log *` | AC-23 check, inspection | yes (`Bash(git diff *)` etc.) | none |
| text search for `innerHTML` and the other four terms | Stage 6 and T-9 | yes (Grep tool, no shell) | none |

Working directory: the developer's shell starts at the repository root and resets between calls, while the commands run from `frontend/` (architecture R-10, R-11). A form such as `cd frontend && java server/FrontendServerTest.java` is likely not to match the entry (how the permission matcher treats compound commands is **UNVERIFIED**). No repository-root entry is requested (review Q-1, default no). `java server/FrontendServerTest.java` is written to work from either directory, so a human can run it from the root.

Fallback, stated plainly: if the entries are missing at Stage 5 start (the Orchestrator reads the file and records it) or the agent's form is refused, the developer does not retry with other forms and does not edit any settings file. Test B is then run by the human and pasted back (HUMAN-REPORTED), or it is reported NOT_RUN. The code is still written; only the evidence label changes. The same applies to `java -version` (R-1): if `java` is not on `PATH`, the human uses the `JAVA_HOME` form from the README.

### 3.2 Human-run browser tests and checklist (HUMAN-REPORTED, S2-3)
- After Wave 4 the Orchestrator asks the human to start the backend (per `backend/README.md`) and `frontend/`, open `http://127.0.0.1:8000/tests/`, and paste the `N passed, M failed` line, the `<pre id="results">` block when failures need diagnosing, and the browser and version.
- Default schedule (suggested; the Orchestrator may merge the two): page A once right after Wave 4 (about 2 minutes; a failure goes straight back to the developer as a rework delegation of the named tasks, before code review); checklist D once at Stage 7, after any Stage 6 fixes (about 10 minutes).
- Results are recorded verbatim by the Orchestrator or verification agent with the label HUMAN-REPORTED. Nothing in AC-1..AC-23 is reported as agent-verified. Anything the human does not report is NOT_RUN.
- If test B is also human-run, he pastes its output at the same time.

## 4. Scope manifest
| Path | Action | Task |
|---|---|---|
| `frontend/server/FrontendServer.java` | create | T-1 |
| `frontend/server/FrontendServerTest.java` | create | T-2 |
| `frontend/public/tests/index.html` | create (T-3); modify (T-4, T-5, T-7, T-8 add import lines) | T-3 |
| `frontend/public/tests/harness.js` | create | T-3 |
| `frontend/public/tests/fakes.js` | create | T-3 |
| `frontend/public/tests/validation.test.js` | create | T-3 |
| `frontend/public/js/validation.js` | create | T-3 |
| `frontend/public/js/api.js` | create | T-4 |
| `frontend/public/js/messages.js` | create | T-4 |
| `frontend/public/tests/api.test.js` | create | T-4 |
| `frontend/public/tests/messages.test.js` | create | T-4 |
| `frontend/public/index.html` | create | T-5 |
| `frontend/public/css/styles.css` | create | T-5 |
| `frontend/public/js/announcer.js` | create | T-5 |
| `frontend/public/js/dialog.js` | create | T-5 |
| `frontend/public/tests/announcer.test.js` | create | T-5 |
| `frontend/public/tests/dialog.test.js` | create | T-5 |
| `frontend/public/js/view.js` | create | T-6 |
| `frontend/public/js/app.js` | create (T-7); modify (T-8) | T-7 |
| `frontend/public/tests/app.test.js` | create | T-7 |
| `frontend/public/tests/app-edit-delete.test.js` | create | T-8 |
| `frontend/public/js/main.js` | create | T-9 |
| `frontend/README.md` | create | T-9 |
| `docs/sdlc/CAP-1/implementation-report.md` | create | T-9 |
| `docs/changelog.md` | modify (a CAP-1 line under `Unreleased`; written by the pr-manager at Stage 8, not by the developer) | none (Stage 8) |
| `docs/sdlc/CAP-1/branch-plan.md` | create (pr-manager `prepare`) | K-1 |

Unchanged on purpose: `backend/**`, `.claude/**` (including both settings files), root `.gitignore` (the frontend writes no files). No deletions.

### Needs explicit approval at kickoff
| Item | Type | Task | Reason |
|---|---|---|---|
| New top-level `frontend/` directory with a second runnable process on port 8000 | config (new runnable program; no new dependency, no new technology) | T-1..T-9 | Already ratified as G2-1; listed so G4 covers the scope. No dependency, no build tool, no migration, no deletion |
| The human adds the three `java` entries to his own `.claude/settings.local.json` | config (human-owned, gitignored, not in the change set) | T-2 (runner of test B) | Without them test B is human-run or NOT_RUN. No agent edits it |
| Feature branch name `feature/CAP-1-todo-frontend-ui`, base `main` (follows the `feature/<KEY>-<slug>` convention of the repo; 37 characters) | config (git) | K-1 | Local `Main` is protected and equals `9d2bc51` (merged CAP-2). The human approves the name at G4 |
| Working-tree state before the branch is created | git state | K-1 | `git status` shows `M .claude/settings.json`, `?? docs/codemie/`, `?? docs/sdlc/CAP-1/`. See Question 1 |

## 5. Traceability
| Requirement | Tasks |
|---|---|
| FR-1 | T-6, T-7 |
| FR-2 | T-5, T-7 |
| FR-3 | T-7 |
| FR-4 | T-6, T-8 |
| FR-5 | T-5, T-8 |
| FR-6 | T-5, T-6, T-7 |
| FR-7 | T-3, T-7, T-8 |
| FR-8 | T-4, T-7, T-8 |
| FR-9 | T-4, T-8 |
| FR-10 | T-7, T-8 |
| FR-11 | T-6, T-7, T-8 |
| NFR-1 | T-1, T-2, T-9 |
| NFR-2 | T-5, T-6, T-9 (functional display only; security NOT_RUN by H-2) |
| NFR-3 | T-5, T-6, T-7, T-8 |
| NFR-4 | T-9 (browsers recorded from the human's run) |
| NFR-5 | T-3, T-4, T-5 |
| NFR-6 | T-4 (abort logic; 10 s limit NOT_RUN by H-2) |
| NFR-7 | T-9 (documented as NOT_RUN by H-2; nothing designed or built) |
| AC-1 | T-6, T-7 |
| AC-2 | T-5, T-7 |
| AC-3 | T-1, T-2, T-4, T-7 |
| AC-4 | T-7 |
| AC-5 | T-3, T-7 |
| AC-6 | T-3, T-7, T-8 |
| AC-7 | T-8 |
| AC-8 | T-8 |
| AC-9 | T-5, T-8 |
| AC-10 | T-7 |
| AC-11 | T-4, T-7, T-8 |
| AC-12 | T-4, T-8 |
| AC-13 | T-7, T-8 |
| AC-14 | T-5, T-7 |
| AC-15 | T-4, T-7, T-8 |
| AC-16 | T-3 |
| AC-17 | T-3, T-4, T-7 |
| AC-18 | T-7 |
| AC-19 | T-6, T-7 |
| AC-20 | T-1, T-2, T-4 |
| AC-21 | T-5, T-6, T-9 |
| AC-22 | T-6, T-8 |
| AC-23 | T-1, T-2, T-9 |
| Orphan requirements | none (11 of 11 FR, 7 of 7 NFR, 23 of 23 AC mapped) |
| Orphan tasks | none (every task maps to at least one requirement) |

## 6. Test strategy
- Levels and tools (provenance: architecture 10.1 and repo-profile.md section 3; none of these has a recorded run yet):
  - B, proxy and static server: `java server/FrontendServerTest.java` (JDK 25.0.5.1 confirmed only by `docs/sdlc/CAP-2/verification-report.md:48-60`; `java` on `PATH` UNCONFIRMED). AGENT-EXECUTED, HUMAN-REPORTED or NOT_RUN per section 3.1.
  - A, UI behaviour: self-test page `http://127.0.0.1:8000/tests/`. Always HUMAN-REPORTED.
  - D, end to end against the real backend: checklist in `frontend/README.md`. Always HUMAN-REPORTED.
  - Static agent checks without `java`: text search for forbidden terms, `git diff --stat -- backend/`. AGENT-EXECUTED.
  - Not run, by H-2 or by design: security, speed, performance, the 10 s timing, path-traversal and hostile-input tests, malformed-percent behaviour, the 504 path.
- Data needs: stub backend inside test B; fake API inside test A; the real backend for D (started by the human).

| AC | Test or check | Task |
|---|---|---|
| AC-1 | A, D | T-6, T-7 |
| AC-2 | A | T-5, T-7 |
| AC-3 | A; B (502); D (backend stopped, then started) | T-1, T-2, T-7 |
| AC-4 | A including case (c); D | T-7 |
| AC-5 | A including case (d) | T-3, T-7 |
| AC-6 | A | T-3, T-7, T-8 |
| AC-7 | A; D | T-8 |
| AC-8 | A (zero recorded calls) | T-8 |
| AC-9 | A including case (g) and DR-12; D | T-5, T-8 |
| AC-10 | A; D | T-7 |
| AC-11 | A including case (e) | T-4, T-7, T-8 |
| AC-12 | A cases (a), (b) | T-8 |
| AC-13 | A (double click records one request) | T-7, T-8 |
| AC-14 | A (live-region content after flush, including DR-13); D (keyboard and screen reader, best effort) | T-5, T-7 |
| AC-15 | A | T-4, T-7 |
| AC-16 | A (named vectors) | T-3 |
| AC-17 | A (recorded request body) | T-3, T-7 |
| AC-18 | A | T-7 |
| AC-19 | A | T-6, T-7 |
| AC-20 | A (classification); B (stubbed `400 text/html` pass-through) | T-1, T-2, T-4 |
| AC-21 | A; D with a real task | T-6, T-9 |
| AC-22 | A cases (a), (f), DR-14 | T-6, T-8 |
| AC-23 | B (partial, stub); D (real backend); `git diff --stat -- backend/` | T-1, T-2, T-9 |

## 7. Definition of done
- [ ] T-1..T-9 implemented, each acceptance criterion met or, where it needs a browser, handed to the human run with the exact steps in `frontend/README.md`.
- [ ] Test B result recorded with its true label: AGENT-EXECUTED (output and exit code verbatim), HUMAN-REPORTED, or NOT_RUN. The final run shows `0 skipped`.
- [ ] Self-test page A: the human's pasted `N passed, M failed` line recorded verbatim as HUMAN-REPORTED, with `0 failed`, or every failure fixed and re-reported.
- [ ] Checklist D reported item by item by the human (HUMAN-REPORTED); unreported items are NOT_RUN. The browsers and versions actually used are recorded (NFR-4).
- [ ] DR-12, DR-13 and DR-14 each have a named test in the self-test page and a passing human report.
- [ ] `git diff --stat -- backend/` is empty (AGENT-EXECUTED); text search finds no `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write` or `eval` in `frontend/public/js/`.
- [ ] `frontend/README.md` and `implementation-report.md` written; the report names what was and was not run.
- [ ] No unresolved critical review finding (Stage 6); no commit or push by any agent before Stage 8; Jira and GitHub writes only with the human's explicit approval.
- [ ] The verification report, G6 and the PR description state the NOT_RUN items by H-2 (security aspect of NFR-2, the 10 s limit of NFR-6, NFR-7) as a verification gap, not as met. No security, speed or performance check is part of this definition of done.

## 8. Blocked tasks, prerequisites and risks
| Item | Unblocking condition / mitigation | Owner |
|---|---|---|
| K-1 pr-manager `prepare` (before Stage 5) | Local `Main` is protected (`.claude/hooks/protected-branches.txt`) and the developer hooks refuse source writes and Bash on a protected branch (`pre-implementation-check.sh:56-61,88-90`). The pr-manager (it alone may change branches, in stages 4, 5, 8) must: confirm the base is the default branch `main`, with `origin/main` containing `9d2bc51` (not verifiable here, no shell); propose `feature/CAP-1-todo-frontend-ui` for the human's approval at G4; confirm it exists neither locally nor on the remote; create it locally from the base after approval (`git switch -c`, the human is prompted); write `docs/sdlc/CAP-1/branch-plan.md` with the commit strategy: no commits or pushes by any agent before Stage 8; one commit at Stage 8, staged by explicit path (`frontend/`, `docs/sdlc/CAP-1/`, `docs/changelog.md`), excluding `.claude/settings.json`, `docs/codemie/`, `.sdlc/`. The Orchestrator records the branch in workflow state | pr-manager, Orchestrator, human (name approval) |
| Dirty working tree at `prepare` | The `prepare` procedure stops when unrelated uncommitted changes exist (`github-pr-management` SKILL, MODE prepare step 4). Unrelated: `.claude/settings.json` (modified) and `docs/codemie/` (untracked). See Question 1 | human |
| `java` permission entries missing at Stage 5 (R-10) | Human-run test B or NOT_RUN; developer does not work around | human, Orchestrator |
| R-1: `java` not on `PATH` | README `JAVA_HOME` forms; `java -version` check once the entry exists | human |
| R-2: `jdk.httpserver` or `java.net.http` missing from the JDK image | First start of T-1 or B shows a compile error; return NEEDS_CLARIFICATION as a new stack question | developer, Orchestrator |
| R-4: AC-1..AC-23 rest on HUMAN-REPORTED evidence | Verbatim paste, labelled; never claimed as agent-verified | Orchestrator |
| JDK-behaviour items that stay UNVERIFIED | exception hierarchy in the catch order, `Path.resolve` of a leading `/` on Windows, malformed percent paths, `<dialog>` Escape/`close` order and focus moving to `<body>` when a control is disabled, screen-reader reading. Reported as UNVERIFIED in the implementation report and Stage 7 | developer, verification agent |
| T-7 size | Split line T-7a / T-7b | Orchestrator |

## 9. Rollout and rollback
- Rollout: start the backend as in `backend/README.md`, then `java server/FrontendServer.java` from `frontend/`, then open `http://127.0.0.1:8000/`. Local use only (A-10); no deployment, CI or packaging.
- Rollback: delete `frontend/` (the human's approval is needed for any deletion). `backend/`, settings and `.gitignore` are unchanged, so nothing else needs reverting. The PR is not merged by any agent.
