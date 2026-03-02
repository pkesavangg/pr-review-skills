# HealthKitService Test Coverage Guide

## Purpose
This document explains how `HealthKitService` is tested, what flows are covered, and how to reach **85%+** code coverage for the service (per TESTING_GUIDELINES.md).

## Files Involved
- Service under test: `meApp/Data/Services/HealthKitService.swift`
- Protocol / handler abstraction: `meApp/Domain/Services/HealthKitHandlerProtocol.swift`, `meApp/Data/Services/AppleHealthHandlerAdapter.swift`
- Main test suite: `meAppTests/Features/HealthKit/HealthKitServiceTests.swift`
- HealthKit-specific fixtures/mocks:
  - `meAppTests/Features/HealthKit/Fixtures/HealthKitTestFixtures.swift`
  - `meAppTests/Features/HealthKit/Mocks/MockIntegrationService.swift`
  - `meAppTests/Features/HealthKit/Mocks/MockHealthKitHandler.swift`
  - `meAppTests/Features/HealthKit/Mocks/MockKvStorageService.swift`
  - `meAppTests/Features/HealthKit/Mocks/MockEntryService.swift`
- Shared test support: `MockLoggerService`, `MockAccountService`, `AccountTestFixtures` (for account model)

## Coverage Strategy
HealthKitService is tested via **protocol-based injection** 
1. **HealthKitHandlerProtocol** abstracts the ggHealthKitPackage so tests never touch real Apple Health.
2. **Constructor injection**: `HealthKitService(integrationService:logger:accountService:entryService:kvStore:healthKitHandler:)` with optional protocol parameters; when `nil`, production defaults are used.
3. Each test builds a **makeSUT** with only the mocks needed for that scenario.

## Flows Covered

### 1) integrate(turnOn:)
- **Success** (turnOn): available, authorized, permissions granted, setStoredIntegrationData succeeds → returns `true`.
- **User conflict**: `isIntegrationAlreadyUsed` true → throws `IntegrationError.userConflict`.
- **Unavailable**: handler.available() false → returns `false`.
- **Authorization failed**: requestAuthorization() false → returns `false`.
- **No permissions**: getApprovedPermissionList empty → returns `false`.
- **Persistence failure**: setStoredIntegrationData throws → returns `false`.
- **Turn off**: calls clearHealthKit (clearIntegrationStatus + deleteAllData) → returns `false`.

### 2) isHKOutOfSync()
- Integrated + no permissions → `true`.
- Not integrated / has permissions / getStoredIntegrationData throws → `false`.

### 3) syncAllData()
- No active account → returns without throwing (early exit).

### 4) openAppleHealth()
- Delegates to handler; call count asserted.

### 5) checkAuthorizationStatus / getApprovedPermissionList
- Non-empty list → true; empty → false. List returned as-is from handler.

### 6) syncNewData(notification:) / deleteEntry(notification:)
- Success: payload built and handler saveData/deleteEntry called.

### 7) clearHealthKit()
- Success: clearIntegrationStatus and deleteAllData.
- clearIntegrationStatus throws: deleteAllData still called.
- deleteAllData throws: error rethrown.

### 8) shouldShowHKIntegrationModal()
- No accountId → `nil`.
- **Out of sync**: integrated, no permissions, flag not set → `.outOfSync`.
- **Finish adding**: permissions granted, no stored integration, not used by another account → `.finishAdding`.
- **Add integration**: account has HealthKit on, no stored data, not used by another → `.addIntegration`.
- No modal needed (e.g. integrated + has permissions) → `nil`.

### 9) setWaitingForPermissionsRestored / clearWaitingForPermissionsRestored
- KV scoped key set/cleared as expected.

### 10) checkIfPermissionsRestoredAfterOutOfSync()
- Not waiting → `false`.
- Waiting + permissions restored → clears flag, returns `true`.
- Waiting + not restored → `false`.

## How makeSUT Works
`makeSUT` in `HealthKitServiceTests` builds `HealthKitService` with mock dependencies only (no DI container reset; HealthKitService does not use @Injector when constructed with explicit params). All protocol parameters default to fresh mocks so tests stay isolated.

## Run and Check Coverage
From **repo root**:

**Simulator (use a destination that exists on your machine, e.g. iPhone 17):**
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Dev \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:meAppTests/HealthKitServiceTests
```

In Xcode: Test Report (Cmd+9) → Coverage tab → `HealthKitService.swift`. Target **85%+** for this file.

## Team Expectation
- Keep HealthKitService coverage at least **80%**, preferably **85%+** for this critical integration.
- New branches or bug fixes in HealthKitService should add or extend tests in `HealthKitServiceTests`.
