// DashboardStore - Coordinator for dashboard state management.
// Business logic is delegated to specialized managers.

import Charts
import Combine
import Foundation
import SwiftData
import SwiftUI

/// Simplified DashboardStore focused on coordination between managers
/// Uses specialized managers for business logic while exposing centralized state for UI
@MainActor
class DashboardStore: ObservableObject, DashboardStateProviding {
    // MARK: - Dependencies

    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector var accountService: AccountService
    @Injector var logger: LoggerService
    @Injector private var scaleService: ScaleService
    @Injector private var entryService: EntryService

    // MARK: - Formatter and Cache Services
    let formatter: DashboardFormatterProtocol
    let cacheManager: DashboardCacheManagerProtocol

    // MARK: - Centralized State

    @Published var state = DashboardState()

    // MARK: - Private Properties

    private var cancellables = Set<AnyCancellable>()
    private var lastAccountSettingsSnapshot: AccountSettingsSnapshot?

    // MARK: - UI Update Batching (Performance Optimization)

    private var pendingUIUpdate = false
    private var uiUpdateDebounceTask: Task<Void, Never>?

    // MARK: - Initialization Tracking

    private var initializationTask: Task<Void, Never>?
    @Published private(set) var isInitialized: Bool = false

    func scheduleUIUpdate() {
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

    func forceImmediateUIUpdate() {
        uiUpdateDebounceTask?.cancel()
        pendingUIUpdate = false
        objectWillChange.send()
    }

    // MARK: - Edit Session Manager

    let editSessionManager: DashboardEditSessionManaging

    // MARK: - Constants

    let lang = LoaderStrings.self
    static let allowedNumericCharacters = CharacterSet(charactersIn: "0123456789.-")

    // MARK: - Domain Managers (Business Logic)

    let metricsManager: DashboardMetricsManager
    let graphManager: DashboardGraphManager
    let goalManager: DashboardGoalManager
    public let streakManager: DashboardStreakManager
    public let dataManager: DashboardDataManager
    let metricsCalculator: DashboardMetricsCalculatorProtocol
    let dateRangeManager: DashboardDateRangeManagerProtocol
    let syncCoordinator: DashboardSyncCoordinatorProtocol

    // MARK: - Coordinating Managers

    private(set) var gridEditingManager: DashboardGridEditingManager!
    private(set) var chartManager: DashboardChartManager!
    private(set) var displayManager: DashboardDisplayManager!
    private(set) var lifecycleManager: DashboardLifecycleManager!

    // MARK: - Initialization

    init(
        formatter: DashboardFormatterProtocol? = nil,
        cacheManager: DashboardCacheManagerProtocol? = nil
    ) {
        self.formatter = formatter ?? DashboardFormatter()
        self.cacheManager = cacheManager ?? DashboardCacheManager()

        self.metricsManager = DashboardMetricsManager(skipInitialSetup: true)
        self.graphManager = DashboardGraphManager()
        self.streakManager = DashboardStreakManager(skipInitialSetup: true)
        self.dataManager = DashboardDataManager()
        self.metricsCalculator = DashboardMetricsCalculator()
        self.goalManager = DashboardGoalManager()

        self.dateRangeManager = DashboardDateRangeManager()
        self.syncCoordinator = DashboardSyncCoordinator()
        self.editSessionManager = DashboardEditSessionManager()

        // Initialize coordinating managers (must happen after self is available)
        initializeCoordinatingManagers()

        state.ui.isResettingDashboard = true

        setupBindings()
        setupSubscriptions()

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

        initializationTask = Task {
            await lifecycleManager.initializeDashboard()
            await MainActor.run {
                isInitialized = true
            }
        }
    }

    /// Lightweight initializer for ephemeral contexts (e.g., Metric Info sheet).
    init(
        lightweight: Bool,
        formatter: DashboardFormatterProtocol? = nil,
        cacheManager: DashboardCacheManagerProtocol? = nil
    ) {
        self.formatter = formatter ?? DashboardFormatter()
        self.cacheManager = cacheManager ?? DashboardCacheManager()

        self.metricsManager = DashboardMetricsManager()
        self.graphManager = DashboardGraphManager()
        self.streakManager = DashboardStreakManager()
        self.dataManager = DashboardDataManager()
        self.metricsCalculator = DashboardMetricsCalculator()
        self.goalManager = DashboardGoalManager()

        self.dateRangeManager = DashboardDateRangeManager()
        self.syncCoordinator = DashboardSyncCoordinator()
        self.editSessionManager = DashboardEditSessionManager()

        initializeCoordinatingManagers()

        setupBindings()

        if !lightweight {
            setupSubscriptions()
            Task { await lifecycleManager.initializeDashboard() }
        }
    }

    /// Wires up the four coordinating managers with their dependencies.
    private func initializeCoordinatingManagers() {
        self.gridEditingManager = DashboardGridEditingManager(
            stateProvider: self,
            metricsManager: metricsManager,
            streakManager: streakManager,
            syncCoordinator: syncCoordinator,
            cacheManager: cacheManager,
            getMetricsToShow: { [weak self] in self?.metricsToShow ?? [] },
            getStreakItemsToShow: { [weak self] in self?.streakItemsToShow ?? [] },
            beginEdit: { [weak self] in self?.beginEdit() }
        )

        self.chartManager = DashboardChartManager(
            stateProvider: self,
            graphManager: graphManager,
            metricsManager: metricsManager,
            goalManager: goalManager,
            dataManager: dataManager,
            cacheManager: cacheManager,
            getContinuousOperations: { [weak self] in self?.continuousOperations ?? [] },
            getIsWeightlessModeEnabled: { [weak self] in self?.isWeightlessModeEnabled ?? false },
            getWeightlessAnchorWeight: { [weak self] in self?.weightlessAnchorWeight },
            getGoalWeightForDisplay: { [weak self] in self?.goalWeightForDisplay }
        )

        self.displayManager = DashboardDisplayManager(
            stateProvider: self,
            graphManager: graphManager,
            dateRangeManager: dateRangeManager,
            metricsCalculator: metricsCalculator,
            metricsManager: metricsManager,
            goalManager: goalManager,
            dataManager: dataManager,
            formatter: formatter,
            cacheManager: cacheManager,
            getContinuousOperations: { [weak self] in self?.continuousOperations ?? [] },
            getVisibleOperations: { [weak self] in self?.visibleOperations ?? [] },
            getIsWeightlessModeEnabled: { [weak self] in self?.isWeightlessModeEnabled ?? false },
            getWeightlessAnchorWeight: { [weak self] in self?.weightlessAnchorWeight }
        )

        // Wire cross-reference: chartManager needs displayManager for metric updates after scroll
        self.chartManager.displayManager = self.displayManager

        self.lifecycleManager = DashboardLifecycleManager(
            stateProvider: self,
            chartManager: chartManager,
            displayManager: displayManager,
            gridEditingManager: gridEditingManager,
            metricsManager: metricsManager,
            graphManager: graphManager,
            streakManager: streakManager,
            dataManager: dataManager,
            goalManager: goalManager,
            syncCoordinator: syncCoordinator,
            editSessionManager: editSessionManager,
            cacheManager: cacheManager
        )
    }

    // MARK: - Reactive Bindings

    private func setupBindings() {
        metricsManager.$state
            .sink { [weak self] metricsState in
                guard let self = self else { return }
                if !self.state.ui.isResettingDashboard {
                    self.state.metrics = metricsState
                }
            }
            .store(in: &cancellables)

        streakManager.$state
            .sink { [weak self] streakState in
                guard let self = self else { return }
                if !self.state.ui.isResettingDashboard {
                    self.state.streak = streakState
                }
            }
            .store(in: &cancellables)

        goalManager.$state
            .sink { [weak self] goalState in
                self?.state.goal = goalState
            }
            .store(in: &cancellables)

        graphManager.$state
            .sink { [weak self] graphState in
                self?.state.graph = graphState
            }
            .store(in: &cancellables)

        dataManager.$state
            .sink { [weak self] dataState in
                self?.state.data = dataState
            }
            .store(in: &cancellables)

        dataManager.$state
            .map { ($0.dailySummaries.count, $0.monthlySummaries.count) }
            .removeDuplicates { $0 == $1 }
            .dropFirst()
            .sink { [weak self] _ in
                self?.invalidateContinuousOperationsCache()
            }
            .store(in: &cancellables)

        dataManager.$state
            .map { $0.hasAnyEntries }
            .removeDuplicates()
            .dropFirst()
            .filter { $0 == true }
            .sink { [weak self] _ in
                guard let self else { return }
                if self.state.ui.hasInitializedChart {
                    self.state.ui.hasInitializedChart = false
                    self.chartManager.initializeChart()
                }
            }
            .store(in: &cancellables)
    }

    private func setupSubscriptions() {
        entryService.entrySaved
            .sink { [weak self] entry in
                self?.lifecycleManager.onEntryAdded(entry)
            }
            .store(in: &cancellables)

        entryService.entryDeleted
            .sink { [weak self] entry in
                self?.lifecycleManager.onEntryDeleted(entry)
            }
            .store(in: &cancellables)

        accountService.$activeAccount
            .map { AccountSettingsSnapshot(from: $0) }
            .removeDuplicates()
            .dropFirst()
            .debounce(for: .milliseconds(100), scheduler: DispatchQueue.main)
            .sink { [weak self] snapshot in
                guard let self else { return }
                let previous = self.lastAccountSettingsSnapshot
                self.lastAccountSettingsSnapshot = snapshot
                let goalRelatedChanged = previous == nil || previous?.goalWeight != snapshot.goalWeight
                    || previous?.initialWeight != snapshot.initialWeight || previous?.goalType != snapshot.goalType
                self.lifecycleManager.handleSettingsChange(shouldRefreshStreak: goalRelatedChanged)
            }
            .store(in: &cancellables)

        accountService.$activeAccount
            .map { $0?.accountId }
            .removeDuplicates()
            .dropFirst()
            .sink { [weak self] account in
                if account != nil {
                    self?.lifecycleManager.handleActiveAccountChanged()
                }
            }
            .store(in: &cancellables)

        accountService.$activeAccount
            .compactMap { $0?.dashboardSettings?.dashboardType }
            .removeDuplicates()
            .sink { [weak self] _ in
                self?.lifecycleManager.handleDashboardTypeChange()
            }
            .store(in: &cancellables)
    }

    // MARK: - Display State Computed Properties

    var shouldShowGoalCardOrStreaks: Bool {
        return !state.ui.isGoalCardRemoved || !streakItemsToShow.isEmpty
    }

    var hasBodyMetrics: Bool { !metricsToShow.isEmpty }

    var shouldShowBodyMetrics: Bool {
        if !state.ui.hasLoadedDashboardConfig {
            guard let dashboardMetrics = accountService.activeAccount?.dashboardSettings?.dashboardMetrics,
                  !dashboardMetrics.isEmpty
            else { return false }
            let metrics = dashboardMetrics.split(separator: ",").map(String.init)
            return !metrics.isEmpty && metrics.allSatisfy { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        } else {
            return hasBodyMetrics
        }
    }

    var shouldShowBodyMetricsSkeleton: Bool {
        shouldShowBodyMetrics &&
            (!state.ui.hasLoadedDashboardConfig || !state.ui.hasLoadedMetricValues)
    }

    var shouldShowProgressMetricsSkeleton: Bool {
        if !state.ui.hasLoadedProgressMetrics { return true }
        if !shouldShowGoalStreakSection { return false }
        let needsStreakOrder = !state.ui.isEditMode && !streakItemsToShow.isEmpty && state.ui.streakGridOrder.isEmpty
        return needsStreakOrder
    }

    var skeletonProgressMetricsHasContentAbove: Bool { shouldShowBodyMetrics }

    var shouldShowDivider: Bool {
        let hasBodyMetricsToShow = shouldShowBodyMetrics
        let hasProgressMetricsToShow = shouldShowProgressMetricsSkeleton || shouldShowGoalStreakSection
        return hasBodyMetricsToShow && hasProgressMetricsToShow
    }

    var shouldShowGoalStreakSection: Bool {
        state.ui.hasLoadedDashboardConfig && hasGoalOrStreaks
    }

    private var hasGoalOrStreaks: Bool {
        !streakItemsToShow.isEmpty || !state.ui.isGoalCardRemoved
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
        guard state.ui.hasLoadedDashboardConfig else { return [] }
        return metricsManager.getMetricsToShow(
            isEditMode: state.ui.isEditMode,
            dashboardType: effectiveDashboardType,
            removedMetrics: state.ui.removedMetrics
        )
    }

    var effectiveDashboardType: DashboardType {
        return state.metrics.dashboardType
    }

    var streakColumns: [GridItem] {
        return streakManager.getStreakGridColumns()
    }

    var streakItemsToShow: [MetricItem] {
        let allStreaks = streakManager.state.streakItems
        guard !allStreaks.isEmpty else { return [] }
        if !state.ui.isEditMode, !state.ui.hasLoadedProgressMetrics { return allStreaks }
        let nonRemoved = allStreaks.filter { !state.ui.removedStreaks.contains($0.label) }
        let removed = allStreaks.filter { state.ui.removedStreaks.contains($0.label) }
        return state.ui.isEditMode ? nonRemoved + removed : nonRemoved
    }

    var isAnyItemBeingDragged: Bool { state.ui.isAnyItemBeingDragged }

    var allContentRemoved: Bool {
        let allMetricsRemoved = !state.ui.isEditMode &&
            metricsManager.state.metrics.allSatisfy { state.ui.removedMetrics.contains($0.label) }
        let allStreaksRemoved = !state.ui.isEditMode &&
            streakManager.state.streakItems.allSatisfy { state.ui.removedStreaks.contains($0.label) }
        return metricsToShow.isEmpty &&
            (!state.ui.isEditMode && state.ui.isGoalCardRemoved) &&
            (!streakManager.shouldShowStreakGrid()) &&
            allMetricsRemoved && allStreaksRemoved
    }

    var hasGoalSet: Bool { state.goal.hasGoalSet }

    var shouldShowStreakGrid: Bool {
        let visibleStreaks = streakItemsToShow.filter { !state.ui.removedStreaks.contains($0.label) }
        return !visibleStreaks.isEmpty
    }

    // MARK: - Cached Operations (Performance Optimization)

    var continuousOperations: [BathScaleWeightSummary] {
        return cacheManager.getContinuousOperations(for: state.graph.selectedPeriod) {
            dataManager.getContinuousOperations(for: state.graph.selectedPeriod)
        }
    }

    func invalidateContinuousOperationsCache() {
        cacheManager.invalidateContinuousOperationsCache()
    }

    var visibleOperations: [BathScaleWeightSummary] {
        return cacheManager.getVisibleOperations(isScrolling: state.graph.isScrolling) {
            graphManager.getVisibleOperations(from: continuousOperations)
        }
    }

    var chartSeriesData: [GraphSeries] {
        return cacheManager.getChartSeriesData(
            isScrolling: state.graph.isScrolling,
            isProcessingScrollEnd: chartManager.isProcessingScrollEnd,
            period: state.graph.selectedPeriod,
            selectedMetric: state.ui.selectedMetricLabel,
            operationsCount: continuousOperations.count,
            yAxisDomain: chartManager.yAxisDomain
        ) {
            graphManager.generateChartDataWithYAxisDomain(
                from: continuousOperations,
                visibleOperations: visibleOperations,
                selectedMetric: state.ui.selectedMetricLabel,
                isWeightlessMode: isWeightlessModeEnabled,
                anchorWeight: weightlessAnchorWeight,
                convertWeight: goalManager.convertWeightToDisplay,
                yAxisDomain: chartManager.yAxisDomain
            )
        }
    }

    var hasAnyEntries: Bool { state.data.hasAnyEntries }

    // MARK: - Account & Weight Properties

    var isWeightlessModeEnabled: Bool {
        accountService.activeAccount?.weightlessSettings?.isWeightlessOn ?? false
    }

    var weightlessAnchorWeight: Double? {
        guard let weightlessSettings = accountService.activeAccount?.weightlessSettings,
              weightlessSettings.isWeightlessOn,
              let weightlessWeight = weightlessSettings.weightlessWeight
        else { return nil }
        return goalManager.convertWeightToDisplay(Int(weightlessWeight))
    }

    var goalWeightForDisplay: Double? {
        return goalManager.getGoalWeightForDisplay(
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight
        )
    }

    var selectedBodyMetric: BodyMetric {
        guard let selectedLabel = state.ui.selectedMetricLabel else { return .weight }
        return metricsManager.getBodyMetric(for: selectedLabel)
    }

    var currentUnit: WeightUnit { accountService.activeAccount?.weightSettings?.weightUnit ?? .lb }
    var currentUnitString: String { currentUnit.rawValue }
    var currentUnitText: String { accountService.activeAccount?.weightSettings?.weightUnit?.rawValue ?? "lbs" }
    var currentWeightlessMode: Bool { accountService.activeAccount?.weightlessSettings?.isWeightlessOn ?? false }
    var unitText: String { goalManager.getUnitText() }

    var hasEntriesButNoneInCurrentPeriod: Bool {
        return goalManager.hasEntriesButNoneInCurrentPeriod(continuousOperations: continuousOperations, visibleOperations: visibleOperations)
    }

    func visibleDomainLength(for period: TimePeriod) -> TimeInterval {
        return graphManager.visibleDomainLength(for: period)
    }

    // MARK: - Edit Session

    func currentEditSnapshot() -> EditSessionSnapshot {
        EditSessionSnapshot(
            metrics: metricsManager.state.metrics,
            activeMetricsCount: metricsManager.state.activeMetricsCount,
            streakItems: streakManager.state.streakItems,
            activeStreakItemsCount: streakManager.state.activeStreakItemsCount,
            isGoalCardRemoved: state.ui.isGoalCardRemoved,
            goalCardPosition: state.ui.goalCardPosition,
            streakGridOrder: state.ui.streakGridOrder,
            removedMetrics: state.ui.removedMetrics,
            removedStreaks: state.ui.removedStreaks
        )
    }

    func restoreFromSnapshot(_ snapshot: EditSessionSnapshot) {
        metricsManager.state.metrics = snapshot.metrics
        metricsManager.state.activeMetricsCount = snapshot.activeMetricsCount
        streakManager.state.streakItems = snapshot.streakItems
        streakManager.state.activeStreakItemsCount = snapshot.activeStreakItemsCount
        state.ui.isGoalCardRemoved = snapshot.isGoalCardRemoved
        state.ui.goalCardPosition = snapshot.goalCardPosition
        state.ui.streakGridOrder = snapshot.streakGridOrder
        state.ui.removedMetrics = snapshot.removedMetrics
        state.ui.removedStreaks = snapshot.removedStreaks
    }

    func beginEdit() {
        editSessionManager.takeSnapshot(currentEditSnapshot())
    }

    func updateSnapshot() {
        editSessionManager.updateSnapshot(currentEditSnapshot())
    }

    func hasUnsavedChanges() -> Bool {
        editSessionManager.hasUnsavedChanges(current: currentEditSnapshot())
    }

    func cancelEdit() {
        if let snapshot = editSessionManager.snapshot {
            restoreFromSnapshot(snapshot)
        }
        gridEditingManager.syncRemovalStateFromMetricsManager()
        state.ui.selectedMetricLabel = nil
        state.ui.draggingMetric = nil
        state.ui.draggingStreak = nil
        state.ui.dropHoverId = nil
        withAnimation(.easeInOut(duration: 0.2)) {
            state.ui.isEditMode = false
        }
        editSessionManager.clearSnapshot()
        forceImmediateUIUpdate()
    }

    func resetEditSession() {
        if let snapshot = editSessionManager.snapshot {
            restoreFromSnapshot(snapshot)
        }
        metricsManager.resetOrderToDefault()
        if state.metrics.dashboardType == .dashboard12 {
            metricsManager.resetActiveMetricsCountToShowAll()
        }
        gridEditingManager.syncRemovalStateFromMetricsManager()
        gridEditingManager.syncRemovalStateFromStreakManager()
        state.ui.selectedMetricLabel = nil
        state.ui.draggingMetric = nil
        state.ui.draggingStreak = nil
        state.ui.dropHoverId = nil
        editSessionManager.clearSnapshot()
        beginEdit()
        forceImmediateUIUpdate()
    }

}
