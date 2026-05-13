# pr-review-skills

A team-shared Claude Code reviewer for **SwiftUI** and **Jetpack Compose** pull requests.

One command — `/review-pr <PR-url-or-number>` — that:

- Auto-detects whether the PR touches iOS (Swift / SwiftUI), Android (Kotlin / Compose), or both.
- Auto-detects whether this is a first review or a re-review (looking for prior `P0/P1/P2/Nit` comments the skill left on earlier passes).
- Runs cross-platform **security** ([references/security/](references/security/)) and **privacy compliance** ([references/privacy/](references/privacy/)) checks on every PR — secrets, insecure storage, TLS bypass, weak crypto, PII/PHI in logs, exposure surfaces, App Store / Play Store submission gates.
- Applies Paul Hudson's [`swiftui-pro`](https://github.com/twostraws/SwiftUI-Agent-Skill) rules (vendored under [references/vendored/swiftui-pro/](references/vendored/swiftui-pro/), MIT licensed) for SwiftUI quality, then adds iOS cross-cutting checks (concurrency, logging placement, test flake) from [references/ios/](references/ios/).
- Applies aldefy's [`compose-expert`](https://github.com/aldefy/compose-skill) rules (vendored under [references/vendored/compose-expert/](references/vendored/compose-expert/), MIT licensed) for Compose quality, then adds project-tuned Compose rules from [references/compose/](references/compose/).
- Posts inline GitHub comments tagged `P0` / `P1` / `P2` / `Nit`.
- On re-review: walks every prior priority comment, decides if it's now resolved / accepted with a valid reason / partial / still open, and replies on the same thread.
- Reviews any new code added since the previous pass.
- Posts a top-level summary with `REQUEST_CHANGES` if any P0/P1 remains open, else `COMMENT`. Never auto-approves.

## What's in this repo

```
pr-review-skills/
├── README.md
├── INSTALL.md
├── .claude/
│   └── commands/
│       └── review-pr.md        ← the orchestrator slash command
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
    └── ios/                     ← project-tuned iOS rules on top of swiftui-pro
        ├── concurrency.md       ← Swift Concurrency footguns
        ├── logging-hygiene.md   ← log-in-body / log-in-onChange / empty-catch
        └── test-hygiene.md      ← sleep / .shared singletons / framework mixing
```

The two upstream SwiftUI / Compose rule sets are vendored verbatim under [references/vendored/](references/vendored/) per their MIT licenses. This means teammates need only `git clone` this repo — no separate plugin installs. Vendored versions are pinned and re-synced quarterly; see [references/vendored/UPSTREAM.md](references/vendored/UPSTREAM.md) for the routine.

## Quick start

See [INSTALL.md](INSTALL.md) for the full installer. TL;DR:

```bash
# One-time: clone this repo and symlink the command. That's it — all rules ship inside.
git clone https://github.com/pkesavangg/pr-review-skills.git ~/pr-review-skills
mkdir -p ~/.claude/commands
ln -s ~/pr-review-skills/.claude/commands/review-pr.md ~/.claude/commands/review-pr.md
```

## Usage

```
/review-pr https://github.com/org/repo/pull/123
/review-pr 123 124 125              # multiple PRs in one call
```

Re-review is the **same** command — the skill detects mode automatically.

## Updating

```bash
cd ~/pr-review-skills && git pull
```

That's the only command. All rule sets (vendored upstream + project-tuned) flow through this single update. The maintainer re-syncs the vendored upstream snapshots quarterly per [references/vendored/UPSTREAM.md](references/vendored/UPSTREAM.md).

## Contributing

Tune the rules under [references/](references/) as the team learns what false-positives to suppress — security, privacy, compose, ios. Open a PR; one approval required.
