# CAP-2: Add Todo backend task management REST APIs

<!-- Draft written by pr-manager (MODE draft, attempt 1). No commit, push or PR call has been made. Gate G7 is pending.
     The repository has no PR template (no `.github/` directory), so the standard five sections are used. -->

**Proposed PR title** (49 characters): `CAP-2: Add Todo backend task management REST APIs`
**Head:** `feature/CAP-2-todo-backend-task-apis`   **Base:** `main` (does not exist on the remote yet, see "Before the PR can be created")
**This PR will NOT be merged automatically.** It is opened for human review and merged only by a human.

## Summary
Adds the Todo backend for JIRA CAP-2, "Todo App - Backend: Task management REST APIs": a single Maven module in
`backend/` (Java, Spring Boot 4.1.3 on embedded Tomcat, SQLite file storage through Spring JDBC and sqlite-jdbc) that
creates, lists, reads, replaces, changes the status of and deletes todo tasks, so that the Todo frontend (CAP-1) has one
reliable source of truth and a stable contract. It validates every request, answers with a fixed JSON envelope and
status codes, applies a fixed error precedence (route, body size, media type, id, body syntax, fields, existence), and
stores tasks durably in a SQLite file. Requirement coverage as verified: FR 13 of 13, AC 35 of 36, NFR 8 PASS; AC-35 and
NFR-5 (performance) are **NOT VERIFIED** and overall verification status is **PARTIAL** (details below).

