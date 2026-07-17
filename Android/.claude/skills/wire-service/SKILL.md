---
name: wire-service
description: Register or update a service/repository through the meApp Android Hilt dependency graph. Use when the user says "add a service", "register this in DI", "inject this dependency", "bind this interface", or when a change touches a Hilt @Module, @Binds/@Provides, or an I*-interface in core/di/.
---

Wire a service or injectable dependency through the app's Hilt DI graph.

The service or dependency change is: $ARGUMENTS

## Instructions

### 1 — Inspect the existing pattern
Read first:
- the Hilt modules in `core/di/`
- the target interface in `domain/repository/` or `domain/services/` (prefix `I`)
- the concrete implementation in `data/repository/` or `core/service/`
- planned `@Inject` / `@HiltViewModel` call sites

### 2 — Classify the binding
- interface → impl → **`@Binds`** (abstract module)
- constructed/3rd-party type → **`@Provides`**
- pick the component: `SingletonComponent` (app-wide) vs `ViewModelComponent`, etc.
- add a `@Scope` (`@Singleton`, …) only if the instance must be shared
- multibinding? use `@IntoSet`/`@IntoMap`

### 3 — Implement
- Put the binding in the right `@Module @InstallIn(...)` in `core/di/`.
- Keep all API methods `suspend`.
- If a big change, run agent `hilt-impact-finder` first to get the full blast radius.

### 4 — Update test wiring
If tests inject this type, add/adjust the Hilt test double (`@TestInstallIn`/`@UninstallModules` + fake). Hilt resolves at **compile time** — a missing binding fails `assembleDebug`, so build to confirm.

### 5 — Report & verify
Summarize where it's registered, the component/scope, and whether test DI was updated. Then:
```bash
cd Android && ./gradlew assembleDebug :app:testDebugUnitTest
```
