# Design review - CAP-1

Verdict: APPROVE        Review round: 2 (round 1 was REVISE)
Reviewed: requirements.md (G1 approved), architecture.md (attempt 3, FINAL, with section 16 "Review responses"), repo-profile.md, clarifications.md (H-1..H-3, S2-1..S2-3), approvals.md context, `backend/README.md`, `backend/src/main/java/**` (read-only)

Convention: statements about JDK, browser or HTTP behaviour that the repository cannot show are marked **UNVERIFIED**.
Nothing in this review asks for security, speed or performance testing (H-2).
Round 2 method: I did not rely on section 16. For each finding I read the sections it cites and the sections they
interact with (7.2, 7.3, 8, 8.1, 8.2, 8.4, 10.1, 13, 15), then looked for new problems the revision introduced.

## 1. Verdict summary
All eleven round-1 findings are resolved in the design body, not only in section 16. The three majors are closed:
- DR-1: only `initial` and `retry` loads clear the message; a `refresh` leaves the message area untouched, on success
  and on failure (8.1 steps 3-4, 7.2 table, 8.4 steps 1 and 3). The announcer is not called by a refresh, so no deferred
  write is lost either.
- DR-2: `loadGen` plus `dirtyDuringLoad` and apply-by-id close the stale-list races I could construct (section 5, DR-2).
- DR-3: field-error text is also written to `#msg-alert` (7.2, 8.2, 8.1 Add step 2 and Edit step 3), and `AC-5`
  ("shown next to the field") is still met by the `<p>` under the field.

I found no critical or major issue in the revision. Three new, small points came out of the second look: one minor
(a re-entry rule of the dialog that can turn into a second DELETE) and two suggestions. None blocks Stage 4.
Counts (open): critical 0, major 0, minor 1, suggestions 2. Closed from round 1: major 3, minor 4, suggestion 4.
G2-1, G2-2 and G2-3 are still consistent with S2-1..S2-3 (section 8).

## 2. Requirement coverage
| Requirement | Addressed in architecture.md | Assessment | Finding |
|---|---|---|---|
| FR-1 | 8.1 Load, C-5/C-8/C-9, no re-sort | covered | - |
| FR-2 | 8.1 Load step 3/4, `#list-status` | covered | - |
| FR-3 | 6.2 add form, 8.1 Add | covered | - |
| FR-4 | 6.2 edit mode, 8.1 Edit, D-4 keyed reconcile | covered | DR-4 closed |
| FR-5 | D-5, C-11, 8.1 Delete, 8.2 | covered | DR-11 closed; DR-12 |
| FR-6 | 6.2 badge + strike-through, 8.1 Status | covered | - |
| FR-7 | D-6, C-6, 6.2 (no `maxlength`) | covered | DR-9 closed |
| FR-8 | 7.2 table, C-10 | covered | DR-1 closed |
| FR-9 | 7.2 classifier and table | covered | - |
| FR-10 | 8.1 "Ordering of mutation results", `loadGen`, `dirtyDuringLoad` | covered | DR-2 closed |
| FR-11 | `pending` set, 8.1 guard | covered | DR-12 (edge) |
| NFR-1 | C-1, 7.3, D-1 | covered | DR-5 closed |
| NFR-2 | 8.3 | covered (functional only; H-2) | - |
| NFR-3 | 8.2, C-3, C-11, 8.1 focus rules | covered | DR-3, DR-4 closed |
| NFR-4 | section 10, R-5, 10.2(3) | covered (human-reported browsers) | - |
| NFR-5 | section 10 | covered | - |
| NFR-6 | D-7 (timer armed through `text()`), C-1 timeouts | covered (10 s timing NOT_RUN by H-2) | DR-10 closed |
| NFR-7 | section 10, nothing designed | covered (NOT_RUN by H-2) | - |
| AC-1 | 8.1 Load | covered | - |
| AC-2 | 6.2 order (form before list), empty state | covered | - |
| AC-3 | 8.1 Load failure + Retry, I-2 502 | covered | DR-13 (narrow) |
| AC-4 | 8.1 Add, ordering rule, test A case (c) | covered | DR-2 closed |
| AC-5 | C-6, 7.2 field `<p>` plus alert text | covered | DR-3 closed |
| AC-6 | C-6, no `maxlength` | covered | DR-3 closed |
| AC-7 | 8.1 Edit | covered | - |
| AC-8 | 8.1 Edit Cancel/Escape, no request | covered | - |
| AC-9 | 8.1 Delete, C-11 | covered | DR-12 (edge) |
| AC-10 | 8.1 Status | covered | - |
| AC-11 | 7.2 note, 8.1 failure branches, focus rule | covered | DR-4 closed |
| AC-12 | 8.4, 8.1 reason `refresh`, A cases (a), (b) | covered | DR-1 closed |
| AC-13 | 8.1 synchronous guard | covered (see DR-12 for the dialog re-entry edge) | DR-12 |
| AC-14 | C-10 with injected `defer`, 8.2, field errors in `#msg-alert` | covered | DR-3, DR-10 closed; DR-13 |
| AC-15 | 7.2 table, validation row | covered | - |
| AC-16 | D-6, named vectors | covered | DR-9 closed |
| AC-17 | C-6, 7.1 trimmed body | covered | - |
| AC-18 | 8.1 Load failure, no empty state | covered | - |
| AC-19 | 8.1 per-task pending | covered | - |
| AC-20 | 7.2 steps 2/3/5, C-1 `text/html` pass-through (B uses a stub) | covered | DR-5 closed |
| AC-21 | 8.3 | covered | - |
| AC-22 | 8.4 orphan panel, message kept, keyed edit row | covered | DR-1, DR-4 closed; DR-13 |
| AC-23 | C-1, C-14, D checklist; B (stub, partial) + D (real backend) | covered | - |

