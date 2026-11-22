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
