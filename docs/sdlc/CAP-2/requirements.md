# Requirements - CAP-2: Todo App - Backend: Task management REST APIs

Status: DRAFT (all 22 questions answered; pending human approval at gate G1)        Source of truth: JIRA CAP-2 (agentic-workflow-123.atlassian.net)

Revision: attempt 2 (mode: clarified). Decisions are taken from `docs/sdlc/CAP-2/clarifications.md` and cited as "Clar. #n". The human chose "Accept all defaults" for Clar. #1-#22 except #19 (Durable) and #21 (No CORS needed), which were asked individually. Jira was not re-fetched; the story is unchanged.

## 1. Source summary
| Field | Value |
|---|---|
| Issue type / status / priority | Story / To Do / Medium |
| Reporter / assignee | not returned by the evidence view (not retrievable from the fields requested) |
| Labels / components | labels: backend, todo-app; components: none |
| Parent / epic | none |
| Linked issues | No Jira issue links on CAP-2. CAP-1 "Todo App - Frontend: Task management user interface" (Story, To Do) is named in the description as the companion story and was found via JQL `project = CAP`. It is the consumer of this API; see section 7. |
| Remote links / Confluence pages | none (no remote links; Confluence search found nothing) |

### Retrieval log
| Item | Operation used | Result |
|---|---|---|
| Site | cloudId supplied by the Orchestrator (ae0964bc-acb9-4c6c-82a9-3eae409414f8) | used as given; getAccessibleAtlassianResources not called |
| Issue (evidence view) | getJiraIssue (CAP-2) | fetched (attempt 1); description holds the story, data model, API contract, FR-1..FR-10, error table, AC-1..AC-14 |
| Comments | listJiraIssueComments | none (total 0) (attempt 1) |
| Linked issues | getJiraIssue.issuelinks (empty); searchJiraIssuesUsingJql `project = CAP` | no formal links; CAP-1 (frontend companion) fetched via JQL, read-only. CAP-2 and CAP-1 are the only issues in the project |
| Remote links | listJiraIssueRemoteIssueLinks | none (empty list) |
| Confluence pages | searchConfluence `text ~ "CAP-2" OR title ~ "Todo App"` | no results; nothing to fetch |
| Attachments | attachment field | none; nothing downloaded |
| Human decisions | read `docs/sdlc/CAP-2/clarifications.md` (attempt 2) | 22 decisions folded in; no Jira re-fetch (story unchanged) |

Items not retrievable: reporter and assignee only.

## 2. Business context
The Todo app has a frontend (CAP-1) that holds no authoritative task data. The backend owns task data, validates every request and returns consistent, structured responses with correct HTTP status codes, so that users' tasks are stored reliably and can be managed from the UI (CAP-2 description and user story). Value: a single reliable source of truth and a stable contract that the frontend can be built against, including against a stand-in (CAP-1, Dependencies). The backend contains no UI concerns.

## 3. User story
**As a** client application of the Todo app (the Todo frontend),
**I want** APIs to create, read, update, delete and change the status of todo tasks,
**So that** users' tasks are stored reliably and can be managed from the user interface.

(verbatim from CAP-2)

