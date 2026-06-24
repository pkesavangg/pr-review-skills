---
title: "Jira Tasks — Android Comprehensive Quality Improvement"
type: jira-backlog
status: ready-to-create
date: 2026-03-09
source: docs/plans/2026-03-09-refactor-android-comprehensive-quality-improvement-plan.md
project: MA
---

# Jira Tasks — Android Comprehensive Quality Improvement

**Source plan**: [2026-03-09-refactor-android-comprehensive-quality-improvement-plan.md](2026-03-09-refactor-android-comprehensive-quality-improvement-plan.md)

---

## Required Jira Fields Reference

Every issue below is defined using these standard Jira fields:

| Field | Type | Notes |
|-------|------|-------|
| **Project** | Key | `MA` |
| **Issue Type** | Select | Epic / Story / Task / Sub-task |
| **Summary** | Text (255 chars) | Short, actionable title |
| **Description** | Rich text | Context, steps, implementation notes |
| **Priority** | Select | Blocker → Critical → High → Medium → Low |
| **Story Points** | Number | Fibonacci: 1, 2, 3, 5, 8, 13, 21 |
| **Labels** | Multi-select | `android`, `security`, `testing`, `ci-cd`, `compose`, `performance`, `tech-debt` |
| **Component/s** | Multi-select | `Android` |
| **Sprint** | Select | Phase 1 / Phase 2a / Phase 2b / Phase 2c / Phase 3 / Phase 4 |
| **Epic Link** | Issue link | Link sub-tasks to the parent epic |
| **Blocked By** | Issue link | Dependency on another Jira issue |
| **Acceptance Criteria** | Text (in Description) | Done-when checklist |
| **Assignee** | User | Unassigned until sprint planning |
| **Reporter** | User | Set to creator |
| **Fix Version** | Version | Leave blank until release planning |

### Priority Mapping

| Plan Priority | Jira Priority |
|---------------|---------------|
| P0 — Critical | Blocker |
| P1 — Important | High |
| P2 — Normal | Medium |
| P3 — Backlog | Low |

---

## Epic

### MA-ANDROID-QUALITY — Android Comprehensive Quality Improvement

| Field | Value |
|-------|-------|
| **Issue Type** | Epic |
| **Summary** | `[Android] Comprehensive Quality Improvement — Security, Testing, CI, Observability` |
| **Priority** | Blocker |
| **Story Points** | (sum of children) |
| **Labels** | `android`, `security`, `testing`, `ci-cd`, `compose`, `performance`, `tech-debt` |
| **Component/s** | Android |
| **Sprint** | — (Epic spans multiple sprints) |

**Description:**

> Transform the MeApp Android codebase from well-architected but under-tooled into a production-grade, observable, and comprehensively tested application.
>
> **Scope**: 732+ Kotlin files. Architecture (MVI + Clean Architecture) is sound. This epic addresses infrastructure gaps only — no architectural changes.
>
> **Critical problems being solved**:
> - Hardcoded GitHub PAT in `settings.gradle.kts` (active credential exposure)
> - Zero test coverage, no CI pipeline, no crash reporting
> - PII (emails, passwords, tokens) in logs
> - Unencrypted auth token storage
> - 88× lifecycle-leaking `collectAsState()` calls
> - 27 unmanaged `CoroutineScope` constructions
>
> **Phases**: P0 (do now) → P1 Next Quarter → P2 Following Quarter → P3 Backlog
>
> **Plan**: docs/plans/2026-03-09-refactor-android-comprehensive-quality-improvement-plan.md

---

## Phase 1 — Critical Fixes (P0 — Do Immediately)

> **Execution order is strict**: 1.1 → 1.2 → 1.3 → 1.4 → 1.5 → 1.6

---

### MA-XXXX — Task 1.1: Revoke and Externalize GitHub PAT

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Security] Revoke and externalize hardcoded GitHub PAT in settings.gradle.kts` |
| **Priority** | Blocker |
| **Story Points** | 3 |
| **Labels** | `android`, `security` |
| **Component/s** | Android |
| **Sprint** | Phase 1 |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | — |

**Description:**

> `settings.gradle.kts` contains a hardcoded GitHub PAT `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` across **two** private Maven repository blocks (ggBluetoothNativeLibrary and vico). The username `Selva-GG` is also hardcoded. Both must be externalized.
>
> **Files**: `Android/settings.gradle.kts` (lines 22–34)
>
> **SAFE rotation order (do not deviate)**:
> 1. Generate new token (scope: `read:packages`)
> 2. Distribute setup instructions to team — each dev adds to `~/.gradle/gradle.properties` (Windows: `%USERPROFILE%\.gradle\gradle.properties`)
> 3. Verify at least one dev can `./gradlew assembleDebug` with new token
> 4. Update `settings.gradle.kts` to read from `gradle.properties` / env vars
> 5. Only AFTER verified: revoke old token on GitHub
> 6. Git history scrub: use `git filter-repo` or BFG Repo Cleaner; push `backup/pre-scrub` tag first; coordinate team re-clone
>
> **CI**: Add `GITHUB_USERNAME` and `GITHUB_TOKEN` to CircleCI environment variables.

**Acceptance Criteria:**

- [ ] Old token revoked on GitHub (only after new token verified working)
- [ ] `settings.gradle.kts` contains zero hardcoded credentials in both Maven blocks
- [ ] Git history cleaned OR decision documented to rely on revocation only
- [ ] `./gradlew assembleDebug` succeeds using `~/.gradle/gradle.properties`
- [ ] CircleCI `GITHUB_USERNAME` + `GITHUB_TOKEN` env vars set and build verified

---

### MA-XXXX — Task 1.2: Sanitize PII from Log Statements

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Security] Remove PII (email, password, tokens) from all log statements` |
| **Priority** | Blocker |
| **Story Points** | 3 |
| **Labels** | `android`, `security` |
| **Component/s** | Android |
| **Sprint** | Phase 1 |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Task 1.1 |

> **This is a prerequisite for Task 1.3 (Token Encryption) and Task 1.4 (Crashlytics). Enabling Crashlytics before this task will upload PII to Firebase.**

**Description:**

