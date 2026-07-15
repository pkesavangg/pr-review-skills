# Documentation

Current, maintained documentation for the meApp monorepo. Stale working docs (old plans, brainstorms, one-off solutions) have been removed — recover them from git history if needed.

## Index

| Doc | What it covers |
|-----|----------------|
| [database-schema.md](database-schema.md) | Local persistence schema (Room / SwiftData) and entity relationships. |
| [account-switching-flow.md](account-switching-flow.md) | Multi-account management and the account-switching flow. |
| [product-types-current-state.md](product-types-current-state.md) | Current state of product types across the app. |
| [dashboard-hybrid-latest-vs-average.md](dashboard-hybrid-latest-vs-average.md) | Dashboard weight display: latest-vs-average hybrid behavior. |
| [automation.md](automation.md) | All automation in the repo: Claude Code AI tooling, git hooks (Lefthook), CI (CircleCI), and scripts. |
| [circleci.md](circleci.md) | CircleCI pipeline: how it's configured, the checks per platform, why SwiftLint and the iOS build run separately, and required secrets. |
| [confluence.md](confluence.md) | The Me App Confluence hub structure — page tree + IDs, and which repo change syncs to which page. |

## Related

- Monorepo overview & conventions: [`/CLAUDE.md`](../CLAUDE.md)
- iOS architecture: [`/iOS/architecture.md`](../iOS/architecture.md) · iOS context: [`/iOS/CLAUDE.md`](../iOS/CLAUDE.md)
- Android context: [`/Android/CLAUDE.md`](../Android/CLAUDE.md)
