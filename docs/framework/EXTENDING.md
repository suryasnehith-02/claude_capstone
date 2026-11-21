# Extending the framework

Rules of thumb: agents define who and what is allowed; skills hold procedure and templates; hooks and permissions
enforce. Change the right layer, and re-run `scripts/validate-framework.sh --complete` and
`tests/hooks/run-tests.sh` after every change.

## Change an artifact template or a procedure
Edit the skill's `templates/*.md` or `SKILL.md`. If you rename a required heading, update
`.claude/hooks/artifact-sections.conf` too (agents cannot finish while required headings are missing).

## Add discovery hints for another ecosystem
Add a row to `.claude/skills/repo-discovery/reference/stack-signals.md`. It only lists filename patterns that help
find evidence; commands must still come from the target repository. No other change is needed.

## Add or change a gate
Add a row to `.claude/hooks/gates.conf` (gate id, stage it is decided in, artifacts it covers), add the matching
`gate_gN` key to `.claude/skills/sdlc/templates/state.json` and `reference/state-schema.md`, describe the gate
in the `sdlc` skill, and add tests. Delegation order and GitHub writes require every gate decided before the
stage in question, so a new gate is enforced automatically.

## Tighten or loosen an agent's write scope
Edit its row in `.claude/hooks/agent-paths.conf`; add a test case for the new behaviour.

## Change protected branches or hook switches
`.claude/hooks/protected-branches.txt`, `.claude/hooks/hooks.conf`.

## Add an MCP-backed capability
1. Connect the server and inspect its real tools (do not assume operations exist).
2. Grant exact tool names in the one agent that needs them (`tools:`), read tools in `permissions.allow`, write
   tools in `permissions.ask`.
3. If the tool writes to an external system, extend `workflow-validation.sh` so it is allowed only to the right
   agent, in the right stage, with the right gates (see `check_github`).

## Add Jira feedback (comment or transition after the PR)
Not enabled: it needs a Jira write tool on exactly one agent, an `ask` permission and a final gate. Keep it
optional and human-approved.

## What not to change
The count of one Orchestrator and eight specialists, `disallowedTools: Agent` on specialists, the `Agent(...)`
allowlist on the Orchestrator, and the `deny` list. The validator fails if these drift.

## Reusing in another repository
See [INSTALL.md](INSTALL.md). Nothing in `.claude/` is specific to a language or project.