> Multiple files log email addresses, passwords, and auth tokens in plaintext. `HttpLoggingInterceptor` is set to `Level.BODY`, which logs full request/response bodies including `Authorization: Bearer <token>` on every API call.
>
> **Files with confirmed PII/direct android.util.Log**:
> - `migration/service/MigrationService.kt` (lines 379, 445) — email in plaintext
> - `migration/repository/MigrationRepository.kt` — `android.util.Log` direct usage
> - `features/changePassword/viewmodel/ChangePasswordViewModel.kt` (line 132) — password
> - `features/common/components/chart/GraphView.kt` (line 244) — `android.util.Log`
> - `core/network/interceptors/AuthTokenInterceptor.kt` — account IDs; BODY-level interceptor
> - `core/network/TokenManager.kt` — account IDs (lines 83, 96, 101)
> - `shared/utilities/webview/IonicDatabaseHelper.kt`, `IonicDataConverter.kt` — `android.util.Log`
> - `core/shared/utilities/logging/ILogRepository.kt` — `android.util.Log`
>
> **Steps**:
> 1. Replace all `android.util.Log` with `AppLog` (5+ files confirmed)
> 2. Redact PII: `"email: ${email.take(3)}***"` or remove
> 3. Change `HttpLoggingInterceptor` from `Level.BODY` → `Level.HEADERS` in `NetworkModule.kt`
> 4. Add `AppLog.sanitize()` utility (email → `us***@***.com`, token → last 4 chars, account ID → first 8 chars of UUID)
> 5. Grep verification: zero PII patterns in log calls

**Acceptance Criteria:**

- [ ] Zero email addresses in log statements
- [ ] Zero passwords or tokens in log statements
- [ ] Zero direct `android.util.Log` usages
- [ ] `HttpLoggingInterceptor` changed from `BODY` to `HEADERS`
- [ ] `AppLog.sanitize()` utility exists for PII redaction
- [ ] Grep audit passes with zero PII matches

---

### MA-XXXX — Task 1.3: Encrypt DataStore Token Storage

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Security] Encrypt auth token storage using EncryptedSharedPreferences` |
| **Priority** | Blocker |
| **Story Points** | 5 |
| **Labels** | `android`, `security` |
| **Component/s** | Android |
| **Sprint** | Phase 1 |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Task 1.2 |

> **Ordered BEFORE Task 1.4 (Crashlytics)**: Crashlytics crash context could expose plaintext tokens if encryption is not done first.

**Description:**

> Auth tokens (`accessToken`, `refreshToken`, `expiresAt`) are stored in plain Protobuf DataStore — readable by anyone with device access or a backup.
>
> **Files**: `Android/gradle/libs.versions.toml`, `Android/app/build.gradle.kts`, `data/storage/datastore/UserDataStore.kt`, `Android/app/proguard-rules.pro`
>
> **Library**: `androidx.security:security-crypto:1.0.0` (stable — NOT `1.1.0-alpha06`)
>
> **Steps**:
> 1. Add dependency `security-crypto:1.0.0`
> 2. Create `EncryptedTokenStorage` using `EncryptedSharedPreferences` for token fields only
> 3. Keep non-sensitive preferences in standard DataStore
> 4. Migration: read from old DataStore → write to encrypted → verify readable → THEN delete old
> 5. Handle `MasterKey` creation failure (Samsung/backup-restore devices) → graceful re-login + Crashlytics non-fatal
> 6. Handle key invalidation after lock screen change (`GeneralSecurityException`) → graceful re-login
> 7. Add ProGuard rules (Tink must not be obfuscated):
>    ```
>    -keep class com.google.crypto.tink.** { *; }
>    -dontwarn com.google.crypto.tink.**
>    ```

**Acceptance Criteria:**

- [ ] Tokens stored in `EncryptedSharedPreferences` in release builds
- [ ] Plain DataStore migration completed with read→verify→delete pattern
- [ ] `MasterKey` failure → graceful re-login (no crash)
- [ ] `GeneralSecurityException` → graceful re-login (no crash)
- [ ] ProGuard/Tink keep rules added to `proguard-rules.pro`
- [ ] Release build (`./gradlew assembleRelease`) succeeds without Tink obfuscation errors

---

### MA-XXXX — Task 1.4: Integrate Firebase Crashlytics

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Observability] Enable Firebase Crashlytics for crash reporting` |
| **Priority** | Blocker |
| **Story Points** | 2 |
| **Labels** | `android`, `performance` |
| **Component/s** | Android |
| **Sprint** | Phase 1 |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Tasks 1.2 AND 1.3 |

**Description:**

> Crashlytics plugin is in the build but not enabled. Zero crash visibility in production.
>
> **Files**: `Android/app/build.gradle.kts`, `.circleci/config.yml`
>
> **Steps**:
> 1. Enable Crashlytics in Firebase Console
> 2. Enable in `build.gradle.kts`
> 3. `google-services.json` CI strategy: encode as `GOOGLE_SERVICES_JSON_BASE64` env var in CircleCI; decode in CI job before build
> 4. Verify crashes appear in Firebase Console
> 5. Confirm zero PII in crash keys/messages (convention from Task 1.2)

**Acceptance Criteria:**

- [ ] Crashes reported to Firebase Console from debug build
- [ ] `google-services.json` decoded from `GOOGLE_SERVICES_JSON_BASE64` in CI
- [ ] Zero PII in any custom Crashlytics keys or messages

---

### MA-XXXX — Task 1.5: Verify Firebase Analytics and Add Custom Events

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Observability] Verify Firebase Analytics is active and add custom events` |
| **Priority** | Critical |
| **Story Points** | 2 |
| **Labels** | `android`, `performance` |
| **Component/s** | Android |
| **Sprint** | Phase 1 |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Task 1.4 |

**Description:**

> Analytics is present in the build but disabled. Custom events needed for product visibility.
>
> **Steps**:
> 1. Verify Analytics is enabled in Firebase Console
> 2. Confirm events flowing in DebugView
> 3. Add custom events: `weight_entry_created`, `manual_entry_created`, `scale_connected`, `account_switched`, `login_success`, `login_failure`, `signup_completed`
> 4. Document event naming convention for the team

**Acceptance Criteria:**

- [ ] Events visible in Firebase DebugView for debug builds
- [ ] 7+ custom events tracked
- [ ] Event naming convention documented in `Android/CLAUDE.md`

---

### MA-XXXX — Task 1.6: Set Up Android CI Pipeline on CircleCI

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][CI] Set up Android CI pipeline (build, lint, unit tests) on CircleCI` |
| **Priority** | Critical |
| **Story Points** | 5 |
| **Labels** | `android`, `ci-cd` |
| **Component/s** | Android |
| **Sprint** | Phase 1 |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Task 1.1 (PAT), Task 2.2 (`ExampleUnitTest.kt` deleted) |

