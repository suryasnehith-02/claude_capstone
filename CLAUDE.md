# Agentic SDLC Framework

This repository contains a reusable, technology-agnostic Agentic SDLC Framework built on Claude Code
subagents, skills, hooks, settings and MCP. It takes a JIRA issue key through 8 SDLC stages to a
GitHub Pull Request, with human approval gates between stages.

## Entry point

One Master Orchestrator (`sdlc-orchestrator`) runs as the main-thread agent and is the only actor that
talks to the human, writes workflow state and delegates to the 8 specialist agents. Subagents cannot ask
the human questions, so they return structured results and the Orchestrator asks.

Start with: `/sdlc <ISSUE-KEY>` (also `status`, `resume`, `retry <n>`, `stage <n>`).

## Stages and agents

| # | Stage | Agent | Artifact (in `docs/sdlc/<KEY>/`) |
|---|---|---|---|
| 1 | Requirements | requirements-analyst | requirements.md |
| 2 | Architecture | solution-architect | architecture.md, repo-profile.md |
| 3 | Design review | design-reviewer | design-review.md |
| 4 | Implementation planning | implementation-planner | impl-plan.md |
| 5 | Implementation | developer | source, tests, docs |
| 6 | Code review | code-reviewer | code-review.md |
| 7 | Verification | verification-agent | verification-report.md |
| 8 | Pull request | pr-manager | pr-description.md, PR |

Procedures and templates live in `.claude/skills/`. Agent files only define role, boundaries and tools.

## Hard rules (also enforced by hooks and permissions)

- Never push to protected branches (list in `.claude/hooks/protected-branches.txt`). Never merge a PR.
- Never create a PR before the final human approval gate (G7).
- Never expose secrets or tokens; never read `.env*` or key files.
- Never delete user work without approval.
- Never proceed past an unresolved critical review finding without a recorded human risk acceptance.
- Never claim a test, build or check passed unless it was actually executed and the output recorded.
- Never run a stage whose predecessors are not complete and approved.
- Jira and GitHub write operations need explicit human approval each time.

## Technology neutrality

No language, framework, build tool or test runner is assumed anywhere in the framework. Agents learn the
stack from the target repository using the `repo-discovery` skill and record it, with evidence, in
`docs/sdlc/<KEY>/repo-profile.md`. A command with no evidence in the repo is never run; ask the human.

## State and artifacts

- `.sdlc/active` - active issue key. `.sdlc/state/<KEY>.json` - workflow state (Orchestrator is the only writer).
- `.sdlc/audit/<KEY>.log` - append-only audit log written by hooks. `.sdlc/` is gitignored.
- `docs/sdlc/<KEY>/` - all stage artifacts (committed with the PR).

## MCP servers

Expects servers named `atlassian` (Jira + Confluence) and `github`. They are user-scoped; see
`.mcp.json.example` and `docs/framework/CONFIGURATION.md`. Inspect available tools before use; do not
assume an operation exists.

## Docs

`docs/framework/` - INSTALL, CONFIGURATION, USAGE, EXTENDING, TROUBLESHOOTING.
