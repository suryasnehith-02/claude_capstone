# Changelog

All notable changes to this repository are recorded here. Entries reference the JIRA issue key.
Released sections are never edited; new work goes under `Unreleased`.

## Unreleased

### Added
- CAP-2: Todo backend with task management REST APIs in `backend/` (Java 21 release target, Spring Boot 4.1.3 on
  embedded Tomcat, SQLite file storage). Endpoints: `POST /todos`, `GET /todos`, `GET /todos/{id}`, `PUT /todos/{id}`,
  `PATCH /todos/{id}/status`, `DELETE /todos/{id}`. JSON envelope `{"data": ...}` / `{"error": {...}}`, fixed error
  precedence, durable storage that survives a restart and a forced kill of the process, Maven wrapper and `backend/README.md`.
  Not verified: the performance target (NFR-5 / AC-35, p95 at or under 500 ms at about 1,000 tasks) was not measured
  (human decision IMP-6). Details: `docs/sdlc/CAP-2/pr-description.md` and `docs/sdlc/CAP-2/verification-report.md`.
- CAP-1: Todo frontend task management UI in `frontend/` (plain HTML, CSS and native ES modules, no build step, no
  dependency). List, add, edit, mark Completed or Pending, and delete (with an in-page confirmation) tasks. A single-file
  Java server (`frontend/server/FrontendServer.java`, JDK 21 or newer, port 8000) serves the page and proxies `/todos` to
  the backend on `127.0.0.1:8080`, so the UI and the API share one origin; `backend/` is unchanged. Run the backend first,
  then the frontend server; see `frontend/README.md`. Not verified: no agent has executed the UI JavaScript; the browser
  self-test results (`275 passed, 0 failed` in Edge 158 and in the VS Code Electron browser) are human-reported; the
  real-backend manual checks (no CORS error, load failure and Retry, real delete, keyboard-only use, screen reader) are
  still open; security, speed and performance were not tested (human decision H-2). Verification status: PARTIAL.
  Details: `docs/sdlc/CAP-1/pr-description.md` and `docs/sdlc/CAP-1/verification-report.md`.
