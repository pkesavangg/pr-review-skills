Build the project and verify the implementation compiles cleanly before asking the user to test.

## Instructions

### 1 — Build

First, find the connected physical device ID:
```bash
xcodebuild -project iOS/meApp.xcodeproj -scheme meApp -showdestinations 2>&1 | grep "platform:iOS," | grep -v Simulator | head -5
```

Run from the repo root (`meApp-1/`) using the physical device ID:

```bash
xcodebuild build \
  -project iOS/meApp.xcodeproj \
  -scheme meApp \
  -configuration Dev \
  -destination 'id={DEVICE_ID}'
```

### 2 — Fix Build Errors

If the build fails:
1. Read the error output carefully
2. Identify the root cause in the changed files
3. Fix the issue
4. Re-run the build
5. Repeat until the build succeeds — do not proceed with errors

### 3 — Confirm to User

Once the build succeeds, report:
> "Build passed. Please run the app on the device and verify the change behaves as expected. Let me know if anything looks wrong."

Wait for the user's response before continuing. If they report issues, investigate and fix them before proceeding.
