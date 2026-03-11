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
xcodebuild -project meApp.xcodeproj -scheme "{SCHEME}" -showdestinations 2>&1 \
  | grep "platform:iOS," | grep -v Simulator | head -1
```

Extract the `id:` value as `{DEVICE_ID}`.

If no physical device is found:
> "No physical device detected. Please connect and unlock your iPhone, then try again."
Stop here.

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
