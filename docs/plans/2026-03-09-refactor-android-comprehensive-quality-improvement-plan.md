---
title: "Android Comprehensive Quality Improvement"
type: refactor
status: active
date: 2026-03-09
origin: docs/brainstorms/2026-03-09-android-comprehensive-audit-brainstorm.md
review: docs/plans/2026-03-09-refactor-android-review-findings.md
---

# Android Comprehensive Quality Improvement

## Overview

Transform the MeApp Android codebase from a well-architected but under-tooled project into a production-grade, observable, and comprehensively tested application. The existing MVI + Clean Architecture, component library (155 shared components), and coding patterns are strong — this plan addresses gaps in testing (0% coverage), observability, CI/CD, security, and code quality enforcement.

(see brainstorm: docs/brainstorms/2026-03-09-android-comprehensive-audit-brainstorm.md)

## Problem Statement

The Android codebase has 732+ Kotlin source files with strong architecture but critical infrastructure gaps:

1. **Security vulnerability**: A GitHub PAT is hardcoded in `settings.gradle.kts` across two private Maven repositories
2. **Zero observability**: No crash reporting, analytics disabled, no performance monitoring
3. **No CI/CD**: No automated builds, tests, or quality checks for Android (iOS-only CircleCI)
4. **Near-zero test coverage**: 2 placeholder test files for 732+ source files
5. **Linting effectively disabled**: Detekt with `buildUponDefaultConfig = false` and only 1 rule active
6. **No formatting enforcement**: EditorConfig references ktlint but ktlint is not installed
7. **No pre-commit hooks**: No automated quality gates before code reaches the repository
8. **Compose lifecycle gap**: `collectAsState()` used instead of `collectAsStateWithLifecycle()` — 88 instances across 43 files (including 3 in `:iam` module)
9. **Missing stability annotations**: State data classes lack `@Stable`, causing unnecessary recompositions
10. **Unencrypted token storage**: Auth tokens stored in plain Protobuf DataStore (not encrypted)
11. **PII leakage in logs**: Email addresses and passwords logged in plaintext across 5+ files
12. **Unmanaged CoroutineScope proliferation**: 27 manual `CoroutineScope(Dispatchers...)` constructions across 11 files; `AccountService` has a cancel-and-recreate race condition

## Proposed Solution

A phased approach organized by priority:

- **Phase 1 (P0)**: Critical security fixes + observability + CI pipeline
- **Phase 2 (P1 — Next Quarter)**: Testing foundation + code quality + Compose improvements + security hardening
- **Phase 3 (P2)**: Code cleanup + error handling + dead code + build hygiene
- **Phase 4 (P3)**: Firebase Performance + OWASP scanning + input validation + remaining optimizations

## Technical Approach

No architectural changes needed. The existing MVI + Clean Architecture is well-implemented and consistent. All improvements are additive (testing, tooling, quality gates) or targeted fixes (Compose lifecycle, security).

---

### Phase 1: Critical Fixes (P0) — Do Immediately

**Goal**: Eliminate security vulnerabilities, enable crash visibility, establish CI pipeline.

**Execution order** (strict): Task 1.1 → Task 1.2 → Task 1.3 (Token Encryption) → Task 1.4 (Crashlytics) → Task 1.5 (Analytics) → Task 1.6 (CI)

#### Task 1.1: Revoke and Externalize GitHub PAT

**Files to modify:**
- `Android/settings.gradle.kts` (lines 22-34)

> **CRITICAL**: `settings.gradle.kts` has **TWO** private Maven repositories (ggBluetoothNativeLibrary and vico) both using the hardcoded PAT `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`. Both must be updated. The username `Selva-GG` must also be externalized.

**Steps (SAFE ORDER — do not deviate):**

1. Generate a new token with minimum required scopes (`read:packages`)
2. Distribute setup instructions to the team **before** revoking the old token. Each developer adds to `~/.gradle/gradle.properties` (Windows: `%USERPROFILE%\.gradle\gradle.properties`):
   ```
   GITHUB_USERNAME=<your-github-username>
   GITHUB_TOKEN=<new-token>
   ```
3. Verify at least one developer can `./gradlew assembleDebug` successfully with the new token
4. Update `settings.gradle.kts` for **both** Maven blocks:

```kotlin
fun getGitHubProperty(key: String): String {
    val localProps = java.util.Properties()
    val localFile = file("${System.getProperty("user.home")}/.gradle/gradle.properties")
    if (localFile.exists()) localProps.load(localFile.inputStream())
    return localProps.getProperty(key)
        ?: System.getenv(key)
        ?: error("Missing $key in ~/.gradle/gradle.properties or environment")
}

// Apply to BOTH maven blocks:
maven {
    url = uri("https://maven.pkg.github.com/dmdbrands/ggBluetoothNativeLibrary")
    credentials {
        username = getGitHubProperty("GITHUB_USERNAME")
        password = getGitHubProperty("GITHUB_TOKEN")
    }
}
maven {
    url = uri("https://maven.pkg.github.com/dmdbrands/vico")
    credentials {
        username = getGitHubProperty("GITHUB_USERNAME")
        password = getGitHubProperty("GITHUB_TOKEN")
    }
}
```

5. Only **after** builds are verified: revoke the exposed token on GitHub
6. **Git history scrubbing** (CRITICAL): Removing the PAT from the current file does NOT remove it from git history. Push a `backup/pre-scrub` tag first. Then use `git filter-repo` or BFG Repo Cleaner to strip the token from all historical commits. Coordinate team re-clone after the rewrite.

**Team coordination**: Announce setup instructions to the team and schedule the history rewrite during a low-activity period. Both steps require advance notice.

**Acceptance criteria:**
- [ ] Old token revoked on GitHub (ONLY after new token verified working)
- [ ] `settings.gradle.kts` contains zero hardcoded credentials in both Maven blocks
- [ ] Git history cleaned (preferred) OR decision documented to rely on revocation only
- [ ] `./gradlew assembleDebug` succeeds with properties from `~/.gradle/gradle.properties`
- [ ] CircleCI environment variables `GITHUB_USERNAME` and `GITHUB_TOKEN` set and CI build verified

(see brainstorm: Resolved Question 1 — "Both" approach decided)

#### Task 1.2: Sanitize PII from Log Statements (PREREQUISITE for Task 1.4)

> **Prerequisite for Task 1.4 (Crashlytics)**: Enabling Crashlytics before removing PII will upload email addresses and passwords to Firebase — a GDPR/privacy violation.

**Files to modify:**
- `migration/service/MigrationService.kt` (lines 379, 445) — email in plaintext logs
- `migration/repository/MigrationRepository.kt` — `android.util.Log` direct usage
- `features/changePassword/viewmodel/ChangePasswordViewModel.kt` (line 132) — "Password reset requested for email: $email"
- `features/common/components/chart/GraphView.kt` (line 244) — `android.util.Log` direct usage
- `core/network/interceptors/AuthTokenInterceptor.kt` — account IDs in logs; `HttpLoggingInterceptor` at BODY level leaks auth tokens
- `core/network/TokenManager.kt` — account IDs at debug/verbose levels (lines 83, 96, 101, etc.)
- `shared/utilities/webview/IonicDatabaseHelper.kt`, `IonicDataConverter.kt`, `core/shared/utilities/logging/ILogRepository.kt` — `android.util.Log` direct usage
- Audit ALL `AppLog` calls for PII patterns

