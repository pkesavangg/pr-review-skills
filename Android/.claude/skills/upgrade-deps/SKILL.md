---
name: upgrade-deps
description: Safely upgrades Android dependency versions in libs.versions.toml using a phased, risk-ordered approach. Use when asked to update dependencies, upgrade versions, or check for outdated libraries.
---

<objective>
Read `gradle/libs.versions.toml` and all module `build.gradle.kts` files, identify outdated dependency versions, and apply updates in risk-ordered phases with build verification after each phase.

- Structural cleanup first (unused entries, mismatched versions, hardcoded values)
- Tightly-coupled groups updated together (Kotlin/KSP, AGP/Gradle, Compose BOM, Firebase BOM)
- Independent libraries updated separately
- One commit per phase for easy rollback
- Build + test verification after every phase
</objective>

<quick_start>
1. Read `gradle/libs.versions.toml` and all module `build.gradle.kts` files in parallel.
2. Load `reference/version-groups.md` for dependency grouping and compatibility rules.
3. Identify locked deps (`# Dont update` comments) and internal deps (`com.dmdbrands.lib`).
4. Look up latest stable versions via web search or the `dependencyUpdates` Gradle plugin.
5. Apply updates phase-by-phase, running `./gradlew assembleDebug && ./gradlew test` after each.
6. Final verification: `./gradlew clean assembleRelease && ./gradlew detekt && ./gradlew dependencyCheckAnalyze`.
</quick_start>

## Workflow

### Step 1: Read project files

Read these files in parallel to understand the full dependency landscape:

| File | Purpose |
|------|---------|
| `gradle/libs.versions.toml` | Version catalog (primary file to update) |
| `app/build.gradle.kts` | Main app deps, hardcoded versions (protoc, leakcanary) |
| `iam/build.gradle.kts` | May have stale `composeOptions`, hardcoded protoc |
| `notification/build.gradle.kts` | Firebase BOM usage |
| `bleWrapper/build.gradle.kts` | Internal gg-bluetooth dep |
| `app/healthconnect/build.gradle.kts` | Compose BOM + Health Connect |
| `app/wificonnect/build.gradle.kts` | Hilt usage |
| `app/appsync/build.gradle.kts` | CameraX + Compose |
| `gradle/wrapper/gradle-wrapper.properties` | Current Gradle version |
| `settings.gradle.kts` | Module structure + private Maven repos |

Load `.claude/skills/upgrade-deps/reference/version-groups.md` for grouping and compatibility rules.

### Step 2: Identify DO-NOT-UPDATE entries

Scan `libs.versions.toml` for:
- **Comments**: Lines with `# Dont update` or `# Do not update` — these versions are locked
- **Internal deps**: Libraries from `com.dmdbrands.lib` (vico, gg-bluetooth-android) — managed separately
- **Record all locked entries** and skip them in all subsequent phases

### Step 3: Look up latest versions

For each dependency not locked, find the latest stable version:

| Source | Where to check |
|--------|----------------|
| AndroidX libraries | https://developer.android.com/jetpack/androidx/versions |
| Kotlin / KotlinX | https://kotlinlang.org/docs/releases.html |
| KSP | https://github.com/google/ksp/releases |
| Firebase | https://firebase.google.com/support/release-notes/android |
| Hilt / Dagger | https://github.com/google/dagger/releases |
| Retrofit / OkHttp | GitHub releases for square/retrofit and square/okhttp |
| Other third-party | Their respective GitHub releases pages |
| Gradle plugins | https://plugins.gradle.org/ |

**Alternative**: Add the `com.github.ben-manes.versions` plugin and run `./gradlew dependencyUpdates` for an automated report.

**Preferred doc source:** for each dependency, pull the **official release notes / migration guide** via the **context7 MCP** (`resolve-library-id` → `query-docs`, version-aware for AndroidX/Compose/Room/Hilt/Kotlin) — fall back to WebSearch/WebFetch against the vendor pages above when context7 has no match. If docs are unavailable (headless/offline), say so and proceed on the vendor version pages only.

### Step 3.5: Per-upgrade report + developer notify (do this before applying)

Build a report row for **every** dependency you intend to bump, sourced from Step 3's docs:

| Dependency | old → new | Breaking changes | Migration steps | Risk |
|------------|-----------|------------------|-----------------|------|
| e.g. `coil` | 2.7.0 → 3.0.4 | new `coil3` package, API renames | swap imports, update `ImageLoader` builder | **High** |

Risk rating: **Low** (patch/minor, no API change) · **Medium** (minor with deprecations) · **High** (major, transitive shifts like Retrofit 3/OkHttp 5, alpha APIs like Navigation3, package moves like Coil 3).

**Notify + gate:**
- Surface this report to the developer (in chat, and persist it in the PR description).
- For any **High-risk** bump, **pause and get explicit go-ahead** before applying that phase — do not auto-apply high-risk upgrades.
- Low/Medium may proceed through the normal phased flow.

### Step 4: Structural cleanup (Phase 0)

Before changing any versions, fix structural issues:

