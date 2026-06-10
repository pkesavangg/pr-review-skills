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

    /// Latest entry date in the active period — used to drive first-appear / initial-load auto-select.
    private var latestEntryDate: Date? {
        dashboardStore.continuousOperations.max(by: { $0.date < $1.date })?.date
    }

    // Whether the selection callout is currently visible for the active period
    private var isShowingSelectionCallout: Bool {
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
        !dashboardStore.state.graph.isGraphReady
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

    private var chartView: some View {
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

#Preview {
    GraphView(dashboardStore: DashboardStore())
        .frame(height: 240)
        .padding()
}
