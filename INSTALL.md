# Install — pr-review-skills

One-time setup per teammate. Takes ~2 minutes.

## Prerequisites

- [Claude Code](https://claude.com/claude-code) installed and authenticated.
- [GitHub CLI](https://cli.github.com/) authenticated: `gh auth status` should report logged in. If not: `gh auth login`.
- Write access to the GitHub repos you'll be reviewing (needed to post inline comments). Forks may 403 on inline comments — the skill falls back to a top-level summary in that case.

## Step 1 — Install Paul Hudson's `swiftui-pro` (SwiftUI rules)

In Claude Code:

```
/plugin marketplace add twostraws/SwiftUI-Agent-Skill
/plugin install swiftui-pro@swiftui-agent-skill
```

When prompted, choose **`Install for you (user scope)`**. Then:

```
/reload-plugins
```

Verify by typing `/` — you should see the swiftui-pro skill available.

This plugin is maintained by Paul Hudson and updates via `/plugin update`. We don't vendor SwiftUI rules into this repo — the upstream stays authoritative.

## Step 2 — Install `compose-expert` (Compose rules)

In Claude Code:

```
/plugin marketplace add aldefy/compose-skill
/plugin install compose-expert
```

Choose **`Install for you (user scope)`** at the picker. Then:

```
/reload-plugins
```

This plugin is maintained by [aldefy](https://github.com/aldefy/compose-skill) and ships rule references plus androidx/androidx source-code receipts. Updates via `/plugin update`. Same model as swiftui-pro: we don't vendor Compose API rules — the upstream stays authoritative. The project-tuned Compose rules in [references/compose/](references/compose/) sit on top.

## Step 3 — Clone this repo

```bash
git clone <internal-host>/pr-review-skills.git ~/pr-review-skills
```

The path `~/pr-review-skills` is a **convention** — the orchestrator looks there for the reference files under [references/security/](references/security/), [references/privacy/](references/privacy/), [references/ios/](references/ios/), and [references/compose/](references/compose/). If you clone somewhere else, edit `~/.claude/commands/review-pr.md` and replace every `$HOME/pr-review-skills` with your chosen path.

## Step 4 — Symlink the slash command into user scope

```bash
mkdir -p ~/.claude/commands
ln -s ~/pr-review-skills/.claude/commands/review-pr.md ~/.claude/commands/review-pr.md
```

This makes `/review-pr` available from **any** project on your machine — you don't need to be inside the `pr-review-skills` directory to use it.

Restart Claude Code (or open a new session). Type `/` — you should see `/review-pr`.

## Step 5 — Smoke test

Pick any PR on a repo you have write access to:

```
/review-pr https://github.com/your-org/some-repo/pull/123
```

Expected:

1. The skill announces detected platform (`iOS only` / `Android only` / `iOS + Android`).
2. Announces mode (`first-review` or `re-review`).
3. Posts inline comments tagged `P0 — `, `P1 — `, `P2 — `, or `Nit — `.
4. Posts a top-level summary with verdict `COMMENT` or `REQUEST_CHANGES`.

If nothing posts, run `gh auth status` and confirm the auth scopes include `repo`.

## Updates

```bash
cd ~/pr-review-skills && git pull                    # orchestrator + security / privacy / ios / compose project-tuned refs
/plugin update swiftui-pro@swiftui-agent-skill       # SwiftUI rules (Paul Hudson)
/plugin update compose-expert@aldefy-compose-skill   # Compose rules (aldefy)
```

## Uninstall

```bash
rm ~/.claude/commands/review-pr.md
rm -rf ~/pr-review-skills
# In Claude Code:
/plugin uninstall swiftui-pro@swiftui-agent-skill
/plugin uninstall compose-expert@aldefy-compose-skill
```

## Troubleshooting

- **`/review-pr` doesn't appear in the picker.** Confirm the symlink: `ls -la ~/.claude/commands/review-pr.md` should show `→ /Users/<you>/pr-review-skills/.claude/commands/review-pr.md`. Restart Claude Code after creating the symlink.
- **"swiftui-pro skill not found"** when reviewing a SwiftUI PR. Step 1 wasn't completed — run the two plugin commands and `/reload-plugins`.
- **"compose-expert skill not found"** when reviewing a Compose PR. Step 2 wasn't completed — run the two plugin commands and `/reload-plugins`.
- **First `/review-pr` after install doesn't actually delegate to swiftui-pro or compose-expert.** This is expected. The available-skills list inside a Claude Code conversation is snapshotted at conversation start. Newly-installed plugins are picked up by `/reload-plugins` at the harness level, but the *current* conversation's skill registry won't see them until a new conversation begins. **Start a fresh Claude Code conversation after running Steps 1-2** before your first `/review-pr`. The reviewer falls back gracefully to the project-tuned reference files if delegation is unavailable, so the run won't fail — but you'll get noticeably stronger SwiftUI / Compose findings on the second conversation onward.
- **Reference files not found** (e.g. `references/security/secrets-and-storage.md` or `references/compose/recomposition.md`). You cloned to a non-default path. Edit the orchestrator (`~/.claude/commands/review-pr.md`) and replace every `$HOME/pr-review-skills` with your actual clone path.
- **Inline comments return 403.** PR is from a fork or you lack write access. The skill auto-falls-back to a top-level summary with `path:line` references — that's expected.
- **Skill installed but `Skill` tool says unknown.** Newly installed plugins are picked up at session start. If you installed mid-session, run `/reload-plugins`; if that doesn't help, restart Claude Code so the skill registry refreshes.