1. **Remove unused version entries** — entries in `[versions]` not referenced by any library or plugin
2. **Remove duplicate library entries** — e.g., duplicate `ui` entries where one is BOM-managed
3. **Centralize hardcoded versions** — move versions from `build.gradle.kts` files into the catalog (e.g., protoc version, leakcanary version)
4. **Remove stale build config** — e.g., `composeOptions { kotlinCompilerExtensionVersion }` that is now managed by the `kotlin-compose` plugin
5. **Unify split versions** — if `okhttp` and `okhttpLogging` use the same version, consolidate to one entry
6. **Align mismatched groups** — e.g., lifecycle artifacts that should share one version but don't

```bash
cd Android && ./gradlew assembleDebug && ./gradlew test
```

**Commit**: `chore: clean up version catalog structure`

### Step 5: Phase 1 — Kotlin + KSP

These MUST move together. KSP version must start with the Kotlin version.

| Entry | Current pattern |
|-------|-----------------|
| `kotlin` | e.g., `2.1.21` |
| `ksp` | e.g., `2.1.21-2.0.1` (prefix must match Kotlin) |

The Compose Compiler is managed automatically by the `kotlin-compose` plugin — no separate update needed.

```bash
cd Android && ./gradlew clean assembleDebug && ./gradlew test && ./gradlew detekt
```

**Commit**: `chore: update Kotlin X.Y.Z + KSP`

### Step 6: Phase 2 — AGP + Gradle wrapper

AGP and Gradle wrapper must be compatible. Check the compatibility matrix:
https://developer.android.com/build/releases/gradle-plugin#updating-gradle

| Entry | File |
|-------|------|
| `agp` | `libs.versions.toml` |
| `distributionUrl` | `gradle/wrapper/gradle-wrapper.properties` |

```bash
cd Android && ./gradlew clean assembleDebug && ./gradlew test
```

**Commit**: `chore: update AGP X.Y.Z + Gradle Z.W`

### Step 7: Phase 3 — Compose BOM + Material3

The Compose BOM controls all `androidx.compose.*` artifact versions. Material3 may have an intentional alpha override for Navigation3 compatibility.

| Entry | Notes |
|-------|-------|
| `composeBom` | Controls all Compose artifact versions |
| `material3` | Check if BOM now includes a sufficiently recent version |

After Phase 0 cleanup, most explicit Compose version overrides should be removed — the BOM manages them.

```bash
cd Android && ./gradlew assembleDebug && ./gradlew test
```

Manual UI smoke test recommended after this phase.

**Commit**: `chore: update Compose BOM + Material3`

### Step 8: Phase 4 — Hilt / Dagger

All Hilt/Dagger core artifacts share one version. AndroidX Hilt extensions have their own version track.

| Entry | Group |
|-------|-------|
| `hilt` | Dagger core (hilt-android, hilt-android-compiler, plugin) |
| `hiltNavigationCompose`, `hiltNavigationFragment`, `hiltCommon`, `hiltWork` | AndroidX Hilt |

Verify KSP compatibility — Hilt with KSP requires a compatible KSP version (check Dagger release notes).

```bash
cd Android && ./gradlew assembleDebug && ./gradlew test
```

**Commit**: `chore: update Hilt/Dagger`

### Step 9: Phase 5 — Firebase BOM + plugins

| Entry | Notes |
|-------|-------|
| `firebaseBom` | Controls all Firebase library versions |
| `firebaseCrashlyticsPlugin` | Crashlytics Gradle plugin |
| `googleService` | Google Services plugin |
| `firebaseMessagingKtx` | Should be BOM-managed (remove explicit version if possible) |

```bash
cd Android && ./gradlew assembleDebug
```

Test push notifications and Crashlytics in a debug build.

**Commit**: `chore: update Firebase BOM + plugins`

### Step 10: Phase 6 — Retrofit + OkHttp (HIGH RISK)

Retrofit 3.x depends on OkHttp 5.x transitively. If the project pins OkHttp 4.x explicitly, there may be a version conflict.

**Steps:**
1. Check what OkHttp version Retrofit's POM declares
2. Update `okhttp` and `okhttpLogging` to match
3. OkHttp 5 changes: some API differences, `mockwebserver` package may have moved
4. Audit all `OkHttpClient`, interceptor, and `MockWebServer` usages
5. If OkHttp 5 is not yet stable, consider staying on Retrofit 2.x instead

```bash
cd Android && ./gradlew assembleDebug && ./gradlew test
```

Test API calls end-to-end on a device.

**Commit**: `chore: update Retrofit + OkHttp`

### Step 11: Phase 7 — AndroidX libraries (low risk)

Update each independently. Key alignment rules:
- **Lifecycle group**: `lifecycleRuntimeKtx`, `lifecycleViewmodelCompose`, `lifecycle-process` should all use the same version
- **Activity group**: `activityCompose` and `activityKtx` should match

