---
name: verify-tests
description: Build, run unit tests, and enforce the JaCoCo coverage gate for the meApp Android app; add targeted tests for anything below threshold. Use after writing/modifying Kotlin code, or when the user says "run tests", "check coverage", "verify tests pass".
---

Run unit tests + coverage and close any gaps.

The files/area under test: $ARGUMENTS

## Instructions

### 1 — Run tests + coverage
```bash
cd Android && ./gradlew :app:testDebugUnitTest :app:jacocoTestReport :app:jacocoTestCoverageVerification
```
- If tests fail: fix them before analyzing coverage.
- Report: `Android/app/build/reports/jacoco/jacocoTestReport/html/index.html`.

### 2 — Enforce the gate
Project minimum is **80% line coverage**. If `jacocoTestCoverageVerification` fails or a changed file is below 80%:
- spawn agent `coverage-gap-finder` to list exact uncovered methods/branches,
- add tests via `/unit-tests` or agent `reducer-test-scaffolder`,
- re-run until the gate passes.

### 3 — Scope
Exclude pure-UI Composables and generated code from the target (mirror the JaCoCo config). Don't pad coverage with assertion-free tests — cover real success + failure paths.

### 4 — Instrumented tests (when relevant)
DAO/migration/Compose-UI changes: `./gradlew connectedDebugAndroidTest` on a device/emulator.
