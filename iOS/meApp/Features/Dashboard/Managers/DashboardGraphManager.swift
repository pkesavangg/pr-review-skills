import SwiftUI
import Charts
import os
import Foundation

/// Manages all graph and chart operations for the dashboard
@MainActor
class DashboardGraphManager: ObservableObject, DashboardGraphManaging {

    // MARK: - Dependencies
    @Injector private var logger: LoggerService

    // MARK: - Published Properties
    @Published var state: GraphState

    // MARK: - Private Properties
    private let calendar = Calendar.current

    private var lastCalculatedVisibleOps: [BathScaleWeightSummary] = []
    private var lastVisibleOpsScrollPosition: Date?
    private var lastVisibleOpsPeriod: TimePeriod?
    private var lastChartData: [GraphSeries] = []
    private var lastChartDataWeightRange: ClosedRange<Double>?
    private var lastChartDataSelectedMetric: String?

    // Store scroll position during scroll, update state only at end
    private var latestScrollPosition: Date?
    private var lastYAxisScale: YAxisScale?
    public var lastXAxisValues: [Date] = []
    private var lastXAxisScrollPosition: Date?
    private var lastXAxisPeriod: TimePeriod?

    init(initialState: GraphState = GraphState()) {
        self.state = initialState
    }

    func updateScrollPosition(to date: Date) {
        guard !state.isScrolling else {
            return
        }
        state.xScrollPosition = date
    }

    func handleScrollPositionChange(_ newPosition: Date?) {
        guard let newPosition = newPosition else { return }
        if state.isScrolling {
            latestScrollPosition = newPosition
        } else {
            latestScrollPosition = nil
        }
    }

    func handleScrollStart() {
        state.scrollEndTimer?.invalidate()
        if !state.isScrolling {
            state.isScrolling = true
            state.clearSelection()
        }
    }

    func handleChartSelection(at selectedDate: Date?) async {
        // Only handle selection if not currently scrolling
        guard !state.isScrolling else { return }

        // If no date selected, clear selection
        guard let selectedDate = selectedDate else {
            state.clearSelection()
            logger.log(level: .info, tag: "DashboardGraphManager", message: "Chart selection cleared")
            return
        }

        // Hide any existing crosshair first
        state.showCrosshair = false
        state.selectedXValue = selectedDate

        logger.log(level: .info, tag: "DashboardGraphManager", message: "Chart selection handled at date: \(selectedDate)")
    }

    /// Handles complete chart selection including finding closest point and updating metrics
    /// This method should be called from the DashboardStore with the necessary dependencies
    func handleCompleteChartSelection(at selectedDate: Date,
                                     operations: [BathScaleWeightSummary],
                                     updateMetrics: @escaping (BathScaleWeightSummary) async throws -> Void,
                                     resetMetrics: @escaping () -> Void) async {
        // Only handle selection if not currently scrolling
        guard !state.isScrolling else { return }

        // Hide any existing crosshair first
        state.showCrosshair = false

        guard !operations.isEmpty else { return }

        // Find the closest data point to the selected date
        let selectedBin = operations.min { bin1, bin2 in
            abs(bin1.date.timeIntervalSince(selectedDate)) < abs(bin2.date.timeIntervalSince(selectedDate))
        }

        guard let selectedBin = selectedBin else { return }

        // Set the selected point and show crosshair
        updateSelectedPoint(selectedBin)

        // Update metrics with the selected point's values
        do {
            try await updateMetrics(selectedBin)
            logger.log(level: .info, tag: "DashboardGraphManager", message: "Updated metrics with selected point: \(selectedBin.date)")
        } catch {
            logger.log(level: .error, tag: "DashboardGraphManager", message: "Failed to update metrics: \(error)")
            resetMetrics()
        }
    }

    func findClosestPoint(to selectedDate: Date, in operations: [BathScaleWeightSummary]) -> BathScaleWeightSummary? {
        guard !operations.isEmpty else { return nil }
        return operations.min { point1, point2 in
            let distance1 = abs(point1.date.timeIntervalSince(selectedDate))
            let distance2 = abs(point2.date.timeIntervalSince(selectedDate))
            return distance1 < distance2
        }
    }

    func updateSelectedPoint(_ point: BathScaleWeightSummary?) {
        state.selectedPoint = point
        state.showCrosshair = point != nil
        if let point = point {
            logger.log(level: .info, tag: "DashboardGraphManager", message: "Selected point updated: \(point.date) with weight: \(point.weight)")
        } else {
            logger.log(level: .info, tag: "DashboardGraphManager", message: "Selected point cleared")
        }
    }

    @available(iOS 18.0, *)
    func handleScrollPhaseChange(_ phase: ScrollPhase) async {
        switch phase {
        case .idle:
            state.isScrolling = false
            state.hasDetectedScrollInCurrentGesture = false

            // Clear selection state for better UX
            state.clearSelection()

             if let finalPosition = self.latestScrollPosition {
                    self.state.xScrollPosition = finalPosition
                    self.logger.log(level: .debug, tag: "DashboardGraphManager", message: "Updated scroll position at end: \(finalPosition)")
                    self.latestScrollPosition = nil
            }
            state.updateScrollState(isScrolling: false)
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
        @unknown default:
            logger.log(level: .info, tag: "DashboardGraphManager", message: "Unknown scroll phase encountered")
        }
    }

    func handleScrollEnd() async {
        state.scrollEndTimer?.invalidate()
        state.scrollEndTimer = Timer.scheduledTimer(withTimeInterval: DashboardConstants.UI.scrollEndDebounceDelay, repeats: false) { [weak self] _ in
            Task { @MainActor in
                guard let self = self else { return }

                // Update scroll position from stored value first
                if let finalPosition = self.latestScrollPosition {
                    self.state.xScrollPosition = finalPosition
                    self.latestScrollPosition = nil
                }
                self.state.updateScrollState(isScrolling: false)

                self.logger.log(level: .debug, tag: "DashboardGraphManager", message: "Scroll ended - all caches cleared for fresh calculation")
            }
        }
    }

