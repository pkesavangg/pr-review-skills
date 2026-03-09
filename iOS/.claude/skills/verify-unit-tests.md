Build and run unit tests, then verify coverage is ≥ 85% for the files under test.

Inputs available: list of test files and their corresponding source files written/modified in this task

## Instructions

### 1 — Select Scheme & Device

List available unit test schemes:
```bash
xcodebuild -project iOS/meApp.xcodeproj -list 2>&1 | grep -A 20 "Schemes:" | grep -i "meAppTests"
```

Ask the user:
> "Which test scheme should I use? (e.g. `meAppTests` or `meAppTests 1`)"

Store the answer as `{SCHEME}`.

Then list connected **physical** devices (never use a simulator):
```bash
xcodebuild -project iOS/meApp.xcodeproj -scheme "{SCHEME}" -showdestinations 2>&1 | grep "platform:iOS," | grep -v Simulator
```

Pick the first device from the list that has **no `error:` field**. Do NOT fall back to a simulator — if no eligible physical device is listed, ask the user to connect one before continuing.

Store the chosen `id:` value as `{DEVICE_ID}`.

### 2 — Build & Run Tests

Run from the repo root (`meApp-1/`):

```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme "{SCHEME}" \
  -configuration Dev \
  -destination 'id={DEVICE_ID}'
```

- If the build fails: read the errors, fix them, and re-run before continuing
- If tests fail: investigate each failure, fix the root cause, and re-run

### 3 — Generate Coverage Report

Run from the repo root (`meApp-1/`):

```bash
SCHEME="{SCHEME}" DEVICE_ID={DEVICE_ID} CONFIGURATION=Dev ./iOS/scripts/run_tests_with_coverage.sh
```

When prompted for test type, enter `1` (Unit tests).

This generates:
- `iOS/meAppTests/Reports/coverage-report.md`
- `iOS/meAppTests/Reports/coverage-report.csv`
- `iOS/meAppTests/Reports/coverage-report.html`

### 4 — Check Coverage for Tested Files

Read `iOS/meAppTests/Reports/coverage-report.md` and extract coverage % for each source file touched by this task.

**Minimum required — apply the threshold for the layer each file belongs to:**

| Layer | Minimum |
|-------|---------|
| `Data/API` repository adapters | 75% |
| `Data/Services` (general) | 80% |
| `Data/Services` (auth / account / sync) | 85% |
| Stores / ViewModels | 80% |
| Forms / validation | 85% |

UI layer files (`Views/`, `*View.swift`, `*Screen.swift`, `*Modifier.swift`) are excluded from coverage metrics.

| Source file | Layer | Minimum | Coverage % | Pass? |
|-------------|-------|---------|-----------|-------|
| … | … | … | … | ✅ / ❌ |

### 5 — Improve Coverage if Below Minimum

For any file below its layer minimum:
1. Read the source file and the corresponding test file
2. Identify which methods/branches are not covered
3. Add targeted tests to cover the missing paths — focus on:
   - Uncovered failure/error paths
   - Guard conditions and early returns
   - Edge cases (nil inputs, empty collections, boundary values)
4. Re-run steps 1–3 until all files reach their layer minimum

### 6 — Report

Once all files pass, summarise:
- Final coverage % per file
- Any tests added during this step and why
