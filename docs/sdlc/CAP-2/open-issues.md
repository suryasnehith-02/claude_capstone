# Open issues - CAP-2

Maintained by the Orchestrator. Details live in the owning artifact; this file tracks what is unresolved.

## Design review round 1 (Stage 3) - verdict REVISE (rework loop 1 of 2)
Source: `design-review.md`. 0 critical, 1 major, 9 minor, 5 suggestions. Owner: solution-architect (Stage 2 rework, attempt 3).

| ID | Severity | Topic | Status |
|---|---|---|---|
| DR-1 | major | Tomcat answers TRACE before the filter chain (unverified): no envelope, 405 vs 404 on unknown paths | open |
| DR-2 | minor | Which id string P4 parses; double decoding | open |
| DR-3 | minor | Filter order tie with Boot's character-encoding filter | open |
| DR-4 | minor | Container ERROR dispatch outside the boundary | open |
| DR-5 | minor | Where 500s are logged (D-20 vs section 10) | open |
| DR-6 | minor | One DataSource / transaction manager binding; pool size in code | open |
| DR-7 | minor | Pin Spring Boot 4.x minor; behaviours-to-verify list; unverified support-date claim | open |
| DR-8 | minor | G2-5 list incomplete (multiple Content-Type, number length in ignored members, encoded literals, HEAD on unknown path, TRACE) | open |
| DR-9 | minor | Windows-safe test data lifecycle | open |
| DR-10 | minor | journal_mode=DELETE create/delete per commit on Windows; fallback order | open |
| DR-11..DR-15 | suggestion | BOM / Jackson features explicit; serialize before commit; shared route constants and working dir; cite S2-4 in section 13; child-JVM class path, reseeding, 100-continue | open |

## Environment facts and unanswered decisions (human)
- Installed per human report (clarifications.md S2-ENV): Maven 3.10.1, JDK 25.0.5.1 (not executed or verified by any agent). The architecture recommends a JDK 21 compile target; compatibility of the 4.x line and sqlite-jdbc with JDK 25 is unverified. Decision belongs to G2 (G2-2).
- G2-7 (permission for agents to run Maven / download dependencies) is still **not given**. Blocks Stages 5 and 7.
- G2-8 (how the Maven wrapper is created) is open; Maven is installed, so the human could run `mvn wrapper:wrapper`.
- `requirements.md` header still says DRAFT while G1 is approved. Not edited (editing an approved artifact would invalidate G1). Decision on an addendum is pending (DR-14).
- Branch `Main` is protected and has no commits; base commit and feature branch needed before Stage 5 (needs human approval).
  - UPDATE: base commit `7e63ced` made by the human (framework files only). Local feature branch `feature/CAP-2-todo-backend-task-apis` created by the pr-manager `prepare` step (no commit, no push); `branch-plan.md` written.

