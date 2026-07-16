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
| `ios-unit-tests` | macOS `m4pro.medium`, Xcode 16.4.0 | `meAppTests` on the iPhone 16 simulator + 80% non-UI line coverage (runs after `ios-build`) |
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

## iOS unit tests (simulator)

`ios-unit-tests` is **enabled** and runs on every iOS change (after `ios-build`, same
branch filters). It runs `meAppTests` on the **iPhone 16 simulator** and enforces an
**80% non-UI line-coverage** gate.

The BLE + Wi-Fi native packages don't build for the simulator on their shipping branches
(`ggBluetoothNativeLibrary=dev`, `ggWifiScalePackage=main`), so **this job — and only this
job — repoints those two at their `simulator-support` branch** on the throwaway CI
checkout, then re-resolves. The committed project keeps `main`/`dev` for device + App
Store builds; nothing is committed back. (Locally, mirror this with
`/prepare-simulator-build` / `scripts/sim-packages.sh`, and `restore` before committing.)

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
- **On `develop` only**, `android-build` uploads the just-built debug APK to
  **BrowserStack App Automate** under a stable `custom_id=meApp_android_dev` (MOB-1437),
  so the `meAppTest` E2E pipeline can reference `bs://meApp_android_dev` without
  rebuilding. It runs after the Gradle cache save; feature-branch builds skip it so they
  don't clobber the shared `custom_id`. Needs `BROWSERSTACK_USERNAME` +
  `BROWSERSTACK_ACCESS_KEY` (the job fails if either is unset).

## Required env vars / secrets

| Name | Used by | Purpose |
|------|---------|---------|
| `GITHUB_USERNAME` | Android | GitHub account for Packages (Maven) auth — set to **`pkesavangg`** in CircleCI |
| `GITHUB_PACKAGES_TOKEN` | iOS + Android | **`pkesavangg`'s** classic PAT (`read:packages` + `repo`, SSO-authorized for `gg-engineering`), stored as a CircleCI env var. Used by iOS SPM clones and Android Maven. |
| `GOOGLESERVICE_INFO_BASE64` | iOS | base64 of `GoogleService-Info.plist` (used by `ios-build` and `ios-unit-tests`) |
| `GOOGLE_SERVICES_JSON_BASE64` | Android | base64 of `google-services.json` |
| `BROWSERSTACK_USERNAME` | Android | BrowserStack App Automate auth for the `develop` APK upload |
| `BROWSERSTACK_ACCESS_KEY` | Android | BrowserStack App Automate auth for the `develop` APK upload |

CI authenticates to the private GitHub repos and packages as the **`pkesavangg`**
GitHub account: `GITHUB_USERNAME` holds the account name and `GITHUB_PACKAGES_TOKEN`
holds that account's personal access token. Both are configured as CircleCI
project/context **environment variables** — the token is a secret and is never
stored in the repo. If CI auth breaks (401/403), check or rotate `pkesavangg`'s PAT.
