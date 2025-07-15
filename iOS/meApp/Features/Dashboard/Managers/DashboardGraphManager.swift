import SwiftUI
import Charts
import os
import Foundation

/// Protocol defining graph management operations
protocol DashboardGraphManaging {
    func updateScrollPosition(to date: Date) async
    func handleScrollPositionChange(_ newPosition: Date?) async
    func handleChartSelection(at selectedDate: Date?) async
    @available(iOS 18.0, *)
    func handleScrollPhaseChange(_ phase: ScrollPhase) async
  func generateChartData(from operations: [BathScaleWeightSummary], selectedMetric: String?, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double) async -> [GraphSeries]
    func updateSelectedPeriod(_ period: TimePeriod) async
    func getYAxisScale(from operations: [BathScaleWeightSummary], goalWeight: Double, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double, chartHeight: CGFloat) async -> YAxisScale
  func calculateAndCacheYAxisDomain(from operations: [BathScaleWeightSummary], goalWeight: Double, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double, chartHeight: CGFloat) async
  func getVisibleOperations(from operations: [BathScaleWeightSummary]) async -> [BathScaleWeightSummary]
    func ensureLatestEntriesVisible(from operations: [BathScaleWeightSummary]) async
    func triggerDomainRecalculation(reason: String) async
    func handleScrollStart() async
    func handleScrollEnd() async
}

/// Manages all graph and chart operations for the dashboard
@MainActor
class DashboardGraphManager: ObservableObject, DashboardGraphManaging {


    // MARK: - Dependencies
    @Injector private var logger: LoggerService

    // MARK: - Published Properties
    @Published var state: GraphState

    // MARK: - Private Properties
    private let perfLog = OSLog(subsystem: Bundle.main.bundleIdentifier ?? "DashboardGraphManager", category: "Scrolling")
    private let calendar = Calendar.current

    // MARK: - Performance Optimization - Simple Scroll State Check
    private var lastCalculatedVisibleOps: [BathScaleWeightSummary] = []

    // Simple chart data optimization
    private var lastChartData: [GraphSeries] = []

    // Flag to prevent multiple recalculations immediately after scroll
    private var isRecalculating = false

    // Store scroll position during scroll, update state only at end
    private var latestScrollPosition: Date?

    // Store last Y-axis scale for fallback when no data
    private var lastYAxisScale: YAxisScale?

    // MARK: - Initialization
    init(initialState: GraphState = GraphState()) {
        self.state = initialState
    }

    // MARK: - Scroll Management
    func updateScrollPosition(to date: Date) async {
        // Only update position if we're not actively scrolling or recalculating
        // This prevents overriding user scroll gestures
        guard !state.isScrolling && !isRecalculating else {
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
            // Not scrolling: update the position immediately
            state.xScrollPosition = newPosition
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

            // Log scroll end for debugging
            os_log("ScrollPhase: idle - Computing visible operations and Y-axis domain", log: perfLog, type: .info)

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
            state.isScrolling = true

        case .animating:
            // System is animating to a final target (programmatic scroll) - do NOT compute domain yet
            state.isScrolling = true

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

                // Trigger domain recalculation now that scrolling has ended
                await self.triggerDomainRecalculation(reason: "scroll end")
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
        if (state.isScrolling || isRecalculating) && !lastChartData.isEmpty {
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "Using cached chart data: \(lastChartData.count) points")
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

        // Trigger domain recalculation for new segment load
        await triggerDomainRecalculation(reason: "segment load - period: \(period.rawValue)")

        logger.log(level: .info, tag: "DashboardGraphManager", message: "Updated selected period to: \(period.rawValue)")
    }

    // MARK: - Domain Recalculation
    /// Triggers visible operations and Y-axis domain recalculation
    /// Should only be called on scroll end or segment load for performance
    func triggerDomainRecalculation(reason: String) async {
        // Set recalculation flag to prevent multiple calculations
        isRecalculating = true

        // Clear last calculated results to force fresh calculation
        lastCalculatedVisibleOps = []
        lastChartData = []

        // Increment data change trigger to force chart refresh
        state.dataChangeTrigger += 1

        logger.log(level: .info, tag: "DashboardGraphManager", message: "Domain recalculation triggered - \(reason)")

        // Clear recalculation flag after a delay to allow one fresh calculation and prevent multiple calls
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            self.isRecalculating = false
            self.logger.log(level: .debug, tag: "DashboardGraphManager", message: "Recalculation period ended")
        }
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
            minTickSpacing: DashboardConstants.UI.minimumTickSpacing,
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

        logger.log(level: .debug, tag: "DashboardGraphManager", message: "Cached Y-axis domain: \(yAxisScale.domain) and ticks: \(yAxisScale.ticks)")
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
        // During active scrolling or recalculation, return last calculated results to avoid expensive recalculation
        if (state.isScrolling || isRecalculating) && !lastCalculatedVisibleOps.isEmpty {
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "Using cached visible operations: \(lastCalculatedVisibleOps.count) out of \(operations.count)")
            return lastCalculatedVisibleOps
        }

