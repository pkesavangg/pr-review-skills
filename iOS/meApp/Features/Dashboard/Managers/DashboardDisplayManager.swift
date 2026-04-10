//
//  DashboardDisplayManager.swift
//  meApp
//
//  Weight display, date range labels, formatting, metric info,
//  and metric view updates.
//

import Foundation
import SwiftUI

@MainActor
final class DashboardDisplayManager: DashboardDisplayManaging {
    private struct MetricsUpdateSignature: Equatable {
        enum Mode: Equatable {
            case selectedPoint(String)
            case selectedDate(Date)
            case visibleAverage([String])
            case placeholders(String)
        }

        let mode: Mode
    }

    private struct LabelRangeOperationsCacheKey: Equatable {
        let period: TimePeriod
        let scrollPosition: TimeInterval
        let operationCount: Int
        let firstTimestamp: String?
        let lastTimestamp: String?
    }

    // MARK: - Dependencies

    weak var stateProvider: DashboardStateProviding?

    private let graphManager: DashboardGraphManager
    private let dateRangeManager: DashboardDateRangeManagerProtocol
    private let metricsCalculator: DashboardMetricsCalculatorProtocol
    private let metricsManager: DashboardMetricsManager
    private let goalManager: DashboardGoalManager
    private let dataManager: DashboardDataManager
    private let formatter: DashboardFormatterProtocol
    private let cacheManager: DashboardCacheManagerProtocol
    @Injector var accountService: AccountService
    @Injector var logger: LoggerService

    // MARK: - BPM State

    /// The current AHA classification, driven by point selection or last entry. Defaults to `.normal`.
    var currentBpmClassification: AhaPressureClass = .normal
    private var metricsUpdateTask: Task<Void, Never>?
    private var lastMetricsUpdateSignature: MetricsUpdateSignature?
    private var resetMetricsTask: Task<Void, Never>?
    private var cachedLabelRangeOperationsKey: LabelRangeOperationsCacheKey?
    private var cachedLabelRangeOperations: [BathScaleWeightSummary] = []

    /// Updates the AHA classification based on a selected/tapped data point.
    func handleBpmPointSelection(_ point: BathScaleWeightSummary) {
        guard let sys = point.systolic, let dia = point.diastolic else { return }
        currentBpmClassification = AhaPressureClass.classify(systolic: Int(sys), diastolic: Int(dia))
    }

    /// Returns the BPM display values — either from the selected point or the visible window average.
    func getBpmDisplayValues() -> BpmDisplayData? {
        // 1. If a point is selected, show that point's values
        if let selected = stateProvider?.state.graph.selectedPoint,
           let sys = selected.systolic, let dia = selected.diastolic {
            let pulse = Int(selected.pulse ?? 0)
            let classification = AhaPressureClass.classify(systolic: Int(sys), diastolic: Int(dia))
            let dateLabel = formatChartDate(selected.date)
            return BpmDisplayData(systolic: Int(sys), diastolic: Int(dia), pulse: pulse, classification: classification, label: dateLabel)
        }

        // 2. Fallback to visible window average
        let visible = getVisibleOperations()
        let validOps = visible.filter { $0.systolic != nil }
        guard !validOps.isEmpty else { return nil }

        let avgSys = Int(round(validOps.compactMap(\.systolic).reduce(0, +) / Double(validOps.count)))
        let avgDia = Int(round(validOps.compactMap(\.diastolic).reduce(0, +) / Double(validOps.count)))
        let avgPulse = Int(round(validOps.compactMap(\.pulse).reduce(0, +) / Double(validOps.count)))
        let classification = AhaPressureClass.classify(systolic: avgSys, diastolic: avgDia)

        // Build a date range label for the visible window
        let label: String
        if let first = validOps.first, let last = validOps.last, first.date != last.date {
            label = "\(formatChartDate(first.date)) - \(formatChartDate(last.date))"
        } else if let first = validOps.first {
            label = formatChartDate(first.date)
        } else {
            label = ""
        }

        return BpmDisplayData(systolic: avgSys, diastolic: avgDia, pulse: avgPulse, classification: classification, label: label)
    }

