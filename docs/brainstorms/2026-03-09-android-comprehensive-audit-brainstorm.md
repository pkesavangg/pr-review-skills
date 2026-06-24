# Android Comprehensive Project Audit

**Date:** 2026-03-09
**Status:** Brainstorm
**Scope:** Full audit of MeApp Android codebase (`Android/`)

---

## What We're Building

A comprehensive audit and improvement plan for the MeApp Android project covering architecture, code quality, security, testing, CI/CD, observability, and adherence to modern Android development best practices. The goal is to identify gaps, prioritize fixes, and establish a roadmap for bringing the codebase to production-grade quality.

---

## Audit Findings

### Legend

| Icon | Meaning |
|------|---------|
| :white_check_mark: | Good — meets or exceeds standards |
| :warning: | Needs improvement — functional but has gaps |
| :x: | Critical — requires immediate action |

---

## 1. Architecture

**Rating: :white_check_mark: Excellent**

- **Pattern:** MVI (Model-View-Intent) + Clean Architecture
- **Layers:** `domain/` -> `data/` -> `features/` with `core/` for infrastructure
- **State Management:** `BaseIntentViewModel<State, Intent>` + `IReducer<State, Intent>` — pure reducer pattern
- **Modules:** 7 Gradle modules (`:app`, `:app:healthconnect`, `:app:wificonnect`, `:app:appsync`, `:notification`, `:bleWrapper`, `:iam`)
- **Features:** 36+ feature directories, each consistently structured with `reducer/`, `viewmodel/`, `strings/`, `components/`, `screens/`
- **Layer separation:** Clean boundaries between domain, data, and presentation

**Issues found:**
- None significant. Architecture is consistent and well-implemented.

---

## 2. Coding Standards

**Rating: :white_check_mark: Good**

- `.editorconfig` configured: 2-space indent (Kotlin), 120-char line length, official Kotlin code style
- Interface naming: All use `I` prefix (e.g., `IAccountRepository`, `IEntryService`) — consistent across 16+ repositories and 25+ services
- PascalCase `Strings` objects per feature for static text
- Immutable state data classes with `.copy()` updates

**Issues found:**
- Unused imports in ~20+ files (e.g., `CustomizeScaleSettings.kt`, `ScaleModeSettings.kt`)
- Wildcard import in `LogDao.kt` (`import androidx.room.*`) — should be explicit
- No automated import cleanup enforced

---

## 3. Linting

**Rating: :warning: Needs Improvement**

- **Detekt** is configured (v1.23.7) but only **1 rule enabled** (`UnsafeCallOnNullableType`)
- `buildUponDefaultConfig = false` — means ALL default rules are disabled
- **No ktlint** or **Spotless** configured for code formatting enforcement
- EditorConfig exists but nothing enforces it in CI

**Recommendations:**
- Enable Detekt default rules (`buildUponDefaultConfig = true`)
- Add custom rule sets for: complexity, naming, style, empty-blocks, exceptions
- Add ktlint or Spotless for formatting enforcement
- Integrate linting into CI pipeline

---

## 4. Security

**Rating: :x: Critical Issue Found**

### Critical
| Issue | Location | Details |
|-------|----------|---------|
| **Hardcoded GitHub PAT** | `settings.gradle.kts:22-34` | Token `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` committed in plaintext. Username: `Selva-GG`. Used for private Maven repos. **Must be revoked immediately.** |

### Secure Areas
- Token management: Bearer auth with proactive refresh (5-min buffer), Mutex-locked concurrent refresh
- Network: HTTPS enforced in release (`cleartextTrafficPermitted="false"`), debug allows only localhost/emulator
- Data: Cloud backup disabled (`allowBackup="false"`), all domains excluded from backup/transfer
- Obfuscation: R8 minification + resource shrinking enabled with comprehensive ProGuard rules
- Manifest: Only launcher activity and HealthConnect-required alias exported
- No other hardcoded credentials found in source files

### Recommended Improvements
- Move GitHub PAT to environment variables or `~/.gradle/gradle.properties`
- Consider certificate pinning for API endpoints (recommended for health apps)
- `SimpleDateFormat` used for token expiry parsing — could fail silently; consider `java.time` APIs

---

## 5. Standardization

**Rating: :white_check_mark: Good**

- Consistent MVI pattern across all 36+ features
- Uniform file organization per feature: `reducer/`, `viewmodel/`, `strings/`, `components/`, `screens/`
- All API methods are `suspend` functions
- All repositories follow `I` prefix convention
- Shared components centralized in `features/common/` (155 files)
- Theme tokens used consistently (249+ usages)
- `AppLog` used universally (zero `android.util.Log` usage)

---

## 6. Testing

**Rating: :x: Critical Gap**

