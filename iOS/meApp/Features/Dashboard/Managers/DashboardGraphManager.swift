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
    
    // Chart data caching for scroll performance
    private var cachedChartSeriesData: [GraphSeries] = []
    private var lastCachedScrollPosition: Date?
    private var lastCachedYAxisDomain: ClosedRange<Double>?
    private var lastCachedSelectedMetric: String?
    private var lastCachedOperationsCount: Int = 0
    private var chartDataGenerationThrottle: Timer?

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
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "Chart selection cleared")
            return
        }

        // Hide any existing crosshair first
        state.showCrosshair = false
        state.selectedXValue = selectedDate

        logger.log(level: .debug, tag: "DashboardGraphManager", message: "Chart selection handled at date: \(selectedDate)")
    }

    /// Handles complete chart selection including finding closest point and updating metrics
    /// This method should be called from the DashboardStore with the necessary dependencies
    func handleCompleteChartSelection(at selectedDate: Date,
                                     operations: [BathScaleWeightSummary],
                                     updateMetrics: @escaping (BathScaleWeightSummary) async throws -> Void,
                                     resetMetrics: @escaping () -> Void,
                                     setMetricPlaceholders: @escaping () -> Void ) async {
        // Only handle selection if not currently scrolling
        guard !state.isScrolling else { return }

        // Hide any existing crosshair first
        state.showCrosshair = false
        // Persist the raw selected X position so UI can render crosshair even if there's no data point
        state.selectedXValue = selectedDate

        guard !operations.isEmpty else { return }

        // Find exact data point at the selected date (same day/month depending on granularity)
        // If none exists, keep crosshair but do not set selectedPoint; set placeholders instead.
        let calendar = Calendar.current
        let exactPoint: BathScaleWeightSummary? = {
            switch state.selectedPeriod {
            case .week, .month:
                return operations.first { calendar.isDate($0.date, inSameDayAs: selectedDate) }
            case .year, .total:
                return operations.first { calendar.isDate($0.date, equalTo: selectedDate, toGranularity: .month) }
            }
        }()

        if let point = exactPoint {
            updateSelectedPoint(point)
            do {
                try await updateMetrics(point)
                logger.log(level: .debug, tag: "DashboardGraphManager", message: "Updated metrics with exact selected point: \(point.date)")
            } catch {
                logger.log(level: .error, tag: "DashboardGraphManager", message: "Failed to update metrics: \(error)")
                resetMetrics()
            }
            return
        }

        guard let selectedBin = selectedBin else { return }

        // Set the selected point and show crosshair
        updateSelectedPoint(selectedBin)

        // Update metrics with the selected point's values
        do {
            try await updateMetrics(selectedBin)
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "Updated metrics with selected point: \(selectedBin.date)")
        } catch {
            logger.log(level: .error, tag: "DashboardGraphManager", message: "Failed to update metrics: \(error)")
            resetMetrics()
        }

        // ADDITIONAL: Find exact data point for body metrics only (not weight)
        // If no exact point exists for body metrics, set placeholders
        let calendar = Calendar.current
        let exactPoint: BathScaleWeightSummary? = {
            switch state.selectedPeriod {
            case .week, .month:
                return operations.first { calendar.isDate($0.date, inSameDayAs: selectedDate) }
            case .year, .total:
                return operations.first { calendar.isDate($0.date, equalTo: selectedDate, toGranularity: .month) }
            }
        }()

        // Only apply exact point logic for body metrics (not weight)
        // Weight uses the closest point logic above, body metrics use exact matching
        if exactPoint == nil {
            // No exact point for body metrics: set placeholders for body metrics only
            setMetricPlaceholders()
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "No exact point for body metrics; showing placeholders")
        }
    }

    /// Computes an interpolated display weight at a given date using surrounding summaries.
    /// If only one side exists, falls back to that side's display weight.
    func interpolatedDisplayWeight(
        at date: Date,
        from operations: [BathScaleWeightSummary],
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Int) -> Double
    ) -> Double? {
        guard !operations.isEmpty else { return nil }

        // Find the immediate neighbors around the target date
        let sorted = operations.sorted { $0.date < $1.date }
        var previous: BathScaleWeightSummary?
        var next: BathScaleWeightSummary?

        for op in sorted {
            if op.date <= date { previous = op } else { next = op; break }
        }

        // Helper to map stored weight to display/weightless value
        func mapWeight(_ w: Int) -> Double {
            if isWeightlessMode {
                guard let anchor = anchorWeight else { return 0 }
                return convertWeight(w) - anchor
            }
            return convertWeight(w)
        }

        if let prev = previous, let next = next {
            // Linear interpolation by time
            let t0 = prev.date.timeIntervalSinceReferenceDate
            let t1 = next.date.timeIntervalSinceReferenceDate
            let t = date.timeIntervalSinceReferenceDate
            let v0 = mapWeight(Int(prev.weight))
            let v1 = mapWeight(Int(next.weight))
            let denom = t1 - t0
            // Identical timestamps: return boundary value directly to avoid undefined ratio
            if denom == 0 { return v0 }
            let ratio = min(max((t - t0) / denom, 0), 1)
            return v0 + (v1 - v0) * ratio
        }

        if let prev = previous { return mapWeight(Int(prev.weight)) }
        if let next = next { return mapWeight(Int(next.weight)) }
        return nil
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
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "Selected point updated: \(point.date) with weight: \(point.weight)")
        } else {
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "Selected point cleared")
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
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "Unknown scroll phase encountered")
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
    /// Now includes caching and throttling for scroll performance
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
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "No operations available for chart data generation")
            return []
        }

        // Check if we can use cached data during scrolling
        if state.isScrolling && canUseCachedChartData(
            operationsCount: allOperations.count,
            yAxisDomain: yAxisDomain,
            selectedMetric: selectedMetric
        ) {
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "Using cached chart data during scroll")
            return cachedChartSeriesData
        }
        
        // During scrolling, use simplified data for better performance
        let operationsToProcess = state.isScrolling ? 
            simplifyDataForScrolling(allOperations) : 
            allOperations

        // Generate fresh data
        var series: [GraphSeries] = []
        
        // Add weight series (always present) from operations to show continuous line
        // removed verbose generation log to reduce noise
        for summary in operationsToProcess {
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

        // Cache the generated data for future scroll events
        cacheChartData(
            series: series,
            operationsCount: allOperations.count,
            yAxisDomain: yAxisDomain,
            selectedMetric: selectedMetric
        )

        logger.log(level: .debug, tag: "DashboardGraphManager",
                  message: "Generated fresh chart data with Y-axis domain: \(series.count) points, " +
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
        
        // Clear chart data cache when period changes
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

    /// Returns operations strictly within the on-screen visible domain (no buffer)
    /// Uses the chart's configured visible domain length starting at the current left edge (xScrollPosition).
    func getStrictVisibleOperations(from operations: [BathScaleWeightSummary]) -> [BathScaleWeightSummary] {
        guard !operations.isEmpty else { return [] }

        let allDates = operations.map { $0.date }
        guard let minDate = allDates.min(), let maxDate = allDates.max() else { return [] }

        let domainLength = visibleDomainLength(for: state.selectedPeriod)
        // IMPORTANT: xScrollPosition in our state represents the LEFT boundary of the visible window
        let start = max(state.xScrollPosition, minDate)
        let end = min(state.xScrollPosition.addingTimeInterval(domainLength), maxDate)

        let strictlyVisible = operations.filter { summary in
            summary.date >= start && summary.date <= end
        }

        logger.log(level: .debug, tag: "DashboardGraphManager", message: "Calculated strict visible operations: \(strictlyVisible.count) between \(start) and \(end)")
        return strictlyVisible
    }

    /// Returns the operations that immediately bracket the current visible window
    /// - Note: Uses the store convention where `xScrollPosition` is the LEFT edge of the visible window.
    ///         When there are no points inside the visible window, these two points determine the
    ///         connecting line that traverses the window. We use them to compute a meaningful Y-axis.
    func getBracketingOperations(from operations: [BathScaleWeightSummary]) -> [BathScaleWeightSummary] {
        guard !operations.isEmpty else { return [] }

        // Determine the visible window [leftEdge, rightEdge]
        let leftEdge: Date = state.xScrollPosition
        let rightEdge: Date = state.xScrollPosition.addingTimeInterval(visibleDomainLength(for: state.selectedPeriod))

        // Find the last operation at or before the left edge
        let previous = operations
            .filter { $0.date <= leftEdge }
            .max(by: { $0.date < $1.date })

        // Find the first operation at or after the right edge
        let next = operations
            .filter { $0.date >= rightEdge }
            .min(by: { $0.date < $1.date })

        // If not found on one side, try to at least capture the closest on that side of the window
        // This helps when the rightEdge lies beyond the last entry or leftEdge before the first.
        let fallbackPrev = previous ?? operations.filter { $0.date < rightEdge }.max(by: { $0.date < $1.date })
        let fallbackNext = next ?? operations.filter { $0.date > leftEdge }.min(by: { $0.date < $1.date })

        var result: [BathScaleWeightSummary] = []
        if let p = fallbackPrev { result.append(p) }
        if let n = fallbackNext, result.last?.date != n.date { result.append(n) }
        return result
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


    func generateVisibleXAxisValues(for period: TimePeriod, from operations: [BathScaleWeightSummary], scrollPosition: Date) -> [Date] {
        // During scrolling we usually want to keep ticks stable, but when the window
        // moves significantly or approaches dataset edges we must recompute so the
        // trailing phantom tick (extra space) appears immediately, avoiding the post-scroll
        // "jump" of the last data point.
        if state.isScrolling, !lastXAxisValues.isEmpty, lastXAxisPeriod == period {
            let domainLength: TimeInterval = visibleDomainLength(for: period)
            let lastPos = lastXAxisScrollPosition ?? state.xScrollPosition
            let delta = abs(scrollPosition.timeIntervalSince(lastPos))

            // Heuristic: allow reuse unless the user moved > ~1/6 of the domain or is near edges
            let movedFar = delta > (domainLength / 6.0)

            // Edge detection: if the right or left boundary is close to the dataset edges,
            // recompute so we include the empty trailing/leading space immediately.
            let allDates: [Date] = operations.map { $0.date }
            let minDate = allDates.min() ?? scrollPosition
            let maxDate = allDates.max() ?? scrollPosition
            let leftEdge = scrollPosition
            let rightEdge = scrollPosition.addingTimeInterval(domainLength)
            let nearLeft = leftEdge <= minDate.addingTimeInterval(domainLength / 4.0)
            let nearRight = rightEdge >= maxDate.addingTimeInterval(-domainLength / 4.0)

            if !(movedFar || nearLeft || nearRight) {
                logger.log(level: .debug, tag: "DashboardGraphManager", message: "Using cached X-axis values during scroll (stable segment)")
                return lastXAxisValues
            }
        }

        let domainLength: TimeInterval = visibleDomainLength(for: period)
        let allDates: [Date] = operations.map { $0.date }
        guard let overallMinDate = allDates.min() else { return [] }
        let buffer: TimeInterval = domainLength * 2.0
        let currentDate = Date()
        let visibleStart = max(overallMinDate, scrollPosition.addingTimeInterval(-domainLength / 2.0 - buffer))
        let visibleEnd = min(currentDate, scrollPosition.addingTimeInterval(domainLength / 2.0 + buffer))
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
        let allDates: [Date] = operations.map { $0.date }
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
        let buffer: TimeInterval = domainLength * 2.0
        let visibleStart = max(overallMinDate, centerPosition.addingTimeInterval(-domainLength / 2.0 - buffer))
        let visibleEnd = min(overallMaxDate, centerPosition.addingTimeInterval(domainLength / 2.0 + buffer))
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

        // Use a Sunday-start Gregorian calendar locally to avoid region differences
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = calendar.timeZone
        cal.locale = calendar.locale
        cal.firstWeekday = 1 // Sunday

        var dates: [Date] = []

        // Start of oldest week (Sunday 00:00 local)
        let weekStartForOldest = cal.dateInterval(of: .weekOfYear, for: startDate)?.start ?? startDate
        // End returned by dateInterval is exclusive (start of next week).
        // We'll compare using this exclusive boundary so Saturday is included.
        let weekEndExclusive = cal.dateInterval(of: .weekOfYear, for: endDate)?.end ?? endDate

        // Calculate total weeks from start of oldest week to inclusive end of latest week
        let timeInterval = weekEndExclusive.timeIntervalSince(weekStartForOldest)
        let totalWeeks = max(1, Int(ceil(timeInterval / DashboardConstants.TimeInterval.week)))

        for weekOffset in 0..<totalWeeks {
            if let currentWeekStart = cal.date(byAdding: .weekOfYear, value: weekOffset, to: weekStartForOldest) {
                // Iterate 7 days per week; anchor to noon to avoid DST/timezone boundary issues
                let startOfCurrentWeek = cal.startOfDay(for: currentWeekStart)
                for dayOffset in 0...6 { // include Saturday
                    if let dayStart = cal.date(byAdding: .day, value: dayOffset, to: startOfCurrentWeek),
                       let dayDate = cal.date(byAdding: .hour, value: 12, to: dayStart) { // noon
                        if dayDate >= weekStartForOldest && dayDate < weekEndExclusive {
                            dates.append(dayDate)
                        }
                    }
                }
            }
        }
        // Append a phantom tick one day after the last date so the real last tick (Saturday)
        // is not exactly at the right edge of the visible domain.
        if let last = dates.max() {
            let lastStart = cal.startOfDay(for: last)
            if let nextDay = cal.date(byAdding: .day, value: 1, to: lastStart),
               let phantomNoon = cal.date(byAdding: .hour, value: 12, to: nextDay) {
                dates.append(phantomNoon)
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

        // Use a stable Gregorian calendar and anchor ticks at local noon
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = calendar.timeZone
        cal.locale = calendar.locale

        var dates: [Date] = []

        // Start of oldest year and exclusive end (start of next year)
        let yearStartForOldest = cal.dateInterval(of: .year, for: startDate)?.start ?? startDate
        let yearEndExclusive = cal.dateInterval(of: .year, for: endDate)?.end ?? endDate

        // Calculate total years from start of oldest year to exclusive end of latest year
        let timeInterval = yearEndExclusive.timeIntervalSince(yearStartForOldest)
        let totalYears = max(1, Int(ceil(timeInterval / DashboardConstants.TimeInterval.year)))

        for yearOffset in 0..<totalYears {
            if let currentYearStart = cal.date(byAdding: .year, value: yearOffset, to: yearStartForOldest) {
                let startOfYear = cal.startOfDay(for: currentYearStart)
                for monthOffset in 0..<12 {
                    if let monthStart = cal.date(byAdding: .month, value: monthOffset, to: startOfYear),
                       let monthNoon = cal.date(byAdding: .hour, value: 12, to: monthStart) {
                        if monthNoon >= yearStartForOldest && monthNoon < yearEndExclusive {
                            dates.append(monthNoon)
                        }
                    }
                }
            }
        }

        // Append a phantom tick one month after the last real month to keep December visible
        if let last = dates.max() {
            let startOfLast = cal.startOfDay(for: last)
            if let nextMonth = cal.date(byAdding: .month, value: 1, to: startOfLast),
               let phantomNoon = cal.date(byAdding: .hour, value: 12, to: nextMonth) {
                dates.append(phantomNoon)
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
            formatter.dateFormat = "MMM, yyyy"
        }
        return formatter.string(from: date)
    }

    func formatDateRange(minDate: Date, maxDate: Date, for period: TimePeriod) -> String {
        let calendar = Calendar.current
        // Normalize order to avoid inverted labels when inputs are swapped or nudged
        let startDate = min(minDate, maxDate)
        let endDate = max(minDate, maxDate)

        // Special handling for TOTAL: if the dataset spans only one month, show just "Sep 2025"
        if period == .total {
            if calendar.isDate(startDate, equalTo: endDate, toGranularity: .month) {
                return DateTimeTools.formatter("MMM yyyy").string(from: minDate)
            }
        }

        // Special handling for month: snap range to actual month boundaries based on the center
        if period == .month {
            let span = endDate.timeIntervalSince(startDate)
            let center = startDate.addingTimeInterval(max(0, span / 2))

            if let monthInterval = calendar.dateInterval(of: .month, for: center) {
                let startOfMonth = monthInterval.start
                let inclusiveEndOfMonth = calendar.date(byAdding: .day, value: -1, to: monthInterval.end) ?? monthInterval.end

                let startDay = calendar.component(.day, from: startOfMonth)
                let endDay = calendar.component(.day, from: inclusiveEndOfMonth)
                let startMonth = DateTimeTools.formatter("LLL").string(from: startOfMonth).lowercased()
                let endMonth = DateTimeTools.formatter("LLL").string(from: inclusiveEndOfMonth).lowercased()
                let endYear = calendar.component(.year, from: inclusiveEndOfMonth)

                return "\(startMonth) \(startDay) - \(endMonth) \(endDay), \(endYear)"
            }
        }

        // For year: clamp to full month boundaries inside the visible window
        if period == .year {
            // Robust: derive the label purely from the mid-point year, ignoring phantom edges
            let span = endDate.timeIntervalSince(startDate)
            let mid = startDate.addingTimeInterval(max(0, span / 2))
            let year = calendar.component(.year, from: mid)

            // Jan 1 of the year and Dec 1 of the same year
            let startOfYear = calendar.date(from: DateComponents(year: year, month: 1, day: 1)) ?? minDate
            let startMonthStr = DateTimeTools.formatter("LLL").string(from: startOfYear).lowercased()
            let startYear = year

            let decOfYear = calendar.date(from: DateComponents(year: year, month: 12, day: 1)) ?? maxDate
            let endMonthStr = DateTimeTools.formatter("LLL").string(from: decOfYear).lowercased()
            let endYear = year

            return "\(startMonthStr) \(startYear) - \(endMonthStr), \(endYear)"
        }

        // For total: snap both ends to month starts for stability (independent of any phantom months)
        if period == .total {
            let startMonthStart = (calendar.dateInterval(of: .month, for: startDate)?.start) ?? startDate
            var endMonthStart = (calendar.dateInterval(of: .month, for: endDate)?.start) ?? endDate

            if endMonthStart < startMonthStart {
                endMonthStart = startMonthStart
            }

            let startMonthStr = DateTimeTools.formatter("LLL").string(from: startMonthStart).lowercased()
            let startYear = calendar.component(.year, from: startMonthStart)
            let endMonthStr = DateTimeTools.formatter("LLL").string(from: endMonthStart).lowercased()
            let endYear = calendar.component(.year, from: endMonthStart)

            return "\(startMonthStr) \(startYear) - \(endMonthStr), \(endYear)"
        }

        // Default (week) with inclusive end-day handling
        let inclusiveEndDate: Date = calendar.date(byAdding: .day, value: -1, to: endDate) ?? endDate
        let startDay = calendar.component(.day, from: startDate)
        let endDay = calendar.component(.day, from: inclusiveEndDate)
        let startMonth = DateTimeTools.formatter("LLL").string(from: startDate).lowercased()
        let endMonth = DateTimeTools.formatter("LLL").string(from: inclusiveEndDate).lowercased()
        let endYear = calendar.component(.year, from: inclusiveEndDate)

        return "\(startMonth) \(startDay) - \(endMonth) \(endDay), \(endYear)"
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

    func endScrollingImmediately() {
        state.scrollEndTimer?.invalidate()
        state.isScrolling = false
        state.hasDetectedScrollInCurrentGesture = false
        latestScrollPosition = nil
        
        // Clear chart data cache when scrolling ends to ensure fresh data on next scroll
        clearChartDataCache()
    }
    
    // MARK: - Chart Data Caching for Scroll Performance
    
    /// Checks if cached chart data can be used during scrolling
    private func canUseCachedChartData(
        operationsCount: Int,
        yAxisDomain: ClosedRange<Double>,
        selectedMetric: String?
    ) -> Bool {
        // Must have cached data
        guard !cachedChartSeriesData.isEmpty else { return false }
        
        // Operations count must match
        guard operationsCount == lastCachedOperationsCount else { return false }
        
        // Selected metric must match
        guard selectedMetric == lastCachedSelectedMetric else { return false }
        
        // Y-axis domain must be similar (within 5% tolerance)
        guard let lastDomain = lastCachedYAxisDomain else { return false }
        
        let domainDifference = abs(yAxisDomain.lowerBound - lastDomain.lowerBound) + 
                              abs(yAxisDomain.upperBound - lastDomain.upperBound)
        let domainSize = max(yAxisDomain.upperBound - yAxisDomain.lowerBound, 0.1)
        let tolerance = domainSize * 0.05 // 5% tolerance
        
        return domainDifference <= tolerance
    }
    
    /// Caches chart data for reuse during scrolling
    private func cacheChartData(
        series: [GraphSeries],
        operationsCount: Int,
        yAxisDomain: ClosedRange<Double>,
        selectedMetric: String?
    ) {
        cachedChartSeriesData = series
        lastCachedOperationsCount = operationsCount
        lastCachedYAxisDomain = yAxisDomain
        lastCachedSelectedMetric = selectedMetric
        lastCachedScrollPosition = state.xScrollPosition
    }
    
    /// Clears chart data cache (called when scrolling ends or data changes)
    private func clearChartDataCache() {
        cachedChartSeriesData = []
        lastCachedScrollPosition = nil
        lastCachedYAxisDomain = nil
        lastCachedSelectedMetric = nil
        lastCachedOperationsCount = 0
        
        // Cancel any pending throttled generation
        chartDataGenerationThrottle?.invalidate()
        chartDataGenerationThrottle = nil
    }
    
    /// Simplifies data during scrolling by reducing the number of points for better performance
    private func simplifyDataForScrolling(_ operations: [BathScaleWeightSummary]) -> [BathScaleWeightSummary] {
        // For very large datasets, sample every nth point during scrolling
        let maxPointsDuringScroll = 100 // Limit to 100 points during scroll
        
        guard operations.count > maxPointsDuringScroll else {
            return operations
        }
        
        let step = operations.count / maxPointsDuringScroll
        var simplifiedOps: [BathScaleWeightSummary] = []
        
        for i in stride(from: 0, to: operations.count, by: max(1, step)) {
            simplifiedOps.append(operations[i])
        }
        
        // Always include the last point to maintain chart continuity
        if let lastOp = operations.last, !simplifiedOps.contains(where: { $0.date == lastOp.date }) {
            simplifiedOps.append(lastOp)
        }
        
        logger.log(level: .debug, tag: "DashboardGraphManager", 
                  message: "Simplified data for scrolling: \(operations.count) -> \(simplifiedOps.count) points")
        
        return simplifiedOps
    }
}
