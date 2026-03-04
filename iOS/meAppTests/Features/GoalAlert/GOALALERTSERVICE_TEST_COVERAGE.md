# GoalAlertService Test Coverage Guide

## Purpose
This document explains how `GoalAlertService` is tested, what flows are covered, and how to maintain **85%+** coverage for this service.

## Files Involved
- Service under test:
  - `meApp/Data/Services/GoalAlertService.swift`
- Protocol:
  - `meApp/Domain/Services/GoalAlertServiceProtocol.swift`
- Main test suite:
  - `meAppTests/Features/GoalAlert/GoalAlertServiceTests.swift`
- Goal Alert fixtures/mocks:
  - `meAppTests/Features/GoalAlert/Fixtures/GoalAlertTestFixtures.swift`
  - `meAppTests/Features/GoalAlert/Mocks/MockNotificationHelperService.swift`
- Shared test support:
  - `meAppTests/Support/Mocks/Services/MockAccountService.swift`
  - `meAppTests/Support/Mocks/Services/MockBluetoothService.swift`
  - `meAppTests/Support/Mocks/Services/MockLoggerService.swift`
  - `meAppTests/Features/HealthKit/Mocks/MockKvStorageService.swift`
  - `meAppTests/Features/Account/Fixtures/AccountTestFixtures.swift`

## Coverage Strategy
`GoalAlertService` is tested with protocol-based dependency injection:
1. Inject `NotificationHelperServiceProtocol`, `AccountServiceProtocol`, `BluetoothServiceProtocol`, `LoggerServiceProtocol`, and `KvStorageServiceProtocol` into `GoalAlertService`.
2. Use a test-controlled `setGoalModalDelay` so modal tests are deterministic and fast.
3. Validate each path with explicit assertions on:
   - state changes (`isShowingAlert`)
   - callback invocations (navigation/dashboard-tab checks)
   - side effects (KV flags, alert/modal presentation, create-goal call)

## Flows Covered

### 1) `showGoalMetMessage(currentWeight:)`
- Initializes goal-met KV flag when key is missing.
- Gain/Lose goal met path presents goal-met alert.
- `NEW GOAL` button path:
  - dismisses alert
  - triggers `onNavigateToGoalSetting`
  - resets `isShowingAlert`
- Maintain goal drift path presents goal-leave alert.
- Goal-leave `NO` path keeps user in flow and resets `isShowingAlert`.
- Goal-leave `YES` path dismisses and triggers navigation callback.
- Goal-met `MAINTAIN` action path:
  - creates maintain goal via account service
  - resets goal-met flag
  - dismisses alert
- Guard path: setup-in-progress returns early with no alert.

### 2) `resetGoalMetFlag()`
- Resets account-scoped goal-met KV flag to `false`.

### 3) `checkSetGoalCard(entryCount:)`
- Does not show card when dashboard tab callback is false.
- Shows set-goal modal when eligible (entry count >= 3, no goal set, not already shown).
- Persists set-goal modal shown flag.
- Re-checks dashboard callback after delay and cancels modal if tab changed.

## Assertion Quality
- No test case is assertion-free.
- Every test includes `#expect(...)` checks and uses `Issue.record(...)` in guard branches.
- Tests verify behavior, not just method invocation.

## Run and Check Coverage
From repo root (`iOS`):

**Focused Goal Alert tests (device):**
```bash
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meAppTests \
  -destination "id=<YOUR_DEVICE_ID>" \
  -only-testing:meAppTests/GoalAlertServiceTests
```

**Note:** In this project, simulator builds can fail for certain vendor XCFrameworks without simulator slices. Use a connected device for reliable execution.

**Coverage check from xcresult:**
```bash
xcrun xccov view --report <PATH_TO_XCRESULT> | rg 'GoalAlertService.swift'
```


## Team Expectation
- Keep `GoalAlertService.swift` coverage at **85%+**.
- Any goal-alert behavior change should include a regression test update in `GoalAlertServiceTests`.

## Current Coverage
- `GoalAlertService.swift`: **90.1%** from latest available test result bundle (`Test-meAppTests 1-2026.03.03_10-36-34-+0530.xcresult`, March 3, 2026).
