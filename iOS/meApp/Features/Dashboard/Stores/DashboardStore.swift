// swiftlint:disable file_length
// DashboardStore - Coordinator for dashboard state management.
// Business logic is delegated to specialized managers.

import Charts
import Combine
import Foundation
import SwiftUI

/// Simplified DashboardStore focused on coordination between managers
/// Uses specialized managers for business logic while exposing centralized state for UI
@MainActor
// swiftlint:disable:next type_body_length
class DashboardStore: ObservableObject, DashboardStateProviding {
    private struct DataContentSignature: Equatable {
        let daily: [String]
        let monthly: [String]
    }

    // MARK: - Dependencies

    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector var accountService: AccountService
    @Injector var logger: LoggerService
    @Injector private var deviceService: DeviceService
    @Injector private var entryService: EntryService
    @Injector private var productTypeStore: ProductTypeStoreProtocol

    // MARK: - Formatter and Cache Services
    let formatter: DashboardFormatterProtocol
    let cacheManager: DashboardCacheManagerProtocol

    // MARK: - Centralized State

    @Published var state = DashboardState()
    private(set) var dataChangeRevision: Int = 0
    @Published private(set) var hasBabySnapshotItem: Bool = false
    @Published private(set) var canShowSnapshotOverview: Bool = false

    /// The active product type for the dashboard (weight or BPM).
    @Published var productType: EntryType = .scale
    @Published private(set) var availableProductItems: [ProductSelection] = []
    @Published private(set) var selectedProductItem: ProductSelection = .myWeight

    /// MOB-518 v2 (A3): the single published source of truth for the new weight chart. Built by
    /// `rebuildWeightChartModel(scrollPosition:)`; `WeightChartHost` observes it (no local `@State` copy).
    /// Weight (`.scale`) only for now — baby/BPM stay on the legacy engine. `nil` until first build.
    @Published private(set) var chartModel: ChartModel?

    // MARK: - Private Properties

    private var cancellables = Set<AnyCancellable>()
    private var lastAccountSettingsSnapshot: AccountSettingsSnapshot?
    private var lastDataContentSignature = DataContentSignature(daily: [], monthly: [])
    /// Guards against the Combine subscriber double-firing when `selectProductItem` updates state directly.
    private var isSelectingDirectly = false

    // MARK: - UI Update Batching (Performance Optimization)

    private var pendingUIUpdate = false
    private var uiUpdateDebounceTask: Task<Void, Never>?

    // MARK: - Initialization Tracking

    private var initializationTask: Task<Void, Never>?
    @Published private(set) var isInitialized: Bool = false

    private static func makeDataContentSignature(from state: DataState) -> DataContentSignature {
        DataContentSignature(
            daily: state.dailySummaries.compactMap { summary in
                guard let summary else { return nil }
                return "\(summary.period)|\(summary.entryTimestamp)|\(summary.weight)"
            },
            monthly: state.monthlySummaries.compactMap { summary in
                guard let summary else { return nil }
                return "\(summary.period)|\(summary.entryTimestamp)|\(summary.weight)"
            }
        )
    }

    var isBabySelection: Bool {
        selectedBabyProfile != nil
    }

    var selectedBabyProfile: BabyProfile? {
        if case .baby(let profile) = selectedProductItem {
            return profile
        }
        return nil
    }

    /// True when the baby product is selected but no real baby profile exists yet — i.e. the
    /// "Baby Scale" placeholder selection. Drives the "No babies added yet" / ADD A BABY empty
    /// state (a profile, not a device, is the blocker per MOB-1245).
    var isPendingBabySelection: Bool {
        selectedBabyProfile?.isPendingSelection ?? false
    }

