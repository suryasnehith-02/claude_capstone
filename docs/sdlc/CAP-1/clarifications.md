# Clarifications - CAP-1

Questions asked by the Orchestrator and answers given by the human. Decisions made by the Orchestrator are labelled as such.

## Standing human directive (2026-11-22)
Human message: "Please Complete this FRONTEND TASK ASAP, no need to tests like security, speed, performance. Please complete this user story and tasks as soon as possible."

Recorded as:
- **H-1 Speed:** keep the work fast; no extra rounds beyond what the gates need.
- **H-2 Verification scope reduction:** NO security testing, NO speed or performance testing. Consequence to carry to the verification report, G6 and the PR description: any requirement or check about security, performance or speed is NOT VERIFIED / NOT_RUN by human decision. Functional behaviour is still verified. No requirement is waived at G1; this is a verification gap, not an acceptance of the requirement being met.
- **H-3 Gates and stages are not skipped.** Every stage and gate G1-G7 still runs and needs a real human Approve.

## Stage 1 clarifications, round 1 (asked by the Orchestrator, headers S1 Q1-S1 Q3; answers by the human)
| # | Question | Decision (decided by human) |
|---|---|---|
| Q-1 | Frontend technology | **Plain HTML/CSS/JS, no build step**, static files in a new `frontend/` directory. The architect records the final detail with repo evidence. |
| Q-2 | How the UI is served and reaches the API | **A small static-file server in `frontend/` that also proxies `/todos` to `127.0.0.1:8080`**; the user runs the backend (per `backend/README.md`) and then the frontend server as a second process; same origin; no change to `backend/`. It needs some runtime to run: the architect must pick it from what is confirmed installed (the JDK 25 is; Node.js and Python are NOT confirmed) and may return NEEDS_CLARIFICATION if none is acceptable. |
| Q-3..Q-6 | Input handling, message per backend code, interaction behaviour, non-functional defaults | **Accept ALL the analyst's defaults** as written in `requirements.md`: lengths in Unicode code points after trimming, trim before sending; UI keeps backend order (no re-sort); 404 TODO_NOT_FOUND -> "This task no longer exists" and refresh, 400 VALIDATION_ERROR -> backend field message beside the field (else "Please check your input and try again."), everything else -> "Something went wrong. Please try again." (never show codes or status numbers); per-task pending state; in-page delete confirmation with focus handling; Retry refetches the list; one screen with the add form always visible; one message area; accessibility (ARIA live region, no colour-only cues, keyboard operable, visible focus, modal focus trap, WCAG 2.1 AA best effort); latest 2 versions of Chrome/Edge/Firefox/Safari on desktop, no IE; English only; 10 s request timeout; an unexpected response body is a failure with the generic message and no data change. |

## Stage 2 clarifications, round 1 (asked by the Orchestrator, headers S2 Q1-S2 Q3; answers by the human)
| # | Question | Decision (decided by human) |
|---|---|---|
| S2-1 (G2-1) | Runtime for the small static server and proxy | **Single Java file on JDK 25**: `frontend/server/FrontendServer.java`, run from `frontend/` with `java server/FrontendServer.java`, port 8000 (overridable with `--port`), using only what ships with the JDK; no Maven module, no dependency, no build step; `backend/` unchanged. The human did NOT paste the output of `java -version` (asked, optional), so whether `java` is on his PATH is still unconfirmed; the README must also give the `JAVA_HOME` form. The only evidence for the JDK is the recorded CAP-2 verification run (`./mvnw -v`: 25.0.5.1). |
| S2-2 (G2-2) | Agent permission to run Java | **The human will add** these entries to his OWN `.claude/settings.local.json`: `Bash(java -version)`, `Bash(java server/FrontendServer.java*)`, `Bash(java server/FrontendServerTest.java)`. NOT yet done at the time of this row (stated as an intention); the Orchestrator will read the file before Stage 5 and will not edit it. If they are missing when Stage 5 starts, the proxy test is run by the human or reported NOT_RUN. |
| S2-3 (G2-3) | Who runs the browser tests of the UI | **The human runs them and pastes the results**: opens `http://127.0.0.1:8000/tests/` and pastes the "N passed, M failed" line; runs the ~10-minute manual checklist against the real backend (AC-23, load failure with the backend stopped, keyboard and screen-reader checks, which browsers were used). These results are HUMAN-REPORTED, not agent-verified, and must be labelled so in the verification report and the PR. |

