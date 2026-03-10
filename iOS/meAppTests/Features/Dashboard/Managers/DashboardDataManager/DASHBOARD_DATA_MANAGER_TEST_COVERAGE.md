# DashboardDataManager Test Coverage

## Overview

Unit tests for `DashboardDataManager`, covering EntryService synchronization, sorted dashboard caches, date-bound reuse, latest-entry retrieval, cache validation, analytics, and update-ordering behavior.

Current measured coverage (as of 2026-03-10): **99.6%**

**File:** `DashboardDataManagerTests.swift`
**Target Class:** `DashboardDataManager` (`iOS/meApp/Features/Dashboard/Managers/DashboardDataManager.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **57** |
| **Measured Coverage** | **99.6%** |

### Covered Areas

- initialization with default and custom `DataState`
- initial loading and manager initialization entry points
- Combine bindings from `EntryService.dailySummaries` and `EntryService.monthlySummaries`
- sorting, cache-dictionary rebuilding, and min/max date-bound caching for daily and monthly data
- publication ordering so downstream readers see refreshed cached operations during state updates
- `getContinuousOperations(for:)` across all `TimePeriod` cases
- `getDateBounds(for:)` for populated and empty daily/monthly datasets
- `getLatestEntry()` and `loadLatestEntryData()` success, nil, multi-entry, weightless, and mapped-error paths
- `clearCache()` state resets, cache invalidation, and idempotence
- `validateCacheConsistency()` success, nil-filtering, and daily/monthly mismatch failures
- `getDataAnalytics()` entry counts, date range, cache-size estimation, completeness, and empty-state behavior
- rapid update handling, daily/monthly isolation, and large-dataset edge cases

### Methods Not Directly Tested (Tested Indirectly)

- `updateStateFromDailySummaries` (private) via Combine binding tests
- `updateStateFromMonthlySummaries` (private) via Combine binding tests
- `calculateDateRange` (private) via analytics tests
- `calculateDataCompleteness` (private) via analytics tests
- `calculateCacheSize` (private) via analytics tests

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/DashboardDataManagerTests
xcrun xccov view --report --json --archive <result-bundle>
```
