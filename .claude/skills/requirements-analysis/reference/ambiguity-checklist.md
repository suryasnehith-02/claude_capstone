# Ambiguity and gap checklist

Walk every requirement through these. Anything unanswered by a cited source becomes a question (Q-n).

- **Actors:** who performs/receives this? Roles and permissions? Anonymous vs authenticated?
- **Trigger and flow:** what starts it, what is the happy path, what ends it?
- **Inputs:** types, ranges, formats, size limits, required vs optional, validation and error messages?
- **Outputs:** format, destination, ordering, pagination, localisation?
- **Data:** what is created/changed/deleted, retention, migration of existing data, privacy classification?
- **Errors and edge cases:** empty input, missing file/resource, duplicate, concurrent change, timeout, partial failure, dependency outage, retries, idempotency?
- **Non-functional:** performance targets, scale, availability, security (authn/authz, secrets, audit), accessibility, compatibility, observability?
- **Integration:** which systems/APIs, contracts, versions, auth, rate limits, failure behaviour?
- **Compatibility:** backward compatibility, feature flags, rollout, rollback?
- **Wording:** vague terms ("fast", "user-friendly", "etc.", "should", "as needed") without measurable meaning?
- **Conflicts:** description vs comments vs linked issues vs Confluence; later comments overriding earlier text?
- **Missing acceptance criteria:** any requirement with no way to verify it?
- **Scope:** anything implied but not stated; anything stated but likely out of scope?
- **Testability:** can an independent person decide pass/fail from the text alone?
