#!/bin/sh
# workflow-validation.sh - workflow-level enforcement and evidence:
#   PreToolUse  Agent         delegation order: earlier stages done and earlier gates valid before a stage agent runs
#   PreToolUse  mcp__github__ only the pr-manager may write to GitHub, only at stage 8 with G1-G7 valid, only on the workflow branch
#   SubagentStart             audit the delegation and the skills the agent carries
#   SubagentStop              block a stop without the STATUS contract, missing artifacts/sections, or PASS claims with no executed command
# Dormant (exit 0, input unread) when no workflow is active. Blocks with exit 2 and a reason on stderr.
case $0 in
  */.claude/hooks/*) _D=${0%/*}; _R=${_D%/.claude/hooks} ;;
  *\\*) _D=${0%\\*}; _R=${_D%\\.claude\\hooks} ;;
  */*) _D=${0%/*}; _R=. ;;
  *) _D=.; _R=. ;;
esac
[ "$_R" = . ] || [ -s "$_R/.sdlc/active" ] || exit 0   # dormant fast path: no active workflow, no work
. "$_D/lib.sh"
hook_init
[ "$KEY_BAD" = 0 ] || deny "invalid issue key in .sdlc/active"
STAGE=${ST_current_stage:-}

# ------------------------------------------------------------ PreToolUse: Agent
check_delegation() {
  state_ok || deny "workflow $KEY is active but its state file is missing or unreadable"
  case "$AGENT" in ''|sdlc-orchestrator) ;; *) deny "subagents may not delegate to other agents (caller: $AGENT)" ;; esac
  sub=${T_subagent_type:-}
  stage_of_agent "$sub"; n=$SA
  [ -n "$n" ] || deny "'$sub' is not one of the eight SDLC agents; only those may be delegated while a workflow is active"
  extra=""; mode=""
  case "$sub" in
    pr-manager)
      case ${T_prompt:-} in *'MODE: '*) mode=${T_prompt#*'MODE: '}; mode=${mode%%[!a-z]*} ;; esac
      case "$mode" in
        prepare) n=5 ;;
        draft)   n=8 ;;
        publish) n=8; extra="G7" ;;
        *) deny "pr-manager delegation must state MODE: prepare, draft or publish" ;;
      esac ;;
    developer) extra="G4" ;;
  esac
  k=1
  while [ "$k" -lt "$n" ]; do
    eval "_s=\${ST_stage_${k}_status:-}"
    [ "$_s" = "done" ] || deny "cannot start stage $n ($sub): stage $k is '${_s:-unset}', not done"
    k=$((k+1))
  done
  need=
  while IFS='|' read -r g gs _a _d; do
    case $g in ''|'#'*) continue ;; esac
    [ "$gs" -lt "$n" ] && need="$need $g"
  done < "$HOOK_DIR/gates.conf"
  # shellcheck disable=SC2086
  gates_valid $need $extra || deny "cannot start stage $n ($sub): gates not approved or changed since approval:$MISSING_GATES"
  audit ALLOW "delegate $sub (stage $n${mode:+, mode $mode})"
  exit 0
}

# ------------------------------------------------------------ PreToolUse: GitHub MCP
check_github() {
  name=${TOOL#mcp__github__}
  case "$name" in
    get_*|list_*|search_*|pull_request_read|issue_read|run_secret_scanning) exit 0 ;;
    merge_pull_request|delete_*|create_repository|fork_repository) deny "$name is never allowed" ;;
    create_pull_request|update_pull_request|push_files|create_or_update_file|create_branch) ;;
    *) deny "$name is not part of the SDLC workflow" ;;
  esac
  state_ok || deny "workflow state is missing or unreadable"
  [ "$AGENT" = "pr-manager" ] || deny "only the pr-manager may write to GitHub (caller: ${AGENT:-main thread})"
  [ "$STAGE" = "8" ] || deny "GitHub writes are only allowed in stage 8 (current stage: $STAGE)"
  gates_valid G1 G2 G3 G4 G5 G6 G7 || deny "GitHub writes need all gates G1-G7 approved and unchanged (not valid:$MISSING_GATES)"
  if [ "$CFG_REQ_ASK" = "1" ]; then
    audit_has_ask G7 || deny "no approval question for G7 is recorded in the audit log"
  fi
  wb=${ST_branch:-}; bb=${ST_base_branch:-}
  [ -n "$wb" ] || deny "no feature branch recorded in workflow state"
  is_protected "$wb" && deny "workflow branch '$wb' is protected"
  case "$name" in
    create_pull_request)
      head=${T_head:-}; head=${head##*:}; base=${T_base:-}
      [ "$head" = "$wb" ] || deny "PR head '$head' is not the workflow branch '$wb'"
      is_protected "$head" && deny "PR head '$head' is a protected branch"
      [ -z "$bb" ] || [ "$base" = "$bb" ] || deny "PR base '$base' is not the recorded base branch '$bb'" ;;
    push_files|create_or_update_file|create_branch)
      br=${T_branch:-}
      [ "$br" = "$wb" ] || deny "target branch '$br' is not the workflow branch '$wb'"
      is_protected "$br" && deny "target branch '$br' is protected" ;;
  esac
  audit ALLOW "$name on $wb (gates G1-G7 valid)"
  exit 0
}

# ------------------------------------------------------------ SubagentStart / SubagentStop
agent_skills() { # sets AS to the comma separated skills in the agent's frontmatter
  AS=; f="$PROJECT_ROOT/.claude/agents/$1.md"
  [ -f "$f" ] || return 0
  _on=0; _first=1
  while IFS= read -r _l || [ -n "$_l" ]; do
    if [ "$_first" = 1 ]; then _first=0; continue; fi
    case $_l in
      '---') break ;;
      'skills:'*) _on=1 ;;
      '  - '*) [ "$_on" = 1 ] && AS="${AS}${AS:+,}${_l#'  - '}" ;;
      [A-Za-z]*) _on=0 ;;
    esac
  done < "$f"
}

evidence_problems() { # verification-report.md: every PASS needs exit 0 and an executed command in the audit log
  rep="$PROJECT_ROOT/docs/sdlc/$KEY/verification-report.md"
  [ -f "$rep" ] || return 0
  awk '/^cmd:/ {sub(/^cmd: */,""); c=$0; e=""; next}
       /^exit:/ {sub(/^exit: */,""); e=$0; next}
       /^result:/ {sub(/^result: */,""); if ($0 ~ /^PASS/) { print e; print c } }' "$rep" | while IFS= read -r e && IFS= read -r c; do
    [ -n "$c" ] || continue
    [ "$e" = "0" ] || { printf 'PASS recorded with exit %s for: %s\n' "${e:-?}" "$c"; continue; }
    frag=$(sanitize "$c" | cut -c1-150)
    grep '|TOOL|verification-agent|Bash ok ' "$AUDIT_LOG" 2>/dev/null | grep -qF -- "$frag" || printf 'PASS claimed but no successful execution is in the audit log for: %s\n' "$c"
  done
}

