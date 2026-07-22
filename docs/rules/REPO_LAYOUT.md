# Docs repo layout — placement & naming standard

**This is the enforced placement standard for meApp documentation.** Read it before adding or
moving any doc. It defines *where* a doc goes and *how it's named*; the [README](../README.md) is the
index of what currently exists.

meApp has **three docs roots that share this one taxonomy**:

| Root | Scope |
|------|-------|
| [`docs/`](../) | Cross-platform — concerns both iOS and Android (this root). |
| [`../iOS/docs/`](../../iOS/docs/) | iOS-only. |
| [`../Android/docs/`](../../Android/docs/) | Android-only. |

## Decide scope first, then bucket

1. **Scope** — is the doc about **both** apps (→ `docs/`) or **one** app (→ that platform's `docs/`)?
2. **Bucket** — pick the folder below.

| Folder | Holds |
|--------|-------|
| `overview/` | Orientation — ecosystem/app context, architecture, alignment decisions, the automation map. |
| `guides/` | As-built "how a thing works" references (incl. per-feature / per-screen docs) and reports/audits. |
| `rules/` | Enforced conventions — "you must" (repo layout, git standards). |
| `plans/` | Time-bound migration / hardening plans (date-prefixed: `YYYY-MM-DD-…`). |
| `archive/` | Retired docs, kept for history. |
| `assets/` | Images / diagrams referenced by docs. |

## Naming convention

- **Topic docs** in `overview/`, `guides/`, and `rules/` use **`UPPER_SNAKE_CASE`** filenames —
  e.g. `DATABASE_SCHEMA.md`, `ACCOUNT_SWITCHING_FLOW.md`, `CLAUDE_AUTOMATION.md`, `REPO_LAYOUT.md`.
- **Dated plans** in `plans/` keep the **`YYYY-MM-DD-<TOPIC>.md`** date-prefix convention
  (e.g. `2026-07-14-MOB-1008-android-claude-orchestration-agents-skills.md`) so they sort chronologically.
- `README.md` keeps its conventional name (it's the folder index).
- `assets/` files keep the name of whatever they depict.

## Maintained docs are wired into tooling

Several docs in `overview/` and `guides/` are **maintained** — a source change that outdates one must
update it in the same task. Two places encode the source→doc map and **must stay identical**:

- [`scripts/docs-freshness-check.sh`](../../scripts/docs-freshness-check.sh) — the `doc_for()` map; a
  root PostToolUse hook runs it on every edit and prints `📝 Docs check …`.
- [`iOS/.claude/skills/update-architecture/SKILL.md`](../../iOS/.claude/skills/update-architecture/SKILL.md)
  — the same table, plus the `architecture.md` + `docs/` update procedure.

**Moving, adding, or renaming a maintained doc?** Update the map in **both** files above so they stay
identical, fix any inbound links, and — if the change also affects a Confluence page — see
[`overview/CONFLUENCE.md`](../overview/CONFLUENCE.md) and run `/update-confluence`.