**Description:**

> No Android CI exists. iOS-only CircleCI pipeline. Builds are never automatically verified.
>
> **Files**: `.circleci/config.yml`, `Android/gradle.properties`
>
> **Steps**:
> 1. **Delete `ExampleUnitTest.kt`** first — it uses `createComposeRule()` (instrumented API) in a unit test file and will fail the first CI unit test run
> 2. Add `org.gradle.caching=true` to `Android/gradle.properties`
> 3. Add Android CI job with `cimg/android:2024.11.1` executor
> 4. Cache key: `{{ checksum "Android/gradle/libs.versions.toml" }}-{{ checksum "Android/app/build.gradle.kts" }}`
> 5. Cached paths: `~/.gradle/caches`, `~/.gradle/wrapper`, `~/.gradle/build-cache`
> 6. Jobs: `android-build` (`assembleDebug`), `android-lint` (`./gradlew lint`), `android-test` (`./gradlew test`)
> 7. Add path filtering: Android changes don't trigger iOS builds
> 8. Add `GITHUB_USERNAME`, `GITHUB_TOKEN`, `GOOGLE_SERVICES_JSON_BASE64` to CircleCI env vars

**Acceptance Criteria:**

- [ ] `ExampleUnitTest.kt` deleted before CI job is added
- [ ] CI triggers on PRs to `dev` and `main`
- [ ] `assembleDebug` succeeds in CI
- [ ] `./gradlew lint` passes in CI
- [ ] `./gradlew test` passes in CI
- [ ] Build cache active (`org.gradle.caching=true` + cached paths)
- [ ] Android changes don't trigger iOS builds (path filtering)

---

## Phase 2a — Infrastructure (Weeks 1–3)

---

### MA-XXXX — Task 2.1: Add Test Dependencies to Gradle

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Testing] Add MockK, Turbine, Kover, Robolectric and test infrastructure dependencies` |
| **Priority** | High |
| **Story Points** | 2 |
| **Labels** | `android`, `testing` |
| **Component/s** | Android |
| **Sprint** | Phase 2a |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Task 1.6 |

**Description:**

> Zero test dependencies exist. Required before any test writing can begin.
>
> **Libraries to add**:
> | Library | Version | Scope |
> |---------|---------|-------|
> | MockK | 1.13.15 | testImplementation |
> | MockK Android | 1.13.15 | androidTestImplementation |
> | Turbine | 1.1.0 | testImplementation |
> | Robolectric | 4.14 | testImplementation |
> | kotlinx-coroutines-test | 1.10.1 | testImplementation |
> | Room testing | 2.7.2 | testImplementation |
> | Hilt testing | 2.56.2 | androidTestImplementation |
> | Kover | 0.9.1 | plugin |
> | lifecycle-runtime-compose | 2.9.1 | **implementation** (NOT in BOM) |
> | kotlinx-collections-immutable | 0.3.8 | **implementation** |
>
> Also configure:
> - `testOptions.unitTests.isIncludeAndroidResources = true` (Robolectric)
> - `testOptions.unitTests.isReturnDefaultValues = true`
> - Kover class/package exclusions for generated code

**Acceptance Criteria:**

- [ ] All dependencies resolve (`./gradlew dependencies`)
- [ ] `./gradlew test` runs without dependency errors
- [ ] Kover generates HTML report at `build/reports/kover/`
- [ ] `lifecycle-runtime-compose` available as `implementation` dep
- [ ] `kotlinx-collections-immutable` available as `implementation` dep

---

### MA-XXXX — Task 2.2: Create Test Infrastructure and Shared Fixtures

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Testing] Create MainDispatcherRule, test fixtures, and BaseViewModel reflection helper` |
| **Priority** | High |
| **Story Points** | 3 |
| **Labels** | `android`, `testing` |
| **Component/s** | Android |
| **Sprint** | Phase 2a |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Task 2.1 |

**Description:**

> Tests cannot be written without shared test infrastructure. `BaseViewModel` uses field injection (`@Inject lateinit var`) requiring a reflection helper for unit tests.
>
> **Files to create**:
> - `src/test/.../testutil/MainDispatcherRule.kt` — `TestWatcher` that sets/resets `Dispatchers.Main`
> - `src/test/.../testutil/TestFixtures.kt` — sample `Account`, `Entry`, `Device`, `ScaleEntry`, `BpmEntry`
> - `src/test/.../testutil/ViewModelTestExtensions.kt` — `initTestDependencies()` reflection helper
>
> **Tech debt note**: The reflection helper is needed because `BaseViewModel` uses `@Inject lateinit var` for `navigationService`, `dialogQueueService`, `customTabManager`. Long-term fix: constructor injection (Future Considerations in plan).
>
> **Also**: Delete `ExampleUnitTest.kt` in this task (prerequisite for Task 1.6 CI job).

**Acceptance Criteria:**

- [ ] `MainDispatcherRule` usable via `@get:Rule` in ViewModel tests
- [ ] `initTestDependencies()` resolves `BaseViewModel` field injection
- [ ] `TestFixtures` covers Account, Entry, Device, ScaleEntry, BpmEntry
- [ ] `ExampleUnitTest.kt` deleted
- [ ] Sample test using all three utilities passes

---

