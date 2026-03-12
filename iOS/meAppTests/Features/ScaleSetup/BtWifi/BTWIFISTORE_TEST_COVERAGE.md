# BtWifiScaleSetupStore Test Coverage Guide

## Purpose
This document explains:
- what is being tested for the BT+WiFi scale setup flow
- why a small dependency-injection refactor was needed
- how the suites are split
- what behavior each suite is intended to protect
- how to extend the tests without weakening determinism

The goal is to keep the BT+WiFi setup journey stable when navigation, pairing, Wi-Fi setup, duplicate-user handling, or retry/recovery logic changes.

## Current Coverage
Coverage snapshot date: **March 5, 2026**

Bottom-line (base store file) coverage:
- `BtWifiScaleSetupStore.swift`: **91.04% (833/915)**

## Test Count Summary
- `Store Core`: 13 tests
- `Customization`: 11 tests
- `Navigation`: 7 tests
- `Pairing Flow`: 6 tests
- `Recovery And Errors`: 7 tests
- `WiFi And Users`: 9 tests
- Total: **53 tests**

## Files Involved

### Production code under test
- `meApp/Features/ScaleSetup/BtWifi/Stores/BtWifiScaleSetupStore.swift`
- `meApp/Features/ScaleSetup/BtWifi/Stores/BtWifiScaleSetupStoreActionHandlers.swift`
- `meApp/Features/ScaleSetup/BtWifi/Stores/BtWifiScaleSetupStorePairingFlow.swift`
- `meApp/Features/ScaleSetup/BtWifi/Stores/BtWifiScaleSetupStoreWifiUserFlow.swift`
- `meApp/Features/ScaleSetup/BtWifi/Stores/BtWifiScaleSetupStoreNavigationFlow.swift`
- `meApp/Features/ScaleSetup/BtWifi/Stores/BtWifiScaleSetupStoreHelpers.swift`
- `meApp/Features/ScaleSetup/BtWifi/Stores/BtWifiScaleSetupStoreUserFlow.swift`
- `meApp/Features/ScaleSetup/BtWifi/Stores/BtWifiScaleSetupStoreCustomization.swift`

### Main test entry point
- `meAppTests/Features/ScaleSetup/BtWifi/BtWifiStoreTests.swift`

### BT+WiFi-specific test fixtures and suites
- `meAppTests/Features/ScaleSetup/BtWifi/Fixtures/BtWifiStoreTestFixtures.swift`
- `meAppTests/Features/ScaleSetup/BtWifi/TestSuits/BtWifiStoreCoreStateTests.swift`
- `meAppTests/Features/ScaleSetup/BtWifi/TestSuits/BtWifiStoreCustomizationTests.swift`
- `meAppTests/Features/ScaleSetup/BtWifi/TestSuits/BtWifiStoreNavigationTests.swift`
- `meAppTests/Features/ScaleSetup/BtWifi/TestSuits/BtWifiStorePairingTests.swift`
- `meAppTests/Features/ScaleSetup/BtWifi/TestSuits/BtWifiStoreWifiUserFlowTests.swift`
- `meAppTests/Features/ScaleSetup/BtWifi/TestSuits/BtWifiStoreRecoveryTests.swift`

### Shared mocks/support used by these suites
- `meAppTests/Support/DI/TestDependencyContainer.swift`
- `meAppTests/Support/Mocks/Services/MockBluetoothService.swift`
- `meAppTests/Support/Mocks/Services/MockPermissionsService.swift`
- `meAppTests/Support/Mocks/Services/MockAccountService.swift`
- `meAppTests/Support/Mocks/Services/MockNetworkMonitor.swift`
- `meAppTests/Support/Mocks/Services/TestNotificationHelperService.swift`
- `meAppTests/Support/Mocks/Services/MockWifiScaleService.swift`
- `meAppTests/Support/Mocks/Services/MockPushNotificationService.swift`
- `meAppTests/Features/Bluetooth/Mocks/MockScaleService.swift`
- `meAppTests/Features/HealthKit/Mocks/MockEntryService.swift`
- `meAppTests/Features/Entry/Mocks/MockGoalAlertService.swift`

## Why The Protocol/DI Changes Were Needed

`BtWifiScaleSetupStore` was originally wired to several concrete singleton-style services:
- `ScaleService`
- `BluetoothService`
- `PermissionsService`
- `WifiScaleService`
- `PushNotificationService`
- `EntryService`
- `GoalAlertService`
- `NetworkMonitor.shared`

That works in production, but it blocks deterministic unit testing for the full flow.

### The specific problem with `ScaleServiceProtocol`
The store calls `scaleService.createR4Scale(...)` inside the pairing success path.

Without that method on `ScaleServiceProtocol`, the store had only two bad test options:
1. bind directly to concrete `ScaleService`
2. instantiate or partially drive the real persistence/network stack in unit tests

Both options are inappropriate for a store unit test:
- they make tests slow
- they make failures depend on persistence/network side effects
- they make edge-case branches much harder to isolate
- they hide regressions in orchestration logic behind unrelated service behavior

