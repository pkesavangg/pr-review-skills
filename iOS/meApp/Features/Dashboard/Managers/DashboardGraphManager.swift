import SwiftUI
import Charts
import os
import Foundation

/// Protocol defining graph management operations
protocol DashboardGraphManaging {
    func updateScrollPosition(to date: Date) async
    func handleChartSelection(at selectedDate: Date) async
    @available(iOS 18.0, *)
    func handleScrollPhaseChange(_ phase: ScrollPhase) async
    func generateChartData(from operations: [BathScaleWeightSummary], selectedMetric: String?, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double) -> [GraphSeries]
    func updateSelectedPeriod(_ period: TimePeriod) async
    func getYAxisScale(from operations: [BathScaleWeightSummary], goalWeight: Double, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double, chartHeight: CGFloat) -> YAxisScale
    func getVisibleOperations(from operations: [BathScaleWeightSummary]) -> [BathScaleWeightSummary]
    func ensureLatestEntriesVisible(from operations: [BathScaleWeightSummary]) async
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

    // MARK: - Initialization
    init(initialState: GraphState = GraphState()) {
        self.state = initialState
    }

    // MARK: - Scroll Management
    func updateScrollPosition(to date: Date) async {
        // Cancel any existing timer
        state.scrollEndTimer?.invalidate()

        // Set a debounced timer to update scroll position
        state.scrollEndTimer = Timer.scheduledTimer(withTimeInterval: DashboardConstants.UI.scrollEndDebounceDelay, repeats: false) { [weak self] _ in
            Task { @MainActor in
                guard let self = self else { return }
                self.state.xScrollPosition = date
                await self.snapToNearestPosition()
            }
        }
    }

    func handleChartSelection(at selectedDate: Date) async {
        // Only handle selection if not currently scrolling
        guard !state.isScrolling else { return }

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
            // No scrolling is occurring
            state.isScrolling = false
            state.hasDetectedScrollInCurrentGesture = false

            // Clear selection state for better UX
            state.clearSelection()

            os_log("ScrollPhase: idle - Scroll ended", log: perfLog, type: .info)

        case .tracking:
            // User is touching but hasn't started scrolling yet
            state.hasDetectedScrollInCurrentGesture = false

        case .interacting:
            // User is actively scrolling
            if !state.hasDetectedScrollInCurrentGesture {
                state.hasDetectedScrollInCurrentGesture = true
                state.updateScrollState(isScrolling: true)
            }

        case .decelerating:
            // User stopped scrolling, chart is decelerating to final position
            state.isScrolling = true

        case .animating:
            // System is animating to a final target (programmatic scroll)
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

                // Update scrolling state
                self.state.updateScrollState(isScrolling: false)

                os_log("Scroll ended with enhanced page snapping", log: self.perfLog, type: .info)

                // Apply snapping
                await self.snapToNearestPosition()
            }
        }
    }

    // MARK: - Chart Data Generation
    func generateChartData(from operations: [BathScaleWeightSummary], selectedMetric: String?, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double) -> [GraphSeries] {
        guard !operations.isEmpty else {
            logger.log(level: .info, tag: "DashboardGraphManager", message: "No operations available for chart data generation")
            return []
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

        logger.log(level: .info, tag: "DashboardGraphManager", message: "Generated \(series.count) chart data points")
        return series
    }

    // MARK: - Time Period Management
    func updateSelectedPeriod(_ period: TimePeriod) async {
        state.selectedPeriod = period
        state.clearSelection()

        // Increment data change trigger for refresh
        state.dataChangeTrigger += 1

        logger.log(level: .info, tag: "DashboardGraphManager", message: "Updated selected period to: \(period.rawValue)")
    }

    // MARK: - Y-Axis Calculations
    func getYAxisScale(from operations: [BathScaleWeightSummary], goalWeight: Double, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double, chartHeight: CGFloat) -> YAxisScale {
        return YAxisCalculator.calculateYAxis(
            operations: operations,
            goalWeight: goalWeight,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertStoredWeightToDisplay: convertWeight,
            chartHeight: chartHeight,
            minTickSpacing: DashboardConstants.UI.minimumTickSpacing
        )
    }

    // MARK: - Visible Operations
    func getVisibleOperations(from operations: [BathScaleWeightSummary]) -> [BathScaleWeightSummary] {
        let visibleStart = state.xScrollPosition.addingTimeInterval(-visibleDomainLength(for: state.selectedPeriod) / 2)
        let visibleEnd = state.xScrollPosition.addingTimeInterval(visibleDomainLength(for: state.selectedPeriod) / 2)

        let visibleOps = operations.filter { summary in
            return summary.date >= visibleStart && summary.date <= visibleEnd
        }

        return visibleOps
    }

    // MARK: - Entry Visibility
    func ensureLatestEntriesVisible(from operations: [BathScaleWeightSummary]) async {
        guard let latestDate = operations.map(\.date).max() else { return }
        
        // Immediately update scroll position for initialization
        state.xScrollPosition = latestDate
        
        logger.log(level: .info, tag: "DashboardGraphManager", message: "Ensured latest entries visible at date: \(latestDate)")
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
            // Many entries: repeat labels throughout scroll view
            let totalWeeks = max(8, Int(ceil(maxDate.timeIntervalSince(minDate) / DashboardConstants.TimeInterval.week)))
            let weekStart = calendar.dateInterval(of: .weekOfYear, for: minDate)?.start ?? minDate
            let bufferWeeks = 2

            for weekOffset in -bufferWeeks..<(totalWeeks + bufferWeeks) {
                if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: weekStart) {
                    for dayOffset in 0..<7 {
                        if let dayDate = calendar.date(byAdding: .day, value: dayOffset, to: weekDate) {
                            dates.append(dayDate)
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
            // Many entries: repeat labels throughout scroll view
            let totalMonths = max(6, Int(ceil(maxDate.timeIntervalSince(minDate) / DashboardConstants.TimeInterval.month)))
            let monthStart = calendar.dateInterval(of: .month, for: minDate)?.start ?? minDate
            let bufferMonths = 2

            for monthOffset in -bufferMonths..<(totalMonths + bufferMonths) {
                if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: monthStart) {
                    for weekOffset in 0..<5 {
                        if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: monthDate) {
                            dates.append(weekDate)
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
            // Many entries: repeat labels throughout scroll view
            let totalYears = max(3, Int(ceil(maxDate.timeIntervalSince(minDate) / DashboardConstants.TimeInterval.year)))
            let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
            let bufferYears = 1

            for yearOffset in -bufferYears..<(totalYears + bufferYears) {
                if let yearDate = calendar.date(byAdding: .year, value: yearOffset, to: yearStart) {
                    for monthOffset in 0..<12 {
                        if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: yearDate) {
                            dates.append(monthDate)
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
            // For multiple years, use quarterly intervals
            let totalQuarters = max(8, Int(ceil(maxDate.timeIntervalSince(minDate) / DashboardConstants.TimeInterval.quarter)))
            let quarterStart = calendar.date(from: calendar.dateComponents([.year, .month], from: minDate)) ?? minDate
            let bufferQuarters = 2
            var dates: [Date] = []

            for quarterOffset in -bufferQuarters..<(totalQuarters + bufferQuarters) {
                if let quarterDate = calendar.date(byAdding: .month, value: quarterOffset * 3, to: quarterStart) {
                    dates.append(quarterDate)
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
