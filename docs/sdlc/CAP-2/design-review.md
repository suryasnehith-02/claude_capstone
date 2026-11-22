# Design review - CAP-2

Verdict: APPROVE        Review round: 2
Reviewed: requirements.md (approved at G1), architecture.md (Stage 2 attempt 3, REVISED, section 16 review responses),
repo-profile.md (attempt 3), clarifications.md (Clar. #1-#22, S2-1..S2-4, S2-ENV), open-issues.md, approvals.md, and the
repository (Read/Glob/Grep only; nothing executed).

Convention in this review: statements about Spring, Jackson, Tomcat, SQLite, sqlite-jdbc, HikariCP, Surefire/Failsafe,
Maven or JDK 25 behaviour cannot be checked against this repository, because no Java code, POM or dependency exists.
Each one is marked **(unverified)**. It is the reviewer's understanding of public behaviour, not an established fact.
The planner (S4, against the pinned versions, architecture.md 14.1) or the S7 tests must confirm it. The S2-ENV toolchain
(Maven 3.10.1, JDK 25.0.5.1) is the human's report and was not verified by any agent, including this one.

Round 2 method: each DR-1..DR-15 fix was checked in the cited sections of architecture.md, not taken from section 16 on
trust. The revised design was then re-read in full for regressions: D-22, D-23, the ERROR-dispatch boundary, the disabled
encoding filter, the JDK 25 / `--release 21` recommendation, the version-selection rule, the Failsafe packaged-JAR
durability test and traceability.

## 1. Verdict summary
All 15 round-1 findings are resolved, and the reviewer agrees with every disposition. All 15 were accepted and none
rejected. The major finding, DR-1 (TRACE answered by Tomcat outside the envelope), is fixed. TRACE is passed through at the
connector (`allowTrace=true`), rejected by C13 at P1, tested by T-P1b on defined and undefined paths, and has a G2-6
fallback. New decisions D-22 (one DataSource and one transaction manager, built in code) and D-23 (render before commit)
are sound and close DR-6 and DR-12. Traceability is still complete: 13/13 FR, 10/10 NFR and 36/36 AC.
The re-review found **no new critical or major issues**. It found 3 new minor findings, about edges the revision itself
created or left out:
- DR-16: the ERROR dispatch skips P1, so with `allowTrace=true` a TRACE could reach `HttpServlet.doTrace`.
- DR-17: unmapped container statuses are rewritten to 500, and this rule is not on the G2-5 list.
- DR-18: Jackson's member-name length limit is not set, so G2-5 (b) is incomplete.
It also found 3 suggestions (DR-19..DR-21). None of these needs re-architecture, and all can be fixed in S4 planning.
Counts (open): critical 0, major 0, minor 3, suggestions 3. Resolved from round 1: 1 major, 9 minor, 5 suggestions.

## 2. Requirement coverage
Rows marked * were traced into the revised decision text, not only the table in section 13.

| Requirement | Addressed in architecture.md | Assessment | Finding |
|---|---|---|---|
| FR-1 | 7.4, D-9, D-11, C7, C8, C11 | covered | - |
| FR-2 | D-8 `ORDER BY created_at DESC, seq DESC` | covered | - |
| FR-3* | D-13 (raw segment from C3, decoded once by C5), C8 | covered (DR-2 resolved) | - |
| FR-4 | 7.4, C8, D-9 | covered | - |
| FR-5 | C8, section 8 hard delete, 204 | covered | - |
| FR-6 | 7.4, D-9 | covered | - |
| FR-7* | D-10, D-11, D-13, D-14, 7.4; section 13 cites S2-4 | covered | - |
| FR-8* | D-10; D-14 explicit `StreamReadConstraints`; G2-5 (b) | covered. The body-level limits on ignored members are now listed for ratification, but the name-length limit is missing | DR-18 |
| FR-9* | C12, D-15, D-19 (REQUEST + ERROR) | covered | DR-17 |
| FR-10 | D-5, D-6, D-22 | covered | - |
| FR-11* | D-12 (TRACE via `allowTrace=true`), 7.5 first row, C2 `TomcatConfig` | covered (DR-1 resolved, unverified until T-P1b passes; fallback G2-6) | DR-16 |
| FR-12* | 7.2 P1-P7, 10.1 | covered. Order still matches AC-25 / A-14 | - |
| FR-13 | D-4; section 13 cites S2-4 | covered | - |
| NFR-1* | D-19, T-ERR (incl. ERROR dispatch) | covered | DR-16 |
| NFR-2 | C12, D-15 | covered | - |
| NFR-3* | 7.2, D-7, D-23 (render failure rolls back) | covered (DR-12 resolved) | - |
| NFR-4 | D-6, D-22, durability (a) in-JVM and (b) Failsafe child JVM | covered | DR-21 |
| NFR-5 | D-6, performance suite (reseeded, p95 asserted, max reported) | covered (estimate; measured at S7) | - |
| NFR-6 | D-2 | covered | - |
| NFR-7* | D-20 rules (1)-(3), section 10 Observability, T-LOG three paths | covered (DR-5 resolved) | - |
| NFR-8 | no target | covered | - |
| NFR-9 | D-12, D-15, T-CORS; S2-4 cited | covered | - |
| NFR-10 | D-15, C7 | covered | - |
| AC-1..AC-11 | as in round 1 (C5-C8, C12, D-8, D-9, 7.2, 7.4) | covered | - |
| AC-12* | D-15, D-19 (ERROR dispatch in the boundary), D-12 (TRACE in the envelope), G2-6 (non-conformant HTTP only) | covered | DR-16, DR-17 |
| AC-13 | C13, ApiExceptionHandler, T-ERR | covered | - |
| AC-14 | C10, D-6 | covered | - |
| AC-15* | D-13; S2-4 cited | covered | - |
| AC-16 | D-4; S2-4 cited | covered | - |
| AC-17..AC-21 | C6, C7, D-10, D-15, 7.4 | covered | AC-18: DR-18 |
| AC-22* | durability (b): packaged JAR, `destroyForcibly()`, restart, compare | covered | DR-21 |
| AC-23 | C6, 7.4 | covered | - |
| AC-24* | D-12 (TRACE on undefined path gives 404), T-P1b | covered | - |
| AC-25 | 7.2, 10.1 | covered | - |
| AC-26, AC-27 | D-8, D-9 | covered | - |
| AC-28..AC-31 | D-11, 7.4, section 8 | covered | - |
| AC-32* | D-7, D-22, T-TX | covered (DR-6 resolved) | - |
| AC-33, AC-34 | D-15, T-CORS | covered | - |
| AC-35* | performance suite, reseeded per operation (DELETE from 1,200, POST up to 1,200); "about 1,000" holds | covered | - |
| AC-36* | D-20, T-LOG | covered | - |

Traceability table (architecture.md section 13): all 59 ids are present (re-counted against `requirements.md`, which
has 13 FR, 10 NFR and 36 AC rows). No row is wrong. S2-4 is cited on FR-7, FR-13, NFR-9, AC-15 and AC-16 (DR-14).

## 3. Repository evidence check
| Claim in architecture.md | Verified how (path read) | Result | Finding |
|---|---|---|---|
| The shell allow list is read-only git only (`.claude/settings.json:44-48`); commit and push are "ask" | Read `.claude/settings.json:40-69` | true | - |
| Source edits need S5, gates and a non-protected branch (`agent-paths.conf:9,15`) | Read `.claude/hooks/agent-paths.conf` (line 9 `source-gates`, line 15 developer row); line 13 limits this reviewer to `design-review.md` | true | - |
| Branch `Main` matches the protected `main` glob | Read `.claude/hooks/protected-branches.txt` (line 3 `main`, case-insensitive per line 1); `.git/HEAD` exists (round 1: `refs/heads/Main`) | true | - |
| `.sdlc/` is gitignored (`.gitignore:1-2`) | Read `.gitignore` (line 1 comment, line 2 `.sdlc/`) | true | - |
| No `pom.xml`, `mvnw`, `*.java` or `build.gradle*`; `backend/` absent ("Glob re-run in attempt 3") | Glob `**/{pom.xml,mvnw*,*.java,build.gradle*}` and `backend/**`: no files | true | - |
| `requirements.md` header still reads DRAFT while G1 is approved | Read `requirements.md:3`, `approvals.md:7` | true | - |
| S2-ENV toolchain (Maven 3.10.1, JDK 25.0.5.1) | `clarifications.md:42`, which itself says "NOT executed or verified by any agent" | unverifiable (correctly labelled as reported, not verified) | - |
| 13 FR / 10 NFR / 36 AC mapped | Grep of `requirements.md` id rows vs. section 13 | true | - |

## 4. Category results
| Category | Result | Findings |
|---|---|---|
| Security | Improved. The ERROR dispatch now carries `nosniff` and the request id. TRACE is rejected in the envelope and never echoed on the REQUEST dispatch. Residual: enabling `allowTrace` removes Tomcat's own TRACE guard, and the boundary-only ERROR mode does not reinstate it. There is no known trigger, but the security claim "TRACE never reaches the DispatcherServlet" holds only for REQUEST | DR-16 |
| Scalability | Unchanged and adequate (single instance, about 1,000 tasks) | none |
| Reliability and failure handling | Strong. D-22 fixes the connection binding, and the 5 s `connectionTimeout` turns a wiring mistake into a 500 instead of a hang. D-23 removes the "500 after commit" case. Journal fallback order is configuration only. Mapping unmapped container statuses to 500 is a reliability-visible choice that has not been ratified | DR-17 |
| Maintainability | Improved: `ApiRoutes` is the single literal source; U-ARCH enforces no `@Transactional` and inward dependencies; the 14.1 checklist ties each version-sensitive name to a test | none |
| Internal consistency | Consistent. Checked: D-1 / D-12 / 7.2 / 7.5 / T-P1b agree on TRACE; D-13 / C3 / C5 / 7.3 / controller agree on one id source; D-20 / section 10 / C13 / exception-mapping rows agree on logging; D-7 / D-22 / D-23 / 7.3 / section 8 / sequence diagram agree on lock, transaction, render and commit; the header's pointer to 14.1 is now real. G2-5 (b) says the parser limits are complete, but the name-length limit is missing | DR-18 |
| Fit with existing system and conventions | Good. `backend/` only; no framework files touched; settings changes left to the human | none |
| Technology choices and dependencies | Justified. Failsafe is a new plugin, but its version is managed by the Boot parent (unverified). The `--release 21` on JDK 25 recommendation is reasonable and honestly limited ("runs on 21" stays unverified). The version-selection rule is deterministic but has no outcome if no line qualifies | DR-20 |
| Testability | Strong: the Windows-safe data lifecycle, the explicit context close, the packaged-JAR child JVM, reseeded performance runs, raw-socket TRACE/HEAD/100-continue cases, and T-TX. Gaps: some Failsafe and Boot-repackage mechanics, and the T-RT order check sees Boot registrations only | DR-19, DR-21 |
| Operability (observability, deployment, rollback) | Good. One logging rule; the absolute data path and journal mode are logged at startup; run instructions require `backend/` or an absolute path; `--enable-native-access` is in every launch path | none |
| Over-engineering / complexity | Grew slightly (D-22, D-23, boundary-only mode, 30 V-items), but each addition closes a named finding. Judged **justified**; nothing removable without reopening a requirement | none |
| Dependency and licensing risk | Low and unchanged (Apache-2.0, public domain, EPL/LGPL, test-scope EPL-2.0/MIT; unverified per artifact). The JDK 25 support of the line and libraries is unverified (V-26), and fallbacks are defined (G2-2 option B/C) | none |

## 5. Findings
Round-1 findings DR-1..DR-15 are resolved (section 6). The open findings below are new in round 2.

| ID | Severity | Location | Description | Impact | Recommendation |
|---|---|---|---|---|---|
| DR-1 | major | D-12, 7.5 | Round 1: Tomcat answers TRACE before the filter chain. **Resolved** (section 6) | - | - |
| DR-2..DR-10 | minor | various | Round 1. **Resolved** (section 6) | - | - |
| DR-11..DR-15 | suggestion | various | Round 1. **Resolved** (section 6) | - | - |
| DR-16 | minor | D-12, D-19 (boundary-only mode), C13, 7.5 TRACE row | With `allowTrace=true`, Tomcat no longer guards TRACE; only P1 does, and P1 runs on the REQUEST dispatch only. On an ERROR dispatch, C13 runs in boundary-only mode and calls `chain.doFilter`. A TRACE request that reached an ERROR dispatch would go to the DispatcherServlet. Spring's `FrameworkServlet.doTrace` then falls back to `HttpServlet.doTrace` by default (`dispatchTraceRequest=false`), which echoes the request headers (unverified). The only trigger is a `sendError` (or an uncaught exception reaching Tomcat) during a TRACE request. The design never says that C13 and C12 write rejections with `setStatus` plus a body and never with `sendError`. A `sendError` would also cause an ERROR dispatch for every P1-P3 rejection. | No known trigger today, but the claim "TRACE never reaches the DispatcherServlet" is true only for REQUEST. A future `sendError` would reopen header echo (XST) and an out-of-envelope response (AC-12) | (1) State in C13/C12: rejections are written with `setStatus` plus bytes; `sendError` is never used by application code. (2) In boundary-only mode, if the method is not one of the route methods (for example TRACE), write the precomputed body for the dispatched status directly and do not call `chain.doFilter`. (3) Add a T-ERR case: the test-only `sendError` hook invoked for a TRACE request; assert the envelope and no echoed header |
| DR-17 | minor | D-19 (FallbackErrorController status mapping), G2-5, T-ERR | The ERROR dispatch maps 404, 405, 413 and 415 to their codes and "anything else to INTERNAL_ERROR". The T-ERR expectation ("500 or the mapped code") implies the HTTP status becomes 500. So a container-originated 4xx that is not in the list (for example 400 or 408, if Tomcat dispatches it to the error page; unverified which ones do) becomes a 500, and rule (3) logs it at ERROR. The catalog is fixed (FR-9), so 500 may be the right choice. But it is a client-visible behaviour, and it is neither in G2-5 nor in G2-6. | The human ratifies an incomplete contract list. Client 4xx can show up as server errors in logs and responses | Add G2-5 (h): "A container ERROR dispatch whose status has no catalog code is answered as 500 INTERNAL_ERROR and logged once", or the alternative "keep the original status and use INTERNAL_ERROR as the code". Make T-ERR assert the chosen status explicitly for one unmapped 4xx and one unmapped 5xx |
| DR-18 | minor | D-14 (`StreamReadConstraints`), G2-5 (b), 7.4 last row | D-14 says every relevant read limit is set explicitly. It lists nesting depth, number length and string length, but not the member-**name** length limit. Jackson added that limit in 2.16, with a default of about 50,000 characters (unverified; the Jackson 3 default is also unverified). A 65,536-byte body can hold a longer name, so an ignored member could still give 400. That contradicts G2-5 (b) as written ("the string limit equals the body cap, so it never triggers"). The document-length and token-count limits are also not stated (defaults unverified). | FR-8 / AC-18: a client-visible 400 for an ignored member that the human was not asked to ratify; the behaviour may change with the Jackson version | Set `maxNameLength` to the body cap (65,536) explicitly. Set the document-length and token-count limits explicitly, to "unlimited" or to the body cap. List them in G2-5 (b) and V-22. Add a U-JSON case with a 60,000-character member name |
| DR-19 | suggestion | T-RT (b), D-1, V-9, V-10 | (a) T-RT (b) reads the order from Boot's servlet-context initializer collection, which sees only Boot registration beans. Filters that the container adds itself are not in it. For example, Tomcat's WebSocket `WsFilter`, added by the websocket initializer when `tomcat-embed-websocket` is on the class path, is believed to be matched after declared filters and inert for non-upgrade requests (unverified). (b) `OncePerRequestFilter` sends a *nested* ERROR dispatch to `doFilterNestedErrorDispatch`, which by default skips `doFilterInternal` (unverified). Overriding `shouldNotFilterErrorDispatch()` alone does not cover that case. | "First servlet filter" is proven only among Boot registrations. A nested ERROR dispatch would bypass the boundary-only mode | (a) Note in D-1 that container-added filters are outside T-RT, and add a V-item for `WsFilter` placement (or exclude `tomcat-embed-websocket`, if not needed). (b) Also override `doFilterNestedErrorDispatch` to run the boundary-only mode, or state why a nested ERROR dispatch cannot occur here. Add to V-10 |
| DR-20 | suggestion | 4.1 Spring Boot row, G2-2 | The version-selection rule is deterministic, but: (a) it gives no outcome when no 4.x line meets all three criteria (for example, 4.0 near end of support and the newest line without a patch release); (b) criterion (2) is measured "on the S4 date", but the pin is ratified at G2; (c) the text says "the human (or the planner) confirms the line at G2", but the planner runs only after G2. | G2-2 may be ratified as a rule rather than a version, and the version is then decided at S4 by an agent | State the tie-break (for example, "if none qualifies, take the newest line meeting (1) and (3) and record the support date") and measure (2) on the G2 date. G2-2 should record a concrete minor line chosen by the human. If the human ratifies only the rule, the planner records the line it applied in impl-plan.md, and the Orchestrator shows that line to the human at G4 |
| DR-21 | suggestion | Section 10 Testability, durability (b); 10.2; V-28 | The packaged-JAR approach is sound, but several mechanics are not stated: (a) Boot's repackaged JAR places classes under `BOOT-INF/classes`. Failsafe by default puts the project JAR on the test class path, so the Boot parent's Failsafe `classesDirectory` setting is needed (believed to be preconfigured; unverified). (b) The test needs the JAR's file name, so `project.build.finalName` has to be passed in, for example as a Failsafe system property. (c) The Failsafe `integration-test` and `verify` executions must be bound in the POM, because a version managed by the parent does not run anything by itself. (d) The `*IT` naming convention must keep Surefire from also running the class. | `./mvnw -B verify` may silently skip, or fail, the AC-22 forced-kill test | Add (a)-(d) to V-28 and to the S4 plan notes. The S7 report must show the durability (b) test in the Failsafe summary |

## 6. Recommendations and disposition
Dispositions are taken from architecture.md section 16. Each one was re-checked against the cited sections, not assumed.

| Finding | Recommendation | Disposition | Reason (architect) and reviewer's check | Reviewer agrees? |
|---|---|---|---|---|
| DR-1 (major) | Override Tomcat TRACE handling (or ratify it in G2-6); extend T-P1b | accepted | Checked in 7.5 (new first row, default marked unverified, V-1), D-12 (`allowTrace=true`, TRACE at P1, never echoed on REQUEST, fallback to G2-6), C2 `TomcatConfig`, 7.1 last row, 7.2 P1, T-P1b (TRACE with a distinctive header on a defined path, TRACE/HEAD on `/nope`, route `Allow`), G2-5 (e), G2-6, R-1. Residual ERROR-dispatch edge raised as new DR-16 | yes |
| DR-2 | One id source, decoded once; double-encoding tests | accepted | Checked in D-13 (raw `RouteMatch` segment, single strict decode, `%2530` fails the regex, `%61` accepted), C3/C5/controller (no `@PathVariable`), 7.2 structural rule, 7.3 `RouteMatch` row, 7.5 row, T-P4 / T-P4b / U-ID, G2-5 (f), R-16 | yes |
| DR-3 | No reliance on an order tie | accepted | Checked in 7.5 (encoding filter disabled, property name unverified, V-9), the properties list, T-RT (b) (actual order, no tie, filter absent), and the documented fallback. D-15 no-charset rationale is consistent. Disabling is safe because C13, C12 and the error controller read and write bytes only and nothing calls `getParameter`/`getReader`. Residual scope gap: suggestion DR-19 (a) | yes |
| DR-4 | Cover the ERROR dispatch; T-RT excludes `/error` | accepted | Checked in D-19 (REQUEST + ERROR, boundary-only mode, MDC restore, `shouldNotFilterErrorDispatch()` false), C2/C13 rows, 7.5 row, FallbackErrorController `nosniff` and precomputed bytes, T-ERR, T-RT (a), V-10, V-11. New edges: DR-16, DR-17, DR-19 (b) | yes |
| DR-5 | One explicit logging rule | accepted | Checked in D-20 rules (1)-(3), section 10 Observability, C13 and exception-mapping rows, and T-LOG (three paths, exactly one entry each, 4xx not logged). Consistent | yes |
| DR-6 | One DataSource + manager; pool and timeout in code; no `@Transactional` check | accepted | Checked in D-22 (Hikari built in code, pool 1, `connectionTimeout` 5 s, one `DataSourceTransactionManager`, `TransactionTemplate` and `JdbcClient` from those beans, no `spring.datasource.*`), D-7, 7.5 row, properties, R-15, U-ARCH, T-TX, V-20, V-21. Reads take no lock, so no lock-order inversion exists with the one connection | yes |
| DR-7 | Pin the 4.x minor at G2; behaviours-to-verify list | accepted | Checked: 4.1 selection rule with candidate 4.1.x (unverified, fallback 4.0.x); the unverified Boot 3.5 support claim is removed; the header points to 14.1; 14.1 lists V-1..V-30, each with a proving test and an "if wrong" action. Residual: the rule's edge cases and timing, suggestion DR-20 | yes |
| DR-8 | Complete G2-5 (a)-(e) with tests | accepted | Checked: G2-5 now has (a)-(g), each with its decision and test (T-P3 two Content-Type headers, T-P5 / T-B1 limits at and over the boundary, T-P1a `/%74odos`, T-P1b HEAD/TRACE on `/nope`). Residual: name-length limit (DR-18) and unmapped ERROR statuses (DR-17) | yes |
| DR-9 | Windows-safe test data lifecycle | accepted | Checked in section 10 Testability: a per-context directory under `backend/target/test-data/`, never deleted by tests; no `@TempDir` for databases; `@DirtiesContext(AFTER_CLASS)` on contract and durability classes; an explicit close with a `HikariDataSource`-closed assertion before restart; repository tests close their DataSource | yes |
| DR-10 | Name the journal create/delete risk; TRUNCATE, then WAL; report max | accepted | Checked in R-10 (b), D-6 (`todo.store.journal-mode` property, `synchronous` fixed FULL, startup read-back, fallback order, S7 record), G2-1, performance test reporting max, V-29 | yes |
| DR-11 | Explicit BOM check; every Jackson read feature explicit | accepted | Checked in D-14 and C6 (U+FEFF check before parsing, every feature listed on or off, `FAIL_ON_TRAILING_TOKENS` plus a manual check, duplicates last-wins, explicit constraints), V-22, U-JSON. Name-length gap: DR-18 | yes |
| DR-12 | Serialize before commit, or document | accepted (design change) | Checked: D-23 (a `Function<Task,R>` renderer inside the `TransactionTemplate` callback; a render failure rolls back), 7.3 TaskService row, section 8 steps 5-7, the sequence diagram. `domain` still imports no `web` type. The remaining gap (socket write fails after commit) is correctly stated | yes |
| DR-13 | Shared route constants; working directory and data path | accepted | Checked: `ApiRoutes` (section 6) used by C3 and the annotations, 7.2 rule, section 8 and 10.2 working-directory rule with the absolute-path option, startup log of the absolute path | yes |
| DR-14 | Cite S2-4 in section 13; Orchestrator decides on an addendum | accepted (section 13 part) | Checked: FR-7, FR-13, NFR-9, AC-15 and AC-16 cite S2-4; the header note is updated. `requirements.md:3` still says DRAFT; the addendum remains the Orchestrator's decision (open-issues.md line 26) | yes |
| DR-15 | Child-JVM class path, reseeding, 100-continue | accepted | Checked: durability (b) uses the packaged JAR under Failsafe, with an explicit class path as the alternative (V-28); the performance run reseeds per operation; T-P2 / T-P2b cover 100-continue (D-3 records, but does not assert, whether `100 Continue` was sent). Failsafe mechanics: suggestion DR-21 | yes |
| DR-16 | Never `sendError` in application code; boundary-only mode short-circuits non-route methods; T-ERR TRACE case | pending (new) | - | - |
| DR-17 | G2-5 (h) for unmapped container statuses; T-ERR asserts the chosen status | pending (new) | - | - |
| DR-18 | Set `maxNameLength` (and document/token limits) explicitly; extend G2-5 (b), V-22, U-JSON | pending (new) | - | - |
| DR-19 | Note container-added filters outside T-RT; cover nested ERROR dispatch | pending (new) | - | - |
| DR-20 | Tie-break and timing for the version rule; record a concrete line | pending (new) | - | - |
| DR-21 | Failsafe `classesDirectory`, `finalName`, executions, `*IT` naming in V-28 and S4 | pending (new) | - | - |

DR-16..DR-21 are minor or suggestion. Under the skill's verdict rule they do not block APPROVE. They can be carried into S4
as plan items without another Stage 2 rework. Whether to fix them in architecture.md first is the Orchestrator's and the
human's choice.

### Re-check of revision-specific areas
- **D-22 (persistence wiring):** sound. One Hikari pool of 1, built in code, one manager, `JdbcClient` sharing the
  DataSource; transaction-bound connection reuse is unverified (V-20) and proven by T-TX. The 5 s `connectionTimeout`
  equals `busy_timeout`; acceptable. No regression.
- **D-23 (render before commit):** sound and minimal. The render function is a JDK type, so the layering rule still
  holds. No regression.
- **ERROR-dispatch boundary:** closes DR-4. New edges DR-16, DR-17 and DR-19 (b), all minor or suggestion.
- **Encoding filter disabled:** safe for this design, because every body is read and written as bytes and no API depends
  on the request character encoding. The 4.x property name is unverified (V-9), and a fallback is defined. No regression.
- **JDK 25 / `--release 21`:** reasonable. The parent mapping `java.version` to `maven.compiler.release` is unverified
  (V-30); the JNI native-access flag (JEP 472, warning only, unverified) is applied to every launch path; option B
  (`--release 25`) is an equally valid, simpler choice if portability to 21 is not wanted. The architecture correctly
  says JDK 25 is the only runtime S7 can verify.
- **Version-selection rule:** deterministic, and excludes a fresh `.0` line. The candidate 4.1.x is plausible on
  2026-11-20 given a six-month cadence after 4.0 (all unverified). Edge cases: DR-20.
- **Failsafe packaged-JAR durability test:** the right approach for a real forced kill on Windows (`destroyForcibly`
  is TerminateProcess). Mechanics: DR-21.
- **Traceability:** complete (13/10/36). No requirement lost coverage in the revision.

### Assessment of the G2 decisions (section 14)
| Item | Reviewer opinion | Notes |
|---|---|---|
| G2-1 SQLite + sqlite-jdbc, DELETE + FULL, fallback TRUNCATE then WAL (both FULL) | **Endorse** | DR-10 incorporated. The fallback is configuration only and recorded at S7. The H2 adapter behind C9 remains the last resort (SQLite/H2 behaviour unverified) |
| G2-2 Versions (changed) | **Endorse option A, with DR-20** | `--release 21` on the installed JDK 25 is reasonable; option B is also acceptable. Ask the human to name a concrete Boot 4.x minor line (candidate 4.1.x, unverified) rather than ratifying only the rule. sqlite-jdbc latest 3.x, pinned. The human must accept that JDK 25 support by the line and libraries is unverified (V-26) |
| G2-3 65,536 bytes | **Endorse** | Unchanged; arithmetic re-checked (under 7,500 bytes worst case) |
| G2-4 Coordinates | **Endorse** | Placeholder; mechanical to rename |
| G2-5 Contract details (a)-(g) | **Endorse (a)-(g); list still incomplete** | (a) two Content-Type headers give 415: safe. (b) parser limits: endorse nesting 1,000 and number length 1,000 (or the alternative of number length = body cap); **add the name-length limit (DR-18)**. (c) encoded literals give 404: consistent with raw matching and A-15. (d) HEAD: 404 / 405 without body, consistent with S2-4. (e) TRACE in the envelope: endorse (DR-1). (f) single decode: endorse. (g) 100-continue gives 413 without reading: endorse. **Add (h) for unmapped container statuses (DR-17)** |
| G2-6 Container-level rejections | **Endorse** | TRACE correctly removed (moves back only if the override fails at T-P1b). The list now names `%zz`, `%5C` and invalid UTF-8 as candidates, depending on V-3 |
| G2-7 Permission to run `./mvnw` / `mvn`, network | **Required, still not given** | Verified again: `.claude/settings.json:44-48`. Blocks S5 and S7. S2-ENV is a report only; the first permitted step `./mvnw -v` must record the real toolchain |
| G2-8 Wrapper creation | **Endorse the proposed default** | The human (or one explicitly approved agent command) runs `mvn wrapper:wrapper` in `backend/` with the installed Maven 3.10.1, after the POM exists. Whether the goal needs a POM is unverified (V-30). The workflow pauses inside S5 for this step, and the Orchestrator should plan for that pause |

## 7. Open questions for the human
No question blocks this review. The design can be approved, and the human decides these at G2:

| # | Question | Why it matters |
|---|---|---|
| 1 | G2-2: which Spring Boot 4.x minor line exactly (candidate 4.1.x, unverified; fallback 4.0.x)? And which JDK target: `--release 21` on JDK 25 (recommended), `--release 25` or `--release 17`? | Every override in 7.5 and every V-item in 14.1 depends on the line. Only JDK 25 can be verified at S7 (DR-20) |
| 2 | G2-5: ratify (a)-(g) as listed, and also (h) and the name-length limit? (h): an unmapped container error status becomes 500 INTERNAL_ERROR, or keeps its status with the code INTERNAL_ERROR (DR-17). Name-length limit: very long member names in ignored members are accepted up to the body cap (DR-18) | Client-visible behaviour not covered by Clar. #1-#22 or S2-4 |
| 3 | G2-5 (b): keep the number-length limit at 1,000 characters, or raise it to the body cap so that only nesting depth can reject an ignored member? | FR-8 "never cause an error" versus parser safety |
| 4 | G2-7: may agents run `./mvnw` and `mvn` (wrapper creation) and download from Maven Central? Is JDK 25.0.5.1 the JDK on `JAVA_HOME`/`PATH` for the agents' shell? | S5 and S7 cannot build or verify anything without this. S2-ENV is unverified |
| 5 | G2-8: accept the default of running `mvn wrapper:wrapper` once in `backend/` after the POM exists (by the human, or by one approved agent command)? | The wrapper is the agreed build entry point (S2-1b) |
| 6 | Should DR-16..DR-21 be fixed in architecture.md before G2, or carried into the S4 plan? | All are minor or suggestion. The reviewer recommends carrying them into S4 as explicit plan items |
