# Automation

Every automated check, hook, and AI-tooling mechanism in the meApp monorepo — what runs, when, and where it's configured. Layered so a problem caught locally never reaches CI.

```
Edit a file        →  Claude Code hooks (SwiftLint, reminders)        iOS/.claude/settings.json
Commit             →  Lefthook pre-commit + commit-msg                .lefthook.yml
Push / PR          →  CircleCI (build, lint, test, security)          .circleci/config.yml
Author/run a task  →  Claude Code skills · agents · commands          .claude/ · iOS/.claude/ · Android/.claude/
```

---

## 1. Claude Code AI tooling

Claude Code is the primary dev tool. The shared AI-context is committed (see `.gitignore` — `.claude/*` is ignored but skills/agents/commands/orchestra/settings.json are re-included; `settings.local.json` stays local).

| Mechanism | Location | Purpose |
|-----------|----------|---------|
| **Skills** (auto-trigger by `description`) | `.claude/skills/` (generic), `iOS/.claude/skills/` (iOS), `Android/.claude/skills/` (Android) | Reusable task procedures. Catalog + taxonomy: [`.claude/skills/README.md`](../.claude/skills/README.md). |
| **Agents** | `iOS/.claude/agents/` | Focused subagents: `api-change-planner`, `coverage-gap-finder`, `di-impact-finder`, `gen-mock-batch`, `ios-perf-analyzer`. |
| **Commands** | `iOS/.claude/commands/` | Orchestrators: `work-ticket` (full SDLC), `release-cut`, `review-pr`. |
| **Orchestration** | `iOS/.claude/orchestra.md` | Workflow sequences, review pipeline, verification checklist, parallelization rules. |
| **MCP servers** | `iOS/.claude/MCP_SERVERS.md` | Atlassian (Jira/Confluence), GitHub, Figma, Context7 setup. |
| **skill-creator** | enabled in `.claude/settings.json` + `iOS/.claude/settings.json` | Scaffold/validate new skills in `SKILL.md` format. |

**Format & placement rules:** every skill is a `<name>/SKILL.md` directory (flat `.md` files are not discovered). Generic skills live at the repo root so they trigger anywhere; platform skills are scoped to their subtree (`iOS/.claude/skills/`, `Android/.claude/skills/`). A scoped skill only auto-triggers when Claude is rooted in that subtree, so all **iOS skills are mirrored into the repo-root `.claude/skills/` as relative symlinks** — this is what lets them trigger from the monorepo root. **When you add or remove an iOS skill, regenerate the mirror** with [`scripts/sync-root-skill-links.sh`](../scripts/sync-root-skill-links.sh) (`--check` verifies it in CI). Full rules in the [skills README](../.claude/skills/README.md).

**Phase 2:** `phase2-context` and `phase2-design-system` skills carry the Me.Health 2.0 multi-product API + design context (see [`/CLAUDE.md`](../CLAUDE.md) → Phase 2).

### Claude Code editor hooks (`iOS/.claude/settings.json`)

Run automatically as Claude edits files:

| Trigger | Action |
|---------|--------|
| Edit/Write `*.swift` | Run SwiftLint on the file; print the top violations |
| Edit `Info.plist` / `GoogleService-Info.plist` / `.env` / `Secrets` / `keys.plist` | Sensitive-config warning |
| Edit a `*Tests/*.swift` file | Reminder: unit tests run on a **physical device**, not the simulator |
| Every 5 / 10 / 20+ Swift edits in a session | Suggest running `/post-change-guard` |
| `git commit` (Bash) | **Blocks the commit** via [`scripts/commit-review-gate.sh`](../scripts/commit-review-gate.sh) unless `/self-review` has passed on the current working tree, and reports the **specific reason**: not-yet-reviewed · reviewed-but-`NEEDS FIXES` (echoes the outstanding findings) · stale-because-files-changed (lists which). Bypass: `git commit --no-verify`. (Only fires for commits made *through Claude Code*; terminal commits are gated by Lefthook below.) |

### Docs-freshness hook (root `.claude/settings.json`)

Monorepo-wide, so it also catches Android edits (the iOS hooks above only fire under `iOS/`):

| Trigger | Action |
|---------|--------|
| Edit/Write any source file | Run [`scripts/docs-freshness-check.sh`](../scripts/docs-freshness-check.sh); if the path maps to a maintained doc, print `📝 Docs check …` (naming the doc + change significance) and `🌐 Also mirror this to Confluence …` (naming the hub page) |

The source→doc map lives in the script's `doc_for()` and, identically, in the [`update-architecture`](../iOS/.claude/skills/update-architecture/SKILL.md) skill's Scope table — the skill does the actual update (`architecture.md` + `docs/`). Each hit is classified by change size: **`NEW FILE`** (structural), **`major change (N lines)`** when ≥ `MAJOR_LINES` (default 20) added+deleted lines, else **`minor change (N lines)`** — NEW/major say "update the doc", minor says "only if behaviour/schema changed". `architecture.md` is flagged only for **newly-added** structural files; existing-file edits map to their specific doc (schema, product types, etc.) or nothing. Deduped per (doc, tier) per day, so a minor note never hides a later major one. It's a reminder, never a gate.