### MA-XXXX — Task 2.7: Enable Detekt Default Rules + detekt-compose-rules + Spotless

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Quality] Enable Detekt default rules, detekt-compose-rules, and Spotless formatting` |
| **Priority** | High |
| **Story Points** | 5 |
| **Labels** | `android`, `tech-debt` |
| **Component/s** | Android |
| **Sprint** | Phase 2a |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Task 2.2 |

**Description:**

> Detekt runs with `buildUponDefaultConfig = false` and only 1 rule active. Spotless references ktlint but ktlint is not installed.
>
> **Files**: `Android/build.gradle.kts`, `Android/config/detekt/detekt.yml`, `Android/gradle/libs.versions.toml`
>
> **CRITICAL — step order matters** (do not deviate):
> 1. Add Spotless plugin; run `./gradlew spotlessApply` on all existing code
> 2. Set `buildUponDefaultConfig = true` in Detekt config
> 3. Run `./gradlew detektBaseline` to baseline ALL existing violations
> 4. Commit both reformatted files and baseline
> 5. Set `ignoreFailures = false` — CI enforces on new code only
> 6. Add `detekt-compose-rules:0.4.22` plugin
> 7. Incrementally reduce baseline category-by-category
>
> **Spotless**: `ratchetFrom("origin/dev")` limits formatting to changed files only — avoids a single massive PR.
>
> **Scope note**: `:bleWrapper` is excluded from Detekt analysis — preserve this exclusion in the baseline.

**Acceptance Criteria:**

- [ ] `buildUponDefaultConfig = true` in Detekt config
- [ ] Baseline generated and committed
- [ ] `./gradlew detekt` passes in CI (baseline absorbs existing violations)
- [ ] Spotless configured with `ratchetFrom("origin/dev")`
- [ ] `detekt-compose-rules:0.4.22` active
- [ ] `:bleWrapper` exclusion preserved

---

### MA-XXXX — Task 2.8: Add Lefthook Pre-commit Hooks

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Quality] Add Lefthook pre-commit hooks for Detekt (staged files only, <10s)` |
| **Priority** | High |
| **Story Points** | 2 |
| **Labels** | `android`, `tech-debt`, `ci-cd` |
| **Component/s** | Android |
| **Sprint** | Phase 2a |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Task 2.7 |

**Description:**

> No pre-commit quality gates. Code reaches the repo unvalidated.
>
> **Implementation**: Lefthook with Detekt CLI binary (NOT `./gradlew detekt`) on staged files only — Gradle startup time alone exceeds the 10s target.
>
> **Install**:
> - macOS: `brew install lefthook`
> - Windows: `scoop install lefthook` / `choco install lefthook`
> - Linux: package manager or binary
>
> **Note**: `{staged_files}` passes Unix-style paths — may need adjustment on Windows CI.
>
> **Team action required**: All developers run `lefthook install` after this task merges.

**Acceptance Criteria:**

- [ ] Lefthook configured with Detekt CLI on staged `.kt` files
- [ ] Pre-commit hook completes in <10 seconds
- [ ] `lefthook install` documented in `CLAUDE.md` onboarding steps
- [ ] Hook runs on macOS and Linux (Windows documented as best-effort)

---

## Phase 2b — Test Coverage (Weeks 4–9)

---

### MA-XXXX — Task 2.3: Write Reducer Unit Tests

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Testing] Write unit tests for all ~30 MVI reducer classes` |
| **Priority** | High |
| **Story Points** | 13 |
| **Labels** | `android`, `testing` |
| **Component/s** | Android |
| **Sprint** | Phase 2b |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Task 2.2 |

**Description:**

> ~30 reducer classes exist. Reducers are pure functions — no mocking needed. Highest ROI per hour of any test task.
>
> **Priority order** (highest risk first):
> 1. `LoginReducer` — auth core
> 2. `SignupReducer` — user onboarding
> 3. `DashboardReducer` — primary screen
> 4. `EntryReducer` / `ManualEntryReducer` — core data entry
> 5. `AccountReducer`
> 6. `SettingsReducer`
> 7. Remaining alphabetically
>
> **Pattern**: `reducer.reduce(initialState, intent)` → assert new state fields. Every intent type gets at least one test.

**Acceptance Criteria:**

- [ ] Every reducer has a test file
- [ ] Every intent type has at least one test case
- [ ] `./gradlew test --tests "*ReducerTest*"` passes
- [ ] Coverage ≥80% on reducer classes

---

### MA-XXXX — Task 2.4: Write Repository Unit Tests

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Testing] Write unit tests for all ~15 repository implementations` |
| **Priority** | High |
| **Story Points** | 13 |
| **Labels** | `android`, `testing` |
| **Component/s** | Android |
| **Sprint** | Phase 2b |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Task 2.2 |

**Description:**

> ~15 repository implementations with API and DAO dependencies. MockK for all dependencies.
>
> **Priority order**:
> 1. `AccountRepository` (911 lines — highest complexity, highest risk)
> 2. `EntryRepository`
> 3. `DashboardRepository`
> 4. `DeviceRepository`
> 5. Remaining alphabetically
>
> Test both happy path and error paths for key methods.

**Acceptance Criteria:**

- [ ] Every repository has a test file
- [ ] Happy path and at least one error path per key method
- [ ] `./gradlew test --tests "*RepositoryTest*"` passes

---

### MA-XXXX — Task 2.5: Write ViewModel Unit Tests

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Testing] Write unit tests for 10 critical ViewModels` |
| **Priority** | High |
| **Story Points** | 8 |
| **Labels** | `android`, `testing` |
| **Component/s** | Android |
| **Sprint** | Phase 2b |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Task 2.2 |

**Description:**

> 10 highest-risk ViewModels. Uses MockK + Turbine + `MainDispatcherRule` + `initTestDependencies()`.
>
> **Priority order**:
> 1. `LoginViewModel`
> 2. `DashboardViewModel`
> 3. `EntryViewModel` / `ManualEntryViewModel`
> 4. `SignupViewModel`
> 5. `SettingsViewModel`
> 6. `AccountViewModel`
> 7. `HomeViewModel`
> 8. Plus 3 others by risk
>
> Verify: state transitions (Turbine), navigation calls (`coVerify`), API calls (`coVerify`).

**Acceptance Criteria:**

- [ ] 10 critical ViewModels have test files
- [ ] State transitions verified with Turbine
- [ ] Navigation and API calls verified with `coVerify`
- [ ] `./gradlew test --tests "*ViewModelTest*"` passes

---

### MA-XXXX — Task 2.6: Write DAO Instrumented Tests

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Testing] Write instrumented tests for all 4 Room DAOs` |
| **Priority** | High |
| **Story Points** | 5 |
| **Labels** | `android`, `testing` |
| **Component/s** | Android |
| **Sprint** | Phase 2b |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Task 2.1 |

**Description:**