| Entry | Library group |
|-------|---------------|
| `coreKtx` | core |
| `lifecycleRuntimeKtx` | lifecycle |
| `lifecycleViewmodelCompose` | lifecycle (align with above) |
| `activityCompose` | activity |
| `activityKtx` | activity (align with above) |
| `roomRuntime` | room |
| `datastorePreferencesCore` | datastore |
| `appcompat` | appcompat |
| `browser` | browser |
| `coreSplashscreen` | core |
| `workRuntimeKtx` | work |
| `cameraCore` | camera |
| `connectClient` | health-connect |
| `securityCrypto` | security |
| `profileinstaller` | profileinstaller |
| `baselineprofile` | benchmark |
| `benchmarkMacroJunit4` | benchmark |
| `uiautomator` | test |
| `playServicesLocation` | play-services |
| `play-review` | play-core |
| `material` (MDC) | material-components |

```bash
cd Android && ./gradlew assembleDebug && ./gradlew test
```

**Commit**: `chore: update AndroidX libraries`

### Step 12: Phase 8 — Navigation3 (pre-release)

These are alpha libraries with frequent breaking API changes.

| Entry | Notes |
|-------|-------|
| `nav3Core` | Navigation3 runtime + UI |
| `lifecycleViewmodelNav3` | ViewModel integration |

Read release notes carefully. Be prepared for breaking changes in route/navigation APIs. Manually test all navigation flows.

```bash
cd Android && ./gradlew assembleDebug && ./gradlew test
```

**Commit**: `chore: update Navigation3`

### Step 13: Phase 9 — KotlinX libraries

Must be compatible with the Kotlin version from Phase 1.

| Entry | Library |
|-------|---------|
| `kotlinxSerialization` / `kotlinxSerializationCore` | kotlinx-serialization (keep both in sync) |
| `kotlinxCollectionsImmutable` | kotlinx-collections-immutable |
| `coroutinesTest` | kotlinx-coroutines |

```bash
cd Android && ./gradlew assembleDebug && ./gradlew test
```

**Commit**: `chore: update KotlinX libraries`

### Step 14: Phase 10 — Third-party libraries

| Entry | Notes |
|-------|-------|
| `coil` | Coil 3.x has breaking changes (new `coil3` package). Decide: patch 2.x or migrate to 3.x |
| `gson` | Stable, minor updates |
| `timber` | Very stable |
| `protobufJavalite` | Also update centralized protoc version to match |
| leakcanary | Hardcoded in `app/build.gradle.kts` — update inline or move to catalog |

```bash
cd Android && ./gradlew assembleDebug && ./gradlew test
```

**Commit**: `chore: update third-party libraries`

### Step 15: Phase 11 — Build/lint plugins

| Entry | Notes |
|-------|-------|
| `detekt` | Verify `config/detekt/detekt.yml` compatibility after update |
| `ktlint` | May introduce new formatting rules — run `./gradlew ktlintFormat` after |
| `owaspDependencyCheck` | Security scanning plugin |
| `protobuf` (plugin) | Protobuf Gradle plugin |

```bash
cd Android && ./gradlew detekt && ./gradlew ktlintCheck && ./gradlew assembleDebug
```

**Commit**: `chore: update build/lint plugins`

### Step 16: Phase 12 — Test dependencies

| Entry | Notes |
|-------|-------|
| `mockk` | MockK updates can change relaxed mock behavior — run full test suite |
| `turbine` | Flow testing |
| `truth` | Assertions |
| `junit6` | JUnit Jupiter |

```bash
cd Android && ./gradlew test
```

**Commit**: `chore: update test dependencies`

### Step 17: Final verification

Run all verification commands:

```bash
cd Android && ./gradlew clean assembleRelease   # Release build with R8/ProGuard
cd Android && ./gradlew detekt                   # Static analysis
cd Android && ./gradlew ktlintCheck              # Code formatting
cd Android && ./gradlew test                     # All unit tests
cd Android && ./gradlew dependencyCheckAnalyze   # OWASP vulnerability scan
```

Full app smoke test on a physical device or emulator.

<success_criteria>
upgrade-deps is complete when:
- [ ] Per-upgrade report produced (version delta / breaking changes / migration / risk) and shared with the developer
- [ ] Docs sourced from context7 (or vendor pages on fallback) for each bumped dependency
- [ ] High-risk bumps were not applied without explicit developer go-ahead
- [ ] All phases applied in order (0 through 12)
- [ ] `./gradlew assembleDebug` passes after each phase
- [ ] `./gradlew test` passes after each phase
- [ ] No locked deps modified (`# Dont update` comments respected)
- [ ] No internal deps modified (`com.dmdbrands.lib` packages untouched)
- [ ] Tightly-coupled groups updated together (Kotlin/KSP, AGP/Gradle, Compose BOM, Firebase BOM)
- [ ] Lifecycle artifacts all share one version
- [ ] Activity artifacts all share one version
- [ ] `./gradlew clean assembleRelease` succeeds (R8/ProGuard)
- [ ] `./gradlew detekt` passes
- [ ] `./gradlew ktlintCheck` passes
- [ ] One commit per phase for easy rollback
- [ ] Structural cleanup completed before version changes
</success_criteria>