# Implementation Plan — Todo Query Enhancements + Partial Updates (Local-only)

**Repository:** `claude_capstone`  
**Base branch:** `main` (must not be modified directly)  
**Feature branch (for docs only, per approval):** `feature/todo-query-and-patch`  
**Direction:** Local-only (`127.0.0.1` / `localhost`). No authentication/authorization. No new frameworks. Preserve custom gateway/pipeline.

---

## Project Overview
Enhance the existing Todo application to support scalable browsing (search/filter/sort/pagination) and partial updates, with strict validation, deterministic ordering, expanded automated tests, and clear documentation—without changing architecture or introducing new tooling.

---

## Scope

### In Scope
- **Backend API**
  - Enhance `GET /todos` with **search (`q`)**, **filter (`status`)**, **sort (`sortBy`, `sortOrder`)**, **pagination (`limit`, `offset`)**.
  - Keep response as a **plain JSON array**; preserve existing default behavior when no query params.
  - Add `PATCH /todos/{id}` to partially update **title** and/or **description** only.
  - Enforce strict validation (unknown query params rejected, invalid combinations rejected).
  - Deterministic ordering when timestamps match.
- **Frontend**
  - Add controls for search/filter/sort/pagination.
  - Improve list loading/error/retry/empty states.
  - Preserve no-npm / no build-step approach.
- **Testing & Docs**
  - Add/expand API contract tests, negative and boundary tests.
  - Document and test gateway/pipeline parsing/routing/error handling relevant to changes.

### Out of Scope
- Authentication/authorization
- LAN/remote exposure, HTTPS, rate limiting
- Health/ready endpoints, observability
- Archived status/soft deletion, bulk operations
- Due dates, priorities, tags
- Major refactoring of architecture
- DB schema changes beyond optional indexes (indexes only if testing proves need)

---

## Technology Stack
- **Backend:** Java (21+), Spring Boot, JDBC, SQLite (xerial sqlite-jdbc)
- **Frontend:** Vanilla HTML/CSS/JavaScript (ES modules)
- **Frontend server:** Java static server + reverse proxy

---

## Approved Jira Stories (planned; keys TBD)
**Epic:** Todo Query Enhancements & Partial Update Support

1. API: Search todos via `q`
2. API: Filter todos via `status`
3. API: Sort todos via `sortBy/sortOrder` with deterministic ordering
4. API: Paginate todos via `limit/offset` (MAX_LIMIT=100; offset requires limit)
5. API: Reject unknown query parameters on `GET /todos`
6. API: Add `PATCH /todos/{id}` partial update (title/description only, atomic)
7. Frontend: Add search/filter/sort/pagination controls + robust states
8. Engineering: Add gateway/pipeline docs + targeted tests; expand API contract/negative tests

---

## Requirements Summary (implementation targets)

### `GET /todos` query params (supported set only)
- `q` (string): trimmed; if empty after trim → treated as not provided.
- `status`: `Pending` | `Completed`
- `sortBy`: `createdAt` | `updatedAt`
- `sortOrder`: `asc` | `desc`
- `limit`: int in `[1..100]`
- `offset`: int `>= 0`

### Validation rules
- Reject **unknown query parameters** with `400`.
- Reject `offset` without `limit` with `400`.
- If `limit` provided and `offset` omitted → default `offset=0`.
- Reject `sortOrder` without `sortBy` with `400`.
- If `sortBy` provided and `sortOrder` omitted → default `sortOrder=desc`.
- Preserve current default ordering when **no sorting params** supplied.
- Sorting must be deterministic: when timestamps tie, apply a consistent **secondary key**.

### Search behavior
- Case-insensitive contains across title OR description.
- Unicode supported as SQLite permits.

### `PATCH /todos/{id}`
- Supports only `title` and `description`.
- Reject `{}` with `400`.
- Reject unsupported fields, nulls, invalid types with `400`.
- Omitted fields remain unchanged.
- Updates are **atomic**: any validation failure → no persistence.

---

## Implementation Sequence (Phases 0–5)

### Phase 0 — Discovery & baseline protection
**Goal:** verify current behavior for compatibility.
- Characterize current `GET /todos` ordering and response shape.
- Identify where query params are parsed/validated in gateway/pipeline.
- Identify stable secondary key for deterministic ordering (e.g., `seq` or `id`).

**Deliverables**
- Characterization tests (backend) verifying default `GET /todos` unchanged.

---

### Phase 1 — Backend request model + validation plumbing
**Goal:** add query parsing + validation through existing pipeline/validator.
- Introduce **TaskQuery** model to represent parsed query params.
- Extend `TodoRequestPipeline` (or adjacent request parsing layer) to:
  - Parse and validate supported query params.
  - Enforce unknown parameter rejection.
- Extend `TaskRequestValidator` with:
  - query validation rules above
  - PATCH body validation (see Phase 3)

**Notes**
- Keep custom gateway/pipeline architecture; do not migrate to Spring MVC binding/DTOs.

---