        print("domain state.xScrollPosition: \(state.xScrollPosition)")
        print("domain state.selectedPeriod: \(state.selectedPeriod)")

        // Calculate visible operations based on bounded position
        let allDates = operations.map { $0.date }
        let minDate = allDates.min() ?? Date()
        let maxDate = allDates.max() ?? Date()

        let calculatedStart = state.xScrollPosition.addingTimeInterval(-visibleDomainLength(for: state.selectedPeriod) / 2)
        let calculatedEnd = state.xScrollPosition.addingTimeInterval(visibleDomainLength(for: state.selectedPeriod))

        let visibleStart = max(calculatedStart, minDate)
        let visibleEnd = min(calculatedEnd, maxDate)

        let visibleOps = operations.filter { summary in
            return summary.date >= visibleStart && summary.date <= visibleEnd
        }

        // Store results for next call
        lastCalculatedVisibleOps = visibleOps

        logger.log(level: .debug, tag: "DashboardGraphManager", message: "Calculated visible operations with boundaries - \(visibleOps.count) out of \(operations.count) operations visible")
        return visibleOps
    }





    // MARK: - Entry Visibility
    func ensureLatestEntriesVisible(from operations: [BathScaleWeightSummary]) async {
        guard let latestDate = operations.map(\.date).max() else {
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "No operations available for latest entry positioning")
            return
        }

        // Prevent positioning during active scroll or recalculation
        guard !state.isScrolling && !isRecalculating else {
            logger.log(level: .debug, tag: "DashboardGraphManager", message: "Skipping latest entry positioning during scroll/recalculation")
            return
        }

        // Apply boundary enforcement to ensure the latest entry is properly centered
        let boundedPosition = enforceScrollBoundaries(latestDate, from: operations)

        logger.log(level: .debug, tag: "DashboardGraphManager", message: "Positioning chart to latest entry: \(latestDate) (bounded: \(boundedPosition))")
        await updateScrollPosition(to: boundedPosition)
    }

    // MARK: - X-Axis Generation
    func generateXAxisValues(for period: TimePeriod, from operations: [BathScaleWeightSummary]) -> [Date] {
        let allDates = operations.map(\.date)
        guard let minDate = allDates.min(), let maxDate = allDates.max() else { return [] }

        let entryCount = operations.count
        let shouldRepeat = shouldRepeatXAxisLabels(for: period, entryCount: entryCount)

        switch period {
        case .week:
            return generateWeeklyXAxis(minDate: minDate, maxDate: maxDate, shouldRepeat: shouldRepeat, entryCount: entryCount)
        case .month:
            return generateMonthlyXAxis(minDate: minDate, maxDate: maxDate, shouldRepeat: shouldRepeat, entryCount: entryCount)
        case .year:
            return generateYearlyXAxis(minDate: minDate, maxDate: maxDate, shouldRepeat: shouldRepeat, entryCount: entryCount)
        case .total:
            return generateTotalXAxis(minDate: minDate, maxDate: maxDate, operations: operations, shouldRepeat: shouldRepeat, entryCount: entryCount)
        }
    }

    func formatXAxisLabel(for date: Date, period: TimePeriod, operations: [BathScaleWeightSummary]) -> String? {
        switch period {
        case .week:
            return WeekDay.abbreviation(for: calendar.component(.weekday, from: date))
        case .month:
            return "\(calendar.component(.day, from: date))"
        case .year:
            return Month.initial(for: calendar.component(.month, from: date))
        case .total:
            if areEntriesInSameEra(operations) {
                return Month.initial(for: calendar.component(.month, from: date))
            } else {
                return "\(calendar.component(.year, from: date))"
            }
        }
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
        switch period {
        case .week:
            return DashboardConstants.TimeInterval.week
        case .month:
            return DashboardConstants.TimeInterval.month
        case .year:
            return DashboardConstants.TimeInterval.year
        case .total:
            return DashboardConstants.TimeInterval.year
        }
    }

    private func shouldRepeatXAxisLabels(for period: TimePeriod, entryCount: Int) -> Bool {
        switch period {
        case .week:
            return entryCount >= DashboardConstants.Thresholds.weekRepeatThreshold
        case .month:
            return entryCount >= DashboardConstants.Thresholds.monthRepeatThreshold
        case .year, .total:
            return entryCount >= DashboardConstants.Thresholds.yearRepeatThreshold
        }
    }

    private func areEntriesInSameEra(_ summaries: [BathScaleWeightSummary]) -> Bool {
        guard !summaries.isEmpty else { return true }
        let years = Set(summaries.map { calendar.component(.year, from: $0.date) })
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

    // MARK: - Snapping
    private func snapToNearestPosition() async {
        // This method would calculate optimal snap positions based on the current period
        // For now, we'll just log the action
        logger.log(level: .info, tag: "DashboardGraphManager", message: "Snapping to nearest position")
    }
}
