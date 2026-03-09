# SettingsStore Test Guide

## What this document is for
This file explains how the `SettingsStore` tests are organized, what each suite is responsible for, how dependencies are wired for tests, and how to extend the suite safely.

If you are new to this area, start here before adding or changing tests.

## Current Coverage
Latest recorded coverage for `SettingsStore` is `93.9%`.

This suite is intended to keep `SettingsStore` safely above the project expectation for store-level regression coverage while protecting the most failure-prone user flows.

## Where the tests live
Main suite entry:
- `meAppTests/Features/Settings/SettingsStoreTests.swift`

Per-topic suites:
- `meAppTests/Features/Settings/TestSuits/SettingsStoreCoreStateTests.swift`
- `meAppTests/Features/Settings/TestSuits/SettingsStoreProfileAndPreferencesTests.swift`
- `meAppTests/Features/Settings/TestSuits/SettingsStorePasswordTests.swift`
- `meAppTests/Features/Settings/TestSuits/SettingsStoreGoalTests.swift`
- `meAppTests/Features/Settings/TestSuits/SettingsStoreWeightlessTests.swift`
- `meAppTests/Features/Settings/TestSuits/SettingsStoreAccountActionsTests.swift`

Shared helpers:
- `meAppTests/Features/Settings/Fixtures/SettingsStoreTestFixtures.swift`
- `meAppTests/Support/DI/TestDependencyContainer.swift`
- `meAppTests/Support/Mocks/Services/TestNotificationHelperService.swift`
- `meAppTests/Support/Mocks/Services/MockFeedService.swift`

## How the suite is structured
`SettingsStoreTests.swift` defines the root Swift Testing suite:

- `@Suite(.serialized)`
- `@MainActor`

This matters for two reasons:

- `SettingsStore` is main-actor-bound UI/store code, so tests run on the main actor.
- The suite is serialized because the tests share the app DI container and some concrete singleton-backed services. Running them in parallel would make the suite flaky.

Each file under `TestSuits/` extends the same root `SettingsStoreTests` type and adds one focused nested suite. This keeps the suite readable without turning one file into a thousand-line test dump.

We intentionally split the suite into separate nested structs because:
- each struct maps to one user-facing settings area
- failures are easier to scan in Xcode and CI
- adding a new test is simpler because the correct file is usually obvious
- setup stays shared while test intent stays local to one topic

Important runtime note:
- if you run the root `SettingsStoreTests` suite from `SettingsStoreTests.swift`, Swift Testing will also discover and run all nested suites defined in the files inside `TestSuits/`
- because the root suite is marked `@Suite(.serialized)`, those suites still run serially, not in parallel

## Test count summary
- `Core State`: 25 tests
- `Profile And Preferences`: 44 tests
- `Password`: 23 tests
- `Goals`: 21 tests
- `Weightless`: 18 tests
- `Account Actions`: 14 tests
- Total: 145 tests

## What each suite covers
### Core State
File:
- `meAppTests/Features/Settings/TestSuits/SettingsStoreCoreStateTests.swift`

Test count:
- 25

Use this suite for:
- initial store hydration
- derived display text
- browser state
- picker presentation flags
- account publisher reactions
- entry publisher reactions
- feed badge and message-title behavior
- general state that does not belong to one specific settings flow

Examples already covered:
- active-account hydration
- logout-all visibility
- `messagesTitleText`
- browser dismissal cleanup
- feed badge publisher updates

### Profile And Preferences
File:
- `meAppTests/Features/Settings/TestSuits/SettingsStoreProfileAndPreferencesTests.swift`

Test count:
- 44

Use this suite for:
- edit-profile form population
- profile save flows
- body-composition preference updates
- notification preference updates
- height, gender, unit, activity, and related preference handling

Examples already covered:
- pristine vs dirty edit-form population
- invalid height handling
- no-internet suppression cases
- pending-scale-update alerts
- save success and failure branches

### Password
File:
- `meAppTests/Features/Settings/TestSuits/SettingsStorePasswordTests.swift`

Test count:
- 23

Use this suite for:
- change-password field validation
- touch/blur handling
- forgot-password flows
- exit-confirmation behavior for password editing
- password save success and failure behavior

Examples already covered:
- discard confirmation
- server-error messaging
- forgot-password success and retry paths

### Goals
File:
- `meAppTests/Features/Settings/TestSuits/SettingsStoreGoalTests.swift`

Test count:
- 21

Use this suite for:
- goal-form population
- maintain/lose/gain goal handling
- goal save logic
- goal exit/discard confirmation
- latest-entry fallback behavior used by goal saving

Examples already covered:
- dirty goal exit confirmation
- maintain-goal payload creation
- pending-scale-update alert behavior
- goal save failure toast handling

### Weightless
File:
- `meAppTests/Features/Settings/TestSuits/SettingsStoreWeightlessTests.swift`

Test count:
- 18

Use this suite for:
- weightless form population
- unit-aware formatting and validation
- weightless save flow
- toggle behavior
- weightless-specific error and alert handling

Examples already covered:
- empty-account reset behavior
- missing-weight handling
- imperial max validation
- save success and failure paths