**Confluence mirror.** Every mapped change also prints a `🌐` line pointing at the [Me App Confluence hub](CONFLUENCE.md) (the `meApp - Development` page mirrors this repo's architecture + automation docs). Run [`/update-confluence`](../.claude/skills/update-confluence/SKILL.md) to publish upward — it reads the target page, drafts the section edit from repo state, and **writes only after you approve** (Confluence is a shared, hand-curated wiki, so writes are never automatic). The `🌐` line is shown at most once per day. Full page tree + IDs + the change→page map: [`docs/overview/CONFLUENCE.md`](CONFLUENCE.md).

---

## 2. Git hooks — Lefthook (`.lefthook.yml`)

Local pre-commit / commit-msg gates. Setup: `brew install lefthook detekt swiftlint && lefthook install`. Bypass in emergencies with `git commit --no-verify`.

| Stage | Check | What it does |
|-------|-------|--------------|
| `pre-commit` | **detekt** | Static analysis on staged `*.kt` (uses `Android/config/detekt/detekt.yml` + baseline). Update baseline: `cd Android && ./gradlew detektBaseline`. |
| `pre-commit` | **swiftlint** | SwiftLint on staged iOS `*.swift` (`iOS/.swiftlint.yml` — includes the custom snapshot-boundary + accessibility rules). Fails on error-severity violations. Auto-fix the correctable ones with `/swiftlint`. |
| `pre-commit` | **gitleaks** | `gitleaks protect --staged` secrets scan (warns if gitleaks not installed). |
| `commit-msg` | **jira-ticket** | Requires a Jira key (`[A-Z]+-[0-9]+`, e.g. `MOB-1234`) in the message. Skips Merge/Revert/fixup!/squash!. |

> Commit convention: `MOB-XXXX Short description` (legacy `MA-XXXX` accepted). No `Co-Authored-By` / attribution trailer.

---

## 3. CI — CircleCI (`.circleci/config.yml`)

Workflow **`build-and-test`** runs on push/PR (macOS for iOS jobs, `cimg/android` for Android):

| Job | Stack | Gate |
|-----|-------|------|
| `gitleaks` | — | Secrets scan (`gitleaks detect`) |
| `swiftlint` | iOS | SwiftLint static analysis (skips when no `iOS/` changes) |
| `build` | iOS | `xcodebuild` build (Xcode 16.2) |
| `dependency-audit` | iOS | iOS dependency audit |
| `android-build` | Android | `./gradlew assembleDebug` |
| `android-lint` | Android | `./gradlew lint` (+ artifacts) |
| `android-test` | Android | Unit tests + JaCoCo coverage (**80% line minimum**); results + report artifacts |
| `android-owasp-scan` | Android | OWASP dependency-check (+ artifact) |

iOS coverage thresholds (per-layer 75–85%) are documented in [`/iOS/docs/COVERAGE_REPORTING.md`](../iOS/docs/COVERAGE_REPORTING.md).

---

## 4. Scripts

| Script | Purpose |
|--------|---------|
| [`scripts/check-skill-links.sh`](../scripts/check-skill-links.sh) | Flag broken `.md` references across skills, CLAUDE.md, and orchestra.md. Exit 1 on a broken link — safe in CI / pre-commit. |
| [`scripts/sync-root-skill-links.sh`](../scripts/sync-root-skill-links.sh) | Mirror iOS skills into the repo-root `.claude/skills/` as relative symlinks so they trigger from the monorepo root. Run after adding/removing an iOS skill; `--check` exits 1 if out of sync — safe in CI / pre-commit. |
| [`scripts/review-fingerprint.sh`](../scripts/review-fingerprint.sh) | Stable working-tree fingerprint (staging-independent) used by the `/self-review` pass-marker and the Claude `git commit` gate to prove self-review ran on exactly the code being committed. |
| [`scripts/commit-review-gate.sh`](../scripts/commit-review-gate.sh) | Layer-2 commit gate: blocks a Claude-Code `git commit` unless `/self-review` passed on the current tree, printing the specific block reason + outstanding findings. Honors `--no-verify`. Called by the `git commit` PreToolUse hook. |
| [`scripts/docs-freshness-check.sh`](../scripts/docs-freshness-check.sh) | Map a changed source path (or the whole working tree) to the maintained doc it affects and print a staleness reminder. Runs per-edit via the root docs-freshness hook; also runnable manually or in CI. Always exits 0. |
| `iOS/scripts/find-device.sh` | Resolve a connected physical device ID for test runs. |
| `iOS/scripts/run_tests_with_coverage.sh` | Run unit tests on device + produce coverage. |
| `iOS/scripts/export_coverage_reports.py` | Export/parse coverage reports. |

> `iOS/CLAUDE.md` references `scripts/check-snapshot-boundary.sh` (snapshot-boundary enforcement) — confirm it's present locally; it is not currently tracked in the repo.

---

## Related

- Skill catalog & taxonomy: [`.claude/skills/README.md`](../.claude/skills/README.md)
- Confluence hub structure & sync: [`CONFLUENCE.md`](CONFLUENCE.md)
- iOS orchestration: [`/iOS/.claude/orchestra.md`](../iOS/.claude/orchestra.md)
- Monorepo conventions: [`/CLAUDE.md`](../CLAUDE.md) · iOS: [`/iOS/CLAUDE.md`](../iOS/CLAUDE.md) · Android: [`/Android/CLAUDE.md`](../Android/CLAUDE.md)
