---
name: code-implementation
description: Implement approved tasks in an existing repository using its own language, framework and conventions - understand before editing, minimal secure changes, tests and docs, scope-change detection, and a completion report. Use for SDLC Stage 5 and for rework after review or verification findings.
---

# Code implementation

Output: source changes, tests and docs in the repository, plus `docs/sdlc/<KEY>/implementation-report.md`
(template: `templates/implementation-report.md`).

## Preconditions (verify first; otherwise return `BLOCKED`)

1. `impl-plan.md`, `architecture.md`, `requirements.md` and `repo-profile.md` exist, and the delegation message
   says gates G1-G4 are approved.
2. The current branch is the feature branch named in `branch-plan.md` and is **not** listed in
   `.claude/hooks/protected-branches.txt` (check with `git branch --show-current`). Never edit on a protected branch.
3. The delegation message names the tasks to do (all non-blocked tasks, or a rework list).

## Understand before editing

- Read `CLAUDE.md`/`AGENTS.md`, contribution docs, and the profile's *Conventions* section.
- For each task, read the files it will touch and their neighbours; find how the repo already solves similar
  problems (error handling, logging, configuration, naming, tests) and copy that style. Search for existing
  helpers before writing new ones.
- Use the repository's own language, framework and tools exactly as the profile records them. Never introduce a
  different stack, formatter style or project structure.

## Implement

- Work task by task in plan order. Keep diffs minimal and focused; no unrelated refactoring, renames or
  reformatting; no commented-out code; no leftover debug output.
- Quality bar: correct, readable, DRY, consistent with neighbouring code, handles errors and edge cases the
  requirements name, validates inputs at system boundaries, logs without sensitive data.
- Security: never hardcode secrets or tokens; never log them; validate and sanitize external input; use safe
  APIs for queries, file paths, commands and deserialization; least privilege; no new dependency without approval.
- Tests: write or update tests for every task as its `test requirements` say (unit, integration, edge and failure
  cases such as missing files and failed external calls), following the repo's test layout and naming.
- Docs: update or add documentation the plan calls for (README sections, comments only where the *why* is
  non-obvious, API docs).
- Run the repo's own build/test/lint commands from the profile to check your work, **only commands that have
  provenance in the profile**. Record each command, exit code and a short output excerpt. If a tool is missing
  or a command is unknown, say so; never state that something passed unless you ran it and it did.
- Do **not** commit, push, create branches, change CI, or touch `.claude/`, `.sdlc/`, other agents' artifacts.

## Scope-change triggers: stop and return `NEEDS_APPROVAL`

Stop before doing any of these, describe it, and wait for the Orchestrator to relay the human's decision:
file outside the plan's scope manifest; deleting or moving existing files; adding, removing or upgrading a
dependency; schema or data migration; public interface change; configuration, CI or build-system change; a plan
step that is impossible or contradicts the architecture. Items the plan already marked as "needs approval at
kickoff" are covered only if the delegation message says they were approved.

## Rework

When delegated review or verification findings (ids, files, severities): fix each one, add or adjust tests, and
list every finding id in the report under *Review and verification findings addressed* with what changed. If you
disagree with a finding, do not ignore it: record the reason and return it as a question.

## Return

`COMPLETE` when every assigned task is done or explicitly pending with a reason, and the report is written.
SUMMARY: tasks done/pending, files changed (count), tests added, commands run with exit codes, deviations.