    /// Closures for store-level computed properties
    let getContinuousOperations: () -> [BathScaleWeightSummary]
    let getVisibleOperations: () -> [BathScaleWeightSummary]
    let getIsWeightlessModeEnabled: () -> Bool
    let getWeightlessAnchorWeight: () -> Double?

    // MARK: - Initialization

    init(
        stateProvider: DashboardStateProviding,
        graphManager: DashboardGraphManager,
        dateRangeManager: DashboardDateRangeManagerProtocol,
        metricsCalculator: DashboardMetricsCalculatorProtocol,
        metricsManager: DashboardMetricsManager,
        goalManager: DashboardGoalManager,
        dataManager: DashboardDataManager,
        formatter: DashboardFormatterProtocol,
        cacheManager: DashboardCacheManagerProtocol,
        getContinuousOperations: @escaping () -> [BathScaleWeightSummary],
        getVisibleOperations: @escaping () -> [BathScaleWeightSummary],
        getIsWeightlessModeEnabled: @escaping () -> Bool,
        getWeightlessAnchorWeight: @escaping () -> Double?
    ) {
        self.stateProvider = stateProvider
        self.graphManager = graphManager
        self.dateRangeManager = dateRangeManager
        self.metricsCalculator = metricsCalculator
        self.metricsManager = metricsManager
        self.goalManager = goalManager
        self.dataManager = dataManager
        self.formatter = formatter
        self.cacheManager = cacheManager
        self.getContinuousOperations = getContinuousOperations
        self.getVisibleOperations = getVisibleOperations
        self.getIsWeightlessModeEnabled = getIsWeightlessModeEnabled
        self.getWeightlessAnchorWeight = getWeightlessAnchorWeight
    }

    // MARK: - Weight Display

    var displayWeight: Double? {
        guard let stateProvider else { return nil }
        let continuousOps = getContinuousOperations()
        let visibleOps = getVisibleOperations()
        let context = DisplayWeightContext(
            selectedPoint: stateProvider.state.graph.selectedPoint,
            selectedDate: stateProvider.state.graph.selectedXValue,
            operations: continuousOps,
            visibleOperations: visibleOps,
            operationsForLabel: getOperationsForLabelDateRange(),
            isWeightlessMode: getIsWeightlessModeEnabled(),
            anchorWeight: getWeightlessAnchorWeight(),
            period: stateProvider.state.graph.selectedPeriod,
            convertWeight: goalManager.convertWeightToDisplay,
            interpolatedWeight: { date, ops, isWeightless, anchor, convert in
                self.graphManager.interpolatedDisplayWeight(
                    at: date,
                    from: ops,
                    isWeightlessMode: isWeightless,
                    anchorWeight: anchor,
                    convertWeight: convert
                )
            },
            interpolatedAverage: { ops, period, isWeightless, anchor, convert, labelRange in
                self.graphManager.calculateInterpolatedAverageForVisibleRange(
                    from: ops,
                    period: period,
                    isWeightlessMode: isWeightless,
                    anchorWeight: anchor,
                    convertWeight: convert,
                    labelRange: labelRange
                )
            },
            weightlessDisplay: { ops, anchor, period, convert in
                self.graphManager.calculateWeightlessDisplay(ops, anchorWeight: anchor, period: period, convertWeight: convert)
            },
            labelRangeForPeriod: { period in
                switch period {
                case .month:
                    return self.getLabelDateRangeForMonth()
                case .year:
                    return self.getLabelDateRangeForYear()
                case .week:
                    return self.getLabelDateRangeForWeek()
                case .total:
                    return nil
                }
            }
        )
        return metricsCalculator.calculateDisplayWeight(context: context)
    }

    var weightLabel: String {
        guard let stateProvider else { return "" }

        if !stateProvider.state.data.hasAnyEntries && getContinuousOperations().isEmpty {
            return emptyStatePeriodLabel(for: stateProvider.state.graph.selectedPeriod)
        }

        if let label = selectionLabel() {
            return label
        }
        let period = stateProvider.state.graph.selectedPeriod
        switch period {
        case .total:
            return labelForTotalPeriod()
        case .year:
            return labelForYearGridlines()
        case .month:
            return labelForMonthGridlines()
        case .week:
            return labelForWeekGridlines()
        }
    }

