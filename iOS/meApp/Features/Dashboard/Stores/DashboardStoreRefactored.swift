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

    // MARK: - Constants
    let lang = LoaderStrings.self

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
        return metricsManager.getMetricGridColumns(for: state.metrics.metricType)
    }

    var metricsToShow: [MetricItem] {
        let result = metricsManager.getMetricsToShow(
            isEditMode: state.ui.isEditMode,
            metricType: state.metrics.metricType
        )

        return result
    }

    var streakColumns: [GridItem] {
        return streakManager.getStreakGridColumns()
    }

    var streakItemsToShow: [MetricItem] {
        let result = streakManager.getStreakItemsToShow(isEditMode: state.ui.isEditMode)

        return result
    }

    var isAnyItemBeingDragged: Bool {
        state.ui.isAnyItemBeingDragged
    }

    var allContentRemoved: Bool {
        metricsToShow.isEmpty && (!state.ui.isEditMode && state.ui.isGoalCardRemoved) && (!streakManager.shouldShowStreakGrid())
    }

    var shouldShowStreakGrid: Bool {
        streakManager.shouldShowStreakGrid()
    }

    // Delegate data operations to DataManager
    var continuousOperations: [BathScaleWeightSummary] {
        dataManager.getContinuousOperations(for: state.graph.selectedPeriod)
    }

    var visibleOperations: [BathScaleWeightSummary] {
        graphManager.getVisibleOperations(from: continuousOperations)
    }

    var hasAnyEntries: Bool {
        state.data.hasAnyEntries
    }

    // Delegate weightless mode to managers
    var isWeightlessModeEnabled: Bool {
        accountService.activeAccount?.weightlessSettings?.isWeightlessOn ?? false
    }

    var weightlessAnchorWeight: Double? {
        guard let weightlessWeight = accountService.activeAccount?.weightlessSettings?.weightlessWeight else {
            return nil
        }
        return goalManager.convertWeightToDisplay(Int(weightlessWeight))
    }

    // Delegate goal operations to GoalManager
    var goalWeightForDisplay: Double {
        return goalManager.getGoalWeightForDisplay(
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight
        )
    }

    var displayWeight: Double? {
        // If a point is selected, show its weight value
        if let selectedPoint = state.graph.selectedPoint {
            if isWeightlessModeEnabled {
                guard let anchorWeight = weightlessAnchorWeight else { return nil }
                let currentWeight = goalManager.convertWeightToDisplay(Int(selectedPoint.weight))
                return currentWeight - anchorWeight
            } else {
                return goalManager.convertWeightToDisplay(Int(selectedPoint.weight))
            }
        }

        // When no point is selected, show average of visible region if available
        let opsToUse = visibleOperations

        // Check if weightless mode is enabled
        if isWeightlessModeEnabled {
            return calculateWeightlessDisplay(opsToUse, anchorWeight: weightlessAnchorWeight)
        }

        // Calculate average of operations in visible region (or all if no visible region)
        let weights = opsToUse.map { goalManager.convertWeightToDisplay(Int($0.weight)) }
        guard !weights.isEmpty else { return nil }
        let averageWeight = weights.reduce(0, +) / Double(weights.count)

        return averageWeight
    }

    var weightLabel: String {
        guard !visibleOperations.isEmpty else {
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
            if let originalSummary = continuousOperations.first(where: { $0.entryTimestamp == selectedEntry.entryTimestamp }) {
                return formatSelectedDate(originalSummary.date)
            }
        }

        // Otherwise show the period range for visible data
        let opsToUse = visibleOperations
        guard let minDate = opsToUse.map(\.date).min(),
              let maxDate = opsToUse.map(\.date).max() else {
            return fallbackTimeLabel()
        }

        return formatDateRange(minDate: minDate, maxDate: maxDate, for: state.graph.selectedPeriod)
    }

    // Delegate metric operations to MetricsManager
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

    // Delegate chart data generation to GraphManager
    var chartSeriesData: [GraphSeries] {
        graphManager.generateChartData(
            from: continuousOperations,
            selectedMetric: state.ui.selectedMetricLabel,
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight,
            convertWeight: goalManager.convertWeightToDisplay
        )
    }

    /// Returns the average weight for the current visible or all operations
    @MainActor
    func getCurrentAverageWeight() -> Double {
        let visibleOps = visibleOperations
        let opsToUse = visibleOps.isEmpty ? visibleOperations : visibleOps

        let weightValues = opsToUse.map { summary -> Double in
            if isWeightlessModeEnabled {
                guard let anchorWeight = weightlessAnchorWeight else { return 0 }
                let currentWeight = goalManager.convertWeightToDisplay(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return goalManager.convertWeightToDisplay(Int(summary.weight))
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
        let result = !continuousOperations.isEmpty && visibleOperations.isEmpty
        return result
    }

    // Delegate time calculations to GraphManager
    func visibleDomainLength(for period: TimePeriod) -> TimeInterval {
        return graphManager.visibleDomainLength(for: period)
    }

    /// Updates visible data after scroll ends (forces UI update and logs average weight)
    func updateVisibleDataAfterScroll() {
        objectWillChange.send()
        let visibleOps = visibleOperations
        let opsToUse = visibleOps.isEmpty ? visibleOperations : visibleOps
        let weightValues = opsToUse.map { summary -> Double in
            if isWeightlessModeEnabled {
                guard let anchorWeight = weightlessAnchorWeight else { return 0 }
                let currentWeight = goalManager.convertWeightToDisplay(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return goalManager.convertWeightToDisplay(Int(summary.weight))
            }
        }
        if let averageWeight = weightValues.isEmpty ? nil : weightValues.reduce(0, +) / Double(weightValues.count) {
            logger.log(level: .info, tag: "DashboardStore", message: "updateVisibleDataAfterScroll - Average weight of visible operations: \(averageWeight)")
        }
       
    }

    // Delegate X-axis operations to GraphManager
    func xAxisValuesWithBuffer(for period: TimePeriod) -> [Date] {
        return graphManager.generateXAxisValues(for: period, from: continuousOperations)
    }

    func xLabelString(for date: Date, period: TimePeriod) -> String? {
        graphManager.formatXAxisLabel(for: date, period: period, operations: continuousOperations)
    }

    /// Selects an entry for the chart
    func selectEntry(_ entry: BathScaleWeightSummary?) {
        if let entry = entry {
            state.graph.selectedEntry = BathScaleOperationDTO(from: entry)
            state.graph.selectedWeight = goalManager.convertWeightToDisplay(Int(entry.weight))
        } else {
            state.graph.selectedEntry = nil
            state.graph.selectedWeight = nil
        }
        objectWillChange.send()
    }

    // Delegate metric operations to MetricsManager
    func resetMetricsToLatestEntry() {
        Task {
            do {
                guard let latestEntry = try await dataManager.getLatestEntry() else {
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
        // Determine dashboard metric type based on R4 scale presence
        await determineDashboardMetricType()
        
        // Load dashboard configuration from API first
        await loadDashboardConfigurationFromAPI()
        
        // Then load other data
        loadLatestEntryData()
        await loadInitialData()
    }

    // MARK: - Dashboard Metric Type Logic

    private func determineDashboardMetricType() async {
      let hasR4Scale = await checkForR4ScaleInPairedDevices()
      let hasR4Entries = await checkForR4ScaleEntries()

      logger.log(level: .info, tag: "DashboardStore", message: "R4 scale detection: hasR4Scale=\(hasR4Scale), hasR4Entries=\(hasR4Entries)")

      if hasR4Scale || hasR4Entries {
          state.metrics.metricType = .twelve
          // Also update the metrics manager state to ensure synchronization
          metricsManager.state.metricType = .twelve
          logger.log(level: .info, tag: "DashboardStore", message: "Dashboard metric type set to 12 (R4 scale detected) - Centralized: \(state.metrics.metricType), Manager: \(metricsManager.state.metricType)")
      } else {
          state.metrics.metricType = .four
          // Also update the metrics manager state to ensure synchronization
          metricsManager.state.metricType = .four
          logger.log(level: .info, tag: "DashboardStore", message: "Dashboard metric type set to 4 (no R4 scale) - Centralized: \(state.metrics.metricType), Manager: \(metricsManager.state.metricType)")
      }
    }

    private func checkForR4ScaleInPairedDevices() async -> Bool {
        do {
            let devices = try await scaleService.getDevices()
            
            let r4Scales = devices.filter { device in
                let scaleType = ScaleTypeHelper.determineScaleType(for: device)
                let isR4 = scaleType == .bluetoothR4
                return isR4
            }
            
            logger.log(level: .info, tag: "DashboardStore", message: "Found \(r4Scales.count) R4 scales")
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
                guard let latestEntry = try await dataManager.getLatestEntry() else { return }

                if let weight = latestEntry.scaleEntry?.weight {
                    state.data.latestWeightStored = weight
                }

                try await metricsManager.updateMetrics(with: latestEntry)

            } catch {
                logger.log(level: .error, tag: "DashboardStore", message: "Failed to load latest entry data: \(error)")
            }
        }
    }

    // Delegate goal loading to GoalManager
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

    // Delegate data loading to DataManager
    private func loadInitialData() async {
        do {
            try await dataManager.loadInitialData()
            logger.log(level: .info, tag: "DashboardStore", message: "Initial data loaded successfully")
        } catch {
            logger.log(level: .error, tag: "DashboardStore", message: "Failed to load initial data: \(error)")
        }
    }

    // Delegate configuration loading to respective managers
    private func loadDashboardConfigurationFromAPI() async {
        do {
            // Load dashboard metrics configuration from API
            try await metricsManager.loadMetricsFromAPI()
            
            // Refresh streak data with real values from API
            try await streakManager.refreshStreakData()
            
            logger.log(level: .info, tag: "DashboardStore", message: "Dashboard configuration loaded from API successfully")
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

    // Delegate entry lifecycle to DataManager
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

    // Delegate metric management to MetricsManager
    func toggleMetricRemovalInReorderedArray(at reorderedIndex: Int) {
        let metricsToShow = self.metricsToShow
        guard reorderedIndex < metricsToShow.count else { return }
        let metric = metricsToShow[reorderedIndex]
        guard let originalIndex = metricsManager.state.metrics.firstIndex(where: { $0.id == metric.id }) else { return }
        Task {
            try? await metricsManager.toggleMetricVisibility(at: originalIndex)
        }
    }

    func isMetricRemovedInReorderedArray(at reorderedIndex: Int) -> Bool {
        let metricsToShow = self.metricsToShow
        guard reorderedIndex < metricsToShow.count else { return false }
        let metric = metricsToShow[reorderedIndex]
        guard let originalIndex = metricsManager.state.metrics.firstIndex(where: { $0.id == metric.id }) else { return false }
        return originalIndex >= metricsManager.state.activeMetricsCount
    }

    // Delegate streak management to StreakManager
    func toggleStreakRemovalInReorderedArray(at reorderedIndex: Int) {
        let streakItemsToShow = self.streakItemsToShow
        guard reorderedIndex < streakItemsToShow.count else { return }
        let streak = streakItemsToShow[reorderedIndex]
        guard let originalIndex = state.streak.streakItems.firstIndex(where: { $0.id == streak.id }) else { return }
        Task {
            try? await streakManager.toggleStreakVisibility(at: originalIndex)
        }
    }

    func isStreakRemovedInReorderedArray(at reorderedIndex: Int) -> Bool {
        let streakItemsToShow = self.streakItemsToShow
        guard reorderedIndex < streakItemsToShow.count else { return false }
        let streak = streakItemsToShow[reorderedIndex]
        guard let originalIndex = state.streak.streakItems.firstIndex(where: { $0.id == streak.id }) else { return false }
        return streakManager.isStreakRemoved(at: originalIndex)
    }

    // Remove duplicate methods - now handled by managers

    func toggleGoalCardRemoval() {
        state.ui.isGoalCardRemoved.toggle()
    }

    func resetDragState() {
        state.ui.draggingMetric = nil
        state.ui.draggingStreak = nil
        state.ui.dropHoverId = nil

        state.ui.gridLayoutId = UUID()
    }

    func selectMetric(_ label: String) {
        if state.ui.selectedMetricLabel == label {
            state.ui.selectedMetricLabel = nil
        } else {
            state.ui.selectedMetricLabel = label
        }
    }

    // Delegate graph operations to GraphManager
    func ensureLatestEntriesVisible() {
        Task {
            await graphManager.ensureLatestEntriesVisible(from: continuousOperations)
        }
    }

    func handleSettingsChange() {
        loadGoalCardData()
        objectWillChange.send()
    }

    // Delegate save operations to MetricsManager
    func saveChanges() {
        state.ui.isLoading = true
        state.ui.loaderOverride = LoaderModel(text: lang.saving)

        state.ui.selectedMetricLabel = nil
        state.ui.resetDragState()

        Task {
            do {
                // Save dashboard metrics configuration to API
                // This sends the current dashboard metrics configuration to the server
                // Sends body metrics in the order they appear on the dashboard (from top to bottom)
                // Only includes body metrics as per API specification
                try await metricsManager.saveMetricsToAPI()

                logger.log(level: .info, tag: "DashboardStore", message: "Dashboard changes saved to API successfully")

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

                // Delegate reset operations to managers
                Task {
                    try? await self.metricsManager.resetMetricsToDefaults()
                    try? await self.streakManager.resetStreakData()
                    
                    // After resetting metrics to defaults, update them with latest data
                    // This ensures the values are not placeholder but actual data
                    if let latestEntry = try? await self.dataManager.getLatestEntry() {
                        try? await self.metricsManager.updateMetrics(with: latestEntry)
                    }
                    
                    // Save the reset configuration to API
                    try? await self.metricsManager.saveMetricsToAPI()
                }

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

    // Delegate chart selection to GraphManager
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
        return visibleOperations
    }

    // Delegate Y-axis calculations to GraphManager
    func getYAxisScale() -> YAxisScale {
        return graphManager.getYAxisScale(
            from: visibleOperations,
            goalWeight: goalWeightForDisplay,
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight,
            convertWeight: goalManager.convertWeightToDisplay,
            chartHeight: state.graph.chartHeight
        )
    }

    // MARK: - Helper Methods

    // Remove duplicate - use GoalManager method
    func convertStoredWeightToDisplay(_ storedWeight: Int) -> Double {
        return goalManager.convertWeightToDisplay(storedWeight)
    }

    // Delegate weight formatting to GoalManager
    func formatWeightDisplayText(_ weight: Double?) -> String {
        guard let weight = weight else { return "0.0" }
        return goalManager.formatWeightForDisplay(weight, isWeightlessMode: isWeightlessModeEnabled)
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

    // Delegate entry creation to MetricsManager
    func createEntryForMetricInfo(metricLabel: String? = nil) -> Entry {
        // Use the latest entry from data manager if available
        if let latestEntry = dataManager.getLatestEntrySync() {
            return latestEntry
        } else {
            // Fallback to creating a new entry with latest weight
            let latestWeight = dataManager.state.latestWeightStored
            return metricsManager.createEntryForMetricInfoSync(metricLabel: metricLabel, weight: latestWeight)
        }
    }

    // Async version for when we need the latest data
    func createEntryForMetricInfo(metricLabel: String? = nil) async -> Entry {
        return await metricsManager.createEntryForMetricInfo(metricLabel: metricLabel)
    }

    func getBodyMetric(for metricLabel: String) -> BodyMetric {
        return metricsManager.getBodyMetric(for: metricLabel)
    }

    // MARK: - Private Helper Methods

    // Remove - now handled by managers

    private func calculateWeightlessDisplay(_ operations: [BathScaleWeightSummary], anchorWeight: Double?) -> Double? {
        guard let anchorWeight = anchorWeight else { return nil }
        let allOps = operations

        switch state.graph.selectedPeriod {
        case .week, .month:
            guard let latestWeight = allOps.last.map({ goalManager.convertWeightToDisplay(Int($0.weight)) }) else {
                return nil
            }
            return latestWeight - anchorWeight
        case .year, .total:
            let weights = allOps.map { goalManager.convertWeightToDisplay(Int($0.weight)) }
            guard !weights.isEmpty else { return nil }
            let averageWeight = weights.reduce(0, +) / Double(weights.count)
            return averageWeight - anchorWeight
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

    // Remove graph helper methods - now in GraphManager

    // MARK: - Drag and Drop Bindings

    /// Binding for metrics array to enable live reordering during drag
    var metricsBinding: Binding<[MetricItem]> {
        Binding(
            get: { 
                return self.metricsManager.state.metrics 
            },
            set: { newValue in
                self.metricsManager.state.metrics = newValue
            }
        )
    }

    /// Binding for streak items array to enable live reordering during drag
    var streakItemsBinding: Binding<[MetricItem]> {
        Binding(
            get: { 
                return self.streakManager.state.streakItems 
            },
            set: { newValue in
                self.streakManager.state.streakItems = newValue
            }
        )
    }

    /// Binding for dragging metric state
    var draggingMetricBinding: Binding<MetricItem?> {
        Binding(
            get: { 
                return self.state.ui.draggingMetric 
            },
            set: { newValue in
                self.state.ui.draggingMetric = newValue
            }
        )
    }

    /// Binding for dragging streak state
    var draggingStreakBinding: Binding<MetricItem?> {
        Binding(
            get: { 
                return self.state.ui.draggingStreak 
            },
            set: { newValue in
                self.state.ui.draggingStreak = newValue
            }
        )
    }

    /// Binding for drop hover ID
    var dropHoverIdBinding: Binding<String?> {
        Binding(
            get: { 
                return self.state.ui.dropHoverId 
            },
            set: { newValue in
                self.state.ui.dropHoverId = newValue
            }
        )
    }

    // MARK: - Drag State Management

    /// Start dragging a metric item
    func startDraggingMetric(_ metric: MetricItem) {
        state.ui.draggingMetric = metric
    }

    /// Start dragging a streak item
    func startDraggingStreak(_ streak: MetricItem) {
        state.ui.draggingStreak = streak
    }

    /// Update drop target during drag
    func updateDropTarget(_ targetId: String?) {
        state.ui.dropHoverId = targetId
    }

    /// End dragging and clear drag state
    func endDragging() {
        state.ui.draggingMetric = nil
        state.ui.draggingStreak = nil
        state.ui.dropHoverId = nil
    }

    /// Handle drag end for metrics
    func handleMetricDragEnd() {
        endDragging()
        // Force UI update to reflect any reordering
        objectWillChange.send()
    }

    /// Handle drag end for streaks
    func handleStreakDragEnd() {
        endDragging()
        // Force UI update to reflect any reordering
        objectWillChange.send()
    }

    // MARK: - Reordering Methods

    /// Reorder metrics during drag
    func reorderMetrics(from source: IndexSet, to destination: Int) {
        metricsManager.state.metrics.move(fromOffsets: source, toOffset: destination)
        
        logger.log(level: .info, tag: "DashboardStore", message: "Reordered metrics from \(source) to \(destination)")
    }

    /// Reorder streak items during drag
    func reorderStreakItems(from source: IndexSet, to destination: Int) {
        streakManager.state.streakItems.move(fromOffsets: source, toOffset: destination)
        
        logger.log(level: .info, tag: "DashboardStore", message: "Reordered streak items from \(source) to \(destination)")
    }

    // MARK: - Graph State Management

    /// Clear all selection states
    @MainActor
    func clearSelection() {
        state.graph.clearSelection()

        // Reset metrics to latest entry values
        resetMetricsToLatestEntry()
    }

    // Delegate chart initialization to GraphManager
    @MainActor
    func initializeChart() {
        Task {
            await graphManager.ensureLatestEntriesVisible(from: continuousOperations)
            
            // Force UI update after scroll position is set
            objectWillChange.send()
        }
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
            Task {
                try? await metricsManager.updateMetrics(with: selectedPoint)
            }
        } else {
            // If no selection, show average of visible region or latest entry
            let visibleOps = visibleOperations
            if !visibleOps.isEmpty && visibleOps.count > 1 {
                // Show average metrics for visible region
                // For now, just reset to latest - could implement average later
                resetMetricsToLatestEntry()
            } else {
                // Fallback to latest entry
                resetMetricsToLatestEntry()
            }
        }
    }

    // Remove duplicate helper methods - now handled by managers

    // MARK: - UI State Management
    
    /// Handle metric long press interaction
    /// Updates selection state and creates entry for metric info display
    func handleMetricLongPress(for metricLabel: String, selectedEntry: Binding<Entry?>, selectedMetric: Binding<BodyMetric?>) {
        // Update selection state if needed
        if state.ui.selectedMetricLabel != metricLabel {
            selectMetric(metricLabel)
        }
        
        // Delegate to metrics manager for entry creation
        Task {
            await metricsManager.handleMetricLongPress(for: metricLabel, selectedEntry: selectedEntry, selectedMetric: selectedMetric)
        }
    }
    
    /// Handle selected metric info change
    /// Updates UI state and creates entry for the selected metric
    func handleSelectedMetricInfoChange(_ newValue: String?, selectedEntry: Binding<Entry?>, selectedMetric: Binding<BodyMetric?>) async {
        guard let label = newValue else { return }
        state.ui.selectedMetricLabel = label
        
        // Delegate to metrics manager for entry creation
        await metricsManager.handleSelectedMetricInfoChange(newValue, selectedEntry: selectedEntry, selectedMetric: selectedMetric)
    }
    
    /// Handle selected metric label change
    /// Clears selection state when metric label is cleared
    func handleSelectedMetricLabelChange(_ newValue: String?) {
        if newValue == nil {
            // Clear selection state
            // Note: This is handled by the UI layer since it manages the selectedEntry and selectedMetric state
        }
    }
    
    /// Handle selected entry change
    /// Clears metric selection when entry is cleared
    func handleSelectedEntryChange(_ newValue: Entry?) {
        if newValue == nil {
            state.ui.selectedMetricLabel = nil
        }
    }
    
    /// Handle metric info sheet dismiss
    /// Clears metric selection when sheet is dismissed
    func handleMetricInfoSheetDismiss(_ newValue: MetricInfoWrapper?) {
        if newValue == nil {
            state.ui.selectedMetricLabel = nil
        }
    }
    
    // MARK: - Lifecycle Methods
    
    /// Perform actions when dashboard appears
    /// Loads latest data, goal card, and ensures proper initialization
    func onAppearActions() {
        loadLatestEntryData()
        loadGoalCardData()
        // Handle any settings changes
        handleSettingsChange()
        // Ensure chart shows the latest entries by default
        Task {
            await graphManager.ensureLatestEntriesVisible(from: continuousOperations)
        }
        
        logger.log(level: .info, tag: "DashboardStore", message: "Dashboard onAppear actions completed")
    }

    // MARK: - Memory Management

//    @MainActor
//    deinit {
//        cancellables.removeAll()
//        state.graph.scrollEndTimer?.invalidate()
//    }
}
