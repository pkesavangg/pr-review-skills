# Contributing

All contributions must follow the [SDLC Policy](https://greatergoods.atlassian.net/wiki/spaces/me/pages/1244233748) and [Information Security Program](https://greatergoods.atlassian.net/wiki/spaces/me/pages/1245249562).

## Development Setup

1. Clone the repository
2. Configure local git hooks: `git config core.hooksPath .githooks`
3. Install required tools:


| Tool           | Install                                                       | Purpose                |
| -------------- | ------------------------------------------------------------- | ---------------------- |
| Xcode 16.0+    | Mac App Store                                                 | iOS build and test     |
| Android Studio | [developer.android.com](https://developer.android.com/studio) | Android build and test |
| SwiftLint      | `brew install swiftlint`                                      | iOS static analysis    |
| Gitleaks       | `brew install gitleaks`                                       | Secrets detection      |
| Lefthook       | `brew install lefthook`                                       | Git hook manager (pre-commit) |
| Detekt         | `brew install detekt`                                         | Android static analysis (pre-commit) |


1. **iOS:** Open `iOS/meApp.xcodeproj` in Xcode. Resolve Swift Package dependencies when prompted.
2. **Android:** Open the `Android/` directory in Android Studio. Gradle sync will run automatically.
3. **Android:** Run `lefthook install` from the repo root to activate pre-commit hooks (Detekt, Gitleaks, JIRA ticket validation). This is also done automatically on every Gradle build.
4. Run tests:
  - **iOS:** `cd iOS && xcodebuild test -project meApp.xcodeproj -scheme meApp -destination 'id={DEVICE_ID}' -only-testing:meAppTests`
  - **Android:** `cd Android && ./gradlew test`

## Code Style

### General

- Follow `.editorconfig` for indentation and formatting (UTF-8, LF line endings, trailing newline)
- Swift and Kotlin: 4-space indentation
- JSON: 2-space indentation
- YAML/TOML: 2-space indentation

### iOS (Swift)

- SwiftLint enforces all style rules — see `iOS/.swiftlint.yml`
- **HIPAA rules are errors, not warnings.** The following will block your commit:
  - `force_cast`, `force_try`, `force_unwrapping` — use optional binding instead
  - `print()` / `NSLog()` — use `LoggerService` exclusively
  - Direct `UserDefaults` access — use `KvStorageService` or `KeychainService`
  - Hardcoded credentials — use environment variables or secure storage
- Never hardcode colors, fonts, or spacing — use `Theme/` resources
- Static strings go in feature-specific `Strings/` folders as PascalCase structs

### Android (Kotlin)

- Detekt enforces static analysis — see `Android/config/detekt/detekt.yml`
- Follow MVI pattern: `BaseIntentViewModel<State, Intent>` with immutable state data classes
- Use `AppLog` for logging, never `Log` directly
- Never hardcode colors/typography/spacing — use `MeAppTheme` tokens

Static analysis (SwiftLint, Detekt, Gitleaks) is enforced in CI. Run checks locally before pushing.

## Commit Messages

All commits must reference a Jira ticket from the `MA` project. The commit-msg hook enforces this automatically.

**Format:** `MA-XXXX Short description of what was done`

**Examples:**

```
MA-3428 Add git hooks for lint and commit validation
MA-3425 Add Gitleaks config and integrate secrets scanning into pre-commit hook
```

Merge commits, reverts, and fixup/squash commits are exempt from this requirement.

See [SDLC Policy Section 3.1](https://greatergoods.atlassian.net/wiki/spaces/me/pages/1244233748/Software+Development+Lifecycle+SDLC+Policy#3.1-Branching-Strategy) for full commit and branching rules.

## Branching Strategy


| Branch                       | Purpose                           | Deploys To   |
| ---------------------------- | --------------------------------- | ------------ |
| `main`                       | Production-ready, tagged releases | Production   |
| `dev`                        | Integration branch                | Dev          |
| `MA-XXXX-description`        | Feature / bug fix work            | —            |
| `release/X.Y.Z`              | Release candidates                | Staging / QA |
| `hotfix/MA-XXXX-description` | Emergency production fixes        | Production   |


All work is done on feature branches off `dev`. Direct commits to `main` or `dev` are prohibited.

**Branch naming format:**

```
MA-{TICKET_NUMBER}-{slugified-summary}
```

Examples:

- `MA-3428-upgrade-swiftlint-rules-to-meet-hipaa-security-requirements`
- `MA-1009-fix-layout-and-long-press-gesture-issues-in-dashboard-metrics`

## Pull Request Process

1. Create a feature branch from `dev`: `MA-XXXX-short-description`
2. Make changes and add tests
3. Run checks locally (build, lint, tests)
4. Submit PR against `dev`
5. Wait for CI checks and code review (1 approval minimum, 2 for sensitive areas)

**Sensitive areas requiring 2 approvals:** authentication, PHI-touching code, database migrations, Keychain/secure storage changes, CI pipeline modifications.

PRs must be focused on a single Jira ticket. Avoid bundling unrelated changes.

## CI Pipeline

CircleCI runs on every push with the following jobs:


| Job                | What it checks                                        |
| ------------------ | ----------------------------------------------------- |
| `gitleaks`         | Scans the repository for leaked secrets               |
| `swiftlint`        | Runs SwiftLint in `--strict` mode on the iOS codebase |
| `build`            | Builds the iOS project (Debug, no code signing)       |
| `dependency-audit` | Verifies all SPM dependencies are version-pinned      |


All jobs must pass before a PR can be merged.

## Git Hooks

The `.githooks/` directory contains hooks that run automatically after setup:


| Hook         | What it does                                                                                                                                    |
| ------------ | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `pre-commit` | Runs **Gitleaks** on staged files to detect secrets, then runs **SwiftLint** on staged `.swift` files. Blocks the commit if either check fails. |
| `commit-msg` | Validates that the commit message contains a Jira reference (`MA-XXXX`). Blocks the commit if missing.                                          |


If you need to bypass hooks in an emergency: `git commit --no-verify`. Use sparingly — CI will still enforce these checks.

Android pre-commit hooks (Detekt, Gitleaks, and JIRA ticket validation) are managed by **Lefthook** via `.lefthook.yml` — separate from the `.githooks/` directory above. They activate automatically after running `lefthook install` or on your first Gradle build.

## Testing

### iOS

- **Framework:** Swift Testing (`@Test`, `@Suite`, `#expect`)
- **Unit tests:** `iOS/meAppTests/`
- **UI tests:** `iOS/meAppUITests/`
- Tests require a **physical device** — simulator is not supported due to library compatibility constraints

### Android

- **Unit tests:** `Android/app/src/test/`
- **Instrumented tests:** `Android/app/src/androidTest/`

### Coverage Targets


| Layer                          | Minimum  |
| ------------------------------ | -------- |
| Services / Stores              | 80%      |
| Auth / Account / Sync services | 85%      |
| Forms / Validation             | 85%      |
| API repository adapters        | 75%      |
| UI layer (Views, Screens)      | Excluded |


## Security

This project handles health data subject to **HIPAA** compliance requirements. All contributors must follow the [Information Security Program](https://greatergoods.atlassian.net/wiki/spaces/me/pages/1245249562).

- **Never commit secrets, API keys, tokens, or credentials.** Gitleaks scans both locally (pre-commit) and in CI.
- Sensitive files (`.env`, `*.pem`, `*.key`, `GoogleService-Info.plist`) are listed in `.gitignore`.
- Auth tokens must be stored in Keychain (iOS) or encrypted storage (Android), never in plain-text persistence.
- Do not log PHI or PII. Use `LoggerService` (iOS) or `AppLog` (Android) which redact sensitive data.
- Report vulnerabilities to **[security@dmdbrands.com](mailto:security@dmdbrands.com)**. See `SECURITY.md` for the full disclosure process.

## Dependency Management

- **iOS:** Swift Package Manager. Dependabot monitors for vulnerabilities weekly and opens PRs automatically. All dependencies must be pinned to a specific version (enforced by the `dependency-audit` CI job).
- **Android:** Gradle. Pin dependency versions in `build.gradle.kts`.

New dependencies require review for license compatibility and security posture before merging.

## Versioning

This project uses [Semantic Versioning](https://semver.org/). See the [Versioning Policy](https://greatergoods.atlassian.net/wiki/spaces/me/pages/1323958382) for details.

## Release Process

Releases follow the [SDLC release process](https://greatergoods.atlassian.net/wiki/spaces/me/pages/1244233748) (Section 6.3). Contact the team lead to initiate a release cycle.

## Code of Conduct

All contributors are expected to maintain a professional and respectful environment. Harassment, discrimination, and disruptive behavior will not be tolerated.

## Questions?

- **Jira:** Tickets are tracked in the `MA` project on [greatergoods.atlassian.net](https://greatergoods.atlassian.net)
- **Code ownership:** See `CODEOWNERS` for review assignments
- **SDLC standards:** [SDLC Policy](https://greatergoods.atlassian.net/wiki/spaces/me/pages/1244233748)

