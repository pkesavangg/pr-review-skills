# AccountMigrationService Test Coverage Guide

## Purpose
This document explains how `AccountMigrationService` is tested, what migration flows are covered, and how to extend tests safely.

## Files Involved
- Service under test:
  - `meApp/Data/Services/AccountMigrationService.swift`
- Main test suite:
  - `meAppTests/Features/Migration/AccountMigrationServiceTests.swift`
- Migration-specific mocks:
  - `meAppTests/Features/Migration/Mocks/MockMigrationKvStorageService.swift`
  - `meAppTests/Features/Migration/Mocks/MockMigrationAccountRepository.swift`
  - `meAppTests/Features/Migration/Mocks/MockMigrationScaleMigrationService.swift`
  - `meAppTests/Features/Migration/Mocks/MockMigrationIntegrationStore.swift`
- Shared fixtures/support:
  - `meAppTests/Features/Scale/Fixtures/ScaleTestFixtures.swift`
  - `meAppTests/Support/DI/TestDependencyContainer.swift`

## Coverage Strategy
`AccountMigrationService` is migration-heavy and branch-heavy. Coverage is improved by testing each migration path with:
1. Happy path conversion/migration
2. Missing-data and malformed-data guards
3. Error propagation (or safe non-throw behavior where intended)
4. Cleanup/idempotency behavior

## Flows Covered

### 1) Migration Gating and Account Conversion
- `isMigrationNeeded`: flag absent/present behavior
- `migrateAccountData`: no data, invalid JSON, valid JSON, save failure, token migration
- Account field mapping validation (`isLoggedIn`, `isActiveAccount`, sync/expiry flags)

### 2) Orchestrated Full Migration
- `migrateAccountAndScaleData`: overall completion flow
- Scale migration aggregation with/without account migration success
- Migration-completed flag write
- Cleanup sequence coverage

### 3) Per-Domain Migrations
- Goal alert migration (`true`/`false`/absent)
- Goal card status migration (`true`/`false`/absent)
- Appearance migration mapping (`light`, `dark`, `system`, unknown)
- Notification alert migration (account-scoped and global)
- Feed migration:
  - JSON string payload
  - dictionary payload
  - encoded `Data` payload
  - last-triggered timestamp copy
- HealthKit integration migration:
  - integrated/deintegrated variants
  - assigned-account behavior
  - store error safety
- Scale migration:
  - not-needed path
  - success with cleanup
  - throw propagation

### 4) Cleanup and Multi-Account Discovery
- Account/offline cleanup
- Feed/goal/appearance/notification/scale cleanup helpers
- HealthKit cleanup guard conditions
- All-accounts helpers:
  - `migrateAllGoalAlertData`
  - `migrateAllNotificationAlertData`
  - `migrateAllFeedData`
  - `migrateAllScaleData`
  - `migrateAllGoalCardStatusData`
  - `migrateAllIntegrationData`
  - `cleanupAllScaleData`

## How `makeSUT` Works
`makeSUT` in `AccountMigrationServiceTests` builds the service with migration mocks and shared DI setup:
- Resets test DI container each test
- Registers base dependencies (logger, keychain, bluetooth) through `TestDependencyContainer`
- Injects protocol-backed migration mocks for kv/account/scale/integration dependencies
- Uses `@MainActor`-safe initialization defaults

## How To Add New AccountMigrationService Tests
1. Seed the migration key/value state in `MockMigrationKvStorageService`.
2. Configure mock result behavior for repositories/services.
3. Execute the migration method under test.
4. Assert:
   - migrated values
   - side effects (`setValue`, `setCodable`, cleanup)
   - call counts/error propagation as expected.

## Run and Check Coverage
Run from repo root:

```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -only-testing:meAppTests/AccountMigrationServiceTests
```

For a shareable report (Markdown/CSV/HTML):

```bash
CONFIGURATION=Dev ./iOS/scripts/run_tests_with_coverage.sh
```

Details: `iOS/docs/COVERAGE_REPORTING.md`

## Team Expectation
- Keep `AccountMigrationService.swift` coverage at **95%+**.
- Every migration bug fix should add a regression test case.

## Current Coverage
- `AccountMigrationService.swift`: **~97.3%** from latest `AccountMigrationServiceTests` validation snapshot (March 5, 2026).
