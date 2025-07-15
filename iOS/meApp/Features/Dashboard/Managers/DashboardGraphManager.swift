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



    // MARK: - Snapping
    private func snapToNearestPosition() async {
        // This method would calculate optimal snap positions based on the current period
        // For now, we'll just log the action
        logger.log(level: .info, tag: "DashboardGraphManager", message: "Snapping to nearest position")
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
