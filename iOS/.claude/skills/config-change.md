---
name: config-change
description: Safely change environment or build configuration behavior. Use when a task touches xcconfig files, Environment.swift, Info.plist-backed settings, target config mapping, or runtime environment selection.
---

Plan and implement an environment/build configuration change with parity checks.

The configuration change is: $ARGUMENTS

## Instructions

### 1 — Read the Current Config Sources

Inspect:
- `meApp/Core/Config/Dev.xcconfig`
- `meApp/Core/Config/Production.xcconfig`
- `meApp/Core/Config/Environment.swift`
- `docs/XCCONFIG_STRUCTURE.md`
- any affected `Info.plist`-backed usage

### 2 — Classify the Change

Determine whether it affects:
- API base URL or scheme
- app environment switching
- target/project configuration mapping
- release-vs-dev behavior
- runtime constants consumed across features

### 3 — Apply With Parity Checks

Ensure:
- `Dev` and `Production` both remain valid
- docs stay aligned with source
- no config value is changed in one layer but not the others
- archive/release assumptions are still correct

### 4 — Verify Blast Radius

Search for usage sites:
```bash
rg -n "APP_ENV|API_BASE_URL|AppEnvironment|AppConstants" meApp docs -g '*'
```

Call out any caller that may now behave differently.

### 5 — Report

Return:
- files changed
- parity checks performed
- any release/CI follow-up needed