So the change was:
- not a behavior change
- not a new product capability
- only a protocol-surface alignment so the store can be tested against a mock for the path it already uses in production

### The other DI changes
The store now depends on protocols or testable abstractions where appropriate:
- `NotificationHelperServiceProtocol`
- `PermissionsServiceProtocol`
- `BluetoothServiceProtocol`
- `AccountServiceProtocol`
- `WifiScaleServiceProtocol`
- `ScaleServiceProtocol`
- `PushNotificationServiceProtocol`
- `EntryServiceProtocol`
- `GoalAlertServiceProtocol`
- `NetworkMonitoring`

This keeps production behavior the same while letting tests inject:
- controlled success/failure results
- deterministic permission and connectivity state
- controlled discovery, pairing, user-list, and Wi-Fi responses
- no real SDK/persistence side effects

## Coverage Strategy
The BT+WiFi store is branch-heavy and stateful, so coverage is split by behavior instead of by file.

Each important flow should be tested with:
1. success path
2. validation/guard rejection path
3. retry or recovery path
4. failure path
5. state-transition assertions

For this store, the most important assertions are:
- current step
- error state
- connection state
- preserved form values
- side-effect call counts to mocked services

## Suite Breakdown

### 1) Store Core
File:
- `meAppTests/Features/ScaleSetup/BtWifi/TestSuits/BtWifiStoreCoreStateTests.swift`

Test count:
- 13

Focus:
- base computed state and flags
- core step-view branch coverage
- core transition guards and index-revert behavior
- dashboard branch initialization path

Covered examples:
- `stepViews` empty-before-configure and configured behavior
- settings/reconnect/duplicate mode flag behavior
- password form validity behavior
- gathering/wifi-list/password/connecting/view-settings/update/measurement branch coverage
- step-index revert while exiting
- dashboard metrics branch wiring with concrete dashboard dependencies

### 2) Navigation
File:
- `meAppTests/Features/ScaleSetup/BtWifi/TestSuits/BtWifiStoreNavigationTests.swift`

Test count:
- 7

Focus:
- intro routing
- permissions/network gating
- Wi-Fi-only entry
- back-navigation behavior
- reconnect entry behavior

Covered examples:
- intro -> wakeup when prerequisites are met
- intro -> permissions when prerequisites are missing
- Wi-Fi-only flow starts at gathering network
- network selection opens password step
- network selection falls back when permissions/network are missing
- back from password resets network form
- reconnect flow loads user list and enters max-user recovery state

### 2) Customization
File:
- `meAppTests/Features/ScaleSetup/BtWifi/TestSuits/BtWifiStoreCustomizationTests.swift`

Test count:
- 11

Focus:
- customization page preload behavior
- username and scale-mode validation
- save/back behavior for view-settings screens
- dependency interactions during settings update
- incomplete/invalid configuration rejection
- repeated-entry and repeated-save stability

Covered examples:
- username customization preloads saved preference and current user list
- scale metrics customization falls back to defaults and preserves saved snapshots on re-entry
- scale mode changes toggle next-button enablement based on actual delta from snapshots
- invalid username save is rejected without persisting or marking settings as saved
- back from username customization restores original value
- repeated scale-metrics saves preserve the latest snapshot without duplicating selection state
- customization helper methods track visited items and present the correct modals
- `updateCustomizeSettings()` fails fast when no saved scale exists
- `updateCustomizeSettings()` success updates scale preference, pushes local changes, updates Bluetooth account state, and navigates to step-on
- local scale-preference update failure surfaces `.updateSettingsFailed`
- Bluetooth account update failure keeps the flow recoverable and prevents forward navigation

### 3) Navigation
File:
- `meAppTests/Features/ScaleSetup/BtWifi/TestSuits/BtWifiStoreNavigationTests.swift`

Test count:
- 7

Focus:
- intro routing
- permissions/network gating
- Wi-Fi-only entry
- back-navigation behavior
- reconnect entry behavior

Covered examples:
- intro -> wakeup when prerequisites are met
- intro -> permissions when prerequisites are missing
- Wi-Fi-only flow starts at gathering network
- network selection opens password step
- network selection falls back when permissions/network are missing
- back from password resets network form
- reconnect flow loads user list and enters max-user recovery state

### 4) Pairing Flow
File:
- `meAppTests/Features/ScaleSetup/BtWifi/TestSuits/BtWifiStorePairingTests.swift`

Test count:
- 6

Focus:
- discovery handling
- known/new scale behavior
- confirm-pair result branches
- save-scale side effects

Covered examples:
- new discovery event advances to bluetooth connection
- known discovery event disconnects/reports known-scale alert
- confirm-pair success saves the scale and triggers push-setup
- duplicate-user response enters duplicate recovery flow
- memory-full response enters max-user recovery flow
- pairing failure leaves connection in failure state

