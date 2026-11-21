# Troubleshooting

## "BLOCKED by SDLC framework hook: ..."
The message names the rule. Typical causes:

| Message | Meaning | Fix |
|---|---|---|
| gates not approved or changed since approval | an artifact was edited after its gate was approved, or the gate was never approved | re-run the gate; the Orchestrator sets the gate to `invalidated` and asks again |
| current branch ... protected / not the workflow branch | you or an agent is on `main` etc. | switch to the feature branch from `branch-plan.md` |
| ... may not write during stage N | an agent wrote outside its stage | the Orchestrator should re-delegate in the right stage |
| ... is outside the paths ... may write | agent write scope | check `agent-paths.conf`; usually an agent bug |
| PASS claimed but no successful execution is in the audit log | the verification report claims a pass that was not run | the Verification Agent must run the command and copy its real output |
| cannot start stage N | earlier stage not `done` or gate not valid | `/sdlc status`, then continue in order |

Hooks only act while `.sdlc/active` is non-empty. To check what happened, read `.sdlc/audit/<KEY>.log`
(lines: `time|EVENT|agent|detail`; `DENY` lines give the reason).

## A hook error or odd behaviour
Run the same payload by hand:
`printf '%s' '{"hook_event_name":"PreToolUse","tool_name":"Write","tool_input":{"file_path":"x"}}' | sh .claude/hooks/pre-implementation-check.sh; echo $?`
Exit 2 = block (reason on stderr), 0 = allow. Run `sh tests/hooks/run-tests.sh` to confirm the scripts still behave.
On Windows, hooks need Git Bash; set `CLAUDE_CODE_GIT_BASH_PATH` if it is not found.

## Workflow state is missing or unreadable
`.sdlc/active` names a key whose `.sdlc/state/<KEY>.json` is gone. Hooks fail closed (source edits and delegation
are blocked). Either restore the file or empty `.sdlc/active`.

## An agent cannot see an MCP tool
Run `claude mcp list`. Servers must be named `atlassian` and `github`. MCP servers registered with local scope
are only available in the project directory they were added from. In scratch directories or other repositories
they will be missing: register them there.

## `/sdlc` or an agent is not found
Run `/agents` and `/reload-skills`, or restart the session. Run `sh scripts/validate-framework.sh --complete`.

## AskUserQuestion approvals and the audit-log cross-check
`REQUIRE_GATE_QUESTION_IN_AUDIT` is `0` by default because headless runs cannot exercise `AskUserQuestion`.
To turn it on: run one interactive workflow, answer a gate question, then check the audit log for a line like
`|ASK|-|headers=G1 ok ...`. If present, set the switch to `1` in `.claude/hooks/hooks.conf`. If absent, leave it `0`
and open an issue: the hook relies on the `header` field of the tool input.

## Verification shows many permission prompts
Expected: the framework cannot pre-approve commands it does not know. Add trusted commands to your own
`.claude/settings.local.json` as `Bash(<command> *)`.

## `git push` fails
The PR Manager stops and asks before using the GitHub API fallback (`create_branch` + `push_files`), which creates
API commits rather than your local ones. Fix credentials and re-run `publish`, or approve the fallback.

## The remote repository is empty
A PR needs a base branch. The PR Manager returns `BLOCKED` and explains. Create the initial commit on the default
branch yourself (the framework never pushes to protected branches), then resume.
