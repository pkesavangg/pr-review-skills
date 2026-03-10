# GraphDataPreparer Test Coverage

## Overview

Unit tests for `GraphDataPreparer`, covering chart-series construction, weightless-mode handling, metric normalization, Hermite interpolation, visible-window extraction, binary-search helpers, and edge-case behavior.

Current measured coverage (as of 2026-03-09): **97.1%**

**File:** `GraphDataPreparerTests.swift`
**Target Class:** `GraphDataPreparer` (`iOS/meApp/Features/Dashboard/Managers/Graph/GraphDataPreparer.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **42** |
| **Measured Coverage** | **97.1%** |

### Covered Areas

- chart series building for weight-only and mixed metric graphs
- weight series generation in normal and weightless modes
- normalized metric rendering with dynamic ranges, static fallback ranges, and explicit Y-axis domains
- Hermite/Fritsch-Carlson interpolation for single-point, two-point, clamped, and normalized-date paths
- metric extraction and static range mapping across all supported dashboard metrics
- weightless display and average-weight calculations across period modes
- interpolated visible-range averaging with sample filtering and invalid-input guards
- large-dataset windowing, visible-domain filtering, strict-domain filtering, and bracketing-point lookup
- binary search correctness for empty, exact-match, in-between, and not-found cases

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -project meApp/iOS/meApp.xcodeproj -scheme meApp -destination 'id=<device-id>' -only-testing:meAppTests/GraphDataPreparerTests
xcrun xccov view --report --json --archive <result-bundle>
```
