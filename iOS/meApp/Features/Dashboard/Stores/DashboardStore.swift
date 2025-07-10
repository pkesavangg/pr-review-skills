//
//  DashboardStore.swift
//  meApp
//
//  Created by Lakshmi Priya on 01/07/25.
//

import SwiftUI
import SwiftData
import Combine
import Charts
import os

@MainActor
class DashboardStore: ObservableObject, EntryServiceDelegate {
    @Injector private var notificationService: NotificationHelperService
    @Injector var accountService: AccountService
    @Injector private var logger : LoggerService
    
    private let perfLog = OSLog(subsystem: Bundle.main.bundleIdentifier ?? "DashboardStore", category: "Scrolling")
    
    @Published var isLoading: Bool = false
    @Published var loaderOverride: LoaderModel? = nil
    @Published var alertData: AlertModel? = nil
    
    let lang = LoaderStrings.self
    
    @Published var metricType: DashboardMetricType = .twelve
    
    @Published var isEditMode: Bool = false
    @Published var isGoalCardRemoved: Bool = false
    @Published var activeMetricsCount: Int = 12
    @Published var activeStreakItemsCount: Int = 6
    
    @Published var dropHoverId: String? = nil
    @Published var gridLayoutId = UUID()
    @Published var draggingMetric: MetricItem? = nil
    @Published var draggingStreak: MetricItem? = nil
    
    @Published var selectedMetricLabel: String? = nil
    
    @Published var goalType: GoalType = .gain
    @Published var goalStartWeight: Double = 0.0
    @Published var goalWeight: Double = 0.0
    @Published var goalUnit: WeightUnit = .lb
    @Published var goalDelta: Double = 0.0
    @Published var goalProgress: CGFloat = 0.0
    
    @Published var dailySummaries: [BathScaleWeightSummary?] = []
    @Published var monthlySummaries: [BathScaleWeightSummary?] = []
    private var cancellables = Set<AnyCancellable>()
    
    // Internal caches for fast incremental updates
    private var dailyCache: [String: BathScaleWeightSummary] = [:]
    private var monthlyCache: [String: BathScaleWeightSummary] = [:]
    
    private var latestWeightStored: Int = 0
    @Injector private var entryService: EntryService
    
    // MARK: - Graph Properties
    @Published var selectedEntry: BathScaleOperationDTO? = nil
    @Published var selectedPeriod: TimePeriod = .week
    @Published var xScrollPosition: Date = Date()
    @Published var selectedWeight: Double? = nil
    @Published var scrollEndTimer: Timer?
    @Published var chartHeight: CGFloat = 0
    @Published var annotationHeight: CGFloat = 0
    
    // Crosshair and scroll state
    @Published var showCrosshair: Bool = false
    @Published var selectedPoint: BathScaleWeightSummary? = nil
    @Published var selectedXValue: Date? = nil
    @Published var isScrolling: Bool = false
    @Published var hasDetectedScrollInCurrentGesture: Bool = false
    
    // Data change trigger for graph refresh
    @Published var dataChangeTrigger: Int = 0
    
    private let calendar = Calendar.current

    var loaderData: Binding<LoaderModel?> {
        Binding(
            get: { self.loaderOverride ?? (self.isLoading ? LoaderModel(text: self.lang.saving) : nil) },
            set: { _ in }
        )
    }
    
    private let originalMetrics: [(value: String, label: String, unit: String?, preLabel: String?, icon: String?)] = [
        (DashboardStrings.placeholder, DashboardStrings.bmi, nil, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.bodyFat, DashboardStrings.bodyFatUnit, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.muscle, DashboardStrings.muscleUnit, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.water, DashboardStrings.waterUnit, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.heartBpm, DashboardStrings.heartBpmUnit, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.bone, DashboardStrings.boneUnit, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.visceralFat, nil, DashboardStrings.visceralFatPre, nil),
        (DashboardStrings.placeholder, DashboardStrings.subFat, DashboardStrings.subFatUnit, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.protein, DashboardStrings.proteinUnit, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.skelMuscle, DashboardStrings.skelMuscleUnit, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.bmrKcal, DashboardStrings.bmrKcalUnit, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.metAge, DashboardStrings.metAgeUnit, nil, nil)
    ]
    
    private let originalStreakItems: [(value: String, label: String, unit: String?, preLabel: String?, icon: String?)] = [
        (DashboardStrings.placeholder, DashboardStrings.currentStreak, nil, nil, AppAssets.streak),
        (DashboardStrings.placeholder, DashboardStrings.longestStreak, nil, nil, AppAssets.longestStreak),
        (DashboardStrings.placeholder, DashboardStrings.lbsWeek, nil, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.lbsMonth, nil, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.lbsYear, nil, nil, nil),
        (DashboardStrings.placeholder, DashboardStrings.lbsTotal, nil, nil, nil)
    ]
    
