//
//  GraphView.swift
//  meApp
//
//  Created by Lakshmi Priya on 10/06/25.

import Charts
import SwiftUI

struct GraphView: View {
    @ObservedObject var dashboardStore: DashboardStore
    @EnvironmentObject private var accountService: AccountService
    @Environment(\.appTheme) private var theme
    // MOB-1591: viewport height (from DashboardScreen) so the loading skeleton matches the adaptive baby height.
    @Environment(\.dashboardViewportHeight) private var viewportHeight

    // MOB-1516 Phase D: the four legacy `*SectionViewModel` @StateObjects + the deferred period-change task
    // are gone — every product renders through `TrendChartHost`, which owns its own scroll/selection/rebuild.

    // MA-3837: tracks whether the first-appear auto-selection has run, so navigating away and
    // back doesn't override a user's intentional manual clear.
    @State private var didInitialSelect = false

    /// MOB-1516 Phase D: every product (weight / BPM / baby) renders through the v2 `TrendChartHost`; the
    /// legacy `BaseGraphView` engine is gone. This stays product-gated (not a toggle) as the vestige of the
    /// A/B era — it's `true` for all supported products and only feeds `shouldShowSkeleton`'s first-sync guard.
    private var usesNewEngine: Bool {
        // MOB-1516 / MOB-1591: weight, BPM, AND baby all render through the v2 `TrendChartHost` — including a
        // baby with NO entries, which the host draws as an empty baby `ChartModel` (`ChartPrep.buildEmpty`),
        // so the empty state shares the engine's per-period axes / reserved y-column / closed box.
        switch dashboardStore.productType {
        case .scale, .bpm, .baby: return true
        default: return false
        }
    }

    /// Latest entry date in the active period — used to drive first-appear / initial-load auto-select.
    private var latestEntryDate: Date? {
        // A baby with no real readings would otherwise auto-select a phantom point from the
        // dummy summaries in `continuousOperations`, flipping the header to "<period> average".
        // Treat it as having no data so the empty state stays clean.
        if dashboardStore.isBabySelection && !dashboardStore.hasBabyEntries { return nil }
        return dashboardStore.continuousOperations.max { $0.date < $1.date }?.date
    }

    // Whether the selection callout is currently visible for the active period. When true, the date/range
    // label above the chart is hidden (opacity 0) — the selected date is shown by the callout instead
    // (floating above the line for the new engine; the legacy floating callout otherwise), so it must not
    // ALSO appear under the weight.
    private var isShowingSelectionCallout: Bool {
        // MOB-1516: all products drive selection through the STORE, so the redundant selected-date label under
        // the weight hides on the store's crosshair flag (the floating callout shows the date instead).
        // MOB-1591: gate on the model actually having readings — an empty chart (a baby with no entries) can
        // carry a phantom store selection (`updateSelectedPeriod` auto-selects a dummy summary) but draws no
        // crosshair/callout, so the under-graph label must stay visible (parity with empty weight/BPM).
        dashboardStore.state.graph.showCrosshair && (dashboardStore.chartModel?.hasReadings ?? false)
    }

    // Show skeleton until graph is ready (set after settling delay)
    private var shouldShowSkeleton: Bool {
        if !dashboardStore.state.graph.isGraphReady { return true }
        // MOB-516: on FIRST login local SwiftData is still empty while the initial full-history
        // sync populates it — the fixed 300 ms `isGraphReady` timer would otherwise hide the
        // skeleton into an empty graph for a few seconds. Keep the skeleton until data lands.
        // Once the sync finishes (isSyncing=false), a genuinely empty account falls through to
        // the empty state (no infinite skeleton). Weight engine only; baby/BPM unaffected.
        if usesNewEngine, dashboardStore.continuousOperations.isEmpty, dashboardStore.isSyncing {
            return true
        }
        return false
    }

    // Match skeleton frame to the actual chart container height (baby charts are taller).
    // MOB-1591: baby uses the adaptive height so the skeleton and the live chart agree on shorter screens.
    // The taller height applies ONLY to a POPULATED baby graph (percentile band + dual axis); an empty baby
    // (no reading for the selected metric) or no baby profile matches the standard weight/BPM height, so the
    // skeleton doesn't tower over the empty chart that follows it. Mirrors `TrendChartHost.chartHeight` and
    // the `hasBabyReadings(for:)` gate `rebuildChartModel` uses to pick the empty skeleton vs the real chart.
    private var skeletonHeight: CGFloat {
        dashboardStore.selectedBabyProfile != nil
            && dashboardStore.hasBabyReadings(for: dashboardStore.selectedBabyMetric)
            ? DashboardChartLayout.babyHeight(forAvailableHeight: viewportHeight)
            : DashboardChartLayout.standardHeight
    }