    var selectedBabyMetric: BabyMetric {
        state.ui.selectedMetricLabel == BabyMetric.height.rawValue ? .height : .weight
    }

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
        availableProductItems = productTypeStore.availableItems
        selectedProductItem = productTypeStore.selectedItem

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
    private func initializeCoordinatingManagers() { // swiftlint:disable:this function_body_length
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

    // swiftlint:disable:next function_body_length cyclomatic_complexity
    private func setupBindings() {
        metricsManager.$state
            .sink { [weak self] metricsState in
                guard let self = self else { return }
                guard self.state.metrics != metricsState else { return }
                if !self.state.ui.isResettingDashboard {
                    self.state.metrics = metricsState
                }
            }
            .store(in: &cancellables)

        streakManager.$state
            .sink { [weak self] streakState in
                guard let self = self else { return }
                guard self.state.streak != streakState else { return }
                if !self.state.ui.isResettingDashboard {
                    self.state.streak = streakState
                }
            }
            .store(in: &cancellables)

        goalManager.$state
            .sink { [weak self] goalState in
                guard let self, self.state.goal != goalState else { return }
                self.state.goal = goalState
            }
            .store(in: &cancellables)

        graphManager.$state
            .sink { [weak self] graphState in
                guard let self, self.state.graph != graphState else { return }
                self.state.graph = graphState
            }
            .store(in: &cancellables)

        dataManager.$state
            .sink { [weak self] dataState in
                guard let self, self.state.data != dataState else { return }
                let nextSignature = Self.makeDataContentSignature(from: dataState)
                if self.lastDataContentSignature != nextSignature {
                    self.dataChangeRevision &+= 1
                    self.lastDataContentSignature = nextSignature
                }
                self.state.data = dataState
            }
            .store(in: &cancellables)

        dataManager.$state
            .map(Self.makeDataContentSignature)
            .removeDuplicates()
            .dropFirst()
            .sink { [weak self] _ in
                guard let self else { return }
                self.invalidateContinuousOperationsCache()
                self.graphManager.forceVisibleOperationsRecalculation()
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
        setupEntryServiceSubscriptions()
        setupAccountServiceSubscriptions()
        setupProductTypeStoreSubscriptions()
    }

    private func setupEntryServiceSubscriptions() {
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
    }

    private func setupAccountServiceSubscriptions() {
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
            .compactMap { $0?.dashboardType }
            .removeDuplicates()
            // `@Published` fires in `willSet`, so the sink runs before `activeAccount` is
            // committed. `handleDashboardTypeChange()` re-reads `activeAccount`, so hop to the
            // next main-actor turn to ensure it reads the new value rather than the stale one.
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.lifecycleManager.handleDashboardTypeChange()
            }
            .store(in: &cancellables)
    }

    private func setupProductTypeStoreSubscriptions() {
        productTypeStore.availableItemsPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] items in
                guard let self else { return }
                self.availableProductItems = items
                self.hasBabySnapshotItem = items.contains {
                    if case .baby = $0 { return true }
                    return false
                }
                // Show the snapshot overview ONLY when there are 2+ distinct product
                // categories to choose between (weight / blood pressure / baby). A
                // single-category account has nothing to pick, so it drills straight into
                // that product's detail dashboard:
                //   • Baby-only (with or without a profile) → the baby trend scaffold, which
                //     renders the empty chart + "No babies added yet" footer for the pending
                //     state (MOB-1245). Multiple baby profiles are switched via the header
                //     dropdown, not the overview — see navbarHeader().
                //   • Weight-only / BP-only → that product's detail dashboard.
                // Multiple baby profiles must NOT inflate the count — baby is one category.
                let hasWeight = items.contains { $0 == .myWeight }
                let hasBpm = items.contains { $0 == .myBloodPressure }
                let productCategoryCount = [hasWeight, hasBpm, self.hasBabySnapshotItem]
                    .filter { $0 }.count
                self.canShowSnapshotOverview = productCategoryCount > 1
            }
            .store(in: &cancellables)

