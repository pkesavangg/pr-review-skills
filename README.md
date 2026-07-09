# pr-review-skills

A team-shared Claude Code reviewer for **SwiftUI**, **Jetpack Compose**, and **Appium / WebdriverIO E2E** test code, plus a PR description writer. Two slash commands and one auto-triggering skill sharing one rule set:

| Trigger          | Type     | Who runs it | When                       | Input                                              | Output                                                                       |
| ---------------- | -------- | ----------- | -------------------------- | -------------------------------------------------- | ---------------------------------------------------------------------------- |
| `/review`        | Command  | Author      | Before commit / before PR  | Local working tree (staged + unstaged)             | `.claude-review/report.md` + offered in-place fixes (you stage them yourself) |
| `/review-pr`     | Command  | Reviewer    | After PR is opened         | GitHub PR diff + comments                          | Inline GitHub review comments + summary                                       |
| `pr-description` | Skill    | Author      | Before / after opening PR  | Current branch, branch override, or GitHub PR URL  | PR title + Markdown body (Jira-linked) ready to paste or pipe to `gh pr create` |

Both commands:

- Auto-detect whether the change touches iOS (Swift / SwiftUI), Android (Kotlin / Compose), both, or Appium/WebdriverIO E2E test code (TypeScript).
- Run cross-platform **security** ([references/security/](references/security/)) and **privacy compliance** ([references/privacy/](references/privacy/)) checks — secrets, insecure storage, TLS bypass, weak crypto, PII/PHI in logs, exposure surfaces, App Store / Play Store submission gates.
- Apply Paul Hudson's [`swiftui-pro`](https://github.com/twostraws/SwiftUI-Agent-Skill) rules (vendored under [references/vendored/swiftui-pro/](references/vendored/swiftui-pro/), MIT licensed) for SwiftUI quality, then add iOS cross-cutting checks (concurrency, logging placement, test flake) from [references/ios/](references/ios/).
- Apply aldefy's [`compose-expert`](https://github.com/aldefy/compose-skill) rules (vendored under [references/vendored/compose-expert/](references/vendored/compose-expert/), MIT licensed) for Compose quality, then add project-tuned Compose rules from [references/compose/](references/compose/).
- For Appium/WebdriverIO E2E code, run the mobile test-automation pipeline from [references/appium/](references/appium/) (locators, waits, gestures, Page Object discipline, test structure, flakiness, TypeScript/async, config & secrets) **instead of** the SwiftUI/Compose pipelines.
- Tag findings `P0` / `P1` / `P2` / `Nit`.

`/review-pr` adds: re-review mode (walks prior priority comments, decides resolved / accepted / partial / still open, replies on the thread; reviews any new code added since the previous pass; posts a top-level summary). Auto-approves (`gh pr review --approve`) only when the PR is genuinely clean — zero findings on a first-review, or all prior findings resolved/accepted with verified tickets and no new findings on a re-review; otherwise posts `--comment`. Never `--request-changes`.

`/review` adds: writes findings to a local Markdown report, offers an interactive "apply fixes" picker, applies fixes in-place via `Edit`. Never mutates git state — you `git add` deliberately. Has a `--staged --no-prompt` mode for opt-in pre-commit hooks.

`pr-description` is an auto-triggering skill (no slash). It fires on phrases like "write a PR description", "raise a PR against KITC-541", "describe this PR", or when you paste a `github.com/.../pull/N` URL. Resolves the Jira ID from the message, branch name, or commits; reads the diff; emits a Title block + Markdown Body block with Summary / Changes / Test plan / Jira sections. Never adds the AI attribution footer. Will not open or push the PR unless you ask.

## How it works

See [HOW-IT-WORKS.md](HOW-IT-WORKS.md) for flowcharts of both pipelines, what gets checked at each step, and the loops involved.

## What's in this repo

