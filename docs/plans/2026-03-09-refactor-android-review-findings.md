# Technical Review Findings: Android Quality Improvement Plan

**Plan reviewed**: `docs/plans/2026-03-09-refactor-android-comprehensive-quality-improvement-plan.md`
**Review date**: 2026-03-09
**Reviewers**: Architecture Strategist, Security Sentinel, Spec Flow Analyzer, Performance Oracle, Code Simplicity Reviewer

---

## P1 — Critical (Must fix before execution)

These issues will cause build failures, security incidents, or runtime crashes if not addressed.

---

### F-01: PAT rotation order is backwards — will break all builds

**Source**: Spec Flow Analyzer, Security Sentinel, Architecture Strategist

The plan says "Revoke the exposed token FIRST." Revoking before distributing the replacement creates a window where every developer's Gradle sync fails and the CI pipeline is broken.

**Correct order**:
1. Generate new token
2. Distribute `~/.gradle/gradle.properties` instructions to team
3. Verify at least one developer can build successfully
4. Revoke old token only after confirmation

Also: `settings.gradle.kts` has **two** private Maven repos with the hardcoded PAT (ggBluetoothNativeLibrary and vico), not one as stated in the plan. Both the token AND the username (`Selva-GG`) must be externalized from both blocks.

**Effort**: 30 min to fix plan text; 1 hour for actual execution coordination

---

### F-02: ProGuard rules missing for EncryptedSharedPreferences

**Source**: Architecture Strategist

Task 1.5 proposes `androidx.security:security-crypto:1.1.0-alpha06`. This library uses Tink cryptographic classes internally. Release builds have `isMinifyEnabled = true`. Without explicit ProGuard/R8 keep rules for Tink, the app **will crash in release builds** when attempting to initialize `EncryptedSharedPreferences`.

Must add to Task 1.5:
```proguard
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
```

Also reconsider alpha dependency: `security-crypto:1.0.0` (stable) supports `EncryptedSharedPreferences` without the alpha risk. The key advantage of `1.1.0-alpha06` is encrypted DataStore support, but the plan is migrating to `SharedPreferences` anyway. Use stable `1.0.0`.

**Effort**: 30 min to add rules; 1 hour to evaluate stable vs. alpha

---

### F-03: ExampleUnitTest.kt will break CI immediately

**Source**: Spec Flow Analyzer, Architecture Strategist

`ExampleUnitTest.kt` contains `MainBottomNavTest` which uses `createComposeRule()` — an instrumented test API that requires the Android test runner. The file lives in `src/test/` (unit test directory) and will fail as soon as the CI unit test job runs.

Additionally, `testItemSelectionCallback` asserts `selectedIndex == 1` but never wires the callback, so `selectedIndex` stays at -1 — the assertion uses `assert()` not `assertEquals()` so it passes silently now, but will fail with a real test setup.

**Fix**: Delete `ExampleUnitTest.kt` (or move `MainBottomNavTest` to `src/androidTest/`) as a prerequisite for Task 1.6, not during it.

**Effort**: 30 min

---

### F-04: ResponseInterceptor is dead code — Task 3.3 is blocked

**Source**: Architecture Strategist

`ResponseInterceptor` is provided via Hilt in `NetworkModule.kt` but is **never added** to the OkHttpClient interceptor chain (lines 149-174). The chain adds `networkInterceptor`, `baseUrlInterceptor`, `authTokenInterceptor`, and `tokenAuthenticator` — but not `ResponseInterceptor`.

Task 3.3 ("Complete ResponseInterceptor Error Handling") plans to implement the TODO handlers inside this interceptor, but the interceptor is currently unreachable. The plan must note this as a prerequisite: wire the interceptor into the chain before implementing its handlers.

**Effort**: 15 min to note in plan; 1 hour to wire during Task 3.3

---

### F-05: lifecycle-runtime-compose is not in the Compose BOM

**Source**: Architecture Strategist, Performance Oracle

Task 2.1 says `lifecycle-runtime-compose` will be added "via BOM." The Compose BOM does **not** include this artifact — it is part of the Lifecycle library, not the Compose BOM. Without the explicit dependency, Task 2.9 (collectAsStateWithLifecycle migration) will not compile.

Add to `libs.versions.toml`:
```toml
# Already has: lifecycleRuntimeKtx = "2.9.1"
# Add:
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeKtx" }
```

**Effort**: 15 min to update plan and version catalog

