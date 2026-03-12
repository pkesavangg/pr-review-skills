# DashboardStore Test Coverage Guide

## Purpose
This document explains:
- what `DashboardStore` tests are responsible for
- how the store suites are split
- what each suite is protecting
- the full list of current test cases in each suite
- the current measured coverage position for `DashboardStore.swift`

The goal is to keep dashboard orchestration stable when initialization flow, bindings, subscriptions, edit-mode state management, and computed UI state change.

## Current Coverage
Coverage snapshot date: **March 9, 2026**

Bottom-line (base store file) coverage:
- `DashboardStore.swift`: **92%** from the latest local run


Note:
- The checked-in aggregate report under `meAppTests/Reports/coverage-report.*` is older and does not reflect the latest dashboard-store-focused work.
- This guide documents the current suite structure and the latest reported `77%` number.

## Test Count Summary
- `Initialization And Bindings`: 24 tests
- `Edit Session`: 29 tests
- `Computed Properties`: 32 tests
- `Utility And Derived Data`: 27 tests
- Total: **112 tests**

## Files Involved

### Production code under test
- `meApp/Features/Dashboard/Stores/DashboardStore.swift`

### Main test entry point
- `meAppTests/Features/Dashboard/Stores/DashboardStore/DashboardStoreTests.swift`

### DashboardStore-specific fixtures and suites
- `meAppTests/Features/Dashboard/Stores/DashboardStore/Fixtures/DashboardStoreTestSupport.swift`
- `meAppTests/Features/Dashboard/Stores/DashboardStore/TestSuits/DashboardStoreInitializationAndBindingTests.swift`
- `meAppTests/Features/Dashboard/Stores/DashboardStore/TestSuits/DashboardStoreEditSessionTests.swift`
- `meAppTests/Features/Dashboard/Stores/DashboardStore/TestSuits/DashboardStoreComputedPropertiesTests.swift`
- `meAppTests/Features/Dashboard/Stores/DashboardStore/TestSuits/DashboardStoreUtilityTests.swift`

### Dashboard-specific support used by these suites
- `meAppTests/Features/Dashboard/Fixtures/DashboardTestFixtures.swift`
- `meAppTests/Features/Dashboard/Mocks/MockDashboardStoreDependencies.swift`
- `meAppTests/Support/DI/TestDependencyContainer.swift`

## Coverage Strategy
`DashboardStore` is a coordinator store, not a pure data model. Its main risk is not isolated calculation bugs; it is orchestration drift:
- state not propagating between managers and store
- subscriptions not responding to account or entry changes
- edit sessions not restoring or preserving state correctly
- computed UI flags diverging from manager/account state
- cached/derived data paths returning stale or empty values

Because of that, the suites are split by behavior instead of by file size:
1. initialization and binding orchestration
2. edit-session lifecycle
3. computed UI-facing state
4. utility, cache, and derived-data helpers

## Suite Breakdown

### 1) Initialization And Bindings
File:
- `meAppTests/Features/Dashboard/Stores/DashboardStore/TestSuits/DashboardStoreInitializationAndBindingTests.swift`

Test count:
- 24

Focus:
- lightweight and full initializer behavior
- manager wiring
- manager-to-store state propagation
- reset suppression behavior
- subscription-driven updates from account and entry state

Full test list:
- `init lightweight: creates store with default state`
- `init lightweight: managers are initialized`
- `init lightweight true: does not setup subscriptions`
- `init lightweight false: sets up subscriptions`
- `init full: seeds default progress state and kicks off initialization`
- `init: formatter and cacheManager are injected correctly`
- `init: editSessionManager starts with no snapshot`
- `bindings: metricsManager state changes propagate to store`
- `bindings: streakManager state changes propagate to store`
- `bindings: goalManager state changes propagate to store`
- `bindings: graphManager state changes propagate to store`
- `bindings: dataManager state changes propagate to store`
- `bindings: metricsManager state suppressed during dashboard reset`
- `bindings: streakManager state suppressed during dashboard reset`
- `bindings: goalManager state NOT suppressed during dashboard reset`
- `subscriptions: dashboard type change updates store metrics type`
- `subscriptions: active account change clears chart initialization state`
- `bindings: entry data changes invalidate continuous operations cache`
- `bindings: first entry arrival resets chart initialization while scrolling`
- `graph period change: store state reflects new period after manager update`
- `graph period: all period values can be set`
- `data state: adding daily summaries updates hasAnyEntries`
- `data state: clearing daily summaries updates hasAnyEntries`
- `goal state: goalManager changes propagate`

What this suite protects:
- store construction stays deterministic
- manager state keeps flowing into centralized dashboard state
- reset mode suppresses only the intended bindings
- account and entry subscriptions continue to trigger dashboard reactions

