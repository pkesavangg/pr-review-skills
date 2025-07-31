import SwiftUI
import SwiftData
import Combine
import Charts
import Foundation

/// Simplified DashboardStore focused on coordination between managers
/// Uses specialized managers for business logic while exposing centralized state for UI
@MainActor
class DashboardStore: ObservableObject {

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
    private var lastUserScrollTime: Date?

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
        setupSubscriptions()
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

    private func setupSubscriptions() {
        entryService.entrySaved
            .sink { [weak self] entry in
              self?.onEntryAdded(entry)
            }
            .store(in: &cancellables)

        entryService.entryDeleted
            .sink { [weak self] entry in
              self?.onEntryDeleted(entry)
            }
            .store(in: &cancellables)

        // Subscribe to weight unit changes
        accountService.$activeAccount
            .compactMap { $0?.weightSettings?.weightUnit }
            .removeDuplicates()
            .dropFirst() // Skip initial value
            .sink { [weak self] newWeightUnit in
                self?.logger.log(level: .info, tag: "DashboardStore", message: "Weight unit changed to: \(newWeightUnit.rawValue)")
                self?.handleSettingsChange()
            }
            .store(in: &cancellables)

        // Subscribe to weightless mode changes
        accountService.$activeAccount
            .compactMap { $0?.weightlessSettings?.isWeightlessOn }
            .removeDuplicates()
            .dropFirst() // Skip initial value
            .sink { [weak self] isWeightlessOn in
                self?.logger.log(level: .info, tag: "DashboardStore", message: "Weightless mode changed to: \(isWeightlessOn)")
                self?.handleSettingsChange()
            }
            .store(in: &cancellables)

        // Subscribe to weightless anchor weight changes
        accountService.$activeAccount
            .compactMap { $0?.weightlessSettings?.weightlessWeight }
            .removeDuplicates()
            .dropFirst() // Skip initial value
            .sink { [weak self] weightlessWeight in
                self?.logger.log(level: .info, tag: "DashboardStore", message: "Weightless anchor weight changed to: \(weightlessWeight)")
                self?.handleSettingsChange()
            }
            .store(in: &cancellables)

        // Subscribe to dashboard type changes
        accountService.$activeAccount
            .compactMap { $0?.dashboardSettings?.dashboardType }
            .removeDuplicates()
            .dropFirst() // Skip initial value
            .sink { [weak self] dashboardType in
                self?.handleDashboardTypeChange()
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
        return metricsManager.getMetricGridColumns(for: state.metrics.dashboardType)
    }

    var metricsToShow: [MetricItem] {
        return metricsManager.getMetricsToShow(
            isEditMode: state.ui.isEditMode,
            dashboardType: state.metrics.dashboardType
        )
    }

    var streakColumns: [GridItem] {
        return streakManager.getStreakGridColumns()
    }

    var streakItemsToShow: [MetricItem] {
        return streakManager.getStreakItemsToShow(isEditMode: state.ui.isEditMode)
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
        // Use cached operations with improved caching logic in graph manager
        let visible = graphManager.getVisibleOperations(from: continuousOperations)
        return visible
    }

        // Delegate chart data generation to GraphManager
    var chartSeriesData: [GraphSeries] {
        // Pass Y-axis domain to ensure metric normalization uses the same range
        return graphManager.generateChartDataWithYAxisDomain(
            from: continuousOperations,
            visibleOperations: visibleOperations,
            selectedMetric: state.ui.selectedMetricLabel,
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight,
            convertWeight: goalManager.convertWeightToDisplay,
            yAxisDomain: yAxisDomain
        )
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
            return graphManager.calculateWeightlessDisplay(opsToUse, anchorWeight: weightlessAnchorWeight, period: state.graph.selectedPeriod, convertWeight: goalManager.convertWeightToDisplay)
        }

        // Calculate average of operations in visible region (or all if no visible region)
        let weights = opsToUse.map { goalManager.convertWeightToDisplay(Int($0.weight)) }
        guard !weights.isEmpty else { return nil }
        let averageWeight = weights.reduce(0, +) / Double(weights.count)

        return averageWeight
    }

