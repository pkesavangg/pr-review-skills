# ContentViewModel Test Coverage Guide

## Purpose
This document explains how `ContentViewModel` is tested, what startup/login regressions are covered, and the current measured coverage.

## Files Involved
- View model under test:
  - `meApp/Features/Common/ViewModels/ContentViewModel.swift`
- Main test suite:
  - `meAppTests/Features/Common/ViewModels/ContentViewModelTests.swift`
- Fixtures:
  - `meAppTests/Features/Common/ViewModels/Fixtures/ContentViewModelTestFixtures.swift`
- Mocks:
  - `meAppTests/Features/Common/ViewModels/Mocks/MockContentViewModelDependencies.swift`
  - `meAppTests/Support/Mocks/Services/MockAccountService.swift`
  - `meAppTests/Support/Mocks/Services/MockLoggerService.swift`

## Test Strategy
`ContentViewModel` tests use protocol-based injection:
1. Inject `AccountServiceProtocol`, `EntryServiceProtocol`, `FeedServiceProtocol`, `ScaleServiceProtocol`, `BluetoothServiceProtocol`, `AccountFlagServiceProtocol`, and `LoggerServiceProtocol` through the test DI container.
2. Drive initialization behavior through published account state changes and direct `performAppInitialization()` calls.
3. Validate both final UI state (`contentViewState`) and startup side effects (refresh/load/sync/service call counts).
4. Cover success and failure branches for startup/account refresh plus publisher-driven re-initialization behavior.

## Covered Flows
- Initialization transition handling:
  - starts in `.initializing`
  - resolves to `.landing` for logged-out
  - resolves to `.dashboard` for logged-in.
- Logged-out and deferred-landing paths:
  - immediate logged-out landing
  - deferred unauthenticated landing wait flow.
- Logged-in startup loading triggers:
  - account refresh attempt
  - entry migration/sync/load
  - feed fetch + modal check
  - scale sync
  - Bluetooth startup.
- Final view state mapping via `updateViewState(isLoggedIn:)`.
- Account publisher behavior:
  - re-initialization when state changes after initialization
  - recursion guard while already initializing
  - duplicate suppression for same account and same `lastActiveTime`
  - re-initialization when `lastActiveTime` changes.
- Entry-saved trigger behavior for post-entry account-flag checks.
- Error paths:
  - account published-state refresh failure
  - account refresh failure fallback
  - startup entry fetch/sync failure fallback to empty entries.

## Run Tests
From repo root (`iOS`):

```bash
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme meAppTests \
  -destination 'platform=iOS,id=<DEVICE_ID>' \
  -only-testing:meAppTests/ContentViewModelTests \
  -enableCodeCoverage YES
```

## Check Coverage
```bash
xcrun xccov view --report <PATH_TO_XCRESULT> | rg 'ContentViewModel.swift'
```

## Team Expectation
- Keep `ContentViewModel.swift` coverage at **90%+**.
- Any app startup/login initialization change must include regression tests for initialization transitions, startup loading side effects, publisher-driven re-init behavior, and error handling branches.

## Current Coverage
- `ContentViewModel.swift`: **97.33%** (219/225)
- Source: `/tmp/ContentViewModelTests-device-10.xcresult` (March 3, 2026)
