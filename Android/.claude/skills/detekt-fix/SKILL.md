---
name: detekt-fix
description: Run detekt on changed Kotlin, auto-fix correctable violations, and manually resolve the rest for the meApp Android app. Use when the user says "fix lint/detekt", "clean up lint", "detekt errors", or when violations block a commit. Enforces the project !! ban.
---

Run detekt and resolve violations.

## Instructions

### 1 — Run detekt
```bash
cd Android && ./gradlew detekt
```
(Use `detektAutoCorrect`/`--auto-correct` if the config enables it for formatting rules.)

### 2 — Fix violations
- Auto-correct formatting/style first.
- Manually fix the rest — **never** silence with a blanket `@Suppress` or by loosening the baseline.
- **Hard rule: no `!!` (not-null assertion).** Replace with `requireNotNull(...)` + message, `?.`/`?:`, `checkNotNull`, or a proper guard.
- If a violation is a genuine false positive, suppress narrowly with a justification comment — not a file-wide suppression.

### 3 — Baseline
- Don't add new entries to the detekt baseline to dodge a fresh violation. The baseline is for pre-existing debt only; refresh it only when explicitly doing baseline maintenance.

### 4 — Verify
```bash
cd Android && ./gradlew detekt
```
Re-run until clean. Confirm no new `!!`:
```bash
rg -n "!!" Android/app/src/main -g '*.kt'
```
