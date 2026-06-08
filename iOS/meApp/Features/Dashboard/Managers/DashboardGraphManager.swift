// swiftlint:disable file_length
import Charts
import Foundation
import SwiftUI

/// Thin orchestrator for graph operations.
/// Routes work to `GraphDataPreparer`, `GraphRenderingConfiguration`,
/// `GraphInteractionHandler`, and `GraphAnimationManager`.
@MainActor
// swiftlint:disable:next type_body_length
class DashboardGraphManager: ObservableObject, DashboardGraphManaging {

    // MARK: - Dependencies
    @Injector private var logger: LoggerService

    // MARK: - State & Sub-components
    @Published var state: GraphState
    let dataPreparer  = GraphDataPreparer()
    let renderConfig  = GraphRenderingConfiguration()
    let interaction   = GraphInteractionHandler()
    let animationMgr  = GraphAnimationManager()
    let selectionPresentationResolver = GraphSelectionPresentationResolver()

    // MARK: - Chart Series Cache (keyed on scrolling state, so lives here)
    private var cachedChartSeries: [GraphSeries] = []
    private var lastCachedCount: Int = 0
    private var lastCachedDomain: ClosedRange<Double>?
    private var lastCachedMetric: String?

    // MARK: - Private State
    private var isChangingPeriod = false
    private var lastYAxisScale: YAxisScale?

    init(initialState: GraphState = GraphState()) { self.state = initialState }

    // MARK: - Scroll Handling

    func updateScrollPosition(to date: Date) { state.xScrollPosition = date }

    func handleScrollPositionChange(_ newPosition: Date?) {
        guard let pos = newPosition else { return }
        interaction.captureScrollPosition(pos)
    }

    func handleScrollStart() {
        state.scrollEndTimer?.invalidate()
        guard !state.isScrolling else { return }
        state.isScrolling = true
        state.clearSelection()
    }

    @available(iOS 18.0, *)
    func handleScrollPhaseChange(_ phase: ScrollPhase) async {
        switch phase {
        case .idle:
            // MA-3977: only clear the selection on `.idle` when the user actually scrolled
            // (`hasDetectedScrollInCurrentGesture`). After a tab/period switch, SwiftUI Charts
            // emits `.idle` as the chart re-mounts and settles into the programmatic scroll
            // position — clearing unconditionally wiped the auto-selection just applied by
            // updateSelectedPeriod, producing the "selection appears then disappears" symptom.
            let didUserScroll = state.hasDetectedScrollInCurrentGesture
            if let final = interaction.consumeBufferedScrollPosition() {
                state.xScrollPosition = state.selectedPeriod == .month
                    ? renderConfig.snapScrollPosition(final, for: .month)
                    : final
            }
            state.updateScrollState(isScrolling: false)
            state.hasDetectedScrollInCurrentGesture = false
            if didUserScroll {
                state.clearSelection()
            }
        case .tracking:
            state.hasDetectedScrollInCurrentGesture = false
        case .interacting:
            if !state.hasDetectedScrollInCurrentGesture {
                state.hasDetectedScrollInCurrentGesture = true
                state.updateScrollState(isScrolling: true)
                state.clearSelection()
            }
        case .decelerating, .animating:
            state.updateScrollState(isScrolling: true)
        @unknown default: break
        }
    }