Section 13 maps 11 of 11 FR, 7 of 7 NFR and 23 of 23 AC. I re-checked AC-5, AC-12, AC-14, AC-15, AC-22 and AC-23
against `requirements.md` (rows at lines 88, 95, 97, 103, 110, 111): the mapping is accurate. AC-23 evidence is still
only partly agent-checkable (B is a stub, D is human-run), and the table says so.

## 3. Repository evidence check
Round 1 checked 17 claims. All the true ones stay true; the changed ones are below. Round 2 re-checked:
| Claim in architecture.md | Verified how (path read) | Result | Finding |
|---|---|---|---|
| 404 code is `TODO_NOT_FOUND`; id errors are `INVALID_ID` | `web/response/ErrorCode.java:10-11`, `web/ApiExceptionHandler.java:58` | true | - |
| AC-5 text "shown next to the field", AC-12 "list is refreshed", AC-22 "typed text remains visible together with the message" | `requirements.md:88, 95, 110` | true; the design satisfies each | - |
| 404 on list/add is "unexpected" generic | `requirements.md:156`; 7.2 table (add: generic) | true | - |
| Lone-surrogate rejection reaches the UI as the generic message | `JsonBodyReader.java:85-98` (round 1); 7.2 step 4 | true | DR-9 closed |
| S2-2 entries, S2-3 labelling | `clarifications.md:24-25` | design matches | - |
| Agent paths allow `frontend/` | `.claude/hooks/agent-paths.conf:15` (round 1) | true | - |
| Malformed percent sequence handling by the JDK server | not in repo | now worded as UNVERIFIED and "not promised" (7.3, 10.1 "Not run"); acceptable | DR-5 closed |
| Catch order `HttpConnectTimeoutException` before `HttpTimeoutException` before `IOException`; `HttpTimeoutException` is an `IOException` | JDK API, not in repo | **UNVERIFIED**, but the order in 7.3 is correct on that API (subclasses first) | - |
| `Path.resolve("/x")` on Windows gives a drive-root path | JDK behaviour, not in repo | **UNVERIFIED**; the design now strips the leading `/`, which is safe either way | - |
| `<dialog>` `close` event fires after Escape (`cancel` then `close`), `returnValue` handling | browser behaviour | **UNVERIFIED**; the design's use of `close` is the standard model and R-5 covers the oldest Safari | - |

