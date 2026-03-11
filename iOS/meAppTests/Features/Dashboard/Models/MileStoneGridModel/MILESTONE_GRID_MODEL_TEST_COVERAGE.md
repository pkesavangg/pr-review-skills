# MileStoneGridModel Test Coverage

## Overview

Unit tests for `MileStoneGridModel`, covering construction order, item moves, goal-card and streak reordering rules, invalid-index safety, and span-based grid reflow behavior.

Measured coverage: Pending verification from `xccov` because the focused Xcode validation run did not complete cleanly in this session.

**File:** `MileStoneGridModelTests.swift`
**Target File:** `MileStoneGridModel.swift` (`iOS/meApp/Features/Dashboard/Models/MileStoneGridModel.swift`)

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **10** |
| **Measured Coverage** | **Pending** |

### Covered Areas

- model construction preserving the provided milestone order
- `moveWidget(from:to:)` normal goal-card movement
- immediate-neighbor streak swaps around a goal card
- normal move fallback when no goal card exists
- invalid-index and same-index no-op behavior
- fallback behavior when the immediate swap target is not a streak
- `reorderGrid(spanCount:hasRemovedStreaks:)` when the goal card already starts a row
- goal-card movement to the next row when it would otherwise split a row
- skip behavior when streak removal should suppress automatic reflow
- skip behavior when streak count is not divisible by the row span

### Methods Not Directly Tested

- The private `getItemSpan(for:spanCount:)` helper is covered indirectly through `reorderGrid(...)` outcomes rather than by direct invocation.

### Coverage Command

```bash
xcodebuild test -enableCodeCoverage YES -project meApp/iOS/meApp.xcodeproj -scheme meAppTests -destination 'id=<device-id>' -only-testing:meAppTests/MileStoneGridModelTests
xcrun xccov view --report --archive <result-bundle>
```