    func generateChartData(from operations: [BathScaleWeightSummary], selectedMetric: String?, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double) -> [GraphSeries] {
        guard !operations.isEmpty else {
            logger.log(level: .info, tag: "DashboardGraphManager", message: "No operations available for chart data generation")
            return []
        }

        // Get weight values for current normalization check
        let weightValues = operations.map { summary -> Double in
            if isWeightlessMode {
                guard let anchorWeight = anchorWeight else { return 0 }
                let currentWeight = convertWeight(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return convertWeight(Int(summary.weight))
            }
        }

        // Calculate current weight range
        guard let weightMin = weightValues.min(),
              let weightMax = weightValues.max(),
              weightMax > weightMin else {
            logger.log(level: .info, tag: "DashboardGraphManager", message: "Invalid weight range for chart data")
            return []
        }

        let currentWeightRange = weightMin...weightMax

        // Check if we can use cached data during scrolling
        let canUseCachedData = state.isScrolling &&
                              !lastChartData.isEmpty &&
                              shouldUseCachedData(
                                currentWeightRange: currentWeightRange,
                                currentSelectedMetric: selectedMetric
                              )

        if canUseCachedData {
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "Using cached chart data during scroll")
            return lastChartData
        }

        var series: [GraphSeries] = []

        // Use the already calculated weight range
        let weightRange = currentWeightRange

        // Add weight series (always present)
        for summary in operations {
            let displayWeight: Double
            if isWeightlessMode {
                guard let anchorWeight = anchorWeight else { continue }
                let currentWeight = convertWeight(Int(summary.weight))
                displayWeight = currentWeight - anchorWeight
            } else {
                displayWeight = convertWeight(Int(summary.weight))
            }
            series.append(GraphSeries(
                date: summary.date,
                value: displayWeight,
                series: DashboardStrings.weight
            ))
        }
        if let selectedMetric = selectedMetric, selectedMetric != DashboardStrings.weight {
            // Use dynamic normalization similar to the demo project
            let normalizedMetricSeries = generateNormalizedMetricSeries(
                for: selectedMetric,
                from: operations,
                toWeightRange: weightRange
            )
            series.append(contentsOf: normalizedMetricSeries)
        }

        // Store the generated data and context for next call
        lastChartData = series
        lastChartDataWeightRange = currentWeightRange
        lastChartDataSelectedMetric = selectedMetric

        logger.log(level: .info, tag: "DashboardGraphManager", message: "Generated fresh chart data: \(series.count) points, weightRange: \(currentWeightRange), selectedMetric: \(selectedMetric ?? "none")")
        return series
    }


    // MARK: - Chart Data Generation with Y-Axis Domain Consistency