### Phase 2 — Persistence enhancements (search/filter/sort/pagination)
**Goal:** implement efficient repository query without breaking existing behavior.
- Keep existing `findAllOrdered()` untouched (backward compatible).
- Add new repository method, e.g. `findByQuery(TaskQuery query)`.
- Implement SQL to support:
  - optional search (`q`) across title/description
  - optional status filter
  - optional sortBy/sortOrder (and deterministic tie-break)
  - pagination (limit/offset)

**DB Changes**
- No schema changes required.
- **Do not add indexes** unless tests demonstrate a performance need.

---

### Phase 3 — Service + Controller wiring (enhanced GET + new PATCH)
**Goal:** expose features via API with consistent envelope and conventions.
- `GET /todos`
  - If no query params: call existing list method and preserve behavior.
  - If any supported query param present: validate and use query-based list.
- Add `PATCH /todos/{id}`
  - Parse id via pipeline.
  - Validate PATCH body (title/description only).
  - Apply atomic update and return updated todo.

**Compatibility**
- Preserve existing error envelope and HTTP conventions.

---

### Phase 4 — Frontend UI changes (no npm)
**Goal:** add controls and improve states.
- Add search/filter/sort/pagination controls.
- Ensure UI never emits invalid combinations (e.g., offset without limit).
- Improve states: loading, error (retry), empty, no-results.
- Preserve existing CRUD/status-toggle flows.

---

### Phase 5 — Docs + test hardening
**Goal:** lock behavior with documentation and comprehensive tests.
- Update docs:
  - new query params and examples
  - PATCH schema and examples
  - gateway/pipeline behavior (routing, parsing, error mapping)
- Add/expand tests:
  - contract tests for combined query behavior
  - negative and boundary tests
  - gateway/pipeline behavior tests

---

## Dependencies
- Frontend controls depend on final backend query validation rules.
- Deterministic ordering depends on confirmed secondary key (`seq` vs `id`).

---

## File-Level Change Plan (expected)

### Backend — expected modifications
- `backend/src/main/java/com/capstone/todo/web/TodoController.java`
- `backend/src/main/java/com/capstone/todo/web/request/TodoRequestPipeline.java`
- `backend/src/main/java/com/capstone/todo/validation/TaskRequestValidator.java`
- `backend/src/main/java/com/capstone/todo/domain/TaskService.java`
- `backend/src/main/java/com/capstone/todo/domain/TaskRepository.java`
- `backend/src/main/java/com/capstone/todo/persistence/SqliteTaskRepository.java`

### Backend — expected new files
- `backend/src/main/java/com/capstone/todo/domain/TaskQuery.java`
- `backend/src/main/java/com/capstone/todo/domain/PatchTask.java` (or equivalent command object)

### Frontend — expected modifications
- `frontend/public/index.html`
- `frontend/public/*.js`
- `frontend/public/*.css`
- `frontend/public/tests/*` (manual harness additions, as appropriate)

---

## Database Changes
- None required.
- Optional: add indexes only if benchmarks/tests show clear need.

---

## API Changes
- **Enhanced:** `GET /todos` supports validated query params; rejects unknown params.
- **New:** `PATCH /todos/{id}` supports title/description only (atomic).
- No response envelope changes; `GET /todos` remains plain JSON array.

---

## Frontend Changes
- Add controls for query params.
- Improve list view states.
- Keep existing no-build approach.

---

## Testing Strategy

### Unit tests
- Validator tests for query parameter rules and PATCH body rules.
- Repository tests for SQL correctness: search/filter/sort/pagination + deterministic ordering.

### API/Integration tests
- Characterization/regression: default `GET /todos` unchanged.
- `GET /todos` param combinations and negative cases (unknown params, invalid ranges/combos).
- `PATCH /todos/{id}`: positive/negative/atomicity tests.

### Frontend tests (within constraints)
- Extend browser-based test harness/checklist for new controls and states.
- Java proxy tests to ensure query strings are forwarded intact.

---

## Security Considerations
- Local-only. No auth.
- Emphasis on input validation, strict allowlist, and consistent error envelopes.

---

## Risks & Mitigations
- **Default ordering drift:** add characterization test before changing list query path.
- **SQLite case-insensitive Unicode limitations:** implement best-effort `LOWER(...) LIKE LOWER(?)` and document.
- **Deterministic ordering key ambiguity:** confirm schema and use stable secondary key.
- **UI emitting invalid query combos:** enforce UI rules and add simple unit-ish checks in frontend code where feasible.

---

## Milestones
1. M1: Characterization tests + query model drafted
2. M2: Backend GET query support complete + tests
3. M3: PATCH endpoint complete + tests
4. M4: Frontend controls + states complete
5. M5: Docs + regression suite green

---

## Definition of Done
- Default `GET /todos` unchanged (verified by automated tests).
- New query capabilities and PATCH endpoint implemented per validation rules.
- Automated tests cover positive, negative, and boundary scenarios.
- Documentation updated for API and gateway/pipeline behavior.
- No npm/build step introduced.
- No changes to `main`; work done on feature branch only.
