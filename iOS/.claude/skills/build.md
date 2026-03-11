---
name: build
description: Quick build verification without running tests. Use when the user says "does this build", "build check", "compile check", "verify build", or when you need a fast compilation check after edits.
---

Run a build-only verification and report success or failure.

## Instructions

### 1 — Build

Run from the repo root:

```bash
xcodebuild build \
  -project meApp.xcodeproj \
  -scheme meApp \
  -configuration Dev \
  -destination 'generic/platform=iOS' \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
```

---

### 2 — Report

- **Build succeeds:** Confirm with "Build passed."
- **Build fails:** List each error with file path and line number. Group by file if multiple errors exist.

This skill intentionally does NOT run tests. For tests, use `/run-tests`. For tests with coverage, use `/verify-tests`.
