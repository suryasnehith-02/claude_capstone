#!/bin/sh
# pre-implementation-check.sh - PreToolUse hook for Write|Edit|MultiEdit|NotebookEdit and Bash.
# While an SDLC workflow is active it enforces: per-agent write policy by stage (agent-paths.conf),
# approval gates and branch protection before source edits, and safe shell usage (git writes limited to the PR manager).
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
[ "$EVENT" = "PreToolUse" ] || exit 0
[ "$KEY_BAD" = 0 ] || deny "invalid issue key in .sdlc/active"

GLOBAL_DENY='.git/*,.claude/*,.sdlc/audit/*,.sdlc/state/*.hashes,.sdlc/state/*.stamp.*,.env,*/.env,.env.*,*/.env.*,*.pem,*.key,*id_rsa*,*.p12,*.pfx'
STAGE=${ST_current_stage:-}

# ---------------------------------------------------------------- Write / Edit
check_write() {
  p=${T_file_path:-}; [ -n "$p" ] || p=${T_notebook_path:-}; [ -n "$p" ] || p=${T_path:-}
  [ -n "$p" ] || deny "cannot determine the target path of this edit"
  rel_path "$p"
  [ "$REL" != "OUTSIDE" ] || deny "edits outside the repository are not allowed while a workflow is active ($p)"

  match_any "$RELM" "$GLOBAL_DENY" && deny "protected path: $REL"

  if ! state_ok; then
    case $RELM in .sdlc/*) exit 0 ;; esac
    deny "workflow $KEY is active but its state file is missing or unreadable; only .sdlc/ may be written"
  fi

  find_row() {
    while IFS='|' read -r _a _st _al _dn _rq; do
      case $_a in ''|'#'*) continue ;; esac
      if [ "$_a" = "$1" ]; then R_stages=$_st; R_allow=$_al; R_deny=$_dn; R_requires=$_rq; return 0; fi
    done < "$HOOK_DIR/agent-paths.conf"
    return 1
  }
  find_row "${AGENT:--}" || find_row "-" || deny "no write policy found for ${AGENT:-the main thread}"
  who=${AGENT:-the Orchestrator}

  case $R_stages in
    '*') ;;
    *) case ",$R_stages," in *",$STAGE,"*) ;; *) deny "$who may not write during stage $STAGE (allowed stages: $R_stages)" ;; esac ;;
  esac
  if [ -n "$R_deny" ]; then
    subst_key "$R_deny"; match_any "$RELM" "$SK" && deny "$who may not modify $REL"
  fi
  subst_key "$R_allow"; match_any "$RELM" "$SK" || deny "$REL is outside the paths $who may write"

  case $R_requires in
    *source-gates*)
      gates_valid_fast G1 G2 G3 G4 || deny "source edits need approved, unchanged gates G1-G4 (not valid:$MISSING_GATES)"
      current_branch; b=$CB
      is_protected "$b" && deny "current branch '$b' is protected or detached; source edits are only allowed on the workflow feature branch"
      wb=${ST_branch:-}
      [ -n "$wb" ] || deny "no feature branch recorded in workflow state; run branch preparation first"
      [ "$b" = "$wb" ] || deny "current branch '$b' is not the workflow branch '$wb'"
      ;;
  esac

  if [ "$REL" = ".sdlc/state/$KEY.json" ] && [ "$CFG_REQ_ASK" = "1" ]; then
    newtxt=${T_content:-}; [ -n "$newtxt" ] || newtxt=${T_new_string:-}
    for n in 1 2 3 4 5 6 7; do
      case $newtxt in
        *"\"gate_g$n\": \"approved\""*)
          eval "_cur=\${ST_gate_g$n:-}"
          [ "$_cur" = "approved" ] || audit_has_ask "G$n" || deny "gate G$n cannot be recorded as approved: no AskUserQuestion for G$n is in the audit log" ;;
      esac
    done
  fi
  exit 0
}

# ---------------------------------------------------------------- Bash
write_like() { # 0 if the normalised command (benign redirects already removed) appears to write files
  case " $1 " in
    *">"*|*" tee "*|*" sed -i"*|*" mv "*|*" cp "*|*" truncate "*|*" dd "*|*" chmod "*|*" ln "*|*" install "*|*" touch "*|*" mkdir "*|*" perl -i"*) return 0 ;;
  esac
  return 1
}

publish_branch_ok() {
  current_branch; b=$CB
  is_protected "$b" && deny "current branch '$b' is protected or detached"
  wb=${ST_branch:-}
  [ -n "$wb" ] && [ "$b" = "$wb" ] || deny "current branch '$b' is not the recorded workflow branch '${wb:-none}'"
}

check_bash() {
  cmd=${T_command:-}
  [ -n "$cmd" ] || deny "cannot read the shell command"
  norm=$(printf '%s' "$cmd" | awk 'BEGIN{RS="\001"} {
    s=tolower($0); gsub(/[\n\r\t]/," ",s); gsub(/  +/," ",s); rl=s
    c=s; gsub(/[;&|()]+/," ; ",c); gsub(/git( +(-c|-C) +[^ ]+| +--[a-z-]+(=[^ ]+)?| +-p)+/,"git",c); gsub(/  +/," ",c)
    x=c; gsub(/2>&1/,"",x); gsub(/&>\/dev\/null/,"",x); gsub(/[0-9]*> ?\/dev\/null/,"",x)
    print rl; print c; print x }')
  rl=${norm%%"$LF"*}; rest=${norm#*"$LF"}; cl=${rest%%"$LF"*}; cs=${rest#*"$LF"}

  case "$rl" in
    *"| sh"*|*"|sh "*|*"| bash"*|*"|bash "*|*"| zsh"*|*"| sudo"*|*" sudo "*|sudo*) deny "piping into a shell or using sudo is not allowed" ;;
  esac
  cw=" $cl "
  has() { case "$cw" in *"$1"*) return 0 ;; esac; return 1; }

  if { has ".sdlc" || has ".claude/"; } && write_like "$cs"; then
    deny "shell commands may not modify workflow state, audit data or framework configuration"
  fi

  has " rm " && deny "deleting files from the shell is not allowed; request approval from the human instead"
  { has " rmdir " || has " unlink " || has " shred " || has " git rm " || has " git clean"; } && deny "deleting files is not allowed without human approval"
  has " git reset --hard" && deny "git reset --hard would discard work"
  { has " git checkout --" || has " git checkout ." || has " git restore" || has " git stash drop" || has " git stash clear"; } && deny "this git command would discard uncommitted work"
  { has " git branch -d " || has " git branch --delete" || has " git push --delete" || has " git tag -d"; } && deny "deleting branches or tags is not allowed"
  { has " git merge" || has " git rebase" || has " git pull" || has " git cherry-pick"; } && deny "merging, rebasing and pulling are not allowed (merge decisions belong to the human)"
  { has " git config" || has " git remote add" || has " git remote set-url" || has " git remote remove" || has " git remote rename"; } && deny "changing git configuration or remotes is not allowed"

  if has " git push"; then
    case "$cw" in *" --force"*|*" -f "*|*"--force-with-lease"*|*" +"*) deny "force pushes are not allowed" ;; esac
    [ "$AGENT" = "pr-manager" ] || deny "only the pr-manager may push, and only after final approval"
    [ "$STAGE" = "8" ] || deny "pushing is only allowed in stage 8 (current stage: $STAGE)"
    gates_valid G1 G2 G3 G4 G5 G6 G7 || deny "pushing needs all gates G1-G7 approved and unchanged (not valid:$MISSING_GATES)"
    publish_branch_ok
    args=${cl#*git push}; args=${args%%;*}
    set -f
    for t in $args; do
      case "$t" in -*) continue ;; esac
      t=${t##*:}
      lc _tl "$t"; lc _wl "${ST_branch:-}"
      is_protected "$t" && [ "$_tl" != "$_wl" ] && deny "pushing to protected branch '$t' is not allowed"
    done
    set +f
    audit ALLOW "git push (stage 8, gates valid)"
    exit 0
  fi

  if has " git commit" || has " git add" || has " git mv"; then
    [ "$AGENT" = "pr-manager" ] || deny "only the pr-manager may stage or commit, and only in stage 8 after final approval"
    [ "$STAGE" = "8" ] || deny "committing is only allowed in stage 8 (current stage: $STAGE)"
    gates_valid G1 G2 G3 G4 G5 G6 G7 || deny "committing needs all gates G1-G7 approved and unchanged (not valid:$MISSING_GATES)"
    publish_branch_ok
    { has " git add -a" || has " git add --all" || has " git add ." || has " git add *" || has " git commit -a" || has " git commit --all"; } && deny "stage files by explicit path; blanket staging is not allowed"
    audit ALLOW "git stage/commit (stage 8, gates valid)"
    exit 0
  fi

  if has " git switch" || has " git checkout" || has " git worktree" || has " git stash" || has " git tag"; then
    case "$cw" in *" git stash list"*|*" git stash show"*) ;; *)
      [ "$AGENT" = "pr-manager" ] || deny "only the pr-manager may switch or create branches"
      case "$STAGE" in 4|5|8) ;; *) deny "branch changes are only allowed during branch preparation or stage 8" ;; esac ;;
    esac
  fi
  if has " git branch"; then
    case "$cw" in
      *" git branch --show-current"*|*" git branch --list"*|*" git branch -a"*|*" git branch -r"*|*" git branch -v"*|*" git branch --contains"*|*" git branch --merged"*|*" git branch --no-merged"*) ;;
      *" git branch ; "*|*" git branch ") ;;
      *) [ "$AGENT" = "pr-manager" ] || deny "only the pr-manager may create or rename branches" ;;
    esac
  fi

  case "${AGENT:-}" in
    solution-architect|code-reviewer|design-reviewer|implementation-planner)
      write_like "$cs" && deny "$AGENT is read-only in the shell" ;;
    requirements-analyst)
      case "$cl" in *"docs/sdlc/$KEYL/attachments/"*) ;; *) deny "requirements-analyst may only run the attachment download command into docs/sdlc/$KEY/attachments/" ;; esac ;;
  esac
  exit 0
}

case "$TOOL" in
  Write|Edit|MultiEdit|NotebookEdit) check_write ;;
  Bash) check_bash ;;
esac
exit 0
