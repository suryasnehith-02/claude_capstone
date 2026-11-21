# Quality attribute checklist

Use as prompts, not as boilerplate. Skip items that do not apply and say so; address every NFR from requirements.

- **Scalability:** expected load and growth; stateless vs stateful parts; bottlenecks; data volume; backpressure; caching and its invalidation.
- **Reliability:** failure modes of each dependency; timeouts, retries (with limits and idempotency), circuit breaking; partial failure and consistency; recovery; data loss risk.
- **Maintainability:** clear module boundaries; dependency direction; no duplicated logic; consistency with existing conventions; ease of removal/change.
- **Security:** assets and trust boundaries; authentication and authorization on every entry point; input validation and output encoding; injection risks; secrets handling (never in code or logs); least privilege; data at rest/in transit; PII handling and retention; logging without sensitive data; third-party dependency risk and licensing.
- **Observability:** logs, metrics, traces, health checks, alertable failure signals, correlation ids.
- **Performance:** latency and throughput targets; hot paths; resource use; avoid premature optimisation but justify obvious risks.
- **Testability:** seams for isolation; deterministic behaviour; test data; ability to simulate dependency failure.
- **Compatibility and operations:** backward compatibility of APIs/data; migrations; configuration and environments; feature flags; deployment, rollout and rollback; documentation.
