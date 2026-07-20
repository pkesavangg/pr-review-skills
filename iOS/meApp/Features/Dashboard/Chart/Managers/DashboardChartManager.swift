//
//  DashboardChartManager.swift
//  meApp
//
//  Chart initialization, scroll handling, Y-axis management, chart selection,
//  and chart-specific view updates.
//

import Charts
import Foundation
import SwiftUI

@MainActor
final class DashboardChartManager: DashboardChartManaging {

    // MARK: - Dependencies

    weak var stateProvider: DashboardStateProviding?

    private let graphManager: DashboardGraphManager
    private let metricsManager: DashboardMetricsManager
    private let goalManager: DashboardGoalManager
    private let dataManager: DashboardDataManager
    private let cacheManager: DashboardCacheManagerProtocol
    @Injector var accountService: AccountService
    @Injector var logger: LoggerService

    /// Closures for store-level computed properties
    let getContinuousOperations: () -> [BathScaleWeightSummary]
    /// MOB-1591: baby-aware operations for an ARBITRARY period. The period-switch scroll math below needs the
    /// NEW period's ops before `state.graph.selectedPeriod` flips, and for baby those come from the baby
    /// summaries — NOT `dataManager` (the weight cache, empty for a baby-only account, which made a section
    /// switch land on `Date()` → the wrong week). Weight/BPM resolve to the same `dataManager` source as before.
    let getContinuousOperationsForPeriod: (TimePeriod) -> [BathScaleWeightSummary]
    let getIsWeightlessModeEnabled: () -> Bool
    let getWeightlessAnchorWeight: () -> Double?
    let getGoalWeightForDisplay: () -> Double?

    /// Reference to display manager for metric updates after scroll
    var displayManager: DashboardDisplayManaging?

    // MARK: - Scroll State

    var lastUserScrollTime: Date?
    var isProcessingScrollEnd = false
    var scrollEndTask: Task<Void, Never>?

    /// Cancellable task for the delayed `isGraphReady = true` transition.
    /// Cancelled on every new `initializeChart()` call so a stale task from a
    /// previous product switch cannot briefly flash the old chart.
    private var graphReadyTask: Task<Void, Never>?

    // MARK: - Initialization

    init(
        stateProvider: DashboardStateProviding,
        graphManager: DashboardGraphManager,
        metricsManager: DashboardMetricsManager,
        goalManager: DashboardGoalManager,
        dataManager: DashboardDataManager,
        cacheManager: DashboardCacheManagerProtocol,
        getContinuousOperations: @escaping () -> [BathScaleWeightSummary],
        getContinuousOperationsForPeriod: @escaping (TimePeriod) -> [BathScaleWeightSummary],
        getIsWeightlessModeEnabled: @escaping () -> Bool,
        getWeightlessAnchorWeight: @escaping () -> Double?,
        getGoalWeightForDisplay: @escaping () -> Double?
    ) {
        self.stateProvider = stateProvider
        self.graphManager = graphManager
        self.metricsManager = metricsManager
        self.goalManager = goalManager
        self.dataManager = dataManager
        self.cacheManager = cacheManager
        self.getContinuousOperations = getContinuousOperations
        self.getContinuousOperationsForPeriod = getContinuousOperationsForPeriod
        self.getIsWeightlessModeEnabled = getIsWeightlessModeEnabled
        self.getWeightlessAnchorWeight = getWeightlessAnchorWeight
        self.getGoalWeightForDisplay = getGoalWeightForDisplay
        // Cache DI-backed services during construction so later container mutations
        // continue using the intended dependencies for this manager instance.
        _ = accountService
        _ = logger
    }

    // MARK: - Y-Axis

    var yAxisDomain: ClosedRange<Double> {
        if let cachedDomain = stateProvider?.state.graph.cachedYAxisDomain {
            return cachedDomain
        }
        return 0.0 ... 100.0
    }

    var yAxisTicks: [Double] {
        if let cachedTicks = stateProvider?.state.graph.cachedYAxisTicks {
            return cachedTicks
        }
        return [0.0, 25.0, 50.0, 75.0, 100.0]
    }

    func getYAxisScale() -> YAxisScale {
        let visibleOps = getVisibleOperations()
        let continuousOps = getContinuousOperations()
        let operationsForYAxis = stateProvider?.state.graph.selectedPeriod == .total ? continuousOps : visibleOps

        guard let stateProvider else {
            return graphManager.getYAxisScale(
                from: operationsForYAxis,
                goalWeight: getGoalWeightForDisplay(),
                isWeightlessMode: getIsWeightlessModeEnabled(),
                anchorWeight: getWeightlessAnchorWeight(),
                convertWeight: goalManager.convertWeightToDisplay,
                chartHeight: 200
            )
        }

        return stateProvider.yAxisScale(
            for: operationsForYAxis,
            chartHeight: stateProvider.state.graph.chartHeight
        )
    }

