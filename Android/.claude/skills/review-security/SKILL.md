---
name: review-security
description: Review meApp Android changes for security issues — hardcoded secrets/tokens, PII or health data in logs, insecure HTTP, exported components, and unsafe not-null/crash paths. Use when reviewing for security, or when the user says "security review", "check for secrets". Also called by /self-review and /review-pr.
---

Security audit of the diff. Read-only; findings need human judgement.

## Instructions

1. **Secrets** — no hardcoded API keys, tokens, passwords, or the prod QA credentials committed. Check for a leftover local `BASE_URL`→prod edit that shouldn't be committed.
2. **PII / health data** — none in `AppLog`/analytics/exceptions (names, emails, weights, BP, baby data). ids/enums only.
3. **Transport** — no cleartext `http://`; TLS/pinning respected; check `network_security_config.xml` if touched.
4. **Storage** — tokens/credentials via the secure store, not plain `SharedPreferences`/Room columns.
5. **Android surface** — newly `exported` activities/services/receivers/providers justified; no overly broad intent filters; `PendingIntent` immutability.
6. **Crash paths** — `!!` in critical paths, unchecked casts, unguarded `null`.
7. Report each finding with severity + fix. Verdict: PASS / WARNING / FAIL.
