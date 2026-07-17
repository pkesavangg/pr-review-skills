---
name: refresh-skill-docs
description: Keep the Android skill library current — re-read official documentation for each doc-backed skill, and open a PR when a skill's reference content has drifted from the current best practice. Use when the user says "refresh the skills", "update skills from docs", "are the skills still current", or when the weekly maintenance routine runs.
---

Re-source the doc-backed Android skills from official documentation and open a PR only when something actually changed. Never auto-merge.

The skill(s) to refresh (default: all doc-backed): $ARGUMENTS

## Why this exists

`SKILL.md` files are static — they can silently drift from the frameworks they describe (Room, Compose, Hilt, Navigation3, detekt/JaCoCo). This skill closes that gap on demand or on a schedule.

## Which skills are doc-backed

Skills whose guidance tracks an external framework/tool and therefore have a `reference/` section worth refreshing:
`room-change` (androidx.room) · `wire-navigation` (androidx.navigation3) · `theme-guide` (compose material3) · `wire-service` (dagger/hilt) · `compose-perf-analyzer` (compose performance) · `verify-tests` (jacoco) · `detekt-fix` (detekt) · `upgrade-deps` (per-library).

## Instructions

### 1 — Select targets
Use `$ARGUMENTS` if given; otherwise iterate the doc-backed list above.

### 2 — Fetch current official docs
For each target, resolve the library/tool and pull the current canonical guidance:
- **context7 MCP** first (`resolve-library-id` → `query-docs`) — version-aware.
- **WebSearch/WebFetch** fallback against the official developer.android.com / vendor page.
- If docs are unavailable (headless/offline): log it, skip that target, do **not** edit it. Report the skip.

### 3 — Diff against the committed skill
Compare the fetched guidance to the skill's current `reference/` (or the doc-derived steps in `SKILL.md`):
- API renames, deprecated → replacement, new required steps, changed defaults, new migration guidance.
- Ignore cosmetic wording; only act on **substantive** drift.

### 4 — Update on drift
- Edit the skill's `reference/*.md` (preferred) or the relevant `SKILL.md` step.
- Keep the `SKILL.md` lean; put bulk in `reference/`.
- Add a short dated note at the top of the changed reference: `> Refreshed <date> from <source>.`
- Leave the `description`/trigger phrases alone unless the skill's scope genuinely changed.

### 5 — Open a PR (never auto-merge)
- If nothing drifted: report "all doc-backed skills current — no PR" and stop.
- If something changed: branch `chore/refresh-skill-docs-<date>` off `develop`, commit per skill (`MOB-XXXX Refresh <skill> reference from <source>`), push, open a PR summarizing exactly what changed and the source links. A human reviews and merges.

## Scheduling (the weekly routine)

Run this weekly and let it PR only on change. Register via the scheduled-tasks mechanism with a spec equivalent to:

```
name:     refresh-android-skill-docs
schedule: weekly (e.g. Mon 09:00 local)
action:   run skill `refresh-skill-docs` (all doc-backed) in the meApp repo
outcome:  opens a PR against develop iff a reference drifted; otherwise no-op
```

> Activation is a one-time step. Because this creates a standing automation that opens PRs autonomously, activate it explicitly (not silently) — the committed spec above is the source of truth; flipping it live is a deliberate action.
