#!/bin/sh
# session-status.sh - SessionStart hook. Injects a short workflow status into the session so an interrupted
# workflow can be resumed. Dormant (no output, input unread) when no workflow is active.
case $0 in
  */.claude/hooks/*) _D=${0%/*}; _R=${_D%/.claude/hooks} ;;
  *\\*) _D=${0%\\*}; _R=${_D%\\.claude\\hooks} ;;
  */*) _D=${0%/*}; _R=. ;;
  *) _D=.; _R=. ;;
esac
[ "$_R" = . ] || [ -s "$_R/.sdlc/active" ] || exit 0   # dormant fast path: no active workflow, no work
. "$_D/lib.sh"
hook_init
[ "$KEY_BAD" = 0 ] || { context_out SessionStart "SDLC framework: .sdlc/active holds an invalid issue key. Fix or clear it."; exit 0; }
state_ok || { context_out SessionStart "SDLC framework: workflow $KEY is marked active but .sdlc/state/$KEY.json is missing or unreadable. Do not proceed until this is resolved with the human."; exit 0; }

stages=""; interrupted=""
for n in 1 2 3 4 5 6 7 8; do
  eval "v=\${ST_stage_${n}_status:-}"
  stages="$stages $n=$v"
  [ "$v" = "in_progress" ] && interrupted="$interrupted $n"
done
gates=""; warn=""
while IFS='|' read -r g _s _a _d; do
  case $g in ''|'#'*) continue ;; esac
  lc lg "$g"; eval "st=\${ST_gate_$lg:-}"
  if [ "$st" = "approved" ]; then
    if gate_valid "$g"; then :; else st="APPROVED-BUT-NOT-VALID"; warn="$warn $g"; fi
  fi
  gates="$gates $g=$st"
done < "$HOOK_DIR/gates.conf"

msg="SDLC workflow $KEY (${ST_jira_summary:-}) - workflow_status=${ST_workflow_status:-}, current_stage=${ST_current_stage:-}.
Stages:$stages
Gates:$gates
Branch: ${ST_branch:-} (base: ${ST_base_branch:-}). Open critical=${ST_open_critical:-0} major=${ST_open_major:-0} waived_critical=${ST_waived_critical:-0}. Verification=${ST_verification_status:-}. PR: ${ST_pr_url:-}
Next action: ${ST_next_action:-}"
[ -z "$warn" ] || msg="$msg
WARNING: approvals for$warn are not valid (artifacts changed or hash missing). Treat them as invalidated and re-run the gate."
[ -z "$interrupted" ] || msg="$msg
NOTE: stage(s)$interrupted were in_progress when the last session ended; re-run them on resume."
msg="$msg
Resume with: /sdlc resume $KEY"
audit SESSION "start status=${ST_workflow_status:-} stage=${ST_current_stage:-}"
context_out SessionStart "$msg"
exit 0
