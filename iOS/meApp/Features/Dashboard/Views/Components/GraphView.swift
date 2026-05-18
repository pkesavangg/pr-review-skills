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

            let finalPosition = dashboardStore.graph.xScrollPosition
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
