# Delegation message template

Send this as the prompt when delegating to a specialist. Fill every field; pass paths, not document bodies.

```
WORKFLOW: <KEY>   STAGE: <n> <stage name>   ATTEMPT: <k> of 3   MODE: <default | prepare | publish | rework | clarified>
JIRA SITE: <host>              REPO ROOT: <path>
ARTIFACT DIR: docs/sdlc/<KEY>/

INPUTS (read these; they are the only context you have):
- <path> - <one-line description>
- ...

TASK: <what this stage must produce, in one or two sentences>

CLARIFICATION ANSWERS (if re-invoked after questions):
- Q<n>: <question> -> <human answer or recorded assumption>

REWORK FINDINGS (if re-invoked after a failed check or review):
- <id> <severity> <file or section> - <what to fix>

CONSTRAINTS:
- Write only your own artifact(s): <paths>
- Do not guess; if information is missing return NEEDS_CLARIFICATION.
- Return the structured result below. Keep SUMMARY to 15 lines or fewer.
```

## Structured return the specialist must end with

```
STATUS: COMPLETE | NEEDS_CLARIFICATION | NEEDS_APPROVAL | BLOCKED | FAILED
ARTIFACT: <path or "none">
QUESTIONS:            # required for NEEDS_CLARIFICATION / NEEDS_APPROVAL, else "none"
  1. <question> | options: <a> / <b> | suggested default: <a> | why it matters: <...>
SUMMARY:
  <15 lines or fewer: what was done, key decisions, risks, counts (tasks, findings)>
```
