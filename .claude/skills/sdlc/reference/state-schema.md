# State file schema

`.sdlc/state/<KEY>.json` is valid JSON written in a **strict flat format** so shell hooks can read it with
`grep`/`sed`/`awk` and no JSON tooling.

Format rules:
- Exactly one `"key": value` pair per line, 2-space indent, no nesting, no arrays.
- Values are a string, an integer, or `null`. Strings never contain a double quote, backslash or newline.
- Update by rewriting the whole file (Write) or by editing single lines (Edit). Keep every key present.
- Only the Orchestrator writes this file. Do not store hashes, timestamps or secrets here (hooks keep hashes in
  `.sdlc/state/<KEY>.hashes` and timestamps in the audit log).

| Key | Values | Meaning |
|---|---|---|
| `issue_key` | string | JIRA key, e.g. `PROJ-123` |
| `jira_site` | string | Atlassian site host resolved at preflight |
| `jira_summary` | string | Issue title, sanitized to one line |
| `workflow_status` | `running`, `awaiting_input`, `awaiting_approval`, `blocked`, `complete`, `aborted` | Overall state |
| `current_stage` | 1-8 | Stage being worked or waiting |
| `branch` / `base_branch` | string | Feature branch (set after `branch-plan.md`) and its target branch |
| `stage_N_status` | `pending`, `in_progress`, `awaiting_input`, `awaiting_approval`, `changes_requested`, `failed`, `done`, `invalidated` | Per-stage status, N = 1-8 |
| `stage_N_attempts` | integer | Delegations to the stage agent so far (cap 3 before asking the human) |
| `gate_gN` | `not_reached`, `pending`, `approved`, `rejected`, `invalidated` | Human gate G1-G7 (definitions in `gates.conf`) |
| `tasks_total`, `tasks_done` | integer | From `impl-plan.md` / `implementation-report.md` |
| `open_critical`, `open_major`, `waived_critical` | integer | From `code-review.md` plus recorded human waivers |
| `verification_status` | `not_run`, `pass`, `fail`, `partial` | From `verification-report.md` |
| `verification_commands_run` | integer | Count of executed commands with recorded evidence |
| `pr_number`, `pr_url` | integer, string | Set after the PR is created (`0` / `""` before) |
| `next_action` | string | One line telling a resuming session what to do next |

Detailed items (open findings, risk acceptances, Q&A, approvals) live in Markdown under `docs/sdlc/<KEY>/`
(`open-issues.md`, `clarifications.md`, `approvals.md`), not in state.

Hooks treat a gate as approved only when `gate_gN` is `approved` **and** the hash ledger holds a hash that still
matches the gate's artifacts (see `.claude/hooks/gates.conf`).