    func handleScrollEnd() async {
        state.scrollEndTimer?.invalidate()
        state.scrollEndTimer = Timer.scheduledTimer(
            withTimeInterval: DashboardConstants.UIConstants.scrollEndDebounceDelay,
            repeats: false
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self else { return }
                if let final = self.interaction.consumeBufferedScrollPosition() {
                    self.state.xScrollPosition = self.state.selectedPeriod == .month
                        ? self.renderConfig.snapScrollPosition(final, for: .month)
                        : final
                }
                self.state.updateScrollState(isScrolling: false)
            }
        }
    }

    func handleScrollEndOptimized(
        updateWeightDisplay: @escaping () -> Void,
        recalculateYAxis: @escaping () -> Void,
        updateMetrics: @escaping () -> Void
    ) {
        state.scrollEndTimer?.invalidate()
        state.scrollEndTimer = Timer.scheduledTimer(withTimeInterval: DashboardConstants.UIConstants.scrollEndDebounceDelay, repeats: false) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.state.isScrolling = false
                self.state.hasDetectedScrollInCurrentGesture = false
                updateWeightDisplay(); recalculateYAxis(); updateMetrics()
            }
        }
    }

    func endScrollingImmediately() {
        state.scrollEndTimer?.invalidate()
        state.isScrolling = false
        state.hasDetectedScrollInCurrentGesture = false
        _ = interaction.consumeBufferedScrollPosition()
        clearChartSeriesCache()
    }

    // MARK: - Chart Selection

    func handleChartSelection(at selectedDate: Date?) async {
        guard !state.isScrolling else { return }
        guard let date = selectedDate else { state.clearSelection(); return }
        state.showCrosshair = false
        state.selectedXValue = date
    }

    /// MA-3837/MA-3977: synchronous selection apply used by `DashboardChartManager.updateSelectedPeriod`
    /// to seed the auto-selection on the latest entry BEFORE `state.selectedPeriod` publishes. Applying
    /// inline (rather than via an async tail) means the new BaseGraphView mounts with the store already
    /// holding the selection, so the on-mount `syncViewModelSelectionFromStore` lands the crosshair on
    /// first render instead of racing the chartIdentity remount and section-VM geometry guards.
    func applyChartSelectionSync(at selectedDate: Date, operations: [BathScaleWeightSummary]) {
        state.selectedXValue = selectedDate

        guard !operations.isEmpty else {
            state.selectedPoint = nil
            state.showCrosshair = false
            return
        }

        let calendar = Calendar.current
        let exactPoint: BathScaleWeightSummary? = {
            switch state.selectedPeriod {
            case .week, .month:
                return operations.first { calendar.isDate($0.date, inSameDayAs: selectedDate) }
            case .year, .total:
                return operations.first { calendar.isDate($0.date, equalTo: selectedDate, toGranularity: .month) }
            }
        }()

        state.selectedPoint = exactPoint
        state.showCrosshair = true
    }

    /// Refreshes the selected point in-place when its underlying entry was updated externally.
    /// Preserves crosshair visibility while updating values.
    func updateSelectedPoint(_ point: BathScaleWeightSummary) {
        state.selectedPoint = point
    }

    func handleCompleteChartSelection(
        at selectedDate: Date,
        operations: [BathScaleWeightSummary],
        updateMetrics: @escaping (BathScaleWeightSummary) async throws -> Void,
        resetMetrics: @escaping () -> Void,
        setMetricPlaceholders: @escaping () -> Void
    ) async {
        guard !state.isScrolling, !operations.isEmpty else { return }
        state.showCrosshair = false
        state.selectedXValue = selectedDate
        let cal = Calendar.current
        let exact: BathScaleWeightSummary? = {
            switch state.selectedPeriod {
            case .week, .month:  return operations.first { cal.isDate($0.date, inSameDayAs: selectedDate) }
            case .year, .total:  return operations.first { cal.isDate($0.date, equalTo: selectedDate, toGranularity: .month) }
            }
        }()
        if let exact {
            state.selectedXValue = exact.date
            state.selectedPoint = exact
            do { try await updateMetrics(exact) } catch {
                logger.log(level: .error, tag: "DashboardGraphManager", message: "updateMetrics failed: \(error)")
                resetMetrics()
            }
        } else {
            state.selectedPoint = nil
            setMetricPlaceholders()
        }
        state.showCrosshair = true
    }

    // MARK: - Period Management

    func updateSelectedPeriod(_ period: TimePeriod) {
        isChangingPeriod = true
        state.selectedPeriod = period
        state.clearSelection()
        clearChartSeriesCache()
        interaction.invalidateVisibleOpsCache()
        interaction.invalidateXAxisCache()
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 500_000_000)
            self.isChangingPeriod = false
        }
    }

    // MARK: - Chart Data Generation

    func generateChartData(
        from operations: [BathScaleWeightSummary],
        selectedMetric: String?,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Double) -> Double
    ) -> [GraphSeries] {
        dataPreparer.buildChartSeries(
            from: operations,
            selectedMetric: selectedMetric,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertWeight: convertWeight,
            yAxisDomain: nil,
            period: state.selectedPeriod
        )
    }

    /// Generates BPM chart data (3 series: systolic, diastolic, pulse).
    func generateBpmChartData(from operations: [BathScaleWeightSummary]) -> [GraphSeries] {
        dataPreparer.buildBpmChartSeries(from: operations, period: state.selectedPeriod)
    }

    // swiftlint:disable:next function_parameter_count
    func generateBabyChartData(
        from allOperations: [BathScaleWeightSummary],
        visibleOperations: [BathScaleWeightSummary],
        babyProfile: BabyProfile,
        metric: BabyMetric,
        convertWeight: @escaping (Double) -> Double,
        convertDecigramsToDisplay: @escaping (Int) -> Double,
        yAxisDomain: ClosedRange<Double>
    ) -> [GraphSeries] {
        switch metric {
        case .weight:
            let weightSeries = generateChartDataWithYAxisDomain(
                from: allOperations,
                visibleOperations: visibleOperations,
                selectedMetric: nil,
                isWeightlessMode: false,
                anchorWeight: nil,
                convertWeight: convertWeight,
                yAxisDomain: yAxisDomain
            )

            let percentileSeries = BabyDashboardChartSupport.percentileSeries(
                for: babyProfile,
                operations: allOperations,
                convertDecigramsToDisplay: convertDecigramsToDisplay
            )

            return weightSeries + percentileSeries
        case .height:
            let heightSeries = BabyDashboardChartSupport.dummyHeightSeries(
                for: babyProfile,
                operations: allOperations
            )
            let percentileSeries = BabyDashboardChartSupport.heightPercentileSeries(
                for: babyProfile,
                operations: allOperations
            )
            return heightSeries + percentileSeries
        }
    }

    // swiftlint:disable:next function_parameter_count
    func generateChartDataWithYAxisDomain(
        from allOperations: [BathScaleWeightSummary],
        visibleOperations: [BathScaleWeightSummary],
        selectedMetric: String?,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Double) -> Double,
        yAxisDomain: ClosedRange<Double>
    ) -> [GraphSeries] {
        guard !allOperations.isEmpty else { return [] }
        if state.isScrolling, canUseCachedChartSeries(count: allOperations.count, domain: yAxisDomain, metric: selectedMetric) {
            return cachedChartSeries
        }
        let opsForYAxis: [BathScaleWeightSummary]
        if state.selectedPeriod == .total {
            opsForYAxis = allOperations
        } else {
            let bracketing = getBracketingOperations(from: allOperations)
            let existing = Set(visibleOperations.map(\.entryTimestamp))
            var combined = visibleOperations
            for bracket in bracketing where !existing.contains(bracket.entryTimestamp) { combined.append(bracket) }
            opsForYAxis = combined.isEmpty ? allOperations : combined
        }
        let series = dataPreparer.buildChartSeries(
            from: allOperations,
            selectedMetric: selectedMetric,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertWeight: convertWeight,
            yAxisDomain: yAxisDomain,
            visibleOperations: visibleOperations,
            operationsForYAxis: opsForYAxis,
            period: state.selectedPeriod
        )
        cacheChartSeries(
            series,
            count: allOperations.count,
            domain: yAxisDomain,
            metric: selectedMetric
        )
        logger.log(
            level: .debug,
            tag: "DashboardGraphManager",
            message: "Generated chart: \(series.count) pts, metric: \(selectedMetric ?? "none")"
        )
        return series
    }

    // MARK: - Y-Axis

    // swiftlint:disable:next function_parameter_count
    func getYAxisScale(
        from operations: [BathScaleWeightSummary],
        goalWeight: Double?,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Double) -> Double,
        chartHeight: CGFloat
    ) -> YAxisScale {
        let scale = YAxisCalculator.calculateYAxis(
            operations: operations,
            goalWeight: goalWeight,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertStoredWeightToDisplay: convertWeight,
            chartHeight: chartHeight,
            lastScale: lastYAxisScale
        )
        lastYAxisScale = scale
        return scale
    }

    /// Calculates Y-axis for BPM data (systolic, diastolic, pulse — all in mmHg/bpm).
    func getBpmYAxisScale(
        from operations: [BathScaleWeightSummary],
        chartHeight: CGFloat
    ) -> YAxisScale {
        let scale = DashboardChartScaleProvider.bpmScale(from: operations)
        lastYAxisScale = scale
        return scale
    }

    // swiftlint:disable:next function_parameter_count
    func calculateAndCacheYAxisDomain(
        from operations: [BathScaleWeightSummary],
        goalWeight: Double?,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Double) -> Double,
        chartHeight: CGFloat
    ) {
        let scale = getYAxisScale(
            from: operations,
            goalWeight: goalWeight,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertWeight: convertWeight,
            chartHeight: chartHeight
        )
        guard state.cachedYAxisDomain != scale.domain || state.cachedYAxisTicks != scale.ticks else { return }
        state.cachedYAxisDomain = scale.domain
        state.cachedYAxisTicks = scale.ticks
    }

    // MARK: - Visible Operations

    func getVisibleOperations(from operations: [BathScaleWeightSummary]) -> [BathScaleWeightSummary] {
        interaction.visibleOperations(
            from: operations,
            scrollPosition: state.xScrollPosition,
            period: state.selectedPeriod,
            visibleDomainLength: visibleDomainLength(for: state.selectedPeriod),
            dataPreparer: dataPreparer
        )
    }

    func getStrictVisibleOperations(from operations: [BathScaleWeightSummary]) -> [BathScaleWeightSummary] {
        dataPreparer.strictlyVisibleOperations(
            from: operations,
            scrollPosition: state.xScrollPosition,
            visibleDomainLength: visibleDomainLength(for: state.selectedPeriod)
        )
    }

    func getBracketingOperations(from operations: [BathScaleWeightSummary]) -> [BathScaleWeightSummary] {
        dataPreparer.bracketingOperations(
            from: operations,
            scrollPosition: state.xScrollPosition,
            visibleDomainLength: visibleDomainLength(for: state.selectedPeriod)
        )
    }

    func forceVisibleOperationsRecalculation() { interaction.invalidateVisibleOpsCache() }

    func ensureLatestEntriesVisible(from operations: [BathScaleWeightSummary]) {
        guard operations.last?.date != nil, !state.isScrolling, !isChangingPeriod else { return }
        let bounds: (min: Date, max: Date)? = operations.first.flatMap { firstOp in
            operations.last.map { (min: firstOp.date, max: $0.date) }
        }
        updateScrollPosition(
            to: calculateOptimalScrollPosition(
                for: state.selectedPeriod,
                from: operations,
                cachedBounds: bounds
            )
        )
    }

    // MARK: - X-Axis & Scroll Math

    func generateVisibleXAxisValues(
        for period: TimePeriod,
        from operations: [BathScaleWeightSummary],
        scrollPosition: Date
    ) -> [Date] {
        interaction.xAxisValues(
            for: period,
            from: operations,
            scrollPosition: scrollPosition,
            renderConfig: renderConfig
        )
    }

    func calculateOptimalScrollPosition(
        for period: TimePeriod,
        from operations: [BathScaleWeightSummary],
        anchorDate: Date? = nil,
        showingLatest: Bool = true,
        cachedBounds: (min: Date, max: Date)? = nil
    ) -> Date {
        renderConfig.optimalScrollPosition(
            for: period,
            from: operations,
            anchorDate: anchorDate,
            showingLatest: showingLatest,
            cachedBounds: cachedBounds
        )
    }

    func clampScrollPosition(
        _ position: Date,
        for period: TimePeriod,
        minDate: Date,
        maxDate: Date
    ) -> Date {
        renderConfig.clampScrollPosition(position, for: period, minDate: minDate, maxDate: maxDate)
    }

    func snapScrollPosition(_ position: Date, for period: TimePeriod) -> Date {
        renderConfig.snapScrollPosition(position, for: period)
    }

    // MARK: - Interpolation & Stats

    func interpolatedDisplayWeight(
        at date: Date,
        from operations: [BathScaleWeightSummary],
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Double) -> Double
    ) -> Double? {
        dataPreparer.interpolatedDisplayWeight(
            at: date,
            from: operations,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertWeight: convertWeight,
            period: state.selectedPeriod
        )
    }

    func calculateInterpolatedAverageForVisibleRange(
        from allOperations: [BathScaleWeightSummary],
        period: TimePeriod,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Double) -> Double,
        labelRange: DateInterval? = nil
    ) -> Double? {
        dataPreparer.interpolatedAverageForVisibleRange(
            from: allOperations,
            period: period,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertWeight: convertWeight,
            labelRange: labelRange,
            sampleDates: generateSampleDatesForVisibleRange(for: period)
        )
    }

    func getCurrentAverageWeight(
        from operations: [BathScaleWeightSummary],
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Double) -> Double
    ) -> Double {
        dataPreparer.averageWeight(
            for: operations,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertWeight: convertWeight
        )
    }

    func calculateWeightlessDisplay(
        _ operations: [BathScaleWeightSummary],
        anchorWeight: Double?,
        period: TimePeriod,
        convertWeight: @escaping (Double) -> Double
    ) -> Double? {
        dataPreparer.weightlessDisplay(
            for: operations,
            anchorWeight: anchorWeight,
            period: period,
            convertWeight: convertWeight
        )
    }

    // swiftlint:disable:next function_parameter_count
    func resolveBabySelectionPresentation(
        babyProfile: BabyProfile?,
        metric: BabyMetric,
        selectedCrosshairDate: Date?,
        plottedPoints: [PlottedGraphSeries],
        plotXDate: (Date) -> Date,
        currentUnit: WeightUnit,
        displayWeight: Double?
    ) -> BabyGraphSelectionPresentation? {
        selectionPresentationResolver.babySelectionPresentation(
            babyProfile: babyProfile,
            metric: metric,
            selectedCrosshairDate: selectedCrosshairDate,
            plottedPoints: plottedPoints,
            plotXDate: plotXDate,
            currentUnit: currentUnit,
            displayWeight: displayWeight
        )
    }

    // MARK: - Metric Helpers

    func getMetricValue(for label: String, from summary: BathScaleWeightSummary) -> Double? {
        dataPreparer.metricValue(for: label, from: summary)
    }

    func canDisplayMetric(_ metricLabel: String, from operations: [BathScaleWeightSummary]) -> Bool {
        dataPreparer.canDisplay(metricLabel, in: operations)
    }

    func getAvailableMetrics(from operations: [BathScaleWeightSummary]) -> [String] {
        dataPreparer.availableMetrics(in: operations)
    }

    // MARK: - Formatting

    func formatXAxisLabel(for date: Date, period: TimePeriod, operations: [BathScaleWeightSummary]) -> String? {
        renderConfig.formatXAxisLabel(for: date, period: period, operations: operations)
    }

    func formatSelectedDate(_ date: Date, for period: TimePeriod) -> String {
        renderConfig.formatSelectedDate(date, for: period)
    }

    func formatDateRange(minDate: Date, maxDate: Date, for period: TimePeriod) -> String {
        renderConfig.formatDateRange(minDate: minDate, maxDate: maxDate, for: period)
    }

    func fallbackTimeLabel(for period: TimePeriod) -> String { renderConfig.fallbackTimeLabel(for: period) }

    // MARK: - Domain Length & Midpoints

    func visibleDomainLength(for period: TimePeriod) -> TimeInterval {
        visibleDomainLength(for: period, at: state.xScrollPosition)
    }

    func visibleDomainLength(for period: TimePeriod, at position: Date) -> TimeInterval {
        renderConfig.visibleDomainLength(for: period, at: position)
    }

    var currentVisibleMidpoint: Date {
        state.xScrollPosition.addingTimeInterval(visibleDomainLength(for: state.selectedPeriod) / 2)
    }

    func visibleMidpoint(for period: TimePeriod) -> Date {
        state.xScrollPosition.addingTimeInterval(visibleDomainLength(for: period) / 2)
    }

    func generateSampleDatesForVisibleRange(for period: TimePeriod) -> [Date] {
        renderConfig.sampleDates(for: period, scrollPosition: state.xScrollPosition)
    }

    // MARK: - Performance Helpers

    func getChartOperationsWithBuffer(
        from allOperations: [BathScaleWeightSummary],
        scrollPosition: Date,
        period: TimePeriod
    ) -> [BathScaleWeightSummary] {
        dataPreparer.windowedOperations(
            from: allOperations,
            scrollPosition: scrollPosition,
            period: period,
            visibleDomainLength: visibleDomainLength(for: period, at: scrollPosition)
        )
    }

    // MARK: - Trigger Helpers

    func updateWeightDisplayForCurrentView(triggerUpdate: @escaping () -> Void) { triggerUpdate() }

    func recalculateYAxisForVisibleData(triggerUpdate: @escaping () -> Void) {
        state.dataChangeTrigger += 1; triggerUpdate()
    }

    func updateMetricsForCurrentView(
        selectedPoint: BathScaleWeightSummary?,
        visibleOperations: [BathScaleWeightSummary],
        updateMetrics: @escaping (BathScaleWeightSummary) async throws -> Void,
        resetMetrics: @escaping () -> Void
    ) {
        if let point = selectedPoint { Task { try? await updateMetrics(point) } } else { resetMetrics() }
    }

    // MARK: - Public X-Axis Cache Access
    var lastXAxisValues: [Date] { interaction.lastXAxisValues }

    // MARK: - Private Chart Cache

    private func canUseCachedChartSeries(count: Int, domain: ClosedRange<Double>, metric: String?) -> Bool {
        guard !cachedChartSeries.isEmpty, count == lastCachedCount, metric == lastCachedMetric else { return false }
        if state.isScrolling { return true }
        guard let last = lastCachedDomain else { return false }
        let diff = abs(domain.lowerBound - last.lowerBound) + abs(domain.upperBound - last.upperBound)
        return diff <= max(domain.upperBound - domain.lowerBound, 0.1) * 0.10
    }

    private func cacheChartSeries(_ series: [GraphSeries], count: Int, domain: ClosedRange<Double>, metric: String?) {
        cachedChartSeries = series; lastCachedCount = count; lastCachedDomain = domain; lastCachedMetric = metric
    }

    private func clearChartSeriesCache() {
        cachedChartSeries = []; lastCachedCount = 0; lastCachedDomain = nil; lastCachedMetric = nil
        animationMgr.cancelChartDataThrottle()
    }
}
