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
import Foundation

// Import ScrollPhase for iOS 18+ compatibility
@available(iOS 18.0, *)
typealias ScrollPhase = SwiftUI.ScrollPhase

@MainActor
class DashboardStoreOld: ObservableObject, EntryServiceDelegate {
    @Injector private var notificationService: NotificationHelperService
    @Injector var accountService: AccountService
    @Injector private var logger : LoggerService
    @Injector private var scaleService: ScaleService

    private let perfLog = OSLog(subsystem: Bundle.main.bundleIdentifier ?? "DashboardStore", category: "Scrolling")

    @Published var isLoading: Bool = false
    @Published var loaderOverride: LoaderModel? = nil
    @Published var alertData: AlertModel? = nil

    let lang = LoaderStrings.self

    @Published var metricType: DashboardMetricType = .four // Default to 4, will be updated based on R4 scale presence

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
            // Determine dashboard metric type based on R4 scale presence
            await determineDashboardMetricType()

            // Load dashboard configuration from API first
            await loadDashboardConfigurationFromAPI()

            // Then load other data
            loadLatestEntryData()
            await loadInitialData()
        }
    }

    // MARK: - Dashboard Metric Type Logic

    /// Determine dashboard metric type based on R4 scale presence
    /// Dashboard type = 12 if R4 scale is present or has entries, otherwise = 4
    @MainActor
    private func determineDashboardMetricType() async {
        // Check if there are any R4 scales in paired devices
        let hasR4Scale = await checkForR4ScaleInPairedDevices()

        // Check if there are any entries from R4 scales
        let hasR4Entries = await checkForR4ScaleEntries()

        // Update metric type based on R4 presence
        if hasR4Scale || hasR4Entries {
            metricType = .twelve
            logger.log(level: .info, tag: "DashboardStore", message: "Dashboard metric type set to 12 (R4 scale detected)")
        } else {
            metricType = .four
            logger.log(level: .info, tag: "DashboardStore", message: "Dashboard metric type set to 4 (no R4 scale)")
        }
    }

    /// Check if there are any R4 scales in paired devices
    private func checkForR4ScaleInPairedDevices() async -> Bool {
        do {
            let devices = try await scaleService.getDevices()
            let r4Scales = devices.filter { device in
                // Check if device is R4 scale using ScaleTypeHelper
                let scaleType = ScaleTypeHelper.determineScaleType(for: device)
                return scaleType == .bluetoothR4
            }

            let hasR4Scale = !r4Scales.isEmpty
            logger.log(level: .info, tag: "DashboardStore", message: "R4 scales in paired devices: \(r4Scales.count), hasR4Scale: \(hasR4Scale)")
            return hasR4Scale
        } catch {
            logger.log(level: .error, tag: "DashboardStore", message: "Failed to check for R4 scales: \(error)")
            return false
        }
    }

    /// Check if there are any entries from R4 scales
    private func checkForR4ScaleEntries() async -> Bool {
        do {
            let entries = try await entryService.getAllEntries()

            // Check if any entries have R4 scale source
            let r4Entries = entries.filter { entry in
                // Check if the entry source indicates R4 scale
                if let source = entry.scaleEntry?.source {
                    return source.lowercased().contains("r4") ||
                           source.lowercased().contains("btwifi") ||
                           source.lowercased().contains("bluetooth/wifi")
                }
                return false
            }

            let hasR4Entries = !r4Entries.isEmpty
            logger.log(level: .info, tag: "DashboardStore", message: "R4 scale entries found: \(r4Entries.count), hasR4Entries: \(hasR4Entries)")
            return hasR4Entries
        } catch {
            logger.log(level: .error, tag: "DashboardStore", message: "Failed to check for R4 scale entries: \(error)")
            return false
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
    /// In normal mode, shows actual weight values with one decimal point
    func formatWeightDisplayText(_ weight: Double?) -> String {
        guard let weight = weight else { return "0.0" }

        if isWeightlessModeEnabled {
            // Show +/- prefix for weightless mode with one decimal point
            let prefix = weight >= 0 ? "+" : ""
            return String(format: "%@%.1f", prefix, weight)
        } else {
            // Show normal weight with one decimal point
            return String(format: "%.1f", weight)
        }
    }

    /// Format Y-axis tick labels as whole numbers (no decimal points)
    func formatYAxisTickLabel(_ weight: Double) -> String {
        return String(format: "%.0f", weight)
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
            // Divide BMR by 10 before displaying
            let bmrValue = Double(bmr) / 10.0
            let formattedValue = BodyMetricsConvertor.convert(bmrValue, shouldCompose: false, wholeNumber: true)
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
            // Divide BMR by 10 before displaying
            let bmrValue = bmr / 10.0
            let formattedValue = BodyMetricsConvertor.convert(bmrValue, shouldCompose: false, wholeNumber: true)
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
        if isEditMode {
            // In edit mode, show all metrics with proper removal indicators
        if metricType == .four {
            let fourLabels: Set<String> = [DashboardStrings.bmi, DashboardStrings.bodyFat, DashboardStrings.muscle, DashboardStrings.water]
            let filteredMetrics = metrics.filter { fourLabels.contains($0.label) }
            logger.log(level: .debug, tag: "DashboardStore", message: "4-metric mode: filtered \(metrics.count) metrics to \(filteredMetrics.count) metrics")
            return filteredMetrics
        } else {
                return metrics
            }
        } else {
            // In normal mode, show only active metrics
            let activeMetrics = Array(metrics.prefix(activeMetricsCount))
            if metricType == .four {
                let fourLabels: Set<String> = [DashboardStrings.bmi, DashboardStrings.bodyFat, DashboardStrings.muscle, DashboardStrings.water]
                let filteredMetrics = activeMetrics.filter { fourLabels.contains($0.label) }
                logger.log(level: .debug, tag: "DashboardStore", message: "4-metric mode (normal): activeMetrics=\(activeMetrics.count), filtered=\(filteredMetrics.count), metricType=\(metricType)")
                return filteredMetrics
            } else {
                logger.log(level: .debug, tag: "DashboardStore", message: "12-metric mode: showing \(activeMetrics.count) active metrics")
                return activeMetrics
            }
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
                bmr: selectedPoint.bmr.map { Int($0 / 10.0) },
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

        let bmr = bmrStr.flatMap { Double($0) }.flatMap { Int($0 * 10) } // Multiply by 10 for storage
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

                // Refresh streak data after reset
                Task {
                    await self.refreshStreakData()
                    do {
                       try await self.saveDashboardMetricsToAPI()
                    }catch{
                        self.logger.log(level: .error, tag: "DashboardStore", message: "Failed to save dashboard changes: \(error)")
                    }
                }

                // Clear all selection states
                self.selectedMetricLabel = nil
                self.selectedEntry = nil
                self.selectedPoint = nil
                self.selectedXValue = nil
                self.selectedWeight = nil
                self.showCrosshair = false

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

        Task {
            do {
                // Save all dashboard configuration to API (body metrics, progress metrics, goal card)
                try await saveDashboardMetricsToAPI()

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            withAnimation(.easeInOut(duration: 0.3)) {
                self.isLoading = false
                self.loaderOverride = nil
                self.isEditMode = false
                self.resetDragState()
            }
        }
            } catch {
                logger.log(level: .error, tag: "DashboardStore", message: "Failed to save dashboard changes: \(error)")
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        self.isLoading = false
                        self.loaderOverride = nil
                        self.isEditMode = false
                        self.resetDragState()
                    }
                }
            }
        }
    }

    /// Save dashboard metrics to API
    /// This method sends the current dashboard metrics configuration to the server
    /// Sends body metrics in the order they appear on the dashboard (from top to bottom)
    /// Only includes body metrics as per API specification
    private func saveDashboardMetricsToAPI() async throws {
        // Get current visible body metrics in the order they appear on the dashboard (from top to bottom)
        // Only include active metrics (not removed ones) and maintain their current order
        let visibleMetrics = Array(metrics.prefix(activeMetricsCount)).map { $0.label }

        // Convert metric labels to API format (remove units and formatting)
        let apiMetrics = visibleMetrics.map { label -> String in
            // Convert display labels to API format
            switch label {
            case DashboardStrings.bmi:
                return "bmi"
            case DashboardStrings.bodyFat:
                return "bodyFat"
            case DashboardStrings.muscle:
                return "muscleMass"
            case DashboardStrings.water:
                return "water"
            case DashboardStrings.heartBpm:
                return "pulse"
            case DashboardStrings.bone:
                return "boneMass"
            case DashboardStrings.visceralFat:
                return "visceralFatLevel"
            case DashboardStrings.subFat:
                return "subcutaneousFatPercent"
            case DashboardStrings.protein:
                return "proteinPercent"
            case DashboardStrings.skelMuscle:
                return "skeletalMusclePercent"
            case DashboardStrings.bmrKcal:
                return "bmr"
            case DashboardStrings.metAge:
                return "metabolicAge"
            default:
                return label.lowercased().replacingOccurrences(of: " ", with: "")
            }
        }

        // Update dashboard metrics via API (only body metrics as per API specification)
        _ = try await accountService.updateDashboardMetrics(metrics: apiMetrics)

        logger.log(level: .info, tag: "DashboardStore", message: "Dashboard metrics saved to API: \(apiMetrics)")
    }



    /// Load dashboard configuration from API
    /// This method loads the dashboard body metrics from the server
    private func loadDashboardConfigurationFromAPI() async {
        guard let account = accountService.activeAccount else {
            logger.log(level: .error, tag: "DashboardStore", message: "No active account found for dashboard configuration")
            return
        }

        // Load dashboard body metrics from account
        if let dashboardMetrics = account.dashboardSettings?.dashboardMetrics {
            let metricArray = dashboardMetrics.split(separator: ",").map(String.init)
            updateMetricsFromAPI(metricArray)
            logger.log(level: .info, tag: "DashboardStore", message: "Loaded dashboard body metrics from API: \(metricArray)")
        }

        // Always refresh streak data with real values from API
        await refreshStreakData()
    }

    /// Update metrics display based on API data
    /// This method updates the metrics array based on the metrics received from the API
    /// Handles body metrics only as per API specification
    private func updateMetricsFromAPI(_ apiMetrics: [String]) {
        // Update body metrics from API (API only returns body metrics)
        updateBodyMetricsFromAPI(apiMetrics)

        logger.log(level: .info, tag: "DashboardStore", message: "Updated body metrics from API: \(apiMetrics)")
    }

    /// Update body metrics from API data
    private func updateBodyMetricsFromAPI(_ apiBodyMetrics: [String]) {
        // Convert API metrics to display labels for comparison
        let displayMetrics = apiBodyMetrics.map { apiMetric -> String in
            // Convert API format back to display labels
            switch apiMetric {
            case "bmi":
                return DashboardStrings.bmi
            case "bodyFat":
                return DashboardStrings.bodyFat
            case "muscleMass":
                return DashboardStrings.muscle
            case "water":
                return DashboardStrings.water
            case "pulse":
                return DashboardStrings.heartBpm
            case "boneMass":
                return DashboardStrings.bone
            case "visceralFatLevel":
                return DashboardStrings.visceralFat
            case "subcutaneousFatPercent":
                return DashboardStrings.subFat
            case "proteinPercent":
                return DashboardStrings.protein
            case "skeletalMusclePercent":
                return DashboardStrings.skelMuscle
            case "bmr":
                return DashboardStrings.bmrKcal
            case "metabolicAge":
                return DashboardStrings.metAge
            default:
                return apiMetric
            }
        }

        // Create a dictionary to map display labels to their API order
        var apiOrderMap: [String: Int] = [:]
        for (index, displayMetric) in displayMetrics.enumerated() {
            apiOrderMap[displayMetric] = index
        }

        // Reorder metrics: active metrics in API order first, then inactive metrics
        var activeMetrics: [MetricItem] = []
        var inactiveMetrics: [MetricItem] = []

        // First, collect all active metrics in API order
        for displayMetric in displayMetrics {
            if let originalMetric = originalMetrics.first(where: { $0.label == displayMetric }) {
                let metricItem = MetricItem(
                    value: originalMetric.value,
                    label: originalMetric.label,
                    unit: originalMetric.unit,
                    preLabel: originalMetric.preLabel,
                    icon: originalMetric.icon
                )
                activeMetrics.append(metricItem)
            }
        }

        // Then collect all inactive metrics
        for metric in originalMetrics {
            if !displayMetrics.contains(metric.label) {
                let metricItem = MetricItem(
                    value: metric.value,
                    label: metric.label,
                    unit: metric.unit,
                    preLabel: metric.preLabel,
                    icon: metric.icon
                )
                inactiveMetrics.append(metricItem)
            }
        }

        // Combine active metrics first (in API order), then inactive metrics
        metrics = activeMetrics + inactiveMetrics
        activeMetricsCount = activeMetrics.count

        logger.log(level: .info, tag: "DashboardStore", message: "Updated body metrics from API: \(apiBodyMetrics) -> \(displayMetrics), active count: \(activeMetricsCount), total metrics: \(metrics.count)")
        logger.log(level: .debug, tag: "DashboardStore", message: "Active metrics in API order: \(activeMetrics.map { $0.label })")
        logger.log(level: .debug, tag: "DashboardStore", message: "All metrics: \(metrics.map { $0.label })")
    }





    /// Update streak items with progress data from API
    @MainActor
    private func updateStreakItemsWithProgress(_ progress: Progress) {
        // Update streak items with real data from API
        var updatedStreakItems: [MetricItem] = []

        // Current streak
        updatedStreakItems.append(MetricItem(
            value: "\(progress.currentStreak)",
            label: DashboardStrings.currentStreak,
            unit: nil,
            preLabel: nil,
            icon: AppAssets.streak
        ))

        // Longest streak
        updatedStreakItems.append(MetricItem(
            value: "\(progress.longestStreak)",
            label: DashboardStrings.longestStreak,
            unit: nil,
            preLabel: nil,
            icon: AppAssets.longestStreak
        ))

        // Weekly change (divide by 10)
        updatedStreakItems.append(MetricItem(
            value: String(format: "%.1f", (Double("\(progress.week)") ?? 0) / 10.0),
            label: DashboardStrings.lbsWeek,
            unit: nil,
            preLabel: nil,
            icon: nil
        ))

        // Monthly change (divide by 10)
        updatedStreakItems.append(MetricItem(
            value: String(format: "%.1f", (Double("\(progress.month)") ?? 0) / 10.0),
            label: DashboardStrings.lbsMonth,
            unit: nil,
            preLabel: nil,
            icon: nil
        ))

        // Yearly change (divide by 10)
        updatedStreakItems.append(MetricItem(
            value: String(format: "%.1f", (Double("\(progress.year)") ?? 0) / 10.0),
            label: DashboardStrings.lbsYear,
            unit: nil,
            preLabel: nil,
            icon: nil
        ))

        // Total change (divide by 10)
        updatedStreakItems.append(MetricItem(
            value: String(format: "%.1f", (Double(progress.total ?? 0) / 10.0)),
            label: DashboardStrings.lbsTotal,
            unit: nil,
            preLabel: nil,
            icon: nil
        ))

        // Update the streak items array
        streakItems = updatedStreakItems
        activeStreakItemsCount = updatedStreakItems.count

        logger.log(level: .info, tag: "DashboardStore", message: "Updated streak items with progress data: currentStreak=\(progress.currentStreak), longestStreak=\(progress.longestStreak), week=\(progress.week), month=\(progress.month), year=\(progress.year), total=\(String(describing: progress.total))")
    }

    private func restoreOriginalMetricOrder() {
        metrics = originalMetrics.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }
        activeMetricsCount = originalMetrics.count
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
    var shouldShowStreakGrid: Bool {
        !streakItemsToShow.isEmpty
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
        // Force Y-axis recalculation by triggering objectWillChange
        objectWillChange.send()
        // Ensure goal weight is updated in the UI
        DispatchQueue.main.async {
            self.objectWillChange.send()
        }
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

        // Re-evaluate dashboard metric type when account changes
        Task {
            await determineDashboardMetricType()
        }
    }

    /// Handle entry changes (add/update/delete) with unit and weightless mode updates
    @MainActor
    func handleEntryChange() {
        // Force graph refresh by incrementing data change trigger
        dataChangeTrigger += 1

        // Update metrics with latest entry
        loadLatestEntryData()

        // Re-evaluate dashboard metric type based on new entries
        Task {
            await determineDashboardMetricType()
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
                // Apply snapping for better page alignment
                self?.snapToNearestPosition()
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
        // Apply snapping for better page alignment
        snapToNearestPosition()
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

                // Get weights in display units
                let initialWeightStored = Int(goalSettings.initialWeight ?? 0)
                let goalWeightStored = Int(goalSettings.goalWeight ?? 0)

                // Convert to display units
                let initialWeightDisplay = self.convertStoredWeightToDisplay(initialWeightStored)
                let goalWeightDisplay = self.convertStoredWeightToDisplay(goalWeightStored)
                let currentWeightDisplay = self.convertStoredWeightToDisplay(currentWeightStored)

                // Apply weightless mode adjustments if enabled
                if self.isWeightlessModeEnabled {
                    guard let anchorWeight = self.weightlessAnchorWeight else {
                        // Fallback to normal mode if no anchor weight
                        self.goalStartWeight = initialWeightDisplay
                        self.goalWeight = goalWeightDisplay
                        self.goalDelta = currentWeightDisplay - initialWeightDisplay
                        return
                    }

                    // In weightless mode, show differences from anchor weight
                    self.goalStartWeight = initialWeightDisplay - anchorWeight
                    self.goalWeight = goalWeightDisplay - anchorWeight
                    self.goalDelta = currentWeightDisplay - anchorWeight
                } else {
                    // Normal mode - show actual weights
                    self.goalStartWeight = initialWeightDisplay
                    self.goalWeight = goalWeightDisplay
                    self.goalDelta = currentWeightDisplay - initialWeightDisplay
                }

                // Calculate progress based on weightless-adjusted values
                let totalDistance = abs(self.goalWeight - self.goalStartWeight)
                let achievedDistance = abs(self.goalDelta)

                if totalDistance > 0 {
                    let progress = min(max(CGFloat(achievedDistance / totalDistance), 0), 1)
                    self.goalProgress = progress
                } else {
                    self.goalProgress = 1.0
                }

                logger.log(level: .info, tag: "DashboardStore", message: "Goal card data loaded - weightless mode: \(self.isWeightlessModeEnabled), start: \(self.goalStartWeight), goal: \(self.goalWeight), delta: \(self.goalDelta)")

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
            await refreshStreakData()
            handleSettingsChange()
            if let latestDate = continuousOperations.map(\.date).max() {
                updateScrollPositionDebounced(to: latestDate)
                // Apply snapping for better page alignment
                snapToNearestPosition()
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
            await refreshStreakData()
            handleSettingsChange()
            if let latestDate = continuousOperations.map(\.date).max() {
                updateScrollPositionDebounced(to: latestDate)
                // Apply snapping for better page alignment
                snapToNearestPosition()
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
            await refreshStreakData()
            handleSettingsChange()
            if let latestDate = continuousOperations.map(\.date).max() {
                updateScrollPositionDebounced(to: latestDate)
                // Apply snapping for better page alignment
                snapToNearestPosition()
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

        // Get weight values for normalization (use all operations, not just visible ones)
        let weightValues = continuousOperations.map { summary -> Double in
            if isWeightlessModeEnabled {
                guard let anchorWeight = weightlessAnchorWeight else { return 0 }
                let currentWeight = convertStoredWeightToDisplay(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return convertStoredWeightToDisplay(Int(summary.weight))
            }
        }

        // Calculate weight range from all data points (like WeightGraph)
        guard let weightMin = weightValues.min(),
              let weightMax = weightValues.max(),
              weightMax > weightMin else {
            print("Hello: DashboardStore - chartSeriesData - No valid weight range found")
            return []
        }

        let weightRange = weightMin...weightMax

        print("Hello: DashboardStore - chartSeriesData - Weight range: [\(weightMin), \(weightMax)]")

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
                    // Normalize metric value to weight range using WeightGraph approach
                    let normalizedValue = normalizeMetricValue(metricValue, for: selectedMetric, toWeightRange: weightRange)
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
    /// Uses the WeightGraph approach: scale metric values to fit within weight range
    /// Formula: normalizedValue = weightMin + (metricValue - metricMin) * (weightMax - weightMin) / (metricMax - metricMin)
    private func normalizeMetricValue(_ value: Double, for metricLabel: String, toWeightRange weightRange: ClosedRange<Double>) -> Double {
        let weightMin = weightRange.lowerBound
        let weightMax = weightRange.upperBound
        let weightSpan = weightMax - weightMin

        // Get all metric values for this metric to calculate actual min/max
        let metricValues = continuousOperations.compactMap { summary -> Double? in
            switch metricLabel {
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
                // BMR is stored as integer (kcal * 10), convert to display value
                return summary.bmr.map { Double($0) / 10.0 }
            case DashboardStrings.metAge:
                // Metabolic age is stored as integer
                return summary.metabolicAge.map { Double($0) }
            default:
                return nil
            }
        }.filter { $0.isFinite && !$0.isNaN }

        guard let metricMin = metricValues.min(),
              let metricMax = metricValues.max(),
              metricMax > metricMin else {
            // Fallback to predefined ranges based on metric type
            let (fallbackMin, fallbackMax): (Double, Double) = {
            switch metricLabel {
            case DashboardStrings.bmi:
                return (18.0, 35.0) // BMI range
            case DashboardStrings.bodyFat, DashboardStrings.muscle, DashboardStrings.water,
                 DashboardStrings.bone, DashboardStrings.subFat, DashboardStrings.protein,
                 DashboardStrings.skelMuscle:
                    return (0.0, 100.0) // Percentage metrics (0-100%)
            case DashboardStrings.heartBpm:
                return (40.0, 200.0) // Heart rate range
            case DashboardStrings.visceralFat:
                return (1.0, 30.0) // Visceral fat level range
            case DashboardStrings.bmrKcal:
                    return (1000.0, 3000.0) // BMR range (kcal)
            case DashboardStrings.metAge:
                return (15.0, 80.0) // Metabolic age range
            default:
                    return (0.0, 100.0)
                }
            }()

            // Clamp value to fallback range
            let clampedValue = max(fallbackMin, min(fallbackMax, value))

            // Normalize to weight range using fallback range (WeightGraph approach)
            let fallbackSpan = fallbackMax - fallbackMin
            let normalizedValue = weightMin + (clampedValue - fallbackMin) * weightSpan / fallbackSpan

            print("Hello: normalizeMetricValue - Using fallback range for \(metricLabel), Original: \(value), Range: [\(fallbackMin), \(fallbackMax)], Normalized: \(normalizedValue)")

            return normalizedValue
        }

        // Clamp value to actual metric range
        let clampedValue = max(metricMin, min(metricMax, value))

        // Normalize to weight range using actual metric range (WeightGraph approach)
        let metricSpan = metricMax - metricMin
        let normalizedValue = weightMin + (clampedValue - metricMin) * weightSpan / metricSpan

        print("Hello: normalizeMetricValue - Metric: \(metricLabel), Original: \(value), Actual Range: [\(metricMin), \(metricMax)], Normalized: \(normalizedValue), WeightRange: [\(weightMin), \(weightMax)]")

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

        // Apply snapping for better page alignment when period changes
        snapToNearestPosition()
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

    // MARK: - Y-Axis Calculations

    /// Get Y-axis scale for chart display
    func getYAxisScale() -> YAxisScale {
        // Ensure Y-axis calculation is triggered when unit changes
        let currentUnit = accountService.activeAccount?.weightSettings?.weightUnit ?? .lb

        return YAxisCalculator.calculateYAxis(
            operations: continuousOperations,
            goalWeight: goalWeightForDisplay,
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight,
            convertStoredWeightToDisplay: convertStoredWeightToDisplay,
            chartHeight: chartHeight,
            minTickSpacing: 40
        )
    }

    /// Get average weight for display based on visible operations
    func getCurrentAverageWeight() -> Double {
        let visibleOps = getVisibleOperations()
        let opsToUse = visibleOps.isEmpty ? continuousOperations : visibleOps

        let weightValues = opsToUse.map { summary -> Double in
            if isWeightlessModeEnabled {
                guard let anchorWeight = weightlessAnchorWeight else { return 0 }
                let currentWeight = convertStoredWeightToDisplay(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return convertStoredWeightToDisplay(Int(summary.weight))
            }
        }

        guard !weightValues.isEmpty else { return 0 }
        let average = weightValues.reduce(0, +) / Double(weightValues.count)
        print("Hello: DashboardStore - getCurrentAverageWeight - Using visible operations average: \(average)")
        return average
    }

    /// Get metric value for a summary based on selected metric
    /// Returns the appropriate value for each metric type
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
            // BMR is stored as integer (kcal * 10), convert to display value
            return summary.bmr.map { Double($0) / 10.0 }
        case DashboardStrings.metAge:
            // Metabolic age is stored as integer
            return summary.metabolicAge.map { Double($0) }
        default:
            return nil
        }
    }


    /// Get selected point's metric values for metric info sheet
    /// Returns the appropriate values for each metric type
    func getSelectedPointMetricValues() -> [String: Double] {
        guard let selectedPoint = selectedPoint else { return [:] }

        var metricValues: [String: Double] = [:]

        // Add all available metrics from the selected point with proper type handling
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
        if let bmr = selectedPoint.bmr {
            // BMR is stored as integer (kcal * 10), convert to display value
            metricValues[DashboardStrings.bmrKcal] = Double(bmr) / 10.0
        }
        if let metAge = selectedPoint.metabolicAge {
            // Metabolic age is stored as integer
            metricValues[DashboardStrings.metAge] = Double(metAge)
        }

        return metricValues
    }

    /// Check if we should repeat x-axis labels based on entry count thresholds
    /// - Week: Repeat if ≥ 7 entries
    /// - Month: Repeat if ≥ 20 entries
    /// - Year/Total: Repeat if ≥ 12 entries
    private func shouldRepeatXAxisLabels(for period: TimePeriod) -> Bool {
        let entryCount = continuousOperations.count

        switch period {
        case .week:
            return entryCount >= 7
        case .month:
            return entryCount >= 20
        case .year, .total:
            return entryCount >= 12
        }
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
    /// Shows labels once for few entries, repeats for many entries based on thresholds
    func xAxisValues(for period: TimePeriod) -> [Date] {
        let allDates = continuousOperations.map(\.date)
        guard let minDate = allDates.min(), let maxDate = allDates.max() else { return [] }

        let entryCount = continuousOperations.count
        let shouldRepeat = shouldRepeatXAxisLabels(for: period)

        print("Hello: xAxisValues - Period: \(period), Entry count: \(entryCount), Should repeat: \(shouldRepeat)")

        switch period {
        case .week:
            if entryCount < 7 {
                // Few entries: show labels once
                let weekStart = calendar.dateInterval(of: .weekOfYear, for: minDate)?.start ?? minDate
                var dates: [Date] = []
                for dayOffset in 0..<7 {
                    if let dayDate = calendar.date(byAdding: .day, value: dayOffset, to: weekStart) {
                        dates.append(dayDate)
                    }
                }
                return dates
            } else {
                // Many entries: repeat labels throughout scroll view
                let totalWeeks = max(8, Int(ceil(maxDate.timeIntervalSince(minDate) / (7 * 24 * 60 * 60))))
                let weekStart = calendar.dateInterval(of: .weekOfYear, for: minDate)?.start ?? minDate
                let bufferWeeks = 2
                var dates: [Date] = []

                for weekOffset in -bufferWeeks..<(totalWeeks + bufferWeeks) {
                    if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: weekStart) {
                        for dayOffset in 0..<7 {
                            if let dayDate = calendar.date(byAdding: .day, value: dayOffset, to: weekDate) {
                                dates.append(dayDate)
                            }
                        }
                    }
                }
                return dates
            }

        case .month:
            if entryCount < 20 {
                // Few entries: show labels once
                let monthStart = calendar.dateInterval(of: .month, for: minDate)?.start ?? minDate
                var dates: [Date] = []
                for weekOffset in 0..<5 {
                    if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: monthStart) {
                        dates.append(weekDate)
                    }
                }
                return dates
            } else {
                // Many entries: repeat labels throughout scroll view
                let totalMonths = max(6, Int(ceil(maxDate.timeIntervalSince(minDate) / (30 * 24 * 60 * 60))))
                let monthStart = calendar.dateInterval(of: .month, for: minDate)?.start ?? minDate
                let bufferMonths = 2
                var dates: [Date] = []

                for monthOffset in -bufferMonths..<(totalMonths + bufferMonths) {
                    if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: monthStart) {
                        for weekOffset in 0..<5 {
                            if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: monthDate) {
                                dates.append(weekDate)
                            }
                        }
                    }
                }
                return dates
            }

        case .year:
            if entryCount < 12 {
                // Few entries: show labels once
            let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
                var dates: [Date] = []
                for monthOffset in 0..<12 {
                    if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: yearStart) {
                        dates.append(monthDate)
                    }
                }
                return dates
            } else {
                // Many entries: repeat labels throughout scroll view
                let totalYears = max(3, Int(ceil(maxDate.timeIntervalSince(minDate) / (365 * 24 * 60 * 60))))
                let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
                let bufferYears = 1
                var dates: [Date] = []

                for yearOffset in -bufferYears..<(totalYears + bufferYears) {
                    if let yearDate = calendar.date(byAdding: .year, value: yearOffset, to: yearStart) {
                        for monthOffset in 0..<12 {
                            if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: yearDate) {
                                dates.append(monthDate)
                            }
                        }
                    }
                }
                return dates
            }

        case .total:
            if areEntriesInSameEra(continuousOperations) {
                // For same era, treat like year view
                if entryCount < 12 {
                    // Few entries: show labels once
                let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
                    var dates: [Date] = []
                    for monthOffset in 0..<12 {
                        if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: yearStart) {
                            dates.append(monthDate)
                        }
                    }
                    return dates
            } else {
                    // Many entries: repeat labels throughout scroll view
                    let totalYears = max(3, Int(ceil(maxDate.timeIntervalSince(minDate) / (365 * 24 * 60 * 60))))
                    let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
                    let bufferYears = 1
                    var dates: [Date] = []

                    for yearOffset in -bufferYears..<(totalYears + bufferYears) {
                        if let yearDate = calendar.date(byAdding: .year, value: yearOffset, to: yearStart) {
                            for monthOffset in 0..<12 {
                                if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: yearDate) {
                                    dates.append(monthDate)
                                }
                            }
                        }
                    }
                    return dates
                }
            } else {
                // For multiple years, always repeat labels
                let totalQuarters = max(8, Int(ceil(maxDate.timeIntervalSince(minDate) / (90 * 24 * 60 * 60))))
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
    }

    func xAxisValuesWithBuffer(for period: TimePeriod) -> [Date] {
        let allDates = continuousOperations.map(\.date)
        guard let minDate = allDates.min(), let maxDate = allDates.max() else { return [] }

        let entryCount = continuousOperations.count
        let shouldRepeat = shouldRepeatXAxisLabels(for: period)

        print("Hello: xAxisValuesWithBuffer - Period: \(period), Entry count: \(entryCount), Should repeat: \(shouldRepeat)")

        switch period {
        case .week:
            if entryCount < 7 {
                // Few entries: show labels once with minimal buffer
                let weekStart = calendar.dateInterval(of: .weekOfYear, for: minDate)?.start ?? minDate
                var dates: [Date] = []
                for dayOffset in -1..<8 { // Add 1 day buffer
                    if let dayDate = calendar.date(byAdding: .day, value: dayOffset, to: weekStart) {
                        dates.append(dayDate)
                    }
                }
                return dates
            } else {
                // Many entries: repeat labels throughout scroll view with more buffer
                let totalWeeks = max(10, Int(ceil(maxDate.timeIntervalSince(minDate) / (7 * 24 * 60 * 60))))
                let weekStart = calendar.dateInterval(of: .weekOfYear, for: minDate)?.start ?? minDate
                let bufferWeeks = 3
                var dates: [Date] = []

                for weekOffset in -bufferWeeks..<(totalWeeks + bufferWeeks) {
                    if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: weekStart) {
                        for dayOffset in 0..<7 {
                            if let dayDate = calendar.date(byAdding: .day, value: dayOffset, to: weekDate) {
                                dates.append(dayDate)
                            }
                        }
                    }
                }
                return dates
            }

        case .month:
            if entryCount < 20 {
                // Few entries: show labels once with minimal buffer
                let monthStart = calendar.dateInterval(of: .month, for: minDate)?.start ?? minDate
                var dates: [Date] = []
                for weekOffset in -1..<6 { // Add 1 week buffer
                    if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: monthStart) {
                        dates.append(weekDate)
                    }
                }
                return dates
            } else {
                // Many entries: repeat labels throughout scroll view with more buffer
                let totalMonths = max(8, Int(ceil(maxDate.timeIntervalSince(minDate) / (30 * 24 * 60 * 60))))
                let monthStart = calendar.dateInterval(of: .month, for: minDate)?.start ?? minDate
                let bufferMonths = 3
                var dates: [Date] = []

                for monthOffset in -bufferMonths..<(totalMonths + bufferMonths) {
                    if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: monthStart) {
                        for weekOffset in 0..<5 {
                            if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: monthDate) {
                                dates.append(weekDate)
                            }
                        }
                    }
                }
                return dates
            }

        case .year:
            if entryCount < 12 {
                // Few entries: show labels once with minimal buffer
            let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
                var dates: [Date] = []
                for monthOffset in -1..<13 { // Add 1 month buffer
                    if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: yearStart) {
                        dates.append(monthDate)
                    }
                }
                return dates
            } else {
                // Many entries: repeat labels throughout scroll view with more buffer
                let totalYears = max(5, Int(ceil(maxDate.timeIntervalSince(minDate) / (365 * 24 * 60 * 60))))
                let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
                let bufferYears = 2
                var dates: [Date] = []

                for yearOffset in -bufferYears..<(totalYears + bufferYears) {
                    if let yearDate = calendar.date(byAdding: .year, value: yearOffset, to: yearStart) {
                        for monthOffset in 0..<12 {
                            if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: yearDate) {
                                dates.append(monthDate)
                            }
                        }
                    }
                }
                return dates
            }

        case .total:
            if areEntriesInSameEra(continuousOperations) {
                // For same era, treat like year view
                if entryCount < 12 {
                    // Few entries: show labels once with minimal buffer
                let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
                    var dates: [Date] = []
                    for monthOffset in -1..<13 { // Add 1 month buffer
                        if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: yearStart) {
                            dates.append(monthDate)
                        }
                    }
                    return dates
            } else {
                    // Many entries: repeat labels throughout scroll view with more buffer
                    let totalYears = max(5, Int(ceil(maxDate.timeIntervalSince(minDate) / (365 * 24 * 60 * 60))))
                    let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
                    let bufferYears = 2
                    var dates: [Date] = []

                    for yearOffset in -bufferYears..<(totalYears + bufferYears) {
                        if let yearDate = calendar.date(byAdding: .year, value: yearOffset, to: yearStart) {
                            for monthOffset in 0..<12 {
                                if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: yearDate) {
                                    dates.append(monthDate)
                                }
                            }
                        }
                    }
                    return dates
                }
            } else {
                // For multiple years, always repeat labels with buffer
                let totalQuarters = max(12, Int(ceil(maxDate.timeIntervalSince(minDate) / (90 * 24 * 60 * 60))))
                let quarterStart = calendar.date(from: calendar.dateComponents([.year, .month], from: minDate)) ?? minDate
                let bufferQuarters = 3
                var dates: [Date] = []

                for quarterOffset in -bufferQuarters..<(totalQuarters + bufferQuarters) {
                    if let quarterDate = calendar.date(byAdding: .month, value: quarterOffset * 3, to: quarterStart) {
                        dates.append(quarterDate)
                }
            }
        return dates
            }
        }
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

    /// Enhanced time snap unit calculation for better page snapping
    ///
    /// Provides optimal snap units based on the selected time period:
    /// - Week: Snap to week boundaries (7 days)
    /// - Month: Snap to month boundaries (30 days)
    /// - Year: Snap to quarters (90 days) or months (30 days) based on data span
    /// - Total: Adaptive snapping based on data era and span
    ///
    /// Note: iOS 18+ uses built-in snapping with these units, iOS <18 uses custom gesture-based snapping
    func timeSnapUnit(for period: TimePeriod) -> TimeInterval {
        switch period {
        case .week:
            return 7 * 24 * 60 * 60 // Snap to week boundaries
        case .month:
            return 30 * 24 * 60 * 60 // Snap to month boundaries
        case .year:
            // Check if entries span less than a year
            if doEntriesSpanLessThanYear(continuousOperations) {
                // Use monthly snap units for better precision when data spans less than a year
                return 30 * 24 * 60 * 60
            } else {
                // Use quarterly snap units for better performance when data spans full year
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

    /// Get visible operations based on current scroll position
    /// This method returns operations that are currently visible in the chart
    /// Used for Y-axis domain calculations to ensure all visible points are properly displayed
    func getVisibleOperations() -> [BathScaleWeightSummary] {
        let visibleStart = xScrollPosition.addingTimeInterval(-visibleDomainLength(for: selectedPeriod) / 2)
        let visibleEnd = xScrollPosition.addingTimeInterval(visibleDomainLength(for: selectedPeriod) / 2)

        let visibleOps = continuousOperations.filter { summary in
            return summary.date >= visibleStart && summary.date <= visibleEnd
        }

        print("Hello: getVisibleOperations - Scroll position: \(xScrollPosition)")
        print("Hello: getVisibleOperations - Visible range: \(visibleStart) to \(visibleEnd)")
        print("Hello: getVisibleOperations - Total operations: \(continuousOperations.count)")
        print("Hello: getVisibleOperations - Visible operations: \(visibleOps.count)")

        return visibleOps
    }

    var visibleOperations: [BathScaleWeightSummary] {
        return getVisibleOperations()
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
    /// This method is called when scrolling stops to recalculate Y-axis domain and ticks
    /// based on the currently visible operations, ensuring all visible points are properly displayed
    @MainActor
    func updateVisibleDataAfterScroll() {
        // Force UI update to recalculate Y-axis based on visible operations
        objectWillChange.send()

        // Update weight display to show average of visible operations
        let visibleOps = getVisibleOperations()
        let opsToUse = visibleOps.isEmpty ? continuousOperations : visibleOps

        let weightValues = opsToUse.map { summary -> Double in
            if isWeightlessModeEnabled {
                guard let anchorWeight = weightlessAnchorWeight else { return 0 }
                let currentWeight = convertStoredWeightToDisplay(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return convertStoredWeightToDisplay(Int(summary.weight))
            }
        }

        if let averageWeight = weightValues.isEmpty ? nil : weightValues.reduce(0, +) / Double(weightValues.count) {
            print("Hello: updateVisibleDataAfterScroll - Average weight of visible operations: \(averageWeight)")
        }

        print("Hello: updateVisibleDataAfterScroll - Updated Y-axis domain and ticks based on visible operations")
    }

    /// Handle scroll phase changes for iOS 18+
    /// This method provides precise scroll state management using the new ScrollPhase API
    ///
    /// iOS 18+ uses the new ScrollPhase API for better scroll state detection:
    /// - .idle: No scrolling is occurring
    /// - .tracking: User is touching but hasn't started scrolling
    /// - .interacting: User is actively scrolling
    /// - .decelerating: User stopped scrolling, chart is decelerating
    /// - .animating: System is animating to a final target
    @available(iOS 18.0, *)
    @MainActor
    func handleScrollPhaseChange(_ phase: ScrollPhase) {
        switch phase {
        case .idle:
            // No scrolling is occurring
            isScrolling = false
            hasDetectedScrollInCurrentGesture = false

            // Clear selection state for better UX
            showCrosshair = false
            selectedXValue = nil
            selectedPoint = nil
            selectEntry(nil)

            // Reset metrics to latest entry when scrolling ends
            resetMetricsToLatestEntry()

            // Update visible data after scroll ends for better Y-axis calculation
            updateVisibleDataAfterScroll()

            // Log scroll end for debugging
            os_log("ScrollPhase: idle - Scroll ended", log: perfLog, type: .info)

            print("Hello: handleScrollPhaseChange - iOS 18+ ScrollPhase: idle")

        case .tracking:
            // User is touching but hasn't started scrolling yet
            hasDetectedScrollInCurrentGesture = false
            print("Hello: handleScrollPhaseChange - iOS 18+ ScrollPhase: tracking")

        case .interacting:
            // User is actively scrolling
            if !hasDetectedScrollInCurrentGesture {
                hasDetectedScrollInCurrentGesture = true
                isScrolling = true

                // Clear selection when scrolling starts
                selectedXValue = nil
                selectedPoint = nil
                showCrosshair = false
                selectEntry(nil)
            }
            print("Hello: handleScrollPhaseChange - iOS 18+ ScrollPhase: interacting")

        case .decelerating:
            // User stopped scrolling, chart is decelerating to final position
            isScrolling = true
            print("Hello: handleScrollPhaseChange - iOS 18+ ScrollPhase: decelerating")

        case .animating:
            // System is animating to a final target (programmatic scroll)
            isScrolling = true
            print("Hello: handleScrollPhaseChange - iOS 18+ ScrollPhase: animating")

        @unknown default:
            // Handle any future cases
            print("Hello: handleScrollPhaseChange - iOS 18+ ScrollPhase: unknown case")
        }
    }

    func handleScrollEnd() {
        // Cancel any existing timer
        scrollEndTimer?.invalidate()

        // Set a timer to detect when scrolling has truly ended
        scrollEndTimer = Timer.scheduledTimer(withTimeInterval: 0.3, repeats: false) { [weak self] _ in
            Task { @MainActor in
                guard let self = self else { return }

                // Update scrolling state
                self.isScrolling = false

                // Clear selection state for better UX
                self.showCrosshair = false
                self.selectedXValue = nil
                self.selectedPoint = nil
                self.selectEntry(nil)

                // Reset metrics to latest entry when scrolling ends
                self.resetMetricsToLatestEntry()

                // Update visible data after scroll ends for better Y-axis calculation
                self.updateVisibleDataAfterScroll()

                // Log scroll end for debugging
                os_log("Scroll ended with enhanced page snapping", log: self.perfLog, type: .info)

                print("Hello: handleScrollEnd - Enhanced scroll end handling completed")
            }
        }
    }

    /// Custom scroll end handling for iOS versions below 18
    /// Includes manual snap position calculation since built-in snapping is not available
    ///
    /// This method is used for iOS versions below 18.0 where built-in chart snapping
    /// is not available. It provides custom gesture-based snapping with manual
    /// position calculation and smooth animation to optimal snap positions.
    func handleScrollEndWithCustomSnapping() {
        // Cancel any existing timer
        scrollEndTimer?.invalidate()

        // Set a timer to detect when scrolling has truly ended
        scrollEndTimer = Timer.scheduledTimer(withTimeInterval: 0.3, repeats: false) { [weak self] _ in
            Task { @MainActor in
                guard let self = self else { return }

                // Update scrolling state
                self.isScrolling = false

                // Clear selection state for better UX
                self.showCrosshair = false
                self.selectedXValue = nil
                self.selectedPoint = nil
                self.selectEntry(nil)

                // Apply custom snapping for iOS versions below 18
                self.applyCustomSnapping()

                // Reset metrics to latest entry when scrolling ends
                self.resetMetricsToLatestEntry()

                // Update visible data after scroll ends for better Y-axis calculation
                self.updateVisibleDataAfterScroll()

                // Log scroll end for debugging
                os_log("Scroll ended with custom snapping for iOS <18", log: self.perfLog, type: .info)

                print("Hello: handleScrollEndWithCustomSnapping - Custom snapping completed")
            }
        }
    }

    /// Apply custom snapping for iOS versions below 18
    /// This method calculates the nearest snap position and animates to it
    ///
    /// Used for iOS versions below 18.0 where built-in chart snapping is not available.
    /// Calculates optimal snap positions based on time period and data, then animates
    /// the scroll position to the nearest optimal boundary for smooth user experience.
    private func applyCustomSnapping() {
        let snapPositions = calculateOptimalSnapPositions()
        guard !snapPositions.isEmpty else { return }

        // Find the nearest snap position to current scroll position
        let nearest = snapPositions.min { pos1, pos2 in
            abs(pos1.timeIntervalSince(xScrollPosition)) < abs(pos2.timeIntervalSince(xScrollPosition))
        }

        if let nearest = nearest {
            // Animate to the nearest snap position
            withAnimation(.easeInOut(duration: 0.3)) {
                xScrollPosition = nearest
            }
            print("Hello: applyCustomSnapping - Snapped to position: \(nearest)")
        }
    }

    /// Calculate optimal snap positions for the current time period
    /// This helps with better page snapping behavior
    ///
    /// Calculates meaningful snap points based on the selected time period:
    /// - Week: Snap to week boundaries (start of each week)
    /// - Month: Snap to month boundaries (start of each month)
    /// - Year: Snap to quarter boundaries (start of each quarter)
    /// - Total: Adaptive snapping based on data era (quarters for same year, years for multiple years)
    ///
    /// Returns: Array of optimal snap positions sorted chronologically
    func calculateOptimalSnapPositions() -> [Date] {
        guard !continuousOperations.isEmpty else { return [] }

        let allDates = continuousOperations.map(\.date).sorted()
        var snapPositions: [Date] = []

        switch selectedPeriod {
        case .week:
            // Snap to week boundaries (start of each week)
            let calendar = Calendar.current
            for date in allDates {
                if let weekStart = calendar.dateInterval(of: .weekOfYear, for: date)?.start {
                    if !snapPositions.contains(where: { calendar.isDate($0, equalTo: weekStart, toGranularity: .weekOfYear) }) {
                        snapPositions.append(weekStart)
                    }
                }
            }

        case .month:
            // Snap to month boundaries (start of each month)
            let calendar = Calendar.current
            for date in allDates {
                if let monthStart = calendar.dateInterval(of: .month, for: date)?.start {
                    if !snapPositions.contains(where: { calendar.isDate($0, equalTo: monthStart, toGranularity: .month) }) {
                        snapPositions.append(monthStart)
                    }
                }
            }

        case .year:
            // Snap to quarter boundaries for year view
            let calendar = Calendar.current
            for date in allDates {
                let quarter = (calendar.component(.month, from: date) - 1) / 3
                let quarterStart = calendar.date(from: DateComponents(year: calendar.component(.year, from: date), month: quarter * 3 + 1)) ?? date
                if !snapPositions.contains(where: { calendar.isDate($0, equalTo: quarterStart, toGranularity: .month) }) {
                    snapPositions.append(quarterStart)
                }
            }

        case .total:
            if areEntriesInSameEra(continuousOperations) {
                // For same era, snap to quarter boundaries like year view
                let calendar = Calendar.current
                for date in allDates {
                    let quarter = (calendar.component(.month, from: date) - 1) / 3
                    let quarterStart = calendar.date(from: DateComponents(year: calendar.component(.year, from: date), month: quarter * 3 + 1)) ?? date
                    if !snapPositions.contains(where: { calendar.isDate($0, equalTo: quarterStart, toGranularity: .month) }) {
                        snapPositions.append(quarterStart)
                    }
                }
            } else {
                // For multiple years, snap to year boundaries
                let calendar = Calendar.current
                for date in allDates {
                    let yearStart = calendar.date(from: DateComponents(year: calendar.component(.year, from: date))) ?? date
                    if !snapPositions.contains(where: { calendar.isDate($0, equalTo: yearStart, toGranularity: .year) }) {
                        snapPositions.append(yearStart)
                    }
                }
            }
        }

        return snapPositions.sorted()
    }

    /// Snap scroll position to the nearest optimal position
    ///
    /// Automatically aligns the current scroll position to the nearest meaningful boundary
    /// based on the calculated optimal snap positions. This ensures the chart always
    /// displays data aligned to logical time boundaries (weeks, months, quarters, years).
    ///
    /// The method finds the closest snap position to the current scroll position
    /// and updates the scroll position to that optimal point.
    func snapToNearestPosition() {
        let snapPositions = calculateOptimalSnapPositions()
        guard !snapPositions.isEmpty else { return }

        // Find the nearest snap position to current scroll position
        let nearest = snapPositions.min { pos1, pos2 in
            abs(pos1.timeIntervalSince(xScrollPosition)) < abs(pos2.timeIntervalSince(xScrollPosition))
        }

        if let nearest = nearest {
            // iOS version-specific snapping behavior
            if #available(iOS 18.0, *) {
                // iOS 18+: Built-in snapping handles positioning, just update position
                xScrollPosition = nearest
                print("Hello: snapToNearestPosition - iOS 18+ snapped to position: \(nearest)")
            } else {
                // iOS <18: Animate to snap position for smoother experience
                withAnimation(.easeInOut(duration: 0.3)) {
                    xScrollPosition = nearest
                }
                print("Hello: snapToNearestPosition - iOS <18 animated to position: \(nearest)")
            }
        }
    }
}

/// Dashboard store with incremental updates for daily and monthly summaries.

extension DashboardStoreOld {

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

        // Refresh streak data when new entry is added
        await refreshStreakData()

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

        // Refresh streak data when entry is deleted
        await refreshStreakData()

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

    /// Refresh streak data from API
    private func refreshStreakData() async {
        do {
            let progress = try await entryService.getProgress()
             updateStreakItemsWithProgress(progress)
            logger.log(level: .info, tag: "DashboardStore", message: "Refreshed streak data from API")
        } catch {
            logger.log(level: .error, tag: "DashboardStore", message: "Failed to refresh streak data: \(error)")
        }
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
            // Apply snapping for better page alignment
            snapToNearestPosition()
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


