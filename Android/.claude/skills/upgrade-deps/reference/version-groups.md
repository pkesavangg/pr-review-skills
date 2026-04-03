# Dependency Version Groups & Compatibility Rules

This reference documents tightly-coupled dependency groups that must be updated together, locked entries, and compatibility constraints.

## DO NOT UPDATE

### Locked by comment
These entries have explicit comments in `libs.versions.toml` marking them as locked:

| Entry | Version | Comment |
|-------|---------|---------|
| `junitVersion` | 1.3.0 | `# Dont update this package` |
| `espressoCore` | 3.7.0 | `# Dont update this package` |

### Internal / private dependencies
These are published to private GitHub Maven repos (`com.dmdbrands.lib`) and must NOT be updated without coordination with the library maintainers:

| Entry | Library | Private repo |
|-------|---------|--------------|
| `vico` | `com.dmdbrands.lib:vico-core`, `vico-compose`, `vico-compose-m3` | `github.com/dmdbrands/vico` |
| `ggBluetoothAndroid` | `com.dmdbrands.lib:gg-bluetooth-android` | `github.com/dmdbrands/ggBluetoothNativeLibrary` |

---

## Tightly-Coupled Groups

### Group 1: Kotlin + KSP + Compose Compiler

**Rule**: KSP version MUST start with the Kotlin version prefix.

| Entry | Example | Constraint |
|-------|---------|------------|
| `kotlin` | `2.1.21` | Primary — update this first |
| `ksp` | `2.1.21-2.0.1` | Prefix must match `kotlin` version |

The Compose Compiler version is managed automatically by the `kotlin-compose` Gradle plugin (`org.jetbrains.kotlin.plugin.compose`). No separate entry needed.

**Where to check**:
- Kotlin: https://kotlinlang.org/docs/releases.html
- KSP: https://github.com/google/ksp/releases (find the release matching your Kotlin version)

### Group 2: AGP + Gradle Wrapper

**Rule**: AGP requires a minimum Gradle version. Check the compatibility matrix.

| Entry | File | Constraint |
|-------|------|------------|
| `agp` | `libs.versions.toml` | Android Gradle Plugin |
| `distributionUrl` | `gradle/wrapper/gradle-wrapper.properties` | Must meet AGP's minimum Gradle version |

**Compatibility matrix**: https://developer.android.com/build/releases/gradle-plugin#updating-gradle

### Group 3: Compose BOM

**Rule**: The Compose BOM manages versions for all `androidx.compose.*` artifacts. Do not set explicit versions on BOM-managed artifacts unless there is a documented reason.

**BOM-managed artifacts** (should NOT have `version.ref` in the catalog):
- `androidx.compose.ui:ui`
- `androidx.compose.ui:ui-graphics`
- `androidx.compose.ui:ui-tooling`
- `androidx.compose.ui:ui-tooling-preview`
- `androidx.compose.ui:ui-test-manifest`
- `androidx.compose.ui:ui-test-junit4`
- `androidx.compose.runtime:runtime-livedata`
- `androidx.compose.runtime:runtime-saveable`
- `androidx.compose.foundation:foundation-layout`
- `androidx.compose.material:material-icons-extended`

**Intentionally overridden** (may have explicit version for a reason):
- `material3` — may be pinned to an alpha for Navigation3 compatibility. Check if the BOM now includes a sufficient version before removing the override.

**BOM mapping reference**: https://developer.android.com/develop/ui/compose/bom/bom-mapping

### Group 4: Firebase BOM

**Rule**: The Firebase BOM manages versions for all Firebase libraries. Do not set explicit versions on BOM-managed artifacts.

**BOM-managed artifacts**:
- `com.google.firebase:firebase-analytics`
- `com.google.firebase:firebase-crashlytics`
- `com.google.firebase:firebase-messaging-ktx` (currently has explicit version — evaluate removing it)

**Separate version tracks** (NOT managed by BOM):
- `firebaseCrashlyticsPlugin` — Crashlytics Gradle plugin
- `googleService` — Google Services Gradle plugin

**Release notes**: https://firebase.google.com/support/release-notes/android

### Group 5: Hilt / Dagger Ecosystem

**Rule**: Dagger core artifacts share one version. AndroidX Hilt extensions have their own version track.

| Entry | Group |
|-------|-------|
| `hilt` | Dagger core: `hilt-android`, `hilt-android-compiler`, Hilt Gradle plugin |
| `hiltNavigationCompose` | AndroidX Hilt |
| `hiltNavigationFragment` | AndroidX Hilt |
| `hiltCommon` | AndroidX Hilt |
| `hiltWork` | AndroidX Hilt |