> 4 DAOs: `AccountDao`, `EntryDao`, `DeviceDao`, `LogDao`. All in `src/androidTest/`.
>
> **CI constraint**: `cimg/android` Docker executor does not support emulators (no KVM). Must decide BEFORE implementing:
> - Option A: Machine executor with KVM in CircleCI
> - Option B: Firebase Test Lab
> - Option C: Robolectric-based Room in-memory tests (avoids emulator entirely)
>
> Document the decision in `Android/CLAUDE.md`.

**Acceptance Criteria:**

- [ ] All 4 DAOs have test files
- [ ] CRUD operations and Flow queries verified
- [ ] CI strategy documented and implemented
- [ ] Tests pass in CI

---

## Phase 2c — Compose, Security, Performance (Weeks 10–14)

---

### MA-XXXX — Task 2.9: Migrate collectAsState() to collectAsStateWithLifecycle()

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Compose] Replace 88× collectAsState() with collectAsStateWithLifecycle()` |
| **Priority** | High |
| **Story Points** | 5 |
| **Labels** | `android`, `compose`, `performance` |
| **Component/s** | Android |
| **Sprint** | Phase 2c |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Task 2.1 (lifecycle-runtime-compose dep) |

**Description:**

> 88 instances across 43 files (including 3 in `:iam` module: `IamFeedLandingScreen.kt`, `FeedMessagesSettingsScreen.kt`, `FeedMessagesScreen.kt`). `collectAsState()` continues collecting when the app is backgrounded — wasting battery and potentially causing state corruption.
>
> **Before migrating**: Audit whether any screen depends on continued collection during `onStop` (e.g., background sync status). Handle those screens separately.
>
> **Scope decision required**: Explicitly decide whether to include or defer the 3 `:iam` files.
>
> Replace: `.collectAsState()` → `.collectAsStateWithLifecycle()`
> Add import: `import androidx.lifecycle.compose.collectAsStateWithLifecycle`

**Acceptance Criteria:**

- [ ] Zero `collectAsState()` calls in `com.dmdbrands.gurus.weight` package
- [ ] `:iam` module scope decision documented
- [ ] `onStop` behavioral audit completed before migration
- [ ] All 88 instances migrated (or explicitly deferred with justification)

---

### MA-XXXX — Task 2.10: Add @Stable, ImmutableList, and LazyColumn Keys

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Compose] Annotate ~30 state classes @Stable, add ImmutableList, add LazyColumn keys` |
| **Priority** | High |
| **Story Points** | 8 |
| **Labels** | `android`, `compose`, `performance` |
| **Component/s** | Android |
| **Sprint** | Phase 2c |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Task 2.1 (kotlinx-collections-immutable dep) |

**Description:**

> ~30 state data classes lack `@Stable` annotation, causing Compose compiler to treat them as unstable → unnecessary recompositions. `List<T>` fields in state classes are inherently unstable even with `@Stable`. 13 of 17 `LazyColumn`/`LazyRow` calls lack stable key parameters.
>
> **Part A**: Add `@Stable` to all MVI state data classes
>
> **Part B**: Replace `List<T>` with `ImmutableList<T>` / `PersistentList<T>` (from `kotlinx-collections-immutable`) for List-containing state classes
>
> **Part C**: Add stable `key` parameters to 13 `LazyColumn`/`LazyRow` calls using entity IDs
>
> **Verification**: Run Compose compiler stability report to confirm unstable parameter count decreases ≥30.

**Acceptance Criteria:**

- [ ] All ~30 state data classes annotated `@Stable`
- [ ] `List<T>` fields in state classes replaced with `ImmutableList<T>` / `PersistentList<T>`
- [ ] 13 LazyColumn/LazyRow calls have stable `key` parameters
- [ ] Compose compiler stability report shows ≥30 fewer unstable parameters
- [ ] No regressions in existing UI behaviour

---

### MA-XXXX — Task 2.11: Add Staging Build Type (BLOCKED)

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Security] Add staging build type to prevent debug builds hitting production API` |
| **Priority** | High |
| **Story Points** | 3 |
| **Labels** | `android`, `security` |
| **Component/s** | Android |
| **Sprint** | Phase 2c |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | **BLOCKED — Staging API URL required from backend team** |

**Description:**

> Debug builds currently hit the production API. This is an elevated security risk — debug builds skip certificate pinning and have verbose logging enabled.
>
> **Status**: BLOCKED. Cannot proceed without staging API base URL from backend team.
>
> **When unblocked**: Add `staging` build type in `build.gradle.kts`; set `BuildConfig.BASE_URL` per build type; ensure Crashlytics disabled in staging.

**Acceptance Criteria:**

- [ ] Staging API URL obtained from backend team
- [ ] `staging` build type created in `build.gradle.kts`
- [ ] Staging build uses staging API URL
- [ ] Debug build uses staging API URL
- [ ] Release build uses production API URL
- [ ] Crashlytics disabled for staging and debug builds

---

### MA-XXXX — Task 2.12: CoroutineScope Audit and Remediation

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Stability] Remediate 27 manual CoroutineScope constructions; fix AccountService race condition` |
| **Priority** | High |
| **Story Points** | 8 |
| **Labels** | `android`, `tech-debt` |
| **Component/s** | Android |
| **Sprint** | Phase 2c |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Tasks 2.3, 2.5 (partial) |

**Description:**

> 27 manual `CoroutineScope(Dispatchers...)` constructions across 11 files. These scopes are never cancelled, causing memory leaks and potential background work after logout.
>
> **Critical**: `AccountService` has a cancel-and-recreate pattern that creates a **data loss race condition** — new scope starts work before old scope finishes cancellation.
>
> **Also fix**:
> - `SimpleDateFormat` in `OkHttp` interceptors — not thread-safe; replace with `DateTimeFormatter`
> - `EntryAggregationService`: `getActiveAccount().first()` called inside a `combine` operator — can deadlock
>
> **Also add**: LeakCanary (`debugImplementation`) for detecting scope leaks in debug builds.
>
> **Prerequisite**: Tasks 2.3 and 2.5 (partial) to provide regression safety before altering async timing.

**Acceptance Criteria:**

