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

    func updateScrollPosition(to date: Date) async {
        guard !state.isScrolling else {
            return
        }
        state.xScrollPosition = date
    }

    func handleScrollPositionChange(_ newPosition: Date?) async {
        guard let newPosition = newPosition else { return }
        if state.isScrolling {
            latestScrollPosition = newPosition
        } else {
            latestScrollPosition = nil
        }
    }

    func handleScrollStart() async {
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

            // Clear chart data cache to ensure fresh data for new visible range
            clearChartDataCache()

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

                // Clear chart data cache to ensure fresh data for new visible range
                self.clearChartDataCache()

                // Update scroll position from stored value first
                if let finalPosition = self.latestScrollPosition {
                    self.state.xScrollPosition = finalPosition
                    self.latestScrollPosition = nil
                }
                self.state.updateScrollState(isScrolling: false)
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
                          "yAxisDomain: \(yAxisDomain), selectedMetric: \(selectedMetric ?? "none")")
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

    /// Clears chart data cache to force regeneration with new parameters
    func clearChartDataCache() {
        lastChartData.removeAll()
        lastChartDataWeightRange = nil
        lastChartDataSelectedMetric = nil
        logger.log(level: .debug, tag: "DashboardGraphManager", message: "Chart data cache cleared")
    }

    // MARK: - Dynamic Metric Normalization (inspired by demo project)

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
                let normalizedValue = weightMin + (clampedValue - effectiveMetricMin) *
                                    (weightMax - weightMin) / (effectiveMetricMax - effectiveMetricMin)

                // Ensure normalized value is within weight bounds
                guard normalizedValue >= weightMin && normalizedValue <= weightMax else {
                    logger.log(level: .info, tag: "DashboardGraphManager",
                              message: "Normalized value \(normalizedValue) out of weight range for \(selectedMetric)")
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

        // FIXED: Use ALL operations to determine metric range for consistent trends
        // Using visible operations was causing metric trends to change with scroll position
        let operationsToAnalyze = allOperations

        // Collect metric values from ALL operations to calculate consistent dynamic range
        // This ensures the same metric value always maps to the same relative position
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

        // FIXED: Calculate weight range from the same data used for metric range (all operations)
        // This ensures consistent relative positioning between weight and metric lines
        let weightValues = allOperations.map { summary -> Double in
            if isWeightlessMode {
                guard let anchorWeight = anchorWeight else { return 0 }
                let currentWeight = convertWeight(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return convertWeight(Int(summary.weight))
            }
        }

        guard let weightMin = weightValues.min(),
              let weightMax = weightValues.max(),
              weightMax > weightMin else {
            logger.log(level: .info, tag: "DashboardGraphManager", message: "Invalid weight range for metric normalization")
            return []
        }

        var normalizedSeries: [GraphSeries] = []

        // Generate normalized metric series for all operations (to show continuous line)
        for summary in allOperations {
            if let metricValue = getMetricValue(for: selectedMetric, from: summary) {

                // Clamp the metric value to the effective range
                let clampedValue = max(effectiveMetricMin, min(effectiveMetricMax, metricValue))

                                // Step 1: Normalize metric to consistent weight range
                let normalizedToWeightRange = weightMin + (clampedValue - effectiveMetricMin) *
                                            (weightMax - weightMin) / (effectiveMetricMax - effectiveMetricMin)

                // Step 2: Map to Y-axis domain for visibility while preserving relationships
                let yAxisMin = yAxisDomain.lowerBound
                let yAxisMax = yAxisDomain.upperBound
                let yAxisSpan = yAxisMax - yAxisMin
                let weightSpan = weightMax - weightMin

                // Calculate the relative position within weight range
                let relativePosition = (normalizedToWeightRange - weightMin) / weightSpan

                // Map this relative position to Y-axis domain
                let finalValue = yAxisMin + (relativePosition * yAxisSpan)

                // Clamp to Y-axis bounds for safety
                let clampedFinalValue = max(yAxisMin, min(yAxisMax, finalValue))

                normalizedSeries.append(GraphSeries(
                    date: summary.date,
                    value: clampedFinalValue,
                    series: selectedMetric
                ))
            }
        }

                        logger.log(level: .info, tag: "DashboardGraphManager",
                  message: "Generated metric series with consistent relative positioning for \(selectedMetric): \(normalizedSeries.count) points, " +
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

    func updateSelectedPeriod(_ period: TimePeriod) async {
        state.selectedPeriod = period
        state.clearSelection()

        // Clear chart data cache since period change means different operations and ranges
        clearChartDataCache()

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
        state.cachedYAxisDomain = yAxisScale.domain
        state.cachedYAxisTicks = yAxisScale.ticks
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
        return visibleOps
    }

    func ensureLatestEntriesVisible(from operations: [BathScaleWeightSummary]) async {
        guard let latestDate = operations.map(\.date).max() else {
            return
        }
        guard !state.isScrolling else {
            return
        }
        let boundedPosition = enforceScrollBoundaries(latestDate, from: operations)
        await updateScrollPosition(to: boundedPosition)
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
        let domainLength = visibleDomainLength(for: period)
        if state.isScrolling {
            if !lastXAxisValues.isEmpty,
               let lastPosition = lastXAxisScrollPosition,
               lastXAxisPeriod == period {
                let positionChange = abs(scrollPosition.timeIntervalSince(lastPosition))
                if positionChange < domainLength / 4 {
                    return lastXAxisValues
                }
            }
        }
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

    private func generateVisibleWeeklyXAxisWithBuffer(visibleStart: Date, visibleEnd: Date, shouldRepeat: Bool) -> [Date] {
        // Ensure dates are in correct order to prevent range errors
        let startDate = min(visibleStart, visibleEnd)
        let endDate = max(visibleStart, visibleEnd)

        // Handle very small date ranges (less than 2 days) with special logic
        let daysDifference = calendar.dateComponents([.day], from: startDate, to: endDate).day ?? 0
        if daysDifference < DashboardConstants.Thresholds.weekRepeatThreshold {
            // For very small ranges, generate daily ticks with padding
            var dates: [Date] = []
            // set padding start to start of week of startDate
            let paddingStart = calendar.dateInterval(of: .weekOfYear, for: startDate)?.start ?? startDate
            // set padding end to end of week of endDate
            let paddingEnd = calendar.dateInterval(of: .weekOfYear, for: endDate)?.end ?? endDate

            var currentDate = paddingStart
            while currentDate <= paddingEnd {
                dates.append(currentDate)
                currentDate = calendar.date(byAdding: .day, value: 1, to: currentDate) ?? currentDate
            }
            return dates
        }

        var dates: [Date] = []
        let weekStart = calendar.dateInterval(of: .weekOfYear, for: startDate)?.start ?? startDate
        let bufferStart = calendar.date(byAdding: .day, value: -1, to: startDate) ?? startDate

        // Calculate total weeks safely
        let timeInterval = endDate.timeIntervalSince(weekStart)
        let totalWeeks = max(1, Int(ceil(timeInterval / DashboardConstants.TimeInterval.week)) + 1)

        for weekOffset in 0..<totalWeeks {
            if let currentWeekStart = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: weekStart) {
                for dayOffset in 0..<7 {
                    if let dayDate = calendar.date(byAdding: .day, value: dayOffset, to: currentWeekStart) {
                        if dayDate >= bufferStart && dayDate <= endDate.addingTimeInterval(DashboardConstants.TimeInterval.day) {
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

        // Handle very small date ranges (less than 1 week) with special logic
        let daysDifference = calendar.dateComponents([.day], from: startDate, to: endDate).day ?? 0
        if daysDifference < DashboardConstants.Thresholds.monthRepeatThreshold {
            // For very small ranges, generate daily ticks with padding
            var dates: [Date] = []
            // set padding start to start of month of startDate
            let paddingStart = calendar.dateInterval(of: .month, for: startDate)?.start ?? startDate
            // set padding end to end of month of endDate
            let paddingEnd = calendar.dateInterval(of: .month, for: endDate)?.end ?? endDate

            var currentDate = paddingStart
            while currentDate <= paddingEnd {
                //append only weekdate like done below
                if let weekDate = calendar.date(byAdding: .weekOfYear, value: 1, to: currentDate) {
                    dates.append(weekDate)
                }
                currentDate = calendar.date(byAdding: .weekOfYear, value: 1, to: currentDate) ?? currentDate
            }
            return dates
        }

        var dates: [Date] = []
        let monthStart = calendar.dateInterval(of: .month, for: startDate)?.start ?? startDate
        let bufferStart = calendar.date(byAdding: .weekOfYear, value: -1, to: startDate) ?? startDate

        // Calculate total months safely
        let timeInterval = endDate.timeIntervalSince(monthStart)
        let totalMonths = max(1, Int(ceil(timeInterval / DashboardConstants.TimeInterval.month)) + 1)

        for monthOffset in 0..<totalMonths {
            if let currentMonthStart = calendar.date(byAdding: .month, value: monthOffset, to: monthStart) {
                for weekOffset in 0..<5 {
                    if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: currentMonthStart) {
                        if weekDate >= bufferStart && weekDate <= endDate.addingTimeInterval(DashboardConstants.TimeInterval.week) {
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

        // Handle very small date ranges (less than 1 month) with special logic
        let daysDifference = calendar.dateComponents([.day], from: startDate, to: endDate).day ?? 0
        if daysDifference < 365 {
            // For very small ranges, generate weekly ticks with padding
            var dates: [Date] = []
            // set padding start to start of the year of startDate
            let paddingStart = calendar.dateInterval(of: .year, for: startDate)?.start ?? startDate
            // set padding end to end of the year of endDate
            let paddingEnd = calendar.dateInterval(of: .year, for: endDate)?.end ?? endDate

            var currentDate = paddingStart
            while currentDate <= paddingEnd {
                //append only monthdate like done below
                if let monthDate = calendar.date(byAdding: .month, value: 1, to: currentDate) {
                    dates.append(monthDate)
                }
                currentDate = calendar.date(byAdding: .month, value: 1, to: currentDate) ?? currentDate
            }
            return dates
        }

        var dates: [Date] = []
        let yearStart = calendar.dateInterval(of: .year, for: startDate)?.start ?? startDate
        let bufferStart = calendar.date(byAdding: .month, value: -1, to: startDate) ?? startDate

        // Calculate total years safely
        let timeInterval = endDate.timeIntervalSince(yearStart)
        let totalYears = max(1, Int(ceil(timeInterval / DashboardConstants.TimeInterval.year)) + 1)

        for yearOffset in 0..<totalYears {
            if let currentYearStart = calendar.date(byAdding: .year, value: yearOffset, to: yearStart) {
                for monthOffset in 0..<12 {
                    if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: currentYearStart) {
                        if monthDate >= bufferStart && monthDate <= endDate.addingTimeInterval(DashboardConstants.TimeInterval.month) {
                            dates.append(monthDate)
                        }
                    }
                }
            }
        }
        return dates
    }

    private func generateVisibleTotalXAxisWithBuffer(visibleStart: Date, visibleEnd: Date, operations: [BathScaleWeightSummary], shouldRepeat: Bool) -> [Date] {
        // Handle small datasets (1-2 entries) with special logic
        if operations.count <= 2 {
            // For small datasets, just return the actual entry dates with some padding
            var dates: [Date] = []
            let allDates = operations.map(\.date).sorted()

            if let firstDate = allDates.first, let lastDate = allDates.last {
                // Add padding before and after the data range
                let paddingInterval: TimeInterval = 24 * 60 * 60 // 1 day
                let paddedStart = firstDate.addingTimeInterval(-paddingInterval)
                let paddedEnd = lastDate.addingTimeInterval(paddingInterval)

                // Generate daily ticks for the padded range
                var currentDate = paddedStart
                while currentDate <= paddedEnd {
                    dates.append(currentDate)
                    currentDate = calendar.date(byAdding: .day, value: 1, to: currentDate) ?? currentDate
                }
            }

            return dates
        }

        // For larger datasets, use existing logic
        if areEntriesInSameEra(operations) {
            return generateVisibleYearlyXAxisWithBuffer(visibleStart: visibleStart, visibleEnd: visibleEnd, shouldRepeat: shouldRepeat)
        } else {
            // Ensure dates are in correct order to prevent range errors
            let startDate = min(visibleStart, visibleEnd)
            let endDate = max(visibleStart, visibleEnd)

            var dates: [Date] = []
            let quarterStart = calendar.date(from: calendar.dateComponents([.year, .month], from: startDate)) ?? startDate
            let bufferStart = calendar.date(byAdding: .month, value: -1, to: startDate) ?? startDate

            // Calculate total quarters safely
            let timeInterval = endDate.timeIntervalSince(quarterStart)
            let totalQuarters = max(1, Int(ceil(timeInterval / DashboardConstants.TimeInterval.quarter)) + 1)

            for quarterOffset in 0..<totalQuarters {
                if let quarterDate = calendar.date(byAdding: .month, value: quarterOffset * 3, to: quarterStart) {
                    if quarterDate >= bufferStart && quarterDate <= endDate.addingTimeInterval(DashboardConstants.TimeInterval.month) {
                        dates.append(quarterDate)
                    }
                }
            }
            return dates
        }
    }

    private func generateVisibleWeeklyXAxis(visibleStart: Date, visibleEnd: Date, shouldRepeat: Bool) -> [Date] {
        var dates: [Date] = []
        let weekStart = calendar.dateInterval(of: .weekOfYear, for: visibleStart)?.start ?? visibleStart
        let totalWeeks = Int(ceil(visibleEnd.timeIntervalSince(weekStart) / DashboardConstants.TimeInterval.week)) + 1
        for weekOffset in 0..<totalWeeks {
            if let currentWeekStart = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: weekStart) {
                for dayOffset in 0..<7 {
                    if let dayDate = calendar.date(byAdding: .day, value: dayOffset, to: currentWeekStart) {
                        if dayDate >= visibleStart && dayDate <= visibleEnd {
                            dates.append(dayDate)
                        }
                    }
                }
            }
        }
        return dates
    }

    private func generateVisibleMonthlyXAxis(visibleStart: Date, visibleEnd: Date, shouldRepeat: Bool) -> [Date] {
        var dates: [Date] = []
        let monthStart = calendar.dateInterval(of: .month, for: visibleStart)?.start ?? visibleStart
        let totalMonths = Int(ceil(visibleEnd.timeIntervalSince(monthStart) / DashboardConstants.TimeInterval.month)) + 1
        for monthOffset in 0..<totalMonths {
            if let currentMonthStart = calendar.date(byAdding: .month, value: monthOffset, to: monthStart) {
                for weekOffset in 0..<5 {
                    if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: currentMonthStart) {
                        if weekDate >= visibleStart && weekDate <= visibleEnd {
                            dates.append(weekDate)
                        }
                    }
                }
            }
        }
        return dates
    }

    private func generateVisibleYearlyXAxis(visibleStart: Date, visibleEnd: Date, shouldRepeat: Bool) -> [Date] {
        var dates: [Date] = []
        let yearStart = calendar.dateInterval(of: .year, for: visibleStart)?.start ?? visibleStart
        let totalYears = Int(ceil(visibleEnd.timeIntervalSince(yearStart) / DashboardConstants.TimeInterval.year)) + 1
        for yearOffset in 0..<totalYears {
            if let currentYearStart = calendar.date(byAdding: .year, value: yearOffset, to: yearStart) {
                for monthOffset in 0..<12 {
                    if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: currentYearStart) {
                        if monthDate >= visibleStart && monthDate <= visibleEnd {
                            dates.append(monthDate)
                        }
                    }
                }
            }
        }
        return dates
    }

    private func generateVisibleTotalXAxis(visibleStart: Date, visibleEnd: Date, operations: [BathScaleWeightSummary], shouldRepeat: Bool) -> [Date] {
        if areEntriesInSameEra(operations) {
            return generateVisibleYearlyXAxis(visibleStart: visibleStart, visibleEnd: visibleEnd, shouldRepeat: shouldRepeat)
        } else {
            var dates: [Date] = []
            let quarterStart = calendar.date(from: calendar.dateComponents([.year, .month], from: visibleStart)) ?? visibleStart
            let totalQuarters = Int(ceil(visibleEnd.timeIntervalSince(quarterStart) / DashboardConstants.TimeInterval.quarter)) + 1
            for quarterOffset in 0..<totalQuarters {
                if let quarterDate = calendar.date(byAdding: .month, value: quarterOffset * 3, to: quarterStart) {
                    if quarterDate >= visibleStart && quarterDate <= visibleEnd {
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
            return "\(startMonth) \(startDay) \(startYear) - \(endMonth) \(endDay), \(endYear)"
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

    func handleScrollStart() {
        guard !state.isScrolling else { return }
        state.isScrolling = true
    }

    func handleScrollEndOptimized(updateWeightDisplay: @escaping () -> Void, recalculateYAxis: @escaping () -> Void, updateMetrics: @escaping () -> Void) {
        state.scrollEndTimer?.invalidate()
        state.scrollEndTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: false) { [weak self] _ in
            Task { @MainActor in
                guard let self = self else { return }
                self.state.isScrolling = false
                self.state.hasDetectedScrollInCurrentGesture = false

                // Clear chart data cache to ensure fresh data for new visible range
                self.clearChartDataCache()

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