    var body: some View {
        #if DEBUG
        _ = Self._logChanges()
        #endif
        return ZStack {
            // Skeleton loader shown only during initial graph load
            if shouldShowSkeleton {
                GraphSkeletonView(height: skeletonHeight)
            }

            // Actual graph content
            VStack(alignment: .leading) {
                // Preserve layout height: fade the label out instead of removing it to avoid jump
                Text(dashboardStore.displayManager.weightLabel.lowercased())
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
                    // Hide immediately when the callout is shown (driven by the same VM flag)
                    .opacity(isShowingSelectionCallout ? 0 : 1)
                    .animation(.none, value: isShowingSelectionCallout)
                    .padding(.leading, .spacingSM)
                    .padding(.vertical, .spacingXS)
                chartView
            }
            .opacity(shouldShowSkeleton ? 0 : 1)
        }
        .animation(.easeInOut(duration: 0.3), value: dashboardStore.state.graph.isGraphReady)
        // MOB-1516 Phase D: the legacy `.onChange(selectedPeriod)` section-VM machinery + the section-VM
        // tear-down handlers are gone. `TrendChartHost` reacts to period / product / metric changes itself
        // (via its `rebuildSignal` + `.onChange(selectedPeriod)`), so there is nothing to configure here.
        // Immediately react to active account goal updates like GoalProgressView.
        // Skip during dashboard reset to prevent handleActiveAccountChanged from
        // re-triggering skeleton (refreshAccount publishes activeAccount mid-init).
        .onReceive(accountService.$activeAccount) { _ in
            guard !dashboardStore.state.ui.isResettingDashboard else { return }
            dashboardStore.lifecycleManager.handleSettingsChange()
        }
        // MA-3837: on first appear with data already present, auto-select the latest entry so the
        // header tile/crosshair shows the most recent point on cold start.
        .onAppear { performInitialSelectIfNeeded() }
        // MA-3837: handle data arriving after the view appeared (initial load was empty).
        .onChange(of: latestEntryDate) { oldLatest, newLatest in
            guard oldLatest == nil, newLatest != nil else { return }
            didInitialSelect = true
            dashboardStore.chartManager.selectLatestEntryIfNeeded()
        }
        // Cold-start safety net: the one-shot 100ms auto-select above can fire before the
        // section VM / chart data / scroll-to-latest have settled, so on first open the chart
        // can land on the wrong week with no crosshair and the date callout on the wrong entry
        // (release reset to the latest reliably via updateSelectedPeriod). When the graph
        // finishes loading (isGraphReady true) and nothing landed yet, re-run the same
        // reset-to-latest path a tab switch uses — it scrolls to the latest window AND
        // auto-selects the latest entry. hasLandedInitialSelection keeps this to cold start so
        // it never overrides a user's manual scroll/selection.
        .onChange(of: dashboardStore.state.graph.isGraphReady) { wasReady, isReady in
            guard !wasReady, isReady else { return }
            guard latestEntryDate != nil, !dashboardStore.state.ui.hasLandedInitialSelection else { return }
            didInitialSelect = true
            dashboardStore.chartManager.updateSelectedPeriod(dashboardStore.state.graph.selectedPeriod)
        }
    }

    // MARK: - First-Appear Auto Selection

    private func performInitialSelectIfNeeded() {
        guard !didInitialSelect else { return }
        guard latestEntryDate != nil else { return } // No data yet — the onChange above handles it.
        didInitialSelect = true
        // Defer briefly so the section view model is wired to the store before selecting,
        // otherwise chartOperations is empty and the selection can't resolve a point.
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 100_000_000) // 100ms
            dashboardStore.chartManager.selectLatestEntryIfNeeded()
        }
    }

    // MARK: - Chart View

    private var chartView: some View {
        // MOB-1516 / MOB-1591: the v2 engine renders EVERY product AND every empty state — including a baby
        // with no readings. That empty case is an empty baby `ChartModel` (`ChartPrep.buildEmpty`, dispatched
        // in `DashboardStore.rebuildChartModel`), so it gets the same per-period axes, reserved y-axis column,
        // closed box, and leading inset as an empty weight/BPM chart — instead of a period-blind hand-rolled
        // grid. (`BabyEmptyGraphView` remains for the snapshot card, which has no period sections.)
        TrendChartHost(dashboardStore: dashboardStore)
    }

}

#Preview {
    GraphView(dashboardStore: DashboardStore())
        .frame(height: 240)
        .padding()
}