---

### F-06: google-services.json has no CI strategy

**Source**: Architecture Strategist, Spec Flow Analyzer, Security Sentinel

The file `Android/app/google-services.json` is committed to the repo. For Crashlytics (Task 1.3) to work in CI, this file must be accessible. The iOS pipeline already handles `GoogleService-Info.plist` via a `GOOGLESERVICE_INFO_BASE64` CircleCI environment variable. Android needs the same pattern, or the plan must confirm the file is committed and call out that it should not contain secrets beyond what Google considers safe.

Action required: verify Firebase security rules on the associated project are restrictive.

**Effort**: 1 hour to set up CI secret; 30 min to verify Firebase rules

---

### F-07: Token encryption missing MasterKey failure and key invalidation handling

**Source**: Security Sentinel, Spec Flow Analyzer

Task 1.5 describes the migration flow ("read from old, write to new, delete old") but is missing:

1. **MasterKey creation failure**: Android Keystore can fail on Samsung devices after OS updates, or on devices restored from backup to different hardware. If `MasterKey` init fails, the app must not crash — it must force re-login gracefully and log to Crashlytics.
2. **Key invalidation**: If the user changes their device lock screen, `EncryptedSharedPreferences` will throw a `GeneralSecurityException` on the next read. This must be treated as a forced re-login, not an unhandled exception.
3. **Rollback gap**: "Keep old until new is verified readable" before deleting old storage.

**Effort**: 2 hours to add failure handling code; 30 min to update plan

---

### F-08: collectAsState() count wrong — :iam module instances will be missed

**Source**: Spec Flow Analyzer

The plan states "82 instances in 40 files." Actual count is 88 occurrences across 43 files — including 3 files in the `:iam` module (`IamFeedLandingScreen.kt`, `FeedMessagesSettingsScreen.kt`, `FeedMessagesScreen.kt`) which use a different package (`com.greatergoods.ggInAppMessaging`). A search scoped to `com.dmdbrands.gurus.weight` will miss them entirely.

The plan must either explicitly include `:iam` in Task 2.9's scope, or explicitly exclude it and note the deferral.

**Effort**: 15 min to update plan scope statement

---

### F-09: HttpLoggingInterceptor logs auth tokens at BODY level

**Source**: Architecture Strategist, Security Sentinel

`NetworkModule.kt` line 59 sets `HttpLoggingInterceptor.Level.BODY` for debug builds. This logs full request/response bodies including `Authorization: Bearer <token>` headers on every API call. Task 1.2 addresses PII in `AppLog` calls, but the OkHttp interceptor operates independently of `AppLog`.

Add to Task 1.2: configure `HttpLoggingInterceptor` to use `HEADERS` level (which redacts sensitive headers) or add a custom `HttpLoggingInterceptor` redaction function.

**Effort**: 1 hour

---

### F-10: Task 1.5 should be ordered before Tasks 1.3 and 1.4

**Source**: Security Sentinel

From a security standpoint, encrypting credentials actively exploitable on production devices is more urgent than Crashlytics or Analytics setup. If Crashlytics is enabled before token encryption and a crash occurs during token read, the plaintext token could appear in crash context.

Corrected P0 order:
1. Task 1.1 — PAT externalization
2. Task 1.2 — PII sanitization
3. Task 1.5 — Token encryption *(move up)*
4. Task 1.3 — Crashlytics (depends on 1.2)
5. Task 1.4 — Analytics
6. Task 1.6 — CI pipeline

**Effort**: 15 min to reorder plan

---

## P2 — Important (Significantly affects correctness, scope, or safety)

---

### F-11: Phase 2 timeline labeled "Next Sprint" — actually 11–14 weeks

**Source**: Architecture Strategist, Spec Flow Analyzer, Code Simplicity Reviewer

Phase 2 contains: 35 reducer tests (~2 weeks), 15 repo + 25 service tests (~3-4 weeks), 10 ViewModel tests (~4-5 weeks), plus Detekt/Spotless, Lefthook, collectAsStateWithLifecycle, @Stable, staging build type, CoroutineScope audit, certificate pinning, Baseline Profiles, CI quality gates.

Architecture Strategist estimate: 8–10 weeks (1 dev) or 5–6 weeks (2 devs).
Spec Flow conclusion: "Not a sprint — it's a quarter."