- [ ] Zero manual `CoroutineScope(Dispatchers...)` in production code (replaced with `viewModelScope`, `lifecycleScope`, or injected scopes)
- [ ] `AccountService` race condition fixed (data loss bug resolved)
- [ ] `SimpleDateFormat` replaced with `DateTimeFormatter` in interceptors
- [ ] `EntryAggregationService` deadlock fix applied
- [ ] LeakCanary integrated for debug builds
- [ ] Existing reducer and ViewModel tests still pass after changes

---

### MA-XXXX — Task 2.13: Add Certificate Pinning

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Security] Add certificate pinning to OkHttpClient with rotation strategy` |
| **Priority** | High |
| **Story Points** | 5 |
| **Labels** | `android`, `security` |
| **Component/s** | Android |
| **Sprint** | Phase 2c |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Task 1.4 (Crashlytics — for pin mismatch reporting) |

**Description:**

> No certificate pinning. MitM attacks on the API are undetected.
>
> **Implementation**: Pin to intermediate CA (not leaf certificate). Leaf certs rotate frequently; intermediate CA is more stable.
>
> **Steps**:
> 1. Coordinate with backend/infrastructure team for TLS renewal schedule and intermediate CA hash
> 2. Add `CertificatePinner` to `OkHttpClient` in `NetworkModule.kt`
> 3. Implement graceful pin mismatch: catch `SSLPeerUnverifiedException`; log non-fatal to Crashlytics; show "Please update the app" dialog
> 4. Add named owner to `CLAUDE.md`: person responsible for pin rotation
> 5. Add 90-day pre-expiry calendar reminder for pin rotation

**Acceptance Criteria:**

- [ ] Certificate pin active on release builds
- [ ] Intermediate CA pinned (not leaf cert)
- [ ] Pin mismatch shows user-friendly update dialog (no crash)
- [ ] Pin mismatch logged as Crashlytics non-fatal event
- [ ] Named rotation owner documented
- [ ] 90-day pre-expiry reminder scheduled

---

### MA-XXXX — Task 2.14: Baseline Profiles

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Performance] Generate Baseline Profiles with Macrobenchmark` |
| **Priority** | Medium |
| **Story Points** | 3 |
| **Labels** | `android`, `performance` |
| **Component/s** | Android |
| **Sprint** | Phase 2c |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Task 2.1 |

**Description:**

> No Baseline Profiles. App startup and initial rendering are unoptimized — ART cannot pre-compile hot paths.
>
> **Recommended approach**: Macrobenchmark module (not hand-written rule wildcards).
>
> **Dependency**: `profileinstaller:1.4.0+` required.
>
> **Steps**:
> 1. Add `:benchmark` Macrobenchmark module
> 2. Write `BaselineProfileGenerator` for critical user journeys (login, dashboard load, entry creation)
> 3. Generate profiles with `./gradlew :benchmark:generateBaselineProfile`
> 4. Commit `baseline-prof.txt` to `app/src/main/`

**Acceptance Criteria:**

- [ ] Macrobenchmark module configured
- [ ] Baseline profiles generated for login, dashboard, entry creation flows
- [ ] `baseline-prof.txt` committed to source control
- [ ] Cold start time measured before/after (document improvement)

---

### MA-XXXX — Task 2.15: Firebase Performance Monitoring + CI Quality Gates

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Observability] Add Firebase Performance Monitoring and CI coverage/quality gates` |
| **Priority** | Medium |
| **Story Points** | 5 |
| **Labels** | `android`, `performance`, `ci-cd` |
| **Component/s** | Android |
| **Sprint** | Phase 2c |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Tasks 1.6, 2.3, 2.7 |

**Description:**

> No performance monitoring. No automated quality gate enforcement in CI.
>
> **Part A — Firebase Performance**:
> 1. Add `firebase-perf` plugin to `build.gradle.kts`
> 2. Add custom traces for: weight entry save, dashboard load, scale connection
>
> **Part B — CI Quality Gates**:
> 1. Add Kover coverage gate: `./gradlew koverVerify` (fail if <60% on modified files)
> 2. Add Detekt quality gate: `./gradlew detekt` (fail on new violations)
> 3. Add Spotless check: `./gradlew spotlessCheck` (fail on unformatted files)
> 4. Wire all three into CircleCI PR workflow

**Acceptance Criteria:**

- [ ] Firebase Performance traces visible in Firebase Console
- [ ] CI fails PRs with <60% coverage on modified files
- [ ] CI fails PRs with new Detekt violations
- [ ] CI fails PRs with Spotless formatting violations

---

### MA-XXXX — Task 2.16: WebView URL Validation

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Security] Add URL validation to WebView (HTTPS enforcement + domain allowlist)` |
| **Priority** | High |
| **Story Points** | 2 |
| **Labels** | `android`, `security` |
| **Component/s** | Android |
| **Sprint** | Phase 2c |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | — |

**Description:**

> WebView URLs are not validated before loading. A malicious deep link could redirect the WebView to an arbitrary URL — XSS risk.
>
> **Steps**:
> 1. Add domain allowlist constant (weightgurus.com, greatergoods.com, and explicit others)
> 2. In `WebViewClient.shouldOverrideUrlLoading()`: enforce HTTPS scheme; reject URLs not in allowlist
> 3. Log rejected URLs as Crashlytics non-fatal events (may indicate attack attempt)
> 4. Write unit tests for allowlist validation logic

**Acceptance Criteria:**

- [ ] WebView rejects non-HTTPS URLs
- [ ] WebView rejects URLs outside the domain allowlist
- [ ] Rejected URL logged to Crashlytics non-fatal
- [ ] Unit tests cover allowlist validation

---

## Phase 3 — Code Cleanup (P2 — Following Quarter)

---

### MA-XXXX — Task 3.1: Remove Dead Code

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Cleanup] Remove dead methods and commented-out code blocks` |
| **Priority** | Medium |
| **Story Points** | 2 |
| **Labels** | `android`, `tech-debt` |
| **Component/s** | Android |
| **Sprint** | Phase 3 |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | — |

**Description:**

> - `DashboardService.kt`: remove `getVisibleMetricKeys()`, `getVisibleMilestoneKeys()`, `resetVisibleMetricKeys()`, `resetVisibleMilestoneKeys()` + interface methods
> - `EntryRepository.kt` line 121: remove commented-out `deleteAllEntriesForAccount()`
> - `AppInitializer.kt`: remove commented-out analytics init code

**Acceptance Criteria:**

- [ ] Zero methods marked `// TODO: no use` or `// TODO: Not in use`
- [ ] Zero commented-out code blocks in production source
- [ ] App compiles and tests pass after removal

