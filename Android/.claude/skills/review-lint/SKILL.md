---
name: review-lint
description: Check new/changed Kotlin against the meApp Android detekt config and style conventions. Use when reviewing for lint/style, or when the user says "lint check", "detekt review". Also called by /self-review and /review-pr.
---

Check the diff against detekt + project style. Read-only review (use `/detekt-fix` to apply fixes).

## Instructions

1. Run `cd Android && ./gradlew detekt` and read the report.
2. Review the changed `.kt` for:
   - **`!!` usage** (banned) — flag every occurrence.
   - Android Lint gate: run `./gradlew lint` if resources/manifest changed.
   - naming (`I` prefix on interfaces), unused imports/vars, long methods, magic numbers, blanket `@Suppress`, new baseline entries added to dodge violations.
   - `AppLog` used instead of `android.util.Log`; no hardcoded strings/colors/dp.
3. Report each finding as `file:line — issue — fix`. Verdict: PASS / WARNING / FAIL.
