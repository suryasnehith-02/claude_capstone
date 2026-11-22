# Todo backend (CAP-2)

REST API for task management: create, list, get, replace, change status and delete tasks. One Maven module, Java,
Spring Boot 4.1.3 (Spring MVC on embedded Tomcat), SQLite file storage through Spring JDBC and sqlite-jdbc. Compiled
for Java release 21 and built and tested on JDK 25.0.5.1 (Windows 11); no other JDK or operating system was tried.

Status of the claims below: "verified" means a test or build run in this repository showed it
(`docs/sdlc/CAP-2/implementation-report.md` has the evidence); "unverified" means nobody ran it.

## Working directory rule

Run every command from this directory (`backend/`). The Maven wrapper (`mvnw`, `mvnw.cmd`) and the default data file
`./data/todo.db` are relative to it. A relative `todo.data-file` is resolved against the working directory of the
process, so starting the server from another directory silently creates a second, empty database there. Use an absolute
`--todo.data-file` (see Run) to avoid this.

## Build and test

Commands (run in `backend/`; all downloads come from Maven Central):

| Command | Purpose | Status |
|---|---|---|
| `./mvnw -v` | print the Maven and JDK in use | verified (Maven 3.10.1, JDK 25.0.5.1) |
| `./mvnw -B test` | unit, repository and contract tests (Surefire) | verified |
| `./mvnw -B test -Dtest=<ClassName>` | one test class | verified |
| `./mvnw -B verify` | tests, package the executable JAR, then the forced-kill durability test (Failsafe, `*IT` classes) | verified |
| `./mvnw spring-boot:run` | start from sources | unverified (never run) |
| `mvnw.cmd ...` | the same on Windows `cmd` | unverified (only `./mvnw` through Git Bash was run) |

The performance suite (`-Pperformance`) was not written and no performance test was run (decision IMP-6): the
p95 target (NFR-5, AC-35) is NOT VERIFIED. `mvn clean`, lint, format and audit tools are not part of this project.

## Run

After `./mvnw -B verify` the executable JAR is `target/todo-backend-0.0.1-SNAPSHOT.jar`. This form is what the
durability test starts in a child JVM (verified), here with an absolute data file:

```
java --enable-native-access=ALL-UNNAMED -jar target/todo-backend-0.0.1-SNAPSHOT.jar --todo.data-file=<absolute path>/todo.db
```

- `--enable-native-access=ALL-UNNAMED` is needed on JDK 25: without it the JVM prints a "restricted method" warning when
  sqlite-jdbc loads its native library (observed, a warning only). The POM passes the flag to Surefire, Failsafe and
  `spring-boot:run`.
- The server listens on `127.0.0.1:8080` by default (loopback only). Change with `--server.port=<port>` and
  `--server.address=<address>`; listening beyond loopback was not tested, and the API has no authentication.

## Configuration

| Property | Environment variable | Default | Notes |
|---|---|---|---|
| `todo.data-file` | `TODO_DATA_FILE` | `./data/todo.db` | SQLite file. The path is made absolute at startup and missing parent directories are created. An unusable or unreadable file stops startup. |
| `todo.store.journal-mode` | `TODO_STORE_JOURNAL_MODE` | `DELETE` | `DELETE`, `TRUNCATE` or `WAL`. Any other value stops startup naming the property. |
| `server.port` | `SERVER_PORT` | `8080` | |
| `server.address` | `SERVER_ADDRESS` | `127.0.0.1` | |

- The environment variable names bind in a configuration-binder test; starting the JAR with them set in the real
  process environment was not run (unverified). Command-line arguments (`--todo.data-file=...`) were run (verified).
- `synchronous` is fixed to `FULL` and is not configurable. At startup the application reads the journal mode and
  `synchronous` back from SQLite and refuses to start if they differ from the configuration. The startup log has one
  INFO line with the absolute data file and the journal mode.
- Journal-mode fallback: the default `DELETE` creates and removes a `<file>-journal` file on every commit, and
  antivirus software on Windows can interfere with that. No such interference was seen, and no load or timing test was
  run, so there is no evidence that a change is ever needed. If file-lock or timing problems appear, change only
  `todo.store.journal-mode`, first to `TRUNCATE`, then to `WAL` (all three modes were verified to start, read back and
  keep data across a restart; `WAL` adds `-wal` and `-shm` files next to the database while the server runs).

