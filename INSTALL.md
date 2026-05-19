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

You can clone anywhere — the orchestrator resolves its reference directory at runtime by following the symlink created in Step 2. `~/pr-review-skills` is just a convention. If you clone to `~/code/pr-review-skills`, the symlink target points there and `$REFS_DIR` is derived as `~/code/pr-review-skills/references` automatically. No file edits required.

## Step 2 — Symlink the slash commands and skill into user scope

```bash
mkdir -p ~/.claude/commands ~/.claude/skills
ln -s ~/pr-review-skills/.claude/commands/review-pr.md      ~/.claude/commands/review-pr.md
ln -s ~/pr-review-skills/.claude/commands/review.md         ~/.claude/commands/review.md
ln -s ~/pr-review-skills/.claude/skills/pr-description      ~/.claude/skills/pr-description
```

This makes `/review-pr`, `/review`, and the `pr-description` skill available from **any** project on your machine — you don't need to be inside the `pr-review-skills` directory to use them.

Restart Claude Code (or open a new session). Type `/` — you should see both `/review-pr` and `/review`. The `pr-description` skill auto-triggers from natural language (no slash needed).

### The two commands + one skill

| Trigger          | Type     | Who runs it | When                       | Input                                              | Output                                                                       |
| ---------------- | -------- | ----------- | -------------------------- | -------------------------------------------------- | ---------------------------------------------------------------------------- |
| `/review`        | Command  | Author      | Before commit / before PR  | Local working tree (`git diff`)                    | `.claude-review/report.md` + offered in-place fixes (you stage them yourself) |
| `/review-pr`     | Command  | Reviewer    | After PR is opened         | GitHub PR diff + comments                          | Inline GitHub review comments + summary                                       |
| `pr-description` | Skill    | Author      | Before / after opening PR  | Current branch, branch override, or GitHub PR URL  | PR title + Markdown body (with Jira link) ready to paste or pass to `gh pr create` |

The two review commands use the same rule references — they only differ in input source and output sink. The `pr-description` skill auto-triggers when you say things like "raise a PR against KITC-541", "write a PR description", or paste a `github.com/.../pull/N` URL.

## Step 3 — Smoke test `/review-pr`

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

## Step 4 — Smoke test `/review`

Inside any iOS / Android repo you're actively editing, make a small change (or stage one) and run:

```
/review
```

Expected:

1. Announces scope (e.g. `Scope: staged+unstaged · 3 file(s) · 47 lines of diff`).
2. Announces detected platform and pass mode.
3. Writes `.claude-review/report.md` at the repo root with P0/P1/P2/Nit sections.
4. Asks "Apply fixes from .claude-review/report.md?" via a 5-option picker.

Add `.claude-review/` to your repo's `.gitignore` so the report never gets committed:

```bash
echo '.claude-review/' >> .gitignore
```

### Optional — pre-commit hook

If you want `/review --staged` to run automatically before every commit, drop this into your repo:

```bash
cat > .git/hooks/pre-commit <<'SH'
#!/usr/bin/env bash
set -e
# Skip when no TTY (CI, rebase, merge auto-commit) — never block automated commits
[ -t 1 ] || exit 0
# Set SKIP_CLAUDE_REVIEW=1 in your env to opt out for a single commit
[ "${SKIP_CLAUDE_REVIEW:-}" = "1" ] && exit 0
echo "Running /review --staged on staged files…"
claude --print "/review --staged --no-prompt" || true
# /review writes the report but never blocks the commit on its own.
exit 0
SH
chmod +x .git/hooks/pre-commit
```

Two design choices worth knowing:

- **The hook never blocks the commit.** It always `exit 0`s. The hook surfaces information; you decide whether to address it before pushing. A blocking hook that runs an LLM is hostile.
- **`--no-prompt` mode** writes the report and exits without the interactive fix question — pre-commit isn't an interactive context. After the hook prints the counts, look at `.claude-review/report.md`, and run `/review` (without `--no-prompt`) interactively if you want the fix loop.

## Why no plugin installs?

Earlier versions of this repo asked teammates to also install [twostraws/SwiftUI-Agent-Skill](https://github.com/twostraws/SwiftUI-Agent-Skill) (`swiftui-pro`) and [aldefy/compose-skill](https://github.com/aldefy/compose-skill) (`compose-expert`) as separate Claude Code plugins. Both are MIT licensed, so the current setup vendors verbatim copies into [`references/vendored/`](references/vendored/) — see [`references/vendored/UPSTREAM.md`](references/vendored/UPSTREAM.md) for attribution, version pins, and the quarterly sync routine. This gives every teammate the full rule set from a single `git clone`, predictable behaviour, and offline-friendly setup.

## Updates

```bash
cd ~/pr-review-skills && git pull
```

That's it. The vendored SwiftUI + Compose rules update along with our own project-tuned rules. (If you previously installed the upstream `swiftui-pro` / `compose-expert` plugins, they're harmless to keep but no longer used by this orchestrator — feel free to uninstall via `/plugin uninstall`.)

## Step 5 — Smoke test the `pr-description` skill

On any feature branch with at least one commit, ask Claude (natural language — no slash):

```
write a PR description for this branch
```

or

```
raise a PR against KITC-541
```

Expected:

1. The skill resolves the Jira ID (from your message, branch name, or commits) and the base branch.
2. It reads the diff and recent commits.
3. It prints a **Title** block and a **Body** block (Markdown) ready to paste into GitHub or pipe to `gh pr create`.
4. No AI attribution footer is added.

If the skill doesn't fire from natural language, confirm the symlink: `ls -la ~/.claude/skills/pr-description` should point at `~/pr-review-skills/.claude/skills/pr-description`. Restart Claude Code after creating the symlink.

## Uninstall

```bash
rm ~/.claude/commands/review-pr.md
rm ~/.claude/commands/review.md
rm ~/.claude/skills/pr-description
rm -rf ~/pr-review-skills
# If you installed the pre-commit hook in a repo, also:  rm .git/hooks/pre-commit
```

## Troubleshooting

- **`/review-pr` or `/review` doesn't appear in the picker.** Confirm the symlinks: `ls -la ~/.claude/commands/review-pr.md ~/.claude/commands/review.md` should each show `→ /Users/<you>/pr-review-skills/.claude/commands/<file>.md`. Restart Claude Code after creating the symlinks.
- **`pr-description` skill doesn't trigger from natural language.** Confirm `ls -la ~/.claude/skills/pr-description` shows a symlink into your clone. Restart Claude Code. As a fallback, you can always say "use the pr-description skill" explicitly.
- **Reference files not found** (e.g. `references/security/secrets-and-storage.md` or `references/vendored/swiftui-pro/SKILL.md`). One of the symlinks is broken or points at a stale location. Run `readlink ~/.claude/commands/review-pr.md` (and same for `review.md`) to confirm they resolve to your actual clone, then re-create the symlinks from Step 2 if needed.
- **Inline comments return 403.** PR is from a fork or you lack write access. The skill auto-falls-back to a top-level summary with `path:line` references — that's expected.
- **First run after `git clone` doesn't pick up the commands.** Restart Claude Code. The slash-command picker is populated at session start; new symlinks need a fresh session.
- **`/review` ran but `.claude-review/report.md` shows up in `git status`.** Add `.claude-review/` to your repo's `.gitignore`. The report is meant to be local-only.
- **`/review` collides with another `/review` command.** Some Claude Code installs ship a built-in `/review` (PR review). The user-scope symlink takes precedence; if it doesn't, you can rename ours to e.g. `~/.claude/commands/review-local.md` instead.
