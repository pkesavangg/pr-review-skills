# SQLiteMigrationService Test Coverage Guide

## Purpose
Unit tests for `SQLiteMigrationService`, which migrates entry data from the Ionic app's SQLite database (`opStack` / `opStack_metric` tables) to SwiftData. Tests use real temporary SQLite databases with a mock `EntryRepository` to verify the full migration pipeline.

## Files Involved
- **Source:** `meApp/Data/Services/SQLiteMigrationService.swift`
- **Tests:** `meAppTests/Features/Migration/SQLiteMigrationServiceTests.swift`
- **Error Types:** `meApp/Domain/Models/API/Auth/MigrationError.swift`

## Test Strategy
- Inject a temporary database path via `databasePathOverride` and a `MockEntryRepository` via `entryRepository`
- Create real SQLite databases in `NSTemporaryDirectory()` with controlled test data
- `SQLiteTestHelper` provides factory methods for creating databases with opStack/opStack_metric tables
- `MockSQLiteMigrationEntryRepository` supports selective save failures for partial migration tests
- `TestDependencyContainer.reset()` ensures `@Injector` logger resolves to mock

## Refactoring for Testability
Minimal changes to the service (backward-compatible):
- Changed `@Injector private var logger: LoggerService` → `LoggerServiceProtocol` (matches project DI convention)
- Added `init(databasePathOverride:entryRepository:)` with nil defaults
- `migrateAllUsersEntries()` uses injected repository when available, falls back to `EntryRepository()`

## Flows Covered

### Migration Detection (2 tests)
| Test | Scenario |
|------|----------|
| isMigrationNeeded true | Database file exists |
| isMigrationNeeded false | No database file |

### First-Time Migration (9 tests)
| Test | Scenario |
|------|----------|
| Single user migration | Correct count returned |
| Multi-user migration | Per-user counts tracked |
| Scale data preservation | Weight, bodyFat, muscleMass, water, bmi, source |
| Metric data preservation | All 8 metric fields from opStack_metric |
| Weight zero handling | No BathScaleEntry created |
| No metrics available | No BathScaleMetric created |
| NULL optional fields | Graceful nil handling |
| Entry defaults | Unsynced, scale device type, nil serverTimestamp |
| NULL operationType | Defaults to "create" |

### Already-Migrated / Empty State (2 tests)
| Test | Scenario |
|------|----------|
| No opStack tables | Returns empty dict, no saves |
| Empty opStack table | Returns empty dict, no saves |

### Error Handling (3 tests)
| Test | Scenario |
|------|----------|
| No database file | Throws databaseConnectionFailed |
| Partial save failure | Continues with remaining entries |
| All saves fail | Returns empty dict (no crash) |

### Cleanup (2 tests)
| Test | Scenario |
|------|----------|
| Cleanup removes file | File deleted from disk |
| Cleanup no-op | No error when file doesn't exist |

### Full Flow & Idempotency (2 tests)
| Test | Scenario |
|------|----------|
| Post-cleanup detection | isMigrationNeeded false after cleanup |
| End-to-end flow | Check → migrate → cleanup |

### Partial Metric Data (2 tests)
| Test | Scenario |
|------|----------|
| Only bmr exists | Metric created with bmr only |
| Only metabolicAge exists | Metric created with metabolicAge only |

## Known Gaps
- Corrupted SQLite database (invalid file content) — sqlite3_open succeeds on most files; corruption is hard to simulate reliably
- Concurrent migration calls — not applicable (service is stateful with `db` pointer)

## Run Tests
```bash
xcodebuild test \
  -project iOS/meApp.xcodeproj \
  -scheme meAppTests \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -only-testing:meAppTests/SQLiteMigrationServiceTests
```

## Team Expectation
- Keep coverage at **85%+**

## Current Coverage
- `SQLiteMigrationService.swift`: **87.5%** (March 6, 2026)