| Metric | Value |
|--------|-------|
| Source files | 732+ Kotlin files |
| Unit tests | **1 file** (`ExampleUnitTest.kt` — placeholder) |
| Instrumented tests | **1 file** (`ExampleInstrumentedTest.kt` — placeholder) |
| UI tests | **0** |
| Test coverage | **~0%** |
| Module tests (notification, iam, bleWrapper) | **0** |

**Test frameworks available (in libs.versions.toml):**
- JUnit 4.13.2
- AndroidX Test JUnit 1.1.5
- Espresso 3.5.0
- Compose UI Test JUnit4

**Recommendations:**
- **Unit tests (Priority 1):** Reducers (pure functions, easiest to test), repositories, services, ViewModels
- **Integration tests (Priority 2):** Room DAOs, DataStore operations, API response parsing
- **UI tests (Priority 3):** Compose UI tests for critical flows (login, entry, dashboard)
- **E2E tests (Priority 4):** Full user journeys with Espresso or Maestro

**Tools needed:**
- MockK or Mockito-Kotlin for mocking
- Turbine for Flow testing
- Robolectric for unit-testing Android framework code
- Compose UI test rules
- JaCoCo or Kover for coverage reporting
- Target: 60%+ coverage for business logic, 80%+ for reducers

---

## 7. CI/CD

**Rating: :x: Not Configured for Android**

- CircleCI configured for iOS only (macOS M1, Xcode 16.0.0)
- **No Android CI pipeline exists**
- No automated builds, tests, or deployments for Android

**Recommendations:**
- Add Android CI job to CircleCI (or GitHub Actions)
- Pipeline stages: lint -> build -> unit test -> instrumented test -> deploy
- Integrate Detekt/ktlint reports
- Add APK artifact archiving
- Add test coverage reporting (JaCoCo/Kover)
- Consider Fastlane for release automation

---

## 8. Code Smells (SonarQube-equivalent)

**Rating: :warning: Needs Improvement**

### Dead Code
| Item | Location | Details |
|------|----------|---------|
| `getVisibleMetricKeys()` | `DashboardService.kt:128` | Marked `// TODO: no use` |
| `getVisibleMilestoneKeys()` | `DashboardService.kt:138` | Marked `// TODO: no use` |
| `resetVisibleMetricKeys()` | `DashboardService.kt:241` | Marked `// TODO: Not in use` |
| `resetVisibleMilestoneKeys()` | `DashboardService.kt:252` | Marked `// TODO: Not in use` |
| `deleteAllEntriesForAccount()` | `EntryRepository.kt:121` | Commented-out implementation |

### Incomplete TODO Comments (26+ instances)
| Location | TODO |
|----------|------|
| `ResponseInterceptor.kt:32-44` | HTTP_FORBIDDEN, HTTP_BAD_REQUEST, HTTP_INTERNAL_ERROR — no handlers |
| `AppStatusService.kt` | `isMetric = false // TODO: Get from user preferences` |
| `ScaleDetailsViewModel.kt` | `// TODO: need to implement download option` |
| `EntryHelper.kt` | `accountId = "TODO"`, `deviceId = "TODO"` — placeholder strings |
| `EntryRepository.kt:88` | `//TODO: This is not used at the moment` |

### Code Duplication
- **764 LaunchedEffect instances** — no centralized reusable effect helpers
- Form validation patterns repeated across features (similar `FormValidations.required()` chains)
- Try-catch error handling not standardized across repositories (50 blocks, each with own pattern)
- No common `Result<T>` wrapper for API responses

### Magic Numbers
- `30 * 1000` (30s timeout) in `AppViewModel.kt:229` — should be named constant
- `1000` (1s delay) in `AppViewModel.kt:378` — unclear purpose

---

## 9. Documentation

**Rating: :white_check_mark: Good**

- README files in key packages: `core/`, `domain/`, `features/common/helper/form/`
- KDoc headers present on repository interfaces and feature files
- `CLAUDE.md` provides comprehensive project guidance
- `docs/database-schema.md` and `docs/account-switching-flow.md` exist
- Feature-level Strings objects serve as implicit documentation

**Gaps:**
- No API documentation (Swagger/OpenAPI)
- No architecture decision records (ADRs)
- Complex business logic in some reducers lacks inline comments

---

## 10. Comment Standards

**Rating: :warning: Needs Improvement**

- KDoc on interfaces: Good
- 26+ TODO/FIXME comments with no tracking system
- Some commented-out code blocks (e.g., `EntryRepository.kt:121`, analytics initialization)
- No standardized comment format enforced

**Recommendations:**
- Link TODOs to Jira tickets (e.g., `// TODO(MA-XXXX): ...`)
- Remove commented-out code — use git history instead
- Establish comment guidelines in CLAUDE.md

---

## 11. Logging

**Rating: :white_check_mark: Excellent**

- Custom `AppLog` wrapper used consistently (zero `android.util.Log` imports)
- TAG constants properly defined in ViewModels and services
- Supports `d`, `e`, `w`, `i` levels
- `LogEntity` stored in Room for persistence
- `LogDao` for querying logs
- No sensitive data logged in network interceptors

