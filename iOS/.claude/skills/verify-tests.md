---
name: verify-tests
description: Build and run unit or UI tests on a connected physical device, verify coverage meets layer thresholds, and add targeted tests for any file below its minimum. Use this skill whenever tests need to be run after writing or modifying Swift code — unit tests after services/stores/repositories, UI tests after screen flows. Also use when the user says "run tests", "check coverage", or "verify tests pass".
---

Build and run tests (unit or UI), verify coverage meets layer thresholds, and add targeted tests for any file below its minimum.

## Instructions

### 1 — Determine Test Type

If not already specified by the caller, ask:
> "Unit tests or UI tests?"

- **Unit** → scheme `meAppTests`, coverage report type `1`, reports in `meAppTests/Reports/`
- **UI** → scheme `meAppUITests`, coverage report type `2`, reports in `meAppUITests/Reports/`

Store as `{SCHEME}` and `{REPORT_TYPE}`.

---

### 2 — Find Connected Physical Device

All tests must run on a physical device — never use a simulator.

```bash
DEVICE_ID=$(./scripts/find-device.sh "{SCHEME}")
```

If the script exits with a non-zero status, stop and display this message:

> ❌ No physical iOS device found.
>
> This project requires a connected physical device — it cannot run on the iOS Simulator
> because third-party packages (GGBluetoothSwiftPackage, gWifiScalePackage, AppSyncPackage)
> do not support the simulator.
>
> To proceed:
> 1. Connect an iOS device via USB
> 2. Trust the Mac on the device (Settings → General → VPN & Device Management)
> 3. Unlock the device and keep it awake
> 4. Re-run this command
>
> Original error: {error from find-device.sh}

Store `{DESTINATION}` = `id={DEVICE_ID}`.

---

### 3 — Build & Run Tests

Run from the repo root:

```bash
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme "{SCHEME}" \
  -configuration Dev \
  -destination '{DESTINATION}'
```

- **Build failure**: read errors, fix root cause in the changed files, re-run. Do not proceed with errors.
- **Test failure**: investigate each failure — check mock setups, assertions, and async timing — fix and re-run.

---

### 4 — Generate Coverage Report

```bash
SCHEME="{SCHEME}" DESTINATION="{DESTINATION}" CONFIGURATION=Dev ./scripts/run_tests_with_coverage.sh
```

When prompted for test type, enter `{REPORT_TYPE}`.

Reads coverage from the generated `coverage-report.md`.

---

### 5 — Check Coverage Thresholds

Read the coverage report and extract % for each source file touched by this task.

Use the layer minimums from `CLAUDE.md`. **UI test threshold:** 85% flat for all exercised source files.

UI layer files (`Views/`, `*View.swift`, `*Screen.swift`, `*Modifier.swift`) are excluded from coverage metrics.

| Source file | Layer | Minimum | Coverage % | Pass? |
|-------------|-------|---------|-----------|-------|
| … | … | … | … | ✅ / ❌ |

---

### 6 — Improve Coverage If Below Minimum

For any file below its threshold:
1. Read the source file and its test file side by side.
2. Identify uncovered methods, branches, and guard conditions.
3. Add targeted tests — focus on:
   - Failure / error paths
   - Guard / early return conditions
   - Edge cases (nil inputs, empty collections, boundary values)
   - For UI tests: untested screen states (empty, error, loading) and navigation paths
4. Re-run from Step 3 until all files pass.

---

### 7 — Report

Summarise:
- Final coverage % per file (✅ / ❌)
- Any tests added and why
