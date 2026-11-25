# Implementation Plan — Todo Query Enhancements + Partial Updates (Local-only)

Base branch: `main` (protected; do not modify)  
Work branch: `feature/todo-query-and-patch` (docs-only commit in this step)  
Repository: `claude_capstone`

## Project Overview
Enhance the existing local-only Todo application to support:
- `GET /todos` **search** (`q`), **filter** (`status`), **sort** (`sortBy`/`sortOrder`), and **pagination** (`limit`/`offset`)
- New `PATCH /todos/{id}` for **partial updates** (title/description only)
- Frontend controls and improved states (vanilla JS; no npm/build)
- Expanded backend tests (contract/negative/boundary) and gateway/pipeline documentation & tests

All changes must preserve:
- Existing architecture (Spring Boot + SQLite + Java FrontendServer proxy + vanilla HTML/CSS/JS)
- Existing custom gateway/pipeline approach
- Existing error envelope and HTTP conventions
- Backward compatibility: **`GET /todos` with no query params returns the full list using the current default ordering**

---

## Scope
### In Scope
1. **Todo search and filtering**
   - Search (`q`) case-insensitive contains across title + description
   - Status filter (`status`) Pending/Completed
2. **Todo sorting and pagination**
   - Sort by `createdAt` or `updatedAt`
   - `sortOrder` asc/desc
   - Pagination with `limit` (1..100) and `offset` (>=0)
3. **Partial updates**
   - `PATCH /todos/{id}` updates only `title` and/or `description`
   - Reject `{}` empty payload; reject unknown fields; reject nulls; reject invalid types
   - Atomic persistence
4. **Frontend enhancements**
   - Search/filter/sort/pagination controls
   - Clear loading/error/retry/empty states
5. **Testing & docs**
   - Contract/negative/boundary tests for new behaviors
   - Gateway/pipeline documentation and targeted tests

### Out of Scope
- AuthN/AuthZ
- Remote/LAN exposure, HTTPS, rate limiting
- Archived status, bulk operations, tags/due dates/priorities
- Response envelope changes for `GET /todos`
- Major refactors or new frameworks
- DB indexing unless tests demonstrate a performance need

---

## Technology Stack
- Backend: Java 21+, Spring Boot, JDBC, SQLite (file DB)
- Frontend: vanilla HTML/CSS/JavaScript (ES modules)
- Frontend server: Java static server + reverse proxy

---

## Approved Jira Epic & Stories (keys TBD)
**Epic:** Todo Query Enhancements & Partial Update Support

1. API: Search todos via `q`
2. API: Filter todos via `status`
3. API: Sort todos via `sortBy/sortOrder` with deterministic ordering
4. API: Paginate todos via `limit/offset` (MAX_LIMIT=100; offset requires limit)
5. API: Reject unknown query parameters on `GET /todos`
6. API: Add `PATCH /todos/{id}` partial update (title/description only, atomic)
7. Frontend: Add search/filter/sort/pagination controls + robust states
8. Engineering: Gateway/pipeline docs + targeted tests; expand API contract/negative tests

---

## Implementation Sequence (Roadmap)

### Phase 0 — Discovery & characterization tests
**Goal:** lock in current behavior before enhancements.
- Add characterization tests for:
  - `GET /todos` default ordering when no query params
  - existing error envelope patterns (baseline)
- Identify schema fields to use as deterministic secondary key (e.g., `seq` or `id`).

**Deliverables:** characterization tests; confirmed ordering and tie-break strategy.

### Phase 1 — Request model and validation wiring (gateway/pipeline)
**Goal:** implement strict parsing & validation without changing persistence yet.
- Introduce domain model for list query:
  - `TaskQuery` with optional `q`, `status`, `sortBy`, `sortOrder`, `limit`, `offset`
- Implement strict query validation:
  - allowlist query params only
  - `offset` requires `limit`
  - `limit` 1..100
  - `offset` >= 0
  - `sortOrder` requires `sortBy`
  - `sortBy` without `sortOrder` defaults `desc`
  - `q` trimming; whitespace-only treated as missing
- Implement PATCH body validation:
  - only `title`/`description`
  - reject `{}`
  - reject unknown fields/nulls/invalid types
  - reuse existing title/description validation rules

**Deliverables:** request parsing/validation implemented in the existing pipeline/validator components.

### Phase 2 — Persistence enhancements for list query
**Goal:** add repository support for queried list operations.
- Keep existing `findAllOrdered()` unchanged.
- Add new repository method (example): `findByQuery(TaskQuery query)`.
- Implement SQL generation that supports:
  - optional status filter
  - optional case-insensitive contains search across title/description
  - sort on createdAt/updatedAt with `asc|desc` and deterministic secondary sort key
  - optional `limit`/`offset`

**Deliverables:** repository method + tests for query correctness.