- JIRA: [CAP-2](https://agentic-workflow-123.atlassian.net/browse/CAP-2) (site `agentic-workflow-123.atlassian.net`; Story; companion story CAP-1 is the frontend consumer)
- Requirement coverage source: `docs/sdlc/CAP-2/verification-report.md` sections 1 and 6 (13 FR, 36 AC, 10 NFR)

## Changes Made
All paths are new files (no file is modified or deleted by this PR). `M` = `backend/src/main/java/com/capstone/todo/`,
`T` = `backend/src/test/java/com/capstone/todo/`.

| Area | Change | Files |
|---|---|---|
| Build | Maven POM (parent `spring-boot-starter-parent` 4.1.3; dependencies `spring-boot-starter-web`, `spring-boot-starter-jdbc`, `org.xerial:sqlite-jdbc:3.54.1.0`, `spring-boot-starter-test` (test); Surefire, Failsafe, unused `performance` profile; `--release 21`), Maven wrapper (Maven 3.10.1), module `.gitignore` (`target/`, `data/`) | `backend/pom.xml`, `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/maven-wrapper.properties`, `backend/.gitignore` |
| Application and config | Application class, typed properties, Jackson 3 mapper with every read feature and limit explicit, UTC clock and id generator, SQLite pool and transaction wiring, Tomcat customisation, MVC configuration, `application.properties` (includes `spring.sql.init.mode=never`) | `M/TodoApplication.java`, `M/config/{TodoProperties,JsonConfig,ClockConfig,PersistenceConfig,TomcatConfig,WebConfig}.java`, `backend/src/main/resources/application.properties` |
| Request gateway | Servlet filter that decides route (404/405), body size (413) and media type (415) before the controller, rejects TRACE, turns every uncaught failure into one 500 envelope and one ERROR log entry | `M/web/gate/{ApiGatewayFilter,BodyGate,RawBody,RouteMatch,RouteTable}.java`, `M/web/ApiRoutes.java` |
| Request parsing and validation | Strict id parser (one percent-decode, strict UTF-8, canonical UUID), strict JSON body reader (BOM, duplicate keys, trailing tokens, unpaired surrogates, limits), field validator counting Unicode code points after trimming | `M/web/request/{IdParser,JsonBodyReader,TodoRequestPipeline}.java`, `M/validation/{TaskRequestValidator,UnicodeText}.java` |
| Controller and errors | Six operations, exception advice, container error controller, response envelope writers and error catalog (9 codes) | `M/web/{TodoController,GateAttributeArgumentResolver,ApiExceptionHandler,FallbackErrorController}.java`, `M/web/response/{ApiException,ApiResponses,ErrorCode,ErrorDetail,TaskJson}.java` |
| Domain and persistence | Task model and status, repository port, service (write lock taken before the transaction), id generator, SQLite schema initialiser (read-back of journal mode and `synchronous=FULL`) and repository | `M/domain/*.java`, `M/domain/support/*.java`, `M/persistence/{SchemaInitializer,SqliteTaskRepository}.java` |
| Tests | 68 test and test-support Java files: unit, contract (real Tomcat and real SQLite over raw HTTP), durability (in-JVM restart and forced-kill integration test `ForcedKillDurabilityIT`) | `T/**` |
| Docs | Backend README (build, run, configuration, limits, API, known limits) | `backend/README.md` |
| SDLC artifacts | Stage artifacts and workflow record for CAP-2 (13 files incl. this one) | `docs/sdlc/CAP-2/{requirements,architecture,repo-profile,design-review,impl-plan,branch-plan,open-issues,clarifications,approvals,implementation-report,code-review,verification-report,pr-description}.md` |
| Changelog | New file with an `Unreleased` section and one CAP-2 entry | `docs/changelog.md` |

File count (from `git status --short --untracked-files=all`, excluding generated directories): 114 files under `backend/`
(39 main Java, 68 test Java, `application.properties`, and 6 build/doc files), 13 under `docs/sdlc/CAP-2/`, 1 changelog.

### Files excluded from this PR (never staged)
- `.sdlc/` (gitignored workflow state and audit log), `.claude/settings.local.json` (gitignored)
- `backend/target/` (generated; ignored by `backend/.gitignore`; its logs contain test fake-secret strings) and `backend/data/`
- `docs/codemie/analytics/*.json` (4 untracked files that may hold personal data; decision S5-1)
- the uncommitted edit to `.claude/settings.json` (removes one duplicated `Bash(git rev-parse *)` allow line; framework file outside the CAP-2 scope manifest; human decision S8-3: leave uncommitted, excluded from the commit and the PR)
- secrets, `.env*`, key files, editor and OS junk

## Test Evidence
Overall verification status: **PARTIAL** (source: `docs/sdlc/CAP-2/verification-report.md`, round 1, attempt 1; accepted as PARTIAL by the human at gate G6).

Why PARTIAL, quoted from the report: every executed check passed, but AC-35 and NFR-5 (performance) are NOT_RUN by human
decision IMP-6, and the dependency audit, lint, format and static-analysis checks have no command in the repository and none was authorised.

Environment of the run: Apache Maven 3.10.1, Java 25.0.5.1 (Oracle), Windows 11 amd64, `--release 21` target.

| Check | Command (run in `backend/` except where noted) | Exit | Result |
|---|---|---|---|
| V-1 Toolchain | `./mvnw -v` | 0 | PASS: Maven 3.10.1, Java 25.0.5.1 |
| V-2 Branch | `git branch --show-current` | 0 | PASS: `feature/CAP-2-todo-backend-task-apis` |
| V-3 Working tree | `git status --short` | 0 | PASS (recorded: nothing committed or staged) |
| V-4 Build, Surefire suite, package, Failsafe | `./mvnw -B verify` (log redirected to `target/verify-v7.log`; Maven's own exit code `MVN_EXIT=0`) | 0 | PASS: `BUILD SUCCESS`, total time 32.141 s. Surefire: Tests run 1181, Failures 0, Errors 0, Skipped 1. Failsafe `ForcedKillDurabilityIT`: Tests run 1, Failures 0, Errors 0, Skipped 0. JAR built: `target/todo-backend-0.0.1-SNAPSHOT.jar` |

Counts: 4 of 4 executed commands exited 0; 0 FAIL; 10 check categories NOT_RUN (see Known Limitations).
No `<failure>` or `<error>` element was found in any Surefire XML report. Defects found by the verification run: none.

Requirement coverage (verification-report sections 1 and 6):
| Item | PASS | FAIL | NOT_RUN | NOT_APPLICABLE |
|---|---|---|---|---|
| AC (36) | 35 | 0 | 1 (AC-35) | 0 |
| FR (13) | 13 | 0 | 0 | 0 |
| NFR (10) | 8 (NFR-1, 2, 3, 4, 6, 7, 9, 10) | 0 | 1 (NFR-5) | 1 (NFR-8, no target in the requirements) |

Important qualifier carried from the report and the G6 approval: most AC rows rest on a test that was found **by name**
in the Surefire XML of this run (it ran with 0 failures, 0 errors); only a few test bodies were read by the verifier.
NFR-4 / AC-22 durability is proven for a force-killed server process only, not for power loss.

Acceptance criteria (primary evidence per row; all test classes are under `com.capstone.todo`; full method lists are in verification-report section 6):
| AC | Result | Evidence (test class . method, ran in V-4) |
|---|---|---|
| AC-1 | PASS | `contract.CrudContractTest.createReturns201WithTheTrimmedTaskInTheDataEnvelopeAndTheTaskIsListed_ac1_ac19_ac27` |
| AC-2 | PASS | `contract.PrecedenceContractTest.everyFieldRuleGivesValidationErrorWithItsDetailsBeforeTheStoreIsLookedAt_tp6`; `TaskRequestValidatorTest` (98) |
| AC-3 | PASS | `CrudContractTest.aBodyThatIsNotOneJsonObjectIsInvalidRequestBodyOnEveryBodyOperation_ac3_ac17_ac23` |
| AC-4 | PASS | `CrudContractTest.idStatusAndTimestampsInTheBodyAreIgnoredOnCreate_ac4` |
| AC-5 | PASS | `CrudContractTest.theListIsNewestFirstAndAnUpdateDoesNotMoveATask_ac5` |
| AC-6 | PASS | `CrudContractTest.getReturnsTheTaskAndAnUnknownIdIsTodoNotFound_ac6` |
| AC-7 | PASS | `CrudContractTest.putReplacesTitleAndDescriptionAndKeepsStatusAndCreatedAt_ac7` |
| AC-8 | PASS | `CrudContractTest.invalidValuesOnPutAreRejectedWithTheCap1WordingAndTheStoredTaskIsUnchanged_ac8_ac20` |
| AC-9 | PASS | `CrudContractTest.deleteIs204WithNoBodyAndNoContentTypeAndTheTaskIsGone_ac9` |
| AC-10 | PASS | `CrudContractTest.statusMovesBetweenPendingAndCompletedAndASameValueIsStill200WithALaterUpdatedAt_ac10` |
| AC-11 | PASS | `CrudContractTest.aMissingOrWrongStatusIsAValidationErrorOnFieldStatusAndTheTaskIsUnchanged_ac11_ac30` |
| AC-12 | PASS | `ErrorMappingContractTest`, `ErrorCodeTest` (7), `ApiResponsesTest` (11), `ErrorContractTest` (class-level for the unit classes) |
| AC-13 | PASS | `contract.ErrorMappingContractTest.aFailingStoreGivesOneInternalErrorEnvelopeAndOneLogEntryOnEveryOperation_ac13` (body read) |
| AC-14 | PASS | `CrudContractTest.theCreatedTaskIsReallyInTheSqliteFile`; `ForcedKillDurabilityIT` (body read) |
| AC-15 | PASS | `CrudContractTest.anInvalidIdOnEveryIdOperationIsInvalidIdAndNothingChanges_ac6_ac15`; `IdParserTest` (77). Exception: malformed percent sequences, see Known Limitations (G2-6) |
| AC-16 | PASS | `contract.GatewayContractTest.p3EveryOtherMediaTypeShapeIsUnsupportedMediaTypeBeforeTheBodyIsLookedAt_v16`; `BodyGateTest` (105) |
| AC-17 | PASS | `CrudContractTest.aBodyThatIsNotOneJsonObjectIsInvalidRequestBodyOnEveryBodyOperation_ac3_ac17_ac23` |
| AC-18 | PASS | `CrudContractTest.anIdAndCreatedAtInTheBodyOfAPutOrPatchAreIgnoredAndTheStoredOnesStay_ac18` |
| AC-19 | PASS | `CrudContractTest.createReturns201...ac19_ac27`, `anEmptyStoreListsAnEmptyArray_ac5` |
| AC-20 | PASS | `CrudContractTest.everyFailingFieldIsListedTogetherInTheOrderTitleThenDescription_ac20` |
| AC-21 | PASS | `CrudContractTest.anAbsentNullEmptyOrWhitespaceOnlyDescriptionIsAlwaysTheEmptyStringOnEveryRead_ac21` |
| AC-22 | PASS | `durability.InJvmDurabilityTest...ac22`; `durability.ForcedKillDurabilityIT.createdChangedAndDeletedTasksSurviveAForcedKillOfTheServer_ac22` (body read; process force-kill, not power loss) |
| AC-23 | PASS | `CrudContractTest.anEmptyObjectIsAValidationErrorAndNotABodyError_ac23` |
| AC-24 | PASS | `GatewayContractTest.p1aUndefinedPathsAreNotFoundWithTheEnvelope`, `p2ABodyOfExactlyTheLimitPassesAndOneByteMoreIsPayloadTooLarge`; `RouteTableMappingTest` (6) |
| AC-25 | PASS | `PrecedenceContractTest.theEarlierStageWinsOverRealHttpAndNothingChanges(String, Case)[1..21]` |
| AC-26 | PASS | `CrudContractTest.tasksCreatedInTheSameMillisecondAreOrderedByInsertionNewestFirst_d8` |
| AC-27 | PASS | `CrudContractTest.aZeroMillisecondPartIsStillPrintedWithThreeDigits_ac27`, `everySuccessfulPutAndPatchMovesUpdatedAtStrictlyForward..._ac27`; `TaskJsonTest` (4) |
| AC-28 | PASS | `CrudContractTest.theLimitsAreCountedInCodePointsAfterTrimming_ac28`; `PrecedenceContractTest.theLimitsAreCodePointsAfterTrimmingAndTheValuesAtTheLimitPassP6_tp6` (body read) |
| AC-29 | PASS | `CrudContractTest.aPutWithoutADescriptionClearsItAndAPutWithoutATitleIsRejectedAndChangesNothing_ac29` |
| AC-30 | PASS | `CrudContractTest.aMissingOrWrongStatusIsAValidationErrorOnFieldStatusAndTheTaskIsUnchanged_ac11_ac30` |
| AC-31 | PASS | `CrudContractTest.twoTasksWithTheSameTitleAreBothCreatedWithDistinctIds_ac31` |
| AC-32 | PASS | `TransactionWiringContractTest.twoConcurrentUpdatesOfOneTaskEndWithTheLaterWriteAndNeverAMix_ac32` (single JVM) |
| AC-33 | PASS | `CrudContractTest.textIsReturnedVerbatimWithoutHtmlEscapingOrNonAsciiEscaping_ac33`; `BoundaryContractTest...tb1` |
| AC-34 | PASS | `CrudContractTest.everyOperationAnswersTheSameForAnyAcceptHeaderAndAnOriginHeaderAddsNoCorsHeaders_tacc_tcors_thdr`; `GatewayContractTest.noCorsHeaderAppearsOnAPassedRequestEither` |
| **AC-35** | **NOT_RUN (NOT VERIFIED)** | none. Human decision IMP-6: task T-16 not implemented, `-Pperformance` not authorised, no performance or speed test run |
| AC-36 | PASS | `contract.ErrorLogContentContractTest.aRealSqliteFailureWithRequestDataInScopeLogsNoRequestDataNotEvenInTheThrowable_ac36`; `ErrorMappingContractTest...ac13` (body read) |

NFR-5 is NOT_RUN (IMP-6). NFR-8 has no target (NOT_APPLICABLE). All other NFRs PASS on the evidence of the AC rows above (verification-report section 6).

## Known Limitations
Not verified or not run (each with its source):
- **AC-35 / NFR-5 performance is NOT VERIFIED** (p95 at or under 500 ms at about 1,000 tasks). Human decision IMP-6 (`open-issues.md`, 2026-11-22: "do not perform any testing like performance speed"). T-16 (performance suite) was not implemented; the `performance` Maven profile in `backend/pom.xml` is present but unused. Source: verification-report sections 6 and 10; accepted at G6.
- **Overall verification status is PARTIAL**, not PASS (verification-report header and "Basis for PARTIAL"; accepted at G6).
- **No dependency audit, static analysis, lint or format check was run.** No command exists for them in the repository (no CI, no plugin in `pom.xml`) and none was authorised at G4 (`audit`, `lint`, `format` NOT authorised). The resolved dependency tree has therefore not been checked for known vulnerabilities (code-review.md section 5 recommends this as a human decision). Source: verification-report section 10.
- **Nothing was built from scratch.** `clean` was not authorised, so Maven printed `Nothing to compile - all classes are up to date.` for main and test sources in V-4; the existing class files in `backend/target/` were tested. The JAR was rebuilt and repackaged in the run, and the Failsafe test ran against it. Source: verification-report section 3.
- **1 skipped test:** `SchemaInitializerTest.aReadOnlyDirectoryFailsStartupWhereTheFileSystemEnforcesIt`, skipped by a JUnit assumption ("this file system cannot mark a directory read-only"; Windows). The read-only-data-directory startup failure is therefore NOT_RUN on this machine.
- Also NOT_RUN: power-loss and OS-crash durability (only a forced kill of the server process was tested); other JDK versions and other operating systems (only JDK 25.0.5.1 on Windows 11 was run); `./mvnw spring-boot:run`; `mvnw.cmd`; `./mvnw -B test` as a separate command and `-Dtest=<Class>` (the Surefire phase ran inside `verify`). Source: verification-report section 10.
- The code review was static (nothing run by the reviewer); verification evidence is the V-4 run above.

Known behaviour limit, accepted:
- **Tomcat-handled HTML 400 (G2-6).** For six malformed percent sequences in the path (`%zz`, `%FF`, `%`, `a%00b`, `%C0%AF`, `%E2%82`) Tomcat itself answers 400 `text/html` before the application sees the request, so those ids do not get `INVALID_ID` in the JSON envelope. The page carries no server name or version; data is unchanged. Source: architecture decision G2-6 (approved at G2), code-review.md section 3.1, `backend/README.md` lines 110-114, test `PrecedenceContractTest.aMalformedPercentSequenceIsTomcatsOwn400OutsideTheEnvelopeAndNothingChanges_g2_6` (PASS).

Accepted risks (recorded human risk acceptance S6-2, approved at G5; code-review.md section 3.2; none is a waived critical finding):
- **CR-2** `spring-boot-starter-web` is deprecated in Spring Boot 4.1.3 in favour of `spring-boot-starter-webmvc`. It resolves and builds. A later Boot 4.x line could remove it.
- **CR-3** `tomcat-embed-websocket` 11.0.27 comes in with the web starter although the API has no websocket endpoint; the container registers its `WsFilter`. Kept as is (a verification test, `GatewayContractTest.theRealTomcatFilterChainIsRecordedWithAnyFilterTheContainerAddedItself_v9`, passed).
- **CR-4** the Maven wrapper (`backend/.mvn/wrapper/maven-wrapper.properties`) has no `distributionSha256Sum`, so the downloaded Maven 3.10.1 distribution is not integrity-checked on a new machine.

Ratified deviation: **CR-8** (S6-3): T-15 uses a Logback `LogCapture` instead of Boot's `OutputCaptureExtension`, and four planned T-15 files (`HeadersContractTest`, `NegotiationContractTest`, `CorsContractTest`, `LoggingContractTest`) were not created (their cases exist in other classes per the developer's mapping).

Open nits (10, non-blocking, code-review.md round 2 verdict PASS; CR-9..CR-16 stay open by decision S6-1; ids and file references are in code-review.md section 3):
- **CR-9** `WebConfig.java`: an orphaned Javadoc block above `addArgumentResolvers`; `configureContentNegotiation` has none.
- **CR-10** `application.properties` line 1 cites NFR-9 for the loopback default; the right reference is D-16.
- **CR-11** product comments in `ApiGatewayFilter`, `TomcatConfig` and `pom.xml` refer to workflow history (task ids, checkpoint names, run numbers).
- **CR-12** the strict UTF-8 decoder is built twice (`IdParser`, `JsonBodyReader`); the "details only for VALIDATION_ERROR" rule is checked in two classes.
- **CR-13** `TaskService.get` and `load` are the same expression; the client-abort DEBUG log statement is written at four sites.
- **CR-14** `JsonBodyReader` uses only the `FAIL_ON_TRAILING_TOKENS` feature, not also the explicit end-of-input check architecture D-14 names; the deviation is not listed in the implementation report.
- **CR-15** `TaskService` exposes test-only seams (`isWriteLockHeldByCurrentThread()` public, `writeLock()` package-private).
- **CR-16** `TaskServiceTest.aMutationWaitsForTheLockBeforeItTouchesTheStore` uses `Thread.sleep(300)` (timing-based; could pass falsely, not flake).
- **CR-17** possible Spring WARN with a stack trace when the exception advice writes its 500 to an already-gone client. Not observed in the V-4 log (3 `WARN` lines, all deliberate startup-failure tests), but not ruled out and no test covers that path.
- **CR-18** the error controller logs every INTERNAL_ERROR dispatch of a request at DEBUG once the abort attribute is set; the attribute is also set for any `IOException` from the body gate.

Environment, versions and platform notes:
- **Versions are as reported by the human and verified only by the build resolving them:** Spring Boot 4.1.3 (support window to June 2027 is the human's statement, S5-4 / S5-6), `org.xerial:sqlite-jdbc:3.54.1.0` ("latest" per the human, S5-4), Maven 3.10.1, JDK 25.0.5.1 (approvals G4; implementation-report "Version resolution"). No agent verified them independently. Maven Central downloads were accepted at G4.
- **`--release 21` was built on JDK 25.0.5.1.** Class files are major version 65 (asserted by `BuildSanityTest.classesAreCompiledForRelease21`, which passed in V-4). Library support for JDK 25 was unverified until the build ran; only JDK 25 on Windows 11 was tried.
- **SQLite native library:** sqlite-jdbc extracts a JNI native library into the temp directory at startup (architecture R-10; code-review.md section 4.6). On JDK 25 the application must run with `--enable-native-access=ALL-UNNAMED` or the JVM prints a "restricted method" warning (a warning only; the POM passes the flag to Surefire, Failsafe and `spring-boot:run`; `backend/README.md`). Only the Windows x86_64 path was exercised.
- Test logs show Mockito / Byte Buddy "Java agent has been loaded dynamically" warnings from test dependencies (test scope only).
- The API has no authentication and listens on `127.0.0.1:8080` by default; single instance only; local disk only (`backend/README.md`, "Operating limits").
- `docs/sdlc/CAP-2/requirements.md` still shows `Status: DRAFT` in its header although G1 is approved; it was not edited because editing an approved artifact would invalidate G1 (open-issues.md).
- Process deviations (disclosed, open): the developer ran a few read-only or own-file shell commands outside the G4 list (`unzip`, `javap`, `ls`, `find`, `grep`, one `sed -i` on its own test file; open-issues IMP-1, IMP-1b, IMP-1c); during verification two shell attempts were refused by the permission system and re-issued in the authorised form (G6 row).

## Reviewer Checklist
- [ ] Requirements and acceptance criteria are met (see `docs/sdlc/CAP-2/requirements.md`): 35 of 36 AC verified; **AC-35 / NFR-5 NOT VERIFIED (IMP-6)**: decide whether that is acceptable before merge
- [ ] Design matches `docs/sdlc/CAP-2/architecture.md` (design review round 2: APPROVE; deviations IMP-3 property names, IMP-4 token-level surrogate scan, IMP-5 delete via repository boolean were accepted in code review)
- [ ] Tests are meaningful and cover failure paths (1,181 Surefire + 1 Failsafe; note most AC evidence was found by test name, and no mutation run was ever done to prove the tests fail on a broken product)
- [ ] Security-sensitive code paths reviewed: gateway filter and error handling (`M/web/gate/*`, `ApiExceptionHandler`, `FallbackErrorController`), strict id and body parsing (`IdParser`, `JsonBodyReader`), SQL (parameterised in `SqliteTaskRepository`), TRACE handling (`TomcatConfig` `allowTrace=true` is safe only because the gateway always rejects TRACE), logs contain no request data (AC-36)
- [ ] Accepted risks CR-2, CR-3, CR-4 and the Tomcat HTML 400 (G2-6) are acceptable
- [ ] Dependency tree checked for known vulnerabilities (no audit tool was configured or authorised; this is still open)
- [ ] Documentation and changelog updated (`backend/README.md`, `docs/changelog.md`)
- [ ] No unexpected files in the diff: nothing from `.sdlc/`, `backend/target/`, `backend/data/`, `docs/codemie/analytics/*.json`; `.claude/settings.json` not changed by this PR
- [ ] Migration / rollback considered: n/a for deployment (new module, no existing data or schema; no file in the repository is modified or deleted). The SQLite schema is created at first start (`SchemaInitializer`, schema version 1; a data file with another version refuses to start). Rollback is reverting the PR; the local data file `backend/data/todo.db` is never deleted by the application or tests

## Workflow record
| Item | Value (source: `approvals.md`, `.sdlc/state/CAP-2.json`) |
|---|---|
| G1 requirements | approved |
| G2 architecture (after design review round 2: APPROVE, 0 critical, 0 major) | approved |
| G3 implementation plan | approved |
| G4 implementation kickoff (branch, scope, authorised commands, dependencies) | approved |
| G5 code review (round 2 PASS; 0 critical, 0 major, 0 minor open, 10 nits open) | approved |
| G6 verification (PARTIAL accepted) | approved |
| G7 final approval before the PR | **pending** (state `gate_g7: not_reached`); the PR is not created until it is approved |
| Waived critical findings | **0** (`waived_critical: 0`; open critical 0, open major 0) |
| Tasks | 16 of 17 done; T-16 skipped by human decision IMP-6 |
| Jira / GitHub writes | none by this stage |

### Commit strategy (human decision S8-1, `clarifications.md`, 2026-11-22; resolves branch-plan BP-4)
- **One commit**, message `CAP-2: Add Todo backend task management REST APIs`, ending with the attribution trailer.
- Staged by explicit path only: the paths listed in "Changes Made" plus `docs/changelog.md` and `docs/sdlc/CAP-2/*`. Never `git add -A` or `git add .`; the exclusions listed above are never staged.
- This supersedes the 9-commit plan in `docs/sdlc/CAP-2/branch-plan.md` section 2. That file is not edited because it is covered by the G4 hash; `clarifications.md` row S8-1 and this document carry the change.
- The earlier concern (`backend/pom.xml` changed across waves, only its final state exists, so per-wave commits might not build on their own) is moot with a single commit.

### Before the PR can be created (human actions; no agent may push)
- **The remote `suryasnehith-02/claude_capstone` was EMPTY at the last check** (re-verified read-only for this draft: GitHub `list_branches` returned `[]`; earlier `get_file_contents` on `/` returned `409 Git Repository is empty`). It shares no history with local `Main`, so there is no base branch for a PR to target.
- The base branch name is decided as `main` (human decision S8-2, resolves BP-2). The human runs `git push origin Main:main` himself to push the base commit `7e63ced` ("Add Agentic SDLC framework") (the local base branch is `Main` with a capital M; pushing it unchanged would create a remote `Main`, not `main`). Not done yet at the time of this draft. After that the pr-manager re-reads `list_branches` and confirms the base before Stage 8 publish.
- The uncommitted `.claude/settings.json` edit stays uncommitted (human decision S8-3, resolves BP-3); the PR and the commit will not include it. This is no longer an open item.
- Re-checked for this draft (read-only): current branch `feature/CAP-2-todo-backend-task-apis`; `git log --oneline` shows exactly one commit (`7e63ced`); working tree: ` M .claude/settings.json`, untracked `backend/`, `docs/codemie/`, `docs/sdlc/` (and the new `docs/changelog.md`); nothing staged.

## Related artifacts
All under `docs/sdlc/CAP-2/`: [requirements.md](requirements.md) - [architecture.md](architecture.md) - [design-review.md](design-review.md) - [impl-plan.md](impl-plan.md) - [implementation-report.md](implementation-report.md) - [code-review.md](code-review.md) - [verification-report.md](verification-report.md) - [approvals.md](approvals.md) - [open-issues.md](open-issues.md) - [branch-plan.md](branch-plan.md)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
