---
name: feature-slice
description: Scaffold or plan a feature slice using the closest existing module pattern in the repo. Use when the user says "add a feature", "scaffold this flow", "create a new settings section", "new feature", or when a task needs more than the generic new-feature template.
---

Create or plan a feature slice based on the nearest existing pattern in the codebase.

The feature or slice is: $ARGUMENTS

## Instructions

### 1 — Find the Closest Existing Pattern

Search for the nearest match in:
- `meApp/Features/Settings/`
- `meApp/Features/ScaleSetup/`
- `meApp/Features/Common/`
- the top-level feature folders for simple standalone flows

Use repo structure, not assumptions:
```bash
find meApp/Features -maxdepth 4 -type d | sort
rg -n "{keyword}" meApp/Features meAppTests/Features -g '*.swift'
```

### 2 — Classify the Slice Archetype

Choose one:
- simple standalone feature
- nested settings subsection
- scale setup subflow
- shared view model/store
- form-driven feature
- API-backed store flow
#### Archetype: simple standalone feature

Use when: top-level module, no nested subflows, not a settings subsection, not scale-setup-style.

Generate:
- `Routes/<FeatureName>Route.swift` — enum with `case root`, conforming to `Routable`
- `Stores/<FeatureName>Store.swift` — `@MainActor final class`, `@Published isLoading`, `@Published errorMessage`
- `Views/Screens/<FeatureName>Screen.swift` — `struct` with `@ObservedObject store`
- `Views/Components/` — empty directory
- `Strings/<FeatureName>Strings.swift` — PascalCase struct with `static let title`
- `meAppTests/Features/<FeatureName>/` — empty test directory

After creating files, print this checklist:
```
Manual next steps:
□ Add <FeatureName>Route to the parent router or app-level routing
□ Wire <FeatureName>Screen into the calling view navigation — run /wire-navigation
□ Uncomment and wire @Injector dependencies in <FeatureName>Store once services exist — run /wire-service
□ Register any new services in ServiceRegistry (essential vs. session-scoped)
□ Run: /gen-test-file meApp/Features/<FeatureName>/Stores/<FeatureName>Store.swift
□ Run: /gen-mock-single for each protocol dependency once defined
```

### 3 — Scaffold Only What Fits

Create only the folders/files that the chosen archetype needs, for example:
- `Stores/`, `Views/`, `Forms/`, `Strings/`, `Models/`, `Enums/`, `Routes/`
- matching `meAppTests/Features/...` folders

Do not force a flat template onto nested modules like `Settings` or `ScaleSetup`.

### 4 — Identify Adjacent Wiring

Check whether the new slice also requires:
- routing updates → run `/wire-navigation`
- DI registration → run `/wire-service`
- new strings/constants → run `/add-strings`
- service/repository additions → run `/add-endpoint`
- UI test scenario hooks

### 5 — Report

Return:
- chosen reference feature(s)
- archetype used
- files/folders created or proposed
- next recommended commands
