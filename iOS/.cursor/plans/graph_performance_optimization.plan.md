# Graph Performance Optimization Plan

## Problem Statement

The graph is slow when there are many entries (10 years of data with ~3,650 daily summaries):

- Week and month graphs load slowly
- Tab switching takes time
- Scrolling and domain changes cause slow graph resizing

## Root Cause Analysis

### Critical Bottlenecks Identified

#### 1. `getContinuousOperations` Sorts Data on Every Call (CRITICAL)

**File:** `DashboardDataManager.swift` (lines 53-61)

```swift
func getContinuousOperations(for period: TimePeriod) -> [BathScaleWeightSummary] {
    switch period {
    case .week, .month:
        return state.dailySummaries.compactMap { $0 }.sorted { $0.date < $1.date }
    case .year, .total:
        return state.monthlySummaries.compactMap { $0 }.sorted { $0.date < $1.date }
    }
}
```

This sorts 3,650+ entries EVERY time it's accessed. Called 10-20+ times per frame during scroll via `chartOperations`.

#### 2. Expensive `chartOperations` Computed Property (CRITICAL)

**File:** `BaseSectionViewModel.swift` (line 117-119)

```swift
var chartOperations: [BathScaleWeightSummary] {
    return dashboardStore?.continuousOperations ?? []
}
```

Called from: `dateRange`, `xAxisValues`, `isAtLeftBoundary`, Y-axis config, selection handling.

#### 3. X-axis Generation Maps All Operations (HIGH)

**File:** `DashboardGraphManager.swift` (lines 1261-1263, 1276-1277)

```swift
let allDates: [Date] = operations.map { $0.date }  // Maps 3,650 items
let minDate = allDates.min() ?? scrollPosition
let maxDate = allDates.max() ?? scrollPosition
```

#### 4. Chart Data Generation Loops Through All Operations (HIGH)

**File:** `DashboardGraphManager.swift` (lines 469-484)

Processes all 3,650 operations even when only ~7-30 are visible.

#### 5. Multiple `.onChange` Handlers Trigger Cascading Updates (MEDIUM)

**File:** `BaseGraphView.swift`

Many handlers trigger `updateCachedChartData()` which triggers full chart regeneration.

---

## Solution: Multi-Level Caching + Windowed Data Strategy

### Phase 1: Cache Sorted Operations (HIGHEST PRIORITY)

**Goal:** Sort operations ONCE when data changes, not on every access.

**File:** `DashboardDataManager.swift`

**Changes:**

1. Add cached sorted arrays as private properties
2. Update cache when data changes in `updateStateFromDailySummaries` and `updateStateFromMonthlySummaries`
3. Return cached array from `getContinuousOperations`
```swift
// Add to DashboardDataManager
private var cachedSortedDailySummaries: [BathScaleWeightSummary] = []
private var cachedSortedMonthlySummaries: [BathScaleWeightSummary] = []

private func updateStateFromDailySummaries(_ dailySummaries: [BathScaleWeightSummary]) {
    state.dailySummaries = dailySummaries.map { $0 }
    // Pre-sort once
    cachedSortedDailySummaries = dailySummaries.sorted { $0.date < $1.date }
    state.dailyCache = Dictionary(uniqueKeysWithValues: dailySummaries.map { ($0.period, $0) })
}

func getContinuousOperations(for period: TimePeriod) -> [BathScaleWeightSummary] {
    switch period {
    case .week, .month:
        return cachedSortedDailySummaries
    case .year, .total:
        return cachedSortedMonthlySummaries
    }
}
```


---

### Phase 2: Cache Min/Max Date Bounds (HIGH PRIORITY)

**Goal:** Calculate date bounds once, not on every scroll frame.

**File:** `DashboardDataManager.swift`

**Changes:**