### Phase 3 — Service/controller wiring + new PATCH endpoint
**Goal:** expose new capabilities through existing controller and response helpers.
- `GET /todos` behavior:
  - no query params → existing behavior (service.list() using findAllOrdered)
  - any query params → parse/validate to `TaskQuery`, then call service.list(query)
- Add `PATCH /todos/{id}`:
  - parse id
  - parse/validate body
  - call service.patch(...) with atomic update

**Deliverables:** API endpoints complete with consistent responses/errors.

### Phase 4 — Frontend enhancements (no build step)
**Goal:** add UI controls and state handling.
- Add search/filter/sort/pagination controls
- Ensure UI never emits invalid query combinations:
  - never send `offset` without `limit`
  - never send `sortOrder` without `sortBy`
- State behavior:
  - loading, empty, no-results, error with retry
  - reset `offset=0` when query/filter/sort/limit changes

**Deliverables:** updated UI, compatible with existing flows.

### Phase 5 — Documentation + test hardening
**Goal:** finalize docs and strengthen regression coverage.
- Document:
  - new query params + validation rules
  - PATCH endpoint schema + examples
  - gateway/pipeline behavior and error envelope conventions
- Add/extend tests:
  - contract/negative/boundary for GET query params
  - PATCH atomicity and validation
  - gateway/pipeline behavior tests for unknown query params

**Deliverables:** docs + full automated test suite passing.

---

## Dependencies
- Phase 0 is prerequisite for ensuring backward compatibility (ordering).
- Frontend work depends on finalized backend validation rules to avoid emitting invalid requests.
- Deterministic ordering depends on confirming the stable secondary key in schema.

---

## File-Level Change Plan (Expected)

### Backend (likely to modify)
- `backend/src/main/java/com/capstone/todo/web/TodoController.java`
- `backend/src/main/java/com/capstone/todo/web/request/TodoRequestPipeline.java`
- `backend/src/main/java/com/capstone/todo/validation/TaskRequestValidator.java`
- `backend/src/main/java/com/capstone/todo/domain/TaskService.java`
- `backend/src/main/java/com/capstone/todo/domain/TaskRepository.java`
- `backend/src/main/java/com/capstone/todo/persistence/SqliteTaskRepository.java`
- `backend/README.md` (or docs file where API is documented)

### Backend (likely new)
- `backend/src/main/java/com/capstone/todo/domain/TaskQuery.java`
- `backend/src/main/java/com/capstone/todo/domain/PatchTask.java` (or similar)

### Frontend (likely to modify)
- `frontend/public/index.html`
- `frontend/public/*.js` (list fetch/render/state)
- `frontend/public/*.css`
- `frontend/public/tests/*` (optional: update harness)

---

## Database Changes
- No schema changes required.
- Do not add indexes unless performance tests demonstrate need.

---

## Testing Strategy

### Unit tests
- Validator tests for query param rules and PATCH payload rules.
- Domain model tests for `TaskQuery` normalization (q trimming).

### Repository tests
- Verify search/filter/sort/pagination correctness.
- Verify deterministic ordering tie-breaker.

### API/Integration tests
- Characterize default `GET /todos` behavior (no query params) to prevent regressions.
- GET query matrix tests:
  - search-only, filter-only, sort-only, pagination-only, combined
  - invalid values: limit out of range, offset negative, offset without limit, sortOrder without sortBy, unknown params
- PATCH tests:
  - title-only, description-only, both
  - reject `{}` and unsupported fields
  - atomicity (mixed valid/invalid => no persistence)

### Frontend testing
- Extend existing browser-based test harness checklist to cover:
  - search/filter/sort/pagination UI interactions
  - error/retry states

---

## Risks & Mitigations
- **Default ordering regression risk:** mitigate with characterization tests in Phase 0.
- **SQLite case-insensitive Unicode behavior variance:** implement `LOWER(...) LIKE LOWER(?)` and document limitations; add tests for ASCII and a minimal Unicode case.
- **Validation strictness breaking unknown clients:** this is explicitly required; mitigate with clear docs and tests.
- **Frontend state complexity:** keep rules simple (reset offset on any query-affecting change; disable next when results < limit).

---

## Milestones
- M1: Phase 0 complete (behavior locked by tests)
- M2: Query validation + TaskQuery model (Phase 1)
- M3: Repository query support (Phase 2)
- M4: Controller/service wiring + PATCH endpoint (Phase 3)
- M5: Frontend controls + states (Phase 4)
- M6: Docs + full regression suite (Phase 5)

---

## Definition of Done
- Backward compatible default `GET /todos` verified by automated tests.
- New query params validated strictly per requirements.
- `PATCH /todos/{id}` implemented with atomic updates and strict validation.
- Frontend supports controls and clear states with no build tooling.
- Automated tests cover positive/negative/boundary scenarios.
- Documentation updated.

---

## Human Approval Gate
This plan is approved; next step is docs-only commit on `feature/todo-query-and-patch`.
