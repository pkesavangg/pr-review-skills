---
name: orchestra
description: Master workflow orchestration guide for Android. Defines how skills, agents, and commands compose into end-to-end workflows using Android/Kotlin/Compose idioms (MVI, Hilt, Room, detekt, JaCoCo). Reference this for task sequencing, the review pipeline, subagent strategy, and verification checklists. Auto-loaded via Android/CLAUDE.md.
---

# Workflow Orchestration — Android

> This file defines how skills, agents, and commands compose into end-to-end workflows for the Android app. Follow these orchestration rules for every non-trivial task. Sibling: `iOS/.claude/orchestra.md` (the two share the parity table in §8).

---

## 1. Core Principles

| Principle | Rule |
|-----------|------|
| **Plan first** | Enter plan mode for any task with 3+ steps or architectural decisions. If something goes sideways, STOP and re-plan. |
| **Build it right, then review** | Prefer the builder skills (§7) that encode the correct current pattern over improvising; reviews are a backstop, not the design step. |
| **Best-practice-first + live docs** | Builder/reference skills pull the current official pattern (context7 MCP, web fallback) before applying — don't ship stale habits. See §10. |
| **Simplicity first** | Smallest change that solves it. No unrelated cleanup, no speculative abstraction. |
| **Minimal impact** | Only touch what's necessary. Match surrounding code's naming, idioms, comment density. |
| **Verify before done** | Never mark a task complete without proving it works — build, tests, and (for UI) an emulator run. |

---

## 2. Task Management Protocol

Every non-trivial task follows this lifecycle:

```
1. /fetch-ticket        → requirements + acceptance criteria
2. /create-branch       → branch from develop, transition Jira to In Progress
3. /create-prd          → implementation plan under Android/docs/plans/ (non-trivial tasks)
4. [Implementation — use the builder skills per task type]
5. /verify-tests        → build + unit tests + JaCoCo coverage
6. /self-review         → run the 5 specialist reviews
7. /verify-on-emulator  → prod-URL install + screenshot/video (UI-affecting changes)
8. /commit → /raise-pr → /log-work
```

---

## 3. Subagent Strategy

Use agents (isolated context — they investigate and return a summary) to keep the main window clean:

| When | Agent |
|------|-------|
| Assessing Hilt/DI registration blast radius | `hilt-impact-finder` |
| Finding JaCoCo coverage gaps | `coverage-gap-finder` |
| Diagnosing Compose recomposition / jank (with a real Perfetto trace) | `compose-perf-analyzer` |
| Scaffolding reducer/ViewModel/service tests | `reducer-test-scaffolder` (delegates to `android-service-test-writer`) |
| Broad codebase exploration | Explore subagent |

**Rules:** one focused job per agent; never duplicate work an agent is already doing; offload research/analysis; use background agents for independent work.

---

## 4. Workflow Sequences

### 4.1 Full Ticket (SDLC)
```
/fetch-ticket → /create-branch → /create-prd
  ↓ [implementation]
/verify-tests → /self-review → /verify-on-emulator (if UI)
  ↓
/commit → /raise-pr → /log-work
```

### 4.2 New MVI Feature
```
/feature-slice        → State + Intent + IReducer + BaseIntentViewModel + Composable
/wire-service         → Hilt @Module/@Binds for any new I*-interface
/wire-navigation      → AppRoute sealed entry + Navigation3
/add-strings          → feature strings/ PascalCase Strings object
/theme-guide          → MeAppTheme tokens; @PreviewTheme previews
  ↓
/unit-tests           → reducer + viewmodel tests (agent: reducer-test-scaffolder)
/verify-tests → /self-review → /verify-on-emulator → /commit → /raise-pr
```

### 4.3 Bug Fix
```
[reproduce + locate root cause]
[fix + regression test via /unit-tests]
/verify-tests → /self-review → /verify-on-emulator (if UI) → /commit → /raise-pr
```

### 4.4 Refactor
```
/create-prd (scope + constraints)
agent: hilt-impact-finder (if touching DI/services)
[refactor without behaviour change]
/verify-tests (all existing tests still green) → /self-review → /commit
```

### 4.5 Room / Storage Change
```
/room-change          → entity/DAO rules, SELECT * ban, migration check
/unit-tests           → DAO tests (instrumented where needed)
/verify-tests → /self-review → /commit
```

### 4.6 Test-Coverage Improvement
```
agent: coverage-gap-finder → uncovered methods/branches (JaCoCo)
/unit-tests or agent: reducer-test-scaffolder → add tests
/verify-tests (meets 80% gate) → /commit
```

### 4.7 Dependency Upgrade
```
/upgrade-deps         → phased, risk-ordered bumps in libs.versions.toml;
                        fetches official release notes/migration guide per bump,
                        emits a report, notifies the developer, PAUSES on high risk
(+ /agp-9-upgrade if bumping AGP to 9)
/build → /verify-tests → /commit
```

