# meApp CircleCI — Issues & Fixes

Reference for the meApp CircleCI pipeline (`.circleci/config.yml`), documenting
the issues hit on the `MOB-1049` branch and how each was resolved. Useful when a
gate fails or when setting the pipeline up on a fresh project/context.

## Pipeline at a glance

| Job | Runner | What it does |
|-----|--------|--------------|
| `gitleaks` | `cimg/base` | Secrets scan (working tree) |
| `swiftlint` | macOS `m4pro.medium`, Xcode 16.4.0 | iOS lint (`--strict`) |
| `build` | macOS `m4pro.medium`, Xcode 16.4.0 | iOS build |
| `dependency-audit` | macOS `m4pro.medium` | SPM `Package.resolved` pin check |
| `android-build` | `cimg/android:2024.11.1` | `./gradlew assembleDebug` |
| `android-lint` | `cimg/android:2024.11.1` | `./gradlew lint` + `detekt` |
| `android-test` | `cimg/android:2024.11.1` | `test` + JaCoCo 80% gate |
| `android-owasp-scan` | `cimg/android:2024.11.1` | Weekly OWASP dependency scan |

## Required CircleCI env vars / secrets

| Name | Purpose |
|------|---------|
| `GITHUB_USERNAME` | GitHub username for GitHub Packages (Maven) auth |
| `GITHUB_PACKAGES_TOKEN` | **Classic** PAT with **`read:packages` + `repo`**, SSO-authorized for the `gg-engineering` org. Used by Android Maven *and* iOS SPM clones. |
| `GOOGLESERVICE_INFO_BASE64` | base64 of iOS `GoogleService-Info.plist` |
| `GOOGLE_SERVICES_JSON_BASE64` | base64 of Android `google-services.json` |

---

## Issues & fixes

### 1. iOS build — `'visionOS' is unavailable` (invalid manifest)
- **Symptom:** `xcodebuild -resolvePackageDependencies` fails compiling `swiftui-cached-async-image`'s `Package.swift` — `error: 'visionOS' is unavailable`.
- **Root cause:** `Package.resolved` was gitignored (`*.xcworkspace` in root `.gitignore` + `Package.resolved` in `iOS/.gitignore`), so CI re-resolved to the **latest** tag (`2.1.2`), whose manifest uses `.visionOS(.v1)` under `// swift-tools-version:5.6` (broken). Local worked only because `~/.gradle`/SPM cache held the good `2.1.1`.
- **Fix:** Commit `Package.resolved` (negation rules added to both `.gitignore`s) so CI resolves the exact pinned versions. Pins `swiftui-cached-async-image` at `2.1.1`. _(commit `01b3798c`)_
- **Bonus:** also makes `dependency-audit` actually run (it had been skipping because the file was absent).

### 2. Android build — `401 Unauthorized` from `maven.pkg.github.com`
- **Symptom:** `Could not resolve com.dmdbrands.lib:gg-bluetooth-android … 401 Unauthorized`.
- **Root cause:** `Android/settings.gradle.kts` reads `GITHUB_USERNAME` / `GITHUB_TOKEN`; CI never set them.
- **Fix:** Added a reusable `setup_github_packages_auth` command that maps the existing `GITHUB_PACKAGES_TOKEN` secret into `GITHUB_TOKEN` (and reads `GITHUB_USERNAME` natively), wired into all Android jobs. Also DRY'd the duplicated google-services restore into `restore_google_services`. _(commits `01b3798c`, `dd132edb`)_

### 3. Token scope — `repo` alone is not enough
- **Symptom:** iOS SPM git clones succeed, but Android Maven still `401`.
- **Root cause:** git clone needs `repo`; **GitHub Packages download needs `read:packages`** — a separate scope.
- **Fix (user action):** classic PAT with **both** `read:packages` + `repo`, **SSO-authorized** for `gg-engineering`. A non-fatal `curl` probe step prints the HTTP status so future failures self-explain: `401` = missing scope, `403` = SSO not authorized, `200` = OK. _(commit `dd132edb`)_

### 4. Org rename `dmdbrands` → `gg-engineering`
- **Symptom:** `Could not find … gg-bluetooth-android:1.6.10. Searched in … /dmdbrands/…`.
- **Root cause:** the GitHub org was renamed `dmdbrands` → `gg-engineering`. The repo REST API follows the rename redirect; **GitHub Packages (`maven.pkg.github.com`) does not** — old `dmdbrands/*` URLs 404 forever. Resolution is also **per-repo**. Local worked from pre-rename `~/.gradle` cache.
- **Fix:** point the Maven repos at `gg-engineering/ggBluetoothNativeLibrary` and `gg-engineering/vico` in `settings.gradle.kts`. _(commit `e5c608f8`)_
- **Latent bug (other repo):** `ggBluetoothNativeLibrary`'s own Android publish config still pushes to the dead `dmdbrands/*` URL — future releases land nowhere until fixed there.