**Dagger releases**: https://github.com/google/dagger/releases
**AndroidX Hilt releases**: https://developer.android.com/jetpack/androidx/releases/hilt

### Group 6: Retrofit + OkHttp

**Rule**: Retrofit 3.x depends on OkHttp 5.x transitively. The explicit OkHttp version in the catalog should match what Retrofit expects.

| Entry | Constraint |
|-------|------------|
| `retrofit` | Check its POM for the expected OkHttp version |
| `okhttp` | Must align with Retrofit's transitive dependency |
| `okhttpLogging` | Same OkHttp version (should use same version key) |

**Breaking changes in OkHttp 5**:
- Some API differences in `OkHttpClient.Builder`
- `mockwebserver` package may have moved to `okhttp3.mockwebserver3`
- Audit all interceptor and `MockWebServer` usages after updating

### Group 7: Lifecycle

**Rule**: All `androidx.lifecycle` artifacts should use the same version.

| Entry | Artifact |
|-------|----------|
| `lifecycleRuntimeKtx` | `lifecycle-runtime-ktx`, `lifecycle-runtime-compose`, `lifecycle-process` |
| `lifecycleViewmodelCompose` | `lifecycle-viewmodel-compose` (should match above) |

**Releases**: https://developer.android.com/jetpack/androidx/releases/lifecycle

### Group 8: Activity

**Rule**: All `androidx.activity` artifacts should use the same version.

| Entry | Artifact |
|-------|----------|
| `activityCompose` | `activity-compose` |
| `activityKtx` | `activity-ktx` (should match above) |

**Releases**: https://developer.android.com/jetpack/androidx/releases/activity

### Group 9: Navigation3 (Pre-release)

**Rule**: These are alpha libraries. Expect breaking API changes between versions. Always read the release notes.

| Entry | Artifact |
|-------|----------|
| `nav3Core` | `navigation3-runtime`, `navigation3-ui` |
| `lifecycleViewmodelNav3` | `lifecycle-viewmodel-navigation3` |

### Group 10: KotlinX

**Rule**: KotlinX libraries must be compatible with the Kotlin version. Check release notes for Kotlin version requirements.

| Entry | Library |
|-------|---------|
| `kotlinxSerialization` | `kotlinx-serialization-json` |
| `kotlinxSerializationCore` | `kotlinx-serialization-core` (keep in sync with above) |
| `kotlinxCollectionsImmutable` | `kotlinx-collections-immutable` |
| `coroutinesTest` | `kotlinx-coroutines-test` |

---

## Known Structural Issues

These should be fixed in Phase 0 (structural cleanup) before any version changes:

### Unused version entries
Check for version entries in `[versions]` not referenced by any `[libraries]` or `[plugins]` entry. Common culprits:
- Duplicate entries (e.g., `activityComposeVersion` vs `activityCompose`)
- Entries left over after library removal

### Hardcoded versions in build files
Versions that should be in the catalog but are hardcoded in `build.gradle.kts`:
- `protoc` artifact version in `protobuf { protoc { artifact = "..." } }` blocks
- `leakcanary` version in `debugImplementation("com.squareup.leakcanary:...")`
- Any `composeOptions { kotlinCompilerExtensionVersion = "..." }` (stale — managed by kotlin-compose plugin)

### BOM-overridden artifacts
Compose and Firebase libraries with explicit `version.ref` that should be BOM-managed. Remove the explicit version unless there is a documented reason for the override.

### Split version keys
Entries like `okhttp` and `okhttpLogging` that always use the same version should be consolidated into a single version key.

---

## Version Lookup Quick Reference

| Category | URL |
|----------|-----|
| AndroidX (all) | https://developer.android.com/jetpack/androidx/versions |
| Compose BOM mapping | https://developer.android.com/develop/ui/compose/bom/bom-mapping |
| Kotlin releases | https://kotlinlang.org/docs/releases.html |
| KSP releases | https://github.com/google/ksp/releases |
| Firebase release notes | https://firebase.google.com/support/release-notes/android |
| Dagger/Hilt releases | https://github.com/google/dagger/releases |
| AGP/Gradle compatibility | https://developer.android.com/build/releases/gradle-plugin#updating-gradle |
| Gradle Plugin Portal | https://plugins.gradle.org/ |
| Google Maven Repository | https://maven.google.com/web/index.html |
| Automated check | Add `com.github.ben-manes.versions` plugin, run `./gradlew dependencyUpdates` |