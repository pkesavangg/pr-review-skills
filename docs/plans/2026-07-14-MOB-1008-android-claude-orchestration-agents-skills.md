# MOB-1008 — Android `.claude` orchestration, agents & expanded skills (iOS parity + self-maintaining docs)

> **Status:** Plan (how-to) · **Author:** Rengadevi V · **Date:** 2026-07-14
> **Branch:** `MOB-1008-create-android-claude-orchestration-agents-and-expanded-skills-to-match-ios` (from `develop`)
> **Jira:** [MOB-1008](https://greatergoods.atlassian.net/browse/MOB-1008) — *Create Android .claude orchestration, agents, and expanded skills to match iOS* (In Progress, `android`, `dev-experience`)
> **Sibling (iOS):** [MOB-1007](https://greatergoods.atlassian.net/browse/MOB-1007) — *Audit & refresh iOS .claude skills/agents* — the iOS counterpart of this same epic; this plan reuses its taxonomy, decisions, and house format.

---

## 0. TL;DR — what we are actually doing

We use Claude Code as the primary dev tool for this repo. iOS has a rich `.claude` (an `orchestra.md`, 5 agents, 3 commands, ~50 skills); **Android has only 5 skills, no `orchestra.md`, no agents, no commands.** AI-assisted Android work is therefore materially less guided, even though Android has equally strict conventions (MVI reducers, Hilt `onDependenciesReady`, the `!!` ban via detekt, `@PreviewTheme`, Room `SELECT *` rules, JaCoCo 80%).

This task brings Android to parity **and goes one step further** — the skills are best-practice-first and stay current from official docs:

1. **Create `Android/.claude/orchestra.md`** — the workflow map, using Android idioms, wiring in the skills we already have (the 5 committed + the ~17 Google Android skills).
2. **Add 4 Android agents** — `hilt-impact-finder`, `coverage-gap-finder`, `compose-perf-analyzer` (Perfetto-aware), `reducer-test-scaffolder`.
3. **Add Android skills** — scaffold/wire, reference guides, build/test, review pipeline, plus an Android-only `verify-on-emulator`.
4. **Best-practice-first + live docs** — builder/reference skills pull current official docs at runtime (context7 MCP, web fallback), not just meApp-local habits.
5. **Enhance `upgrade-deps`** — fetch official release notes / migration guides per bump, emit a report, notify the developer, pause on high risk.
6. **Self-maintaining library** — a `refresh-skill-docs` skill + a weekly scheduled routine that re-reads official docs and opens a PR when a skill's reference section drifts.
7. **Align taxonomy with iOS** — parity table checked into **both** `orchestra.md` files.

The output of *this* step is this document + the branch (both done). Implementation follows on approval, in the phased checkpoints in §5.

---

## 1. Decisions (confirmed with user)

| # | Decision | Resolution |
|---|----------|------------|
| D-scope | Parity breadth | **Core parity** — the skills/agents the ACs name, not a 1:1 mirror of all ~50 iOS skills. |
| D-review | Review-layer packaging | **Modular** — `self-review` + 5 small `review-*` skills, each independently runnable. |
| D-perf | Perf agent | **Perfetto-aware** `compose-perf-analyzer` (drives real traces), not a static-only port. |
| D-emulator | `verify-on-emulator` skill | **In scope** — codifies the existing prod-URL → install → screenshot/video ritual (Android-only; iOS needs a device). |
| D-docsrc | Doc source for live fetch | **context7 MCP first, WebSearch/WebFetch fallback** — context7 is version-aware for libraries (Room/Compose/Hilt/AndroidX). |
| D-docsync | Self-updating mechanism | **In scope** — `refresh-skill-docs` + weekly scheduled routine. |
| D-branch | Base branch | **`develop`** (active integration branch carrying Phase 2). `main` = legacy 5.0.x line. |
| D-format | Skill format | **Anthropic skill-creator `<name>/SKILL.md`** (Android already uses this correctly). |
| D-place | Placement | New skills/agents under **`Android/.claude/`**; reuse repo-root generic git/Jira/PR skills. |

---

## 2. The gap (current state, verified on `develop`)

| | iOS `.claude` | Android `.claude` |
|---|---|---|
| `orchestra.md` | ✅ 392-line workflow guide | ❌ missing |
| `agents/` | ✅ 5 | ❌ none |
| `commands/` | ✅ 3 | ❌ none |
| `skills/` | ~50 | 5 (`android-service-test-writer`, `drawable-import`, `drawable-scan`, `unit-tests`, `upgrade-deps`) |

Also present but **not wired into any workflow map**: ~17 official Google Android skills in the repo (`adaptive`, `agp-9-upgrade`, `android-cli`, `edge-to-edge`, `migrate-xml-views-to-jetpack-compose`, `jetpack-compose-m3`, `navigation-3`, `perfetto-sql`, `perfetto-trace-analysis`, `r8-analyzer`, `styles`, `play-billing…`, `camera1-to-camerax`, `engage-sdk-integration`, `appfunctions`, `verified-email`, `display-glasses…`).

### 2.1 ⚠️ Blocker: `.gitignore` silently drops new files

`.gitignore` uses `Android/.claude/*` (line 66) and re-includes **only** `!Android/.claude/skills/` (line 75). iOS re-includes `skills/ agents/ commands/ orchestra.md` (lines 69–74); Android is missing three. **New `agents/`, `commands/`, and `orchestra.md` would be untracked** — so the `.gitignore` fix is Phase 0, before anything else.

---

## 3. Design philosophy — build-it-right, not just review-it-after

Two layers, both sourced from authoritative docs:

| Layer | Purpose | Skills |
|---|---|---|
| **Builder (proactive)** | Encode the correct current pattern + boilerplate; pull official docs at use time | `feature-slice`, `room-change`, `wire-service`, `wire-navigation`, `theme-guide`, `logging-guide`, `analytics` |
| **Review (reactive, backstop)** | Catch anything that slipped, before commit | `self-review` → `review-lint`/`review-regression`/`review-security`/`review-issue-fix`/`review-accessibility` |

"Boilerplate as a skill" = the builder layer. Live-docs = each doc-heavy builder skill has a step: *"before applying, pull the current official pattern via context7 (fallback web)."* This keeps the guidance current without editing the file.

---

## 4. Target architecture

```
meApp/
├── .gitignore                         ← EDIT (Phase 0): re-include Android agents/ commands/ orchestra.md
├── iOS/.claude/orchestra.md           ← EDIT: add shared iOS↔Android parity table
└── Android/.claude/
    ├── orchestra.md                   ← NEW: the workflow map (§4.1)
    ├── agents/
    │   ├── hilt-impact-finder.md       ← NEW (DI blast radius; ↔ iOS di-impact-finder)
    │   ├── coverage-gap-finder.md      ← NEW (JaCoCo gaps)
    │   ├── compose-perf-analyzer.md    ← NEW (Perfetto-aware; ↔ iOS ios-perf-analyzer)
    │   └── reducer-test-scaffolder.md  ← NEW (wraps android-service-test-writer)
    └── skills/
        ├── feature-slice/SKILL.md      ← NEW (MVI: BaseIntentViewModel + IReducer + State.copy())
        ├── wire-service/SKILL.md        ← NEW (Hilt @Module/@Binds in core/di/)
        ├── wire-navigation/SKILL.md     ← NEW (AppRoute sealed class + Navigation3)
        ├── add-strings/SKILL.md         ← NEW (feature strings/ PascalCase Strings object)
        ├── room-change/SKILL.md         ← NEW (Room entity/DAO, SELECT * ban, migrations)
        ├── theme-guide/SKILL.md         ← NEW (MeAppTheme colorScheme/typography/spacing)
        ├── logging-guide/SKILL.md       ← NEW (AppLog, never Log)
        ├── analytics/SKILL.md           ← NEW (event instrumentation)
        ├── build/SKILL.md               ← NEW (./gradlew assembleDebug)
        ├── verify-tests/SKILL.md        ← NEW (testDebugUnitTest + JaCoCo 80%)
        ├── verify-on-emulator/SKILL.md  ← NEW (Android-only: prod-URL install → screenshot/video)
        ├── detekt-fix/SKILL.md          ← NEW (detekt auto-fix + !! ban)
        ├── self-review/SKILL.md         ← NEW (orchestrates the 5 review-* below)
        ├── review-lint/SKILL.md         ← NEW
        ├── review-regression/SKILL.md   ← NEW
        ├── review-security/SKILL.md     ← NEW
        ├── review-issue-fix/SKILL.md    ← NEW
        ├── review-accessibility/SKILL.md← NEW
        ├── refresh-skill-docs/SKILL.md  ← NEW (re-read official docs → PR when a reference drifts)
        ├── upgrade-deps/                 ← ENHANCE (doc-fetch + report + notify + pause on risk)
        ├── unit-tests/ · android-service-test-writer/ · drawable-*/  ← REUSE (reference from orchestra)
        └── (Google skills referenced from orchestra's "Android extensions")
```

Plus a **weekly scheduled routine** (via the scheduled-tasks mechanism) that runs `refresh-skill-docs`.

### 4.1 `orchestra.md` contents

Sections mirror iOS: Core Principles · Task Management · **Subagent Strategy** (the 4 agents) · **Workflow Sequences** · Review Pipeline · **Verification Checklist** (`assembleDebug` + `testDebugUnitTest` + detekt + JaCoCo 80%) · Skill Reference by Category · **iOS↔Android Parity Table** · **Android Extensions** (the Google skills, mapped to workflows) · Parallelization · Error Recovery.

Workflow sequences wire in existing skills, e.g.:
- **New MVI feature:** `feature-slice` → `wire-service` → `wire-navigation` → `add-strings` → `theme-guide` → `unit-tests` → `verify-tests` → `self-review` → commit → PR
- **Bug fix:** debug → fix + `unit-tests` → `verify-tests` → `self-review`
- **Room change:** `room-change` → `unit-tests` (dao) → `verify-tests`
- **Dependency upgrade:** `upgrade-deps` (+ `agp-9-upgrade` if AGP) → `build` → report to developer
- **Perf investigation:** `compose-perf-analyzer` agent → `perfetto-trace-analysis`
- **Size audit:** `r8-analyzer`
- **XML→Compose migration:** `migrate-xml-views-to-jetpack-compose`

### 4.2 iOS↔Android parity table (goes in both orchestras)

| Task | iOS | Android |
|---|---|---|
| New feature scaffold | `feature-slice` | `feature-slice` (MVI) |
| DI wiring | `wire-service` | `wire-service` (Hilt) |
| Navigation | `wire-navigation` | `wire-navigation` (AppRoute/Nav3) |
| Strings | `add-strings` | `add-strings` |
| Theme/tokens | `theme-guide` | `theme-guide` (MeAppTheme) |
| Logging | `logging-guide` | `logging-guide` (AppLog) |
| Analytics | `analytics` | `analytics` |
| Storage/DB | `storage-change`+`swiftdata` | `room-change` |
| Lint/format | `swiftlint`/`review-lint` | `detekt-fix`/`review-lint` |
| Pre-commit review | `self-review` (5 checks) | `self-review` (5 checks) |
| Build | `build` | `build` |
| Tests + coverage | `verify-tests` | `verify-tests` (JaCoCo) |
| Unit tests | `gen-test-file` | `unit-tests`/`android-service-test-writer` |
| Device/emulator run | *(physical device only)* | `verify-on-emulator` |
| DI impact | `di-impact-finder` | `hilt-impact-finder` |
| Coverage gaps | `coverage-gap-finder` | `coverage-gap-finder` |
| Perf | `ios-perf-analyzer` (static) | `compose-perf-analyzer` (Perfetto) |
| Dep upgrade | *(gap)* | `upgrade-deps` (doc-sourced) |
| Git/Jira/PR | repo-root generic | repo-root generic |

---

## 5. Implementation plan (phased — each is a reviewable checkpoint / commit)

### Phase 0 — Unblock tracking
- Add to `.gitignore`: `!Android/.claude/agents/`, `!Android/.claude/commands/`, `!Android/.claude/orchestra.md`.
- Create empty `agents/` dir; commit a trivial file to prove tracking works.

### Phase 1 — `orchestra.md`
- Author `Android/.claude/orchestra.md` (§4.1). Defines the frame the rest fills.

### Phase 2 — Agents (4)
- Author the 4 agent files; `compose-perf-analyzer` references `perfetto-trace-analysis`/`perfetto-sql`; `reducer-test-scaffolder` delegates to `android-service-test-writer`.

### Phase 3 — Skills
- Builder/reference (7) with runtime doc-fetch step.
- Build/test (2) + `verify-on-emulator` (1).
- Review (7): `detekt-fix`, `self-review` + 5 `review-*`.

### Phase 4 — Doc-sourced skills
- Enhance `upgrade-deps` (doc-fetch → report → notify → pause on risk).
- Author `refresh-skill-docs` + register the weekly scheduled routine.

### Phase 5 — Taxonomy, wiring & verification
- Parity table into both `orchestra.md` files; "Android extensions" section listing the Google skills.
- Run `scripts/check-skill-links.sh` (if present; else add a grep-based link check); confirm every `SKILL.md` frontmatter (`name`+`description`) loads and all referenced paths resolve.
- Discovery check: confirm the new Android skills surface as available skills.

---

## 6. Acceptance criteria → coverage map

| MOB-1008 criterion | Covered by |
|---|---|
| `Android/.claude/orchestra.md` with workflow sequences | Phase 1 (§4.1) |
| Core agents (DI-impact, coverage-gap, test-scaffolder, compose-perf) | Phase 2 |
| Skill coverage (Hilt, nav, strings, theme, MVI, logging, analytics, detekt, Room, self/PR-review) | Phase 3 (+ reuse repo-root branch/commit/log-work) |
| Taxonomy lines up with iOS (parity table in orchestra.md) | Phase 5 (§4.2) |
| Each new skill/agent references valid paths and loads | Phase 5 verification |
| *(added)* .gitignore re-includes | Phase 0 |
| *(added)* best-practice-first + live docs | Phase 3 (§3) |
| *(added)* upgrade-deps doc-fetch + report + notify | Phase 4 |
| *(added)* verify-on-emulator | Phase 3 |
| *(added)* refresh-skill-docs + weekly schedule | Phase 4 |

---

## 7. Risks & rollback

- **Scope is larger than the original 5-point parity task.** Mitigation: land in the 5 phase-commits so each is independently reviewable; estimate bumped to 2d + scope recorded on the Jira ticket.
- **This task edits `.claude/` itself** — the repo-root `.claude` is synced/untracked from a separate source in places. We only touch **`Android/.claude/`** (tracked, 22 files) + `.gitignore` + `iOS/.claude/orchestra.md`; no repo-root skill files.
- **Doc-fetch tooling availability.** context7/web may be absent in headless/cron runs; skills must degrade gracefully (state "docs unavailable, using cached guidance") rather than fail.
- **Scheduled routine noise.** `refresh-skill-docs` opens a PR *only* when a reference section actually drifts; never auto-merges.
- **No app runtime surface.** Deliverable is Markdown — the usual emulator+video push gate does not apply to the deliverable itself (`verify-on-emulator` is a skill we *write*, not a gate we *run* here).
- Everything is on the MOB-1008 branch; rollback = drop the branch/worktree.

---

## 8. Suggested commit sequence (one PR, targeting `develop`)

1. `MOB-1008 Re-include Android .claude agents/commands/orchestra in .gitignore`
2. `MOB-1008 Add Android .claude/orchestra.md workflow guide`
3. `MOB-1008 Add Android agents (hilt-impact, coverage-gap, compose-perf, reducer-test-scaffolder)`
4. `MOB-1008 Add Android builder/reference/build/review skills`
5. `MOB-1008 Add verify-on-emulator + doc-sourced upgrade-deps + refresh-skill-docs + weekly schedule`
6. `MOB-1008 Add iOS↔Android parity table to both orchestras + link-check verification`
