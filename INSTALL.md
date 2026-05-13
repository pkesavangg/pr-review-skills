# Install — pr-review-skills

One-time setup per teammate. **Takes ~30 seconds.** No plugin installs required — all rules ship in this repo.

## Prerequisites

- [Claude Code](https://claude.com/claude-code) installed and authenticated.
- [GitHub CLI](https://cli.github.com/) authenticated: `gh auth status` should report logged in. If not: `gh auth login`.
- Write access to the GitHub repos you'll be reviewing (needed to post inline comments). Forks may 403 on inline comments — the skill falls back to a top-level summary in that case.

## Step 1 — Clone this repo

```bash
git clone https://github.com/pkesavangg/pr-review-skills.git ~/pr-review-skills
```

The path `~/pr-review-skills` is a **convention** — the orchestrator looks there for all rule files (`references/security/`, `references/privacy/`, `references/ios/`, `references/compose/`, and the vendored SwiftUI + Compose skills under `references/vendored/`). If you clone somewhere else, edit `~/.claude/commands/review-pr.md` and replace every `$HOME/pr-review-skills` with your chosen path.

## Step 2 — Symlink the slash command into user scope

```bash
mkdir -p ~/.claude/commands
ln -s ~/pr-review-skills/.claude/commands/review-pr.md ~/.claude/commands/review-pr.md
```

This makes `/review-pr` available from **any** project on your machine — you don't need to be inside the `pr-review-skills` directory to use it.

Restart Claude Code (or open a new session). Type `/` — you should see `/review-pr`.

## Step 3 — Smoke test

Pick any PR on a repo you have write access to:

```
/review-pr https://github.com/your-org/some-repo/pull/123
```

Expected:

1. The skill announces detected platform (`iOS only` / `Android only` / `iOS + Android`).
2. Announces mode (`first-review` or `re-review`).
3. Posts inline comments tagged `P0 — `, `P1 — `, `P2 — `, or `Nit — `.
4. Posts a top-level summary review (`COMMENT` verdict during the current rollout phase).

If nothing posts, run `gh auth status` and confirm the auth scopes include `repo`.

## Why no plugin installs?

Earlier versions of this repo asked teammates to also install [twostraws/SwiftUI-Agent-Skill](https://github.com/twostraws/SwiftUI-Agent-Skill) (`swiftui-pro`) and [aldefy/compose-skill](https://github.com/aldefy/compose-skill) (`compose-expert`) as separate Claude Code plugins. Both are MIT licensed, so the current setup vendors verbatim copies into [`references/vendored/`](references/vendored/) — see [`references/vendored/UPSTREAM.md`](references/vendored/UPSTREAM.md) for attribution, version pins, and the quarterly sync routine. This gives every teammate the full rule set from a single `git clone`, predictable behaviour, and offline-friendly setup.

## Updates

```bash
cd ~/pr-review-skills && git pull
```

That's it. The vendored SwiftUI + Compose rules update along with our own project-tuned rules. (If you previously installed the upstream `swiftui-pro` / `compose-expert` plugins, they're harmless to keep but no longer used by this orchestrator — feel free to uninstall via `/plugin uninstall`.)

## Uninstall

```bash
rm ~/.claude/commands/review-pr.md
rm -rf ~/pr-review-skills
```

## Troubleshooting

- **`/review-pr` doesn't appear in the picker.** Confirm the symlink: `ls -la ~/.claude/commands/review-pr.md` should show `→ /Users/<you>/pr-review-skills/.claude/commands/review-pr.md`. Restart Claude Code after creating the symlink.
- **Reference files not found** (e.g. `references/security/secrets-and-storage.md` or `references/vendored/swiftui-pro/SKILL.md`). You cloned to a non-default path. Edit the orchestrator (`~/.claude/commands/review-pr.md`) and replace every `$HOME/pr-review-skills` with your actual clone path.
- **Inline comments return 403.** PR is from a fork or you lack write access. The skill auto-falls-back to a top-level summary with `path:line` references — that's expected.
- **First run after `git clone` doesn't pick up `/review-pr`.** Restart Claude Code. The slash-command picker is populated at session start; new symlinks need a fresh session.