## 4. Functional requirements
Priorities are not stated in the story; all are `Must (inferred)` because the story cannot function without any of them (A-9, approved under Clar. #1-#22 bulk acceptance only to the extent noted in section 9).

| ID | Requirement (testable) | Priority | Source | Decisions applied |
|---|---|---|---|---|
| FR-1 | Create a task from a title and an optional description; the server sets id, status Pending, createdAt and updatedAt. The title is trimmed; the description is trimmed and, when absent, null, empty or whitespace-only, is stored and returned as an empty string. | Must (inferred) | CAP-2 description | Clar. #3, #7, #8, #10 |
| FR-2 | Return all tasks ordered by createdAt descending; tasks with equal createdAt are ordered later-created first; updates never change a task's position. An empty collection is a valid result. | Must (inferred) | CAP-2 description | Clar. #11 |
| FR-3 | Return a single task by id. | Must (inferred) | CAP-2 description | Clar. #3 |
| FR-4 | Update a task as a full replacement of title and description: title is required; an omitted description clears it to empty. The status is not changed by this operation. createdAt is unchanged; updatedAt is set. | Must (inferred) | CAP-2 description | Clar. #6, #8, #9, #10 |
| FR-5 | Delete an existing task permanently (hard delete). | Must (inferred) | CAP-2 description | A-6 |
| FR-6 | Set the status of an existing task to Completed or Pending using body `{ "status": "<Pending|Completed>" }`; the response is 200 with the full task. updatedAt is set on every successful status request, including when the status is already the requested value. | Must (inferred) | CAP-2 description | Clar. #4, #5, #10 |
| FR-7 | Validate every request: body is valid JSON and a JSON object, required fields are present, types are correct (no coercion), title is 1-100 and description 0-500 Unicode code points after trimming (limits inclusive), status is exactly "Pending" or "Completed" (case-sensitive), id is a canonical-form UUID. Report all failing fields together. | Must (inferred) | CAP-2 description | Clar. #1, #3, #4, #8, #13, #16 |
| FR-8 | Ignore client-supplied id, createdAt and updatedAt, and any other unknown property, on create, update and status update (including status in create/update bodies and an id in the body that differs from the path). They are never taken from the request and never cause an error. | Must (inferred) | CAP-2 description | Clar. #9 |
| FR-9 | Return structured JSON responses in the formats of Appendix A.3 with the correct HTTP status code. Error codes in Appendix A.4 are a fixed contract; messages are English text and not a machine contract. | Must (inferred) | CAP-2 description | Clar. #15 |
| FR-10 | Persist tasks durably: created or changed tasks are available to later requests and survive a service restart. No storage technology is implied. | Must (inferred; durability level decided) | CAP-2 description (FR-10, "stored reliably") | Clar. #19 |
| FR-11 | Respond to requests that match no route with 404 NOT_FOUND, to a known path with an unsupported method with 405 METHOD_NOT_ALLOWED, and to an oversized body with 413 PAYLOAD_TOO_LARGE, all in the standard error format. Paths are exactly as in Appendix A.2 with no prefix, no version and no trailing-slash tolerance. | Must (derived) | derived from Clar. #2, #15 | Clar. #2, #15 |
| FR-12 | When more than one error applies, return the one earliest in the precedence order of section 7 (so a missing task is reported only after all request-shape and validation checks pass). | Must (derived) | derived from Clar. #14 | Clar. #14 |
| FR-13 | Accept request bodies only as application/json (charset parameter allowed) when a body is present; otherwise 415 UNSUPPORTED_MEDIA_TYPE. GET and DELETE ignore Content-Type. | Must (derived) | CAP-2 error table, Clar. #12 | Clar. #12 |

## 5. Non-functional requirements
The story states no explicit NFR section. NFR-1 to NFR-3 are derived from stated requirements; NFR-4 to NFR-9 come from human decisions.

| ID | Category | Requirement and measurable target | Source |
|---|---|---|---|
| NFR-1 | Security / information disclosure | An unexpected failure returns only a generic message; the response never contains a stack trace or internal detail. Measurable: inspection of a forced-failure response. | AC-13, error table (inferred as NFR) |
| NFR-2 | Compatibility / contract stability | Response and error formats are consistent across all operations so the frontend (CAP-1) can rely on them. | CAP-1 Dependencies, AC-12 |
| NFR-3 | Reliability / data integrity | Tasks created or changed are returned with their latest values by later requests; a rejected request changes no stored data. Concurrent updates to one task: last write wins; there is no optimistic locking or conflict error. | FR-10, AC-14, AC-2, AC-3, AC-8, AC-11; Clar. #18 |
| NFR-4 | Durability | Every task acknowledged by a successful create, update, status update or delete is reflected after a service restart. Measurable: create/change/delete tasks, restart the service, read all tasks. | Clar. #19 |
| NFR-5 | Performance / scale | Designed and verified for a single user at up to about 1,000 tasks; the full list is returned on every read (no pagination); 95th percentile response time at or under 500 ms at that scale, for every operation. | Clar. #20 |
| NFR-6 | Resilience / request size | Oversized request bodies are rejected with 413 PAYLOAD_TOO_LARGE. The numeric limit is not stated in the decisions; it is a design-stage value (A-12) that must be large enough that no valid task (title 100 plus description 500 Unicode code points plus JSON overhead) is ever rejected. | Clar. #15, #20 |
| NFR-7 | Observability | Unexpected failures are logged server-side; logs contain no secrets or personal data. No other observability target. | Clar. #20 |
| NFR-8 | Availability | No availability target. | Clar. #20 |
| NFR-9 | Compatibility / cross-origin | No cross-origin support: the frontend is served from the same origin as the API or reaches it through a proxy, and the API emits no CORS headers. | Clar. #21 |
| NFR-10 | Security / text handling | Title and description are stored and returned verbatim (JSON-encoded only; no HTML sanitising or escaping in the backend). The client is responsible for safe display. | Clar. #22 |

Authentication is explicitly out of scope for this iteration.

## 6. Acceptance criteria
AC-1..AC-14 are copied from Jira (origin JIRA), preserving ids. Given/When/Then text is verbatim.

| ID | Criterion | Origin | Covers |
|---|---|---|---|
| AC-1 | **Create a task.** Given a request with a valid title and an optional description, When a task is created, Then the response is 201 with the created task containing a server-generated unique id, the trimmed title, status Pending, and createdAt and updatedAt set. And a following request for all tasks includes the new task. | JIRA | FR-1, FR-9 |
| AC-2 | **Create validation.** Given a request with a missing, empty or whitespace-only title, a title over 100 characters, a description over 500 characters, or a field of the wrong type, When a task is created, Then the response is 400 in the error format with a per-field detail, and no task is stored. | JIRA | FR-7, FR-1 |
| AC-3 | **Malformed body.** Given a request whose body is not valid JSON or is empty, When a task is created or updated, Then the response is 400 with code INVALID_REQUEST_BODY and no data is changed. | JIRA | FR-7 |
| AC-4 | **Ignore server-owned fields.** Given a create request that includes id, status Completed or createdAt values, When the task is created, Then the id and timestamps are generated by the server, and the status is Pending. | JIRA | FR-8, FR-1 |
| AC-5 | **Get all tasks.** Given several tasks exist, When all tasks are requested, Then the response is 200 with every task, newest first. Given no tasks exist, When all tasks are requested, Then the response is 200 with an empty list. | JIRA | FR-2 |
| AC-6 | **Get a task by id.** Given a task exists, When it is requested by its id, Then the response is 200 with that task. Given no task has the id, Then the response is 404 with code TODO_NOT_FOUND. Given the id has an invalid format, Then the response is 400 with code INVALID_ID. | JIRA | FR-3, FR-7 |
| AC-7 | **Update a task.** Given a task exists, When a valid title and description are sent, Then the response is 200 with the updated task, its status is unchanged, createdAt is unchanged and updatedAt is later than before. | JIRA | FR-4 |
| AC-8 | **Update validation and not found.** Given a task exists, When invalid values are sent (blank title, too long, wrong type), Then the response is 400 and the stored task is unchanged. Given no task has the id, When an update is sent, Then the response is 404 with code TODO_NOT_FOUND. | JIRA | FR-4, FR-7 |
| AC-9 | **Delete a task.** Given a task exists, When it is deleted, Then the response is 204 with no body, and a following request for that id returns 404. Given no task has the id, When a delete is sent, Then the response is 404 with code TODO_NOT_FOUND. | JIRA | FR-5 |
| AC-10 | **Update the status.** Given a Pending task, When the status is set to Completed, Then the response is 200 with the task showing Completed and an updated updatedAt. Given a Completed task, When the status is set to Pending, Then the response is 200 with the task showing Pending. Given the task already has the requested status, Then the response is 200 and the task is returned unchanged in status. | JIRA | FR-6 |
| AC-11 | **Status validation.** Given a status value other than Pending or Completed, or a missing status, When a status update is sent, Then the response is 400 with a per-field detail and the task is unchanged. Given no task has the id, Then the response is 404 with code TODO_NOT_FOUND. | JIRA | FR-6, FR-7 |
| AC-12 | **Consistent error format.** Given any error response, Then it has the structure `{ "error": { "code", "message" } }` with a machine-readable code, a human-readable message and the matching HTTP status code. | JIRA | FR-9, NFR-2 |
| AC-13 | **Unexpected failure.** Given an unexpected internal failure, When a request is processed, Then the response is 500 with code INTERNAL_ERROR and a generic message, and the response contains no stack trace or internal detail. | JIRA | FR-9, NFR-1 |
| AC-14 | **Persistence.** Given a task was created or changed through the API, When it is read by later requests, Then the latest values are returned. | JIRA | FR-10, NFR-3 |

Derived criteria. They follow from the human decisions cited in the Origin column. The behaviour was decided by the human; the exact wording of each criterion is proposed here and is ratified at gate G1.

| ID | Criterion | Origin | Covers |
|---|---|---|---|
| AC-15 | Given an id that is not a canonical-form UUID on get, update, status update or delete, Then the response is 400 with code INVALID_ID and no data is changed. A well-formed UUID that matches no task gives 404 TODO_NOT_FOUND. | derived (Clar. #3) | FR-7, FR-3, FR-4, FR-5, FR-6 |
| AC-16 | Given a request with a body and a Content-Type other than application/json (a charset parameter is accepted), or with no Content-Type, Then the response is 415 with code UNSUPPORTED_MEDIA_TYPE and no data is changed. Given GET or DELETE, Then Content-Type is ignored. | derived (Clar. #12) | FR-13, FR-9 |
| AC-17 | Given a status update whose body is not valid JSON, is empty or is JSON that is not an object, Then the response is 400 with code INVALID_REQUEST_BODY and the task is unchanged (AC-3 names create and update only). | derived (Clar. #13) | FR-7, FR-6 |
| AC-18 | Given a create, update or status update that includes id, createdAt, updatedAt or any other unknown property (including status in a create or update body, and an id in the body that differs from the path), Then those properties are ignored, no error results for them, and the stored id and createdAt are unchanged. | derived (Clar. #9) | FR-8 |
| AC-19 | Given any successful response with content, Then the body is `{ "data": ... }` with a JSON content type: the full task object for create, get-by-id, update and status update, and an array for get-all. | derived (Clar. #5, A-2) | FR-9 |
| AC-20 | Given a validation error (VALIDATION_ERROR), Then `error.details` lists every failing field (all failures reported together), each with `field` equal to the JSON property name (title, description or status) and a reason message; the messages for equivalent rules use the CAP-1 wording ("Title is required", "Title must be 100 characters or fewer", "Description must be 500 characters or fewer"); a wrong-type value is VALIDATION_ERROR (no type coercion); `details` is absent for all other error codes. | derived (Clar. #16, A-7) | FR-7, FR-9 |
| AC-21 | Given a task is created with a title only, or with a description that is null, "" or whitespace-only, Then the returned task and every later read have `"description": ""` (the key is always present). | derived (Clar. #7, #8) | FR-1 |
| AC-22 | Given tasks were created, changed and deleted through the API, When the service is restarted, Then all later reads return the tasks as they were before the restart, and deleted tasks remain absent. | derived (Clar. #19) | FR-10, NFR-4 |
| AC-23 | Given a body that is valid JSON but not an object (array, string, number, null), Then the response is 400 INVALID_REQUEST_BODY. Given a body of `{}`, Then the response is 400 VALIDATION_ERROR (missing title on create and update, missing status on status update). | derived (Clar. #13) | FR-7 |
| AC-24 | Given a request to a path that matches no route (including a trailing-slash form such as `/todos/`), Then 404 with code NOT_FOUND. Given a defined path with an unsupported method, Then 405 with code METHOD_NOT_ALLOWED. Given a body larger than the allowed size, Then 413 with code PAYLOAD_TOO_LARGE. All use the standard error format and change no data. | derived (Clar. #2, #15) | FR-11, FR-9 |
| AC-25 | Given a request to which several errors apply, Then the response is the earliest of: NOT_FOUND / METHOD_NOT_ALLOWED (route), PAYLOAD_TOO_LARGE, UNSUPPORTED_MEDIA_TYPE, INVALID_ID, INVALID_REQUEST_BODY, VALIDATION_ERROR, TODO_NOT_FOUND. Example: PUT with an invalid body to a well-formed but missing id returns 400, not 404; an invalid id with an invalid body returns INVALID_ID. | derived (Clar. #14, A-14) | FR-12 |
| AC-26 | Given tasks with equal createdAt, Then get-all lists the later-created first. Given a task is updated or its status changed, Then its position in get-all does not change. | derived (Clar. #11) | FR-2 |
| AC-27 | Given any returned task, Then createdAt and updatedAt are ISO-8601 UTC with a "Z" suffix and millisecond precision. Given any successful PUT or PATCH, including one that changes no value (same title/description, or the status is already the requested one), Then updatedAt is strictly later than its previous value and createdAt is unchanged. | derived (Clar. #10) | FR-1, FR-4, FR-6 |
| AC-28 | Given a title of exactly 100 and a description of exactly 500 Unicode code points after trimming, Then the request is accepted; Given 101 and 501, Then 400 VALIDATION_ERROR. Length is counted in Unicode code points (not bytes or UTF-16 units) after leading/trailing Unicode whitespace is trimmed; internal whitespace is preserved; a title of 100 characters plus surrounding padding is valid. | derived (Clar. #1, #8) | FR-7, FR-1 |
| AC-29 | Given a PUT without a description, Then the stored description becomes "" and the response shows it; a PUT without a title gives 400 VALIDATION_ERROR and the task is unchanged. | derived (Clar. #6) | FR-4 |
| AC-30 | Given a status value with different case (for example "pending", "COMPLETED") or any value other than the exact strings "Pending" and "Completed", Then 400 VALIDATION_ERROR on field "status" and the task is unchanged. Responses always use the exact strings. | derived (Clar. #4, #5) | FR-6, FR-7 |
| AC-31 | Given two tasks with the same title, Then both creates succeed and both tasks exist with distinct ids. | derived (Clar. #17) | FR-1 |
| AC-32 | Given two updates to the same task, Then both succeed (no conflict error, no version or ETag required) and later reads show the values of the last update processed. | derived (Clar. #18) | NFR-3 |
| AC-33 | Given a title or description containing markup or special characters, Then it is returned exactly as submitted (after trimming only), JSON-encoded, with no HTML escaping or sanitising. | derived (Clar. #22) | NFR-10 |
| AC-34 | Given any response from the API, Then it contains no CORS headers (no Access-Control-* headers). | derived (Clar. #21) | NFR-9 |
| AC-35 | Given about 1,000 stored tasks, Then the 95th percentile response time of each operation, measured over the verification run, is at or under 500 ms. | derived (Clar. #20) | NFR-5 |
| AC-36 | Given an unexpected failure, Then it is recorded in server-side logs, and the logs contain no secrets or personal data. (Response content is covered by AC-13.) | derived (Clar. #20) | NFR-7 |

## 7. Constraints, dependencies, out of scope
- Constraints:
  - Responses are JSON in the formats of Appendix A.3. Paths are exactly those of Appendix A.2 (Clar. #2): no prefix, no version, no trailing-slash tolerance. The story calls the paths a proposal; the human has now fixed them.
  - Error precedence when several errors apply (Clar. #14, extended by A-14): route errors (NOT_FOUND, METHOD_NOT_ALLOWED), PAYLOAD_TOO_LARGE, UNSUPPORTED_MEDIA_TYPE, INVALID_ID, INVALID_REQUEST_BODY, VALIDATION_ERROR, TODO_NOT_FOUND.
  - No authentication in this iteration. No CORS support (Clar. #21). No technology or stack decision is made in this document.
- Dependencies:
  - CAP-1 (Frontend, To Do, no Jira link) consumes these APIs and relies on the response and error formats. Interface contract captured from CAP-1:
    - Task fields shown/edited: title, description, status (Pending or Completed; new tasks Pending). Newest first.
    - CAP-1 expects 400 validation errors to carry a message that it shows next to the relevant field "when one is given" (so `details[].field` maps to the form fields title/description; satisfied by AC-20).
    - CAP-1 expects 404 on edit, delete and status change to mean the task no longer exists, and then refreshes the list.
    - CAP-1 treats 5xx/unreachable as a generic failure and does not show technical details.
    - CAP-1 uses the same field rules (title 1-100 after trimming, description up to 500); these are now confirmed by the human (Clar. #1). Separate product-owner sign-off is not recorded.
    - CAP-1 may be built against a stand-in that follows this contract; the contract is now fully specified by sections 4 to 6 and Appendix A.
    - The frontend must be served from the same origin as the API or through a proxy, since no CORS is provided (Clar. #21). This is a deployment constraint on CAP-1, outside this story.
- Explicitly out of scope (CAP-2): user accounts and authentication, filtering, search, pagination, user interface, deployment and infrastructure, CORS, soft delete, optimistic locking, localisation.

## 8. Questions and decisions
All 22 questions are answered. No question is open and no new question arose from the answers (the interpretations needed to apply them are recorded as assumptions A-11 to A-16 for review at G1).

Answered by: "human (bulk)" = chose "Accept all defaults" (S1 Q1), so the default proposed in attempt 1 became the human's decision; "human (individual)" = asked and answered separately. Source for all rows: `clarifications.md`, Clar. # equals the Q number.

### Group A: Business assumptions flagged by the story
| ID | Question | Status | Decision | Answered by | Applied in |
|---|---|---|---|---|---|
| Q-1 | Limits title 1-100, description 0-500; unit; inclusive? | answered | Confirmed as stated, inclusive, counted in Unicode code points after trimming (Clar. #1) | human (bulk) | FR-7, AC-28, A-10 |
| Q-2 | Paths, prefix/version, trailing slash | answered | Paths as proposed; no prefix; no version; no trailing-slash tolerance (Clar. #2) | human (bulk) | FR-11, AC-24, A.2 |

### Group B: Data contract
| ID | Question | Status | Decision | Answered by | Applied in |
|---|---|---|---|---|---|
| Q-3 | Id format | answered | UUID in canonical text form, opaque to clients; anything else is INVALID_ID (Clar. #3) | human (bulk) | FR-7, AC-15, A.1 |
| Q-4 | Status representation | answered | Exact strings "Pending" and "Completed", case-sensitive on input and output (Clar. #4) | human (bulk) | FR-6, AC-30 |
| Q-5 | PATCH status body and response | answered | Body `{ "status": "<Pending\|Completed>" }`; 200 with the full task in `{ "data": ... }`; missing/invalid status is VALIDATION_ERROR on field "status" (Clar. #5) | human (bulk) | FR-6, AC-19, AC-30 |
| Q-6 | PUT full or partial | answered | Full replacement of title and description; title required; omitted description clears it; status never changes (Clar. #6) | human (bulk) | FR-4, AC-29 |
| Q-7 | Absent description representation | answered | Key always present; "" when none; omitted, null and "" on input all mean none (Clar. #7) | human (bulk) | FR-1, AC-21 |
| Q-8 | Whitespace handling | answered | Trim title and description; limit checked after trimming; internal whitespace preserved; whitespace-only description becomes empty (Clar. #8) | human (bulk) | FR-1, AC-21, AC-28 |
| Q-9 | Unknown and server-owned properties | answered | Ignore all, on create, update and status update (including status in POST/PUT and a differing id in the body); never reject for them (Clar. #9) | human (bulk) | FR-8, AC-18 |
| Q-10 | Timestamps and no-op updates | answered | ISO-8601 UTC with "Z", millisecond precision; every successful PUT/PATCH sets updatedAt strictly later, even when nothing changed; createdAt never changes (Clar. #10) | human (bulk) | FR-6, AC-27 |
| Q-11 | Ordering ties | answered | createdAt descending; ties: later-created first; updates never reorder (Clar. #11) | human (bulk) | FR-2, AC-26 |

### Group C: Errors and request handling
| ID | Question | Status | Decision | Answered by | Applied in |
|---|---|---|---|---|---|
| Q-12 | Content-type handling | answered | 415 only when a body is present and type is not application/json (charset allowed; missing type counts as unsupported); GET/DELETE ignore it; empty body with JSON type is 400 INVALID_REQUEST_BODY (Clar. #12) | human (bulk) | FR-13, AC-16, A-11 |
| Q-13 | Non-object JSON, `{}`, AC-3 on PATCH | answered | Non-object JSON is INVALID_REQUEST_BODY; `{}` is VALIDATION_ERROR; AC-3 applies to PATCH status (Clar. #13) | human (bulk) | AC-17, AC-23 |
| Q-14 | Error precedence | answered | 415, INVALID_ID, INVALID_REQUEST_BODY, VALIDATION_ERROR, then TODO_NOT_FOUND (Clar. #14) | human (bulk) | FR-12, AC-25, A-14 |
| Q-15 | Error codes fixed? Unknown routes, 405, 413 | answered | Codes fixed (the table's "example" label is superseded); add NOT_FOUND (404), METHOD_NOT_ALLOWED (405), PAYLOAD_TOO_LARGE (413) in the standard format; messages are English and not a machine contract (Clar. #15) | human (bulk) | FR-9, FR-11, AC-24, A.4 |
| Q-16 | Validation details format | answered | Report all failing fields; `field` is the JSON property name; wording aligned with CAP-1; wrong type is VALIDATION_ERROR, no coercion (Clar. #16) | human (bulk) | FR-7, AC-20, A-3 |

### Group D: Behaviour and non-functional
| ID | Question | Status | Decision | Answered by | Applied in |
|---|---|---|---|---|---|
| Q-17 | Duplicate titles | answered | Allowed; no uniqueness constraint (Clar. #17) | human (bulk) | AC-31 |
| Q-18 | Concurrent updates | answered | Last write wins; no optimistic locking (Clar. #18) | human (bulk) | NFR-3, AC-32 |
| Q-19 | Durability | answered | **Durable: tasks survive a service restart** (Clar. #19) | human (individual, S1 Q3) | FR-10, NFR-4, AC-22 |
| Q-20 | NFR targets | answered | About 1,000 tasks; p95 at or under 500 ms; reject oversized bodies; server-side failure logs without secrets; no availability target (Clar. #20) | human (bulk) | NFR-5 to NFR-8, AC-35, AC-36, A-12 |
| Q-21 | CORS | answered | **No CORS needed** (same origin or proxy; no CORS headers) (Clar. #21) | human (individual, S1 Q2) | NFR-9, AC-34 |
| Q-22 | Stored text handling | answered | Store and return text verbatim; client is responsible for safe display (Clar. #22) | human (bulk) | NFR-10, AC-33 |

Open questions: none.

## 9. Assumptions
"Approved by" shows who decided. Items decided by the human are listed with the clarification number. Items marked "pending G1" are interpretations I made to apply the decisions or fill remaining gaps; they are not new questions but the human may veto them at G1.

| ID | Assumption | Basis | Approved by |
|---|---|---|---|
| A-1 | Every task belongs to a single shared collection; there is no per-user data. | Authentication and user accounts are out of scope. | pending G1 |
| A-2 | Successful PUT, PATCH and POST return the full task object in `{ "data": ... }`. | Response structure, AC-1, AC-7, AC-10; PATCH confirmed by Clar. #5. | human (Clar. #5, PATCH); pending G1 (PUT, POST) |
| A-3 | No type coercion: a number, boolean or object for title or description is a wrong-type validation error. | AC-2 "a field of the wrong type". | human (Clar. #16) |
| A-4 | Response and error messages are in English and are not a localisation concern. | CAP-1 messages are English. | human (Clar. #15: messages are English and not a machine contract) |
| A-5 | A repeated DELETE of the same id returns 404 TODO_NOT_FOUND. | AC-9 second case. | pending G1 (follows from AC-9) |
| A-6 | Delete is a hard delete; no soft delete, recycle bin or retention. | FR-5 "permanently". | pending G1 (follows from FR-5) |
| A-7 | `details` is present only on VALIDATION_ERROR; every other error code has no details. | Response structure note "details only for validation errors". | human (Clar. #16 and #15, as AC-20) |
| A-8 | GET and DELETE ignore Content-Type and any request body. | Not stated in the story. | human (Clar. #12) for Content-Type; pending G1 for ignoring a body |
| A-9 | Priorities for FR-1..FR-10 are Must. | Not stated in the story. | pending G1 |
| A-10 | Field limits (title 1-100, description up to 500) and the paths are used as written. | Story "Dependencies and assumptions". | human (Clar. #1, #2) |
| A-11 | A request with no body at all (no bytes) is not checked for Content-Type; it is treated as an empty body and returns 400 INVALID_REQUEST_BODY (AC-3), not 415. Rule 415 applies only when a body is present. | Clar. #12 ("415 when a body is present") combined with AC-3 ("empty body") and Clar. #12 ("empty body with JSON type is 400"). | pending G1 |
| A-12 | The numeric maximum request body size is not specified by the human. It is deferred to the architecture stage, with the lower bound that a maximal valid task is never rejected (NFR-6); a test for 413 uses a body clearly above that value. | Clar. #20 "reject oversized bodies" gives no number. | pending G1 |
| A-13 | A `null` title (or any title that is missing, empty or whitespace-only) is VALIDATION_ERROR; null is accepted only for description. | Clar. #7 (null means none for description), AC-2 (missing title). | pending G1 |
| A-14 | Route errors (NOT_FOUND, METHOD_NOT_ALLOWED) are decided first, and PAYLOAD_TOO_LARGE before UNSUPPORTED_MEDIA_TYPE; the human's order (415, INVALID_ID, INVALID_REQUEST_BODY, VALIDATION_ERROR, TODO_NOT_FOUND) is kept unchanged after them. | Clar. #14 does not place the routing and size errors added by Clar. #15. | pending G1 |
| A-15 | "No trailing-slash tolerance" means a path such as `/todos/` is an unmatched route (404 NOT_FOUND), not redirected or treated as `/todos`. | Clar. #2, #15. | pending G1 |
| A-16 | "No CORS needed" means no CORS headers are sent and preflight (OPTIONS) requests are not specially handled; an OPTIONS request is an unsupported method (405 METHOD_NOT_ALLOWED). | Clar. #21, #15. | pending G1 |

## 10. Conflicts and concerns
| # | Description | Sources in conflict | Resolution |
|---|---|---|---|
| C-1 | No conflicts found between description and comments (there are no comments) or Confluence (none). No instructions aimed at an AI were found in retrieved content. | n/a | none required |
| C-2 | AC-12 defines the error shape as `{ "error": { "code", "message" } }` while the Response structure also defines an optional `details` array. | CAP-2 AC-12 vs CAP-2 Response structure | Resolved: `details` is an additional member present only on VALIDATION_ERROR (Clar. #16, AC-20, A-7) |
| C-3 | The error table labels its code column "Error code (example)", while AC-3, AC-6, AC-9, AC-11 and AC-13 name codes as hard requirements. | CAP-2 error table vs CAP-2 ACs | Resolved: codes are fixed (Clar. #15); the "example" label is superseded |
| C-4 | FR-10 ("across requests") is weaker than the user story's "stored reliably" (could mean across restarts). | CAP-2 FR-10 vs user story | Resolved: durable across restarts (Clar. #19) |
| C-5 | Validation rules in AC-2 (create) and AC-8 (update) overlap, but only create has AC-4 (server-owned fields) and only get has the INVALID_ID case; the error table implies these apply to all operations. | CAP-2 error table / FR-7 / FR-8 vs ACs | Resolved by derived AC-15, AC-17, AC-18 (Clar. #3, #9, #13) |
| C-6 | Whether title length is limited before or after trimming is stated only in CAP-1 ("1-100 characters after trimming"), not in CAP-2. | CAP-1 vs CAP-2 data model | Resolved: after trimming (Clar. #1, #8) |
| C-7 | The story lists the paths as "a proposal" and says behaviour must hold regardless of path; the human has fixed the paths, so the stated flexibility no longer applies. | CAP-2 A.2 vs Clar. #2 | Resolved: paths fixed as in Appendix A.2 |
| C-8 | The story's error table has no row for unmatched routes, unsupported methods or oversized bodies, yet AC-12 says "any error response" uses the standard format. | CAP-2 error table vs AC-12 | Resolved: NOT_FOUND, METHOD_NOT_ALLOWED, PAYLOAD_TOO_LARGE added (Clar. #15) |
| C-9 | The human did not give a numeric request-body size limit although oversized bodies must be rejected. | Clar. #20 | Not a question: deferred to architecture under A-12; flagged for G1 awareness |

## 11. Traceability
| Requirement | Acceptance criteria | Source |
|---|---|---|
| FR-1 | AC-1, AC-2, AC-4, AC-21, AC-27, AC-28, AC-31 | CAP-2 description; Clar. #1, #7, #8, #10, #17 |
| FR-2 | AC-5, AC-26 | CAP-2 description; Clar. #11 |
| FR-3 | AC-6, AC-15 | CAP-2 description; Clar. #3 |
| FR-4 | AC-7, AC-8, AC-15, AC-18, AC-27, AC-29 | CAP-2 description; Clar. #6, #9, #10 |
| FR-5 | AC-9, AC-15 | CAP-2 description |
| FR-6 | AC-10, AC-11, AC-15, AC-17, AC-18, AC-27, AC-30 | CAP-2 description; Clar. #4, #5, #10 |
| FR-7 | AC-2, AC-3, AC-6, AC-8, AC-11, AC-15, AC-17, AC-20, AC-23, AC-28, AC-30 | CAP-2 description, error table; Clar. #1, #3, #4, #13, #16 |
| FR-8 | AC-4, AC-18 | CAP-2 description; Clar. #9 |
| FR-9 | AC-1, AC-12, AC-13, AC-16, AC-19, AC-20, AC-24 | CAP-2 description, response structure; Clar. #15 |
| FR-10 | AC-14, AC-22 | CAP-2 description; Clar. #19 |
| FR-11 | AC-24 | Clar. #2, #15 |
| FR-12 | AC-25 | Clar. #14 |
| FR-13 | AC-16 | CAP-2 error table; Clar. #12 |
| NFR-1 | AC-13 | CAP-2 AC-13 |
| NFR-2 | AC-12, AC-19 | CAP-2 AC-12, CAP-1 dependencies |
| NFR-3 | AC-14, AC-2, AC-3, AC-8, AC-11, AC-32 | CAP-2 FR-10, AC-14; Clar. #18 |
| NFR-4 | AC-22 | Clar. #19 |
| NFR-5 | AC-35 | Clar. #20 |
| NFR-6 | AC-24 | Clar. #15, #20 |
| NFR-7 | AC-36 | Clar. #20 |
| NFR-8 | none (no target; nothing to verify) | Clar. #20 |
| NFR-9 | AC-34 | Clar. #21 |
| NFR-10 | AC-33 | Clar. #22 |

## Appendix A. Story-specified contract (captured from CAP-2, updated with decisions)

### A.1 Task data model
| Field | Rules |
|---|---|
| id | Generated by the server, unique, never changes. Not settable by clients. Canonical-form UUID text, opaque to clients (Clar. #3). |
| title | Required. Text, trimmed. 1-100 Unicode code points after trimming, inclusive. |
| description | Optional. Text, trimmed, 0-500 Unicode code points, inclusive. Always present in responses; "" when none (Clar. #7, #8). |
| status | Exactly "Pending" or "Completed" (case-sensitive). Defaults to Pending on creation. |
| createdAt | Set by the server on creation, never changes. ISO-8601 UTC with "Z", millisecond precision (Clar. #10). |
| updatedAt | Set by the server on every successful change (including no-op PUT/PATCH), strictly later than before. ISO-8601 UTC with "Z", millisecond precision. |

### A.2 API contract (paths fixed by Clar. #2; no prefix, no version, no trailing slash)
| Operation | Request | Success |
|---|---|---|
| Create a task | POST /todos | 201 Created |
| Get all tasks | GET /todos | 200 OK |
| Get a task by id | GET /todos/{id} | 200 OK |
| Update a task | PUT /todos/{id} | 200 OK |
| Delete a task | DELETE /todos/{id} | 204 No Content |
| Update task status | PATCH /todos/{id}/status | 200 OK |

### A.3 Response structure
- Success with content: `{ "data": <task object or array of tasks> }` (full task object for create, get, update and status update).
- Error: `{ "error": { "code": "<MACHINE_READABLE_CODE>", "message": "<human readable message>", "details": [ { "field": "<field name>", "message": "<reason>" } ] } }` (`details` only for VALIDATION_ERROR; lists every failing field).
- Messages are English and not a machine contract; codes are fixed.

### A.4 Validation and error handling
| Situation | Status | Error code |
|---|---|---|
| Missing or blank title; title over 100 characters; description over 500 characters; wrong data type; status other than the exact strings Pending / Completed or missing; `{}` body | 400 | VALIDATION_ERROR (with details per field) |
| Malformed, empty, or non-object JSON body | 400 | INVALID_REQUEST_BODY |
| Id not a canonical-form UUID | 400 | INVALID_ID |
| Task with the given id does not exist | 404 | TODO_NOT_FOUND |
| Path matches no route (including trailing slash) | 404 | NOT_FOUND |
| Defined path, unsupported method | 405 | METHOD_NOT_ALLOWED |
| Body larger than the allowed size | 413 | PAYLOAD_TOO_LARGE |
| Body present with a content type other than application/json (or none) | 415 | UNSUPPORTED_MEDIA_TYPE |
| Unexpected server failure | 500 | INTERNAL_ERROR (generic message; no stack trace, no internal detail) |

Precedence when several apply: NOT_FOUND / METHOD_NOT_ALLOWED, PAYLOAD_TOO_LARGE, UNSUPPORTED_MEDIA_TYPE, INVALID_ID, INVALID_REQUEST_BODY, VALIDATION_ERROR, TODO_NOT_FOUND (Clar. #14, A-14).