## Stage 5 observations (Orchestrator, from the developer's wave 1 report)
- `java -version` executed by the developer agent printed `java version 25.0.5.1 2026-11-17 LTS`: `java` IS on the PATH of the agent shell and the JDK is 25.0.5.1 (agent-executed evidence; supersedes the earlier "java on PATH unconfirmed", which still applies to the human's own terminal).
- `java server/FrontendServerTest.java` from the repo root failed with "Could not find or load main class": the file is at `frontend/server/FrontendServerTest.java`. The permission system did not refuse the command. The agent shell starts at the repo root.
- Wave 1 test B cannot exercise the proxy until `public/index.html` exists (T-5) because the server refuses to start without it; the wave 1 run is limited to the refuses-to-start case plus SKIPPED lines. Accepted by the Orchestrator (default (a)).

## Stage 6 decision (human, 2026-11-22, answer S6 Q1)
**Fix CR-1, CR-5, CR-6, CR-7 before the browser tests**; CR-2, CR-3, CR-4, CR-8 and the nits stay open and go into the PR notes. Reason given by the Orchestrator and accepted: fix first so the human runs the browser self-test page and the checklist once. CR-1 server/README mismatch for starting from the repo root; CR-5 focus after Retry; CR-6 aria-label overriding the pending text; CR-7 dialog-test timing.

## HUMAN-REPORTED browser self-test result (2026-11-22, pasted by the human; not executed or verified by any agent)
Page: `http://127.0.0.1:8000/tests/`. Summary line reported: **`263 passed, 7 failed`** (270 tests). The human did NOT paste the names of the failing tests, the per-test output block, or the browser and version. This is the first execution of the UI JavaScript anywhere in this workflow. Consequence: gate G5 was left unanswered (the human asked to clarify) and Stage 7 must not start; the 7 failures have to be identified and fixed (or each accepted explicitly by the human) first. The failing test names are needed.

### Detail of the browser self-test result (pasted by the human; HUMAN-REPORTED)
Browser string: `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Code/1.147.0 Chrome/150.0.7871.250 Electron/43.8.9 Safari/537.36` (this looks like VS Code's built-in Electron browser, not a standalone Chrome/Edge). 270 tests: 263 PASS, 7 FAIL. The 7 failures, verbatim:
1. `dialog: confirmDelete > Cancel resolves false once, closes the dialog and returns focus to the caller's element: expected [false], got []`
2. `dialog: confirmDelete > Delete resolves true once: expected [true], got []`
3. `dialog: confirmDelete > case (g): Escape (cancel event, then close) resolves false exactly once: expected [false], got []`
4. `dialog: confirmDelete > close without a prior cancel resolves false once: expected [false], got []`
5. `dialog: confirmDelete > showModal is called once per opening: expected 2, got 1`
6. `app part B ... > delete with the real dialog (AC-9, case (g) extension) > Delete in the dialog sends exactly one DELETE, even if the row's Delete was clicked twice: expected 1, got 0`
7. `app part B ... > delete with the real dialog (AC-9, case (g) extension) > Cancel sends no request and returns focus to that row's Delete: expected {}, got {}`
All 7 involve the REAL `<dialog>` closing (resolution on the `close` event); every test with a faked dialog, and all validation, api, messages, announcer, page-shell, add, status, edit, 404, orphan and delete-with-fake-dialog tests PASS (263). Open question: product bug in dialog.js (delete would never send a request) or the CR-7 test change or the Electron environment. Action (Orchestrator): developer diagnoses and fixes by reading; the human re-runs the page in the same browser AND in plain Chrome or Edge.

### Browser self-test re-run after the dialog hardening (HUMAN-REPORTED, pasted by the human, 2026-11-22)
Browser: `Chrome/158.0.0.0 Safari/537.36 Edg/158.0.0.0` (Microsoft Edge 158, Windows 10/11 x64). Summary line: **`275 passed, 0 failed`** (previously 270 tests: 263 passed, 7 failed in the VS Code Electron browser; five tests were added by the developer's fix). All former failures now PASS, including "delete with the real dialog: Delete sends exactly one DELETE" and "Cancel sends no request and returns focus". The pasted block was cut off at its end (last visible line "PASS app part B ... delete with the real dialog ... > de"); the summary line is complete. NOT yet reported: the same page in the VS Code Electron browser where it failed, and the hands-on manual checklist against the real backend (AC-23 real-backend flow with no CORS error, load failure with the backend stopped, real delete, keyboard/screen reader). Those stay owed for Stage 7 (HUMAN-REPORTED). The root cause of the original failures was never proven (developer confidence LOW); the fix hardens the dialog so `confirmDelete` resolves exactly once on any path.

### Browser self-test re-run in the VS Code Electron browser (HUMAN-REPORTED, pasted by the human, 2026-11-22)
Browser: `Code/1.147.0 Chrome/150.0.7871.250 Electron/43.8.9` (the browser where 7 tests failed before the dialog fix). Summary line: **`275 passed, 0 failed`**. The previously failing tests now PASS: all five `dialog: confirmDelete` cases (Cancel, Delete, Escape, close without cancel, `showModal` once per opening) and both `delete with the real dialog` cases (one DELETE after Delete even if the row's Delete was clicked twice; Cancel sends no request and returns focus), plus the Escape case. Together with the Edge 158 run, both browsers pass. Still NOT reported: the hands-on checklist against the real backend (AC-23 no CORS error in the console, AC-3 load failure with the backend stopped and Retry, a real delete, keyboard-only use, screen reader).

## Context from CAP-2 (merged, human-confirmed)
- Backend API contract: `docs/sdlc/CAP-2/requirements.md` Appendix A and `backend/README.md` (paths `/todos`, `/todos/{id}`, `/todos/{id}/status`; `{"data":...}` and `{"error":{"code","message","details"}}` envelopes).
- CAP-2 decision S21 (no CORS): the backend sends NO CORS headers and answers OPTIONS with 405. The frontend must therefore be served from the SAME ORIGIN as the API or reach it through a proxy. This is a hard constraint on the frontend design.
- The API listens on 127.0.0.1:8080 by default, with no authentication.
