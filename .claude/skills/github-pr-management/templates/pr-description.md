# <KEY>: <PR title, 70 characters or fewer>

<!-- If the repository has a PR template, use its structure and keep these five sections. -->

## Summary
<what and why, in one paragraph> - JIRA: <link to KEY>

## Changes Made
| Area | Change | Files |
|---|---|---|

## Test Evidence
Overall verification status: <PASS | PARTIAL | FAIL> (source: `docs/sdlc/<KEY>/verification-report.md`)

| Check | Command | Exit | Result |
|---|---|---|---|

Acceptance criteria:
| AC | Result | Evidence |
|---|---|---|

## Known Limitations
- <open minor findings, NOT_RUN checks, waived critical findings with the human's recorded acceptance, deferred tasks>

## Reviewer Checklist
- [ ] Requirements and acceptance criteria are met (see `docs/sdlc/<KEY>/requirements.md`)
- [ ] Design matches `docs/sdlc/<KEY>/architecture.md`
- [ ] Tests are meaningful and cover failure paths
- [ ] Security-sensitive code paths reviewed (input validation, secrets, auth)
- [ ] Documentation and changelog updated
- [ ] No unexpected files in the diff
- [ ] Migration / rollback considered: <n/a or notes>

## Related artifacts
requirements.md - architecture.md - design-review.md - impl-plan.md - code-review.md - verification-report.md (all under `docs/sdlc/<KEY>/`)