**Steps:**
1. Replace all `android.util.Log` usages with `AppLog` (5+ files confirmed)
2. Replace PII with redacted versions: `"email: ${email.take(3)}***"` or remove entirely
3. Change `HttpLoggingInterceptor` from `Level.BODY` to `Level.HEADERS` in `NetworkModule.kt` — BODY level logs full request/response bodies including `Authorization: Bearer <token>` headers on every API call
4. Add `AppLog.sanitize()` utility for consistent PII redaction:
   - Email: `us***@***.com`
   - Token: last 4 characters only
   - Account ID: first 8 characters of UUID (account IDs linked to health data are pseudonymous identifiers under GDPR)
5. Run a grep to verify zero PII patterns remain:
   ```bash
   grep -rn "email\|password\|token\|accessToken\|refreshToken" --include="*.kt" | grep -i "AppLog\|Log\."
   ```
6. Ensure no Crashlytics custom keys will contain PII (document this as a convention)

**Acceptance criteria:**
- [ ] Zero email addresses in log statements
- [ ] Zero passwords or tokens in log statements
- [ ] Zero direct `android.util.Log` usages (all converted to `AppLog`)
- [ ] `HttpLoggingInterceptor` changed from `BODY` to `HEADERS` level
- [ ] `AppLog` has a `sanitize()` utility for PII redaction
- [ ] Grep audit passes with zero PII matches

#### Task 1.3: Encrypt DataStore Token Storage

> **Priority**: Ordered BEFORE Crashlytics (Task 1.4). If Crashlytics is enabled while tokens are in plain DataStore and a crash occurs during token read, plaintext tokens could appear in crash context.

**Files to modify:**
- `Android/gradle/libs.versions.toml`
- `Android/app/build.gradle.kts`
- `data/storage/datastore/UserDataStore.kt`
- `Android/app/proguard-rules.pro`

> **Library choice**: Use stable `androidx.security:security-crypto:1.0.0` — NOT `1.1.0-alpha06`. The alpha adds DataStore encryption support but is inappropriate for a security-critical production feature.

**Steps:**
1. Add dependency: `androidx.security:security-crypto:1.0.0`
2. Create `EncryptedTokenStorage` using `EncryptedSharedPreferences` for token-specific fields: `accessToken`, `refreshToken`, `expiresAt`
3. Keep non-sensitive preferences in standard DataStore (theme, display settings)
4. Migration path — use read→verify→delete to prevent data loss:
   - Read tokens from old plain DataStore
   - Write to encrypted storage and **verify the write is readable** before proceeding
   - Only then delete from old storage
5. Handle failure scenarios gracefully:
   - `MasterKey` creation failure (known on some Samsung devices after OS updates, or on devices restored from backup to different hardware): force graceful re-login, log error to Crashlytics as non-fatal
   - Key invalidation (user changes device lock screen → `GeneralSecurityException` on next read): catch, force graceful re-login
6. Add ProGuard rules (REQUIRED — Tink classes must not be obfuscated; release builds have `isMinifyEnabled = true`):

```proguard
# Required for EncryptedSharedPreferences / security-crypto (Tink)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
```

**Acceptance criteria:**
- [ ] Auth tokens (`accessToken`, `refreshToken`) stored in `EncryptedSharedPreferences`
- [ ] Non-sensitive preferences remain in standard DataStore
- [ ] Migration works without re-login (read→write→verify→delete pattern)
- [ ] `MasterKey` failure forces graceful re-login (no crash)
- [ ] Key invalidation on lock screen change forces graceful re-login
- [ ] ProGuard rules for Tink added to `proguard-rules.pro`
- [ ] Release build does not crash on first encrypted storage access (verify in release variant)
- [ ] `user_preferences.pb` does not contain plaintext tokens (verifiable via `adb shell` in instrumented test)

#### Task 1.4: Integrate Firebase Crashlytics

> **Prerequisites**: Task 1.2 (PII sanitization) AND Task 1.3 (Token encryption) MUST complete first.

> **CI prerequisite**: `Android/app/google-services.json` must be accessible in CI. If not committed, add `GOOGLE_SERVICES_JSON_BASE64` as a CircleCI environment variable (same pattern as iOS `GOOGLESERVICE_INFO_BASE64`). If committed, verify Firebase security rules on the Firebase Console are restrictive.

**Files to modify:**
- `Android/build.gradle.kts` — Crashlytics Gradle plugin
- `Android/app/build.gradle.kts` — Crashlytics dependency
- `Android/gradle/libs.versions.toml`
- `core/initialization/AppInitializer.kt`
- `core/shared/utilities/AppLog.kt`

**Steps:**
1. Add to `libs.versions.toml`:
   ```toml
   firebase-crashlytics-plugin = "3.0.3"
   firebase-crashlytics = { module = "com.google.firebase:firebase-crashlytics-ktx" }
   ```
2. Apply Crashlytics Gradle plugin in root `build.gradle.kts`
3. Add `firebase-crashlytics` dependency in app `build.gradle.kts`
4. Initialize in `AppInitializer.kt`; disable in debug: `setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)`
5. Wire `AppLog.e()` to call `FirebaseCrashlytics.getInstance().recordException()`
6. Add ProGuard/R8 mapping upload for release builds

**Acceptance criteria:**
- [ ] Crashlytics appears in Firebase Console after a test crash
- [ ] `AppLog.e()` automatically reports exceptions to Crashlytics
- [ ] Release builds upload R8 mapping files
- [ ] Crashlytics disabled in debug builds
- [ ] CI can build successfully (google-services.json accessible)

#### Task 1.5: Verify Firebase Analytics + Add Custom Events

> **Note**: `firebase-analytics` already exists in `build.gradle.kts` (line 163). Basic analytics may already be active. This task is verification, not a new integration.

**Steps:**
1. Verify analytics data is flowing in Firebase Console
2. Add custom event tracking for key user actions: `login`, `entry_created`, `scale_connected`, `account_switched`
3. Create `IAnalyticsService` + `FirebaseAnalyticsService` and wire through Hilt

**Acceptance criteria:**
- [ ] Analytics events visible in Firebase Console
- [ ] Custom events tracked for key user actions

#### Task 1.6: Set Up Android CI Pipeline on CircleCI

> **Prerequisites**: Task 1.1 (PAT externalization) MUST complete first. Fix `ExampleUnitTest.kt` (Task 2.2) BEFORE adding the unit test CI job — it will fail CI immediately on the first run.

**Files to modify:**
- `.circleci/config.yml`
- `Android/gradle.properties` — add `org.gradle.caching=true`

**Steps:**
1. Add `org.gradle.caching=true` to `Android/gradle.properties` (40–60% build time saving on incremental builds)
2. Add Android jobs to `.circleci/config.yml`:

