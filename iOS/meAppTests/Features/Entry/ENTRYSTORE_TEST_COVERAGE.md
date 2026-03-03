# EntryStore Test Coverage Guide

## Purpose
This document explains how `EntryStore` is tested, what behavior is covered, and the coverage target for safe CI validation.

## Files Involved
- Store under test:
  - `meApp/Features/Entry/Stores/EntryStore.swift`
- Main test suite:
  - `meAppTests/Features/Entry/EntryStoreTests.swift`
- Entry fixtures:
  - `meAppTests/Features/Entry/Fixtures/EntryStoreTestFixtures.swift`
- Entry mocks:
  - `meAppTests/Features/Entry/Mocks/MockEntryStoreDependencies.swift`

## Coverage Strategy
`EntryStore` tests use protocol-based dependency injection and validate:
1. Entry loading/saving state transitions (`isSaving`, loader/toast behavior).
2. Form validation and date/time guardrails (required fields, future date/time clamp).
3. Conversion and payload mapping (display-to-stored weight, metrics mapping).
4. Regression guards (duplicate save prevention, observer-driven unit updates, auto-time sync behavior).
5. Success and failure paths (persist success reset, persist failure toast and state retention).

## Key Flows Covered
- Form validation and error surfaces (`getError`, invalid form save).
- Save entry success path and full payload assertions.
- Save entry error path and no-active-account guard.
- Duplicate save prevention while first request is in flight.
- Time clamp logic for today and non-today dates.
- Weight unit refresh and validator changes.
- App Sync metric population and BMI auto-calc toggle.
- Exit/discard confirmation actions.
- Auto time sync:
  - updates while on today and picker closed
  - does not update when picker is open
  - respects user-adjusted time
- Notification observer path for `.accountWeightUnitChanged`.

## Run and Check Coverage
From repo root (`iOS`):

```bash
xcodebuild test \
  -project meApp.xcodeproj \
  -scheme 'meAppTests 1' \
  -destination 'id=<YOUR_DEVICE_ID>' \
  -only-testing:meAppTests/EntryStoreTests
```

```bash
xcrun xccov view --report <PATH_TO_XCRESULT> | rg 'EntryStore.swift'
```

## Team Expectation
- Keep `EntryStore.swift` coverage at **90%+**.
- Any change to validation, save flow, date/time behavior, or conversion logic must include test updates.

## Current Coverage
- `EntryStore.swift`: **97.70%** (468/479)
- Source: `Test-meAppTests 1-2026.03.03_12-39-20-+0530.xcresult` (March 3, 2026)