### 5. `gg-bluetooth-android:1.6.10` was never published
- **Symptom:** `Could not find … 1.6.10` even at the corrected `gg-engineering` URL.
- **Root cause:** `maven-metadata.xml` shows `<latest>1.6.9</latest>`; **`1.6.10` was never published** (the pin was aspirational).
- **Fix:** pin `ggBluetoothAndroid = "1.6.9"` in `Android/gradle/libs.versions.toml`. _(commit `7e6e56a9`)_

### 6. iOS resolve hang / `Too long with no output`
- **Symptom:** `Resolve Swift Package Dependencies` times out; in one run it ran ~3 hours before the job max killed it. All packages cloned in ~1 min, then SwiftPM stalled in the post-checkout **version-solving** phase (Firebase + a `swift-syntax` prerelease graph).
- **Fix:** since `Package.resolved` is committed, pass **`-onlyUsePackageVersionsFromResolvedFile`** to skip the solver (resolve + build); cache SPM (`SourcePackages` + `~/Library/Caches/org.swift.swiftpm`) and DerivedData; remove the earlier heartbeat (it masked real hangs); keep a finite `no_output_timeout`. _(commits `6980a37a`, `3d50fd40`)_

### 7. `dependency-audit` — false "unpinned" failure
- **Symptom:** exit 1, `WARNING: Unpinned dependencies: ggappsyncpackage, ggbluetoothnativelibrary, …`.
- **Root cause:** the audit script flagged any pin without a **semver `version`** as unpinned — but the internal `gg-*` SPM packages track a **branch**, so `Package.resolved` records a commit `revision` (SHA) with no `version`. A committed SHA *is* reproducible. Surfaced only after `Package.resolved` was committed (§1).
- **Fix:** treat a pin as pinned if it has a `version` **or** a `revision`; fail only when neither is present; list branch-tracked packages as an informational `NOTE`. _(commit `f7a993b4`)_

### 8. `gitleaks` — timeout, plus real secrets in history
- **Symptom:** the `Secrets scan` step timed out (`gitleaks detect` walks ~5k commits / ~185 MB with no output → 10-min no-output kill). A full-history scan also finds **132 leaks**, including **3 GitHub PATs** hardcoded in `Android/settings.gradle.kts` *history* (the old `password = "ghp_…"` block, 2025-07 → 2026-01).
- **Fix:** scan the working tree (`gitleaks dir .`) instead of full history — fast, and gates the code that ships; allowlist the 23 working-tree false positives (UI labels / field-name constants / mock data) in `.gitleaks.toml` by convention-safe dirs (`strings/`, `Strings/`, `data/api/`) + specific files. _(commits `f7a993b4`, `3d50fd40`)_
- **🔴 ACTION:** the 3 historical `ghp_` PATs must be **revoked/rotated** — they sat in git history for months. (Working tree is already clean; it uses env-var auth now.)

### 9. `android-lint` — `UnsafeOptInUsageError`
- **Symptom:** 5 lint errors, all `UnsafeOptInUsageError` on `Camera2Interop.Extender` in `app/appsync/.../CameraPreview.kt`.
- **Root cause:** `ExperimentalCamera2Interop` is an **androidx** `@RequiresOptIn` marker — Kotlin's `@OptIn` does **not** satisfy the androidx lint check.
- **Fix:** `@Suppress("UnsafeOptInUsageError")` on `applyContinuousCenterFocus()` (the pattern already used in `YUV420888ToGrayscaleConverter`). _(commit `3d50fd40`)_

---

## Still open (decisions needed — not CI plumbing)

| Job | Issue | Options |
|-----|-------|---------|
| `android-test` | JaCoCo **line coverage 31% vs 80%** gate | write tests, lower the threshold, or accept failing |
| `swiftlint` | **1450** `--strict` violations across 1141 files | scoped lint cleanup (iOS lint was deprioritized) |
| — | **Rotate the 3 historical `ghp_` PATs** found in git history (§8) | GitHub action only |
| — | `ggBluetoothNativeLibrary` publish config still targets dead `dmdbrands/*` (§4) | fix in that repo |

## Gotchas worth remembering
- `Package.resolved` **must be committed** for reproducible CI + to use `-onlyUsePackageVersionsFromResolvedFile`.
- GitHub Packages does **not** follow org-rename redirects, and resolves **per-repo** — every artifact needs its own current-org repo URL.
- The Maven groupId `com.dmdbrands.lib` is just a coordinate string — unrelated to the GitHub org; don't "fix" it.
- A long, silent `xcodebuild` resolve is usually the **version solver** thrashing, not a slow clone — the committed lockfile + `-onlyUsePackageVersionsFromResolvedFile` is the cure, not a longer timeout.