        productTypeStore.selectedItemPublisher
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] selection in
                guard let self, !self.isSelectingDirectly else { return }
                let previousSelection = self.selectedProductItem
                self.selectedProductItem = selection
                let newType = selection.entryType
                if self.productType != newType {
                    self.switchProductType(to: newType)
                } else if previousSelection != selection {
                    self.refreshSelectedProductContext()
                }
            }
            .store(in: &cancellables)
    }

    // MARK: - Product Type Switching

    func switchProductType(to newType: EntryType) {
        guard productType != newType else { return }
        productType = newType
        dataManager.switchDataSource(to: newType)
        resetGraphStateForProductSwitch(productType: newType)
        state.ui.selectedMetricLabel = nil
        chartManager?.clearSelection()
        chartManager?.clearAllCaches()
        cacheManager.setProductContext(productType: newType, babyProfileId: selectedBabyProfile?.id)
        cacheManager.clearAllCaches()
        state.ui.hasInitializedChart = false
        graphManager.state.isGraphReady = false
        Task { [weak self] in
            guard let self else { return }
            await self.lifecycleManager.initializeDashboard()
        }
    }

    private func refreshSelectedProductContext() {
        resetGraphStateForProductSwitch(productType: productType)
        state.ui.selectedMetricLabel = nil
        chartManager?.clearSelection()
        chartManager?.clearAllCaches()
        cacheManager.setProductContext(productType: productType, babyProfileId: selectedBabyProfile?.id)
        cacheManager.clearAllCaches()
        invalidateContinuousOperationsCache()
        state.ui.hasInitializedChart = false
        graphManager.state.isGraphReady = false

        if isBabySelection, let babyProfile = selectedBabyProfile {
            // Load real baby entries, then initialize the chart.
            // Falls back to dummy data if no real entries exist (handled in continuousOperations).
            Task { [weak self] in
                guard let self else { return }
                await self.entryService.loadBabyDashboardData(babyId: babyProfile.id)
                self.invalidateContinuousOperationsCache()
                self.chartManager?.initializeChart()
                self.displayManager.updateMetricsForCurrentView()
                self.scheduleUIUpdate()
            }
        } else {
            Task { [weak self] in
                guard let self else { return }
                await self.lifecycleManager.initializeDashboard()
            }
        }
    }

    func selectProductItem(_ item: ProductSelection) {
        let previousSelection = selectedProductItem
        // Suppress the Combine subscriber while we handle selection directly.
        isSelectingDirectly = true
        defer { isSelectingDirectly = false }

        selectedProductItem = item
        productTypeStore.select(item)

        guard previousSelection != item else { return }
        let newType = item.entryType
        if productType != newType {
            switchProductType(to: newType)
        } else {
            refreshSelectedProductContext()
        }
    }

    // MARK: - Baby Data Access

    /// Date range the baby percentile reference curves should span: the visible chart window
    /// (scroll position → one visible-domain length later), unioned with the actual data extent.
    /// Spanning the window — instead of the sparse operations' min/max — keeps the WHO/CDC curves
    /// filling the chart even when the baby has a single weight entry.
    private func babyChartVisibleDateRange() -> ClosedRange<Date> {
        let period = state.graph.selectedPeriod
        let start = state.graph.xScrollPosition
        let end = start.addingTimeInterval(visibleDomainLength(for: period))
        let operationDates = continuousOperations.map(\.date)
        let rawLower = min(start, end, operationDates.min() ?? start)
        let rawUpper = max(start, end, operationDates.max() ?? end)
        // Snap the lower bound to the period boundary so the percentile curves fill the chart
        // from the same edge the X-axis domain starts at (see babyScrollDomainCap) — otherwise
        // the leading portion of the grid (e.g. the 1st–29th) renders without reference curves.
        // Shared with the chart's domainMin via TimePeriod.periodStart so the two can't drift.
        let lower = period.periodStart(for: rawLower)
        return lower...max(rawUpper, lower)
    }

    /// Returns real baby summaries from EntryService for the given profile and period.
    /// Uses daily summaries for week/month and monthly summaries for year/total.
    private func babySummaries(for babyProfile: BabyProfile, period: TimePeriod) -> [BathScaleWeightSummary] {
        switch period {
        case .week, .month:
            return entryService.babyDailySummariesByProfile[babyProfile.id] ?? []
        case .year, .total:
            return entryService.babyMonthlySummariesByProfile[babyProfile.id] ?? []
        }
    }

    /// Clears leftover Y-axis domain, ticks, selection, and scroll state from the
    /// previous product type so the new chart starts fresh without visual glitches.
    private func resetGraphStateForProductSwitch(productType _: EntryType) {
        graphManager.endScrollingImmediately()
        graphManager.state.updateScrollState(isScrolling: false)
        graphManager.state.cachedYAxisDomain = nil
        graphManager.state.cachedYAxisTicks = nil
        graphManager.state.selectedXValue = nil
        graphManager.state.selectedPoint = nil
        graphManager.state.showCrosshair = false
        state.graph.cachedYAxisDomain = nil
        state.graph.cachedYAxisTicks = nil
        state.graph.selectedXValue = nil
        state.graph.selectedPoint = nil
        state.graph.showCrosshair = false
        state.graph.isScrolling = false
        state.graph.hasDetectedScrollInCurrentGesture = false
        BabyDashboardChartSupport.clearDummySummariesCache()
    }

    var productTypeSelectorStore: ProductTypeStore {
        ProductTypeStore.shared
    }

    func clearProductTypeSelection() {
        productTypeStore.clearPersistedSelection()
    }

    var dashboardEntryService: EntryService {
        entryService
    }

    // MARK: - Display State Computed Properties

    var shouldShowGoalCardOrStreaks: Bool {
        return !state.ui.isGoalCardRemoved || !streakItemsToShow.isEmpty
    }

    var hasBodyMetrics: Bool { !metricsToShow.isEmpty }

    var shouldShowBodyMetrics: Bool {
        if !state.ui.hasLoadedDashboardConfig {
            guard let dashboardMetrics = accountService.activeAccount?.dashboardMetrics,
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
        if let babyProfile = selectedBabyProfile {
            return cacheManager.getContinuousOperations(for: state.graph.selectedPeriod) {
                let realSummaries = self.babySummaries(for: babyProfile, period: state.graph.selectedPeriod)
                if !realSummaries.isEmpty {
                    return realSummaries
                }
                // Fallback to dummy data when no real baby entries exist
                return BabyDashboardChartSupport.dummySummaries(
                    for: babyProfile,
                    period: state.graph.selectedPeriod
                )
            }
        }

        let operations = cacheManager.getContinuousOperations(for: state.graph.selectedPeriod) {
            dataManager.getContinuousOperations(for: state.graph.selectedPeriod)
        }
        if !operations.isEmpty {
            return operations
        }

        // Tests and a few initialization paths seed summaries directly onto state before
        // DashboardDataManager's published-cache bindings have populated. The manager's
        // pre-sorted cache is empty in that window, so fall back to the store's own
        // state-backed summaries. This path is only reached when the manager cache is
        // empty (init race / direct state seeding), so it is not on the scroll hot path.
        switch state.graph.selectedPeriod {
        case .week, .month:
            return state.data.dailySummaries.compactMap { $0 }.sorted { $0.date < $1.date }
        case .year, .total:
            return state.data.monthlySummaries.compactMap { $0 }.sorted { $0.date < $1.date }
        }
    }

    func invalidateContinuousOperationsCache() {
        cacheManager.invalidateContinuousOperationsCache()
    }

    var visibleOperations: [BathScaleWeightSummary] {
        let operations = continuousOperations
        return cacheManager.getVisibleOperations(isScrolling: state.graph.isScrolling) {
            graphManager.getVisibleOperations(from: operations)
        }
    }

    var chartSeriesData: [GraphSeries] {
        let operations = continuousOperations
        let period = state.graph.selectedPeriod

        // For non-total periods, window operations to a buffer around the visible
        // range so we avoid iterating all 10k+ entries during series building.
        let windowedOps: [BathScaleWeightSummary]
        if period == .total {
            windowedOps = operations
        } else {
            windowedOps = graphManager.getChartOperationsWithBuffer(
                from: operations,
                scrollPosition: state.graph.xScrollPosition,
                period: period
            )
        }

        // BPM uses its own 3-series builder; weight uses the existing pipeline
        if productType == .bpm {
            return cacheManager.getChartSeriesData(
                isScrolling: state.graph.isScrolling,
                isProcessingScrollEnd: chartManager.isProcessingScrollEnd,
                period: period,
                selectedMetric: nil,
                operationsCount: operations.count,
                yAxisDomain: chartManager.yAxisDomain
            ) {
                graphManager.generateBpmChartData(from: windowedOps)
            }
        }
        if let babyProfile = selectedBabyProfile {
            return cacheManager.getChartSeriesData(
                isScrolling: state.graph.isScrolling,
                isProcessingScrollEnd: chartManager.isProcessingScrollEnd,
                period: period,
                selectedMetric: selectedBabyMetric == .height ? BabyMetric.height.rawValue : nil,
                operationsCount: operations.count,
                yAxisDomain: chartManager.yAxisDomain
            ) {
                graphManager.generateBabyChartData(
                    from: windowedOps,
                    visibleOperations: visibleOperations,
                    babyProfile: babyProfile,
                    metric: selectedBabyMetric,
                    convertWeight: goalManager.convertWeightToDisplay,
                    convertDecigramsToDisplay: convertBabyDecigramsToDisplay,
                    yAxisDomain: chartManager.yAxisDomain,
                    percentileDateRange: babyChartVisibleDateRange()
                )
            }
        }
        return cacheManager.getChartSeriesData(
            isScrolling: state.graph.isScrolling,
            isProcessingScrollEnd: chartManager.isProcessingScrollEnd,
            period: period,
            selectedMetric: state.ui.selectedMetricLabel,
            operationsCount: operations.count,
            yAxisDomain: chartManager.yAxisDomain
        ) {
            graphManager.generateChartDataWithYAxisDomain(
                from: windowedOps,
                visibleOperations: visibleOperations,
                selectedMetric: state.ui.selectedMetricLabel,
                isWeightlessMode: isWeightlessModeEnabled,
                anchorWeight: weightlessAnchorWeight,
                convertWeight: goalManager.convertWeightToDisplay,
                yAxisDomain: chartManager.yAxisDomain
            )
        }
    }

    func yAxisScale(for operations: [BathScaleWeightSummary], chartHeight: CGFloat) -> YAxisScale {
        if productType == .bpm {
            return graphManager.getBpmYAxisScale(from: operations, chartHeight: chartHeight)
        }

        if let babyProfile = selectedBabyProfile {
            let percentileDateRange = babyChartVisibleDateRange()
            switch selectedBabyMetric {
            case .weight:
                return BabyDashboardChartSupport.yAxisScale(
                    for: operations,
                    babyProfile: babyProfile,
                    dateRange: percentileDateRange,
                    convertStoredWeightToDisplay: goalManager.convertWeightToDisplay,
                    convertDecigramsToDisplay: convertBabyDecigramsToDisplay
                )
            case .height:
                return BabyDashboardChartSupport.heightYAxisScale(
                    for: operations,
                    babyProfile: babyProfile,
                    dateRange: percentileDateRange
                )
            }
        }

        return graphManager.getYAxisScale(
            from: operations,
            goalWeight: goalWeightForDisplay,
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight,
            convertWeight: goalManager.convertWeightToDisplay,
            chartHeight: chartHeight
        )
    }

    // MARK: - v2 Weight-Chart Engine (MOB-518)

    /// Rebuilds the immutable v2 `ChartModel` for the weight graph at `scrollPosition` and publishes it into
    /// `chartModel`, on the main actor, from the same inputs the legacy series/y-axis paths use
    /// (`continuousOperations`, `goalWeightForDisplay`, weightless flags, `convertWeightToDisplay`,
    /// `state.graph.chartHeight`) — so output is at parity. Called by `WeightChartHost` only when a
    /// rebuild-relevant input changes (data / period / unit / goal / scroll-settle) — never per scroll frame.
    /// Weight only for now; baby/BPM stay on the legacy engine.
    func rebuildWeightChartModel(scrollPosition: Date) {
        chartModel = ChartPrep.buildWeight(
            operations: continuousOperations,
            period: state.graph.selectedPeriod,
            scrollPosition: scrollPosition,
            goalWeight: goalWeightForDisplay,
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight,
            convertWeight: goalManager.convertWeightToDisplay,
            chartHeight: state.graph.chartHeight
        )
    }

    /// Scroll-END settle: recompute ONLY the adaptive y-axis for the landed window and update `chartModel`
    /// in place via `ChartModel.withYAxis` — the series + x-geometry (`xDomain` / `visibleDomainLength` /
    /// `xAxisTicks`) are left byte-identical so Swift Charts does NOT rebuild its scroll view (which was the
    /// "can't scroll for ~1 s after a scroll stops" hitch). The y-window reuses the model's frozen
    /// `visibleDomainLength` so the axis matches the on-screen window. No-op if the model isn't built yet.
    /// Weight only; baby/BPM stay on the legacy engine.
    func resettleWeightYAxis(scrollPosition: Date) {
        guard let current = chartModel else {
            rebuildWeightChartModel(scrollPosition: scrollPosition)
            return
        }
        let newYAxis = ChartPrep.weightYAxis(
            operations: continuousOperations,
            period: state.graph.selectedPeriod,
            scrollPosition: scrollPosition,
            visibleDomainLength: current.visibleDomainLength,
            goalWeight: goalWeightForDisplay,
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight,
            convertWeight: goalManager.convertWeightToDisplay,
            chartHeight: state.graph.chartHeight
        )
        guard newYAxis != current.yAxis else { return }
        chartModel = current.withYAxis(newYAxis)
    }

    /// V4 (6a): apply a validated weight-chart selection at `date` (already snapped to a real entry by the
    /// host), resolving `selectedPoint`/`selectedXValue`/`showCrosshair` per period exactly as the legacy
    /// programmatic auto-select does (`applyChartSelectionSync`). `nil` clears the selection. Weight only.
    func selectWeightPoint(at date: Date?) {
        guard let date else {
            graphManager.state.clearSelection()
            return
        }
        graphManager.applyChartSelectionSync(at: date, operations: continuousOperations)
    }

    var hasAnyEntries: Bool { state.data.hasAnyEntries }

    /// True when the selected baby profile has at least one real (non-dummy) scale reading.
    var hasBabyEntries: Bool {
        guard let babyProfile = selectedBabyProfile else { return false }
        let daily = entryService.babyDailySummariesByProfile[babyProfile.id] ?? []
        let monthly = entryService.babyMonthlySummariesByProfile[babyProfile.id] ?? []
        return !daily.isEmpty || !monthly.isEmpty
    }

    // MARK: - Account & Weight Properties

    var isWeightlessModeEnabled: Bool {
        accountService.activeAccount?.isWeightlessOn ?? false
    }

    var weightlessAnchorWeight: Double? {
        guard let account = accountService.activeAccount,
              account.isWeightlessOn,
              let weightlessWeight = account.weightlessWeight
        else { return nil }
        let storedWeight = Int(weightlessWeight)
        let unit = account.weightUnit
        return unit == .kg
            ? ConversionTools.convertStoredToKgRaw(Double(storedWeight))
            : ConversionTools.convertStoredToLbs(storedWeight)
    }

    var goalWeightForDisplay: Double? {
        return goalManager.getGoalWeightForDisplay(
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight
        )
    }

    private func convertBabyDecigramsToDisplay(_ decigrams: Int) -> Double {
        let isMetric = accountService.activeAccount?.weightUnit == .kg
        // swiftlint:disable:next todo
        // TODO: Remove this fallback once baby-scale conversion is confirmed and
        // implemented separately per SKU type.
        let kg = Double(decigrams) / BabyPercentileGrowthReference.decigramsToKgFactor
        let stored = ConversionTools.convertKgToStored(kg)
        return isMetric
            ? ConversionTools.convertStoredToKg(stored)
            : ConversionTools.convertStoredToLbs(stored)
    }

    var selectedBodyMetric: BodyMetric {
        guard let selectedLabel = state.ui.selectedMetricLabel else { return .weight }
        return metricsManager.getBodyMetric(for: selectedLabel)
    }

    var currentUnit: WeightUnit { accountService.activeAccount?.weightUnit ?? .lb }
    var currentUnitString: String { currentUnit.rawValue }
    var currentUnitText: String { accountService.activeAccount?.weightUnit.rawValue ?? "lb" }
    var currentWeightlessMode: Bool { accountService.activeAccount?.isWeightlessOn ?? false }
    var currentMeasurementUnits: MeasurementUnits {
        guard let raw = accountService.activeAccount?.measurementUnits,
              let units = MeasurementUnits(rawValue: raw) else { return .imperialLbOz }
        return units
    }
    var unitText: String { goalManager.getUnitText() }

    var hasEntriesButNoneInCurrentPeriod: Bool {
        let operations = continuousOperations
        let visible = visibleOperations
        return goalManager.hasEntriesButNoneInCurrentPeriod(
            continuousOperations: operations,
            visibleOperations: visible
        )
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