    var weightDisplayLabel: String {
        guard let stateProvider else { return "no entries" }
        let visibleOps = getVisibleOperations()
        let continuousOps = getContinuousOperations()

        let hasNoVisibleOps = visibleOps.isEmpty
        let hasNoSelection = stateProvider.state.graph.selectedXValue == nil && stateProvider.state.graph.selectedPoint == nil
        let canInterpolate = !continuousOps.isEmpty && stateProvider.state.graph.selectedPeriod != .total
        let hasInterpolatedWeight = displayWeight != nil

        if hasNoVisibleOps && hasNoSelection && (!canInterpolate || !hasInterpolatedWeight) {
            return "no entries"
        }

        if stateProvider.state.graph.selectedXValue != nil {
            switch stateProvider.state.graph.selectedPeriod {
            case .week, .month:
                return "day average"
            case .year, .total:
                return "month average"
            }
        }
        return goalManager.getWeightDisplayLabel(for: stateProvider.state.graph.selectedPeriod)
    }

    @MainActor
    func getCurrentAverageWeight() -> Double {
        let opsToUse = getOperationsForLabelDateRange()
        return metricsCalculator.getCurrentAverageWeight(
            from: opsToUse,
            isWeightlessMode: getIsWeightlessModeEnabled(),
            anchorWeight: getWeightlessAnchorWeight(),
            convertWeight: goalManager.convertWeightToDisplay
        )
    }

    var displayUnitText: String {
        let unit: WeightUnit = accountService.activeAccount?.weightSettings?.weightUnit ?? .lb
        let displayValue = displayWeight ?? getCurrentAverageWeight()
        return WeightValueConvertor.unitForDisplay(value: displayValue, unit: unit)
    }

