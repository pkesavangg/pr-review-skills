# BluetoothScaleSetupStore Test Coverage Guide

## Purpose
This document summarizes how `BluetoothScaleSetupStore` tests are organized, how many suites/tests exist, and the current coverage baseline for the main store file.

The goal is to keep Bluetooth setup behavior stable when flow logic changes:
- pairing start and retry behavior
- permission-gated routing
- step progression and next/back rules
- user selection handling
- setup completion behavior
- failure and recovery paths

## Test Files

### Root Suite
- `meAppTests/Features/ScaleSetup/Bluetooth/BluetoothScaleSetupStoreTests.swift`

### Nested Suites
- `meAppTests/Features/ScaleSetup/Bluetooth/TestSuits/BluetoothScaleSetupStoreCompletionAndRecoveryTests.swift`
- `meAppTests/Features/ScaleSetup/Bluetooth/TestSuits/BluetoothScaleSetupStoreNavigationTests.swift`
- `meAppTests/Features/ScaleSetup/Bluetooth/TestSuits/BluetoothScaleSetupStorePairingTests.swift`
- `meAppTests/Features/ScaleSetup/Bluetooth/TestSuits/BluetoothScaleSetupStoreEdgeCoverageTests.swift`

### Shared Fixtures
- `meAppTests/Features/ScaleSetup/Bluetooth/Fixtures/BluetoothScaleSetupStoreTestFixtures.swift`

## Suite and Test Count Summary
Coverage snapshot date: **March 5, 2026**

- Total suites: **5** (1 root + 4 nested)
- Total test cases: **35**

Breakdown by nested suite:
- `Completion Duplicate And Recovery`: **5**
- `Navigation And Selection`: **6**
- `Pairing Start Retry And Errors`: **6**
- `Edge Coverage`: **18**

## Suite Responsibilities

### Completion Duplicate And Recovery (5 tests)
Focus:
- post-pair completion flow
- step-on to finish progression
- duplicate handling for same-user vs different-user return paths
- non-finish exit confirmation behavior

### Navigation And Selection (6 tests)
Focus:
- intro routing with and without Bluetooth permissions
- permission-step next-state behavior
- select-user gating for next button
- resume behavior after successful save
- finish-step dismiss behavior

### Pairing Start Retry And Errors (6 tests)
Focus:
- pairing auto-start when entering connecting step
- retry behavior when re-entering connecting step
- discovery timeout and ignored non-Bluetooth discovery event handling
- successful pairing/save path
- confirm-pair failure path

### Edge Coverage (18 tests)
Focus:
- back-button rules and step-view coverage
- help modal and exit primary action behavior
- confirm-pair guard and unexpected-response branches
- duplicate alert pair flow and duplicate-return guard fallback
- save guard branches (missing context, already-saved, no-active-account)
- duplicate-delete failure tolerance
- permission restore behavior when scale was already saved
- cleanup and state-reset behavior

## How to Run

Run only Bluetooth Scale Setup Store tests:

```bash
xcodebuild test -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=00008120-001E095C1487A01E' -only-testing:meAppTests/BluetoothScaleSetupStoreTests
```

Coverage export command used:

```bash
xcodebuild test -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=00008120-001E095C1487A01E' -only-testing:meAppTests/BluetoothScaleSetupStoreTests -enableCodeCoverage YES -resultBundlePath /tmp/BluetoothScaleSetupStoreTests.xcresult
xcrun xccov view --report /tmp/BluetoothScaleSetupStoreTests.xcresult | rg 'Features/ScaleSetup/Bluetooth/Stores/BluetoothScaleSetupStore.swift'
```

## Bottom-Line Coverage
- `BluetoothScaleSetupStore.swift`: **97.3% (732/752)**
