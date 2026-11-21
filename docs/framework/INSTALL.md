# Installing the framework in a repository

## Prerequisites

- Claude Code (tested with 2.1.281) and a shell that runs POSIX `sh` with `awk`, `sed`, `grep`, `cut`, `tr`,
  `head`, `date` and `git`. On Windows use Git Bash. No `jq`, no language runtime.
- MCP servers named **`atlassian`** (Jira + Confluence) and **`github`**, connected and authenticated
  (`claude mcp list` must show both as connected). The framework refers to their tools as
  `mcp__atlassian__*` and `mcp__github__*`; other server names require editing the agent `tools:` lines and
  `settings.json`. See `.mcp.json.example`. Register them yourself with `claude mcp add`; the framework never
  stores credentials.
- A GitHub repository with at least one commit and a default branch (a PR needs a base).

## Steps

1. Copy into the target repository root: `.claude/agents/`, `.claude/skills/`, `.claude/hooks/`,
   `scripts/validate-framework.sh`, `tests/hooks/run-tests.sh`, `docs/framework/`, `.mcp.json.example`.
2. Merge, do not overwrite, the shared files:
   - `CLAUDE.md`: add the framework section from this repository's `CLAUDE.md` to the target's existing one.
   - `.claude/settings.json`: merge the `permissions` lists. Keep every `deny` entry.
   - `.gitignore`: add `.sdlc/`.
   - Never touch `.claude/settings.local.json`, user settings or MCP credentials.
3. Edit `.claude/hooks/protected-branches.txt` for the repository's real protected branches.
4. Validate: `sh scripts/validate-framework.sh --complete` (all checks must pass).
5. Test the hooks: `sh tests/hooks/run-tests.sh` (all tests must pass). Do this **before** step 6.
6. Enable enforcement: add the hook registration and the session agent to `.claude/settings.json`
   (snippet in [CONFIGURATION.md](CONFIGURATION.md)), then start a new Claude Code session in the repository.
7. Smoke test: start `claude`, run `/sdlc status` (expect "no active workflow"), then check
   `claude mcp list` shows `atlassian` and `github` connected.

The first real run should use a small, well-written JIRA story. Nothing is written to Jira or GitHub without an
explicit permission prompt and the matching approval gate.

## Uninstall

Remove the `hooks` block and `"agent"` line from `.claude/settings.json`, then delete the copied directories.
Artifacts under `docs/sdlc/` are normal files and can be kept or removed.
