# DashboardError Test Coverage

## Overview

Unit tests for `DashboardError`, covering localized messages, recovery and failure metadata, error equality, logger mapping, and `Result` error logging behavior used by the dashboard data layer.

Current measured coverage (as of 2026-03-10): **98.7%**

**File:** `DashboardErrorTests.swift`
**Target File:** `DashboardError.swift` (`iOS/meApp/Features/Dashboard/Models/DashboardError.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **12** |
| **Measured Coverage** | **98.7%** |

### Covered Areas

- `errorDescription` formatting for data-loading, API, metric, scale, graph, and goal errors
- `recoverySuggestion` branch coverage for case-specific and default fallbacks
- `failureReason` branch coverage for targeted and nil-returning cases
- `Equatable` behavior for same-case equality, cross-case inequality, string-backed comparisons, and wrapped `Error` comparisons by localized description
- `log(with:tag:)` severity mapping for info and error cases, including data-layer message mapping
- `Result<_, DashboardError>.logError(with:tag:)` failure logging and success no-op behavior

### Methods Not Directly Tested

- None of the externally used branches in `DashboardError.swift` are intentionally left undocumented by tests; remaining uncovered lines are non-critical formatting or switch fallthrough structure.

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/DashboardErrorTests
xcrun xccov view --report --json --archive <result-bundle>
```
