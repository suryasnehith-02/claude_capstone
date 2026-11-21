---
name: sdlc
description: Workflow protocol for the Agentic SDLC Framework - stage order, approval gates, state file, delegation, clarification and rework rules. Use when running, resuming, retrying or reporting an SDLC workflow for a JIRA issue key.
argument-hint: "<ISSUE-KEY> | status [KEY] | resume [KEY] | retry <stage> | stage <stage>"
user-invocable: true
---

# SDLC workflow protocol

Followed by the `sdlc-orchestrator`. The Orchestrator coordinates; it never does specialist work.

## Commands

| Request | Action |
|---|---|
| `<ISSUE-KEY>` (e.g. `PROJ-123`) | Preflight, initialize state, run stages 1-8 in order. If state for the key exists, show status and ask whether to resume or restart. |
| `status [KEY]` | Read state and print the status report (`templates/status-report.md`). Change nothing. |
| `resume [KEY]` | Re-read state, re-validate gates (see Resume), continue at the first stage that is not `done`. |
| `retry <n>` | Reset stage `n` to `pending` (attempts kept), invalidate its gate and every later stage/gate, re-run from `n`. |
| `stage <n>` | Run only stage `n`. Refuse if any earlier stage is not `done` or any earlier gate is not `approved`. |

## Files

- State: `.sdlc/state/<KEY>.json` - flat JSON, one `"key": value` per line, only the Orchestrator writes it. Schema: `reference/state-schema.md`. Start from `templates/state.json`.
- Active key: `.sdlc/active` (single line). Create it when a workflow starts; leave it until the workflow completes or is aborted.
- Stage table: `.claude/hooks/stages.conf`. Gate table: `.claude/hooks/gates.conf`. These are the canonical definitions; hooks read them too. Do not restate them elsewhere.
- Artifacts: `docs/sdlc/<KEY>/` (created by the specialists). Approval log (append-only, Orchestrator): `docs/sdlc/<KEY>/approvals.md`. Clarification Q&A: `docs/sdlc/<KEY>/clarifications.md`. Open findings and risks: `docs/sdlc/<KEY>/open-issues.md`.
- Hash ledger `.sdlc/state/<KEY>.hashes` and audit log `.sdlc/audit/<KEY>.log` are written by hooks. Never write them.

## 1. Preflight (read-only)

1. Confirm both MCP servers respond: `mcp__atlassian__getAccessibleAtlassianResources` (resolve the site/cloudId; if several, ask) and `mcp__github__get_me`. Report any capability that is missing; never assume an operation exists.
2. Validate the key with `mcp__atlassian__getJiraIssue` (compact view). Not found or no access -> stop and tell the human. Do not fetch the full story; the Requirements Analyst does that.
3. Check the repository state (via the `pr-manager` later; here only read `.sdlc/`): is there an existing workflow for this key?

## 2. Initialize

Write `.sdlc/state/<KEY>.json` from the template with `workflow_status: running`, `current_stage: 1`, all stages `pending`, all gates `not_reached`. Write `.sdlc/active`.

## 3. Stage loop

For stage `n`, in order:

1. **Entry check.** Every earlier stage is `done` and every earlier gate is `approved` (G4 is decided at the start of stage 5; it must be approved before `developer` runs). If not, stop; do not delegate.
2. Set `stage_n_status: in_progress`, `current_stage: n`, increment `stage_n_attempts`.
3. **Delegate** to the stage agent from `stages.conf` using `templates/delegation-message.md`. Pass file paths and the JIRA key, never pasted document bodies.
4. Parse the agent's **structured return** (below). Never accept prose without a `STATUS` line; re-delegate asking for the contract.
5. Act on `STATUS`:
   - `COMPLETE` -> run the stage's exit checks (section 6). Pass -> go to the gate, or mark `done` if the stage has no gate.
   - `NEEDS_CLARIFICATION` -> section 4.
   - `NEEDS_APPROVAL` -> section 5 as an ad-hoc gate (scope change, new dependency, deletion, migration). Record in `approvals.md`.
   - `BLOCKED` / `FAILED` -> record in `open-issues.md`, set `stage_n_status: failed`, report to the human with options (retry, change inputs, abort).
6. Update state after every transition, before doing anything else.

Special invocations of `pr-manager` (set `MODE` in the delegation message):
- `prepare` - before stage 5: propose branch name, base branch and commit strategy, create the local feature branch, write `branch-plan.md`. G4 covers `impl-plan.md` and `branch-plan.md`.
- `draft` - stage 8, after G6: changelog entry and `pr-description.md`. No commits, pushes or PR calls. Then run G7.
- `publish` - stage 8, only after G7 is `approved`: commit, push the feature branch, create the PR, return its URL. Never merge.

