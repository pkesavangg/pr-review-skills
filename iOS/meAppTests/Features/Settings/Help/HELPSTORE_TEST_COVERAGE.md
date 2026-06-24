# HelpStore Test Coverage

## Overview
- Target file: `meApp/Features/Settings/Help/Stores/HelpStore.swift`
- Test file: `meAppTests/Features/Settings/Help/HelpStoreTests.swift`
- Coverage focus: debug/help actions, logs upload flows, resync flows, scale-log flows, and local-data cleanup handling.

## Scenario Coverage
- Computed property behavior:
  - `isSendScaleLogEnabled` for multiple, single-connected, and single-disconnected scale states.
  - `shouldShowScaleTroubleshooting` for empty/non-empty scale states.
- Initialization and publisher behavior:
  - `scalesPublisher` filtering to btWifiR4 scales.
- UI state actions:
  - `openProductManual`, `handleHeaderTap`, `dismissDebugMenu`, and `openHelp`.
- Weight Gurus log flow:
  - success path (loader + success toast),
  - `HTTPError.noInternet` path (no error toast),
  - generic-error path (error toast).
- Entry resync flow:
  - online success path (clear + resync + success toast),
  - online failure path (error toast).
- Local data cleanup:
  - success alert path,
  - failure alert path.
- Scale log flow:
  - explicit-device, single-scale, and multi-scale sheet behavior,
  - bluetooth get-logs failure,
  - upload generic failure,
  - upload no-internet path.
- App review trigger:
  - logger side-effect assertion for `showAppRateModal`.

## Estimated Coverage
- Current coverage (as of 2026-03-05): **~96.4%**
- Uncovered/partially-covered areas:
  - `resyncEntries` offline branch depends on runtime network connectivity.
  - `AppRatingHelper.requestReview()` system-level side effect cannot be unit-asserted directly.

## Test Assets
- Tests:
  - `meAppTests/Features/Settings/Help/HelpStoreTests.swift`
- Mocks:
  - `meAppTests/Features/Settings/Help/Mocks/MockHelpStoreEntryService.swift`
  - `meAppTests/Features/Settings/Help/Mocks/MockHelpStoreBluetoothService.swift`
  - `meAppTests/Features/Settings/Help/Mocks/MockHelpStoreLoggerService.swift`
  - `meAppTests/Features/GoalAlert/Mocks/MockNotificationHelperService.swift`
  - `meAppTests/Features/Bluetooth/Mocks/MockScaleService.swift`
  - `meAppTests/Support/Mocks/Services/MockAccountService.swift`
