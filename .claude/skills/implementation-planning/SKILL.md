---
name: implementation-planning
description: Break an approved architecture into dependency-ordered, independently verifiable implementation tasks with priorities, affected files, acceptance criteria, test requirements and a scope manifest. Use for SDLC Stage 4.
---

# Implementation planning

Output: `docs/sdlc/<KEY>/impl-plan.md` from `templates/impl-plan.md`.

## Preconditions

`requirements.md`, `architecture.md` and `design-review.md` exist; the Orchestrator has recorded G1 and G2 as
approved. Use the architecture exactly as approved; if you find it unimplementable, return `BLOCKED` with the
specific conflict instead of redesigning.

## Procedure

1. Read the three artifacts and `repo-profile.md`; refresh the profile with `repo-discovery` if it is missing.
   Test commands, layout and conventions come from the profile.
2. **Decompose** the architecture into tasks (T-n). A task is small enough to implement and verify on its own
   (target: one coherent change, reviewable in one sitting). Each task has:
   - title and description (what, not how-in-detail);
   - requirement ids it satisfies (FR/NFR/AC);
   - dependencies (task ids) and whether it is blocked by an open question, external system or missing access;
   - priority: `P0` critical path, `P1` required, `P2` should, `P3` could;
   - size: S / M / L (split any L);
   - affected files and modules: existing paths to modify (verified by reading the repo) and new paths to create,
     following the layout conventions in the profile;
   - **acceptance criteria**: observable, specific, verifiable;
   - **test requirements**: which levels (unit, integration, edge cases, failure cases such as missing files and
     failed external calls), where the tests live per repo convention, and what they must assert;
   - documentation to add or update.
3. **Order** tasks topologically into waves; mark which tasks within a wave are independent. Put risky or
   foundational work early. Detect and report dependency cycles instead of hiding them.
4. **Scope manifest.** List every file expected to be created, modified or deleted. Mark separately anything that
   needs explicit human approval at implementation kickoff: deletions, new dependencies, schema or data
   migrations, public interface changes, configuration or CI changes.
5. **Traceability.** Every requirement id maps to at least one task; every task maps to at least one requirement.
   Report orphans either way.
6. **Test strategy** for the whole change: levels, tools discovered in the profile (cite provenance), data needs,
   and how each acceptance criterion from `requirements.md` will be demonstrated.
7. **Definition of done** and rollout/rollback notes.
8. **Blocked tasks.** List them with the unblocking condition; do not silently drop them.

## Return

`COMPLETE` when every requirement is mapped, the dependency order is valid, and each task has acceptance criteria
and test requirements. SUMMARY: task counts by priority/size, waves, blocked tasks, items needing kickoff approval.
Use `NEEDS_CLARIFICATION` for gaps you cannot resolve from the artifacts.
