#!/bin/sh
# validate-framework.sh - structural checks for the Agentic SDLC Framework configuration.
# Pure POSIX sh + awk/grep; no language runtime needed.
# Usage: sh scripts/validate-framework.sh [--complete] [repo-root]
#   --complete  also require all 9 agents and all 10 skills to exist (otherwise only what exists is checked)
set -u
complete=0
if [ "${1:-}" = "--complete" ]; then complete=1; shift; fi
root=${1:-.}
cd "$root" || { echo "cannot cd to $root"; exit 2; }

fail=0; checks=0
ok()  { checks=$((checks+1)); echo "ok:   $*"; }
bad() { checks=$((checks+1)); fail=$((fail+1)); echo "FAIL: $*"; }

AGENT_KEYS=" name description tools disallowedTools model permissionMode maxTurns skills mcpServers hooks memory background isolation effort color initialPrompt "
SKILL_KEYS=" name description when_to_use argument-hint arguments disable-model-invocation user-invocable allowed-tools disallowed-tools model effort context agent background hooks paths shell metadata license compatibility "
SPECIALISTS="requirements-analyst solution-architect design-reviewer implementation-planner developer code-reviewer verification-agent pr-manager"
ALL_SKILLS="sdlc repo-discovery requirements-analysis architecture-design design-review implementation-planning code-implementation code-review verification github-pr-management"

# print top-level frontmatter keys; exit 1 if frontmatter missing/unterminated
fm_keys() {
  awk 'NR==1 && $0!="---" {exit 1}
       NR>1 && $0=="---" {closed=1; exit}
       NR>1 && /^[A-Za-z][A-Za-z_-]*:/ {k=$0; sub(/:.*/,"",k); print k}
       END {if(!closed) exit 1}' "$1"
}
fm_val() { # file key -> scalar value on the key's line
  awk -v k="$2" 'NR>1 && $0=="---" {exit} index($0,k":")==1 {v=$0; sub("^"k":[ ]*","",v); print v; exit}' "$1"
}
fm_list() { # file key -> "  - item" entries following key
  awk -v k="$2" 'NR>1 && $0=="---" {exit}
       index($0,k":")==1 {on=1; next}
       on && /^  - / {sub(/^  - /,""); print; next}
       on && /^[A-Za-z]/ {exit}' "$1"
}

echo "== agents =="
agent_count=0
for f in .claude/agents/*.md; do
  [ -f "$f" ] || continue
  agent_count=$((agent_count+1))
  base=$(basename "$f" .md)
  if ! keys=$(fm_keys "$f"); then bad "$f: frontmatter missing or not closed"; continue; fi
  name=$(fm_val "$f" name)
  [ "$name" = "$base" ] && ok "$base: name matches filename" || bad "$f: name '$name' != filename '$base'"
  [ -n "$(fm_val "$f" description)" ] && ok "$base: has description" || bad "$base: missing description"
  for k in $keys; do
    case "$AGENT_KEYS" in *" $k "*) ;; *) bad "$base: unsupported frontmatter key '$k'";; esac
  done
  case " $keys " in *" permissionMode "*) bad "$base: permissionMode must not be set (framework keeps user permission mode)";; esac
  if [ "$base" != "sdlc-orchestrator" ]; then
    case "$SPECIALISTS" in *"$base"*) ;; *) bad "$base: not one of the 8 specialists or the orchestrator";; esac
    case "$(fm_val "$f" disallowedTools)" in *Agent*) ok "$base: cannot spawn subagents";; *) bad "$base: disallowedTools must include Agent";; esac
    case "$(fm_val "$f" tools)" in *"Agent"*) bad "$base: tools must not include Agent";; esac
  fi
  for s in $(fm_list "$f" skills); do
    [ -f ".claude/skills/$s/SKILL.md" ] && ok "$base: skill '$s' exists" || bad "$base: references missing skill '$s'"
  done
done
if [ "$complete" = 1 ]; then
  [ "$agent_count" = 9 ] && ok "exactly 9 agents (1 orchestrator + 8 specialists)" || bad "expected 9 agents, found $agent_count"
  for a in $SPECIALISTS sdlc-orchestrator; do [ -f ".claude/agents/$a.md" ] || bad "missing agent $a"; done
fi
if [ -f .claude/agents/sdlc-orchestrator.md ]; then
  t=$(fm_val .claude/agents/sdlc-orchestrator.md tools)
  case "$t" in *"Agent("*) ok "orchestrator restricts spawnable agents via Agent(...)";; *) bad "orchestrator tools must use Agent(<names>)";; esac
  for a in $SPECIALISTS; do case "$t" in *"$a"*) ;; *) bad "orchestrator Agent(...) missing $a";; esac; done
  n=$(printf '%s' "$t" | sed 's/.*Agent(\([^)]*\)).*/\1/' | tr ',' '\n' | grep -c '[a-z]')
  [ "$n" = 8 ] && ok "orchestrator may spawn exactly 8 agents" || bad "orchestrator may spawn $n agents, expected 8"
fi

