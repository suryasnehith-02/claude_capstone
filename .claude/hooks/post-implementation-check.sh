#!/bin/sh
# post-implementation-check.sh - PostToolUse / PostToolUseFailure hook.
# 1) Appends an audit entry for commands, edits, skills, delegations, MCP calls and approval questions.
# 2) Maintains the approval-hash ledger (.sdlc/state/<KEY>.hashes) whenever the workflow state file changes.
# 3) Warns (never blocks; PostToolUse cannot) about approvals whose artifacts changed and edits outside the plan scope.
# Dormant (exit 0, input unread) when no workflow is active.
case $0 in
  */.claude/hooks/*) _D=${0%/*}; _R=${_D%/.claude/hooks} ;;
  *\\*) _D=${0%\\*}; _R=${_D%\\.claude\\hooks} ;;
  */*) _D=${0%/*}; _R=. ;;
  *) _D=.; _R=. ;;
esac
[ "$_R" = . ] || [ -s "$_R/.sdlc/active" ] || exit 0   # dormant fast path: no active workflow, no work
. "$_D/lib.sh"
hook_init
case "$EVENT" in PostToolUse|PostToolUseFailure) ;; *) exit 0 ;; esac
[ "$KEY_BAD" = 0 ] || exit 0

status=ok; [ "$EVENT" = "PostToolUseFailure" ] && status=failed
WARN=""
warn() { WARN="${WARN}${WARN:+ }$*"; }

maintain_ledger() {
  state_ok || return 0
  [ -f "$LEDGER" ] || : > "$LEDGER"
  while IFS='|' read -r g _s _a _d; do
    case $g in ''|'#'*) continue ;; esac
    lc lg "$g"; eval "st=\${ST_gate_$lg:-}"; eval "rec=\${LG_$g:-}"
    if [ "$st" = "approved" ]; then
      if [ -z "$rec" ]; then
        gate_hash "$g"
        if [ "$GH" = "MISSING" ]; then
          warn "Gate $g is marked approved but its artifact files are missing, so the approval is NOT valid. Set it back to pending."
        else
          printf '%s %s\n' "$g" "$GH" >> "$LEDGER"; : > "$STATE_DIR/$KEY.stamp.$g"; audit LEDGER "recorded $g"
        fi
      elif ! gate_valid "$g"; then
        warn "Gate $g is marked approved but its artifacts changed since approval. Set it to invalidated and obtain approval again."
      fi
    elif [ -n "$rec" ]; then
      grep -v "^$g " "$LEDGER" > "$LEDGER.tmp"; mv "$LEDGER.tmp" "$LEDGER"; rm -f "$STATE_DIR/$KEY.stamp.$g"
      audit LEDGER "cleared $g (state is $st)"
    fi
  done < "$HOOK_DIR/gates.conf"
}

case "$TOOL" in
  Bash)
    if [ "$status" = "failed" ]; then
      code=?; case ${J_error:-} in 'Exit code '*) code=${J_error#'Exit code '}; code=${code%%[!0-9]*} ;; esac
      audit TOOL "Bash failed exit=$code ${T_command:-}"
    else
      audit TOOL "Bash ok ${T_command:-}"
    fi ;;
  Write|Edit|MultiEdit|NotebookEdit)
    p=${T_file_path:-}; [ -n "$p" ] || p=${T_notebook_path:-}
    rel_path "$p"
    audit FILE "$TOOL $status $REL"
    if [ "$status" = "ok" ]; then
      [ "$REL" = ".sdlc/state/$KEY.json" ] && maintain_ledger
      if [ "$AGENT" = "developer" ] && [ -f "$PROJECT_ROOT/docs/sdlc/$KEY/impl-plan.md" ]; then
        case $RELM in docs/sdlc/*|.sdlc/*) ;; *)
          grep -qiF -- "$REL" "$PROJECT_ROOT/docs/sdlc/$KEY/impl-plan.md" || { audit SCOPE_WARN "$REL is not in the impl-plan scope manifest"; warn "$REL is not listed in impl-plan.md. If it is outside the approved scope, stop and return NEEDS_APPROVAL."; } ;;
        esac
      fi
    fi ;;
  Skill) audit SKILL "${T_skill:-} $status" ;;
  Agent)
    mode=; case ${T_prompt:-} in *'MODE: '*) mode=${T_prompt#*'MODE: '}; mode="MODE: ${mode%%[!a-z]*}" ;; esac
    audit DELEGATE "subagent_type=${T_subagent_type:-} $mode $status" ;;
  AskUserQuestion)
    h=; s=${T_questions:-}
    while :; do
      case $s in
        *'"header":"'*) s=${s#*'"header":"'}; h="$h${s%%\"*}," ;;
        *'"header": "'*) s=${s#*'"header": "'}; h="$h${s%%\"*}," ;;
        *) break ;;
      esac
    done
    parse_kv R_ "tool_response" "$INPUT" 0
    audit ASK "headers=$h $status response=$(printf '%.160s' "${R_tool_response:-}")" ;;
  mcp__*) audit MCP "$TOOL $status" ;;
esac

[ -z "$WARN" ] || context_out "$EVENT" "SDLC framework warning: $WARN"
exit 0
