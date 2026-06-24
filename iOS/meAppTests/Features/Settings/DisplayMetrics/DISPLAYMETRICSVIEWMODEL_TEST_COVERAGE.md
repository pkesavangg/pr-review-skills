# DisplayMetricsViewModel Test Coverage

## Scope
Unit tests for `DisplayMetricsViewModel` protect regression-prone settings behavior for:
- loading and ordering body/progress metrics from saved preference
- weight-only and heart-rate-off mode behavior
- metric selection/reorder updates
- save flow to local preference + optional Bluetooth sync
- error handling with loader/toast correctness

## Cases Covered
1. Initial load orders enabled body and progress metrics from preference
2. Load applies weight-only info and heart-rate-off banner behavior
3. Load without preference falls back to default body/progress metric lists
4. Weight-only preference exposes only BMI in body metrics
5. `updateMetrics` toggle path marks changes and updates value
6. `updateMetrics` reorder path preserves enabled set
7. `updateProgressMetrics` reorder path preserves enabled set
8. `updateProgressMetrics` toggle path updates value
9. Body metric toggle blocks heart-rate enable when heart-rate banner is shown
10. Body metric toggle allowed path updates state and reorders item
11. Progress metric toggle blocked heart-rate path leaves state unchanged
12. Progress metric toggle allowed path updates state and reorders item
13. Save success updates DTO metrics, pushes sync, dismisses loader, and shows success toast
14. Save excludes `heartRate` when pulse is off
15. Connected save path calls Bluetooth `updateAccount`
16. Weight-only save path preserves non-progress metrics and updates BMI/progress placement
17. Weight-only save path removes BMI when disabled
18. Save failure in local update path shows error toast and dismisses loader
19. Save failure in Bluetooth sync path shows error toast and dismisses loader
20. Save without preference exits with no side effects
21. DI-fallback initializer resolves required dependencies from container
22. `refreshScale` fetch path loads latest model by scale id from store

## Dependency Strategy
Tests are serialized and run with explicit mock injection:
- `NotificationHelperServiceProtocol`: `MockNotificationHelperService`
- `ScaleServiceProtocol`: `MockScaleService`
- `BluetoothServiceProtocol`: `MockBluetoothService`
- `LoggerServiceProtocol`: `MockLoggerService`
- `AccountServiceProtocol`: `MockAccountService`

This keeps the suite deterministic and avoids cross-suite DI contamination.

## Run Tests
From repo root (`iOS`):

```bash
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meApp \
  -destination 'platform=iOS,id=<DEVICE_ID>' \
  -only-testing:meAppTests/DisplayMetricsViewModelTests \
  -enableCodeCoverage YES
```

## Check Coverage
```bash
xcrun xccov view --report <PATH_TO_XCRESULT> | rg 'DisplayMetricsViewModel.swift'
```

## Coverage Target
- Keep `DisplayMetricsViewModel.swift` at **95%+** line coverage.
- Any scale-display-metrics behavior change must include tests for:
  - load ordering + banner states
  - body/progress metric update paths (toggle and reorder)
  - save success/failure and connected/disconnected branches

## Current Coverage
- `DisplayMetricsViewModel.swift`: **96.7%** (411/425)
- Source: `/tmp/DisplayMetricsViewModelTests.xcresult` (March 5, 2026)