    func updateYAxisCache(force: Bool = false) {
        guard let stateProvider else { return }

        if stateProvider.state.graph.isScrolling, !force {
            return
        }

        let continuousOps = getContinuousOperations()
        var operationsForYAxis: [BathScaleWeightSummary]
        if stateProvider.state.graph.selectedPeriod == .total {
            operationsForYAxis = continuousOps
        } else {
            let visible = getVisibleOperations()
            let bracket = graphManager.getBracketingOperations(from: continuousOps)

            if visible.isEmpty {
                operationsForYAxis = bracket.isEmpty ? continuousOps : bracket
            } else {
                let visibleTimestamps = Set(visible.map { $0.entryTimestamp })
                var combinedOperations = visible
                for bracketOp in bracket where !visibleTimestamps.contains(bracketOp.entryTimestamp) {
                    combinedOperations.append(bracketOp)
                }
                operationsForYAxis = combinedOperations
            }
        }

        let previousYAxisDomain = graphManager.state.cachedYAxisDomain ?? stateProvider.state.graph.cachedYAxisDomain

        let resolvedScale = stateProvider.yAxisScale(
            for: operationsForYAxis,
            chartHeight: stateProvider.state.graph.chartHeight
        )
        graphManager.state.cachedYAxisDomain = resolvedScale.domain
        graphManager.state.cachedYAxisTicks = resolvedScale.ticks

        // Keep store state aligned with the graph manager immediately so cache invalidation
        // sees the freshly computed domain in the same update pass.
        stateProvider.state.graph = graphManager.state

        if let newYAxisDomain = graphManager.state.cachedYAxisDomain,
           let previousDomain = previousYAxisDomain,
           newYAxisDomain != previousDomain {
            cacheManager.invalidateChartSeriesCache()
            logger.log(
                level: .debug,
                tag: "DashboardChartManager",
                message: "Y-axis domain changed from \(previousDomain) to \(newYAxisDomain), invalidating cached chart series"
            )
        }

        stateProvider.scheduleUIUpdate()
    }

    // MARK: - Chart Initialization