1. Add cached date bound properties
2. Update bounds when summaries change
3. Expose getter for bounds
```swift
// Add to DashboardDataManager
private(set) var cachedDailyMinDate: Date?
private(set) var cachedDailyMaxDate: Date?
private(set) var cachedMonthlyMinDate: Date?
private(set) var cachedMonthlyMaxDate: Date?

private func updateStateFromDailySummaries(_ dailySummaries: [BathScaleWeightSummary]) {
    // ... existing code ...
    
    // Cache date bounds
    cachedDailyMinDate = cachedSortedDailySummaries.first?.date
    cachedDailyMaxDate = cachedSortedDailySummaries.last?.date
}

func getDateBounds(for period: TimePeriod) -> (min: Date, max: Date)? {
    switch period {
    case .week, .month:
        guard let min = cachedDailyMinDate, let max = cachedDailyMaxDate else { return nil }
        return (min, max)
    case .year, .total:
        guard let min = cachedMonthlyMinDate, let max = cachedMonthlyMaxDate else { return nil }
        return (min, max)
    }
}
```


**File:** `DashboardGraphManager.swift`

**Changes:**

- Update `generateVisibleXAxisValues` to use cached bounds instead of mapping all operations

---

### Phase 3: Windowed Chart Data with Buffer (HIGH PRIORITY)

**Goal:** Only generate chart points for visible region + buffer, preventing blank pages.

**File:** `DashboardGraphManager.swift`

#### 3a. Large Buffer Window (Prevents Blank Pages)

```swift
/// Gets operations for chart rendering with large buffer to prevent blank pages during scroll
func getChartOperationsWithBuffer(
    from allOperations: [BathScaleWeightSummary],
    scrollPosition: Date,
    period: TimePeriod
) -> [BathScaleWeightSummary] {
    let domainLength = visibleDomainLength(for: period)
    
    // Use 3x visible domain as buffer (1 screen left + visible + 1 screen right)
    let bufferMultiplier: Double = 3.0
    let totalBufferLength = domainLength * bufferMultiplier
    
    let windowStart = scrollPosition.addingTimeInterval(-totalBufferLength / 2)
    let windowEnd = scrollPosition.addingTimeInterval(totalBufferLength / 2)
    
    // Binary search for efficiency on sorted array
    guard let startIndex = binarySearchFirstIndex(in: allOperations, where: { $0.date >= windowStart }),
          let endIndex = binarySearchLastIndex(in: allOperations, where: { $0.date <= windowEnd }) else {
        return allOperations // Fallback to all if search fails
    }
    
    // Include one extra point on each side for line continuity
    let safeStartIndex = max(0, startIndex - 1)
    let safeEndIndex = min(allOperations.count - 1, endIndex + 1)
    
    return Array(allOperations[safeStartIndex...safeEndIndex])
}

// Binary search helpers for sorted array
private func binarySearchFirstIndex(in operations: [BathScaleWeightSummary], where predicate: (BathScaleWeightSummary) -> Bool) -> Int? {
    var low = 0
    var high = operations.count
    while low < high {
        let mid = (low + high) / 2
        if predicate(operations[mid]) {
            high = mid
        } else {
            low = mid + 1
        }
    }
    return low < operations.count ? low : nil
}

private func binarySearchLastIndex(in operations: [BathScaleWeightSummary], where predicate: (BathScaleWeightSummary) -> Bool) -> Int? {
    var low = 0
    var high = operations.count
    while low < high {
        let mid = (low + high) / 2
        if predicate(operations[mid]) {
            low = mid + 1
        } else {
            high = mid
        }
    }
    return low > 0 ? low - 1 : nil
}
```

#### 3b. Strict Scroll Boundary Enforcement (Prevents Over-Scrolling)