**Minor concern:**
- Network error messages in `AuthTokenInterceptor.kt:196-197` could expose connection details in debug builds

---

## 12. Observability

**Rating: :x: Critical Gap**

| Tool | Status |
|------|--------|
| Firebase Crashlytics | **Not integrated** |
| Firebase Analytics | **Commented out** (initialization disabled in `AppInitializer.kt`) |
| Firebase Performance Monitoring | **Not integrated** |
| Sentry | **Not integrated** |
| Remote Config | **Not integrated** |

- Only Firebase FCM (push notifications) is active
- No crash reporting means production issues are invisible
- No analytics means no usage insights

**Recommendations:**
- **Priority 1:** Integrate Firebase Crashlytics for crash reporting
- **Priority 2:** Enable Firebase Analytics (already partially set up)
- **Priority 3:** Add Firebase Performance Monitoring for network/rendering metrics
- **Priority 4:** Consider Firebase Remote Config for feature flags

---

## 13. Industry-Standard Plugins

**Rating: :warning: Partially Configured**

### Currently Used
- AGP 8.13.1, Kotlin 2.1.21, KSP 2.1.21, Hilt 2.56.2, Google Services 4.4.3 — all current
- Detekt 1.23.7 (but underconfigured)
- Protobuf 0.9.5

### Missing (Recommended)
| Plugin | Purpose |
|--------|---------|
| **ktlint / Spotless** | Code formatting enforcement |
| **JaCoCo / Kover** | Test coverage reporting |
| **Dependency Guard** | Dependency change tracking |
| **OWASP Dependency-Check** | Vulnerability scanning |
| **Firebase Crashlytics Gradle Plugin** | Crash mapping for obfuscated builds |
| **Baseline Profiles** (Jetpack Macrobenchmark) | Startup/runtime performance |
| **Leak Canary** (debug) | Memory leak detection |

---

## 14. Old/Unsupported Plugins

**Rating: :warning: Monitor**

| Library | Version | Concern |
|---------|---------|---------|
| Navigation3 | 1.0.0-alpha05 | **Pre-release alpha** — API may change |
| Material3 | 1.5.0-alpha06 | **Alpha** — should upgrade when stable |
| Espresso | 3.5.0 | Marked `"Dont update"` in version catalog — investigate why |
| OkHttp | 4.12.0 | OkHttp 5.x is available (Kotlin-first) |

---

## 15. Code Simplification

**Rating: :warning: Opportunities Exist**

- **Dead methods in DashboardService** — 4 unused methods to remove
- **Commented-out code** in EntryRepository, AppInitializer — should be deleted
- **Manual CoroutineScope management** in DashboardService (lines 43, 75) — risk of coroutine leaks; should use structured concurrency
- **LaunchedEffect proliferation** (764 instances) — extract common patterns into reusable composable helpers
- **No Result wrapper** — repositories each handle errors differently; a sealed `Result<T>` would simplify

---

## 16. Unused Functions/Variables/Files

**Rating: :warning: Cleanup Needed**

- 4 unused methods in `DashboardService.kt` (marked with TODO comments)
- `deleteAllEntriesForAccount()` in `EntryRepository.kt` — commented-out implementation
- Placeholder values: `accountId = "TODO"`, `deviceId = "TODO"` in `EntryHelper.kt`
- `isMetric = false` hardcoded in `AppStatusService.kt` instead of reading from preferences
- ~20+ unused imports across various files

---

## 17. Authentication

**Rating: :white_check_mark: Secure**

- Bearer token authentication with proactive refresh (5-min buffer)
- Thread-safe token refresh using Mutex
- Multi-account support with account ID headers
- Token stored in encrypted Protobuf DataStore
- 401 handling with `TokenAuthenticator` for automatic retry
- Proper token clearing on logout
- No biometric/PIN authentication (not required for current scope)

---

## 18. Jetpack Compose Utilization

**Rating: :white_check_mark: Excellent**

- Full Compose UI (no XML layouts)
- Compose BOM 2025.06.01 (latest)
- `@PreviewTheme` annotation used consistently across all previews
- All previews wrapped in `MeAppTheme { ... }`
- Shared components in `features/common/components/` (155 files)
- `AppButton`, `AppInput`, `AppDialog`, `AppChip`, `AppBar`, etc. — comprehensive component library
- Proper use of `remember`, `LaunchedEffect`, `rememberCoroutineScope`

**Minor improvements:**
- Consider `derivedStateOf` for computed values in some composables
- Some infinite transitions could benefit from `onDispose` cleanup

---

## 19. Navigation Utilization

**Rating: :white_check_mark: Good (with caveat)**

- Navigation3 (AndroidX) with sealed class routes (`AppRoute`)
- All routes `@Serializable` for type-safe argument passing
- Nested sealed classes for route categories: `Init`, `Main`, `Auth`, `AccountSettings`, `ScaleDetails`
- `navigationService.navigateTo(route)` / `navigateBack()` via injected service
- No string-based navigation
- Dialog queue service for global dialogs

