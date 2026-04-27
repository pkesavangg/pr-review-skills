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

    // Tracks whether the first-appear auto-selection has already run, so navigating
    // away and back doesn't override a user's intentional manual clear.
    @State private var didInitialSelect = false

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

    // Latest entry date in the active period, used to detect when a newly added
    // entry advances the latest beyond what the user currently has selected.
    private var latestEntryDate: Date? {
        dashboardStore.continuousOperations.max(by: { $0.date < $1.date })?.date
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
        .animation(.easeInOut(duration: 0.3), value: dashboardStore.state.graph.isGraphReady)
        .onChange(of: dashboardStore.state.graph.selectedPeriod) { _, newValue in
            // PERFORMANCE: Cancel any pending period change configuration
            periodChangeTask?.cancel()

            // Note: The anchor-based scroll position is already calculated and set by
            // WeightTrendView.onChange(of: localSelectedPeriod) before this handler runs.
            // We only need to handle view model configuration and UI updates here.

            // Immediate lightweight operations (cheap)
            dashboardStore.clearSelection()
            totalSectionViewModel.clearSelection()
            yearSectionViewModel.clearSelection()
            monthSectionViewModel.clearSelection()
            weekSectionViewModel.clearSelection()

            // PERFORMANCE: Defer heavy configuration to prevent CPU spike
            // Only configure the active ViewModel after a brief delay
            periodChangeTask = Task { @MainActor in
                // Brief delay to let the UI settle
                try? await Task.sleep(nanoseconds: 50_000_000) // 50ms
                guard !Task.isCancelled else { return }

                // Configure only the active view model (not all 4)
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

                // Force the active view model to sync with the scroll position set by WeightTrendView
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

                // Recalculate and cache Y-axis based on the new visible region
                dashboardStore.updateYAxisCache()

                // Auto-select the latest entry so the crosshair/callout appears
                // on the active section chart after the tab switch. The store-level
                // selection is driven by DashboardStore.updateSelectedPeriod; here
                // we mirror it onto the active view model's crosshair state.
                // Normalize via plotXDate first so section snap logic (e.g. week)
                // lands exactly on the latest entry's day tick regardless of the
                // original entry timestamp's time-of-day.
                guard !Task.isCancelled else { return }
                if let latestDate = dashboardStore.continuousOperations.max(by: { $0.date < $1.date })?.date {
                    switch newValue {
                    case .week:
                        weekSectionViewModel.handleChartSelection(at: weekSectionViewModel.plotXDate(for: latestDate))
                    case .month:
                        monthSectionViewModel.handleChartSelection(at: monthSectionViewModel.plotXDate(for: latestDate))
                    case .year:
                        yearSectionViewModel.handleChartSelection(at: yearSectionViewModel.plotXDate(for: latestDate))
                    case .total:
                        totalSectionViewModel.handleChartSelection(at: totalSectionViewModel.plotXDate(for: latestDate))
                    }
                }
            }
        }
        // First-appear auto-select: when GraphView mounts and entries already exist but
        // nothing is selected yet, select the latest entry so the crosshair/callout shows
        // the most recent point. Guarded by didInitialSelect so re-appearing the screen
        // (tab navigation) does not override a manual clear.
        .onAppear { performInitialSelectIfNeeded() }
        // Operations changed (add/delete/edit). Three triggers feed into selection sync:
        // (1) latestEntryDate transitions covering first data load, advance on new entry,
        //     and latest-receded; (2) count change covering deletion of a selected
        //     non-latest entry where the latest itself doesn't move.
        .onChange(of: latestEntryDate) { oldLatest, newLatest in
            handleLatestEntryDateChange(oldLatest: oldLatest, newLatest: newLatest)
        }
        .onChange(of: dashboardStore.continuousOperations.count) { _, _ in
            fallBackToLatestIfSelectionInvalid()
        }
        // Immediately react to active account goal updates like GoalProgressView
        .onReceive(accountService.$activeAccount) { _ in
            dashboardStore.handleSettingsChange()
        }
    }

    // MARK: - Auto Selection

    private func performInitialSelectIfNeeded() {
        guard !didInitialSelect else { return }
        guard let latest = latestEntryDate else { return } // No data yet — wait for onChange.
        didInitialSelect = true
        // Defer briefly so BaseGraphView.onAppear has wired the section view model to the
        // store before we ask it to select; otherwise chartOperations is empty and the
        // selectedPoint resolves to nil.
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 100_000_000) // 100ms
            guard !activeSectionHasSelection() else { return }
            applyLatestSelection(latest)
        }
    }

    private func handleLatestEntryDateChange(oldLatest: Date?, newLatest: Date?) {
        guard let newLatest else { return } // All entries deleted; nothing to select.

        // Initial data load while view is alive (was nil, now have data).
        if oldLatest == nil {
            didInitialSelect = true
            applyLatestSelection(newLatest)
            return
        }

        // Latest advanced (new entry past previous latest): follow it if the user was on
        // the previous-latest. Manual selections on older points are respected.
        if let oldLatest, newLatest > oldLatest, activeSectionSelectionMatches(oldLatest) {
            applyLatestSelection(newLatest)
            return
        }

        // Latest receded (the latest entry was deleted) and the user's selection no
        // longer corresponds to any operation — fall back to the new latest.
        if !activeSectionSelectionExistsInOperations() {
            applyLatestSelection(newLatest)
        }
    }

    private func fallBackToLatestIfSelectionInvalid() {
        // Catches deletion of a selected non-latest entry, where latestEntryDate is unchanged
        // but the selected point is gone from operations.
        guard let newLatest = latestEntryDate else { return }
        guard activeSectionHasSelection() else { return }
        if !activeSectionSelectionExistsInOperations() {
            applyLatestSelection(newLatest)
        }
    }

    private func applyLatestSelection(_ date: Date) {
        selectOnActiveSectionViewModel(date)
        Task { await dashboardStore.handleChartSelection(at: date) }
    }

    // Returns true when the active section view model's crosshair is on a data
    // point matching `date` at the period's granularity (day for week/month,
    // month for year/total).
    private func activeSectionSelectionMatches(_ date: Date) -> Bool {
        let (showCrosshair, pointDate): (Bool, Date?) = {
            switch dashboardStore.state.graph.selectedPeriod {
            case .week:
                return (weekSectionViewModel.showCrosshair, weekSectionViewModel.selectedPoint?.date)
            case .month:
                return (monthSectionViewModel.showCrosshair, monthSectionViewModel.selectedPoint?.date)
            case .year:
                return (yearSectionViewModel.showCrosshair, yearSectionViewModel.selectedPoint?.date)
            case .total:
                return (totalSectionViewModel.showCrosshair, totalSectionViewModel.selectedPoint?.date)
            }
        }()
        guard showCrosshair, let pointDate else { return false }

        let calendar = Calendar.current
        switch dashboardStore.state.graph.selectedPeriod {
        case .week, .month:
            return calendar.isDate(pointDate, inSameDayAs: date)
        case .year, .total:
            return calendar.isDate(pointDate, equalTo: date, toGranularity: .month)
        }
    }

    private func selectOnActiveSectionViewModel(_ date: Date) {
        switch dashboardStore.state.graph.selectedPeriod {
        case .week:
            weekSectionViewModel.handleChartSelection(at: weekSectionViewModel.plotXDate(for: date))
        case .month:
            monthSectionViewModel.handleChartSelection(at: monthSectionViewModel.plotXDate(for: date))
        case .year:
            yearSectionViewModel.handleChartSelection(at: yearSectionViewModel.plotXDate(for: date))
        case .total:
            totalSectionViewModel.handleChartSelection(at: totalSectionViewModel.plotXDate(for: date))
        }
    }

    // True when the active section view model currently shows a crosshair on a real point.
    private func activeSectionHasSelection() -> Bool {
        switch dashboardStore.state.graph.selectedPeriod {
        case .week:
            return weekSectionViewModel.showCrosshair && weekSectionViewModel.selectedPoint != nil
        case .month:
            return monthSectionViewModel.showCrosshair && monthSectionViewModel.selectedPoint != nil
        case .year:
            return yearSectionViewModel.showCrosshair && yearSectionViewModel.selectedPoint != nil
        case .total:
            return totalSectionViewModel.showCrosshair && totalSectionViewModel.selectedPoint != nil
        }
    }

    // True when the active section's selectedPoint still corresponds to an entry in
    // continuousOperations (matched at the period's display granularity).
    private func activeSectionSelectionExistsInOperations() -> Bool {
        let selectedDate: Date? = {
            switch dashboardStore.state.graph.selectedPeriod {
            case .week:  return weekSectionViewModel.selectedPoint?.date
            case .month: return monthSectionViewModel.selectedPoint?.date
            case .year:  return yearSectionViewModel.selectedPoint?.date
            case .total: return totalSectionViewModel.selectedPoint?.date
            }
        }()
        guard let selectedDate else { return false }
        let calendar = Calendar.current
        let granularity: Calendar.Component
        switch dashboardStore.state.graph.selectedPeriod {
        case .week, .month: granularity = .day
        case .year, .total: granularity = .month
        }
        return dashboardStore.continuousOperations.contains { op in
            calendar.isDate(op.date, equalTo: selectedDate, toGranularity: granularity)
        }
    }

    // MARK: - Chart View
    private var chartView: some View {
        return HStack(spacing: 0) {
            // Use switch case for different time periods (total, year, month, week)
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