```swift
/// Enforces scroll boundaries to prevent over-scrolling beyond data range
func clampScrollPosition(
    _ position: Date,
    for period: TimePeriod,
    minDate: Date,
    maxDate: Date
) -> Date {
    let domainLength = visibleDomainLength(for: period)
    
    // Left boundary: 10% padding for visual comfort
    let minScrollPosition = minDate.addingTimeInterval(-domainLength * 0.1)
    
    // Right boundary: Keep last 90% of domain showing data
    let maxScrollPosition = maxDate.addingTimeInterval(-domainLength * 0.9)
    
    // Handle edge case where data range is smaller than visible domain
    if minScrollPosition >= maxScrollPosition {
        return minDate
    }
    
    return max(minScrollPosition, min(maxScrollPosition, position))
}
```

#### 3c. Smart Snap Mechanism

```swift
/// Snaps scroll position to nearest valid alignment point
func snapScrollPosition(_ position: Date, for period: TimePeriod) -> Date {
    let calendar = Calendar.current
    
    switch period {
    case .week:
        // Snap to start of day (noon for plotting)
        var components = calendar.dateComponents([.year, .month, .day], from: position)
        components.hour = 12
        return calendar.date(from: components) ?? position
        
    case .month:
        // Snap to start of week
        let weekday = calendar.component(.weekday, from: position)
        let daysToSubtract = weekday - calendar.firstWeekday
        return calendar.date(byAdding: .day, value: -daysToSubtract, to: position) ?? position
        
    case .year:
        // Snap to start of month
        var components = calendar.dateComponents([.year, .month], from: position)
        components.day = 1
        return calendar.date(from: components) ?? position
        
    case .total:
        return position // No snapping for total view
    }
}
```

---

### Phase 4: Integrate Boundary Enforcement

**File:** `DashboardGraphManager.swift`

Update `handleScrollPositionChange`:

```swift
func handleScrollPositionChange(_ newPosition: Date?) {
    guard let newPosition = newPosition else { return }
    
    // Use cached bounds (no sorting needed)
    guard let bounds = dataManager.getDateBounds(for: state.selectedPeriod) else {
        state.xScrollPosition = newPosition
        return
    }
    
    // Clamp to valid range
    let clampedPosition = clampScrollPosition(
        newPosition,
        for: state.selectedPeriod,
        minDate: bounds.min,
        maxDate: bounds.max
    )
    
    // Only update if position actually changed
    guard abs(clampedPosition.timeIntervalSince(state.xScrollPosition)) > 0.1 else { return }
    
    state.xScrollPosition = clampedPosition
    state.isScrolling = true
}
```

**File:** `BaseGraphView.swift`

Update `chartScrollPosition` binding:

```swift
.chartScrollPosition(x: Binding(
    get: { viewModel.scrollPosition },
    set: { newPosition in
        guard let newPosition = newPosition else { return }
        
        // Apply boundary clamping
        if let bounds = dashboardStore.dataManager.getDateBounds(for: viewModel.timePeriod) {
            let clampedPosition = dashboardStore.graphManager.clampScrollPosition(
                newPosition,
                for: viewModel.timePeriod,
                minDate: bounds.min,
                maxDate: bounds.max
            )
            DispatchQueue.main.async {
                viewModel.handleScrollPositionChange(clampedPosition)
            }
        } else {
            DispatchQueue.main.async {
                viewModel.handleScrollPositionChange(newPosition)
            }
        }
    }
))
```

---

### Phase 5: Optimize X-axis Value Generation

**File:** `BaseSectionViewModel.swift`

Cache X-axis values more aggressively:

```swift
// Add cached properties
private var _cachedXAxisValues: [Date] = []
private var _lastXAxisScrollPosition: Date?
private var _lastXAxisPeriod: TimePeriod?

var xAxisValues: [Date] {
    // Return cached if scroll position hasn't changed significantly
    if let lastPos = _lastXAxisScrollPosition,
       _lastXAxisPeriod == timePeriod,
       abs(scrollPosition.timeIntervalSince(lastPos)) < 1.0,
       !_cachedXAxisValues.isEmpty {
        return _cachedXAxisValues
    }
    
    // Generate and cache
    if let store = dashboardStore {
        let ticks = store.graphManager.generateVisibleXAxisValues(
            for: timePeriod,
            from: store.continuousOperations,
            scrollPosition: scrollPosition
        )
        if !ticks.isEmpty {
            _cachedXAxisValues = ticks.sorted()
            _lastXAxisScrollPosition = scrollPosition
            _lastXAxisPeriod = timePeriod
            return _cachedXAxisValues
        }
    }
    
    return fallbackXAxisValues().sorted()
}
```