### 2) Edit Session
File:
- `meAppTests/Features/Dashboard/Stores/DashboardStore/TestSuits/DashboardStoreEditSessionTests.swift`

Test count:
- 29

Focus:
- snapshot capture
- snapshot restore
- unsaved-change detection
- cancel/reset flows
- edge cases around empty arrays, order changes, and removal sets

Full test list:
- `beginEdit: takes snapshot of current state`
- `beginEdit: does not overwrite existing snapshot`
- `cancelEdit: restores state from snapshot`
- `cancelEdit: clears edit mode and selection state`
- `cancelEdit: clears snapshot after restoration`
- `cancelEdit: without snapshot does not crash`
- `hasUnsavedChanges: returns false when no snapshot`
- `hasUnsavedChanges: returns false when state unchanged`
- `hasUnsavedChanges: returns true when metrics changed`
- `hasUnsavedChanges: returns true when activeMetricsCount changed`
- `hasUnsavedChanges: returns true when goal card removal changed`
- `hasUnsavedChanges: returns true when streaks changed`
- `hasUnsavedChanges: returns true when removedMetrics changed`
- `hasUnsavedChanges: returns true when removedStreaks changed`
- `hasUnsavedChanges: returns true when goalCardPosition changed`
- `hasUnsavedChanges: returns true when streakGridOrder changed`
- `updateSnapshot: updates existing snapshot`
- `currentEditSnapshot: captures all fields correctly`
- `restoreFromSnapshot: restores all fields correctly`
- `resetEditSession: restores snapshot, resets order, clears state, and starts new edit`
- `full edit flow: begin → modify → cancel restores original state`
- `full edit flow: begin → modify → save (update snapshot)`
- `edit mode: multiple edit sessions work independently`
- `edit session with empty metrics array`
- `edit session with empty streaks array`
- `edit session: all removal sets empty then populated`
- `edit session: goalCardPosition from 0 to large value`
- `edit session: streakGridOrder reorder detection`
- `edit session: same data different order is detected`

What this suite protects:
- edit mode remains reversible
- snapshot logic remains consistent as UI editing evolves
- reorder/removal state does not silently escape snapshot tracking

### 3) Computed Properties
File:
- `meAppTests/Features/Dashboard/Stores/DashboardStore/TestSuits/DashboardStoreComputedPropertiesTests.swift`

Test count:
- 32

Focus:
- UI-facing computed properties
- body metric and streak visibility rules
- skeleton conditions
- divider and empty-content logic
- account-backed derived flags

Full test list:
- `hasAnyEntries: returns false when data is empty`
- `hasAnyEntries: returns true when daily summaries exist`
- `hasGoalSet: reflects goal state`
- `metricsToShow: returns empty when config not loaded`
- `metricsToShow: returns metrics when config loaded`
- `effectiveDashboardType: reflects metrics state`
- `streakItemsToShow: returns empty when no streaks`
- `streakItemsToShow: filters removed streaks in non-edit mode`
- `streakItemsToShow: shows all in edit mode with removed items last`
- `streakItemsToShow: returns all when progress not loaded yet`
- `isAnyItemBeingDragged: delegates to UIState`
- `isWeightlessModeEnabled: returns false when no account`
- `currentUnit: returns lb by default when no account`
- `selectedBodyMetric: returns weight when no label selected`
- `shouldShowGoalCardOrStreaks: true when goal card not removed`
- `shouldShowGoalCardOrStreaks: true when streaks visible`
- `shouldShowGoalCardOrStreaks: false when all removed and no streaks`
- `hasBodyMetrics: false when metricsToShow is empty`
- `shouldShowBodyMetrics: false when no dashboard config and no account`
- `shouldShowBodyMetrics: delegates to hasBodyMetrics when config loaded`
- `shouldShowBodyMetricsSkeleton: true when config not loaded and should show`
- `shouldShowProgressMetricsSkeleton: true when progress not loaded`
- `shouldShowProgressMetricsSkeleton: false when progress loaded and no goal/streaks`
- `shouldShowGoalStreakSection: false when config not loaded`
- `shouldShowGoalStreakSection: true when config loaded and goal not removed`
- `shouldShowStreakGrid: false when no visible streaks`
- `shouldShowStreakGrid: false when all streaks removed`
- `shouldShowStreakGrid: true when visible streaks exist`
- `shouldShowDivider: false when no body metrics and no progress`
- `shouldShowDivider: true when body metrics and progress content are both visible`
- `allContentRemoved: false by default`
- `allContentRemoved: true when all metrics, goal card, and streaks are removed`

What this suite protects:
- UI visibility rules do not drift as dashboard behavior changes
- empty/loading/editing conditions remain coherent
- account-derived fallback behavior remains predictable