## 4. Category results
| Category | Result | Findings |
|---|---|---|
| Security | No findings within H-2 scope. `textContent` only, loopback bind, containment in the web root, all stated as functional rules. No security testing designed or claimed | - |
| Scalability | No findings (one local user) | - |
| Reliability and failure handling | Good. Stale list responses, the 404 message, proxy exception mapping and the `finally`-close are all specified now. Residual edge: dialog re-entry | DR-12 |
| Maintainability | Good: one-way module graph, pure logic apart from the DOM, one mapping table | - |
| Internal consistency | The round-1 contradiction (7.2 "list ok clears any message" vs 8.4) is gone: the 7.2 table, 8.1 step 3 and 8.4 all say `refresh` keeps the message. 8.1, 8.4, section 10, R-6 and the state diagram agree on the ordering rule. Two small gaps remain | DR-13, DR-14 |
| Fit with existing system and conventions | Good; `backend/` untouched | - |
| Technology choices and dependencies | Good: JDK only, no new dependency, decided by the human (S2-1) | - |
| Testability | Good. `defer` is now injected, `fetch` can hold responses, named cases (a)-(g) cover the fixes. Test B has a complete robustness list. One small spec hole in cancelling a deferred write when `defer` is injected | DR-13 |
| Operability | Adequate; start-up line, one stderr line per failure, favicon noise removed, rollback = delete `frontend/` | - |
| Over-engineering / complexity | Acceptable. The revision adds a generation counter, a dirty flag and a keyed reconcile; each maps to a finding or AC, and the rules are short | - |
| Dependency and licensing risk | None | - |

## 5. Findings
Round 1 findings keep their numbers. The column "Round 2 result" is new.

