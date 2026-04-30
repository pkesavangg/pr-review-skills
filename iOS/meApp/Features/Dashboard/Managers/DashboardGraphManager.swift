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

    // Flag to prevent ensureLatestEntriesVisible from overriding scroll position during period changes
    private var isChangingPeriod: Bool = false

    // Chart data caching for scroll performance
    private var cachedChartSeriesData: [GraphSeries] = []
    private var lastCachedScrollPosition: Date?
    private var lastCachedYAxisDomain: ClosedRange<Double>?
    private var lastCachedSelectedMetric: String?
    private var lastCachedOperationsCount: Int = 0
    private var chartDataGenerationThrottle: Timer?
    
    /// Gregorian calendar used for yearly tick generation and yearly snap quantization.
    /// This keeps rendered month grid lines and snap targets in the same calendar system.
    private var yearlyTickCalendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = calendar.timeZone
        cal.locale = calendar.locale
        return cal
    }

    /// Gregorian calendar aligned with WeekSectionViewModel.plotXDate.
    private var weekPlotCalendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = Calendar.current.timeZone
        cal.locale = Calendar.current.locale
        return cal
    }

    // MARK: - Constants

    /// Position for single metric points within the Y-axis domain (0.0 = bottom, 1.0 = top)
    /// Value of 0.6 places the point at 60% height, slightly above center for optimal visibility
    /// without touching top or bottom boundaries
    private static let singleMetricPointYAxisPosition: Double = 0.6

    init(initialState: GraphState = GraphState()) {
        self.state = initialState
    }

    func updateScrollPosition(to date: Date) {
        // Always allow scroll position updates, even during scrolling
        // This ensures period changes and initialization always work correctly
        state.xScrollPosition = date
    }

    func handleScrollPositionChange(_ newPosition: Date?) {
        guard let newPosition = newPosition else {
            return
        }
        // Always keep the latest position candidate so small drags that do not
        // spend long in `.interacting` still settle to a snapped endpoint.
        latestScrollPosition = newPosition
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
            return
        }

        // Hide any existing crosshair first
        state.showCrosshair = false
        state.selectedXValue = selectedDate

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

        // If no operations available, show placeholders and return
        guard !operations.isEmpty else {
            updateSelectedPoint(nil)
            setMetricPlaceholders()
            return
        }

        // Determine if there's an exact data point for the selected date based on the current period granularity
        let calendar = Calendar.current
        let exactPoint: BathScaleWeightSummary? = {
            switch state.selectedPeriod {
            case .week, .month:
                return operations.first { calendar.isDate($0.date, inSameDayAs: selectedDate) }
            case .year, .total:
                return operations.first { calendar.isDate($0.date, equalTo: selectedDate, toGranularity: .month) }
            }
        }()

        // If we have an exact point, select it and update metrics; otherwise keep selection as interpolated-only
        if let exact = exactPoint {
            updateSelectedPoint(exact)
            do {
                try await updateMetrics(exact)
            } catch {
                logger.log(level: .error, tag: "DashboardGraphManager", message: "Failed to update metrics: \(error)")
                resetMetrics()
            }
        } else {
            // No exact point at this date: clear selected point so UI uses interpolated display weight
            updateSelectedPoint(nil)
            // For body metrics, show placeholders when there's no exact match
            setMetricPlaceholders()
        }

        // Always show crosshair at the selected X position, even when interpolating between points
        state.showCrosshair = true
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

        // Keep interpolation X values aligned with the chart's plotted X values.
        // Week and month views plot points at local noon (see corresponding plotXDate overrides),
        // so interpolation must use the same normalization to avoid value-vs-line drift.
        @inline(__always)
        func normalizedInterpolationDate(_ input: Date) -> Date {
            guard state.selectedPeriod == .week || state.selectedPeriod == .month else { return input }
            let cal = weekPlotCalendar
            let dayStart = cal.startOfDay(for: input)
            return cal.date(byAdding: .hour, value: 12, to: dayStart) ?? input
        }

        // 1) Sort and map to (x,y) in display space (units or weightless delta)
        let sorted = operations.sorted { $0.date < $1.date }
        let xs: [Double] = sorted.map { normalizedInterpolationDate($0.date).timeIntervalSinceReferenceDate }

        func mapWeight(_ w: Int) -> Double {
            if isWeightlessMode {
                guard let anchor = anchorWeight else { return 0 }
                return convertWeight(w) - anchor
            }
            return convertWeight(w)
        }
        let ys: [Double] = sorted.map { mapWeight(Int($0.weight)) }

        let n = xs.count
        if n == 1 { return ys[0] }

        let t = normalizedInterpolationDate(date).timeIntervalSinceReferenceDate

        // 2) For dates outside the data range, return the edge values (clamping behavior)
        // This allows interpolation to work at the boundaries while the calling method
        // can decide whether to use these edge values or filter them out
        if t <= xs[0] { return ys[0] }
        if t >= xs[n - 1] { return ys[n - 1] }

        // 3) Locate segment i such that xs[i] <= t <= xs[i+1]
        //    (upperBound - 1)
        var low = 0, high = n - 1
        while low <= high {
            let mid = (low + high) >> 1
            // Use upperBound semantics so exact matches select the right segment (i = upperBound - 1)
            if xs[mid] <= t {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        let i = max(0, min(n - 2, low - 1))
        let h_i = xs[i + 1] - xs[i]
        if h_i == 0 { return ys[i] } // degenerate

        // 4) Slopes m[k] across all segments (needed for monotone tangents)
        var m = Array(repeating: 0.0, count: n - 1)
        for k in 0..<(n - 1) {
            let h = xs[k + 1] - xs[k]
            m[k] = h == 0 ? 0 : (ys[k + 1] - ys[k]) / h
        }

        // 5) Compute Fritsch–Carlson tangents d[k]
        var d = Array(repeating: 0.0, count: n)

        if n == 2 {
            // Straight line case
            d[0] = m[0]
            d[1] = m[0]
        } else {
            // Interior points
            for k in 1..<(n - 1) {
                let m0 = m[k - 1], m1 = m[k]
                if m0 == 0 || m1 == 0 || m0.sign != m1.sign {
                    d[k] = 0
                } else {
                    let h0 = xs[k] - xs[k - 1]
                    let h1 = xs[k + 1] - xs[k]
                    let w1 = 2 * h1 + h0
                    let w2 = h1 + 2 * h0
                    d[k] = (w1 + w2) / (w1 / m0 + w2 / m1)
                }
            }

            // Endpoints (Fritsch–Carlson one-sided)
            // d0
            do {
                let h0 = xs[1] - xs[0]
                let h1 = xs[2] - xs[1]
                // Guard against division by zero when consecutive timestamps are identical
                var d0: Double
                if h0 + h1 == 0 {
                    d0 = 0
                } else {
                    d0 = ((2 * h0 + h1) * m[0] - h0 * m[1]) / (h0 + h1)
                }
                if d0.sign != m[0].sign { d0 = 0 }
                else if abs(d0) > 3 * abs(m[0]) { d0 = 3 * m[0] }
                d[0] = d0
            }
            // d_{n-1}
            do {
                let hm1 = xs[n - 1] - xs[n - 2]
                let hm2 = xs[n - 2] - xs[n - 3]
                // Guard against division by zero when consecutive timestamps are identical
                var dn1: Double
                if hm1 + hm2 == 0 {
                    dn1 = 0
                } else {
                    dn1 = ((2 * hm1 + hm2) * m[n - 2] - hm1 * m[n - 3]) / (hm1 + hm2)
                }
                if dn1.sign != m[n - 2].sign { dn1 = 0 }
                else if abs(dn1) > 3 * abs(m[n - 2]) { dn1 = 3 * m[n - 2] }
                d[n - 1] = dn1
            }
        }

        // 6) Hermite evaluation on segment i
        let s = (t - xs[i]) / h_i
        let s2 = s * s
        let s3 = s2 * s

        let h00 =  2 * s3 - 3 * s2 + 1
        let h10 =      s3 - 2 * s2 + s
        let h01 = -2 * s3 + 3 * s2
        let h11 =      s3 -     s2

        let y = h00 * ys[i]
        + h10 * h_i * d[i]
        + h01 * ys[i + 1]
        + h11 * h_i * d[i + 1]

        guard y.isFinite else { return nil }

        // Apply same rounding logic as other weight calculations
        let roundedY = (y * 100).rounded(.toNearestOrAwayFromZero) / 100

        return roundedY
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
            break
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
            }
        }
    }

    func generateChartData(from operations: [BathScaleWeightSummary], selectedMetric: String?, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double) -> [GraphSeries] {
        guard !operations.isEmpty else {
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
            return []
        }

        // Check if we can use cached data during scrolling
        if state.isScrolling && canUseCachedChartData(
            operationsCount: allOperations.count,
            yAxisDomain: yAxisDomain,
            selectedMetric: selectedMetric
        ) {
            return cachedChartSeriesData
        }

        // Generate fresh data using all operations
        // Note: Windowing was tried but caused UI jumps during scrolling
        // The ~8ms generation time with all 3660 points is acceptable
        var series: [GraphSeries] = []

        // Add weight series (always present) from operations to show continuous line
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
        // Calculate operations used for Y-axis calculation to ensure consistency
        // This matches the logic in DashboardStore.updateYAxisCache
        let operationsForYAxis: [BathScaleWeightSummary]
        if state.selectedPeriod == .total {
            operationsForYAxis = allOperations
        } else {
            let bracketingOperations = getBracketingOperations(from: allOperations)
            // Use Set for O(1) lookup instead of O(n) contains(where:) - fixes O(n²) performance
            let existingTimestamps = Set(visibleOperations.map { $0.entryTimestamp })
            var combinedOperations = visibleOperations
            for bracketOp in bracketingOperations where !existingTimestamps.contains(bracketOp.entryTimestamp) {
                combinedOperations.append(bracketOp)
            }
            operationsForYAxis = combinedOperations.isEmpty ? allOperations : combinedOperations
        }

        if let selectedMetric = selectedMetric, selectedMetric != DashboardStrings.weight {
            let normalizedMetricSeries = generateNormalizedMetricSeriesWithDomain(
                for: selectedMetric,
                from: allOperations,
                visibleOperations: visibleOperations,
                operationsForYAxis: operationsForYAxis,
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
            return false
        }

        // Check if weight range changed significantly
        guard let lastWeightRange = lastChartDataWeightRange else {
            return false
        }

        // Calculate range overlap and size changes
        let currentSpan = currentWeightRange.upperBound - currentWeightRange.lowerBound
        let lastSpan = lastWeightRange.upperBound - lastWeightRange.lowerBound

        // Check for significant range size change (more than 25%)
        let spanChangeRatio = abs(currentSpan - lastSpan) / max(lastSpan, 0.1)
        if spanChangeRatio > 0.25 {
            return false
        }

        // Check for significant range position change
        let currentCenter = (currentWeightRange.upperBound + currentWeightRange.lowerBound) / 2
        let lastCenter = (lastWeightRange.upperBound + lastWeightRange.lowerBound) / 2
        let centerChange = abs(currentCenter - lastCenter)

        // If center moved more than 50% of the range span, recalculate
        if centerChange > (max(currentSpan, lastSpan) * 0.5) {
            return false
        }

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
            return []
        }

        // Calculate dynamic metric range from actual data
        guard let metricMin = metricValues.min(),
              let metricMax = metricValues.max() else {
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

        return normalizedSeries
    }

    /// Generates normalized metric series using the provided Y-axis domain for consistency
    /// This ensures metric normalization matches the visible Y-axis range
    /// - Parameter operationsForYAxis: The same operations set used for Y-axis domain calculation
    ///                                (visibleOperations + bracketingOperations for non-total periods)
    private func generateNormalizedMetricSeriesWithDomain(
        for selectedMetric: String,
        from allOperations: [BathScaleWeightSummary],
        visibleOperations: [BathScaleWeightSummary],
        operationsForYAxis: [BathScaleWeightSummary],
        toWeightDomain yAxisDomain: ClosedRange<Double>,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Int) -> Double
    ) -> [GraphSeries] {

        // Use all operations to find any metric values for range calculation
        let allMetricValues = allOperations.compactMap { summary in
            getMetricValue(for: selectedMetric, from: summary)
        }

        guard !allMetricValues.isEmpty else {
            return []
        }

        // Use the same operations set that was used for Y-axis domain calculation
        // This ensures metric normalization is consistent with the Y-axis domain
        // For non-total periods, this should be visibleOperations + bracketingOperations
        // For total period, this should be allOperations
        let operationsForMetricRange = operationsForYAxis

        // Validate that operationsForYAxis matches expected pattern
        // Log warning if there's a potential mismatch (for debugging)
        let visibleAndBracketingMetricValues = operationsForMetricRange.compactMap { summary in
            getMetricValue(for: selectedMetric, from: summary)
        }

        // If no visible+bracketing metric values, use all metric values for range calculation
        let metricValues = visibleAndBracketingMetricValues.isEmpty ? allMetricValues : visibleAndBracketingMetricValues

        // Calculate dynamic metric range from visible data
        guard let metricMin = metricValues.min(),
              let metricMax = metricValues.max() else {
            return []
        }

        // Ensure we have some variation in the data
        let metricRange = metricMax - metricMin
        let effectiveMetricMin: Double
        let effectiveMetricMax: Double
        let isSingleMetricPoint = metricRange < 0.01 // Almost no variation (single point or minimal variation)

        if isSingleMetricPoint {
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

        // Generate normalized metric series ONLY for operations that actually have metric values
        // This avoids showing a point on dates where the metric is missing (nil)
        for summary in allOperations {
            guard let metricValue = getMetricValue(for: selectedMetric, from: summary) else {
                // Skip creating a point for missing metric values
                continue
            }

            // FIX: For single metric points, place them in the middle of the Y-axis domain
            // instead of normalizing against static range (which can place them at boundaries)
            if isSingleMetricPoint {
                // Place single metric point at a fixed position in the Y-axis range
                // This ensures it's visible and not touching top/bottom boundaries
                let yAxisSpan = weightMax - weightMin
                let positionInRange = weightMin + (yAxisSpan * Self.singleMetricPointYAxisPosition)
                normalizedSeries.append(GraphSeries(
                    date: summary.date,
                    value: positionInRange,
                    series: selectedMetric
                ))
                continue
            }

            // Use the actual metric value and map it into the dynamic y-axis domain
            let clampedValue = max(effectiveMetricMin, min(effectiveMetricMax, metricValue))

            let metricRangeSpan = effectiveMetricMax - effectiveMetricMin
            guard metricRangeSpan > 0 else {
                // If no metric variation, use middle of y-axis domain
                let mid = (weightMin + weightMax) / 2
                normalizedSeries.append(GraphSeries(
                    date: summary.date,
                    value: mid,
                    series: selectedMetric
                ))
                continue
            }

            let yAxisSpan = weightMax - weightMin
            let normalizedValue = weightMin + (clampedValue - effectiveMetricMin) * yAxisSpan / metricRangeSpan

            // Keep well inside bounds to account for edge buffers applied to Y-axis domain
            // Edge buffers extend the domain, so we use a more conservative margin (1.5% of span)
            // to ensure metrics stay within visible bounds even when domain has been extended
            let epsilon = yAxisSpan * 0.015 // 1.5% of y-axis span (increased from 0.1% to account for edge buffers)
            let safeMin = weightMin + epsilon
            let safeMax = weightMax - epsilon
            let clampedFinalValue = max(safeMin, min(safeMax, normalizedValue))

            guard clampedFinalValue.isFinite else {
                let mid = (weightMin + weightMax) / 2
                normalizedSeries.append(GraphSeries(
                    date: summary.date,
                    value: mid,
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

        return normalizedSeries
    }

    /// Interpolates a metric value for a date that doesn't have metric data
    /// Uses surrounding metric values to create a smooth line
    private func interpolateMetricValue(
        for targetDate: Date,
        from allOperations: [BathScaleWeightSummary],
        selectedMetric: String,
        effectiveMetricMin: Double,
        effectiveMetricMax: Double,
        weightMin: Double,
        weightMax: Double
    ) -> Double {
        // Find operations with metric values before and after the target date
        let operationsWithMetric = allOperations.compactMap { operation -> (Date, Double)? in
            guard let metricValue = getMetricValue(for: selectedMetric, from: operation) else { return nil }
            return (operation.date, metricValue)
        }.sorted { $0.0 < $1.0 }

        guard !operationsWithMetric.isEmpty else {
            // No metric data available - use middle of weight range
            return (weightMin + weightMax) / 2
        }

        // Find the closest metric values before and after target date
        let before = operationsWithMetric.last { $0.0 <= targetDate }
        let after = operationsWithMetric.first { $0.0 >= targetDate }

        let interpolatedMetricValue: Double

        if let before = before, let after = after, before.0 != after.0 {
            // Interpolate between before and after values
            let timeDiff = after.0.timeIntervalSince(before.0)
            let targetTimeDiff = targetDate.timeIntervalSince(before.0)
            let ratio = timeDiff > 0 ? targetTimeDiff / timeDiff : 0
            interpolatedMetricValue = before.1 + (after.1 - before.1) * ratio
        } else if let before = before {
            // Use the closest previous value
            interpolatedMetricValue = before.1
        } else if let after = after {
            // Use the closest next value
            interpolatedMetricValue = after.1
        } else {
            // Fallback to middle of range
            return (weightMin + weightMax) / 2
        }

        // Normalize the interpolated value to the weight range
        let clampedValue = max(effectiveMetricMin, min(effectiveMetricMax, interpolatedMetricValue))
        let metricRange = effectiveMetricMax - effectiveMetricMin

        guard metricRange > 0 else {
            return (weightMin + weightMax) / 2
        }

        let yAxisSpan = weightMax - weightMin
        let normalizedValue = weightMin + (clampedValue - effectiveMetricMin) * yAxisSpan / metricRange

        // Apply safe bounds
        let epsilon = yAxisSpan * 0.001
        let safeMin = weightMin + epsilon
        let safeMax = weightMax - epsilon

        return max(safeMin, min(safeMax, normalizedValue))
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
        // Set flag to prevent ensureLatestEntriesVisible from overriding position during period change
        isChangingPeriod = true

        state.selectedPeriod = period
        state.clearSelection()

        // Clear chart data cache when period changes
        clearChartDataCache()

        // Clear the flag after a brief delay to allow scroll position to be set
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            self.isChangingPeriod = false
        }
    }

    func getYAxisScale(from operations: [BathScaleWeightSummary], goalWeight: Double?, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double, chartHeight: CGFloat) -> YAxisScale {
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

    func calculateAndCacheYAxisDomain(from operations: [BathScaleWeightSummary], goalWeight: Double?, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double, chartHeight: CGFloat) {
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


    func getVisibleOperations(from operations: [BathScaleWeightSummary]) -> [BathScaleWeightSummary] {
        // During active scrolling, reuse cache ONLY if it still lies fully within the
        // current strict window boundaries. This avoids left-edge "lingering" where a
        // point remains visible after crossing the left boundary.
        if state.isScrolling && !lastCalculatedVisibleOps.isEmpty {
            let domainLength = visibleDomainLength(for: state.selectedPeriod)
            let leftEdge = state.xScrollPosition
            let rightEdge = state.xScrollPosition.addingTimeInterval(domainLength)

            // If scrolled past last entry, clear cache and recalculate
            let lastEntryDate = operations.last?.date
            if let lastEntryDate = lastEntryDate, leftEdge > lastEntryDate {
                // Scrolled past last entry - clear cache and recalculate to return empty
                lastCalculatedVisibleOps = []
                lastVisibleOpsScrollPosition = nil
                lastVisibleOpsPeriod = nil
                // Fall through to recalculation
            } else {
                // Apply the same buffer for cache validation
                let bufferTime: TimeInterval = 1 * 60 * 60 // 1 hour buffer
                let adjustedLeftEdge = leftEdge.addingTimeInterval(-bufferTime)
                let adjustedRightEdge = rightEdge.addingTimeInterval(bufferTime)

                // Use .first/.last for O(1) lookup on sorted array instead of O(n) map().min()/max()
                if let cachedMin = lastCalculatedVisibleOps.first?.date,
                   let cachedMax = lastCalculatedVisibleOps.last?.date,
                   cachedMin >= adjustedLeftEdge && cachedMax <= adjustedRightEdge {
                    // Cached set is still fully inside current window → safe to reuse
                    return lastCalculatedVisibleOps
                }
            }
            // Else fall through and recompute strictly for the new window
        }

        // Check if we can reuse cached result based on position change threshold
        if !lastCalculatedVisibleOps.isEmpty,
           let lastPosition = lastVisibleOpsScrollPosition,
           let lastPeriod = lastVisibleOpsPeriod,
           lastPeriod == state.selectedPeriod {
            let domainLength = visibleDomainLength(for: state.selectedPeriod)
            let positionChange = abs(state.xScrollPosition.timeIntervalSince(lastPosition))

            // Threshold-based caching: only recalculate if scroll moved > 10% of domain
            // This uses a 10% movement threshold for re-computation
            let cacheThreshold = domainLength / 10

            if positionChange < cacheThreshold {
                // Reuse only if cached content is still fully inside the current strict window
                let leftEdge = state.xScrollPosition
                let rightEdge = state.xScrollPosition.addingTimeInterval(domainLength)

                // Apply the same buffer for cache validation
                let bufferTime: TimeInterval = 1 * 60 * 60 // 1 hour buffer
                let adjustedLeftEdge = leftEdge.addingTimeInterval(-bufferTime)
                let adjustedRightEdge = rightEdge.addingTimeInterval(bufferTime)

                // If scrolled past last entry, clear cache and recalculate
                let lastEntryDate = operations.last?.date
                if let lastEntryDate = lastEntryDate, leftEdge > lastEntryDate {
                    // Scrolled past last entry - clear cache and recalculate to return empty
                    lastCalculatedVisibleOps = []
                    lastVisibleOpsScrollPosition = nil
                    lastVisibleOpsPeriod = nil
                    // Fall through to recalculation
                // Use .first/.last for O(1) lookup on sorted array instead of O(n) map().min()/max()
                } else if let cachedMin = lastCalculatedVisibleOps.first?.date,
                   let cachedMax = lastCalculatedVisibleOps.last?.date,
                   cachedMin >= adjustedLeftEdge && cachedMax <= adjustedRightEdge {
                    return lastCalculatedVisibleOps
                }
            }
        }

        // Use binary search for O(log n) performance on pre-sorted data
        // Operations are now pre-sorted by DashboardDataManager
        let leftEdge = state.xScrollPosition
        let domainLength = visibleDomainLength(for: state.selectedPeriod)
        let rightEdge = state.xScrollPosition.addingTimeInterval(domainLength)

        let minDate = operations.first?.date ?? leftEdge
        let maxDate = operations.last?.date ?? leftEdge

        // Add buffer to handle timezone edge cases and ensure entries on boundary dates are included
        let bufferTime: TimeInterval = 1 * 60 * 60 // 1 hour buffer
        let dayBufferTime: TimeInterval = 24 * 60 * 60 // 1 day buffer for midnight-local summaries
        let adjustedLeftEdge = leftEdge.addingTimeInterval(-dayBufferTime)
        let adjustedRightEdge = rightEdge.addingTimeInterval(bufferTime)

        // Use binary search helpers for O(log n) lookup instead of O(n) filter
        guard let startIndex = binarySearchFirstIndex(in: operations, where: { $0.date >= adjustedLeftEdge }),
              let endIndex = binarySearchLastIndex(in: operations, where: { $0.date <= adjustedRightEdge }) else {
            // No operations in range - clear cache and return empty
            lastCalculatedVisibleOps = []
            lastVisibleOpsScrollPosition = state.xScrollPosition
            lastVisibleOpsPeriod = state.selectedPeriod
            return []
        }

        // Validate index range
        guard startIndex <= endIndex else {
            lastCalculatedVisibleOps = []
            lastVisibleOpsScrollPosition = state.xScrollPosition
            lastVisibleOpsPeriod = state.selectedPeriod
            return []
        }

        let visibleOps = Array(operations[startIndex...endIndex])

        // Update cache
        lastCalculatedVisibleOps = visibleOps
        lastVisibleOpsScrollPosition = state.xScrollPosition
        lastVisibleOpsPeriod = state.selectedPeriod

        return visibleOps
    }

    /// Returns operations strictly within the on-screen visible domain (no buffer)
    /// Uses the chart's configured visible domain length starting at the current left edge (xScrollPosition).
    /// Uses binary search for O(log n) performance on pre-sorted data.
    func getStrictVisibleOperations(from operations: [BathScaleWeightSummary]) -> [BathScaleWeightSummary] {
        guard !operations.isEmpty else { return [] }

        let minDate = operations.first?.date
        let maxDate = operations.last?.date
        guard let minDate = minDate, let maxDate = maxDate else { return [] }

        let domainLength = visibleDomainLength(for: state.selectedPeriod)
        let start = max(state.xScrollPosition, minDate)
        let end = min(state.xScrollPosition.addingTimeInterval(domainLength), maxDate)

        guard start <= end else {
            return []
        }

        // Use binary search for O(log n) performance on pre-sorted data
        guard let startIndex = binarySearchFirstIndex(in: operations, where: { $0.date >= start }),
              let endIndex = binarySearchLastIndex(in: operations, where: { $0.date <= end }) else {
            return []
        }

        guard startIndex <= endIndex else {
            return []
        }

        let strictlyVisible = Array(operations[startIndex...endIndex])

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

        // Use binary search for O(log n) performance instead of O(n) filter
        // Find the last operation at or before the left edge
        var previous: BathScaleWeightSummary?
        if let lastBeforeIdx = binarySearchLastIndex(in: operations, where: { $0.date <= leftEdge }) {
            previous = operations[lastBeforeIdx]
        }

        // Find the first operation at or after the right edge
        var next: BathScaleWeightSummary?
        if let firstAfterIdx = binarySearchFirstIndex(in: operations, where: { $0.date >= rightEdge }) {
            next = operations[firstAfterIdx]
        }

        // Fallback using binary search if not found on one side
        let fallbackPrev: BathScaleWeightSummary?
        if let prev = previous {
            fallbackPrev = prev
        } else if let lastBeforeRightIdx = binarySearchLastIndex(in: operations, where: { $0.date < rightEdge }) {
            fallbackPrev = operations[lastBeforeRightIdx]
        } else {
            fallbackPrev = nil
        }

        let fallbackNext: BathScaleWeightSummary?
        if let nxt = next {
            fallbackNext = nxt
        } else if let firstAfterLeftIdx = binarySearchFirstIndex(in: operations, where: { $0.date > leftEdge }) {
            fallbackNext = operations[firstAfterLeftIdx]
        } else {
            fallbackNext = nil
        }

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
    }

    func ensureLatestEntriesVisible(from operations: [BathScaleWeightSummary]) {
        // Use .last for O(1) lookup on sorted array instead of O(n) map().max()
        guard operations.last?.date != nil else {
            return
        }
        guard !state.isScrolling else {
            return
        }
        // Prevent overriding scroll position during period changes
        // This ensures the optimal position set during period change is not overridden
        guard !isChangingPeriod else {
            return
        }
        // Use the same optimal scroll position calculation as initialization
        // This ensures consistent snapping behavior and prevents extra days being added
        // Use .first/.last for O(1) lookup on sorted array instead of O(n) map().min()/max()
        let cachedBounds: (min: Date, max: Date)? = {
            guard let minDate = operations.first?.date,
                  let maxDate = operations.last?.date else {
                return nil
            }
            return (min: minDate, max: maxDate)
        }()
        let optimalPosition = calculateOptimalScrollPosition(
            for: state.selectedPeriod,
            from: operations,
            anchorDate: nil,
            showingLatest: true,
            cachedBounds: cachedBounds
        )
        updateScrollPosition(to: optimalPosition)
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
            return summary.visceralFatLevel.map { $0 / 10.0 }
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
        let minDate = operations.first?.date ?? scrollPosition
        let maxDate = operations.last?.date ?? scrollPosition

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
            let leftEdge = scrollPosition
            let rightEdge = scrollPosition.addingTimeInterval(domainLength)
            let nearLeft = leftEdge <= minDate.addingTimeInterval(domainLength / 4.0)
            let nearRight = rightEdge >= maxDate.addingTimeInterval(-domainLength / 4.0)

            if !(movedFar || nearLeft || nearRight) {
                return lastXAxisValues
            }
        }

        let domainLength: TimeInterval = visibleDomainLength(for: period)
        guard let overallMinDate = operations.first?.date,
              let overallMaxDate = operations.last?.date else {
            return []
        }
        let buffer: TimeInterval = domainLength * 2.0
        let currentDate = Date()

        // Calculate additional buffer for X-axis ticks when entries fall on calendar boundaries
        let calendar = Calendar.current
        var minDateBuffer: TimeInterval = 0
        var maxDateBuffer: TimeInterval = 0

        switch period {
        case .week:
            // Add extra week buffer if minDate is the first day of the week
            let weekday = calendar.component(.weekday, from: overallMinDate)
            if weekday == calendar.firstWeekday {
                minDateBuffer = DashboardConstants.TimeInterval.week
            }
            // Align max to end of week
            let maxWeekday = calendar.component(.weekday, from: overallMaxDate)
            let maxDaysFromStart = (maxWeekday - calendar.firstWeekday + 7) % 7
            maxDateBuffer = TimeInterval(6 - maxDaysFromStart) * DashboardConstants.TimeInterval.day

        case .month:
            // Add extra month buffer if minDate is the first day of the month
            let day = calendar.component(.day, from: overallMinDate)
            if day == 1 {
                minDateBuffer = DashboardConstants.TimeInterval.month
            }
            // Align max to end of month
            if let range = calendar.range(of: .day, in: .month, for: overallMaxDate) {
                let maxDay = calendar.component(.day, from: overallMaxDate)
                maxDateBuffer = TimeInterval(range.count - maxDay) * DashboardConstants.TimeInterval.day
            }

        case .year:
            // Add extra year buffer if minDate is in January
            let month = calendar.component(.month, from: overallMinDate)
            if month == 1 {
                minDateBuffer = DashboardConstants.TimeInterval.year
            }
            // Align max to end of year
            let maxMonth = calendar.component(.month, from: overallMaxDate)
            maxDateBuffer = TimeInterval(12 - maxMonth) * DashboardConstants.TimeInterval.month

        case .total:
            break
        }

        // For year view: display all X-axis ticks for the entire time period
        // For other views: use scroll-based calculation
        let visibleStart: Date
        let visibleEnd: Date

  
        let adjustedMinDate = overallMinDate.addingTimeInterval(-minDateBuffer)
        let adjustedMaxDate = overallMaxDate.addingTimeInterval(maxDateBuffer)

        // Calculate data span to decide which domain strategy to use
        let dataSpan = overallMaxDate.timeIntervalSince(overallMinDate)
        let oneYearInterval = DashboardConstants.TimeInterval.year
        let useFixedDomain = period == .year ? dataSpan <= 5 * oneYearInterval : dataSpan <= oneYearInterval

        if useFixedDomain {
            // This ensures the domain remains constant during scrolling, preventing "shooting" behavior
            // Calculate end of current calendar period for extending the domain
            let currentPeriodEnd: Date
            switch period {
            case .week:
                // End of current week (Saturday at noon)
                let currentWeekday = calendar.component(.weekday, from: currentDate)
                let daysUntilSaturday = (7 - currentWeekday + 7) % 7
                if let saturdayStart = calendar.date(byAdding: .day, value: daysUntilSaturday, to: calendar.startOfDay(for: currentDate)),
                   let saturdayNoon = calendar.date(byAdding: .hour, value: 12, to: saturdayStart) {
                    currentPeriodEnd = saturdayNoon
                } else {
                    currentPeriodEnd = currentDate
                }
            case .month:
                // Add extra month buffer
                minDateBuffer = DashboardConstants.TimeInterval.month
                // End of current month
                if let monthInterval = calendar.dateInterval(of: .month, for: currentDate) {
                    currentPeriodEnd = monthInterval.end.addingTimeInterval(-1)
                } else {
                    currentPeriodEnd = currentDate
                }
            case .year, .total:
                // End of current year
                if let yearInterval = calendar.dateInterval(of: .year, for: currentDate) {
                    currentPeriodEnd = yearInterval.end.addingTimeInterval(-1)
                } else {
                  currentPeriodEnd = currentDate
                }
            }

            visibleStart = adjustedMinDate
            let extendedMaxDate = max(adjustedMaxDate, currentPeriodEnd)
            visibleEnd = extendedMaxDate
        } else {
            // For data spanning more than 1 year: use scroll-based bounds for performance
            let scrollBasedEnd = scrollPosition.addingTimeInterval(domainLength / 2 + buffer)
            visibleStart = max(adjustedMinDate, scrollPosition.addingTimeInterval(-domainLength / 2 - buffer))

            if adjustedMaxDate > currentDate {
                visibleEnd = min(adjustedMaxDate, scrollBasedEnd)
            } else if adjustedMaxDate < currentDate.addingTimeInterval(-domainLength * 3) {
                visibleEnd = max(currentDate, scrollBasedEnd)
            } else {
                visibleEnd = min(currentDate, scrollBasedEnd)
            }
        }
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
    /// - Parameters:
    ///   - period: Target time period
    ///   - operations: Data operations for bounds calculation
    ///   - anchorDate: Optional anchor date to center the viewport around (for preserving temporal context)
    ///   - showingLatest: Whether to show latest entries (ignored if anchorDate is provided)
    ///   - cachedBounds: Optional cached date bounds for O(1) lookup performance
    /// - Returns: Optimal scroll position for the given parameters
    func calculateOptimalScrollPosition(
        for period: TimePeriod,
        from operations: [BathScaleWeightSummary],
        anchorDate: Date? = nil,
        showingLatest: Bool = true,
        cachedBounds: (min: Date, max: Date)? = nil
    ) -> Date {
        // Use cached bounds if provided for performance, otherwise calculate from operations
        let overallMinDate: Date
        let overallMaxDate: Date

        if let cached = cachedBounds {
            overallMinDate = cached.min
            overallMaxDate = cached.max
        } else {
            let allDates: [Date] = operations.map { $0.date }
            guard let minDate = allDates.min(), let maxDate = allDates.max() else {
                return Date()
            }
            overallMinDate = minDate
            overallMaxDate = maxDate
        }

        // For .total period, always return overallMinDate to show all data from the beginning
        if period == .total {
            return overallMinDate
        }

        let domainLength = visibleDomainLength(for: period)

        // If anchor date is provided, center the viewport around it (temporal context preservation)
        if let anchor = anchorDate {
            // Calculate scroll position that centers the anchor in the viewport
            // scrollPosition is the LEFT edge, so subtract half domain length from anchor
            var targetScrollPosition = anchor.addingTimeInterval(-domainLength / 2)

            // Skip snapping to preserve exact centering.
            // Snapping can shift the position significantly (e.g., up to 2 weeks for year view).

            // However, we DO want to shift back if the viewport lands in a region with no data.
            // Check if the viewport's right edge extends past the latest data entry.
            let viewportEnd = targetScrollPosition.addingTimeInterval(domainLength)

            // Add a small buffer past the latest entry for visual comfort
            let bufferPastLatest: TimeInterval
            switch period {
            case .week:
                bufferPastLatest = 2 * DashboardConstants.TimeInterval.day
            case .month:
                bufferPastLatest = DashboardConstants.TimeInterval.week
            case .year:
                bufferPastLatest = DashboardConstants.TimeInterval.month
            case .total:
                bufferPastLatest = 0
            }

            // The max allowed end for data visibility is the latest data + buffer
            // (Calendar boundaries are for X-axis display, but we want to show actual data when zooming)
            let maxDataEnd = overallMaxDate.addingTimeInterval(bufferPastLatest)

            if viewportEnd > maxDataEnd {
                // Shift the viewport back so the right edge aligns with the latest data + buffer
                let adjustment = viewportEnd.timeIntervalSince(maxDataEnd)
                targetScrollPosition = targetScrollPosition.addingTimeInterval(-adjustment)
            }

            // Also ensure we don't go too far into the past (left boundary)
            let minScrollPosition = overallMinDate.addingTimeInterval(-domainLength * 0.1)
            if targetScrollPosition < minScrollPosition {
                targetScrollPosition = minScrollPosition
            }

            return targetScrollPosition
        }

        if showingLatest {
            let latestEntry = overallMaxDate
            let domainLength = visibleDomainLength(for: period)
            let scrollPosition: Date

            switch period {
            case .week:
                let rightEdgeWithBuffer = latestEntry.addingTimeInterval(2 * DashboardConstants.TimeInterval.day)
                scrollPosition = rightEdgeWithBuffer.addingTimeInterval(-domainLength)

            case .month:
                let rightEdgeWithBuffer = latestEntry.addingTimeInterval(DashboardConstants.TimeInterval.week)
                scrollPosition = rightEdgeWithBuffer.addingTimeInterval(-domainLength)

            case .year:
                let rightEdgeWithBuffer = latestEntry.addingTimeInterval(DashboardConstants.TimeInterval.month)
                scrollPosition = rightEdgeWithBuffer.addingTimeInterval(-domainLength)

            case .total:
                scrollPosition = overallMinDate
            }

            return scrollPosition
        } else {
            return overallMinDate
        }
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

        // Iterate week-by-week with calendar math to avoid drift from non-calendar viewport widths.
        var currentWeekStart = weekStartForOldest
        while currentWeekStart < weekEndExclusive {
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
            guard let nextWeek = cal.date(byAdding: .weekOfYear, value: 1, to: currentWeekStart) else {
                break
            }
            currentWeekStart = nextWeek
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
        var sundayCalendar = Calendar(identifier: .gregorian)
        sundayCalendar.timeZone = calendar.timeZone
        sundayCalendar.locale = calendar.locale
        sundayCalendar.firstWeekday = 1

        for monthOffset in 0..<totalMonths {
            guard let currentMonthStart = sundayCalendar.date(byAdding: .month, value: monthOffset, to: monthStartForOldest),
                  let currentMonthEnd = sundayCalendar.dateInterval(of: .month, for: currentMonthStart)?.end else {
                continue
            }

            let monthInterval = DateInterval(start: currentMonthStart, end: currentMonthEnd)
            let monthTicks = DateTimeTools.sundayTicksForMonth(
                in: monthInterval,
                baseCalendar: calendar,
                includeTrailingPhantom: false
            )
            dates.append(contentsOf: monthTicks.filter { $0 >= monthStartForOldest && $0 <= monthEndForLatest })
        }

        // Append one trailing phantom weekly tick so the last real month section remains selectable
        // (e.g., in Feb 2025, keep the section after Feb 22/23 through month end).
        if let last = dates.max() {
            if let nextSunday = sundayCalendar.date(byAdding: .weekOfYear, value: 1, to: last),
               let phantomNoon = sundayCalendar.date(bySettingHour: 12, minute: 0, second: 0, of: nextSunday) {
                dates.append(phantomNoon)
            }
        }
        return dates
    }

    private func generateVisibleYearlyXAxisWithBuffer(visibleStart: Date, visibleEnd: Date, shouldRepeat: Bool) -> [Date] {
        // Ensure dates are in correct order to prevent range errors
        let startDate = min(visibleStart, visibleEnd)
        let endDate = max(visibleStart, visibleEnd)

        // Use the same calendar as yearly snapping so rendered ticks and snap targets align.
        let cal = yearlyTickCalendar

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


    func formatSelectedDate(_ date: Date, for period: TimePeriod) -> String {
        // Use cached formatter from DateTimeTools instead of creating new DateFormatter each call
        switch period {
        case .week, .month:
            return DateTimeTools.formatter("MMM d, yyyy").string(from: date)
        case .year, .total:
            return DateTimeTools.formatter("MMM yyyy").string(from: date)
        }
    }

    func formatDateRange(minDate: Date, maxDate: Date, for period: TimePeriod) -> String {
        let calendar = Calendar.current
        // Normalize order to avoid inverted labels when inputs are swapped or nudged
        var startDate = min(minDate, maxDate)
        var endDate = max(minDate, maxDate)

        startDate = calendar.startOfDay(for: startDate)
        endDate = calendar.startOfDay(for: endDate)

        // Special handling for TOTAL: if the dataset spans only one month, show just "Sep 2025"
        if period == .total {
            if calendar.isDate(startDate, equalTo: endDate, toGranularity: .month) {
                return DateTimeTools.formatter("MMM yyyy").string(from: startDate)
            }
            // Multi-month: "MMM yyyy – MMM yyyy"
            let fmt = DateTimeTools.formatter("MMM yyyy")
            return "\(fmt.string(from: startDate)) – \(fmt.string(from: endDate))"
        }

        // Special handling for month: show range if spanning months, otherwise show single month
        if period == .month {
            let startYear = calendar.component(.year, from: startDate)
            let startMonth = calendar.component(.month, from: startDate)
            let endYear = calendar.component(.year, from: endDate)
            let endMonth = calendar.component(.month, from: endDate)

            // If start and end are in the same month, show just "MMM yyyy"
            if startYear == endYear && startMonth == endMonth {
                return DateTimeTools.formatter("MMM yyyy").string(from: startDate)
            }

            // Cross-year: "MMM d, yyyy – MMM d, yyyy"
            if startYear != endYear {
                let fmt = DateTimeTools.formatter("MMM d, yyyy")
                return "\(fmt.string(from: startDate)) – \(fmt.string(from: endDate))"
            }

            // Cross-month within same year: "MMM d – MMM d, yyyy"
            let startFmt = DateTimeTools.formatter("MMM d")
            let endFmt = DateTimeTools.formatter("MMM d, yyyy")
            return "\(startFmt.string(from: startDate)) – \(endFmt.string(from: endDate))"
        }

        // For year: show range only if spanning different years, otherwise show single year
        if period == .year {
            let startYear = calendar.component(.year, from: startDate)
            let endYear = calendar.component(.year, from: endDate)

            // If start and end are in the same year, show just the year (e.g., "2026")
            if startYear == endYear {
                return DateTimeTools.formatter("yyyy").string(from: startDate)
            }

            // Different years: show range "MMM yyyy – MMM yyyy"
            let startFmt = DateTimeTools.formatter("MMM yyyy")
            return "\(startFmt.string(from: startDate)) – \(startFmt.string(from: endDate))"
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
        case .week, .month, .year, .total:
            // Use visible-window average for all periods to keep display behavior consistent.
            let weights = allOps.map { convertWeight(Int($0.weight)) }
            guard !weights.isEmpty else { return nil }
            let averageWeight = weights.reduce(0, +) / Double(weights.count)
            let weightlessValue = averageWeight - anchorWeight
            // Apply same rounding logic as other weight calculations
            return (weightlessValue * 100).rounded(.toNearestOrAwayFromZero) / 100
        }
    }

    /// Generates sample dates for the visible range based on the time period
    /// These correspond to the vertical lines that are selectable in the UI
    func generateSampleDatesForVisibleRange(for period: TimePeriod) -> [Date] {
        guard period != .total else { return [] }

        let leftEdge = state.xScrollPosition
        let domainLength = visibleDomainLength(for: period)
        let rightEdge = leftEdge.addingTimeInterval(domainLength)

        var sampleDates: [Date] = []

        switch period {
        case .week:
            // Daily samples - generate samples within the visible range directly
            var currentDate = leftEdge

            while currentDate <= rightEdge {
                sampleDates.append(currentDate)
                if let nextDay = calendar.date(byAdding: .day, value: 1, to: currentDate) {
                    currentDate = nextDay
                } else {
                    break // If date addition fails, exit loop
                }
            }

        case .month:
            // Weekly samples - generate samples within the visible range directly
            // Instead of using month boundaries, generate weekly samples within the visible window
            var currentDate = leftEdge

            while currentDate <= rightEdge {
                sampleDates.append(currentDate)
                if let nextWeek = calendar.date(byAdding: .weekOfYear, value: 1, to: currentDate) {
                    currentDate = nextWeek
                } else {
                    break // If date addition fails, exit loop
                }
            }

        case .year:
            // Monthly samples - generate samples within the visible range directly
            var currentDate = leftEdge

            while currentDate <= rightEdge {
                sampleDates.append(currentDate)
                if let nextMonth = calendar.date(byAdding: .month, value: 1, to: currentDate) {
                    currentDate = nextMonth
                } else {
                    break // If date addition fails, exit loop
                }
            }

        case .total:
            // Not applicable for total view
            break
        }

        return sampleDates
    }

    /// Calculates the average of interpolated weights for the visible range when no entries are visible
    /// This provides a meaningful weight display even when the visible window contains no actual data points
    /// Only interpolates within the actual data boundaries, returns nil if visible range is outside graph line
    func calculateInterpolatedAverageForVisibleRange(
        from allOperations: [BathScaleWeightSummary],
        period: TimePeriod,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Int) -> Double,
        labelRange: DateInterval? = nil
    ) -> Double? {
        // Only apply to non-total periods
        guard period != .total, !allOperations.isEmpty else {
            return nil
        }

        // Determine the actual data boundaries (start and end of graph line)
        let sortedOperations = allOperations.sorted { $0.date < $1.date }
        guard let firstDataPoint = sortedOperations.first?.date,
              let lastDataPoint = sortedOperations.last?.date else {
            return nil
        }

        let sampleDates = generateSampleDatesForVisibleRange(for: period)
        guard !sampleDates.isEmpty else {
            return nil
        }

        // Filter sample dates to only include those within the actual data boundaries
        // This prevents interpolation outside the graph line range
        // Add a small buffer (1 hour) to account for timezone differences and boundary precision
        let bufferTime: TimeInterval = 60 * 60 // 1 hour buffer
        let effectiveStartBoundary = firstDataPoint.addingTimeInterval(-bufferTime)
        let effectiveEndBoundary = lastDataPoint.addingTimeInterval(bufferTime)

        var validSampleDates = sampleDates.filter { sampleDate in
            sampleDate >= effectiveStartBoundary && sampleDate <= effectiveEndBoundary
        }

        // If a label range is provided, only include samples inside it.
        if let labelRange {
            validSampleDates = validSampleDates.filter { sampleDate in
                sampleDate >= labelRange.start && sampleDate <= labelRange.end
            }
        }

        // If no sample dates fall within the data boundaries, return nil
        // This means the visible range is completely outside the graph line
        guard !validSampleDates.isEmpty else {
            return nil
        }

        var interpolatedWeights: [Double] = []

        for sampleDate in validSampleDates {
            if let interpolatedWeight = interpolatedDisplayWeight(
                at: sampleDate,
                from: allOperations,
                isWeightlessMode: isWeightlessMode,
                anchorWeight: anchorWeight,
                convertWeight: convertWeight
            ) {
                interpolatedWeights.append(interpolatedWeight)
            }
        }

        guard !interpolatedWeights.isEmpty else {
            return nil
        }

        let average = interpolatedWeights.reduce(0, +) / Double(interpolatedWeights.count)

        // Apply same rounding logic as other weight calculations
        let roundedAverage = (average * 100).rounded(.toNearestOrAwayFromZero) / 100

        return roundedAverage
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

        // Apply same robust rounding logic as other weight calculations
        let roundedAverage = (average * 100).rounded(.toNearestOrAwayFromZero) / 100
        return roundedAverage
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
    /// During scroll, we're very aggressive about using cache to prevent CPU spikes
    private func canUseCachedChartData(
        operationsCount: Int,
        yAxisDomain: ClosedRange<Double>,
        selectedMetric: String?
    ) -> Bool {
        // Must have cached data
        guard !cachedChartSeriesData.isEmpty else { return false }

        // Operations count must match (data hasn't changed)
        guard operationsCount == lastCachedOperationsCount else { return false }

        // Selected metric must match
        guard selectedMetric == lastCachedSelectedMetric else { return false }

        // During scrolling, ALWAYS use cache regardless of Y-axis changes
        // Y-axis domain changes are visual only and shouldn't trigger regeneration
        // This prevents the main CPU spike during scroll
        if state.isScrolling {
            return true
        }

        // When not scrolling, Y-axis domain must be similar (within 10% tolerance)
        guard let lastDomain = lastCachedYAxisDomain else { return false }

        let domainDifference = abs(yAxisDomain.lowerBound - lastDomain.lowerBound) +
        abs(yAxisDomain.upperBound - lastDomain.upperBound)
        let domainSize = max(yAxisDomain.upperBound - yAxisDomain.lowerBound, 0.1)
        let tolerance = domainSize * 0.10 // 10% tolerance (was 5%)

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
    /// Only applies to very large datasets to maintain visual quality
    private func simplifyDataForScrolling(_ operations: [BathScaleWeightSummary]) -> [BathScaleWeightSummary] {
        // Only simplify for VERY large datasets (>2000 points)
        // For most users, the performance benefit isn't worth the visual degradation
        let simplificationThreshold = 2000
        let maxPointsDuringScroll = 500 // Keep enough points for smooth visual appearance

        guard operations.count > simplificationThreshold else {
            return operations
        }

        let step = max(1, operations.count / maxPointsDuringScroll)
        var simplifiedOps: [BathScaleWeightSummary] = []

        for i in stride(from: 0, to: operations.count, by: step) {
            simplifiedOps.append(operations[i])
        }

        // Always include the last point to maintain chart continuity
        if let lastOp = operations.last, !simplifiedOps.contains(where: { $0.date == lastOp.date }) {
            simplifiedOps.append(lastOp)
        }

        return simplifiedOps
    }

    // MARK: - Windowed Chart Data (Performance Optimization)

    /// Gets operations for chart rendering with large buffer to prevent blank pages during scroll.
    /// Uses binary search for O(log n) performance on sorted arrays.
    /// - Parameters:
    ///   - allOperations: All available operations (must be pre-sorted by date)
    ///   - scrollPosition: Current scroll position
    ///   - period: Current time period
    /// - Returns: Subset of operations within visible window + buffer
    func getChartOperationsWithBuffer(
        from allOperations: [BathScaleWeightSummary],
        scrollPosition: Date,
        period: TimePeriod
    ) -> [BathScaleWeightSummary] {
        guard !allOperations.isEmpty else { return [] }

        // Only apply windowing for very large datasets (>2000 points, ~5+ years of daily data)
        // For typical datasets, the overhead of windowing isn't worth the visual complexity
        // This prevents any visual glitches and chart discontinuities
        let windowingThreshold = 2000
        if allOperations.count < windowingThreshold {
            return allOperations
        }

        let domainLength = visibleDomainLength(for: period)

        // scrollPosition is the LEFT edge of the visible window
        // Visible window: [scrollPosition, scrollPosition + domainLength]
        // Use very generous buffer (5x on each side) to ensure smooth scrolling
        let bufferSize = domainLength * 5.0

        let windowStart = scrollPosition.addingTimeInterval(-bufferSize)
        let windowEnd = scrollPosition.addingTimeInterval(domainLength + bufferSize)

        // Binary search for efficiency on sorted array - O(log n) instead of O(n)
        guard let startIndex = binarySearchFirstIndex(in: allOperations, where: { $0.date >= windowStart }),
              let endIndex = binarySearchLastIndex(in: allOperations, where: { $0.date <= windowEnd }) else {
            // Fallback: return all if search fails
            return allOperations
        }

        // Include extra points on each side for line continuity at buffer edges
        let safeStartIndex = max(0, startIndex - 5)
        let safeEndIndex = min(allOperations.count - 1, endIndex + 5)

        // Validate index range
        guard safeStartIndex <= safeEndIndex else { return allOperations }

        let windowedOperations = Array(allOperations[safeStartIndex...safeEndIndex])

        // If windowing only saves <30% of data, not worth the complexity - return all
        let savingsRatio = Double(allOperations.count - windowedOperations.count) / Double(allOperations.count)
        if savingsRatio < 0.3 {
            return allOperations
        }

        return windowedOperations
    }

    // MARK: - Binary Search Helpers

    /// Binary search to find first index where predicate is true.
    /// Assumes array is sorted and predicate transitions from false to true.
    /// - Parameters:
    ///   - operations: Sorted array to search
    ///   - predicate: Condition to find first true occurrence
    /// - Returns: First index where predicate is true, or nil if never true
    private func binarySearchFirstIndex(
        in operations: [BathScaleWeightSummary],
        where predicate: (BathScaleWeightSummary) -> Bool
    ) -> Int? {
        guard !operations.isEmpty else { return nil }

        var low = 0
        var high = operations.count

        while low < high {
            let mid = (low + high) / 2
            if predicate(operations[mid]) {
                high = mid
            } else {
                low = mid + 1
            }
        }

        return low < operations.count ? low : nil
    }

    /// Binary search to find last index where predicate is true.
    /// Assumes array is sorted and predicate transitions from true to false.
    /// - Parameters:
    ///   - operations: Sorted array to search
    ///   - predicate: Condition to find last true occurrence
    /// - Returns: Last index where predicate is true, or nil if never true
    private func binarySearchLastIndex(
        in operations: [BathScaleWeightSummary],
        where predicate: (BathScaleWeightSummary) -> Bool
    ) -> Int? {
        guard !operations.isEmpty else { return nil }

        var low = 0
        var high = operations.count

        while low < high {
            let mid = (low + high) / 2
            if predicate(operations[mid]) {
                low = mid + 1
            } else {
                high = mid
            }
        }

        return low > 0 ? low - 1 : nil
    }

    // MARK: - Scroll Boundary Enforcement

    /// Enforces scroll boundaries to prevent over-scrolling beyond data range.
    /// - Parameters:
    ///   - position: Requested scroll position
    ///   - period: Current time period
    ///   - minDate: Minimum date in dataset
    ///   - maxDate: Maximum date in dataset
    /// - Returns: Clamped position within valid scroll range
    func clampScrollPosition(
        _ position: Date,
        for period: TimePeriod,
        minDate: Date,
        maxDate: Date
    ) -> Date {
        let domainLength = visibleDomainLength(for: period)

        // Left boundary: period-aware padding to allow viewing the full calendar period
        // containing the first entry (e.g., full week containing Nov 1)
        let padding: TimeInterval
        switch period {
        case .week:
            padding = DashboardConstants.TimeInterval.week // 1 week before first entry
        case .month:
            padding = DashboardConstants.TimeInterval.month // ~1 month before first entry
        case .year:
            padding = DashboardConstants.TimeInterval.year // 12 months before first entry
        case .total:
            padding = 0
        }
        let minScrollPosition = minDate.addingTimeInterval(-padding)

        let maxScrollPosition = maxDate.addingTimeInterval(padding)

        // Handle edge case where data range is smaller than visible domain
        if minScrollPosition >= maxScrollPosition {
            return minDate
        }

        return max(minScrollPosition, min(maxScrollPosition, position))
    }

    // MARK: - Smart Snap Mechanism

    /// Snaps scroll position to nearest valid alignment point for the given period.
    /// - Parameters:
    ///   - position: Current scroll position
    ///   - period: Current time period
    /// - Returns: Snapped position aligned to period's grid
    func snapScrollPosition(_ position: Date, for period: TimePeriod) -> Date {
        let calendar = Calendar.current

        switch period {
        case .week:
            // Snap to start of day (noon for plotting consistency)
            var components = calendar.dateComponents([.year, .month, .day], from: position)
            components.hour = 12
            components.minute = 0
            components.second = 0
            return calendar.date(from: components) ?? position

        case .month:
            // Snap to start of week
            let weekday = calendar.component(.weekday, from: position)
            let daysToSubtract = (weekday - calendar.firstWeekday + 7) % 7
            guard let snappedDate = calendar.date(byAdding: .day, value: -daysToSubtract, to: position) else {
                return position
            }
            // Also set to start of day
            var components = calendar.dateComponents([.year, .month, .day], from: snappedDate)
            components.hour = 0
            components.minute = 0
            components.second = 0
            return calendar.date(from: components) ?? position

        case .year:
            // Snap to month tick (day 1 at local noon) to match yearly X-axis grid lines.
            let cal = yearlyTickCalendar
            var components = cal.dateComponents([.year, .month], from: position)
            components.day = 1
            components.hour = 12
            components.minute = 0
            components.second = 0
            return cal.date(from: components) ?? position

        case .total:
            // No snapping for total view (not scrollable)
            return position
        }
    }
}