## Branch preparation findings (pr-manager prepare, status BLOCKED on the remote only)
| # | Item | Needed for | Owner | Status |
|---|---|---|---|---|
| BP-1 | Remote `suryasnehith-02/claude_capstone` is EMPTY (no branches; 409 on contents). The base commit must be pushed by the human, for example `git push origin Main:main`, so a PR base with shared history exists. Agents cannot push. | Stage 8 (PR creation), not Stage 5 | human | open |
| BP-1b | Stage 8 publish attempt (after G7 approval, 2026-11-22): pr-manager returned BLOCKED at its read-only check: `list_branches` on `suryasnehith-02/claude_capstone` returned `[]`, no `main`, no `7e63ced`. Nothing was staged, committed, pushed or created. Human action required: `git push origin Main:main` from the repo root (agents cannot push), then re-run publish. G7 approval stays valid as long as `pr-description.md` is unchanged (hash). | Stage 8 / human | open |
| BP-1c | After the human reported `git push origin Main:main` succeeded (new branch `main`, 95 objects; human's terminal output, not yet verified by an agent), the Orchestrator's delegation of the publish step to the pr-manager was DENIED by the Claude Code permission classifier ("Blocked by fast classifier"). No commit, push or PR was made. No workaround was attempted. Decision belongs to the human: add a permission rule, or run the commit, push and PR creation himself. | Stage 8 / human | open |
| BP-1d | Second publish delegation (after the human's explicit chat request to let the agents do it) was DENIED again by the permission classifier ("Blocked by fast classifier"). No workaround attempted. The human had already run `git add backend docs/changelog.md docs/sdlc/CAP-2` (index not checked by an agent). Human to run the commit, push and PR creation himself, or change his own permission rules. | Stage 8 / human | open |
| BP-2 | Remote base branch name: recommended `main` (state records `base_branch: main` as the planned PR base). Pushing local `Main` unchanged would create a remote `Main`. | Stage 8 | human | open, default `main` |
| BP-3 | Uncommitted `.claude/settings.json` (only removes a duplicated `git rev-parse` allow line; Maven allow entries are already in the base commit). Not in the CAP-2 manifest; the pr-manager will not stage it. No action taken; leaving it uncommitted is non-destructive. Human may commit it on `Main`, discard it, or leave it. | none | human | open, default leave |
| BP-4 | Per-wave commits: `backend/pom.xml` changes across waves but only its final state is committed (commit 1), so later per-wave commits may not build alone. Human may collapse to one commit at Stage 8. | Stage 8 | human | open, decide at G7 |
| BP-5 | Changelog: no changelog exists; `docs/changelog.md` to be created at Stage 8 draft, subject to agreement. | Stage 8 | human | open |
| IMP-1 | Process deviation, wave 2: the developer ran read-only commands outside the G4 list (`unzip -l/-p`, `javap`, `ls`, `find`, `grep`, `git status --short`) on jars in `~/.m2` and on `target/classes` to read real class and property names. Disclosed in the report (section 4 run 8 and the deviations table). My delegation said to check names "by reading the downloaded jars", which was ambiguous against "NOT authorised: any other command". Nothing was written or changed by them. For later waves the developer must not use commands outside the G4 list; the name table in the report covers Boot 4.1.3 so far, and new jar inspection needs a decision. | Stage 5 / human | open, human may authorise read-only inspection |
| IMP-6 | **Human-directed scope reduction (2026-11-22, human message: "do not perform any testing like performance speed, make it quick"):** task T-16 (performance suite) is NOT implemented and no performance test is run. Consequence: NFR-5 and AC-35 (p95 at or under 500 ms at about 1,000 tasks) are **NOT VERIFIED**. Stage 7 must report them as NOT_RUN, not as passed; the PR description must say so. No requirement was changed or waived at G1; this is an open verification gap, not an acceptance of the requirement being met. The `performance` Maven profile stays in the POM unused. If the human later wants the check, T-16 can be run before G6. | Stage 5 to 8 / human | open |
| IMP-1b | Repeat of IMP-1 in wave 4a part 1: the developer used a shell `grep -E` on its own redirected Maven log files in `/tmp` (read-only, disclosed). Not on the G4 command list. Instruction tightened: read logs with the Read/Grep tools. Nothing changed by the command. | Stage 5 / human | open, same decision as IMP-1 |
| IMP-1c | Third disclosure in wave 4b: one `sed -i` on the developer's own new test file (not on the G4 command list; otherwise only Edit/Write/Read/Grep). Content change only inside its own new test file. Pattern now: small read-only or self-file shell commands outside the list, always disclosed. Human decision wanted: either authorise a short read-only/own-file set (for example `ls`, `grep`, `sed` on files under `backend/`) in G4 terms, or keep forbidding it. | Stage 5 / human | open |
| IMP-5 | Wave 4b deviations: `TaskService.delete` relies on the boolean returned by the repository (no `findById` first); constraint failures surface as `UncategorizedSQLException` (a `DataAccessException`); one SchemaInitializer test (read-only directory) is skipped on Windows; Boot's `dataSourceScriptDatabaseInitializer` still exists and the `JdbcClient` bean depends on it (`spring.sql.init.mode` not read). V-26 cannot be judged because `--enable-native-access` was in effect; V-27: Mockito self-attach "Java agent loaded dynamically" warning (test only). Stage 6 review items. | Stage 6 | open |
| IMP-4 | Wave 4a part 1 design deviation: `JsonBodyReader` scans parser tokens for unpaired surrogates before `readTree` (a tree scan would miss `{"a":"\ud800","a":"x"}` because duplicate keys keep only the last). Stricter than the architecture's "post-parse scan"; consistent with the intent (surrogates in ignored members give INVALID_REQUEST_BODY). Signatures: `IdParser.parse(String)` static returning `UUID`; `JsonBodyReader.read(byte[])` as `@Component` with `@Qualifier("apiJsonMapper")`. For Stage 6 review. | Stage 6 | open, accepted for now |
| IMP-2 | `spring-boot-starter-web` is deprecated in 4.1.3 in favour of `spring-boot-starter-webmvc` (developer report). Kept for now. Check at Stage 6. | Stage 6 | open |
| IMP-3 | Property names differ from architecture 7.5: `server.error.*` and `server.servlet.encoding.enabled` are deprecated at error level; `spring.web.error.*` and `spring.servlet.encoding.enabled` used. Jackson 3 (`tools.jackson`) read-constraint defaults: nesting 500, member name length 50,000 (need explicit settings, DR-18). | T-4, T-12 | open, tracked in the report |
| BP-6 | The harness flagged the pr-manager's report as containing instruction-shaped text ("settings-json"). Nothing in the report was an instruction to the Orchestrator; the mention is the BP-3 settings.json note. Reported here for transparency. | none | Orchestrator | noted |