section_problems() { # section_problems FILE -> missing required headings
  _f=$1; _b=${_f##*/}; _want=
  while IFS='|' read -r _n _h; do
    [ "$_n" = "$_b" ] && { _want=$_h; break; }
  done < "$HOOK_DIR/artifact-sections.conf"
  [ -n "$_want" ] || return 0
  _o=$IFS; IFS=,; set -f; set -- $_want; set +f; IFS=$_o
  for _frag in "$@"; do
    grep -qi "^#.*$_frag" "$_f" || printf '%s: missing section "%s"\n' "$_b" "$_frag"
  done
}

check_subagent_stop() {
  msg=${J_last_assistant_message:-}
  again=${J_stop_hook_active:-false}
  status=; case $msg in *'STATUS:'*) status=${msg##*STATUS:}; status=${status# }; status=${status%%[!A-Z_]*} ;; esac
  audit SUBAGENT_STOP "type=$AGENT id=${J_agent_id:-} status=${status:-none}"
  stage_of_agent "$AGENT"; [ -n "$SA" ] || exit 0
  problems=""
  case "$status" in
    COMPLETE|NEEDS_CLARIFICATION|NEEDS_APPROVAL|BLOCKED|FAILED) ;;
    *) problems="Your reply must end with the structured return: a line 'STATUS: <COMPLETE|NEEDS_CLARIFICATION|NEEDS_APPROVAL|BLOCKED|FAILED>', then ARTIFACT, QUESTIONS and SUMMARY." ;;
  esac
  if [ "$status" = "COMPLETE" ]; then
    req=
    while IFS='|' read -r _n _nm _a _r; do
      [ "$_a" = "$AGENT" ] && { req=$_r; break; }
    done < "$HOOK_DIR/stages.conf"
    d="$PROJECT_ROOT/docs/sdlc/$KEY"
    case "$req" in
      */*) found=0; _o=$IFS; IFS=/; set -f; set -- $req; set +f; IFS=$_o
           for f in "$@"; do [ -s "$d/$f" ] && found=1; done
           [ "$found" = 1 ] || problems="$problems
Required artifact missing or empty (need one of: $req) in docs/sdlc/$KEY/." ;;
      *)   _o=$IFS; IFS=,; set -f; set -- $req; set +f; IFS=$_o
           for f in "$@"; do [ -s "$d/$f" ] || problems="$problems
Required artifact missing or empty: docs/sdlc/$KEY/$f"; done ;;
    esac
    _all=$(printf '%s' "$req" | tr ',/' '  ')
    for f in $_all; do
      if [ -s "$d/$f" ]; then
        sp=$(section_problems "$d/$f"); [ -z "$sp" ] || problems="$problems
$sp"
      fi
    done
    if [ "$AGENT" = "verification-agent" ]; then
      ep=$(evidence_problems); [ -z "$ep" ] || problems="$problems
$ep"
    fi
  fi
  [ -n "$problems" ] || exit 0
  if [ "$again" = "true" ]; then
    audit STOP_CHECK_FAILED "$AGENT: not blocking again (stop_hook_active): $(printf '%s' "$problems" | tr '\n' ' ')"
    exit 0
  fi
  audit STOP_BLOCKED "$AGENT: $(printf '%s' "$problems" | tr '\n' ' ')"
  printf 'SDLC framework check failed for %s. Fix these, then finish again:%s\n' "$AGENT" "$problems" >&2
  exit 2
}

case "$EVENT" in
  PreToolUse)
    case "$TOOL" in
      Agent)          check_delegation ;;
      mcp__github__*) check_github ;;
    esac ;;
  SubagentStart)
    agent_skills "$AGENT"
    audit SUBAGENT_START "type=$AGENT id=${J_agent_id:-} skills=$AS" ;;
  SubagentStop) check_subagent_stop ;;
esac
exit 0
