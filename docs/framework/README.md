# Agentic SDLC Framework

A technology-agnostic framework that takes a JIRA issue key through 8 SDLC stages to a GitHub Pull Request using
Claude Code subagents, skills, hooks, permissions and MCP. One Orchestrator coordinates eight specialist agents; a
human approves at seven gates. Nothing in the framework names a language, framework, build tool or test runner:
agents learn the stack from the target repository.

| Doc | Read it to |
|---|---|
| [INSTALL.md](INSTALL.md) | add the framework to a repository |
| [CONFIGURATION.md](CONFIGURATION.md) | tune permissions, protected branches, hook switches, MCP names |
| [USAGE.md](USAGE.md) | run, resume, retry and approve a workflow |
| [EXTENDING.md](EXTENDING.md) | change templates, agents, gates, discovery hints |
| [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | diagnose blocked actions, missing tools, stuck workflows |

## Map

```
Human <-> sdlc-orchestrator (main-thread agent: only actor that asks questions and writes workflow state)
  S1 requirements-analyst      JIRA/Confluence via MCP     -> requirements.md          gate G1
  S2 solution-architect        repo-discovery               -> architecture.md, repo-profile.md
  S3 design-reviewer           independent review           -> design-review.md        gate G2
  S4 implementation-planner                                 -> impl-plan.md            gate G3
  S5 developer                 (pr-manager prepares branch) -> code, tests, docs       gate G4 (kickoff)
  S6 code-reviewer             11-category review           -> code-review.md          gate G5
  S7 verification-agent        runs the repo's own checks   -> verification-report.md gate G6
  S8 pr-manager                changelog, PR description    -> GitHub PR               gate G7 (never merges)
```

## Layout

| Path | Purpose |
|---|---|
| `.claude/agents/` | the 9 agents (role, boundaries, tools) |
| `.claude/skills/` | 10 skills: procedures and templates (`sdlc`, `repo-discovery`, one per stage) |
| `.claude/hooks/` | enforcement scripts and their config tables (`*.conf`, `protected-branches.txt`) |
| `.claude/settings.json` | permissions (and, once enabled, hook registration and the Orchestrator as session agent) |
| `docs/sdlc/<KEY>/` | per-issue artifacts, committed with the PR |
| `.sdlc/` | runtime state, hash ledger and audit log (gitignored) |
| `scripts/validate-framework.sh` | structural validator for the configuration |
| `tests/hooks/run-tests.sh` | behavioural tests for the hooks |

## Guarantees and their limits

Enforced by tooling (hooks and permissions), independent of agent instructions: no merge, no push to protected
branches, no PR before approval gate G7, no source edits before gates G1-G4 and a feature branch, stage order,
per-agent write scope, no PASS in the verification report without a recorded successful execution, secrets
redacted from the audit log.

Not enforceable by tooling: that a human actually answered a gate question honestly. The state file records the
approval; a hash ledger ties it to the exact artifact contents; an optional switch
(`REQUIRE_GATE_QUESTION_IN_AUDIT`) additionally requires an approval question to appear in the audit log. Shell
write blocking is pattern-based defence in depth, not a sandbox.
