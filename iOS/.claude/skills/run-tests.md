---
name: run-tests
description: Quickly build and run unit or UI tests on a connected physical device without the full coverage pipeline. Use when the user says "run tests", "do tests pass", "run my tests", "check tests", or when you need a fast pass/fail check after a code change.
---

Run tests on a connected physical device and report pass/fail.

The test scope is: $ARGUMENTS

## Instructions

### 1 — Determine Test Type and Scope

If `$ARGUMENTS` specifies a test type or specific test:
- `unit` or no argument → scheme `meAppTests`
- `ui` → scheme `meAppUITests`
- A specific test class/method → use `-only-testing:meAppTests/{TestClassName}` or `-only-testing:meAppTests/{TestClassName}/{testMethodName}`

Store as `{SCHEME}` and optional `{ONLY_TESTING}`.

---

### 2 — Find Connected Physical Device

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

---

### 3 — Run Tests

```bash
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme "{SCHEME}" \
  -configuration Dev \
  -destination 'id={DEVICE_ID}' \
  {-only-testing:{ONLY_TESTING} if specified}
```

---

### 4 — Report

- **All tests pass:** Report pass count and confirm success.
- **Tests fail:** List each failing test with a one-line summary of the failure reason. Do not attempt to fix — just report.
- **Build failure:** Report the build errors. Do not attempt to fix — just report.

This skill intentionally does NOT run coverage analysis. For coverage, use `/verify-tests` instead.
