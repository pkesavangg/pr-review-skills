# DashboardMetricsCalculator Test Coverage

## Overview

Unit tests for `DashboardMetricsCalculator`, covering average weight calculations, display weight resolution, entry creation from all code paths, and edge cases.

Current measured coverage (as of 2026-03-10): **98.57% (484/491)** from the focused dashboard range/formatter/calculator coverage run.

**File:** `DashboardMetricsCalculatorTests.swift`
**Target Class:** `DashboardMetricsCalculator` (`iOS/meApp/Features/Dashboard/Managers/DashboardMetricsCalculator.swift`)

---

## Test Categories

### 1. getCurrentAverageWeight (14 tests)
| Test | Description |
|------|-------------|
| `averageWeightEmptyOperations` | Empty operations returns 0 |
| `averageWeightSingleOperation` | Single operation returns converted weight |
| `averageWeightMultipleOperations` | Multiple operations returns average rounded to 1 decimal |
| `averageWeightRoundsCorrectly` | Verifies rounding to 1 decimal place |
| `averageWeightRoundingBoundary` | 0.05 boundary rounds away from zero |
| `averageWeightAllSame` | All same weights returns exact value |
| `averageWeightWeightlessModeWithAnchor` | Subtracts anchor from each weight before averaging |
| `averageWeightWeightlessModeNoAnchor` | Returns 0 when anchor is nil |
| `averageWeightWeightlessModeSingle` | Single operation weightless mode |
| `averageWeightWeightlessModeNegative` | Negative weightless difference |
| `averageWeightLargeValues` | Handles large weight values |
| `averageWeightSmallValues` | Handles small weight values |
| `averageWeightCustomConversion` | Works with custom identity conversion |
| `averageWeightManyOperations` | Stress test with 100 operations |

### 2. calculateDisplayWeight (14 tests)
| Test | Description |
|------|-------------|
| `displayWeightSelectedPointNormal` | Selected point returns converted weight |
| `displayWeightSelectedPointWeightless` | Selected point in weightless mode returns difference |
| `displayWeightSelectedPointWeightlessNoAnchor` | Returns nil when anchor missing |
| `displayWeightSelectedDate` | Uses interpolatedWeight callback |
| `displayWeightSelectedDateNilInterpolation` | Nil interpolation returns nil |
| `displayWeightNoSelectionEmptyOpsForLabelNotTotal` | Uses interpolatedAverage when not total period |
| `displayWeightNoSelectionEmptyOpsForLabelTotal` | Falls through for total period |
| `displayWeightNoSelectionNoOperations` | Returns nil with no data |
| `displayWeightWeightlessModeCallback` | Uses weightlessDisplay callback |
| `displayWeightNormalModeAverage` | Averages visible operations |
| `displayWeightNormalModeRounding` | Rounds average to 1 decimal |
| `displayWeightPointPriorityOverDate` | Point selection has priority over date |
| `displayWeightPointNegativeWeightless` | Negative weightless difference |
| `displayWeightNormalModeSingleOp` | Single operation returns exact value |
| `displayWeightVerySmallWeight` | Very small weight handled correctly |

### 3. createEntryForMetricInfo (24 tests)
| Test | Description |
|------|-------------|
| `createEntryBaseProperties` | Entry has correct accountId, operationType, deviceType |
| `createEntrySelectedPoint` | Populates scaleEntry with point values |
| `createEntrySelectedPointZeroWeight` | Zero weight produces nil |
| `createEntrySelectedPointZeroBodyFat` | Zero bodyFat produces nil |
| `createEntrySelectedPointNilMetrics` | Nil metrics produce nil in entry |
| `createEntrySelectedPointBmrPlaceholder` | BMR with placeholder tile produces nil |
| `createEntrySelectedPointVisceralFatZeroTile` | Visceral fat with "0" tile produces nil |
| `createEntrySelectedPointAllMetricFields` | All metric fields populated correctly |
| `createEntrySelectedPointTimestamp` | Entry timestamp matches point's date |
| `createEntrySelectedDate` | Interpolated entry with correct weight and nil body metrics |
| `createEntrySelectedDateWeightless` | Weightless mode adds anchor back for stored weight |
| `createEntrySelectedDateNilInterpolation` | Nil interpolation produces nil weight |
| `createEntryNoSelectionEmptyVisibleOps` | Uses interpolated average path |
| `createEntryNoSelectionEmptyEverything` | Empty everything produces nil weight |
| `createEntryNoSelectionNilInterpolatedAverage` | Nil average produces nil weight |
| `createEntryVisibleOpsAverages` | Averages all body metrics across visible ops |
| `createEntryVisibleOpsScaleEntryMetrics` | Averages scale entry metric fields |
| `createEntryVisibleOpsZeroAverages` | Zero averages produce nil |
| `createEntryVisibleOpsNilMetrics` | Nil metrics produce nil averages |
| `createEntryVisibleOpsSingle` | Single operation uses its exact values |
| `createEntryPointPriorityOverDate` | Point takes priority over date |
| `createEntryDatePriorityOverVisibleOps` | Date takes priority over visible ops |
| `createEntryMetricUnitKg` | Kg unit uses metric conversion |
| `createEntryInterpolatedAverageKg` | Interpolated average with kg unit |

### 4. Edge Cases (5 tests)
| Test | Description |
|------|-------------|
| `averageWeightWeightlessZeroAnchor` | Zero anchor returns raw converted weight |
| `createEntrySelectedPointBmrZeroPointZeroTile` | BMR "0.0" tile produces nil |
| `createEntryEmptyVisibleOpsWeightless` | Empty visible ops with weightless mode |
| `createEntryVisibleOpsLatestWeightZero` | Zero latestWeightStored with ops present |
| `createEntryVisibleOpsMixedMetrics` | Mixed nil and valid metrics averaged correctly |

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **59** |
| **Measured Coverage** | **98.57% (484/491)** |
| **Target Minimum** | **90%** |

### Code Paths Covered
getCurrentAverageWeight (empty/single/multi/rounding/weightless/anchor/no-anchor/negative/large/small), calculateDisplayWeight (all 6 code paths with variations), createEntryForMetricInfo (all 4 code paths with metric edge cases), unit conversions, priority ordering.

### Methods Not Directly Tested (Tested Indirectly)
- `createEntryFromSelectedPoint` (private) — tested via `createEntryForMetricInfo` tests
- `createEntryFromInterpolatedDate` (private) — tested via `createEntryForMetricInfo` tests
- `createEntryFromInterpolatedAverage` (private) — tested via `createEntryForMetricInfo` tests
- `createEntryFromVisibleOperations` (private) — tested via `createEntryForMetricInfo` tests
- `intOrNil` / `scaled10OrNil` (inner functions) — tested via entry creation tests