```
pr-review-skills/
├── README.md
├── CLAUDE.md                   ← guidance for Claude when editing this repo (architecture invariants, rule-authoring house style, guardrails)
├── INSTALL.md
├── HOW-IT-WORKS.md
├── .claude/
│   ├── commands/
│   │   ├── review-pr.md        ← reviewer-side orchestrator (post-PR)
│   │   └── review.md           ← author-side orchestrator (pre-commit, local)
│   └── skills/
│       └── pr-description/     ← auto-triggering skill: title + body from branch/PR/Jira
│           └── SKILL.md
└── references/
    ├── vendored/                ← MIT-licensed snapshots of swiftui-pro + compose-expert; see UPSTREAM.md
    │   ├── UPSTREAM.md          ← attribution, version pins, quarterly sync routine
    │   ├── swiftui-pro/         ← Paul Hudson's SwiftUI rules (vendored from v1.0.0)
    │   └── compose-expert/      ← aldefy's Compose rules (vendored from v2.3.1)
    ├── security/                ← cross-platform security (iOS + Android)
    │   ├── secrets-and-storage.md          ← hardcoded creds, Keychain / EncryptedSharedPreferences, backup flags
    │   ├── transport-crypto-input.md       ← TLS bypass, weak crypto / RNG, injection
    │   └── logging-and-exposure.md         ← PII/PHI in logs, clipboard, deep links, WebView bridges
    ├── privacy/                 ← cross-platform App Store / Play Store compliance
    │   └── store-compliance.md             ← PrivacyInfo.xcprivacy, NSXxxUsageDescription, ATT, Android dangerous-permission runtime requests
    ├── compose/                 ← project-tuned Compose rules on top of compose-expert
    │   ├── recomposition.md
    │   ├── state-management.md
    │   ├── modifier-conventions.md
    │   ├── accessibility.md
    │   └── api-guidelines.md
    ├── appium/                  ← WebdriverIO + Appium E2E test-automation rules (run instead of SwiftUI/Compose)
    │   ├── locators.md                      ← selector tiers, platformLocator, duplicated literals
    │   ├── waits-and-synchronization.md     ← pause / bumped-timeout band-aids, TIMEOUTS / WAIT constants
    │   ├── gestures-and-scrolling.md        ← scroll-into-view, deprecated touchAction, fixed-pixel offsets
    │   ├── page-objects.md                  ← POM boundaries: assertions / selectors / data in the right layer
    │   ├── test-structure-and-assertions.md ← test independence, clean state, real assertions
    │   ├── reliability-and-flakiness.md     ← swallowed catches, .catch(()=>false) feeding an assertion
    │   ├── typescript-and-async.md          ← missing await (P0), floating promises, type safety
    │   ├── config-and-secrets.md            ← committed secrets in test/data, lint-gate recommendation
    │   ├── helpers-and-reuse.md             ← reuse AppHelper / AuthHelper / ElementHelper vs re-rolling
    │   └── mobile-commands-and-context.md   ← native↔WebView context restore, appium* legacy commands
    └── ios/                     ← project-tuned iOS rules on top of swiftui-pro
        ├── concurrency.md       ← Swift Concurrency footguns
        ├── logging-hygiene.md   ← log-in-body / log-in-onChange / empty-catch
        └── test-hygiene.md      ← sleep / .shared singletons / framework mixing
```

The two upstream SwiftUI / Compose rule sets are vendored verbatim under [references/vendored/](references/vendored/) per their MIT licenses. This means teammates need only `git clone` this repo — no separate plugin installs. Vendored versions are pinned and re-synced quarterly; see [references/vendored/UPSTREAM.md](references/vendored/UPSTREAM.md) for the routine.

## Quick start

See [INSTALL.md](INSTALL.md) for the full installer. TL;DR:

```bash
# One-time: clone this repo and symlink both commands + the skill. All rules ship inside.
git clone https://github.com/pkesavangg/pr-review-skills.git ~/pr-review-skills
mkdir -p ~/.claude/commands ~/.claude/skills
ln -s ~/pr-review-skills/.claude/commands/review-pr.md      ~/.claude/commands/review-pr.md
ln -s ~/pr-review-skills/.claude/commands/review.md         ~/.claude/commands/review.md
ln -s ~/pr-review-skills/.claude/skills/pr-description      ~/.claude/skills/pr-description
```

## Usage

```
# Author flow — pre-commit, local
/review                                       # staged + unstaged vs HEAD
/review --staged                              # only staged (matches pre-commit hook)
/review --vs main                             # everything since branching from main
/review --no-prompt                           # write report only, no fix picker (for hooks)

# Reviewer flow — post-PR, GitHub
/review-pr https://github.com/org/repo/pull/123
/review-pr 123 124 125                        # multiple PRs in one call

# PR description — auto-triggers from natural language (no slash needed)
write a PR description for this branch
raise a PR against KITC-541
describe this PR: https://github.com/org/repo/pull/123
```

Re-review and re-pass are the **same** commands — both auto-detect mode from prior state (GitHub comments for `/review-pr`, the prior `.claude-review/report.md` for `/review`).

## Updating

```bash
cd ~/pr-review-skills && git pull
```

That's the only command. All rule sets (vendored upstream + project-tuned) flow through this single update. The maintainer re-syncs the vendored upstream snapshots quarterly per [references/vendored/UPSTREAM.md](references/vendored/UPSTREAM.md).

## Contributing

Tune the rules under [references/](references/) as the team learns what false-positives to suppress — security, privacy, compose, ios. Open a PR; one approval required.
