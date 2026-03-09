# AccountFlagService Test Coverage Guide

## Purpose
This document explains how `AccountFlagService` is tested, what flag-processing flows are covered, and how to extend tests safely.

## Files Involved
- Service under test:
  - `meApp/Data/Services/AccountFlagService.swift`
- Main test suite:
  - `meAppTests/Features/Account/AccountFlagServiceTests.swift`
- Account-flag fixtures/mocks:
  - `meAppTests/Features/Account/Fixtures/AccountFlagTestFixtures.swift`
  - `meAppTests/Features/Account/Mocks/MockAccountFlagRepositoryAPI.swift`
  - `meAppTests/Features/Account/Mocks/MockAppReviewHandler.swift`
- Shared test support:
  - `meAppTests/Support/Mocks/Services/MockLoggerService.swift`

## Coverage Strategy
`AccountFlagService` is branch-heavy around trigger/type handling. Coverage is improved by testing each method with:
1. Success path
2. Guard and mismatch behavior
3. Trigger side effects
4. Error propagation and invalid-data behavior

## Flows Covered

### 1) Flag Retrieval
- `getAccountFlag`: empty response -> `nil`
- `getAccountFlag`: login trigger preferred over entry trigger
- `getAccountFlag`: first flag chosen when login trigger is absent
- `getAccountFlag`: API failure throws and clears cached state

### 2) Flag Processing
- `checkAccountFlag`: no current flag -> `false`
- `checkAccountFlag`: trigger mismatch -> `false`
- `checkAccountFlag`: unknown flag type -> `false`
- `checkAccountFlag` for `app-rate-ask`:
  - success (delete + app review trigger)
  - delete returns `false` branch
  - delete throws branch
- `checkAccountFlag` for `scale-review-ask`:
  - success emits `ScaleReviewEvent` with parsed SKU
  - missing SKU emits event with empty SKU

### 3) Trigger Conditions
- App review path triggers only for `app-rate-ask` with matching trigger
- Scale review event path triggers only for `scale-review-ask` with matching trigger

### 4) Failure Handling
- Invalid/unknown flag types handled gracefully (no crash, returns `false`)
- Delete API errors propagate correctly
- Cached-flag state is cleared when active flag is deleted

## How `makeSUT` Works
`makeSUT` in `AccountFlagServiceTests` builds `AccountFlagService` with protocol-backed mocks:
- mock account-flag API repository
- mock app-review handler
- mock logger

This keeps tests deterministic and independent from DI/global singleton state.

## How To Add New AccountFlagService Tests
1. Build DTO fixtures with `AccountFlagTestFixtures`.
2. Configure mock API results in `MockAccountFlagRepositoryAPI`.
3. Call `getAccountFlag`, `checkAccountFlag`, or `deleteFlag`.
4. Assert:
   - return value and thrown errors
   - repository call counts/arguments
   - trigger side effects (app review call or scale review event)

## Run and Check Coverage
Run from repo root.

**Simulator:**
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -configuration Production \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:meAppTests/AccountFlagServiceTests
```

Coverage in Xcode:
1. Test Report (`Cmd+9`)
2. Open latest run
3. Coverage tab
4. Inspect `AccountFlagService.swift`

Shareable coverage export (Markdown + CSV + HTML):
```bash
CONFIGURATION=Dev ./iOS/scripts/run_tests_with_coverage.sh
```

Full reporting details: `iOS/docs/COVERAGE_REPORTING.md`.

## Team Expectation
- Keep `AccountFlagService.swift` coverage at least **90%**
- Every bug fix in account-flag handling should add a regression test

## Current Coverage
- `AccountFlagService.swift`: **~95%** from latest `AccountFlagServiceTests` validation snapshot (March 5, 2026).
- Current coverage (as of 2026-03-05): **~95%**
- Estimated line coverage for `AccountFlagService.swift`: **~95%**
