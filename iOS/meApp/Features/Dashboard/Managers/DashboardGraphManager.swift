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

    // MARK: - Performance Optimization - Simple Scroll State Check
    private var lastCalculatedVisibleOps: [BathScaleWeightSummary] = []
    private var lastVisibleOpsScrollPosition: Date?
    private var lastVisibleOpsPeriod: TimePeriod?

    // Simple chart data optimization
    private var lastChartData: [GraphSeries] = []

    // Store scroll position during scroll, update state only at end
    private var latestScrollPosition: Date?

    // Store last Y-axis scale for fallback when no data
    private var lastYAxisScale: YAxisScale?

    // Add caching for x-axis values during scroll
    private var lastXAxisValues: [Date] = []
    private var lastXAxisScrollPosition: Date?
    private var lastXAxisPeriod: TimePeriod?

    // MARK: - Initialization
    init(initialState: GraphState = GraphState()) {
        self.state = initialState
    }

    // MARK: - Scroll Management
    func updateScrollPosition(to date: Date) async {
        // Only update position if we're not actively scrolling or recalculating
        // This prevents overriding user scroll gestures
        guard !state.isScrolling else {
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "Ignoring position update during scroll/recalculation: \(date)")
            return
        }

        // Update position for programmatic changes only
        state.xScrollPosition = date
    }

    /// Handle scroll position changes - only store during scroll, update at end
    func handleScrollPositionChange(_ newPosition: Date?) async {
        guard let newPosition = newPosition else { return }

        if state.isScrolling {
            // During scroll: only store the position, don't update the state
            latestScrollPosition = newPosition
        } else {

            latestScrollPosition = nil
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "Updated scroll position immediately: \(newPosition)")
        }
    }

    func handleScrollStart() async {
        guard !state.isScrolling else { return }

        state.isScrolling = true

        // Clear selection when scrolling starts
        state.clearSelection()

        logger.log(level: .debug, tag: "DashboardGraphManager", message: "Scroll started")
    }

    func handleChartSelection(at selectedDate: Date?) async {
        // Only handle selection if not currently scrolling
        guard !state.isScrolling else { return }

        // If no date provided, clear selection
        guard let selectedDate = selectedDate else {
            state.clearSelection()
            logger.log(level: .info, tag: "DashboardGraphManager", message: "Chart selection cleared")
            return
        }

        // Hide any existing crosshair first
        state.showCrosshair = false

        // This method should be called with the continuous operations from the data manager
        // For now, we'll just update the selected date
        state.selectedXValue = selectedDate

        // Force UI update
        DispatchQueue.main.async {
            self.state.showCrosshair = true
        }

        logger.log(level: .info, tag: "DashboardGraphManager", message: "Chart selection handled at date: \(selectedDate)")
    }

    @available(iOS 18.0, *)
    func handleScrollPhaseChange(_ phase: ScrollPhase) async {
        switch phase {
        case .idle:
            // No scrolling is occurring - NOW compute visible operations and Y-axis domain
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
            // User is touching but hasn't started scrolling yet
            state.hasDetectedScrollInCurrentGesture = false

        case .interacting:
            // User is actively scrolling - do NOT compute domain
            if !state.hasDetectedScrollInCurrentGesture {
                state.hasDetectedScrollInCurrentGesture = true
                state.updateScrollState(isScrolling: true)
            }

        case .decelerating:
            // User stopped scrolling, chart is decelerating to final position - do NOT compute domain yet
            state.updateScrollState(isScrolling: true)

        case .animating:
            // System is animating to a final target (programmatic scroll) - do NOT compute domain yet
            state.updateScrollState(isScrolling: true)

        @unknown default:
            // Handle any future cases
            logger.log(level: .info, tag: "DashboardGraphManager", message: "Unknown scroll phase encountered")
        }
    }

    func handleScrollEnd() async {
        // Cancel any existing timer
        state.scrollEndTimer?.invalidate()

        // Set a timer to detect when scrolling has truly ended
        state.scrollEndTimer = Timer.scheduledTimer(withTimeInterval: DashboardConstants.UI.scrollEndDebounceDelay, repeats: false) { [weak self] _ in
            Task { @MainActor in
                guard let self = self else { return }
                // Update scroll position from stored value first
                if let finalPosition = self.latestScrollPosition {
                    self.state.xScrollPosition = finalPosition
                    self.logger.log(level: .debug, tag: "DashboardGraphManager", message: "Updated scroll position at end: \(finalPosition)")
                    self.latestScrollPosition = nil
                }
                // Update scrolling state
                self.state.updateScrollState(isScrolling: false)


            }
        }
    }

    // MARK: - Chart Data Generation with Simple Optimization
    func generateChartData(from operations: [BathScaleWeightSummary], selectedMetric: String?, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double) -> [GraphSeries] {
        guard !operations.isEmpty else {
            logger.log(level: .info, tag: "DashboardGraphManager", message: "No operations available for chart data generation")
            return []
        }

        // During scrolling or recalculation, return last calculated chart data to avoid expensive recalculation
        if (state.isScrolling) && !lastChartData.isEmpty {
            return lastChartData
        }

        var series: [GraphSeries] = []

        // Get weight values for normalization
        let weightValues = operations.map { summary -> Double in
            if isWeightlessMode {
                guard let anchorWeight = anchorWeight else { return 0 }
                let currentWeight = convertWeight(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return convertWeight(Int(summary.weight))
            }
        }

        // Calculate weight range
        guard let weightMin = weightValues.min(),
              let weightMax = weightValues.max(),
              weightMax > weightMin else {
            logger.log(level: .info, tag: "DashboardGraphManager", message: "Invalid weight range for chart data")
            return []
        }

        let weightRange = weightMin...weightMax

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

        // Add selected metric series (if a metric is selected)
        if let selectedMetric = selectedMetric, selectedMetric != DashboardStrings.weight {
            for summary in operations {
                if let metricValue = getMetricValue(for: selectedMetric, from: summary) {
                    let normalizedValue = normalizeMetricValue(metricValue, for: selectedMetric, toWeightRange: weightRange)
                    series.append(GraphSeries(
                        date: summary.date,
                        value: normalizedValue,
                        series: selectedMetric
                    ))
                }
            }
        }

        // Store the generated data for next call
        lastChartData = series

        logger.log(level: .info, tag: "DashboardGraphManager", message: "Generated fresh chart data: \(series.count) points")
        return series
    }

    // MARK: - Time Period Management
    func updateSelectedPeriod(_ period: TimePeriod) async {
        state.selectedPeriod = period
        state.clearSelection()

        logger.log(level: .info, tag: "DashboardGraphManager", message: "Updated selected period to: \(period.rawValue)")
    }



    // MARK: - Y-Axis Calculations
    func getYAxisScale(from operations: [BathScaleWeightSummary], goalWeight: Double, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double, chartHeight: CGFloat) -> YAxisScale {
        // Calculate fresh Y-axis scale (no state updates)
        let yAxisScale = YAxisCalculator.calculateYAxis(
            operations: operations,
            goalWeight: goalWeight,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertStoredWeightToDisplay: convertWeight,
            chartHeight: chartHeight,
            lastScale: lastYAxisScale
        )

        // Store the calculated scale for future fallback use
        lastYAxisScale = yAxisScale

        logger.log(level: .debug, tag: "DashboardGraphManager", message: "Calculated Y-axis scale: \(yAxisScale.domain)")

        return yAxisScale
    }

    // Method to calculate and cache Y-axis domain (called only during domain recalculation)
    func calculateAndCacheYAxisDomain(from operations: [BathScaleWeightSummary], goalWeight: Double, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double, chartHeight: CGFloat) {
        let yAxisScale = getYAxisScale(
            from: operations,
            goalWeight: goalWeight,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertWeight: convertWeight,
            chartHeight: chartHeight
        )

        // Cache both domain and ticks
        state.cachedYAxisDomain = yAxisScale.domain
        state.cachedYAxisTicks = yAxisScale.ticks

    }

    // MARK: - Scroll Boundaries with Centering

    /// Enforces scroll boundaries to ensure latest entry is properly centered and oldest entry is accessible
    private func enforceScrollBoundaries(_ position: Date, from operations: [BathScaleWeightSummary]) -> Date {
        guard !operations.isEmpty else { return position }

        let allDates = operations.map { $0.date }
        guard let minDate = allDates.min(), let maxDate = allDates.max() else { return position }

        let domainLength = visibleDomainLength(for: state.selectedPeriod)
        let halfDomain = domainLength / 2

        // Calculate centering buffer for future dates only (not past dates)
        let centeringBuffer: TimeInterval
        switch state.selectedPeriod {
        case .week:
            centeringBuffer = DashboardConstants.TimeInterval.day * 3.5 // 3.5 days
        case .month:
            centeringBuffer = DashboardConstants.TimeInterval.week * 2 // 2 weeks
        case .year:
            centeringBuffer = DashboardConstants.TimeInterval.month * 2 // 2 months
        case .total:
            centeringBuffer = DashboardConstants.TimeInterval.month * 3 // 3 months
        }

        // For past dates: Allow scrolling to see all historical data with minimal buffer
        let pastBuffer: TimeInterval
        switch state.selectedPeriod {
        case .week:
            pastBuffer = DashboardConstants.TimeInterval.day * 1 // 1 day before oldest
        case .month:
            pastBuffer = DashboardConstants.TimeInterval.week * 1 // 1 week before oldest
        case .year:
            pastBuffer = DashboardConstants.TimeInterval.month * 1 // 1 month before oldest
        case .total:
            pastBuffer = DashboardConstants.TimeInterval.month * 2 // 2 months before oldest
        }

        // Calculate the earliest allowed position (furthest back in time)
        let earliestAllowedPosition = minDate.addingTimeInterval(-pastBuffer - halfDomain)

        // Calculate the latest allowed position (furthest forward in time)
        let maxVisibleEnd = maxDate.addingTimeInterval(centeringBuffer)
        let latestAllowedPosition = maxVisibleEnd.addingTimeInterval(-halfDomain)

        // Clamp the position between the allowed bounds
        let clampedPosition = max(earliestAllowedPosition, min(position, latestAllowedPosition))

        if clampedPosition != position {
            logger.log(level: .debug, tag: "DashboardGraphManager",
                      message: "Enforced scroll boundary: \(position) -> \(clampedPosition), period: \(state.selectedPeriod)")
        }

        return clampedPosition
    }

        // MARK: - Visible Operations with Simple Optimization
    func getVisibleOperations(from operations: [BathScaleWeightSummary]) -> [BathScaleWeightSummary] {
        // During active scrolling, return cached results
        if state.isScrolling && !lastCalculatedVisibleOps.isEmpty {
            return lastCalculatedVisibleOps
        }

        // Check if we can use cached results (position and period haven't changed significantly)
        if !lastCalculatedVisibleOps.isEmpty,
           let lastPosition = lastVisibleOpsScrollPosition,
           let lastPeriod = lastVisibleOpsPeriod,
           lastPeriod == state.selectedPeriod {

            let domainLength = visibleDomainLength(for: state.selectedPeriod)
            let positionChange = abs(state.xScrollPosition.timeIntervalSince(lastPosition))

            // Only recalculate if position changed significantly (more than 1/10 domain)
            if positionChange < domainLength / 10 {
                return lastCalculatedVisibleOps
            }
        }

        // Calculate visible operations based on bounded position
        let allDates = operations.map { $0.date }
        let minDate = allDates.min() ?? Date()
        let maxDate = allDates.max() ?? Date()

        let calculatedStart = state.xScrollPosition.addingTimeInterval(-visibleDomainLength(for: state.selectedPeriod) / 4)
        let calculatedEnd = state.xScrollPosition.addingTimeInterval(visibleDomainLength(for: state.selectedPeriod))

        let visibleStart = max(calculatedStart, minDate)
        let visibleEnd = min(calculatedEnd, maxDate)

        let visibleOps = operations.filter { summary in
            return summary.date >= visibleStart && summary.date <= visibleEnd
        }

        // Store results and position for next call
        lastCalculatedVisibleOps = visibleOps
        lastVisibleOpsScrollPosition = state.xScrollPosition
        lastVisibleOpsPeriod = state.selectedPeriod

        logger.log(level: .debug, tag: "DashboardGraphManager", message: "Calculated visible operations with boundaries - \(visibleOps.count) out of \(operations.count) operations visible")
        return visibleOps
    }





    // MARK: - Entry Visibility
    func ensureLatestEntriesVisible(from operations: [BathScaleWeightSummary]) async {
        guard let latestDate = operations.map(\.date).max() else {
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "No operations available for latest entry positioning")
            return
        }

        // Prevent positioning during active scroll
        guard !state.isScrolling else {
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "Skipping latest entry positioning during scroll")
            return
        }

        // Apply boundary enforcement to ensure the latest entry is properly centered
        let boundedPosition = enforceScrollBoundaries(latestDate, from: operations)

        logger.log(level: .debug, tag: "DashboardGraphManager", message: "Positioning chart to latest entry: \(latestDate) (bounded: \(boundedPosition))")
        await updateScrollPosition(to: boundedPosition)
    }

    // MARK: - X-Axis Generation
    func generateXAxisValues(for period: TimePeriod, from operations: [BathScaleWeightSummary]) -> [Date] {
        let entryCount = operations.count
        let shouldRepeat = DateTimeTools.shouldRepeatXAxisLabels(for: period, entryCount: entryCount)
        return DateTimeTools.generateXAxisValues(for: period, from: operations, shouldRepeat: shouldRepeat, entryCount: entryCount)
    }

    func formatXAxisLabel(for date: Date, period: TimePeriod, operations: [BathScaleWeightSummary]) -> String? {
        return DateTimeTools.formatXAxisLabel(for: date, period: period, operations: operations)
    }

    // MARK: - Private Methods
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

    private func normalizeMetricValue(_ value: Double, for metricLabel: String, toWeightRange weightRange: ClosedRange<Double>) -> Double {
        let weightMin = weightRange.lowerBound
        let weightMax = weightRange.upperBound
        let weightSpan = weightMax - weightMin

        // Get appropriate range for the metric
        let (metricMin, metricMax): (Double, Double) = {
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
        }()

        // Clamp value to metric range
        let clampedValue = max(metricMin, min(metricMax, value))

        // Normalize to weight range
        let metricSpan = metricMax - metricMin
        let normalizedValue = weightMin + (clampedValue - metricMin) * weightSpan / metricSpan

        return normalizedValue
    }

    func visibleDomainLength(for period: TimePeriod) -> TimeInterval {
        return DateTimeTools.visibleDomainLength(for: period)
    }


    private func areEntriesInSameEra(_ summaries: [BathScaleWeightSummary]) -> Bool {
        guard !summaries.isEmpty else { return true }
        
        // Validate that all summaries have valid dates
        let validSummaries = summaries.filter { summary in
            // Ensure the date is not in the distant past or future (basic validation)
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
            // Few entries: show labels once
            let weekStart = calendar.dateInterval(of: .weekOfYear, for: minDate)?.start ?? minDate
            for dayOffset in 0..<7 {
                if let dayDate = calendar.date(byAdding: .day, value: dayOffset, to: weekStart) {
                    dates.append(dayDate)
                }
            }
        } else {
            // Many entries: respect data boundaries with limited buffer
            let centeringBuffer = DashboardConstants.TimeInterval.week * 0.5 // Same as scroll boundary
            let maxAllowedDate = maxDate.addingTimeInterval(centeringBuffer)

            let weekStart = calendar.dateInterval(of: .weekOfYear, for: minDate)?.start ?? minDate
            let totalWeeks = Int(ceil(maxAllowedDate.timeIntervalSince(weekStart) / DashboardConstants.TimeInterval.week))

            for weekOffset in 0..<totalWeeks {
                if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: weekStart) {
                    for dayOffset in 0..<7 {
                        if let dayDate = calendar.date(byAdding: .day, value: dayOffset, to: weekDate) {
                            // Only add dates that don't exceed our maximum allowed date
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
            // Few entries: show labels once
            let monthStart = calendar.dateInterval(of: .month, for: minDate)?.start ?? minDate
            for weekOffset in 0..<5 {
                if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: monthStart) {
                    dates.append(weekDate)
                }
            }
        } else {
            // Many entries: respect data boundaries with limited buffer
            let centeringBuffer = DashboardConstants.TimeInterval.week * 2 // Same as scroll boundary
            let maxAllowedDate = maxDate.addingTimeInterval(centeringBuffer)

            let monthStart = calendar.dateInterval(of: .month, for: minDate)?.start ?? minDate
            let totalMonths = Int(ceil(maxAllowedDate.timeIntervalSince(monthStart) / DashboardConstants.TimeInterval.month))

            for monthOffset in 0..<totalMonths {
                if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: monthStart) {
                    for weekOffset in 0..<5 {
                        if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: monthDate) {
                            // Only add dates that don't exceed our maximum allowed date
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
            // Few entries: show labels once
            let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
            for monthOffset in 0..<12 {
                if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: yearStart) {
                    dates.append(monthDate)
                }
            }
        } else {
            // Many entries: respect data boundaries with limited buffer
            let centeringBuffer = DashboardConstants.TimeInterval.month * 2 // Same as scroll boundary
            let maxAllowedDate = maxDate.addingTimeInterval(centeringBuffer)

            let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
            let totalYears = Int(ceil(maxAllowedDate.timeIntervalSince(yearStart) / DashboardConstants.TimeInterval.year))

            for yearOffset in 0..<totalYears {
                if let yearDate = calendar.date(byAdding: .year, value: yearOffset, to: yearStart) {
                    for monthOffset in 0..<12 {
                        if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: yearDate) {
                            // Only add dates that don't exceed our maximum allowed date
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
            // For same era, treat like year view
            return generateYearlyXAxis(minDate: minDate, maxDate: maxDate, shouldRepeat: shouldRepeat, entryCount: entryCount)
        } else {
            // For multiple years, use quarterly intervals with limited buffer
            let centeringBuffer = DashboardConstants.TimeInterval.month * 3 // Same as scroll boundary
            let maxAllowedDate = maxDate.addingTimeInterval(centeringBuffer)

            let quarterStart = calendar.date(from: calendar.dateComponents([.year, .month], from: minDate)) ?? minDate
            let totalQuarters = Int(ceil(maxAllowedDate.timeIntervalSince(quarterStart) / DashboardConstants.TimeInterval.quarter))
            var dates: [Date] = []

            for quarterOffset in 0..<totalQuarters {
                if let quarterDate = calendar.date(byAdding: .month, value: quarterOffset * 3, to: quarterStart) {
                    // Only add dates that don't exceed our maximum allowed date
                    if quarterDate <= maxAllowedDate {
                        dates.append(quarterDate)
                    }
                }
            }

            return dates
        }
    }

    // MARK: - Visible X-Axis Generation
    func generateVisibleXAxisValues(for period: TimePeriod, from operations: [BathScaleWeightSummary], scrollPosition: Date) -> [Date] {
        let domainLength = visibleDomainLength(for: period)

        // During active scrolling, use cached values if position hasn't changed significantly
        if state.isScrolling {
            if !lastXAxisValues.isEmpty,
               let lastPosition = lastXAxisScrollPosition,
               lastXAxisPeriod == period {

                // Check if position changed significantly (more than quarter domain to refresh more frequently)
                let positionChange = abs(scrollPosition.timeIntervalSince(lastPosition))

                if positionChange < domainLength / 4 {
                    return lastXAxisValues
                }
            }
        }

        let allDates = operations.map(\.date)
        guard let overallMinDate = allDates.min(), let overallMaxDate = allDates.max() else { return [] }

        // Use moderate buffer to balance performance and label coverage
        let buffer = domainLength * 2  // 2x domain buffer - enough for smooth scrolling without freezing

        // Calculate visible range with moderate buffer
        let visibleStart = max(overallMinDate, scrollPosition.addingTimeInterval(-domainLength / 2 - buffer))
        let visibleEnd = min(overallMaxDate, scrollPosition.addingTimeInterval(domainLength / 2 + buffer))

        let entryCount = operations.count
        let shouldRepeat =  DateTimeTools.shouldRepeatXAxisLabels(for: period, entryCount: entryCount)

        let xAxisValues: [Date]
        switch period {
        case .week:
            xAxisValues = generateVisibleWeeklyXAxis(visibleStart: visibleStart, visibleEnd: visibleEnd, shouldRepeat: shouldRepeat)
        case .month:
            xAxisValues = generateVisibleMonthlyXAxis(visibleStart: visibleStart, visibleEnd: visibleEnd, shouldRepeat: shouldRepeat)
        case .year:
            xAxisValues = generateVisibleYearlyXAxis(visibleStart: visibleStart, visibleEnd: visibleEnd, shouldRepeat: shouldRepeat)
        case .total:
            xAxisValues = generateVisibleTotalXAxis(visibleStart: visibleStart, visibleEnd: visibleEnd, operations: operations, shouldRepeat: shouldRepeat)
        }

        // Cache the results for use during scrolling
        lastXAxisValues = xAxisValues
        lastXAxisScrollPosition = scrollPosition
        lastXAxisPeriod = period

        logger.log(level: .debug, tag: "DashboardGraphManager", message: "Generated visible x-axis values: \(xAxisValues.count) values for period \(period.rawValue)")
        return xAxisValues
    }

    // Revert to the original visible range methods with reasonable buffer
    private func generateVisibleWeeklyXAxis(visibleStart: Date, visibleEnd: Date, shouldRepeat: Bool) -> [Date] {
        var dates: [Date] = []

        // Find the start of the week containing visibleStart
        let weekStart = calendar.dateInterval(of: .weekOfYear, for: visibleStart)?.start ?? visibleStart

        // Calculate weeks needed to cover the visible range with small buffer
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

    // MARK: - Snapping
    private func snapToNearestPosition() async {
        // This method would calculate optimal snap positions based on the current period
        // For now, we'll just log the action
        logger.log(level: .info, tag: "DashboardGraphManager", message: "Snapping to nearest position")
    }

    // Add these buffer calculation methods for stride-based x-axis

    func pastBufferFor(period: TimePeriod) -> TimeInterval {
        switch period {
        case .week:
            return DashboardConstants.TimeInterval.day * 1
        case .month:
            return DashboardConstants.TimeInterval.week * 1
        case .year:
            return DashboardConstants.TimeInterval.month * 1
        case .total:
            return DashboardConstants.TimeInterval.month * 2
        }
    }

    func centeringBufferFor(period: TimePeriod) -> TimeInterval {
        switch period {
        case .week:
            return DashboardConstants.TimeInterval.day * 3.5
        case .month:
            return DashboardConstants.TimeInterval.week * 2
        case .year:
            return DashboardConstants.TimeInterval.month * 2
        case .total:
            return DashboardConstants.TimeInterval.month * 3
        }
    }

    // MARK: - Date Formatting Methods (moved from DashboardStore)

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

        switch period {
        case .week:
            let month = DateTimeTools.formatter("LLL").string(from: minDate)
            let startDay = calendar.component(.day, from: minDate)
            let endDay = calendar.component(.day, from: maxDate)
            let year = calendar.component(.year, from: maxDate)
            return "\(month) \(startDay)-\(endDay), \(year)"
        case .month:
            return DateTimeTools.formatter("LLL yyyy").string(from: minDate)
        case .year:
            return DateTimeTools.formatter("yyyy").string(from: minDate)
        case .total:
            let minYear = calendar.component(.year, from: minDate)
            let maxYear = calendar.component(.year, from: maxDate)
            return minYear == maxYear ? "\(minYear)" : "\(minYear)-\(maxYear)"
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

    // MARK: - Weight Calculation Methods (moved from DashboardStore)

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

    // MARK: - Scroll Handling Methods (moved from DashboardStore)

    func handleScrollPositionChange(_ newPosition: Date?, isScrolling: Bool, updateWeightDisplay: @escaping () -> Void) {
        guard let newPosition = newPosition else { return }

        // Update position immediately for smooth scrolling
        state.xScrollPosition = newPosition

        // If not currently in a scroll gesture, this might be a programmatic change
        if !isScrolling {
            updateWeightDisplay()
        }
    }

    func handleScrollStart() {
        guard !state.isScrolling else { return }
        state.isScrolling = true
    }

    func handleScrollEndOptimized(updateWeightDisplay: @escaping () -> Void, recalculateYAxis: @escaping () -> Void, updateMetrics: @escaping () -> Void) {
        // Cancel any existing timer
        state.scrollEndTimer?.invalidate()

        // Set a timer to detect when scrolling has truly ended
        state.scrollEndTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: false) { [weak self] _ in
            Task { @MainActor in
                guard let self = self else { return }

                // Update scrolling state
                self.state.isScrolling = false
                self.state.hasDetectedScrollInCurrentGesture = false

                // Update weight display to show average of visible region
                updateWeightDisplay()

                // Force Y-axis recalculation based on visible operations
                recalculateYAxis()

                // Reset metrics to show visible region average or latest entry
                updateMetrics()
            }
        }
    }

    func updateWeightDisplayForCurrentView(triggerUpdate: @escaping () -> Void) {
        triggerUpdate()
    }

    func recalculateYAxisForVisibleData(triggerUpdate: @escaping () -> Void) {
        // Force chart to recalculate Y-axis by triggering data change
        state.dataChangeTrigger += 1
        triggerUpdate()
    }

    func updateMetricsForCurrentView(selectedPoint: BathScaleWeightSummary?, visibleOperations: [BathScaleWeightSummary], updateMetrics: @escaping (BathScaleWeightSummary) async throws -> Void, resetMetrics: @escaping () -> Void) {
        if let selectedPoint = selectedPoint {
            // If a point is selected, show its values
            Task {
                try? await updateMetrics(selectedPoint)
            }
        } else {
            // If no selection, show average of visible region or latest entry
            if !visibleOperations.isEmpty && visibleOperations.count > 1 {
                // Show average metrics for visible region
                // For now, just reset to latest - could implement average later
                resetMetrics()
            } else {
                // Fallback to latest entry
                resetMetrics()
            }
        }
    }
}