```yaml
orbs:
  android: circleci/android@2.5.0

android-build:
  docker:
    - image: cimg/android:2024.12
  resource_class: large
  environment:
    GRADLE_OPTS: "-Xmx4096m -Dfile.encoding=UTF-8"
  steps:
    - checkout
    - restore_cache:
        keys:
          - gradle-v3-{{ checksum "Android/gradle/wrapper/gradle-wrapper.properties" }}-{{ checksum "Android/gradle/libs.versions.toml" }}-{{ checksum "Android/app/build.gradle.kts" }}
    - run:
        name: Build Debug APK
        working_directory: Android
        command: ./gradlew assembleDebug --stacktrace
    - save_cache:
        paths:
          - ~/.gradle/wrapper
          - ~/.gradle/caches
          - ~/.gradle/build-cache
        key: gradle-v3-{{ checksum "Android/gradle/wrapper/gradle-wrapper.properties" }}-{{ checksum "Android/gradle/libs.versions.toml" }}-{{ checksum "Android/app/build.gradle.kts" }}
    - store_artifacts:
        path: Android/app/build/outputs/apk/debug/
```

> Cache key includes `build.gradle.kts` to detect plugin version changes. Build cache (`~/.gradle/build-cache`) is added to cached paths.

3. Add path filtering to prevent Android builds from triggering on iOS-only changes:
   ```yaml
   # Option A: CircleCI workflow path filters (setup: true + dynamic config)
   # Option B: workflow filters on branch patterns
   # iOS changes → iOS jobs only; Android changes → Android jobs only
   ```

4. Add environment variables: `GITHUB_USERNAME`, `GITHUB_TOKEN`, `GOOGLE_SERVICES_JSON_BASE64` (if not committed)

