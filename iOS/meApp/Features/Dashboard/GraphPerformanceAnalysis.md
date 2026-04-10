# Graph Performance Analysis

## Current Graph Status

1. Graph-related severe hangs have reduced compared with the earlier profiling passes.
2. The graph is noticeably better during normal updates, but there are still visible stalls during the later chart refresh and interaction windows.
3. The graph is no longer failing mainly because of snapshot preprocessing. The remaining cost is centered on SwiftUI/Charts invalidation around `BaseGraphView`.
4. The graph is still main-thread-sensitive, so any upstream dashboard refresh that dirties the graph subtree can turn into a visible stall.

## Graph Root Cause

1. The main graph hot path is still `BaseGraphView.mainChartView` -> `scrollableChartModifiers` -> `ChartSeriesContent` -> `BaseGraphViewCacheSupport.pointsToRender`.
2. SwiftUI invalidation work such as `AG::Graph`, `ViewGraph`, and `_UIHostingView` still shows up around the chart subtree, which means the graph is being rebuilt more broadly than it should be.
3. The remaining graph stalls are often amplified by upstream dashboard progress and streak refreshes. Those refreshes dirty the dashboard first, and the graph then pays the redraw cost.
4. Chart interaction can still add extra work on top of rebuilds, especially when selection and gesture plumbing overlap with the same invalidation window.

## Graph Remediations Applied

1. `BaseGraphView` now observes a single Y-axis cache signature instead of reacting separately to cached-domain and cached-tick updates.
2. `BaseSectionViewModel.syncYAxisFromStore()` now exits early when the incoming cached Y-axis values already match local state.
3. `BaseGraphView.handleDataSignatureChange(...)` no longer performs an extra local cache invalidation after `refreshData()` already invalidates the same caches.
4. `ChartSeriesContent.chartContentForSeries(...)` now resolves colors once per series render pass instead of re-resolving them for every plotted point.
5. `DashboardStreakManager.refreshStreakData()` now coalesces overlapping refreshes so repeated dashboard lifecycle callbacks do not keep re-triggering graph-invalidating progress work.
6. `DateTimeTools` now caches parsed timestamps plus derived local day/month keys, which reduces repeated formatter work that was feeding back into graph refreshes.
7. `SwiftDataWorker.fetchProgressData(accountId:)` now derives week and month slices from ISO-8601 string boundaries instead of reparsing every timestamp during each progress refresh.

## What To Validate Next

1. Re-profile the late-session graph windows and confirm `BaseGraphView`, `ChartSeriesContent`, and `pointsToRender` appear less often in the top stacks.
2. Confirm the graph no longer gets dirtied repeatedly by overlapping streak and progress refreshes.
3. If interaction stalls remain, simplify the redundant gesture plumbing layered on top of `chartXSelection`.
4. If the graph body is still rebuilding too broadly, move large chart render inputs behind a reference-backed render model so SwiftUI does less copying and diffing per invalidation.
