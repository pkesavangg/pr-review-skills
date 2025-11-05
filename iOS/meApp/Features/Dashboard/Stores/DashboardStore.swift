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
    // MARK: - Edit Session Snapshot
    private var snapshotMetrics: [MetricItem] = []
    private var snapshotActiveMetricsCount: Int = 0
    private var snapshotStreakItems: [MetricItem] = []
    private var snapshotActiveStreakItemsCount: Int = 0
    private var snapshotGoalCardRemoved: Bool = false
    private var snapshotGoalCardPosition: Int = 0
    private var snapshotStreakGridOrder: [String] = []
    private var snapshotRemovedMetrics: Set<String> = []
    private var snapshotRemovedStreaks: Set<String> = []
    private var hasEditSnapshot: Bool = false
    
    // MARK: - Constants
    let lang = LoaderStrings.self
    static let allowedNumericCharacters: CharacterSet = CharacterSet(charactersIn: "0123456789.-")
    // MARK: - Managers (Business Logic)
    public let metricsManager: DashboardMetricsManager
    let graphManager: DashboardGraphManager
    let goalManager: DashboardGoalManager
    public let streakManager: DashboardStreakManager
    private let dataManager: DashboardDataManager
    
    var shouldShowGoalCardOrStreaks: Bool {
            !state.ui.isGoalCardRemoved || !streakItemsToShow.isEmpty
        }
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
    
    func syncEntries() async {
        await entryService.syncAllEntriesWithRemote()
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
            .dropFirst()
            .sink { [weak self] newWeightUnit in
                self?.logger.log(level: .debug, tag: "DashboardStore", message: "Weight unit changed to: \(newWeightUnit.rawValue)")
                self?.handleSettingsChange()
            }
            .store(in: &cancellables)
        
        // Subscribe to weightless mode changes
        accountService.$activeAccount
            .compactMap { $0?.weightlessSettings?.isWeightlessOn }
            .removeDuplicates()
            .dropFirst()
            .sink { [weak self] isWeightlessOn in
                self?.logger.log(level: .debug, tag: "DashboardStore", message: "Weightless mode changed to: \(isWeightlessOn)")
                self?.handleSettingsChange()
            }
            .store(in: &cancellables)
        
        // Subscribe to weightless anchor weight changes
        accountService.$activeAccount
            .compactMap { $0?.weightlessSettings?.weightlessWeight }
            .removeDuplicates()
            .dropFirst()
            .sink { [weak self] weightlessWeight in
                self?.logger.log(level: .debug, tag: "DashboardStore", message: "Weightless anchor weight changed to: \(weightlessWeight)")
                self?.handleSettingsChange()
            }
            .store(in: &cancellables)
        
        // Subscribe to active account changes
        // This is used to reinitialize the dashboard state when the active account changes
        accountService.$activeAccount
            .map { $0?.accountId }
            .removeDuplicates()
            .dropFirst()
            .sink { [weak self] account in
                if account != nil {
                    self?.handleActiveAccountChanged()
                }
            }
            .store(in: &cancellables)

        accountService.$activeAccount
            .compactMap { $0?.goalSettings }
            .map { settings -> (Double?, Double?, GoalType?) in
                let gw: Double? = settings.goalWeight
                let iw: Double? = settings.initialWeight
                let gt: GoalType? = settings.goalType
                return (gw, iw, gt)
            }
            .removeDuplicates { (lhs: (Double?, Double?, GoalType?), rhs: (Double?, Double?, GoalType?)) -> Bool in
                lhs.0 == rhs.0 && lhs.1 == rhs.1 && lhs.2 == rhs.2
            }
            .dropFirst()
            .sink { [weak self] _ in
                self?.handleSettingsChange()
            }
            .store(in: &cancellables)
        
        accountService.$activeAccount
            .compactMap { $0?.dashboardSettings?.dashboardType }
            .removeDuplicates()
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
        return metricsManager.getMetricGridColumns(for: effectiveDashboardType)
    }
    
    var metricsToShow: [MetricItem] {
        let result = metricsManager.getMetricsToShow(
            isEditMode: state.ui.isEditMode,
            dashboardType: effectiveDashboardType,
            removedMetrics: state.ui.removedMetrics
        )
        return result
    }
    
    
    // Expose effective dashboard type based on the active account only
    var effectiveDashboardType: DashboardType {
        // Prefer the current in-memory type to avoid accidental downgrades when metrics are empty
        let result = state.metrics.dashboardType
        logger.log(level: .debug, tag: "DashboardStore", 
                  message: "effectiveDashboardType: \(result.rawValue)")
        return result
    }
    
    var streakColumns: [GridItem] {
        return streakManager.getStreakGridColumns()
    }
    
    var streakItemsToShow: [MetricItem] {
        let baseStreaks = streakManager.getStreakItemsToShow(isEditMode: state.ui.isEditMode)
        
        // In edit mode, show all streaks so users can toggle removal state
        // In non-edit mode, filter out removed streaks
        if state.ui.isEditMode {
            return baseStreaks
        } else {
            return baseStreaks.filter { !state.ui.removedStreaks.contains($0.label) }
        }
    }
    
    var isAnyItemBeingDragged: Bool {
        state.ui.isAnyItemBeingDragged
    }
    
    var allContentRemoved: Bool {
        // Check if all metrics are removed (when not in edit mode)
        let allMetricsRemoved = !state.ui.isEditMode && 
            metricsManager.state.metrics.allSatisfy { state.ui.removedMetrics.contains($0.label) }
        
        // Check if all streaks are removed (when not in edit mode)
        let allStreaksRemoved = !state.ui.isEditMode && 
            streakManager.state.streakItems.allSatisfy { state.ui.removedStreaks.contains($0.label) }
        
        return metricsToShow.isEmpty && 
               (!state.ui.isEditMode && state.ui.isGoalCardRemoved) && 
               (!streakManager.shouldShowStreakGrid()) &&
               allMetricsRemoved &&
               allStreaksRemoved
    }
    
    /// Checks if a goal is set for the active account
    /// Returns true if goalSettings exists and has valid goal type, initial weight, and goal weight
    var hasGoalSet: Bool {
            guard let account = accountService.activeAccount,
                  let goalSettings = account.goalSettings,
                  let goalType = goalSettings.goalType,
                  let initialWeight = goalSettings.initialWeight,
                  let goalWeight = goalSettings.goalWeight else {
                return false
            }
            return initialWeight > 0 && goalWeight > 0
        }
    
    var shouldShowStreakGrid: Bool {
        // Only show streak grid if there are visible (non-removed) streaks
        let visibleStreaks = streakItemsToShow.filter { !state.ui.removedStreaks.contains($0.label) }
        return !visibleStreaks.isEmpty
    }
    
    // Delegate data operations to DataManager
    var continuousOperations: [BathScaleWeightSummary] {
        dataManager.getContinuousOperations(for: state.graph.selectedPeriod)
    }
    
    var visibleOperations: [BathScaleWeightSummary] {
        // During scrolling, use cached result to prevent excessive graph manager calls
        if state.graph.isScrolling {
            let timeSinceLastCache = Date().timeIntervalSince(lastVisibleOperationsCacheTime)
            // Use cache for up to 100ms during scrolling to reduce call frequency
            if timeSinceLastCache < 0.1 && !cachedVisibleOperations.isEmpty {
                return cachedVisibleOperations
            }
        }
        
        // Get fresh result from graph manager (which has its own caching)
        let visible = graphManager.getVisibleOperations(from: continuousOperations)
        
        // Update cache
        cachedVisibleOperations = visible
        lastVisibleOperationsCacheTime = Date()
        
        return visible
    }
    
    // Delegate chart data generation to GraphManager with scroll performance optimization
    var chartSeriesData: [GraphSeries] {
        // Skip expensive recalculation during scroll end processing
        // This prevents the 3-4 redundant generations we saw in logs
        guard !isProcessingScrollEnd else {
            // Return cached data if available, otherwise empty array
            return cachedChartSeriesData ?? []
        }
        
        // Generate chart data and cache result
        let seriesData = graphManager.generateChartDataWithYAxisDomain(
            from: continuousOperations,
            visibleOperations: visibleOperations,
            selectedMetric: state.ui.selectedMetricLabel,
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight,
            convertWeight: goalManager.convertWeightToDisplay,
            yAxisDomain: yAxisDomain
        )
        
        // Cache the result for potential reuse
        cachedChartSeriesData = seriesData
        return seriesData
    }
    
    // Cache chart series data to prevent excessive recalculation
    private var cachedChartSeriesData: [GraphSeries]?
    
    // Cache visible operations to prevent excessive calls to graph manager during scroll
    private var cachedVisibleOperations: [BathScaleWeightSummary] = []
    private var lastVisibleOperationsCacheTime: Date = Date.distantPast
    
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
    var goalWeightForDisplay: Double? {
        return goalManager.getGoalWeightForDisplay(
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight
        )
    }
    
    var displayWeight: Double? {
        // If a concrete point is selected, ALWAYS show its exact weight value
        if let selectedPoint = state.graph.selectedPoint {
            if isWeightlessModeEnabled {
                guard let anchorWeight = weightlessAnchorWeight else { return nil }
                let currentWeight = goalManager.convertWeightToDisplay(Int(selectedPoint.weight))
                return currentWeight - anchorWeight
            } else {
                return goalManager.convertWeightToDisplay(Int(selectedPoint.weight))
            }
        }

        // Else, if a crosshair date is selected (can be on empty day), compute interpolated weight at that date
        if let selectedDate = state.graph.selectedXValue {
            return graphManager.interpolatedDisplayWeight(
                at: selectedDate,
                from: continuousOperations,
                isWeightlessMode: isWeightlessModeEnabled,
                anchorWeight: weightlessAnchorWeight,
                convertWeight: goalManager.convertWeightToDisplay
            )
        }
        
        // When no selection, show average of visible region if available
        let opsToUse = visibleOperations
        // If no visible operations, but we have data and we're not in total view, 
        // calculate interpolated average for the visible range
        if opsToUse.isEmpty && !continuousOperations.isEmpty && state.graph.selectedPeriod != .total {
            let interpolatedAverage = graphManager.calculateInterpolatedAverageForVisibleRange(
                from: continuousOperations,
                period: state.graph.selectedPeriod,
                isWeightlessMode: isWeightlessModeEnabled,
                anchorWeight: weightlessAnchorWeight,
                convertWeight: goalManager.convertWeightToDisplay
            )
            return interpolatedAverage
        }
        
        // Check if weightless mode is enabled
        if isWeightlessModeEnabled {
            return graphManager.calculateWeightlessDisplay(opsToUse, anchorWeight: weightlessAnchorWeight, period: state.graph.selectedPeriod, convertWeight: goalManager.convertWeightToDisplay)
        }
        
        // Calculate average of operations in visible region (or all if no visible region)
        let weights = opsToUse.map { goalManager.convertWeightToDisplay(Int($0.weight)) }
        guard !weights.isEmpty else { return nil }
        let averageWeight = weights.reduce(0, +) / Double(weights.count)
        
        // Apply same rounding logic as getCurrentAverageWeight() and formatWeightForDisplay()
        // Use a more robust rounding approach to handle floating-point precision issues
        let roundedAverage = (averageWeight * 100).rounded(.toNearestOrAwayFromZero) / 100
        return roundedAverage
    }
    
    var weightLabel: String {
        // Empty-state label override: when there are no account entries, show
        // current period labels derived from today's date.
        if !state.data.hasAnyEntries {
            return emptyStatePeriodLabel(for: state.graph.selectedPeriod)
        }

        if let label = selectionLabel() {
            return label
        }
        let period = state.graph.selectedPeriod
        let lastScrollPosition = graphManager.state.xScrollPosition
        switch period {
        case .total:
            return labelForTotalPeriod()
        case .year:
            return labelForYearGridlines()
        case .month:
            return labelForMonthGridlines()
        default:
            return defaultRangeLabel(for: period, lastScrollPosition: lastScrollPosition)
        }
    }

    /// Reinitialize dashboard state when the active account changes
    private func handleActiveAccountChanged() {
        // Clear caches and scrolling flags to ensure fresh computations
        clearAllCaches()
        state.ui.hasInitializedChart = false
        state.graph.clearSelection()

        // Kick off data loads for the new account
        loadLatestEntryData()
        loadGoalCardData()

        // Reset metrics/streak removal state to match the new account config
        syncRemovalStateFromMetricsManager()
        syncRemovalStateFromStreakManager()

        // Reposition graph to latest entries for the new account
        ensureLatestEntriesVisible()

        // Recalculate and cache Y-axis promptly for the new account's data
        updateYAxisCache(force: true)

        // Force UI refresh so graphs re-render with the new context immediately
        objectWillChange.send()
    }

    // MARK: - Empty-state period labels
    private func emptyStatePeriodLabel(for period: TimePeriod, today: Date = Date()) -> String {
        let cal = Calendar.current
        switch period {
        case .week:
            // Find the most recent Sunday (start of week), then end at Saturday
            let startOfDay = cal.startOfDay(for: today)
            let sundayStart = cal.nextDate(after: startOfDay,
                                           matching: DateComponents(weekday: 1),
                                           matchingPolicy: .nextTime,
                                           direction: .backward) ?? startOfDay
            guard let weekEnd = cal.date(byAdding: .day, value: 6, to: sundayStart) else {
                return DateTimeTools.formatter("MMM d, yyyy").string(from: today)
            }
            let sameYear = cal.isDate(sundayStart, equalTo: weekEnd, toGranularity: .year)
            if sameYear {
                let s = DateTimeTools.formatter("MMM d").string(from: sundayStart)
                let e = DateTimeTools.formatter("MMM d, yyyy").string(from: weekEnd)
                return "\(s) - \(e)"
            } else {
                let s = DateTimeTools.formatter("MMM d, yyyy").string(from: sundayStart)
                let e = DateTimeTools.formatter("MMM d, yyyy").string(from: weekEnd)
                return "\(s) - \(e)"
            }
        case .month:
            return DateTimeTools.formatter("MMM, yyyy").string(from: today)
        case .year:
            return DateTimeTools.formatter("yyyy").string(from: today)
        case .total:
            // Show current year for total in empty-state per spec
            return DateTimeTools.formatter("yyyy").string(from: today)
        }
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
        // Check if we have no visible operations but can calculate interpolated value
        let hasNoVisibleOps = visibleOperations.isEmpty
        let hasNoSelection = state.graph.selectedXValue == nil && state.graph.selectedPoint == nil
        let canInterpolate = !continuousOperations.isEmpty && state.graph.selectedPeriod != .total
        let hasInterpolatedWeight = displayWeight != nil
        
        if hasNoVisibleOps && hasNoSelection && (!canInterpolate || !hasInterpolatedWeight) {
            return "no entries"
        }
        
        // If a point is selected, override period label granularity
        if state.graph.selectedXValue != nil {
            switch state.graph.selectedPeriod {
            case .week, .month:
                return "day average"
            case .year, .total:
                return "month average"
            }
        }
        return goalManager.getWeightDisplayLabel(for: state.graph.selectedPeriod)
    }
    
    /// Returns the average weight for the current visible operations with proper rounding
    /// - Returns: The average weight rounded to 1 decimal place, or 0 if no operations are available
    @MainActor
    func getCurrentAverageWeight() -> Double {
        // Prefer strict on-screen visible operations; if empty, fall back to buffered visible range
        // This avoids returning 0.0 when the left boundary barely excludes entries (e.g., UTC vs local midnight)
        let strictVisibleOps = graphManager.getStrictVisibleOperations(from: continuousOperations)
        let opsToUse = strictVisibleOps.isEmpty ? visibleOperations : strictVisibleOps
        
        // Return 0 if no operations are available
        guard !opsToUse.isEmpty else { 
            return 0 
        }
        
        // Calculate weight values with proper error handling
        let weightValues = opsToUse.compactMap { summary -> Double? in
            do {
                if isWeightlessModeEnabled {
                    guard let anchorWeight = weightlessAnchorWeight else {
                        return nil 
                    }
                    let currentWeight = goalManager.convertWeightToDisplay(Int(summary.weight))
                    return currentWeight - anchorWeight
                } else {
                    return goalManager.convertWeightToDisplay(Int(summary.weight))
                }
            } catch {
                return nil
            }
        }
        
        // Return 0 if no valid weight values were calculated
        guard !weightValues.isEmpty else { 
            return 0 
        }
        
        // Calculate average with proper rounding to 1 decimal place
        let sum = weightValues.reduce(0, +)
        let average = sum / Double(weightValues.count)
        
        // Round to 1 decimal place using a more robust approach to handle floating-point precision
        let roundedAverage = (average * 100).rounded(.toNearestOrAwayFromZero) / 100
        return roundedAverage
    }
    
    /// Returns the current weight unit as a string (e.g., "lbs" or "kg")
    var unitText: String {
        return goalManager.getUnitText()
    }
    
    /// Returns the dynamic unit label for the primary weight display.
    /// - Notes:
    ///   - For kg: always "kg".
    ///   - For lb: singular/plural depends on displayed magnitude (after rounding like the weight text):
    ///       - 0          -> "lbs"
    ///       - (0, 1)     -> "lb"
    ///       - 1          -> "lb"
    ///       - (> 1)      -> "lbs"
    var displayUnitText: String {
        let unit: WeightUnit = accountService.activeAccount?.weightSettings?.weightUnit ?? .lb
        let displayValue = displayWeight ?? getCurrentAverageWeight()
        return WeightValueConvertor.unitForDisplay(value: displayValue, unit: unit)
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
            logger.log(level: .debug, tag: "DashboardStore", message: "updateVisibleDataAfterScroll - Average weight of visible operations: \(averageWeight)")
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
        
        // Initialize data manager to set up bindings
        await initializeDataManager()
        
        // Load dashboard configuration from API
        await loadDashboardConfigurationFromAPI()
        
        // Ensure removal state is synced after API loading
        syncRemovalStateFromMetricsManager()
        syncRemovalStateFromStreakManager()

        // Load other data
        loadLatestEntryData()
        
        // Initialize chart - data will be available from ContentView loading
        initializeChart()
    }
    
    // MARK: - Dashboard Type Management   

    
    // MARK: - Dashboard Type Logic
    
    /// Determines dashboard type based on account dashboardType
    private func determineDashboardTypeFromAccount() -> DashboardType {
        guard let account = accountService.activeAccount,
              let dashboardTypeString = account.dashboardSettings?.dashboardType else {
            return .dashboard12
        }
        
        switch dashboardTypeString {
        case "dashboard4":
            return .dashboard4
        case "dashboard12":
            return .dashboard12
        default:
            return .dashboard12
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
                
                // Instead of always updating with latest entry, preserve current selection state
                // and update metrics appropriately for the current view
                await MainActor.run {
                    self.updateMetricsForCurrentView()
                }
                
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
    func loadDashboardConfigurationFromAPI() async {
        do {
            // Load dashboard metrics configuration from API
            try await metricsManager.loadMetricsFromAPI()
            
            // Sync the dashboard type from metrics manager to store state
            await MainActor.run {
                state.metrics.dashboardType = metricsManager.state.dashboardType
            }
            
            // Refresh streak data with real values from API
            try await streakManager.refreshStreakData()
            
            // Mark loading as complete
            await MainActor.run {
                objectWillChange.send()
            }
            
            logger.log(level: .info, tag: "DashboardStore", message: "Dashboard configuration loaded from API successfully")
        } catch {
            // Even on error, update UI state
            await MainActor.run {
                objectWillChange.send()
            }
            logger.log(level: .error, tag: "DashboardStore", message: "Failed to load dashboard configuration from API: \(error)")
        }
    }

    // MARK: - View Helpers moved from DashboardScreen
    func reloadDashboardConfiguration(fullRefresh: Bool = false, updateMetrics: Bool = false) async {
        await loadDashboardConfigurationFromAPI()
        if updateMetrics {
            self.updateMetricsForCurrentView()
        }
        await MainActor.run {
            self.objectWillChange.send()
            if fullRefresh { self.refreshDashboardState() }
        }
    }

    func refreshAll() async {
        await syncEntries()
        onAppearActions()
    }
    
    /// Syncs the removal state from the metrics manager to the UI state
    func syncRemovalStateFromMetricsManager() {
        // Get the current metrics from the manager
        let currentMetrics = metricsManager.state.metrics
        let activeCount = metricsManager.state.activeMetricsCount
        
        // Clear existing removal state
        state.ui.removedMetrics.removeAll()
        
        // Ensure activeCount doesn't exceed currentMetrics.count to prevent range crash
        let safeActiveCount = min(activeCount, currentMetrics.count)
        
        // Mark metrics beyond the active count as removed
        for i in safeActiveCount..<currentMetrics.count {
            state.ui.removedMetrics.insert(currentMetrics[i].label)
        }
    }
    
    /// Syncs the removal state from the streak manager to the UI state
    func syncRemovalStateFromStreakManager() {
        // Get the current streak items from the manager
        let currentStreakItems = streakManager.state.streakItems
        let activeCount = streakManager.state.activeStreakItemsCount
        
        // Clear existing removal state
        state.ui.removedStreaks.removeAll()
        
        // Ensure activeCount doesn't exceed currentStreakItems.count to prevent range crash
        let safeActiveCount = min(activeCount, currentStreakItems.count)
        
        // Mark streak items beyond the active count as removed
        for i in safeActiveCount..<currentStreakItems.count {
            state.ui.removedStreaks.insert(currentStreakItems[i].label)
        }
    }
    
    // Delegate entry lifecycle to DataManager
    // MARK: - Entry Lifecycle Management
    internal func onEntryAdded(_ entry: Entry) {
        handleEntryLifecycleChange()
    }
    
    internal func onEntryUpdated(_ entry: Entry) {
        handleEntryLifecycleChange()
    }
    
    internal func onEntryDeleted(_ entry: Entry) {
        handleEntryLifecycleChange()
    }

    private func handleEntryLifecycleChange() {
        // EntryService handles incremental summaries; update dashboard state/UI consistently
        DispatchQueue.main.async {
            self.loadLatestEntryData()
            self.loadGoalCardData()
            
            // Clear caches to force recalculation
            self.cachedChartSeriesData = nil
            self.cachedVisibleOperations = []
            self.lastVisibleOperationsCacheTime = Date.distantPast
            
            // Force full recomputation of visible operations, Y-axis, and weight display
            self.forceCompleteRecalculationAfterScrollPosition()
            
            // Also schedule a follow-up domain recalc after brief propagation
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                self.updateYAxisCache()
            }
        }
    }

    // MARK: - UI Action Methods

    func toggleMetricRemovalInReorderedArray(at reorderedIndex: Int) {
        let metricsToShow = self.metricsToShow
        guard reorderedIndex < metricsToShow.count else { return }
        let metric = metricsToShow[reorderedIndex]
        guard let originalIndex = metricsManager.state.metrics.firstIndex(where: { $0.id == metric.id }) else { return }
        
        // Call the underlying manager to actually reorder the array
        Task {
            try? await metricsManager.toggleMetricVisibility(at: originalIndex)
            
            // Sync the UI state with the metrics manager after the change
            await MainActor.run {
                self.syncRemovalStateFromMetricsManager()
            }
        }
        
        // Update the UI state for consistency
        let isCurrentlyRemoved = originalIndex >= metricsManager.state.activeMetricsCount
        if isCurrentlyRemoved {
            // Metric is being added back - remove from removed set
            state.ui.removedMetrics.remove(metric.label)
        } else {
            // Metric is being removed - add to removed set
            state.ui.removedMetrics.insert(metric.label)
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
        
        // Call the underlying manager to actually reorder the array
        Task {
            try? await streakManager.toggleStreakVisibility(at: originalIndex)
            
            // Sync the UI state with the streak manager after the change
            await MainActor.run {
                self.syncRemovalStateFromStreakManager()
            }
        }
        
        // Update the UI state for consistency
        let isCurrentlyRemoved = originalIndex >= streakManager.state.activeStreakItemsCount
        if isCurrentlyRemoved {
            // Streak is being added back - remove from removed set
            state.ui.removedStreaks.remove(streak.label)
        } else {
            // Streak is being removed - add to removed set
            state.ui.removedStreaks.insert(streak.label)
        }
    }
    
    func isStreakRemovedInReorderedArray(at reorderedIndex: Int) -> Bool {
        let streakItemsToShow = self.streakItemsToShow
        guard reorderedIndex < streakItemsToShow.count else { return false }
        let streak = streakItemsToShow[reorderedIndex]
        guard let originalIndex = state.streak.streakItems.firstIndex(where: { $0.id == streak.id }) else { return false }
        return streakManager.isStreakRemoved(at: originalIndex)
    }
    
    // MARK: - Removal Status Methods for UIKit Views
    
    /// Returns true if the metric with the given label is currently removed
    func isMetricRemoved(_ metricLabel: String) -> Bool {
        // Check if the metric is in the removed metrics set
        return state.ui.removedMetrics.contains(metricLabel)
    }
    
    /// Returns true if the streak with the given label is currently removed
    func isStreakRemoved(_ streakLabel: String) -> Bool {
        // Check if the streak is in the removed streaks set
        return state.ui.removedStreaks.contains(streakLabel)
    }
    
    // MARK: - Toggle Removal Methods for UIKit Views
    
    /// Toggles the removal state of a metric by its label
    func toggleMetricRemoval(_ metricLabel: String) {
        // Find the metric in the current metrics array
        if let metricIndex = metricsManager.state.metrics.firstIndex(where: { $0.label == metricLabel }) {
            // Call the underlying manager to actually reorder the array
            Task {
                try? await metricsManager.toggleMetricVisibility(at: metricIndex)
                
                // Sync the UI state with the metrics manager after the change
                await MainActor.run {
                    self.syncRemovalStateFromMetricsManager()
                }
            }
        }
        
        // Update the UI state
        if state.ui.removedMetrics.contains(metricLabel) {
            state.ui.removedMetrics.remove(metricLabel)
        } else {
            state.ui.removedMetrics.insert(metricLabel)
        }
    }
    
    /// Toggles the removal state of a streak by its label
    func toggleStreakRemoval(_ streakLabel: String) {
        // Find the streak in the current streak items array
        if let streakIndex = streakManager.state.streakItems.firstIndex(where: { $0.label == streakLabel }) {
            // Call the underlying manager to actually reorder the array
            Task {
                try? await streakManager.toggleStreakVisibility(at: streakIndex)
                
                // Sync the UI state with the streak manager after the change
                await MainActor.run {
                    self.syncRemovalStateFromStreakManager()
                    // Validate goal card position after streak removal
                    self.validateGoalCardPosition()
                }
            }
        }
        
        // Update the UI state
        if state.ui.removedStreaks.contains(streakLabel) {
            state.ui.removedStreaks.remove(streakLabel)
        } else {
            state.ui.removedStreaks.insert(streakLabel)
        }
        
        // Validate goal card position immediately
        validateGoalCardPosition()
    }
    
    func toggleGoalCardRemoval() {
        state.ui.isGoalCardRemoved.toggle()
    }
    
    /// Updates the goal card position in the grid (like a large widget)
    /// - Parameter newPosition: The new position after the divider (0 = first position)
    func updateGoalCardPosition(_ newPosition: Int) {
        let maxPosition = streakItemsToShow.count // Goal card can be at the end
        var clampedPosition = max(0, min(newPosition, maxPosition))
        if state.ui.removedStreaks.isEmpty {
            let columns = DevicePlatform.isTablet ? 4 : 2
            clampedPosition = (clampedPosition / columns) * columns
        }
        
        if state.ui.goalCardPosition != clampedPosition {
            state.ui.goalCardPosition = clampedPosition
            logger.log(level: .debug, tag: "DashboardStore", message: "Goal card position updated to: \(clampedPosition)")
        }
    }
    
    /// Ensures goal card position is valid and snaps to row-starts when all streaks are present
    func validateGoalCardPosition() {
        let maxPosition = streakItemsToShow.count
        
        // Basic clamping
        if state.ui.goalCardPosition > maxPosition {
            // Only clamp if position is way beyond reasonable bounds
            state.ui.goalCardPosition = maxPosition
            logger.log(level: .debug, tag: "DashboardStore", message: "Goal card position clamped to: \(maxPosition) due to streak removal")
        }
        
        // Additional validation: ensure goal card position is never negative
        if state.ui.goalCardPosition < 0 {
            state.ui.goalCardPosition = 0
            logger.log(level: .debug, tag: "DashboardStore", message: "Goal card position clamped to 0 due to negative value")
        }

        // When no streaks are removed (all present), snap to row start to keep layout valid
        if state.ui.removedStreaks.isEmpty {
            let columns = DevicePlatform.isTablet ? 4 : 2
            let snapped = (state.ui.goalCardPosition / columns) * columns
            if snapped != state.ui.goalCardPosition {
                state.ui.goalCardPosition = snapped
            }
        }
        
        let hasRemovedStreaks = !state.ui.removedStreaks.isEmpty
        logger.log(level: .debug, tag: "DashboardStore", message: "Goal card position validated: \(state.ui.goalCardPosition), maxPosition: \(maxPosition), streakCount: \(streakItemsToShow.count), hasRemovedStreaks: \(hasRemovedStreaks), isEditMode: \(state.ui.isEditMode)")
    }
    
    func resetDragState() {
        state.ui.draggingMetric = nil
        state.ui.draggingStreak = nil
        state.ui.dropHoverId = nil
        state.ui.isGoalCardBeingDragged = false
    }
    
    func resetGridLayout() {
        resetDragState()
        state.ui.gridLayoutId = UUID()
    }
    
    /// Restarts wiggle animations for all visible cells when app becomes active from background
    func restartWiggleAnimations() {
        // Clear the interval cache to ensure fresh randomization
        UIView.clearWiggleIntervalCache()
        
        resetGridLayout()
        logger.log(level: .debug, tag: "DashboardStore", message: "Restarting wiggle animations after app became active")
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
        if !state.ui.isEditMode {
            // Entering edit mode - begin edit session
            beginEdit()
            state.ui.isEditMode = true
        } else {
            // Already in edit mode - reset current edit session and start fresh
            resetEditSession()
        }
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
        Task {
            do {
                try await self.goalManager.loadGoalData()
                self.logger.log(level: .debug, tag: "DashboardStore", message: "Goal data reloaded after settings change")
            } catch {
                self.logger.log(level: .error, tag: "DashboardStore", message: "Failed to reload goal data after settings change: \(error)")
            }
            
            await MainActor.run {
                // Sync removal state to ensure consistency
                self.syncRemovalStateFromMetricsManager()
                self.syncRemovalStateFromStreakManager()
                
                self.updateYAxisCache()
                self.objectWillChange.send()
            }
        }
    }
    
    /// Handles dashboard type changes by updating the metric type and refreshing the UI
    func handleDashboardTypeChange() {
        let newDashboardType = determineDashboardTypeFromAccount()
        state.metrics.dashboardType = newDashboardType
        metricsManager.updateDashboardType(newDashboardType)
        
        // Force UI update to reflect the new metric type
        objectWillChange.send()
        
        logger.log(level: .debug, tag: "DashboardStore", message: "Dashboard type changed, updated metric type to: \(newDashboardType)")
    }
    
    /// Handles unit changes by refreshing streak data and goal data
    func handleUnitChange() {
        Task {
            do {
                // Refresh streak data with new unit
                try await streakManager.refreshStreakDataForUnitChange()
                logger.log(level: .debug, tag: "DashboardStore", message: "Refreshed streak data for unit change")
                
                // Refresh goal data with new unit
                try await goalManager.refreshGoalDataForUnitChange()
                logger.log(level: .debug, tag: "DashboardStore", message: "Refreshed goal data for unit change")
                
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
        state.ui.selectedMetricLabel = nil
        state.ui.resetDragState()

        notificationService.showLoader(LoaderModel(text: lang.saving))
        Task {
            defer { notificationService.dismissLoader() }
            do {
                try await metricsManager.saveMetricsToAPI()
                logger.log(level: .info, tag: "DashboardStore", message: "Dashboard changes saved to API successfully")
                commonPostSaveUIReset()
            } catch {
                logger.log(level: .error, tag: "DashboardStore", message: "Failed to save dashboard changes: \(error)")
                commonPostSaveUIReset()
            }
        }
    }

    private func commonPostSaveUIReset() {
        withAnimation(.easeInOut(duration: 0.3)) {
            self.state.ui.isEditMode = false
            self.state.ui.resetDragState()
            self.state.ui.selectedMetricLabel = nil
            self.hasEditSnapshot = false
        }
    }
    
    func resetDashboard() {
        performDashboardResetFlow()
    }
    
    /// Resets the saved grid order to restore default order
    private func resetGridOrder() {
        state.ui.streakGridOrder = []
        state.ui.goalCardPosition = 0
        logger.log(level: .debug, tag: "DashboardStore", message: "Reset grid order to default")
    }
    
    /// Enhanced reset that properly restores removed items and reverses reordering
    func resetDashboardEnhanced() {
        performDashboardResetFlow()
    }

    private func performDashboardResetFlow() {
        state.ui.isLoading = true
        state.ui.loaderOverride = LoaderModel(text: lang.saving)
        // Ensure loader is visible via global notification system
        notificationService.showLoader(LoaderModel(text: lang.saving))
        
        state.ui.selectedMetricLabel = nil
        state.ui.isEditMode = false
        state.ui.resetDragState()
        
        // Reset the saved order to restore default order
        resetGridOrder()
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            withAnimation(.easeInOut(duration: 0.3)) {
                self.state.ui.isLoading = false
                self.state.ui.loaderOverride = nil
                self.notificationService.dismissLoader()
                
                // Delegate reset operations to managers
                Task {
                    try? await self.metricsManager.resetMetricsToDefaults()
                    try? await self.streakManager.resetStreakData()
                    
                    // Reset metrics to their original body metrics order
                    self.metricsManager.resetOrderToDefault()
                    
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
                        
                        // Sync the removal state from both managers after reset
                        await MainActor.run {
                            self.syncRemovalStateFromMetricsManager()
                            self.syncRemovalStateFromStreakManager()
                        }
                    } catch {
                        self.logger.log(level: .error, tag: "DashboardStore", message: "Failed to save dashboard changes: \(error)")
                    }
                }
                
                self.state.ui.selectedMetricLabel = nil
                self.state.graph.clearSelection()
                self.state.ui.isEditMode = false
                self.state.ui.resetDragState()
                self.hasEditSnapshot = false
                
                // Force UI update to reflect the reset state
                self.objectWillChange.send()
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
                    self.resetDashboardEnhanced()
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    func updateSelectedPeriod(_ period: TimePeriod) {
        // Reset chart initialization for new period
        state.ui.hasInitializedChart = false
        
        // Clear all caches when period changes
        clearAllCaches()
        
        // End any scrolling immediately so new period computes fresh domain/x-axis
        graphManager.endScrollingImmediately()
        
        // Calculate optimal scroll position based on X-axis computation logic for segment change
        // This ensures the leftmost visible X-axis value aligns with computed X-axis ticks
        let optimalScrollPosition = graphManager.calculateOptimalScrollPosition(
            for: period,
            from: continuousOperations,
            showingLatest: true
        )
        graphManager.updateScrollPosition(to: optimalScrollPosition)
        // Delegate period update to graph manager (this will clear chart data cache)
        graphManager.updateSelectedPeriod(period)
        
        self.forceCompleteRecalculationAfterScrollPosition()
        state.ui.hasInitializedChart = true

        // For TOTAL period, immediately compute and show visible-window averages
        if period == .total {
            updateMetricsForCurrentView()
        }
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
            },
            setMetricPlaceholders: {
                self.metricsManager.setPlaceholdersForAllMetrics()
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
    func updateYAxisCache(force: Bool = false) {
        // Avoid domain updates during active scrolling unless explicitly forced
        if state.graph.isScrolling && !force {
            logger.log(level: .debug, tag: "DashboardStore", message: "Blocking Y-axis update during scroll (not forced)")
            return
        }
        
        // Choose operations for Y-axis domain
        // - TOTAL: all operations
        // - Others: visible operations PLUS bracketing points to account for line segments extending beyond visible window
        var operationsForYAxis: [BathScaleWeightSummary]
        if state.graph.selectedPeriod == .total {
            operationsForYAxis = continuousOperations
        } else {
            let visible = visibleOperations
            let bracket = graphManager.getBracketingOperations(from: continuousOperations)
            
            if visible.isEmpty {
                // No visible points - use bracketing points or all data as fallback
                operationsForYAxis = bracket.isEmpty ? continuousOperations : bracket
            } else {
                // Combine visible points with bracketing points for complete line coverage
                // This ensures Y-axis accounts for line segments that extend beyond the visible window
                // Manually deduplicate by entryTimestamp since BathScaleWeightSummary doesn't conform to Hashable
                var combinedOperations = visible
                for bracketOp in bracket {
                    // Only add if not already present (check by entryTimestamp for uniqueness)
                    if !combinedOperations.contains(where: { $0.entryTimestamp == bracketOp.entryTimestamp }) {
                        combinedOperations.append(bracketOp)
                    }
                }
                // Sort using the same key used for deduplication to ensure consistency
                operationsForYAxis = combinedOperations.sorted { $0.entryTimestamp < $1.entryTimestamp }
            }
        }
        
        // Apply cache update in a transaction that disables animations to prevent layout jumps
        graphManager.calculateAndCacheYAxisDomain(
            from: operationsForYAxis,
            goalWeight: goalWeightForDisplay,
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight,
            convertWeight: goalManager.convertWeightToDisplay,
            chartHeight: state.graph.chartHeight
        )
        
        // Invalidate chart series cache so metric normalization recomputes using the new Y-axis domain
        cachedChartSeriesData = nil
        
        // Force a UI refresh so Charts read the updated cached domain/ticks immediately
        objectWillChange.send()
        
        logger.log(level: .debug, tag: "DashboardStore", message: "Y-axis domain updated (force=\(force))")
    }
    
    
    
    // MARK: - Helper Methods
    
    // Extracted helper to compute selection-based label first
    private func selectionLabel() -> String? {
        // If a crosshair date is selected, show that date
        if let selectedDate = state.graph.selectedXValue {
            return graphManager.formatSelectedDate(selectedDate, for: state.graph.selectedPeriod)
        }
        // If a point is selected, show its date
        if let selectedPoint = state.graph.selectedPoint {
            return graphManager.formatSelectedDate(selectedPoint.date, for: state.graph.selectedPeriod)
        }
        // If an entry is selected, use its date or find matching summary
        if let selectedEntry = state.graph.selectedEntry {
            if let date = selectedEntry.date {
                return graphManager.formatSelectedDate(date, for: state.graph.selectedPeriod)
            }
            if let originalSummary = continuousOperations.first(where: { $0.entryTimestamp == selectedEntry.entryTimestamp }) {
                return graphManager.formatSelectedDate(originalSummary.date, for: state.graph.selectedPeriod)
            }
        }
        return nil
    }

    private func labelForTotalPeriod() -> String {
        let minDate = continuousOperations.min(by: { $0.date < $1.date })?.date
        let maxDate = continuousOperations.max(by: { $0.date < $1.date })?.date
        if let minDate = minDate, let maxDate = maxDate {
            return graphManager.formatDateRange(minDate: minDate, maxDate: maxDate, for: .total)
        }
        return graphManager.fallbackTimeLabel(for: .total)
    }

    

    // New: Year label based on visible X-axis gridlines instead of chart points
    private func labelForYearGridlines() -> String {
        let period: TimePeriod = .year
        let leftEdge = graphManager.state.xScrollPosition
        let rightEdge = leftEdge.addingTimeInterval(graphManager.visibleDomainLength(for: period))
        let ticks = xAxisValuesWithBuffer(for: period)
        let visibleTicks = ticks.filter { $0 >= leftEdge && $0 <= rightEdge }.sorted(by: { $0 < $1 })
        let startDate: Date
        let endDate: Date
        if let first = visibleTicks.first, let last = visibleTicks.last {
            startDate = first
            endDate = last
        } else {
            startDate = leftEdge
            endDate = rightEdge
        }
        return formatYearRangeLabel(from: startDate, to: endDate)
    }

    private func formatYearRangeLabel(from start: Date, to end: Date) -> String {
        let cal = Calendar.current
        let sameYear = cal.isDate(start, equalTo: end, toGranularity: .year)
        if sameYear {
            // Full calendar year (Jan..Dec) → show just "yyyy"
            let startMonth = cal.component(.month, from: start)
            let endMonth = cal.component(.month, from: end)
            if startMonth == 1 && endMonth == 12 {
                return DateTimeTools.formatter("yyyy").string(from: start)
            }
            // Same calendar year partial → "MMM - MMM, yyyy" (or "MMM yyyy" if same month)
            let sameMonth = cal.isDate(start, equalTo: end, toGranularity: .month)
            if sameMonth {
                return DateTimeTools.formatter("MMM yyyy").string(from: start)
            }
            let startStr = DateTimeTools.formatter("MMM").string(from: start)
            let endStr = DateTimeTools.formatter("MMM, yyyy").string(from: end)
            return "\(startStr) - \(endStr)"
        }
        // Cross-year → "MMM yyyy - MMM, yyyy"
        let s = DateTimeTools.formatter("MMM yyyy").string(from: start)
        let e = DateTimeTools.formatter("MMM, yyyy").string(from: end)
        return "\(s) - \(e)"
    }

    

    // New: Month label based on visible X-axis gridlines instead of chart points
    private func labelForMonthGridlines() -> String {
        let period: TimePeriod = .month
        // Visible window boundaries (strict)
        let leftEdge = graphManager.state.xScrollPosition
        let rightEdge = leftEdge.addingTimeInterval(graphManager.visibleDomainLength(for: period))
        // Special-case: if window exactly spans the full month, show "MMM yyyy"
        if let monthInterval = Calendar.current.dateInterval(of: .month, for: leftEdge) {
            let startOfMonth = monthInterval.start
            let inclusiveEnd = Calendar.current.date(byAdding: .day, value: -1, to: monthInterval.end) ?? monthInterval.end
            // Compare by day granularity to avoid hour/timezone differences
            let coversFullMonth = Calendar.current.isDate(leftEdge, inSameDayAs: startOfMonth) &&
            Calendar.current.isDate(rightEdge, inSameDayAs: inclusiveEnd)
            if coversFullMonth {
                return DateTimeTools.formatter("MMM yyyy").string(from: startOfMonth)
            }
        }
        // Get generated X-axis values (may include buffer); filter to strictly visible window
        let ticks = xAxisValuesWithBuffer(for: period)
        let visibleTicks = ticks.filter { $0 >= leftEdge && $0 <= rightEdge }.sorted(by: { $0 < $1 })
        let startDate: Date
        let endDate: Date
        if let first = visibleTicks.first, let last = visibleTicks.last {
            startDate = first
            endDate = last
        } else {
            // Fallback: use window edges when no ticks fall strictly inside
            startDate = leftEdge
            endDate = rightEdge
        }
        return formatMonthRangeLabel(from: startDate, to: endDate)
    }

    private func formatMonthRangeLabel(from start: Date, to end: Date) -> String {
        let calendar = Calendar.current
        let sameYear = calendar.isDate(start, equalTo: end, toGranularity: .year)
        let sameMonth = calendar.isDate(start, equalTo: end, toGranularity: .month)

        // Cross-year: include years on both sides
        if !sameYear {
            let fmt = DateTimeTools.formatter("MMM d, yyyy")
            return "\(fmt.string(from: start)) - \(fmt.string(from: end))"
        }

        // Same month (within same year): always show "MMM yyyy"
        if sameMonth {
            return DateTimeTools.formatter("MMM yyyy").string(from: start)
        }

        // Cross-month within same year: omit year on sides → "MMM d - MMM d"
        let fmt = DateTimeTools.formatter("MMM d")
        return "\(fmt.string(from: start)) - \(fmt.string(from: end))"
    }

    private func defaultRangeLabel(for period: TimePeriod, lastScrollPosition: Date) -> String {
        let minDate = lastScrollPosition
        let maxDate = lastScrollPosition.addingTimeInterval(graphManager.visibleDomainLength(for: period))
        return graphManager.formatDateRange(minDate: minDate, maxDate: maxDate, for: period)
    }

    // Delegate weight formatting to GoalManager
    func formatWeightDisplayText(_ weight: Double?) -> String {
        guard let weight = weight else { return "0.0" }
        return goalManager.formatWeightForDisplay(weight, isWeightlessMode: isWeightlessModeEnabled)
    }
    
    func formatYAxisTickLabel(_ weight: Double) -> String {
        let value = roundedGoalWeight(weight)
        // Thousand separators; keep decimals only when value has fractional part
        let nf = NumberFormatter()
        nf.numberStyle = .decimal
        nf.maximumFractionDigits = value == floor(value) ? 0 : 2
        nf.minimumFractionDigits = value == floor(value) ? 0 : 2
        return nf.string(from: NSNumber(value: value)) ?? String(format: "%.0f", value)
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
    
    func roundedGoalWeight(_ weight: Double) -> Double {
        return weight.rounded(.toNearestOrAwayFromZero) // or your preferred rule
    }
    
    func formattedMetricValue(for metric: (preLabel: String?, value: String)) -> String {
        let raw = metric.value.trimmingCharacters(in: .whitespacesAndNewlines)
        // Extract numeric portion to check for zero (handles "0", "0.0", etc.)
        let numericScalars = raw.unicodeScalars.filter { DashboardStore.allowedNumericCharacters.contains($0) }
        let numericChars = String(String.UnicodeScalarView(numericScalars))
        if let number = Double(numericChars), number == 0 {
            return DashboardStrings.placeholder
        }
        return metric.preLabel.map { "\($0) \(metric.value)" } ?? metric.value
    }

    // MARK: - Metric Info Date Label (for Metric Info Sheet)
    /// Returns the period-aware label used in the Metric Info sheet, matching Dashboard behavior.
    /// - Selection: "day average <MMM d, yyyy>" for week/month; "month average <MMM yyyy>" for year/total.
    /// - No selection: "<period> average <visible-range-label>" using the same visible-region label as the Dashboard.
    func metricInfoDateLabel() -> String {
        let period = state.graph.selectedPeriod

        if let selectedPoint = state.graph.selectedPoint {
            let prefix = selectionPrefix(for: period)
            let dateText = formatMetricInfoSingleDate(selectedPoint.date, period: period)
            return composeMetricInfoLabel(prefix: prefix, dateText: dateText)
        }
        if let crosshairDate = state.graph.selectedXValue {
            let prefix = selectionPrefix(for: period)
            let dateText = formatMetricInfoSingleDate(crosshairDate, period: period)
            return composeMetricInfoLabel(prefix: prefix, dateText: dateText)
        }

        let prefix = "\(period.rawValue) average"
        let dateText = weightLabel // already computed from visible region
        return composeMetricInfoLabel(prefix: prefix, dateText: dateText)
    }

    private func selectionPrefix(for period: TimePeriod) -> String {
        switch period {
        case .week, .month: return "day average"
        case .year, .total: return "month average"
        }
    }

    private func formatMetricInfoSingleDate(_ date: Date, period: TimePeriod) -> String {
        let formatter = DateFormatter()
        switch period {
        case .week, .month:
            formatter.dateFormat = "MMM d, yyyy"
        case .year, .total:
            formatter.dateFormat = "MMM yyyy"
        }
        return formatter.string(from: date)
    }

    private func composeMetricInfoLabel(prefix: String, dateText: String) -> String {
        return "\(prefix) \(dateText)".lowercased()
    }
    
    // Delegate entry creation to MetricsManager
    func createEntryForMetricInfo(metricLabel: String? = nil) -> Entry {
        // Build an entry that mirrors the current dashboard context
        // If a chart point is selected, use that point's values
        // Otherwise, use averages of the currently visible operations
        // Initialize with a timestamp that we'll override below based on selection/context
        let entry = Entry(
            id: UUID(),
            entryTimestamp: DateTimeTools.getCurrentDatetimeIsoString(),
            accountId: "dashboard",
            operationType: OperationType.create.rawValue,
            deviceType: "scale",
            isSynced: true
        )

        if let point = state.graph.selectedPoint {
            // Helpers to convert Double? to Int? with 0 -> nil
            func intOrNil(_ x: Double?) -> Int? {
                guard let x = x else { return nil }
                let v = Int(x.rounded())
                return v == 0 ? nil : v
            }
            func scaled10OrNil(_ x: Double?) -> Int? {
                guard let x = x else { return nil }
                let v = Int((x * 10.0).rounded())
                return v == 0 ? nil : v
            }

            let storedWeight: Int? = {
                let v = Int(point.weight.rounded())
                return v == 0 ? nil : v
            }()

            // Use the actual point's timestamp
            entry.entryTimestamp = DateTimeTools.isoFormatter().string(from: point.date)

            entry.scaleEntry = BathScaleEntry(
                weight: storedWeight,
                bodyFat: intOrNil(point.bodyFat),
                muscleMass: intOrNil(point.muscleMass),
                water: intOrNil(point.water),
                bmi: intOrNil(point.bmi),
                source: "dashboard"
            )
            entry.scaleEntryMetric = BathScaleMetric(
                bmr: scaled10OrNil(point.bmr),
                metabolicAge: intOrNil(point.metabolicAge),
                proteinPercent: intOrNil(point.proteinPercent),
                pulse: intOrNil(point.pulse),
                skeletalMusclePercent: intOrNil(point.skeletalMusclePercent),
                subcutaneousFatPercent: intOrNil(point.subcutaneousFatPercent),
                visceralFatLevel: scaled10OrNil(point.visceralFatLevel),
                boneMass: intOrNil(point.boneMass),
                impedance: nil,
                unit: nil
            )
            return entry
        }

        // Interpolated selection: show interpolated weight for the selectedXValue and placeholders for body metrics
        if let selectedDate = state.graph.selectedXValue {
            // Compute interpolated display weight in current UI context
            let interpolated = graphManager.interpolatedDisplayWeight(
                at: selectedDate,
                from: continuousOperations,
                isWeightlessMode: isWeightlessModeEnabled,
                anchorWeight: weightlessAnchorWeight,
                convertWeight: goalManager.convertWeightToDisplay
            )
            // Map display weight to stored (handle weightless by adding anchor back)
            let unit = accountService.activeAccount?.weightSettings?.weightUnit ?? .lb
            let displayAbsolute: Double? = {
                if let w = interpolated {
                    if isWeightlessModeEnabled, let anchor = weightlessAnchorWeight {
                        return w + anchor
                    } else {
                        return w
                    }
                }
                return nil
            }()
            let storedWeight: Int? = {
                guard let displayAbs = displayAbsolute else { return nil }
                return ConversionTools.convertDisplayToStored(displayAbs, isMetric: unit == .kg)
            }()

            // Timestamp is the crosshair date selected by the user
            entry.entryTimestamp = DateTimeTools.isoFormatter().string(from: selectedDate)

            entry.scaleEntry = BathScaleEntry(
                weight: storedWeight,
                bodyFat: nil,
                muscleMass: nil,
                water: nil,
                bmi: nil,
                source: "dashboard"
            )
            entry.scaleEntryMetric = BathScaleMetric(
                bmr: nil,
                metabolicAge: nil,
                proteinPercent: nil,
                pulse: nil,
                skeletalMusclePercent: nil,
                subcutaneousFatPercent: nil,
                visceralFatLevel: nil,
                boneMass: nil,
                impedance: nil,
                unit: nil
            )
            return entry
        }

        // No selection: compute visible-window averages to mirror tiles and weight label
        let ops = getVisibleOperations()
        if ops.isEmpty {
            var storedWeightForInfo: Int? = nil

            if state.data.hasAnyEntries {
                let interpolatedAverage = graphManager.calculateInterpolatedAverageForVisibleRange(
                    from: continuousOperations,
                    period: state.graph.selectedPeriod,
                    isWeightlessMode: isWeightlessModeEnabled,
                    anchorWeight: weightlessAnchorWeight,
                    convertWeight: goalManager.convertWeightToDisplay
                )
                if let displayAvg = interpolatedAverage {
                    let unit = accountService.activeAccount?.weightSettings?.weightUnit ?? .lb
                    storedWeightForInfo = ConversionTools.convertDisplayToStored(displayAvg, isMetric: unit == .kg)
                }
            }

            entry.scaleEntry = BathScaleEntry(
                weight: storedWeightForInfo,
                bodyFat: nil,
                muscleMass: nil,
                water: nil,
                bmi: nil,
                source: "dashboard"
            )
            entry.scaleEntryMetric = BathScaleMetric(
                bmr: nil,
                metabolicAge: nil,
                proteinPercent: nil,
                pulse: nil,
                skeletalMusclePercent: nil,
                subcutaneousFatPercent: nil,
                visceralFatLevel: nil,
                boneMass: nil,
                impedance: nil,
                unit: nil
            )
            return entry
        }

        // Average helpers
        func avg(_ values: [Double?]) -> Double? {
            let xs = values.compactMap { $0 }
            guard !xs.isEmpty else { return nil }
            return xs.reduce(0, +) / Double(xs.count)
        }

        // Helpers for averages: Double? -> Int? with 0 -> nil
        func intOrNil(_ x: Double?) -> Int? {
            guard let x = x else { return nil }
            let v = Int(x.rounded())
            return v == 0 ? nil : v
        }
        func scaled10OrNil(_ x: Double?) -> Int? {
            guard let x = x else { return nil }
            let v = Int((x * 10.0).rounded())
            return v == 0 ? nil : v
        }

        // Weight average in stored units
        let avgStoredWeightOpt: Int? = {
            let ws = ops.map { Double($0.weight) }
            guard let mean = avg(ws) else { return dataManager.state.latestWeightStored == 0 ? nil : dataManager.state.latestWeightStored }
            let v = Int(mean.rounded())
            return v == 0 ? nil : v
        }()

        // Build scaleEntry from averages (convert display doubles to stored Ints where appropriate)
        let avgBodyFat = avg(ops.map { $0.bodyFat }).map { Int($0.rounded()) }
        let avgMuscle = avg(ops.map { $0.muscleMass }).map { Int($0.rounded()) }
        let avgWater = avg(ops.map { $0.water }).map { Int($0.rounded()) }
        let avgBmi = avg(ops.map { $0.bmi }).map { Int($0.rounded()) }
        entry.scaleEntry = BathScaleEntry(
            weight: avgStoredWeightOpt,
            bodyFat: intOrNil(avgBodyFat.map { Double($0) }),
            muscleMass: intOrNil(avgMuscle.map { Double($0) }),
            water: intOrNil(avgWater.map { Double($0) }),
            bmi: intOrNil(avgBmi.map { Double($0) }),
            source: "dashboard"
        )

        // Metric entry: visceralFat and bmr are stored scaled by 10
        let avgBmr = scaled10OrNil(avg(ops.map { $0.bmr }))
        let avgMetAge = intOrNil(avg(ops.map { $0.metabolicAge }))
        let avgProtein = intOrNil(avg(ops.map { $0.proteinPercent }))
        let avgPulse = intOrNil(avg(ops.map { $0.pulse }))
        let avgSkel = intOrNil(avg(ops.map { $0.skeletalMusclePercent }))
        let avgSubFat = intOrNil(avg(ops.map { $0.subcutaneousFatPercent }))
        let avgVisceral = scaled10OrNil(avg(ops.map { $0.visceralFatLevel }))
        let avgBone = intOrNil(avg(ops.map { $0.boneMass }))

        entry.scaleEntryMetric = BathScaleMetric(
            bmr: avgBmr,
            metabolicAge: avgMetAge,
            proteinPercent: avgProtein,
            pulse: avgPulse,
            skeletalMusclePercent: avgSkel,
            subcutaneousFatPercent: avgSubFat,
            visceralFatLevel: avgVisceral,
            boneMass: avgBone,
            impedance: nil,
            unit: nil
        )
        return entry
    }
    
    func createEntryForMetricInfoAsync(metricLabel: String? = nil) async -> Entry {
        await metricsManager.createEntryForMetricInfo(metricLabel: metricLabel)
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
    
    /// Start dragging the goal card
    func startDraggingGoalCard() {
        state.ui.isGoalCardBeingDragged = true
    }
    
    /// Update drop target during drag
    func updateDropTarget(_ targetId: String?) {
        state.ui.dropHoverId = targetId
    }
    
    /// End dragging and clear drag state
    func endDragging() {
        state.ui.draggingMetric = nil
        state.ui.draggingStreak = nil
        state.ui.isGoalCardBeingDragged = false
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
    
    /// Move a metric from source index to destination index (for UIKit drag and drop)
    /// - Parameters:
    ///   - from: The source index
    ///   - to: The destination index
    func moveMetric(from sourceIndex: Int, to destinationIndex: Int) {
        // Validate indices before performing the move
        // Restrict moves to only active (non-removed) metrics
        // Calculate the number of active (non-removed) metrics dynamically
        let activeMetricsCount = metricsToShow.count - state.ui.removedMetrics.count
        
        guard sourceIndex != destinationIndex,
              sourceIndex >= 0 && sourceIndex < activeMetricsCount,
              destinationIndex >= 0 && destinationIndex < activeMetricsCount,
              sourceIndex < metricsToShow.count,
              destinationIndex < metricsToShow.count else {
            // logger.log(level: .warning, tag: "DashboardStore", message: "Invalid move indices: from \(sourceIndex) to \(destinationIndex). Active metrics count: \(activeMetricsCount)")
            return
        }
        
        // Get the metrics that are currently being shown
        let visibleMetrics = metricsToShow
        let sourceMetric = visibleMetrics[sourceIndex]
        let destinationMetric = visibleMetrics[destinationIndex]
        
        // Find the actual indices in the full metrics array
        guard let sourceActualIndex = metricsManager.state.metrics.firstIndex(where: { $0.id == sourceMetric.id }),
              let destinationActualIndex = metricsManager.state.metrics.firstIndex(where: { $0.id == destinationMetric.id }) else {
            logger.log(level: .debug, tag: "DashboardStore", message: "Could not find actual indices for metrics")
            return
        }
        
        // Move the metric in the data source using actual indices
        let movedMetric = metricsManager.state.metrics.remove(at: sourceActualIndex)
        metricsManager.state.metrics.insert(movedMetric, at: destinationActualIndex)
        
        // Update active metrics count if needed
        let currentActiveCount = min(metricsManager.state.activeMetricsCount, metricsManager.state.metrics.count)
        metricsManager.state.activeMetricsCount = currentActiveCount
        
        // Provide haptic feedback for successful move
        HapticFeedbackService.light()
        
        logger.log(level: .info, tag: "DashboardStore", message: "Moved metric '\(sourceMetric.label)' from \(sourceActualIndex) to \(destinationActualIndex)")
    }
    
    // MARK: - Graph State Management
    
    /// Clear all selection states
    @MainActor
    func clearSelection() {
        // Delegate to graph manager - do not manipulate graph state directly
        Task {
            // Clear selection through graph manager
            await graphManager.handleChartSelection(at: nil)

            // Show visible-window averages for all periods when selection is cleared
            self.updateMetricsForCurrentView()
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
        // Prevent multiple concurrent scroll end operations
        guard !isProcessingScrollEnd else { return }
        isProcessingScrollEnd = true
        
        // Delegate to graph manager - do not manipulate graph state directly
        Task {
            await graphManager.handleScrollEnd()
        }
        
        // Update UI state after scroll ends - wait for graph manager's timer
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
            // Only perform updates if we're still the active scroll end operation
            guard self.isProcessingScrollEnd else { return }
            
            // Update Y-axis cache after domain recalculation
            self.updateYAxisCache()
            
            // Only update UI elements that don't trigger domain recalculation
            self.updateWeightDisplayForCurrentView()
            self.updateMetricsForCurrentView()
            
            // Summary log at end of scroll
            let count = self.visibleOperations.count
            self.logger.log(level: .debug, tag: "DashboardStore", message: "Scroll end summary - period=\(self.state.graph.selectedPeriod), visibleOps=\(count)")

            // Mark scroll end processing as complete
            self.isProcessingScrollEnd = false
        }
    }
    
    // Flag to prevent multiple concurrent scroll end operations
    private var isProcessingScrollEnd = false
    
    /// Clears all performance caches when data changes
    private func clearAllCaches() {
        cachedChartSeriesData = nil
        cachedVisibleOperations = []
        lastVisibleOperationsCacheTime = Date.distantPast
        isProcessingScrollEnd = false
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
    func updateMetricsForCurrentView() {
        if let selectedPoint = state.graph.selectedPoint {
            // Exact data point selected - show its values
            Task {
                try? await self.metricsManager.updateMetrics(with: selectedPoint)
            }
        } else if state.graph.selectedXValue != nil {
            // Interpolated position selected (no exact data point) - show placeholders
            metricsManager.setPlaceholdersForAllMetrics()
        } else {
            // No selection: compute visible-window averages for all metrics
            let ops = self.visibleOperations
            Task {
                await self.metricsManager.updateMetricsForVisibleAverage(visibleOperations: ops)
            }
        }
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
            state.ui.selectedMetricLabel = nil
        }
    }
    
    /// Handle selected entry change
    /// Clears metric selection when entry is cleared
    func handleSelectedEntryChange(_ newValue: Entry?) {
        
        _ = newValue
    }
    
    /// Handle metric info sheet dismiss
    /// Clears metric selection when sheet is dismissed
    func handleMetricInfoSheetDismiss(_ newValue: MetricInfoWrapper?) {
        _ = newValue
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
        
        logger.log(level: .debug, tag: "DashboardStore", message: "Forced complete recalculation after programmatic scroll position change")
    }
    
    /// Perform actions when dashboard appears
    /// Loads latest data, goal card, and ensures proper initialization
    func onAppearActions() {
        loadLatestEntryData()
        loadGoalCardData()
        // Handle any settings changes
        handleSettingsChange()
        
        // Refresh dashboard configuration from API to ensure latest changes are reflected
        Task {
            await loadDashboardConfigurationFromAPI()
            await MainActor.run {
                self.objectWillChange.send()
            }
        }
        
        // After positioning is complete, update Y-axis cache to ensure proper domain calculation
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            self.updateYAxisCache()
            // Force both grids to rebuild and recalc intrinsic size after re-appearing (fixes half-height issue)
            self.resetGridLayout()
            self.objectWillChange.send()
        }
        
        logger.log(level: .debug, tag: "DashboardStore", message: "Dashboard onAppear actions completed")
    }
    
    /// Force a complete refresh of the dashboard state
    /// This ensures all UI elements are updated immediately
    func refreshDashboardState() {
        // Force refresh of all dashboard components
        loadLatestEntryData()
        loadGoalCardData()
        handleSettingsChange()
        
        // Force UI update
        objectWillChange.send()
        
        // Reset grid layout to ensure proper display
        resetGridLayout()

    }
    
    /// Begins an edit session by snapshotting the current state for synchronous revert.
    func beginEdit() {
        guard !hasEditSnapshot else { return }
        snapshotMetrics = metricsManager.state.metrics
        snapshotActiveMetricsCount = metricsManager.state.activeMetricsCount
        snapshotStreakItems = streakManager.state.streakItems
        snapshotActiveStreakItemsCount = streakManager.state.activeStreakItemsCount
        snapshotGoalCardRemoved = state.ui.isGoalCardRemoved
        snapshotGoalCardPosition = state.ui.goalCardPosition
        snapshotStreakGridOrder = state.ui.streakGridOrder
        snapshotRemovedMetrics = state.ui.removedMetrics
        snapshotRemovedStreaks = state.ui.removedStreaks
        hasEditSnapshot = true

    }
    
    /// Cancels the current edit session and discards unsaved changes by restoring the snapshot synchronously.
    func cancelEdit() {
        logger.log(level: .info, tag: "DashboardStore", message: "Cancelling edit session and restoring snapshot.")
        // Restore synchronous snapshots first to immediately revert UI/state
        if hasEditSnapshot {
            metricsManager.state.metrics = snapshotMetrics
            metricsManager.state.activeMetricsCount = snapshotActiveMetricsCount
            streakManager.state.streakItems = snapshotStreakItems
            streakManager.state.activeStreakItemsCount = snapshotActiveStreakItemsCount
            state.ui.isGoalCardRemoved = snapshotGoalCardRemoved
            state.ui.goalCardPosition = snapshotGoalCardPosition
            state.ui.streakGridOrder = snapshotStreakGridOrder
            state.ui.removedMetrics = snapshotRemovedMetrics
            state.ui.removedStreaks = snapshotRemovedStreaks
        }
        
        // Sync the removal state to ensure consistency after restoration
        syncRemovalStateFromMetricsManager()
        syncRemovalStateFromStreakManager()
        
        // Clear selection/drag and exit edit mode without forcing relayout
        state.ui.selectedMetricLabel = nil
        // Inline non-destructive drag state clearing to avoid layout jumps
        state.ui.draggingMetric = nil
        state.ui.draggingStreak = nil
        state.ui.dropHoverId = nil
        withAnimation(.easeInOut(duration: 0.2)) {
            state.ui.isEditMode = false
        }
        hasEditSnapshot = false
        objectWillChange.send()
    }

    /// Resets the current edit session and starts a fresh one by reverting changes and creating new snapshot
    func resetEditSession() {
        logger.log(level: .info, tag: "DashboardStore", message: "Resetting edit session and starting fresh.")
        
        // First, restore the original state from snapshot
        if hasEditSnapshot {
            metricsManager.state.metrics = snapshotMetrics
            metricsManager.state.activeMetricsCount = snapshotActiveMetricsCount
            streakManager.state.streakItems = snapshotStreakItems
            streakManager.state.activeStreakItemsCount = snapshotActiveStreakItemsCount
            state.ui.isGoalCardRemoved = snapshotGoalCardRemoved
            state.ui.goalCardPosition = snapshotGoalCardPosition
            state.ui.streakGridOrder = snapshotStreakGridOrder
            state.ui.removedMetrics = snapshotRemovedMetrics
            state.ui.removedStreaks = snapshotRemovedStreaks
        }
        
        // Additionally, ensure order is restored to API/defaults when resetting within edit mode
        metricsManager.resetOrderToDefault()
        if state.metrics.dashboardType == .dashboard12 {
            metricsManager.resetActiveMetricsCountToShowAll()
        }

        // Sync the removal state to ensure consistency after restoration and reset
        syncRemovalStateFromMetricsManager()
        syncRemovalStateFromStreakManager()

        // Clear selection/drag state
        state.ui.selectedMetricLabel = nil
        state.ui.draggingMetric = nil
        state.ui.draggingStreak = nil
        state.ui.dropHoverId = nil
        
        // Clear the old snapshot and create a fresh one
        hasEditSnapshot = false
        beginEdit()
        
        // Force UI update to reflect the reset state
        objectWillChange.send()
        
        logger.log(level: .info, tag: "DashboardStore", message: "Edit session reset successfully - all changes reverted and fresh session started.")
    }
}