### 4) Utility And Derived Data
File:
- `meAppTests/Features/Dashboard/Stores/DashboardStore/TestSuits/DashboardStoreUtilityTests.swift`

Test count:
- 27

Focus:
- cache invalidation helpers
- object-will-change behavior
- cached operation accessors
- chart data empty path
- account- and goal-derived values
- loader binding behavior

Full test list:
- `invalidateContinuousOperationsCache: delegates to cache manager`
- `invalidateContinuousOperationsCache: multiple calls increment counter`
- `forceImmediateUIUpdate: triggers objectWillChange`
- `scheduleUIUpdate: eventually triggers objectWillChange`
- `scheduleUIUpdate: debounces multiple rapid calls`
- `allowedNumericCharacters: contains expected characters`
- `allowedNumericCharacters: does not contain letters`
- `continuousOperations: returns data from cache manager`
- `continuousOperations: returns sorted daily summaries from data manager`
- `visibleOperations: returns data from cache manager`
- `chartSeriesData: returns empty when no continuous operations exist`
- `visibleDomainLength: returns expected values for supported periods`
- `weightlessAnchorWeight: returns nil when no account`
- `weightlessAnchorWeight: returns converted anchor weight when enabled`
- `goalWeightForDisplay: returns nil when no goal set`
- `goalWeightForDisplay: returns displayed goal weight from active account`
- `goalWeightForDisplay: subtracts anchor weight in weightless mode`
- `hasEntriesButNoneInCurrentPeriod: false when no entries`
- `hasEntriesButNoneInCurrentPeriod: true when continuous operations exist but visible operations are empty`
- `currentUnitString: returns rawValue of unit`
- `currentUnitText: defaults to lbs when there is no active account`
- `current unit properties: reflect active account settings`
- `unitText: delegates to goal manager`
- `loaderData: returns nil when not loading and no override`
- `loaderData: returns loader when isLoading is true`
- `loaderData: returns override when set`
- `store state remains consistent after rapid state mutations`

What this suite protects:
- helper accessors keep returning stable, testable values
- cached data paths continue to read through the intended manager/cache layers
- account, goal, unit, and weightless-mode derivations remain correct

## What These Tests Intentionally Validate
The dashboard store suites are designed to protect regressions in:
- store initialization and manager wiring
- manager-to-store synchronization
- account and entry subscription handling
- chart reset and cache invalidation side effects
- edit-mode snapshot correctness
- derived UI visibility flags
- account-backed unit and goal display behavior
- cached operation and empty-state accessors

These are the highest-risk areas because `DashboardStore` is coordinating multiple managers and asynchronous updates.

## What Is Not Fully Covered Yet
Even with 112 tests, the latest measured `DashboardStore.swift` coverage is still below target.

The remaining gap is most likely in branch-heavy orchestration paths such as:
- more of the full initializer flow
- deeper subscription combinations in `setupSubscriptions()`
- more lifecycle-manager-triggered reactions
- additional chart/cache/coordinator branches exercised only after real state transitions

These gaps are why the file is still at **77%** instead of the required **85%+**.

## Fixture Design
`DashboardStoreTestSupport.makeSUT()` does the following:
- resets the dependency container
- registers concrete dashboard managers and services backed by mocks
- injects mock dashboard formatter and cache services when requested
- returns the store plus the active `AccountService`
- keeps the lightweight store path available for fast deterministic tests

Why that matters:
- `DashboardStore` depends on concrete dashboard managers and injected services
- the tests need real orchestration behavior with controlled service state
- direct UI/state assertions are only useful if the DI graph is deterministic

## How To Add New DashboardStore Tests
1. Put the test in the behavior suite it belongs to.
2. Reuse `DashboardStoreTestSupport.makeSUT()` unless the full initializer is required.
3. If the behavior depends on published dashboard data, seed the injected `EntryService` or `AccountService`, not just the derived store state.
4. Prefer asserting observable store outcomes:
   - `state`
   - computed properties
   - cache-manager call counts
   - chart/reset flags
5. For async propagation, wait on the store-visible state change before asserting.
6. Add regression coverage for whichever branch was fixed, not just the final happy path.

## Coverage Expectation
- Minimum target for `DashboardStore.swift`: **85%**
- Latest reported `DashboardStore.swift` coverage: **77%**
- Current status: **needs more coverage work**

Each dashboard-store bug fix should add a regression test in the suite that owns that behavior:
- initialization/subscription bugs -> `Initialization And Bindings`
- edit-mode regressions -> `Edit Session`
- visibility/computed-state regressions -> `Computed Properties`
- cache/helper/derived-value regressions -> `Utility And Derived Data`