    @MainActor
    func initializeChart() {
        guard let stateProvider else { return }

        guard !stateProvider.state.ui.hasInitializedChart, !stateProvider.state.graph.isScrolling else {
            updateWeightDisplayForCurrentView()
            if stateProvider.state.ui.hasInitializedChart, !graphManager.state.isGraphReady {
                graphManager.state.isGraphReady = true
            }
            return
        }

        let continuousOps = getContinuousOperations()
        let optimalScrollPosition = graphManager.calculateOptimalScrollPosition(
            for: stateProvider.state.graph.selectedPeriod,
            from: continuousOps,
            anchorDate: nil,
            showingLatest: true,
            cachedBounds: nil
        )

        let period = stateProvider.state.graph.selectedPeriod
        let shouldSnapProgrammaticPosition = period != .total
        let alignedScrollPosition = shouldSnapProgrammaticPosition
            ? graphManager.snapScrollPosition(optimalScrollPosition, for: period)
            : optimalScrollPosition

        graphManager.updateScrollPosition(to: alignedScrollPosition)

        forceCompleteRecalculationAfterScrollPosition()

        stateProvider.state.ui.hasInitializedChart = true

        graphReadyTask?.cancel()
        graphReadyTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            graphManager.state.isGraphReady = true
        }
    }

    // MARK: - Scroll Handling

    @MainActor
    func handleScrollPositionChange(_ newPosition: Date?) {
        graphManager.handleScrollPositionChange(newPosition)
    }

    @MainActor
    func handleScrollStart() {
        lastUserScrollTime = Date()
        graphManager.handleScrollStart()
    }

    @MainActor
    func handleScrollEndOptimized() {
        guard !isProcessingScrollEnd else { return }
        isProcessingScrollEnd = true

        scrollEndTask?.cancel()

        Task {
            await graphManager.handleScrollEnd()
        }

        scrollEndTask = Task { @MainActor [weak self] in
            guard let self = self else { return }

            try? await Task.sleep(nanoseconds: 100_000_000)
            guard !Task.isCancelled, self.isProcessingScrollEnd else { return }

            try? await Task.sleep(nanoseconds: 200_000_000)
            guard !Task.isCancelled, self.isProcessingScrollEnd else { return }
            self.updateYAxisCache()

            try? await Task.sleep(nanoseconds: 200_000_000)
            guard !Task.isCancelled, self.isProcessingScrollEnd else { return }
            self.updateWeightDisplayForCurrentView()

            try? await Task.sleep(nanoseconds: 200_000_000)
            guard !Task.isCancelled, self.isProcessingScrollEnd else { return }
            self.displayManager?.updateMetricsForCurrentView()

            let count = self.getVisibleOperations().count
            logger.log(
                level: .debug,
                tag: "DashboardChartManager",
                message: "Scroll end summary - period=\(self.stateProvider?.state.graph.selectedPeriod ?? .week), visibleOps=\(count)"
            )

            self.isProcessingScrollEnd = false
        }
    }

    func clearAllCaches() {
        scrollEndTask?.cancel()
        scrollEndTask = nil
        graphReadyTask?.cancel()
        graphReadyTask = nil
        cacheManager.clearAllCaches()
        isProcessingScrollEnd = false
    }

    func handleScrollPhaseChange(to phase: ScrollPhase) async {
        await graphManager.handleScrollPhaseChange(phase)

        if phase == .idle {
            Task { @MainActor [weak self] in
                try? await Task.sleep(nanoseconds: 50_000_000)
                guard let self = self else { return }

                self.updateYAxisCache()
                self.updateWeightDisplayForCurrentView()
                self.displayManager?.updateMetricsForCurrentView()
            }
        }
    }

    // MARK: - Chart Selection & Period

    func handleChartSelection(at selectedDate: Date?) async {
        let continuousOps = getContinuousOperations()
        let selectionOps: [BathScaleWeightSummary]
        if stateProvider?.productType == .bpm {
            let period = stateProvider?.state.graph.selectedPeriod ?? .week
            selectionOps = graphManager.dataPreparer.aggregatedBpmOperationsForPeriod(
                from: continuousOps,
                period: period
            )
        } else {
            selectionOps = continuousOps
        }

        guard let selectedDate = selectedDate else {
            clearSelection()
            return
        }

        await graphManager.handleCompleteChartSelection(
            at: selectedDate,
            operations: selectionOps,
            updateMetrics: { selectedPoint in
                // Update BPM AHA classification on point selection
                self.displayManager?.handleBpmPointSelection(selectedPoint)
                try await self.metricsManager.updateMetrics(with: selectedPoint)
            },
            resetMetrics: { [weak self] in
                self?.displayManager?.resetMetricsToLatestEntry()
            },
            setMetricPlaceholders: { [weak self] in
                self?.metricsManager.setPlaceholdersForAllMetrics()
            }
        )

        await MainActor.run {
            self.stateProvider?.scheduleUIUpdate()
        }
    }

    @MainActor
    func clearSelection() {
        Task {
            await graphManager.handleChartSelection(at: nil)
            self.displayManager?.updateMetricsForCurrentView()
        }
    }

    /// MA-3837: cold-start / first-appear auto-selection of the latest entry for the current
    /// period, without the full period-switch reset. No-op if a selection already exists, so it
    /// won't override a user's manual selection. The freshly-mounted BaseGraphView syncs the
    /// resulting store selection onto the active section view model.
    @MainActor
    func selectLatestEntryIfNeeded() {
        guard let stateProvider else { return }
        guard stateProvider.state.graph.validatedSelection == nil else { return }
        let period = stateProvider.state.graph.selectedPeriod
        // MOB-1591: baby-aware source (see `getContinuousOperationsForPeriod`) — `dataManager` is empty for a
        // baby-only account, so the auto-select would find no latest entry (or the wrong one).
        let operations = getContinuousOperationsForPeriod(period)
        guard let latestDate = operations.max(by: { $0.date < $1.date })?.date else { return }
        graphManager.applyChartSelectionSync(at: latestDate, operations: operations)
        stateProvider.state.ui.hasLandedInitialSelection = true
        displayManager?.updateMetricsForCurrentView()
        stateProvider.scheduleUIUpdate()
    }

    func updateSelectedPeriod(_ period: TimePeriod, anchorDate: Date? = nil) {
        guard let stateProvider else { return }

        stateProvider.state.ui.hasInitializedChart = false

        clearAllCaches()

        graphManager.endScrollingImmediately()

        // MOB-1591: baby-aware ops for the NEW period (see `getContinuousOperationsForPeriod`). For baby the
        // `dataManager` weight cache is empty, so the previous `dataManager.getContinuousOperations` made
        // `calculateOptimalScrollPosition` fall back to `Date()` (today) → the section switch landed on the
        // wrong week instead of the week containing the latest entry (weight/BPM were unaffected). Passing the
        // real baby summaries + `nil` bounds lets `optimalScrollPosition` derive bounds from them, exactly as
        // weight does. `getDateBounds` is already `nil` for baby, so weight/BPM keep their cached bounds.
        let operationsForNewPeriod = getContinuousOperationsForPeriod(period)

        let optimalScrollPosition = graphManager.calculateOptimalScrollPosition(
            for: period,
            from: operationsForNewPeriod,
            anchorDate: anchorDate,
            showingLatest: anchorDate == nil,
            cachedBounds: dataManager.getDateBounds(for: period)
        )

        let requiresSnapWithAnchor = (period == .week || period == .month || period == .year)
        let shouldSnapProgrammaticPosition = period != .total && (anchorDate == nil || requiresSnapWithAnchor)
        let alignedScrollPosition = shouldSnapProgrammaticPosition
            ? graphManager.snapScrollPosition(optimalScrollPosition, for: period)
            : optimalScrollPosition

        graphManager.updateScrollPosition(to: alignedScrollPosition)
        graphManager.updateSelectedPeriod(period)

        forceCompleteRecalculationAfterScrollPosition()

        stateProvider.state.ui.hasInitializedChart = true

        // Ensure the graph-ready flag is restored after the period switch.
        // clearAllCaches() above cancels the in-flight graphReadyTask, so if
        // the user switches period while the initial skeleton is still showing
        // (isGraphReady == false), the flag would stay false forever.
        if !graphManager.state.isGraphReady {
            graphReadyTask?.cancel()
            graphReadyTask = Task { @MainActor in
                try? await Task.sleep(nanoseconds: 300_000_000)
                guard !Task.isCancelled else { return }
                graphManager.state.isGraphReady = true
            }
        }

        // MA-3837: reset the chart to the latest entry on a period/tab switch and auto-select
        // it so the header tile shows its value/date immediately. Apply synchronously so the
        // store carries the new selection (selectedXValue, selectedPoint, showCrosshair) BEFORE
        // selectedPeriod is observed by the view layer — the new BaseGraphView mount's
        // sync-from-store path then lands the crosshair on first render rather than fighting the
        // chartIdentity remount + section-VM geometry guards from an async tail.
        if let latestDateForNewPeriod = operationsForNewPeriod.max(by: { $0.date < $1.date })?.date {
            graphManager.applyChartSelectionSync(at: latestDateForNewPeriod, operations: operationsForNewPeriod)
            stateProvider.state.ui.hasLandedInitialSelection = true
        }
        displayManager?.updateMetricsForCurrentView()
    }

    // MARK: - View Updates

    @MainActor
    func updateWeightDisplayForCurrentView() {
        stateProvider?.scheduleUIUpdate()
    }

    @MainActor
    func forceCompleteRecalculationAfterScrollPosition() {
        graphManager.forceVisibleOperationsRecalculation()
        updateYAxisCache()
        updateWeightDisplayForCurrentView()
        stateProvider?.scheduleUIUpdate()
    }

    // MARK: - Delegations

    func xAxisValuesWithBuffer(for period: TimePeriod) -> [Date] {
        let continuousOps = getContinuousOperations()
        guard let scrollPosition = stateProvider?.state.graph.xScrollPosition else { return [] }
        return graphManager.generateVisibleXAxisValues(for: period, from: continuousOps, scrollPosition: scrollPosition)
    }

    func xLabelString(for date: Date, period: TimePeriod) -> String? {
        let continuousOps = getContinuousOperations()
        return graphManager.formatXAxisLabel(for: date, period: period, operations: continuousOps)
    }

    func selectEntry(_ entry: BathScaleWeightSummary?) {
        metricsManager.selectEntry(entry, convertWeight: goalManager.convertWeightToDisplay) {
            self.stateProvider?.forceImmediateUIUpdate()
        }
    }

    func ensureLatestEntriesVisible() {
        let recentlyScrolled = lastUserScrollTime.map { Date().timeIntervalSince($0) < 2.0 } ?? false
        guard !recentlyScrolled else { return }
        let continuousOps = getContinuousOperations()
        graphManager.ensureLatestEntriesVisible(from: continuousOps)
    }

    func getVisibleOperations() -> [BathScaleWeightSummary] {
        let continuousOps = getContinuousOperations()
        return cacheManager.getVisibleOperations(
            isScrolling: stateProvider?.state.graph.isScrolling ?? false
        ) {
            graphManager.getVisibleOperations(from: continuousOps)
        }
    }
}
