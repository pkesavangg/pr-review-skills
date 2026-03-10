# DashboardCacheManager Test Coverage

## Overview

Unit tests for `DashboardCacheManager`, covering continuous-operation caching, visible-operation reuse during scroll, chart-series cache validation and invalidation, label date-range caching, dependent-cache clearing, and UserDefaults passthrough behavior.

Current measured coverage (as of 2026-03-10): **100.0%**

**File:** `DashboardCacheManagerTests.swift`
**Target Class:** `DashboardCacheManager` (`iOS/meApp/Features/Dashboard/Managers/DashboardCacheManager.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **21** |
| **Measured Coverage** | **100.0%** |

### Covered Areas

- `getContinuousOperations(for:getOperations:)` first-load caching, same-period cache hits, empty-result retries, and period changes
- `invalidateContinuousOperationsCache()` clearing of continuous, visible, chart-series, and label-range dependent caches
- `getVisibleOperations(isScrolling:getVisibleOperations:)` refresh behavior when not scrolling and debounce-window reuse while scrolling
- chart-series caching across scroll-end reuse, metadata cache hits, metric changes, metric deselection, period changes, operation-count changes, and Y-axis domain changes
- `invalidateChartSeriesCache()` forcing recalculation
- `getLabelDateRangeOperations(period:scrollPosition:getOperations:)` cache hits, empty-cache misses, and invalidation on period or scroll-position changes
- `getBool(forKey:)` / `setBool(_:forKey:)` round-trip persistence
- `clearAllCaches()` resetting every cache layer

### Methods Not Directly Tested

- None. All public logic in `DashboardCacheManager.swift` is covered by `DashboardCacheManagerTests.swift`.

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/DashboardCacheManagerTests
xcrun xccov view --report --json --archive <result-bundle>
```
