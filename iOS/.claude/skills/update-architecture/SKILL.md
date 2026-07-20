---
name: update-architecture
description: Update architecture.md AND the maintained docs/ folder after a structural or documented-behaviour change to the codebase. Use after adding a new feature, service, SwiftData/Room model, external dependency, DI registration, or after changing schema, product types, account switching, dashboard graph behaviour, CI, or repo automation. Also use when the user says "update architecture", "architecture is outdated", "docs are stale", "docs out of date", "the docs didn't update", or "reflect this in the docs". Recommended follow-up after /feature-slice and /wire-service, and whenever the docs-freshness hook reports a doc may be stale.
---

Keep architecture.md AND the maintained `docs/` folder current after a change.

The change that was made is: $ARGUMENTS

## Scope — which docs this skill owns

This skill keeps two things in sync with the code:

1. **`iOS/architecture.md`** — the iOS structural inventory (Steps 2–3 below).
2. **The maintained monorepo `docs/`** — via the source→doc map below. The same map
   is encoded in [`scripts/docs-freshness-check.sh`](../../../../scripts/docs-freshness-check.sh),
   which the root `.claude/settings.json` PostToolUse hook runs on every edit to remind
   you a doc may be stale. When the hook fires, come here and update the named doc.

| Changed area | Doc to update |
|---|---|
| `iOS/meApp/Domain/Models/DB/*`, Android `data/storage/*`, `proto/*` | [`docs/database-schema.md`](../../../../docs/database-schema.md) |
| `*ProductType*` / `*ProductSelection*` (Phase 2 product model) | [`docs/product-types-current-state.md`](../../../../docs/product-types-current-state.md) |
| Account-switching flow (`*AccountSwitch*`) | [`docs/account-switching-flow.md`](../../../../docs/account-switching-flow.md) |
| Dashboard chart engine (unified v2, `Dashboard/Chart/*`) | [`docs/dashboard-hybrid-latest-vs-average.md`](../../../../docs/dashboard-hybrid-latest-vs-average.md) |
| `.circleci/*` | [`docs/circleci.md`](../../../../docs/circleci.md) |
| `.lefthook.yml`, `scripts/*`, `.claude/*`, `iOS/.claude/*`, `Android/.claude/*` | [`docs/automation.md`](../../../../docs/automation.md) |
| New feature / service / model / DI registration (structural) | `iOS/architecture.md` |

If you add, move, or retire a maintained doc, update **both** this table and the
`doc_for()` map in `scripts/docs-freshness-check.sh` so they stay identical.

## Instructions

### 1 — Identify What Changed

Determine the category of change from $ARGUMENTS or the recent git diff:

```bash
git diff $(git merge-base HEAD origin/main) HEAD --name-only
```

Then run the freshness check over the working tree to list every maintained doc the
change touches (it uses the same source→doc map as the Scope table above):

```bash
scripts/docs-freshness-check.sh
```

Categories:
- New feature module added to `meApp/Features/`
- New service (protocol + implementation + `ServiceRegistry` registration)
- New SwiftData `@Model` in `Domain/Models/DB/`
- New value-type snapshot (e.g. new `*Snapshot` struct in `Domain/Models/Domain/<Area>/`) — update §4.1a Value-type Snapshots table
- New external SPM dependency
- Renamed type, method, or file that affects architecture
- New API endpoint category or integration

---

### 2 — Read the Current Architecture File

Read `architecture.md` in full. Identify which sections need updating based on the change category:

| Change type | Sections to update |
|---|---|
| New feature module | Section 3.2 Feature Modules table; Section 3 bottom tab nav if tab-visible |
| New service | Section 3.3 Service Layer table |
| New SwiftData model | Section 4.1 Key Models table |
| New snapshot type | Section 4.1a Value-type Snapshots table |
| New SPM dependency | Section 5 External Integrations table |
| Renamed type | Any section referencing the old name |
| Architecture pattern change | Section 3.1 and/or Section 2 diagrams |

---

### 3 — Update architecture.md

Apply the minimum necessary changes:
- Add the new row(s) to the relevant table(s), following the existing row format exactly
- Update the `Date of Last Update` field in Section 10 to today's date
- Do not reformat, rewrite, or restructure sections that were not affected by the change

---

### 3a — Update the affected `docs/` file(s)

For every doc the Scope map / freshness check named:
- Read the doc, find the section that describes the changed behaviour or entity.
- Apply the minimum edit that makes it match the code (add the new column/row, correct
  the renamed field, update the changed flow). Match the doc's existing tone and format.
- If a change genuinely affects nothing a doc describes, note that and move on — do not
  invent content to satisfy the reminder.
- Re-run `scripts/docs-freshness-check.sh` and confirm it reports nothing for the docs
  you just touched (edits to `docs/*` and `*.md` are never flagged, so a clean run means
  the source side is covered).

---

### 4 — Check Skill Files for Stale References

If any type, file, or class was **renamed or removed**, search `.claude/skills/` and `.claude/agents/` for stale references:

```bash
rg -l "{old type name}" .claude/skills .claude/agents -g '*.md'
```

For each file that contains a stale reference:
- Read the skill
- Identify the outdated class name, file path, or method name
- Report it as a staleness warning

Do NOT auto-edit skill files without user confirmation — report them and ask whether to update.

---

### 5 — Report

```
architecture.md updated:
- Section(s) changed: {list}
- Rows added: {count and names}
- Date updated: {today}

Staleness check:
- Skill files with stale references: {list or "None found"}
```

Recommend running `/build` to confirm the codebase still compiles after any structural change.