**Recommendation**: Rename "Next Sprint" → "Next Quarter." Split into:
- Phase 2a: Test Infrastructure + Tooling (Kover, test deps, Lefthook, Detekt baseline)
- Phase 2b: First 20 Reducer + Repository tests
- Phase 2c: Compose lifecycle + @Stable + CoroutineScope + Security

**Effort**: 30 min to restructure plan text

---

### F-12: Task 2.12 (CoroutineScope) must soft-depend on Tests 2.3/2.5

**Source**: Architecture Strategist

Changing scope management across 11+ service files without any test coverage is high-risk. The dependency table should add:

| Task 2.12 | Soft-blocked by Tasks 2.3 and 2.5 | Scope timing changes require regression tests |

Also: CoroutineScope count is **27 occurrences across 11 files** (not "80+"). The "80+" likely counted all `launch {}` calls. The acceptance criteria must clarify exactly which pattern is being remediated.

Additional correctness issues in Task 2.12 (Performance Oracle):
- `AccountService` cancel-and-recreate is a **race condition**: any coroutine launched between cancel and recreate is silently dropped. Fix: use `SupervisorJob` with child jobs instead of scope recreation.
- `EntryService` and `EntryAggregationService` are non-Hilt-managed classes. The `LoggingModule` pattern (singleton scope, no cancellation) is intentional for singletons but inappropriate here. Need a different pattern (factory or `@ServiceScope` qualifier).

**Effort**: 30 min to update plan; 2–4 hours for correct implementation patterns

---

### F-13: Detekt baseline generation must happen AFTER Spotless reformatting

**Source**: Spec Flow Analyzer, Architecture Strategist

If Task 2.7 generates a Detekt baseline before Spotless reformats existing files, the baseline line numbers will shift after reformatting — causing previously-baselined violations to reappear as "new."

Correct order within Task 2.7:
1. Apply Spotless to ALL existing code
2. THEN generate Detekt baseline

The `bleWrapper` Detekt exclusion must also be preserved when baseline is generated, to avoid including third-party violations.

**Effort**: 15 min to fix task order in plan

---

### F-14: CoroutineScope remediation in AccountService is a race condition, not a style issue

**Source**: Performance Oracle

`AccountService` (line 95-96) cancels and recreates a `CoroutineScope`. This creates a race: any coroutine launched between cancel and recreate is silently dropped. With account switching, orphaned collectors on Room/Flow hold database connections. After 10 account switches, dozens of orphaned collectors accumulate.

The plan's acceptance criteria ("services recreate scopes properly cancel previous scope") are insufficient. The fix requires structured concurrency — one `SupervisorJob` per service lifecycle, with child jobs, not scope recreation.

**Effort**: 3–5 hours per affected service; document in plan

---

### F-15: @Stable on List-containing classes still shows as unstable

**Source**: Performance Oracle

Kotlin's `List<T>` interface is treated as **unstable** by the Compose compiler regardless of `@Stable` on the containing class. State classes like `DashboardState` (contains `List<DashboardKey>`) and `EntryState` will still show unstable parameters in Compose compiler reports.

