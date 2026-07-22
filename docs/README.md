# meApp docs

This is the **cross-platform docs root**. Per-platform docs live under [`../iOS/docs/`](../iOS/docs/)
and [`../Android/docs/`](../Android/docs/). All three docs roots share one taxonomy.

**Adding or moving a doc?** Read [`rules/REPO_LAYOUT.md`](rules/REPO_LAYOUT.md) first — it's the full
placement **and** naming standard. The short version:

| Folder | Holds |
|--------|-------|
| [`overview/`](overview/) | Orientation — ecosystem/app context, architecture, alignment decisions, the automation map. |
| [`guides/`](guides/) | As-built "how a thing works" references (incl. per-feature / per-screen docs) and reports/audits. |
| [`rules/`](rules/) | Enforced conventions — "you must" (repo layout, git standards). |
| [`plans/`](plans/) | Time-bound migration / hardening plans (date-prefixed). |
| [`archive/`](archive/) | Retired docs, kept for history. |
| [`assets/`](assets/) | Images / diagrams referenced by docs. |

Decide scope first, then bucket: is the doc about **both** apps (→ this root `docs/`) or **one** app
(→ that platform's `docs/`)? Then pick the folder above. **Naming:** topic docs in `overview/`, `guides/`,
`rules/` use `UPPER_SNAKE_CASE`; dated plans keep the `YYYY-MM-DD-…` prefix.

## Index

### overview/
| Doc | What it covers |
|-----|----------------|
| [overview/CLAUDE_AUTOMATION.md](overview/CLAUDE_AUTOMATION.md) | All automation in the repo: Claude Code AI tooling, git hooks (Lefthook), CI (CircleCI), and scripts — the automation map. |
| [overview/CONFLUENCE.md](overview/CONFLUENCE.md) | The Me App Confluence hub structure — page tree + IDs, and which repo change syncs to which page. |

### guides/
| Doc | What it covers |
|-----|----------------|
| [guides/DATABASE_SCHEMA.md](guides/DATABASE_SCHEMA.md) | Local persistence schema (Room / SwiftData) and entity relationships. |
| [guides/ACCOUNT_SWITCHING_FLOW.md](guides/ACCOUNT_SWITCHING_FLOW.md) | Multi-account management and the account-switching flow. |
| [guides/PRODUCT_TYPES_CURRENT_STATE.md](guides/PRODUCT_TYPES_CURRENT_STATE.md) | Current state of product types across the app. |
| [guides/CIRCLECI.md](guides/CIRCLECI.md) | CircleCI pipeline: config, per-platform checks, why SwiftLint and the iOS build run separately, and required secrets. |
| [guides/SDLC_AUDIT_2026-07-22.md](guides/SDLC_AUDIT_2026-07-22.md) | SDLC compliance audit of `develop` (2026-07-22): status ✅ PASS with 1 Medium + 2 Low findings. |

### rules/
| Doc | What it covers |
|-----|----------------|
| [rules/REPO_LAYOUT.md](rules/REPO_LAYOUT.md) | Docs placement + naming standard — scope, folder taxonomy, UPPER_SNAKE naming, and the maintained-doc tooling map. |

### plans/
| Doc | What it covers |
|-----|----------------|
| [plans/2026-07-14-MOB-1008-android-claude-orchestration-agents-skills.md](plans/2026-07-14-MOB-1008-android-claude-orchestration-agents-skills.md) | Android Claude orchestration: agents + skills plan. |
| [plans/2026-06-25-MOB-1007-ios-claude-skills-audit-refresh.md](plans/2026-06-25-MOB-1007-ios-claude-skills-audit-refresh.md) | iOS Claude skills audit + refresh plan. |

## Related

- Monorepo overview & conventions: [`/CLAUDE.md`](../CLAUDE.md)
- iOS architecture: [`/iOS/architecture.md`](../iOS/architecture.md) · iOS context: [`/iOS/CLAUDE.md`](../iOS/CLAUDE.md)
- Android context: [`/Android/CLAUDE.md`](../Android/CLAUDE.md)

## Keeping docs current

The `overview/` and `guides/` docs are **maintained** — a code change that outdates one should update it
in the same task. A PostToolUse hook runs [`../scripts/docs-freshness-check.sh`](../scripts/docs-freshness-check.sh)
on every edit and prints `📝 Docs check …` naming the affected doc; run
[`/update-architecture`](../iOS/.claude/skills/update-architecture/SKILL.md) to do the update, and
[`/update-confluence`](../.claude/skills/update-confluence/SKILL.md) to mirror it upward.
