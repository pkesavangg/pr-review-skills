# DashboardState Test Coverage

## Overview

Unit tests for `DashboardState` and its nested state models, covering default initialization, derived state, drag/reset behavior, count clamping, cache storage, cross-state mutation safety, and edge cases.

Current measured coverage (as of 2026-03-09): **100.0%**

**File:** `DashboardStateTests.swift`
**Target File:** `DashboardState.swift` (`iOS/meApp/Features/Dashboard/Models/DashboardState.swift`)

---

## Test Categories

### 1. Default Initialization (1 test)
| Test | Description |
|------|-------------|
| `initDefaults` | Verifies `DashboardState` initializes all nested state models with the expected default values |

### 2. UIState (11 tests)
| Test | Description |
|------|-------------|
| `uiNoDrag` | `isAnyItemBeingDragged` is false when nothing is being dragged |
| `uiDraggingMetric` | Dragging a metric sets `isAnyItemBeingDragged` to true |
| `uiDraggingStreak` | Dragging a streak sets `isAnyItemBeingDragged` to true |
| `uiDraggingGoalCard` | Dragging the goal card sets `isAnyItemBeingDragged` to true |
| `uiMultipleDraggingStates` | Multiple active drag states still resolve to true |
| `uiResetDragState` | `resetDragState()` clears all drag-related fields |
| `uiResetDragStateIdempotent` | Resetting an already clean drag state is safe |
| `uiRemovedMetricsSetOperations` | `removedMetrics` behaves correctly as a set |
| `uiRemovedStreaksSetOperations` | `removedStreaks` behaves correctly as a set |
| `uiStreakGridOrderPreservesOrder` | `streakGridOrder` preserves insertion order |
| `uiGridLayoutIdUnique` | `gridLayoutId` is unique per new state instance |

### 3. MetricsState (9 tests)
| Test | Description |
|------|-------------|
| `metricsToShowPrefix` | Returns the active prefix of metrics |
| `metricsToShowAllWhenCountExceeds` | Returns all metrics when active count exceeds collection size |
| `metricsToShowEmptyWhenNoMetrics` | Returns empty when no metrics exist |
| `metricsToShowEmptyWhenCountZero` | Returns empty when active count is zero |
| `metricsToShowNegativeCountClampedToZero` | Negative active count is clamped to zero |
| `gridColumnsDashboard12` | `dashboard12` produces the expected grid column count |
| `gridColumnsDashboard4` | `dashboard4` produces the expected grid column count |
| `defaultDashboardType` | Default dashboard type is `dashboard12` |
| `metricsStateRemovedMetrics` | `removedMetrics` is independent from `UIState.removedMetrics` |

### 4. StreakState (7 tests)
| Test | Description |
|------|-------------|
| `streakItemsToShowPrefix` | Returns the active prefix of streak items |
| `streakItemsToShowAllWhenCountExceeds` | Returns all items when active count exceeds collection size |
| `streakItemsToShowEmptyWhenNoItems` | Returns empty when no streak items exist |
| `streakItemsToShowEmptyWhenCountZero` | Returns empty when active count is zero |
| `streakItemsToShowNegativeCountClampedToZero` | Negative active count is clamped to zero |
| `streakDefaultActiveCount` | Default active streak count is six |
| `streakRemovedStreaksSetOps` | `removedStreaks` behaves correctly as a set |

### 5. GraphState (12 tests)
| Test | Description |
|------|-------------|
| `graphDefaultPeriod` | Default selected period is `.week` |
| `graphClearSelection` | `clearSelection()` resets all selection-related fields |
| `graphClearSelectionIdempotent` | Clearing an already empty selection is safe |
| `graphUpdateScrollStateTrueClearsSelection` | Entering scroll mode clears selection |
| `graphUpdateScrollStateFalsePreservesSelection` | Leaving scroll mode preserves existing selection |
| `graphUpdateScrollStateToggle` | Scroll state toggles correctly across transitions |
| `graphCachedYAxisDomain` | Cached Y-axis domain is stored correctly |
| `graphCachedYAxisTicks` | Cached Y-axis ticks are stored correctly |
| `graphCachedXAxisValues` | Cached X-axis values are stored correctly |
| `graphDataChangeTrigger` | Data change trigger is mutable and tracked correctly |
| `graphHeightsIndependent` | Chart and annotation heights can be set independently |
| `graphAllPeriodAssignments` | All `TimePeriod` cases can be assigned |