Solutions (pick one):
1. Add `kotlinx.collections.immutable` (`ImmutableList`/`PersistentList`) for hottest composables (Dashboard, History)
2. Add a Compose compiler [stability configuration file](https://developer.android.com/develop/ui/compose/performance/stability/diagnose#configuration-file) to declare `kotlin.collections.List` as stable

Currently zero usage of `kotlinx.collections.immutable` in the codebase.

**Effort**: 2–4 hours to add library and wrap list fields in hottest composables

---

### F-16: LazyColumn/LazyRow missing stable key parameters

**Source**: Performance Oracle

13 of 17 lazy layout usages lack stable `key` parameters. Without keys, Compose recomposes all visible items on any list change. Affected hot paths: `HistoryList.kt` (many months of data), `DashboardMilestoneGrid`, `DashboardMetrics`.

Files with keys: `AppSwipableList`, `ScaleList`, `AppDraggableList`, `AppDraggableList` only.

Add to Task 2.10 or Task 3.2 as an explicit sub-task.

**Effort**: 2–4 hours to audit and add keys

---

### F-17: Detekt pre-commit hook will take 20–60 seconds, not <10 seconds

**Source**: Performance Oracle

The `lefthook.yml` example runs `./gradlew detekt` — this analyzes all 732+ files. Gradle startup alone is 5–10 seconds. The plan's "Pre-commit hook completes in < 10 seconds" acceptance criterion is not achievable with this configuration.

Options:
1. Use Detekt CLI directly on staged files (binary from GitHub releases)
2. Move Detekt to CI-only; keep only Spotless in pre-commit hook

**Effort**: 1 hour to reconfigure

---

### F-18: Reducer class count is incorrect

**Source**: Architecture Strategist (35 classes), Spec Flow Analyzer (~30 classes)

The plan references "36+ reducers" throughout Tasks 2.3 and 2.10. Actual count is 30–35 (Architecture Strategist found 35 with a regex on `class.*Reducer.*IReducer`; Spec Flow found ~30 concrete classes). Update all references and recalibrate coverage estimates accordingly.

**Effort**: 15 min

---

### F-19: WebView loads untrusted URLs with JavaScript enabled

**Source**: Security Sentinel

`WebViewScreen.kt` has `javaScriptEnabled = true` (line 194) and `InAppWebViewActivity` loads URLs from `intent.getStringExtra(EXTRA_URL)` without validation. Although the Activity is `android:exported="false"`, any internal code path that passes an attacker-influenced URL enables JS execution.

Fixes needed:
1. Validate URL scheme: reject anything that is not `https://`
2. Implement a domain allowlist (only `weightgurus.com` and trusted domains)
3. Add to Task 3.2 or as a new security task

**Effort**: 2 hours

---

### F-20: SimpleDateFormat thread safety in token interceptors

**Source**: Security Sentinel, Architecture Strategist

`AuthTokenInterceptor` (line 44) and `TokenAuthenticator` (line 47) use class-level `SimpleDateFormat` instances. `SimpleDateFormat` is not thread-safe. OkHttp interceptors are called from multiple threads simultaneously. This can cause incorrect token expiration calculations — silently using expired tokens or refusing valid ones.

**Fix**: Replace with `java.time.format.DateTimeFormatter` (thread-safe, available from API 26 = minSdk).

**Effort**: 1 hour; add to Task 2.12 or Task 3.3

---

### F-21: Debug builds pointing at production API is understated as a risk

**Source**: Security Sentinel

The debug `network_security_config.xml` includes `<certificates src="user" />`, which trusts user-installed CA certificates. Combined with the production API URL in debug builds, this means any developer's debug build can be MITM'd while talking to the production API — real user data at risk.

This is elevated beyond the "staging build type" concern. It should be P0 or early P1, not deferred to Task 2.11.

**Effort**: 1–2 days to set up staging environment; escalate priority in plan

---

### F-22: EntryAggregationService calls suspend function inside combine

**Source**: Performance Oracle

`EntryAggregationService.kt` line 126, inside a `combine` block:
```kotlin
val account = accountRepository.getActiveAccount().first()
```
This creates a new flow collection and fires a Room query on **every emission** of the combined flow (which combines 7+ upstream flows). This should be lifted as an upstream to `combine`.

**Effort**: 1 hour to fix; note in plan as a Task 2.12 or 3.x finding

---

### F-23: Missing prioritization of which reducers/repos to test first

**Source**: Spec Flow Analyzer

The plan says "test ALL 30 reducers" then "ALL 15 repos" with no risk-based ordering. If time runs out, the highest-risk code must already be covered.

Recommended priority order (highest risk first):
- `LoginReducer`, `AccountRepository`, `EntryRepository`, `DashboardReducer`
- Then service layer: `TokenManager`, `AccountService`, `EntryService`
- Last: `DebugMenuReducer`, `HelpReducer`, etc.

**Effort**: 30 min to add priority table to plan

---

### F-24: Lefthook missing Windows installation instructions

**Source**: Spec Flow Analyzer

The plan shows `brew install lefthook` (macOS) and npm install, but this project is developed on Windows (current environment is `win32`). Missing:
- `scoop install lefthook` or `choco install lefthook`
- Note that `{staged_files}` uses Unix paths — may not resolve in Git Bash on Windows
- Gradle property file path on Windows: `%USERPROFILE%\.gradle\gradle.properties`
- The `root: "Android/"` and `./gradlew` commands need Windows path verification

**Effort**: 30 min

---

### F-25: No team coordination plan for Phase 1 changes

**Source**: Spec Flow Analyzer

Phase 1 affects every developer immediately: PAT rotation requires updating local gradle.properties; git history rewrite requires re-cloning. There is no communication plan, no Slack message template, no coordination timeline. Add a "Team Communication" section to each Phase 1 task.

Also: Lefthook requires every developer to run `lefthook install` after pulling. If not automated, adoption is voluntary. Consider adding a Gradle task that auto-runs `lefthook install`.

**Effort**: 2 hours to add coordination notes

---

## P3 — Nice to Have

---

### F-26: LeakCanary should be in Phase 2, not Phase 4

**Source**: Performance Oracle

With 27+ unmanaged `CoroutineScope` instances and the AccountService cancel/recreate pattern, memory leaks are present in production now. `debugImplementation` LeakCanary has zero production impact. Moving it to Phase 2 alongside the CoroutineScope audit lets you confirm the remediation actually fixes the leaks.

**Effort**: 15 min to add dependency

---

### F-27: Add Firebase Performance Monitoring

**Source**: Performance Oracle

The plan adds Crashlytics and Analytics but not Firebase Performance. `firebase-perf` is incremental (~100KB, already included via BOM) and provides automatic Retrofit call latency, cold/warm/hot start timing, and frame rendering metrics — exactly what would validate the Baseline Profiles, @Stable, and lifecycle collection improvements.

Without it, the plan has no way to measure whether performance improvements worked in production.

**Effort**: 1–2 hours to add and configure

---

### F-28: Enable Gradle build caching

**Source**: Performance Oracle

`gradle.properties` has `org.gradle.parallel=true` and `org.gradle.daemon=true` but NOT `org.gradle.caching=true`. The CI config caches dependency caches but not the Gradle build cache. For a multi-module project (7 modules, 732+ files, Hilt/KSP), build caching can save 40–60% on incremental builds.

Add `org.gradle.caching=true` to `gradle.properties` and `~/.gradle/build-cache` to CI cache keys.

**Effort**: 30 min

---

### F-29: Remove kotlin-reflect dependency

**Source**: Performance Oracle

`build.gradle.kts` line 99 includes `kotlin-reflect`. Only 2 files use it (`GraphUtil.kt`, `SegmentButtonGroup.kt`) for `KProperty1`. This library adds ~400–700KB to the release APK (after R8). Replace with direct property access or lambda parameters.

**Effort**: 2 hours

---

### F-30: Certificate pinning should target intermediate CA, not leaf

**Source**: Security Sentinel

The plan's example shows leaf certificate pins. Leaf certificates rotate annually, causing app breakage if users don't update. Best practice: pin the **intermediate CA** certificate (rotates every 5–10 years). Also add:
- Pin rotation monitoring: calendar alert at 90 days before expiration
- Graceful failure: show a meaningful error on pin mismatch, report to Crashlytics as a security event
- Confirm debug config does NOT include the pin set (developers need proxy access)

**Effort**: 2 hours to update plan text and verify approach

---

### F-31: work-runtime-ktx version catalog duplication missed in Task 3.7

**Source**: Architecture Strategist

`libs.versions.toml` has two separate entries: `workRuntimeKtx = "2.10.2"` and `workRuntimeKtxVersion = "2.10.3"`. Both are referenced in `build.gradle.kts`. Task 3.7 addresses duplicate dependencies in the build file but misses this version catalog duplication.

**Effort**: 15 min

---

### F-32: CI path filtering missing (Android changes trigger iOS builds)

**Source**: Spec Flow Analyzer

In a monorepo, every push triggers both iOS and Android CI regardless of what changed. CircleCI path filtering (`setup: true` with dynamic config) should ensure:
- Android jobs only trigger on changes to `Android/**`
- iOS jobs only trigger on changes to `iOS/**`
- Root config changes trigger both

**Effort**: 2–4 hours

---

### F-33: Plan is 1,115 lines — recommend 27% reduction

**Source**: Code Simplicity Reviewer

Specific removals recommended:
- **Enhancement Summary** and **System-Wide Impact** sections (context, not action)
- **Integration Test Scenarios** section (appendix, not plan)
- **Alternative Approaches** subsections (keep only chosen approach)
- **Task 2.11** (staging build type): no staging URL exists — defer until backend provides it
- **Task 4.3** (biometric): feature work, not quality improvement — move to backlog
- **Task 3.5** (Room export schema/migration): no pending migrations, no schema changes needed — collapse to one line in Task 2.6 acceptance criteria
- **Task 1.4** analytics abstraction: verify only, do not add abstraction layer

**Effort**: 2–3 hours of editing

---

### F-34: Input validation missing (OWASP M4)

**Source**: Security Sentinel

Zero client-side input validation anywhere in the codebase. Health data apps should validate:
- Email format before API submission
- Password complexity client-side
- Numeric range for health metrics (weight, body fat %)
- Length limits on text inputs

Add as a P2 task.

**Effort**: 1–2 days for shared validation utility + feature integration

---

### F-35: Evaluate OWASP dependency scanning elevation

**Source**: Security Sentinel

OWASP dependency scanning is currently Phase 4 (backlog). For a health data app that depends on a private Maven package (`ggBluetoothNativeLibrary`) with an externally-hosted PAT, supply chain integrity is unknown. Recommend elevating to Phase 2 or 3.

**Effort**: 2 hours to configure `dependencyCheck` Gradle plugin

---

## Summary by Task Impact

| Finding | Severity | Primary Task(s) | Effort |
|---------|----------|-----------------|--------|
| F-01: PAT rotation order reversed | P1 | Task 1.1 | 30 min |
| F-02: ProGuard rules for Tink missing | P1 | Task 1.5 | 1–2 hr |
| F-03: ExampleUnitTest breaks CI | P1 | Pre-Task 1.6 | 30 min |
| F-04: ResponseInterceptor dead code | P1 | Task 3.3 | 15 min |
| F-05: lifecycle-runtime-compose not in BOM | P1 | Task 2.1 | 15 min |
| F-06: google-services.json CI strategy | P1 | Task 1.3 / Task 1.6 | 1 hr |
| F-07: Token encryption failure handling | P1 | Task 1.5 | 2 hr |
| F-08: collectAsState count wrong, :iam missing | P1 | Task 2.9 | 15 min |
| F-09: OkHttp BODY logging leaks tokens | P1 | Task 1.2 | 1 hr |
| F-10: Task 1.5 must precede 1.3/1.4 | P1 | Sequencing | 15 min |
| F-11: Phase 2 timeline "Next Sprint" → "Next Quarter" | P2 | Plan structure | 30 min |
| F-12: Task 2.12 needs test dependencies | P2 | Task 2.12 | 30 min |
| F-13: Detekt baseline after Spotless reformatting | P2 | Task 2.7 | 15 min |
| F-14: AccountService scope recreate is race condition | P2 | Task 2.12 | 3–5 hr/service |
| F-15: List fields still unstable after @Stable | P2 | Task 2.10 | 2–4 hr |
| F-16: LazyColumn missing key parameters | P2 | Task 2.10 / 3.2 | 2–4 hr |
| F-17: Detekt pre-commit hook too slow | P2 | Task 2.8 | 1 hr |
| F-18: Reducer count 36+ → 30–35 | P2 | Tasks 2.3, 2.10 | 15 min |
| F-19: WebView JS + unvalidated URL | P2 | New security task | 2 hr |
| F-20: SimpleDateFormat thread safety | P2 | Task 2.12 / 3.3 | 1 hr |
| F-21: Debug → production API elevated risk | P2 | Task 2.11 | 1–2 days |
| F-22: suspend call inside combine block | P2 | Task 2.12 / 3.x | 1 hr |
| F-23: No test prioritization by risk | P2 | Tasks 2.3–2.5 | 30 min |
| F-24: Lefthook Windows instructions missing | P2 | Task 2.8 | 30 min |
| F-25: No team coordination plan | P2 | Phase 1 all | 2 hr |
| F-26: LeakCanary move to Phase 2 | P3 | Task 2.12 | 15 min |
| F-27: Add Firebase Performance Monitoring | P3 | Task 1.3/1.4 | 1–2 hr |
| F-28: Enable Gradle build caching | P3 | Task 1.6 / gradle.properties | 30 min |
| F-29: Remove kotlin-reflect dependency | P3 | Task 3.7 | 2 hr |
| F-30: Certificate pinning — pin intermediate CA | P3 | Task 2.13 | 2 hr |
| F-31: work-runtime-ktx version duplication | P3 | Task 3.7 | 15 min |
| F-32: CI path filtering (Android/iOS) | P3 | Task 1.6 | 2–4 hr |
| F-33: Plan length 1115→800 lines | P3 | Plan editing | 2–3 hr |
| F-34: Input validation (OWASP M4) | P3 | New task | 1–2 days |
| F-35: OWASP dependency scanning elevation | P3 | Task 4.2 | 2 hr |

**Totals**: 10 P1 findings · 15 P2 findings · 10 P3 findings