    func updateVisibleDataAfterScroll() {
        let visibleOps = getVisibleOperations()
        let continuousOps = getContinuousOperations()
        stateProvider?.scheduleUIUpdate()
        let opsToUse = visibleOps.isEmpty ? continuousOps : visibleOps
        let weightValues = opsToUse.map { summary -> Double in
            if getIsWeightlessModeEnabled() {
                guard let anchorWeight = getWeightlessAnchorWeight() else { return 0 }
                let currentWeight = goalManager.convertWeightToDisplay(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return goalManager.convertWeightToDisplay(Int(summary.weight))
            }
        }
        if let averageWeight = weightValues.isEmpty ? nil : weightValues.reduce(0, +) / Double(weightValues.count) {
            logger.log(
                level: .debug,
                tag: "DashboardDisplayManager",
                message: "updateVisibleDataAfterScroll - Average weight of visible operations: \(averageWeight)"
            )
        }
    }

    var activeMonthInterval: DateInterval? {
        guard stateProvider?.state.graph.selectedPeriod == .month else { return nil }
        return getFullyContainedMonthInterval()
    }

    // MARK: - Date Range Labels & Helpers

    private func selectionLabel() -> String? {
        guard let stateProvider else { return nil }
        if let selectedDate = stateProvider.state.graph.selectedXValue {
            return graphManager.formatSelectedDate(selectedDate, for: stateProvider.state.graph.selectedPeriod)
        }
        if let selectedPoint = stateProvider.state.graph.selectedPoint {
            return graphManager.formatSelectedDate(selectedPoint.date, for: stateProvider.state.graph.selectedPeriod)
        }
        if let selectedEntry = stateProvider.state.graph.selectedEntry {
            if let date = selectedEntry.date {
                return graphManager.formatSelectedDate(date, for: stateProvider.state.graph.selectedPeriod)
            }
            let continuousOps = getContinuousOperations()
            if let originalSummary = continuousOps.first(where: { $0.entryTimestamp == selectedEntry.entryTimestamp }) {
                return graphManager.formatSelectedDate(originalSummary.date, for: stateProvider.state.graph.selectedPeriod)
            }
        }
        return nil
    }

    private func labelForTotalPeriod() -> String {
        return dateRangeManager.labelForTotalPeriod(
            dateBounds: dataManager.getDateBounds(for: .total),
            formatDateRange: { min, max, period in
                graphManager.formatDateRange(minDate: min, maxDate: max, for: period)
            },
            fallbackLabel: {
                graphManager.fallbackTimeLabel(for: .total)
            }
        )
    }

    private func labelForYearGridlines() -> String {
        return dateRangeManager.labelForYearGridlines(
            xScrollPosition: graphManager.state.xScrollPosition,
            formatDateRange: { min, max, period in
                graphManager.formatDateRange(minDate: min, maxDate: max, for: period)
            },
            fallbackLabel: {
                graphManager.fallbackTimeLabel(for: .year)
            }
        )
    }

    private func labelForMonthGridlines() -> String {
        return dateRangeManager.labelForMonthGridlines(
            xScrollPosition: graphManager.state.xScrollPosition,
            visibleDomainLength: graphManager.visibleDomainLength(for: .month),
            continuousOperations: getContinuousOperations()
        ) { min, max, period in
            graphManager.formatDateRange(minDate: min, maxDate: max, for: period)
        }
    }

    private func labelForWeekGridlines() -> String {
        return dateRangeManager.labelForWeekGridlines(
            xScrollPosition: graphManager.state.xScrollPosition
        ) { min, max, period in
            graphManager.formatDateRange(minDate: min, maxDate: max, for: period)
        }
    }

    private func getFullyContainedMonthInterval() -> DateInterval? {
        return dateRangeManager.getFullyContainedMonthInterval(
            xScrollPosition: graphManager.state.xScrollPosition,
            visibleDomainLength: graphManager.visibleDomainLength(for: .month)
        )
    }

    private func getLabelDateRangeForMonth() -> DateInterval {
        return dateRangeManager.getLabelDateRangeForMonth(
            xScrollPosition: graphManager.state.xScrollPosition,
            visibleDomainLength: graphManager.visibleDomainLength(for: .month),
            continuousOperations: getContinuousOperations()
        )
    }

    private func getLabelDateRangeForYear() -> DateInterval {
        return dateRangeManager.getLabelDateRangeForYear(
            xScrollPosition: graphManager.state.xScrollPosition,
            visibleDomainLength: graphManager.visibleDomainLength(for: .year)
        )
    }

    private func getLabelDateRangeForWeek() -> DateInterval {
        return dateRangeManager.getLabelDateRangeForWeek(xScrollPosition: graphManager.state.xScrollPosition)
    }

    func getOperationsForLabelDateRange() -> [BathScaleWeightSummary] {
        guard let stateProvider else { return [] }
        let continuousOps = getContinuousOperations()
        let cacheKey = LabelRangeOperationsCacheKey(
            period: stateProvider.state.graph.selectedPeriod,
            scrollPosition: graphManager.state.xScrollPosition.timeIntervalSinceReferenceDate,
            operationCount: continuousOps.count,
            firstTimestamp: continuousOps.first?.entryTimestamp,
            lastTimestamp: continuousOps.last?.entryTimestamp
        )

        if cachedLabelRangeOperationsKey == cacheKey {
            return cachedLabelRangeOperations
        }

        let result = cacheManager.getLabelDateRangeOperations(
            period: stateProvider.state.graph.selectedPeriod,
            scrollPosition: graphManager.state.xScrollPosition
        ) {
            dateRangeManager.getOperationsForLabelDateRange(
                period: stateProvider.state.graph.selectedPeriod,
                xScrollPosition: graphManager.state.xScrollPosition,
                visibleDomainLength: { period in
                    graphManager.visibleDomainLength(for: period)
                },
                continuousOperations: continuousOps,
                dateBounds: dataManager.getDateBounds(for: .total),
                cachedPeriod: nil,
                cachedScrollPos: nil,
                cachedOps: []
            )
        }
        cachedLabelRangeOperationsKey = cacheKey
        cachedLabelRangeOperations = result.operations
        return result.operations
    }

    private func emptyStatePeriodLabel(for period: TimePeriod, today: Date = Date()) -> String {
        return dateRangeManager.emptyStatePeriodLabel(for: period, today: today)
    }

    // MARK: - Formatting

    func formatWeightDisplayText(_ weight: Double?) -> String {
        guard let weight = weight else { return "0.0" }
        return goalManager.formatWeightForDisplay(weight, isWeightlessMode: getIsWeightlessModeEnabled())
    }

    func formatYAxisTickLabel(_ weight: Double) -> String {
        return formatter.formatYAxisTickLabel(weight)
    }

    func formatChartDate(_ date: Date) -> String {
        guard let stateProvider else { return "" }
        return formatter.formatChartDate(date, period: stateProvider.state.graph.selectedPeriod)
    }

    func roundedGoalWeight(_ weight: Double) -> Double {
        return formatter.roundedGoalWeight(weight)
    }

    func formattedMetricValue(for metric: (preLabel: String?, value: String)) -> String {
        return formatter.formattedMetricValue(for: metric)
    }

    // MARK: - Metric Info

    func metricInfoDateLabel(for entryDTO: BathScaleOperationDTO) -> String {
        guard let stateProvider else { return "" }
        let isHistoryEntry = !formatter.isDashboardEntry(entryDTO)
        guard let entryDate = formatter.parseEntryDate(from: entryDTO) else {
            return formatter.formatMetricInfoDateLabel(
                entryDate: nil,
                isFromHistory: false,
                period: stateProvider.state.graph.selectedPeriod,
                selectedPointDate: stateProvider.state.graph.selectedPoint?.date,
                crosshairDate: stateProvider.state.graph.selectedXValue,
                weightLabel: weightLabel
            )
        }
        return formatter.formatMetricInfoDateLabel(
            entryDate: entryDate,
            isFromHistory: isHistoryEntry,
            period: stateProvider.state.graph.selectedPeriod,
            selectedPointDate: stateProvider.state.graph.selectedPoint?.date,
            crosshairDate: stateProvider.state.graph.selectedXValue,
            weightLabel: weightLabel
        )
    }

    func allowedMetricsForMetricInfo() -> [BodyMetric] {
        guard let stateProvider else { return [.weight, .bmi, .bodyFat, .muscleMass, .water] }
        switch stateProvider.state.metrics.dashboardType {
        case .dashboard4:
            return [.weight, .bmi, .bodyFat, .muscleMass, .water]
        case .dashboard12:
            return [
                .weight,
                .bmi,
                .bodyFat,
                .muscleMass,
                .water,
                .pulse,
                .boneMass,
                .visceralFatLevel,
                .subcutaneousFatPercent,
                .proteinPercent,
                .skeletalMusclePercent,
                .bmr,
                .metabolicAge
            ]
        }
    }

    func validateMetricInfoSelection(_ current: BodyMetric) -> BodyMetric {
        let allowed = allowedMetricsForMetricInfo()
        return allowed.contains(current) ? current : (allowed.first ?? .bmi)
    }

    func createEntryForMetricInfo(metricLabel: String? = nil) -> Entry {
        guard let stateProvider else {
            return Entry(
                entryTimestamp: DateTimeTools.getCurrentDatetimeIsoString(),
                accountId: "dashboard",
                operationType: OperationType.create.rawValue,
                deviceType: "scale",
                isSynced: true
            )
        }
        _ = metricLabel
        let continuousOps = getContinuousOperations()
        let visibleOps = getVisibleOperations()
        let context = EntryCreationContext(
            selectedPoint: stateProvider.state.graph.selectedPoint,
            selectedDate: stateProvider.state.graph.selectedXValue,
            operations: continuousOps,
            visibleOperations: visibleOps,
            metrics: stateProvider.state.metrics.metrics,
            isWeightlessMode: getIsWeightlessModeEnabled(),
            anchorWeight: getWeightlessAnchorWeight(),
            period: stateProvider.state.graph.selectedPeriod,
            weightUnit: accountService.activeAccount?.weightSettings?.weightUnit ?? .lb,
            latestWeightStored: dataManager.state.latestWeightStored,
            convertWeight: goalManager.convertWeightToDisplay,
            interpolatedWeight: { date, ops, isWeightless, anchor, convert in
                self.graphManager.interpolatedDisplayWeight(
                    at: date,
                    from: ops,
                    isWeightlessMode: isWeightless,
                    anchorWeight: anchor,
                    convertWeight: convert
                )
            },
            interpolatedAverage: { ops, period, isWeightless, anchor, convert, labelRange in
                self.graphManager.calculateInterpolatedAverageForVisibleRange(
                    from: ops,
                    period: period,
                    isWeightlessMode: isWeightless,
                    anchorWeight: anchor,
                    convertWeight: convert,
                    labelRange: labelRange
                )
            }
        )
        return metricsCalculator.createEntryForMetricInfo(context: context)
    }

    func createEntryForMetricInfoAsync(metricLabel: String? = nil) async -> Entry {
        await metricsManager.createEntryForMetricInfo(metricLabel: metricLabel)
    }

    func getBodyMetric(for metricLabel: String) -> BodyMetric {
        return metricsManager.getBodyMetric(for: metricLabel)
    }

    // MARK: - Metrics Updates

    @MainActor
    func updateMetricsForCurrentView() {
        guard let stateProvider else { return }

        guard !stateProvider.state.ui.isResettingDashboard, stateProvider.state.ui.hasLoadedDashboardConfig else {
            return
        }

        guard !stateProvider.state.graph.isScrolling else {
            return
        }

        if let selectedPoint = stateProvider.state.graph.selectedPoint {
            let signature = MetricsUpdateSignature(mode: .selectedPoint(selectedPoint.entryTimestamp))
            guard shouldRunMetricsUpdate(for: signature) else { return }

            metricsUpdateTask = Task { [weak self, weak stateProvider] in
                guard let self else { return }
                try? await self.metricsManager.updateMetrics(with: selectedPoint)
                await MainActor.run {
                    stateProvider?.state.ui.hasLoadedMetricValues = true
                }
            }
            return
        }
        if stateProvider.state.graph.selectedXValue != nil {
            let signature = MetricsUpdateSignature(
                mode: .selectedDate(stateProvider.state.graph.selectedXValue ?? .distantPast)
            )
            guard shouldRunMetricsUpdate(for: signature) else { return }
            metricsManager.setPlaceholdersForAllMetrics()
            stateProvider.state.ui.hasLoadedMetricValues = true
            return
        }
        let ops = self.getOperationsForLabelDateRange()

        if ops.isEmpty {
            let signature = MetricsUpdateSignature(mode: .placeholders("empty"))
            guard shouldRunMetricsUpdate(for: signature) else { return }
            metricsManager.setPlaceholdersForAllMetrics()
            stateProvider.state.ui.hasLoadedMetricValues = true
            return
        }

        let signature = MetricsUpdateSignature(mode: .visibleAverage(metricsSignatureComponents(for: ops)))
        guard shouldRunMetricsUpdate(for: signature) else { return }

        metricsUpdateTask = Task { [weak self, weak stateProvider] in
            guard let self else { return }
            await self.metricsManager.updateMetricsForVisibleAverage(visibleOperations: ops)
            await MainActor.run {
                stateProvider?.state.ui.hasLoadedMetricValues = true
            }
        }
    }

    func updateMetricsWithVisibleRegionAverage() {
        let visibleOps = getOperationsForLabelDateRange()
        Task {
            await metricsManager.updateMetricsForVisibleAverage(visibleOperations: visibleOps)
            await MainActor.run {
                stateProvider?.state.ui.hasLoadedMetricValues = true
            }
        }
    }

    func resetMetricsToLatestEntry() {
        resetMetricsTask?.cancel()
        resetMetricsTask = Task { [weak self] in
            guard let self else { return }
            await metricsManager.resetMetricsToLatestEntry {
                try await self.dataManager.getLatestEntry()
            }
        }
    }

    private func shouldRunMetricsUpdate(for signature: MetricsUpdateSignature) -> Bool {
        guard lastMetricsUpdateSignature != signature else { return false }
        lastMetricsUpdateSignature = signature
        metricsUpdateTask?.cancel()
        metricsUpdateTask = nil
        return true
    }

    private func metricsSignatureComponents(for operations: [BathScaleWeightSummary]) -> [String] {
        guard let first = operations.first, let last = operations.last else { return ["empty"] }
        return [
            "\(operations.count)",
            first.period,
            first.entryTimestamp,
            "\(first.weight)",
            last.period,
            last.entryTimestamp,
            "\(last.weight)"
        ]
    }
}
