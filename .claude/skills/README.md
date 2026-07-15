# meApp Claude Code skills — catalog

How Claude Code skills are organized in this monorepo, what each one does, and how the two platforms map to each other. Audited and refreshed under [MOB-1007](https://greatergoods.atlassian.net/browse/MOB-1007).

## How skills work here

- **Format:** every skill is a directory containing a `SKILL.md` with `name` + `description` frontmatter (the Anthropic **skill-creator** format). Flat `.md` files are **not** discovered. Use `/skill-creator` to scaffold/validate new ones.
- **Auto-trigger:** Claude Code matches a request against each skill's `description`. No manual "read the file by path" step.
- **Scoping & placement:**
  - **Generic skills → repo-root `.claude/skills/`** — trigger anywhere in the monorepo.
  - **iOS skills → `iOS/.claude/skills/`** — scoped to work under `iOS/`.
  - **Android skills → `Android/.claude/skills/`** — scoped to work under `Android/`.
- **Symlink fallback:** if an iOS/Android-scoped skill must be reachable from the monorepo root regardless of working directory, symlink it into root with a **relative** link, e.g.:
  ```bash
  cd "$(git rev-parse --show-toplevel)/.claude/skills" && ln -s ../../iOS/.claude/skills/graph graph
  ```
  The source stays single-sourced under the platform folder; the symlink just widens its scope. Note the trade-off: a root-symlinked iOS skill is then also offered while working on Android.
- **Tracking:** shared skills are committed (see `.gitignore` — `.claude/*` is ignored but skills/agents/commands/orchestra/settings.json are re-included). Local `settings.local.json` stays ignored.

---

## Root — generic, cross-platform (`.claude/skills/`)

| Skill | Purpose |
|-------|---------|
| `fetch-ticket` | Fetch full Jira issue details from greatergoods.atlassian.net |
| `create-branch` | Branch from `develop` with `{ISSUE-ID}-{slug}`, transition Jira to In Progress |
| `create-prd` | Write an implementation plan (PRD) before non-trivial work |
| `commit` | Stage + commit with `MOB-XXXX` prefix (no attribution trailer) |
| `pr-description` | Generate a PR description from a branch or existing PR |
| `gen-pr-description-template` | Shared PR-description template (used by `pr-description` + `raise-pr`) |
| `raise-pr` | Push and open a PR (targets `develop` unless told otherwise) |
| `log-work` | Log time on the Jira issue |
| `read-figma` | Extract a Design Summary from a Figma URL in a ticket |
| `read-jira-images` | Analyze Jira image attachments with vision |
| **`phase2-context`** | **Phase 2 (Me.Health 2.0) product + unified-API model — auto-triggers on multi-product/2.0 work** |

Plus the two cross-platform "expert" reference skills: `compose-expert-skill` (Android Compose) and `swiftui-expert-skill` (iOS SwiftUI).

---

## iOS (`iOS/.claude/skills/`) — 49 skills

**Scaffolding & wiring:** `feature-slice`, `add-endpoint`, `wire-service`, `wire-navigation`, `add-strings`, `add-preview`, `add-accessibility`, `gen-mock-single`, `update-mock`
**Testing & coverage:** `build`, `run-tests`, `verify-tests`, `gen-test-file`, `gen-ui-test-file`, `analyze-coverage`, `visual-regression`
**Build tooling:** **`prepare-simulator-build`** (flip `ggWifiScalePackage`/`ggBluetoothNativeLibrary` to their `simulator-support` branch so the app builds on the Simulator, and back again — via `scripts/sim-packages.sh`; **root-symlinked** so it triggers from the monorepo root)
**Review & quality:** `self-review`, `post-change-guard`, `swiftlint`, `review-lint`, `review-security`, `review-regression`, `review-issue-fix`, `review-accessibility`, `review-code-standards`, `review-ui-standards`, `refactor`
**Debugging:** `debug-issue`, `fix-bug`, `fix-pr-comments`
**Reference guides:** `api-guide`, `form-guide`, `logging-guide`, `notification-guide`, `theme-guide`, `keychain-pattern`, `analytics`
**Data / concurrency / config:** `swiftdata`, `swift-concurrency`, `storage-change`, `config-change`, `graph`
**Docs:** `update-architecture`
**Phase 2 technical (new):** **`phase2-design-system`** (Figma 2.0 → Theme tokens), **`unified-entries`** (`/v3/entries/`), **`paired-device`** (`/v3/paired-device/`), **`baby-profile`** (`/v3/baby/`, owner-CRUD only), **`product-selection`** (`productTypes`/`measurementUnits`)

Agents (`iOS/.claude/agents/`): `api-change-planner`, `coverage-gap-finder`, `di-impact-finder`, `gen-mock-batch`, `ios-perf-analyzer`.
Commands (`iOS/.claude/commands/`): `work-ticket`, `release-cut`, `review-pr`. Orchestration: `iOS/.claude/orchestra.md`.

---

## Android (`Android/.claude/skills/`) — 5 skills

| Skill | Purpose |
|-------|---------|
| `unit-tests` | Generate unit + instrumented tests (service/repo/VM/reducer/DAO/Compose) |
| `android-service-test-writer` | MockK unit tests for a service class |
| `drawable-import` | Import SVG/PNG/GIF/WebP/Lottie into `res/` with naming + registration |
| `drawable-scan` | Audit drawables — duplicates, unused, naming violations |
| `upgrade-deps` | Phased, risk-ordered dependency upgrades in `libs.versions.toml` |

---

## Android ↔ iOS taxonomy

So a task maps to comparable skills on either platform:

| Task | iOS | Android |
|------|-----|---------|
| Write unit tests | `gen-test-file` + `verify-tests` | `unit-tests` / `android-service-test-writer` |
| UI tests | `gen-ui-test-file` | `unit-tests` (Compose UI mode) |
| Generate a mock | `gen-mock-single` (+ `gen-mock-batch` agent) | (MockK inline in `unit-tests`) |
| Coverage check | `analyze-coverage` / `verify-tests` | `unit-tests` + JaCoCo |
| Theming / tokens | `theme-guide` | `compose-expert-skill` (meapp-theme) |
| Dependency upgrade | *(gap — SPM upgrade skill TODO)* | `upgrade-deps` |
| Asset / drawable import | *(gap — asset-catalog skill TODO)* | `drawable-import` / `drawable-scan` |
| Phase 2 product model | `product-selection` + `phase2-context` | *(parity TODO)* + `phase2-context` |
| Phase 2 unified entries | `unified-entries` | *(parity TODO)* |
| Git / Jira / PR workflow | root generic skills | root generic skills |

Identified parity gaps (follow-ups): iOS SPM `upgrade-deps`, iOS asset-catalog import/scan, Android Phase 2 technical skills.

---

## Maintenance

- **Add/edit a skill:** use `/skill-creator`; keep `SKILL.md` lean (move bulk into a `reference/` subdir as `unit-tests/` and `upgrade-deps/` do).
- **Validate links:** run `scripts/check-skill-links.sh` (flags broken file/`.md` references in skills, CLAUDE.md, orchestra.md).
- **Jira/branch conventions:** examples use `MOB-XXXX`; new branches base off `develop`.
