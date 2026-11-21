---
name: github-pr-management
description: Branch, commit, changelog and pull-request workflow using local git and the GitHub MCP, gated by recorded human approvals - prepare a feature branch, draft the changelog and PR description with real test evidence, then publish a PR only after final approval. Never merges. Use for SDLC Stage 8 and the pre-implementation branch preparation.
---

# GitHub PR management

The delegation message sets `MODE`: `prepare`, `draft` or `publish`. Do only that mode.

## Universal rules

- Never push to, commit on, or force-push a protected branch (`.claude/hooks/protected-branches.txt`, case-insensitive).
- Never merge, close or delete a PR or branch. Never use force flags. Never rewrite published history.
- Never include secrets, `.env*`, key files, `.sdlc/` or editor/OS junk in a commit. Stage files **by explicit path**.
- Read the workflow state (`.sdlc/state/<KEY>.json`) to confirm gates; do not write it.
- Mutating git and GitHub calls prompt the human; wait for the approval, and if denied stop and report.
- Follow the attribution rules in force for the session when writing commit messages and PR bodies.
- Inspect first: confirm which GitHub tools are available and what the remote contains (`get_me`, `list_branches`,
  `get_file_contents`). If the remote repository is empty or the base branch does not exist, return `BLOCKED`
  explaining that a base commit must be created by the human first.

## MODE prepare (before Stage 5)

1. Determine the base branch: the repo's default branch (remote HEAD or `list_branches`/docs), confirmed against the profile.
2. Propose a branch name following the repo's convention (profile/CONTRIBUTING/recent branches); default
   `<type>/<KEY>-<short-kebab-summary>` with type from the issue type (feature, fix, chore), lowercase, no spaces,
   at most 60 characters. It must not be protected and must not already exist locally or remotely.
3. Propose the commit strategy: message style from the repo's history (else `<KEY>: <imperative summary>`),
   one commit per task or wave when tasks are independent, otherwise a single commit; where
   `docs/sdlc/<KEY>/` artifacts are committed (default: same PR); what is excluded.
4. Create the local branch from the base (`git switch -c` or equivalent). If the repository has no commits or the
   working tree contains unrelated uncommitted changes, do not proceed: return `NEEDS_APPROVAL`/`BLOCKED` describing the state.
5. Write `docs/sdlc/<KEY>/branch-plan.md` (template `templates/branch-plan.md`). Return `COMPLETE`.

## MODE draft (Stage 8, after G6; no commits, no pushes, no PR calls)

1. Check preconditions in state: G1-G6 `approved`; `verification_status` is `pass` (or `partial` with the human's
   acceptance recorded in `approvals.md`); `open_critical` is 0 or fully covered by `waived_critical` with recorded
   acceptance. Otherwise return `BLOCKED` naming the unmet condition.
2. `git status --short` and `git diff --stat <base>...HEAD`: confirm the set of changes matches `branch-plan.md`
   and the implementation report; flag unexpected files.
3. **Changelog.** Use the repo's existing convention and file (and its format and category headings). If none
   exists, create `docs/changelog.md` with an `Unreleased` section. Add one entry referencing the JIRA key. Do
   not edit released sections.
4. **PR description** -> `docs/sdlc/<KEY>/pr-description.md`. First look for a PR template (`get_file_contents`
   on `.github/pull_request_template.md`, `.github/PULL_REQUEST_TEMPLATE/`, `docs/`, repo root). Use its
   structure if present, and make sure these five sections exist regardless:
   1. **Summary** - what and why, JIRA link, one paragraph.
   2. **Changes Made** - grouped by component/task, with file paths.
   3. **Test Evidence** - copied from `verification-report.md`: each command, exit code, result, and the AC matrix.
      Include nothing that was not executed. Mark `NOT_RUN` items honestly.
   4. **Known Limitations** - open minor findings, waived critical findings (with the human's recorded acceptance),
      `NOT_RUN` checks, deferred or blocked tasks.
   5. **Reviewer Checklist** - concrete checkboxes (requirements met, tests reviewed, security-sensitive paths,
      docs, migration/rollback if any).
   Also include links to `requirements.md`, `architecture.md`, `design-review.md`, `code-review.md`.
5. Return `COMPLETE` with the proposed PR title (<= 70 characters), head and base branches, and the commit plan.

## MODE publish (Stage 8, only if state shows `gate_g7: approved`)

1. Re-check state: G1-G7 `approved`; branch not protected; head != base. If anything fails, stop with `BLOCKED`.
2. Stage the explicit file list from `branch-plan.md` plus the changelog and `docs/sdlc/<KEY>/` artifacts; run
   `git status` to verify only expected files are staged; commit following the commit plan.
3. Push the feature branch (`git push -u origin <branch>`). Never force. If the push fails (credentials, remote
   rules), return `NEEDS_APPROVAL` offering the GitHub MCP fallback (`create_branch` + `push_files`); do not use
   it without approval.
4. Create the PR with `mcp__github__create_pull_request`: title, body from `pr-description.md`, head, base. Do not
   mark it draft unless the Orchestrator says so. Do not request reviewers unless given usernames.
5. Read it back (`pull_request_read` `get`) and confirm head, base and title. Return the PR number and URL.
6. State explicitly: the PR is open and **not merged**.

## Return

`COMPLETE` with the mode-specific artifact or PR URL. SUMMARY: mode, branch, base, commits, files, PR number/URL or the blocker.