**Caveat:** Navigation3 is in **alpha05** — API may change before stable release. Monitor for breaking changes.

---

## 20. Dependency Injection (Hilt)

**Rating: :white_check_mark: Excellent**

- 15+ Hilt modules: `APIModule`, `DatabaseModule`, `NetworkModule`, `ServiceModule`, `RepositoryModule`, etc.
- `@HiltViewModel` for all ViewModels
- `@AndroidEntryPoint` on Activity
- `@EntryPoint` for non-injectable classes (`LogManagerEntryPoint`)
- Constructor injection preferred throughout
- `@Singleton` scope applied correctly
- Interface-to-implementation bindings via `@Binds`

---

## 21. Coroutines Utilization

**Rating: :white_check_mark: Good**

- All async work via `viewModelScope.launch { }`
- `suspend` functions for all repository/service methods
- `Flow` for reactive data streams
- No `runBlocking` or `Thread.sleep` in production code
- Proper scope management prevents leaks

**Minor issue:**
- Manual `CoroutineScope` creation in `DashboardService` (lines 43, 75) — should use structured concurrency

---

## 22. Retrofit Utilization

**Rating: :white_check_mark: Excellent**

- Retrofit 3.0.0 (latest)
- All API methods are `suspend` functions (42+ methods across 14+ interfaces)
- `AuthTokenInterceptor` for Bearer auth
- `TokenAuthenticator` for 401 retry
- `NetworkInterceptor` for connectivity check
- `ResponseInterceptor` for response code handling (though some handlers incomplete)
- Network timeouts properly configured in `NetworkConfig.kt` (15s connect, 30s read/write)

---

## 23. Room Utilization

**Rating: :white_check_mark: Good**

- 18 entities, 4 DAOs, proper type converters
- Database view (`ActiveEntryEntity`)
- `fallbackToDestructiveMigration(false)` — will fail on schema change without migration
- Custom callback for `IonicMigrationWorker` on first creation
- Database version 1 — no migrations needed yet

**Recommendation:** Plan migration strategy before adding schema changes.

---

## 24. Layer Abstraction

**Rating: :white_check_mark: Correct**

- `domain/repository/` — interfaces only (no implementation)
- `data/repository/` — implementations
- `domain/services/` — interfaces
- `core/service/` — implementations
- `data/api/` — Retrofit interfaces (network calls only)
- `features/` — UI layer (composables + ViewModels)
- No business logic leaking into API repositories

---

## 25. Hardcoded Secrets

**Rating: :x: Critical**

- **GitHub PAT** in `settings.gradle.kts` — must be revoked and externalized
- Firebase API key in `google-services.json` — acceptable (restricted via Firebase Console)
- No other passwords, API keys, or sensitive data found in code

---

## 26. Component Previews

**Rating: :white_check_mark: Excellent**

- `@PreviewTheme` annotation used universally
- All previews wrapped in `MeAppTheme { ... }`
- Preview data uses inline sample values (not production data)
- Common components all have comprehensive previews

---

## 27. Shared Components

**Rating: :white_check_mark: Excellent**

- 155 files in `features/common/`
- Comprehensive library: buttons, inputs, dialogs, pickers, charts, bars, loaders, etc.
- Each component supports multiple variants via enums (e.g., `ButtonType` has 10+ variants)
- No significant duplication found across features

---

## 28. Helper Functions

**Rating: :warning: Some Duplication**

- Form validation helpers exist in `features/common/helper/form/`
- `DeviceHelper.kt` — well-organized with named constants
- **Duplication found:** Try-catch patterns in repositories not standardized
- **Missing:** Common error handling wrapper, API response wrapper
- LaunchedEffect patterns repeated — could extract common effects

---

## 29. Constants and Magic Values

**Rating: :warning: Mostly Good, Some Gaps**

- Network config properly uses named constants (`CONNECT_TIMEOUT_SECONDS`, `READ_TIMEOUT_SECONDS`)
- Auth interceptor uses named constants (`MAX_RETRY_ATTEMPTS`, `RETRY_DELAY_MS`)
- Device SKUs use named constants in `DeviceHelper.kt`
- **Gaps:** `30 * 1000` and `1000` delays in `AppViewModel.kt` without named constants
- Placeholder `"TODO"` strings in `EntryHelper.kt`

---

## 30. Theme and Variable Usage

**Rating: :white_check_mark: Excellent**

- Comprehensive theme system: `MeTheme.colorScheme`, `.typography`, `.spacing`, `.borderRadius`, `.animation`
- Custom `ColorPalette`, `ColorScheme`, `ColorTokens` for full token coverage
- 249+ theme token usages across features
- No hardcoded hex colors in composables
- `Color.Transparent` used appropriately

---

## 31. Dark Theme Handling

