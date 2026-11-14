# Maintained docs — placement, structure and content

Rules for the **content and shape** of a repo's maintained docs. The *"code changed but the doc wasn't updated"* check stays inline in `review-pr.md` § 4a.3 / `review.md` § 4.3; this file owns what a doc in the diff must look like.

## Read the repo's doc standard first

These rules fire only when the repo declares one. Look for, in order:

- `docs/common/CONTRIBUTING.md` — *Feature doc structure*, *Why it works this way*, *Documentation upkeep* (meApp).
- `docs/rules/REPO_LAYOUT.md` — where a doc goes and how it is named.
- The repo `CLAUDE.md` *Read the docs first* / *Keeping docs current* sections.
- `scripts/check-docs.py` — if present, its checks are authoritative. `/review` may run it; `/review-pr` reads it and never runs it in parallel mode.

No standard → skip this file. When the standard and a rule here disagree, the standard wins.

Fires on added or changed `*.md` under the repo's docs roots (meApp: `docs/`, `iOS/docs/`, `Android/docs/`), **and** on a code change whose mapped feature doc is in the diff. Never on `docs/plans/**` or `docs/archive/**`.

---

## P1 — Behaviour or logic change with no *Why it works this way* row

When a PR changes how a feature behaves, the next reader needs the reason. meApp's standard requires a row in the feature doc's *Why it works this way* table, written as the reason, not the story.

**Sniff.** The diff changes logic in a feature (a store, view model, service, reducer or validation — not a pure refactor, copy or style change) **and** the mapped `iOS/docs/features/<FEATURE>.md` either isn't in the diff or is in the diff with no added/changed row under `## Why it works this way`.

Raise as P1 only when the repo makes it mandatory (meApp `CLAUDE.md` step 6: *"including a 'Why it works this way' row when logic or behaviour changed"*); otherwise P2.

**Fix.** Add or rewrite one row:

```markdown
| Behaviour | Why | Since |
|-----------|-----|-------|
| Maintain goal starts from the latest weigh-in | The tab has no starting-weight field, so the latest reading is the only honest value | 5.2.0 |
```

Rewrite the existing row when the same behaviour changes again; don't stack history. `doc-maintainer` (or `/update-architecture`) can write it.

---

## P2 — Feature doc breaks the section order

Every feature doc uses the same sections, in this order; a section with nothing to say is dropped, never renamed or reordered:

1 Summary · 2 Entry points · 3 Data model · 4 States · 5 Behaviour · 6 Alerts, toasts and messages · 7 Interactions with other features · 8 Why it works this way · 9 Known gaps · 10 How it works in code (always last)

**Sniff.** In a changed `iOS/docs/features/*.md`, the `## ` headings: an unknown heading, two known headings out of order, or `How it works in code` not last.

**Fix.** Move the content under the matching section heading.

---

## P2 — Ticket keys, history or plans inside a maintained doc

Maintained docs describe current behaviour on `develop`, in present tense. Ticket keys, "before this change…", "will be added in …" and names rot.

**Sniff.** Added lines in a maintained doc matching `[A-Z]{2,6}-\d+`, `\b(previously|used to|before (this|the) change|will be|planned|TODO)\b`, or a person's name. (`Since` version cells in the *Why* table are allowed.)

**Fix.** State the current behaviour only. History lives in git and the PR.

---

## P2 — Doc in the wrong root or bucket, or misnamed

**Scope first:** both platforms → `docs/`; iOS only → `iOS/docs/`; Android only → `Android/docs/`. **Then bucket:** `overview/`, `guides/`, `rules/`, `plans/`, `archive/`, `assets/` (and `features/` / `system/` under `iOS/docs/`). Topic docs are `UPPER_SNAKE_CASE.md`; plans are `YYYY-MM-DD-<topic>.md`; no ticket keys in names.

**Sniff.** An added `*.md`: cross-platform content (mentions both `Swift`/`SwiftUI` and `Kotlin`/`Compose`, or "both apps") under a platform root; a topic doc not matching `^[A-Z0-9_]+\.md$`; a plan without the date prefix; `[A-Z]{2,6}-\d+` in the filename.

**Fix.** Name the right path, e.g. `docs/guides/GOAL_BEHAVIOUR.md`.

---

## P2 — Maintained doc added, moved or renamed without updating the source→doc map

meApp keeps the map in two places that must stay identical: `scripts/docs-freshness-check.sh` (`doc_for()`) and `iOS/.claude/skills/update-architecture/SKILL.md`.

**Sniff.** A maintained doc is added, renamed or deleted and either map file is missing from the diff (or only one of them changed).

**Fix.** Update both maps in this PR and fix inbound links. If a Confluence page mirrors it, say so in the PR so `/update-confluence` runs after merge.
