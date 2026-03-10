# MetricInfoSheetWrapper Test Coverage

## Overview

Unit tests for `MetricInfoSheetWrapper`, covering DTO resolution from refetched and contextless entries, nil-metric preservation, async DTO loading fallback behavior, and the SwiftUI wrapper’s initial task / on-change reload logic.

Current measured coverage (as of 2026-03-10): **92.00%**

**File:** `MetricInfoSheetWrapperTests.swift`
**Target File:** `MetricInfoSheetWrapper.swift` (`iOS/meApp/Features/Dashboard/Utils/MetricInfoSheetWrapper.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **7** |
| **Measured Coverage** | **92.00%** |

### Covered Areas

- `MetricInfoSheetDTOResolver.resolveDTO(...)` for refetched entries, entries already attached to a `ModelContext`, contextless entries, and partial entries with nil metric fields
- `MetricInfoSheetWrapper.loadDTO(...)` success and refetch-failure fallback behavior
- `MetricInfoSheetWrapper` body/task behavior through hosted SwiftUI execution
- `.onChange` reload behavior for selected-period changes and metrics-array changes

### Methods Not Directly Tested

- a small amount of view composition structure remains uncovered, but the data-loading and reload behaviors used by the dashboard are covered

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/MetricInfoSheetWrapperTests
xcrun xccov view --report --json --archive <result-bundle>
```