### 6. GoalState (7 tests)
| Test | Description |
|------|-------------|
| `goalDefaults` | Verifies default goal state values |
| `goalSetLossType` | Goal type resolves correctly for a loss goal |
| `goalSetGainType` | Goal type resolves correctly for a gain goal |
| `goalZeroDeltaMaintainsType` | Zero delta preserves the existing goal type |
| `goalUnitKg` | Goal unit can be switched to kilograms |
| `goalProgressBoundaryValues` | Goal progress handles boundary values correctly |
| `goalNoneType` | Goal type can be set to `.none` |

### 7. DataState (14 tests)
| Test | Description |
|------|-------------|
| `dataHasAnyEntriesFalse` | `hasAnyEntries` is false when both summary collections are empty |
| `dataHasAnyEntriesDaily` | `hasAnyEntries` is true when daily summaries exist |
| `dataHasAnyEntriesMonthly` | `hasAnyEntries` is true when monthly summaries exist |
| `dataHasAnyEntriesBoth` | `hasAnyEntries` is true when both collections contain values |
| `dataHasAnyEntriesWithNil` | `hasAnyEntries` still reflects non-empty storage with nil elements |
| `dataContinuousOperationsSorted` | `continuousOperations` compact-maps and sorts daily summaries |
| `dataContinuousOperationsFiltersNil` | Nil entries are excluded from `continuousOperations` |
| `dataContinuousOperationsEmpty` | Empty daily summaries produce no continuous operations |
| `dataContinuousOperationsAllNil` | All-nil daily summaries produce no continuous operations |
| `dataDailyCacheStorage` | `dailyCache` stores and retrieves by key |
| `dataMonthlyCacheStorage` | `monthlyCache` stores and retrieves by key |
| `dataLatestWeightStored` | `latestWeightStored` persists its stored integer value |
| `dataContinuousOperationsSingle` | Single-entry continuous operations are preserved |
| `dataContinuousOperationsLargeDataset` | Large datasets remain sorted correctly |

### 8. Cross-State Mutation Safety (3 tests)
| Test | Description |
|------|-------------|
| `mutatingSubStateDoesNotAffectOthers` | Mutating one nested state does not affect unrelated state |
| `replacingSubStatePreservesOthers` | Replacing a nested state preserves unrelated nested state |
| `fullStateReplacement` | Replacing the full dashboard state resets back to defaults |

### 9. Edge Cases (9 tests)
| Test | Description |
|------|-------------|
| `uiSelectedMetricLabelEmptyString` | Empty metric label is stored safely |
| `uiGoalCardPositionNegative` | Negative goal card position is stored safely |
| `uiGoalCardPositionLarge` | Large goal card position is stored safely |
| `goalLargeWeightValues` | Very large goal weights are preserved correctly |
| `goalZeroWeightValues` | Zero goal weights are preserved correctly |
| `graphSetAndClearCaches` | Graph cache values can be set and cleared cleanly |
| `dataEmptyCachesAfterRemoval` | Cache dictionaries are empty after `removeAll()` |
| `dataOverwriteCacheEntry` | Cache values are overwritten when the same key is reused |
| `graphXScrollPositionDefault` | Default scroll position is initialized with a valid `Date` |

---

## Coverage Summary

| Metric | Value |
|--------|-------|
| **Total Tests** | **73** |
| **Measured Coverage** | **100.0%** |

### Code Paths Covered
default initialization for all nested state types, drag-state aggregation and reset logic, metric/streak prefix selection, negative-count clamping, grid column derivation, graph selection clearing and scroll-state transitions, goal state mutation, data-state caches and derived sorting, cross-state independence, and scalar edge cases.

### Methods Not Directly Tested
- None. All logic in `DashboardState.swift` is covered by `DashboardStateTests.swift`.