**Rating: :white_check_mark: Good**

- `ThemeMode` protobuf enum: LIGHT, DARK, AUTO
- Per-account theme preference stored in DataStore
- Android 12+: `UiModeManager.setApplicationNightMode()`
- Pre-12: `AppCompatDelegate.setDefaultNightMode()`
- `values-night/colors.xml` provides dark variants
- `drawable-night/` for dark drawable variants
- No Material You / Dynamic Color support (uses custom palette instead)

---

## 32. Permission Handling

**Rating: :white_check_mark: Good**

- Runtime permissions handled through UI flows
- `GGPermissionType` and `PermissionState` for permission management
- Notification permission alert shown before system prompt
- HealthConnect permissions managed by dedicated module
- Manifest declares only necessary permissions

---

## 33. Navigation Arguments

**Rating: :white_check_mark: Excellent**

- All routes use `@Serializable` data classes for type-safe arguments
- Example: `ScaleMode(scaleId: String)`, `MetricInfo(entryId: String, metricType: MetricType)`
- No string-based argument passing
- No Bundle manipulation

---

## 34. Jetpack Architecture Components

**Rating: :white_check_mark: Good**

- ViewModel: All extend `BaseIntentViewModel` with Hilt injection
- Lifecycle: Proper lifecycle awareness via Compose
- Room: Well-configured with entities, DAOs, type converters
- DataStore: Protobuf-based with proper serialization
- WorkManager: Used for IonicMigration and background sync
- Navigation: Navigation3 with type-safe routes

---

## 35. Import Cleanup

**Rating: :warning: Needs Cleanup**

- ~20+ files with unused imports identified
- EditorConfig prohibits wildcard imports (threshold: 2147483647) but not enforced in CI
- `LogDao.kt` uses wildcard import `import androidx.room.*`
- No automated import optimization in build pipeline

---

## 36. Code Formatting

**Rating: :warning: Not Enforced**

- `.editorconfig` defines formatting rules (2-space indent, 120-char lines)
- No ktlint or Spotless plugin to enforce formatting
- No CI step to check formatting
- Developers rely on IDE settings (not guaranteed consistent)

---

## 37. Pre-commit and Pre-push Hooks

**Rating: :x: Not Configured**

- Only sample hooks in `.git/hooks/` (no custom hooks)
- No Husky, lint-staged, or similar tooling
- No commitlint for conventional commits
- No pre-commit lint/format checks
- CODEOWNERS file exists (for PR review assignment)

**Recommendations:**
- Add pre-commit hook: run Detekt + ktlint
- Add pre-push hook: run unit tests
- Consider Husky or a Gradle task to install hooks
- Add commitlint for conventional commit messages

---

## 38. Build Types and Flavors

**Rating: :white_check_mark: Adequate**

- **debug:** `BASE_URL = "https://api.weightgurus.com/v3/"`
- **release:** Same BASE_URL, minification + shrinking enabled
- No product flavors (appropriate for single-variant app)
- No staging/QA build type (may want to add for testing)
- No signing config in build.gradle.kts (likely external)
- APK naming: `Weight gurus-{variant}-v{version}({versionCode})-{date}.apk`

**Recommendation:** Consider adding a `staging` build type with staging API URL.

---

## 39. Status Bar and Navigation Bar

**Rating: :white_check_mark: Good**

- Edge-to-edge enabled: `WindowCompat.setDecorFitsSystemWindows(window, false)` + `enableEdgeToEdge()`
- System bar appearance handled by Material3 theming
- Status bar icon color adapts to background luminance

**Minor gap:** No explicit `WindowInsetsCompat` padding configuration found — relies on Material3 defaults.

---

## 40. Splash Screen

**Rating: :white_check_mark: Good**

- Android 12+: `androidx.core:core-splashscreen:1.0.1` with animated icon
- Pre-12: Theme-based splash via `android:windowBackground`
- Exit animation: 300ms fade-out
- Separate light/dark splash colors
- `splash_screen.xml` layer-list drawable

---

## 41. Resource Usage (Colors, Dimensions, Strings, Icons, Fonts)

**Rating: :white_check_mark: Good**

- **Colors:** All via theme tokens, no hardcoded hex in composables
- **Strings:** PascalCase `Strings` objects per feature (code-based, not XML resources)
- **Icons:** Centralized in `AppIcons.kt`, vector drawables preferred
- **Fonts:** Custom fonts in `res/font/` directory
- **Images:** 36 raster images — candidates for WebP conversion
- **Density:** Uses vector drawables (density-independent), some `drawable-night/` variants

**Recommendation:** Convert remaining PNG/JPG assets to WebP for ~25% size reduction.

---

## 42. Animations and Transitions

**Rating: :white_check_mark: Good**

- `rememberInfiniteTransition()` for loaders and pulsing effects
- `AnimatedVisibility()` for conditional visibility
- Navigation transitions: `fadeIn`/`fadeOut` with `tween` specs
- Animation tokens in theme: `MeTheme.animation`
- No blocking animations observed

