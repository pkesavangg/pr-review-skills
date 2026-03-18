---
name: debug-issue
description: Investigate a bug before coding a fix. Use when the user says "debug this", "find the root cause", "why is this failing", or when a ticket is ambiguous and needs technical narrowing before implementation.
---

Debug an issue methodically and produce a root-cause-oriented implementation path.

The issue is: $ARGUMENTS

## Instructions

### 1 — Define the Failure Shape

Extract from the user or ticket:
- expected behavior
- actual behavior
- affected feature/module
- whether the issue is UI, service, API, persistence, DI, or environment-related

### 2 — Inspect the Execution Path

Read the likely path from entry point to side effect:
- screen/store/view model
- service
- repository/API or storage
- shared infrastructure if relevant (`HTTPClient`, `DependencyContainer`, environment, permissions)

### 3 — Look for Existing Signals

Check:
- related tests in `meAppTests/Features/...`
- related mocks/fixtures
- logging behavior
- recent patterns in nearby code

### 4 — Produce a Root Cause Note

Before editing, summarize:
- most likely root cause
- competing hypotheses if still uncertain
- exact files to change
- test or verification needed to prove the fix

### 5 — Recommend Next Step

| Root cause type | Next skill |
|----------------|-----------|
| Isolated bug (1–3 files) | `/fix-bug` |
| Missing endpoint or API call | `/add-endpoint` |
| DI misconfiguration | `/wire-service` |
| SwiftData or persistence issue | `/storage-change` |
| Full feature implementation needed | `/work-ticket` |
| Regression risk suspected | `/review-regression` |
| Navigation not wired | `/wire-navigation` |
