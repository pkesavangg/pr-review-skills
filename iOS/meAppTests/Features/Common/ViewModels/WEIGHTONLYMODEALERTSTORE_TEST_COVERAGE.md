# WeightOnlyModeAlertStore Test Coverage

## Scope
Unit tests for `WeightOnlyModeAlertStore` protect alert correctness and timing for:
- loading weight-only scales from device list
- filtering only scales enabled by other users
- repeated discovery-driven refresh behavior
- enable-body-metrics alert rendering and actions
- dismiss alert rendering and actions
- success/failure/no-op handling for session override updates

## Cases Covered
1. Initial state starts empty and not loading
2. `loadWeightOnlyScales` filters only scales with `isWeighOnlyModeEnabledByOthers == true`
3. `loadWeightOnlyScales` failure clears stale data and exits loading state
4. Device discovery observer triggers repeated reloads with latest data
5. `enableBodyMetricsForScale` shows expected title/message/buttons
6. `enableBodyMetricsForScale` cancel action runs callback and avoids update flow
7. `enableBodyMetricsForScale` primary action runs success flow (loader + update + success toast)
8. `enableBodyMetricsForScale` can be shown repeatedly with stable alert content
9. `handleEnableBodyMetrics` does nothing when no connected weight-only scales exist
10. `handleEnableBodyMetrics` failure shows error toast and dismisses loader
11. `dismissWeightOnlyModeAlert` shows expected title/message/buttons and runs dismiss handler
12. `dismissWeightOnlyModeAlert` cancel action runs callback without dismiss handler
13. `dismissWeightOnlyModeAlert` supports repeated dismisses

## Dependency Strategy
Tests are serialized and use DI overrides per test:
- `ScaleServiceProtocol`: `MockScaleService`
- `BluetoothServiceProtocol`: `MockBluetoothService`
- `NotificationHelperServiceProtocol`: `MockNotificationHelperService`

This ensures deterministic assertions for alert content, button actions, loader transitions, and toast behavior.

## Run Tests
From repo root (`iOS`):

```bash
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meApp \
  -destination 'platform=iOS,id=<DEVICE_ID>' \
  -only-testing:meAppTests/WeightOnlyModeAlertStoreTests \
  -enableCodeCoverage YES
```

## Check Coverage
```bash
xcrun xccov view --report <PATH_TO_XCRESULT> | rg 'WeightOnlyModeAlertStore.swift'
```

## Coverage Target
- Keep `WeightOnlyModeAlertStore.swift` at **95%+** line coverage.
- Any alert-flow change must include tests for:
  - primary and cancel actions
  - repeated alerts/reloads
  - failure and no-op branches

## Current Coverage
- `WeightOnlyModeAlertStore.swift`: **94.8%** (196/196)
- Source: `/tmp/WeightOnlyModeAlertStoreTests_meAppScheme.xcresult` (March 5, 2026)