## Operating limits

- Single instance only: the write lock is inside the process and the connection pool holds one connection. Two server
  processes on one data file, or any other program writing to it, were not tested and are not supported.
- Local disk only: a database file on a network share was not tested and SQLite does not support that reliably.
- Data survives a clean stop and a forced kill of the process (verified: in-JVM restart and `destroyForcibly()` of the
  packaged JAR, the list is byte-identical afterwards and a deleted task stays absent). Power loss and disk failure were not tested.
- Deleting a task removes the row for good. The data file is never deleted by the application or by the tests.
- Availability: there is no availability target (NFR-8) and nothing was built for one (no replication, health check or
  failover).
- Performance: about 1,000 tasks and p95 at or under 500 ms were the targets (NFR-5, AC-35); they were never measured.
  NOT VERIFIED.

## API

All bodies are UTF-8 JSON. Success is `{"data": ...}`; every error is `{"error":{"code":"...","message":"..."}}`, and
`details` (a list of `{"field","message"}`) appears only on `VALIDATION_ERROR`.

| Operation | Success |
|---|---|
| `POST /todos` | 201 with the new task (`title` required, `description` optional) |
| `GET /todos` | 200 with a list, newest first |
| `GET /todos/{id}` | 200 with the task |
| `PUT /todos/{id}` | 200; replaces `title` and `description`, keeps `status` |
| `PATCH /todos/{id}/status` | 200; `{"status":"Pending"}` or `{"status":"Completed"}` (exact spelling) |
| `DELETE /todos/{id}` | 204, no body |

A task is `{id, title, description, status, createdAt, updatedAt}`; `id` is a lowercase UUID (an uppercase or
percent-encoded spelling is accepted and finds the same task); timestamps are UTC with milliseconds (`2026-03-04T05:06:07.008Z`).

Limits and rules (see the requirements for the exact wording): title 1 to 100 and description at most 500 Unicode code
points after trimming; request body at most 65,536 bytes; `Content-Type` must be `application/json` (parameters ignored)
when a body is sent; unknown members are ignored; `Accept` is ignored; no CORS headers are ever sent (a browser
pre-flight gets 405); `HEAD`, `OPTIONS` and `TRACE` get 405 on a defined path (TRACE is never echoed) and 404 otherwise.
Error precedence is fixed: route (404/405), body size (413), media type (415), id (400 `INVALID_ID`), body syntax
(400 `INVALID_REQUEST_BODY`), fields (400 `VALIDATION_ERROR`), then existence (404 `TODO_NOT_FOUND`).

Error codes: `VALIDATION_ERROR`, `INVALID_REQUEST_BODY`, `INVALID_ID` (400); `TODO_NOT_FOUND`, `NOT_FOUND` (404);
`METHOD_NOT_ALLOWED` (405); `PAYLOAD_TOO_LARGE` (413); `UNSUPPORTED_MEDIA_TYPE` (415); `INTERNAL_ERROR` (500).
Failures of the server itself are logged once at ERROR with a request id, the method, the route template and the exception class,
and never with titles, query strings or ids; client errors are not logged.

Known limit (accepted as G2-6, container-level rejections outside the envelope): Tomcat itself rejects a malformed
percent sequence in the path (for example `/todos/%zz`, `/todos/%FF`, `/todos/%`, `/todos/a%00b`, `/todos/%C0%AF`) with
its own `400 text/html` page before the application sees the request. Those ids therefore get that page and not
`INVALID_ID`. The page carries no server name or version (verified). Other container-level refusals of malformed HTTP
(oversized headers, broken framing) are Tomcat's own answers too and were not tested.

## Tests

`./mvnw -B test` runs the Surefire suites; test databases are created under `target/test-data/` and are never deleted by
the tests. `./mvnw -B verify` also runs `ForcedKillDurabilityIT` through Failsafe against the packaged JAR. Test logs may
show Mockito and Byte Buddy "Java agent has been loaded dynamically" warnings: they come from the test dependencies and
not from the application.
