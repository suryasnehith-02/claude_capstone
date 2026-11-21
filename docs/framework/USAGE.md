# Using the framework

## Start

```
claude                      # with "agent": "sdlc-orchestrator" in settings.json
claude --agent sdlc-orchestrator   # otherwise
```

Then, in the session:

| Request | Effect |
|---|---|
| `/sdlc PROJ-123` | preflight, create state, run all 8 stages with approval gates |
| `/sdlc status [KEY]` | print the status report, change nothing |
| `/sdlc resume [KEY]` | continue an interrupted workflow from saved state |
| `/sdlc retry <n>` | re-run stage `n`; later stages and their approvals are reset |
| `/sdlc stage <n>` | run only stage `n` (refused if earlier stages or gates are not complete) |

## What happens

1. **Preflight (read-only):** checks the Jira and GitHub connections and that the key exists.
2. **S1** The Requirements Analyst reads the story, comments, linked issues, remote links, attachments and linked
   Confluence pages. Questions come to you through the Orchestrator. **G1** approves `requirements.md`.
3. **S2-S3** Architecture is written from the repository's real stack, then independently reviewed (critical
   findings loop back, at most twice). **G2** approves architecture plus review.
4. **S4** The plan breaks the work into ordered tasks with acceptance criteria and tests. **G3** approves it.
5. **Branch prep and G4:** a feature branch and commit strategy are proposed; you approve the branch and file scope.
6. **S5** The Developer implements on the feature branch. Anything outside the plan (new files, deletions, new
   dependencies, migrations) stops for your approval.
7. **S6** The Code Reviewer reports findings (file, line, severity). **G5**: critical findings must be fixed;
   waiving one needs an explicit written risk acceptance that is carried into the PR.
8. **S7** The Verification Agent runs the repository's own checks and records verbatim evidence. **G6** approves.
   You will see permission prompts for each build/test command: approve the ones you trust.
9. **S8** The PR Manager drafts the changelog and PR description (five required sections, real test evidence).
   **G7** is the final approval. Only then does it commit, push the feature branch and open the PR.
   The PR is never merged by the framework.

At each gate the question header is the gate id (`G1`..`G7`) and the options are `Approve`, `Request changes`,
`Reject`.

## Where things are

- Artifacts: `docs/sdlc/<KEY>/` (requirements.md, architecture.md, design-review.md, impl-plan.md,
  implementation-report.md, code-review.md, verification-report.md, pr-description.md, plus clarifications.md,
  approvals.md, open-issues.md, repo-profile.md, branch-plan.md).
- State: `.sdlc/state/<KEY>.json`. Audit log: `.sdlc/audit/<KEY>.log` (delegations, skills, MCP calls, commands,
  approval questions; secrets redacted). Both are ignored by git.

## Interrupt, resume, abort

Close the session at any time. A new session prints a workflow status summary at start; run `/sdlc resume`.
Stages left `in_progress` are re-run. If an approved artifact changed since its approval, the approval is treated
as invalid and must be given again.

To abandon a workflow, empty the file `.sdlc/active` (and tell the Orchestrator); hooks then go dormant. Nothing
is deleted.

## Good practice

- Give the story acceptance criteria; the analyst quotes them verbatim and traces every requirement to them.
- Keep stories small enough for one PR.
- Read the gate summaries; approvals are tied to the exact artifact contents, so edits after approval cost a re-approval.
