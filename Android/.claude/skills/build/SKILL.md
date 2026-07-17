---
name: build
description: Quick compile/build verification for the meApp Android app without running tests. Use when the user says "does this build", "build check", "compile check", or when you need a fast compilation check after edits.
---

Fast build verification — no tests.

## Instructions

```bash
cd Android && ./gradlew assembleDebug
```

- Fix any compilation errors before proceeding; re-run until green.
- For a release/signing-sensitive check use `assembleRelease` only if a signing config + `google-services.json` are present; otherwise stay on `assembleDebug`.
- This does **not** run tests — use `/verify-tests` for that.