    @Published var metrics: [MetricItem] = []
    @Published var streakItems: [MetricItem] = []
    
    
//    
    init() {
        metrics = originalMetrics.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }
        streakItems = originalStreakItems.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }
        
        // Initialize data loading
        Task {
            loadLatestEntryData()
            await loadInitialData()
        }
    }
    
    // MARK: - Graph Computed Properties (merged from GraphStore)
    var continuousOperations: [BathScaleWeightSummary] {
        switch selectedPeriod {
        case .week:
            // For week view, use daily summaries (average of each day)
            return dailySummaries.compactMap { $0 }.sorted { $0.date < $1.date }
        case .month:
            // For month view, use daily summaries (average of each day in the month)
            return dailySummaries.compactMap { $0 }.sorted { $0.date < $1.date }
        case .year:
            // For year view, use monthly summaries but limit to 12 values per year
            let monthlyData = monthlySummaries.compactMap { $0 }.sorted { $0.date < $1.date }
            return limitToYearlyData(monthlyData)
        case .total:
            // For total view, check if entries are in the same era (same year)
            let monthlyData = monthlySummaries.compactMap { $0 }.sorted { $0.date < $1.date }
            if areEntriesInSameEra(monthlyData) {
                // If entries are in the same era, treat like year view
                return limitToYearlyData(monthlyData)
            } else {
                // If entries span multiple years, use all monthly summaries
                return monthlyData
            }
        }
    }
    
    /// Check if all entries are in the same era (same year)
    private func areEntriesInSameEra(_ summaries: [BathScaleWeightSummary]) -> Bool {
        guard !summaries.isEmpty else { return true }
        
        let years = Set(summaries.map { calendar.component(.year, from: $0.date) })
        return years.count == 1
    }
    
    /// Check if entries span less than a year
    private func doEntriesSpanLessThanYear(_ summaries: [BathScaleWeightSummary]) -> Bool {
        guard summaries.count >= 2 else { return true }
        
        let minDate = summaries.map(\.date).min() ?? Date()
        let maxDate = summaries.map(\.date).max() ?? Date()
        
        let timeInterval = maxDate.timeIntervalSince(minDate)
        let oneYearInSeconds: TimeInterval = 365 * 24 * 60 * 60
        
        return timeInterval < oneYearInSeconds
    }
    
    /// Limits monthly data to 12 values per year for year view
    private func limitToYearlyData(_ monthlyData: [BathScaleWeightSummary]) -> [BathScaleWeightSummary] {
        var result: [BathScaleWeightSummary] = []
        var currentYear: Int?
        var yearCount = 0
        
        for summary in monthlyData {
            let year = calendar.component(.year, from: summary.date)
            
            if currentYear != year {
                currentYear = year
                yearCount = 0
            }
            
            if yearCount < 12 {
                result.append(summary)
                yearCount += 1
            }
        }
        
        return result
    }
    
    var weightLabel: String {
        guard !continuousOperations.isEmpty else {
            return fallbackTimeLabel()
        }

        // If a point is selected, show its date
        if let selectedPoint = selectedPoint {
            return formatSelectedDate(selectedPoint.date)
        }
        
        if let selectedEntry = selectedEntry {
            if let date = selectedEntry.date {
                return formatSelectedDate(date)
            }
            if let originalSummary = findOriginalSummary(for: selectedEntry) {
                return formatSelectedDate(originalSummary.date)
            }
        }

        // Otherwise show the period range for visible data
        let visibleOps = getVisibleOperations()
        let opsToUse = visibleOps.isEmpty ? continuousOperations : visibleOps
        guard let minDate = opsToUse.map(\.date).min(),
              let maxDate = opsToUse.map(\.date).max() else {
            return fallbackTimeLabel()
        }

        switch selectedPeriod {
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

    private func fallbackTimeLabel() -> String {
        let now = Date()
        
        switch selectedPeriod {
        case .week:
            let formatter = DateTimeTools.formatter("MMM d")
            if let week = calendar.dateInterval(of: .weekOfYear, for: now) {
                let start = formatter.string(from: week.start)
                let end = DateTimeTools.formatter("d").string(from: week.end.addingTimeInterval(-1))
                let year = calendar.component(.year, from: now)
                return "\(start)-\(end), \(year)"
            }

            // If week interval fails, manually calculate 7-day range
            let start = formatter.string(from: calendar.date(byAdding: .day, value: -3, to: now) ?? now)
            let end = formatter.string(from: calendar.date(byAdding: .day, value: 3, to: now) ?? now)
            let year = calendar.component(.year, from: now)
            return "\(start)-\(end), \(year)"
            
        case .month:
            return DateTimeTools.formatter("LLLL yyyy").string(from: now)
            
        case .year, .total:
            return DateTimeTools.formatter("yyyy").string(from: now)
        }
    }

    
    /// Find the original BathScaleWeightSummary for a selected DTO
    private func findOriginalSummary(for dto: BathScaleOperationDTO) -> BathScaleWeightSummary? {
        return continuousOperations.first { summary in
            summary.entryTimestamp == dto.entryTimestamp
        }
    }
    
    /// Format date for selected point display
    /// Shows "Jul 5, 2024" for week/month periods
    /// Shows "Jul 2024" for year/total periods
    private func formatSelectedDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        switch selectedPeriod {
        case .week, .month:
            formatter.dateFormat = "MMM d, yyyy"
        case .year, .total:
            formatter.dateFormat = "MMM yyyy"
        }
        return formatter.string(from: date)
    }
    
    /// Format date for chart annotation display
    /// Shows "Jul 5" for week/month periods
    /// Shows "Jul 2024" for year/total periods
    func formatChartDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        switch selectedPeriod {
        case .week, .month:
            formatter.dateFormat = "MMM d"
        case .year, .total:
            formatter.dateFormat = "MMM yyyy"
        }
        return formatter.string(from: date)
    }
    
    var displayWeight: Double? {
        // If a point is selected, show its weight value
        if let selectedPoint = selectedPoint {
            if isWeightlessModeEnabled {
                guard let anchorWeight = weightlessAnchorWeight else { return nil }
                let currentWeight = convertStoredWeightToDisplay(Int(selectedPoint.weight))
                return currentWeight - anchorWeight
            } else {
                return convertStoredWeightToDisplay(Int(selectedPoint.weight))
            }
        }
        
        // When no point is selected, show average of all operations in current time period
        let allOps = continuousOperations
        
        // Check if weightless mode is enabled
        if isWeightlessModeEnabled {
            return calculateWeightlessDisplay(allOps)
        }
        
        // Calculate average of all operations in current time period
        let weights = allOps.map { convertStoredWeightToDisplay(Int($0.weight)) }
        guard !weights.isEmpty else { return nil }
        let averageWeight = weights.reduce(0, +) / Double(weights.count)
        
        print("Hello: displayWeight - All operations: \(allOps.count), Average weight: \(averageWeight)")
        
        return averageWeight
    }
    
    /// Check if weightless mode is enabled for the current account
    /// Weightless mode shows +/- progress instead of actual weight values
    var isWeightlessModeEnabled: Bool {
        return accountService.activeAccount?.weightlessSettings?.isWeightlessOn ?? false
    }
    
    /// Get the weightless anchor weight in display units
    /// This is the reference weight from which differences are calculated
    var weightlessAnchorWeight: Double? {
        guard let weightlessWeight = accountService.activeAccount?.weightlessSettings?.weightlessWeight else {
            return nil
        }
        return convertStoredWeightToDisplay(Int(weightlessWeight))
    }
    
    /// Get the goal weight in the correct format for the current mode
    /// In weightless mode, returns the difference from anchor weight
    /// In normal mode, returns the actual goal weight
    var goalWeightForDisplay: Double {
        if isWeightlessModeEnabled {
            guard let anchorWeight = weightlessAnchorWeight else { return goalWeight }
            return goalWeight - anchorWeight
        } else {
            return goalWeight
        }
    }
    
    /// Calculate weightless display value (difference from anchor weight)
    /// In weightless mode, shows the difference from the chosen anchor weight
    /// Positive values indicate weight gain, negative values indicate weight loss
    private func calculateWeightlessDisplay(_ operations: [BathScaleWeightSummary]) -> Double? {
        guard let anchorWeight = weightlessAnchorWeight else { return nil }
        
        // Use all operations in current time period, not just visible ones
        let allOps = operations
        
        switch selectedPeriod {
        case .week:
            // For week view, show the difference from the latest day's weight
            guard let latestWeight = allOps.last.map({ convertStoredWeightToDisplay(Int($0.weight)) }) else {
                return nil
            }
            return latestWeight - anchorWeight
        case .month:
            // For month view, show the difference from the latest day's weight
            guard let latestWeight = allOps.last.map({ convertStoredWeightToDisplay(Int($0.weight)) }) else {
                return nil
            }
            return latestWeight - anchorWeight
        case .year:
            // For year view, show the average difference from all monthly data
            let weights = allOps.map { convertStoredWeightToDisplay(Int($0.weight)) }
            guard !weights.isEmpty else { return nil }
            let averageWeight = weights.reduce(0, +) / Double(weights.count)
            return averageWeight - anchorWeight
        case .total:
            // For total view, show the average difference from all monthly data
            let weights = allOps.map { convertStoredWeightToDisplay(Int($0.weight)) }
            guard !weights.isEmpty else { return nil }
            let averageWeight = weights.reduce(0, +) / Double(weights.count)
            return averageWeight - anchorWeight
        }
    }
    
    /// Format weight display text based on weightless mode
    /// In weightless mode, shows +/- prefix for differences from anchor weight
    /// In normal mode, shows actual weight values
    func formatWeightDisplayText(_ weight: Double?) -> String {
        guard let weight = weight else { return "0" }
        
        if isWeightlessModeEnabled {
            // Show +/- prefix for weightless mode
            let prefix = weight >= 0 ? "+" : ""
            return String(format: "%@%.0f", prefix, weight)
        } else {
            // Show normal weight as whole number
            return String(format: "%.0f", weight)
        }
    }
    
    
    
    @MainActor
    func loadLatestEntryData() {
        Task {
            do {
                guard let latestEntry = try await entryService.getLatestEntry() else {
                    return
                }
                if let weight = latestEntry.scaleEntry?.weight {
                    latestWeightStored = weight
                }
                updateMetricsWithEntry(latestEntry)
            } catch {
                logger.log(level: .error, tag: "DashboardStore", message: "Failed to load latest entry data: \(error)")
            }
        }
    }
    
    private func updateMetricsWithEntry(_ entry: Entry) {
        if let bmi = entry.scaleEntry?.bmi {
            let formattedValue = BodyMetricsConvertor.convert(Double(bmi), shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.bmi, value: formattedValue)
        }
        
        if let bodyFat = entry.scaleEntry?.bodyFat {
            let formattedValue = BodyMetricsConvertor.convert(Double(bodyFat), shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.bodyFat, value: formattedValue)
        }
        
        if let muscleMass = entry.scaleEntry?.muscleMass {
            let formattedValue = BodyMetricsConvertor.convert(Double(muscleMass), shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.muscle, value: formattedValue)
        }
        
        if let water = entry.scaleEntry?.water {
            let formattedValue = BodyMetricsConvertor.convert(Double(water), shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.water, value: formattedValue)
        }
        
        if let pulse = entry.scaleEntryMetric?.pulse {
            let formattedValue = BodyMetricsConvertor.convert(Double(pulse), shouldCompose: false, wholeNumber: true)
            updateMetricValue(for: DashboardStrings.heartBpm, value: formattedValue)
        }
        
        if let boneMass = entry.scaleEntryMetric?.boneMass {
            let formattedValue = BodyMetricsConvertor.convert(Double(boneMass), shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.bone, value: formattedValue)
        }
        
        if let visceralFat = entry.scaleEntryMetric?.visceralFatLevel {
            let formattedValue = BodyMetricsConvertor.convert(Double(visceralFat), shouldCompose: false, wholeNumber: true)
            updateMetricValue(for: DashboardStrings.visceralFat, value: formattedValue)
        }
        
        if let subFat = entry.scaleEntryMetric?.subcutaneousFatPercent {
            let formattedValue = BodyMetricsConvertor.convert(Double(subFat), shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.subFat, value: formattedValue)
        }
        
        if let protein = entry.scaleEntryMetric?.proteinPercent {
            let formattedValue = BodyMetricsConvertor.convert(Double(protein), shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.protein, value: formattedValue)
        }
        
        if let skelMuscle = entry.scaleEntryMetric?.skeletalMusclePercent {
            let formattedValue = BodyMetricsConvertor.convert(Double(skelMuscle), shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.skelMuscle, value: formattedValue)
        }
        
        if let bmr = entry.scaleEntryMetric?.bmr {
            let formattedValue = BodyMetricsConvertor.convert(Double(bmr), shouldCompose: false, wholeNumber: true)
            updateMetricValue(for: DashboardStrings.bmrKcal, value: formattedValue)
        }
        
        if let metabolicAge = entry.scaleEntryMetric?.metabolicAge {
            let formattedValue = BodyMetricsConvertor.convert(Double(metabolicAge), shouldCompose: false, wholeNumber: true)
            updateMetricValue(for: DashboardStrings.metAge, value: formattedValue)
        }
    }
    
    /// Update metric values with selected point data
    private func updateMetricsWithSelectedPoint(_ selectedPoint: BathScaleWeightSummary) {
        if let bmi = selectedPoint.bmi {
            let formattedValue = BodyMetricsConvertor.convert(bmi, shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.bmi, value: formattedValue)
        }
        
        if let bodyFat = selectedPoint.bodyFat {
            let formattedValue = BodyMetricsConvertor.convert(bodyFat, shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.bodyFat, value: formattedValue)
        }
        
        if let muscleMass = selectedPoint.muscleMass {
            let formattedValue = BodyMetricsConvertor.convert(muscleMass, shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.muscle, value: formattedValue)
        }
        
        if let water = selectedPoint.water {
            let formattedValue = BodyMetricsConvertor.convert(water, shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.water, value: formattedValue)
        }
        
        if let pulse = selectedPoint.pulse {
            let formattedValue = BodyMetricsConvertor.convert(Double(pulse), shouldCompose: false, wholeNumber: true)
            updateMetricValue(for: DashboardStrings.heartBpm, value: formattedValue)
        }
        
        if let boneMass = selectedPoint.boneMass {
            let formattedValue = BodyMetricsConvertor.convert(boneMass, shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.bone, value: formattedValue)
        }
        
        if let visceralFat = selectedPoint.visceralFatLevel {
            let formattedValue = BodyMetricsConvertor.convert(visceralFat, shouldCompose: false, wholeNumber: true)
            updateMetricValue(for: DashboardStrings.visceralFat, value: formattedValue)
        }
        
        if let subFat = selectedPoint.subcutaneousFatPercent {
            let formattedValue = BodyMetricsConvertor.convert(subFat, shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.subFat, value: formattedValue)
        }
        
        if let protein = selectedPoint.proteinPercent {
            let formattedValue = BodyMetricsConvertor.convert(protein, shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.protein, value: formattedValue)
        }
        
        if let skelMuscle = selectedPoint.skeletalMusclePercent {
            let formattedValue = BodyMetricsConvertor.convert(skelMuscle, shouldCompose: true, wholeNumber: false)
            updateMetricValue(for: DashboardStrings.skelMuscle, value: formattedValue)
        }
        
        if let bmr = selectedPoint.bmr {
            let formattedValue = BodyMetricsConvertor.convert(bmr, shouldCompose: false, wholeNumber: true)
            updateMetricValue(for: DashboardStrings.bmrKcal, value: formattedValue)
        }
        
        if let metabolicAge = selectedPoint.metabolicAge {
            let formattedValue = BodyMetricsConvertor.convert(Double(metabolicAge), shouldCompose: false, wholeNumber: true)
            updateMetricValue(for: DashboardStrings.metAge, value: formattedValue)
        }
    }
    
    private func updateMetricValue(for label: String, value: String) {
        if let index = metrics.firstIndex(where: { $0.label == label }) {
            metrics[index] = MetricItem(
                value: value,
                label: metrics[index].label,
                unit: metrics[index].unit,
                preLabel: metrics[index].preLabel,
                icon: metrics[index].icon
            )
        }
    }
    
    var metricGridColumns: [GridItem] {
        metricType == .four ?
        Array(repeating: GridItem(.flexible(), spacing: 16), count: 2) :
        Array(repeating: GridItem(.flexible(), spacing: 16), count: 3)
    }
    
    var metricsToShow: [MetricItem] {
        let metricsToProcess = isEditMode ? metrics : Array(metrics.prefix(activeMetricsCount))
        if metricType == .four {
            let fourLabels: Set<String> = [DashboardStrings.bmi, DashboardStrings.bodyFat, DashboardStrings.muscle, DashboardStrings.water]
            return metricsToProcess.filter { fourLabels.contains($0.label) }
        } else {
            return metricsToProcess
        }
    }
    
    let streakColumns = Array(repeating: GridItem(.flexible(), spacing: 16), count: 2)
    
    var streakItemsToShow: [MetricItem] {
        isEditMode ? streakItems : Array(streakItems.prefix(activeStreakItemsCount))
    }
    
    func createEntryForMetricInfo() -> Entry {
        // If a point is selected, use its values
        if let selectedPoint = selectedPoint {
            let entry = Entry(
                id: UUID(),
                entryTimestamp: DateTimeTools.getCurrentDatetimeIsoString(),
                accountId: "dashboard",
                operationType: OperationType.create.rawValue,
                deviceType: "scale",
                isSynced: true
            )
            entry.scaleEntry = BathScaleEntry(
                weight: Int(selectedPoint.weight),
                bodyFat: selectedPoint.bodyFat.map { Int($0) },
                muscleMass: selectedPoint.muscleMass.map { Int($0) },
                water: selectedPoint.water.map { Int($0) },
                bmi: selectedPoint.bmi.map { Int($0) },
                source: "dashboard"
            )
            entry.scaleEntryMetric = BathScaleMetric(
                bmr: selectedPoint.bmr.map { Int($0) },
                metabolicAge: Int(selectedPoint.metabolicAge ?? 0),
                proteinPercent: selectedPoint.proteinPercent.map { Int($0) },
                pulse: Int(selectedPoint.pulse ?? 0),
                skeletalMusclePercent: selectedPoint.skeletalMusclePercent.map { Int($0) },
                subcutaneousFatPercent: selectedPoint.subcutaneousFatPercent.map { Int($0) },
                visceralFatLevel: selectedPoint.visceralFatLevel.map { Int($0) },
                boneMass: selectedPoint.boneMass.map { Int($0) },
                impedance: nil,
                unit: nil
            )
            return entry
        }
        
        // Fallback to using metric values from the dashboard
        let bmiStr = metrics.first(where: { $0.label == DashboardStrings.bmi })?.value
        let bodyFatStr = metrics.first(where: { $0.label == DashboardStrings.bodyFat })?.value
        let muscleStr = metrics.first(where: { $0.label == DashboardStrings.muscle })?.value
        let waterStr = metrics.first(where: { $0.label == DashboardStrings.water })?.value
        
        let bmi = bmiStr.flatMap { Double($0) }.flatMap { Int($0) }
        let bodyFat = bodyFatStr.flatMap { Double($0) }.flatMap { Int($0) }
        let muscleMass = muscleStr.flatMap { Double($0) }.flatMap { Int($0) }
        let water = waterStr.flatMap { Double($0) }.flatMap { Int($0) }
        let actualWeight = latestWeightStored
        
        let bmrStr = metrics.first(where: { $0.label == DashboardStrings.bmrKcal })?.value
        let metabolicAgeStr = metrics.first(where: { $0.label == DashboardStrings.metAge })?.value
        let pulseStr = metrics.first(where: { $0.label == DashboardStrings.heartBpm })?.value
        let skeletalMuscleStr = metrics.first(where: { $0.label == DashboardStrings.skelMuscle })?.value
        let subFatStr = metrics.first(where: { $0.label == DashboardStrings.subFat })?.value
        let visceralFatStr = metrics.first(where: { $0.label == DashboardStrings.visceralFat })?.value
        let boneStr = metrics.first(where: { $0.label == DashboardStrings.bone })?.value
        let proteinStr = metrics.first(where: { $0.label == DashboardStrings.protein })?.value
        
        let bmr = bmrStr.flatMap { Double($0) }.flatMap { Int($0) }
        let metabolicAge = metabolicAgeStr.flatMap { Double($0) }.flatMap { Int($0) }
        let pulse = pulseStr.flatMap { Double($0) }.flatMap { Int($0) }
        let skeletalMusclePercent = skeletalMuscleStr.flatMap { Double($0) }.flatMap { Int($0) }
        let subcutaneousFatPercent = subFatStr.flatMap { Double($0) }.flatMap { Int($0) }
        let visceralFatLevel = visceralFatStr.flatMap { Double($0) }.flatMap { Int($0) }
        let boneMass = boneStr.flatMap { Double($0) }.flatMap { Int($0) }
        let proteinPercent = proteinStr.flatMap { Double($0) }.flatMap { Int($0) }
        
        let entry = Entry(
            id: UUID(),
            entryTimestamp: DateTimeTools.getCurrentDatetimeIsoString(),
            accountId: "dashboard",
            operationType: OperationType.create.rawValue,
            deviceType: "scale",
            isSynced: true
        )
        entry.scaleEntry = BathScaleEntry(
            weight: actualWeight,
            bodyFat: bodyFat,
            muscleMass: muscleMass,
            water: water,
            bmi: bmi,
            source: "dashboard"
        )
        entry.scaleEntryMetric = BathScaleMetric(
            bmr: bmr,
            metabolicAge: metabolicAge,
            proteinPercent: proteinPercent,
            pulse: pulse,
            skeletalMusclePercent: skeletalMusclePercent,
            subcutaneousFatPercent: subcutaneousFatPercent,
            visceralFatLevel: visceralFatLevel,
            boneMass: boneMass,
            impedance: nil,
            unit: nil
        )
        return entry
    }
    
    func createEntryForMetricInfo(metricLabel: String) -> Entry {
        return createEntryForMetricInfo()
    }
    
    func getBodyMetric(for metricLabel: String) -> BodyMetric {
        switch metricLabel {
        case DashboardStrings.bmi:
            return .bmi
        case DashboardStrings.bodyFat:
            return .bodyFat
        case DashboardStrings.muscle:
            return .muscleMass
        case DashboardStrings.water:
            return .water
        default:
            return .weight
        }
    }
    
    func toggleMetricRemoval(at index: Int) {
        guard index < metrics.count else { return }
        let metric = metrics[index]
        let isCurrentlyRemoved = isMetricRemoved(at: index)
        metrics.remove(at: index)
        if isCurrentlyRemoved {
            metrics.insert(metric, at: activeMetricsCount)
            activeMetricsCount += 1
        } else {
            metrics.append(metric)
            activeMetricsCount -= 1
        }
        
        resetDragState()
    }
    
    func isMetricRemoved(at index: Int) -> Bool {
        guard index < metrics.count else { return false }
        return index >= activeMetricsCount
    }
    
    func toggleGoalCardRemoval() {
        isGoalCardRemoved.toggle()
    }
    
    func toggleStreakRemoval(at index: Int) {
        guard index < streakItems.count else { return }
        let item = streakItems[index]
        let isCurrentlyRemoved = isStreakRemoved(at: index)
        streakItems.remove(at: index)
        if isCurrentlyRemoved {
            streakItems.insert(item, at: activeStreakItemsCount)
            activeStreakItemsCount += 1
        } else {
            streakItems.append(item)
            activeStreakItemsCount -= 1
        }
        
        resetDragState()
    }
    
    func isStreakRemoved(at index: Int) -> Bool {
        guard index < streakItems.count else { return false }
        return index >= activeStreakItemsCount
    }
    
    func resetDashboard() {
        isLoading = true
        loaderOverride = LoaderModel(text: lang.saving)
        
        selectedMetricLabel = nil
        isEditMode = false
        resetDragState()
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            withAnimation(.easeInOut(duration: 0.3)) {
                self.isLoading = false
                self.loaderOverride = nil
                self.restoreOriginalMetricOrder()
                self.restoreOriginalStreakOrder()
                self.activeMetricsCount = self.originalMetrics.count
                self.activeStreakItemsCount = self.originalStreakItems.count
                self.isGoalCardRemoved = false
                
                self.selectedMetricLabel = nil
                self.isEditMode = false
                self.resetDragState()
                
                self.gridLayoutId = UUID()
            }
        }
    }
    
    @MainActor
    func showResetDashboardAlert() {
        let alertLang = AlertStrings.ResetDashboardAlert.self
        let alert = AlertModel(
            title: alertLang.title,
            message: alertLang.message,
            buttons: [
                AlertButtonModel(title: alertLang.cancelButton, type: .secondary) { _ in
                },
                AlertButtonModel(title: alertLang.resetButton, type: .primary) { _ in
                    self.resetDashboard()
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    func saveChanges() {
        isLoading = true
        loaderOverride = LoaderModel(text: lang.saving)
        
        selectedMetricLabel = nil
        resetDragState()
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            withAnimation(.easeInOut(duration: 0.3)) {
                self.isLoading = false
                self.loaderOverride = nil
                self.isEditMode = false
                self.resetDragState()
            }
        }
    }
    
    private func restoreOriginalMetricOrder() {
        metrics = originalMetrics.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }
    }
    private func restoreOriginalStreakOrder() {
        streakItems = originalStreakItems.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }
    }
    
    func isMetricRemovedInReorderedArray(at reorderedIndex: Int) -> Bool {
        let metricsToShow = self.metricsToShow
        guard reorderedIndex < metricsToShow.count else { return false }
        let metric = metricsToShow[reorderedIndex]
        guard let originalIndex = metrics.firstIndex(where: { $0.id == metric.id }) else { return false }
        return isMetricRemoved(at: originalIndex)
    }
    func toggleMetricRemovalInReorderedArray(at reorderedIndex: Int) {
        let metricsToShow = self.metricsToShow
        guard reorderedIndex < metricsToShow.count else { return }
        let metric = metricsToShow[reorderedIndex]
        guard let originalIndex = metrics.firstIndex(where: { $0.id == metric.id }) else { return }
        toggleMetricRemoval(at: originalIndex)
    }
    func isStreakRemovedInReorderedArray(at reorderedIndex: Int) -> Bool {
        let streakItemsToShow = self.streakItemsToShow
        guard reorderedIndex < streakItemsToShow.count else { return false }
        let streak = streakItemsToShow[reorderedIndex]
        guard let originalIndex = streakItems.firstIndex(where: { $0.id == streak.id }) else { return false }
        return isStreakRemoved(at: originalIndex)
    }
    func toggleStreakRemovalInReorderedArray(at reorderedIndex: Int) {
        let streakItemsToShow = self.streakItemsToShow
        guard reorderedIndex < streakItemsToShow.count else { return }
        let streak = streakItemsToShow[reorderedIndex]
        guard let originalIndex = streakItems.firstIndex(where: { $0.id == streak.id }) else { return }
        toggleStreakRemoval(at: originalIndex)
    }
    
    func selectMetric(_ label: String) {
        if selectedMetricLabel == label {
            selectedMetricLabel = nil
        } else {
            selectedMetricLabel = label
        }
    }
    
    func resetDragState() {
        draggingMetric = nil
        draggingStreak = nil
        dropHoverId = nil
        
        gridLayoutId = UUID()
    }
    
    var isAnyItemBeingDragged: Bool {
        draggingMetric != nil || draggingStreak != nil
    }
    
    @MainActor
    var allContentRemoved: Bool {
        metricsToShow.isEmpty && (!isEditMode && isGoalCardRemoved) && (!shouldShowStreakGrid)
    }
    
    @MainActor
    var isStreakEnabled: Bool {
        accountService.activeAccount?.streaksSettings?.isStreakOn ?? false
    }
    
    @MainActor
    var shouldShowStreakGrid: Bool {
        isStreakEnabled && !streakItemsToShow.isEmpty
    }
    
    func formattedMetricValue(for metric: (preLabel: String?, value: String)) -> String {
        metric.preLabel.map { "\($0) \(metric.value)" } ?? metric.value
    }
    
    var selectedBodyMetric: BodyMetric {
        guard let selectedLabel = selectedMetricLabel else { return .weight }
        switch selectedLabel {
        case DashboardStrings.bmi:
            return .bmi
        case DashboardStrings.bodyFat:
            return .bodyFat
        case DashboardStrings.muscle:
            return .muscleMass
        case DashboardStrings.water:
            return .water
        case DashboardStrings.heartBpm:
            return .pulse
        case DashboardStrings.bone:
            return .boneMass
        case DashboardStrings.visceralFat:
            return .visceralFatLevel
        case DashboardStrings.subFat:
            return .subcutaneousFatPercent
        case DashboardStrings.protein:
            return .proteinPercent
        case DashboardStrings.skelMuscle:
            return .skeletalMusclePercent
        case DashboardStrings.bmrKcal:
            return .bmr
        case DashboardStrings.metAge:
            return .metabolicAge
        default:
            return .weight
        }
    }
    
    @MainActor
    private func displayWeight(fromStored stored: Int) -> Double {
        let unit = accountService.activeAccount?.weightSettings?.weightUnit ?? .lb
        if unit == .kg {
            return ConversionTools.convertStoredToKg(stored)
        } else {
            return ConversionTools.convertStoredToLbs(stored)
        }
    }
    
    /// Convert stored weight to display weight based on user's unit preference
    @MainActor
    func convertStoredWeightToDisplay(_ storedWeight: Int) -> Double {
        let unit = accountService.activeAccount?.weightSettings?.weightUnit ?? .lb
        if unit == .kg {
            return ConversionTools.convertStoredToKg(storedWeight)
        } else {
            return ConversionTools.convertStoredToLbs(storedWeight)
        }
    }
    
    /// Handle unit change specifically
    @MainActor
    func handleUnitChange() {
        loadGoalCardData()
        // Force graph refresh by incrementing data change trigger
        dataChangeTrigger += 1
        objectWillChange.send()
    }
    
    /// Handle weightless mode change specifically
    @MainActor
    func handleWeightlessModeChange() {
        // Force graph refresh by incrementing data change trigger
        dataChangeTrigger += 1
        objectWillChange.send()
    }
    
    /// Handle account changes efficiently
    @MainActor
    func handleAccountChange() {
        loadGoalCardData()
        objectWillChange.send()
    }
    
    /// Handle entry changes (add/update/delete) with unit and weightless mode updates
    @MainActor
    func handleEntryChange() {
        // Force graph refresh by incrementing data change trigger
        dataChangeTrigger += 1
        
        // Update metrics with latest entry
        Task {
            await loadLatestEntryData()
        }
    }
    
    /// Handle settings changes (unit, weightless mode, etc.)
    @MainActor
    func handleSettingsChange() {
        loadGoalCardData()
        objectWillChange.send()
    }
    
    /// Get current unit for reactive updates
    var currentUnit: WeightUnit {
        accountService.activeAccount?.weightSettings?.weightUnit ?? .lb
    }
    
    /// Get current weightless mode for reactive updates
    var currentWeightlessMode: Bool {
        accountService.activeAccount?.weightlessSettings?.isWeightlessOn ?? false
    }
    
    // MARK: - Scroll Handling
    
    /// Update scroll position with debouncing to prevent performance issues
    @MainActor
    func updateScrollPositionDebounced(to date: Date) {
        scrollEndTimer?.invalidate()
        scrollEndTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: false) { [weak self] _ in
            DispatchQueue.main.async {
                self?.xScrollPosition = date
            }
        }
    }
        

    

    
    /// Handle chart selection at a specific date
    @MainActor
    func handleChartSelection(at selectedDate: Date) {
        // Only handle selection if not currently scrolling
        guard !isScrolling else { return }
        
        // Hide any existing crosshair first
        showCrosshair = false
        
        guard !continuousOperations.isEmpty else { return }
        
        // Find the closest data point to the selected date
        let selectedPoint = continuousOperations.min { point1, point2 in
            abs(point1.date.timeIntervalSince(selectedDate)) < abs(point2.date.timeIntervalSince(selectedDate))
        }
        
        guard let selectedPoint = selectedPoint else { 
            // Clear selection if no point found
            self.selectedPoint = nil
            selectEntry(nil)
            // Reset to latest entry values
            resetMetricsToLatestEntry()
            return 
        }
        
        // Set the selected point and show crosshair
        self.selectedPoint = selectedPoint
        selectEntry(selectedPoint)
        
        // Update metric values with selected point data
        updateMetricsWithSelectedPoint(selectedPoint)
        
        // Force UI update
        DispatchQueue.main.async {
            self.showCrosshair = true
        }
    }
    

    
    /// Ensure chart shows the latest entries by default
    /// This method should be called when data is loaded or when switching periods
    @MainActor
    func ensureLatestEntriesVisible() {
        guard let latestDate = continuousOperations.map(\.date).max() else { return }
        xScrollPosition = latestDate
    }
    
    /// Get the latest date from all operations
    /// This is used to set the initial scroll position
    var latestDate: Date? {
        return continuousOperations.map(\.date).max()
    }
    
    @MainActor
    func loadGoalCardData() {
        Task { [weak self] in
            guard let self = self else { return }
            do {
                let latestEntry = try await self.entryService.getLatestEntry()
                var currentWeightStored: Int = 0
                if let latestWeight = latestEntry?.scaleEntry?.weight {
                    currentWeightStored = latestWeight
                }
                
                guard let account = self.accountService.activeAccount,
                      let goalSettings = account.goalSettings else {
                    logger.log(level: .error, tag: "DashboardStore", message: "No account or goal settings found")
                    return
                }
                
                self.goalType = goalSettings.goalType ?? .gain
                self.goalUnit = account.weightSettings?.weightUnit ?? .lb
                
                let initialWeightStored = Int(goalSettings.initialWeight ?? 0)
                self.goalStartWeight = self.displayWeight(fromStored: initialWeightStored)
                if let goalW = goalSettings.goalWeight {
                    self.goalWeight = self.displayWeight(fromStored: Int(goalW))
                }
                let currentWeight = self.displayWeight(fromStored: currentWeightStored)
                self.goalDelta = currentWeight - self.goalStartWeight
                
                let totalDistance = abs(self.goalWeight - self.goalStartWeight)
                let achievedDistance = abs(currentWeight - self.goalStartWeight)
                
                if totalDistance > 0 {
                    let progress = min(max(CGFloat(achievedDistance / totalDistance), 0), 1)
                    self.goalProgress = progress
                } else {
                    self.goalProgress = 1.0
                }
                
            } catch {
                logger.log(level: .error, tag: "DashboardStore", message: "Error loading goal card data: \(error)")
            }
        }
    }
    
    /// Refresh graph data when unit changes
    @MainActor
    func refreshGraphData() {
        // Force graph refresh by incrementing data change trigger
        dataChangeTrigger += 1
        objectWillChange.send()
    }
    
    /// Refresh all dashboard data
    @MainActor
    func refreshDashboardData() {
        Task {
            await loadInitialData()
            loadLatestEntryData()
            loadGoalCardData()
            handleSettingsChange()
            if let latestDate = continuousOperations.map(\.date).max() {
                updateScrollPositionDebounced(to: latestDate)
            }
        }
    }
    
    /// Refresh when new entry is added
    @MainActor
    func refreshOnNewEntry() {
        Task {
            await loadInitialData()
            loadLatestEntryData()
            loadGoalCardData()
            handleSettingsChange()
            if let latestDate = continuousOperations.map(\.date).max() {
                updateScrollPositionDebounced(to: latestDate)
            }
        }
    }
    
    /// Refresh when view appears
    @MainActor
    func refreshOnViewAppear() {
        Task {
            await loadInitialData()
            loadLatestEntryData()
            loadGoalCardData()
            handleSettingsChange()
            if let latestDate = continuousOperations.map(\.date).max() {
                updateScrollPositionDebounced(to: latestDate)
            }
        }
    }
    
    // MARK: - Graph Methods (merged from GraphStore)
    
    /// Generate series data for chart rendering
    var chartSeriesData: [GraphSeries] {
        guard !continuousOperations.isEmpty else { 
            print("Hello: DashboardStore - chartSeriesData - No continuous operations available")
            return [] 
        }
        
        var series: [GraphSeries] = []
        
        // Get weight values for normalization
        let weightValues = continuousOperations.map { summary -> Double in
            if isWeightlessModeEnabled {
                guard let anchorWeight = weightlessAnchorWeight else { return 0 }
                let currentWeight = convertStoredWeightToDisplay(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return convertStoredWeightToDisplay(Int(summary.weight))
            }
        }
        
        let weightMin = weightValues.min() ?? 0
        let weightMax = weightValues.max() ?? 1
        
        // Add weight series (always present) - convert to display unit
        for summary in continuousOperations {
            let displayWeight: Double
            if isWeightlessModeEnabled {
                // In weightless mode, show difference from anchor weight
                guard let anchorWeight = weightlessAnchorWeight else { continue }
                let currentWeight = convertStoredWeightToDisplay(Int(summary.weight))
                displayWeight = currentWeight - anchorWeight
            } else {
                // Normal mode - show actual weight
                displayWeight = convertStoredWeightToDisplay(Int(summary.weight))
            }
            
            series.append(GraphSeries(
                date: summary.date,
                value: displayWeight,
                series: DashboardStrings.weight
            ))
        }
        
        // Add selected metric series (if a metric is selected) - normalized to weight range
        if let selectedMetric = selectedMetricLabel, selectedMetric != DashboardStrings.weight {
            for summary in continuousOperations {
                if let metricValue = getMetricValue(for: summary) {
                    // Normalize metric value to weight range
                    let normalizedValue = normalizeMetricValue(metricValue, for: selectedMetric, toWeightRange: weightMin...weightMax)
                    series.append(GraphSeries(
                        date: summary.date,
                        value: normalizedValue,
                        series: selectedMetric
                    ))
                }
            }
        }
        
        print("Hello: DashboardStore - chartSeriesData - Generated \(series.count) series data points")
        print("Hello: DashboardStore - chartSeriesData - Weight series count: \(series.filter { $0.series == DashboardStrings.weight }.count)")
        print("Hello: DashboardStore - chartSeriesData - Weight values: \(series.filter { $0.series == DashboardStrings.weight }.map { $0.value })")
        
        return series
    }
    
    /// Normalize metric value to weight range for chart display
    private func normalizeMetricValue(_ value: Double, for metricLabel: String, toWeightRange weightRange: ClosedRange<Double>) -> Double {
        let weightMin = weightRange.lowerBound
        let weightMax = weightRange.upperBound
        let weightSpan = weightMax - weightMin
        
        // Define metric ranges based on metric type
        let (metricMin, metricMax): (Double, Double) = {
            switch metricLabel {
            case DashboardStrings.bmi:
                return (18.0, 35.0) // BMI range
            case DashboardStrings.bodyFat, DashboardStrings.muscle, DashboardStrings.water, 
                 DashboardStrings.bone, DashboardStrings.subFat, DashboardStrings.protein, 
                 DashboardStrings.skelMuscle:
                return (0.0, 100.0) // Percentage ranges (0-100%)
            case DashboardStrings.heartBpm:
                return (40.0, 200.0) // Heart rate range
            case DashboardStrings.visceralFat:
                return (1.0, 30.0) // Visceral fat level range
            case DashboardStrings.bmrKcal:
                return (1000.0, 3000.0) // BMR range
            case DashboardStrings.metAge:
                return (15.0, 80.0) // Metabolic age range
            default:
                return (0.0, 100.0) // Default percentage range
            }
        }()
        
        let metricSpan = metricMax - metricMin
        
        // Clamp value to metric range
        let clampedValue = max(metricMin, min(metricMax, value))
        
        // Normalize to weight range
        let normalizedValue = weightMin + (clampedValue - metricMin) * weightSpan / metricSpan
        
        print("Hello: normalizeMetricValue - Metric: \(metricLabel), Original: \(value), Range: [\(metricMin), \(metricMax)], Normalized: \(normalizedValue), WeightRange: [\(weightMin), \(weightMax)]")
        
        return normalizedValue
    }
    
    func updateSelectedPeriod(_ period: TimePeriod) {
        selectedPeriod = period
        
        // Clear crosshair and selection when time period changes
        showCrosshair = false
        selectedPoint = nil
        selectedXValue = nil
        selectedEntry = nil
        selectedWeight = nil
        
        // Ensure chart shows the latest entries when switching periods
        ensureLatestEntriesVisible()
        selectedWeight = displayWeight
    }
    

    
    func selectEntry(_ entry: BathScaleWeightSummary?) {
        if let entry = entry {
            selectedEntry = BathScaleOperationDTO(from: entry)
            selectedWeight = convertStoredWeightToDisplay(Int(entry.weight))
        } else {
            selectedEntry = nil
            selectedWeight = nil
        }
        // Force UI update to refresh weightLabel
        objectWillChange.send()
    }
    
    func handleChartTap(at location: CGPoint, proxy: ChartProxy) {
        if let (entry, _) = getSelectedEntry(at: location, proxy: proxy) {
            selectEntry(entry)
        } else {
            selectEntry(nil)
        }
    }
    
    // MARK: - Y-Axis Calculation using YAxisCalculator
    
    /// Get Y-axis scale based on all data points in current time period (not just visible)
    func getCurrentYAxisScale() -> YAxisScale {
        // Use all operations in the current time period, not just visible ones
        let allOps = continuousOperations
        
        let allWeightValues = allOps.map { summary -> Double in
            if isWeightlessModeEnabled {
                guard let anchorWeight = weightlessAnchorWeight else { return 0 }
                let currentWeight = convertStoredWeightToDisplay(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return convertStoredWeightToDisplay(Int(summary.weight))
            }
        }
        
        // Filter out outliers for better Y-axis calculation
        let filteredValues = filterOutliers(allWeightValues)
        
        // Use existing YAxisCalculator with chart height for optimal tick spacing
        let scale = YAxisCalculator.calculateYAxis(
            weights: filteredValues,
            goalWeight: goalWeightForDisplay,
            chartHeight: chartHeight,
            minTickSpacing: 40
        )
        
        // Log all data points for debugging
        print("Hello: getCurrentYAxisScale - All operations count: \(allOps.count)")
        print("Hello: getCurrentYAxisScale - All weight values: \(allWeightValues)")
        print("Hello: getCurrentYAxisScale - Filtered values: \(filteredValues)")
        print("Hello: getCurrentYAxisScale - Goal weight: \(goalWeightForDisplay)")
        print("Hello: getCurrentYAxisScale - Chart height: \(chartHeight)")
        print("Hello: getCurrentYAxisScale - Calculated scale: min=\(scale.min), max=\(scale.max), step=\(scale.step), labels=\(scale.labels), average=\(scale.average)")
        print("Hello: getCurrentYAxisScale - Domain: \(scale.domain)")
        
        return scale
    }
    
    /// Filter out outliers using IQR method
    private func filterOutliers(_ values: [Double]) -> [Double] {
        guard values.count > 1 else { return values }
        
        // For small datasets, use a more aggressive approach
        if values.count <= 4 {
            let sorted = values.sorted()
            let median = sorted[sorted.count / 2]
            
            // Calculate the range of the data
            let dataRange = sorted.last! - sorted.first!
            
            // For small datasets, filter out values that are too far from the median
            // Use a tighter bound: median ± (range * 0.3)
            let tolerance = dataRange * 0.3
            let lowerBound = median - tolerance
            let upperBound = median + tolerance
            
            let filtered = values.filter { $0 >= lowerBound && $0 <= upperBound }
            print("Hello: filterOutliers - Small dataset filtering: values=\(values), median=\(median), range=\(dataRange), tolerance=\(tolerance), bounds=[\(lowerBound), \(upperBound)], filtered=\(filtered)")
            return filtered
        }
        
        // For larger datasets, use IQR method
        let sorted = values.sorted()
        let q1Index = values.count / 4
        let q3Index = (values.count * 3) / 4
        
        let q1 = sorted[q1Index]
        let q3 = sorted[q3Index]
        let iqr = q3 - q1
        
        let lowerBound = q1 - (iqr * 1.5)
        let upperBound = q3 + (iqr * 1.5)
        
        let filtered = values.filter { $0 >= lowerBound && $0 <= upperBound }
        print("Hello: filterOutliers - IQR filtering: values=\(values), q1=\(q1), q3=\(q3), iqr=\(iqr), bounds=[\(lowerBound), \(upperBound)], filtered=\(filtered)")
        return filtered
    }
    
    /// Get current Y-axis ticks based on all data points in current time period
    func getCurrentYAxisTicks() -> [Double] {
        let scale = getCurrentYAxisScale()
        let ticks = scale.labels.map { Double($0) }
        print("Hello: DashboardStore - getCurrentYAxisTicks - Using ALL operations data")
        print("Hello: DashboardStore - getCurrentYAxisTicks - Chart height: \(chartHeight)")
        print("Hello: DashboardStore - getCurrentYAxisTicks - Optimal tick count: \(scale.labels.count)")
        print("Hello: DashboardStore - getCurrentYAxisTicks - Final ticks: \(ticks)")
        return ticks
    }
    
    /// Get current Y-axis scale domain based on all data points in current time period
    func getCurrentYScaleDomain() -> ClosedRange<Double> {
        let scale = getCurrentYAxisScale()
        let domain = scale.domain
        
        // Safety check: ensure domain includes all data points and goal weight
        let allOps = continuousOperations
        let allWeightValues = allOps.map { summary -> Double in
            if isWeightlessModeEnabled {
                guard let anchorWeight = weightlessAnchorWeight else { return 0 }
                let currentWeight = convertStoredWeightToDisplay(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return convertStoredWeightToDisplay(Int(summary.weight))
            }
        }
        
        if let minWeight = allWeightValues.min(),
           let maxWeight = allWeightValues.max() {
            // Calculate optimal domain that makes full use of chart height
            let dataRange = maxWeight - minWeight
            let goalWeight = goalWeightForDisplay
            
            // Include goal weight in the range calculation
            let effectiveMin = min(minWeight, goalWeight)
            let effectiveMax = max(maxWeight, goalWeight)
            let effectiveRange = effectiveMax - effectiveMin
            
            // Add padding to ensure all data points are visible and make full use of chart height
            let padding = max(effectiveRange * 0.15, 8.0) // Increased padding for better spacing
            let adjustedMin = effectiveMin - padding
            let adjustedMax = effectiveMax + padding
            
            print("Hello: DashboardStore - getCurrentYScaleDomain - Original domain: \(domain)")
            print("Hello: DashboardStore - getCurrentYScaleDomain - All weights: \(allWeightValues)")
            print("Hello: DashboardStore - getCurrentYScaleDomain - Goal weight: \(goalWeightForDisplay)")
            print("Hello: DashboardStore - getCurrentYScaleDomain - Data range: \(dataRange), Effective range: \(effectiveRange)")
            print("Hello: DashboardStore - getCurrentYScaleDomain - Optimal domain: \(adjustedMin)...\(adjustedMax)")
            
            return adjustedMin...adjustedMax
        }
        
        print("Hello: DashboardStore - getCurrentYScaleDomain - Using all operations domain: \(domain)")
        return domain
    }
    
    /// Get average weight for display based on all data points in current time period
    func getCurrentAverageWeight() -> Double {
        let average = getCurrentYAxisScale().average
        print("Hello: DashboardStore - getCurrentAverageWeight - Using ALL operations average: \(average)")
        return average
    }
    
    /// Get metric value for a summary based on selected metric
    func getMetricValue(for summary: BathScaleWeightSummary) -> Double? {
        guard let selectedLabel = selectedMetricLabel else { return nil }
        
        switch selectedLabel {
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
            return summary.bmr
        case DashboardStrings.metAge:
            return summary.metabolicAge.map { Double($0) }
        default:
            return nil
        }
    }
    
    
    /// Get selected point's metric values for metric info sheet
    func getSelectedPointMetricValues() -> [String: Double] {
        guard let selectedPoint = selectedPoint else { return [:] }
        
        var metricValues: [String: Double] = [:]
        
        // Add all available metrics from the selected point
        if let bmi = selectedPoint.bmi { metricValues[DashboardStrings.bmi] = bmi }
        if let bodyFat = selectedPoint.bodyFat { metricValues[DashboardStrings.bodyFat] = bodyFat }
        if let muscle = selectedPoint.muscleMass { metricValues[DashboardStrings.muscle] = muscle }
        if let water = selectedPoint.water { metricValues[DashboardStrings.water] = water }
        if let heartBpm = selectedPoint.pulse { metricValues[DashboardStrings.heartBpm] = Double(heartBpm) }
        if let bone = selectedPoint.boneMass { metricValues[DashboardStrings.bone] = bone }
        if let visceralFat = selectedPoint.visceralFatLevel { metricValues[DashboardStrings.visceralFat] = visceralFat }
        if let subFat = selectedPoint.subcutaneousFatPercent { metricValues[DashboardStrings.subFat] = subFat }
        if let protein = selectedPoint.proteinPercent { metricValues[DashboardStrings.protein] = protein }
        if let skelMuscle = selectedPoint.skeletalMusclePercent { metricValues[DashboardStrings.skelMuscle] = skelMuscle }
        if let bmr = selectedPoint.bmr { metricValues[DashboardStrings.bmrKcal] = bmr }
        if let metAge = selectedPoint.metabolicAge { metricValues[DashboardStrings.metAge] = Double(metAge) }
        
        return metricValues
    }
    
    /// Returns true if we should show all x-axis labels (monthly) for .year or .total
    /// Show all labels by default when entries are present, regardless of count
    /// Updated to show all x-axis labels by default when entries are present
    /// Note: This method is now primarily used for documentation as the logic has been simplified
    private func shouldShowAllXAxisLabels(_ summaries: [BathScaleWeightSummary]) -> Bool {
        // If there are any entries present, show all labels by default
        if !summaries.isEmpty {
            return true
        }
        // Fallback to original logic for edge cases
        return summaries.count < 10 || doEntriesSpanLessThanYear(summaries)
    }

    /// Generate x-axis values for the chart
    /// Always shows the complete time period regardless of data availability
    /// This ensures all x-axis labels are displayed even with minimal data
    func xAxisValues(for period: TimePeriod) -> [Date] {
        let allDates = continuousOperations.map(\.date)
        guard let minDate = allDates.min(), let maxDate = allDates.max() else { return [] }
        
        var dates: [Date] = []
        
        switch period {
        case .week:
            // Show full week (7 days) regardless of data range
            // Use a simpler approach to ensure labels are shown
            var current = calendar.startOfDay(for: minDate)
            let end = calendar.date(byAdding: .day, value: 6, to: current) ?? current
            while current <= end {
                dates.append(current)
                current = calendar.date(byAdding: .day, value: 1, to: current) ?? current
            }
            
        case .month:
            // Show full month (4-5 weeks) regardless of data range
            // Use a simpler approach to ensure labels are shown
            var current = calendar.startOfDay(for: minDate)
            let end = calendar.date(byAdding: .weekOfYear, value: 4, to: current) ?? current
            while current <= end {
                dates.append(current)
                current = calendar.date(byAdding: .weekOfYear, value: 1, to: current) ?? current
            }
            
        case .year:
            // Always show all 12 months regardless of data range
            let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
            var current = calendar.date(from: calendar.dateComponents([.year, .month], from: yearStart)) ?? yearStart
            let end = calendar.date(byAdding: .month, value: 11, to: current) ?? current
            while current <= end {
                dates.append(current)
                current = calendar.date(byAdding: .month, value: 1, to: current) ?? current
            }
            
        case .total:
            if areEntriesInSameEra(continuousOperations) {
                // For total time period with entries in same year, display like year time period
                // Always show all 12 months regardless of data range
                let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
                var current = calendar.date(from: calendar.dateComponents([.year, .month], from: yearStart)) ?? yearStart
                let end = calendar.date(byAdding: .month, value: 11, to: current) ?? current
                while current <= end {
                    dates.append(current)
                    current = calendar.date(byAdding: .month, value: 1, to: current) ?? current
                }
            } else {
                // If spanning multiple years, show quarterly labels
                var current = calendar.date(from: calendar.dateComponents([.year, .month], from: minDate)) ?? minDate
                let endComponents = calendar.dateComponents([.year, .month], from: maxDate)
                let end = calendar.date(from: endComponents) ?? maxDate
                while current <= end {
                    dates.append(current)
                    current = calendar.date(byAdding: .month, value: 3, to: current) ?? current
                }
            }
        }
        
        return dates
    }

    func xAxisValuesWithBuffer(for period: TimePeriod) -> [Date] {
        let allDates = continuousOperations.map(\.date)
        guard let minDate = allDates.min(), let maxDate = allDates.max() else { return [] }
        
        var dates: [Date] = []
        
        switch period {
        case .week:
            // Show full week with buffer
            let bufferDays: TimeInterval = 1 * 24 * 60 * 60 // 1 day buffer
            let startDate = calendar.startOfDay(for: minDate.addingTimeInterval(-bufferDays))
            let endDate = calendar.startOfDay(for: maxDate.addingTimeInterval(bufferDays))
            
            var current = startDate
            while current <= endDate {
                dates.append(current)
                current = calendar.date(byAdding: .day, value: 1, to: current) ?? current
            }
            
        case .month:
            // Show full month with buffer
            let bufferWeeks: TimeInterval = 7 * 24 * 60 * 60 // 1 week buffer
            let startDate = calendar.startOfDay(for: minDate.addingTimeInterval(-bufferWeeks))
            let endDate = calendar.startOfDay(for: maxDate.addingTimeInterval(bufferWeeks))
            
            var current = startDate
            while current <= endDate {
                dates.append(current)
                current = calendar.date(byAdding: .weekOfYear, value: 1, to: current) ?? current
            }
            
        case .year:
            // Always show all 12 months with buffer
            let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
            let startDate = calendar.date(byAdding: .month, value: -1, to: yearStart) ?? yearStart
            let endDate = calendar.date(byAdding: .month, value: 12, to: yearStart) ?? yearStart
            
            var current = startDate
            while current <= endDate {
                dates.append(current)
                current = calendar.date(byAdding: .month, value: 1, to: current) ?? current
            }
            
        case .total:
            if areEntriesInSameEra(continuousOperations) {
                // For total time period with entries in same year, display like year time period
                // Always show all 12 months with buffer
                let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
                let startDate = calendar.date(byAdding: .month, value: -1, to: yearStart) ?? yearStart
                let endDate = calendar.date(byAdding: .month, value: 12, to: yearStart) ?? yearStart
                
                var current = startDate
                while current <= endDate {
                    dates.append(current)
                    current = calendar.date(byAdding: .month, value: 1, to: current) ?? current
                }
            } else {
                let bufferQuarters: TimeInterval = 90 * 24 * 60 * 60 // 3 months buffer
                let startDate = calendar.date(from: calendar.dateComponents([.year, .month], from: minDate.addingTimeInterval(-bufferQuarters))) ?? minDate
                let endDate = calendar.date(from: calendar.dateComponents([.year, .month], from: maxDate.addingTimeInterval(bufferQuarters))) ?? maxDate
                
                var current = startDate
                while current <= endDate {
                    dates.append(current)
                    current = calendar.date(byAdding: .month, value: 3, to: current) ?? current
                }
            }
        }
        
        return dates
    }

    func xLabelString(for date: Date, period: TimePeriod) -> String? {
        switch period {
        case .week:
            return WeekDay.abbreviation(for: calendar.component(.weekday, from: date))
        case .month:
            return "\(calendar.component(.day, from: date))"
        case .year:
            // Always show month initials for year time period
            return Month.initial(for: calendar.component(.month, from: date))
        case .total:
            if areEntriesInSameEra(continuousOperations) {
                // For total time period with entries in same year, display like year time period
                // Always show month initials (J, F, M, etc.) when entries are in same era
                return Month.initial(for: calendar.component(.month, from: date))
            } else {
                return "\(calendar.component(.year, from: date))"
            }
        }
    }
    
    func getSelectedEntry(at location: CGPoint, proxy: ChartProxy) -> (entry: BathScaleWeightSummary, pointY: CGFloat)? {
        guard let date: Date = proxy.value(atX: location.x) else { return nil }
        guard let nearest = continuousOperations
            .map({ summary -> (BathScaleWeightSummary, Date) in
                (summary, summary.date)
            })
                .min(by: { abs($0.1.timeIntervalSince(date)) < abs($1.1.timeIntervalSince(date)) })?.0,
              let y = proxy.position(forY: convertStoredWeightToDisplay(Int(nearest.weight))) else { return nil }
        return (nearest, y)
    }
    
    func getFirstDateInAllOps() -> Date? {
        continuousOperations.map(\.date).min()
    }
    
    func getLastDateInAllOps() -> Date? {
        continuousOperations.map(\.date).max()
    }
    
    // MARK: - Scrollable Chart Helpers
    func visibleDomainLength(for period: TimePeriod) -> TimeInterval {
        switch period {
        case .week: return 7 * 24 * 60 * 60
        case .month: return 30 * 24 * 60 * 60
        case .year:
            // Check if entries span less than a year
            if doEntriesSpanLessThanYear(continuousOperations) {
                // Show a smaller domain for better detail when data spans less than a year
                return 180 * 24 * 60 * 60 // 6 months
            } else {
                return 365 * 24 * 60 * 60 // 1 year
            }
        case .total: 
            // Check if entries are in the same era (same year)
            if areEntriesInSameEra(continuousOperations) {
                // If in same era, treat like year view
                if doEntriesSpanLessThanYear(continuousOperations) {
                    // Show a smaller domain for better detail when data spans less than a year
                    return 180 * 24 * 60 * 60 // 6 months
                } else {
                    return 365 * 24 * 60 * 60 // 1 year
                }
            } else {
                // If spanning multiple years, calculate based on total range
                let allDates = continuousOperations.map(\.date)
                guard let minDate = allDates.min(), let maxDate = allDates.max() else {
                    return 365 * 24 * 60 * 60
                }
                let totalRange = maxDate.timeIntervalSince(minDate)
                return max(totalRange / 4, 365 * 24 * 60 * 60)
            }
        }
    }
    
    func timeSnapUnit(for period: TimePeriod) -> TimeInterval {
        switch period {
        case .week: return 24 * 60 * 60
        case .month: return 7 * 24 * 60 * 60
        case .year:
            // Check if entries span less than a year
            if doEntriesSpanLessThanYear(continuousOperations) {
                // Use monthly snap units for better precision
                return 30 * 24 * 60 * 60
            } else {
                // Use quarterly snap units for better performance
                return 90 * 24 * 60 * 60
            }
        case .total:
            // Check if entries are in the same era (same year)
            if areEntriesInSameEra(continuousOperations) {
                // If in same era, treat like year view
                if doEntriesSpanLessThanYear(continuousOperations) {
                    // Use monthly snap units for better precision
                    return 30 * 24 * 60 * 60
                } else {
                    // Use quarterly snap units for better performance
                    return 90 * 24 * 60 * 60
                }
            } else {
                // If spanning multiple years, use quarterly snap units
                return 90 * 24 * 60 * 60
            }
        }
    }
    
    private func getVisibleOperations() -> [BathScaleWeightSummary] {
        let visibleStart = xScrollPosition.addingTimeInterval(-visibleDomainLength(for: selectedPeriod) / 2)
        let visibleEnd = xScrollPosition.addingTimeInterval(visibleDomainLength(for: selectedPeriod) / 2)
        
        let visibleOps = continuousOperations.filter { summary in
            return summary.date >= visibleStart && summary.date <= visibleEnd
        }
        
        print("Hello: getVisibleOperations - Scroll position: \(xScrollPosition)")
        print("Hello: getVisibleOperations - Visible range: \(visibleStart) to \(visibleEnd)")
        print("Hello: getVisibleOperations - Total operations: \(continuousOperations.count)")
        print("Hello: getVisibleOperations - Visible operations: \(visibleOps.count)")
        print("Hello: getVisibleOperations - Visible operation dates: \(visibleOps.map { $0.date })")
        print("Hello: getVisibleOperations - All operation dates: \(continuousOperations.map { $0.date })")
        print("Hello: getVisibleOperations - Latest operation date: \(continuousOperations.map { $0.date }.max() ?? Date())")
        
        return visibleOps
    }
    
    var visibleOperations: [BathScaleWeightSummary] {
        let visibleStart = xScrollPosition.addingTimeInterval(-visibleDomainLength(for: selectedPeriod) / 2)
        let visibleEnd = xScrollPosition.addingTimeInterval(visibleDomainLength(for: selectedPeriod) / 2)
        
        return continuousOperations.filter { summary in
            return summary.date >= visibleStart && summary.date <= visibleEnd
        }
    }
    

    
    /// Get the label text for weight display
    /// Shows "Selected" when a point is selected, otherwise shows period average
    var weightDisplayLabel: String {
        return "\(selectedPeriod.rawValue) average"
    }
    
    /// Check if there are any entries in the system (across all time periods)
    var hasAnyEntries: Bool {
        return !dailySummaries.isEmpty || !monthlySummaries.isEmpty
    }
    
    /// Check if there are entries but none in the current time period
    var hasEntriesButNoneInCurrentPeriod: Bool {
        return hasAnyEntries && continuousOperations.isEmpty
    }
    
    /// Reset metric values to latest entry data
    func resetMetricsToLatestEntry() {
        Task {
            do {
                guard let latestEntry = try await entryService.getLatestEntry() else {
                    return
                }
                updateMetricsWithEntry(latestEntry)
            } catch {
                logger.log(level: .error, tag: "DashboardStore", message: "Failed to reset metrics to latest entry: \(error)")
            }
        }
    }

    /// Update visible data after scroll ends
    /// This recalculates Y-axis domain and ticks based on all data points in current time period
    @MainActor
    func updateVisibleDataAfterScroll() {
        // Force UI update to recalculate Y-axis based on all data points
        objectWillChange.send()
        
        // Update weight display to show average of all data points in current time period
        let averageWeight = getCurrentAverageWeight()
        print("Hello: updateVisibleDataAfterScroll - Average weight in current time period: \(averageWeight)")
        
        print("Hello: updateVisibleDataAfterScroll - Updated Y-axis domain and ticks based on all data points")
    }
    
    private func handleScrollEnd() {
        // Cancel any existing timer
        scrollEndTimer?.invalidate()
        
        // Set a timer to detect when scrolling has truly ended
        scrollEndTimer = Timer.scheduledTimer(withTimeInterval: 0.3, repeats: false) { [weak self] _ in
            Task { @MainActor in
                guard let self = self else { return }
                self.isScrolling = false
                self.showCrosshair = false
                self.selectedXValue = nil  // Clear selection state
                // Reset metrics to latest entry when scrolling ends
                self.resetMetricsToLatestEntry()
                // Update visible data after scroll ends
                self.updateVisibleDataAfterScroll()
                os_log("Scroll ended", log: self.perfLog, type: .info)
            }
        }
    }
}

/// Dashboard store with incremental updates for daily and monthly summaries.

extension DashboardStore {
    
    /// Initial load: aggreconvenience gates all entries into daily and monthly summaries
    func loadInitialData() async {
        guard let accountId = accountService.activeAccount?.accountId else { return }
        isLoading = true
        defer { isLoading = false }
        
        do {
            let entries = try await entryService.getAllEntries()
            let daily = entryService.aggregateByDay(entries: entries, accountId: accountId)
            let monthly = entryService.aggregateByMonth(entries: entries, accountId: accountId)
            dailyCache = Dictionary(
                uniqueKeysWithValues: daily.compactMap { summary in
                    guard let summary = summary else { return nil }
                    return (summary.period, summary)
                }
            )
            monthlyCache = Dictionary(
                uniqueKeysWithValues: monthly.compactMap { summary in
                    guard let summary = summary else { return nil }
                    return (summary.period, summary)
                }
            )
            updatePublishedArrays()
        } catch {
            // Handle error (log, show alert, etc.)
            print("Error loading initial dashboard data: \(error)")
        }
    }
    
    /// Call this when a new entry is added
    func onEntryAdded(_ entry: Entry) async {
        guard let accountId = accountService.activeAccount?.accountId else { return }
        let dayKey = DateTimeTools.getDateStringFromDate(entry.entryTimestamp)
        let monthKey = DateTimeTools.getMonthStringFromDate(entry.entryTimestamp)
        
        // Fetch all entries for the affected day/month only
        let dayEntries = await fetchEntriesForPeriod(dayKey, .day)
        let monthEntries = await fetchEntriesForPeriod(monthKey, .month)
        
        if let daySummary = entryService.aggregateByDay(entries: dayEntries, accountId: accountId).first {
            dailyCache[dayKey] = daySummary
        }
        if let monthSummary = entryService.aggregateByMonth(entries: monthEntries, accountId: accountId).first {
            monthlyCache[monthKey] = monthSummary
        }
        updatePublishedArrays()
        
        // Trigger UI updates for unit and weightless mode changes
        await MainActor.run {
            handleEntryChange()
        }
    }
    
    /// Call this when an entry is deleted
    func onEntryDeleted(_ entry: Entry) async {
        guard let accountId = accountService.activeAccount?.accountId else { return }
        let dayKey = DateTimeTools.getDateStringFromDate(entry.entryTimestamp)
        let monthKey = DateTimeTools.getMonthStringFromDate(entry.entryTimestamp)
        
        // Fetch all entries for the affected day/month only
        let dayEntries = await fetchEntriesForPeriod(dayKey, .day)
        let monthEntries = await fetchEntriesForPeriod(monthKey, .month)
        
        if let daySummary = entryService.aggregateByDay(entries: dayEntries, accountId: accountId).first {
            dailyCache[dayKey] = daySummary
        } else {
            dailyCache.removeValue(forKey: dayKey)
        }
        if let monthSummary = entryService.aggregateByMonth(entries: monthEntries, accountId: accountId).first {
            monthlyCache[monthKey] = monthSummary
        } else {
            monthlyCache.removeValue(forKey: monthKey)
        }
        updatePublishedArrays()
        
        // Trigger UI updates for unit and weightless mode changes
        await MainActor.run {
            handleEntryChange()
        }
    }
    
    /// Call this when an entry is updated
    func onEntryUpdated(_ entry: Entry) async {
        // For simplicity, treat as delete+add
        await onEntryDeleted(entry)
        await onEntryAdded(entry)
    }
    
    /// Call this when unit settings change
    @MainActor
    func onUnitSettingsChanged() {
        handleUnitChange()
    }
    
    /// Call this when weightless mode settings change
    @MainActor
    func onWeightlessModeSettingsChanged() {
        handleWeightlessModeChange()
    }
    
    // MARK: - Helpers
    private func updatePublishedArrays() {
        dailySummaries = Array(dailyCache.values).sorted { $0.period < $1.period }
        monthlySummaries = Array(monthlyCache.values).sorted { $0.period < $1.period }
        
        // Increment data change trigger to force graph refresh
        dataChangeTrigger += 1
        
        // Always initialize graph scroll position to the latest date when data is first loaded
        // This ensures the chart shows the latest entry by default
        if let latestDate = continuousOperations.map(\.date).max() {
            updateScrollPositionDebounced(to: latestDate)
        }
    }
    
    private enum PeriodType { case day, month }
    
    private func fetchEntriesForPeriod(_ periodKey: String, _ type: PeriodType) async -> [Entry] {
        // This assumes you have a way to fetch entries for a specific day or month from your repository/service
        // You may need to implement these methods in your EntryService/Repository
        do {
            switch type {
            case .day:
                // periodKey: "yyyy-MM-dd"
                return try await entryService.getEntries(forDay: periodKey)
            case .month:
                // periodKey: "yyyy-MM"
                return try await entryService.getEntries(forMonth: periodKey)
            }
        } catch {
            return []
        }
    }
    
}