### 4.8 Performance Investigation
```
agent: compose-perf-analyzer → recomposition/stability findings + real Perfetto trace
(/perfetto-trace-analysis + /perfetto-sql for deep trace queries)
[apply fixes] → /verify-on-emulator → /commit
```

### 4.9 APK Size / R8
```
/r8-analyzer → redundant/broad keep rules, size wins
/build (release) → /commit
```

### 4.10 XML → Compose Migration
```
/migrate-xml-views-to-jetpack-compose → planned, staged migration
/theme-guide → tokens · /unit-tests (Compose UI) → /verify-tests → /self-review
```

### 4.11 PR Review
```
/review-pr {url}   (plugin command) — or run the §5 pipeline manually
```

---

## 5. Review Pipeline

Before any commit on non-trivial changes, `/self-review` runs these five specialist reviews in order:

| Step | Skill | Focus |
|------|-------|-------|
| 1 | `/review-lint` | detekt rules, `!!` ban, style conventions |
| 2 | `/review-regression` | breaking changes in public APIs, tests, Hilt DI, Room migrations |
| 3 | `/review-security` | secrets, tokens, insecure HTTP, PII in logs, exported components |
| 4 | `/review-issue-fix` | does the change meet the Jira acceptance criteria? |
| 5 | `/review-accessibility` | TalkBack, contentDescription, semantics, touch targets, testTags |

**Rule:** fix all findings before committing. Each `review-*` is also runnable on its own (e.g. re-run only `/review-security` after a fix). `/detekt-fix` auto-fixes correctable lint before the pipeline.

---

## 6. Verification Checklist

Before marking any task complete, confirm:

- [ ] Builds: `cd Android && ./gradlew assembleDebug`
- [ ] Unit tests pass: `./gradlew :app:testDebugUnitTest`
- [ ] Coverage meets the 80% gate: `./gradlew :app:jacocoTestReport :app:jacocoTestCoverageVerification`
- [ ] detekt clean: `./gradlew detekt` (no new `!!`, no suppressed violations)
- [ ] UI-affecting change verified on an emulator with a recorded video (`/verify-on-emulator`)
- [ ] No unrelated files modified
- [ ] Commit message: `MOB-XXXX Description`
- [ ] New DI/service/Room/nav wiring reflected where it belongs (no partial registration)

---

## 7. Skill Reference by Category

### Planning & Research (repo-root generic)
| Skill/Agent | Purpose |
|-------------|---------|
| `/fetch-ticket` | Fetch Jira issue details |
| `/create-prd` | Generate an implementation plan (PRD) |
| `/read-figma` | Extract design context from a Figma URL |
| `/read-jira-images` | Analyze Jira image attachments |
| `/phase2-context` | Phase 2 (Me.Health 2.0) product + unified-API model |
| `hilt-impact-finder` | Hilt/DI registration blast radius |
| `coverage-gap-finder` | Uncovered methods/branches (JaCoCo) |

### Scaffolding & Wiring (Android)
| Skill | Purpose |
|-------|---------|
| `/feature-slice` | MVI slice: State + Intent + `IReducer` + `BaseIntentViewModel` + Composable |
| `/wire-service` | Hilt `@Module`/`@Binds` for an `I*`-interface in `core/di/` |
| `/wire-navigation` | `AppRoute` sealed entry + Navigation3 |
| `/add-strings` | Feature `strings/` PascalCase `Strings` object |
| `/room-change` | Room entity/DAO, `SELECT *` ban, migrations |

### Reference Guides (Android)
| Skill | Purpose |
|-------|---------|
| `/theme-guide` | `MeAppTheme` tokens — colorScheme, typography, spacing; `@PreviewTheme` |
| `/logging-guide` | `AppLog` (never `Log`) |
| `/analytics` | Event instrumentation |

### Build, Test & Verify (Android)
| Skill/Agent | Purpose |
|-------------|---------|
| `/build` | `./gradlew assembleDebug` |
| `/verify-tests` | Unit tests + JaCoCo 80% gate |
| `/verify-on-emulator` | Prod-URL install → launch → screenshot/video |
| `/unit-tests` | Generate unit/instrumented tests (service/repo/VM/reducer/DAO/Compose) |
| `/android-service-test-writer` | MockK unit tests for a service class |
| `reducer-test-scaffolder` | Scaffold reducer/ViewModel tests |
| `compose-perf-analyzer` | Compose recomposition/jank via Perfetto |

### Review & Quality (Android)
| Skill | Purpose |
|-------|---------|
| `/detekt-fix` | detekt auto-fix + `!!` ban |
| `/self-review` | Run all 5 specialist reviews |
| `/review-lint` `/review-regression` `/review-security` `/review-issue-fix` `/review-accessibility` | The five checks |

### Git & Delivery (repo-root generic)
`/create-branch` · `/commit` · `/raise-pr` · `/log-work` · `/pr-description`

---