| ID | Severity | Location | Round 1 description (short) | Round 2 result |
|---|---|---|---|---|
| DR-1 | major | 7.2, 8.1, 8.4 | The refresh triggered by a 404 cleared "This task no longer exists" on success and replaced it on failure | **Closed.** 8.1 step 3 ("`initial` and `retry` clear it; `refresh` leaves it unchanged") and step 4 (a failed `refresh` leaves the message area unchanged; generic text and Retry only in `#list-status`) both exist. 8.4 steps 1 and 3 repeat it, the 7.2 table cells for list agree, and the state diagram shows "404 refresh (message kept)". A `refresh` never calls the announcer, so the 50 ms deferred write of the 404 message is not cancelled by it. Tests (a) and (b) in 10.1 cover success and failure. Checked the other callers of `refresh`: the ordering-rule refresh keeps whatever success message is showing, which is correct. A user-driven Retry after a failed refresh clears the message (reason `retry`), which is a deliberate user action and acceptable |
| DR-2 | major | 8.1, section 8, R-6 | No rule ordered concurrent responses | **Closed.** I tried these sequences against 8.1: (1) refresh in flight, add/edit/status/delete succeeds, list arrives: `dirtyDuringLoad` is true, the snapshot is not applied, one more refresh runs. (2) Two overlapping loads: the older response is dropped by `myGen !== loadGen`, whatever its outcome (failure included). A newer load resets `dirtyDuringLoad`, which is correct because the newer request was sent after every mutation that has already completed. (3) Mutation response after the list was applied: applied by id (add unshifts or replaces, edit/status replace or refresh if absent, delete removes if present). (4) Retry during a pending load: Retry is hidden and disabled while loading (step 1), so it cannot overlap. (5) Delete or edit completing during a load: covered by (1). (6) Mutation on a task that a stale snapshot includes or excludes: covered by apply-by-id. (7) Repeated mutations cannot starve the refresh in practice: each extra refresh needs a new completed mutation. Test (c) covers case (1) and also asserts the second `GET /todos`. R-6 and the state diagram match the text. Two cosmetic edges are recorded as DR-14 |
| DR-3 | major | 7.2, 8.1 Add step 2, 8.2 | Field errors announced only through focus and `aria-describedby` | **Closed.** 7.2 (note under the table) and 8.2 write the field messages, in field order, plus "Please check your input and try again." when another field is named, into `#msg-alert` through C-10; `aria-invalid` and `aria-describedby` stay. AC-5 and AC-15 still hold because the text is also beside the field. The alert is deferred, so it follows the focus move rather than competing with it; a repeat of the same text is announced again because the regions are emptied first. Test case (d) asserts the alert text and focus. Possible double reading (alert plus `aria-describedby` on the focused field) is an accepted trade-off, and screen-reader behaviour is **UNVERIFIED** and covered by checklist D as best effort |
| DR-4 | minor | 8.1 flows, D-4 | Focus after failure, draft sync, edit-row DOM lost on refresh | **Closed.** Per-flow focus rules exist (Add 7, Edit 6, Status 3, Delete 5) and the shared "Focus after failure" rule says focus is set after re-enabling. Drafts update on `input` (state block, Edit step 1). D-4 and C-9 specify a keyed reconcile that never rebuilds the edit-mode row and restores focus and selection if a node move blurs it. Test (f) covers it. The keyed reconcile does not conflict with the pending-state rules: pending rows keep their disabled state because C-9 derives it from `pending` |
| DR-5 | minor | 7.3 | Malformed-percent claim; catch order; unmapped exceptions | **Closed.** The claim is now "may be rejected by the JDK server itself (**UNVERIFIED**) ... not promised". The catch list has the correct order, maps `InterruptedException` (flag restored) and `IllegalArgumentException` to 502, adds a catch-all `RuntimeException`, sends an error only if headers are not yet sent, and always calls `exchange.close()` in `finally` |
| DR-6 | minor | 7.3 Static files, I-1 | Static path resolution underspecified for Windows | **Closed.** `getPath()` (no `+` decoding), leading `/` stripped, `InvalidPathException` gives 404, regular files only, directory without `/` gives 301, directory with `/` serves `index.html` if regular. B cases for `/js/main.js` and `/tests` are in 10.1. Residual: a path such as `//js/main.js` leaves one leading `/` after the strip and will probably give 404, which is an acceptable answer. Not a finding |
| DR-7 | minor | C-13, 10.1 row B, R-11 | Test B robustness; R-11 likelihood | **Closed.** Rules (1)-(7) cover directory discovery (cwd, else `./frontend`, else exit 2), free ports, `java.home` launcher, explicit working directory, output drain, 60 s readiness on the start-up line, per-request and overall timeouts, and destroy in `finally`. R-11 is now "high". The permitted command form (`java server/FrontendServerTest.java` from `frontend/`) needs no arguments, so the discovery rule works with it. Note for Stage 5: the free-port probe (`new ServerSocket(0)`, then close, then start the child) has a small window in which another process can take the port; the 60 s readiness wait turns that into a FAIL, not a hang |
| DR-8 | suggestion | C-2, C-12, C-14 | Favicon console noise | **Closed.** `<link rel="icon" href="data:,">` in both pages; checklist note present |
| DR-9 | suggestion | D-6, 10.1 row A, 7.2 step 4 | Differing vectors; lone surrogate | **Closed.** The six named vectors are in 10.1 row A and D-6; lone-surrogate behaviour is in 7.2 step 4 and section 3 |
| DR-10 | suggestion | C-10, I-9, D-7 | `requestAnimationFrame` not injected; abort timer | **Closed with one residual (DR-13).** I-9 has `defer` (default `setTimeout(fn, 50)`, which still fires in a background tab; browser timer clamping of about 1 s there is **UNVERIFIED** and harmless). The abort timer is cleared in `finally` after `text()` settles, and test A covers a timeout during `text()` |
| DR-11 | suggestion | C-11, I-7, 8.2 | Dialog double resolution and re-entry | **Closed, with a new edge (DR-12).** One resolution, in the `close` handler, from `returnValue === 'confirm'`; `cancel` is not prevented and only leads to a decline; initial focus is on Cancel; Tab is trapped; test (g). The re-entry rule itself is the source of DR-12 |
| DR-12 | minor | 8.1 Delete step 1, 8.2 Dialog "Re-entry", I-7 | New. If `confirmDelete` is called a second time while the dialog is open, the design returns the same pending promise. Both callers then resolve to `true` together. The guard in Delete step 1 is checked only before `await confirm(title)`, and `pending` is set after it, so both continuations mark the task pending and send `DELETE`. The second request gets 404 and the user sees "This task no longer exists" right after "Task deleted". The second caller may also pass a different title than the one in the dialog, and would delete a task the user never confirmed | A second call is not reachable through the UI, because the modal dialog makes the page inert and the first call runs `showModal()` synchronously inside the click handler. It is reachable by a test or by later code. If it happens it breaks AC-13 (one request per action) and, with a different id, FR-5 (delete only after confirming that task) | Re-check the guard after the `await` (if `pending` already has the id, or the id is no longer in `tasks`, return without sending). Optionally have C-8 keep a `confirming` flag and ignore a Delete click while it is set, and have `confirmDelete` return the pending promise only for the same task id, otherwise resolve `false` at once. Add one test-A assertion to case (g): two `confirmDelete` calls while open send one `DELETE` |
| DR-13 | suggestion | C-10, I-9, 8.2 `announce`, 8.1 Load step 4 | New. (a) 8.2 says "a newer `announce` cancels a deferred write that has not run yet", but I-9 gives `defer` the shape `fn => setTimeout(fn, 50)`, which returns a handle that the design does not use, and test A injects a queue. The cancel mechanism is not stated. If it relied on `clearTimeout(handle)`, the injected queue would not cancel and an old message could overwrite a newer one in test A (or the reverse). (b) If a load that started as `initial` is turned into a `refresh` by the dirty rule (8.1 Load step 3) and then fails, step 4 does not announce the generic message in the alert region, because the reason is now `refresh`. `#list-status` is a plain `<p>` in 6.2, so a screen-reader user is not told | (a) Flaky or misleading tests, and a possible stale message in a real tab. (b) A narrow case (an add succeeds during the first page load, and then the refresh fails) in which the load failure is silent to assistive technology (AC-3, AC-14) | (a) State that `announce` bumps a sequence number and the deferred callback writes only if its number is still current; this works with any `defer`. (b) Carry the original reason through the dirty refresh (an `initial` or `retry` load that re-runs keeps its reason for the failure announcement and for clearing the message), or give `#list-status` `role="status"` for the error text |
| DR-14 | suggestion | 8.1 Load step 3 (orphan bullet), 8.4 steps 2 and 4, 8.1 Delete step 4 | New, cosmetic edges. (a) If a refresh finds that the task in the open edit form has gone, the draft goes to the orphan panel "without a new message", so a task disappears with no explanation (only reachable when someone else deletes it during the edit). If a save was in flight at that moment, the later 404 repeats the panel step; harmless but untested. (b) 8.4 step 4 moves focus to "the orphan panel heading", but 6.2 does not say the heading has `tabindex="-1"` (only `#list-heading` has). (c) Delete step 4 does not say what to do when the row is already gone because a refresh removed it first | (a) The user sees a panel titled "Your unsaved text" with no reason. (b) `focus()` on a non-focusable heading does nothing, so focus stays on a disabled or removed control. (c) A small focus gap | (a) Announce "This task no longer exists" when the draft is orphaned by a refresh and no message is currently showing. (b) Add `tabindex="-1"` to the orphan heading in 6.2. (c) Say that if the row is absent, focus goes to `#list-heading` |

