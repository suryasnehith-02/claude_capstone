# Requirements - CAP-1: Todo App - Frontend: Task management user interface

Status: FINAL, awaiting gate G1 approval (0 open questions; Q-1..Q-6 resolved by the human)        Source of truth: JIRA CAP-1 (agentic-workflow-123.atlassian.net)

Revision: attempt 2 of 3 (mode: clarified). Updated in place from the attempt 1 draft by folding in `docs/sdlc/CAP-1/clarifications.md` (standing directive H-1..H-3 and the human's answers to Q-1..Q-6, "Stage 1 clarifications, round 1"). Jira was not re-fetched (story unchanged; no instruction to re-fetch).

Standing human directive (clarifications.md): H-1 work fast; H-2 NO security, speed or performance testing (those checks are NOT_RUN by human decision; this is a verification gap, no requirement is waived or presented as verified); H-3 no stage or gate is skipped.

## 0. Verification limitations (H-2, human decision)
- Security, speed and performance checks are NOT_RUN by human decision (H-2). They will be reported as NOT VERIFIED / NOT_RUN in the verification report, at G6 and in the PR description. The requirement is still recorded; it is not waived and not claimed as met.
- Items affected: NFR-2 (security aspect only), NFR-6 (the 10-second time limit), NFR-7 (performance). Their status is "NOT_RUN by H-2" in sections 5, 6 and 11.
- Still verified functionally: the safe plain-text display of markup (AC-21, a functional check, not a security test), failure handling when the backend is unreachable or returns errors, and all other FR/AC.

## 1. Source summary
| Field | Value |
|---|---|
| Issue type / status / priority | Story / To Do / Medium |
| Reporter / assignee | not returned by the evidence view (not retrievable from the fields requested) |
| Labels / components | labels: frontend, todo-app; components: none |
| Parent / epic | none |
| Linked issues | No Jira issue links on CAP-1. The companion story CAP-2 "Todo App - Backend: Task management REST APIs" is named in the description (dependency). It is already implemented and merged; read here only as the interface source from `docs/sdlc/CAP-2/requirements.md` (Appendix A) and `backend/README.md`, not re-fetched from Jira |
| Remote links / Confluence pages | none |

### Retrieval log
| Item | Operation used | Result |
|---|---|---|
| Site | cloudId supplied by the Orchestrator (ae0964bc-acb9-4c6c-82a9-3eae409414f8) | used as given; getAccessibleAtlassianResources not called |
| Issue (evidence view) | getJiraIssue (CAP-1) with attachment, issuelinks fields (attempt 1) | fetched; description holds the story, task fields, FR-1..FR-11, validation/error rules, AC-1..AC-14, dependencies |
| Comments | listJiraIssueComments (attempt 1) | none (total 0) |
| Linked issues | getJiraIssue.issuelinks (attempt 1) | empty; no Jira-linked issues. CAP-2 used via local merged documents (see above) |
| Remote links | listJiraIssueRemoteIssueLinks (attempt 1) | none (empty list) |
| Confluence pages | none referenced by the issue | nothing to fetch; no search made (no page referenced by title) |
| Attachments | attachment field (attempt 1) | none returned; nothing downloaded |
| Local context | read `docs/sdlc/CAP-1/clarifications.md`, `docs/sdlc/CAP-2/requirements.md`, `backend/README.md` | read; they define the real merged API contract, the no-CORS constraint and the human's decisions |
| Attempt 2 | no Jira or Confluence call | answers applied from clarifications.md only |

Items not retrievable: reporter and assignee only.

## 2. Business context
The Todo app needs a user interface so a user can view, add, edit, delete and complete tasks and see what is done and what is pending from one screen (CAP-1 user story). The frontend holds no authoritative task data: it shows only what the backend (CAP-2) confirmed after each action (CAP-1 description, FR-10). Value: the user organises work from a single screen; the backend stays the single source of truth. Anonymous single-user, shared collection (CAP-2 A-1; accounts/authentication out of scope).

## 3. User story
**As a** todo app user,
**I want** a user interface where I can view, add, edit, delete and complete my todo tasks,
**So that** I can organise my work and track what is done and what is pending from a single screen.

(verbatim from CAP-1)

## 4. Functional requirements
Priorities are not stated in the story; all are `Must (inferred)` because the story cannot function without any of them (A-1). FR-1..FR-11 and their wording are copied from Jira; Notes record interpretation and the decision that settled it.

| ID | Requirement (testable) | Priority | Source | Notes |
|---|---|---|---|---|
| FR-1 | Display the list of all tasks, showing title, description (if any) and status. Newest tasks appear first. | Must (inferred) | CAP-1 description | Backend `GET /todos` already returns newest first (CAP-2 FR-2/AC-26). Decided Q-3(c) (human, accepted defaults): the UI renders in the backend order and does not re-sort; a new task is shown first; an edited or status-changed task keeps its position. Backend returns `""` when no description, so show nothing for an empty description. Description is shown as plain text with line breaks kept (Q-3(d)). |
| FR-2 | Display a clear empty state when there are no tasks. | Must (inferred) | CAP-1 description | Decided Q-5(d): one screen, add form always visible, empty state shown together with the add form. |
| FR-3 | Provide a way to add a new task (title required, description optional). | Must (inferred) | CAP-1 description | `POST /todos`. Add form is always visible (Q-5(d)). |
| FR-4 | Provide a way to edit the title and description of an existing task, with the form pre-filled and a way to cancel. | Must (inferred) | CAP-1 description | `PUT /todos/{id}` is a full replacement and does not change status (CAP-2 FR-4). Both fields are always sent. Decided Q-5(e): in-place inline form in the task row, only one task in edit mode at a time. |
| FR-5 | Provide a way to delete a task, after the user confirms. | Must (inferred) | CAP-1 description | `DELETE /todos/{id}`, 204 no body. Decided Q-5(b): in-page confirmation dialog with focus handling (not the browser `confirm()`). |
| FR-6 | Provide a way to mark a task Completed or Pending. The two states are visually distinct. | Must (inferred) | CAP-1 description | `PATCH /todos/{id}/status` with exactly "Pending" or "Completed". Distinction is shown by a text label and style, not by colour alone (Q-6(a), AC-14). |
| FR-7 | Validate required fields and length limits in the UI before sending a request. | Must (inferred) | CAP-1 description | Decided Q-3(a)(b): length counted in Unicode code points after trimming; title and description are trimmed before validating and sending. |
| FR-8 | Show a success message after each successful add, edit, delete and status change. | Must (inferred) | CAP-1 description | Texts fixed by AC-4, AC-7, AC-9, AC-10. Decided Q-5(h): one message region, a new message replaces the previous one, auto-dismiss not required. |
| FR-9 | Show an understandable error message when an action fails, without exposing technical details. | Must (inferred) | CAP-1 description | Mapping per backend code: section 7.3, decided Q-4. |
| FR-10 | Update the displayed data from the backend response, not from assumptions made before the response arrives. | Must (inferred) | CAP-1 description | No optimistic update. Decided Q-5(f): after add/edit/status use the returned task; after delete remove only on 204; the whole list is refetched only after a 404 and on Retry. |
| FR-11 | Show a loading indication while a request is in progress and prevent duplicate submissions while it is pending. | Must (inferred) | CAP-1 description | Decided Q-5(a): per-task pending state (only that task's controls and the add/edit form disabled while its request runs; other tasks stay usable); the add form is disabled while an add runs. |

## 5. Non-functional requirements
The story has no NFR section. NFR-1 to NFR-3 come from stated requirements and the CAP-2 contract; NFR-3..NFR-6 targets are the human's decisions (Q-6). Per H-2, any security, speed or performance check is NOT_RUN; the requirement is still recorded.

| ID | Category | Requirement and measurable target | Source | Verification in this iteration |
|---|---|---|---|---|
| NFR-1 | Compatibility / deployment | The frontend must work with the real CAP-2 backend, which sends no CORS headers and answers OPTIONS with 405. Therefore the browser reaches the API at the same origin as the page, through the human-decided static server and proxy (section 7.1, H-C2). Measurable: the full flow (list, add, edit, status, delete) works in a browser against the running backend with no CORS error. | clarifications.md (CAP-2 S21), CAP-2 NFR-9, Q-2 | Functional; verified via AC-23 and the whole flow |
| NFR-2 | Safe display (security-flavoured) | Title and description are returned verbatim by the backend (no sanitising, CAP-2 NFR-10); the UI must display them as plain text so markup or script in a task is shown literally and never interpreted. Measurable functional check: a task titled `<b>x</b><script>1</script>` displays exactly that text (AC-21). | CAP-2 NFR-10, AC-33 | AC-21 functional check is verified. Any security testing or security assessment beyond it: NOT_RUN by H-2 |
| NFR-3 | Accessibility | Decided Q-6(a): success and error messages go to an ARIA live region (status/alert) with text, not colour alone; Completed is shown by text label and style, not colour only; every control is keyboard operable with visible focus; inputs are labelled; the delete dialog traps focus and returns it on close. Target WCAG 2.1 AA as best effort. | AC-14; Q-6(a) | Functional; verified via AC-14 and manual/automated checks the later stages define. WCAG AA is best effort, not a certified claim |
| NFR-4 | Compatibility / browsers | Decided Q-6(b): current desktop Chrome, Edge, Firefox and Safari, latest 2 versions; no Internet Explorer; responsive mobile layout not required. | Q-6(b) | Functional; the browsers actually available for verification are recorded in the verification report |
| NFR-5 | Localisation | Decided Q-6(c): English only, no localisation. | Q-6(c) | Functional (text review) |
| NFR-6 | Resilience / timeout | Decided Q-6(d): a request that does not complete within 10 seconds is aborted and treated as a failure ("timeout" case of the 5xx/unreachable rule, generic message, list and input kept). | CAP-1 Validation and error handling; Q-6(d) | The failure handling for unreachable/network error is verified functionally (AC-11, AC-3). The 10-second time limit itself is time/speed dependent: NOT_RUN by H-2 |
| NFR-7 | Performance | No target stated (CAP-2 sized for about 1,000 tasks, full list on every read, no pagination). | CAP-2 NFR-5; H-2 | NOT_RUN by human decision H-2; not testable in this iteration |

## 6. Acceptance criteria
AC-1..AC-14 are copied from Jira (origin JIRA), ids preserved, Given/When/Then text verbatim. AC-15 onward are derived criteria; they follow from the Jira rules, the CAP-2 contract and the human's decisions, and are ratified or removed at G1. None of AC-1..AC-23 depends on security, speed or performance testing, except where a "Verification" note says NOT_RUN by H-2.

| ID | Criterion | Origin | Covers |
|---|---|---|---|
| AC-1 | **List tasks.** Given tasks exist in the backend, When the user opens the application, Then every task is listed with its title, description (if any) and status, newest first. | JIRA | FR-1 |
| AC-2 | **Empty state.** Given there are no tasks, When the user opens the application, Then an empty-state message such as "No tasks yet" is shown together with the way to add a task. | JIRA | FR-2, FR-3 |
| AC-3 | **Load failure.** Given the backend cannot be reached, When the user opens the application, Then an error message with a Retry action is shown and the screen does not claim that there are no tasks. When the user selects Retry and the backend is reachable, Then the list is displayed. | JIRA | FR-1, FR-9 |
| AC-4 | **Add a task.** Given the add form is open and the title is valid, When the user submits, Then a request is sent to the backend, and when it succeeds the new task appears in the list with status Pending, the form is cleared and the message "Task added" is shown. | JIRA | FR-3, FR-8, FR-10 |
| AC-5 | **Add validation.** Given the title is empty or only whitespace, When the user submits, Then "Title is required" is shown next to the field and no request is sent. | JIRA | FR-7 |
| AC-6 | **Length validation.** Given a title over 100 characters or a description over 500 characters, When the user submits an add or an edit, Then the matching error message is shown and no request is sent. | JIRA | FR-7 |
| AC-7 | **Edit a task.** Given an existing task, When the user chooses Edit, Then a form pre-filled with its current title and description is shown. When the user saves valid changes and the backend confirms, Then the list shows the values returned by the backend and "Task updated" is shown. | JIRA | FR-4, FR-8, FR-10 |
| AC-8 | **Cancel edit.** Given the edit form is open with changes made, When the user cancels, Then no request is sent and the task is unchanged. | JIRA | FR-4 |
| AC-9 | **Delete with confirmation.** Given an existing task, When the user chooses Delete, Then a confirmation is requested. When the user confirms and the backend confirms, Then the task disappears from the list and "Task deleted" is shown. When the user declines, Then nothing changes and no request is sent. | JIRA | FR-5, FR-8 |
| AC-10 | **Mark Completed or Pending.** Given a Pending task, When the user marks it Completed and the backend confirms, Then the task is displayed as Completed in a visually distinct way and "Task marked as completed" is shown. Given a Completed task, When the user marks it Pending and the backend confirms, Then it is displayed as Pending and "Task marked as pending" is shown. | JIRA | FR-6, FR-8 |
| AC-11 | **Backend error on an action.** Given the backend returns an error or cannot be reached during add, edit, delete or status change, When the response arrives, Then an error message is shown, the list keeps showing the last state the backend confirmed, and the text the user entered is preserved. | JIRA | FR-9, FR-10 |
| AC-12 | **Task no longer exists.** Given a task was removed by someone else, When the user edits, deletes or changes its status and the backend answers 404, Then "This task no longer exists" is shown and the list is refreshed. | JIRA | FR-9 |
| AC-13 | **No duplicate submissions.** Given a request is in progress, When the user triggers the same action again (for example double-click), Then only one request is sent and the controls are disabled until the response arrives. | JIRA | FR-11 |
| AC-14 | **Accessible messages.** Given a success or error message appears, Then it is announced to assistive technologies and is not conveyed by colour alone. | JIRA | NFR-3, FR-8, FR-9 |

Derived criteria (each follows from a Jira rule, the CAP-2 contract or a human decision; ratified or removed at G1):

| ID | Criterion | Origin | Covers |
|---|---|---|---|
| AC-15 | Given the backend returns 400 VALIDATION_ERROR with `details`, Then each `details[].field` of `title` or `description` is shown as its message next to that field, other or unmapped fields (for example `status`) and a missing/empty `details` are shown as the general message "Please check your input and try again." (section 7.3), and the text the user typed is kept. | derived (CAP-1 Validation rule "Backend validation error (400)"; Q-4) | FR-9, FR-7 |
| AC-16 | Given a title or description, Then the UI measures length in Unicode code points after trimming and accepts exactly 100 (title) / 500 (description) and rejects 101 / 501; an emoji counts as 1. | derived (CAP-2 AC-28; Q-3(a)) | FR-7 |
| AC-17 | Given the user submits an add or edit, Then the trimmed title and trimmed description are the values validated and sent. | derived (CAP-1 "after trimming"; Q-3(b)) | FR-7 |
| AC-18 | Given a failure to load the list, Then no empty-state message and no stale "No tasks yet" is shown; Retry refetches the full list (`GET /todos`). | derived (CAP-1 "Failure to load the list"; Q-5(c)) | FR-1, FR-2 |
| AC-19 | Given a request is pending, Then a loading indication is visible for that request, only the controls of the affected task or form are disabled, other tasks remain usable, and the add form is disabled while an add runs. | derived (FR-11; Q-5(a)) | FR-11 |
| AC-20 | Given the backend answers with an unexpected body (not the documented envelope, a non-JSON page, or a 2xx without the expected `data`), Then the UI shows "Something went wrong. Please try again.", does not change the displayed data and shows no technical detail, status numbers or codes. | derived (FR-9, FR-10; Q-6(e)) | FR-9, FR-10 |
| AC-21 | Given a title or description containing markup or special characters (for example `<b>x</b><script>1</script>`), Then it is displayed exactly as stored, as plain text, with line breaks kept for the description. Functional display check only; security testing is NOT_RUN (H-2). | derived (CAP-2 AC-33; Q-3(d)) | NFR-2 |
| AC-22 | Given a 404 TODO_NOT_FOUND while a task's edit form is open, Then "This task no longer exists" is shown, the list is refreshed, and the user's typed text remains visible together with the message (it is not silently discarded before the message is shown). | derived (AC-11 vs AC-12; Q-5(g)) | FR-9 |
| AC-23 | Given the backend is running as documented in `backend/README.md` and the frontend static server is running as a second process, When the user opens the application at the frontend server's address, Then the list, add, edit, status change and delete all work against the real backend, with no CORS error, the page and the API share one origin, and `backend/` is unchanged. | derived (Q-2; NFR-1) | NFR-1 |

## 7. Constraints, dependencies, out of scope

### 7.1 Constraints
Human-decided constraints (decided by the human at round 1; recorded as constraints, not as design):
- **H-C1 (Q-1):** The frontend is plain HTML, CSS and JavaScript with no build step, delivered as static files in a new `frontend/` directory. No JavaScript framework, bundler or build tool; no server-rendered pages inside the Spring Boot app.
- **H-C2 (Q-2):** A small static-file server in `frontend/` serves the files and also proxies `/todos` (and sub-paths) to `127.0.0.1:8080`. The user starts the backend as in `backend/README.md`, then starts the frontend server as a second process. The page and the API share one origin. There is no change to `backend/`. The page uses the same-origin path `/todos`. The proxy passes requests and responses (methods, paths, body, status codes, JSON body) through unchanged, so the backend contract in section 7.2 is what the page sees.
- The backend sends no CORS headers and answers OPTIONS with 405 (NFR-1); this is the reason for H-C2.
- The API has no authentication, listens on `127.0.0.1:8080` by default, request body at most 65,536 bytes, UTF-8 JSON.
- H-2: security, speed and performance checks are NOT_RUN (section 0). Functional behaviour is still verified.
- No technology decision is made in this document beyond H-C1 and H-C2.

### 7.1.1 Open design items for Stage 2 (not requirement questions)
- **D-1 Runtime for the static-file-plus-proxy server (H-C2).** The architect chooses the runtime from what is confirmed installed on the user's machine. Confirmed: JDK 25. Not confirmed: Node.js, Python. If no runtime is acceptable, the architect returns NEEDS_CLARIFICATION. This is deliberately left open by the human and is not an unresolved requirement question. Any option chosen must respect H-C1 (page is plain HTML/CSS/JS, no build step) and leave `backend/` unchanged.
- **D-2 Other layout and file-structure detail** inside `frontend/` (file names, how the server is started, port) is for the architect, with repo evidence.

### 7.2 Dependencies
- CAP-2 backend (merged). Contract captured below from `docs/sdlc/CAP-2/requirements.md` Appendix A and `backend/README.md` (the real, merged contract):

| Operation | Request | Success |
|---|---|---|
| Load list | `GET /todos` | 200 `{"data":[task,...]}` newest first (empty array when none) |
| Add | `POST /todos` body `{"title","description"}` | 201 `{"data":task}`; status Pending |
| Edit | `PUT /todos/{id}` body `{"title","description"}` | 200 `{"data":task}`; status unchanged, full replacement |
| Status | `PATCH /todos/{id}/status` body `{"status":"Pending"\|"Completed"}` (exact case) | 200 `{"data":task}` |
| Delete | `DELETE /todos/{id}` | 204, no body |

Task: `{id (UUID), title, description (always present, "" if none), status, createdAt, updatedAt (ISO-8601 UTC "Z")}`. Errors: `{"error":{"code","message","details":[{"field","message"}]}}`, `details` only on VALIDATION_ERROR, `field` = JSON property name (title, description, status). Requests with a body must be sent as `application/json`.

- Constraint from the backend: Tomcat answers a malformed percent sequence in the path with its own `400 text/html` page (not the envelope). The UI only builds paths from server-issued UUIDs, so this should not occur, but an unexpected non-JSON body must be handled (AC-20).
- The user starts the backend (documented command in `backend/README.md`) before using the frontend; the backend is not started by the frontend.

### 7.3 Mapping of backend responses to frontend messages
Decided by the human (Q-4, accepted defaults). Messages in quotes marked "Jira" are fixed by the story; the rest are the human-accepted wording. Codes, status numbers, stack traces and raw backend `error.message` are never shown, except the field-level `details[].message` of a 400 VALIDATION_ERROR.

| Backend result | Where it can occur | Frontend behaviour | Source |
|---|---|---|---|
| UI validation fails (title empty/whitespace) | add, edit | "Title is required" next to the title field; no request | Jira |
| UI validation fails (title > 100) | add, edit | "Title must be 100 characters or fewer"; no request | Jira |
| UI validation fails (description > 500) | add, edit | "Description must be 500 characters or fewer"; no request | Jira |
| 400 VALIDATION_ERROR, `details[].field` = title / description | add, edit | show `details[].message` next to that field; keep typed text | Jira (400 rule), CAP-2 AC-20; Q-4 |
| 400 VALIDATION_ERROR with other field (`status`) or no/empty `details` | status, add, edit | general message "Please check your input and try again."; keep typed text | Jira ("otherwise a general message"); Q-4 |
| 400 INVALID_REQUEST_BODY, INVALID_ID | any | "Something went wrong. Please try again." (cannot be caused by valid UI input) | Q-4 |
| 404 TODO_NOT_FOUND | edit, delete, status | "This task no longer exists"; refresh the list; typed text in an open edit form stays visible (AC-22) | Jira, AC-12; Q-4, Q-5(g) |
| 404 TODO_NOT_FOUND on list/add | n/a (cannot occur) | treated as unexpected: "Something went wrong. Please try again." | Q-4 |
| 404 NOT_FOUND, 405 METHOD_NOT_ALLOWED, 413 PAYLOAD_TOO_LARGE, 415 UNSUPPORTED_MEDIA_TYPE | any | "Something went wrong. Please try again." (indicates a client/deployment misconfiguration; no technical detail shown) | Q-4 |
| 500 INTERNAL_ERROR, any 5xx (including a gateway error from the proxy when the backend is down) | any | "Something went wrong. Please try again."; list and input kept | Jira; Q-4 |
| Unreachable, network error, timeout (10 s, NFR-6) | any | "Something went wrong. Please try again."; list and input kept | Jira; Q-4, Q-6(d) |
| Load of the list fails (any cause) | initial load, Retry | error message with Retry; never an empty-state | Jira, AC-3 |
| Unexpected body (non-JSON, wrong shape) | any | "Something went wrong. Please try again."; displayed data unchanged | Q-6(e) |

### 7.4 Out of scope (CAP-1)
User accounts and authentication, filtering, sorting controls, search, pagination, drag-and-drop, offline support, notifications, and any backend logic. (Also per CAP-2: no CORS, no localisation.) Also out of scope: deployment, hosting, CI and containerisation of the frontend (A-10), responsive mobile layout, Internet Explorer.

## 8. Questions and decisions
All questions are resolved. No question is open. Answers were given by the human in `docs/sdlc/CAP-1/clarifications.md` ("Stage 1 clarifications, round 1"); the original option text is in the attempt 1 draft.

| ID | Question (short) | Status | Answer / decision | Answered by |
|---|---|---|---|---|
| Q-1 | Frontend technology/stack | resolved | Plain HTML/CSS/JS, no build step, static files in a new `frontend/` directory (recorded as human-decided constraint H-C1, not a design). The architect records the final detail with repo evidence. | human (clarifications.md Q-1) |
| Q-2 | How the UI is served and reaches the API; who runs what | resolved | A small static-file server in `frontend/` that also proxies `/todos` to `127.0.0.1:8080`; the user runs the backend (per `backend/README.md`) then the frontend server as a second process; same origin; no change to `backend/` (H-C2). The server's runtime is an open design item for Stage 2 (D-1): JDK 25 confirmed installed, Node.js and Python not confirmed. | human (clarifications.md Q-2) |
| Q-3 | Input handling | resolved | Accepted defaults: (a) length in Unicode code points; (b) trim before validating and sending, title and description; (c) UI keeps backend order, no re-sort, a new task is shown first, edited/status-changed tasks keep position; (d) description shown as plain text with line breaks kept. | human (clarifications.md Q-3..Q-6, accept all defaults) |
| Q-4 | Error messages per backend code | resolved | Accepted defaults: section 7.3 as written (404 TODO_NOT_FOUND -> "This task no longer exists" and refresh; 400 VALIDATION_ERROR -> `details[].message` beside the field, else "Please check your input and try again."; everything else -> "Something went wrong. Please try again."; never show codes or status numbers). | human (clarifications.md Q-3..Q-6) |
| Q-5 | Interaction behaviour | resolved | Accepted defaults (a)-(h): per-task pending state; in-page delete confirmation with focus management; Retry refetches the full list; one screen with the add form always visible and the empty state beside it; inline edit, one task at a time; use the returned task after add/edit/status, remove on delete, refetch only after 404 and Retry; on 404 during edit keep the typed text visible with the message and refresh the list; one message region, new message replaces the previous, no auto-dismiss required. | human (clarifications.md Q-3..Q-6) |
| Q-6 | Non-functional and robustness defaults | resolved | Accepted defaults (a)-(e): accessibility (ARIA live region, no colour-only cues, keyboard operable, visible focus, modal focus trap, WCAG 2.1 AA best effort); latest 2 versions of desktop Chrome/Edge/Firefox/Safari, no IE; English only; 10-second request timeout; unexpected response body is a failure with the generic message and no data change. | human (clarifications.md Q-3..Q-6) |

## 9. Assumptions
| ID | Assumption | Basis | Approved by |
|---|---|---|---|
| A-1 | Priorities of FR-1..FR-11 are Must. | Not stated in the story. | pending (confirmed at G1) |
| A-2 | There is a single anonymous user and one shared task collection; no authentication. | CAP-1 out-of-scope, CAP-2 A-1. | pending (follows from the story; confirmed at G1) |
| A-3 | The CAP-2 contract in section 7.2 (paths, envelopes, codes) is exactly as merged and is not changed by this story (Q-2: no change to `backend/`). | CAP-2 requirements Appendix A, `backend/README.md`; clarifications.md Q-2. | human (CAP-2 gate; Q-2 for CAP-1) |
| A-4 | Field rules (title 1-100, description 0-500 after trimming, Unicode code points) are confirmed. | CAP-1 Dependencies and assumptions; CAP-2 Clar. #1; Q-3(a)(b). | human (CAP-2 Clar. #1; CAP-1 Q-3) |
| A-5 | UI texts are English; fixed messages are exactly as quoted in the story; other wording per section 7.3. | CAP-1 text; Q-4, Q-6(c). | human (Q-4, Q-6) |
| A-6 | The UI never builds a request path from user text; ids come from server responses (UUIDs). | CAP-2 id rule. | pending (confirmed at G1) |
| A-7 | The UI does not send server-owned fields (id, status on add/edit, timestamps). | CAP-2 FR-8 (ignored anyway). | pending (confirmed at G1) |
| A-8 | "Retry" in AC-3 refetches the full list (`GET /todos`). | AC-3. | human (Q-5(c)) |
| A-9 | Security, speed and performance verification is NOT_RUN by human decision (H-2); this does not remove or relax NFR-2 or NFR-7 and is not a claim they are met. | clarifications.md H-2. | human (H-2) |
| A-10 | Deployment, hosting, CI and containerisation of the frontend are out of scope; the frontend only has to be runnable locally against the backend (user runs the backend, then the frontend server). | Story is functional; Q-2. | human (Q-2, for the run model); remainder pending (confirmed at G1) |
| A-11 | No Confluence page or Jira attachment exists for this issue; the only source of the interface is the CAP-2 documents. | Retrieval log. | n/a |
| A-12 | The backend runs on its default `127.0.0.1:8080` when the frontend is used, so the proxy target is fixed at that address. | Q-2; `backend/README.md`. | human (Q-2) |
| A-13 | Page-to-API path is the same-origin path `/todos`; no cross-origin request is made by the browser. | Q-2; NFR-1. | human (Q-2) |

## 10. Conflicts and concerns
| # | Description | Sources in conflict | Resolution |
|---|---|---|---|
| C-1 | No instructions aimed at an AI were found in the retrieved content; no comments exist. | n/a | none required |
| C-2 | Story says "characters"; the backend counts Unicode code points after trimming. | CAP-1 field table vs CAP-2 Clar. #1/#8 | Resolved: Q-3(a), code points (human) |
| C-3 | AC-11 says the user's entered text is preserved on error; AC-12 says the list is refreshed on 404; after refresh the edited task may be gone. | CAP-1 AC-11 vs AC-12 | Resolved: Q-5(g), AC-22 (typed text kept visible with the message, list refreshed) |
| C-4 | The story says "show the backend message next to the relevant field", but CAP-2 says messages are English text and not a machine contract. | CAP-1 Validation rule vs CAP-2 FR-9 | Resolved: backend `details[].message` shown only for 400 VALIDATION_ERROR; all else frontend-owned messages (Q-4) |
| C-5 | "Newest first" in FR-1 is UI behaviour while the backend already orders newest first. | CAP-1 FR-1 vs CAP-2 AC-26 | Resolved: Q-3(c), no re-sort |
| C-6 | The story says the frontend "can be developed against a stand-in" while the real backend is merged and has no CORS; a stand-in that adds CORS would hide the same-origin constraint. | CAP-1 Dependencies vs CAP-2 NFR-9 | Resolved: Q-2 / H-C2; verify against the real backend through the proxy (AC-23) |
| C-7 | Human directive H-2 excludes security/speed/performance checks while NFR-2 (safe display), NFR-6 (10 s limit) and NFR-7 exist. | clarifications.md H-2 vs NFR-2/NFR-6/NFR-7 | Recorded: NFR-2 keeps its functional check (AC-21); the security aspect, the 10 s limit and NFR-7 are NOT_RUN by H-2; carried to verification and the PR as a gap, not waived |
| C-8 | Tomcat can answer a malformed path with an HTML 400 page outside the envelope (CAP-2 known limit G2-6). | `backend/README.md` | Covered by AC-20, A-6 |
| C-9 | The proxy introduces a failure source not in the backend contract: when the backend is down the proxy cannot reach it, so the response may be a gateway error or a connection failure rather than the backend envelope. | Q-2 vs section 7.2 | Covered by the 5xx / unreachable / unexpected-body rows of section 7.3 and AC-20 |
| C-10 | The runtime for the static server is not decided; only JDK 25 is confirmed installed. | Q-2 | Carried to Stage 2 as open design item D-1; not a requirement question |

## 11. Traceability
| Requirement | Acceptance criteria | Source | Verification in this iteration |
|---|---|---|---|
| FR-1 | AC-1, AC-3, AC-18 | CAP-1 description | functional |
| FR-2 | AC-2, AC-18 | CAP-1 description | functional |
| FR-3 | AC-2, AC-4 | CAP-1 description | functional |
| FR-4 | AC-7, AC-8 | CAP-1 description | functional |
| FR-5 | AC-9 | CAP-1 description | functional |
| FR-6 | AC-10 | CAP-1 description | functional |
| FR-7 | AC-5, AC-6, AC-15, AC-16, AC-17 | CAP-1 description, validation rules | functional |
| FR-8 | AC-4, AC-7, AC-9, AC-10, AC-14 | CAP-1 description | functional |
| FR-9 | AC-3, AC-11, AC-12, AC-14, AC-15, AC-20, AC-22 | CAP-1 description, error rules | functional |
| FR-10 | AC-4, AC-7, AC-11, AC-20 | CAP-1 description | functional |
| FR-11 | AC-13, AC-19 | CAP-1 description | functional |
| NFR-1 | AC-23 (and the whole flow AC-1, AC-4, AC-7, AC-9, AC-10 against the real backend) | clarifications.md, CAP-2 NFR-9, Q-2 | functional |
| NFR-2 | AC-21 | CAP-2 NFR-10 | AC-21 functional check; security testing NOT_RUN by H-2 |
| NFR-3 | AC-14 | CAP-1 AC-14, Q-6(a) | functional; WCAG 2.1 AA best effort |
| NFR-4 | none (browser support statement, Q-6(b)) | Q-6(b) | functional on the browsers available |
| NFR-5 | none (English only, Q-6(c)) | Q-6(c) | text review |
| NFR-6 | AC-3, AC-11 (unreachable/network-error case) | CAP-1 error rules, Q-6(d) | failure handling functional; 10 s time limit NOT_RUN by H-2 |
| NFR-7 | none | CAP-2 NFR-5, H-2 | NOT_RUN by H-2 |
