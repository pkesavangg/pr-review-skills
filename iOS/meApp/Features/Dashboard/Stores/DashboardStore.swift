import SwiftUI
import SwiftData
import Combine
import Charts
import Foundation

/// Snapshot of account settings for consolidated subscription
/// Used to detect changes across multiple settings in a single subscription
private struct AccountSettingsSnapshot: Equatable {
    let weightUnit: WeightUnit?
    let isWeightlessOn: Bool?
    let weightlessWeight: Double?
    let goalWeight: Double?
    let initialWeight: Double?
    let goalType: GoalType?

    init(from account: Account?) {
        self.weightUnit = account?.weightSettings?.weightUnit
        self.isWeightlessOn = account?.weightlessSettings?.isWeightlessOn
        self.weightlessWeight = account?.weightlessSettings?.weightlessWeight
        self.goalWeight = account?.goalSettings?.goalWeight
        self.initialWeight = account?.goalSettings?.initialWeight
        self.goalType = account?.goalSettings?.goalType
    }
}

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
    /// Previous account settings snapshot for diffing; used to avoid redundant streak refresh when only unit changes.
    private var lastAccountSettingsSnapshot: AccountSettingsSnapshot?
    private static let allProgressMetricsRemovedKey = "dashboard.allProgressMetricsRemoved"

    // MARK: - UI Update Batching (Performance Optimization)
    /// Prevents cascading UI updates by batching multiple state changes
    private var pendingUIUpdate = false
    private var uiUpdateDebounceTask: Task<Void, Never>?
    
    // MARK: - Debounced Sync
    /// Debounce task to prevent excessive sync calls during drag operations or rapid metric toggles
    private var syncDebounceTask: Task<Void, Never>?
    
    // MARK: - Initialization Tracking
    /// Tracks whether dashboard initialization has completed to prevent race conditions
    private var initializationTask: Task<Void, Never>?
    @Published private(set) var isInitialized: Bool = false

    /// Batches multiple state changes into a single UI update (~1 frame at 60fps)
    /// Use this for non-critical updates that can be coalesced
    private func scheduleUIUpdate() {
        guard !pendingUIUpdate else { return }
        pendingUIUpdate = true

        uiUpdateDebounceTask?.cancel()
        uiUpdateDebounceTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 16_000_000) // ~1 frame at 60fps
            guard !Task.isCancelled, let self = self else { return }
            self.pendingUIUpdate = false
            self.objectWillChange.send()
        }
    }

    /// For critical updates that must be immediate (user-initiated actions)
    private func forceImmediateUIUpdate() {
        uiUpdateDebounceTask?.cancel()
        pendingUIUpdate = false
        objectWillChange.send()
    }
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
    public let dataManager: DashboardDataManager

    var shouldShowGoalCardOrStreaks: Bool {
        return !state.ui.isGoalCardRemoved || !streakItemsToShow.isEmpty
    }

    // MARK: - Display State Computed Properties

    /// Whether there are body metrics to display
    var hasBodyMetrics: Bool {
        !metricsToShow.isEmpty
    }

    /// Whether body metrics should be shown (either skeleton or loaded)
    var shouldShowBodyMetrics: Bool {
        if !state.ui.hasLoadedDashboardConfig {
            // Before loading, check if account has body metrics configured
            guard let dashboardMetrics = accountService.activeAccount?.dashboardSettings?.dashboardMetrics,
                  !dashboardMetrics.isEmpty else {
                return false
            }
            let metrics = dashboardMetrics.split(separator: ",").map(String.init)
            return !metrics.isEmpty && metrics.allSatisfy { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        } else {
            // After loading, check if there are metrics to show
            return hasBodyMetrics
        }
    }

    /// Whether body metrics skeleton should be shown
    var shouldShowBodyMetricsSkeleton: Bool {
    shouldShowBodyMetrics &&
    (!state.ui.hasLoadedDashboardConfig || !state.ui.hasLoadedMetricValues)
}

    /// Whether progress metrics skeleton should be shown
    var shouldShowProgressMetricsSkeleton: Bool {
        if !state.ui.hasLoadedProgressMetrics {
            return true
        }

        if !shouldShowGoalStreakSection {
            return false
        }
        // In non-edit mode, wait until streak order is ready
        let needsStreakOrder =
            !state.ui.isEditMode &&
            !streakItemsToShow.isEmpty &&
            state.ui.streakGridOrder.isEmpty

        return needsStreakOrder
    }

    /// Whether skeleton progress metrics has content above (body metrics)
    var skeletonProgressMetricsHasContentAbove: Bool {
        shouldShowBodyMetrics
    }

    /// Whether the divider should be shown between body metrics and goal/streak section.
    /// Shows divider only when BOTH body metrics AND progress metrics are present.
    var shouldShowDivider: Bool {
        let hasBodyMetricsToShow = shouldShowBodyMetrics
        let hasProgressMetricsToShow = shouldShowProgressMetricsSkeleton || shouldShowGoalStreakSection
        return hasBodyMetricsToShow && hasProgressMetricsToShow
    }

    /// Whether the goal/streak section should be shown.
    /// Only shows after body metrics configuration is loaded to prevent premature display.
    var shouldShowGoalStreakSection: Bool {
        state.ui.hasLoadedDashboardConfig && hasGoalOrStreaks
    }

    // MARK: - Private Helpers

    /// Goal card should be shown if it's not removed, regardless of whether a goal is set
    private var hasGoalOrStreaks: Bool {
        !streakItemsToShow.isEmpty || !state.ui.isGoalCardRemoved
    }
    // MARK: - Initialization
    init() {
        // Initialize managers without default metrics to prevent flash of stale data
        // Defaults will be set only if API load fails
        self.metricsManager = DashboardMetricsManager(skipInitialSetup: true)
        self.graphManager = DashboardGraphManager()
        self.streakManager = DashboardStreakManager(skipInitialSetup: true)
        self.dataManager = DashboardDataManager()
        self.goalManager = DashboardGoalManager()

        // Suppress reactive updates during initialization to prevent AttributeGraph cycles
        state.ui.isResettingDashboard = true

        // Set up reactive bindings
        setupBindings()
        setupSubscriptions()
        
        // Preemptively set flag to prevent empty state during initialization
        if !streakManager.state.streakItems.isEmpty {
            state.ui.hasLoadedProgressMetrics = true
            if state.ui.streakGridOrder.isEmpty {
                state.ui.streakGridOrder = streakManager.state.streakItems.map { $0.id.uuidString }
            }
        } else {
            streakManager.setupInitialStreakItems()
            if !streakManager.state.streakItems.isEmpty {
                state.ui.hasLoadedProgressMetrics = true
                state.ui.streakGridOrder = streakManager.state.streakItems.map { $0.id.uuidString }
                state.ui.removedStreaks = []
            }
        }
        
        // Initialize dashboard - track task to prevent concurrent initialization
        initializationTask = Task {
            await initializeDashboard()
            await MainActor.run {
                isInitialized = true
            }
        }
    }

    /// Lightweight initializer that avoids subscriptions and heavy async initialization.
    /// Use this for ephemeral contexts (e.g., Metric Info sheet without full dashboard state).
    init(lightweight: Bool) {
        // Initialize managers
        self.metricsManager = DashboardMetricsManager()
        self.graphManager = DashboardGraphManager()
        self.streakManager = DashboardStreakManager()
        self.dataManager = DashboardDataManager()
        self.goalManager = DashboardGoalManager()

        // Bind state so basic computed properties can work
        setupBindings()

        // Skip subscriptions and async initialization to avoid network/processing
        if !lightweight {
            setupSubscriptions()
            Task { await initializeDashboard() }
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
                guard let self = self else { return }
                // Suppress UI updates during reset to prevent flickering
                if !self.state.ui.isResettingDashboard {
                    self.state.metrics = metricsState
                }
            }
            .store(in: &cancellables)

        // Sync streak manager state to centralized state
        streakManager.$state
            .sink { [weak self] streakState in
                guard let self = self else { return }
                // Suppress UI updates during reset to prevent flickering
                if !self.state.ui.isResettingDashboard {
                    self.state.streak = streakState
                }
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

        // MARK: - Consolidated Settings Subscription (Performance Optimization)
        // Single subscription replaces 4 separate ones to prevent cascading updates
        // Combines: weight unit, weightless mode, weightless weight, goal settings
        // Uses debounce to batch rapid changes into single update
        accountService.$activeAccount
            .map { AccountSettingsSnapshot(from: $0) }
            .removeDuplicates()
            .dropFirst()
            .debounce(for: .milliseconds(100), scheduler: DispatchQueue.main)
            .sink { [weak self] snapshot in
                guard let self else { return }
                let previous = self.lastAccountSettingsSnapshot
                self.lastAccountSettingsSnapshot = snapshot
                // Only refresh streak when goal/initial weight changes; unit changes are handled by handleUnitChange
                let goalRelatedChanged = previous == nil || previous?.goalWeight != snapshot.goalWeight
                    || previous?.initialWeight != snapshot.initialWeight || previous?.goalType != snapshot.goalType
                self.logger.log(level: .debug, tag: "DashboardStore", message: "Account settings changed (consolidated subscription)")
                self.handleSettingsChange(shouldRefreshStreak: goalRelatedChanged)
            }
            .store(in: &cancellables)

        // Subscribe to active account changes (account switch - different from settings changes)
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

        // Subscribe to dashboard type changes (separate from settings as it has different handler)
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
        // Don't show metrics until dashboard config is loaded from API to prevent flash of stale data
        guard state.ui.hasLoadedDashboardConfig else { return [] }

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
        let allStreaks = streakManager.state.streakItems
        
        guard !allStreaks.isEmpty else { return [] }
        
        // Show all streaks before API loads to prevent empty state
        if !state.ui.isEditMode && !state.ui.hasLoadedProgressMetrics {
            return allStreaks
        }
        
        let nonRemoved = allStreaks.filter { !state.ui.removedStreaks.contains($0.label) }
        let removed = allStreaks.filter { state.ui.removedStreaks.contains($0.label) }

        return state.ui.isEditMode ? nonRemoved + removed : nonRemoved
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

    /// Checks if a goal is set for the active account (as resolved by the goal manager)
    var hasGoalSet: Bool {
        // Use the goal manager's resolved state to avoid drifting logic.
        // It already checks the API goal settings and normalizes to a boolean.
        state.goal.hasGoalSet
    }

    var shouldShowStreakGrid: Bool {
        // Only show streak grid if there are visible (non-removed) streaks
        let visibleStreaks = streakItemsToShow.filter { !state.ui.removedStreaks.contains($0.label) }
        return !visibleStreaks.isEmpty
    }

    // MARK: - Cached Operations (Performance Optimization)
    // Cache for continuousOperations to prevent repeated data manager calls
    private var _cachedContinuousOperations: [BathScaleWeightSummary] = []
    private var _cachedContinuousPeriod: TimePeriod?

    // Cache for label date range operations to avoid repeated O(n) filters
    private var _cachedLabelDateRangeOps: [BathScaleWeightSummary] = []
    private var _cachedLabelDateRangePeriod: TimePeriod?
    private var _cachedLabelDateRangeScrollPos: Date?

    /// Cached continuous operations - only recalculates when period changes or cache is invalidated
    var continuousOperations: [BathScaleWeightSummary] {
        // Return cache if valid for current period
        if _cachedContinuousPeriod == state.graph.selectedPeriod && !_cachedContinuousOperations.isEmpty {
            return _cachedContinuousOperations
        }
        // Recalculate and cache
        _cachedContinuousOperations = dataManager.getContinuousOperations(for: state.graph.selectedPeriod)
        _cachedContinuousPeriod = state.graph.selectedPeriod
        return _cachedContinuousOperations
    }

    /// Invalidates the continuousOperations cache - call when data changes
    func invalidateContinuousOperationsCache() {
        _cachedContinuousOperations = []
        _cachedContinuousPeriod = nil
        // Also invalidate dependent caches
        cachedVisibleOperations = []
        cachedChartSeriesData = nil
        // Invalidate label date range cache
        _cachedLabelDateRangeOps = []
        _cachedLabelDateRangePeriod = nil
        _cachedLabelDateRangeScrollPos = nil
    }

    var visibleOperations: [BathScaleWeightSummary] {
        // During scrolling, be very aggressive about using cache
        // The slight inaccuracy is worth the CPU savings
        if state.graph.isScrolling && !cachedVisibleOperations.isEmpty {
            let timeSinceLastCache = Date().timeIntervalSince(lastVisibleOperationsCacheTime)
            // Use cache for up to 250ms during scrolling to significantly reduce CPU
            if timeSinceLastCache < 0.25 {
                return cachedVisibleOperations
            }
        }

        // Get fresh result from graph manager (which uses binary search)
        let visible = graphManager.getVisibleOperations(from: continuousOperations)

        // Update cache
        cachedVisibleOperations = visible
        lastVisibleOperationsCacheTime = Date()

        return visible
    }

    // Delegate chart data generation to GraphManager with scroll performance optimization
    var chartSeriesData: [GraphSeries] {
        // Skip expensive recalculation during scroll end processing
        guard !isProcessingScrollEnd else {
            return cachedChartSeriesData ?? []
        }
        
        // During scrolling, use cached data ONLY if the metric selection hasn't changed
        // If metric selection changed, recalculate immediately
        if state.graph.isScrolling, let cached = cachedChartSeriesData, !cached.isEmpty {
            // Compare metric selection - handles both selection and deselection cases
            let metricUnchanged = cachedChartSeriesMetric == state.ui.selectedMetricLabel
            if metricUnchanged {
                return cached
            }
            // Metric changed → clear cache
            cachedChartSeriesData = nil
            cachedChartSeriesMetric = nil
        }

        // Prepare values used to validate whether cached chart data is still valid
        let ops = continuousOperations
        let currentYAxisDomain = yAxisDomain

        // Check if cached data is still valid (same period, same data count, same metric, same Y-axis domain)
        if let cached = cachedChartSeriesData,
           !cached.isEmpty,
           cachedChartSeriesPeriod == state.graph.selectedPeriod,
           cachedChartSeriesMetric == state.ui.selectedMetricLabel,
           cachedChartSeriesCount == ops.count,
           lastCachedYAxisDomain == currentYAxisDomain {
            return cached
        }

        // Generate fresh data
        let seriesData = graphManager.generateChartDataWithYAxisDomain(
            from: ops,
            visibleOperations: visibleOperations,
            selectedMetric: state.ui.selectedMetricLabel,
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight,
            convertWeight: goalManager.convertWeightToDisplay,
            yAxisDomain: yAxisDomain
        )

        // Cache the result with metadata for validation
        cachedChartSeriesData = seriesData
        cachedChartSeriesPeriod = state.graph.selectedPeriod
        cachedChartSeriesMetric = state.ui.selectedMetricLabel
        cachedChartSeriesCount = ops.count
        lastCachedYAxisDomain = currentYAxisDomain

        return seriesData
    }

    // Cache chart series data to prevent excessive recalculation
    private var cachedChartSeriesData: [GraphSeries]?
    private var cachedChartSeriesPeriod: TimePeriod?
    private var cachedChartSeriesMetric: String?
    private var cachedChartSeriesCount: Int = 0
    // Track cached Y-axis domain to detect changes and invalidate metric series cache
    private var lastCachedYAxisDomain: ClosedRange<Double>?

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
        guard let weightlessSettings = accountService.activeAccount?.weightlessSettings,
              weightlessSettings.isWeightlessOn,
              let weightlessWeight = weightlessSettings.weightlessWeight else {
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
        // Use operations filtered to match the date range shown in the label
        let opsToUse = getOperationsForLabelDateRange()

        // If no visible operations, but we have data and we're not in total view,
        // calculate interpolated average for the visible range
        if opsToUse.isEmpty && !continuousOperations.isEmpty && state.graph.selectedPeriod != .total {
            let labelRange: DateInterval?
            if state.graph.selectedPeriod == .month {
                labelRange = getLabelDateRangeForMonth()
            } else if state.graph.selectedPeriod == .year {
                labelRange = getLabelDateRangeForYear()
            } else if state.graph.selectedPeriod == .week {
                labelRange = getLabelDateRangeForWeek()
            } else {
                labelRange = nil
            }
            let interpolatedAverage = graphManager.calculateInterpolatedAverageForVisibleRange(
                from: continuousOperations,
                period: state.graph.selectedPeriod,
                isWeightlessMode: isWeightlessModeEnabled,
                anchorWeight: weightlessAnchorWeight,
                convertWeight: goalManager.convertWeightToDisplay,
                labelRange: labelRange
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

        // Round to 1 decimal place using a more robust approach to handle floating-point precision
        let roundedAverage = (averageWeight * 10).rounded(.toNearestOrAwayFromZero) / 10
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
        switch period {
        case .total:
            return labelForTotalPeriod()
        case .year:
            return labelForYearGridlines()
        case .month:
            return labelForMonthGridlines()
        case .week:
            return labelForWeekGridlines()
        default:
            let lastScrollPosition = graphManager.state.xScrollPosition
            return defaultRangeLabel(for: period, lastScrollPosition: lastScrollPosition)
        }
    }

    /// Reinitialize dashboard state when the active account changes
    private func handleActiveAccountChanged() {
        // Clear caches and scrolling flags to ensure fresh computations
        clearAllCaches()
        state.ui.hasInitializedChart = false
        graphManager.state.isGraphReady = false
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
        forceImmediateUIUpdate()
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
        // Use operations filtered to match the date range shown in the label
        let opsToUse = getOperationsForLabelDateRange()

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
        let roundedAverage = (average * 10).rounded(.toNearestOrAwayFromZero) / 10
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
        scheduleUIUpdate()
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
            self.forceImmediateUIUpdate()
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
        // Preserve early initialization flag to avoid hiding pre-loaded streaks
        await MainActor.run {
            state.ui.isResettingDashboard = true
            if streakManager.state.streakItems.isEmpty {
                state.ui.hasLoadedProgressMetrics = false
            }
            state.ui.hasLoadedMetricValues = false
        }

        // Refresh account data first to ensure we have latest dashboard settings
        // This ensures changes made during scale setup are reflected
        try? await accountService.refreshAccount()
        
        // Determine dashboard type based on account dashboardType
        let dashboardType = determineDashboardTypeFromAccount()
        state.metrics.dashboardType = dashboardType
        metricsManager.updateDashboardType(dashboardType)

        // Load metrics from local account immediately so they show right away
        // This provides instant feedback while API loads in background
        await loadMetricsFromLocalAccount()

        // Initialize data manager to set up bindings
        await initializeDataManager()

        // Sync entries first to ensure we have latest data from all devices
        // This ensures metric values (like BMI) are consistent across devices
        await syncEntries()

        // Load progress metrics FIRST (before dashboard config)
        // This ensures they're ready before UI renders, preventing gaps
        do {
            // Refresh account data to ensure we have latest dashboard settings
            try? await accountService.refreshAccount()

            // Refresh streak data with real values from API
            try await streakManager.refreshStreakData()

            // Load progress metrics configuration from API
            await loadProgressMetricsFromAccount()

            // Load goal card data to ensure it's ready before showing
            try? await goalManager.loadGoalData()

            // Mark progress metrics as loaded so they appear immediately
            await MainActor.run {
                state.ui.hasLoadedProgressMetrics = true
            }
        } catch {
            // On error, still mark as loaded so UI can show defaults
            await MainActor.run {
                state.ui.hasLoadedProgressMetrics = true
            }
        }

        await loadDashboardConfigurationFromAPI()

        // Ensure removal state is synced after API loading
        // Note: syncRemovalStateFromStreakManager() is NOT called here because
        // loadProgressMetricsFromAccount() (called from loadDashboardConfigurationFromAPI)
        // already sets the removal state correctly from the account data
        syncRemovalStateFromMetricsManager()

        // Load other data - metrics will be updated via updateMetricsForCurrentView() based on visible region
        loadLatestEntryData()

        // Initialize chart AFTER all metrics are loaded
        // This ensures graph and metrics appear together, preventing gaps
        await MainActor.run {
            initializeChart()
        }

        // Re-enable reactive updates after initialization completes
        await MainActor.run {
            state.ui.isResettingDashboard = false
            // Update metrics after initialization is complete and chart is initialized
            // If visibleOperations is empty, it will correctly show placeholders
            if state.ui.hasInitializedChart {
                updateMetricsForCurrentView()
            }
            scheduleUIUpdate()
        }
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

                // R7: Extract relationship data immediately after fetch, before any further await
                let weight = latestEntry.scaleEntry?.weight

                if let weight {
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

    // Load metrics from local account immediately (synchronous, fast)
    // This allows body metrics to show immediately while API loads in background
    private func loadMetricsFromLocalAccount() async {
        await MainActor.run {
            // Try to load from local account if available
            if let account = accountService.activeAccount {
                let dashboardTypeString = account.dashboardSettings?.dashboardType
                let dashboardType: DashboardType
                switch dashboardTypeString {
                case "dashboard4":
                    dashboardType = .dashboard4
                case "dashboard12":
                    dashboardType = .dashboard12
                default:
                    dashboardType = .dashboard12
                }
                metricsManager.updateDashboardType(dashboardType)
                state.metrics.dashboardType = dashboardType

                // Load metrics order from local account if available
                if let dashboardMetrics = account.dashboardSettings?.dashboardMetrics {
                    let metricArray = dashboardMetrics.split(separator: ",").map(String.init)
                    metricsManager.updateMetricsOrder(from: metricArray)
                    syncRemovalStateFromMetricsManager()
                } else {
                    if metricsManager.state.metrics.isEmpty {
                        metricsManager.setupInitialMetrics()
                    }
                }
            } else {
                // Set up default metrics if no account
                if metricsManager.state.metrics.isEmpty {
                    metricsManager.setupInitialMetrics()
                }
            }
            
            // Use local data to avoid empty state during slow API calls
            if !metricsManager.state.metrics.isEmpty {
                state.ui.hasLoadedDashboardConfig = true
            }
        }
    }

    // Delegate configuration loading to respective managers
    func loadDashboardConfigurationFromAPI() async {
        do {
            // Refresh account data from API to ensure we have latest dashboard settings
            try? await accountService.refreshAccount()

            // Sync entries before loading metrics to ensure we have latest entry data from all devices
            // This ensures metric values (like BMI) are consistent across devices
            // Note: This may be a no-op if already synced in initializeDashboard
            await syncEntries()

            // Load dashboard metrics configuration from API
            try await metricsManager.loadMetricsFromAPI()

            // Sync the dashboard type from metrics manager to store state
            await MainActor.run {
                state.metrics.dashboardType = metricsManager.state.dashboardType
                // Sync removal state after loading metrics to ensure UI state reflects API data
                syncRemovalStateFromMetricsManager()
            }

            // Load progress metrics (goal card + streaks) only if not already loaded
            // This prevents duplicate loading when called from initializeDashboard
            let progressMetricsAlreadyLoaded = await MainActor.run {
                state.ui.hasLoadedProgressMetrics
            }

            if !progressMetricsAlreadyLoaded {
                // Refresh streak data with real values from API
                try await streakManager.refreshStreakData()

                // Load progress metrics configuration from API
                await loadProgressMetricsFromAccount()

                // Load goal card data to ensure it's ready before showing
                try? await goalManager.loadGoalData()

                // Mark progress metrics as loaded so they appear after body metrics
                await MainActor.run {
                    state.ui.hasLoadedProgressMetrics = true
                }
            }

            // Mark body metrics as loaded AFTER progress metrics check
            // This ensures both are ready before UI renders (when called from initializeDashboard)
            // For other callers (like onAppearActions), this still works as expected
            await MainActor.run {
                state.ui.hasLoadedDashboardConfig = true
            }

            // Note: Metric values will be updated via updateMetricsForCurrentView() based on visible region
            // This ensures BMI and other metrics use the average from visible region, not latest entry

            // Mark loading as complete
            await MainActor.run {
                scheduleUIUpdate()
            }

            // Update metrics after config is loaded and chart is initialized
            await MainActor.run {
                if state.ui.hasInitializedChart {
                    updateMetricsForCurrentView()
                }
            }

            logger.log(level: .info, tag: "DashboardStore", message: "Dashboard configuration loaded from API successfully")
        } catch {
            // On error, set up default metrics and streaks to prevent empty state
            await MainActor.run {
                if metricsManager.state.metrics.isEmpty {
                    metricsManager.setupInitialMetrics()
                    syncRemovalStateFromMetricsManager()
                }
                // Mark body metrics as loaded even on error so UI can show defaults
                state.ui.hasLoadedDashboardConfig = true

                // Set up default streaks if empty
                if streakManager.state.streakItems.isEmpty {
                    streakManager.setupInitialStreakItems()
                }
                // Mark progress metrics as loaded even on error so UI can show defaults
                state.ui.hasLoadedProgressMetrics = true

                scheduleUIUpdate()
            }
            logger.log(level: .error, tag: "DashboardStore", message: "Failed to load dashboard configuration from API: \(error)")
        }
    }

    func loadProgressMetricsFromAccount() async {
        guard let account = accountService.activeAccount else {
            await MainActor.run { setupDefaultProgressMetricsOrder() }
            return
        }

        guard let progressMetricsString = account.dashboardSettings?.progressMetrics else {
            await MainActor.run { setupDefaultProgressMetricsOrder() }
            return
        }

        let progressMetrics = progressMetricsString.isEmpty
            ? []
            : progressMetricsString.split(separator: ",").map { String($0) }.filter { !$0.isEmpty }

        // Handle case where API defaults empty progress metrics back to all metrics
        let allMetricsRemovedFlag = UserDefaults.standard.bool(forKey: Self.allProgressMetricsRemovedKey)
        let defaultMetricsList: Set<String> = ["goal", "currentStreak", "longestStreak", "weeklyChange", "monthlyChange", "yearlyChange", "totalChange"]
        let isDefaultFullList = Set(progressMetrics) == defaultMetricsList && progressMetrics.count == defaultMetricsList.count

        // If the flag is set and API returns the default full list, treat all as removed
        let shouldTreatAsAllRemoved = allMetricsRemovedFlag && (progressMetrics.isEmpty || isDefaultFullList)

        await MainActor.run {
            let allStreaks = streakManager.state.streakItems

            // Don't mark all as removed on initial load (empty API before user config)
            let isInitialLoad = state.ui.streakGridOrder.isEmpty && state.ui.removedStreaks.isEmpty
            
            if progressMetrics.isEmpty && isInitialLoad {
                setupDefaultProgressMetricsOrder()
                return
            }
            
            if (progressMetrics.isEmpty || shouldTreatAsAllRemoved) && !isInitialLoad {
                // All progress metrics are removed
                state.ui.goalCardPosition = 0
                state.ui.isGoalCardRemoved = true
                // Preserve saved streak order in edit mode; otherwise use default order
                if state.ui.streakGridOrder.isEmpty {
                    // Set default order so edit mode can show removed items in order
                    let defaultOrder = allStreaks.map { $0.id.uuidString }
                    state.ui.streakGridOrder = defaultOrder
                }
                state.ui.removedStreaks = Set(allStreaks.map { $0.label })

                streakManager.state.activeStreakItemsCount = 0

                scheduleUIUpdate()
                return
            }

            guard !allStreaks.isEmpty else {
                setupDefaultProgressMetricsOrder()
                return
            }

            func mapAPIValueToStreakLabel(_ apiValue: String) -> String? {
                switch apiValue {
                case "currentStreak": return DashboardStrings.currentStreak
                case "longestStreak": return DashboardStrings.longestStreak
                case "weeklyChange": return allStreaks.first(where: { $0.label.contains("/week") })?.label
                case "monthlyChange": return allStreaks.first(where: { $0.label.contains("/month") })?.label
                case "yearlyChange": return allStreaks.first(where: { $0.label.contains("/year") })?.label
                case "totalChange": return allStreaks.first(where: { $0.label.contains("/total") })?.label
                default: return nil
                }
            }

            var goalCardPosition: Int?
            var orderedStreakIds: [String] = []
            var foundStreakLabels: Set<String> = []

            for (index, apiValue) in progressMetrics.enumerated() {
                if apiValue == "goal" {
                    goalCardPosition = index
                } else if let streakLabel = mapAPIValueToStreakLabel(apiValue),
                          let streakItem = allStreaks.first(where: { $0.label == streakLabel }) {
                    orderedStreakIds.append(streakItem.id.uuidString)
                    foundStreakLabels.insert(streakLabel)
                }
            }

            state.ui.goalCardPosition = goalCardPosition ?? 0
            state.ui.isGoalCardRemoved = goalCardPosition == nil
            state.ui.streakGridOrder = orderedStreakIds
            state.ui.removedStreaks = Set(allStreaks.map { $0.label }).subtracting(foundStreakLabels)

            // Sync active streak count from UI removal state
            let activeCount = max(
                0,
                allStreaks.count - state.ui.removedStreaks.count
            )

            streakManager.state.activeStreakItemsCount = min(activeCount, allStreaks.count)

            logger.log(
                level: .debug,
                tag: "DashboardStore",
                message: "Active streaks synced: \(streakManager.state.activeStreakItemsCount)/\(allStreaks.count)"
            )

            scheduleUIUpdate()
        }
    }

    func resetProgressMetricsToDefaults() async {
        await MainActor.run { setupDefaultProgressMetricsOrder() }
    }

    private func setupDefaultProgressMetricsOrder() {
        let allStreaks = streakManager.state.streakItems
        var defaultOrder: [String] = []

        if let streak = allStreaks.first(where: { $0.label == DashboardStrings.currentStreak }) {
            defaultOrder.append(streak.id.uuidString)
        }
        if let streak = allStreaks.first(where: { $0.label == DashboardStrings.longestStreak }) {
            defaultOrder.append(streak.id.uuidString)
        }
        if let streak = allStreaks.first(where: { $0.label.contains("/week") }) {
            defaultOrder.append(streak.id.uuidString)
        }
        if let streak = allStreaks.first(where: { $0.label.contains("/month") }) {
            defaultOrder.append(streak.id.uuidString)
        }
        if let streak = allStreaks.first(where: { $0.label.contains("/year") }) {
            defaultOrder.append(streak.id.uuidString)
        }
        if let streak = allStreaks.first(where: { $0.label.contains("/total") }) {
            defaultOrder.append(streak.id.uuidString)
        }

        state.ui.goalCardPosition = 0
        state.ui.isGoalCardRemoved = false
        state.ui.streakGridOrder = defaultOrder
        state.ui.removedStreaks = []
    }
    /// Rebuild streak order after refresh by matching labels (IDs change each refresh)
    private func regenerateStreakGridOrderAfterRefresh(
        oldStreakItems: [MetricItem],
        oldOrder: [String]
    ) {
        let newItems = streakManager.state.streakItems
        guard !oldOrder.isEmpty else {
            setupDefaultProgressMetricsOrder()
            return
        }
        
        // Maps for quick lookup
        let oldIdToLabel = Dictionary(
            oldStreakItems.map { ($0.id.uuidString, $0.label) },
            uniquingKeysWith: { first, _ in first }
        )
        
        let labelToNewId = Dictionary(
            newItems.map { ($0.label, $0.id.uuidString) },
            uniquingKeysWith: { first, _ in first }
        )
        
        // Restore order using labels
        var newOrder = oldOrder.compactMap {
            oldIdToLabel[$0].flatMap { labelToNewId[$0] }
        }
        
        // Append any new items not already included
        let existingIds = Set(newOrder)
        newOrder.append(contentsOf:
            newItems
                .map { $0.id.uuidString }
                .filter { !existingIds.contains($0) }
        )
        
        state.ui.streakGridOrder = newOrder
    }

    // MARK: - View Helpers moved from DashboardScreen
    func reloadDashboardConfiguration(fullRefresh: Bool = false, updateMetrics: Bool = false) async {
        await loadDashboardConfigurationFromAPI()
        if updateMetrics {
            self.updateMetricsForCurrentView()
        }
        await MainActor.run {
            self.scheduleUIUpdate()
            if fullRefresh {
                self.refreshDashboardState()
            }
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
    
    /// Debounces sync calls to prevent excessive UI updates during drag operations or rapid metric toggles
    func debouncedSyncRemovalState() {
        // Cancel any existing debounce task
        syncDebounceTask?.cancel()
        
        // Create new debounced task
        syncDebounceTask = Task { @MainActor [weak self] in
            // Wait for 150ms to debounce rapid changes
            try? await Task.sleep(nanoseconds: 150_000_000)
            
            // Only sync if task wasn't cancelled
            if !Task.isCancelled {
                self?.syncRemovalStateFromMetricsManager()
            }
        }
    }

    /// Syncs the removal state from the streak manager to the UI state
    /// This should ONLY be called after a toggle operation completes, not during initialization
    func syncRemovalStateFromStreakManager() {
        // Get the current streak items from the manager
        let currentStreakItems = streakManager.state.streakItems
        var activeCount = streakManager.state.activeStreakItemsCount

        guard !currentStreakItems.isEmpty else {
            state.ui.removedStreaks.removeAll()
            return
        }

        if activeCount > currentStreakItems.count {
            activeCount = currentStreakItems.count
        }

        state.ui.removedStreaks.removeAll()

        let safeActiveCount = min(activeCount, currentStreakItems.count)

        guard safeActiveCount < currentStreakItems.count else {
            return
        }
        for i in safeActiveCount..<currentStreakItems.count {
            state.ui.removedStreaks.insert(currentStreakItems[i].label)
        }
    }

    // Delegate entry lifecycle to DataManager
    // MARK: - Entry Lifecycle Management
    internal func onEntryAdded(_ notification: EntryNotification) {
        handleEntryLifecycleChange()
    }

    internal func onEntryUpdated(_ notification: EntryNotification) {
        handleEntryLifecycleChange()
    }

    internal func onEntryDeleted(_ notification: EntryNotification) {
        handleEntryLifecycleChange()
    }

    private func handleEntryLifecycleChange() {
        // EntryService handles incremental summaries; update dashboard state/UI consistently
        DispatchQueue.main.async {
            self.loadLatestEntryData()
            self.loadGoalCardData()
            
            Task {
                do {
                    // Save the old streak items to preserve order by label
                    let oldStreakItems = self.streakManager.state.streakItems
                    let oldOrder = self.state.ui.streakGridOrder
                    
                    try await self.streakManager.refreshStreakData()

                    await MainActor.run {
                        self.regenerateStreakGridOrderAfterRefresh(
                            oldStreakItems: oldStreakItems,
                            oldOrder: oldOrder
                        )
                        
                        // Set flags only after successful refresh to ensure UI state matches actual data
                        self.state.ui.hasLoadedProgressMetrics = true
                        self.state.ui.hasLoadedDashboardConfig = true
                    }
                } catch {
                    self.logger.log(level: .error, tag: "DashboardStore", message: "Failed to refresh streak data after entry change: \(error)")
                }
            }

            // Clear all caches to force recalculation (including continuousOperations)
            self.invalidateContinuousOperationsCache()
            self.lastVisibleOperationsCacheTime = Date.distantPast

            // Force full recomputation of visible operations, Y-axis, and weight display
            self.forceCompleteRecalculationAfterScrollPosition()

            // Check if selected point still exists after deletion
            // If the selected point was deleted, clear selection and update metrics
            // If it still exists, refresh it with the updated summary data
            Task { @MainActor in
                if let selectedPoint = self.state.graph.selectedPoint {
                    let calendar = Calendar.current
                    let updatedPoint: BathScaleWeightSummary? = {
                        switch self.state.graph.selectedPeriod {
                        case .week, .month:
                            return self.continuousOperations.first { calendar.isDate($0.date, inSameDayAs: selectedPoint.date) }
                        case .year, .total:
                            return self.continuousOperations.first { calendar.isDate($0.date, equalTo: selectedPoint.date, toGranularity: .month) }
                        }
                    }()

                    if let updatedPoint = updatedPoint {
                        // Selected point still exists but may have updated values - refresh it
                        self.graphManager.updateSelectedPoint(updatedPoint)
                    } else {
                        // Selected point was deleted - clear selection
                        await self.graphManager.handleChartSelection(at: nil)
                    }
                }

                // Update metrics for current view (either selected point or averages)
                self.updateMetricsForCurrentView()
            }

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
            // This will correctly update removal state for ALL items based on activeMetricsCount
            await MainActor.run {
                self.syncRemovalStateFromMetricsManager()
                // Explicitly trigger objectWillChange to notify subscribers (like Save button enablement)
                self.forceImmediateUIUpdate()
            }
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
            // This will correctly update removal state for ALL items based on activeStreakItemsCount
            await MainActor.run {
                self.syncRemovalStateFromStreakManager()
                // Validate goal card position after streak removal
                self.validateGoalCardPosition()
                // Explicitly trigger objectWillChange to notify subscribers (like Save button enablement)
                self.forceImmediateUIUpdate()
            }
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
        return state.ui.removedStreaks.contains(streakLabel)
    }

    // MARK: - Toggle Removal Methods for UIKit Views

    /// Toggles the removal state of a metric by its label
    func toggleMetricRemoval(_ metricLabel: String) {
        // Find the metric in the current metrics array
        if let metricIndex = metricsManager.state.metrics.firstIndex(where: { $0.label == metricLabel }) {
            if state.ui.removedMetrics.contains(metricLabel) {
                state.ui.removedMetrics.remove(metricLabel)
            } else {
                state.ui.removedMetrics.insert(metricLabel)
            }
            do {
                try metricsManager.toggleMetricVisibilitySync(at: metricIndex)
                syncRemovalStateFromMetricsManager()
            } catch {
                logger.log(level: .error, tag: "DashboardStore", message: "Failed to toggle metric visibility: \(error)")
            }
        }
    }

    /// Toggles the removal state of a streak by its label
    func toggleStreakRemoval(_ streakLabel: String) {
        // Find the streak in the current streak items array
        guard let streakIndex = streakManager.state.streakItems.firstIndex(where: { $0.label == streakLabel }) else {
            return
        }

        // Capture the current state BEFORE toggle to ensure we have the correct baseline
        let currentActiveCount = streakManager.state.activeStreakItemsCount
        let isCurrentlyRemoved = streakIndex >= currentActiveCount

        // Call the underlying manager to actually reorder the array
        Task {
            // Perform the toggle operation atomically
            try? await streakManager.toggleStreakVisibility(at: streakIndex)

            await MainActor.run {
                self.syncRemovalStateFromStreakManager()
                // Validate goal card position after streak removal
                self.validateGoalCardPosition()
                // Explicitly trigger objectWillChange to notify subscribers (like Save button enablement)
                self.forceImmediateUIUpdate()
            }
        }
    }

    func toggleGoalCardRemoval() {
        state.ui.isGoalCardRemoved.toggle()
        // Explicitly trigger objectWillChange to notify subscribers (like Save button enablement)
        forceImmediateUIUpdate()
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
            // Explicitly trigger objectWillChange to notify subscribers (like Save button enablement)
            forceImmediateUIUpdate()
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

    /// - Parameter shouldRefreshStreak: When false, skips streak refresh (e.g. when only unit changed; handleUnitChange handles that).
    func handleSettingsChange(shouldRefreshStreak: Bool = true) {
        Task {
            do {
                if shouldRefreshStreak {
                    try await self.streakManager.refreshStreakData()
                }

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
                self.scheduleUIUpdate()
            }

            // Reload progress metrics from account to restore removal state
            // (syncRemovalStateFromStreakManager overwrites removal state, so we need to restore it)
            await loadProgressMetricsFromAccount()
        }
    }

    /// Handles dashboard type changes by updating the metric type and refreshing the UI
    func handleDashboardTypeChange() {
        let newDashboardType = determineDashboardTypeFromAccount()
        state.metrics.dashboardType = newDashboardType
        metricsManager.updateDashboardType(newDashboardType)

        // Force UI update to reflect the new metric type
        scheduleUIUpdate()

        logger.log(level: .debug, tag: "DashboardStore", message: "Dashboard type changed, updated metric type to: \(newDashboardType)")
    }

    /// Handles unit changes by refreshing streak data and goal data
    func handleUnitChange() {
        Task {
            do {
                // Refresh streak data with new unit
                try await streakManager.refreshStreakDataForUnitChange()
                logger.log(level: .debug, tag: "DashboardStore", message: "Refreshed streak data for unit change")

                // Reload progress metrics from account to map removal state to new labels
                // This ensures that removed streaks are correctly identified after unit change
                await loadProgressMetricsFromAccount()
                logger.log(level: .debug, tag: "DashboardStore", message: "Reloaded progress metrics after unit change")

                // Refresh goal data with new unit
                try await goalManager.refreshGoalDataForUnitChange()
                logger.log(level: .debug, tag: "DashboardStore", message: "Refreshed goal data for unit change")

                // Trigger UI update to refresh views with new unit
                await MainActor.run {
                    self.scheduleUIUpdate()
                }
            } catch {
                logger.log(level: .error, tag: "DashboardStore", message: "Failed to refresh data for unit change: \(error)")
            }
        }
    }

    /// Saves all dashboard changes (metrics and progress metrics) to the API.
    /// After successful save, reloads progress metrics from account to ensure UI state is synchronized.
    func saveChanges() {
        state.ui.selectedMetricLabel = nil
        state.ui.resetDragState()

        notificationService.showLoader(LoaderModel(text: lang.saving))
        Task {
            defer { notificationService.dismissLoader() }
            do {
                // Save dashboard metrics first
                try await metricsManager.saveMetricsToAPI()

                // Save progress metrics (goal card and streak items)
                // Note: AccountService.updateProgressMetrics() already updates activeAccount via updatePublishedState()
                try await saveProgressMetricsToAPI()

                logger.log(level: .info, tag: "DashboardStore", message: "Dashboard changes saved to API successfully")

                // Reload progress metrics from already-updated account to sync UI state.
                // This ensures that streaks added back in edit mode are properly reflected when exiting edit mode.
                await loadProgressMetricsFromAccount()

                commonPostSaveUIReset()
            } catch {
                logger.log(level: .error, tag: "DashboardStore", message: "Failed to save dashboard changes: \(error)")
                commonPostSaveUIReset()
            }
        }
    }

    /// Saves progress metrics (goal card and streak items) to the API.
    /// Maps UI labels to API values and preserves the order from the current state.
    func saveProgressMetricsToAPI() async throws {
        guard accountService.activeAccount != nil else {
            throw DashboardError.noActiveAccount
        }

        // Get all streak items
        let allStreakItems = streakManager.state.streakItems
        let streakOrder = state.ui.streakGridOrder
        let goalCardPosition = state.ui.goalCardPosition
        let isGoalCardRemoved = state.ui.isGoalCardRemoved
        let removedStreaks = state.ui.removedStreaks

        // Helper to map streak label to API value
        func mapStreakLabelToAPI(_ label: String) -> String? {
            if label == DashboardStrings.currentStreak {
                return "currentStreak"
            } else if label == DashboardStrings.longestStreak {
                return "longestStreak"
            } else if label.contains("/week") {
                return "weeklyChange"
            } else if label.contains("/month") {
                return "monthlyChange"
            } else if label.contains("/year") {
                return "yearlyChange"
            } else if label.contains("/total") {
                return "totalChange"
            }
            return nil
        }

        // Reconstruct ordered streaks from saved order
        var orderedStreaks: [MetricItem] = []
        if !streakOrder.isEmpty {
            // Map IDs to actual streak items preserving order
            orderedStreaks = streakOrder.compactMap { id in
                allStreakItems.first(where: { $0.id.uuidString == id })
            }
            // Add any streaks not in the order list (new streaks)
            let missingStreaks = allStreakItems.filter { item in
                !streakOrder.contains(item.id.uuidString)
            }
            orderedStreaks.append(contentsOf: missingStreaks)
        } else {
            // No saved order, use default order
            orderedStreaks = allStreakItems
        }

        // Filter out removed streaks
        let activeStreaks = orderedStreaks.filter { !removedStreaks.contains($0.label) }

        // Build combined order: goal card + streaks
        // The goalCardPosition is the index in the combined grid (goal + all streaks)
        var progressMetrics: [String] = []

        if !isGoalCardRemoved {
            // Build combined array: insert goal at goalCardPosition
            var combinedItems: [String] = []

            // Add streaks first
            for streak in activeStreaks {
                if let apiValue = mapStreakLabelToAPI(streak.label) {
                    combinedItems.append(apiValue)
                }
            }

            // Insert goal at goalCardPosition (clamped to valid range)
            let clampedPosition = min(goalCardPosition, combinedItems.count)
            combinedItems.insert("goal", at: clampedPosition)

            progressMetrics = combinedItems
        } else {
            // Goal is removed, just add all active streaks
            for streak in activeStreaks {
                if let apiValue = mapStreakLabelToAPI(streak.label) {
                    progressMetrics.append(apiValue)
                }
            }
        }

        // Validate: only allow allowed values
        let allowedValues: Set<String> = ["goal", "currentStreak", "longestStreak", "weeklyChange", "monthlyChange", "yearlyChange", "totalChange"]
        progressMetrics = progressMetrics.filter { allowedValues.contains($0) }

        // Preserve saved streak order in edit mode; otherwise use default order
        let allMetricsRemoved = progressMetrics.isEmpty
        UserDefaults.standard.set(allMetricsRemoved, forKey: Self.allProgressMetricsRemovedKey)

        // Log the order being saved for debugging
        logger.log(level: .info, tag: "DashboardStore", message: "Saving progress metrics to API with order: \(progressMetrics), allRemoved: \(allMetricsRemoved)")

        // Save to API
        _ = try await accountService.updateProgressMetrics(metrics: progressMetrics)
        logger.log(level: .info, tag: "DashboardStore", message: "Progress metrics saved to API successfully: \(progressMetrics)")
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

        // Set flag to suppress UI updates during reset to prevent flickering
        state.ui.isResettingDashboard = true
        state.ui.hasLoadedMetricValues = false

        // Reset the saved order to restore default order
        resetGridOrder()

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            Task { @MainActor in
                do {
                    try await self.metricsManager.resetMetricsToDefaults()
                    try await self.streakManager.resetStreakData()

                    // Reset metrics to their original body metrics order
                    self.metricsManager.resetOrderToDefault()


                    try await self.streakManager.refreshStreakData()
                    self.syncRemovalStateFromMetricsManager()
                    self.syncRemovalStateFromStreakManager()

                    // Reset progress metrics to default order: goal, currentStreak, longestStreak, weeklyChange, monthlyChange, yearlyChange, totalChange
                    // Must be called AFTER refreshStreakData() to ensure streak items are available
                    await self.resetProgressMetricsToDefaults()

                    // Save the reset configuration to API
                    // Save dashboard metrics first, then progress metrics
                    try await self.metricsManager.saveMetricsToAPI()
                    try await self.saveProgressMetricsToAPI()

                    // Now manually sync manager states to UI state since we suppressed updates
                    self.state.metrics = self.metricsManager.state
                    self.state.streak = self.streakManager.state

                    // Now batch all UI state changes together to prevent flickering
                    // Clear the reset flag as part of the batched update
                    withAnimation(.easeInOut(duration: 0.3)) {
                        self.state.ui.isLoading = false
                        self.state.ui.loaderOverride = nil
                        self.state.ui.isGoalCardRemoved = false
                        self.state.ui.selectedMetricLabel = nil
                        self.state.graph.clearSelection()
                        self.state.ui.isEditMode = false
                        self.state.ui.resetDragState()
                        self.state.ui.isResettingDashboard = false
                        self.hasEditSnapshot = false
                    }

                    self.notificationService.dismissLoader()
                    self.resetMetricsToLatestEntry()

                    // Mark metric values as loaded since reset restores defaults
                    self.state.ui.hasLoadedMetricValues = true

                    // Single UI update after all state changes are complete
                    self.forceImmediateUIUpdate()
                } catch {
                    self.logger.log(level: .error, tag: "DashboardStore", message: "Failed to reset dashboard: \(error)")

                    // Manually sync manager states even on error
                    self.state.metrics = self.metricsManager.state
                    self.state.streak = self.streakManager.state

                    // Clear the reset flag as part of the batched update
                    withAnimation(.easeInOut(duration: 0.3)) {
                        self.state.ui.isLoading = false
                        self.state.ui.loaderOverride = nil
                        self.state.ui.isGoalCardRemoved = false
                        self.state.ui.selectedMetricLabel = nil
                        self.state.graph.clearSelection()
                        self.state.ui.isEditMode = false
                        self.state.ui.resetDragState()
                        self.state.ui.isResettingDashboard = false
                        self.hasEditSnapshot = false
                    }
                    self.notificationService.dismissLoader()
                    self.resetMetricsToLatestEntry()
                    // Mark metric values as loaded on error recovery too
                    self.state.ui.hasLoadedMetricValues = true
                    self.forceImmediateUIUpdate()
                }
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

    /// Updates the selected time period with optional anchor date for temporal context preservation
    /// - Parameters:
    ///   - period: The new time period to switch to
    ///   - anchorDate: Optional anchor date to center the viewport around (preserves user's temporal focus)
    func updateSelectedPeriod(_ period: TimePeriod, anchorDate: Date? = nil) {
        // Reset chart initialization for new period
        state.ui.hasInitializedChart = false

        // Clear all caches when period changes
        clearAllCaches()

        // End any scrolling immediately so new period computes fresh domain/x-axis
        graphManager.endScrollingImmediately()

        // IMPORTANT: Get the correct operations for the NEW period directly from dataManager
        let operationsForNewPeriod = dataManager.getContinuousOperations(for: period)

        // Calculate optimal scroll position based on X-axis computation logic for segment change
        // This ensures the leftmost visible X-axis value aligns with computed X-axis ticks
        // Use cached bounds for O(1) lookup
        // If anchorDate is provided, center the viewport around it for temporal context preservation
        let optimalScrollPosition = graphManager.calculateOptimalScrollPosition(
            for: period,
            from: operationsForNewPeriod,
            anchorDate: anchorDate,
            showingLatest: anchorDate == nil, // Only show latest if no anchor
            cachedBounds: dataManager.getDateBounds(for: period)
        )

        // Keep section switches aligned to tick grids, but preserve explicit anchor semantics.
        // - Do not snap when an anchor is provided (preserve temporal centering intent).
        // - Do not force month snapping here (month uses its own tick scheme).
        // Week/year must always be tick-aligned after section switches; otherwise transitions
        // can land between visible grid lines.
        let requiresSnapWithAnchor = (period == .week || period == .year)
        let shouldSnapProgrammaticPosition = period != .total && period != .month && (anchorDate == nil || requiresSnapWithAnchor)
        let alignedScrollPosition = shouldSnapProgrammaticPosition
            ? graphManager.snapScrollPosition(optimalScrollPosition, for: period)
            : optimalScrollPosition

        graphManager.updateScrollPosition(to: alignedScrollPosition)
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
            self.scheduleUIUpdate()
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
                // Skip sorting - Y-axis calculation only needs min/max values, not ordered data
                // Use Set for O(1) lookup instead of O(n) contains(where:) - fixes O(n²) performance
                let visibleTimestamps = Set(visible.map { $0.entryTimestamp })
                var combinedOperations = visible
                for bracketOp in bracket where !visibleTimestamps.contains(bracketOp.entryTimestamp) {
                    combinedOperations.append(bracketOp)
                }
                operationsForYAxis = combinedOperations
            }
        }

        // Apply cache update in a transaction that disables animations to prevent layout jumps
        let previousYAxisDomain = state.graph.cachedYAxisDomain
        graphManager.calculateAndCacheYAxisDomain(
            from: operationsForYAxis,
            goalWeight: goalWeightForDisplay,
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight,
            convertWeight: goalManager.convertWeightToDisplay,
            chartHeight: state.graph.chartHeight
        )

        // Invalidate cached chart series data if Y-axis domain changed
        // This is necessary because metric normalization depends on the Y-axis domain
        // Weight series don't need regeneration, but metrics do
        if let newYAxisDomain = state.graph.cachedYAxisDomain,
           let previousDomain = previousYAxisDomain,
           newYAxisDomain != previousDomain {
            // Y-axis domain changed - invalidate cached chart series to force metric recalculation
            cachedChartSeriesData = nil
            lastCachedYAxisDomain = nil
            logger.log(level: .debug, tag: "DashboardStore",
                      message: "Y-axis domain changed from \(previousDomain) to \(newYAxisDomain), invalidating cached chart series")
        }

        // Force a UI refresh so Charts read the updated cached domain/ticks immediately
        scheduleUIUpdate()

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
        // Use cached date bounds from data manager to avoid O(n) min/max scans
        if let bounds = dataManager.getDateBounds(for: .total) {
            return graphManager.formatDateRange(minDate: bounds.min, maxDate: bounds.max, for: .total)
        }
        return graphManager.fallbackTimeLabel(for: .total)
    }



    /// Returns the date range used for the year label display.
    /// This ensures the average computation uses the same dates as the label.
    /// Uses a calendar-aligned 12-month window to avoid 13-month labels.
    private func getYearLabelDateRange() -> (start: Date, end: Date)? {
        let calendar = Calendar.current
        let leftEdge = graphManager.state.xScrollPosition

        // Align to the start of the month containing the left edge
        let start = calendar.dateInterval(of: .month, for: leftEdge)?.start ?? calendar.startOfDay(for: leftEdge)
        guard let endExclusive = calendar.date(byAdding: .month, value: 12, to: start) else {
            return nil
        }
        let endInclusive = inclusiveEnd(fromExclusive: endExclusive)
        
        // Keep label aligned to the visible 12-month window, even if trailing months
        // have no entries. This matches the rendered year grid/ticks behavior.
        return (start: start, end: endInclusive)
    }

    private func labelForYearGridlines() -> String {
        guard let dateRange = getYearLabelDateRange() else {
            return graphManager.fallbackTimeLabel(for: .year)
        }
        return graphManager.formatDateRange(
            minDate: dateRange.start,
            maxDate: dateRange.end,
            for: .year
        )
    }



    /// The active month interval for greying out points outside the visible month.
    /// Returns the DateInterval only when in month period and a full month is visible, otherwise nil.
    var activeMonthInterval: DateInterval? {
        guard state.graph.selectedPeriod == .month else { return nil }
        return getFullyContainedMonthInterval()
    }
    /// Checks if the visible window for month period contains an entire month.
    /// Returns the DateInterval of the fully contained month, or nil if no month is fully visible.
    ///
    /// Checks both the month containing leftEdge AND the next month to handle edge cases.
    private func getFullyContainedMonthInterval() -> DateInterval? {
        let calendar = Calendar.current
        let leftEdge = graphManager.state.xScrollPosition
        let rightEdge = leftEdge.addingTimeInterval(graphManager.visibleDomainLength(for: .month))

        let leftDay = calendar.startOfDay(for: leftEdge)
        let rightDay = calendar.startOfDay(for: rightEdge)

        // Helper to check if a month interval is fully contained
        func isFullyContained(_ monthInterval: DateInterval) -> Bool {
            let startDay = calendar.startOfDay(for: monthInterval.start)
            let endDay = calendar.startOfDay(for: monthInterval.end)
            return leftDay <= startDay && rightDay >= endDay
        }

        // Find the month containing leftEdge
        guard let leftMonthInterval = calendar.dateInterval(of: .month, for: leftEdge) else {
            return nil
        }

        // First check: Is the month containing leftEdge fully contained?
        // (This handles cases where leftEdge is at the start of a month, e.g., Nov 1 to Dec 1)
        if isFullyContained(leftMonthInterval) {
            return leftMonthInterval
        }

        // Second check: Is the next month fully contained?
        // (This handles cases where leftEdge is at end of previous month, e.g., Oct 31 to Dec 1)
        let nextMonthStart = leftMonthInterval.end
        if let nextMonthInterval = calendar.dateInterval(of: .month, for: nextMonthStart),
           isFullyContained(nextMonthInterval) {
            return nextMonthInterval
        }

        return nil
    }

    /// Returns the date range used by the month label for the current scroll position.
    /// If a full calendar month is contained, returns that month interval.
    /// Otherwise returns the visible window range.
    private func getLabelDateRangeForMonth() -> DateInterval {
        let calendar = Calendar.current
        let today = Date()
        let hasAnyOps = !continuousOperations.isEmpty
        let lastEntryDate = continuousOperations.last?.date

        if let monthInterval = getFullyContainedMonthInterval() {
            // A full month is visible; always label the full calendar month.
            let fullMonth = DateInterval(start: monthInterval.start, end: inclusiveEnd(fromExclusive: monthInterval.end))
            return fullMonth
        }

        let leftEdge = graphManager.state.xScrollPosition
        let rightEdge = leftEdge.addingTimeInterval(graphManager.visibleDomainLength(for: .month))
        let visibleWindow = DateInterval(start: leftEdge, end: inclusiveEnd(fromExclusive: rightEdge))

        // If the visible window crosses into a *future* month that has no entries,
        // clamp the label to the end of the current month. This prevents labels like
        // "Feb 13 – Mar 17, 2026" when the chart does not render March grid lines/ticks.
        //
        // We only clamp when:
        // - The label would extend beyond the end of the current calendar month (today's month), AND
        // - The latest entry is not beyond that month (i.e., no data in the future month).
        if hasAnyOps,
           let currentMonth = calendar.dateInterval(of: .month, for: today) {
            let endOfCurrentMonthInclusive = inclusiveEnd(fromExclusive: currentMonth.end)
            let crossesIntoFutureMonth = visibleWindow.end > endOfCurrentMonthInclusive
            let noEntriesBeyondCurrentMonth = (lastEntryDate ?? .distantPast) <= endOfCurrentMonthInclusive

            if crossesIntoFutureMonth && noEntriesBeyondCurrentMonth {
                return DateInterval(start: visibleWindow.start, end: endOfCurrentMonthInclusive)
            }
        }

        return visibleWindow
    }

    /// Returns the date range used by the year label for the current scroll position.
    /// If a custom year label range is available, that range is used.
    /// Otherwise returns a 12-month range starting at the beginning of the month
    /// containing the current scroll position.
    private func getLabelDateRangeForYear() -> DateInterval {
        if let dateRange = getYearLabelDateRange() {
            return DateInterval(start: dateRange.start, end: dateRange.end)
        }

        let calendar = Calendar.current
        let leftEdge = graphManager.state.xScrollPosition
        let windowStart = calendar.dateInterval(of: .month, for: leftEdge)?.start ?? calendar.startOfDay(for: leftEdge)
        let endExclusive = calendar.date(byAdding: .month, value: 12, to: windowStart)
            ?? windowStart.addingTimeInterval(graphManager.visibleDomainLength(for: .year))
        return DateInterval(start: windowStart, end: inclusiveEnd(fromExclusive: endExclusive))
    }

    /// Returns the date range used by the week label for the current scroll position.
    /// Mirrors labelForWeekGridlines so averages match the label.
    private func getLabelDateRangeForWeek() -> DateInterval {
        let calendar = Calendar.current
        let leftEdge = graphManager.state.xScrollPosition
        let windowStart = calendar.startOfDay(for: leftEdge)
        let windowEndExclusive = calendar.date(byAdding: .day, value: 7, to: windowStart)
            ?? windowStart.addingTimeInterval(7 * DashboardConstants.TimeInterval.day)
        let windowEndInclusive = windowEndExclusive.addingTimeInterval(-1)
        return DateInterval(start: windowStart, end: windowEndInclusive)
    }

    private func inclusiveEnd(fromExclusive end: Date) -> Date {
        end.addingTimeInterval(-1)
    }

    /// Returns operations filtered to match the date range shown in the current period's label.
    /// This ensures the average calculation uses the same date range as the displayed label.
    /// Uses caching to prevent repeated O(n) filter operations during scrolling.
    private func getOperationsForLabelDateRange() -> [BathScaleWeightSummary] {
        let currentPeriod = state.graph.selectedPeriod
        let currentScrollPos = graphManager.state.xScrollPosition

        // Return cache if valid (same period and scroll position within threshold)
        if _cachedLabelDateRangePeriod == currentPeriod,
           let cachedScrollPos = _cachedLabelDateRangeScrollPos,
           !_cachedLabelDateRangeOps.isEmpty {
            // Use cache if scroll position hasn't changed significantly (within 1 hour for year, 1 day for month)
            let threshold: TimeInterval = currentPeriod == .year ? 3600 : 86400
            if abs(currentScrollPos.timeIntervalSince(cachedScrollPos)) < threshold {
                return _cachedLabelDateRangeOps
            }
        }

        let calendar = Calendar.current
        var result: [BathScaleWeightSummary]

        switch currentPeriod {
        case .month:
            let labelRange = getLabelDateRangeForMonth()
            result = filterOperationsInDateRange(start: labelRange.start, end: labelRange.end)

        case .year:
            let labelRange = getLabelDateRangeForYear()
            result = filterOperationsInDateRange(start: labelRange.start, end: labelRange.end)

        case .week:
            let labelRange = getLabelDateRangeForWeek()
            result = filterOperationsInDateRangeByDay(start: labelRange.start, end: labelRange.end)

        case .total:
            // Total view shows full timeline (e.g. feb 2022 - feb 2026); use ALL ops in that range.
            // visibleOperations uses a 1-year window and would undercount.
            if let bounds = dataManager.getDateBounds(for: .total) {
                result = filterOperationsInDateRange(start: bounds.min, end: bounds.max)
            } else {
                result = continuousOperations
            }

        default:
            result = visibleOperations
        }

        // Cache the result
        _cachedLabelDateRangeOps = result
        _cachedLabelDateRangePeriod = currentPeriod
        _cachedLabelDateRangeScrollPos = currentScrollPos

        return result
    }

    /// Binary search-based filtering for sorted operations array.
    /// Much faster than filter() for large datasets (O(log n) + O(k) vs O(n)).
    private func filterOperationsInDateRange(start: Date, end: Date) -> [BathScaleWeightSummary] {
        let ops = continuousOperations
        guard !ops.isEmpty else { return [] }

        // Binary search for start index
        var lo = 0
        var hi = ops.count
        while lo < hi {
            let mid = (lo + hi) / 2
            if ops[mid].date < start {
                lo = mid + 1
            } else {
                hi = mid
            }
        }
        let startIndex = lo

        // Binary search for end index
        lo = startIndex
        hi = ops.count
        while lo < hi {
            let mid = (lo + hi) / 2
            if ops[mid].date <= end {
                lo = mid + 1
            } else {
                hi = mid
            }
        }
        let endIndex = lo

        guard startIndex < endIndex else { return [] }
        return Array(ops[startIndex..<endIndex])
    }

    /// Day-granular filtering (inclusive of both start/end days in the user's calendar).
    /// This avoids timezone-boundary leaks (e.g., Nov 2 UTC appearing in a Nov 1 local label).
    private func filterOperationsInDateRangeByDay(start: Date, end: Date) -> [BathScaleWeightSummary] {
        let ops = continuousOperations
        guard !ops.isEmpty else { return [] }

        let calendar = Calendar.current
        let startDay = calendar.startOfDay(for: start)
        let endDay = calendar.startOfDay(for: end)

        func firstIndexAtOrAfterDay(_ day: Date) -> Int? {
            var lo = 0
            var hi = ops.count
            while lo < hi {
                let mid = (lo + hi) / 2
                let midDay = calendar.startOfDay(for: ops[mid].date)
                if midDay < day {
                    lo = mid + 1
                } else {
                    hi = mid
                }
            }
            return lo < ops.count ? lo : nil
        }

        func lastIndexAtOrBeforeDay(_ day: Date) -> Int? {
            var lo = 0
            var hi = ops.count
            while lo < hi {
                let mid = (lo + hi) / 2
                let midDay = calendar.startOfDay(for: ops[mid].date)
                if midDay <= day {
                    lo = mid + 1
                } else {
                    hi = mid
                }
            }
            let idx = lo - 1
            return idx >= 0 ? idx : nil
        }

        guard let startIndex = firstIndexAtOrAfterDay(startDay),
              let endIndex = lastIndexAtOrBeforeDay(endDay),
              startIndex <= endIndex else {
            return []
        }

        return Array(ops[startIndex...endIndex])
    }

    private func labelForMonthGridlines() -> String {
        let period: TimePeriod = .month
        let labelRange = getLabelDateRangeForMonth()
        return graphManager.formatDateRange(
            minDate: labelRange.start,
            maxDate: labelRange.end,
            for: period
        )
    }

    private func labelForWeekGridlines() -> String {
        let period: TimePeriod = .week
        let calendar = Calendar.current
        let leftEdge = graphManager.state.xScrollPosition
        let windowStart = calendar.startOfDay(for: leftEdge)
        let windowEndExclusive = calendar.date(byAdding: .day, value: 7, to: windowStart)
            ?? windowStart.addingTimeInterval(7 * DashboardConstants.TimeInterval.day)

        return graphManager.formatDateRange(
            minDate: windowStart,
            maxDate: windowEndExclusive,
            for: period
        )
    }

    private func defaultRangeLabel(for period: TimePeriod, lastScrollPosition: Date) -> String {
        let minDate = lastScrollPosition
        let maxDate = lastScrollPosition.addingTimeInterval(graphManager.visibleDomainLength(for: period))
        switch period {
        case .week:
            // Use shared range formatter (it applies inclusive end-day handling to match Android)
            return graphManager.formatDateRange(minDate: minDate, maxDate: maxDate, for: period)
        default:
            // For other periods, use existing methods
            return graphManager.formatDateRange(minDate: minDate, maxDate: maxDate, for: period)
        }
    }

    private func formatWeekRangeLabel(from start: Date, to end: Date) -> String {
        let calendar = Calendar.current
        let startYear = calendar.component(.year, from: start)
        let endYear = calendar.component(.year, from: end)
        let startMonth = calendar.component(.month, from: start)
        let endMonth = calendar.component(.month, from: end)
        let endDay = calendar.component(.day, from: end)

        // Match Android WEEK formatting logic
        // Cross-year: "MMM d, yyyy – MMM d, yyyy"
        if startYear != endYear {
            let fmt = DateTimeTools.formatter("MMM d, yyyy")
            return "\(fmt.string(from: start)) – \(fmt.string(from: end))"
        }

        // Cross-month: "MMM d – MMM d, yyyy"
        if startMonth != endMonth {
            let startFmt = DateTimeTools.formatter("MMM d")
            let endFmt = DateTimeTools.formatter("MMM d, yyyy")
            return "\(startFmt.string(from: start)) – \(endFmt.string(from: end))"
        }

        // Same month: "MMM d – d, yyyy"
        let startFmt = DateTimeTools.formatter("MMM d")
        return "\(startFmt.string(from: start)) – \(endDay), \(startYear)"
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
        // Use cached formatter from DateTimeTools instead of creating new DateFormatter each call
        switch state.graph.selectedPeriod {
        case .week, .month:
            return DateTimeTools.formatter("MMM d").string(from: date)
        case .year, .total:
            return DateTimeTools.formatter("MMM yyyy").string(from: date)
        }
    }

    func roundedGoalWeight(_ weight: Double) -> Double {
        return weight.rounded(.toNearestOrAwayFromZero) // or your preferred rule
    }

    func formattedMetricValue(for metric: (preLabel: String?, value: String)) -> String {
        let raw = metric.value.trimmingCharacters(in: .whitespacesAndNewlines)
        // Extract numeric portion to check for zero (handles "0", "0.0", etc.)
        let numericScalars = raw.unicodeScalars.filter { DashboardStore.allowedNumericCharacters.contains($0) }
        let numericChars = String(String.UnicodeScalarView(numericScalars))
        // Check if value is placeholder or zero
        let isPlaceholder = raw == DashboardStrings.placeholder || (numericChars.isEmpty == false && Double(numericChars) == 0)

        if isPlaceholder {
            // If there's a preLabel (e.g., "Lv." for visceral fat), show "Lv. --" instead of just "--"
            if let preLabel = metric.preLabel {
                return "\(preLabel) \(DashboardStrings.placeholder)"
            }
            return DashboardStrings.placeholder
        }
        return metric.preLabel.map { "\($0) \(metric.value)" } ?? metric.value
    }

    // MARK: - Metric Info Date Label

    /// Generates date label for metric info sheet. Shows "Measurement taken [Date]" for history entries, otherwise period averages.
    func metricInfoDateLabel(for entryDTO: BathScaleOperationDTO) -> String {
        let isHistoryEntry = !isDashboardEntry(entryDTO)
        guard let entryDate = parseEntryDate(from: entryDTO) else {
            return formatMetricInfoDateLabel(entryDate: nil)
        }
        return formatMetricInfoDateLabel(entryDate: entryDate, isFromHistory: isHistoryEntry)
    }

    /// Formats the date label based on entry date and context. Returns period averages if no date provided.
    private func formatMetricInfoDateLabel(entryDate: Date? = nil, isFromHistory: Bool = false) -> String {
        let period = state.graph.selectedPeriod

        if let entryDate = entryDate {
            let prefix = isFromHistory ? "Measurement taken" : "day average"
            // Use cached formatter from DateTimeTools instead of creating new DateFormatter each call
            let format = isFromHistory ? "MMMM d, yyyy" : "MMM d, yyyy"
            let dateText = DateTimeTools.formatter(format).string(from: entryDate)
            return isFromHistory ? "\(prefix) \(dateText)" : composeMetricInfoLabel(prefix: prefix, dateText: dateText)
        }

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

    // MARK: - Private Helpers

    private func isDashboardEntry(_ entryDTO: BathScaleOperationDTO) -> Bool {
        return entryDTO.source == "dashboard"
    }

    /// Parses date from entry DTO, handling multiple timestamp formats.
    private func parseEntryDate(from entryDTO: BathScaleOperationDTO) -> Date? {
        if let date = entryDTO.date {
            return date
        }

        guard let timestamp = entryDTO.entryTimestamp else {
            return nil
        }

        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: timestamp) {
            return date
        }

        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: timestamp)
    }

    private func selectionPrefix(for period: TimePeriod) -> String {
        switch period {
        case .week, .month: return "day average"
        case .year, .total: return "month average"
        }
    }

    private func formatMetricInfoSingleDate(_ date: Date, period: TimePeriod) -> String {
        // Use cached formatter from DateTimeTools instead of creating new DateFormatter each call
        switch period {
        case .week, .month:
            return DateTimeTools.formatter("MMM d, yyyy").string(from: date)
        case .year, .total:
            return DateTimeTools.formatter("MMM yyyy").string(from: date)
        }
    }

    private func composeMetricInfoLabel(prefix: String, dateText: String) -> String {
        return "\(prefix) \(dateText)".lowercased()
    }

    // MARK: - Metric Info Sheet - Allowed Metrics & Selection Validation
    /// Returns the allowed metrics for the Metric Info sheet based on dashboard type.
    func allowedMetricsForMetricInfo() -> [BodyMetric] {
        switch state.metrics.dashboardType {
        case .dashboard4:
            return [.weight, .bmi, .bodyFat, .muscleMass, .water]
        case .dashboard12:
            return [.weight, .bmi, .bodyFat, .muscleMass, .water, .pulse, .boneMass, .visceralFatLevel, .subcutaneousFatPercent, .proteinPercent, .skeletalMusclePercent, .bmr, .metabolicAge]
        }
    }

    /// Ensures the selected metric is valid for the current dashboard type; if not, returns the first allowed metric.
    func validateMetricInfoSelection(_ current: BodyMetric) -> BodyMetric {
        let allowed = allowedMetricsForMetricInfo()
        return allowed.contains(current) ? current : (allowed.first ?? .bmi)
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
            func scaled10OrNil(_ x: Double?, metricLabel: String) -> Int? {
                guard let x = x else { return nil }

                if let metricItem = state.metrics.metrics.first(where: { $0.label == metricLabel }) {
                    let tileValue = metricItem.value.trimmingCharacters(in: .whitespacesAndNewlines)
                    if tileValue == DashboardStrings.placeholder || tileValue == "0" || tileValue == "0.0" {
                        return nil
                    }
                }

                // For BMR and visceral fat: x is in stored format (scaled by 10) from BathScaleWeightSummary
                // Divide by 10 to get display format before formatting to properly detect zero values
                // This aligns with how MetricDetailView displays these values
                let displayValue = x / 10.0
                let formatted = BodyMetricsConvertor.convert(displayValue, shouldCompose: false, wholeNumber: true)

                if formatted == "0" || formatted == "0.0" || formatted == "--" {
                    return nil
                }
                // x is already in stored format (scaled by 10), so convert to Int directly
                let v = Int(x.rounded())
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
                bmr: scaled10OrNil(point.bmr, metricLabel: DashboardStrings.bmrKcal),
                metabolicAge: intOrNil(point.metabolicAge),
                proteinPercent: intOrNil(point.proteinPercent),
                pulse: intOrNil(point.pulse),
                skeletalMusclePercent: intOrNil(point.skeletalMusclePercent),
                subcutaneousFatPercent: intOrNil(point.subcutaneousFatPercent),
                visceralFatLevel: scaled10OrNil(point.visceralFatLevel, metricLabel: DashboardStrings.visceralFat),
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
            // Convert each weight to display format and calculate average
            let weightValues = ops.map { goalManager.convertWeightToDisplay(Int($0.weight)) }
            guard !weightValues.isEmpty else {
                return dataManager.state.latestWeightStored == 0 ? nil : dataManager.state.latestWeightStored
            }
            let sum = weightValues.reduce(0, +)
            let average = sum / Double(weightValues.count)

            let roundedAverage = (average * 100).rounded(.toNearestOrAwayFromZero) / 100

            // Convert back to stored format
            let unit = accountService.activeAccount?.weightSettings?.weightUnit ?? .lb
            let stored = ConversionTools.convertDisplayToStored(roundedAverage, isMetric: unit == .kg)
            return stored == 0 ? nil : stored
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
        let avgBmr = intOrNil(avg(ops.map { $0.bmr }))
        let avgMetAge = intOrNil(avg(ops.map { $0.metabolicAge }))
        let avgProtein = intOrNil(avg(ops.map { $0.proteinPercent }))
        let avgPulse = intOrNil(avg(ops.map { $0.pulse }))
        let avgSkel = intOrNil(avg(ops.map { $0.skeletalMusclePercent }))
        let avgSubFat = intOrNil(avg(ops.map { $0.subcutaneousFatPercent }))
        let avgVisceral = intOrNil(avg(ops.map { $0.visceralFatLevel }))
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
        forceImmediateUIUpdate()
    }

    /// Handle drag end for streaks
    func handleStreakDragEnd() {
        endDragging()
        // Force UI update to reflect any reordering
        forceImmediateUIUpdate()
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
            // Only mark ready on early return if chart was previously initialized (e.g., tab switch back)
            // Don't set ready if we're just scrolling during initial load
            if state.ui.hasInitializedChart && !graphManager.state.isGraphReady {
                graphManager.state.isGraphReady = true
            }
            return
        }

        // Calculate optimal scroll position based on X-axis computation logic
        // This ensures the leftmost visible X-axis value aligns with computed X-axis ticks
        // Use cached bounds for O(1) lookup
        let optimalScrollPosition = graphManager.calculateOptimalScrollPosition(
            for: state.graph.selectedPeriod,
            from: continuousOperations,
            anchorDate: nil,
            showingLatest: true,
            cachedBounds: nil
        )

        // Keep initialization aligned to tick grids, except month/total where forcing
        // generic snapping can misalign with rendered month tick progression.
        let period = state.graph.selectedPeriod
        let shouldSnapProgrammaticPosition = period != .total && period != .month
        let alignedScrollPosition = shouldSnapProgrammaticPosition
            ? graphManager.snapScrollPosition(optimalScrollPosition, for: period)
            : optimalScrollPosition

        self.graphManager.updateScrollPosition(to: alignedScrollPosition)

        self.forceCompleteRecalculationAfterScrollPosition()

        state.ui.hasInitializedChart = true

        // Mark graph as ready after a settling delay to allow computations to complete
        // This hides the skeleton loader once the graph has stabilized
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 300_000_000) // 300ms settling delay
            graphManager.state.isGraphReady = true
        }
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

        // Cancel any pending scroll end task
        scrollEndTask?.cancel()

        // Delegate to graph manager - do not manipulate graph state directly
        Task {
            await graphManager.handleScrollEnd()
        }

        // PERFORMANCE: Stagger heavy operations across multiple frames to prevent CPU spike
        // This spreads the work over ~700ms instead of doing it all at once
        scrollEndTask = Task { @MainActor [weak self] in
            guard let self = self else { return }

            // Frame 1: Wait for scroll momentum to settle (100ms)
            try? await Task.sleep(nanoseconds: 100_000_000)
            guard !Task.isCancelled, self.isProcessingScrollEnd else { return }

            // Frame 2: Update Y-axis only (300ms total)
            try? await Task.sleep(nanoseconds: 200_000_000)
            guard !Task.isCancelled, self.isProcessingScrollEnd else { return }
            self.updateYAxisCache()

            // Frame 3: Update weight display (500ms total)
            try? await Task.sleep(nanoseconds: 200_000_000)
            guard !Task.isCancelled, self.isProcessingScrollEnd else { return }
            self.updateWeightDisplayForCurrentView()

            // Frame 4: Update metrics (700ms total)
            try? await Task.sleep(nanoseconds: 200_000_000)
            guard !Task.isCancelled, self.isProcessingScrollEnd else { return }
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
    // Task for staggered scroll end processing (cancellable)
    private var scrollEndTask: Task<Void, Never>?

    /// Clears all performance caches when data changes
    private func clearAllCaches() {
        // Clear continuousOperations cache
        _cachedContinuousOperations = []
        _cachedContinuousPeriod = nil
        // Clear chart series cache
        cachedChartSeriesData = nil
        cachedChartSeriesPeriod = nil
        cachedChartSeriesMetric = nil
        lastCachedYAxisDomain = nil
        cachedChartSeriesCount = 0
        // Clear visible operations cache
        cachedVisibleOperations = []
        lastVisibleOperationsCacheTime = Date.distantPast
        isProcessingScrollEnd = false
    }

    func handleScrollPhaseChange(to phase: ScrollPhase) async {
        await graphManager.handleScrollPhaseChange(phase)

        // When scroll ends (phase becomes .idle), defer heavy work to not block scrolling
        if phase == .idle {
            // Defer heavy computation to allow UI to remain responsive
            // This prevents the "hang" when user tries to scroll again immediately
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { [weak self] in
                guard let self = self else { return }

                // Update Y-axis cache after domain recalculation
                self.updateYAxisCache()

                // Only update UI elements that don't trigger domain recalculation
                self.updateWeightDisplayForCurrentView()
                self.updateMetricsForCurrentView()
            }
        }
    }

    /// Update weight display for current visible region
    @MainActor
    private func updateWeightDisplayForCurrentView() {
        // This will trigger displayWeight recalculation which now considers visible operations
        scheduleUIUpdate()
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
        // Don't update metrics during initialization or if config hasn't loaded yet
        // This prevents flickering and showing stale/placeholder values
        guard !state.ui.isResettingDashboard && state.ui.hasLoadedDashboardConfig else {
            return
        }

        if let selectedPoint = state.graph.selectedPoint {
            // Exact data point selected - show its values
            Task {
                try? await self.metricsManager.updateMetrics(with: selectedPoint)
                await MainActor.run {
                    self.state.ui.hasLoadedMetricValues = true
                }
            }
        } else if state.graph.selectedXValue != nil {
            // Interpolated position selected (no exact data point) - show placeholders
            // Don't mark as loaded when showing placeholders - they represent absence of actual values
            metricsManager.setPlaceholdersForAllMetrics()
        } else {
            // No selection: compute averages aligned to the label date range
            let ops = self.getOperationsForLabelDateRange()

            if ops.isEmpty && state.ui.hasLoadedMetricValues {
                return
            }

            Task {
                await self.metricsManager.updateMetricsForVisibleAverage(visibleOperations: ops)
                await MainActor.run {
                    // Mark as loaded even if there are no operations so skeleton loaders can hide
                    // This prevents skeleton loaders from displaying indefinitely when there's no data
                    self.state.ui.hasLoadedMetricValues = true
                }
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
        scheduleUIUpdate()

        logger.log(level: .debug, tag: "DashboardStore", message: "Forced complete recalculation after programmatic scroll position change")
    }

    /// Perform actions when dashboard appears
    /// Loads latest data, goal card, and ensures proper initialization
    func onAppearActions() {
        // Don't hide metrics during refresh - keep showing current values and update in place
        // Only hide metrics on initial app launch (handled in initializeDashboard)

        loadLatestEntryData()
        loadGoalCardData()
        // Refresh dashboard configuration from API to ensure latest changes are reflected
        Task {
            // Sync entries first to ensure we have the latest data from all devices
            await syncEntries()

            await loadDashboardConfigurationFromAPI()
            // Sync removal state after loading from API to ensure it reflects the latest account data
            await MainActor.run {
                self.syncRemovalStateFromMetricsManager()
                self.scheduleUIUpdate()
            }
            // Update metrics after config is loaded and chart is initialized
            // This updates values in place without hiding metrics
            await MainActor.run {
                if state.ui.hasInitializedChart {
                    self.updateMetricsForCurrentView()
                }
            }
            // Handle any settings changes after syncing removal state
            self.handleSettingsChange()
        }

        // After positioning is complete, update Y-axis cache to ensure proper domain calculation
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            self.updateYAxisCache()
            self.scheduleUIUpdate()
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
        scheduleUIUpdate()

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
    
    /// Updates the snapshot to the current state (used after saving changes)
    /// This ensures that when the user clicks back, it reverts to the last saved state
    func updateSnapshot() {
        guard hasEditSnapshot else { return }
        snapshotMetrics = metricsManager.state.metrics
        snapshotActiveMetricsCount = metricsManager.state.activeMetricsCount
        snapshotStreakItems = streakManager.state.streakItems
        snapshotActiveStreakItemsCount = streakManager.state.activeStreakItemsCount
        snapshotGoalCardRemoved = state.ui.isGoalCardRemoved
        snapshotGoalCardPosition = state.ui.goalCardPosition
        snapshotStreakGridOrder = state.ui.streakGridOrder
        snapshotRemovedMetrics = state.ui.removedMetrics
        snapshotRemovedStreaks = state.ui.removedStreaks
    }

    /// Returns true if there are unsaved changes (current state differs from snapshot)
    func hasUnsavedChanges() -> Bool {
        guard hasEditSnapshot else { return false }
        return metricsManager.state.metrics.map { $0.label } != snapshotMetrics.map { $0.label } ||
               metricsManager.state.activeMetricsCount != snapshotActiveMetricsCount ||
               streakManager.state.streakItems.map { $0.label } != snapshotStreakItems.map { $0.label } ||
               streakManager.state.activeStreakItemsCount != snapshotActiveStreakItemsCount ||
               state.ui.isGoalCardRemoved != snapshotGoalCardRemoved ||
               state.ui.goalCardPosition != snapshotGoalCardPosition ||
               state.ui.streakGridOrder != snapshotStreakGridOrder ||
               state.ui.removedMetrics != snapshotRemovedMetrics ||
               state.ui.removedStreaks != snapshotRemovedStreaks
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
        // Skip syncing from StreakManager since snapshot already has the correct removal state
        syncRemovalStateFromMetricsManager()

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
        forceImmediateUIUpdate()
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
        forceImmediateUIUpdate()

        logger.log(level: .info, tag: "DashboardStore", message: "Edit session reset successfully - all changes reverted and fresh session started.")
    }
}
