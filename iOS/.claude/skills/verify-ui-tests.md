Build and run UI tests, then verify coverage is ≥ 85% for the flows under test.

Inputs available: list of UI test files and the screens/flows they cover, written/modified in this task

## Instructions

### 1 — Select Scheme & Device

List available UI test schemes:
```bash
xcodebuild -project iOS/meApp.xcodeproj -list 2>&1 | grep -A 20 "Schemes:" | grep -i "meAppUITests"
```

Ask the user:
> "Which UI test scheme should I use? (e.g. `meAppUITests` or `meAppUITests 1`)"

Store the answer as `{SCHEME}`.

Then list connected physical devices:
```bash
xcodebuild -project iOS/meApp.xcodeproj -scheme "{SCHEME}" -showdestinations 2>&1 | grep "platform:iOS," | grep -v Simulator
```

Ask the user:
> "Which device should I run tests on? (paste the `id:` value from above)"

Store the answer as `{DEVICE_ID}`.

### 2 — Build & Run UI Tests

Run from the repo root (`meApp-1/`):

```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme "{SCHEME}" \
  -configuration Dev \
  -destination 'id={DEVICE_ID}'
```

- If the build fails: read the errors, fix them, and re-run before continuing
- If tests fail: investigate each failure — check accessibility IDs, scenario flags, and navigation flow — fix and re-run

### 3 — Generate Coverage Report

Run from the repo root (`meApp-1/`):

```bash
SCHEME="{SCHEME}" DEVICE_ID={DEVICE_ID} CONFIGURATION=Dev ./iOS/scripts/run_tests_with_coverage.sh
```

When prompted for test type, enter `2` (UI tests).

This generates:
- `iOS/meAppUITests/Reports/coverage-report.md`
- `iOS/meAppUITests/Reports/coverage-report.csv`
- `iOS/meAppUITests/Reports/coverage-report.html`

### 4 — Check Coverage for Tested Flows

Read `iOS/meAppUITests/Reports/coverage-report.md` and extract coverage % for each source file exercised by the UI tests in this task.

**Minimum required: 85% per file.**

| Source file | Coverage % | Pass? |
|-------------|-----------|-------|
| … | … | ✅ / ❌ |

### 5 — Improve Coverage if Below 85%

For any file below 85%:
1. Read the source file and the corresponding UI test file
2. Identify which screens, interactions, or branches are not covered
3. Add targeted UI test cases to cover the missing flows — focus on:
   - Untested screen states (empty, error, loading)
   - User interactions not yet covered (tap, scroll, input)
   - Navigation paths triggered by edge conditions
4. Re-run steps 1–3 until all files reach ≥ 85%

### 6 — Report

Once all files pass, summarise:
- Final coverage % per file
- Any tests added during this step and why
