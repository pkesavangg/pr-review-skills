# pr-review-skills

A team-shared Claude Code reviewer for **SwiftUI** and **Jetpack Compose** pull requests.

One command — `/review-pr <PR-url-or-number>` — that:

- Auto-detects whether the PR touches iOS (Swift / SwiftUI), Android (Kotlin / Compose), or both.
- Auto-detects whether this is a first review or a re-review (looking for prior `P0/P1/P2/Nit` comments the skill left on earlier passes).
- Runs cross-platform **security** ([references/security/](references/security/)) and **privacy compliance** ([references/privacy/](references/privacy/)) checks on every PR — secrets, insecure storage, TLS bypass, weak crypto, PII/PHI in logs, exposure surfaces, App Store / Play Store submission gates.
- Delegates SwiftUI quality review to Paul Hudson's [`swiftui-pro`](https://github.com/twostraws/SwiftUI-Agent-Skill) skill, then adds iOS cross-cutting checks (concurrency, logging placement, test flake) from [references/ios/](references/ios/) that `swiftui-pro` doesn't cover.
- Delegates Compose quality review to [`compose-expert`](https://github.com/aldefy/compose-skill), then adds project-tuned Compose rules from [references/compose/](references/compose/).
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
    ├── security/                ← cross-platform security (iOS + Android)
    │   ├── secrets-and-storage.md          ← hardcoded creds, Keychain / EncryptedSharedPreferences, backup flags
    │   ├── transport-crypto-input.md       ← TLS bypass, weak crypto / RNG, injection
    │   └── logging-and-exposure.md         ← PII/PHI in logs, clipboard, deep links, WebView bridges
    ├── privacy/                 ← cross-platform App Store / Play Store compliance
    │   └── store-compliance.md             ← PrivacyInfo.xcprivacy, NSXxxUsageDescription, ATT, Android dangerous-permission runtime requests
    ├── compose/                 ← Compose-specific review rules
    │   ├── recomposition.md
    │   ├── state-management.md
    │   ├── modifier-conventions.md
    │   ├── accessibility.md
    │   └── api-guidelines.md
    └── ios/                     ← iOS cross-cutting rules on top of swiftui-pro
        ├── concurrency.md       ← Swift Concurrency footguns
        ├── logging-hygiene.md   ← log-in-body / log-in-onChange / empty-catch
        └── test-hygiene.md      ← sleep / .shared singletons / framework mixing
```

SwiftUI API rules are **not** vendored here — they come from `swiftui-pro` at runtime, which keeps Paul Hudson's content authoritative and auto-updating. The `references/ios/` files only cover what `swiftui-pro` doesn't.

## Quick start

See [INSTALL.md](INSTALL.md) for the full installer. TL;DR:

```bash
# One-time: install swiftui-pro (Paul Hudson)
/plugin marketplace add twostraws/SwiftUI-Agent-Skill
/plugin install swiftui-pro@swiftui-agent-skill

# One-time: install compose-expert (aldefy)
/plugin marketplace add aldefy/compose-skill
/plugin install compose-expert

# One-time: clone this repo and symlink the command
git clone <internal>/pr-review-skills.git ~/pr-review-skills
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

Updates flow to the whole team. `swiftui-pro` updates via `/plugin update swiftui-pro@swiftui-agent-skill`.

## Contributing

Tune the rules under [references/](references/) as the team learns what false-positives to suppress — security, privacy, compose, ios. Open a PR; one approval required.