## 6. Recommendations and disposition
| Finding | Recommendation | Disposition | Reason | Reviewer agrees? |
|---|---|---|---|---|
| DR-1 | Keep the 404 message through the refresh it triggers; on refresh failure keep it and show the generic text only in `#list-status`; add tests | accepted | Implemented in 8.1 steps 3-4 by `load(reason)`; also in 7.2 table, 8.4, state diagram, tests (a) and (b). I checked the success path, the failure path and the other `refresh` callers | Yes. Round 1 finding closed |
| DR-2 | Load generation counter, then re-apply or refresh again for mutations that complete during a load; update R-6; add a test | accepted | `loadGen` and `dirtyDuringLoad` with apply-by-id; R-6 rewritten; test (c). I found no remaining race among overlapping loads, retry during a load, or delete/edit/status completing during a load | Yes. Round 1 finding closed |
| DR-3 | Also write field-error text to `#msg-alert`; add a test | accepted | 7.2, 8.2, 8.1 Add step 2 and Edit step 3; test (d); AC-5 still satisfied by the text beside the field | Yes. Round 1 finding closed |
| DR-4 | Focus-after-failure rule per flow; draft synced on `input`; keep the edit row's DOM during refresh | accepted | Per-flow steps, shared rule, state block, keyed reconcile in D-4 and C-9; tests (e), (f). Small edges in DR-14(b),(c) | Yes. Round 1 finding closed |
| DR-5 | Correct the malformed-percent claim; catch order; map `IllegalArgumentException` and `InterruptedException`; close the exchange in `finally` | accepted | 7.3 "URI" and "Failures" bullets, I-2, I-3, R-9, 10.1 "Not run" | Yes. Round 1 finding closed |
| DR-6 | Strip the leading slash, decode safely, catch `InvalidPathException`, serve regular files only, redirect a directory without a slash; two functional B cases | accepted | 7.3 "Static files", I-1, C-1, 10.1 row B | Yes. Round 1 finding closed |
| DR-7 | Test B: readiness wait, `java.home`, explicit working directory, drain, destroy, timeout; independent of the working directory; set R-11 to high | accepted | 10.1 row B rules (1)-(7), C-13, 6.1 note, R-11 high | Yes. Round 1 finding closed |
| DR-8 | Favicon `data:` link, or a note in the checklist | accepted | C-2, C-12, C-14, 10.1 row D | Yes. Round 1 finding closed |
| DR-9 | Name the differing test vectors; note the lone-surrogate behaviour | accepted | D-6, 10.1 row A, 7.2 step 4, section 3, R-3 | Yes. Round 1 finding closed |
| DR-10 | Inject C-10's deferral function; keep the abort timer armed through `text()` | accepted | I-9, C-10, D-7, 7.2 intro; the cancel mechanism needs one sentence (DR-13a) | Yes. Round 1 finding closed; DR-13 is a new, smaller point |
| DR-11 | Resolve the dialog once on `close`; guard against re-entry | accepted | C-11, I-7, 8.2 "Dialog", test (g). The re-entry choice has one downstream effect (DR-12) | Yes. Round 1 finding closed; DR-12 is new |
| DR-12 | Re-check the guard after `await confirm`; same-task-only re-entry; one test | pending | New in round 2. Minor, not blocking | - |
| DR-13 | Sequence number for deferred writes; carry the load reason through the dirty refresh or make `#list-status` a status region | pending | New in round 2. Suggestion, not blocking | - |
| DR-14 | Announce the orphan from a refresh; `tabindex="-1"` on the orphan heading; focus fallback when the row is gone | pending | New in round 2. Suggestion, not blocking | - |