echo "== skills =="
skill_count=0
for d in .claude/skills/*/; do
  [ -d "$d" ] || continue
  s=$(basename "$d"); f="$d/SKILL.md"; skill_count=$((skill_count+1))
  [ -f "$f" ] || { bad "$s: SKILL.md missing"; continue; }
  if ! keys=$(fm_keys "$f"); then bad "$s: frontmatter missing or not closed"; continue; fi
  [ "$(fm_val "$f" name)" = "$s" ] && ok "$s: name matches directory" || bad "$s: name != directory"
  [ -n "$(fm_val "$f" description)" ] && ok "$s: has description" || bad "$s: missing description"
  for k in $keys; do case "$SKILL_KEYS" in *" $k "*) ;; *) bad "$s: unsupported frontmatter key '$k'";; esac; done
  lines=$(wc -l < "$f"); [ "$lines" -le 500 ] && ok "$s: SKILL.md $lines lines (<=500)" || bad "$s: SKILL.md too long ($lines)"
done
if [ "$complete" = 1 ]; then
  [ "$skill_count" = 10 ] && ok "exactly 10 skills" || bad "expected 10 skills, found $skill_count"
  for s in $ALL_SKILLS; do [ -f ".claude/skills/$s/SKILL.md" ] || bad "missing skill $s"; done
fi

echo "== config tables =="
awk -F'|' '!/^#/ && NF>0 && NF!=4 {exit 1}' .claude/hooks/stages.conf 2>/dev/null && ok "stages.conf: 4 columns per row" || bad "stages.conf malformed"
awk -F'|' '!/^#/ && NF>0 && NF!=5 {exit 1}' .claude/hooks/agent-paths.conf 2>/dev/null && ok "agent-paths.conf: 5 columns per row" || bad "agent-paths.conf malformed"
awk -F'|' '!/^#/ && NF>0 && NF!=2 {exit 1}' .claude/hooks/artifact-sections.conf 2>/dev/null && ok "artifact-sections.conf: 2 columns per row" || bad "artifact-sections.conf malformed"
for h in lib.sh pre-implementation-check.sh post-implementation-check.sh workflow-validation.sh session-status.sh; do
  [ -f ".claude/hooks/$h" ] && ok "hook file $h present" || bad "hook file $h missing"
  [ -f ".claude/hooks/$h" ] && { sh -n ".claude/hooks/$h" 2>/dev/null && ok "$h: shell syntax valid" || bad "$h: shell syntax error"; }
done
awk -F'|' '!/^#/ && NF>0 && NF!=4 {exit 1}' .claude/hooks/gates.conf 2>/dev/null && ok "gates.conf: 4 columns per row" || bad "gates.conf malformed"
for a in $(awk -F'|' '!/^#/ && NF==4 {print $3}' .claude/hooks/stages.conf); do
  [ -f ".claude/agents/$a.md" ] || { [ "$complete" = 0 ] && echo "skip: stages.conf agent $a not built yet" || bad "stages.conf agent $a has no agent file"; }
done
bad_state=$(sed '1d;$d' .claude/skills/sdlc/templates/state.json | grep -vcE '^  "[a-z0-9_]+": (-?[0-9]+|null|"[^"\\]*"),?$')
[ "$bad_state" = 0 ] && ok "state.json template is strict flat format" || bad "state.json template has $bad_state non-conforming lines"

echo "== settings =="
if [ -f .claude/settings.json ]; then
  open=$(tr -cd '{' < .claude/settings.json | wc -c); close=$(tr -cd '}' < .claude/settings.json | wc -c)
  [ "$open" = "$close" ] && ok "settings.json braces balanced" || bad "settings.json braces unbalanced"
  grep -q 'mcp__github__merge_pull_request' .claude/settings.json && ok "merge_pull_request is listed" || bad "merge_pull_request not denied"
  grep -q '"deny"' .claude/settings.json && ok "deny rules present" || bad "no deny rules"
  grep -q 'bypassPermissions\|"defaultMode"' .claude/settings.json && bad "settings.json must not change permission mode" || ok "permission mode untouched"
  for p in $(grep -o '\.claude/hooks/[A-Za-z0-9_.-]*' .claude/settings.json | sort -u); do
    [ -f "$p" ] && ok "hook script $p exists" || bad "hook script $p missing"
  done
else
  bad ".claude/settings.json missing"
fi

echo "== technology neutrality =="
hits=$(grep -rniwE 'java|python|node\.js|nodejs|javascript|typescript|django|spring|react|angular|rails|laravel|maven|gradle|npm|pip|pytest|jest|junit|cargo|golang|dotnet|rustc' \
  .claude/agents .claude/skills .claude/hooks CLAUDE.md 2>/dev/null | grep -v 'reference/stack-signals.md' || true)
if [ -z "$hits" ]; then ok "no language/framework/tool names in agents, skills, hooks, CLAUDE.md"; else bad "technology names found:"; echo "$hits"; fi

echo "== existing config preserved =="
[ -f .claude/settings.local.json ] && ok ".claude/settings.local.json present (not managed by framework)" || echo "note: no settings.local.json"
[ -d .codemie ] && ok ".codemie/ present (not managed by framework)" || echo "note: no .codemie"

echo
echo "checks: $checks, failures: $fail"
[ "$fail" = 0 ]
