# Verification report - CAP-2

Overall status: PARTIAL        Round: 1 (attempt 1 of 3)
Branch: feature/CAP-2-todo-backend-task-apis    Inputs: requirements.md, code-review.md, repo-profile.md (not re-read in full; commands re-derived from `backend/pom.xml`, `backend/README.md`, approvals.md G4 row), implementation-report.md (read, its results NOT copied), open-issues.md, approvals.md

Basis for PARTIAL: every executed check passed (4 of 4 commands exit 0; Surefire 1181 run / 0 failures / 0 errors / 1 skipped; Failsafe 1 run / 0 failures), and every NOT_RUN is explained (section 10). It is not PASS because AC-35 and NFR-5 (performance) are NOT_RUN by human decision IMP-6, and the audit, lint, format and static-analysis checks have no command in the repository.

## 1. Summary
| Result | Count |
|---|---|
| PASS (executed commands V-1..V-4) | 4 |
| FAIL | 0 |
| NOT_RUN (check categories, section 10) | 10 |

Commands executed in this run: 4 (V-1 `./mvnw -v`, V-2 `git branch --show-current`, V-3 `git status --short`, V-4 `./mvnw -B verify`). Two further Bash attempts were refused by the permission classifier and did not run (a compound `git ... ; ls target` call, and a compound `mkdir ...; ./mvnw -B verify ...; cat` call run in the background); the same commands were then issued in the authorised form (V-2, V-3, V-4). Nothing was executed from the refused calls.

Requirement coverage (matrix in section 6):
| Item | PASS | FAIL | NOT_RUN | NOT_APPLICABLE |
|---|---|---|---|---|
| AC (36) | 35 | 0 | 1 (AC-35) | 0 |
| FR (13) | 13 | 0 | 0 | 0 |
| NFR (10) | 8 (NFR-1, 2, 3, 4, 6, 7, 9, 10) | 0 | 1 (NFR-5) | 1 (NFR-8, no target) |

Acceptance criteria verified: 35 of 36 (AC-35 NOT_RUN by IMP-6).  Open critical/major review findings: 0/0 (code-review.md round 2: 0 critical, 0 major, 0 minor open, 10 nits open).
Defects found by this run: none (0 FAIL).

## 2. Verification plan
| Check | Purpose | Command (with provenance file:line) | Prerequisites |
|---|---|---|---|
| Toolchain | record Maven, JDK, OS | `./mvnw -v` (G4 row in `approvals.md`; `backend/README.md:23`) | run in `backend/`; JDK 25.0.5.1 |
| Branch / tree | record state | `git branch --show-current`, `git status --short` (G4 row) | none |
| Build + unit + contract tests | compile, Surefire suite | part of `./mvnw -B verify` (`backend/pom.xml:73-78` Surefire config; `backend/README.md:24,26`) | Maven Central reachable (accepted, G4) |
| Package + integration test | executable JAR, Failsafe `ForcedKillDurabilityIT` | `./mvnw -B verify` (`backend/pom.xml:89-99` Failsafe; `backend/README.md:26,35`) | same |
| Performance | AC-35 / NFR-5 | `./mvnw -B verify -Pperformance` (`pom.xml:110`) | NOT AUTHORISED (IMP-6, human decision): NOT_RUN |
| Lint, format, static analysis, dependency audit | quality gates | none defined in the repository, none authorised | NOT_RUN |
| Start from sources | README claim | `./mvnw spring-boot:run` (`README.md:27`) | optional in the delegation; not run |

CI: the repository has no CI definition (Glob `.github/**` found nothing).

## 3. Independent discovery notes
- Commands re-derived from: `backend/pom.xml` (Surefire excludes `**/*IT.java`, Failsafe bound by the Boot parent, profile `performance` at line 110), `backend/README.md:23-28,118-119`, `docs/sdlc/CAP-2/approvals.md` G4 row. No Makefile, no CI file, no package scripts exist.
- Disagreements with implementation-report.md: none in the commands. The developer's last run counts (`./mvnw -B test` 1175 tests, `verify` same plus 1 IT) differ from this run (Surefire 1181): this run executed 6 more Surefire tests than the developer's last quoted count. Not investigated (no authority to diff runs); every test in this run passed, so this is informational. The report itself notes that its older text had older counts.
- Limitation: Maven printed `Nothing to compile - all classes are up to date.` for both main and test sources, so this run did not recompile; it ran the existing class files in `backend/target/`. `clean` is not authorised, so a from-scratch compile was not demonstrated. The JAR was rebuilt and repackaged in this run (`jar:jar`, `spring-boot:repackage`), and the Failsafe IT ran against it.

## 4. Evidence
All Maven output was redirected to `backend/target/verify-v7.log` (read with Read and Grep); Maven's own exit code was captured in `backend/target/verify-v7.exit`, which reads `MVN_EXIT=0`.

### V-1 Toolchain
cmd: `./mvnw -v`
cwd: `C:\Users\NarlaSuryaSnehith\Desktop\claude-capstone-project\backend`
exit: 0
result: PASS
output:
```
Apache Maven 3.10.1 (3afd8084b5629f80b97750906a719879ed4cd283)
Maven home: C:\Users\NarlaSuryaSnehith\.m2\wrapper\dists\apache-maven-3.10.1\987df3ef
Java version: 25.0.5.1, vendor: Oracle Corporation, runtime: C:\Program Files\Java\jdk-25.0.5.1
Default locale: en_IN, platform encoding: UTF-8, time zone: Asia/Calcutta
OS name: "windows 11", version: "10.0", arch: "amd64", family: "windows"
```