The architect rejected nothing in round 1, so there is no rejection to agree or disagree with. DR-12..DR-14 can be
handled by the developer or the implementation planner as plan notes (they are local to C-8, C-10 and C-9, and
change no interface), or fixed by one more small revision at the Orchestrator's choice. They do not change the verdict.

## 7. Open questions for the human
| # | Question | Why it matters |
|---|---|---|
| Q-1 (optional; not blocking; status unchanged) | Should the human also add a repository-root form, `Bash(java frontend/server/FrontendServerTest.java)`, to his own `.claude/settings.local.json`? Suggested default: no. S2-2 stays as decided | The architect's revision made test B independent of the working directory (10.1 row B rule 1), so the root form would work if the human wants it. Without it, B is likely HUMAN-REPORTED or NOT_RUN (R-11 is "high", and the design says so). The Orchestrator applied the default "no" in the architecture, but that is the status quo of the human's own decision S2-2, not a new human answer; the question stays open for the human at G2 if he wants to change it. It does not block Stage 3 or Stage 4 |

There are no new questions for the human. DR-12..DR-14 need no human input.

## 8. Judgement of G2 items and of consistency with the human decisions
- **G2-1 (S2-1), JDK 25 single-file server, port 8000:** still consistent word for word with S2-1 (path, command, port,
  `--port`, no Maven module, `backend/` untouched, `JAVA_HOME` form in the README). The attempt-3 changes to 7.3
  (exception mapping, static-path rules) are implementation detail inside the same decision. No new dependency or
  technology. Fit to ratify. The JDK API claims in 7.3 stay UNVERIFIED but are plausible, and R-2 covers a missing module.
- **G2-2 (S2-2), agent `java` permissions:** still recorded as an intention that has not been carried out; no agent
  edits settings; the Orchestrator reads the file before Stage 5; the fallback (human-run or NOT_RUN) is defined and
  R-11 now honestly says the fallback is the expected default. Whether the entries exist today is UNVERIFIED by me
  (I did not read `.claude/settings.local.json`, and it is not in scope). Fit to ratify.
- **G2-3 (S2-3), test strategy B + A + D:** consistent. A and D are HUMAN-REPORTED in 10.1, 10.2, section 13 and R-4;
  only B and the static checks that need no `java` may be AGENT-EXECUTED. The new test-A cases (a)-(g) are all
  browser-page cases, so they stay under the human-run label. Fit to ratify.
- **H-2:** nothing in the design plans or claims security, speed or performance testing. The static-path rules are
  described as serving rules, the hostile-input tests stay NOT_RUN, the 10 s limit stays NOT_RUN. Consistent.