---

### MA-XXXX — Task 3.2: Fix Minor Compose Issues

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Compose] Remove unused remember/rememberCoroutineScope calls in composables` |
| **Priority** | Medium |
| **Story Points** | 2 |
| **Labels** | `android`, `compose` |
| **Component/s** | Android |
| **Sprint** | Phase 3 |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | — |

**Description:**

> - `AppInput.kt` line 258: remove unused `remember { MutableInteractionSource() }`
> - `DashboardScreen.kt` lines 59, 88: remove two unused `rememberCoroutineScope()` calls

**Acceptance Criteria:**

- [ ] No unused `remember` results
- [ ] Zero unused `rememberCoroutineScope()` calls

---

### MA-XXXX — Task 3.3: Wire and Complete ResponseInterceptor

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Network] Wire ResponseInterceptor into OkHttpClient chain and implement error handlers` |
| **Priority** | Medium |
| **Story Points** | 3 |
| **Labels** | `android`, `tech-debt` |
| **Component/s** | Android |
| **Sprint** | Phase 3 |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | — |

**Description:**

> `ResponseInterceptor` is provided by Hilt but **never added to the OkHttpClient interceptor chain** in `NetworkModule.kt` (lines 149–174). It is effectively dead code. HTTP 403, 400, and 500 TODO handlers are not implemented.
>
> **Steps**:
> 1. Add `ResponseInterceptor` to `provideOkHttpClient()` in `NetworkModule.kt`
> 2. Implement HTTP 403 handler (token expired → trigger re-login)
> 3. Implement HTTP 400 handler (bad request → log error)
> 4. Implement HTTP 500 handler (server error → show error state)
> 5. Write unit tests for each error code path

**Acceptance Criteria:**

- [ ] `ResponseInterceptor` added to OkHttpClient interceptor chain
- [ ] All error code handlers implemented (no TODOs)
- [ ] Unit tests for 400, 403, 500 paths

---

### MA-XXXX — Task 3.4: Resolve TODO Comments

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Cleanup] Resolve all 25+ unlinked TODO comments (implement, ticket, or remove)` |
| **Priority** | Medium |
| **Story Points** | 3 |
| **Labels** | `android`, `tech-debt` |
| **Component/s** | Android |
| **Sprint** | Phase 3 |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | — |

**Description:**

> 25+ unlinked TODOs exist. Each must be: implemented, replaced with `// TODO(MA-XXXX): ...`, or removed.
>
> **Specific items**:
> - `EntryHelper.kt`: `accountId = "TODO"` — replace with actual value
> - `AppStatusService.kt`: `isMetric = false` — replace with user preferences
> - `ScaleDetailsViewModel.kt`: download TODO — create ticket or remove

**Acceptance Criteria:**

- [ ] Zero unlinked TODO comments
- [ ] All remaining TODOs reference a Jira ticket
- [ ] Placeholder `"TODO"` strings replaced with real values

---

### MA-XXXX — Task 3.5: Enable Room Schema Export

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Database] Enable Room schema export for migration testing` |
| **Priority** | Medium |
| **Story Points** | 1 |
| **Labels** | `android`, `tech-debt` |
| **Component/s** | Android |
| **Sprint** | Phase 3 |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | — |

**Description:**

> `exportSchema = false` in `AppDatabase.kt` prevents Room from generating schema JSON files, making migration testing impossible.
>
> Change to `exportSchema = true` + configure `room { schemaDirectory("$projectDir/schemas") }` + commit the baseline schema JSON.

**Acceptance Criteria:**

- [ ] `exportSchema = true` in AppDatabase
- [ ] Schema JSON generated and committed to version control
- [ ] `./gradlew assembleDebug` succeeds with schema export enabled

---

### MA-XXXX — Task 3.6: Convert Raster Images to WebP

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Performance] Convert 36 PNG/JPG drawables to WebP (quality=80)` |
| **Priority** | Low |
| **Story Points** | 2 |
| **Labels** | `android`, `performance` |
| **Component/s** | Android |
| **Sprint** | Phase 3 |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | — |

**Description:**

> 36 PNG/JPG files in `res/drawable*/`. WebP conversion at quality=80 reduces image asset APK size by ~25%.

**Acceptance Criteria:**

- [ ] All 36 raster images converted to WebP
- [ ] APK size reduction measured and documented
- [ ] Visual quality acceptable (QA sign-off)

---

### MA-XXXX — Task 3.7: Clean Up Dependencies and Build Files

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Build] Remove duplicate deps, consolidate version catalog, remove kotlin-reflect` |
| **Priority** | Medium |
| **Story Points** | 3 |
| **Labels** | `android`, `tech-debt` |
| **Component/s** | Android |
| **Sprint** | Phase 3 |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | — |

**Description:**

> - `build.gradle.kts`: `datastore`, `preferences-core`, `gson` declared twice (lines 153–155 and 166–168); `datastore` appears a third time at line 172
> - `libs.versions.toml`: `workRuntimeKtx = "2.10.2"` and `workRuntimeKtxVersion = "2.10.3"` — consolidate to one
> - `kotlin-reflect` dependency: only 2 files use it (`GraphUtil.kt`, `SegmentButtonGroup.kt` for `KProperty1`). Replace with direct access and remove (~400–700KB APK savings after R8)
> - Fix wildcard import in `LogDao.kt`

**Acceptance Criteria:**

- [ ] Zero duplicate dependency declarations in `build.gradle.kts`
- [ ] Zero duplicate version entries in `libs.versions.toml`
- [ ] `kotlin-reflect` removed after replacing `KProperty1` usages
- [ ] Zero wildcard imports
- [ ] APK size reduction from `kotlin-reflect` removal measured

---

## Phase 4 — Backlog (P3)

---

### MA-XXXX — Task 4.1: OWASP Dependency Vulnerability Scanning

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Security] Add OWASP dependency vulnerability scanning to CI (weekly)` |
| **Priority** | Low |
| **Story Points** | 2 |
| **Labels** | `android`, `security`, `ci-cd` |
| **Component/s** | Android |
| **Sprint** | Phase 4 |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Task 1.6 |