### Account Actions
File:
- `meAppTests/Features/Settings/TestSuits/SettingsStoreAccountActionsTests.swift`

Test count:
- 14

Use this suite for:
- export CSV
- logout
- logout-all
- delete account
- integration-clear flows triggered from settings

Examples already covered:
- confirm-before-action alerts
- loader show/dismiss behavior
- success toasts
- cancel behavior
- no-internet export behavior

## Shared fixture strategy
The suite does not build a fake `SettingsStore`. It uses the real `SettingsStore` and replaces surrounding services with test doubles.

The main factory is:
- `meAppTests/Features/Settings/Fixtures/SettingsStoreTestFixtures.swift`

`makeSUT(...)` does the common setup:
- resets the shared DI container
- creates default mocks when a test does not provide one
- seeds a default active account unless the test opts out
- registers dependencies needed by `SettingsStore`
- creates the real `SettingsStore`
- injects the same test doubles back onto the store instance for direct assertions

This keeps most tests small. A typical test only overrides the dependency it actually cares about.

## Dependency injection used by the suite
The suite uses a mix of direct property injection and shared-container registration.

### Shared DI reset
`TestDependencyContainer.reset()` clears `DependencyContainer.shared.dependencies` at the start of fixture creation.

This is important because `SettingsStore` reads several dependencies through the app container. Without a reset, one test can leak services into the next test.

### Services commonly injected for tests
`SettingsStore` tests usually register or assign:
- `TestNotificationHelperService`
- `MockAccountService`
- `MockEntryService`
- `MockFeedService`
- `MockBluetoothService`
- `MockIntegrationService`
- `MockLoggerService`
- `GoalAlertService.shared`

### Why `TestNotificationHelperService` is concrete
`SettingsStore` still uses the concrete `NotificationHelperService` in some paths, not only the protocol. Because of that, the suite uses:

- `TestNotificationHelperService`, which subclasses `NotificationHelperService`

This gives tests two things at once:
- compatibility with code that expects the concrete class
- counters and captured state for assertions like alert, toast, loader, and modal calls

### Why `MockFeedService` exists
`MockFeedService` is the shared feed double for settings tests. It is used to control:
- unread count
- feed settings
- badge publisher updates

This is how tests cover `messagesTitleText`, badge visibility, and related feed-driven state without touching the real feed service.

## What is covered today
The current suite protects regressions in:
- account-derived display state
- browser and picker presentation state
- edit-profile and weightless form population
- password, goal, and profile discard confirmation flows
- forgot-password and CSV export flows
- feed badge and unread-count display
- validation and no-op branches for settings input
- save/update failure handling, including alert and toast behavior

## How to run the tests
Run the full `meAppTests` suite:

```bash
xcodebuild test -project meApp/iOS/meApp.xcodeproj -scheme meAppTests
```

Build tests without executing them:

```bash
xcodebuild build-for-testing -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'generic/platform=iOS'
```

If you only want to work on `SettingsStore`, the practical workflow is:
- build or run `meAppTests`
- filter failures to the `SettingsStore` files in Xcode or CI logs
- keep changes inside the existing suite file that matches the behavior you touched

## How to add a new SettingsStore test
1. Decide which user flow or state branch changed.
2. Put the new test in the matching suite file under `TestSuits/`.
3. Reuse `SettingsStoreTestFixtures.makeSUT(...)` unless the test has a strong reason not to.
4. Override only the dependencies needed for that scenario.
5. Prefer asserting observable behavior:
   state changes, alerts, toasts, loaders, navigation effects, or service calls.
6. Use `SettingsStoreTestFixtures.waitUntil(...)` for async publisher-driven updates instead of hard sleeps.

## How we extend the suite
When new `SettingsStore` behavior is added, we extend the suite by following the same pattern:

- add coverage to the existing per-topic suite if the behavior clearly belongs there
- create or extend a shared fixture only when multiple tests need the same setup
- add fields or counters to shared mocks only when a real test assertion needs them
- keep DI registration in the fixture layer, not copied into every test

As a rule:
- if the change affects general derived state or screen flags, extend `Core State`
- if it affects profile/body/notification preferences, extend `Profile And Preferences`
- if it affects password flows, extend `Password`
- if it affects goal editing/saving, extend `Goals`
- if it affects weightless behavior, extend `Weightless`
- if it affects export/logout/delete/integration actions, extend `Account Actions`

## Common pitfalls
- Do not create a second ad-hoc `SettingsStore` setup path unless the existing fixture cannot support the case.
- Do not run these tests in parallel unless the DI/container strategy is redesigned.
- Do not replace `TestNotificationHelperService` with a protocol-only mock for `SettingsStore` tests. Some code paths still require the concrete notification service type.
- If a new dependency is introduced into `SettingsStore`, update the shared fixture before adding tests, otherwise failures will look unrelated and noisy.

## Summary
This suite is intentionally split by user-facing behavior, not by method count. The goal is to make it obvious where a new test belongs, keep setup shared, and make regressions easy to diagnose when `SettingsStore` changes.
