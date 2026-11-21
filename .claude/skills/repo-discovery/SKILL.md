---
name: repo-discovery
description: Technology-neutral procedure to learn a repository's languages, frameworks, conventions and build/test/lint commands from evidence in the repo, and record them with provenance in repo-profile.md. Use before designing, planning, implementing or verifying anything in an unfamiliar repository.
---

# Repository discovery

Goal: produce or refresh `docs/sdlc/<KEY>/repo-profile.md` (template: `templates/repo-profile.md`) so every later
agent works from the same evidence. **Nothing about the stack is assumed.** Every fact in the profile cites the
file (and line or section) it came from. A fact without evidence is written as `UNKNOWN`, never guessed.

## Procedure

Work from the strongest evidence to the weakest. Stop collecting a topic once you have a cited answer.

1. **Orient.** List the repository root and one level below (Glob). Note empty/greenfield state: if there is no
   source and no manifest, record `greenfield: yes` and stop; the Solution Architect must ask the human to choose
   the stack.
2. **Project instructions.** Read `CLAUDE.md`, `AGENTS.md`, `README*`, `CONTRIBUTING*`, `docs/`, `Makefile`-style
   task files and any file the above links to. These state commands and conventions authoritatively.
3. **Automation as ground truth.** Read CI/CD definitions (search for workflow, pipeline or build-config files).
   Commands that CI runs are the best evidence for how to build, test, lint and package.
4. **Manifests and lock files.** Find dependency/build manifests at the root and in sub-directories (see
   `reference/stack-signals.md` for filename hints; the list is a convenience, not a requirement; an unknown
   file type is identified by reading it). Read them for languages, frameworks, dependencies, declared scripts
   or tasks, and module boundaries (single project vs several modules vs monorepo).
5. **Configuration of tools.** Locate formatter, linter, type-check, test and coverage configuration files and
   record what they enforce.
6. **Sample the code.** Read 3-5 representative source files and 2-3 test files in each major module: layout,
   naming, error handling, logging, dependency injection, how tests are organized and named.
7. **History.** If you have a shell tool and git history exists, read recent `git log --oneline` for
   commit-message style and branch naming; otherwise record `UNKNOWN`.
8. **Reconcile.** If sources disagree (README says one command, CI another), record both, prefer CI, and flag
   the conflict under *Open questions*.

## Rules

- **Commands need provenance.** Build, test, lint, format and type-check commands go in the profile only with the
  file that defines or uses them. A command with no provenance is listed as `UNKNOWN` and is never executed;
  ask the human via the Orchestrator.
- **Do not execute anything during discovery** except read-only inspection (list, read, search, git log/status).
- **No assumptions from file extensions alone.** Extensions suggest a language; manifests and CI confirm it.
- **Multiple stacks are normal.** Record each module with its own commands.
- **Refresh, do not rewrite.** If a profile already exists, re-check its cited files; update only what changed and
  note the reason (e.g. "manifest changed"). Only the Solution Architect and Implementation Planner may write
  the profile; any other agent that finds a discrepancy reports it in its structured return.
- **Secrets.** Never read or record contents of `.env*`, key files or credentials; note only that they exist.

## Output

`docs/sdlc/<KEY>/repo-profile.md` using the template, with a final `Confidence` line per section
(`high` = confirmed by CI or docs, `medium` = manifest only, `low` = inferred). Return a 10-line summary in the
structured return format and list every `UNKNOWN` that blocks later work as a question.