### 5) Recovery And Errors
File:
- `meAppTests/Features/ScaleSetup/BtWifi/TestSuits/BtWifiStoreRecoveryTests.swift`

Test count:
- 7

Focus:
- retry routing
- timeout and recovery transitions
- duplicate validation state
- Wi-Fi validation state
- permission-driven rerouting

Covered examples:
- retry from Wi-Fi failure resets state and returns to gathering network
- retry from Bluetooth failure targets pairing flow
- retry from measurement timeout returns to step-on
- duplicate-user next-button enablement rules
- Wi-Fi-password next-button validation rules
- network loss on Wi-Fi list routes back to recovery state
- permissions step requests the next missing Bluetooth permission

### 6) Wi-Fi And User Flow
File:
- `meAppTests/Features/ScaleSetup/BtWifi/TestSuits/BtWifiStoreWifiUserFlowTests.swift`

Test count:
- 9

Focus:
- Wi-Fi list retrieval
- Wi-Fi setup validation
- Wi-Fi success/failure outcomes
- restore-account and duplicate-user actions
- user-list filtering

Covered examples:
- fetch Wi-Fi networks success
- fetch Wi-Fi networks failure
- password validation and open-network branch
- Wi-Fi setup success updates configured state
- Wi-Fi setup failure preserves failure state/error code
- restore-account success deletes matching user and reconnects
- restore-account permission failure routes to permissions
- duplicate username save preserves edited value and reconnects
- current user token is filtered out of retrieved scale users

## What These Tests Intentionally Validate

The suites are designed to protect regressions in:
- multi-step navigation
- automatic step transitions
- pairing success/failure routing
- duplicate-user and max-user handling
- restore-account flow
- Wi-Fi selection and password validation
- Wi-Fi network retrieval failure handling
- retry/recovery state cleanup
- permission/network gating

These are the areas most likely to break when the setup journey changes.

The customization suite specifically protects regressions in:
- preloaded customization state from saved preferences
- view-settings save/back transitions
- repeated customization entry/save cycles
- invalid/incomplete customization input handling
- scale-service and Bluetooth-service update sequencing
- recovery behavior after failed update-settings attempts

## What Is Not Fully Covered Here

These suites do not try to fully integration-test:
- SwiftUI rendering/layout
- real Bluetooth SDK behavior
- real persistence/database writes
- real dashboard-store customization behavior
- real timers that take minutes to elapse in production

Those would belong to:
- integration tests
- service tests
- UI tests

The store tests are intentionally fast and deterministic.

## Fixture Design

`BtWifiStoreTestFixtures.makeSUT()` does the following:
- resets the dependency container
- registers mock services for all store dependencies
- seeds a default active account
- seeds default Bluetooth permission state
- injects a mock network monitor
- injects a lightweight Bluetooth setup manager mock
- creates the store with deterministic test dependencies

Important design choice:
- the `dashboardStoreFactory` passed to the store in these tests is a failing closure

Reason:
- these suites are focused on BT+WiFi setup orchestration, not dashboard editing
- if a test accidentally reaches dashboard-store behavior, it should fail immediately instead of hiding an unintended dependency

## How To Add New BT+WiFi Store Tests
1. Decide which suite the behavior belongs in.
2. Reuse `BtWifiStoreTestFixtures.makeSUT()` unless you truly need a new fixture shape.
3. Configure only the mock responses required for that scenario.
4. Drive the store through the actual public flow method when possible.
5. Assert:
   - final `currentStep`
   - `scaleSetupError`
   - `connectionState`
   - preserved form state
   - side-effect call counts on mocks
6. Prefer testing user-observable transitions over private implementation details.

## Coverage Expectation
- Minimum target for the BT+WiFi setup store area: **90%**
- Latest measured base-store coverage (`BtWifiScaleSetupStore.swift`): **91.04%**
- Every bug fix in the setup journey should add a regression test
- Retry/recovery bugs should always include both:
  - the failing-state assertion
  - the recovery-state assertion

## Verification Notes

Focused BT+WiFi store validation was run with:

```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -destination 'id=00008020-00191D5136E9002E' \
  -only-testing:meAppTests/BtWifiStoreTests
```

Result:
- `BtWifiStoreTests` passed on the connected iPhone
- the new `Customization` suite passed end-to-end

Important environment note:
- this project currently relies on bundled XCFrameworks that do not build cleanly for the simulator path used in focused test runs here
- for this reason, BT+WiFi store verification was performed on a connected physical device

Device requirements:
- connected
- unlocked
- trusted by Xcode

Known warning from the current focused run:
- `BtWifiStoreTestFixtures.swift` emits a Swift warning about sending non-Sendable `Device` across a protocol requirement boundary
- this warning predates the customization tests and was not changed in this pass

## Team Guidance
- Keep orchestration tests focused on store behavior, not service internals
- Prefer mocks over real service instances for BT+WiFi store tests
- Avoid adding test-only branches to production logic when protocol alignment or abstraction solves the same problem cleanly
- If the setup flow changes, update this document alongside the tests
