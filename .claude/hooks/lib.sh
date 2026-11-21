# lib.sh - shared helpers for the SDLC framework hooks.
# POSIX sh + awk/grep/date/git only; no language runtime and no JSON tool. Sourced by the hook scripts.
# Performance matters (process creation is slow on some platforms): hot paths use shell builtins and at most a
# few awk calls. Contract: hooks are DORMANT (exit 0 before reading input) when .sdlc/active is missing or empty;
# while a workflow is active they fail CLOSED (exit 2 on blocking events) if input or state cannot be understood.

# ---------- locate the project without forking ----------
case $0 in
  */.claude/hooks/*) HOOK_DIR=${0%/*}; PROJECT_ROOT=${HOOK_DIR%/.claude/hooks} ;;   # also covers mixed C:\dir/.claude/hooks/x.sh
  *\\*) HOOK_DIR=$(cd "${0%\\*}" && pwd); PROJECT_ROOT=$(cd "$HOOK_DIR/../.." && pwd) ;;
  *) HOOK_DIR=$(cd "$(dirname "$0")" && pwd); PROJECT_ROOT=$(cd "$HOOK_DIR/../.." && pwd) ;;
esac
STATE_DIR="$PROJECT_ROOT/.sdlc/state"
LF='
'

# ---------- lower-casing without forking where the shell allows ----------
if [ -n "${BASH_VERSION:-}" ]; then
  lc() { eval "$1=\${2,,}"; }
  now_utc() { TZ=UTC printf '%(%Y-%m-%dT%H:%M:%SZ)T' -1; }
else
  lc() { eval "$1=\$(printf '%s' \"\$2\" | tr 'A-Z' 'a-z')"; }
  now_utc() { date -u +%Y-%m-%dT%H:%M:%SZ; }
fi

# ---------- JSON: top-level keys only (look-alike keys nested in strings/objects are ignored) ----------

# parse_kv PREFIX "key1 key2 ..." JSON [DECODE=1]  -> shell variables PREFIXkey1, PREFIXkey2 ...
parse_kv() {
  _out=$(printf '%s' "$3" | awk -v want="$2" -v decode="${4:-1}" -f "$HOOK_DIR/json.awk")
  while IFS= read -r _l; do
    case $_l in ?*=*) _k=${_l%%=*}; _v=${_l#*=}; eval "$1$_k=\$_v" ;; esac
  done <<EOF
$_out
EOF
}

# ---------- paths ----------
# rel_path PATH -> REL (relative to the repository root, original case; "." for the root; OUTSIDE if elsewhere)
#                  RELM (lower-case copy for matching). One awk call.
rel_path() {
  _o=$(printf '%s\n%s\n' "$1" "$PROJECT_ROOT" | awk '
    function norm(x) { gsub(/\\/,"/",x); if (x ~ /^\/[A-Za-z]\//) x=substr(x,2,1) ":" substr(x,3); return x }
    function canon(x,   n,a,i,m,b,out) {
      n=split(x,a,"/"); m=0
      for (i=1;i<=n;i++) { if (a[i]==""||a[i]==".") continue; if (a[i]=="..") { if (m>0) m--; continue } m++; b[m]=a[i] }
      out=""; for (i=1;i<=m;i++) out=out (i>1?"/":"") b[i]
      if (substr(x,1,1)=="/") out="/" out
      return out }
    NR==1 { p=norm($0) } NR==2 { r=norm($0) }
    END {
      if (p !~ /^\// && p !~ /^[A-Za-z]:\//) p=r "/" p
      p=canon(p); r=canon(r); pl=tolower(p); rl=tolower(r)
      if (pl==rl) out="."
      else if (index(pl, rl "/")==1) out=substr(p, length(r)+2)
      else out="OUTSIDE"
      print out; print tolower(out) }')
  REL=${_o%%"$LF"*}; RELM=${_o#*"$LF"}
}

# subst_key STRING -> SK (STRING lower-cased, <key> replaced by the lower-case issue key)
subst_key() {
  lc _s "$1"; _r=
  while :; do
    case $_s in
      *'<key>'*) _r=$_r${_s%%'<key>'*}$KEYL; _s=${_s#*'<key>'} ;;
      *) break ;;
    esac
  done
  SK=$_r$_s
}

# match_any STRING(lower-case) GLOBS(already lower-case, comma separated) -> 0 if any glob matches
match_any() {
  _s=$1; _o=$IFS; IFS=,; set -f; set -- $2; set +f; IFS=$_o
  for _p in "$@"; do
    [ -n "$_p" ] || continue
    case $_s in $_p) return 0 ;; esac
  done
  return 1
}

# ---------- configuration ----------
CFG_AUDIT=1; CFG_REQ_ASK=0
load_cfg() {
  [ -f "$HOOK_DIR/hooks.conf" ] || return 0
  while IFS== read -r _k _v || [ -n "$_k" ]; do
    _v=${_v%%[[:space:]]*}
    case $_k in
      AUDIT_ENABLED) CFG_AUDIT=$_v ;;
      REQUIRE_GATE_QUESTION_IN_AUDIT) CFG_REQ_ASK=$_v ;;
    esac
  done < "$HOOK_DIR/hooks.conf"
}

# ---------- workflow state ----------
KEY=""; KEYL=""; KEY_BAD=0; STATE_FILE=""; LEDGER=""; AUDIT_LOG=""; AGENT=""; STATE_OK=0

load_state() {
  STATE_OK=0
  [ -f "$STATE_FILE" ] || return 0
  while IFS= read -r _l || [ -n "$_l" ]; do
    case $_l in
      '  "'*'": '*)
        _k=${_l#'  "'}; _k=${_k%%'"'*}
        case $_k in ''|*[!a-z0-9_]*) continue ;; esac
        _v=${_l#*'": '}; _v=${_v%,}
        case $_v in '"'*'"') _v=${_v#'"'}; _v=${_v%'"'} ;; esac
        eval "ST_$_k=\$_v" ;;
    esac
  done < "$STATE_FILE"
  [ -n "${ST_current_stage:-}" ] && STATE_OK=1
}
state_ok() { [ "$STATE_OK" = 1 ]; }
sv() { eval "SV=\${ST_$1:-}"; }   # sv NAME -> SV (value of a state key)

load_ledger() {
  [ -f "$LEDGER" ] || return 0
  while read -r _g _h _rest; do
    case $_g in G[0-9]*) eval "LG_$_g=\$_h" ;; esac
  done < "$LEDGER"
}

# load_workflow: return 1 (dormant) unless a workflow is active. Builtins only.
load_workflow() {
  _f="$PROJECT_ROOT/.sdlc/active"
  [ -s "$_f" ] || return 1
  IFS= read -r _raw < "$_f" || [ -n "$_raw" ]
  _raw=${_raw%%[[:space:]]*}
  [ -n "$_raw" ] || return 1
  case $_raw in
    *[!A-Za-z0-9._-]*|[!A-Za-z0-9]*) KEY_BAD=1; return 0 ;;
  esac
  KEY=$_raw; lc KEYL "$KEY"
  STATE_FILE="$STATE_DIR/$KEY.json"; LEDGER="$STATE_DIR/$KEY.hashes"; AUDIT_LOG="$PROJECT_ROOT/.sdlc/audit/$KEY.log"
  load_cfg; load_state; load_ledger
  return 0
}

# ---------- git ----------
current_branch() { # sets CB
  CB=
  if [ -f "$PROJECT_ROOT/.git/HEAD" ]; then
    IFS= read -r _h < "$PROJECT_ROOT/.git/HEAD"
    case $_h in 'ref: refs/heads/'*) CB=${_h#'ref: refs/heads/'}; CB=${CB%%[[:space:]]*} ;; esac
  else
    CB=$(git -C "$PROJECT_ROOT" symbolic-ref --short -q HEAD 2>/dev/null)
  fi
  [ -n "$CB" ] || CB='(detached)'
}

is_protected() { # 0 = protected (also for detached/empty or unreadable list: fail closed)
  lc _b "$1"
  case $_b in ''|'(detached)') return 0 ;; esac
  [ -f "$HOOK_DIR/protected-branches.txt" ] || return 0
  while IFS= read -r _pat || [ -n "$_pat" ]; do
    case $_pat in ''|'#'*) continue ;; esac
    _pat=${_pat%%[[:space:]]*}
    lc _pl "$_pat"
    case $_b in $_pl) return 0 ;; esac
  done < "$HOOK_DIR/protected-branches.txt"
  return 1
}

# ---------- approval gates ----------
gate_files() { # sets GF (space separated) for gate $1
  GF=
  while IFS='|' read -r _g _s _a _d; do
    [ "$_g" = "$1" ] && { GF=$_a; break; }
  done < "$HOOK_DIR/gates.conf"
  _t=$GF; GF=
  while :; do
    case $_t in
      *,*) GF="$GF ${_t%%,*}"; _t=${_t#*,} ;;
      *) GF="$GF $_t"; break ;;
    esac
  done
}

if command -v sha256sum >/dev/null 2>&1; then HASH_CMD='sha256sum'
elif command -v shasum >/dev/null 2>&1; then HASH_CMD='shasum -a 256'
elif command -v openssl >/dev/null 2>&1; then HASH_CMD='openssl dgst -sha256'
else HASH_CMD='cksum'; fi

gate_hash() { # sets GH: content hash of the gate's artifacts, or MISSING
  gate_files "$1"; GH=MISSING
  [ -n "$GF" ] || return 0
  for _f in $GF; do [ -f "$PROJECT_ROOT/docs/sdlc/$KEY/$_f" ] || return 0; done
  GH=$( for _f in $GF; do printf '%s\n' "$_f"; cat "$PROJECT_ROOT/docs/sdlc/$KEY/$_f"; done | $HASH_CMD )
  GH=${GH##*= }; GH=${GH%% *}
}

gate_valid() { # full check: approved in state AND recorded content hash still matches
  lc _lg "$1"; eval "_st=\${ST_gate_$_lg:-}"; [ "$_st" = approved ] || return 1
  eval "_rec=\${LG_$1:-}"; [ -n "$_rec" ] || return 1
  gate_hash "$1"; [ "$_rec" = "$GH" ]
}

gate_valid_fast() { # cheap check for frequent actions: approved, recorded, and no artifact newer than its stamp
  lc _lg "$1"; eval "_st=\${ST_gate_$_lg:-}"; [ "$_st" = approved ] || return 1
  eval "_rec=\${LG_$1:-}"; [ -n "$_rec" ] || return 1
  _stamp="$STATE_DIR/$KEY.stamp.$1"; [ -f "$_stamp" ] || return 1
  gate_files "$1"
  for _f in $GF; do
    _p="$PROJECT_ROOT/docs/sdlc/$KEY/$_f"
    [ -f "$_p" ] || return 1
    [ "$_p" -nt "$_stamp" ] && return 1
  done
  return 0
}

gates_valid()      { MISSING_GATES=; for _g in "$@"; do gate_valid "$_g"      || MISSING_GATES="$MISSING_GATES $_g"; done; [ -z "$MISSING_GATES" ]; }
gates_valid_fast() { MISSING_GATES=; for _g in "$@"; do gate_valid_fast "$_g" || MISSING_GATES="$MISSING_GATES $_g"; done; [ -z "$MISSING_GATES" ]; }

stage_of_agent() { # sets SA to the stage number of an SDLC agent, empty if not one
  SA=
  while IFS='|' read -r _n _nm _a _r; do
    case $_n in ''|'#'*) continue ;; esac
    [ "$_a" = "$1" ] && { SA=$_n; return 0; }
  done < "$HOOK_DIR/stages.conf"
  return 1
}

# ---------- audit + output ----------
sanitize() { # one line, secrets redacted, at most 240 characters
  printf '%s' "$1" | awk 'BEGIN{RS="\001"; split("password passwd token secret apikey api_key api-key authorization bearer", kw, " "); nk=9}
    { line=$0; gsub(/[\n\r\t]/," ",line); out=""
      while (1) {
        low=tolower(line); best=0; bl=0
        for (i=1;i<=nk;i++) { p=index(low,kw[i]); if (p>0 && (best==0||p<best)) { best=p; bl=length(kw[i]) } }
        if (!best) break
        head=substr(line,1,best+bl-1); rest=substr(line,best+bl)
        if (match(rest,/^[=: ]+((Bearer|bearer|BEARER|Basic|basic|BASIC) +)?[^ ]+/)) {
          full=RLENGTH; match(rest,/^[=: ]+/)
          out=out head substr(rest,1,RLENGTH) "[REDACTED]"; line=substr(rest,full+1)
        } else { out=out head; line=rest }
      }
      print substr(out line,1,240) }'
}

audit() { # audit EVENT DETAIL
  [ "$CFG_AUDIT" = 1 ] && [ -n "$KEY" ] || return 0
  [ -d "$PROJECT_ROOT/.sdlc/audit" ] || mkdir -p "$PROJECT_ROOT/.sdlc/audit" 2>/dev/null || return 0
  _d=$(sanitize "$2")
  printf '%s|%s|%s|%s\n' "$(now_utc)" "$1" "${AGENT:--}" "$_d" >> "$AUDIT_LOG" 2>/dev/null
  return 0
}

audit_has_ask() { [ -f "$AUDIT_LOG" ] && grep '|ASK|' "$AUDIT_LOG" | grep -q "$1"; }

deny() { # blocking message to the model on stderr, exit 2
  audit DENY "$EVENT $TOOL: $*"
  printf 'BLOCKED by SDLC framework hook: %s\n' "$*" >&2
  exit 2
}

json_escape() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | awk '{ printf "%s%s", (NR>1 ? "\\n" : ""), $0 }'; }
context_out() { printf '{"hookSpecificOutput":{"hookEventName":"%s","additionalContext":"%s"}}\n' "$1" "$(json_escape "$2")"; }

# hook_init: dormant check first (no input read), then parse the common fields
hook_init() {
  load_workflow || exit 0
  INPUT=; while IFS= read -r _l || [ -n "$_l" ]; do INPUT=$INPUT$_l; done
  parse_kv J_ "hook_event_name tool_name agent_type agent_id tool_input stop_hook_active last_assistant_message error" "$INPUT"
  EVENT=${J_hook_event_name:-}; TOOL=${J_tool_name:-}; AGENT=${J_agent_type:-}; TI=${J_tool_input:-}
  if [ -z "$EVENT" ]; then
    printf 'SDLC hook: cannot parse hook input while a workflow is active (failing closed)\n' >&2
    exit 2
  fi
  if [ -n "$TI" ]; then
    parse_kv T_ "file_path notebook_path path command content new_string subagent_type prompt head base branch skill questions" "$TI"
  fi
}