### V-2 Branch
cmd: `git branch --show-current`
cwd: `C:\Users\NarlaSuryaSnehith\Desktop\claude-capstone-project\backend`
exit: 0
result: PASS
output:
```
feature/CAP-2-todo-backend-task-apis
```

### V-3 Working tree
cmd: `git status --short`
cwd: `C:\Users\NarlaSuryaSnehith\Desktop\claude-capstone-project\backend`
exit: 0
result: PASS
output:
```
 M ../.claude/settings.json
?? ./
?? ../docs/codemie/
?? ../docs/sdlc/
```
(Nothing committed or staged; matches the delegation. `backend/.gitignore` lines 1-2 are `target/` and `data/`.)

### V-4 Full build, Surefire suite, package, Failsafe integration test
cmd: `./mvnw -B verify > target/verify-v7.log 2>&1; echo "MVN_EXIT=$?" > target/verify-v7.exit`
cwd: `C:\Users\NarlaSuryaSnehith\Desktop\claude-capstone-project\backend`
exit: 0 (Maven's own, from `target/verify-v7.exit`: `MVN_EXIT=0`)
result: PASS (1181 Surefire tests and 1 Failsafe test executed; 0 failures, 0 errors; 1 Surefire test skipped by a JUnit assumption, see section 10)
output (lines from `backend/target/verify-v7.log`, filtered by Grep; line numbers are log lines):
```
22:[INFO] --- surefire:3.5.6:test (default-test) @ todo-backend ---
14:[INFO] Nothing to compile - all classes are up to date.
20:[INFO] Nothing to compile - all classes are up to date.
111:[INFO] Tests run: 40, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.131 s -- in com.capstone.todo.contract.CrudContractTest
680:[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.592 s -- in com.capstone.todo.contract.GatewayContractTest
807:[INFO] Tests run: 59, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.826 s -- in com.capstone.todo.contract.PrecedenceContractTest
915:[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.546 s -- in com.capstone.todo.durability.InJvmDurabilityTest
1456:[WARNING] Tests run: 22, Failures: 0, Errors: 0, Skipped: 1, Time elapsed: 2.181 s -- in com.capstone.todo.persistence.SchemaInitializerTest
3180:[INFO] Results:
3182:[INFO] Tests run: 1181, Failures: 0, Errors: 0, Skipped: 1
3186:[INFO] Building jar: C:\Users\NarlaSuryaSnehith\Desktop\claude-capstone-project\backend\target\todo-backend-0.0.1-SNAPSHOT.jar
3192:[INFO] --- failsafe:3.5.6:integration-test (default) @ todo-backend ---
3198:[INFO] Running com.capstone.todo.durability.ForcedKillDurabilityIT
3199:[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.065 s -- in com.capstone.todo.durability.ForcedKillDurabilityIT
3203:[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
3206:[INFO] --- failsafe:3.5.6:verify (default) @ todo-backend ---
3208:[INFO] BUILD SUCCESS
3210:[INFO] Total time:  32.141 s
3211:[INFO] Finished at: 2026-11-22T10:20:52+05:30
```
Per-class Surefire counts (all `Failures: 0, Errors: 0`): BuildSanityTest 2, ClockConfigTest 2, JsonConfigTest 26, TodoPropertiesTest 21, BoundaryContractTest 1, CrudContractTest 40, ErrorContractTest 1, ErrorLogContentContractTest 1, ErrorMappingContractTest 5, GatewayContractTest 27, GatewayFailureContractTest 4, PrecedenceContractTest 59, RouteTableMappingTest 6, TransactionWiringContractTest 4, ArchitectureRulesTest 7, UuidIdGeneratorTest 1, TaskServiceTest 32, TaskStatusTest 14, TaskTest 5, InJvmDurabilityTest 1, PersistenceStartupTest 6, PersistenceWiringTest 6, SchemaInitializerTest 22 (1 skipped), SqliteTaskRepositoryTest 45, FailingTaskRepositoryTest 2, FixedClockTest 3, InMemoryTaskRepositoryTest 12, LogCaptureTest 2, RawHttpTest 6, ScriptedIdsTest 2, TestDataDirTest 3, TaskRequestValidatorTest 98, UnicodeTextTest 72, ApiExceptionHandlerTest 20, FallbackErrorControllerTest 55, ApiGatewayFilterTest 33, BodyGateTest 105, RouteTableTest 98, GateAttributeArgumentResolverTest 6, PrecedenceTableTest 48, IdParserTest 77, JsonBodyReaderTest 155, TodoRequestPipelineTest 19, ApiResponsesTest 11, ErrorCodeTest 7, TaskJsonTest 4, SourceRulesTest 5. Failsafe: ForcedKillDurabilityIT 1.
Report files read in this delegation: `backend/target/surefire-reports/TEST-*.xml` (grep for `<testcase name=`, `<skipped`, `<failure`, `<error`) and `backend/target/failsafe-reports/TEST-com.capstone.todo.durability.ForcedKillDurabilityIT.xml`. No `<failure>` or `<error>` element was found in any Surefire XML; the only `<skipped>` is `SchemaInitializerTest.aReadOnlyDirectoryFailsStartupWhereTheFileSystemEnforcesIt` ("Assumption failed: this file system cannot mark a directory read-only").

Other log observations (verbatim, `verify-v7.log`):
- Lines 67-70: `WARNING: A Java agent has been loaded dynamically (C:\Users\NarlaSuryaSnehith\.m2\repository\net\bytebuddy\byte-buddy-agent\1.18.17\byte-buddy-agent-1.18.17.jar)` plus three more `WARNING:` lines about dynamic agent loading. Test-time only (IMP-5 / V-27 names a Mockito self-attach warning); it is not a failure.
- Exactly 3 Spring `WARN` lines (log lines 920, 1143, 1273), all `AnnotationConfigApplicationContext : Exception encountered during context initialization`, caused respectively by `SQLITE_NOTADB ... file is not a database file`, `The data file has schema version 5 but this application understands only version 1`, and `Could not bind properties to 'TodoProperties'`. These are the deliberate startup-failure cases of `PersistenceStartupTest` (6 tests, all passed).
- `ERROR` lines come from the forced-failure tests (`FallbackErrorController`, `ApiGatewayFilter`, `ApiExceptionHandler`, `LogCapture`) and the same three startup-failure cases; the suite passed, so they are expected output.
- The test fake-secret strings appear in `verify-v7.log` (2 matches for `SECRET`/`secret`). `backend/target/` is gitignored (`backend/.gitignore:1`); these logs must stay out of the commit.

## 5. Edge and failure scenarios
Evidence column = test class and method found by name in the Surefire or Failsafe XML of this run. "Body read" means I read the test body in this delegation; otherwise only the name (and class-level Javadoc where noted).

| Scenario | Evidence (this run) | Result | Gap |
|---|---|---|---|
| Missing task (404 TODO_NOT_FOUND) on get, put, patch, delete | `CrudContractTest.getReturnsTheTaskAndAnUnknownIdIsTodoNotFound_ac6`, `deletingAnUnknownOrAlreadyDeletedIdIsTodoNotFound_ac9_a5`, `aStatusUpdateForAnUnknownIdIsTodoNotFound_ac11`, `putToAnUnknownIdIsTodoNotFoundButAnInvalidBodyForThatIdIsStillA400_ac8_ac25` | PASS | names only |
| Unknown route, trailing slash, unsupported method, TRACE, HEAD, OPTIONS | `GatewayContractTest.p1aUndefinedPathsAreNotFoundWithTheEnvelope`, `p1bEveryOtherMethodTokenOnADefinedPathIsMethodNotAllowedWithTheRoutesAllow`, `p1bTraceOnADefinedPathIsRejectedInTheEnvelopeAndNeverEchoed_v1`, `p1bTraceAndHeadOnAnUndefinedPathAreNotFound`, `p1bHeadGetsStatusAndHeadersButNoBody_v6`; `RouteTableTest` (98) | PASS | names only |
| Empty, malformed, non-object JSON; BOM; invalid UTF-8; duplicate keys; trailing tokens | `CrudContractTest.aBodyThatIsNotOneJsonObjectIsInvalidRequestBodyOnEveryBodyOperation_ac3_ac17_ac23`, `aBomInvalidUtf8AndDuplicateKeysAreHandledByTheBodyRules`; `PrecedenceContractTest.aBodyThatIsNotOneWellFormedJsonObjectIsInvalidRequestBodyOnEveryBodyOperation_tp5`, `duplicateKeysTheLastValueWinsOnEveryBodyOperation_tp5`, `noBodyAtAllIsInvalidRequestBodyAndNotAMediaTypeError_a11_tp5`; `JsonBodyReaderTest` (155) | PASS | names only |
| Limits 100/101 title, 500/501 description, code points not UTF-16 units, padded with NBSP/U+3000 | `CrudContractTest.theLimitsAreCountedInCodePointsAfterTrimming_ac28`; `PrecedenceContractTest.theLimitsAreCodePointsAfterTrimmingAndTheValuesAtTheLimitPassP6_tp6` (body read: 100 and 500 astral code points, NBSP padding, null and blank description accepted), `theLimitsAreAcceptedAtTheBoundaryAndRejectedOneOver_tp5`; `UnicodeTextTest` (72), `TaskRequestValidatorTest` (98) | PASS | CrudContract version by name only |
| Unicode / markup text verbatim, astral, surrogate escapes, maximal task in 65,536-byte body | `CrudContractTest.textIsReturnedVerbatimWithoutHtmlEscapingOrNonAsciiEscaping_ac33`; `BoundaryContractTest.aMaximalTaskWrittenAsSurrogateEscapesInA65536ByteRequestComesBackAsTheCharactersThemselves_tb1`; `ForcedKillDurabilityIT` creates a task with `caf\u00e9 \ud83d\ude00 <b>&</b>` (body read) | PASS | names only except the IT |
| Oversized body 65,536 / 65,537 (Content-Length, chunked, Expect: 100-continue, understated length) | `GatewayContractTest.p2ABodyOfExactlyTheLimitPassesAndOneByteMoreIsPayloadTooLarge`, `p2AChunkedBodyAtTheLimitPassesAndOneByteMoreIsRejected`, `p2AnOversizedContentLengthWithExpect100ContinueIsRejectedWithoutTheBody_v8`, `p2AnUnderstatedLengthCannotSmuggleMoreThanTheCapIntoTheFilter`; `CrudContractTest.aBodyOfExactlyTheLimitIsAcceptedAndOneByteMoreIsRejected_tb1` | PASS | names only |
| Wrong or missing Content-Type, charset edge cases | `GatewayContractTest.p3EveryOtherMediaTypeShapeIsUnsupportedMediaTypeBeforeTheBodyIsLookedAt_v16`, `p3EveryParameterIsIgnoredSoABogusCharsetIsAccepted_s2_4`, `p3IsSkippedWhenThereIsNoBody`, `anIllegalCharsetNameInAContentTypeNeverBecomesA500OnAnyMethod_foundAtCpB`; `CrudContractTest.aContentTypeCharsetThatIsEmptyBogusOrIllegalNeverBecomesA500OnTheRealController_foundAtCpB` | PASS | names only |
| Error precedence (all stage pairs, 3+ violations) | `PrecedenceContractTest.theEarlierStageWinsOverRealHttpAndNothingChanges(String, Case)[1..21]`, `requestsWithThreeOrMoreViolationsOrNoneBehaveAsDocumentedOverRealHttp(String, Case)[1..24]`, `thePairRowsAreAllTwentyOneAndTheExtraRowsAreAllTwentyFour`, `anInvalidIdIsInvalidIdOnEveryIdOperationWhateverElseIsWrongWithTheRequest_tp4`; `PrecedenceTableTest` (48, unit) | PASS | names only |
| Malformed percent sequences handled by Tomcat (G2-6) | `PrecedenceContractTest.aMalformedPercentSequenceIsTomcatsOwn400OutsideTheEnvelopeAndNothingChanges_g2_6`; `GatewayContractTest.v3PercentSequencesTomcatMayRefuseAreRecordedNotAsserted` (records, does not assert) ; `CrudContractTest.anEncodedSlashAnEncodedBackslashAndADoubleEncodedPercentReachTheIdCheckAndAreInvalidId_v2_v19`; `IdParserTest` (77) | PASS (Tomcat's own HTML 400 for six sequences is the accepted carried item G2-6, G5 note) | names only |
| Concurrency: two updates of one task, last write wins; no waiting for connection timeout | `TransactionWiringContractTest.twoConcurrentUpdatesOfOneTaskEndWithTheLaterWriteAndNeverAMix_ac32`, `noRequestWaitsForTheConnectionTimeoutUnderConcurrentLoad_v21`, `everyMutationHoldsTheWriteLockBeforeItAsksForTheConnectionAndAReadHoldsNone_v21`; `InMemoryTaskRepositoryTest` (8 threads) | PASS | names only; in-process concurrency, one JVM |
| Restart durability (clean context restart; process force-kill) | `InJvmDurabilityTest.createdChangedAndDeletedTasksAreIdenticalAfterTheContextIsClosedAndStartedAgain_ac22`; `ForcedKillDurabilityIT.createdChangedAndDeletedTasksSurviveAForcedKillOfTheServer_ac22` (body read: starts the packaged JAR, creates 3 tasks, PUT, PATCH, DELETE, `kill()`, starts a second JVM on the same file, asserts the list is equal and the deleted id is 404) | PASS | power loss / OS crash NOT_RUN |
| Store failure gives 500 INTERNAL_ERROR, generic message, one ERROR log entry per request | `ErrorMappingContractTest.aFailingStoreGivesOneInternalErrorEnvelopeAndOneLogEntryOnEveryOperation_ac13` (body read: all six operations, `assertEnvelope(INTERNAL_ERROR)`, one ERROR entry, no request data, throwable class and message checked); `GatewayFailureContractTest.aFailureBehindTheGatewayIsOne500EnvelopeAndOneErrorEntryWithoutTheQuery`, `aFailureDuringTheErrorDispatchIsOne500EnvelopeAndOneErrorEntry`; `ErrorContractTest.aContainerErrorForATraceRequestIsTheEnvelopeNeverAnEchoAndAnUnmappedStatusIsLoggedOnce_dr16` | PASS | names only except the first |
| Client disconnects mid-body is not a failure (no ERROR) | `GatewayFailureContractTest.aClientThatDisappearsMidBodyIsNotAFailure` (body read: sends a partial body with Content-Length 1000, closes the socket, asserts a DEBUG entry from the gateway, no ERROR entry, next GET is 200); log lines 737, 2623, 2975 | PASS | see CR-17 below |
| No secrets or request data in logs | `ErrorLogContentContractTest.aRealSqliteFailureWithRequestDataInScopeLogsNoRequestDataNotEvenInTheThrowable_ac36`; the ac13 test above | PASS | names only (first) |
| Corrupt data file, unsupported schema version, bad property at startup refuse to start | `PersistenceStartupTest` (6 tests, all passed; the 3 Spring WARN lines in the log are these cases) | PASS | class-level only, method names not listed |
| Read-only data directory fails startup | `SchemaInitializerTest.aReadOnlyDirectoryFailsStartupWhereTheFileSystemEnforcesIt` | NOT_RUN (skipped by assumption: this file system cannot mark a directory read-only) | gap on this OS |
| No CORS headers, preflight is a plain 405 | `GatewayContractTest.p1bAPreflightLooksLikeAnyOtherOptionsAndCarriesNoCorsHeaders`, `noCorsHeaderAppearsOnAPassedRequestEither`; `CrudContractTest.everyOperationAnswersTheSameForAnyAcceptHeaderAndAnOriginHeaderAddsNoCorsHeaders_tacc_tcors_thdr` | PASS | names only |
| Updated-at when the clock stands still or steps back | `CrudContractTest.everySuccessfulPutAndPatchMovesUpdatedAtStrictlyForwardEvenWhenNothingChangesAndTheClockStandsStill_ac27`, `whenTheClockMovesForwardUpdatedAtFollowsItAndWhenItStepsBackUpdatedAtStillMovesForward_ac27` | PASS | names only |
| Performance with about 1,000 tasks (p95 <= 500 ms) | none | NOT_RUN | IMP-6 human decision |

### Specific checks requested
(a) CR-17 (a Spring WARN with a stack trace when a 500 is written to a gone client): searched `backend/target/verify-v7.log` for `WARN`, `ExceptionHandlerExceptionResolver`, `DefaultHandlerExceptionResolver`, `Failure in @ExceptionHandler`, `ClientAbort`, `Broken pipe`, `Connection reset`, `AbortedException`, `ResponseStatusExceptionResolver`. Result: not observed. The log has 3 `WARN` lines in total, all `AnnotationConfigApplicationContext` startup-failure cases from `PersistenceStartupTest` (not related to a client abort); no Spring resolver logger name appears at all. The `ClientAbortException` lines are DEBUG entries by `ApiGatewayFilter` (lines 737, 2623, 2975) and `FallbackErrorController` (line 2259). Caveat: "not observed" is not "ruled out". I could not determine from the test names that any test writes the 500 through the advice to an already-closed real client socket (the real-socket abort test aborts during the request body and is handled by the gateway at DEBUG; the advice path is exercised at unit level by `ApiExceptionHandlerTest`). CR-17 stays an open nit.
(b) V-26: the JNI `--enable-native-access` warning is not required; not assessed (the POM `argLine` sets it per the developer's report). The log contains no `native` warning, but the log does contain the dynamic-agent WARNING quoted in section 4.

## 6. Acceptance criteria matrix
Result is PASS only because the named test ran in this run (V-4), has `Failures: 0, Errors: 0`, and is present by name in the XML report. "Name only" = I located the method by name in the report but did not read its body. Bodies read in this delegation: `ErrorMappingContractTest.aFailingStore...ac13` (lines 111-143), `PrecedenceContractTest.everyFieldRuleGivesValidationError...tp6` (620-628) and `theLimitsAreCodePoints...tp6` (631-652), `GatewayFailureContractTest.aClientThatDisappearsMidBodyIsNotAFailure`, `ForcedKillDurabilityIT.createdChangedAndDeletedTasksSurviveAForcedKill...ac22`. Every other row rests on the test name (and, for `TaskRequestValidatorTest`, its class Javadoc listing AC-2, 8, 11, 18, 20, 21, 23, 28 to 30).

| AC | Evidence (test class . method, ran in V-4) | Result | Notes |
|---|---|---|---|
| AC-1 | `CrudContractTest.createReturns201WithTheTrimmedTaskInTheDataEnvelopeAndTheTaskIsListed_ac1_ac19_ac27` | PASS | name only |
| AC-2 | `PrecedenceContractTest.everyFieldRuleGivesValidationErrorWithItsDetailsBeforeTheStoreIsLookedAt_tp6` (POST and PUT, body read); `CrudContractTest.theLimitsAreCountedInCodePointsAfterTrimming_ac28`; `TaskRequestValidatorTest` (98) | PASS | "no task stored" for create rests on the helper `rejection(...)` and on `theEarlierStageWins...AndNothingChanges[n]` names; helper body not read |
| AC-3 | `CrudContractTest.aBodyThatIsNotOneJsonObjectIsInvalidRequestBodyOnEveryBodyOperation_ac3_ac17_ac23`; `PrecedenceContractTest.aBodyThatIsNotOneWellFormedJsonObjectIsInvalidRequestBodyOnEveryBodyOperation_tp5` | PASS | name only |
| AC-4 | `CrudContractTest.idStatusAndTimestampsInTheBodyAreIgnoredOnCreate_ac4` | PASS | name only |
| AC-5 | `CrudContractTest.theListIsNewestFirstAndAnUpdateDoesNotMoveATask_ac5`, `anEmptyStoreListsAnEmptyArray_ac5` | PASS | name only |
| AC-6 | `CrudContractTest.getReturnsTheTaskAndAnUnknownIdIsTodoNotFound_ac6`, `anInvalidIdOnEveryIdOperationIsInvalidIdAndNothingChanges_ac6_ac15` | PASS | name only |
| AC-7 | `CrudContractTest.putReplacesTitleAndDescriptionAndKeepsStatusAndCreatedAt_ac7` | PASS | name only; "updatedAt later" also by `...ac27` tests |
| AC-8 | `CrudContractTest.invalidValuesOnPutAreRejectedWithTheCap1WordingAndTheStoredTaskIsUnchanged_ac8_ac20`, `putToAnUnknownIdIsTodoNotFoundButAnInvalidBodyForThatIdIsStillA400_ac8_ac25` | PASS | name only |
| AC-9 | `CrudContractTest.deleteIs204WithNoBodyAndNoContentTypeAndTheTaskIsGone_ac9`, `deletingAnUnknownOrAlreadyDeletedIdIsTodoNotFound_ac9_a5` | PASS | name only |
| AC-10 | `CrudContractTest.statusMovesBetweenPendingAndCompletedAndASameValueIsStill200WithALaterUpdatedAt_ac10` | PASS | name only |
| AC-11 | `CrudContractTest.aMissingOrWrongStatusIsAValidationErrorOnFieldStatusAndTheTaskIsUnchanged_ac11_ac30`, `aStatusUpdateForAnUnknownIdIsTodoNotFound_ac11` | PASS | name only |
| AC-12 | `ErrorMappingContractTest` (uses `assertEnvelope`, body read in part), `ErrorCodeTest` (7), `ApiResponsesTest` (11, golden bytes per the class's own description, not read), `ErrorContractTest.aContainerErrorForATraceRequest...dr16` | PASS | method names of `ErrorCodeTest` and `ApiResponsesTest` not listed; class-level only |
| AC-13 | `ErrorMappingContractTest.aFailingStoreGivesOneInternalErrorEnvelopeAndOneLogEntryOnEveryOperation_ac13` (body read), `GatewayFailureContractTest.aFailureBehindTheGatewayIsOne500EnvelopeAndOneErrorEntryWithoutTheQuery` | PASS | NFR-1 "no stack trace in the response" rests on `assertEnvelope` (helper body not read) |
| AC-14 | `CrudContractTest.theCreatedTaskIsReallyInTheSqliteFile`; `InJvmDurabilityTest...ac22`; `ForcedKillDurabilityIT...ac22` (body read); `SqliteTaskRepositoryTest` (45, class Javadoc cites AC-14, AC-22) | PASS | |
| AC-15 | `CrudContractTest.anInvalidIdOnEveryIdOperationIsInvalidIdAndNothingChanges_ac6_ac15`, `anUppercaseAndAPercentEncodedHexSpellingFindTheSameTask_g2_5f`; `PrecedenceContractTest.anInvalidIdIsInvalidIdOnEveryIdOperationWhateverElseIsWrongWithTheRequest_tp4`, `aValidIdSpelledWithEncodedHexLettersOrInUppercaseFindsTheSameTask_tp4b`; `IdParserTest` (77) | PASS | name only |
| AC-16 | `GatewayContractTest.p3EveryOtherMediaTypeShapeIsUnsupportedMediaTypeBeforeTheBodyIsLookedAt_v16`, `p3EveryParameterIsIgnoredSoABogusCharsetIsAccepted_s2_4`, `aGetOrDeleteBodyIsNeverReadWhateverItsTypeAndSize`; `CrudContractTest.aGetOrDeleteBodyIsNeverReadWhateverItsTypeAndSize_a8`; `BodyGateTest` (105) | PASS | name only |
| AC-17 | `CrudContractTest.aBodyThatIsNotOneJsonObjectIsInvalidRequestBodyOnEveryBodyOperation_ac3_ac17_ac23` | PASS | name only |
| AC-18 | `CrudContractTest.anIdAndCreatedAtInTheBodyOfAPutOrPatchAreIgnoredAndTheStoredOnesStay_ac18`; `TaskRequestValidatorTest` | PASS | name only |
| AC-19 | `CrudContractTest.createReturns201WithTheTrimmedTaskInTheDataEnvelopeAndTheTaskIsListed_ac1_ac19_ac27`, `anEmptyStoreListsAnEmptyArray_ac5` | PASS | name only |
| AC-20 | `CrudContractTest.everyFailingFieldIsListedTogetherInTheOrderTitleThenDescription_ac20`, `detailsExistOnlyOnAValidationError_ac20`, `invalidValuesOnPutAreRejectedWithTheCap1WordingAndTheStoredTaskIsUnchanged_ac8_ac20` | PASS | name only |
| AC-21 | `CrudContractTest.anAbsentNullEmptyOrWhitespaceOnlyDescriptionIsAlwaysTheEmptyStringOnEveryRead_ac21` | PASS | name only |
| AC-22 | `InJvmDurabilityTest.createdChangedAndDeletedTasksAreIdenticalAfterTheContextIsClosedAndStartedAgain_ac22`; `ForcedKillDurabilityIT.createdChangedAndDeletedTasksSurviveAForcedKillOfTheServer_ac22` | PASS | IT body read; force-kill of the process, not power loss |
| AC-23 | `CrudContractTest.anEmptyObjectIsAValidationErrorAndNotABodyError_ac23`, `aBodyThatIsNotOneJsonObject..._ac3_ac17_ac23` | PASS | name only |
| AC-24 | `GatewayContractTest.p1aUndefinedPathsAreNotFoundWithTheEnvelope`, `p1bEveryOtherMethodTokenOnADefinedPathIsMethodNotAllowedWithTheRoutesAllow`, `p2ABodyOfExactlyTheLimitPassesAndOneByteMoreIsPayloadTooLarge`; `RouteTableMappingTest` (6) | PASS | name only |
| AC-25 | `PrecedenceContractTest.theEarlierStageWinsOverRealHttpAndNothingChanges(String, Case)[1..21]`, `putToAnUnknownId..._ac8_ac25` (CrudContractTest) | PASS | name only |
| AC-26 | `CrudContractTest.tasksCreatedInTheSameMillisecondAreOrderedByInsertionNewestFirst_d8`, `theListIsNewestFirstAndAnUpdateDoesNotMoveATask_ac5` | PASS | name only |
| AC-27 | `CrudContractTest.aZeroMillisecondPartIsStillPrintedWithThreeDigits_ac27`, `everySuccessfulPutAndPatchMovesUpdatedAtStrictlyForward..._ac27`, `whenTheClockMovesForward..._ac27`, `createReturns201..._ac27`; `TaskJsonTest` (4) | PASS | name only |
| AC-28 | `CrudContractTest.theLimitsAreCountedInCodePointsAfterTrimming_ac28`; `PrecedenceContractTest.theLimitsAreCodePointsAfterTrimmingAndTheValuesAtTheLimitPassP6_tp6` (body read) | PASS | |
| AC-29 | `CrudContractTest.aPutWithoutADescriptionClearsItAndAPutWithoutATitleIsRejectedAndChangesNothing_ac29` | PASS | name only |
| AC-30 | `CrudContractTest.aMissingOrWrongStatusIsAValidationErrorOnFieldStatusAndTheTaskIsUnchanged_ac11_ac30` | PASS | name only |
| AC-31 | `CrudContractTest.twoTasksWithTheSameTitleAreBothCreatedWithDistinctIds_ac31` | PASS | name only |
| AC-32 | `TransactionWiringContractTest.twoConcurrentUpdatesOfOneTaskEndWithTheLaterWriteAndNeverAMix_ac32` | PASS | name only; single JVM |
| AC-33 | `CrudContractTest.textIsReturnedVerbatimWithoutHtmlEscapingOrNonAsciiEscaping_ac33`; `BoundaryContractTest...tb1` | PASS | name only |
| AC-34 | `CrudContractTest.everyOperationAnswersTheSameForAnyAcceptHeaderAndAnOriginHeaderAddsNoCorsHeaders_tacc_tcors_thdr`; `GatewayContractTest.noCorsHeaderAppearsOnAPassedRequestEither`, `p1bAPreflightLooksLikeAnyOtherOptionsAndCarriesNoCorsHeaders` | PASS | name only |
| AC-35 | none | NOT_RUN | human decision IMP-6: T-16 not implemented, `-Pperformance` not authorised, no performance or speed test run |
| AC-36 | `ErrorLogContentContractTest.aRealSqliteFailureWithRequestDataInScopeLogsNoRequestDataNotEvenInTheThrowable_ac36`; `ErrorMappingContractTest...ac13` (body read) | PASS | first test name only |

FR and NFR:
| ID | Evidence | Result |
|---|---|---|
| FR-1 | AC-1, AC-4, AC-21, AC-27, AC-28, AC-31 tests above | PASS |
| FR-2 | AC-5, AC-26 tests | PASS |
| FR-3 | AC-6, AC-15 tests | PASS |
| FR-4 | AC-7, AC-8, AC-18, AC-29 tests | PASS |
| FR-5 | AC-9 tests | PASS |
| FR-6 | AC-10, AC-11, AC-30 tests | PASS |
| FR-7 | AC-2, AC-3, AC-15, AC-17, AC-20, AC-23, AC-28 tests; `TaskRequestValidatorTest`, `IdParserTest`, `JsonBodyReaderTest` | PASS |
| FR-8 | AC-4, AC-18 tests | PASS |
| FR-9 | AC-12, AC-13, AC-19, AC-20 tests | PASS |
| FR-10 | AC-14, AC-22 tests | PASS |
| FR-11 | AC-24 tests | PASS |
| FR-12 | AC-25 tests (45 parametrised rows plus named tp4..tp7 tests) | PASS |
| FR-13 | AC-16 tests; `PrecedenceContractTest.noBodyAtAllIsInvalidRequestBodyAndNotAMediaTypeError_a11_tp5` | PASS |
| NFR-1 | `ErrorMappingContractTest...ac13`, `FallbackErrorControllerTest` (55), `ApiExceptionHandlerTest` (20) | PASS (response content rests on `assertEnvelope`, not read) |
| NFR-2 | AC-12, AC-19 tests | PASS |
| NFR-3 | AC-14, AC-32 tests; rejected requests change nothing (`...AndNothingChanges[n]`) | PASS |
| NFR-4 | AC-22 tests | PASS (process force-kill only) |
| NFR-5 | none | NOT_RUN (IMP-6) |
| NFR-6 | AC-24 tests; `BoundaryContractTest...tb1` (maximal valid task is not rejected at 65,536 bytes) | PASS |
| NFR-7 | AC-36 tests | PASS |
| NFR-8 | no target in requirements.md | NOT_APPLICABLE |
| NFR-9 | AC-34 tests | PASS |
| NFR-10 | AC-33 tests | PASS |

Regenerated evidence files: the delegation also named the `cp-*.txt` evidence files as optional evidence. I did not rely on them: `backend/target/*evidence*.txt` carry no run timestamp I could tie to this run (only `t11-schema-evidence.txt` contains a 2026-11-2x timestamp), so no AC result above cites them.

## 7. Code review findings status
code-review.md round 2: PASS, 0 critical, 0 major, 0 minor open, 10 nits open (CR-9..CR-18). All severities are as stated in `code-review.md`; the review was static.

| Finding | Severity | Status per evidence (resolved / open / unknown) | Evidence |
|---|---|---|---|
| CR-1 (client-abort classification, logged at ERROR) | minor | resolved per code-review round 2; this run supports it: `GatewayFailureContractTest.aClientThatDisappearsMidBodyIsNotAFailure` passed (body read) | V-4 |
| CR-2 (deprecated `spring-boot-starter-web`) | minor | open, accepted risk (G5, S6-2); not changeable by this stage | code-review.md, approvals.md G5 |
| CR-3 (`tomcat-embed-websocket` on class path) | minor | open, accepted risk (G5, S6-2); `GatewayContractTest.theRealTomcatFilterChainIsRecordedWithAnyFilterTheContainerAddedItself_v9` passed | V-4 |
| CR-4 (wrapper has no distribution checksum) | minor | open, accepted risk (G5, S6-2); the wrapper downloaded or reused Maven 3.10.1 in V-1 without a checksum check | V-1 |
| CR-5 (`dataSourceScriptDatabaseInitializer` still created) | minor | unknown by execution (static finding, resolved per code-review round 2); no test in this run targets it | not determinable here |
| CR-6 (stale "UNVERIFIED" comments in pom.xml) | minor | unknown by execution (documentation finding, resolved per code-review round 2); the build ran | not determinable here |
| CR-7 (AC-36 asserted only on the formatted message) | minor | resolved per code-review round 2; this run supports it: `ErrorLogContentContractTest...ac36` passed (name only) | V-4 |
| CR-8 (T-15 deviations) | minor | ratified by the human (G5, S6-3) | approvals.md |
| CR-9..CR-16 | nit | open by decision S6-1 (static; not exercised) | code-review.md |
| CR-17 (possible Spring WARN on the advice abort path) | nit | open; "not observed" in this run (section 5 (a)); not ruled out | V-4 log |
| CR-18 (error controller logs INTERNAL_ERROR dispatches at DEBUG after a client abort) | nit | open; not exercised separately | code-review.md |

Open critical/major: 0/0.

## 8. Documentation validation
| Document | Check | Result | Evidence |
|---|---|---|---|
| `backend/README.md:23` `./mvnw -v` | command runs as written | PASS | V-1 |
| `backend/README.md:26` `./mvnw -B verify` | command runs as written; "executable JAR is `target/todo-backend-0.0.1-SNAPSHOT.jar`" (line 35) | PASS | V-4 (`Building jar: ...\target\todo-backend-0.0.1-SNAPSHOT.jar`) |
| `backend/README.md:24` `./mvnw -B test` | not executed separately (the same Surefire phase ran inside `verify`) | NOT_RUN | section 10 |
| `backend/README.md:25` `./mvnw -B test -Dtest=<ClassName>` | not executed (not in the delegation's authorised list) | NOT_RUN | section 10 |
| `backend/README.md:27` `./mvnw spring-boot:run` | not executed (optional) | NOT_RUN | README itself says "unverified (never run)" |
| `backend/README.md:28` `mvnw.cmd ...` | not executed (Windows `cmd`) | NOT_RUN | README says "unverified" |
| Links and referenced paths inside README beyond the JAR path | not checked | NOT_RUN | scope kept to the commands |

## 9. Failures and reproduction
| Check | What failed | Steps to reproduce | Suspected area |
|---|---|---|---|
| none | No command failed and no test failed or errored. | n/a | n/a |

Observation, not a failure: Surefire's skipped test (read-only directory) and the byte-buddy dynamic-agent WARNING (section 4).

## 10. Not run
| Check | Reason (tool missing / denied / no command defined / environment) |
|---|---|
| Performance and speed test (AC-35, NFR-5): `./mvnw -B verify -Pperformance` | Human decision IMP-6 ("do not perform any testing like performance speed"); T-16 not implemented; not authorised in this delegation. AC-35 and NFR-5 are NOT_RUN, not passed. |
| Dependency vulnerability audit | no command in the repository (no CI, no plugin in `pom.xml`) and none in the G4 list ("audit" NOT authorised) |
| Static analysis | no command in the repository and none authorised |
| Lint | no command in the repository; "lint" NOT authorised in G4 |
| Format check | no command in the repository; "format" NOT authorised in G4 |
| Power-loss / OS-crash durability | cannot be executed with the repository's tests; `ForcedKillDurabilityIT` covers a force-killed server process only |
| Other JDK versions and other operating systems | only JDK 25.0.5.1 on Windows 11 was available and run (the build targets `--release 21`, class files are major version 65 per the developer's `BuildSanityTest`, whose pass is part of V-4) |
| `./mvnw spring-boot:run` | optional in the delegation; not run (README: "unverified") |
| `./mvnw -B test` as a separate command, `./mvnw -B test -Dtest=<Class>`, `mvnw.cmd` | the Surefire phase already ran inside `./mvnw -B verify` (not repeated); `-Dtest` and `mvnw.cmd` are outside the delegation's command list |
| `SchemaInitializerTest.aReadOnlyDirectoryFailsStartupWhereTheFileSystemEnforcesIt` | skipped by a JUnit assumption: "this file system cannot mark a directory read-only" (environment: Windows) |

Housekeeping: the `backend/target/` logs (including `verify-v7.log`, `verify-v7.exit` created by this run) contain test fake-secret strings and must stay out of the commit; `target/` is covered by `backend/.gitignore:1`. Scratch directory `docs/sdlc/CAP-2/verification-artifacts/` was not needed and not created.
