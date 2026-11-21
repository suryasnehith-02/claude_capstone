---
name: verification
description: Verify an implementation by discovering the repository's own build, test, analysis and security tooling from evidence, actually executing it, testing edge and failure scenarios, checking every acceptance criterion, and recording verbatim command evidence in verification-report.md. Never claims a pass that was not executed. Use for SDLC Stage 7.
---

# Verification

Output: `docs/sdlc/<KEY>/verification-report.md` from `templates/verification-report.md`.

## Non-negotiable rules

1. **A result is `PASS` only if you executed the command in this run, it exited 0, and its output shows the checks
   actually ran** (for test runners: a non-zero count of executed tests). Anything else is `FAIL` or `NOT_RUN`.
2. **Evidence is verbatim.** Record the exact command, working directory, exit code, and an unedited output excerpt.
   Never paraphrase or reconstruct output from memory. Redact secrets and tokens as `[REDACTED]`.
3. **Do not guess commands.** Use only commands with provenance (file and line) in `repo-profile.md` or that you
   confirm in the repository yourself. A missing tool or unknown command is `NOT_RUN` with the reason, not a pass.
4. **You verify; you do not fix.** Never modify source, tests or docs. Failures are reported with reproduction steps.
5. **Never loosen a check** (skipping tests, lowering thresholds, excluding files) to make it pass.

## Procedure

1. **Discover independently.** Read `repo-profile.md`, then re-derive the verification commands yourself from CI
   definitions, docs and task files (the developer's commands are not authority). Note any disagreement. Follow
   `repo-discovery` rules for evidence.
2. **Plan the checks** and write them into the report before running: build/compile, unit tests, integration
   tests (if the repo has any), lint, static analysis, type check, dependency/security audit (only if the repo
   defines tooling for it), plus anything CI runs on a PR. Add environment prerequisites you need to satisfy.
3. **Run each check** with `Bash`, one command at a time, from the right working directory. The human approves
   unfamiliar commands via the permission prompt; if denied, mark `NOT_RUN: denied`. Do not install system-wide
   tools or run anything outside the repo's documented commands. If dependencies are missing, run the repo's own
   documented install step only if the profile lists it with provenance; otherwise report `NOT_RUN`.
4. **Edge and failure scenarios.** For each applicable item, show evidence either from an existing test that ran or
   from a manual check you run without modifying source: missing file or path, empty/oversized/invalid input,
   external API or service failure/timeout/bad response, permission denied, duplicate or concurrent action. If no
   test covers a scenario the requirements imply, record the gap. Put any scratch files only under
   `docs/sdlc/<KEY>/verification-artifacts/`; edits outside the repository are blocked.
5. **Acceptance criteria matrix.** For every AC in `requirements.md` give its evidence (check id or test name) and
   result: `PASS`, `FAIL`, `NOT_RUN` or `NOT_VERIFIABLE` (with reason).
6. **Review findings.** For every critical and major finding in `code-review.md`, state whether the evidence shows
   it resolved, still open, or cannot be determined.
7. **Documentation.** Validate generated or updated docs: files exist, links and referenced paths resolve, and any
   command or example shown in docs actually runs as written (execute it).
8. **Overall status:** `PASS` only if every required check and every AC passed and nothing relevant is `NOT_RUN`;
   `PARTIAL` if the only gaps are `NOT_RUN`/`NOT_VERIFIABLE` with reasons; otherwise `FAIL`.

## Evidence entry format (one per executed command; hooks cross-check the `cmd:` lines against the audit log)

```
### V-<n> <purpose>
cmd: <exact command>
cwd: <working directory>
exit: <exit code>
result: PASS | FAIL | NOT_RUN
output:
<verbatim excerpt, at most 40 lines; mark any omission with "[... omitted ...]">
```

## Return

`COMPLETE` when the report is written with every planned check resolved to PASS/FAIL/NOT_RUN. SUMMARY: overall
status, counts of PASS/FAIL/NOT_RUN, ACs verified n of n, open critical/major findings. A `FAIL` or open critical
finding is still `COMPLETE` for this stage: the Orchestrator routes the rework.
