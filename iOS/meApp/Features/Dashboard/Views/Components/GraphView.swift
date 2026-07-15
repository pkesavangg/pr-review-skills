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

    // Section view models
    @StateObject private var totalSectionViewModel = TotalSectionViewModel()
    @StateObject private var yearSectionViewModel = YearSectionViewModel()
    @StateObject private var monthSectionViewModel = MonthSectionViewModel()
    @StateObject private var weekSectionViewModel = WeekSectionViewModel()

    // PERFORMANCE: Cancellable task for deferred period change configuration
    @State private var periodChangeTask: Task<Void, Never>?

    // MA-3837: tracks whether the first-appear auto-selection has run, so navigating away and
    // back doesn't override a user's intentional manual clear.
    @State private var didInitialSelect = false

    /// MOB-518 V6 / MOB-1516 Phase B: the v2 engine (`TrendChartHost`) renders weight AND BPM. Baby still
    /// uses the legacy `BaseGraphView` engine below (they share it) until Phase Y, so this is gated on the
    /// product type, not a toggle. When true, the host owns period/scroll/model and the legacy section-VM
    /// machinery must NOT run (see the `selectedPeriod` handler / `chartView`).
    private var usesNewEngine: Bool {
        // MOB-1516: weight (V6) + BPM (Phase B) render through the v2 `TrendChartHost`. Baby is still on the
        // legacy `BaseGraphView` engine until Phase Y (it falls through to the `else` branch in `chartView`).
        dashboardStore.productType == .scale || dashboardStore.productType == .bpm
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
        // MOB-518: the new weight engine drives selection through the STORE, not the section VMs (which stay
        // unselected for weight). Read the store's crosshair so the redundant selected-date label under the
        // weight hides on selection, matching the legacy behaviour.
        if usesNewEngine {
            return dashboardStore.state.graph.showCrosshair
        }
        switch dashboardStore.state.graph.selectedPeriod {
        case .week:
            return weekSectionViewModel.showCrosshair
        case .month:
            return monthSectionViewModel.showCrosshair
        case .year:
            return yearSectionViewModel.showCrosshair
        case .total:
            return totalSectionViewModel.showCrosshair
        }
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

    // Match skeleton frame to the actual chart container height (baby charts are taller)
    private var skeletonHeight: CGFloat {
        dashboardStore.selectedBabyProfile != nil ? 498 : 265
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
        .onChange(of: dashboardStore.state.graph.selectedPeriod) { _, newValue in
            // PERFORMANCE: Cancel any pending period change configuration
            periodChangeTask?.cancel()

            // MOB-518 V-A4: the new weight engine's host (`WeightChartHost`) reacts to the period change
            // itself (adopts the new anchor + rebuilds its model), so skip the entire legacy section-VM
            // machinery (clear ×4 / tearDown / configure / forceScrollPositionUpdate / updateYAxisCache).
            // Running it here for the new engine was pure wasted work on every switch — the #2 heaviness.
            guard !usesNewEngine else { return }

            // Note: The anchor-based scroll position is already calculated and set by
            // WeightTrendView.onChange(of: localSelectedPeriod) before this handler runs.
            // We only need to handle view model configuration and UI updates here.

            // MA-3837: do NOT clear the store selection here. updateSelectedPeriod auto-selects
            // the latest entry for the new period, and the freshly-mounted BaseGraphView syncs it
            // from the store; clearing the store would wipe that auto-selection. The per-section
            // VMs are still reset (inactive ones are torn down below; the active one is re-synced
            // from the store on mount).
            totalSectionViewModel.clearSelection()
            yearSectionViewModel.clearSelection()
            monthSectionViewModel.clearSelection()
            weekSectionViewModel.clearSelection()

            // Release caches for inactive period ViewModels immediately.
            let allViewModels: [BaseSectionViewModel] = [
                totalSectionViewModel, yearSectionViewModel,
                monthSectionViewModel, weekSectionViewModel
            ]
            for vm in allViewModels where vm.timePeriod != newValue {
                vm.tearDown()
            }

            // Configure the active view model synchronously so the graph
            // switches in the same frame as the segmented control indicator.
            switch newValue {
            case .week:
                weekSectionViewModel.configure(with: dashboardStore)
            case .month:
                monthSectionViewModel.configure(with: dashboardStore)
            case .year:
                yearSectionViewModel.configure(with: dashboardStore)
            case .total:
                totalSectionViewModel.configure(with: dashboardStore)
            }

            // Sync scroll position and recalculate Y-axis in the next run loop
            // to avoid blocking the current layout pass.
            periodChangeTask = Task { @MainActor in
                guard !Task.isCancelled else { return }

                let finalPosition = dashboardStore.state.graph.xScrollPosition
                switch newValue {
                case .week:
                    weekSectionViewModel.forceScrollPositionUpdate(to: finalPosition)
                case .month:
                    monthSectionViewModel.forceScrollPositionUpdate(to: finalPosition)
                case .year:
                    yearSectionViewModel.forceScrollPositionUpdate(to: finalPosition)
                case .total:
                    break // Total view is not scrollable
                }

                dashboardStore.chartManager.updateYAxisCache()
            }
        }
        // Tear down all section VM caches when product type or baby profile changes
        // to prevent stale data from the previous product appearing in the chart.
        .onChange(of: dashboardStore.productType) { _, _ in
            tearDownAllViewModels()
        }
        .onChange(of: dashboardStore.selectedProductItem) { _, _ in
            tearDownAllViewModels()
        }
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

    // MARK: - Cache Management

    private func tearDownAllViewModels() {
        totalSectionViewModel.tearDown()
        yearSectionViewModel.tearDown()
        monthSectionViewModel.tearDown()
        weekSectionViewModel.tearDown()
    }

    // MARK: - Chart View

    @ViewBuilder
    private var chartView: some View {
        if dashboardStore.isBabySelection && !dashboardStore.hasBabyEntries {
            // No real baby readings yet — show the empty grid instead of plotting the
            // dummy summaries that `continuousOperations` falls back to (matches design mock).
            BabyEmptyGraphView()
        } else if usesNewEngine {
            // MOB-518 v2 engine — the weight renderer (V6). Baby/BPM never reach here (they use the
            // legacy BaseGraphView engine in the else branch below).
            TrendChartHost(dashboardStore: dashboardStore)
        } else {
            HStack(spacing: 0) {
                switch dashboardStore.state.graph.selectedPeriod {
                case .week:
                    WeekGraphView(
                        viewModel: weekSectionViewModel,
                        dashboardStore: dashboardStore
                    )
                case .month:
                    MonthGraphView(
                        viewModel: monthSectionViewModel,
                        dashboardStore: dashboardStore
                    )
                case .year:
                    YearGraphView(
                        viewModel: yearSectionViewModel,
                        dashboardStore: dashboardStore
                    )
                case .total:
                    TotalGraphView(
                        viewModel: totalSectionViewModel,
                        dashboardStore: dashboardStore
                    )
                }
            }
        }
    }

}

#Preview {
    GraphView(dashboardStore: DashboardStore())
        .frame(height: 240)
        .padding()
}
