---
name: architecture-design
description: Design a solution architecture from approved requirements and the real state of the target repository - components, interfaces, data flow, integrations, technology choices justified by repo evidence, quality attributes, security, risks, and diagrams. Use for SDLC Stage 2.
---

# Architecture design

Output: `docs/sdlc/<KEY>/architecture.md` (template: `templates/architecture.md`) and, via the preloaded
`repo-discovery` skill, `docs/sdlc/<KEY>/repo-profile.md`.

## Preconditions

`requirements.md` is `FINAL` and approved (the Orchestrator guarantees this; if status is `DRAFT`, return `BLOCKED`).
You cannot ask the human; unresolved choices go in QUESTIONS.

## Procedure

1. **Understand the requirements.** Read `requirements.md` completely. List the architecture drivers: the FR/NFR/AC
   that most constrain the design, ranked by impact. Keep the stable ids (FR-n, NFR-n, AC-n).
2. **Discover the repository** with `repo-discovery` (create or refresh `repo-profile.md`).
   - Greenfield (no source, no manifest): the technology stack is a human decision. Unless requirements or linked
     documents state it, return `NEEDS_CLARIFICATION` with 2-3 options, trade-offs and a recommendation.
3. **Study the existing system.** Identify modules, layers, boundaries, extension points, patterns already in use,
   integration points, and code that should be reused. Cite real file paths. Prefer extending existing structure
   over introducing new structure.
4. **Choose technology from evidence.** Default to what the repository already uses. Introducing a new language,
   framework, datastore, service or major dependency requires an entry in *Decisions needing human approval* with
   justification, alternatives and migration cost. Never name a technology you cannot justify from the repo,
   requirements or linked documents.
5. **Design.** For each significant decision, write the options considered, the choice, and the reason (decision
   table). Define: components and responsibilities, interfaces and contracts (inputs, outputs, errors), data
   model and data flow, integrations and failure behaviour, configuration, and how the new parts attach to
   existing ones.
6. **Quality attributes** (checklist in `reference/quality-checklist.md`): scalability, reliability and failure
   handling, maintainability, security (trust boundaries, authn/authz, input validation, secrets, data
   protection, dependency risk), observability, testability, performance, compatibility and rollout/rollback.
   Every NFR from requirements must be addressed or explicitly deferred with a reason.
7. **Diagrams** when they clarify: use Mermaid code blocks (component, sequence, data flow). Keep each focused.
8. **Traceability.** Table mapping every FR/NFR/AC to the component(s) that satisfy it. No requirement may be unmapped.
9. **Impact on existing code.** List existing files/modules expected to change and any breaking changes.
10. **Risks and open questions.** Each with likelihood, impact, mitigation.

## Revision after design review

When re-invoked with review findings, read `design-review.md`. Address each finding: change the design, or reject with
a reason. Record every response in the *Review responses* section (finding id, accepted/rejected, reason, change
made). Do not edit `design-review.md`.

## Return

Structured return: `COMPLETE` when the document has all template sections, full traceability and a populated
repo profile; otherwise `NEEDS_CLARIFICATION` or `BLOCKED`. SUMMARY: components count, decisions needing approval,
risks, requirements mapped (n of n).
