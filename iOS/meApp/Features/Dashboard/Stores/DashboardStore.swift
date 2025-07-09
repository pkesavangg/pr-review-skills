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

@MainActor
class DashboardStore: ObservableObject, EntryServiceDelegate {
    @Injector private var notificationService: NotificationHelperService
    @Injector var accountService: AccountService
    @Injector private var logger : LoggerService
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
            // For total view, use monthly summaries (average of each month from start to current)
            return monthlySummaries.compactMap { $0 }.sorted { $0.date < $1.date }
        }
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
        let visibleOps = getVisibleOperations()
        let opsToUse = visibleOps.isEmpty ? continuousOperations : visibleOps
        
        // Check if weightless mode is enabled
        if isWeightlessModeEnabled {
            return calculateWeightlessDisplay(opsToUse)
        }
        
        switch selectedPeriod {
        case .week:
            // For week view, show the latest day's weight
            return opsToUse.last.map { convertStoredWeightToDisplay(Int($0.weight)) }
        case .month:
            // For month view, show the latest day's weight
            return opsToUse.last.map { convertStoredWeightToDisplay(Int($0.weight)) }
        case .year:
            // For year view, show the average of the visible monthly data
            let weights = opsToUse.map { convertStoredWeightToDisplay(Int($0.weight)) }
            guard !weights.isEmpty else { return nil }
            return weights.reduce(0, +) / Double(weights.count)
        case .total:
            // For total view, show the average of all monthly data
            let weights = opsToUse.map { convertStoredWeightToDisplay(Int($0.weight)) }
            guard !weights.isEmpty else { return nil }
            return weights.reduce(0, +) / Double(weights.count)
        }
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
        
        let visibleOps = getVisibleOperations()
        let opsToUse = visibleOps.isEmpty ? operations : visibleOps
        
        switch selectedPeriod {
        case .week:
            // For week view, show the difference from the latest day's weight
            guard let latestWeight = opsToUse.last.map({ convertStoredWeightToDisplay(Int($0.weight)) }) else {
                return nil
            }
            return latestWeight - anchorWeight
        case .month:
            // For month view, show the difference from the latest day's weight
            guard let latestWeight = opsToUse.last.map({ convertStoredWeightToDisplay(Int($0.weight)) }) else {
                return nil
            }
            return latestWeight - anchorWeight
        case .year:
            // For year view, show the average difference from the visible monthly data
            let weights = opsToUse.map { convertStoredWeightToDisplay(Int($0.weight)) }
            guard !weights.isEmpty else { return nil }
            let averageWeight = weights.reduce(0, +) / Double(weights.count)
            return averageWeight - anchorWeight
        case .total:
            // For total view, show the average difference from all monthly data
            let weights = opsToUse.map { convertStoredWeightToDisplay(Int($0.weight)) }
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
        objectWillChange.send()
    }
    
    /// Handle weightless mode change specifically
    @MainActor
    func handleWeightlessModeChange() {
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
        
    /// Handle scroll end - disables dynamic Y-axis updates after a delay
    @MainActor
    func handleScrollEnd() {
        scrollEndTimer?.invalidate()
        scrollEndTimer = Timer.scheduledTimer(withTimeInterval: 0.3, repeats: false) { [weak self] _ in
            DispatchQueue.main.async {
                self?.isScrolling = false
                self?.showCrosshair = false
                self?.selectedXValue = nil
                self?.selectedPoint = nil
                self?.selectEntry(nil)
                self?.objectWillChange.send()
            }
        }
    }
    
    /// Handle scroll start - enables dynamic Y-axis updates
    @MainActor
    func handleScrollStart() {
        scrollEndTimer?.invalidate()
        
        if !isScrolling {
            isScrolling = true
            // Clear selection when scrolling starts
            showCrosshair = false
            selectedXValue = nil
            selectedPoint = nil
            selectEntry(nil)
            objectWillChange.send()
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
            return 
        }
        
        // Set the selected point and show crosshair
        self.selectedPoint = selectedPoint
        selectEntry(selectedPoint)
        
        // Force UI update
        DispatchQueue.main.async {
            self.showCrosshair = true
        }
    }
    
    /// Handle scroll gesture detection
    @MainActor
    func handleScrollGestureChanged(translation: CGSize) {
        // Only detect horizontal scrolling gestures
        let isHorizontalScroll = abs(translation.width) > abs(translation.height) * 1.5
        let isSignificantMovement = abs(translation.width) > 8
        
        if isHorizontalScroll && isSignificantMovement && !hasDetectedScrollInCurrentGesture {
            hasDetectedScrollInCurrentGesture = true
            handleScrollStart()
        }
    }
    
    /// Handle scroll gesture ended
    @MainActor
    func handleScrollGestureEnded(translation: CGSize) {
        // Reset for next gesture
        hasDetectedScrollInCurrentGesture = false
        
        // Only handle if it was a horizontal scroll
        let isHorizontalScroll = abs(translation.width) > abs(translation.height) * 1.5
        let isSignificantMovement = abs(translation.width) > 8
        
        if isHorizontalScroll && isSignificantMovement {
            handleScrollEnd()
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
        guard !continuousOperations.isEmpty else { return [] }
        
        var series: [GraphSeries] = []
        
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
        
        // Add selected metric series (if a metric is selected)
        if let selectedMetric = selectedMetricLabel, selectedMetric != DashboardStrings.weight {
            for summary in continuousOperations {
                if let metricValue = getMetricValue(for: summary) {
                    series.append(GraphSeries(
                        date: summary.date,
                        value: metricValue,
                        series: selectedMetric
                    ))
                }
            }
        }
        
        return series
    }
    
    func updateSelectedPeriod(_ period: TimePeriod) {
        selectedPeriod = period
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
    
    func yAxisTicksWithGoal() -> [Double] {
        var ticks = getYAxisTicks()
        if !ticks.contains(goalWeight) { ticks.append(goalWeight) }
        return ticks.sorted()
    }
    
    /// Calculate Y-axis range with proper padding and scaling
    func calculateYAxisRange() -> ClosedRange<Double> {
        let weightValues = continuousOperations.map { summary -> Double in
            if isWeightlessModeEnabled {
                guard let anchorWeight = weightlessAnchorWeight else { return 0 }
                let currentWeight = convertStoredWeightToDisplay(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return convertStoredWeightToDisplay(Int(summary.weight))
            }
        }
        let metricValues = selectedMetricLabel != nil ? continuousOperations.compactMap { getMetricValue(for: $0) } : []
        
        let allValues = weightValues + metricValues
        guard !allValues.isEmpty else { 
            if isWeightlessModeEnabled {
                return -10.0...10.0
            } else {
                let defaultRange = accountService.activeAccount?.weightSettings?.weightUnit == .kg ? 70.0...90.0 : 170.0...190.0
                return defaultRange
            }
        }
        
        let minValue = allValues.min() ?? (isWeightlessModeEnabled ? -10 : (accountService.activeAccount?.weightSettings?.weightUnit == .kg ? 70 : 170))
        let maxValue = allValues.max() ?? (isWeightlessModeEnabled ? 10 : (accountService.activeAccount?.weightSettings?.weightUnit == .kg ? 90 : 190))
        
        let dataRange = maxValue - minValue
        let minRange = isWeightlessModeEnabled ? 2.0 : (accountService.activeAccount?.weightSettings?.weightUnit == .kg ? 2.0 : 5.0)
        let effectiveRange = max(dataRange, minRange)
        let padding = effectiveRange * 0.1
        
        return (minValue - padding)...(maxValue + padding)
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
    
    /// Get Y-axis ticks with proper spacing for the current data range
    func getYAxisTicks() -> [Double] {
        let range = calculateYAxisRange()
        let minValue = range.lowerBound
        let maxValue = range.upperBound
        let dataRange = maxValue - minValue
        
        let optimalTickCount = 6
        let roughStep = dataRange / Double(optimalTickCount - 1)
        let normalizedStep = normalizeStep(roughStep)
        
        var ticks: [Double] = []
        var currentTick = minValue
        
        while currentTick <= maxValue + 0.001 {
            ticks.append(currentTick)
            currentTick += normalizedStep
        }
        
        if goalWeight > 0 {
            let goalValue = goalWeightForDisplay
            if goalValue >= minValue && goalValue <= maxValue {
                if !ticks.contains(where: { abs($0 - goalValue) < normalizedStep * 0.1 }) {
                    ticks.append(goalValue)
                }
            }
        }
        
        return ticks.sorted()
    }
    
    /// Get current Y-axis ticks (alias for getYAxisTicks)
    func getCurrentYAxisTicks() -> [Double] {
        return getYAxisTicks()
    }
    
    /// Get current Y-axis scale domain
    func getCurrentYScaleDomain() -> ClosedRange<Double> {
        return getYScaleDomain()
    }
    
    /// Normalize step size to human-friendly increments
    private func normalizeStep(_ roughStep: Double) -> Double {
        let magnitude = pow(10, Foundation.floor(log10(roughStep)))
        let normalized = roughStep / magnitude
        
        let cleanSteps: [Double] = [0.1, 0.2, 0.5, 1.0, 2.0, 2.5, 5.0, 10.0]
        let closestStep = cleanSteps.min(by: { 
            abs($0 - normalized) < abs($1 - normalized)
        }) ?? 1.0
        
        return closestStep * magnitude
    }
    
    /// Calculate Y-axis scale domain with proper padding
    func getYScaleDomain() -> ClosedRange<Double> {
        let range = calculateYAxisRange()
        let minValue = range.lowerBound
        let maxValue = range.upperBound
        let dataRange = maxValue - minValue
        let padding = dataRange * 0.05
        
        return (minValue - padding)...(maxValue + padding)
    }
    
    func xAxisValues(for period: TimePeriod) -> [Date] {
        let allDates = continuousOperations.map(\.date)
        guard let minDate = allDates.min(), let maxDate = allDates.max() else { return [] }
        
        var dates: [Date] = []
        
        switch period {
        case .week:
            var current = calendar.startOfDay(for: minDate)
            let end = calendar.startOfDay(for: maxDate)
            while current <= end {
                dates.append(current)
                current = calendar.date(byAdding: .day, value: 1, to: current) ?? current
            }
            
        case .month:
            var current = calendar.startOfDay(for: minDate)
            let end = calendar.startOfDay(for: maxDate)
            while current <= end {
                dates.append(current)
                current = calendar.date(byAdding: .weekOfYear, value: 1, to: current) ?? current
            }
            
        case .year:
            var current = calendar.date(from: calendar.dateComponents([.year, .month], from: minDate)) ?? minDate
            let endComponents = calendar.dateComponents([.year, .month], from: maxDate)
            let end = calendar.date(from: endComponents) ?? maxDate
            while current <= end {
                dates.append(current)
                current = calendar.date(byAdding: .month, value: 1, to: current) ?? current
            }
            
        case .total:
            var current = calendar.date(from: calendar.dateComponents([.year, .month], from: minDate)) ?? minDate
            let endComponents = calendar.dateComponents([.year, .month], from: maxDate)
            let end = calendar.date(from: endComponents) ?? maxDate
            while current <= end {
                dates.append(current)
                current = calendar.date(byAdding: .month, value: 3, to: current) ?? current
            }
        }
        
        return dates
    }
    

    
    func xAxisLabels(for period: TimePeriod) -> [Date] {
        return xAxisValues(for: period)
    }
    
    func xLabelString(for date: Date, period: TimePeriod) -> String? {
        switch period {
        case .week:
            return WeekDay.abbreviation(for: calendar.component(.weekday, from: date))
        case .month:
            return "\(calendar.component(.day, from: date))"
        case .year:
            return Month.initial(for: calendar.component(.month, from: date))
        case .total:
            return "\(calendar.component(.year, from: date))"
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
        case .year: return 365 * 24 * 60 * 60
        case .total: 
            let allDates = continuousOperations.map(\.date)
            guard let minDate = allDates.min(), let maxDate = allDates.max() else {
                return 365 * 24 * 60 * 60
            }
            let totalRange = maxDate.timeIntervalSince(minDate)
            return max(totalRange / 4, 365 * 24 * 60 * 60)
        }
    }
    
    func timeSnapUnit(for period: TimePeriod) -> TimeInterval {
        switch period {
        case .week: return 24 * 60 * 60
        case .month: return 7 * 24 * 60 * 60
        case .year: return 30 * 24 * 60 * 60
        case .total: return 90 * 24 * 60 * 60
        }
    }
    
    private func getVisibleOperations() -> [BathScaleWeightSummary] {
        let visibleStart = xScrollPosition.addingTimeInterval(-visibleDomainLength(for: selectedPeriod) / 2)
        let visibleEnd = xScrollPosition.addingTimeInterval(visibleDomainLength(for: selectedPeriod) / 2)
        
        return continuousOperations.filter { summary in
            return summary.date >= visibleStart && summary.date <= visibleEnd
        }
    }
    
    var visibleOperations: [BathScaleWeightSummary] {
        let visibleStart = xScrollPosition.addingTimeInterval(-visibleDomainLength(for: selectedPeriod) / 2)
        let visibleEnd = xScrollPosition.addingTimeInterval(visibleDomainLength(for: selectedPeriod) / 2)
        
        return continuousOperations.filter { summary in
            return summary.date >= visibleStart && summary.date <= visibleEnd
        }
    }
    
    /// Calculate Y-axis range based on ALL data (not just visible data)
    /// This provides stable Y-axis during scrolling for better performance
    func calculateYAxisRangeForAllData() -> ClosedRange<Double> {
        let weightValues = continuousOperations.map { summary -> Double in
            if isWeightlessModeEnabled {
                // In weightless mode, calculate differences from anchor weight
                guard let anchorWeight = weightlessAnchorWeight else { return 0 }
                let currentWeight = convertStoredWeightToDisplay(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                // Normal mode - use actual weights
                return convertStoredWeightToDisplay(Int(summary.weight))
            }
        }
        let metricValues = selectedMetricLabel != nil ? continuousOperations.compactMap { getMetricValue(for: $0) } : []
        
        let allValues = weightValues + metricValues
        guard !allValues.isEmpty else { 
            if isWeightlessModeEnabled {
                // For weightless mode, use a reasonable range for differences
                return -10.0...10.0
            } else {
                let defaultRange = accountService.activeAccount?.weightSettings?.weightUnit == .kg ? 70.0...90.0 : 170.0...190.0
                return defaultRange
            }
        }
        
        let minValue = allValues.min() ?? (isWeightlessModeEnabled ? -10 : (accountService.activeAccount?.weightSettings?.weightUnit == .kg ? 70 : 170))
        let maxValue = allValues.max() ?? (isWeightlessModeEnabled ? 10 : (accountService.activeAccount?.weightSettings?.weightUnit == .kg ? 90 : 190))
        
        // Calculate the actual data range
        let dataRange = maxValue - minValue
        
        // For very small ranges, ensure we have enough visual space
        let minRange = isWeightlessModeEnabled ? 2.0 : (accountService.activeAccount?.weightSettings?.weightUnit == .kg ? 2.0 : 5.0)
        let effectiveRange = max(dataRange, minRange)
        
        // Add padding to prevent clipping (15% on each side for better visibility)
        let padding = effectiveRange * 0.15
        
        return (minValue - padding)...(maxValue + padding)
    }
    
    /// Get Y-axis ticks based on ALL data with buffer labels
    /// This provides stable Y-axis during scrolling and prevents edge clipping
    func getYAxisTicksForAllData() -> [Double] {
        let range = calculateYAxisRangeForAllData()
        let minValue = range.lowerBound
        let maxValue = range.upperBound
        let dataRange = maxValue - minValue
        
        // Calculate optimal number of ticks (5-7 ticks for readability)
        let optimalTickCount = 6
        let roughStep = dataRange / Double(optimalTickCount - 1)
        
        // Normalize step to human-friendly increments
        let normalizedStep = normalizeStep(roughStep)
        
        // Generate ticks from min to max
        var ticks: [Double] = []
        var currentTick = minValue
        
        while currentTick <= maxValue + 0.001 { // Small epsilon for floating point comparison
            ticks.append(currentTick)
            currentTick += normalizedStep
        }
        
        // Add buffer Y-axis labels to prevent edge clipping
        // Always add one more tick above the highest data point to prevent clipping
        let allWeightValues = continuousOperations.map { summary -> Double in
            if isWeightlessModeEnabled {
                guard let anchorWeight = weightlessAnchorWeight else { return 0 }
                let currentWeight = convertStoredWeightToDisplay(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return convertStoredWeightToDisplay(Int(summary.weight))
            }
        }
        
        if let maxDataValue = allWeightValues.max() {
            let highestTick = ticks.max() ?? maxValue
            // Always add a buffer tick above the highest data point to prevent clipping
            // This ensures that if the max weight in visible portion is 83, the max Y-axis label will be 85 (if interval is 5)
            if maxDataValue <= highestTick {
                ticks.append(highestTick + normalizedStep)
            }
        }
        
        // Always include goal weight if it exists and is within range
        if goalWeight > 0 {
            let goalValue = goalWeightForDisplay
            if goalValue >= minValue && goalValue <= maxValue + normalizedStep {
                // Only add if not already close to an existing tick
                if !ticks.contains(where: { abs($0 - goalValue) < normalizedStep * 0.1 }) {
                    ticks.append(goalValue)
                }
            }
        }
        
        return ticks.sorted()
    }
    
    /// Calculate Y-axis scale domain based on ALL data
    /// This ensures stable scaling during scrolling
    func getYScaleDomainForAllData() -> ClosedRange<Double> {
        let range = calculateYAxisRangeForAllData()
        let minValue = range.lowerBound
        let maxValue = range.upperBound
        let dataRange = maxValue - minValue
        
        // Add minimal padding to prevent clipping (5%)
        let padding = dataRange * 0.05
        
        return (minValue - padding)...(maxValue + padding)
    }
    
    // MARK: - Dynamic Y-Axis Calculation for Visible Data
    
    /// Calculate Y-axis range based on visible data points for dynamic scaling
    /// This provides responsive Y-axis that adjusts to visible data during scrolling
    func calculateYAxisRangeForVisibleData() -> ClosedRange<Double> {
        let visibleWeightValues = chartSeriesData.compactMap { series -> Double? in
            // Only consider weight series for Y-axis calculation
            guard series.series == DashboardStrings.weight else { return nil }
            return series.value
        }
        
        let metricValues = selectedMetricLabel != nil ? chartSeriesData.compactMap { series -> Double? in
            guard series.series == selectedMetricLabel else { return nil }
            return series.value
        } : []
        
        let allVisibleValues = visibleWeightValues + metricValues
        guard !allVisibleValues.isEmpty else { 
            // Fallback to all data range if no visible data
            return calculateYAxisRangeForAllData()
        }
        
        let minValue = allVisibleValues.min() ?? 0
        let maxValue = allVisibleValues.max() ?? 1
        
        // Calculate the actual data range
        let dataRange = maxValue - minValue
        
        // For very small ranges, ensure we have enough visual space
        let minRange = isWeightlessModeEnabled ? 2.0 : (accountService.activeAccount?.weightSettings?.weightUnit == .kg ? 2.0 : 5.0)
        let effectiveRange = max(dataRange, minRange)
        
        // Add padding to prevent clipping (20% on each side for better visibility)
        let padding = effectiveRange * 0.20
        
        // Always include goal weight in the range if it exists
        var finalMin = minValue - padding
        var finalMax = maxValue + padding
        
        if goalWeight > 0 {
            let goalValue = goalWeightForDisplay
            finalMin = min(finalMin, goalValue - (effectiveRange * 0.1))
            finalMax = max(finalMax, goalValue + (effectiveRange * 0.1))
        }
        
        return finalMin...finalMax
    }
    
    /// Get Y-axis ticks based on visible data with goal weight always included
    /// This provides dynamic Y-axis that adjusts to visible data during scrolling
    func getYAxisTicksForVisibleData() -> [Double] {
        let range = calculateYAxisRangeForVisibleData()
        let minValue = range.lowerBound
        let maxValue = range.upperBound
        let dataRange = maxValue - minValue
        
        // Calculate optimal number of ticks (5-7 ticks for readability)
        let optimalTickCount = 6
        let roughStep = dataRange / Double(optimalTickCount - 1)
        
        // Normalize step to human-friendly increments
        let normalizedStep = normalizeStep(roughStep)
        
        // Generate ticks from min to max
        var ticks: [Double] = []
        var currentTick = minValue
        
        while currentTick <= maxValue + 0.001 { // Small epsilon for floating point comparison
            ticks.append(currentTick)
            currentTick += normalizedStep
        }
        
        // Always include goal weight if it exists and is within reasonable range
        if goalWeight > 0 {
            let goalValue = goalWeightForDisplay
            if goalValue >= minValue - normalizedStep && goalValue <= maxValue + normalizedStep {
                // Only add if not already close to an existing tick
                if !ticks.contains(where: { abs($0 - goalValue) < normalizedStep * 0.1 }) {
                    ticks.append(goalValue)
                }
            }
        }
        
        return ticks.sorted()
    }
    
    /// Calculate Y-axis scale domain based on visible data
    /// This provides dynamic scaling that adjusts during scrolling
    func getYScaleDomainForVisibleData() -> ClosedRange<Double> {
        let range = calculateYAxisRangeForVisibleData()
        let minValue = range.lowerBound
        let maxValue = range.upperBound
        let dataRange = maxValue - minValue
        
        // Add minimal padding to prevent clipping (5%)
        let padding = dataRange * 0.05
        
        return (minValue - padding)...(maxValue + padding)
    }
    
    /// Get X-axis values with buffer to prevent edge clipping
    /// This ensures points at the edges are fully visible
    func xAxisValuesWithBuffer(for period: TimePeriod) -> [Date] {
        let allDates = continuousOperations.map(\.date)
        guard let minDate = allDates.min(), let maxDate = allDates.max() else { return [] }
        
        var dates: [Date] = []
        
        switch period {
        case .week:
            // For week view, add buffer days before and after
            let bufferDays: TimeInterval = 1 * 24 * 60 * 60 // 1 day buffer
            let startDate = calendar.startOfDay(for: minDate.addingTimeInterval(-bufferDays))
            let endDate = calendar.startOfDay(for: maxDate.addingTimeInterval(bufferDays))
            
            var current = startDate
            while current <= endDate {
                dates.append(current)
                current = calendar.date(byAdding: .day, value: 1, to: current) ?? current
            }
            
        case .month:
            // For month view, add buffer weeks
            let bufferWeeks: TimeInterval = 7 * 24 * 60 * 60 // 1 week buffer
            let startDate = calendar.startOfDay(for: minDate.addingTimeInterval(-bufferWeeks))
            let endDate = calendar.startOfDay(for: maxDate.addingTimeInterval(bufferWeeks))
            
            var current = startDate
            while current <= endDate {
                dates.append(current)
                current = calendar.date(byAdding: .weekOfYear, value: 1, to: current) ?? current
            }
            
        case .year:
            // For year view, add buffer months
            let bufferMonths: TimeInterval = 30 * 24 * 60 * 60 // 1 month buffer
            let startDate = calendar.date(from: calendar.dateComponents([.year, .month], from: minDate.addingTimeInterval(-bufferMonths))) ?? minDate
            let endDate = calendar.date(from: calendar.dateComponents([.year, .month], from: maxDate.addingTimeInterval(bufferMonths))) ?? maxDate
            
            var current = startDate
            while current <= endDate {
                dates.append(current)
                current = calendar.date(byAdding: .month, value: 1, to: current) ?? current
            }
            
        case .total:
            // For total view, add buffer quarters
            let bufferQuarters: TimeInterval = 90 * 24 * 60 * 60 // 3 months buffer
            let startDate = calendar.date(from: calendar.dateComponents([.year, .month], from: minDate.addingTimeInterval(-bufferQuarters))) ?? minDate
            let endDate = calendar.date(from: calendar.dateComponents([.year, .month], from: maxDate.addingTimeInterval(bufferQuarters))) ?? maxDate
            
            var current = startDate
            while current <= endDate {
                dates.append(current)
                current = calendar.date(byAdding: .month, value: 3, to: current) ?? current
            }
        }
        
        return dates
    }
    
    /// Get the weight to display based on selection state
    /// When a point is selected, show that specific weight
    /// When no point is selected, show average of visible range
    var displayWeightForSelection: Double? {
        if let selectedEntry = selectedEntry {
            // If a point is selected, show that specific weight
            return convertStoredWeightToDisplay(Int(selectedEntry.weight ?? 0))
        } else {
            // If no point is selected, show average of visible range
            return calculateAverageWeightForVisibleRange()
        }
    }
    
    /// Calculate average weight for the currently visible range
    /// This is used when no specific point is selected
    private func calculateAverageWeightForVisibleRange() -> Double? {
        let visibleOps = getVisibleOperations()
        let opsToUse = visibleOps.isEmpty ? continuousOperations : visibleOps
        
        // Check if weightless mode is enabled
        if isWeightlessModeEnabled {
            return calculateWeightlessAverageForVisibleRange(opsToUse)
        }
        
        // Calculate average weight for visible operations
        let weights = opsToUse.map { convertStoredWeightToDisplay(Int($0.weight)) }
        guard !weights.isEmpty else { return nil }
        return weights.reduce(0, +) / Double(weights.count)
    }
    
    /// Calculate weightless average for visible range
    /// In weightless mode, shows average difference from anchor weight
    private func calculateWeightlessAverageForVisibleRange(_ operations: [BathScaleWeightSummary]) -> Double? {
        guard let anchorWeight = weightlessAnchorWeight else { return nil }
        
        let visibleOps = getVisibleOperations()
        let opsToUse = visibleOps.isEmpty ? operations : visibleOps
        
        // Calculate average weight for visible operations
        let weights = opsToUse.map { convertStoredWeightToDisplay(Int($0.weight)) }
        guard !weights.isEmpty else { return nil }
        let averageWeight = weights.reduce(0, +) / Double(weights.count)
        return averageWeight - anchorWeight
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