    /// Generates chart data using the provided Y-axis domain for consistent metric normalization
    /// This ensures metric lines stay within the visible Y-axis range when scrolling
    func generateChartDataWithYAxisDomain(
        from allOperations: [BathScaleWeightSummary],
        visibleOperations: [BathScaleWeightSummary],
        selectedMetric: String?,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Int) -> Double,
        yAxisDomain: ClosedRange<Double>
    ) -> [GraphSeries] {

        guard !allOperations.isEmpty else {
            logger.log(level: .info, tag: "DashboardGraphManager", message: "No operations available for chart data generation")
            return []
        }

        var series: [GraphSeries] = []

        // Add weight series (always present) from all operations to show continuous line
        for summary in allOperations {
            let displayWeight: Double
            if isWeightlessMode {
                guard let anchorWeight = anchorWeight else { continue }
                let currentWeight = convertWeight(Int(summary.weight))
                displayWeight = currentWeight - anchorWeight
            } else {
                displayWeight = convertWeight(Int(summary.weight))
            }

            series.append(GraphSeries(
                date: summary.date,
                value: displayWeight,
                series: DashboardStrings.weight
            ))
        }

        // Add selected metric series using Y-axis domain for normalization
        if let selectedMetric = selectedMetric, selectedMetric != DashboardStrings.weight {
            let normalizedMetricSeries = generateNormalizedMetricSeriesWithDomain(
                for: selectedMetric,
                from: allOperations,
                visibleOperations: visibleOperations,
                toWeightDomain: yAxisDomain,
                isWeightlessMode: isWeightlessMode,
                anchorWeight: anchorWeight,
                convertWeight: convertWeight
            )
            series.append(contentsOf: normalizedMetricSeries)
        }

        logger.log(level: .info, tag: "DashboardGraphManager",
                  message: "Generated chart data with Y-axis domain: \(series.count) points, " +
                          "yAxisDomain: \(yAxisDomain), selectedMetric: \(selectedMetric ?? "none"), " +
                          "metric points: \(selectedMetric != nil && selectedMetric != DashboardStrings.weight ? series.filter { $0.series != DashboardStrings.weight }.count : 0)")
        return series
    }

    // MARK: - Chart Data Caching Validation

    /// Determines if cached chart data can be reused based on weight range and metric selection changes
    private func shouldUseCachedData(
        currentWeightRange: ClosedRange<Double>,
        currentSelectedMetric: String?
    ) -> Bool {
        // Check if selected metric changed
        guard currentSelectedMetric == lastChartDataSelectedMetric else {
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "Cannot use cached data: selected metric changed from \(lastChartDataSelectedMetric ?? "none") to \(currentSelectedMetric ?? "none")")
            return false
        }

        // Check if weight range changed significantly
        guard let lastWeightRange = lastChartDataWeightRange else {
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "Cannot use cached data: no previous weight range")
            return false
        }

        // Calculate range overlap and size changes
        let currentSpan = currentWeightRange.upperBound - currentWeightRange.lowerBound
        let lastSpan = lastWeightRange.upperBound - lastWeightRange.lowerBound

        // Check for significant range size change (more than 25%)
        let spanChangeRatio = abs(currentSpan - lastSpan) / max(lastSpan, 0.1)
        if spanChangeRatio > 0.25 {
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "Cannot use cached data: weight range span changed significantly (\(spanChangeRatio * 100)%)")
            return false
        }

        // Check for significant range position change
        let currentCenter = (currentWeightRange.upperBound + currentWeightRange.lowerBound) / 2
        let lastCenter = (lastWeightRange.upperBound + lastWeightRange.lowerBound) / 2
        let centerChange = abs(currentCenter - lastCenter)

        // If center moved more than 50% of the range span, recalculate
        if centerChange > (max(currentSpan, lastSpan) * 0.5) {
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "Cannot use cached data: weight range center moved significantly (\(centerChange))")
            return false
        }

        logger.log(level: .debug, tag: "DashboardGraphManager", message: "Can use cached data: ranges are similar enough")
        return true
    }

    /// Generates normalized metric series using dynamic ranges based on actual data
    /// This approach ensures the metric line properly utilizes the weight range for better visibility
    private func generateNormalizedMetricSeries(
        for selectedMetric: String,
        from operations: [BathScaleWeightSummary],
        toWeightRange weightRange: ClosedRange<Double>
    ) -> [GraphSeries] {

        // Collect all metric values to calculate dynamic range
        let metricValues = operations.compactMap { summary in
            getMetricValue(for: selectedMetric, from: summary)
        }

        guard !metricValues.isEmpty else {
            logger.log(level: .info, tag: "DashboardGraphManager", message: "No metric values found for \(selectedMetric)")
            return []
        }

        // Calculate dynamic metric range from actual data
        guard let metricMin = metricValues.min(),
              let metricMax = metricValues.max() else {
            logger.log(level: .info, tag: "DashboardGraphManager", message: "Could not determine metric range for \(selectedMetric)")
            return []
        }

        // Ensure we have some variation in the data
        let metricRange = metricMax - metricMin
        let effectiveMetricMin: Double
        let effectiveMetricMax: Double

        if metricRange < 0.01 { // Almost no variation
            // Use fallback static ranges for single data points or minimal variation
            let (staticMin, staticMax) = getStaticMetricRange(for: selectedMetric)
            effectiveMetricMin = min(metricMin, staticMin)
            effectiveMetricMax = max(metricMax, staticMax)
        } else {
            // Add small padding to actual range for better visualization
            let padding = metricRange * 0.05 // 5% padding on each side
            effectiveMetricMin = metricMin - padding
            effectiveMetricMax = metricMax + padding
        }

        let weightMin = weightRange.lowerBound
        let weightMax = weightRange.upperBound

        var normalizedSeries: [GraphSeries] = []

        // Generate normalized metric series
        for summary in operations {
            if let metricValue = getMetricValue(for: selectedMetric, from: summary) {

                // Clamp the metric value to the effective range
                let clampedValue = max(effectiveMetricMin, min(effectiveMetricMax, metricValue))

                // Normalize to weight range using the dynamic approach from demo project
                let metricRange = effectiveMetricMax - effectiveMetricMin
                guard metricRange > 0 else {
                    // If no metric variation, use middle of weight range
                    let normalizedValue = (weightMin + weightMax) / 2
                    normalizedSeries.append(GraphSeries(
                        date: summary.date,
                        value: normalizedValue,
                        series: selectedMetric
                    ))
                    continue
                }

                let normalizedValue = weightMin + (clampedValue - effectiveMetricMin) *
                                    (weightMax - weightMin) / metricRange

                // Additional safety check for NaN/infinite values
                guard normalizedValue.isFinite else {
                    let fallbackValue = (weightMin + weightMax) / 2
                    normalizedSeries.append(GraphSeries(
                        date: summary.date,
                        value: fallbackValue,
                        series: selectedMetric
                    ))
                    continue
                }

                // Add small epsilon to keep metrics slightly inside bounds (prevents edge bleeding)
                let epsilon = (weightMax - weightMin) * 0.001 // 0.1% of weight range
                let safeMin = weightMin + epsilon
                let safeMax = weightMax - epsilon

                // Ensure normalized value is within safe bounds
                guard normalizedValue >= safeMin && normalizedValue <= safeMax else {
                    logger.log(level: .info, tag: "DashboardGraphManager",
                              message: "Normalized value \(normalizedValue) out of safe bounds [\(safeMin), \(safeMax)] for \(selectedMetric), using fallback")

                    // Use fallback value within safe bounds
                    let fallbackValue = (safeMin + safeMax) / 2
                    normalizedSeries.append(GraphSeries(
                        date: summary.date,
                        value: fallbackValue,
                        series: selectedMetric
                    ))
                    continue
                }

                normalizedSeries.append(GraphSeries(
                    date: summary.date,
                    value: normalizedValue,
                    series: selectedMetric
                ))
            }
        }

        logger.log(level: .info, tag: "DashboardGraphManager",
                  message: "Generated normalized metric series for \(selectedMetric): \(normalizedSeries.count) points, " +
                          "metricRange: \(effectiveMetricMin)...\(effectiveMetricMax), " +
                          "weightRange: \(weightMin)...\(weightMax)")

        return normalizedSeries
    }

        /// Generates normalized metric series using the provided Y-axis domain for consistency
    /// This ensures metric normalization matches the visible Y-axis range
    private func generateNormalizedMetricSeriesWithDomain(
        for selectedMetric: String,
        from allOperations: [BathScaleWeightSummary],
        visibleOperations: [BathScaleWeightSummary],
        toWeightDomain yAxisDomain: ClosedRange<Double>,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Int) -> Double
    ) -> [GraphSeries] {

        let operationsToAnalyze = visibleOperations

        let metricValues = operationsToAnalyze.compactMap { summary in
            getMetricValue(for: selectedMetric, from: summary)
        }

        guard !metricValues.isEmpty else {
            logger.log(level: .info, tag: "DashboardGraphManager", message: "No metric values found for \(selectedMetric)")
            return []
        }

        // Calculate dynamic metric range from visible data
        guard let metricMin = metricValues.min(),
              let metricMax = metricValues.max() else {
            logger.log(level: .info, tag: "DashboardGraphManager", message: "Could not determine metric range for \(selectedMetric)")
            return []
        }

        // Ensure we have some variation in the data
        let metricRange = metricMax - metricMin
        let effectiveMetricMin: Double
        let effectiveMetricMax: Double

        if metricRange < 0.01 { // Almost no variation
            // Use fallback static ranges for single data points or minimal variation
            let (staticMin, staticMax) = getStaticMetricRange(for: selectedMetric)
            effectiveMetricMin = min(metricMin, staticMin)
            effectiveMetricMax = max(metricMax, staticMax)
        } else {
            // Add small padding to actual range for better visualization
            let padding = metricRange * 0.05 // 5% padding on each side
            effectiveMetricMin = metricMin - padding
            effectiveMetricMax = metricMax + padding
        }

        // Use dynamic y-axis domain for metric normalization to enable dynamic scaling
        // This allows metrics to scale with the visible data range like weight lines
        let weightMin = yAxisDomain.lowerBound
        let weightMax = yAxisDomain.upperBound

        var normalizedSeries: [GraphSeries] = []

        // Generate normalized metric series for all operations (to show continuous line)
        for summary in allOperations {
            if let metricValue = getMetricValue(for: selectedMetric, from: summary) {

                // Clamp the metric value to the effective range
                let clampedValue = max(effectiveMetricMin, min(effectiveMetricMax, metricValue))

                // Directly normalize metric to dynamic y-axis domain for dynamic scaling
                let metricRange = effectiveMetricMax - effectiveMetricMin
                guard metricRange > 0 else {
                    // If no metric variation, use middle of y-axis domain
                    let finalValue = (weightMin + weightMax) / 2

                    normalizedSeries.append(GraphSeries(
                        date: summary.date,
                        value: finalValue,
                        series: selectedMetric
                    ))
                    continue
                }

                // Directly map metric value to y-axis domain range
                let yAxisSpan = weightMax - weightMin
                let normalizedValue = weightMin + (clampedValue - effectiveMetricMin) *
                                    yAxisSpan / metricRange

                // Add small epsilon to keep metrics slightly inside bounds (prevents edge bleeding)
                let epsilon = yAxisSpan * 0.1 // 0.1% of y-axis span
                let safeMin = weightMin + epsilon
                let safeMax = weightMax - epsilon

                // Clamp to safe bounds to ensure metrics stay well within visible range
                let clampedFinalValue = max(safeMin, min(safeMax, normalizedValue))

                // Additional safety check for NaN/infinite values
                guard clampedFinalValue.isFinite else {
                    let fallbackValue = (weightMin + weightMax) / 2

                    normalizedSeries.append(GraphSeries(
                        date: summary.date,
                        value: fallbackValue,
                        series: selectedMetric
                    ))
                    continue
                }

                // Final validation: ensure the value is definitely within safe bounds
                guard clampedFinalValue >= safeMin && clampedFinalValue <= safeMax else {
                    logger.log(level: .info, tag: "DashboardGraphManager",
                              message: "Metric value \(clampedFinalValue) still out of safe bounds [\(safeMin), \(safeMax)] for \(selectedMetric), using fallback")
                    let fallbackValue = (safeMin + safeMax) / 2

                    normalizedSeries.append(GraphSeries(
                        date: summary.date,
                        value: fallbackValue,
                        series: selectedMetric
                    ))
                    continue
                }

                normalizedSeries.append(GraphSeries(
                    date: summary.date,
                    value: clampedFinalValue,
                    series: selectedMetric
                ))
            }
        }

        logger.log(level: .info, tag: "DashboardGraphManager",
  message: "Generated metric series with dynamic y-axis scaling for \(selectedMetric): \(normalizedSeries.count) points, " +
          "metricRange: \(effectiveMetricMin)...\(effectiveMetricMax), " +
          "weightRange: \(weightMin)...\(weightMax), " +
          "yAxisDomain: \(yAxisDomain)")

        return normalizedSeries
    }

    /// Get static metric ranges as fallback for cases with minimal data variation
    private func getStaticMetricRange(for metricLabel: String) -> (min: Double, max: Double) {
        switch metricLabel {
        case DashboardStrings.bmi:
            return (DashboardConstants.MetricRanges.bmi.lowerBound, DashboardConstants.MetricRanges.bmi.upperBound)
        case DashboardStrings.bodyFat, DashboardStrings.muscle, DashboardStrings.water,
             DashboardStrings.bone, DashboardStrings.subFat, DashboardStrings.protein,
             DashboardStrings.skelMuscle:
            return (DashboardConstants.MetricRanges.percentage.lowerBound, DashboardConstants.MetricRanges.percentage.upperBound)
        case DashboardStrings.heartBpm:
            return (DashboardConstants.MetricRanges.heartRate.lowerBound, DashboardConstants.MetricRanges.heartRate.upperBound)
        case DashboardStrings.visceralFat:
            return (DashboardConstants.MetricRanges.visceralFat.lowerBound, DashboardConstants.MetricRanges.visceralFat.upperBound)
        case DashboardStrings.bmrKcal:
            return (DashboardConstants.MetricRanges.bmr.lowerBound, DashboardConstants.MetricRanges.bmr.upperBound)
        case DashboardStrings.metAge:
            return (DashboardConstants.MetricRanges.metabolicAge.lowerBound, DashboardConstants.MetricRanges.metabolicAge.upperBound)
        default:
            return (DashboardConstants.MetricRanges.percentage.lowerBound, DashboardConstants.MetricRanges.percentage.upperBound)
        }
    }

    func updateSelectedPeriod(_ period: TimePeriod) {
        state.selectedPeriod = period
        state.clearSelection()

        logger.log(level: .info, tag: "DashboardGraphManager", message: "Updated selected period to: \(period.rawValue)")
    }

    func getYAxisScale(from operations: [BathScaleWeightSummary], goalWeight: Double, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double, chartHeight: CGFloat) -> YAxisScale {
        let yAxisScale = YAxisCalculator.calculateYAxis(
            operations: operations,
            goalWeight: goalWeight,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertStoredWeightToDisplay: convertWeight,
            chartHeight: chartHeight,
            lastScale: lastYAxisScale
        )
        lastYAxisScale = yAxisScale
        return yAxisScale
    }

    func calculateAndCacheYAxisDomain(from operations: [BathScaleWeightSummary], goalWeight: Double, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double, chartHeight: CGFloat) {
        let yAxisScale = getYAxisScale(
            from: operations,
            goalWeight: goalWeight,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertWeight: convertWeight,
            chartHeight: chartHeight
        )
        let newDomain = yAxisScale.domain
        let newTicks = yAxisScale.ticks

        // Skip if nothing actually changed to avoid unnecessary re-layout churn
        if let cachedDomain = state.cachedYAxisDomain,
           let cachedTicks = state.cachedYAxisTicks,
           cachedDomain == newDomain,
           cachedTicks == newTicks {
            return
        }
        state.cachedYAxisDomain = newDomain
        state.cachedYAxisTicks = newTicks
    }

    private func enforceScrollBoundaries(_ position: Date, from operations: [BathScaleWeightSummary]) -> Date {
        guard !operations.isEmpty else { return position }
        let allDates = operations.map { $0.date }
        guard let minDate = allDates.min(), let maxDate = allDates.max() else { return position }
        let domainLength = visibleDomainLength(for: state.selectedPeriod)
        let halfDomain = domainLength / 2
        let centeringBuffer: TimeInterval
        switch state.selectedPeriod {
        case .week:
            centeringBuffer = DashboardConstants.TimeInterval.day * 3.5
        case .month:
            centeringBuffer = DashboardConstants.TimeInterval.week * 2
        case .year:
            centeringBuffer = DashboardConstants.TimeInterval.month * 2
        case .total:
            centeringBuffer = DashboardConstants.TimeInterval.month * 3
        }
        let pastBuffer: TimeInterval
        switch state.selectedPeriod {
        case .week:
            pastBuffer = DashboardConstants.TimeInterval.day * 1
        case .month:
            pastBuffer = DashboardConstants.TimeInterval.week * 1
        case .year:
            pastBuffer = DashboardConstants.TimeInterval.month * 1
        case .total:
            pastBuffer = DashboardConstants.TimeInterval.month * 2
        }
        let earliestAllowedPosition = minDate.addingTimeInterval(-pastBuffer - halfDomain)
        let maxVisibleEnd = maxDate.addingTimeInterval(centeringBuffer)
        let latestAllowedPosition = maxVisibleEnd.addingTimeInterval(-halfDomain)
        let clampedPosition = max(earliestAllowedPosition, min(position, latestAllowedPosition))
        if clampedPosition != position {
            logger.log(level: .info, tag: "DashboardGraphManager",
                      message: "Enforced scroll boundary: \(position) -> \(clampedPosition), period: \(state.selectedPeriod)")
        }
        return clampedPosition
    }

    func getVisibleOperations(from operations: [BathScaleWeightSummary]) -> [BathScaleWeightSummary] {
        if state.isScrolling && !lastCalculatedVisibleOps.isEmpty {
            return lastCalculatedVisibleOps
        }
        if !lastCalculatedVisibleOps.isEmpty,
           let lastPosition = lastVisibleOpsScrollPosition,
           let lastPeriod = lastVisibleOpsPeriod,
           lastPeriod == state.selectedPeriod {
            let domainLength = visibleDomainLength(for: state.selectedPeriod)
            let positionChange = abs(state.xScrollPosition.timeIntervalSince(lastPosition))
            if positionChange < domainLength / 10 {
                return lastCalculatedVisibleOps
            }
        }
        let allDates = operations.map { $0.date }
        let minDate = allDates.min() ?? Date()
        let maxDate = allDates.max() ?? Date()

        let calculatedStart = state.xScrollPosition.addingTimeInterval(-visibleDomainLength(for: state.selectedPeriod) / 4)
        let calculatedEnd = state.xScrollPosition.addingTimeInterval(visibleDomainLength(for: state.selectedPeriod))
        let visibleStart = max(calculatedStart, minDate)
        let visibleEnd = min(calculatedEnd, maxDate)
        let visibleOps = operations.filter { summary in
            summary.date >= visibleStart && summary.date <= visibleEnd
        }
        lastCalculatedVisibleOps = visibleOps
        lastVisibleOpsScrollPosition = state.xScrollPosition
        lastVisibleOpsPeriod = state.selectedPeriod

        logger.log(level: .info, tag: "DashboardGraphManager", message: "Calculated visible operations: \(visibleOps.count) operations for period \(state.selectedPeriod), scroll position: \(state.xScrollPosition)")

        return visibleOps
    }

    /// Forces recalculation of visible operations and clears cache
    /// Use this after programmatically setting scroll position
    func forceVisibleOperationsRecalculation() {
        lastCalculatedVisibleOps = []
        lastVisibleOpsScrollPosition = nil
        lastVisibleOpsPeriod = nil
        logger.log(level: .info, tag: "DashboardGraphManager", message: "Forced recalculation of visible operations after programmatic scroll position change")
    }

    func ensureLatestEntriesVisible(from operations: [BathScaleWeightSummary]) {
        guard let latestDate = operations.map(\.date).max() else {
            return
        }
        guard !state.isScrolling else {
            return
        }
        let boundedPosition = enforceScrollBoundaries(latestDate, from: operations)
        updateScrollPosition(to: boundedPosition)
    }



    func formatXAxisLabel(for date: Date, period: TimePeriod, operations: [BathScaleWeightSummary]) -> String? {
        return DateTimeTools.formatXAxisLabel(for: date, period: period, operations: operations)
    }

    private func getMetricValue(for label: String, from summary: BathScaleWeightSummary) -> Double? {
        switch label {
        case DashboardStrings.bmi:
            return summary.bmi
        case DashboardStrings.bodyFat:
            return summary.bodyFat
        case DashboardStrings.muscle:
            return summary.muscleMass
        case DashboardStrings.water:
            return summary.water
        case DashboardStrings.heartBpm:
            return summary.pulse.map { Double($0) }
        case DashboardStrings.bone:
            return summary.boneMass
        case DashboardStrings.visceralFat:
            return summary.visceralFatLevel
        case DashboardStrings.subFat:
            return summary.subcutaneousFatPercent
        case DashboardStrings.protein:
            return summary.proteinPercent
        case DashboardStrings.skelMuscle:
            return summary.skeletalMusclePercent
        case DashboardStrings.bmrKcal:
            return summary.bmr.map { Double($0) / 10.0 }
        case DashboardStrings.metAge:
            return summary.metabolicAge.map { Double($0) }
        default:
            return nil
        }
    }

    // MARK: - Metric Selection Support

    /// Validates if a metric can be displayed on the chart
    func canDisplayMetric(_ metricLabel: String, from operations: [BathScaleWeightSummary]) -> Bool {
        let metricValues = operations.compactMap { summary in
            getMetricValue(for: metricLabel, from: summary)
        }

        // Need at least 2 data points with some variation
        guard metricValues.count >= 2 else { return false }

        let metricRange = (metricValues.max() ?? 0) - (metricValues.min() ?? 0)
        return metricRange > 0.001 // Minimum meaningful variation
    }

    /// Gets available metrics that can be displayed for the current data
    func getAvailableMetrics(from operations: [BathScaleWeightSummary]) -> [String] {
        let allMetrics = [
            DashboardStrings.bmi,
            DashboardStrings.bodyFat,
            DashboardStrings.muscle,
            DashboardStrings.water,
            DashboardStrings.heartBpm,
            DashboardStrings.bone,
            DashboardStrings.visceralFat,
            DashboardStrings.subFat,
            DashboardStrings.protein,
            DashboardStrings.skelMuscle,
            DashboardStrings.bmrKcal,
            DashboardStrings.metAge
        ]

        return allMetrics.filter { metric in
            canDisplayMetric(metric, from: operations)
        }
    }

    func visibleDomainLength(for period: TimePeriod) -> TimeInterval {
        return DateTimeTools.visibleDomainLength(for: period)
    }

    private func areEntriesInSameEra(_ summaries: [BathScaleWeightSummary]) -> Bool {
        guard !summaries.isEmpty else { return true }
        let validSummaries = summaries.filter { summary in
            let year = calendar.component(.year, from: summary.date)
            return year >= 1900 && year <= 2100
        }
        guard !validSummaries.isEmpty else { return true }
        let years = Set(validSummaries.map { calendar.component(.year, from: $0.date) })
        return years.count == 1
    }

    // MARK: - X-Axis Generation Methods
    private func generateWeeklyXAxis(minDate: Date, maxDate: Date, shouldRepeat: Bool, entryCount: Int) -> [Date] {
        var dates: [Date] = []
        if !shouldRepeat {
            let weekStart = calendar.dateInterval(of: .weekOfYear, for: minDate)?.start ?? minDate
            for dayOffset in 0..<7 {
                if let dayDate = calendar.date(byAdding: .day, value: dayOffset, to: weekStart) {
                    dates.append(dayDate)
                }
            }
        } else {
            let centeringBuffer = DashboardConstants.TimeInterval.week * 0.5
            let maxAllowedDate = maxDate.addingTimeInterval(centeringBuffer)
            let weekStart = calendar.dateInterval(of: .weekOfYear, for: minDate)?.start ?? minDate
            let totalWeeks = Int(ceil(maxAllowedDate.timeIntervalSince(weekStart) / DashboardConstants.TimeInterval.week))
            for weekOffset in 0..<totalWeeks {
                if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: weekStart) {
                    for dayOffset in 0..<7 {
                        if let dayDate = calendar.date(byAdding: .day, value: dayOffset, to: weekDate) {
                            if dayDate <= maxAllowedDate {
                                dates.append(dayDate)
                            }
                        }
                    }
                }
            }
        }
        return dates
    }

    private func generateMonthlyXAxis(minDate: Date, maxDate: Date, shouldRepeat: Bool, entryCount: Int) -> [Date] {
        var dates: [Date] = []
        if !shouldRepeat {
            let monthStart = calendar.dateInterval(of: .month, for: minDate)?.start ?? minDate
            for weekOffset in 0..<5 {
                if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: monthStart) {
                    dates.append(weekDate)
                }
            }
        } else {
            let centeringBuffer = DashboardConstants.TimeInterval.week * 2
            let maxAllowedDate = maxDate.addingTimeInterval(centeringBuffer)
            let monthStart = calendar.dateInterval(of: .month, for: minDate)?.start ?? minDate
            let totalMonths = Int(ceil(maxAllowedDate.timeIntervalSince(monthStart) / DashboardConstants.TimeInterval.month))
            for monthOffset in 0..<totalMonths {
                if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: monthStart) {
                    for weekOffset in 0..<5 {
                        if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: monthDate) {
                            if weekDate <= maxAllowedDate {
                                dates.append(weekDate)
                            }
                        }
                    }
                }
            }
        }
        return dates
    }

    private func generateYearlyXAxis(minDate: Date, maxDate: Date, shouldRepeat: Bool, entryCount: Int) -> [Date] {
        var dates: [Date] = []
        if !shouldRepeat {
            let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
            for monthOffset in 0..<12 {
                if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: yearStart) {
                    dates.append(monthDate)
                }
            }
        } else {
            let centeringBuffer = DashboardConstants.TimeInterval.month * 2
            let maxAllowedDate = maxDate.addingTimeInterval(centeringBuffer)
            let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
            let totalYears = Int(ceil(maxAllowedDate.timeIntervalSince(yearStart) / DashboardConstants.TimeInterval.year))
            for yearOffset in 0..<totalYears {
                if let yearDate = calendar.date(byAdding: .year, value: yearOffset, to: yearStart) {
                    for monthOffset in 0..<12 {
                        if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: yearDate) {
                            if monthDate <= maxAllowedDate {
                                dates.append(monthDate)
                            }
                        }
                    }
                }
            }
        }
        return dates
    }

    private func generateTotalXAxis(minDate: Date, maxDate: Date, operations: [BathScaleWeightSummary], shouldRepeat: Bool, entryCount: Int) -> [Date] {
        if areEntriesInSameEra(operations) {
            return generateYearlyXAxis(minDate: minDate, maxDate: maxDate, shouldRepeat: shouldRepeat, entryCount: entryCount)
        } else {
            let centeringBuffer = DashboardConstants.TimeInterval.month * 3
            let maxAllowedDate = maxDate.addingTimeInterval(centeringBuffer)
            let quarterStart = calendar.date(from: calendar.dateComponents([.year, .month], from: minDate)) ?? minDate
            let totalQuarters = Int(ceil(maxAllowedDate.timeIntervalSince(quarterStart) / DashboardConstants.TimeInterval.quarter))
            var dates: [Date] = []
            for quarterOffset in 0..<totalQuarters {
                if let quarterDate = calendar.date(byAdding: .month, value: quarterOffset * 3, to: quarterStart) {
                    if quarterDate <= maxAllowedDate {
                        dates.append(quarterDate)
                    }
                }
            }
            return dates
        }
    }

    func generateVisibleXAxisValues(for period: TimePeriod, from operations: [BathScaleWeightSummary], scrollPosition: Date) -> [Date] {
        // NEVER recalculate X-axis values during scrolling to prevent axis jumping
        if state.isScrolling {
            if !lastXAxisValues.isEmpty && lastXAxisPeriod == period {
                logger.log(level: .debug, tag: "DashboardGraphManager", message: "Using cached X-axis values during scroll to prevent jumping")
                return lastXAxisValues
            }
        }

        let domainLength = visibleDomainLength(for: period)
        let allDates = operations.map(\.date)
        guard let overallMinDate = allDates.min(), let overallMaxDate = allDates.max() else { return [] }
        let buffer = domainLength * 2
        let visibleStart = max(overallMinDate, scrollPosition.addingTimeInterval(-domainLength / 2 - buffer))
        let visibleEnd = min(overallMaxDate, scrollPosition.addingTimeInterval(domainLength / 2 + buffer))
        let entryCount = operations.count
        let shouldRepeat =  DateTimeTools.shouldRepeatXAxisLabels(for: period, entryCount: entryCount)
        let xAxisValues: [Date]
        switch period {
        case .week:
            xAxisValues = generateVisibleWeeklyXAxisWithBuffer(visibleStart: visibleStart, visibleEnd: visibleEnd, shouldRepeat: shouldRepeat)
        case .month:
            xAxisValues = generateVisibleMonthlyXAxisWithBuffer(visibleStart: visibleStart, visibleEnd: visibleEnd, shouldRepeat: shouldRepeat)
        case .year:
            xAxisValues = generateVisibleYearlyXAxisWithBuffer(visibleStart: visibleStart, visibleEnd: visibleEnd, shouldRepeat: shouldRepeat)
        case .total:
            xAxisValues = generateVisibleTotalXAxisWithBuffer(visibleStart: visibleStart, visibleEnd: visibleEnd, operations: operations, shouldRepeat: shouldRepeat)
        }
        lastXAxisValues = xAxisValues
        lastXAxisScrollPosition = scrollPosition
        lastXAxisPeriod = period

        return xAxisValues
    }

    /// Calculates the proper scroll position for chart initialization or segment changes
    /// This ensures the scroll position aligns with the computed X-axis values
    func calculateOptimalScrollPosition(for period: TimePeriod, from operations: [BathScaleWeightSummary], showingLatest: Bool = true) -> Date {
        let allDates = operations.map(\.date)
        guard let overallMinDate = allDates.min(), let overallMaxDate = allDates.max() else {
            return Date()
        }

        let domainLength = visibleDomainLength(for: period)

        if showingLatest {
            // For showing latest entries, start from the end and work backwards
            // Calculate what the leftmost position should be to show recent data properly aligned
            let targetEndDate = overallMaxDate
            let targetStartDate = targetEndDate.addingTimeInterval(-domainLength * 0.75) // Show recent 75% of the domain

            // Generate X-axis values for this range to find proper alignment
            let tempScrollPosition = targetStartDate.addingTimeInterval(domainLength / 2) // Center position for generateXAxis logic
            let xAxisValues = generateXAxisValuesForAlignment(for: period, from: operations, centerPosition: tempScrollPosition)

            //Find the right most date and subract the period visisble length
            let rightMostDate = xAxisValues.max()
            let rightMostDateMinusPeriod = rightMostDate?.addingTimeInterval(-visibleDomainLength(for: period))
            if let rightMostDateMinusPeriod = rightMostDateMinusPeriod {
                return rightMostDateMinusPeriod
            }
            // Fallback to calculated position
            return max(overallMinDate, targetStartDate)
        } else {
            // For other cases, use the beginning of data
            return overallMinDate
        }
    }

    /// Generates X-axis values for alignment calculation (internal helper)
    private func generateXAxisValuesForAlignment(for period: TimePeriod, from operations: [BathScaleWeightSummary], centerPosition: Date) -> [Date] {
        let allDates = operations.map(\.date)
        guard let overallMinDate = allDates.min(), let overallMaxDate = allDates.max() else { return [] }

        let domainLength = visibleDomainLength(for: period)
        let buffer = domainLength * 2
        let visibleStart = max(overallMinDate, centerPosition.addingTimeInterval(-domainLength / 2 - buffer))
        let visibleEnd = min(overallMaxDate, centerPosition.addingTimeInterval(domainLength / 2 + buffer))
        let entryCount = operations.count
        let shouldRepeat = DateTimeTools.shouldRepeatXAxisLabels(for: period, entryCount: entryCount)

        switch period {
        case .week:
            return generateVisibleWeeklyXAxisWithBuffer(visibleStart: visibleStart, visibleEnd: visibleEnd, shouldRepeat: shouldRepeat)
        case .month:
            return generateVisibleMonthlyXAxisWithBuffer(visibleStart: visibleStart, visibleEnd: visibleEnd, shouldRepeat: shouldRepeat)
        case .year:
            return generateVisibleYearlyXAxisWithBuffer(visibleStart: visibleStart, visibleEnd: visibleEnd, shouldRepeat: shouldRepeat)
        case .total:
            return generateVisibleTotalXAxisWithBuffer(visibleStart: visibleStart, visibleEnd: visibleEnd, operations: operations, shouldRepeat: shouldRepeat)
        }
    }

    /// Finds the optimal leftmost date from X-axis values
    private func findOptimalLeftmostDate(from xAxisValues: [Date], targetStart: Date, domainLength: TimeInterval) -> Date? {
        let targetEnd = targetStart.addingTimeInterval(domainLength)

        // Find X-axis values that would be visible in our target range
        let visibleXAxisValues = xAxisValues.filter { date in
            date >= targetStart && date <= targetEnd
        }

        // If we have visible X-axis values, use the first one as our leftmost position
        // This ensures the scroll position aligns with an actual X-axis tick
        if let firstVisibleXAxis = visibleXAxisValues.first {
            return firstVisibleXAxis
        }

        // If no X-axis values are in the target range, find the closest one before the target start
        let beforeTarget = xAxisValues.filter { $0 <= targetStart }
        if let closestBefore = beforeTarget.max() {
            return closestBefore
        }

        // Fallback to the first available X-axis value
        return xAxisValues.first
    }

    private func generateVisibleWeeklyXAxisWithBuffer(visibleStart: Date, visibleEnd: Date, shouldRepeat: Bool) -> [Date] {
        // Ensure dates are in correct order to prevent range errors
        let startDate = min(visibleStart, visibleEnd)
        let endDate = max(visibleStart, visibleEnd)

        var dates: [Date] = []

        // Buffer logic: show from start of week (Sunday) of oldest entry to end of week (Saturday) of latest entry
        let weekStartForOldest = calendar.dateInterval(of: .weekOfYear, for: startDate)?.start ?? startDate
        let weekEndForLatest = calendar.dateInterval(of: .weekOfYear, for: endDate)?.end ?? endDate

        // Calculate total weeks from start of oldest week to end of latest week
        let timeInterval = weekEndForLatest.timeIntervalSince(weekStartForOldest)
        let totalWeeks = max(1, Int(ceil(timeInterval / DashboardConstants.TimeInterval.week)))

        for weekOffset in 0..<totalWeeks {
            if let currentWeekStart = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: weekStartForOldest) {
                for dayOffset in 0..<7 {
                    if let dayDate = calendar.date(byAdding: .day, value: dayOffset, to: currentWeekStart) {
                        if dayDate >= weekStartForOldest && dayDate <= weekEndForLatest {
                            dates.append(dayDate)
                        }
                    }
                }
            }
        }
        return dates
    }

    private func generateVisibleMonthlyXAxisWithBuffer(visibleStart: Date, visibleEnd: Date, shouldRepeat: Bool) -> [Date] {
        // Ensure dates are in correct order to prevent range errors
        let startDate = min(visibleStart, visibleEnd)
        let endDate = max(visibleStart, visibleEnd)

        var dates: [Date] = []

        // Buffer logic: show from start of month of oldest entry to end of month of latest entry
        let monthStartForOldest = calendar.dateInterval(of: .month, for: startDate)?.start ?? startDate
        let monthEndForLatest = calendar.dateInterval(of: .month, for: endDate)?.end ?? endDate

        // Calculate total months from start of oldest month to end of latest month
        let timeInterval = monthEndForLatest.timeIntervalSince(monthStartForOldest)
        let totalMonths = max(1, Int(ceil(timeInterval / DashboardConstants.TimeInterval.month)))

        for monthOffset in 0..<totalMonths {
            if let currentMonthStart = calendar.date(byAdding: .month, value: monthOffset, to: monthStartForOldest) {
                for weekOffset in 0..<5 {
                    if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: currentMonthStart) {
                        if weekDate >= monthStartForOldest && weekDate <= monthEndForLatest {
                            dates.append(weekDate)
                        }
                    }
                }
            }
        }
        return dates
    }

    private func generateVisibleYearlyXAxisWithBuffer(visibleStart: Date, visibleEnd: Date, shouldRepeat: Bool) -> [Date] {
        // Ensure dates are in correct order to prevent range errors
        let startDate = min(visibleStart, visibleEnd)
        let endDate = max(visibleStart, visibleEnd)

        var dates: [Date] = []

        // Buffer logic: show from start of year of oldest entry to end of year of latest entry
        let yearStartForOldest = calendar.dateInterval(of: .year, for: startDate)?.start ?? startDate
        let yearEndForLatest = calendar.dateInterval(of: .year, for: endDate)?.end ?? endDate

        // Calculate total years from start of oldest year to end of latest year
        let timeInterval = yearEndForLatest.timeIntervalSince(yearStartForOldest)
        let totalYears = max(1, Int(ceil(timeInterval / DashboardConstants.TimeInterval.year)))

        for yearOffset in 0..<totalYears {
            if let currentYearStart = calendar.date(byAdding: .year, value: yearOffset, to: yearStartForOldest) {
                for monthOffset in 0..<12 {
                    if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: currentYearStart) {
                        if monthDate >= yearStartForOldest && monthDate <= yearEndForLatest {
                            dates.append(monthDate)
                        }
                    }
                }
            }
        }
        return dates
    }

    private func generateVisibleTotalXAxisWithBuffer(visibleStart: Date, visibleEnd: Date, operations: [BathScaleWeightSummary], shouldRepeat: Bool) -> [Date] {
        // For datasets using yearly logic when entries are in same era
        if areEntriesInSameEra(operations) {
            return generateVisibleYearlyXAxisWithBuffer(visibleStart: visibleStart, visibleEnd: visibleEnd, shouldRepeat: shouldRepeat)
        } else {
            // Multi-era datasets use quarterly logic with buffer
            // Ensure dates are in correct order to prevent range errors
            let startDate = min(visibleStart, visibleEnd)
            let endDate = max(visibleStart, visibleEnd)

            var dates: [Date] = []

            // Buffer logic: show from start of quarter of oldest entry to end of quarter of latest entry
            let quarterStartForOldest = calendar.date(from: calendar.dateComponents([.year, .month], from: startDate)) ?? startDate

            // Calculate end of quarter for latest entry
            let endDateComponents = calendar.dateComponents([.year, .month], from: endDate)
            let endMonth = endDateComponents.month ?? 1
            let endYear = endDateComponents.year ?? calendar.component(.year, from: endDate)

            // Find the last month of the quarter containing the end date
            let quarterEndMonth = ((endMonth - 1) / 3 + 1) * 3
            let quarterEndForLatest = calendar.date(from: DateComponents(year: endYear, month: quarterEndMonth + 1, day: 1))?.addingTimeInterval(-1) ?? endDate

            // Calculate total quarters from start of oldest quarter to end of latest quarter
            let timeInterval = quarterEndForLatest.timeIntervalSince(quarterStartForOldest)
            let totalQuarters = max(1, Int(ceil(timeInterval / DashboardConstants.TimeInterval.quarter)))

            for quarterOffset in 0..<totalQuarters {
                if let quarterDate = calendar.date(byAdding: .month, value: quarterOffset * 3, to: quarterStartForOldest) {
                    if quarterDate >= quarterStartForOldest && quarterDate <= quarterEndForLatest {
                        dates.append(quarterDate)
                    }
                }
            }
            return dates
        }
    }


    private func snapToNearestPosition() async {
        logger.log(level: .info, tag: "DashboardGraphManager", message: "Snapping to nearest position")
    }


    func formatSelectedDate(_ date: Date, for period: TimePeriod) -> String {
        let formatter = DateFormatter()
        switch period {
        case .week, .month:
            formatter.dateFormat = "MMM d, yyyy"
        case .year, .total:
            formatter.dateFormat = "MMM yyyy"
        }
        return formatter.string(from: date)
    }

    func formatDateRange(minDate: Date, maxDate: Date, for period: TimePeriod) -> String {
        let calendar = Calendar.current
        let startDay = calendar.component(.day, from: minDate)
        let endDay = calendar.component(.day, from: maxDate)
        let startMonth = DateTimeTools.formatter("LLL").string(from: minDate).lowercased()
        let endMonth = DateTimeTools.formatter("LLL").string(from: maxDate).lowercased()
        let startYear = calendar.component(.year, from: minDate)
        let endYear = calendar.component(.year, from: maxDate)
        switch period {
        case .week, .month:
            return "\(startMonth) \(startDay) - \(endMonth) \(endDay), \(endYear)"
        case .year:
            return "\(startMonth) \(startYear) - \(endMonth), \(endYear)"
        case .total:
            return "\(startMonth) \(startYear) - \(endMonth), \(endYear)"
        }
    }

    func fallbackTimeLabel(for period: TimePeriod) -> String {
        let now = Date()
        let calendar = Calendar.current
        switch period {
        case .week:
            let formatter = DateTimeTools.formatter("MMM d")
            if let week = calendar.dateInterval(of: .weekOfYear, for: now) {
                let start = formatter.string(from: week.start)
                let end = DateTimeTools.formatter("d").string(from: week.end.addingTimeInterval(-1))
                let year = calendar.component(.year, from: now)
                return "\(start)-\(end), \(year)"
            }
            return DateTimeTools.formatter("MMM d, yyyy").string(from: now)
        case .month:
            return DateTimeTools.formatter("LLLL yyyy").string(from: now)
        case .year, .total:
            return DateTimeTools.formatter("yyyy").string(from: now)
        }
    }

    func calculateWeightlessDisplay(_ operations: [BathScaleWeightSummary], anchorWeight: Double?, period: TimePeriod, convertWeight: @escaping (Int) -> Double) -> Double? {
        guard let anchorWeight = anchorWeight else { return nil }
        let allOps = operations
        switch period {
        case .week, .month:
            guard let latestWeight = allOps.last.map({ convertWeight(Int($0.weight)) }) else {
                return nil
            }
            return latestWeight - anchorWeight
        case .year, .total:
            let weights = allOps.map { convertWeight(Int($0.weight)) }
            guard !weights.isEmpty else { return nil }
            let averageWeight = weights.reduce(0, +) / Double(weights.count)
            return averageWeight - anchorWeight
        }
    }

    func getCurrentAverageWeight(from operations: [BathScaleWeightSummary], isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double) -> Double {
        let weightValues = operations.map { summary -> Double in
            if isWeightlessMode {
                guard let anchorWeight = anchorWeight else { return 0 }
                let currentWeight = convertWeight(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return convertWeight(Int(summary.weight))
            }
        }
        guard !weightValues.isEmpty else { return 0 }
        let average = weightValues.reduce(0, +) / Double(weightValues.count)
        return average
    }


    func handleScrollEndOptimized(updateWeightDisplay: @escaping () -> Void, recalculateYAxis: @escaping () -> Void, updateMetrics: @escaping () -> Void) {
        state.scrollEndTimer?.invalidate()
        state.scrollEndTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: false) { [weak self] _ in
            Task { @MainActor in
                guard let self = self else { return }
                self.state.isScrolling = false
                self.state.hasDetectedScrollInCurrentGesture = false

                // Update weight display to show average of visible region
                updateWeightDisplay()
                recalculateYAxis()
                updateMetrics()
            }
        }
    }

    func updateWeightDisplayForCurrentView(triggerUpdate: @escaping () -> Void) {
        triggerUpdate()
    }

    func recalculateYAxisForVisibleData(triggerUpdate: @escaping () -> Void) {
        state.dataChangeTrigger += 1
        triggerUpdate()
    }

    func updateMetricsForCurrentView(selectedPoint: BathScaleWeightSummary?, visibleOperations: [BathScaleWeightSummary], updateMetrics: @escaping (BathScaleWeightSummary) async throws -> Void, resetMetrics: @escaping () -> Void) {
        if let selectedPoint = selectedPoint {
            Task {
                try? await updateMetrics(selectedPoint)
            }
        } else {
            if !visibleOperations.isEmpty && visibleOperations.count > 1 {
                resetMetrics()
            } else {
                resetMetrics()
            }
        }
    }
}
