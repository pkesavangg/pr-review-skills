import SwiftUI
import SwiftData
import Combine
import Charts
import os
import Foundation

/// Unified DashboardStore with clean architecture and UI compatibility
/// Uses specialized managers for business logic while exposing centralized state for UI
@MainActor
class DashboardStore: ObservableObject, EntryServiceDelegate {

    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperService
    @Injector private var accountService: AccountService
    @Injector private var logger: LoggerService
    @Injector private var scaleService: ScaleService
    @Injector private var entryService: EntryService

    // MARK: - Centralized State
    @Published var state: DashboardState = DashboardState()

    // MARK: - Private Properties
    private var cancellables = Set<AnyCancellable>()
    private let calendar = Calendar.current
    private let perfLog = OSLog(subsystem: Bundle.main.bundleIdentifier ?? "DashboardStore", category: "Scrolling")

    // MARK: - Constants
    let lang = LoaderStrings.self

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

    // MARK: - Managers (Business Logic)
    private let metricsManager: DashboardMetricsManager
    private let graphManager: DashboardGraphManager
    private let goalManager: DashboardGoalManager
    private let streakManager: DashboardStreakManager
    private let dataManager: DashboardDataManager

    // MARK: - Initialization
    init() {
        // Initialize managers
        self.metricsManager = DashboardMetricsManager()
        self.graphManager = DashboardGraphManager()
        self.streakManager = DashboardStreakManager()
        self.dataManager = DashboardDataManager()
        self.goalManager = DashboardGoalManager()

        // Initialize state with default values
        state.metrics.metrics = originalMetrics.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }
        state.streak.streakItems = originalStreakItems.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }

        // Set up reactive bindings
        setupBindings()

        // Initialize dashboard
        Task {
            await initializeDashboard()
        }
    }

    // MARK: - Reactive Bindings
    private func setupBindings() {
        // Sync metrics manager state to centralized state
        metricsManager.$state
            .sink { [weak self] metricsState in
                self?.state.metrics = metricsState
            }
            .store(in: &cancellables)

        // Sync streak manager state to centralized state
        streakManager.$state
            .sink { [weak self] streakState in
                self?.state.streak = streakState
            }
            .store(in: &cancellables)

        // Sync goal manager state to centralized state
        goalManager.$state
            .sink { [weak self] goalState in
                self?.state.goal = goalState
            }
            .store(in: &cancellables)

        // Sync graph manager state to centralized state
        graphManager.$state
            .sink { [weak self] graphState in
                self?.state.graph = graphState
            }
            .store(in: &cancellables)

        // Sync data manager state to centralized state
        dataManager.$state
            .sink { [weak self] dataState in
                self?.state.data = dataState
            }
            .store(in: &cancellables)
    }

    // MARK: - Computed Properties

    var loaderData: Binding<LoaderModel?> {
        Binding(
            get: { self.state.ui.loaderOverride ?? (self.state.ui.isLoading ? LoaderModel(text: self.lang.saving) : nil) },
            set: { _ in }
        )
    }

    var metricGridColumns: [GridItem] {
        state.metrics.metricType == .four ?
        Array(repeating: GridItem(.flexible(), spacing: 16), count: 2) :
        Array(repeating: GridItem(.flexible(), spacing: 16), count: 3)
    }

    var metricsToShow: [MetricItem] {
        if state.ui.isEditMode {
            if state.metrics.metricType == .four {
                let fourLabels: Set<String> = [DashboardStrings.bmi, DashboardStrings.bodyFat, DashboardStrings.muscle, DashboardStrings.water]
                return state.metrics.metrics.filter { fourLabels.contains($0.label) }
            } else {
                return state.metrics.metrics
            }
        } else {
            return state.metrics.metricsToShow
        }
    }

    let streakColumns = Array(repeating: GridItem(.flexible(), spacing: 16), count: 2)

    var streakItemsToShow: [MetricItem] {
        state.ui.isEditMode ? state.streak.streakItems : state.streak.streakItemsToShow
    }

    var isAnyItemBeingDragged: Bool {
        state.ui.isAnyItemBeingDragged
    }

    var allContentRemoved: Bool {
        metricsToShow.isEmpty && (!state.ui.isEditMode && state.ui.isGoalCardRemoved) && (!shouldShowStreakGrid)
    }

    var shouldShowStreakGrid: Bool {
        !streakItemsToShow.isEmpty
    }

    var continuousOperations: [BathScaleWeightSummary] {
        dataManager.getContinuousOperations(for: state.graph.selectedPeriod)
    }

    var hasAnyEntries: Bool {
        state.data.hasAnyEntries
    }

    var isWeightlessModeEnabled: Bool {
        return accountService.activeAccount?.weightlessSettings?.isWeightlessOn ?? false
    }

    var weightlessAnchorWeight: Double? {
        guard let weightlessWeight = accountService.activeAccount?.weightlessSettings?.weightlessWeight else {
            return nil
        }
        return convertStoredWeightToDisplay(Int(weightlessWeight))
    }

    var goalWeightForDisplay: Double {
        if isWeightlessModeEnabled {
            guard let anchorWeight = weightlessAnchorWeight else { return state.goal.goalWeight }
            return state.goal.goalWeight - anchorWeight
        } else {
            return state.goal.goalWeight
        }
    }

    var displayWeight: Double? {
        // If a point is selected, show its weight value
        if let selectedPoint = state.graph.selectedPoint {
            if isWeightlessModeEnabled {
                guard let anchorWeight = weightlessAnchorWeight else { return nil }
                let currentWeight = convertStoredWeightToDisplay(Int(selectedPoint.weight))
                return currentWeight - anchorWeight
            } else {
                return convertStoredWeightToDisplay(Int(selectedPoint.weight))
            }
        }

        // When no point is selected, show average of visible region if available
        let visibleOps = getVisibleOperations()
        let opsToUse = visibleOps.isEmpty ? continuousOperations : visibleOps

        // Check if weightless mode is enabled
        if isWeightlessModeEnabled {
            return calculateWeightlessDisplay(opsToUse)
        }

        // Calculate average of operations in visible region (or all if no visible region)
        let weights = opsToUse.map { convertStoredWeightToDisplay(Int($0.weight)) }
        guard !weights.isEmpty else { return nil }
        let averageWeight = weights.reduce(0, +) / Double(weights.count)

        return averageWeight
    }

    var weightLabel: String {
        guard !continuousOperations.isEmpty else {
            return fallbackTimeLabel()
        }

        // If a point is selected, show its date
        if let selectedPoint = state.graph.selectedPoint {
            return formatSelectedDate(selectedPoint.date)
        }

        if let selectedEntry = state.graph.selectedEntry {
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

        return formatDateRange(minDate: minDate, maxDate: maxDate, for: state.graph.selectedPeriod)
    }

    var selectedBodyMetric: BodyMetric {
        guard let selectedLabel = state.ui.selectedMetricLabel else { return .weight }
        return metricsManager.getBodyMetric(for: selectedLabel)
    }

    var currentUnit: WeightUnit {
        accountService.activeAccount?.weightSettings?.weightUnit ?? .lb
    }

    var currentWeightlessMode: Bool {
        accountService.activeAccount?.weightlessSettings?.isWeightlessOn ?? false
    }

    var weightDisplayLabel: String {
        return "\(state.graph.selectedPeriod.rawValue) average"
    }

    var chartSeriesData: [GraphSeries] {
        return graphManager.generateChartData(
            from: continuousOperations,
            selectedMetric: state.ui.selectedMetricLabel,
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight,
            convertWeight: convertStoredWeightToDisplay
        )
    }

    /// Returns the average weight for the current visible or all operations
    @MainActor
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
        return average
    }

    /// Returns the current weight unit as a string (e.g., "lbs" or "kg")
    var unitText: String {
        accountService.activeAccount?.weightSettings?.weightUnit?.rawValue ?? "lbs"
    }

    /// Returns true if there are entries but none in the current time period
    var hasEntriesButNoneInCurrentPeriod: Bool {
        hasAnyEntries && continuousOperations.isEmpty
    }

    /// Returns the visible domain length for the given period
    func visibleDomainLength(for period: TimePeriod) -> TimeInterval {
        // Use the same logic as the old store
        switch period {
        case .week: return 7 * 24 * 60 * 60
        case .month: return 30 * 24 * 60 * 60
        case .year:
            if let ops = Optional(continuousOperations), doEntriesSpanLessThanYear(ops) {
                return 180 * 24 * 60 * 60 // 6 months
            } else {
                return 365 * 24 * 60 * 60 // 1 year
            }
        case .total:
            if let ops = Optional(continuousOperations), areEntriesInSameEra(ops) {
                if doEntriesSpanLessThanYear(ops) {
                    return 180 * 24 * 60 * 60 // 6 months
                } else {
                    return 365 * 24 * 60 * 60 // 1 year
                }
            } else {
                let allDates = continuousOperations.map(\.date)
                guard let minDate = allDates.min(), let maxDate = allDates.max() else {
                    return 365 * 24 * 60 * 60
                }
                let totalRange = maxDate.timeIntervalSince(minDate)
                return max(totalRange / 4, 365 * 24 * 60 * 60)
            }
        }
    }

    /// Returns the time snap unit for the given period
    func timeSnapUnit(for period: TimePeriod) -> TimeInterval {
        switch period {
        case .week:
            return 7 * 24 * 60 * 60
        case .month:
            return 30 * 24 * 60 * 60
        case .year:
            if let ops = Optional(continuousOperations), doEntriesSpanLessThanYear(ops) {
                return 30 * 24 * 60 * 60
            } else {
                return 90 * 24 * 60 * 60
            }
        case .total:
            if let ops = Optional(continuousOperations), areEntriesInSameEra(ops) {
                if doEntriesSpanLessThanYear(ops) {
                    return 30 * 24 * 60 * 60
                } else {
                    return 90 * 24 * 60 * 60
                }
            } else {
                return 90 * 24 * 60 * 60
            }
        }
    }

    /// Updates visible data after scroll ends (forces UI update and logs average weight)
    func updateVisibleDataAfterScroll() {
        objectWillChange.send()
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

    /// Returns x-axis values with buffer for the given period (fix: always return a value)
    func xAxisValuesWithBuffer(for period: TimePeriod) -> [Date] {
        // Use the same logic as the old store
        let allDates = continuousOperations.map(\.date)
        guard let minDate = allDates.min(), let maxDate = allDates.max() else { return [] }
        let entryCount = continuousOperations.count
        _ = shouldRepeatXAxisLabels(for: period)
        switch period {
        case .week:
            if entryCount < 7 {
                let weekStart = calendar.dateInterval(of: .weekOfYear, for: minDate)?.start ?? minDate
                var dates: [Date] = []
                for dayOffset in -1..<8 {
                    if let dayDate = calendar.date(byAdding: .day, value: dayOffset, to: weekStart) {
                        dates.append(dayDate)
                    }
                }
                return dates
            } else {
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
                let monthStart = calendar.dateInterval(of: .month, for: minDate)?.start ?? minDate
                var dates: [Date] = []
                for weekOffset in -1..<6 {
                    if let weekDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: monthStart) {
                        dates.append(weekDate)
                    }
                }
                return dates
            } else {
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
                let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
                var dates: [Date] = []
                for monthOffset in -1..<13 {
                    if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: yearStart) {
                        dates.append(monthDate)
                    }
                }
                return dates
            } else {
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
                if entryCount < 12 {
                    let yearStart = calendar.dateInterval(of: .year, for: minDate)?.start ?? minDate
                    var dates: [Date] = []
                    for monthOffset in -1..<13 {
                        if let monthDate = calendar.date(byAdding: .month, value: monthOffset, to: yearStart) {
                            dates.append(monthDate)
                        }
                    }
                    return dates
                } else {
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
        return [] // Ensure all code paths return a value
    }

    /// Returns the x-axis label string for a given date and period
    func xLabelString(for date: Date, period: TimePeriod) -> String? {
        switch period {
        case .week:
            return WeekDay.abbreviation(for: calendar.component(.weekday, from: date))
        case .month:
            return "\(calendar.component(.day, from: date))"
        case .year:
            return Month.initial(for: calendar.component(.month, from: date))
        case .total:
            if areEntriesInSameEra(continuousOperations) {
                return Month.initial(for: calendar.component(.month, from: date))
            } else {
                return "\(calendar.component(.year, from: date))"
            }
        }
    }

    /// Selects an entry for the chart
    func selectEntry(_ entry: BathScaleWeightSummary?) {
        if let entry = entry {
            state.graph.selectedEntry = BathScaleOperationDTO(from: entry)
            state.graph.selectedWeight = convertStoredWeightToDisplay(Int(entry.weight))
        } else {
            state.graph.selectedEntry = nil
            state.graph.selectedWeight = nil
        }
        objectWillChange.send()
    }

    /// Resets metric values to the latest entry data
    func resetMetricsToLatestEntry() {
        Task {
            do {
                guard let latestEntry = try await entryService.getLatestEntry() else {
                    return
                }
                try await metricsManager.updateMetrics(with: latestEntry)
            } catch {
                logger.log(level: .error, tag: "DashboardStore", message: "Failed to reset metrics to latest entry: \(error)")
            }
        }
    }

    // MARK: - Dashboard Initialization

    private func initializeDashboard() async {
        await determineDashboardMetricType()
        await loadDashboardConfigurationFromAPI()
        loadLatestEntryData()
        await loadInitialData()
    }

    // MARK: - Dashboard Metric Type Logic

    private func determineDashboardMetricType() async {
      let hasR4Scale = await checkForR4ScaleInPairedDevices()
      let hasR4Entries = await checkForR4ScaleEntries()

      if hasR4Scale || hasR4Entries {
          state.metrics.metricType = .twelve
          logger.log(level: .info, tag: "DashboardStore", message: "Dashboard metric type set to 12 (R4 scale detected)")
      } else {
          state.metrics.metricType = .four
          logger.log(level: .info, tag: "DashboardStore", message: "Dashboard metric type set to 4 (no R4 scale)")
      }
    }

    private func checkForR4ScaleInPairedDevices() async -> Bool {
        do {
            let devices = try await scaleService.getDevices()
            let r4Scales = devices.filter { device in
                let scaleType = ScaleTypeHelper.determineScaleType(for: device)
                return scaleType == .bluetoothR4
            }
            return !r4Scales.isEmpty
        } catch {
            logger.log(level: .error, tag: "DashboardStore", message: "Failed to check for R4 scales: \(error)")
            return false
        }
    }

    private func checkForR4ScaleEntries() async -> Bool {
        do {
            let entries = try await entryService.getAllEntries()
            let r4Entries = entries.filter { entry in
                if let source = entry.scaleEntry?.source {
                    return source.lowercased().contains("r4") ||
                           source.lowercased().contains("btwifi") ||
                           source.lowercased().contains("bluetooth/wifi")
                }
                return false
            }
            return !r4Entries.isEmpty
        } catch {
            logger.log(level: .error, tag: "DashboardStore", message: "Failed to check for R4 scale entries: \(error)")
            return false
        }
    }

    // MARK: - Data Loading Methods

    func loadLatestEntryData() {
        Task {
            do {
                guard let latestEntry = try await entryService.getLatestEntry() else { return }

                if let weight = latestEntry.scaleEntry?.weight {
                    state.data.latestWeightStored = weight
                }

                try await metricsManager.updateMetrics(with: latestEntry)

            } catch {
                logger.log(level: .error, tag: "DashboardStore", message: "Failed to load latest entry data: \(error)")
            }
        }
    }

    func loadGoalCardData() {
        Task {
            do {
                try await goalManager.loadGoalData()
                logger.log(level: .info, tag: "DashboardStore", message: "Goal card data loaded successfully")
            } catch {
                logger.log(level: .error, tag: "DashboardStore", message: "Failed to load goal card data: \(error)")
            }
        }
    }

    private func loadInitialData() async {
        do {
            try await dataManager.loadInitialData()
            logger.log(level: .info, tag: "DashboardStore", message: "Initial data loaded successfully")
        } catch {
            logger.log(level: .error, tag: "DashboardStore", message: "Failed to load initial data: \(error)")
        }
    }

    private func loadDashboardConfigurationFromAPI() async {
        do {
            try await metricsManager.loadMetricsFromAPI()
            try await streakManager.refreshStreakData()
            logger.log(level: .info, tag: "DashboardStore", message: "Dashboard configuration loaded from API")
        } catch {
            logger.log(level: .error, tag: "DashboardStore", message: "Failed to load dashboard configuration: \(error)")
        }
    }

    // MARK: - EntryServiceDelegate

    func entryAdded(_ entry: Entry) {
        Task {
            await onEntryAdded(entry)
        }
    }

    func entryUpdated(_ entry: Entry) {
        Task {
            await onEntryUpdated(entry)
        }
    }

    func entryDeleted(_ entry: Entry) {
        Task {
            await onEntryDeleted(entry)
        }
    }

    // MARK: - Entry Lifecycle Management

    internal func onEntryAdded(_ entry: Entry) async {
        do {
            try await dataManager.handleEntryAdded(entry)
            loadLatestEntryData()
            loadGoalCardData()
        } catch {
            logger.log(level: .error, tag: "DashboardStore", message: "Failed to handle entry added: \(error)")
        }
    }

    internal func onEntryUpdated(_ entry: Entry) async {
        do {
            try await dataManager.handleEntryUpdated(entry)
            loadLatestEntryData()
            loadGoalCardData()
        } catch {
            logger.log(level: .error, tag: "DashboardStore", message: "Failed to handle entry updated: \(error)")
        }
    }

    internal func onEntryDeleted(_ entry: Entry) async {
        do {
            try await dataManager.handleEntryDeleted(entry)
            loadLatestEntryData()
            loadGoalCardData()
        } catch {
            logger.log(level: .error, tag: "DashboardStore", message: "Failed to handle entry deleted: \(error)")
        }
    }

    // MARK: - UI Action Methods

    func toggleMetricRemovalInReorderedArray(at reorderedIndex: Int) {
        let metricsToShow = self.metricsToShow
        guard reorderedIndex < metricsToShow.count else { return }
        let metric = metricsToShow[reorderedIndex]
        guard let originalIndex = state.metrics.metrics.firstIndex(where: { $0.id == metric.id }) else { return }
        toggleMetricRemoval(at: originalIndex)
    }

    func isMetricRemovedInReorderedArray(at reorderedIndex: Int) -> Bool {
        let metricsToShow = self.metricsToShow
        guard reorderedIndex < metricsToShow.count else { return false }
        let metric = metricsToShow[reorderedIndex]
        guard let originalIndex = state.metrics.metrics.firstIndex(where: { $0.id == metric.id }) else { return false }
        return isMetricRemoved(at: originalIndex)
    }

    func toggleStreakRemovalInReorderedArray(at reorderedIndex: Int) {
        let streakItemsToShow = self.streakItemsToShow
        guard reorderedIndex < streakItemsToShow.count else { return }
        let streak = streakItemsToShow[reorderedIndex]
        guard let originalIndex = state.streak.streakItems.firstIndex(where: { $0.id == streak.id }) else { return }
        toggleStreakRemoval(at: originalIndex)
    }

    func isStreakRemovedInReorderedArray(at reorderedIndex: Int) -> Bool {
        let streakItemsToShow = self.streakItemsToShow
        guard reorderedIndex < streakItemsToShow.count else { return false }
        let streak = streakItemsToShow[reorderedIndex]
        guard let originalIndex = state.streak.streakItems.firstIndex(where: { $0.id == streak.id }) else { return false }
        return isStreakRemoved(at: originalIndex)
    }

    func toggleMetricRemoval(at index: Int) {
        guard index < state.metrics.metrics.count else { return }
        let metric = state.metrics.metrics[index]
        let isCurrentlyRemoved = isMetricRemoved(at: index)
        state.metrics.metrics.remove(at: index)
        if isCurrentlyRemoved {
            state.metrics.metrics.insert(metric, at: state.metrics.activeMetricsCount)
            state.metrics.activeMetricsCount += 1
        } else {
            state.metrics.metrics.append(metric)
            state.metrics.activeMetricsCount -= 1
        }
        state.ui.resetDragState()
    }

    func isMetricRemoved(at index: Int) -> Bool {
        guard index < state.metrics.metrics.count else { return false }
        return index >= state.metrics.activeMetricsCount
    }

    func toggleStreakRemoval(at index: Int) {
        guard index < state.streak.streakItems.count else { return }
        let item = state.streak.streakItems[index]
        let isCurrentlyRemoved = isStreakRemoved(at: index)
        state.streak.streakItems.remove(at: index)
        if isCurrentlyRemoved {
            state.streak.streakItems.insert(item, at: state.streak.activeStreakItemsCount)
            state.streak.activeStreakItemsCount += 1
        } else {
            state.streak.streakItems.append(item)
            state.streak.activeStreakItemsCount -= 1
        }
        state.ui.resetDragState()
    }

    func isStreakRemoved(at index: Int) -> Bool {
        guard index < state.streak.streakItems.count else { return false }
        return index >= state.streak.activeStreakItemsCount
    }

    func toggleGoalCardRemoval() {
        state.ui.isGoalCardRemoved.toggle()
    }

    func resetDragState() {
        state.ui.resetDragState()
    }

    func selectMetric(_ label: String) {
        if state.ui.selectedMetricLabel == label {
            state.ui.selectedMetricLabel = nil
        } else {
            state.ui.selectedMetricLabel = label
        }
    }

    func ensureLatestEntriesVisible() {
        guard let latestDate = continuousOperations.map(\.date).max() else { return }
        Task {
            await graphManager.updateScrollPosition(to: latestDate)
        }
    }

    func handleSettingsChange() {
        loadGoalCardData()
        objectWillChange.send()
    }

    func saveChanges() {
        state.ui.isLoading = true
        state.ui.loaderOverride = LoaderModel(text: lang.saving)

        state.ui.selectedMetricLabel = nil
        state.ui.resetDragState()

        Task {
            do {
                try await metricsManager.saveMetricsToAPI()

                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        self.state.ui.isLoading = false
                        self.state.ui.loaderOverride = nil
                        self.state.ui.isEditMode = false
                        self.state.ui.resetDragState()
                    }
                }
            } catch {
                logger.log(level: .error, tag: "DashboardStore", message: "Failed to save dashboard changes: \(error)")
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        self.state.ui.isLoading = false
                        self.state.ui.loaderOverride = nil
                        self.state.ui.isEditMode = false
                        self.state.ui.resetDragState()
                    }
                }
            }
        }
    }

    func resetDashboard() {
        state.ui.isLoading = true
        state.ui.loaderOverride = LoaderModel(text: lang.saving)

        state.ui.selectedMetricLabel = nil
        state.ui.isEditMode = false
        state.ui.resetDragState()

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            withAnimation(.easeInOut(duration: 0.3)) {
                self.state.ui.isLoading = false
                self.state.ui.loaderOverride = nil
                self.restoreOriginalMetricOrder()
                self.restoreOriginalStreakOrder()
                self.state.metrics.activeMetricsCount = self.originalMetrics.count
                self.state.streak.activeStreakItemsCount = self.originalStreakItems.count
                self.state.ui.isGoalCardRemoved = false

                Task {
                    do {
                        try await self.streakManager.refreshStreakData()
                        try await self.metricsManager.saveMetricsToAPI()
                    } catch {
                        self.logger.log(level: .error, tag: "DashboardStore", message: "Failed to save dashboard changes: \(error)")
                    }
                }

                self.state.ui.selectedMetricLabel = nil
                self.state.graph.clearSelection()
                self.state.ui.isEditMode = false
                self.state.ui.resetDragState()
                self.state.ui.gridLayoutId = UUID()
            }
        }
    }

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

    func updateSelectedPeriod(_ period: TimePeriod) {
        state.graph.selectedPeriod = period

        // Clear all selection states
        clearSelection()

        // Ensure chart shows the latest entries when switching periods
        ensureLatestEntriesVisible()

        // Update weight display for new period
        updateWeightDisplayForCurrentView()

        // Force Y-axis recalculation for new period
        recalculateYAxisForVisibleData()

        Task {
            await graphManager.updateSelectedPeriod(period)
        }
    }

    func handleChartSelection(at selectedDate: Date?) {
        // Only handle selection if not currently scrolling
        guard !state.graph.isScrolling else { return }

        // If no date selected, clear selection
        guard let selectedDate = selectedDate else {
            clearSelection()
            return
        }

        Task {
            await graphManager.handleChartSelection(at: selectedDate)
        }
    }

    func getVisibleOperations() -> [BathScaleWeightSummary] {
        return graphManager.getVisibleOperations(from: continuousOperations)
    }

    func getYAxisScale() -> YAxisScale {
        // Use visible operations for Y-axis calculation if available, otherwise use all operations
        let visibleOps = getVisibleOperations()
        let opsToUse = visibleOps.isEmpty ? continuousOperations : visibleOps

        return graphManager.getYAxisScale(
            from: opsToUse,
            goalWeight: goalWeightForDisplay,
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight,
            convertWeight: convertStoredWeightToDisplay,
            chartHeight: state.graph.chartHeight
        )
    }

    // MARK: - Helper Methods

    func convertStoredWeightToDisplay(_ storedWeight: Int) -> Double {
        let unit = accountService.activeAccount?.weightSettings?.weightUnit ?? .lb
        if unit == .kg {
            return ConversionTools.convertStoredToKg(storedWeight)
        } else {
            return ConversionTools.convertStoredToLbs(storedWeight)
        }
    }

    func formatWeightDisplayText(_ weight: Double?) -> String {
        guard let weight = weight else { return "0.0" }

        if isWeightlessModeEnabled {
            let prefix = weight >= 0 ? "+" : ""
            return String(format: "%@%.1f", prefix, weight)
        } else {
            return String(format: "%.1f", weight)
        }
    }

    func formatYAxisTickLabel(_ weight: Double) -> String {
        return String(format: "%.0f", weight)
    }

    func formatChartDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        switch state.graph.selectedPeriod {
        case .week, .month:
            formatter.dateFormat = "MMM d"
        case .year, .total:
            formatter.dateFormat = "MMM yyyy"
        }
        return formatter.string(from: date)
    }

    func formattedMetricValue(for metric: (preLabel: String?, value: String)) -> String {
        metric.preLabel.map { "\($0) \(metric.value)" } ?? metric.value
    }

    func createEntryForMetricInfo(metricLabel: String? = nil) -> Entry {
        return metricsManager.createEntryForMetricInfo(metricLabel: metricLabel)
    }

    func getBodyMetric(for metricLabel: String) -> BodyMetric {
        return metricsManager.getBodyMetric(for: metricLabel)
    }

    // MARK: - Private Helper Methods

    private func restoreOriginalMetricOrder() {
        state.metrics.metrics = originalMetrics.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }
        state.metrics.activeMetricsCount = originalMetrics.count
    }

    private func restoreOriginalStreakOrder() {
        state.streak.streakItems = originalStreakItems.map { MetricItem(value: $0.value, label: $0.label, unit: $0.unit, preLabel: $0.preLabel, icon: $0.icon) }
    }

    private func calculateWeightlessDisplay(_ operations: [BathScaleWeightSummary]) -> Double? {
        guard let anchorWeight = weightlessAnchorWeight else { return nil }
        let allOps = operations

        switch state.graph.selectedPeriod {
        case .week, .month:
            guard let latestWeight = allOps.last.map({ convertStoredWeightToDisplay(Int($0.weight)) }) else {
                return nil
            }
            return latestWeight - anchorWeight
        case .year, .total:
            let weights = allOps.map { convertStoredWeightToDisplay(Int($0.weight)) }
            guard !weights.isEmpty else { return nil }
            let averageWeight = weights.reduce(0, +) / Double(weights.count)
            return averageWeight - anchorWeight
        }
    }

    private func findOriginalSummary(for dto: BathScaleOperationDTO) -> BathScaleWeightSummary? {
        return continuousOperations.first { summary in
            summary.entryTimestamp == dto.entryTimestamp
        }
    }

    private func formatSelectedDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        switch state.graph.selectedPeriod {
        case .week, .month:
            formatter.dateFormat = "MMM d, yyyy"
        case .year, .total:
            formatter.dateFormat = "MMM yyyy"
        }
        return formatter.string(from: date)
    }

    private func formatDateRange(minDate: Date, maxDate: Date, for period: TimePeriod) -> String {
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

    private func fallbackTimeLabel() -> String {
        let now = Date()
        let calendar = Calendar.current

        switch state.graph.selectedPeriod {
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

    // MARK: - Private Helper Methods for Graph Logic

    /// Returns true if all entries are in the same era (same year)
    private func areEntriesInSameEra(_ summaries: [BathScaleWeightSummary]) -> Bool {
        guard !summaries.isEmpty else { return true }
        let years = Set(summaries.map { calendar.component(.year, from: $0.date) })
        return years.count == 1
    }

    /// Returns true if entries span less than a year
    private func doEntriesSpanLessThanYear(_ summaries: [BathScaleWeightSummary]) -> Bool {
        guard summaries.count >= 2 else { return true }
        let minDate = summaries.map { $0.date }.min() ?? Date()
        let maxDate = summaries.map { $0.date }.max() ?? Date()
        let timeInterval = maxDate.timeIntervalSince(minDate)
        let oneYearInSeconds: TimeInterval = 365 * 24 * 60 * 60
        return timeInterval < oneYearInSeconds
    }

    /// Returns true if we should repeat x-axis labels based on entry count thresholds
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

    // MARK: - Graph State Management

    /// Clear all selection states
    @MainActor
    func clearSelection() {
        state.graph.clearSelection()

        // Reset metrics to latest entry values
        resetMetricsToLatestEntry()
    }

    /// Initialize chart with latest data position
    @MainActor
    func initializeChart() {
        // Set initial scroll position to latest data
        if let latestDate = continuousOperations.map(\.date).max() {
            state.graph.xScrollPosition = latestDate
        }

        // Ensure weight display shows correct initial value
        updateWeightDisplayForCurrentView()
    }

    /// Handle scroll position changes with debouncing
    @MainActor
    func handleScrollPositionChange(_ newPosition: Date?) {
        guard let newPosition = newPosition else { return }

        // Update position immediately for smooth scrolling
        state.graph.xScrollPosition = newPosition

        // If not currently in a scroll gesture, this might be a programmatic change
        if !state.graph.isScrolling {
            updateWeightDisplayForCurrentView()
        }
    }

    /// Handle scroll start - clear selection and update state
    @MainActor
    func handleScrollStart() {
        guard !state.graph.isScrolling else { return }

        state.graph.isScrolling = true
        state.graph.hasDetectedScrollInCurrentGesture = true

        // Clear selection when scrolling starts
        clearSelection()
    }

    /// Enhanced scroll end handling with proper Y-axis recalculation and weight display update
    @MainActor
    func handleScrollEndOptimized() {
        // Cancel any existing timer
        state.graph.scrollEndTimer?.invalidate()

        // Set a timer to detect when scrolling has truly ended
        state.graph.scrollEndTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: false) { [weak self] _ in
            Task { @MainActor in
                guard let self = self else { return }

                // Update scrolling state
                self.state.graph.isScrolling = false
                self.state.graph.hasDetectedScrollInCurrentGesture = false

                // Update weight display to show average of visible region
                self.updateWeightDisplayForCurrentView()

                // Force Y-axis recalculation based on visible operations
                self.recalculateYAxisForVisibleData()

                // Reset metrics to show visible region average or latest entry
                self.updateMetricsForCurrentView()
            }
        }
    }

    /// Update weight display for current visible region
    @MainActor
    private func updateWeightDisplayForCurrentView() {
        // This will trigger displayWeight recalculation which now considers visible operations
        objectWillChange.send()
    }

    /// Recalculate Y-axis domain based on currently visible operations
    @MainActor
    private func recalculateYAxisForVisibleData() {
        // Force chart to recalculate Y-axis by triggering data change
        state.graph.dataChangeTrigger += 1

        // Force immediate UI update
        objectWillChange.send()
    }

    /// Update metrics to show values for current view (visible region or selected point)
    @MainActor
    private func updateMetricsForCurrentView() {
        if let selectedPoint = state.graph.selectedPoint {
            // If a point is selected, show its values
            updateMetricsWithSelectedPoint(selectedPoint)
        } else {
            // If no selection, show average of visible region or latest entry
            let visibleOps = getVisibleOperations()
            if !visibleOps.isEmpty && visibleOps.count > 1 {
                // Show average metrics for visible region
                updateMetricsWithAverageOfOperations(visibleOps)
            } else {
                // Fallback to latest entry
                resetMetricsToLatestEntry()
            }
        }
    }

    /// Update metrics with values from a selected point
    @MainActor
    private func updateMetricsWithSelectedPoint(_ point: BathScaleWeightSummary) {
        // Implementation for updating metrics with selected point values
        // This would update the metrics array with values from the selected point
        Task {
            do {
                // Create a temporary entry from the selected point for metric updates
                let tempEntry = BathScaleOperationDTO(from: point)
                if let entry = convertDTOToEntry(tempEntry) {
                    try await metricsManager.updateMetrics(with: entry)
                }
            } catch {
                logger.log(level: .error, tag: "DashboardStore", message: "Failed to update metrics with selected point: \(error)")
            }
        }
    }

    /// Update metrics with average values from a set of operations
    @MainActor
    private func updateMetricsWithAverageOfOperations(_ operations: [BathScaleWeightSummary]) {
        guard !operations.isEmpty else {
            resetMetricsToLatestEntry()
            return
        }

        // Calculate averages for each metric type
        let averageMetrics = calculateAverageMetrics(from: operations)

        // Update the metrics array with calculated averages
        updateMetricsWithCalculatedValues(averageMetrics)
    }

    /// Calculate average metrics from a collection of operations
    private func calculateAverageMetrics(from operations: [BathScaleWeightSummary]) -> [String: Double] {
        var averages: [String: Double] = [:]

        // Calculate BMI average
        let bmiValues = operations.compactMap { $0.bmi }
        if !bmiValues.isEmpty {
            averages[DashboardStrings.bmi] = bmiValues.reduce(0, +) / Double(bmiValues.count)
        }

        // Calculate Body Fat average
        let bodyFatValues = operations.compactMap { $0.bodyFat }
        if !bodyFatValues.isEmpty {
            averages[DashboardStrings.bodyFat] = bodyFatValues.reduce(0, +) / Double(bodyFatValues.count)
        }

        // Calculate Muscle Mass average
        let muscleValues = operations.compactMap { $0.muscleMass }
        if !muscleValues.isEmpty {
            averages[DashboardStrings.muscle] = muscleValues.reduce(0, +) / Double(muscleValues.count)
        }

        // Calculate Water average
        let waterValues = operations.compactMap { $0.water }
        if !waterValues.isEmpty {
            averages[DashboardStrings.water] = waterValues.reduce(0, +) / Double(waterValues.count)
        }

        // Calculate other metrics...
        let pulseValues = operations.compactMap { $0.pulse }
        if !pulseValues.isEmpty {
            averages[DashboardStrings.heartBpm] = Double(pulseValues.reduce(0, +)) / Double(pulseValues.count)
        }

        return averages
    }

    /// Update metrics array with calculated average values
    private func updateMetricsWithCalculatedValues(_ calculatedValues: [String: Double]) {
        for i in 0..<state.metrics.metrics.count {
            let metricLabel = state.metrics.metrics[i].label

            if let averageValue = calculatedValues[metricLabel] {
                let formattedValue = formatMetricValue(averageValue, for: metricLabel)
                state.metrics.metrics[i].value = formattedValue
            }
        }
    }

    /// Format metric value based on its type
    private func formatMetricValue(_ value: Double, for metricLabel: String) -> String {
        switch metricLabel {
        case DashboardStrings.bmi, DashboardStrings.bodyFat, DashboardStrings.muscle, DashboardStrings.water:
            return String(format: "%.1f", value)
        case DashboardStrings.heartBpm, DashboardStrings.visceralFat, DashboardStrings.metAge:
            return String(format: "%.0f", value)
        default:
            return String(format: "%.1f", value)
        }
    }

    /// Convert BathScaleOperationDTO to Entry for metric processing
    private func convertDTOToEntry(_ dto: BathScaleOperationDTO) -> Entry? {
        // This would need to be implemented based on your Entry model structure
        // For now, return nil to prevent compilation errors
        return nil
    }

    // MARK: - Memory Management

//    @MainActor
//    deinit {
//        cancellables.removeAll()
//        state.graph.scrollEndTimer?.invalidate()
//    }
}