---

## Priority Matrix

### :x: P0 — Critical (Do Immediately)

1. **Revoke and externalize GitHub PAT** from `settings.gradle.kts`
2. **Integrate Firebase Crashlytics** — zero crash visibility in production
3. **Set up Android CI pipeline** — no automated builds/tests

### :warning: P1 — High Priority (Next Sprint)

4. **Establish testing foundation** — add unit tests for reducers and repositories (target 60%+ coverage)
5. **Enable Detekt default rules** + add ktlint/Spotless for code formatting
6. **Enable Firebase Analytics** (already partially configured)
7. **Add pre-commit hooks** (Detekt + format check)

### :warning: P2 — Medium Priority (Next Quarter)

8. **Remove dead code** — 4 unused methods in DashboardService, commented-out code
9. **Complete error handling** in ResponseInterceptor (3 unhandled HTTP codes)
10. **Resolve 26+ TODO comments** — link to Jira tickets or complete
11. **Standardize error handling** — create sealed `Result<T>` wrapper
12. **Add test coverage reporting** (JaCoCo/Kover)
13. **Add staging build type** with staging API URL
14. **Convert raster images to WebP**

### P3 — Nice to Have (Backlog)

15. Extract common LaunchedEffect patterns into reusable helpers
16. Add certificate pinning for API endpoints
17. Implement Leak Canary for debug builds
18. Add Baseline Profiles for startup performance
19. Add OWASP dependency vulnerability scanning
20. Consider Material You / Dynamic Color support
21. Add Architecture Decision Records (ADRs)

---

## Key Decisions

- **Architecture is solid** — no restructuring needed; focus on testing and tooling gaps
- **Security first** — GitHub PAT revocation is the #1 priority
- **Testing is the biggest gap** — 0% coverage on 732+ files; full foundation approach (unit + integration + UI, 60%+ target)
- **CI/CD for Android is essential** — cannot enforce quality without automated pipelines
- **Linting needs expansion** — Detekt config exists but is effectively disabled
- **Observability is blind** — no crash reporting or analytics in production
- **Stick with custom palette** — no Material You; brand identity takes priority
- **Biometric auth is planned** — add to P1/P2 priority for health data protection
- **Staging build type needed** — staging API exists but isn't wired into Android build
- **Espresso can be updated** — no reason to keep pinned at 3.5.0

---

## Resolved Questions

1. **Where should the GitHub PAT be stored?**
   **Decision:** Both — Gradle properties (`~/.gradle/gradle.properties`) for local dev + environment variables for CI. Read from properties with env var fallback.

2. **Is there a staging API environment?**
   **Decision:** Staging exists but is not yet configured in the Android build. Add a `staging` build type pointing to the staging API URL.

3. **What test coverage target for the first phase?**
   **Decision:** Full foundation — set up unit + integration + UI test infrastructure and target 60%+ coverage across all layers.

4. **Why is Espresso marked "Don't update"?**
   **Decision:** No specific reason; it was pinned and forgotten. Can be updated to latest.

5. **Should the app support Material You (Dynamic Color)?**
   **Decision:** No — stick with custom color palette. Brand colors are important for app identity.

6. **Is biometric authentication planned?**
   **Decision:** Yes — biometric/PIN auth is on the roadmap. Factor into security recommendations and plan accordingly.

## Open Questions

1. **What is the timeline for Navigation3 stable release?** Using alpha05 in production carries API-change risk. Monitor AndroidX release notes.
2. **What is the staging API URL?** Needed to configure the staging build type.

---

---

# Deep-Dive Findings

## A. MVI Pattern & Compose Validation

### A.1 Reducer Purity — PASS

All sampled reducers are **truly pure functions** with no side effects:
- `LoginReducer.kt:85-131` — state.copy() only, returns null for unhandled intents
- `DashboardReducer.kt:81-108` — 15 intent handlers, all pure transformations
- `AddScaleReducer.kt:72-92` — complex state calculations are pure transformations (sorting, mapping)
- `EntryReducer.kt:250+` — properly returns null for unhandled intents

Side effects (API calls, navigation, dialogs) are **correctly separated** into ViewModels via `viewModelScope.launch`:
- `LoginViewModel.kt:83-115` — API calls in viewModelScope, catches exceptions, emits error intents
- `DashboardViewModel.kt:103-115` — routes to specialized side-effect methods
- `EntryViewModel.kt:245-279` — shows loader, calls service, shows toast, navigates back

### A.2 State Immutability — PASS

All state classes are immutable `data class` types updated exclusively via `.copy()`.

### A.3 Intent Definitions — PASS

All intents are properly `sealed class` or `sealed interface` types with typed subtypes.

### A.4 Compose Stability Annotations — NEEDS IMPROVEMENT

