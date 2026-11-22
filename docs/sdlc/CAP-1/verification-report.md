# Verification report - CAP-1

Overall status: PARTIAL        Round: 1 (attempt 1 of 3; report updated in place after the human's VS Code Electron re-run, no new command executed)
Branch: feature/CAP-1-todo-frontend-ui (nothing committed or staged; the work is in the working tree)    Inputs: requirements.md, architecture.md (10.1, 10.2), impl-plan.md, code-review.md (round 3, PASS), approvals.md (G1-G5, G4 extension), clarifications.md (H-1..H-3, human-reported browser results), implementation-report.md, repo-profile.md (listed by the Orchestrator task; not needed for the commands, which come from approvals.md), frontend/**

## Read this first
- **No agent has ever executed the UI JavaScript.** I had no JavaScript engine and no browser. Every statement about UI behaviour below rests on the human's reported browser self-test results (HUMAN-REPORTED) or is PENDING HUMAN / NOT_RUN. The only code an agent has executed is the Java server through its black-box proxy test.
- The human has now reported the self-test page in two browsers, both `275 passed, 0 failed`: Microsoft Edge 158 (`Chrome/158.0.0.0 Safari/537.36 Edg/158.0.0.0`; summary line only, the pasted block was cut off) and the VS Code Electron browser (`Code/1.147.0 Chrome/150.0.7871.250 Electron/43.8.9`, the browser where 7 tests failed before the dialog fix). For the Electron run the human's paste listed every test by name and all passed, including the five `dialog: confirmDelete` cases, the `delete with the real dialog (AC-9, case (g) extension)` Delete/Cancel/Escape cases, the CR-5 retry-focus tests and the CR-6 accessible-name test. **That per-test paste is not stored in the repo**: `clarifications.md` records only the summary lines and the names of the dialog and delete tests; the rest reaches me through the Orchestrator's task message. I did not see the paste and cannot check it. So the AC-to-test mapping below rests on (a) the per-test names the human reports for the Electron run, which are HUMAN-REPORTED and not stored, and (b) my reading of the `describe(` group names in `frontend/public/tests/*.test.js` and the plan in architecture.md 10.1 for the groups the repo does not record. Neither is an agent execution.
- Labels used: AGENT-EXECUTED PASS (an agent ran a command with exit 0 in this stage; only the proxy test and the static checks qualify), HUMAN-REPORTED PASS (reported by the human from the self-test page; not agent-verified), PENDING HUMAN (the manual checklist has not been reported), NOT_RUN BY H-2 (human decision: no security, speed or performance testing), NOT_RUN.

## 1. Summary
| Result | Count |
|---|---|
| AGENT-EXECUTED PASS | 13 (5 commands V-1..V-5, of which V-5 is the proxy test with 32 passing cases; 8 static checks S-1..S-8) |
| HUMAN-REPORTED PASS (self-test page, `275 passed, 0 failed`; not agent-verified) | 2 browser runs: Edge 158, and VS Code Electron / Chrome 150 (the browser that had 7 failures before the dialog fix) |
| FAIL | 0 |
| NOT_RUN / PENDING | see section 10: 5 PENDING HUMAN items, 4 NOT_RUN BY H-2 items, 7 other NOT_RUN items (NOT_RUN total 11) |

Acceptance-criteria results (23): 14 HUMAN-REPORTED PASS only (AC-2, 5, 6, 8, 11, 12, 13, 15, 16, 17, 18, 19, 22 on the self-test page alone; AC-20 on the self-test page plus an AGENT-EXECUTED PASS for the proxy pass-through half), 8 HUMAN-REPORTED PASS on the self-test page AND PENDING HUMAN for the real-backend or keyboard/screen-reader part (AC-1, 3, 4, 7, 9, 10, 14, 21), 1 PENDING HUMAN (AC-23; its stub half and its "backend unchanged" half are AGENT-EXECUTED PASS). AGENT-EXECUTED PASS for a whole AC: 0 of 23. Fully verified (nothing pending): 0 of 23 are agent-verified; 14 of 23 are closed on human-reported evidence alone. The Electron re-run does not change these counts: it closes the dialog-in-Electron gap inside AC-9, which still has its real-delete part pending.
Requirements: 11 FR (3 HUMAN-REPORTED PASS only, 8 with a PENDING HUMAN part), 7 NFR (see section 6.2).
Open critical/major review findings: 0/0 (code-review.md round 3; none of my evidence contradicts it). Open minor 5 and nit 6 are listed in section 7.

## 2. Verification plan
Commands come only from `approvals.md` rows G4 and "G4 extension" (the sole authorised commands) and from the Orchestrator's task. No CI definition exists (Glob `.github/**` found nothing); the repository has no build, lint or type-check step for `frontend/` (README.md: "no build step, no npm and no dependency").
| Check | Purpose | Command (provenance) | Prerequisites |
|---|---|---|---|
| V-1 | Java runtime present | `java -version` (approvals.md G4) | none |
| V-2..V-4 | Branch and change-set scope (backend and .claude untouched) | `git branch --show-current`, `git status --short`, `git diff --stat` (Orchestrator task; read-only) | none |
| V-5 | Proxy and static-server black-box test against an in-process stub backend | `java frontend/server/FrontendServerTest.java` from the repo root, no argument (approvals.md G4 extension; README.md "What is not verified") | JDK 25 on PATH; the test starts and stops its own child server and stub |
| S-1..S-8 | Static text checks by Grep/Read (they prove nothing runs) | Grep and Read tools, no shell | none |
| Not planned | Build, lint, type check, dependency audit | no command defined in the repo | n/a |

## 3. Independent discovery notes
- Commands re-derived from: `docs/sdlc/CAP-1/approvals.md` (G4, G4 extension), `frontend/README.md` (lines 129-136, 39-53), `docs/sdlc/CAP-1/architecture.md` 10.1/10.2, `docs/sdlc/CAP-1/impl-plan.md` sections 3.1 and 6, header of `frontend/server/FrontendServerTest.java` (line 5: `java server/FrontendServerTest.java`).
- Disagreements with impl-plan.md / implementation-report.md:
  1. impl-plan.md section 7 requires the final run of test B to show `0 skipped`; the run shows `1 skipped` (the case "server refuses to start without public/index.html" is skipped whenever index.html exists). The developer reported the same figures (`32 passed, 0 failed, 1 skipped`, implementation-report.md section 11, row 7); my run matches them. Same gap as open finding CR-2. Not a failure, a recorded deviation.
  2. impl-plan.md and G4 say the commands run from `frontend/`; the G4 extension (human, S5 Q1) authorises the repo-root form `java frontend/server/FrontendServerTest.java`. I ran only the root form. The `frontend/`-relative form in the README is therefore not verified by me.

## 4. Evidence
All commands run from `C:\Users\NarlaSuryaSnehith\Desktop\claude-capstone-project` (repo root). Exit codes were captured with a trailing `echo "exit=$?"`, which is part of the recorded cmd line.

### V-1 Java runtime
cmd: `java -version 2>&1; echo "exit=$?"`
cwd: repo root
exit: 0
result: AGENT-EXECUTED PASS (informational)
output:
```
java version "25.0.5.1" 2026-11-17 LTS
Java(TM) SE Runtime Environment (build 25.0.5.1+4-LTS-20)
Java HotSpot(TM) 64-Bit Server VM (build 25.0.5.1+4-LTS-20, mixed mode, sharing)
exit=0
```

### V-2 Current branch
cmd: `git branch --show-current; echo "exit=$?"`
cwd: repo root
exit: 0
result: AGENT-EXECUTED PASS (informational)
output:
```
feature/CAP-1-todo-frontend-ui
exit=0
```

### V-3 Working-tree status
cmd: `git status --short; echo "exit=$?"`
cwd: repo root
exit: 0
result: AGENT-EXECUTED PASS (informational; confirms no path under `backend/`)
output:
```
 M .claude/settings.json
?? docs/codemie/
?? docs/sdlc/CAP-1/
?? frontend/
exit=0
```

### V-4 Diff statistics (tracked files)
cmd: `git diff --stat; echo "exit=$?"`
cwd: repo root
exit: 0
result: AGENT-EXECUTED PASS (informational)
output:
```
warning: in the working copy of '.claude/settings.json', LF will be replaced by CRLF the next time Git touches it
 .claude/settings.json | 1 -
 1 file changed, 1 deletion(-)
exit=0
```
Reading: the only tracked file changed is `.claude/settings.json` (1 deletion; modified by the human before this branch work, NOT part of CAP-1, must not be staged). `backend/` is unchanged (no tracked diff, no untracked path). `.claude/` is unchanged apart from that file. `docs/codemie/` is untracked and not gitignored: it must stay out of the PR (stage explicit paths only).

### V-5 Proxy and static-server test (test B)
cmd: `java frontend/server/FrontendServerTest.java 2>&1; echo "exit=$?"`
cwd: repo root
exit: 0
result: AGENT-EXECUTED PASS (32 cases executed and passed, 0 failed, 1 skipped; scope: Java server against a stub backend only, no browser code, not the real backend)
output (complete, 34 lines):
```
SKIPPED server refuses to start without public/index.html (public/index.html exists)
PASS server started from the repository root finds frontend/public
PASS static GET / returns text/html
PASS static GET /js/main.js returns text/javascript
PASS static GET /tests redirects to /tests/
PASS static missing file returns 404
PASS static POST returns 405
PASS /todosx is not proxied
PASS proxy GET /todos
PASS proxy GET /todos/abc-123
PASS proxy GET /todos/abc-123/status
PASS proxy POST /todos
PASS proxy POST /todos/abc-123
PASS proxy POST /todos/abc-123/status
PASS proxy PUT /todos
PASS proxy PUT /todos/abc-123
PASS proxy PUT /todos/abc-123/status
PASS proxy PATCH /todos
PASS proxy PATCH /todos/abc-123
PASS proxy PATCH /todos/abc-123/status
PASS proxy DELETE /todos
PASS proxy DELETE /todos/abc-123
PASS proxy DELETE /todos/abc-123/status
PASS proxy passes through 200 json
PASS proxy passes through 201 json
PASS proxy passes through 204 no body
PASS proxy passes through 400 envelope
PASS proxy passes through 404 envelope
PASS proxy passes through 405 envelope
PASS proxy passes through 415 envelope
PASS proxy passes through 500 envelope
PASS proxy passes through 400 text/html
PASS proxy answers 502 BAD_GATEWAY when the backend is down
32 passed, 0 failed, 1 skipped
exit=0
```
Summary line verbatim: `32 passed, 0 failed, 1 skipped`. The previous developer run was `32 passed, 0 failed, 1 skipped` (implementation-report.md section 11, row 7); same result. The one skipped case is the refuse-to-start case (finding CR-2); it was not run and is listed in section 10. Test B takes no argument and ran once.

### AGENT-EXECUTED STATIC CHECKS (Grep/Read tools; they show how the files read, they do not prove anything runs)
| Id | Check | How | Result |
|---|---|---|---|
| S-1 | No `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write`, `eval(`, `new Function` anywhere under `frontend/public` (so also `js/`) | Grep, regex `innerHTML\|outerHTML\|insertAdjacentHTML\|document\.write\|eval\s*\(\|new Function`, path `frontend/public` | AGENT-EXECUTED PASS: "No matches found" |
| S-2 | Zero non-ASCII characters in any file under `frontend/` (server, public, tests, README) | Grep, regex `[^\x00-\x7F]`, path `frontend`, count mode | AGENT-EXECUTED PASS: "Found 0 total occurrences across 0 files" |
| S-3 | Every import resolves to an existing file and an exported name | Grep of all `import`/`export` lines in `frontend/public/**/*.js`, plus Read of the multi-line import in `validation.test.js:8-14` and the module list in `tests/index.html:30-38`. `app.js` imports `createView` (view.js:21), `createAnnouncer` (announcer.js:12), `createConfirmDialog` (dialog.js:21), and namespace imports of validation.js and messages.js; the members it uses (`validation.validateTask`, `messages.outcomeFor`) are exported (validation.js:96, messages.js:79). `main.js` imports `createApiClient` (api.js:100) and `createApp` (app.js:38). Tests import only names exported by `harness.js` (describe, it, assert, beforeEach, afterEach, createRunner, run, renderReport), `fakes.js` (settle, createFakeApi, createManualDefer, createFakeFetch, createFakeTimer, errorResponse, jsonResponse, task, ok, fail, mountSandbox), `validation.js` (WHITE_SPACE_CODE_POINTS, codePointLength, isWhiteSpace, trimText, validateTask), `messages.js` (MESSAGES, outcomeFor). The 7 dynamic imports in `tests/index.html` all name existing files. `api.js`, `messages.js`, `validation.js`, `announcer.js`, `view.js`, `dialog.js` import nothing | AGENT-EXECUTED PASS (reading only; whether the files parse in a browser is not shown by any agent run, the human's page runs are the only evidence) |
| S-4 | `index.html` loads `js/main.js` as a module | Read `frontend/public/index.html:59` `<script type="module" src="js/main.js"></script>` | AGENT-EXECUTED PASS |
| S-5 | DOM ids used by the JS exist in `index.html` | Grep of `'#...'` selectors in `frontend/public/js` against `index.html`. view.js:25-39 uses add-form, add-title, add-description, add-title-error, add-description-error, add-submit, task-list, list-status, list-heading, retry, orphan, orphan-heading, orphan-title, orphan-description, orphan-dismiss; app.js:51-52 uses msg-status, msg-alert, confirm-dialog; dialog.js:22-24 uses confirm-text, confirm-cancel, confirm-ok; main.js:10-12 uses `app`, `boot-note`. All 21 are present in index.html (lines 11-57) | AGENT-EXECUTED PASS |
| S-6 | File list equals the impl-plan scope manifest | Glob `frontend/**/*` returned 23 files: server/ (2), public/index.html, public/css/styles.css, public/js/ (8: validation, api, messages, announcer, dialog, view, app, main), public/tests/ (10: index.html, harness, fakes, validation, api, messages, announcer, dialog, app, app-edit-delete), README.md. This equals the manifest (impl-plan.md section 4, 23 `frontend/` rows). Glob `docs/sdlc/CAP-1/*` shows `implementation-report.md` present. `git status` (V-3) shows nothing outside `frontend/`, `docs/sdlc/CAP-1/`, `docs/codemie/` and the human's `.claude/settings.json`; `docs/changelog.md` is untouched (Stage 8 job) | AGENT-EXECUTED PASS |
| S-7 | README commands match what was run | README.md:129-136 gives `java frontend/server/FrontendServerTest.java` from the repo root, exactly the command run in V-5; README.md:22 says `java -version` printed 25.0.5.1, matches V-1; README.md:16-20 says the server starts from the repo root, supported by the passing case `server started from the repository root finds frontend/public` (V-5). Not matched by any run: `java server/FrontendServer.java` (README:42), `java server/FrontendServerTest.java` from `frontend/` (README:131), both `JAVA_HOME` forms, and the backend start (README:30); see section 10 | AGENT-EXECUTED PASS for the root-form test command and the java version; the other README commands are NOT_RUN |
| S-8 | No `maxlength` attribute in the page (D-6) | Grep `maxlength` under `frontend/public`: 2 matches, both in `tests/announcer.test.js` (the page-shell test that asserts the absence); none in `index.html` or `js/` | AGENT-EXECUTED PASS |

## 5. Edge and failure scenarios
| Scenario | Evidence (check id / test name) | Result | Gap |
|---|---|---|---|
| Missing file or path | V-5: `static missing file returns 404`; `/todosx is not proxied`; `static GET /tests redirects to /tests/`; `static POST returns 405` | AGENT-EXECUTED PASS | Refuse-to-start without `public/index.html` is SKIPPED (CR-2). Malformed percent path in a static path and path-traversal cases not exercised (H-2 for the hostile-input part) |
| Invalid or empty input (empty/whitespace title, 100/101, 500/501, emoji count, trimming) | Self-test groups `validation: validateTask rules and messages (AC-5, AC-6, AC-17)`, `validation: named vectors ... (AC-16)`, `app part A ... add (AC-4, AC-5, AC-6, AC-13, AC-14, AC-17; case (d))` | HUMAN-REPORTED PASS (summary `275 passed, 0 failed` in Edge 158 and in VS Code Electron / Chrome 150; groups mapped by my reading, plus the human's per-test names for the Electron run, not stored in the repo) | Not agent-run; keyboard Enter on an empty title is on the manual checklist: PENDING HUMAN |
| Backend down / unreachable | V-5: `proxy answers 502 BAD_GATEWAY when the backend is down` (server half). UI half: self-test groups `api: classification step 1, network and timeout (NFR-6 logic only)`, `app part A ... view and initial load (AC-1, AC-2, AC-3, AC-18 ...)` | AGENT-EXECUTED PASS (proxy 502) and HUMAN-REPORTED PASS (UI with fake API) | Real load failure with the real backend stopped and Retry: PENDING HUMAN (checklist "AC-3") |
| Backend error and non-JSON responses (400/404/405/415/500, `400 text/html`) | V-5: seven `proxy passes through ...` cases (status, content type and bytes unchanged). UI classification: groups `api: classification, unexpected 2xx (AC-20)`, `api: classification step 4 and 5, failures`, `messages: *` | AGENT-EXECUTED PASS (pass-through) and HUMAN-REPORTED PASS (UI mapping) | No proxy case for 413 or for an oversized body (65,536-byte limit not exercised) |
| 404 task no longer exists (edit, delete, status), orphaned draft | Groups `app part B ... 404 handling and orphan draft (AC-12, AC-22; cases (a), (b); DR-1, DR-14)` | HUMAN-REPORTED PASS | Not tried against a real 404 |
| Duplicate or concurrent action (double click, same task confirm twice, refresh vs add ordering) | Groups `add (... AC-13 ...)`, `status change (AC-10, AC-13 ...)`, `edit (... AC-13 ...)`, `delete (AC-9, AC-13 ... DR-12 ...)`, `ordering of load and mutation results (DR-2, case (c) ...)` | HUMAN-REPORTED PASS | Proxy under concurrent requests not tested |
| Delete confirmation dialog on every closing path | Groups `dialog: confirmDelete`, `delete with the real dialog (AC-9, case (g) extension)`. History: 263 passed, 7 failed (VS Code Electron, Chrome 150, all 7 in the real dialog) before the fix, then 275 passed, 0 failed in Edge 158 and 275 passed, 0 failed in the same VS Code Electron browser (Chrome 150) after. Per the human's per-test paste for the Electron run: all five `dialog: confirmDelete` cases and the `delete with the real dialog` Delete, Cancel and Escape cases PASS | HUMAN-REPORTED PASS in Edge 158 and in VS Code Electron / Chrome 150 | The per-test paste is not stored in the repo. Root cause of the original failures was never proven (developer confidence LOW; implementation-report.md section 12), so the fix is defensive and the pass shows the symptom gone in two browsers, not the cause understood; real Escape key by hand and screen reader: PENDING HUMAN |
| Unsafe markup shown as text (functional display only) | S-1 (no HTML-string APIs in the JS); group `view and initial load (... AC-21 ...)` | AGENT-EXECUTED PASS (static) and HUMAN-REPORTED PASS | Real task with `<b>x</b><script>1</script>` on the real backend: PENDING HUMAN. No security testing (H-2) |
| Timeout (10 s) and gateway timeout (504 after 30 s) | `api: classification step 1, network and timeout` uses a fake timer (abort logic only) | HUMAN-REPORTED PASS (logic); time limit NOT_RUN BY H-2 | 504 path and real 10 s limit not run |
| Permission denied | n/a (local app, no authentication) | n/a | none |

## 6. Acceptance criteria matrix
Evidence legend: A = self-test page `http://127.0.0.1:8000/tests/` (HUMAN-REPORTED: `275 passed, 0 failed` in Microsoft Edge 158 and `275 passed, 0 failed` in VS Code Electron / Chrome 150; the Electron paste listed every test by name, all PASS, but it is not stored in the repo; "tests/ group" cites the `describe` group, which for groups the repo does not record is my reading of the test files); B = V-5 proxy test (AGENT-EXECUTED); D = manual checklist in `frontend/README.md` section 4 (NOT reported: PENDING HUMAN); S-n = static check. No row is an agent execution of the UI.

### 6.1 Acceptance criteria (23)
| AC | Evidence | Result | Notes |
|---|---|---|---|
| AC-1 | A: `app part A ... view and initial load (AC-1 ...)`; D: list against real backend | HUMAN-REPORTED PASS (self-test only) + PENDING HUMAN (real backend list) | rests on the human's self-test page |
| AC-2 | A: `view and initial load (AC-2 ...)` | HUMAN-REPORTED PASS | rests only on the self-test page |
| AC-3 | A: `view and initial load (AC-3, AC-18)`; B: `proxy answers 502 BAD_GATEWAY when the backend is down`; D: backend stopped, Retry, backend started | HUMAN-REPORTED PASS (UI with fake API) + AGENT-EXECUTED PASS (proxy 502 only) + PENDING HUMAN (real failure and Retry) | the real load failure and Retry are not reported |
| AC-4 | A: `app part A ... add (AC-4 ...)`, case (c); D | HUMAN-REPORTED PASS + PENDING HUMAN (real add) | |
| AC-5 | A: `validation: validateTask ... (AC-5)`, `add (... AC-5 ...; case (d))` | HUMAN-REPORTED PASS | rests only on the self-test page; keyboard Enter on empty title is PENDING HUMAN (NFR-3 item) |
| AC-6 | A: `validation: validateTask ... (AC-6)`, `add`, `edit` groups | HUMAN-REPORTED PASS | rests only on the self-test page |
| AC-7 | A: `app part B ... edit (AC-7 ...)`; D | HUMAN-REPORTED PASS + PENDING HUMAN (real edit) | |
| AC-8 | A: `edit (AC-8 ...)` (zero recorded requests on cancel) | HUMAN-REPORTED PASS | rests only on the self-test page |
| AC-9 | A: `app part B ... delete (AC-9 ...)`, `dialog: confirmDelete` (5 cases), `delete with the real dialog (AC-9, case (g) extension)` Delete/Cancel/Escape; D: real delete | HUMAN-REPORTED PASS (Edge 158 and VS Code Electron / Chrome 150) + PENDING HUMAN (real delete) | the dialog failed 7 tests in the first browser run and passes in both browsers after a defensive fix of unproven root cause; the per-test names for the Electron run come from the human's paste, not stored in the repo |
| AC-10 | A: `status change (AC-10 ...)`; D | HUMAN-REPORTED PASS + PENDING HUMAN (real status change) | |
| AC-11 | A: `add`, `status change`, `edit`, `delete` groups (failure branches, case (e)) | HUMAN-REPORTED PASS | rests only on the self-test page, fake API |
| AC-12 | A: `404 handling and orphan draft (AC-12 ...)` | HUMAN-REPORTED PASS | rests only on the self-test page |
| AC-13 | A: groups naming AC-13 in `add`, `status change`, `edit`, `delete` | HUMAN-REPORTED PASS | rests only on the self-test page (fake fetch records one request) |
| AC-14 | A: `add (... AC-14 ...)`, `announcer: regions, repeat text, sequence number`; D: keyboard and screen reader | HUMAN-REPORTED PASS (live-region content in the sandbox) + PENDING HUMAN (screen reader, real announcement) | WCAG best effort only |
| AC-15 | A: `messages: validation results`, `api` classification groups | HUMAN-REPORTED PASS | rests only on the self-test page |
| AC-16 | A: `validation: named vectors ... (AC-16)` | HUMAN-REPORTED PASS | rests only on the self-test page |
| AC-17 | A: `validation: validateTask ... (AC-17)`, `add (... AC-17 ...)` | HUMAN-REPORTED PASS | rests only on the self-test page |
| AC-18 | A: `view and initial load (AC-1, AC-2, AC-3, AC-18 ...)` | HUMAN-REPORTED PASS | rests only on the self-test page |
| AC-19 | A: `status change (AC-10, AC-13, AC-19, AC-11)`, `keyed reconcile (D-4, case (f) part, FR-11)` | HUMAN-REPORTED PASS | rests only on the self-test page |
| AC-20 | A: `api: classification, unexpected 2xx (AC-20)`, `messages: http, network, timeout and unexpected results`; B: `proxy passes through 400 text/html` | HUMAN-REPORTED PASS (UI) + AGENT-EXECUTED PASS (proxy pass-through half) | UI half rests only on the self-test page |
| AC-21 | A: `view and initial load (... AC-21 ...)`; S-1 (no HTML-string API in `frontend/public/js`); D: real task | HUMAN-REPORTED PASS + AGENT-EXECUTED PASS (static, supporting only) + PENDING HUMAN (real task) | functional display only; no security testing (H-2) |
| AC-22 | A: `404 handling and orphan draft (AC-12, AC-22 ...)` | HUMAN-REPORTED PASS | rests only on the self-test page |
| AC-23 | B: all `proxy ...` cases against a stub, `server started from the repository root ...`; V-3/V-4: `backend/` unchanged; D: real-backend flow, no CORS error, same-origin requests in the Network tab | PENDING HUMAN (the real-backend flow was not reported). Parts: AGENT-EXECUTED PASS for the stub proxy and for "backend unchanged" | no agent started the backend; no browser console seen by anyone yet |

"Rests only on the self-test page" means the evidence is the human-reported self-test run (two browsers now), not an agent execution and not a manual checklist run. Which AC rest only on the human's self-test page (nothing agent-verified, nothing else reported): AC-2, 5, 6, 8, 11, 12, 13, 15, 16, 17, 18, 19, 22. With an additional PENDING HUMAN part: AC-1, 3, 4, 7, 9, 10, 14, 21. With a partial agent check: AC-3 (502), AC-20 (400 text/html), AC-21 (static), AC-23 (stub, backend unchanged).

### 6.2 Functional and non-functional requirements
| Requirement | Evidence (ACs) | Result | Notes |
|---|---|---|---|
| FR-1 | AC-1, 3, 18 | HUMAN-REPORTED PASS + PENDING HUMAN (real list, real load failure) | |
| FR-2 | AC-2, 18 | HUMAN-REPORTED PASS | self-test only |
| FR-3 | AC-2, 4 | HUMAN-REPORTED PASS + PENDING HUMAN (real add) | |
| FR-4 | AC-7, 8 | HUMAN-REPORTED PASS + PENDING HUMAN (real edit) | |
| FR-5 | AC-9 | HUMAN-REPORTED PASS (Edge 158 and VS Code Electron / Chrome 150) + PENDING HUMAN (real delete) | |
| FR-6 | AC-10 | HUMAN-REPORTED PASS + PENDING HUMAN (real status) | |
| FR-7 | AC-5, 6, 15, 16, 17 | HUMAN-REPORTED PASS | self-test only |
| FR-8 | AC-4, 7, 9, 10, 14 | HUMAN-REPORTED PASS + PENDING HUMAN (messages in the real flow, screen reader) | |
| FR-9 | AC-3, 11, 12, 14, 15, 20, 22 | HUMAN-REPORTED PASS + PENDING HUMAN (real load failure) | |
| FR-10 | AC-4, 7, 11, 20 | HUMAN-REPORTED PASS + PENDING HUMAN (real flow) | |
| FR-11 | AC-13, 19 | HUMAN-REPORTED PASS | self-test only |
| NFR-1 | AC-23 and the real flow | PENDING HUMAN (no CORS error against the real backend not reported). AGENT-EXECUTED PASS for the server half: proxy methods, paths, pass-through and 502 against a stub (V-5), `backend/` unchanged (V-3, V-4) | |
| NFR-2 | AC-21 | HUMAN-REPORTED PASS (display as text, self-test) + AGENT-EXECUTED PASS (S-1 static) + PENDING HUMAN (real task). Security aspect: NOT_RUN BY H-2 | not a claim of security |
| NFR-3 | AC-14; keyboard, focus, dialog trap | HUMAN-REPORTED PASS (sandbox tests: live-region content, focus, `announcer` group, dialog, and per the Electron paste the CR-5 retry-focus and CR-6 accessible-name tests) + PENDING HUMAN (keyboard-only walk-through, screen reader, visible focus by eye, real Escape) | WCAG 2.1 AA best effort, not certified |
| NFR-4 | browsers | HUMAN-REPORTED PASS on the self-test page in two browsers: Microsoft Edge 158 (`275 passed, 0 failed`) and VS Code Electron / Chrome 150 (`Code/1.147.0 Chrome/150.0.7871.250 Electron/43.8.9`, `275 passed, 0 failed`; it was 263 passed, 7 failed before the dialog fix). Standalone Chrome, Firefox, Safari: NOT_RUN | the human has reported no manual checklist run in any browser; both browsers are Chromium-based, so this is no evidence for Firefox or Safari |
| NFR-5 | English only | HUMAN-REPORTED PASS for message wording (group `messages: *`). I read `index.html` and `README.md`; both are English. No localisation mechanism claimed | text review not done on all strings by me |
| NFR-6 | AC-3, 11 (unreachable/network error) | HUMAN-REPORTED PASS (abort logic through a fake timer, group `api: classification step 1`) ; the 10 s time limit: NOT_RUN BY H-2 | |
| NFR-7 | none | NOT_RUN BY H-2 | not testable in this iteration |

## 7. Code review findings status
No critical and no major finding exists in code-review.md (round 3: critical 0, major 0). Minor and nit findings, for completeness:
| Finding | Severity | Status per evidence (resolved / open / unknown) | Evidence |
|---|---|---|---|
| CR-1 README promised a root start the server lacked | minor | resolved | V-5 `PASS server started from the repository root finds frontend/public` (AGENT-EXECUTED); S-7 |
| CR-2 skip guards in test B | minor | open | V-5 shows `1 skipped` (refuse-to-start case never exercised) |
| CR-3 proxy fidelity cases thin (no escaped path, `Accept` forwarding, HEAD, `Cache-Control`) | minor | open | V-5 case list has none of these |
| CR-4 proxy prefix and dot-segments | minor | open (not testable by me: H-2) | no test; code reading only in code-review.md |
| CR-5 focus after Retry | minor | resolved at test level, HUMAN-REPORTED (code-review says resolved by reading; the human's Electron per-test paste lists the CR-5 retry-focus tests as PASS; the paste is not stored in the repo; real focus by eye not observed) | not shown by agent evidence |
| CR-6 pending text in accessible name | minor | resolved at test level, HUMAN-REPORTED (the Electron per-test paste lists the CR-6 accessible-name test as PASS; paste not stored; screen reader not checked) | not shown by agent evidence |
| CR-7 dialog test timing | minor | superseded (nextClose removed); the human's Edge 158 and VS Code Electron runs both pass the dialog groups | clarifications.md |
| CR-8 test B run instructions only a side note in README | minor | open | README.md:129-136 read |
| CR-14 dialog resolves on three routes, architecture.md not amended | minor | accepted by the human at G5 (approvals.md G5 row); whether architecture.md was amended: not checked by me | approvals.md |
| CR-9..CR-13, CR-15 | nit | open (human decision) | not re-verified |

## 8. Documentation validation
| Document | Check | Result | Evidence |
|---|---|---|---|
| frontend/README.md | Root-form test command runs as written | AGENT-EXECUTED PASS | V-5 |
| frontend/README.md | `java -version` claim (25.0.5.1) | AGENT-EXECUTED PASS | V-1 |
| frontend/README.md | Start command `java server/FrontendServer.java` and the test command from `frontend/` | NOT_RUN | not authorised (standalone server would hold port 8000); only the root form of the test was authorised |
| frontend/README.md | Backend start `./mvnw spring-boot:run` | NOT_RUN | Maven not authorised |
| frontend/README.md | Exit code 1 with a one-line message when `public/index.html` is missing | NOT_RUN | the test case is SKIPPED in V-5 |
| frontend/README.md | Referenced paths exist (`server/FrontendServer.java`, `server/FrontendServerTest.java`, `public/`, `/tests/` page, `docs/sdlc/CAP-1/implementation-report.md`) | AGENT-EXECUTED PASS (Glob, S-6) | S-6 |
| frontend/README.md | Checklist and self-test paste-back instructions present | AGENT-EXECUTED PASS (read) | README.md:58-119 |
| implementation-report.md | Its `32 passed, 0 failed, 1 skipped` figure | reproduced by my own run | V-5 (I re-ran; I did not copy) |

## 9. Failures and reproduction
| Check | What failed | Steps to reproduce | Suspected area |
|---|---|---|---|
| none | No executed check failed. | n/a | n/a |

Defects found by this stage: none in executed checks. Observations (not defects): (1) test B shows `1 skipped` against impl-plan DoD `0 skipped` (CR-2); (2) git prints an LF-to-CRLF warning for the human's `.claude/settings.json` (informational); (3) the Edge 158 paste was cut off and gave only the summary line; the Electron paste listed every test by name but is not stored in the repo, so the AC-to-test mapping above combines those reported names with my reading of the group names; (4) `docs/codemie/` is untracked and not gitignored; it must not be staged for the CAP-1 PR; `.claude/settings.json` must not be staged either.

## 10. Not run
PENDING HUMAN (the manual checklist has NOT been reported; HUMAN-REPORTED when the human pastes it). 5 items remain. The former sixth item, the self-test re-run in the VS Code Electron browser, is closed as HUMAN-REPORTED PASS (`275 passed, 0 failed`, `Code/1.147.0 Chrome/150.0.7871.250 Electron/43.8.9`; not agent-verified, per-test paste not stored in the repo):
| Check | Reason |
|---|---|
| AC-23 real-backend flow: list, add, edit, status, delete, no CORS error in the console, requests to `http://127.0.0.1:8000/todos...` | needs the real backend and a browser; the human has not reported it |
| AC-3 real load failure with the backend stopped, then Retry with the backend started | same |
| Real delete against the real backend (dialog, Cancel, Delete, "Task deleted") | same |
| Keyboard-only walk-through (Tab order, visible focus, Enter on empty title, dialog Tab trap, Escape) | needs a browser and a person |
| Screen-reader check (best effort) | needs a person |

NOT_RUN BY H-2 (human decision, a verification gap, not waived and not claimed as met):
| Check | Reason |
|---|---|
| NFR-2 security aspect (any security assessment beyond the functional AC-21 display check) | H-2 |
| NFR-6 10 second time limit as a timing claim | H-2 |
| NFR-7 performance | H-2 |
| Path traversal, hostile or malformed input, proxy dot-segment handling (CR-4), load and response times | H-2 |

NOT_RUN (other):
| Check | Reason |
|---|---|
| Execution of any UI JavaScript by an agent | no JavaScript engine and no browser available to this agent; no agent has ever executed it |
| `java server/FrontendServer.java` standalone and the `frontend/`-relative test form | not authorised (a standalone server would hold port 8000 and cannot be stopped by this agent) |
| Backend start (`./mvnw spring-boot:run` or the packaged JAR) | Maven and other commands not authorised |
| Test B case `server refuses to start without public/index.html` | SKIPPED by the test (index.html exists) |
| 504 gateway-timeout path, malformed percent paths, 413 oversize body through the proxy | not covered by any test |
| Browsers other than Edge 158 and VS Code Electron / Chrome 150: standalone Chrome, Firefox, Safari | not reported |
| Build, lint, type check, dependency audit | no command defined in the repository (no CI, no manifest; README: no build step, no dependency) |