**Acceptance criteria:**
- [ ] Android build runs automatically on every push to `dev` and `main`
- [ ] `org.gradle.caching=true` in `gradle.properties`
- [ ] Gradle build cache included in CI cache paths
- [ ] Cache key includes `build.gradle.kts` to invalidate on plugin changes
- [ ] Path filtering configured (Android changes don't trigger iOS builds)
- [ ] Private Maven repos resolve via environment variables

---

### Phase 2: Quality Foundation (P1) — Next Quarter

**Goal**: Testing infrastructure, code quality enforcement, Compose improvements, security hardening.

> **Realistic timeline**: 1 developer → 10–12 weeks; 2 developers → 6–8 weeks. This is a quarter's worth of work, not a sprint. Split into sub-phases with checkpoint reviews.

#### Phase 2a: Infrastructure (Weeks 1–3)

##### Task 2.1: Add Test Dependencies

**Files to modify:**
- `Android/gradle/libs.versions.toml`
- `Android/app/build.gradle.kts`

| Library | Version | Scope | Purpose |
|---------|---------|-------|---------|
| MockK | 1.13.15 | testImplementation | Kotlin-first mocking |
| MockK Android | 1.13.15 | androidTestImplementation | Mocking on device/emulator |
| Turbine | 1.1.0 | testImplementation | Flow/StateFlow assertions |
| Robolectric | 4.14 | testImplementation | Android framework in JVM tests |
| kotlinx-coroutines-test | 1.10.1 | testImplementation | `runTest`, `TestDispatcher` |
| Room testing | 2.7.2 | testImplementation | In-memory database, migration tests |
| Hilt testing | 2.56.2 | androidTestImplementation | DI in instrumented tests |
| Kover | 0.9.1 | plugin | Kotlin-accurate coverage |
| lifecycle-runtime-compose | 2.9.1 | **implementation** | `collectAsStateWithLifecycle()` support |
| kotlinx-collections-immutable | 0.3.8 | **implementation** | `ImmutableList` for Compose stability |

> **Important**: `lifecycle-runtime-compose` is NOT included in the Compose BOM. Add it as an explicit `implementation` dependency, version-pinned to match `lifecycleRuntimeKtx = "2.9.1"` already in `libs.versions.toml`.

> **Important**: Use `mockk` (not `mockk-android`) for `testImplementation`. Use Kover over JaCoCo — Kover understands Kotlin semantics and avoids false negatives on `when` branches and coroutine state machines.

**Kover configuration:**
```kotlin
kover {
    reports {
        filters {
            excludes {
                classes("*_Factory", "*_HiltModules*", "*_Impl", "*BuildConfig",
                        "*Dao_Impl", "*_MembersInjector", "*ComposableSingletons*")
                packages("hilt_aggregated_deps", "dagger.hilt*")
                annotatedBy("androidx.compose.ui.tooling.preview.Preview",
                            "dagger.Module", "dagger.internal.DaggerGenerated")
            }
        }
    }
}
```

Also configure: `testOptions.unitTests.isIncludeAndroidResources = true` (Robolectric); `testOptions.unitTests.isReturnDefaultValues = true`. Kover multi-module aggregation: `kover { merge { allProjects() } }` in root `build.gradle.kts`.

**Acceptance criteria:**
- [ ] All dependencies resolve
- [ ] `./gradlew test` runs without dependency errors
- [ ] Kover generates HTML coverage report at `build/reports/kover/`
- [ ] `lifecycle-runtime-compose` and `kotlinx-collections-immutable` available for Phase 2c

##### Task 2.2: Create Test Infrastructure and Shared Fixtures

**Files to create:**
- `src/test/.../testutil/MainDispatcherRule.kt`
- `src/test/.../testutil/TestFixtures.kt`
- `src/test/.../testutil/ViewModelTestExtensions.kt`

```kotlin
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) { Dispatchers.setMain(testDispatcher) }
    override fun finished(description: Description) { Dispatchers.resetMain() }
}

// BaseViewModel uses @Inject lateinit var for 3 dependencies.
// Use reflection to inject mocks in unit tests without Hilt.
fun <T : BaseViewModel> T.initTestDependencies(
    navigationService: IAppNavigationService = mockk(relaxed = true),
    dialogQueueService: IDialogQueueService = mockk(relaxed = true),
    customTabManager: ICustomTabManager = mockk(relaxed = true),
): T {
    val baseClass = BaseViewModel::class.java
    listOf("navigationService" to navigationService,
           "dialogQueueService" to dialogQueueService,
           "customTabManager" to customTabManager).forEach { (name, mock) ->
        baseClass.getDeclaredField(name).apply { isAccessible = true; set(this@initTestDependencies, mock) }
    }
    return this
}
```

> **Tech debt note**: `BaseViewModel` field injection (`@Inject lateinit var`) makes every ViewModel test require the reflection helper. Long-term fix: refactor to constructor injection (see Future Considerations).

**Acceptance criteria:**
- [ ] `MainDispatcherRule` usable via `@get:Rule` in all ViewModel tests
- [ ] `initTestDependencies()` resolves BaseViewModel field injection for all VMs
- [ ] TestFixtures covers Account, Entry, Device, ScaleEntry, BpmEntry entities
- [ ] **`ExampleUnitTest.kt` deleted** — it contains `MainBottomNavTest` which uses `createComposeRule()` (an instrumented test API) in a unit test file. It will fail the first CI unit test run. This deletion must happen BEFORE Task 1.6 adds the unit test CI job.

##### Task 2.7: Enable Detekt Default Rules + detekt-compose-rules + Spotless

**Files to modify:**
- `Android/build.gradle.kts`
- `Android/config/detekt/detekt.yml`
- `Android/gradle/libs.versions.toml`

> **Step order is critical**: Run Spotless reformatting on existing code BEFORE generating the Detekt baseline. If the baseline is generated first, Spotless reformatting shifts line numbers and reactivates previously-baselined violations.

**Correct order within this task:**
1. Add Spotless plugin and run `./gradlew spotlessApply` on all existing code
2. Enable `buildUponDefaultConfig = true` in Detekt config
3. Run `./gradlew detektBaseline` to baseline all existing violations
4. Commit both the reformatted files and the baseline file
5. Set `ignoreFailures = false` — CI enforces rules on new code only
6. Incrementally reduce baseline by fixing violations category-by-category

> **Scope note**: `:bleWrapper` is currently excluded from Detekt analysis (`build.gradle.kts` lines 23-24). Evaluate whether to include it. Preserve this exclusion when generating the baseline — do not include `:bleWrapper` violations in the baseline file.

**Add detekt-compose-rules:**
```kotlin
dependencies {
    detektPlugins("io.nlopez.compose.rules:detekt:0.4.22")
}
```

**Spotless with ratcheting:**
```kotlin
spotless {
    ratchetFrom("origin/dev")  // Only enforce on files changed since dev
    kotlin {
        target("Android/**/*.kt")
        targetExclude("**/build/**")
        ktlint("1.5.0")
    }
    kotlinGradle {
        target("Android/**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint("1.5.0")
    }
}
```

> **Note**: `ratchetFrom("origin/dev")` requires `origin/dev` to be a reachable ref. Shallow clones may need `git fetch origin dev` first.

**Acceptance criteria:**
- [ ] Spotless applied to existing code FIRST (before Detekt baseline)
- [ ] Detekt baseline generated AFTER Spotless reformatting
- [ ] `./gradlew detekt` enforces rules on new/changed code only
- [ ] `:bleWrapper` exclusion preserved in baseline generation
- [ ] detekt-compose-rules installed and active
- [ ] `./gradlew spotlessCheck` validates formatting (ratcheted from `origin/dev`)
- [ ] CI runs both checks and fails on new violations

##### Task 2.8: Add Pre-commit and Pre-push Hooks via Lefthook

**Files to create:**
- `lefthook.yml` (project root)

**Installation:**
```bash
# macOS/Linux
brew install lefthook

# Windows
scoop install lefthook  # or: choco install lefthook

# All platforms via npm
npm install -g @evilmartians/lefthook

# After installation (all platforms)
lefthook install
```

> **Windows note**: `{staged_files}` in Lefthook uses Unix paths. In Git Bash on Windows, verify these paths resolve correctly. `./gradlew` works in Git Bash with forward slashes.

**lefthook.yml:**
```yaml
pre-commit:
  parallel: true
  commands:
    spotless:
      root: "Android/"
      glob: "Android/**/*.{kt,kts}"
      run: ./gradlew spotlessCheck -PspotlessChangedFiles="{staged_files}"
    detekt-cli:
      root: "Android/"
      glob: "Android/**/*.kt"
      # Use Detekt CLI binary on staged files — NOT ./gradlew detekt
      # ./gradlew detekt runs on all 732+ files (~20-60s), violating the <10s target
      # Download Detekt CLI: https://github.com/detekt/detekt/releases
      run: detekt-cli --input {staged_files} --config config/detekt/detekt.yml --baseline config/detekt/detekt-baseline.xml

pre-push:
  commands:
    test:
      root: "Android/"
      run: ./gradlew test --fail-fast
```

> **Auto-install**: Consider a Gradle task to auto-run `lefthook install` on first sync, so adoption is not voluntary.

**Acceptance criteria:**
- [ ] `lefthook install` sets up both hooks
- [ ] Pre-commit runs Spotless on staged files only (< 10 seconds)
- [ ] Pre-commit runs Detekt CLI on staged files only (< 10 seconds)
- [ ] Pre-push blocks on test failures
- [ ] Windows installation documented
- [ ] Hook installation documented in `CLAUDE.md`

---

#### Phase 2b: Test Coverage (Weeks 4–9)

> **Priority order**: Write tests for highest-risk code first. If time runs out, the most critical paths must be covered.

##### Task 2.3: Write Reducer Unit Tests (Highest ROI)

**~30 reducer classes** across all features. Reducers are pure functions — no mocking needed, highest ROI per hour spent.

**Priority order** (highest risk first):
1. `LoginReducer` — authentication core
2. `SignupReducer` — user onboarding
3. `DashboardReducer` — primary user screen
4. `EntryReducer` / `ManualEntryReducer` — core data entry
5. `AccountReducer` — account management
6. `SettingsReducer`
7. Remaining reducers alphabetically

**Pattern:**
```kotlin
class LoginReducerTest {
    private val reducer = LoginReducer()
    private val initialState = LoginState()

    @Test
    fun `UpdateForm intent updates email and password`() {
        val intent = LoginIntent.UpdateForm(email = "test@example.com", password = "pass123")
        val newState = reducer.reduce(initialState, intent)
        assertNotNull(newState)
        assertEquals("test@example.com", newState!!.form.email)
    }
}
```

**Acceptance criteria:**
- [ ] Every reducer has a test file
- [ ] Each intent type has at least one test case
- [ ] `./gradlew test --tests "*ReducerTest*"` passes
- [ ] Coverage >= 80% on reducer classes

##### Task 2.4: Write Repository Unit Tests

**~15 repositories**. Priority order (highest risk first):
1. `AccountRepository` (911 lines — highest complexity)
2. `EntryRepository`
3. `DashboardRepository`
4. `DeviceRepository`
5. Remaining repositories alphabetically

**Pattern:** MockK for API and DAO dependencies. Test mapping and error handling.

**Acceptance criteria:**
- [ ] Every repository has a test file
- [ ] Happy path and error path tested for key methods
- [ ] `./gradlew test --tests "*RepositoryTest*"` passes

##### Task 2.5: Write ViewModel Unit Tests

**10 most critical ViewModels:**
1. `LoginViewModel`
2. `DashboardViewModel`
3. `EntryViewModel` / `ManualEntryViewModel`
4. `SignupViewModel`
5. `SettingsViewModel`
6. `AccountViewModel`
7. `HomeViewModel`
8. Plus 3 others by risk

**Pattern:** MockK for services + Turbine for Flow verification + `MainDispatcherRule` + `initTestDependencies()`.

**Acceptance criteria:**
- [ ] 10 critical ViewModels have test files
- [ ] State transitions verified with Turbine
- [ ] Navigation and API calls verified with `coVerify`

##### Task 2.6: Write DAO Instrumented Tests

**4 DAOs**: `AccountDao`, `EntryDao`, `DeviceDao`, `LogDao` — all in `src/androidTest/`.

> **CI note**: Docker-based `cimg/android` does not support Android emulators (no KVM). DAO instrumented tests must use a machine executor with KVM, Firebase Test Lab, or be converted to Robolectric-based Room tests. Decide and document this before implementing.

**Acceptance criteria:**
- [ ] All 4 DAOs have tests
- [ ] CRUD and Flow queries verified
- [ ] CI strategy for instrumented tests documented and implemented

---

#### Phase 2c: Compose, Security, Performance (Weeks 10–14)

##### Task 2.9: Migrate collectAsState() to collectAsStateWithLifecycle()

**88 instances across 43 files** (including 3 in `:iam` module: `IamFeedLandingScreen.kt`, `FeedMessagesSettingsScreen.kt`, `FeedMessagesScreen.kt` — package `com.greatergoods.ggInAppMessaging`).

> **Scope decision required**: A search scoped to `com.dmdbrands.gurus.weight` will miss the 3 `:iam` files. Explicitly include or defer `:iam` and document the decision.

> **Behavioral audit before migrating**: `collectAsStateWithLifecycle()` stops collecting when the lifecycle goes below STARTED. Audit whether any screen depends on continued collection during `onStop` (e.g., background sync status updates). If so, handle those screens separately.

**Replace:** `.collectAsState()` → `.collectAsStateWithLifecycle()`
**Add import:** `import androidx.lifecycle.compose.collectAsStateWithLifecycle`
**Prerequisite:** `lifecycle-runtime-compose:2.9.1` added as `implementation` in Task 2.1

**Acceptance criteria:**
- [ ] Zero occurrences of `collectAsState()` for ViewModel state collection (in scoped modules)
- [ ] Scope decision for `:iam` module documented
- [ ] App compiles and runs correctly after migration

##### Task 2.10: @Stable Annotations + Immutable Collections + LazyColumn Keys

Three related Compose optimization tasks bundled together.

**Part A: @Stable annotations on ~30 state data classes**

Use `@Stable` NOT `@Immutable`. `@Stable` tells the compiler "equals() is reliable for skip decisions" — always true for data classes. `@Immutable` requires all properties to be deeply immutable; if any state has `List<>`, `Map<>`, or mutable references, `@Immutable` is incorrect and causes skipped recompositions when data has changed.

**Part B: kotlinx.collections.immutable for List-heavy state classes**

`@Stable` alone does NOT fix `List<T>` instability. Kotlin's `List<T>` interface is treated as **unstable** by the Compose compiler regardless of `@Stable` on the containing class — child composables with `List<>` parameters will still be flagged unstable in compiler reports.

For hottest composables (Dashboard, History, Metrics):
```kotlin
@Stable
data class DashboardState(
    val metrics: ImmutableList<DashboardMetric> = persistentListOf(),
    val milestones: ImmutableList<DashboardMilestone> = persistentListOf(),
)
```

Alternatively, add a Compose compiler stability configuration file declaring `kotlin.collections.List` as stable app-wide.

**Part C: LazyColumn/LazyRow stable key parameters**

13 of 17 lazy layout usages lack `key` parameters — causing full recomposition on any list change. Affected hot paths include `HistoryList.kt`, `DashboardMilestoneGrid`, and `DashboardMetrics`.

```kotlin
// Before:
LazyColumn { items(entries) { entry -> EntryRow(entry = entry) } }
// After:
LazyColumn { items(entries, key = { entry -> entry.id }) { entry -> EntryRow(entry = entry) } }
```

**Generate Compose compiler reports** to measure before/after:
```kotlin
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}
```

**Acceptance criteria:**
- [ ] All ~30 state data classes annotated with `@Stable`
- [ ] `ImmutableList`/`PersistentList` used in `DashboardState`, `HistoryState`, and other list-heavy state classes
- [ ] All `LazyColumn`/`LazyRow`/`LazyVerticalGrid` have stable `key` parameters
- [ ] Compose compiler stability report shows measurable improvement (unstable parameter count decreases by ≥30 from baseline)
- [ ] No runtime errors after changes

##### Task 2.11: Add Staging Build Type

> **STATUS: BLOCKED** — Waiting on staging API URL from backend team. Create the configuration as a template; do not merge until URL is available.

```kotlin
create("staging") {
    initWith(getByName("debug"))
    buildConfigField("String", "BASE_URL", "\"<STAGING_API_URL>\"")  // BLOCKED
    applicationIdSuffix = ".staging"
    versionNameSuffix = "-staging"
}
```

> **Elevated risk**: Debug builds use `network_security_config.xml` which trusts user-installed CAs AND hits the production API. Debug builds can be MITM'd against production data. Obtaining a staging URL should be treated as a higher priority than this task's P1 label suggests — escalate to backend team.

**Acceptance criteria:**
- [ ] Staging API URL obtained from backend team (prerequisite)
- [ ] `./gradlew assembleStaging` produces staging APK connected to staging API
- [ ] Debug and staging builds never hit production API

(see brainstorm: Open Question 2)

##### Task 2.12: Audit and Remediate Unmanaged CoroutineScope

> **Prerequisite**: Complete Tasks 2.3 (Reducer tests) and 2.5 (ViewModel tests) at least partially before remediating services — scope changes alter async timing and require tests for regression detection.

**27 occurrences across 11 files** (the earlier "80+" count included all `launch {}` calls; this task addresses only `CoroutineScope(Dispatchers...)` construction).

**Key offenders:**
- `GoalService.kt`: 7 fire-and-forget instances
- `HealthConnectService.kt`: 10+ instances
- `AccountService.kt`: cancel-and-recreate race condition (see below)
- `HomeViewModel.kt` (line 129): manual `CoroutineScope(Dispatchers.IO).launch`
- `EntryService`, `EntryAggregationService`: non-Hilt-managed classes with manual scopes

**Remediation patterns:**

In ViewModels: Replace with `viewModelScope.launch(Dispatchers.IO)`.

In Hilt-managed singleton services: Inject a qualified `CoroutineScope`:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object ServiceScopeModule {
    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
```

> Note: The existing `LoggingModule.kt` pattern (singleton scope, no cancellation) is intentionally correct for singleton services that live for the process lifetime.

In non-Hilt-managed classes (`EntryService`, `EntryAggregationService`): Inject scope via constructor parameter or a `@ServiceScope`-qualified provider.

**AccountService race condition** (lines 95–96): Cancels then immediately recreates a `CoroutineScope`. Any coroutine launched between cancel and recreate is silently dropped — a data loss bug, not a style issue. Fix: use a single `SupervisorJob` with child jobs; cancel individual child jobs rather than recreating the scope.

**Additional fixes in this task:**

- **SimpleDateFormat thread safety** (`AuthTokenInterceptor` line 44, `TokenAuthenticator` line 47): Both use class-level `SimpleDateFormat`, which is not thread-safe. OkHttp interceptors run on multiple threads simultaneously. This can produce incorrect token expiration calculations. Replace with `java.time.format.DateTimeFormatter` (thread-safe; available from API 26 = minSdk).

- **EntryAggregationService combine performance** (line 126): `accountRepository.getActiveAccount().first()` is called inside a `combine` block, firing a Room query on every emission of 7+ combined flows. Lift this as an upstream source to `combine`.

**Add LeakCanary** alongside this task (moved from Phase 4 — unmanaged scopes are a present-tense leak risk):
```kotlin
debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
```

**Acceptance criteria:**
- [ ] Zero manual `CoroutineScope()` construction in ViewModels
- [ ] All service scopes injected via Hilt with appropriate lifecycle
- [ ] `AccountService` cancel-and-recreate replaced with `SupervisorJob` + child jobs
- [ ] `SimpleDateFormat` in `AuthTokenInterceptor` and `TokenAuthenticator` replaced with `DateTimeFormatter`
- [ ] `EntryAggregationService.getActiveAccount().first()` lifted out of `combine` block
- [ ] LeakCanary added as `debugImplementation`
- [ ] No memory leaks reported by LeakCanary for account switching flows

##### Task 2.13: Add Certificate Pinning

> **Pin intermediate CA, not leaf certificates**. Leaf certificates rotate annually, requiring an app update on every rotation. Intermediate CAs rotate every 5–10 years.

**Files to create/modify:**
- `Android/app/src/main/res/xml/network_security_config.xml`
- `Android/app/src/main/AndroidManifest.xml`

```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.weightgurus.com</domain>
        <pin-set expiration="2027-01-01">
            <!-- Primary: SHA-256 of intermediate CA certificate (not leaf) -->
            <pin digest="SHA-256">AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=</pin>
            <!-- Backup: SHA-256 of a DIFFERENT intermediate CA or root CA -->
            <pin digest="SHA-256">BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

**Steps:**
1. Extract intermediate CA SHA-256 pins from `api.weightgurus.com` using `openssl s_client`
2. Confirm the debug `network_security_config.xml` does NOT include the pin-set (developers need proxy access via Charles / mitmproxy)
3. On pin mismatch: show a user-friendly error (not generic "network error"); report to Crashlytics as a security event
4. Set up a calendar reminder at 90 days before expiration (2026-10-01 for 2027-01-01 expiry); assign to a named owner
5. Coordinate with backend/infrastructure team on TLS certificate renewal schedule for `api.weightgurus.com`

**Acceptance criteria:**
- [ ] Certificate pinning active for production API domain
- [ ] Pins target intermediate CA (not leaf certificates)
- [ ] Two pins included (primary + backup from different CA)
- [ ] Debug build does NOT include pin-set (proxy works in debug builds)
- [ ] Pin mismatch shows user-friendly error + Crashlytics report
- [ ] Expiration date set; rotation reminder scheduled with named owner
- [ ] Pin rotation process documented with infrastructure team contact

##### Task 2.14: Add Baseline Profiles

> **Macrobenchmark is the recommended approach**. Hand-written wildcard patterns (`HSPLcom/dmdbrands/gurus/weight/**->**()`) cover everything indiscriminately and become stale. Generate profiles from actual startup traces.

**Files to create:**
- `Android/app/src/main/baseline-prof.txt`
- Macrobenchmark module: `Android/macrobenchmark/`

**Steps:**
1. Add `androidx.profileinstaller:profileinstaller:1.4.0+` dependency (required for `compileSdk = 36` + current Compose BOM)
2. Create Macrobenchmark module:
   ```kotlin
   @RunWith(AndroidJUnit4::class)
   class StartupBenchmark {
       @get:Rule val benchmarkRule = MacrobenchmarkRule()

       @Test
       fun startup() = benchmarkRule.measureRepeated(
           packageName = "com.dmdbrands.gurus.weight",
           metrics = listOf(StartupTimingMetric()),
           iterations = 5,
           startupMode = StartupMode.COLD,
           setupBlock = { pressHome() },
           measureBlock = { startActivityAndWait() }
       )
   }
   ```
3. Measure cold start before profile (5-run average via `adb shell am start-activity -W`)
4. Generate profile from Macrobenchmark output
5. Measure cold start after and document improvement

**Acceptance criteria:**
- [ ] `profileinstaller:1.4.0+` dependency added
- [ ] Macrobenchmark module created with startup test
- [ ] Baseline profile generated from actual traces (not hand-written wildcards)
- [ ] Cold start improvement documented (target: ≥15%)

##### Task 2.15: Update CI Pipeline with Quality Gates + Firebase Performance

**Files to modify:**
- `.circleci/config.yml`

**Add to pipeline:**
- `android-unit-test` job: `./gradlew test` with JUnit result storage
- `android-lint-quality` job: `./gradlew detekt spotlessCheck`
- `android-coverage` job (SEPARATE — coverage instrumentation adds 30–50% to build time): `./gradlew koverHtmlReport koverXmlReport`

**Add Firebase Performance Monitoring** (already in Firebase BOM, minimal incremental cost):
```toml
# libs.versions.toml
firebase-perf = { module = "com.google.firebase:firebase-perf-ktx" }
firebase-perf-plugin = "1.4.2"
```

Firebase Performance provides automatic Retrofit call latency, cold/warm/hot start timing, and slow frame detection — exactly what validates the Baseline Profiles, @Stable, and lifecycle collection improvements.

**Acceptance criteria:**
- [ ] CI runs unit tests and fails on failures
- [ ] CI runs Detekt + Spotless and fails on violations
- [ ] Coverage report generated on separate job and stored as artifact
- [ ] Coverage ratchet: coverage cannot decrease between PRs
- [ ] Firebase Performance Monitoring initialized in `AppInitializer.kt`

##### Task 2.16: WebView URL Validation (NEW — Security)

**Files to modify:**
- `core/shared/utilities/webview/InAppWebViewActivity.kt`
- `core/shared/utilities/webview/WebViewScreen.kt`

`InAppWebViewActivity` loads URLs from `intent.getStringExtra(EXTRA_URL)` without validation. `WebViewScreen.kt` enables JavaScript (`javaScriptEnabled = true`). Although the Activity is `android:exported="false"`, any internal code path that passes an attacker-influenced URL enables JS execution.

**Steps:**
1. Add URL validation in `InAppWebViewActivity`:
   ```kotlin
   private fun isValidUrl(url: String?): Boolean {
       if (url == null) return false
       val uri = Uri.parse(url)
       if (uri.scheme != "https") return false
       val allowedHosts = setOf("weightgurus.com", "www.weightgurus.com")
       return allowedHosts.any { uri.host?.endsWith(it) == true }
   }
   ```
2. Reject non-HTTPS URLs and non-allowlisted domains before loading
3. Log rejected URLs to Crashlytics as security events

**Acceptance criteria:**
- [ ] All URLs validated against HTTPS scheme before WebView load
- [ ] Domain allowlist enforced
- [ ] Invalid URL attempts logged to Crashlytics

---

### Phase 3: Code Cleanup (P2) — Following Quarter

**Goal**: Remove dead code, fix error handling, resolve TODO debt, address build hygiene.

##### Task 3.1: Remove Dead Code

- `DashboardService.kt` — remove `getVisibleMetricKeys()`, `getVisibleMilestoneKeys()`, `resetVisibleMetricKeys()`, `resetVisibleMilestoneKeys()` and corresponding interface methods
- `EntryRepository.kt` — remove commented-out `deleteAllEntriesForAccount()` (line 121)
- `AppInitializer.kt` — remove commented-out analytics init code

**Acceptance criteria:**
- [ ] Zero methods marked `// TODO: no use` or `// TODO: Not in use`
- [ ] Zero commented-out code blocks in production source
- [ ] App compiles and tests pass after removal

##### Task 3.2: Fix Compose Issues

- `AppInput.kt` (line 258) — remove unused `remember { MutableInteractionSource() }`
- `DashboardScreen.kt` (lines 59, 88) — remove two unused `rememberCoroutineScope()` calls (creates scopes tied to composition, never used)
- `HomeViewModel.kt` (line 128) — replace `CoroutineScope(Dispatchers.IO).launch` with `viewModelScope.launch(Dispatchers.IO)` (if not already done in Task 2.12)

**Acceptance criteria:**
- [ ] No unused `remember` results
- [ ] Zero unused `rememberCoroutineScope()` calls
- [ ] Zero manual `CoroutineScope` in composables

##### Task 3.3: Complete ResponseInterceptor Error Handling

> **Prerequisite**: `ResponseInterceptor` is provided by Hilt but **never added to the OkHttpClient interceptor chain** in `NetworkModule.kt` (lines 149–174). The chain adds `networkInterceptor`, `baseUrlInterceptor`, `authTokenInterceptor`, and `tokenAuthenticator` — but NOT `ResponseInterceptor`. It is dead code today. Wire it into the chain FIRST before implementing the TODO handlers.

**Steps:**
1. Add `ResponseInterceptor` to `provideOkHttpClient()` in `NetworkModule.kt`
2. Implement handlers for HTTP 403, 400, 500 in `ResponseInterceptor.kt` (lines 32–44)
3. Write unit tests for each error code path

**Acceptance criteria:**
- [ ] `ResponseInterceptor` added to OkHttpClient interceptor chain
- [ ] All HTTP error codes have proper handlers
- [ ] Zero TODO comments in `ResponseInterceptor`
- [ ] Unit tests verify each error code path

##### ~~Task 3.4: Create Sealed Result\<T\> Wrapper~~ → DEFERRED (Standalone Initiative)

Result<T> has the widest blast radius of any item in this plan (15 repository interfaces, 15 implementations, 25+ services, ~30 ViewModels). Extract into a standalone initiative AFTER Phase 2 testing is solid. Requires a dedicated design document; `AccountRepository` (911 lines, 7+ concerns) must be decomposed first.

##### Task 3.4: Resolve TODO Comments

Audit all 26+ TODOs. For each: implement, create a Jira ticket (`// TODO(MA-XXXX): ...`), or remove.

Specific items:
- `EntryHelper.kt`: Replace `accountId = "TODO"` with actual values
- `AppStatusService.kt`: Replace `isMetric = false` with user preferences
- `ScaleDetailsViewModel.kt`: Create ticket or remove download TODO

**Acceptance criteria:**
- [ ] Zero unlinked TODO comments
- [ ] All remaining TODOs reference a Jira ticket
- [ ] Placeholder `"TODO"` strings replaced

##### Task 3.5: Enable Room Schema Export

Change `exportSchema = false` → `exportSchema = true` in `AppDatabase.kt`. Configure schema output directory (`room { schemaDirectory("$projectDir/schemas") }`). Commit the baseline schema JSON. No schema changes are pending — this is infrastructure for future migration testing.

**Acceptance criteria:**
- [ ] `exportSchema = true` in AppDatabase
- [ ] Schema JSON generated and committed to version control

##### Task 3.6: Convert Raster Images to WebP

Convert 36 PNG/JPG files in `res/drawable*/` to WebP (quality=80). Reduces APK size ~25% for image assets.

##### Task 3.7: Clean Up Dependencies and Build Files

1. Run `./gradlew spotlessApply` across entire codebase
2. Fix wildcard import in `LogDao.kt`
3. Remove duplicate dependencies in `build.gradle.kts`:
   - `datastore`, `preferences-core`, `gson` declared twice (lines 153–155 and 166–168) and `datastore` appears a third time at line 172
4. Consolidate duplicate version entries in `libs.versions.toml`:
   - `workRuntimeKtx = "2.10.2"` and `workRuntimeKtxVersion = "2.10.3"` — consolidate to one
5. Remove `kotlin-reflect` dependency (`build.gradle.kts` line 99): only 2 files use it (`GraphUtil.kt` and `SegmentButtonGroup.kt` for `KProperty1`). Replace with direct property access or lambda parameters and remove the dependency (~400–700KB APK savings after R8).

**Acceptance criteria:**
- [ ] `./gradlew spotlessCheck` passes with zero violations
- [ ] Zero duplicate dependency declarations in `build.gradle.kts`
- [ ] Zero duplicate version entries in `libs.versions.toml`
- [ ] `kotlin-reflect` removed after replacing all `KProperty1` usages
- [ ] Zero wildcard imports

---

### Phase 4: Advanced Improvements (P3) — Backlog

##### Task 4.1: Add OWASP Dependency Vulnerability Scanning

> **Consider elevating to Phase 3**: The private Maven package `ggBluetoothNativeLibrary` has unknown supply chain provenance — a health data app should audit this.

Add `org.owasp.dependencycheck` Gradle plugin (v11.1.1). Run as a weekly CI job (NVD database download is slow; not appropriate for every push).

##### Task 4.2: Add Client-Side Input Validation

Zero input validation exists anywhere in the codebase. Create `ValidationUtils` in `core/shared/utilities/` covering:
- Email format (login, signup)
- Password complexity (signup, change password)
- Numeric range for health metrics (weight, body fat %)
- Text field length limits

Integrate into affected forms.

##### ~~Task 4.3: Implement Biometric/PIN Authentication~~ → Backlog (Feature, Not Quality)

Full feature with new ViewModel, Reducer, Screen, and Service. UX implications. Does not belong in a quality improvement plan. Track separately in backlog.

(see brainstorm: Resolved Question 6 — biometric auth is planned)

##### Task 4.3: Create Sealed Result\<T\> Wrapper (Standalone Initiative)

See deferred Task 3.4 notes. Requires design document and `AccountRepository` decomposition first.

---

## Task Dependency Chain

| Task | Blocked By | Reason |
|------|-----------|--------|
| Task 1.3 (Token encryption) | Task 1.2 (PII sanitization) | Crashlytics crash context could expose plaintext tokens if encryption not done first |
| Task 1.4 (Crashlytics) | Tasks 1.2 AND 1.3 | PII must be removed and tokens encrypted before enabling crash reporting |
| Task 1.6 (CI pipeline) | Task 1.1 (PAT externalization) | CI must resolve private Maven repos via env vars |
| Task 1.6 (CI unit test job) | Task 2.2 (`ExampleUnitTest.kt` deleted) | First CI unit test run fails without this fix |
| Task 2.6 (DAO tests) | Task 2.1 (test deps) | Needs `room-testing` artifact |
| Task 2.9 (collectAsStateWithLifecycle) | Task 2.1 (test deps) | Needs `lifecycle-runtime-compose:2.9.1` as `implementation` dep |
| Task 2.10 (@Stable + ImmutableList) | Task 2.1 (test deps) | Needs `kotlinx-collections-immutable` added in Task 2.1 |
| Task 2.11 (staging build type) | Staging API URL from backend | Blocked on external dependency |
| Task 2.12 (CoroutineScope audit) | Tasks 2.3, 2.5 (partial) | Scope changes alter async timing; tests provide regression safety |
| Task 2.15 (CI quality gates) | Tasks 1.6, 2.3, 2.7 | Quality gates require tests and linting to exist |
| Task 3.3 (ResponseInterceptor) | Wire into OkHttpClient first | Interceptor is dead code until added to chain in `NetworkModule.kt` |
| Task 3.5 (Room schema) | `exportSchema = true` change | Cannot test migrations without schema files |
| Task 4.3 (Result\<T\>) | Tasks 2.3–2.5 substantially complete | Tests provide safety net for pervasive signature change |

## Dependencies & Prerequisites

- **GitHub admin access**: Revoke and regenerate PAT (FIRST action)
- **Firebase Console access**: Verify Crashlytics, Analytics, Performance
- **CircleCI admin access**: Add environment variables and Android executor
- **Staging API URL**: From backend team (blocks Task 2.11)
- **TLS certificate renewal schedule**: From infrastructure team (needed for Task 2.13 pin rotation ownership)
- All developers must update `~/.gradle/gradle.properties` after PAT rotation — coordinate BEFORE revoking the old token
- All developers must re-clone after git history rewrite (Task 1.1) — schedule and announce in advance
- All developers must run `lefthook install` after Task 2.8

## Risk Analysis

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| PAT rotation breaks CI | Medium | High | Generate new token, verify locally, THEN revoke old |
| Git history rewrite disrupts team | Medium | Medium | `backup/pre-scrub` tag first; coordinate re-clone during low-activity period |
| Token encryption fails on Samsung devices | Low | High | `MasterKey` failure → graceful re-login + Crashlytics non-fatal |
| Key invalidation on lock screen change | Low | Medium | `GeneralSecurityException` → graceful re-login |
| Detekt expansion flags hundreds of violations | High | Medium | Generate baseline first; enforce only on new code |
| Spotless reformats codebase in one PR | High | Medium | `ratchetFrom("origin/dev")` limits to changed files |
| CoroutineScope changes alter async timing | Medium | Medium | Partial test coverage (Tasks 2.3, 2.5) before remediating |
| collectAsStateWithLifecycle breaks background collection | Low | Medium | Audit `onStop` collection dependencies before migrating |
| Certificate pin rotation missed | Medium | High | Intermediate CA pins rotate rarely; 90-day pre-expiry calendar reminder |
| Navigation3 alpha breaks on update | Medium | High | Pin `nav3Core = "1.0.0-alpha05"`; monitor release notes |
| Debug builds hitting production API | Active | Medium | Staging URL (Task 2.11) — escalate to backend team |
| WebView XSS via URL injection | Low | High | Task 2.16 URL allowlisting |
| DAO instrumented tests fail in CI (no emulator) | High | Medium | Decide on machine executor, Firebase Test Lab, or Robolectric |

## Resource Requirements

- **Phase 1**: 1 developer, 5–7 days
- **Phase 2a** (Infrastructure): 1 developer, ~3 weeks
- **Phase 2b** (Tests): 1–2 developers, ~5–6 weeks
- **Phase 2c** (Compose/Security/Perf): 1 developer, ~4–5 weeks
- **Phase 2 total**: 10–12 weeks (1 dev) or 6–8 weeks (2 devs)
- **Phase 3**: 1 developer, ~1–2 weeks
- **Phase 4**: Ongoing, 1–3 days each

## Success Metrics

| Metric | Current | Phase 1 | Phase 2 | Phase 3 |
|--------|---------|---------|---------|---------|
| Test coverage | 0% | 0% | 60%+ | 70%+ |
| Detekt rules active | 1 | 1 | All defaults + compose rules | Reducing baseline |
| CI pipeline | None | Build + lint | Build + lint + test + coverage | Full |
| Crash visibility | None | Crashlytics | Crashlytics + analytics + perf | Full |
| Hardcoded secrets | 1 PAT | 0 | 0 | 0 |
| Token encryption | None | Encrypted | Encrypted | Encrypted |
| PII in logs | Multiple files | 0 | 0 | 0 |
| Unmanaged CoroutineScope | 27 | 27 | 0 | 0 |
| Certificate pinning | None | None | Active (intermediate CA) | Active |
| TODO comments | 25+ | 25+ | 25+ | 0 |
| Pre-commit hooks | None | None | Lefthook active | Lefthook active |
| Compose stability (`@Stable`) | 0/~30 | 0/~30 | ~30/~30 | ~30/~30 |
| collectAsStateWithLifecycle | 0/88 | 0/88 | 88/88 | 88/88 |
| WebView URL validation | None | None | Active | Active |

## Future Considerations

- **SonarQube/SonarCloud**: Revisit if team grows beyond 5 developers
- **Jetpack Benchmark**: Add microbenchmarks for critical composables after Baseline Profiles
- **Feature flags**: Firebase Remote Config for gradual feature rollouts
- **E2E testing**: Evaluate Maestro after unit/integration foundation is solid
- **AccountRepository decomposition**: 911-line repository with 7+ concerns needs splitting (AccountApiRepo, AccountLocalRepo, AccountTokenRepo) — prerequisite for Result<T>
- **BaseViewModel refactor**: Move `@Inject lateinit var` dependencies to constructor injection to reduce testing friction
- **Compose UI testing**: Screenshot tests and interaction tests — intentionally deferred

## Documentation Plan

After each phase, update:
- `CLAUDE.md` — new build commands, Lefthook setup, test commands, Kover commands
- `Android/CLAUDE.md` — new conventions (@Stable, ImmutableList, CoroutineScope injection, PII-free logging, WebView URL validation)
- `docs/solutions/` — institutional learnings from each phase

## Sources & References

### Origin
- **Brainstorm**: [docs/brainstorms/2026-03-09-android-comprehensive-audit-brainstorm.md](docs/brainstorms/2026-03-09-android-comprehensive-audit-brainstorm.md)
- **Review findings**: [docs/plans/2026-03-09-refactor-android-review-findings.md](docs/plans/2026-03-09-refactor-android-review-findings.md)

### Internal References
- Architecture: `Android/app/src/main/java/com/dmdbrands/gurus/weight/features/common/service/BaseIntentViewModel.kt`
- Navigation: `Android/app/src/main/java/com/dmdbrands/gurus/weight/core/navigation/AppRoute.kt`
- DI modules: `Android/app/src/main/java/com/dmdbrands/gurus/weight/core/di/`
- Build config: `Android/app/build.gradle.kts`
- Detekt config: `Android/config/detekt/detekt.yml`
- CI config: `.circleci/config.yml`

### External References
- MockK: https://mockk.io/ | Turbine: https://github.com/cashapp/turbine | Kover: https://github.com/Kotlin/kotlinx-kover
- Spotless: https://github.com/diffplug/spotless | Detekt: https://detekt.dev/
- Firebase Crashlytics: https://firebase.google.com/docs/crashlytics/get-started?platform=android
