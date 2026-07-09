# Vendored Skills — attribution, version pins, sync instructions

This directory contains **redistributed copies** of two third-party AI agent skills used by the `/review-pr` orchestrator. Both are MIT licensed, copied verbatim with their licenses preserved.

By vendoring them here, the `/review-pr` reviewer needs **no additional plugin install** for teammates — a single `git clone` of this repo provides the entire system. The trade-off is that updates to the upstream skills do not flow automatically; the maintainer of `pr-review-skills` is responsible for periodic syncs (see "Sync instructions" below).

---

## swiftui-pro

- **Upstream:** https://github.com/twostraws/SwiftUI-Agent-Skill
- **Author:** Paul Hudson ([@twostraws](https://github.com/twostraws))
- **Pinned version:** `1.0.0`
- **Pinned at:** 2026-05-13
- **License:** MIT (declared in [`swiftui-pro/SKILL.md`](swiftui-pro/SKILL.md) frontmatter and the upstream `plugin.json`)
- **Files vendored:** `SKILL.md` plus the 9 reference files under `references/` (api / views / data / navigation / design / accessibility / performance / swift / hygiene).
- **What the orchestrator does with it:** [`review-pr.md` § 4a.1](../../.claude/commands/review-pr.md) reads `swiftui-pro/SKILL.md` and the referenced files, then applies the rules to changed Swift files. The SKILL.md uses `${CLAUDE_SKILL_DIR}/references/...` path tokens — the orchestrator interprets that as `references/vendored/swiftui-pro/references/...` in this repo.

## compose-expert

- **Upstream:** https://github.com/aldefy/compose-skill
- **Author:** Adit Lal ([@aldefy](https://github.com/aldefy))
- **Pinned version:** `2.3.1`
- **Pinned at:** 2026-05-13
- **License:** MIT (full [`compose-expert/LICENSE`](compose-expert/LICENSE))
- **Files vendored:** `SKILL.md` plus 32 reference files under `references/`, including 6 androidx source-code receipts under `references/source-code/`.
- **What the orchestrator does with it:** [`review-pr.md` § 4a.2](../../.claude/commands/review-pr.md) reads `compose-expert/SKILL.md` and the referenced files, then applies the rules to changed Kotlin files.
- **Note:** compose-expert's `SKILL.md` includes an "Installation notice" banner directing users to install the upstream plugin. That banner is preserved verbatim under MIT's redistribution clause. The notice is informational; the rules below it still apply correctly when read from this vendored copy.

---

## Cross-checked references (not vendored)

The `references/appium/*.md` rules are **built in-house**, not vendored — there is no off-the-shelf Appium/WebdriverIO *review* skill to redistribute (the ecosystem is all test-*generation* skills). A few rules cite or were cross-checked against third-party sources that are **not** copied into this repo, so no `LICENSE` file or version-pinned snapshot is needed. They're recorded here for provenance.

| Source | Used by | License | How it's used |
|---|---|---|---|
| [WebdriverIO `eslint-plugin-wdio`](https://github.com/webdriverio/webdriverio/tree/main/packages/eslint-plugin-wdio) (v9.x — `no-pause`, `no-floating-promise`, `await-expect`, `no-debug`) | `appium/config-and-secrets.md`, `appium/typescript-and-async.md`, `appium/waits-and-synchronization.md` | MIT (WebdriverIO monorepo) | Named as the **recommended mechanical lint backstop** for the P0 missing-`await` and P1 pause rules. The reviewer recommends the *target* repo adopt it in CI; nothing is copied here. |
| [WebdriverIO Best Practices & Selectors docs](https://webdriver.io/docs/bestpractices/) | `appium/locators.md`, `appium/waits-and-synchronization.md` | Docs (webdriver.io) | Authoritative source for accessibility-id-over-class selectors, avoid-`pause`, retrying `expect`, don't-cache-element-handles (stale). Cited, not copied. |
| [`LambdaTest/agent-skills` webdriverio-skill](https://github.com/LambdaTest/agent-skills) | footers of `appium/waits-and-synchronization.md`, `appium/gestures-and-scrolling.md`, `appium/reliability-and-flakiness.md` | MIT (verified) | A code-**generation** skill, not a reviewer. A few rules cross-checked their currency (Appium 2 `touchAction` deprecation, clickable-vs-displayed waits, stale re-query) against it; browser-only items were excluded. Not vendored because it provides no review rules. |

If a future upgrade *copies* content from one of these verbatim, promote it to a full vendored entry above (LICENSE file + version pin + sync routine), following the swiftui-pro / compose-expert model.

---

## Sync instructions (maintainer routine)

These snapshots are pinned. The upstream projects continue to evolve. Plan to re-sync **quarterly** (or on demand if either upstream lands a notable improvement).

### One-time prep

Make sure the upstream plugins are installed in your Claude Code so the cache has the latest versions:

```
/plugin marketplace add twostraws/SwiftUI-Agent-Skill
/plugin install swiftui-pro@swiftui-agent-skill
/plugin marketplace add aldefy/compose-skill
/plugin install compose-expert
/plugin update swiftui-pro@swiftui-agent-skill
/plugin update compose-expert@aldefy-compose-skill
```

### Sync command

```bash
cd ~/pr-review-skills

# Capture current upstream versions
SWIFTUI_VER=$(ls ~/.claude/plugins/cache/swiftui-agent-skill/swiftui-pro/ | sort -V | tail -1)
COMPOSE_VER=$(ls ~/.claude/plugins/cache/aldefy-compose-skill/compose-expert/ | sort -V | tail -1)
echo "Upstream versions: swiftui-pro $SWIFTUI_VER, compose-expert $COMPOSE_VER"

# Refresh the vendored copies
rm -rf references/vendored/swiftui-pro references/vendored/compose-expert
mkdir -p references/vendored/swiftui-pro/references references/vendored/compose-expert
cp ~/.claude/plugins/cache/swiftui-agent-skill/swiftui-pro/$SWIFTUI_VER/skills/swiftui-pro/SKILL.md \
   references/vendored/swiftui-pro/SKILL.md
cp ~/.claude/plugins/cache/swiftui-agent-skill/swiftui-pro/$SWIFTUI_VER/references/*.md \
   references/vendored/swiftui-pro/references/
cp -r ~/.claude/plugins/cache/aldefy-compose-skill/compose-expert/$COMPOSE_VER/skills/compose-expert/. \
   references/vendored/compose-expert/
cp ~/.claude/plugins/cache/aldefy-compose-skill/compose-expert/$COMPOSE_VER/LICENSE \
   references/vendored/compose-expert/LICENSE

# Review the diff before committing
git diff --stat references/vendored/
git diff references/vendored/

# Update the version pins in this file (UPSTREAM.md) to match $SWIFTUI_VER / $COMPOSE_VER

# Commit
git add references/vendored/ references/vendored/UPSTREAM.md
git commit -m "chore: sync vendored skills — swiftui-pro $SWIFTUI_VER, compose-expert $COMPOSE_VER"
git push
```

### What to verify after a sync

1. The "Pinned version" and "Pinned at" lines above are updated to match the new versions.
2. The orchestrator's path tokens in [`review-pr.md` § 4a.1](../../.claude/commands/review-pr.md) and § 4a.2 still match upstream's `${CLAUDE_SKILL_DIR}/references/...` convention (rarely changes, but worth a glance).
3. Run `/review-pr` on a recent PR to confirm the upgraded rule sets don't introduce false positives.
4. If either upstream changes their license away from MIT, **stop** — re-vendoring is no longer legally clean. Either secure explicit permission, or revert to the plugin-install model documented in commit `e44050d`.

---

## Attribution as required by MIT

Both projects' licenses require that copyright notices and the license text be preserved in redistributions. We satisfy this by:

- Keeping the full `LICENSE` file alongside each vendored skill (see [`compose-expert/LICENSE`](compose-expert/LICENSE); swiftui-pro's MIT declaration is in its [`SKILL.md`](swiftui-pro/SKILL.md) frontmatter, which is included unchanged).
- Listing each author and upstream URL at the top of this file.
- Not modifying the upstream content. If a project-specific tweak is needed, add an override file under `references/ios/` or `references/compose/` (our own project-tuned rules layer) rather than editing the vendored copy.