---

### Phase 6: Throttle Cache Updates During Scroll

**File:** `BaseGraphView.swift`

```swift
// Add throttling state
@State private var lastCacheUpdateTime: Date = .distantPast
private let cacheUpdateThrottle: TimeInterval = 0.05 // 50ms

// Replace direct updateCachedChartData calls with throttled version
private func updateCachedChartDataThrottled() {
    let now = Date()
    guard now.timeIntervalSince(lastCacheUpdateTime) > cacheUpdateThrottle else { return }
    lastCacheUpdateTime = now
    updateCachedChartData()
}

// Consolidate onChange handlers
.onChange(of: viewModel.yAxisDomain) { _, _ in
    scheduleDelayedCacheUpdate()
}

private func scheduleDelayedCacheUpdate() {
    scrollUpdateWorkItem?.cancel()
    scrollUpdateWorkItem = DispatchWorkItem { [self] in
        updateCachedChartData()
        precomputeLabels()
    }
    DispatchQueue.main.asyncAfter(deadline: .now() + 0.05, execute: scrollUpdateWorkItem!)
}
```

---

## Implementation Order

| Step | Priority | Change | Files | Impact |

|------|----------|--------|-------|--------|

| 1 | CRITICAL | Cache sorted operations | `DashboardDataManager.swift` | Eliminates repeated sorting |

| 2 | CRITICAL | Cache min/max date bounds | `DashboardDataManager.swift` | Faster boundary checks |

| 3 | HIGH | Windowed chart data with buffer | `DashboardGraphManager.swift` | Process only visible + buffer data |

| 4 | HIGH | Scroll boundary enforcement | `DashboardGraphManager.swift`, `BaseGraphView.swift` | Prevent over-scrolling |

| 5 | HIGH | Smart snap mechanism | `DashboardGraphManager.swift` | Smooth scroll behavior |

| 6 | MEDIUM | X-axis value caching | `BaseSectionViewModel.swift` | Faster axis generation |

| 7 | MEDIUM | Throttle cache updates | `BaseGraphView.swift` | Reduce redundant work during scroll |

---

## Expected Results

| Metric | Before | After |

|--------|--------|-------|

| Initial load | Slow (multiple sorts) | Fast (pre-sorted) |

| Tab switching | 1-2 seconds | <200ms |

| Scroll FPS | Choppy, drops to 15-30fps | Smooth 60fps |

| Over-scroll | Can scroll to blank areas | Clamped to data bounds |

| Memory during scroll | All 3,650 points in memory | Only ~50-100 buffered points |

---

## Scroll Safeguards Summary

| Concern | Solution | Benefit |

|---------|----------|---------|

| Blank pages during fast scroll | 3x visible domain buffer preloaded | User always sees data |

| Over-scrolling past data | `clampScrollPosition()` enforces boundaries | Cannot scroll to empty areas |

| Jarring scroll stops | Snap to nearest valid alignment | Smooth, predictable behavior |

| Line discontinuity at edges | Include ±1 extra point outside window | Lines connect smoothly |

---

## Files to Modify

1. `DashboardDataManager.swift` - Caching sorted data and bounds
2. `DashboardGraphManager.swift` - Windowed data, boundary enforcement, snap mechanism
3. `BaseSectionViewModel.swift` - X-axis caching
4. `BaseGraphView.swift` - Throttled updates, boundary-aware scroll binding