| Finding | Status |
|---------|--------|
| State classes lack `@Stable`/`@Immutable` | Missing on LoginState, DashboardState, EntryState |
| FormControl marked `@Stable` | Correct |
| Theme tokens marked `@Stable` | Correct |

**Impact:** Without `@Stable`, Compose must be conservative during recomposition, causing unnecessary recompositions.

**Recommendation:** Add `@Immutable` to all state data classes (they're already immutable via data class + .copy()).

### A.5 Compose Best Practices Issues

| Issue | File | Details |
|-------|------|---------|
| Unused `remember` result | `AppInput.kt:258` | `remember { MutableInteractionSource() }` — result not assigned to a variable |
| `collectAsState()` used instead of `collectAsStateWithLifecycle()` | All screens (e.g., `HomeScreen.kt:49`) | Not lifecycle-aware; collects across ALL lifecycle states |
| Manual CoroutineScope | `HomeViewModel.kt:128-138` | `CoroutineScope(Dispatchers.IO).launch` outside viewModelScope — leak risk |
| AppText parameter ordering | `AppText.kt` | `modifier` is not the first optional parameter (minor) |

### A.6 Compose Practices That Are Correct

- `remember` used correctly across LoginScreen, EntryScreen, DashboardScreen
- `rememberUpdatedState` properly used in AppButton, AppInput for callback updates
- `DisposableEffect` with proper cleanup in DashboardScreen (lifecycle observer)
- `LaunchedEffect` keys are correct (Unit for one-time, state keys for reactive)
- PascalCase naming for all composables
- Content lambdas consistently last parameter
- `@PreviewTheme` used universally with `MeAppTheme { }` wrapper

---

## B. Jetpack Architecture Components

### B.1 Lifecycle Management

| Check | Status | Details |
|-------|--------|---------|
| Flow collection | Uses `collectAsState()` | Should migrate to `collectAsStateWithLifecycle()` |
| viewModelScope | Correct throughout | All ViewModels use viewModelScope properly |
| Manual CoroutineScope | 1 instance found | `HomeViewModel.kt:128` — `CoroutineScope(Dispatchers.IO)` |
| onCleared() overrides | None found | Safe — cleanup handled by framework/Hilt |
| Activity/Context in VMs | Safe | Only passed as parameters, never stored as fields |

### B.2 Data Layer

**Room Database:**
- 18 entities, 4 DAOs, proper `@Transaction` annotations on complex queries
- Flow-based queries for real-time data (e.g., `getLatestEntry(): Flow<PopulatedActiveEntry?>`)
- Suspend functions for one-off queries (e.g., `getUnSynced(): List<PopulatedEntry>`)
- Bulk insert handled carefully — single-row inserts to preserve row IDs for foreign keys
- Migration: `fallbackToDestructiveMigration(false)` — will crash on schema change without migration
- **Risk:** No migration plan for future schema changes (currently version 1)

**DataStore:**
- 6 Protobuf-based DataStores, all properly scoped
- Flow-based reads, suspend writes
- Immutable protobuf builders for updates

**Caching:**
- Room database serves as primary cache
- Repositories fall back to cached data on network errors
- `distinctUntilChanged()` used to prevent redundant emissions

**Offline Handling:**
- Entries tracked with `isSynced` flag and `operationType`
- Soft deletes for sync compatibility
- Retry logic with attempt counter and `getFailedOperations()` query
- `clearUnSynced()` for cleanup after successful sync

**Error Handling:**
- `HttpErrorResponse` utility for status code classification
- Repository-level try-catch with AppLog
- Exceptions rethrown to ViewModel callers
- **Gap:** No sealed `Result<T>` wrapper — each repository handles errors differently

### B.3 Navigation

- **Type-safe routes:** All `@Serializable` sealed classes
- **Back stack:** Separate stacks per navigation context via `TopLevelBackStack`
- **Tab state preservation:** Navigation3 decorators: `rememberSavedStateNavEntryDecorator()` + `rememberViewModelStoreNavEntryDecorator()`
- **Zero-animation tab switching:** `tween(0)` for instant tab changes
- **Auth guard:** State-driven (LoadingScreen checks login, AppViewModel redirects) — implicit but functional
- **Dialogs:** Managed via `DialogQueueService`, not in navigation stack
- **Deep linking:** No custom URI scheme configured — only launcher intent

### B.4 WorkManager

- `IonicMigrationWorker` — one-time worker for legacy database migration, triggered on Room database creation
- `HealthConnectSyncWorker` — **stub only** (TODO: implement background sync)
- No explicit constraints on workers

---

## C. Testing & CI/CD Strategy

### C.1 Dependencies to Add

| Dependency | Version | Purpose |
|-----------|---------|---------|
| MockK | 1.13.15 | Kotlin-first mocking |
| Turbine | 1.1.0 | Flow testing |
| Robolectric | 4.13.1 | Android framework for unit tests |
| kotlinx-coroutines-test | 1.9.1 | Coroutine testing |
| Room testing | 2.7.2 | In-memory database tests |
| Hilt testing | 2.56.2 | DI for tests |
| Kover | 0.8.7 | Code coverage reporting |
| Spotless | 6.25.0 | Code formatting (wraps ktlint 1.2.1) |
| OWASP Dependency-Check | 10.0.4 | Vulnerability scanning |

### C.2 Test Folder Structure

```
app/src/test/.../weight/
  _fixtures/         # Shared test data, fakes, dispatchers
  features/
    dashboard/
      DashboardReducerTest.kt    # Pure reducer tests
      DashboardViewModelTest.kt  # ViewModel with MockK + Turbine
    login/
      LoginReducerTest.kt
      LoginViewModelTest.kt
    ...
  data/
    repository/
      AccountRepositoryTest.kt   # MockK API + DAO
      EntryRepositoryTest.kt
  core/
    network/
      TokenManagerTest.kt

app/src/androidTest/.../weight/
  data/storage/db/
    AccountDaoTest.kt    # In-memory Room
    EntryDaoTest.kt
  features/
    DashboardScreenTest.kt  # Compose UI tests
```

### C.3 Test Priority

1. **Reducers** (pure functions, highest ROI, no mocking needed)
2. **Repositories** (MockK API + DAO, test mapping/error handling)
3. **ViewModels** (MockK services + Turbine for state verification)
4. **DAOs** (in-memory Room database)
5. **Compose UI** (screen-level interaction tests)

### C.4 CircleCI Android Pipeline

Using `circleci/android` orb with these stages:
1. **lint** — `./gradlew lint detekt spotlessCheck`
2. **build** — `./gradlew clean assembleDebug` (with Gradle caching)
3. **unit-tests** — `./gradlew test` (store test results + artifacts)
4. **instrumented-tests** — `./gradlew connectedAndroidTest` (on emulator)
5. **coverage-report** — `./gradlew koverHtmlReport` (upload to Codecov)
6. **code-quality** — `./gradlew dependencyCheckAnalyze`

### C.5 Detekt Enhanced Configuration

Expand from 1 rule to full rule sets:
- `complexity` — CyclomaticComplexity (threshold: 12), LongMethod (50), LongParameterList (6)
- `coroutines` — GlobalCoroutineUsage, RedundantSuspendModifier
- `exceptions` — SwallowedException, TooGenericExceptionCaught
- `naming` — InvalidPackageDeclaration, MatchingDeclarationName
- `performance` — ArrayPrimitive, ForEachOnRange
- `potential-bugs` — UnsafeCallOnNullableType, UnreachableCode, DataFlowIssue
- `style` — CollapsibleIfStatements, UseDataClass, UnnecessaryAbstractClass

### C.6 Pre-commit Hook Setup

Gradle task to install git hooks:
```
./gradlew installGitHooks
```
Pre-commit: `./gradlew spotlessCheck detekt`
Pre-push: `./gradlew test`

---

## Updated Priority Matrix

### P0 — Critical (Do Immediately)

1. **Revoke and externalize GitHub PAT** from `settings.gradle.kts`
2. **Integrate Firebase Crashlytics** — zero crash visibility in production
3. **Set up Android CI pipeline** on CircleCI

### P1 — High Priority (Next Sprint)

4. **Establish testing foundation** — MockK + Turbine + Kover, start with reducer tests (target 60%+)
5. **Enable Detekt default rules** + Spotless + ktlint for formatting enforcement
6. **Enable Firebase Analytics** (already partially configured)
7. **Add pre-commit hooks** (Detekt + spotlessCheck)
8. **Migrate `collectAsState()` to `collectAsStateWithLifecycle()`** across all screens
9. **Add `@Immutable` annotations** to all state data classes
10. **Add staging build type** with staging API URL

### P2 — Medium Priority (Next Quarter)

11. **Remove dead code** — 4 unused methods in DashboardService, commented-out code
12. **Fix unused `remember` in AppInput.kt:258**
13. **Fix manual CoroutineScope in HomeViewModel.kt:128**
14. **Complete error handling** in ResponseInterceptor (3 unhandled HTTP codes)
15. **Standardize error handling** — create sealed `Result<T>` wrapper
16. **Resolve 26+ TODO comments** — link to Jira tickets or complete
17. **Plan Room migration strategy** for future schema changes
18. **Implement biometric/PIN authentication**
19. **Convert raster images to WebP**

### P3 — Backlog

20. Extract common LaunchedEffect patterns into reusable helpers
21. Add certificate pinning for API endpoints
22. Implement LeakCanary for debug builds
23. Add Baseline Profiles for startup performance
24. Add OWASP dependency vulnerability scanning in CI
25. Implement HealthConnectSyncWorker (currently stub)
26. Add deep linking URI scheme
27. Add Architecture Decision Records (ADRs)

---

## Next Steps

Run `/workflows:plan` to create an implementation plan addressing the priority items above.
