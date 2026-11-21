# Configuration

## Enabling enforcement and the Orchestrator (`.claude/settings.json`)

Add these keys alongside the existing `permissions` block. Do this only after `tests/hooks/run-tests.sh` passes.

```json
{
  "agent": "sdlc-orchestrator",
  "hooks": {
    "SessionStart": [
      { "hooks": [ { "type": "command", "command": "sh \"${CLAUDE_PROJECT_DIR}/.claude/hooks/session-status.sh\"", "timeout": 15 } ] }
    ],
    "PreToolUse": [
      { "matcher": "Write|Edit|MultiEdit|NotebookEdit|Bash",
        "hooks": [ { "type": "command", "command": "sh \"${CLAUDE_PROJECT_DIR}/.claude/hooks/pre-implementation-check.sh\"", "timeout": 20 } ] },
      { "matcher": "^(Agent|mcp__github__.*)$",
        "hooks": [ { "type": "command", "command": "sh \"${CLAUDE_PROJECT_DIR}/.claude/hooks/workflow-validation.sh\"", "timeout": 20 } ] }
    ],
    "PostToolUse": [
      { "matcher": "^(Write|Edit|MultiEdit|NotebookEdit|Bash|Skill|Agent|AskUserQuestion|mcp__.*)$",
        "hooks": [ { "type": "command", "command": "sh \"${CLAUDE_PROJECT_DIR}/.claude/hooks/post-implementation-check.sh\"", "timeout": 20 } ] }
    ],
    "PostToolUseFailure": [
      { "matcher": "^(Write|Edit|MultiEdit|NotebookEdit|Bash|Skill|Agent|AskUserQuestion|mcp__.*)$",
        "hooks": [ { "type": "command", "command": "sh \"${CLAUDE_PROJECT_DIR}/.claude/hooks/post-implementation-check.sh\"", "timeout": 20 } ] }
    ],
    "SubagentStart": [
      { "hooks": [ { "type": "command", "command": "sh \"${CLAUDE_PROJECT_DIR}/.claude/hooks/workflow-validation.sh\"", "timeout": 20 } ] }
    ],
    "SubagentStop": [
      { "hooks": [ { "type": "command", "command": "sh \"${CLAUDE_PROJECT_DIR}/.claude/hooks/workflow-validation.sh\"", "timeout": 30 } ] }
    ]
  }
}
```

`"agent": "sdlc-orchestrator"` makes the Orchestrator the main-thread agent for every session in the repository.
Alternatively leave it out and start sessions with `claude --agent sdlc-orchestrator`. Hooks do nothing unless a
workflow is active (`.sdlc/active` non-empty), so normal sessions are unaffected.

The permission mode is never set by the framework; your normal prompts and permission mode apply.

## Permissions (`permissions` in `.claude/settings.json`)

| List | Contains | Why |
|---|---|---|
| `allow` | Atlassian read tools, GitHub `get_/list_/search_` reads, read-only git | read-only context needs no prompt |
| `ask` | GitHub writes, Jira/Confluence writes, `git commit`, `git push` | every write needs a human click |
| `deny` | merge, delete, create/fork repository, destructive Atlassian operations, force push, `git merge`, `git reset --hard`, `rm -rf`, `.env`/key reads | never allowed; a deny in any scope beats an allow |

Unlisted shell commands (the repository's own build/test commands) prompt the human by design: the framework
cannot know them in advance. To pre-approve commands you trust, add `Bash(<command> *)` entries to your **own**
`.claude/settings.local.json`.

## Hook config tables (`.claude/hooks/`)

| File | Format | Controls |
|---|---|---|
| `protected-branches.txt` | one glob per line, case-insensitive | branches agents may never commit to or push to |
| `stages.conf` | `stage\|name\|agent\|required_artifacts` | stage order, agent per stage, artifacts required for `STATUS: COMPLETE` |
| `gates.conf` | `gate\|stage\|artifacts\|description` | which files each approval covers (hashed at approval time) |
| `agent-paths.conf` | `agent\|stages\|allow\|deny\|requires` | which files each agent may write, in which stage |
| `artifact-sections.conf` | `file\|heading,heading,...` | headings an artifact must contain before its agent may finish |
| `hooks.conf` | `KEY=VALUE` | `AUDIT_ENABLED`, `REQUIRE_GATE_QUESTION_IN_AUDIT` |
| `json.awk`, `lib.sh` | code | the JSON reader and shared helpers; no configuration needed |

Agents cannot edit any of these (`.claude/**` is write-protected while a workflow is active).

## MCP

Servers must be named `atlassian` and `github`. `.mcp.json.example` shows the transport shape. The framework does
not ship a project `.mcp.json`, because a project entry would shadow a working user-scoped server of the same
name. The Atlassian site (`cloudId`) is resolved at run time; if several sites are accessible the Orchestrator asks.

## Per-agent models

Set in each agent file with an alias (`opus`, `sonnet`, `haiku`, `inherit`). Defaults: Orchestrator, architect,
design reviewer and code reviewer use `opus`; the others use `sonnet`.
