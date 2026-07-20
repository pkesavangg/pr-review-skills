---
name: analytics
description: Instrument a meApp Android flow with analytics/observability events following the project's event pattern. Use when the user says "add analytics for X", "track this event", "instrument this flow", or when a new user-facing action or critical path has no telemetry.
---

Add analytics events to a user flow using the project's existing event mechanism.

The flow to instrument is: $ARGUMENTS

## Instructions

### 1 — Find the pattern
- Locate how the app currently emits analytics (the analytics service/interface + existing event call sites). Match it exactly — do not introduce a second mechanism.

### 2 — Instrument
- Emit events at meaningful moments: screen view, primary CTA, success, and failure/error paths.
- Name events and params consistently with existing ones; keep params typed/enumerated.
- **No PII / health values** in event params (see `/logging-guide`) — use ids/enums/booleans.
- Emit from the ViewModel/service, not from deep inside Composables.

### 3 — Test
- Assert the event fires on the key paths (mock the analytics interface with MockK; verify calls). Cover success + failure.

### 4 — Verify
`cd Android && ./gradlew assembleDebug :app:testDebugUnitTest`.
