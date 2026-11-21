# Status report template

```
SDLC WORKFLOW <KEY> - <jira_summary>
Status: <workflow_status>   Current stage: <n> <name>   Branch: <branch or "not created">

| # | Stage | Agent | Status | Attempts | Artifact |
|---|---|---|---|---|---|
| 1 | requirements | requirements-analyst | <status> | <n> | docs/sdlc/<KEY>/requirements.md |
| ... one row per stage, 1-8 ... |

| Gate | Decision point | Status |
|---|---|---|
| G1 | Requirements finalization | <not_reached|pending|approved|rejected|invalidated> |
| ... G2-G7 ... |

Open findings: <open_critical> critical, <open_major> major, <waived_critical> waived
Verification: <verification_status> (<verification_commands_run> commands executed)
Pull request: <pr_url or "not created">

Warnings: <gates whose artifacts changed since approval, stages left in_progress, missing artifacts, or "none">
Next action: <next_action>
```
