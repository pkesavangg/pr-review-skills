# Dashboard Section ViewModel Test Coverage

## Overview

Unit-test coverage inventory for the five dashboard section view models:

- `BaseSectionViewModel`
- `WeekSectionViewModel`
- `MonthSectionViewModel`
- `YearSectionViewModel`
- `TotalSectionViewModel`

The current suite contains **199 unit tests across 5 test files**. These test files compile without errors.

Measured source-level `xccov` percentages are not included here yet because the current `build-for-testing` flow is blocked by a **pre-existing simulator-incompatible xcframework issue** in the BLE/WiFi scale packages. That failure is unrelated to the section view model tests themselves.

## Coverage Summary

| Test File | Tests | Source File | Coverage Status |
|--------|-------:|-------------|-----------------|
| `BaseSectionViewModel/BaseSectionViewModelTests.swift` | 69 | `Features/Dashboard/ViewModels/BaseSectionViewModel.swift` | Behavior coverage documented; measured `xccov` pending due unrelated simulator xcframework blocker |
| `WeekSectionViewModel/WeekSectionViewModelTests.swift` | 29 | `Features/Dashboard/ViewModels/WeekSectionViewModel.swift` | Behavior coverage documented; measured `xccov` pending due unrelated simulator xcframework blocker |
| `MonthSectionViewModel/MonthSectionViewModelTests.swift` | 30 | `Features/Dashboard/ViewModels/MonthSectionViewModel.swift` | Behavior coverage documented; measured `xccov` pending due unrelated simulator xcframework blocker |
| `YearSectionViewModel/YearSectionViewModelTests.swift` | 28 | `Features/Dashboard/ViewModels/YearSectionViewModel.swift` | Behavior coverage documented; measured `xccov` pending due unrelated simulator xcframework blocker |
| `TotalSectionViewModel/TotalSectionViewModelTests.swift` | 43 | `Features/Dashboard/ViewModels/TotalSectionViewModel.swift` | Behavior coverage documented; measured `xccov` pending due unrelated simulator xcframework blocker |
| **Total** | **199** | 5 source files | Combined behavioral coverage documented |

## Source File Coverage

### `BaseSectionViewModel.swift`

**Test file:** `BaseSectionViewModel/BaseSectionViewModelTests.swift`  
**Test count:** 69  
**Measured coverage:** Pending `xccov` verification because of the pre-existing BLE/WiFi simulator xcframework blocker

Covered behavior:

- shared initial state and safe defaults
- period-specific properties and rendering values
- line width, point sizing, and symbol-area calculations
- chart selection, selection clearing, and preferred date behavior
- scroll start/end handling, throttling, and forced position updates
- chart-frame updates and configuration sync behavior
- date-range derivation and fallback X-axis domains
- chart-series access, visibility filtering, and animation state
- visible-series filtering fallbacks and invalid-domain handling
- goal-chip positioning and X offset calculations
- chart-position calculations across normal and degenerate domains
- X-axis label formatting, selected label fallbacks, and cache invalidation
- solid-line visibility logic by period
- cache reads/writes/grouping/invalidation and explicit data refresh flows
- Y-axis sync from dashboard-store cached values
- left-boundary behavior when scrolled away from the earliest data point

### `WeekSectionViewModel.swift`

**Test file:** `WeekSectionViewModel/WeekSectionViewModelTests.swift`  
**Test count:** 29  
**Measured coverage:** Pending `xccov` verification because of the pre-existing BLE/WiFi simulator xcframework blocker

Covered behavior:

- `plotXDate` snapping to noon while preserving the intended day
- day-tick selection snapping for valid chart selections
- nil store, nil date, empty data, and single-item selection paths
- in-range and out-of-range boundary checks
- scroll position quantization, grid snapping, and nil-position no-op behavior
- empty-state fallback ticks and scroll lifecycle behavior

### `MonthSectionViewModel.swift`

**Test file:** `MonthSectionViewModel/MonthSectionViewModelTests.swift`  
**Test count:** 30  
**Measured coverage:** Pending `xccov` verification because of the pre-existing BLE/WiFi simulator xcframework blocker

Covered behavior:

- `plotXDate` noon snapping behavior
- Sunday-based section snapping
- nearest-point selection inside populated sections
- empty-section fallback to the section start tick
- before-first and outside-range selection handling
- right-edge slack tolerance
- empty-state and single-item behavior
- scroll lifecycle behavior and nil scroll-position handling

### `YearSectionViewModel.swift`

**Test file:** `YearSectionViewModel/YearSectionViewModelTests.swift`  
**Test count:** 28  
**Measured coverage:** Pending `xccov` verification because of the pre-existing BLE/WiFi simulator xcframework blocker

Covered behavior:

- month-tick snapping behavior
- selection handling at year boundaries
- first-month and last-month boundary coverage
- nil store, nil date, empty data, and single-item scenarios
- empty-state fallback ticks across 13 months
- scroll lifecycle coverage, month-grid snapping, and nil scroll-position no-op behavior
- period-specific solid-line and animation behavior

### `TotalSectionViewModel.swift`

**Test file:** `TotalSectionViewModel/TotalSectionViewModelTests.swift`  
**Test count:** 43  
**Measured coverage:** Pending `xccov` verification because of the pre-existing BLE/WiFi simulator xcframework blocker

Covered behavior:

- total-period property overrides and no-X-axis behavior
- no-op scroll methods
- nearest-point snapping across in-range and out-of-range selections
- before-first, after-last, single-item, and right-edge slack selection cases
- padded fallback date-range behavior, including single-point expansion
- `getChartPosition` domain-based positioning
- zero-width frame and zero-domain-range handling
- goal/weight display helpers and visible-series behavior

## Shared Edge Cases Covered

- nil store
- nil date
- empty data
- single item
- zero chart frame
- zero domain range
- goal above domain
- goal below domain
- scroll throttling
- cache invalidation
- multiple rapid selections
- selection-clear-reselection cycles

## Build Status

- Test files compile without errors.
- Current `build-for-testing` coverage verification is blocked by a pre-existing xcframework simulator incompatibility in the BLE/WiFi scale packages.
- That build issue is unrelated to the section view model test code in this folder.
