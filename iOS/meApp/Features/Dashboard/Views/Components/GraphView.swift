//
//  GraphView.swift
//  meApp
//
//  Created by Lakshmi Priya on 10/06/25.
//
//  Y-Axis Tick Animation Fix:
//  Uses selective animation strategy: animates chart data and domain changes
//  while keeping ticks stable to prevent jump animations.
//
//  X-Axis Height Fix:
//  Properly positions goal chip and chart elements accounting for X-axis height
//  in week/month/year periods while maintaining correct positioning for total period.
//

import SwiftUI
import Charts

struct GraphView: View {
    @ObservedObject var dashboardStore: DashboardStore
    @EnvironmentObject private var accountService: AccountService
    @Environment(\.appTheme) private var theme

    // Section view models
    @StateObject private var totalSectionViewModel = TotalSectionViewModel()
    @StateObject private var yearSectionViewModel = YearSectionViewModel()
    @StateObject private var monthSectionViewModel = MonthSectionViewModel()
    @StateObject private var weekSectionViewModel = WeekSectionViewModel()

    // Reset chart identity on period switches to avoid stale animations/state
    @State private var chartIdentity: UUID = UUID()

    // PERFORMANCE: Cancellable task for deferred period change configuration
    @State private var periodChangeTask: Task<Void, Never>?

    // Check if there are any entries to display
    private var hasEntries: Bool {
        return !dashboardStore.continuousOperations.isEmpty
    }

    // Get the appropriate empty state message
    private var emptyStateMessage: String {
        return DashboardStrings.noEntriesMessage
    }

    // Whether the selection callout is currently visible for the active period
    private var isShowingSelectionCallout: Bool {
        switch dashboardStore.graph.selectedPeriod {
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
        !dashboardStore.graph.isGraphReady
    }

    var body: some View {
        ZStack {
            // Skeleton loader shown only during initial graph load
            if shouldShowSkeleton {
                GraphSkeletonView()
            }

            // Actual graph content
            VStack(alignment: .leading) {
                // Preserve layout height: fade the label out instead of removing it to avoid jump
                Text(dashboardStore.weightLabel.lowercased())
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
                    // Hide immediately when the callout is shown (driven by the same VM flag)
                    .opacity(isShowingSelectionCallout ? 0 : 1)
                    .animation(.none, value: isShowingSelectionCallout)
                    .padding(.leading, .spacingSM)
                    .padding(.vertical, .spacingXS)
                chartView
                    .id(chartIdentity)
            }
            .opacity(shouldShowSkeleton ? 0 : 1)
        }
        .animation(.easeInOut(duration: 0.3), value: dashboardStore.graph.isGraphReady)
        .onChange(of: dashboardStore.graph.selectedPeriod) { _, newValue in
            // PERFORMANCE: Cancel any pending period change configuration
            periodChangeTask?.cancel()

            // Note: The anchor-based scroll position is already calculated and set by
            // WeightTrendView.onChange(of: localSelectedPeriod) before this handler runs.
            // We only need to handle view model configuration and UI updates here.

            // Immediate lightweight operations (cheap)
            totalSectionViewModel.clearSelection()
            yearSectionViewModel.clearSelection()
            monthSectionViewModel.clearSelection()
            weekSectionViewModel.clearSelection()

            // Force a fresh BaseGraphView mount for the new period. Without
            // this the old chart's last frame stays on-screen while the new
            // one is configuring (this is what users describe as "graphs
            // overlap each other" / "graph display changed").
            chartIdentity = UUID()

            // Configure the active view model synchronously. The prior code
            // deferred this behind a 50ms `Task.sleep` to "let the UI settle";
            // with the chartIdentity-driven remount above, the new view mounts
            // pre-configured and we no longer need the deferral.
            //
            // The previous `forceScrollPositionUpdate` "nudge" (temp + main.async
            // re-assign of `scrollPosition`) was removed: combined with
            // `PagedChartScrollBehavior`, the +0.001s nudge could snap to an
            // adjacent page on fresh mount, manifesting as "a random window"
            // appearing instead of the latest window after a tab switch.
            // `configure(with:)` already sets `scrollPosition` from
            // `store.graph.xScrollPosition`, and the chartIdentity-driven
            // remount makes the chart read this value on first mount.
            let activeViewModel: BaseSectionViewModel = {
                switch newValue {
                case .week:  return weekSectionViewModel
                case .month: return monthSectionViewModel
                case .year:  return yearSectionViewModel
                case .total: return totalSectionViewModel
                }
            }()
            activeViewModel.configure(with: dashboardStore)

            // Seed the active VM with the auto-selection that
            // `DashboardStore.updateSelectedPeriod` has already synchronously
            // written to `graph`. The helper bypasses the section-specific
            // `handleChartSelection` snap/range guards — those depend on
            // `xAxisValues` / `chartSeriesData` which may not yet be populated
            // on the very first frame after a tab switch, and silently
            // dropped the selection (most visibly on year). The
            // `BaseGraphView` re-mount's `syncViewModelSelectionFromStore`
            // funnels through the same helper so the read-from-store shape
            // can't drift between the two call sites.
            if let selection = dashboardStore.graph.validatedSelection {
                activeViewModel.applyStoreValidatedSelection(date: selection.date, point: selection.point)
            }

            // Recalculate and cache Y-axis based on the new visible region.
            dashboardStore.updateYAxisCache()
        }
        // P2-6: The store's `AccountSettingsSnapshot` subscription already
        // fans out `handleSettingsChange` for any `accountService.$activeAccount`
        // mutation (see DashboardStore.setupSubscriptions). The previous
        // `onReceive` here duplicated that call on every emission — login,
        // settings save, unit toggle, goal save — doing the streak refresh +
        // goal reload + Y-axis recalc + UI update twice. Removed.
    }

    // MARK: - Chart View
    private var chartView: some View {
        return HStack(spacing: 0) {
            // Use switch case for different time periods (total, year, month, week)
            switch dashboardStore.graph.selectedPeriod {
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

    // MARK: - Empty State View
    private var emptyStateView: some View {
        VStack(spacing: .spacingMD) {
            Spacer()

            Text(emptyStateMessage)
                .fontOpenSans(.heading5)
                .foregroundColor(theme.textHeading)
                .fontWeight(.bold)
                .multilineTextAlignment(.center)
                .padding(.horizontal, .spacingLG)

            Spacer()
        }
        .graphViewStyle(canAddPadding: true)
        .padding(.horizontal)
        .background(theme.textInverse)
    }
}

#Preview {
    GraphView(dashboardStore: DashboardStore())
        .frame(height: 240)
        .padding()
}
