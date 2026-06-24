# WifiScaleSetupStore Test Coverage Guide

## Purpose
This document summarizes how `WifiScaleSetupStore` tests are organized, how many suites/tests exist, and the current coverage baseline for the main store file.

The goal is to keep Wi-Fi setup behavior stable when step logic changes:
- permission-gated routing
- AP-mode vs standard-mode branching
- next/back progression
- next-button enable/disable rules
- failure and recovery paths

## Test Files

### Root Suite
- `meAppTests/Features/ScaleSetup/Wifi/WifiScaleSetupStoreTests.swift`

### Nested Suites
- `meAppTests/Features/ScaleSetup/Wifi/TestSuits/WifiScaleSetupStoreNavigationTests.swift`
- `meAppTests/Features/ScaleSetup/Wifi/TestSuits/WifiScaleSetupStoreModesTests.swift`
- `meAppTests/Features/ScaleSetup/Wifi/TestSuits/WifiScaleSetupStoreValidationAndErrorsTests.swift`
- `meAppTests/Features/ScaleSetup/Wifi/TestSuits/WifiScaleSetupStoreEdgeCaseTests.swift`

### Shared Fixtures
- `meAppTests/Features/ScaleSetup/Wifi/Fixtures/WifiScaleSetupStoreTestFixtures.swift`

## Suite and Test Count Summary
Coverage snapshot date: **March 5, 2026**

- Total suites: **5** (1 root + 4 nested)
- Total test cases: **52**

Breakdown by nested suite:
- `Navigation`: **10**
- `Modes And Progression`: **9**
- `Validation And Errors`: **16**
- `Edge Cases`: **17**

## Suite Responsibilities

### Navigation (10 tests)
Focus:
- intro routing by permission state
- skip-permissions behavior
- previous-step behavior with and without permission gates
- special back-navigation paths (copy-MAC, error select, step-on source state)

### Modes And Progression (9 tests)
Focus:
- connection confirm branching
- AP-mode vs standard progression
- finish-path transitions
- save-and-exit behavior from setup finish

### Validation And Errors (16 tests)
Focus:
- next-button eligibility per step
- token/account/user guard failures
- smart connect and ESP touch branch behavior
- exit behavior for finish vs non-finish states
- AP-mode-specific setup branching

### Edge Cases (17 tests)
Focus:
- boundary transitions and fallback paths
- app foreground permission re-check behavior
- setup create failure cleanup behavior
- token cache behavior across reconnect
- help modal and button text/disable logic
- default initializer behavior with dependency container registration

## How to Run

Run only WiFi Scale Setup Store tests:

```bash
xcodebuild test -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=00008120-001E095C1487A01E' -only-testing:meAppTests/WifiScaleSetupStoreTests
```

Coverage export command used:

```bash
xcodebuild test -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=00008120-001E095C1487A01E' -only-testing:meAppTests/WifiScaleSetupStoreTests -enableCodeCoverage YES -resultBundlePath /tmp/WifiScaleSetupStoreTests-final-2.xcresult
xcrun xccov view --report /tmp/WifiScaleSetupStoreTests-final-2.xcresult | rg 'Features/ScaleSetup/Wifi/Stores/WifiScaleSetupStore.swift'
```

## Bottom-Line Coverage
- `WifiScaleSetupStore.swift`: **90.19% (938/1040)**

