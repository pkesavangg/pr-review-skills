# meApp CircleCI

How the meApp pipeline (`.circleci/config.yml`) is configured — the runners, the
checks each platform gates on, and the secrets CI needs. This describes the
**current** setup; it is not a change log.

## Workflows

- **`build-and-test`** — runs on every push to `develop`, `main`, `MA-*`, `MOB-*`.
- **`scheduled-security`** — weekly (Mon 09:00 UTC) OWASP dependency scan on `main`.

Each platform job first inspects the diff and **halts early if nothing under its
platform changed** (`iOS/`, `Android/`, or `.circleci/`), so an Android-only PR
doesn't spend macOS minutes and vice-versa. `gitleaks` always runs.

## Checks

| Job | Runner | Gates on |
|-----|--------|----------|
| `gitleaks` | `cimg/base` (Linux) | No secrets in the working tree |
| `ios-swiftlint` | macOS `m4pro.medium`, Xcode 16.4.0 | `swiftlint --strict` (zero violations) |
| `ios-build` | macOS `m4pro.medium`, Xcode 16.4.0 | App compiles (`-configuration Dev`) |
| `ios-dependency-audit` | macOS `m4pro.medium` | Every SPM pin is reproducible |
| `android-build` | `cimg/android` (Linux) | `./gradlew assembleDebug` |
| `android-lint` | `cimg/android` | `./gradlew lint` + `detekt` |
| `android-test` | `cimg/android` | Unit tests + JaCoCo 80% line coverage |
| `android-owasp-scan` | `cimg/android` | Weekly dependency CVE scan |

## Why SwiftLint and the iOS build run as separate jobs

SwiftLint is its own gate (`ios-swiftlint`): installed via Homebrew and run
directly with `swiftlint --strict`. It is intentionally **not** wired into the
Xcode project as an SPM *build-tool plugin*.

A build-tool plugin is compiled from source during package resolution, and
SwiftLint drags in `SourceKitten` + `swift-syntax` — a large toolchain that
nothing else in the app uses. Building it as part of `ios-build` made package
resolution slow and unreliable. Keeping lint separate means:

- **`ios-build` stays lean** — it resolves and compiles only the app's real
  dependencies, with no linter toolchain in the graph.
- **Lint still gates every iOS change** — `ios-swiftlint` runs `--strict` on the
  same branches (and the local pre-commit hook + `scripts/check-snapshot-boundary.sh`
  enforce the same rules locally).
- The two jobs run **in parallel**, so decoupling them costs no wall-clock time.

## iOS build notes

- The project defines **`Dev`** and **`Production`** configurations — there is no
  `Debug`. CI builds **`Dev`** (the debug-equivalent, `-Onone`).
- `Package.resolved` is **committed**; CI resolves with
  `-onlyUsePackageVersionsFromResolvedFile` to use the exact pinned versions and
  skip SwiftPM's version solver. SPM checkouts and DerivedData are cached.
- Private `gg-*` Swift packages are cloned over HTTPS with a read-only token
  (`GITHUB_PACKAGES_TOKEN`).

## Android build notes

- Private `com.dmdbrands.lib:*` artifacts resolve from GitHub Packages
  (`maven.pkg.github.com`) using `GITHUB_USERNAME` + `GITHUB_PACKAGES_TOKEN`
  (mapped to `GITHUB_TOKEN` for Gradle).
- `google-services.json` is restored from a base64 secret before any Gradle task.

## Required env vars / secrets

| Name | Used by | Purpose |
|------|---------|---------|
| `GITHUB_USERNAME` | Android | GitHub Packages (Maven) auth |
| `GITHUB_PACKAGES_TOKEN` | iOS + Android | Classic PAT with `read:packages` + `repo`, SSO-authorized for `gg-engineering`. Used by iOS SPM clones and Android Maven. |
| `GOOGLESERVICE_INFO_BASE64` | iOS | base64 of `GoogleService-Info.plist` |
| `GOOGLE_SERVICES_JSON_BASE64` | Android | base64 of `google-services.json` |

## Not yet enabled

- **`ios-unit-tests`** — present in the config but commented out. iOS unit tests
  require a physical device (a dependency is unsupported on the simulator).
  Re-enable the job and its workflow entry when simulator support is restored.
