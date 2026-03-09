# ScaleStore Coverage Report

Date: March 6, 2026

## Objective
Document the verified code coverage and executed unit-test inventory for `ScaleStore`, with clear evidence of:
- exact measured coverage percentage,
- number of test cases executed,
- test-case names,
- command(s) used to generate and verify coverage.

## Scope
- Production file: `iOS/meApp/Features/Settings/Scale/Stores/ScaleStore.swift`
- Test suite file: `iOS/meAppTests/Features/Scale/ScaleStoreTests.swift`
- Test suite name: `ScaleStoreTests`

## Coverage Artifact
- Coverage result bundle path: `/tmp/ScaleStoreCoverage.xcresult`
- Coverage extraction tool: `xcrun xccov`

## Commands Used
1. Generate coverage bundle for ScaleStore tests:
```bash
xcodebuild test -quiet \
  -enableCodeCoverage YES \
  -resultBundlePath /tmp/ScaleStoreCoverage.xcresult \
  -project /Users/lakshmipriya/Work/meApp/meApp/iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -destination 'id=00008120-001E095C1487A01E' \
  -only-testing:meAppTests/ScaleStoreTests
```

2. Extract coverage rows for target files:
```bash
xcrun xccov view --report /tmp/ScaleStoreCoverage.xcresult \
  | rg '/Features/Settings/Scale/Stores/ScaleStore.swift|/Features/Scale/ScaleStoreTests.swift'
```

3. Verify test-case pass list:
```bash
xcodebuild test -quiet \
  -project /Users/lakshmipriya/Work/meApp/meApp/iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -destination 'id=00008120-001E095C1487A01E' \
  -only-testing:meAppTests/ScaleStoreTests
```

## Coverage Results (Verified)
- `ScaleStore.swift`: **95.95% (142/148)**
- `ScaleStoreTests.swift`: **92.79% (193/208)**

Rounded value for reporting: **95.9%** (exact measured value remains **95.95%**).

## Test Execution Summary
- Suite executed: `ScaleStoreTests`
- Total test cases: **9**
- Passed: **9**
- Failed: **0**

## Test-Case Inventory (9/9)
1. `loadsAndSortsScaleStateFromPublisher`
2. `sortingHandlesInvalidCreatedAtGracefully`
3. `updateSetupInProgressStatusUpdatesBluetoothState`
4. `determineConnectionStatusHandlesDisconnectedStates`
5. `determineConnectionStatusForBtWifiModes`
6. `determineConnectionStatusForAppSync`
7. `handleDuplicateScaleShowsAlertAndRunsPairAction`
8. `openHelpPresentsModal`
9. `resetFormClearsAndReinitializesForm`

## Behavior Coverage Mapping
- Loading and sorting scale state:
  - sorted by latest `createdAt`,
  - invalid and missing `createdAt` handling.
- Setup status state transitions:
  - in-progress toggling,
  - Bluetooth scan resume and sync clear behavior.
- Connection-state decision logic:
  - Bluetooth disabled/disconnected paths,
  - BT-WiFi setup-incomplete vs connected paths,
  - AppSync `noStatus` path.
- User interaction flows:
  - duplicate scale alert + pair action callback,
  - help modal presentation,
  - form reset and form-object reinitialization.

## Conclusion
Target threshold (90%+) is satisfied.

- `ScaleStore.swift` coverage: **95.95%**
- Result status: **PASS**
