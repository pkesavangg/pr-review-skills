# Confluence structure — Me App

The meApp project's documentation hub lives in Confluence, space **Greater Goods Technology**
(`/wiki/spaces/GGT/`). The [**Me App**](https://greatergoods.atlassian.net/wiki/spaces/GGT/pages/1542782978)
page (id `1542782978`) is the source-of-truth hub; four section pages hang off it. This file
is the **local map** of that tree — page IDs, what each page covers, and which repo changes
should sync to which page. Keep the IDs here in step with Confluence; the
[`update-confluence`](../.claude/skills/update-confluence/SKILL.md) skill reads them.

## Page tree

| Page | ID | Covers |
|------|----|--------|
| **Me App** (hub) | `1542782978` | Overview, three products, at-a-glance table, key links. Parent: App Families (`1475870753`). |
| ├ **meApp - Development** | `1552482315` | Architecture (iOS + Android), repo structure, Phase 2, v3 APIs, dependencies & packages, design tokens, **CI/CD & automation**. |
| ├ **meApp - Testing** | `1552678923` | Unit testing & coverage gates, regression suites, `meAppTest` E2E framework. |
| │  ├ Mobile Test Automation | `1476329473` | E2E / UI automation overview. |
| │  │  ├ Unit Testing & Code Coverage in Swift | `601882648` | iOS unit-test + coverage guide. |
| │  │  └ Android Unit Testing Infrastructure | `1364951047` | Android unit-test guide. |
| │  ├ Regression Test Suites | `1479639062` | Regression plans + per-build test-case pages. |
| │  └ meApp - TestAutomation | `1557856285` | Test-automation framework page. |
| ├ **meApp - UX** | `1552678945` | UI/UX queries & resolutions, design references, Figma design system. |
| │  └ WG-5.0.0 UI/UX Queries & Reference | `1118208007` | Resolved UX-query reference table. |
| └ **meApp - Releases** | `1552809985` | Release process, deployment guides, release QA & store submission. |
| &nbsp;&nbsp;&nbsp;└ Releases and QA | `1475182614` | Release/QA hub. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;├ Me. App iOS Deployment Guide | `1327824993` | iOS App Store deployment steps. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;├ Me. App Android Deployment Guide | `1331724289` | Android Play Store deployment steps. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;├ Mobile App Deployment Workflow | `1323827249` | Cross-platform release workflow. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;└ Test Plan, Sprint Coverage & Release Strategy | `1489698821` | Per-release test plan. |

## Which repo change syncs to which page

The **meApp - Development** page (`1552482315`) mirrors this repo's architecture docs and
[`docs/overview/CLAUDE_AUTOMATION.md`](CLAUDE_AUTOMATION.md), so it's the target for almost every code/config change:

| Repo change | Confluence page → section |
|---|---|
| Claude skills/agents/commands added or removed (`.claude/**`, `iOS/.claude/**`, `Android/.claude/**`) | **meApp - Development** → *CI/CD & automation* |
| Build/run scripts (`scripts/run.sh`, `scripts/*.sh`), CI (`.circleci/*`), git hooks (`.lefthook.yml`) | **meApp - Development** → *CI/CD & automation* |
| New feature / service / SwiftData·Room model / DI registration | **meApp - Development** → *Architecture* |
| Schema, product types, dependencies, design tokens | **meApp - Development** (matching subsection) |
| Unit-test infra / coverage-gate / E2E framework changes | **meApp - Testing** (`1552678923`) |
| Release-process / deployment-script changes | **meApp - Releases** (`1552809985`) |

## Keeping Confluence in sync

This is a **two-step mirror**: code → local doc → Confluence.

1. A code change updates the matching local `docs/` file (enforced by the docs-freshness hook — see [CLAUDE_AUTOMATION.md](CLAUDE_AUTOMATION.md)).
2. The same hook prints a `🌐 Also mirror this to Confluence → …` line naming the page above.
3. Run [`/update-confluence`](../.claude/skills/update-confluence/SKILL.md) — it reads the target page, drafts the section edit from the current repo state, shows you the before/after, and **writes only after you approve**.

Confluence is a shared, hand-curated hub, so writes are never automatic. If you add, move, or
retire a page, update the IDs in this file **and** the `CONF_PAGE` note in
[`scripts/docs-freshness-check.sh`](../scripts/docs-freshness-check.sh).

### Update style — edit in place, not append-only

Keep each page's sections **current by editing them in place** — Confluence keeps a full version
history (author + message + diff) on every save, so editing in place loses nothing. Don't append
dated notes at the bottom (it leaves the reference body stale and contradictory) and don't rewrite
whole pages. For a *notable* change, add one dated row to the page's history table (on **meApp -
Development** that's the §1 *Overview & history* `| Date | Milestone |` table). The `/update-confluence`
skill encodes this convention.
