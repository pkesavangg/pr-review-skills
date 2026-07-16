# Coverage Reporting

## Related Testing Docs
- High-level testing index: `docs/TESTING.md`
- Unit testing details: `meAppTests/docs/UNIT_TESTING.md`
- UI testing details: `meAppUITests/docs/UI_TESTING.md`

## Purpose
Xcode's Coverage UI (`Cmd+9`) is useful for quick local inspection but hard to share with reviewers.

This project uses `iOS/scripts/run_tests_with_coverage.sh` + `iOS/scripts/export_coverage_reports.py` to generate shareable coverage artifacts after unit or UI tests.

## Prerequisites
Before running coverage commands, verify Python 3 is available:

```bash
python3 --version
```

If `python3` is missing on macOS:

```bash
brew install python
```

If Homebrew is not installed, install Python from [python.org](https://www.python.org/downloads/macos/), then reopen Terminal and run:

```bash
python3 --version
```

## Command 1 (Interactive)
Run from the repo root:

```bash
CONFIGURATION=Dev ./iOS/scripts/run_tests_with_coverage.sh
```

No manual `SCHEME` or `DEVICE_ID` is required. The script prompts for both.

## Command 2 (Direct, No Prompts)
Run from the repo root:

```bash
SCHEME="meAppTests 1" DEVICE_ID=<device-udid> CONFIGURATION=Dev ./iOS/scripts/run_tests_with_coverage.sh
```

This runs directly with the provided scheme and device ID.

## Configuration Default
- Default test configuration for this coverage flow is `Dev`.
- Reason:
  - Faster and more stable for daily unit-test/coverage iteration.
  - Avoids release-only optimization differences during routine coverage checks.
- Use `Production` only when you intentionally need release-like verification:
  ```bash
  SCHEME="meAppTests" DEVICE_ID=<device-udid> CONFIGURATION=Production ./iOS/scripts/run_tests_with_coverage.sh
  ```

## What The Script Does
1. Resolves project paths (`iOS/meApp.xcodeproj`, scripts, output folders).
2. Lists available test schemes:
   - Uses `xcodebuild -list -json`.
   - Shows test-related schemes and prompts for selection.
3. Lists available connected physical iOS devices for the selected scheme:
   - Uses `xcodebuild -showdestinations` for the selected scheme.
   - Shows connected physical devices only.
   - Prompts for destination selection.
4. Runs tests with coverage enabled:
   - `xcodebuild test ... -enableCodeCoverage YES -resultBundlePath <...>.xcresult`
5. Exports coverage from `.xcresult` using `xcrun xccov`.
6. Writes shareable reports based on selected scheme type:
   - Unit test schemes -> `iOS/meAppTests/Reports`
   - UI test schemes -> `iOS/meAppUITests/Reports`

## Output Location
- Unit-test schemes (`meAppTests...`):
  - `iOS/meAppTests/Reports/coverage-report.md`
  - `iOS/meAppTests/Reports/coverage-report.csv`
  - `iOS/meAppTests/Reports/coverage-report.html`
- UI-test schemes (`meAppUITests...`):
  - `iOS/meAppUITests/Reports/coverage-report.md`
  - `iOS/meAppUITests/Reports/coverage-report.csv`
  - `iOS/meAppUITests/Reports/coverage-report.html`

Behavior:
- Reports are overwritten on every run.
- Folder is ignored by git (`.gitignore`).

## Coverage Scope
- Official metric for this workflow is App-only non-UI coverage:
  - Include: `meApp/**/*.swift`
  - Exclude UI layer files not targeted by unit tests:
    - Any file under `Views/`, `ViewModifiers/`, `Modifiers/`, `Previews/`
    - Any file ending with `View.swift`, `Modifier.swift`, `Screen.swift`, `UIKitView.swift`, or `Cell.swift`
- Reason:
  - Keeps coverage focused on app code quality.
  - Prevents SwiftUI view rendering files from reducing unit-test coverage metrics.
  - Avoids skew from third-party packages/framework targets included in `.xcresult`.
  - Gives stable, team-actionable numbers for PRs and sprint tracking.

## Output Formats
### Markdown (`.md`)
- Summary section (timestamp, source bundle, app-only covered/executable lines, app-only %).
- Per-file table for filtered non-UI app scope.

### CSV (`.csv`)
- One row per Swift file.
- Columns: `file`, `covered_lines`, `executable_lines`, `coverage_percent`.

### HTML (`.html`)
- Readable visual report for sharing.
- Summary metrics at top for app-only coverage.
- Full per-file table with aligned numeric columns.

## Troubleshooting
### Scheme not found / wrong scheme selected
- Verify project schemes in Xcode.
- If both `meAppTests` and `meAppTests 1` are shown, choose `meAppTests 1` when `meAppTests` fails.
- Override manually if needed:
  ```bash
  SCHEME="meAppTests 1" CONFIGURATION=Dev ./iOS/scripts/run_tests_with_coverage.sh
  ```

### No devices found
- Connect and unlock the phone.
- Trust the Mac on device.
- Ensure device appears in Xcode -> Window -> Devices and Simulators.
- Or run direct with known UDID:
  ```bash
  SCHEME="meAppTests 1" DEVICE_ID=<device-udid> CONFIGURATION=Dev ./iOS/scripts/run_tests_with_coverage.sh
  ```

### Build fails with module/import errors
- Common cause: selecting `meAppTests` when `meAppTests 1` is the working test scheme in your project state.
- Confirm selected scheme and configuration match your working local setup.
- Clean build folder in Xcode and rerun.
- Retry with explicit scheme/config override if needed.

### No coverage rows in output
- Ensure tests were run with `-enableCodeCoverage YES` (handled by run script).
- Ensure `.xcresult` is from unit test execution and not from a failed pre-build stage.
