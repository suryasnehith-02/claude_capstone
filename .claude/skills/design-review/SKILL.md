---
name: design-review
description: Independent senior-level review of a proposed architecture against its requirements and the real repository - coverage, security, scalability, reliability, consistency, over-engineering - with severities, recommendations and a verdict. Use for SDLC Stage 3.
---

# Design review

Output: `docs/sdlc/<KEY>/design-review.md` from `templates/design-review.md`.

## Independence rules

- Judge only from artifacts: `requirements.md`, `architecture.md`, `repo-profile.md` and the repository itself.
  You have no access to the architect's reasoning; do not ask for it. If the architecture does not explain a
  decision, that is a finding.
- Verify claims against the repository (do the cited paths, modules and patterns exist and behave as described?).
- You review; you do not rewrite the architecture and you do not edit any other file.
- You cannot ask the human. Raise questions as findings or in QUESTIONS.

## Procedure

1. **Coverage matrix.** For every FR, NFR and AC in `requirements.md`, find where `architecture.md` addresses it.
   Unmapped or only superficially addressed items are findings.
2. **Evidence check.** Spot-check at least five claims about the existing repository (paths, patterns, reusable
   code, constraints) by reading the repo. Wrong or unverifiable claims are findings.
3. **Review categories** (record a result for each, even if "no findings"):
   security; scalability; reliability and failure handling; maintainability; internal consistency (interfaces
   match across components, data flow closes, no contradictions); fit with existing system and conventions;
   technology choices (justified, minimal new dependencies); testability; operability (observability,
   deployment, rollback); over-engineering and unnecessary complexity; dependency and licensing risk.
4. **Findings.** Each has: id (DR-n), severity, location (section of `architecture.md`), description, impact,
   recommendation. Severities: `critical` (design cannot satisfy a requirement, is unsafe, or contradicts the repo),
   `major` (significant risk or gap, fixable in design), `minor`, `suggestion`.
5. **Verdict.**
   - `APPROVE`: no critical or major findings open.
   - `REVISE`: critical or major findings that the architect can fix.
   - `BLOCK`: fundamental flaw or missing information that revision cannot fix without human input.
6. **Recommendations table.** Every recommendation gets a disposition column. Initially `pending`. When re-invoked
   after a revision, read the architect's *Review responses* section and set each disposition to `accepted` or
   `rejected` with the reason, and say whether you agree with any rejection. Re-check the revised design from
   scratch; do not assume fixes are correct.

## Return

`COMPLETE` with the verdict in SUMMARY (and counts per severity). Use `BLOCKED` only if inputs are missing or unreadable.