**Description:**

> Add `org.owasp.dependencycheck` Gradle plugin (v11.1.1). Run as a weekly CI job — NVD database download is slow and not appropriate for every push.
>
> **Note**: `ggBluetoothNativeLibrary` private Maven package has unknown supply chain provenance — a health data app should audit this.

**Acceptance Criteria:**

- [ ] OWASP plugin configured
- [ ] Weekly CI job scans dependencies
- [ ] Known vulnerabilities in `ggBluetoothNativeLibrary` assessed

---

### MA-XXXX — Task 4.2: Add Client-Side Input Validation

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Security] Add client-side input validation to all form fields (OWASP M4)` |
| **Priority** | Low |
| **Story Points** | 5 |
| **Labels** | `android`, `security` |
| **Component/s** | Android |
| **Sprint** | Phase 4 |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | — |

**Description:**

> OWASP Mobile Top 10 M4 (Insufficient Input/Output Validation) gap. Form fields lack client-side validation before submission.
>
> **Scope**: Login, Signup, Manual Entry, Change Password, Account forms.
>
> **Rules**: Email format, password minimum requirements, weight range (0–999.9 kg), date range validation.

**Acceptance Criteria:**

- [ ] All form fields validated before submission
- [ ] Invalid input shows inline error messages
- [ ] Edge cases (empty, whitespace, special chars) handled
- [ ] Unit tests for all validation rules

---

### MA-XXXX — Task 4.3: Result<T> Wrapper (Deferred — Standalone Initiative)

| Field | Value |
|-------|-------|
| **Issue Type** | Task |
| **Summary** | `[Android][Architecture] Introduce sealed Result<T> wrapper across repositories and services` |
| **Priority** | Low |
| **Story Points** | 21 |
| **Labels** | `android`, `tech-debt` |
| **Component/s** | Android |
| **Sprint** | Phase 4 |
| **Epic Link** | MA-ANDROID-QUALITY |
| **Blocked By** | Tasks 2.3–2.5 substantially complete; AccountRepository decomposition |

**Description:**

> **DEFERRED — Standalone Initiative Required.**
>
> Result<T> has the widest blast radius of any item in this plan: 15 repository interfaces, 15 implementations, 25+ services, ~30 ViewModels. Requires:
> 1. A dedicated design document
> 2. `AccountRepository` (911 lines, 7+ concerns) decomposed into `AccountApiRepo` / `AccountLocalRepo` / `AccountTokenRepo` first
> 3. Phase 2b tests substantially complete to provide safety net
>
> Do not start this task until the above prerequisites are met.

**Acceptance Criteria:**

- [ ] `AccountRepository` decomposed into smaller repos
- [ ] `Result<T>` sealed class designed with all error cases
- [ ] All repositories return `Result<T>`
- [ ] All services propagate `Result<T>`
- [ ] All ViewModels handle `Result.Success` and `Result.Error`
- [ ] Zero unhandled exceptions reaching the UI layer

---

## Summary Table

| Task ID | Summary | Phase | Priority | Points | Blocked By |
|---------|---------|-------|----------|--------|-----------|
| 1.1 | Revoke & externalize GitHub PAT | P0 | Blocker | 3 | — |
| 1.2 | Sanitize PII from logs | P0 | Blocker | 3 | 1.1 |
| 1.3 | Encrypt DataStore token storage | P0 | Blocker | 5 | 1.2 |
| 1.4 | Firebase Crashlytics | P0 | Blocker | 2 | 1.2, 1.3 |
| 1.5 | Firebase Analytics | P0 | Critical | 2 | 1.4 |
| 1.6 | Android CI pipeline | P0 | Critical | 5 | 1.1, 2.2 |
| 2.1 | Add test dependencies | 2a | High | 2 | 1.6 |
| 2.2 | Test infrastructure + fixtures | 2a | High | 3 | 2.1 |
| 2.7 | Detekt + Spotless | 2a | High | 5 | 2.2 |
| 2.8 | Lefthook pre-commit hooks | 2a | High | 2 | 2.7 |
| 2.3 | Reducer unit tests | 2b | High | 13 | 2.2 |
| 2.4 | Repository unit tests | 2b | High | 13 | 2.2 |
| 2.5 | ViewModel unit tests | 2b | High | 8 | 2.2 |
| 2.6 | DAO instrumented tests | 2b | High | 5 | 2.1 |
| 2.9 | collectAsStateWithLifecycle | 2c | High | 5 | 2.1 |
| 2.10 | @Stable + ImmutableList + keys | 2c | High | 8 | 2.1 |
| 2.11 | Staging build type | 2c | High | 3 | **BLOCKED** |
| 2.12 | CoroutineScope remediation | 2c | High | 8 | 2.3, 2.5 |
| 2.13 | Certificate pinning | 2c | High | 5 | 1.4 |
| 2.14 | Baseline Profiles | 2c | Medium | 3 | 2.1 |
| 2.15 | Firebase Perf + CI quality gates | 2c | Medium | 5 | 1.6, 2.3, 2.7 |
| 2.16 | WebView URL validation | 2c | High | 2 | — |
| 3.1 | Remove dead code | P3 | Medium | 2 | — |
| 3.2 | Compose minor fixes | P3 | Medium | 2 | — |
| 3.3 | ResponseInterceptor | P3 | Medium | 3 | — |
| 3.4 | Resolve TODO comments | P3 | Medium | 3 | — |
| 3.5 | Room schema export | P3 | Medium | 1 | — |
| 3.6 | PNG → WebP conversion | P3 | Low | 2 | — |
| 3.7 | Dependency cleanup | P3 | Medium | 3 | — |
| 4.1 | OWASP scanning | P4 | Low | 2 | 1.6 |
| 4.2 | Input validation | P4 | Low | 5 | — |
| 4.3 | Result<T> wrapper | P4 | Low | 21 | 2.3–2.5 |

**Total story points**: ~153

**Phase 1 total**: 20 points (~1 sprint)
**Phase 2 total**: ~82 points (~4–6 sprints)
**Phase 3 total**: ~16 points (~1 sprint)
**Phase 4 total**: ~28 points (backlog)
