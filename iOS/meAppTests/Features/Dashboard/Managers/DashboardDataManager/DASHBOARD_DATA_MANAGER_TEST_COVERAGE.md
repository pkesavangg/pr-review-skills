# DashboardDataManager Test Coverage

## Overview

Unit tests for `DashboardDataManager`, covering data synchronization, Combine pipeline bindings, cache management, entry retrieval, analytics, and edge cases.

Current measured coverage (as of 2026-03-09): **99.6%**

**File:** `DashboardDataManagerTests.swift`
**Target Class:** `DashboardDataManager` (`iOS/meApp/Features/Dashboard/Managers/DashboardDataManager.swift`)

---

## Test Categories

### 1. Initialization (2 tests)
| Test | Description |
|------|-------------|
| `initDefaultState` | Verifies default state has empty summaries, caches, and zero weight |
| `initCustomState` | Verifies custom `DataState` is preserved on init |

### 2. Data Loading (2 tests)
| Test | Description |
|------|-------------|
| `loadInitialDataSuccess` | `loadInitialData()` completes without error |
| `initializeDataManagerDelegates` | `initializeDataManager()` delegates to `loadInitialData()` |

### 3. Data Synchronization via Combine Pipeline (10 tests)
| Test | Description |
|------|-------------|
| `dailySummariesBindingUpdatesState` | Setting `EntryService.dailySummaries` updates state, cache dictionary, and date bounds |
| `dailySummariesSortsUnsorted` | Unsorted daily summaries are sorted ascending by date in cache |
| `dailySummariesDateBounds` | Min/max date bounds are correctly cached from daily summaries |
| `monthlySummariesBindingUpdatesState` | Setting `EntryService.monthlySummaries` updates state and cache |
| `monthlySummariesSortsUnsorted` | Unsorted monthly summaries are sorted ascending by date |
| `monthlySummariesDateBounds` | Min/max date bounds are correctly cached from monthly summaries |
| `dailySummariesEmptyArrayClearsCaches` | Empty daily summaries clears all daily caches and bounds |
| `monthlySummariesEmptyArrayClearsCaches` | Empty monthly summaries clears all monthly caches and bounds |
| `dailySummariesSingleItem` | Single summary sets min and max date to the same value |
| `dailySummariesBuildsCacheDictionary` | Daily cache dictionary is keyed by period string |

### 4. getContinuousOperations (5 tests)
| Test | Description |
|------|-------------|
| `getContinuousOperationsWeek` | `.week` returns daily sorted summaries |
| `getContinuousOperationsMonth` | `.month` returns daily sorted summaries |
| `getContinuousOperationsYear` | `.year` returns monthly sorted summaries |
| `getContinuousOperationsTotal` | `.total` returns monthly sorted summaries |
| `getContinuousOperationsEmpty` | All periods return empty when no data loaded |

### 5. getDateBounds (5 tests)
| Test | Description |
|------|-------------|
| `getDateBoundsWeek` | `.week` returns daily min/max dates |
| `getDateBoundsMonth` | `.month` returns daily min/max dates |
| `getDateBoundsYear` | `.year` returns monthly min/max dates |
| `getDateBoundsTotal` | `.total` returns monthly min/max dates |
| `getDateBoundsNil` | Returns nil for all periods when no data |

### 6. getLatestEntry (5 tests)
| Test | Description |
|------|-------------|
| `getLatestEntryWithWeight` | Returns entry and updates `latestWeightStored` |
| `getLatestEntryWithoutWeight` | Returns entry without updating weight when nil |
| `getLatestEntryNil` | Returns nil when no entries exist |
| `getLatestEntryError` | Throws `DashboardError.dataLoadingFailed` on service error |
| `getLatestEntryMultiple` | Returns the latest entry by timestamp from multiple entries |

### 7. loadLatestEntryData (4 tests)
| Test | Description |
|------|-------------|
| `loadLatestEntryDataWithWeight` | Returns `(entry, weight)` tuple and updates state |
| `loadLatestEntryDataWithoutWeight` | Returns `(entry, nil)` when no weight |
| `loadLatestEntryDataNil` | Returns `(nil, nil)` when no entries |
| `loadLatestEntryDataError` | Rethrows when entry service fails |

### 8. getLatestEntrySync (2 tests)
| Test | Description |
|------|-------------|
| `getLatestEntrySyncReturnsNil` | Always returns nil |
| `getLatestEntrySyncReturnsNilAfterLoad` | Returns nil even after loading entries |

### 9. clearCache (3 tests)
| Test | Description |
|------|-------------|
| `clearCacheResetsState` | Clears all state properties to empty/zero |
| `clearCacheClearsSortedCaches` | Clears sorted caches and date bounds |
| `clearCacheIdempotent` | Clearing already-empty cache succeeds |

### 10. validateCacheConsistency (4 tests)
| Test | Description |
|------|-------------|
| `validateCacheConsistencyPasses` | Passes when state counts match EntryService counts |
| `validateCacheConsistencyBothEmpty` | Passes when both are empty |
| `validateCacheConsistencyDailyMismatch` | Throws `cacheUpdateFailed` with "Daily" message |
| `validateCacheConsistencyMonthlyMismatch` | Throws `cacheUpdateFailed` with "Monthly" message |
| `validateCacheConsistencyHandlesNils` | Nil values in state are excluded from count via compactMap |

### 11. getDataAnalytics (5 tests)
| Test | Description |
|------|-------------|
| `getDataAnalyticsWithData` | Returns correct entry counts, cache size, and timestamp |
| `getDataAnalyticsEmpty` | Returns zeros and nil range when no data |
| `getDataAnalyticsDateRange` | Calculates date range from all cache values |
| `getDataAnalyticsCacheSize` | Cache size is 200 bytes per entry estimate |
| `getDataAnalyticsCompleteness` | Data completeness = actual days / total days |
| `getDataAnalyticsCompletenessOneDay` | Single day → 0% completeness (totalDays = 0) |

### 12. Edge Cases & Integration (4 tests)
| Test | Description |
|------|-------------|
| `multipleRapidUpdates` | Final state reflects the last Combine update |
| `dailyAndMonthlyIndependent` | Daily and monthly pipelines don't interfere |
| `largeDataset` | Handles 365 summaries without issue |
| `duplicatePeriods` | Duplicate period keys produce 1 cache entry |

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **52** |
| **Measured Coverage** | **9.6%** |

### Code Paths Covered
init, loadInitialData, initializeDataManager, Combine bindings (daily + monthly), getContinuousOperations (all periods), getDateBounds (all periods), getLatestEntry (success/error/nil/weight), loadLatestEntryData (all paths), getLatestEntrySync, clearCache, validateCacheConsistency (pass/daily mismatch/monthly mismatch/nil handling), getDataAnalytics (all private helpers), edge cases.

### Methods Not Directly Tested (Tested Indirectly)
- `updateStateFromDailySummaries` (private) — tested via Combine pipeline tests
- `updateStateFromMonthlySummaries` (private) — tested via Combine pipeline tests
- `calculateDateRange` (private) — tested via `getDataAnalytics` tests
- `calculateDataCompleteness` (private) — tested via `getDataAnalytics` tests
- `calculateCacheSize` (private) — tested via `getDataAnalytics` tests
