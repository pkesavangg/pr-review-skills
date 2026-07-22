# MOB-1007 — Audit & Refresh iOS `.claude` Skills/Agents + Phase 2 Context

> **Status:** Plan (how-to) · **Author:** Kesavan P · **Date:** 2026-06-25
> **Branch:** `MOB-1007-audit-refresh-ios-claude-skills` (from `develop`)
> **Jira:** [MOB-1007](https://greatergoods.atlassian.net/browse/MOB-1007) — *Audit and refresh iOS .claude skills/agents* (In Progress, `dev-experience`, `ios`)
> **Sibling (done):** [MOB-259](https://greatergoods.atlassian.net/browse/MOB-259) — *[Android] Update skill format using Anthropic skill-creator tool* — the Android parity task this mirrors.

---

## 0. TL;DR — what we are actually doing

We use Claude Code as the primary dev tool for this repo. The iOS AI-context is rich but **drifting and structurally broken in one important way**: the 53 iOS "skills" are **flat `.md` files** (`iOS/.claude/skills/commit.md`), which Claude Code **does not auto-discover or auto-trigger as Skills**. They only work through the manual "read this file by path" mechanism wired into `iOS/CLAUDE.md` + `orchestra.md`. Android's skills already use the correct **`<skill-name>/SKILL.md` directory format**, so they *are* discovered (you can see them listed as available skills; none of the iOS ones are).

This task does five things:

1. **Convert** every iOS skill from flat `.md` → Anthropic **skill-creator format** (`<name>/SKILL.md`) so they auto-trigger.
2. **Place them correctly**: generic cross-platform skills at the **repo root** `.claude/skills/`; iOS-specific skills under **`iOS/.claude/skills/`**; use **symlinks** as a fallback so iOS skills still trigger when the project is opened from the `meApp` root.
3. **Give Claude Phase 2 context** — what "Me.Health 2.0 / Mega App" is, what's new vs. the shipped 5.0.x app, the new v3 unified APIs, the multi-product model, and the Figma 2.0 design system.
4. **Fix drift** — stale `MA-XXXX` examples, wrong branch base, dead doc links, dead root `settings.json`, duplicated `orchestra.md` section numbers.
5. **Add new technical iOS skills** for the Phase 2 surface (unified entries, paired-device, baby profiles/permissions, blood-pressure entries, product selection, measurement units) and verify the existing `swiftdata` / `swift-concurrency` / `graph` / `add-accessibility` / `review-accessibility` skills match current code.

The output of *this* step is this document + the new branch. Implementation follows on approval.

---

## 1. Decisions (confirmed)

| # | Decision | Resolution |
|---|----------|------------|
| D7 | Jira-key references in skills | **Switch all examples `MA-XXXX` → `MOB-XXXX`.** `MA` is deprecated (per both CLAUDE.md files). |
| D-branch | Base branch for `create-branch` / examples | **`develop`.** `phase2-dev` has been **merged into `develop`**, which is now the up-to-date integration branch carrying Phase 2. New work branches from and targets `develop`. `main` = legacy 5.0.x (MA) release line only. |
| D-place | Skill placement | **Split + symlink fallback.** Generic → root `.claude/skills/`; iOS-specific → `iOS/.claude/skills/`; symlink iOS skills into root if scoped discovery is unreliable. |
| D-format | Skill format | **Anthropic skill-creator `<name>/SKILL.md`** (the `skill-creator@claude-plugins-official` plugin already enabled in `iOS/.claude/settings.json`). |

---

## 2. Background — what Phase 2 ("Me.Health 2.0 / Mega App") is

Claude needs to *know this* to give correct guidance. Today **none** of the CLAUDE.md / orchestra / skills mention Phase 2 at all.

### 2.1 The one-line difference

| | Phase 1 (shipped) | Phase 2 (in progress) |
|---|---|---|
| Product | **Weight Gurus** — weight + body-composition only | **Me.Health "Mega App"** — one app for **Weight + Blood Pressure (Balance) + Baby** |
| Release | `v5.0.0` → `v5.0.2` (Apr–May 2026), `main` / MA line | Next major, "2.0", on `develop` (was `phase2-dev`) |
| Server | wgServer3 v3, weight endpoints | wgServer3 v3 **multi-product merge** — Weight Gurus + Balance + Baby into one server |
| Account | single product implied | **`productTypes`** array: `weight`, `blood_pressure`, `baby` + **`measurementUnits`** |
| Data | weight entries only | **unified entries** across categories (`weight` / `bp` / `baby`) |

Current shipped tags (`gh release list`): **`v5.0.0` (2026-04-30), `v5.0.1` (2026-05-06), `v5.0.2` (2026-05-25)** — all the Phase-1 / MA line.

### 2.2 What's new in the API (source: [Confluence — Me App 2.0 API Changes Specification](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1458962434/Me+App+2.0+API+Changes+Specification))

Base URL `/v3`, Bearer auth. **15 modified + 19 new = 34 endpoints.** The Phase-2-defining additions:

**Account / product model**
- `productTypes`: `["weight" | "blood_pressure" | "baby"]` — added to signup/login/`GET`/`PUT /v3/account/` + all 8 `PATCH /account/*` responses. Auto-managed (pairing a device / creating a baby / adding an entry adds the product) **and** directly settable.
- `measurementUnits`: `metric` | `imperialLbOz` | `imperialLbDecimal` (required for baby-only users).
- New: `PATCH /v3/account/measurement-units`, `PATCH /v3/account/products`, `POST /v3/account/email-check` (no auth).
- `gender` / `dob` / `height` are now **conditionally** required (only for weight/BP users) — important for signup forms.

**Unified Device API** — `POST/GET/PATCH/DELETE /v3/paired-device/` with `deviceType` (`weight_scale` | `baby_scale` | `bpm`). Replaces (legacy still alive) `/v3/paired-scale/`.

**Unified Review API** — `POST /v3/review/` with `reviewType` (`app` | `scale` | `monitor`). Replaces `/v3/review/app` + `/v3/review/scale`.

**Baby APIs** — `POST/GET/PUT/DELETE /v3/baby/`, `GET /v3/baby/:id/accounts`, `PUT .../permissions` (levels 1 view / 2 view+create / 3 owner), `POST /v3/invitation/:babyId` (caregiver invites).

**Unified Entries API** — `POST /v3/entries/` (raw array, atomic, `category` routes to weight/bp/baby tables), `GET /v3/entries/` (two modes: **sync** via `?start` and **cursor pagination** via `?cursor`+`limit`), `GET /v3/entries/csv`. Baby `entryType`s: `weight`, `feedingBottle`, `feedingNursing`, `measureLength`, `sleep`, `snapshot`, `diaperChange`.

> Legacy weight endpoints (`/v3/operation/*`, `/v3/paired-scale/`, `/v3/review/*`) remain unchanged for old apps — **backward compatibility is a hard requirement**.

### 2.3 Phase 2 in the codebase (already present on `develop`)

Evidence Phase 2 is live in this repo:
- `EntrySnapshot` already includes `BPMEntrySnapshot` and `BabyEntrySnapshot` (see `iOS/CLAUDE.md` → Snapshots table).
- Remote branches: `MOB-382/383/384/385/386` (iOS Phase 2: account foundation, unified device+review, unified entries write, unified entries read, baby integration), plus Android `MOB-377…381`.
- Android already has Phase 2 graph/baby/bp plans under `Android/docs/plans/`.

### 2.4 Phase 2 design (Figma)

Me.Health Mega App 2.0 — file `k0HO1SquDGrYOcoMSbrzA0`:
- **Design system / tokens:** `node-id=8-2145` → https://www.figma.com/design/k0HO1SquDGrYOcoMSbrzA0/Me.Health-Mega-App-2.0?node-id=8-2145
- **Screens:** `node-id=26501-375864` → https://www.figma.com/design/k0HO1SquDGrYOcoMSbrzA0/Me.Health-Mega-App-2.0?node-id=26501-375864

> Note: live Figma MCP token/variable extraction requires the node to be **selected in the Figma desktop app**; the existing `read-figma` skill already handles per-ticket extraction. Phase 2 awareness here means *capturing these links + the design-system node IDs* in a reference skill so Claude always knows where the 2.0 design lives.

---

## 3. Root-cause: why iOS skills "don't get triggered"

| Aspect | iOS skills (today) | Android skills (today) | What Claude Code needs |
|---|---|---|---|
| Format | flat `iOS/.claude/skills/commit.md` (53 files) | `Android/.claude/skills/upgrade-deps/SKILL.md` (5 dirs) | `<skill-name>/SKILL.md` directory |
| Auto-discovered as a Skill? | **No** | **Yes** (listed as available, scoped to `Android/`) | dir + `SKILL.md` + valid frontmatter |
| How they run today | only by `orchestra.md` / `iOS/CLAUDE.md` reading the file **by path** | invoked as real `/skill` | — |
| Discoverable from `meApp` root? | No | Yes, but **scoped** ("applies when working on files under Android/") | root-level skills are always in scope |

**Two distinct problems fall out of this:**

1. **Format** — flat `.md` is never registered as a Skill. Fix = convert to `<name>/SKILL.md`.
2. **Scope/working-directory** — even correctly-formatted nested skills are scoped to their subtree. When you open the project at the `meApp` root and ask a *generic* thing ("commit", "raise a PR"), an `iOS/`-scoped skill may not fire. Fix = put generic skills at root; symlink iOS skills to root as a fallback.

This is exactly the structure you asked for: **generic outside iOS, iOS-specific inside iOS, symlink to bridge.**

---

## 4. Target architecture

```
meApp/                              ← you open Claude Code HERE
├── .claude/
│   ├── settings.json              ← REWRITE (currently dead /Users/rengadevi paths)
│   │                                 enable skill-creator plugin at root too
│   └── skills/                    ← GENERIC, cross-platform (always trigger)
│       ├── commit/SKILL.md
│       ├── create-branch/SKILL.md
│       ├── fetch-ticket/SKILL.md
│       ├── log-work/SKILL.md
│       ├── pr-description/SKILL.md
│       ├── gen-pr-description-template/SKILL.md
│       ├── raise-pr/SKILL.md
│       ├── read-figma/SKILL.md
│       ├── read-jira-images/SKILL.md
│       ├── create-prd/SKILL.md
│       ├── phase2-context/SKILL.md          ← NEW (multi-product / unified API model)
│       ├── (existing) compose-expert-skill/  ← keep
│       └── (existing) swiftui-expert-skill/  ← keep
│
├── iOS/
│   ├── CLAUDE.md                  ← add Phase 2 block; fix skill path refs after conversion
│   └── .claude/
│       ├── orchestra.md           ← fix MA→MOB, branch base, dup §4.11, new skills
│       ├── settings.json          ← keep (good hooks); minor: MOB wording
│       ├── MCP_SERVERS.md         ← keep
│       ├── agents/ (5)            ← audit (see §6)
│       ├── commands/ (3)          ← audit (work-ticket / release-cut / review-pr)
│       └── skills/                ← iOS-SPECIFIC only, as <name>/SKILL.md
│           ├── swiftdata/SKILL.md
│           ├── swift-concurrency/SKILL.md
│           ├── theme-guide/SKILL.md
│           ├── graph/SKILL.md
│           ├── add-accessibility/SKILL.md
│           ├── ... (all iOS technical skills)
│           ├── phase2-design-system/SKILL.md     ← NEW (Figma 2.0 links + tokens)
│           ├── unified-entries/SKILL.md           ← NEW (multi-category entries)
│           ├── paired-device/SKILL.md             ← NEW (/v3/paired-device/)
│           ├── baby-profile/SKILL.md              ← NEW (baby + permissions/invites)
│           └── product-selection/SKILL.md         ← NEW (productTypes/measurementUnits)
│
└── Android/.claude/skills/        ← already correct (parity reference)
```

### 4.1 Symlink strategy (the fallback you asked for)

Claude Code discovers skills relative to the project root. When you open `meApp/`, root `.claude/skills/` is always in scope; `iOS/.claude/skills/` is scoped to iOS work. If a needed iOS skill does **not** trigger from the root, bridge it with a symlink so it appears as a root-level skill **without duplicating the file** (single source of truth stays in `iOS/`):

```bash
# Option A — bridge the whole iOS skills tree under a namespaced root folder
ln -s ../../iOS/.claude/skills "$(git rev-parse --show-toplevel)/.claude/skills-ios"

# Option B — bridge a single iOS skill into root (preferred: precise, no noise)
cd "$(git rev-parse --show-toplevel)/.claude/skills"
ln -s ../../iOS/.claude/skills/graph graph
```

Decision rule:
- **Default:** rely on scoped discovery (no symlink). Generic work is covered by root skills; iOS work under `iOS/` picks up iOS skills.
- **If** an iOS skill must be invokable from the root regardless of cwd → **Option B** (symlink that one skill).
- Use **relative** symlinks (above) so they survive `git clone` / different checkout paths. Verify Git tracks them as symlinks (`git config core.symlinks true`, default on macOS). Document any symlink in the skills README so it isn't mistaken for a duplicate.

> Trade-off to record in the PR: a root-symlinked iOS skill loses its iOS scoping and will also be offered while working on Android. Only symlink skills that are genuinely useful platform-agnostically, or that you always want available.

### 4.2 Generic vs iOS-specific split

**Generic → root `.claude/skills/`** (no Swift/Xcode assumptions; work from any cwd):
`commit`, `create-branch`, `fetch-ticket`, `log-work`, `pr-description`, `gen-pr-description-template`, `raise-pr`, `read-figma`, `read-jira-images`, `create-prd`, `phase2-context`.

**iOS-specific → `iOS/.claude/skills/`** (Swift/SwiftUI/Xcode/SwiftData/Swift-Testing):
everything else — `build`, `run-tests`, `verify-tests`, `analyze-coverage`, `visual-regression`, `feature-slice`, `add-endpoint`, `wire-service`, `wire-navigation`, `add-strings`, `gen-test-file`, `gen-ui-test-file`, `gen-mock-single`, `update-mock`, `add-preview`, `add-accessibility`, `analytics`, `keychain-pattern`, `api-guide`, `form-guide`, `logging-guide`, `notification-guide`, `theme-guide`, `swift-concurrency`, `swiftdata`, `storage-change`, `config-change`, `graph`, `refactor`, `debug-issue`, `fix-bug`, `fix-pr-comments`, `self-review`, `post-change-guard`, `swiftlint`, `review-*` (lint/security/regression/issue-fix/accessibility/code-standards/ui-standards), `update-architecture`, + new Phase 2 technical skills.

> `gen-pr-description-template` overlaps with `pr-description` — fold into one during conversion (see §6).

---

## 5. Implementation plan (phased)

Each phase is independently committable. Run `git mv` (not delete+add) so history follows the files where possible; for flat→dir, `git mv commit.md commit/SKILL.md`.

### Phase A — Format migration (flat `.md` → `<name>/SKILL.md`)
1. Enable `skill-creator@claude-plugins-official` in **root** `.claude/settings.json` (currently only iOS) so `/skill-creator` is available from the `meApp` root.
2. For each skill: `mkdir <name>/ && git mv <name>.md <name>/SKILL.md`.
3. Use **`/skill-creator`** to validate/normalize each `SKILL.md`: required `name` + `description` frontmatter, description in the *"<what> — use when <triggers>"* style, body trimmed to imperative steps. Move long reference material into the skill's own `reference/` subdir (as Android `unit-tests/` and `upgrade-deps/` already do) to keep `SKILL.md` lean.
4. The big guides (`api-guide` 20 KB, `notification-guide` 21 KB, `theme-guide` 16 KB, `form-guide` 13 KB, `logging-guide` 12 KB, `keychain-pattern` 12 KB) → `SKILL.md` (short, trigger-focused) + `reference/*.md` (the bulk).

### Phase B — Placement + symlinks
1. Move the 11 generic skills to root `.claude/skills/` (§4.2).
2. Keep iOS-specific skills under `iOS/.claude/skills/`.
3. Add symlinks only where §4.1's decision rule requires.
4. Update **every** path reference: `iOS/CLAUDE.md` (the big skill-matching table references `.claude/skills/X.md` — becomes `.claude/skills/X/SKILL.md`, and generic ones move to repo-root); `orchestra.md` skill paths; cross-skill references.

### Phase C — Phase 2 context injection
1. Add a **Phase 2 section** to root `CLAUDE.md` and `iOS/CLAUDE.md` (the §2 content here, condensed): multi-product model, `productTypes`/`measurementUnits`, unified endpoints, baby permissions, backward-compat rule, current branch = `develop`.
2. New generic skill **`phase2-context`** — the canonical "what is 2.0" brief + API-change cheat sheet (links to Confluence) so any task can pull it in.
3. New iOS skill **`phase2-design-system`** — Figma file key + the two node IDs (design system `8-2145`, screens `26501-375864`) + how to pull tokens via `read-figma`; map Figma tokens → `Theme/` tokens.
4. Update `docs/guides/PRODUCT_TYPES_CURRENT_STATE.md` if it predates the `productTypes` API setter (`PATCH /v3/account/products`).

### Phase D — Drift / stale-reference fixes
1. **`orchestra.md`:** `MA-XXXX` → `MOB-XXXX` (line ~259 verification checklist); "Branch from main" → "Branch from `develop`" (lines ~75, ~332); fix the **duplicate `### 4.11`** (Fix PR Comments *and* Release Preparation both numbered 4.11 → renumber Release to 4.12).
2. **Root `.claude/settings.json`:** delete the dead `/Users/rengadevi/...` allow-list entries and obsolete `mcp__atlassian__*` names (current server is `mcp__claude_ai_Atlassian__*`); replace with a minimal, portable allow-list (no hardcoded home paths).
3. **Skill examples:** `MA-3316`/`MA-33xx`/`MA-35xx` → `MOB-` equivalents across `commit`, `create-branch`, `create-prd`, `fetch-ticket`, `raise-pr`, `pr-description`, `review-issue-fix`, `self-review`.
4. **`create-branch`:** base branch `main` → `develop`; keep a note that `main` is the 5.0.x release line.
5. **Dangling doc links:** resolve or remove references to files that don't exist — the ticket's own `docs/plans/2026-06-23-phase2-repo-hygiene-ci-ai-tooling-epic.md` (not in repo), `iOS/docs/plans/` (orchestra §4.2), `tasks/todo.md` + `tasks/lessons.md` (orchestra §2/§10). Either create stubs or update the references.

### Phase E — New / refreshed technical iOS skills for Phase 2
1. **Verify-against-code** (ticket §3): `swiftdata`, `swift-concurrency`, `graph`, `add-accessibility`, `review-accessibility` — confirm they reflect the snapshot-boundary rule, `BPMEntrySnapshot`/`BabyEntrySnapshot`, and current chart code. Update examples to multi-product where relevant.
2. **New skills** (thin SKILL.md + reference, grounded in real files):
   - `unified-entries` — `POST/GET /v3/entries/`, `category` routing, sync vs cursor modes, `EntrySnapshot` family.
   - `paired-device` — `/v3/paired-device/` + `deviceType`, mapping to `DeviceSnapshot`.
   - `baby-profile` — baby CRUD, permission levels, invitations.
   - `product-selection` — `productTypes` / `measurementUnits`, conditional signup validation, `PATCH /account/products`.
3. **Align taxonomy with Android** (ticket §4): name iOS skills so tasks map across platforms (e.g. Android `unit-tests` ↔ iOS `gen-test-file`/`verify-tests`; Android `upgrade-deps` ↔ add an iOS SPM `upgrade-deps`; drawable skills ↔ iOS asset skill if warranted). Produce the mapping table in the skills README.

### Phase F — Verification & docs
1. **Discovery check:** open Claude Code at `meApp` root, confirm generic skills appear in the available-skills list and iOS skills appear when working under `iOS/` (or via symlink). The current session already proves Android skills surface and iOS flat ones don't — re-run after conversion.
2. **Link check:** a script that greps every `SKILL.md` / `orchestra.md` / `CLAUDE.md` for `.md` / path references and fails on any that don't resolve (the ticket's optional "CI/precommit check"). Add to lint/pre-commit.
3. **`skills/README.md`** (ticket optional) — table of every skill: name · purpose · platform · where it lives. Include the Android↔iOS taxonomy map.
4. Update `orchestra.md` workflow sequences to reference the new skill paths/names (acceptance criterion: sequences still match the refreshed set).

---

## 6. Audit — current inventory & action

### 6.1 Skills (53 iOS flat files)

| Group | Skills | Action |
|-------|--------|--------|
| **Generic git/Jira/PR** | `commit`, `create-branch`, `fetch-ticket`, `log-work`, `pr-description`, `gen-pr-description-template`, `raise-pr` | **Convert + move to root.** Fix `MA→MOB`; `create-branch` base → `develop`. **Merge** `gen-pr-description-template` into `pr-description`. |
| **Generic planning** | `create-prd`, `read-figma`, `read-jira-images` | Convert + move to root. (`create-prd` references `iOS/docs/plans/` — fix path.) |
| **iOS scaffolding/wiring** | `feature-slice`, `add-endpoint`, `wire-service`, `wire-navigation`, `add-strings`, `add-preview`, `gen-mock-single`, `update-mock` | Convert, keep in iOS. Refresh `add-endpoint` to mention unified `/v3/entries/` + `/v3/paired-device/`. |
| **iOS testing** | `build`, `run-tests`, `verify-tests`, `gen-test-file`, `gen-ui-test-file`, `analyze-coverage`, `visual-regression` | Convert, keep in iOS. Confirm physical-device requirement + Swift Testing wording current. |
| **iOS quality/review** | `self-review`, `post-change-guard`, `swiftlint`, `review-lint`, `review-security`, `review-regression`, `review-issue-fix`, `review-accessibility`, `review-code-standards`, `review-ui-standards`, `refactor` | Convert, keep in iOS. `review-issue-fix`/`self-review` `MA→MOB`. |
| **iOS debugging** | `debug-issue`, `fix-bug`, `fix-pr-comments` | Convert, keep in iOS. |
| **iOS reference guides** | `api-guide`, `form-guide`, `logging-guide`, `notification-guide`, `theme-guide`, `keychain-pattern`, `analytics` | Convert, keep in iOS, **split into `SKILL.md` + `reference/`** (large files). |
| **iOS data/concurrency/config** | `swiftdata`, `swift-concurrency`, `storage-change`, `config-change`, `graph` | Convert, keep in iOS, **verify vs current code** (ticket §3). |
| **iOS docs** | `update-architecture` | Convert, keep in iOS. |
| **NEW (iOS, Phase 2)** | `phase2-design-system`, `unified-entries`, `paired-device`, `baby-profile`, `product-selection` | Create. |
| **NEW (generic, Phase 2)** | `phase2-context` | Create at root. |

### 6.2 Agents (5) — `iOS/.claude/agents/`

| Agent | Action |
|-------|--------|
| `api-change-planner` | Keep; update to know unified `/v3/entries/` + `/v3/paired-device/` + product auto-add. |
| `coverage-gap-finder` | Keep; confirm physical-device coverage pipeline still accurate. |
| `di-impact-finder` | Keep. |
| `gen-mock-batch` | Keep. |
| `ios-perf-analyzer` | Keep. |

### 6.3 Commands (3) — `iOS/.claude/commands/`

| Command | Action |
|---------|--------|
| `work-ticket` | Update: branch from `develop`, `MOB-` prefix, new skill paths. |
| `release-cut` | Keep; confirm against 5.0.x tag flow + upcoming 2.0. |
| `review-pr` | Keep; ensure it references the refreshed review skills. |

### 6.4 Config / docs to fix

| File | Issue | Action |
|------|-------|--------|
| `.claude/settings.json` (root) | Dead `/Users/rengadevi/...` paths, old `mcp__atlassian__*` names | Rewrite minimal + portable; enable `skill-creator` |
| `iOS/.claude/settings.json` | Good (hooks, plugins) | Keep; optional MOB wording |
| `iOS/.claude/orchestra.md` | `MA-XXXX` (§6 checklist), "branch from main", duplicate `### 4.11` | Fix all three |
| `iOS/CLAUDE.md` | Skill paths `.claude/skills/X.md`; no Phase 2 | Update paths post-conversion; add Phase 2 block |
| `CLAUDE.md` (root) | No Phase 2 | Add Phase 2 block + `develop` note |
| Ticket spec `docs/plans/2026-06-23-…epic.md` | Referenced, not in repo | Create or stop referencing |

---

## 7. Acceptance criteria → coverage map

| MOB-1007 acceptance criterion | Covered by |
|---|---|
| Written audit (keep/update/remove + reason) of every skill/agent/command | §6 (this doc) → finalized in `skills/README.md` (Phase F) |
| All path/doc references resolve (no dangling links) | Phase D.5 + Phase F.2 (link-check script) |
| Jira-project / branch references corrected per D7 | Phase D.1, D.3, D.4 (`MA→MOB`, base `develop`) |
| Accessibility + graph + SwiftData/concurrency reflect current code | Phase E.1 |
| `orchestra.md` sequences still match refreshed skill set | Phase D.1 + Phase F.4 |
| *(optional)* `skills/README.md` index | Phase F.3 |
| *(optional)* CI/precommit broken-link check | Phase F.2 |

Plus the explicit asks in this request:
- **Skills placed correctly / skill-creator format** → Phase A + B.
- **Claude has Phase 2 context** → Phase C + §2.
- **Generic outside iOS, specific in iOS, symlink bridge** → §4 + Phase B.
- **Technical iOS skills to make work better** → Phase E.

---

## 8. Risks & rollback

- **Path churn breaks the manual orchestrator.** `iOS/CLAUDE.md` + `orchestra.md` read skills by path; converting flat→dir changes every path. Mitigation: do Phase A+B+D.path-updates in one PR, then run the Phase F discovery + link checks before merge.
- **Symlinks across platforms / Git.** Use relative symlinks; verify `core.symlinks=true`; document each in the README. Rollback: delete the symlink (source file untouched in `iOS/`).
- **Scoped-discovery uncertainty.** We *know* nested `SKILL.md` skills are discovered but scoped (Android proves it). If iOS scoping is too narrow in practice, the symlink fallback (§4.1) is the escape hatch — decided per-skill, not globally.
- **Backward compatibility.** Any Phase 2 skill that touches the API must state the legacy endpoints remain live; never guide removal of `/v3/operation/*` or `/v3/paired-scale/`.
- Everything is on branch `MOB-1007-audit-refresh-ios-claude-skills`; revert = drop the branch.

---

## 9. Suggested commit sequence (one PR, targeting `develop`)

1. `MOB-1007 Enable skill-creator at root; rewrite dead root settings.json`
2. `MOB-1007 Convert generic skills to SKILL.md and move to repo-root .claude/skills`
3. `MOB-1007 Convert iOS skills to SKILL.md format (flat .md → <name>/SKILL.md)`
4. `MOB-1007 Add Phase 2 context to CLAUDE.md + phase2-context / phase2-design-system skills`
5. `MOB-1007 Add Phase 2 technical iOS skills (unified-entries, paired-device, baby-profile, product-selection)`
6. `MOB-1007 Fix orchestra.md drift (MA→MOB, base develop, dup §4.11) + update skill paths`
7. `MOB-1007 Add skills/README index + broken-link check + Android↔iOS taxonomy map`