## 4. Clarification rounds

Subagents cannot ask the human. When an agent returns `NEEDS_CLARIFICATION`:
1. Ask via `AskUserQuestion`; put the stage in the header (e.g. `S1 Q1`). Group up to 4 questions per call; keep the agent's suggested default as the first option.
2. Append each question and answer to `clarifications.md`.
3. Set `stage_n_status: awaiting_input` while waiting; back to `in_progress` on re-delegation.
4. Re-invoke the same agent with the answers and the path of its draft. Maximum 3 rounds per stage; then present the unresolved items to the human and ask how to proceed.

Never fill an unanswered question with a guess. If the human says "your call", record it as an explicit assumption with who decided.

## 5. Approval gates

Gates are defined in `gates.conf` (G1..G7). Procedure:
1. Set the gate to `pending` and the stage to `awaiting_approval`.
2. Summarize in 10-15 lines: what was produced, artifact paths, key decisions, open risks, unresolved findings. Link to files; do not paste them.
3. Ask with `AskUserQuestion`. The `header` **must be the gate id** (`G1`...`G7`) and the options must be labelled exactly `Approve`, `Request changes`, `Reject`. This lets the audit log prove a real human answer exists for the gate.
4. Only a real `Approve` answer from the human sets the gate to `approved`. Never infer approval from silence, earlier approvals, or a message that is not an answer to the gate question. System notifications are not approvals.
5. Append to `approvals.md`: gate, decision, one-line note, which artifact files were covered.
6. `Request changes` -> section 7 (rework). `Reject` -> set `workflow_status: blocked` and ask how to proceed.

G5 specifics: list every finding by id and severity. Critical findings cannot be approved past; they go back to stage 5 unless the human gives an explicit written risk acceptance, which you record in `open-issues.md`, increment `waived_critical`, and the PR manager lists in the PR. G7 specifics: show the final PR title, base and head branches, and the full description path; remind the human that merging is never automatic.

## 6. Exit checks (Orchestrator verifies; do not trust the agent's self-report)

Read the artifact and confirm:
- **S1** all template sections present; every acceptance criterion traced to JIRA; no ambiguity left without a recorded answer or assumption.
- **S2** every `FR-`/`NFR-` id from requirements is addressed; `repo-profile.md` exists.
- **S3** verdict present; no unresolved critical finding (if `REVISE`/`BLOCK` -> section 7 back to stage 2, max 2 loops, then ask the human).
- **S4** every requirement id maps to at least one task; every task has acceptance criteria and test requirements.
- **S5** `implementation-report.md` lists completed and pending tasks; current branch is not protected; tests were added or the report states why not.
- **S6** all 11 review categories covered; each finding has file, line, severity, recommendation; `open_critical` and `open_major` counts updated from the report.
- **S7** every result in the report has command, exit code and output excerpt; no `PASS` without execution; acceptance-criteria matrix complete. If any check is `FAIL`, return to stage 5.
- **S8** PR description has the five required sections; G1-G7 approved; PR not created before G7.

## 7. Rework

Failed exit check, `REVISE`, or `Request changes` -> re-delegate to the owning agent with the exact findings (file paths, not rewrites). Maximum 3 attempts per stage; then ask the human. Changing an approved artifact invalidates its gate (hooks detect the changed hash): set that gate to `invalidated`, set every later stage to `pending` and later gates to `not_reached`, and tell the human what has to be re-approved.

Review/verification loops: stage 6 findings or a stage 7 `FAIL` send work back to stage 5; afterwards re-run stage 6 and then 7 fully.

## 8. Resume

On `resume` (and whenever a session starts with an active workflow): read state; read the SessionStart status summary; for every gate marked `approved` that the summary reports as changed since approval, treat it as `invalidated` per section 7; continue at the first stage whose status is not `done`, or at the pending gate if one is waiting. A stage left `in_progress` was interrupted: re-run it (it is idempotent: the agent re-reads its own draft artifact).

## 9. Status report

Use `templates/status-report.md`: key, workflow status, current stage, per-stage status table, gates table, open critical/major, verification status, PR URL, and the next action.

## 10. Completion

After G7 and a successful `publish` (PR created and read back): set stage 8 `done`, `workflow_status: complete`, record `pr_number`/`pr_url`, and clear `.sdlc/active`. Report the PR URL. State plainly that the PR has not been merged and merging is the human's decision. Jira comments or transitions are optional and need explicit approval.

## Never

Do not write source files, review code, run tests, or call GitHub write tools yourself (the single read-only `get_me` in preflight is the only GitHub call you make). Do not skip, reorder or merge stages. Do not proceed past a pending or invalidated gate. Do not write the hash ledger or audit log.