## 8. iOS ↔ Android Parity Table

So a task maps to comparable skills on either platform (kept in sync in both orchestras):

| Task | iOS | Android |
|------|-----|---------|
| New feature scaffold | `feature-slice` | `feature-slice` (MVI) |
| DI wiring | `wire-service` | `wire-service` (Hilt) |
| Navigation | `wire-navigation` | `wire-navigation` (AppRoute/Nav3) |
| Strings | `add-strings` | `add-strings` |
| Theme / tokens | `theme-guide` | `theme-guide` (MeAppTheme) |
| Logging | `logging-guide` | `logging-guide` (AppLog) |
| Analytics | `analytics` | `analytics` |
| Storage / DB | `storage-change` + `swiftdata` | `room-change` |
| Lint / format | `swiftlint` / `review-lint` | `detekt-fix` / `review-lint` |
| Pre-commit review | `self-review` (5 checks) | `self-review` (5 checks) |
| Build | `build` | `build` |
| Tests + coverage | `verify-tests` | `verify-tests` (JaCoCo) |
| Unit tests | `gen-test-file` | `unit-tests` / `android-service-test-writer` |
| Device/emulator run | *(physical device only)* | `verify-on-emulator` |
| DI impact | `di-impact-finder` | `hilt-impact-finder` |
| Coverage gaps | `coverage-gap-finder` | `coverage-gap-finder` |
| Perf | `ios-perf-analyzer` (static) | `compose-perf-analyzer` (Perfetto) |
| Dependency upgrade | *(gap)* | `upgrade-deps` (doc-sourced) |
| Asset / drawable | *(gap)* | `drawable-import` / `drawable-scan` |
| Git / Jira / PR | repo-root generic | repo-root generic |

---

## 9. Android Extensions (official Google skills — installed, not vendored)

These are Google's official Android skills from [`github.com/android/skills`](https://github.com/android/skills). They are **installed per-developer** via the `android` CLI, **not committed** to this repo — so they may or may not be present in a given checkout. Install what you need, then the orchestra wires them into the workflows below:

```bash
android skills add --skill=r8-analyzer --project=.   # or --skill=all
```

| Skill | Use for |
|-------|---------|

| Skill | Use for |
|-------|---------|
| `android-cli` | Create projects, manage AVDs, screenshots, UI inspection, on-device runs |
| `perfetto-trace-analysis` / `perfetto-sql` | Root-cause latency/jank/memory from a real trace |
| `r8-analyzer` | APK size + keep-rule optimization |
| `migrate-xml-views-to-jetpack-compose` | Legacy XML → Compose |
| `adaptive` | Foldable / tablet / desktop / TV / Auto / XR layouts |
| `edge-to-edge` · `styles` | Insets + the Compose Styles API |
| `navigation-3` | Navigation3 patterns (deep links, scenes, multiple backstacks) |
| `jetpack-compose-m3` | Wear OS Compose Material3 |
| `agp-9-upgrade` · `play-billing-library-version-upgrade` · `camera1-to-camerax` | Targeted migrations |
| `engage-sdk-integration` · `appfunctions` | Surface app content/workflows to the system & Google Play |

---

## 10. Live Documentation & Self-Maintenance

- **Runtime doc-fetch:** doc-heavy builder/reference skills (`room-change`, `wire-navigation`, `theme-guide`, `compose-perf-analyzer`, `upgrade-deps`) pull the current official pattern before applying — **context7 MCP first** (version-aware for Room/Compose/Hilt/AndroidX), **WebSearch/WebFetch fallback**. If docs are unavailable (headless/cron), degrade gracefully to cached guidance and say so.
- **`/upgrade-deps`** emits a per-bump report (version delta, breaking changes, migration steps, risk) and pauses on high risk.
- **`/refresh-skill-docs`** re-reads official docs and opens a PR when a skill's `reference/` section has drifted (never auto-merges).
- **Weekly scheduled routine** runs `/refresh-skill-docs` and opens a PR only when something actually changed.

---

## 11. Parallelization Opportunities

| Parallel Group | Skills |
|----------------|--------|
| Research phase | `/fetch-ticket` + `/read-figma` + `/read-jira-images` |
| Impact analysis | `hilt-impact-finder` + `coverage-gap-finder` |
| Review pipeline (read-only) | `/review-lint` + `/review-security` + `/review-accessibility` |

---

## 12. Error Recovery

| Situation | Action |
|-----------|--------|
| Build fails | Fix compilation errors, re-run `/build` |
| Tests fail | Investigate, fix, re-run `/verify-tests` |
| Coverage below 80% | Run `coverage-gap-finder`, add tests, re-run `/verify-tests` |
| detekt violation | `/detekt-fix`; never add `!!` or blanket `@Suppress` |
| Review finds issues | Fix all findings, re-run the specific `review-*` skill |
| Plan goes sideways | STOP. Re-enter plan mode. Re-assess. |