    var weightLabel: String {

        // If a point is selected, show its date
        if let selectedPoint = state.graph.selectedPoint {
            return graphManager.formatSelectedDate(selectedPoint.date, for: state.graph.selectedPeriod)
        }

        if let selectedEntry = state.graph.selectedEntry {
            if let date = selectedEntry.date {
                return graphManager.formatSelectedDate(date, for: state.graph.selectedPeriod)
            }
            if let originalSummary = continuousOperations.first(where: { $0.entryTimestamp == selectedEntry.entryTimestamp }) {
                return graphManager.formatSelectedDate(originalSummary.date, for: state.graph.selectedPeriod)
            }
        }

        if state.graph.selectedPeriod == .total {
            let minDate = continuousOperations.min(by: { $0.date < $1.date })?.date
            let maxDate = continuousOperations.max(by: { $0.date < $1.date })?.date
            if let minDate = minDate, let maxDate = maxDate {
                return graphManager.formatDateRange(minDate: minDate, maxDate: maxDate, for: state.graph.selectedPeriod)
            }
            return graphManager.fallbackTimeLabel(for: state.graph.selectedPeriod)
        }

        let lastScrollPosition = graphManager.state.xScrollPosition
        let minDate = lastScrollPosition
        let maxDate = lastScrollPosition.addingTimeInterval(graphManager.visibleDomainLength(for: state.graph.selectedPeriod))

        return graphManager.formatDateRange(minDate: minDate, maxDate: maxDate, for: state.graph.selectedPeriod)
    }

    // Delegate metric operations to MetricsManager
    var selectedBodyMetric: BodyMetric {
        guard let selectedLabel = state.ui.selectedMetricLabel else { return .weight }
        return metricsManager.getBodyMetric(for: selectedLabel)
    }

    var currentUnit: WeightUnit {
        accountService.activeAccount?.weightSettings?.weightUnit ?? .lb
    }

    var currentUnitString: String {
        currentUnit.rawValue
    }

    /// Returns the current weight unit as a string (e.g., "lbs" or "kg")
    var currentUnitText: String {
        return accountService.activeAccount?.weightSettings?.weightUnit?.rawValue ?? "lbs"
    }

    var currentWeightlessMode: Bool {
        accountService.activeAccount?.weightlessSettings?.isWeightlessOn ?? false
    }

    var weightDisplayLabel: String {
        if visibleOperations.isEmpty {
            return "no entries"
        }
        return goalManager.getWeightDisplayLabel(for: state.graph.selectedPeriod)
    }

    /// Returns the average weight for the current visible or all operations
    @MainActor
    func getCurrentAverageWeight() -> Double {
        let visibleOps = visibleOperations
        if visibleOps.isEmpty {
            return 0
        }
        let opsToUse = visibleOps

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
        return goalManager.getUnitText()
    }

    /// Returns true if there are entries but none in the current time period
    var hasEntriesButNoneInCurrentPeriod: Bool {
        return goalManager.hasEntriesButNoneInCurrentPeriod(continuousOperations: continuousOperations, visibleOperations: visibleOperations)
    }

    // Delegate time calculations to GraphManager
    func visibleDomainLength(for period: TimePeriod) -> TimeInterval {
        return graphManager.visibleDomainLength(for: period)
    }

    /// Updates visible data after scroll ends (forces UI update and logs average weight)
    func updateVisibleDataAfterScroll() {
        objectWillChange.send()
        let visibleOps = visibleOperations
        let opsToUse = visibleOps.isEmpty ? continuousOperations : visibleOps
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
        graphManager.generateVisibleXAxisValues(for: period, from: continuousOperations, scrollPosition: state.graph.xScrollPosition)
    }

    func xLabelString(for date: Date, period: TimePeriod) -> String? {
        graphManager.formatXAxisLabel(for: date, period: period, operations: continuousOperations)
    }

    /// Selects an entry for the chart
    func selectEntry(_ entry: BathScaleWeightSummary?) {
        metricsManager.selectEntry(entry, convertWeight: goalManager.convertWeightToDisplay) {
            self.objectWillChange.send()
        }
    }

    // Delegate metric operations to MetricsManager
    func resetMetricsToLatestEntry() {
        Task {
            await metricsManager.resetMetricsToLatestEntry {
                try await self.dataManager.getLatestEntry()
            }
        }
    }

    // MARK: - Dashboard Initialization

    private func initializeDashboard() async {
        // Determine dashboard type based on account dashboardType
        let dashboardType = determineDashboardTypeFromAccount()
        state.metrics.dashboardType = dashboardType
        metricsManager.updateDashboardType(dashboardType)

        // Force update dashboard type to 12 metrics if it's currently 4 metrics
        if dashboardType == .dashboard4 {
            await forceUpdateDashboardTypeTo12()
        }

        // Initialize data manager to set up bindings
        await initializeDataManager()

        // Load dashboard configuration from API
        await loadDashboardConfigurationFromAPI()

        // Load other data
        loadLatestEntryData()

        // Initialize chart - data will be available from ContentView loading
        initializeChart()

    }

    // MARK: - Dashboard Type Management

    /// Forces the dashboard type to be 12 metrics (3 columns) by updating the account settings
    private func forceUpdateDashboardTypeTo12() async {
        do {
            logger.log(level: .info, tag: "DashboardStore", message: "Forcing dashboard type to 12 metrics")

            // Update the account settings to use 12 metrics
            _ = try await accountService.updateDashboardType(type: .dashboard12)

            // Update the local state
            state.metrics.dashboardType = .dashboard12
            metricsManager.updateDashboardType(.dashboard12)

            logger.log(level: .info, tag: "DashboardStore", message: "Successfully updated dashboard type to 12 metrics")
        } catch {
            logger.log(level: .error, tag: "DashboardStore", message: "Failed to update dashboard type to 12 metrics: \(error)")
        }
    }

    /// Public method to manually switch to 12 metrics dashboard
    func switchTo12MetricsDashboard() {
        Task {
            await forceUpdateDashboardTypeTo12()

            // Force UI update
            await MainActor.run {
                self.objectWillChange.send()
            }
        }
    }

    // MARK: - Dashboard Type Logic

    /// Determines dashboard type based on account dashboardType
    private func determineDashboardTypeFromAccount() -> DashboardType {
        guard let account = accountService.activeAccount,
              let dashboardTypeString = account.dashboardSettings?.dashboardType else {
            logger.log(level: .info, tag: "DashboardStore", message: "No dashboard type found in account, defaulting to 4 metrics")
            return .dashboard4
        }

        // Convert string to DashboardType enum
        guard let dashboardType = DashboardType(rawValue: dashboardTypeString) else {
            return .dashboard4
        }

        logger.log(level: .info, tag: "DashboardStore", message: "Dashboard type set to \(dashboardType.rawValue) (from account dashboardType)")
        return dashboardType
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

    // Initialize data manager (no actual data loading - handled by ContentView)
    private func initializeDataManager() async {
        do {
            try await dataManager.loadInitialData()
            logger.log(level: .info, tag: "DashboardStore", message: "Data manager initialized successfully")
        } catch {
            logger.log(level: .error, tag: "DashboardStore", message: "Failed to initialize data manager: \(error)")
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

        // Delegate entry lifecycle to DataManager
        // MARK: - Entry Lifecycle Management
      internal func onEntryAdded(_ entry: Entry) {
        loadLatestEntryData()
        loadGoalCardData()
        self.updateYAxisCache()
      }

      internal func onEntryUpdated(_ entry: Entry) {
        loadLatestEntryData()
        loadGoalCardData()
        self.updateYAxisCache()
      }

      internal func onEntryDeleted(_ entry: Entry) {
        loadLatestEntryData()
        loadGoalCardData()
        self.updateYAxisCache()
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

    /// Toggles the edit mode state
    func toggleEditMode() {
        state.ui.isEditMode.toggle()
    }

    // Delegate graph operations to GraphManager
    func ensureLatestEntriesVisible() {
        // Don't reposition if user recently scrolled
        let recentlyScrolled = lastUserScrollTime.map { Date().timeIntervalSince($0) < 2.0 } ?? false

        guard !recentlyScrolled else {
            return
        }

        graphManager.ensureLatestEntriesVisible(from: continuousOperations)

    }

    func handleSettingsChange() {
        loadGoalCardData()
        objectWillChange.send()
        self.updateYAxisCache()
    }

    /// Handles dashboard type changes by updating the metric type and refreshing the UI
    func handleDashboardTypeChange() {
        let newDashboardType = determineDashboardTypeFromAccount()
        state.metrics.dashboardType = newDashboardType
        metricsManager.updateDashboardType(newDashboardType)

        // Force UI update to reflect the new metric type
        objectWillChange.send()

        logger.log(level: .info, tag: "DashboardStore", message: "Dashboard type changed, updated metric type to: \(newDashboardType)")
    }

    /// Handles unit changes by refreshing streak data and goal data
    func handleUnitChange() {
        Task {
            do {
                // Refresh streak data with new unit
                try await streakManager.refreshStreakDataForUnitChange()
                logger.log(level: .info, tag: "DashboardStore", message: "Refreshed streak data for unit change")

                // Refresh goal data with new unit
                try await goalManager.refreshGoalDataForUnitChange()
                logger.log(level: .info, tag: "DashboardStore", message: "Refreshed goal data for unit change")

                // Trigger UI update to refresh views with new unit
                await MainActor.run {
                    self.objectWillChange.send()
                }
            } catch {
                logger.log(level: .error, tag: "DashboardStore", message: "Failed to refresh data for unit change: \(error)")
            }
        }
    }

    // Delegate save operations to MetricsManager
    func saveChanges() {
        state.ui.isLoading = true
        state.ui.loaderOverride = LoaderModel(text: lang.saving)

        state.ui.selectedMetricLabel = nil
        state.ui.resetDragState()

        Task {
            do {
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
        // Reset chart initialization for new period
        state.ui.hasInitializedChart = false

        // Calculate optimal scroll position based on X-axis computation logic for segment change
        // This ensures the leftmost visible X-axis value aligns with computed X-axis ticks
        let optimalScrollPosition = graphManager.calculateOptimalScrollPosition(
            for: period,
            from: continuousOperations,
            showingLatest: true
        )
        graphManager.updateScrollPosition(to: optimalScrollPosition)
        // Delegate period update to graph manager
        graphManager.updateSelectedPeriod(period)

        self.forceCompleteRecalculationAfterScrollPosition()
        state.ui.hasInitializedChart = true
    }

    // Delegate chart selection to GraphManager
    func handleChartSelection(at selectedDate: Date?) async {
        // If no date selected, clear selection
        guard let selectedDate = selectedDate else {
            clearSelection()
            return
        }

        // Use the graph manager's complete chart selection method
        await graphManager.handleCompleteChartSelection(
            at: selectedDate,
            operations: continuousOperations,
            updateMetrics: { selectedPoint in
                try await self.metricsManager.updateMetrics(with: selectedPoint)
            },
            resetMetrics: {
                self.resetMetricsToLatestEntry()
            }
        )

        // Force UI update
        await MainActor.run {
            self.objectWillChange.send()
        }
    }

    func getVisibleOperations() -> [BathScaleWeightSummary] {
        return visibleOperations
    }

    // Delegate Y-axis calculations to GraphManager
    func getYAxisScale() -> YAxisScale {
        // For TOTAL period, use all data to ensure Y-axis reflects complete range
        let operationsForYAxis = state.graph.selectedPeriod == .total ? continuousOperations : visibleOperations

        return graphManager.getYAxisScale(
            from: operationsForYAxis,
            goalWeight: goalWeightForDisplay,
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight,
            convertWeight: goalManager.convertWeightToDisplay,
            chartHeight: state.graph.chartHeight
        )
    }

                    // Computed property for Y-axis domain that only reads cached value
    var yAxisDomain: ClosedRange<Double> {
        // Always use cached domain if available
        if let cachedDomain = state.graph.cachedYAxisDomain {
            return cachedDomain
        }

        // Fallback domain if no cache available (should rarely happen)
        return 0.0...100.0
    }

    // Computed property for Y-axis ticks that only reads cached value
    var yAxisTicks: [Double] {
        // Use cached Y-axis ticks if available
        if let cachedTicks = state.graph.cachedYAxisTicks {
            return cachedTicks
        }

        // Fallback ticks if no cache available
        return [0.0, 25.0, 50.0, 75.0, 100.0]
    }

        // Method to update Y-axis cache (called after domain recalculation)
    @MainActor
    func updateYAxisCache() {
        // NEVER update Y-axis domain during active scrolling - this is the root cause of axis jumping
        guard !state.graph.isScrolling else {
            logger.log(level: .debug, tag: "DashboardStore", message: "Blocking Y-axis update during scroll to prevent jumping")
            return
        }

        // For TOTAL period, use all data to ensure Y-axis reflects complete range
        let operationsForYAxis = state.graph.selectedPeriod == .total ? continuousOperations : visibleOperations

        graphManager.calculateAndCacheYAxisDomain(
            from: operationsForYAxis,
            goalWeight: goalWeightForDisplay,
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight,
            convertWeight: goalManager.convertWeightToDisplay,
            chartHeight: state.graph.chartHeight
        )

        logger.log(level: .debug, tag: "DashboardStore", message: "Y-axis domain updated after scroll end")
    }



    // MARK: - Helper Methods

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
        // Delegate to graph manager - do not manipulate graph state directly
        Task {
            // Clear selection through graph manager
            await graphManager.handleChartSelection(at: nil)

            // Reset metrics to latest entry values
            resetMetricsToLatestEntry()
        }
    }

    // Delegate chart initialization to GraphManager
    @MainActor
    func initializeChart() {
        // Don't initialize if already done or currently scrolling
        guard !state.ui.hasInitializedChart && !state.graph.isScrolling else {
            updateWeightDisplayForCurrentView()
            return
        }

        // Calculate optimal scroll position based on X-axis computation logic
        // This ensures the leftmost visible X-axis value aligns with computed X-axis ticks
        let optimalScrollPosition = graphManager.calculateOptimalScrollPosition(
            for: state.graph.selectedPeriod,
            from: continuousOperations,
            showingLatest: true
        )
        self.graphManager.updateScrollPosition(to: optimalScrollPosition)
        self.forceCompleteRecalculationAfterScrollPosition()
        state.ui.hasInitializedChart = true
    }

    /// Handle scroll position changes - delegate to graph manager
    @MainActor
    func handleScrollPositionChange(_ newPosition: Date?) {
      graphManager.handleScrollPositionChange(newPosition)
    }

    /// Handle scroll start - clear selection and update state
    @MainActor
    func handleScrollStart() {
        // Track user scroll time to prevent repositioning shortly after
        lastUserScrollTime = Date()
        graphManager.handleScrollStart()
    }

    /// Enhanced scroll end handling with proper Y-axis recalculation and weight display update
    @MainActor
    func handleScrollEndOptimized() {
        // Delegate to graph manager - do not manipulate graph state directly
        Task {
            await graphManager.handleScrollEnd()
        }

                // Update UI state after scroll ends - wait for graph manager's timer
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
            // Update Y-axis cache after domain recalculation
            self.updateYAxisCache()

            // Only update UI elements that don't trigger domain recalculation
            self.updateWeightDisplayForCurrentView()
            self.updateMetricsForCurrentView()
        }
    }

    @available(iOS 18.0, *)
    func handleScrollPhaseChange(to phase: ScrollPhase) async {
        await graphManager.handleScrollPhaseChange(phase)

        // When scroll ends (phase becomes .idle), perform the 3 operations
        if phase == .idle {
            // Update Y-axis cache after domain recalculation
            updateYAxisCache()

            // Only update UI elements that don't trigger domain recalculation
            updateWeightDisplayForCurrentView()
            updateMetricsForCurrentView()
        }
    }

    /// Update weight display for current visible region
    @MainActor
    private func updateWeightDisplayForCurrentView() {
        // This will trigger displayWeight recalculation which now considers visible operations
        objectWillChange.send()
    }

    /// Recalculate Y-axis domain based on currently visible operations
    /// This should only be called on scroll end or segment load
    @MainActor
    private func recalculateYAxisForVisibleData() {
        self.updateYAxisCache()
    }

    /// Update metrics to show values for current view (visible region or selected point)
    @MainActor
    private func updateMetricsForCurrentView() {
        graphManager.updateMetricsForCurrentView(
            selectedPoint: state.graph.selectedPoint,
            visibleOperations: visibleOperations,
            updateMetrics: { selectedPoint in
                try await self.metricsManager.updateMetrics(with: selectedPoint)
            },
            resetMetrics: {
                self.resetMetricsToLatestEntry()
            }
        )
    }

    // MARK: - UI State Management

    /// Handle metric long press interaction
    /// Updates selection state and creates entry for metric info display
    func handleMetricLongPress(for metricLabel: String, selectedEntry: Binding<Entry?>, selectedMetric: Binding<BodyMetric?>) {
        metricsManager.handleMetricLongPressWithUIState(
            for: metricLabel,
            selectedEntry: selectedEntry,
            selectedMetric: selectedMetric,
            updateSelectedMetric: { newValue in
                self.state.ui.selectedMetricLabel = newValue
            }
        )
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

    /// Forces complete recalculation after programmatic scroll position changes
    /// This ensures visible operations, Y-axis, and other dependent calculations are updated
    @MainActor
    private func forceCompleteRecalculationAfterScrollPosition() {
        // Force recalculation of visible operations
        graphManager.forceVisibleOperationsRecalculation()

        // Force Y-axis cache update with new visible operations
        updateYAxisCache()

        // Update weight display for current view
        updateWeightDisplayForCurrentView()

        // Force UI update
        objectWillChange.send()

        logger.log(level: .info, tag: "DashboardStore", message: "Forced complete recalculation after programmatic scroll position change")
    }

    /// Perform actions when dashboard appears
    /// Loads latest data, goal card, and ensures proper initialization
    func onAppearActions() {
        loadLatestEntryData()
        loadGoalCardData()
        // Handle any settings changes
        handleSettingsChange()
       // After positioning is complete, update Y-axis cache to ensure proper domain calculation
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            self.updateYAxisCache()
            self.objectWillChange.send()
        }

        logger.log(level: .info, tag: "DashboardStore", message: "Dashboard onAppear actions completed")
    }
}
