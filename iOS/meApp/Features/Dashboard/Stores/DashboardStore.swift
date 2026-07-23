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

    /// Mirrors `EntryService.isSyncing`. Combined with `continuousOperations.isEmpty` in
    /// `GraphView.shouldShowSkeleton`, it keeps the skeleton up during the FIRST-login sync —
    /// local SwiftData is empty until that sync lands, so without this the fixed 300 ms
    /// `isGraphReady` timer hid the skeleton into an empty graph (MOB-516).
    @Published private(set) var isSyncing: Bool = false

    /// MOB-1726: `true` once the FIRST entry sync of this account session has finished. The MOB-516
    /// skeleton guard (`GraphView.shouldShowSkeleton`) keeps the skeleton up while `isSyncing` and the
    /// local store is still empty — its purpose is the FIRST-login full-history sync, where an empty
    /// `continuousOperations` just means "data hasn't landed yet". But every product switch also kicks
    /// off a sync, so on a switch to a genuinely-empty product (e.g. Weight/BPM with no readings) that
    /// guard re-showed the skeleton mid-sync — the "graph loads twice" flash. Gating the guard on
    /// `!hasCompletedInitialSync` limits it to the initial sync: afterwards an empty product is known
    /// empty, so later syncs no longer re-trigger the skeleton. Reset on account switch so a new
    /// account's first-login sync re-arms it.
    @Published private(set) var hasCompletedInitialSync: Bool = false

    /// MOB-1726: `false` while a persisted product selection (e.g. a baby) is still being restored
    /// asynchronously by `ProductTypeStore` — during that window the store still holds the default
    /// (`My Weight`) selection, so `DashboardScreen` shows a neutral skeleton instead of building the
    /// weight header + graph and then flipping to baby (the "weight renders first, then changes to
    /// baby" flash on launch). Flips `true` once the applied selection matches the persisted id, once
    /// the user picks a product explicitly, or via a bounded fallback so it can never hang.
    @Published private(set) var hasResolvedInitialProduct: Bool = false

    /// True when the persisted (to-be-restored) selection is a baby, so the resolving-state skeleton
    /// can use the taller baby height and avoid a resize when the real baby graph lands.
    var pendingPersistedProductIsBaby: Bool {
        (productTypeStore.persistedSelectionId ?? "").hasPrefix("baby_")
    }

    /// Whether the currently-applied selection already matches what will be restored from storage
    /// (or there is nothing persisted to wait for).
    private var isPersistedSelectionApplied: Bool {
        guard let persistedId = productTypeStore.persistedSelectionId else { return true }
        return selectedProductItem.id == persistedId
    }

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
                return summaryContentToken(summary)
            },
            monthly: state.monthlySummaries.compactMap { summary in
                guard let summary else { return nil }
                return summaryContentToken(summary)
            }
        )
    }

    /// Builds the per-summary token used for dashboard change detection. Must include every
    /// plotted value across products, not just `weight`: BP summaries carry `weight = 0` and
    /// vary only by systolic/diastolic/pulse, and baby summaries by length — so a weight-only
    /// token misses BP/baby edits, leaving the graph stuck at its initial (zero) state on
    /// add/edit (the chart only rebuilds when `dataChangeRevision` ticks).
    private static func summaryContentToken(_ summary: BathScaleWeightSummary) -> String {
        let bp = "\(summary.systolic ?? 0)|\(summary.diastolic ?? 0)|\(summary.pulse ?? 0)"
        return "\(summary.period)|\(summary.entryTimestamp)|\(summary.weight)|\(bp)|\(summary.babyLengthInches ?? 0)"
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

    /// MOB-1726 review: exposes the selected baby id on `DashboardStateProviding` so managers read it
    /// without downcasting to `DashboardStore`.
    var selectedBabyProfileId: String? { selectedBabyProfile?.id }

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

        // MOB-1726: align the active product to the (already-restored) persisted selection BEFORE the
        // first `initializeDashboard()` runs, so cold-open performs ONE initialization for the correct
        // product. Without this, init always starts on the default `.scale`, then the product
        // subscription delivers the restored selection and runs a SECOND `initializeDashboard()` via
        // `switchProductType` — the double skeleton (and, for baby, the flash of the empty graph) on
        // launch. Mirrors `switchProductType`'s context setup (data source + cache context) minus the
        // graph-state reset, which is a no-op before the chart is built.
        // Qualify with `self.` — inside `init` the bare names `cacheManager` resolve to the optional
        // init PARAMETER, not the stored property.
        let restoredProductType = selectedProductItem.entryType
        if productType != restoredProductType {
            productType = restoredProductType
            self.dataManager.switchDataSource(to: restoredProductType)
        }
        self.cacheManager.setProductContext(productType: restoredProductType, babyProfileId: selectedBabyProfile?.id)

        // MOB-1726: if the persisted product selection is already applied (or there is none), the
        // dashboard can render its product immediately; otherwise hold the resolving skeleton until the
        // async restore lands (see `hasResolvedInitialProduct`).
        hasResolvedInitialProduct = isPersistedSelectionApplied

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
            // MOB-1726: bounded fallback so the resolving-product skeleton can never hang if the
            // persisted selection never resolves (e.g. a persisted baby whose profile fails to load).
            // No-op when the product subscription already resolved it.
            if !hasResolvedInitialProduct {
                try? await Task.sleep(nanoseconds: 1_500_000_000)
                await MainActor.run { hasResolvedInitialProduct = true }
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
            getContinuousOperationsForPeriod: { [weak self] period in self?.continuousOperations(for: period) ?? [] },
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
                // Bust the memoized chart series too: its cache key is
                // operationsCount + yAxisDomain, both of which can stay constant when an
                // existing day's values change in place (e.g. editing a BP reading whose
                // systolic/diastolic land inside the current axis band). The content
                // signature changed here, so the series is genuinely stale.
                self.cacheManager.invalidateChartSeriesCache()
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

        // MOB-516: mirror the sync flag so the graph keeps the skeleton during the first-login
        // sync instead of flashing the empty-entries graph (see GraphView.shouldShowSkeleton).
        entryService.$isSyncing
            .removeDuplicates()
            .receive(on: DispatchQueue.main)
            .sink { [weak self] syncing in
                guard let self else { return }
                // MOB-1726: a true->false transition means the current sync just finished. Mark the
                // first-login sync complete so the MOB-516 skeleton guard stops firing on later syncs
                // (e.g. the sync every product switch kicks off) — otherwise switching to an empty
                // product re-shows the skeleton mid-sync (the "loads twice" flash).
                if self.isSyncing, !syncing {
                    self.hasCompletedInitialSync = true
                }
                self.isSyncing = syncing
            }
            .store(in: &cancellables)

        // MOB-1591: baby graph reactivity on add/delete. Baby summaries live in
        // `entryService.babyDailySummariesByProfile` / `babyMonthlySummariesByProfile` — a source the
        // `DashboardDataManager` never subscribes to (it binds only the weight/BPM daily/monthly summaries).
        // So a baby entry add/delete never ticked `dataChangeRevision`, `TrendChartHost.rebuildSignal` stayed
        // constant, and the v2 chart only caught up on a period switch (which rebuilds via a different
        // onChange). Mirror the weight path here: when baby summaries change, invalidate the ops cache and
        // tick `dataChangeRevision` (+ republish, since it isn't `@Published`) so `rebuildSignal` moves and
        // `TrendChartHost` rebuilds the model immediately. Guarded to the baby product so it's inert otherwise.
        entryService.$babyDailySummariesByProfile
            .combineLatest(entryService.$babyMonthlySummariesByProfile)
            .receive(on: DispatchQueue.main)
            .dropFirst()
            .sink { [weak self] _, _ in
                guard let self, self.productType == .baby else { return }
                self.invalidateContinuousOperationsCache()
                // MOB-1726: seed the model directly from the fresh summaries. Ticking `dataChangeRevision`
                // alone relies on `TrendChartHost` observing the change, but on a cold open with a persisted
                // baby the host can mount AFTER the tick and render the empty (collapsed, 265 pt) model until
                // a period switch forces a rebuild. Rebuilding here — after the ops-cache invalidation, so it
                // reads the fresh summaries — makes the populated baby model deterministic the moment its data
                // lands, independent of view mount timing.
                self.rebuildChartModel(scrollPosition: self.state.graph.xScrollPosition)
                self.dataChangeRevision &+= 1
                self.objectWillChange.send()
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
                self?.logger.log(
                    level: .info,
                    tag: "AcctFlowDebug",
                    message: "[Dashboard] activeAccount accountId emit → \(account ?? "nil"), "
                        + "willReload=\(account != nil)"
                )
                if account != nil {
                    // MOB-1726: a new account session re-arms the first-login skeleton guard, so its
                    // (possibly empty) local store shows the skeleton during its own initial sync.
                    self?.hasCompletedInitialSync = false
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
                // MOB-1726: the persisted product just landed (or any selection now matches storage) —
                // let the dashboard render its real scaffold instead of the resolving skeleton.
                if !self.hasResolvedInitialProduct, self.isPersistedSelectionApplied {
                    self.hasResolvedInitialProduct = true
                }
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
        // MOB-1726: an explicit user pick resolves the initial-product gate immediately (they chose,
        // so there is nothing left to wait for).
        hasResolvedInitialProduct = true
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
    }

    var productTypeSelectorStore: ProductTypeStore {
        ProductTypeStore.shared
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
                // Real baby entries only — no synthetic fill. An empty result renders the baby
                // graph's empty state (see `hasBabyEntries` / `emptyWeightDisplay`).
                self.babySummaries(for: babyProfile, period: state.graph.selectedPeriod)
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

    /// MOB-1591: baby-aware continuous operations for an ARBITRARY period. The period-switch scroll-position
    /// math (`DashboardChartManager.updateSelectedPeriod`) needs the NEW period's ops BEFORE
    /// `state.graph.selectedPeriod` flips, and for baby those come from the baby summaries — not the
    /// `dataManager` weight cache (empty for a baby-only account, which made a section switch land on `Date()`
    /// → the wrong week). Weight/BPM keep the `dataManager` source, exactly as the manager used before. Reads
    /// the summaries directly (no `cacheManager`) so requesting a non-current period can't pollute the
    /// current-period cache.
    func continuousOperations(for period: TimePeriod) -> [BathScaleWeightSummary] {
        if let babyProfile = selectedBabyProfile {
            return babySummaries(for: babyProfile, period: period)
        }
        return dataManager.getContinuousOperations(for: period)
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
    /// MOB-1516: product-neutral name (Phase G0). `.bpm`/`.baby` dispatch is added in Phase B/Y; today the
    /// body is weight-only and only `productType == .scale` routes here via `TrendChartHost`.
    func rebuildChartModel(scrollPosition: Date) {
        if productType == .bpm {
            chartModel = ChartPrep.buildBpm(
                operations: continuousOperations,
                period: state.graph.selectedPeriod,
                scrollPosition: scrollPosition
            )
            return
        }
        if productType == .baby {
            guard let profile = selectedBabyProfile else { chartModel = nil; return }
            // MOB-1591: a baby with no real readings FOR THE SELECTED METRIC renders an EMPTY skeleton through
            // the SAME engine as an empty weight/BPM chart (per-period axes, reserved y-column, closed box,
            // leading inset) — NOT the dummy summaries in `continuousOperations` or the WHO/CDC percentile
            // curves. This replaces the separate `BabyEmptyGraphView` on the dashboard graph and fixes the
            // "every section shows weekdays" bug (the empty view was period-blind; the engine derives labels
            // per period). Metric-scoped (not `hasBabyEntries`): a baby with only weight readings has NO
            // height data, so the Height tab must show the empty skeleton — never the length percentile
            // reference curves floating over a chart with no real points (parity with Smart Baby).
            guard hasBabyReadings(for: selectedBabyMetric) else {
                chartModel = ChartPrep.buildEmpty(
                    productType: .baby,
                    period: state.graph.selectedPeriod,
                    scrollPosition: scrollPosition
                )
                return
            }
            chartModel = ChartPrep.buildBaby(
                operations: continuousOperations,
                period: state.graph.selectedPeriod,
                scrollPosition: scrollPosition,
                babyProfile: profile,
                metric: selectedBabyMetric,
                convertWeight: goalManager.convertWeightToDisplay,
                convertDecigramsToDisplay: convertBabyDecigramsToDisplay
            )
            return
        }
        chartModel = ChartPrep.buildWeight(
            operations: continuousOperations,
            period: state.graph.selectedPeriod,
            scrollPosition: scrollPosition,
            goalWeight: goalWeightForDisplay,
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight,
            convertWeight: goalManager.convertWeightToDisplay,
            chartHeight: state.graph.chartHeight,
            selectedMetric: coPlottedMetric
        )
    }

    /// V4 (6e): the co-plotted body-comp metric (nil / "weight" → weight only). Drives whether a scroll-end
    /// settle can update the y-axis in place or must re-normalize the metric via a full rebuild.
    private var coPlottedMetric: String? {
        let metric = state.ui.selectedMetricLabel
        return metric == DashboardStrings.weight ? nil : metric
    }

    /// Scroll-END settle for the v2 weight engine. The scroll domain is FULL and scroll-independent, so a
    /// settle only needs to (a) resettle the adaptive y-axis for the landed window and (b) refresh the
    /// WINDOWED x-axis ticks so gridlines follow the scroll. Both are swapped IN PLACE via
    /// `ChartModel.withYAxisAndTicks`, leaving `xDomain`/`visibleDomainLength`/`seriesPoints` byte-identical
    /// → Swift Charts never rebuilds its scroll view (no "~1 s can't scroll again" hitch, #3; no jump; no
    /// wall). A co-plotted metric normalizes to the y-domain, so it still needs a full rebuild. Weight only.
    func settleChart(scrollPosition: Date) {
        if productType == .bpm {
            settleBpm(scrollPosition: scrollPosition)
            return
        }
        if productType == .baby {
            // Baby: window-adaptive reference-driven y-axis + full-domain curves → a full rebuild is cheap and
            // correct (no metric co-plot). seriesPoints/x-geometry come out byte-identical, so Swift Charts
            // still doesn't rebuild its scroll view; only the y-axis + windowed ticks change.
            rebuildChartModel(scrollPosition: scrollPosition)
            return
        }
        guard coPlottedMetric == nil, let current = chartModel else {
            rebuildChartModel(scrollPosition: scrollPosition)
            return
        }
        let config = GraphRenderingConfiguration()
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
        let newTicks = config.boundedXAxisValues(
            for: state.graph.selectedPeriod,
            from: continuousOperations,
            around: scrollPosition,
            windows: ChartPrep.tickWindowRadius
        )
        let updated = current.withYAxisAndTicks(newYAxis, ticks: newTicks)
        guard updated != current else { return }
        chartModel = updated
    }

    /// MOB-1516: BPM in-place scroll-end settle — recompute ONLY the adaptive `bpmScale` for the landed window
    /// + refresh the windowed ticks, swapped via `withYAxisAndTicks`. No metric co-plot, so this never needs a
    /// full rebuild (unlike weight); the scroll region stays byte-identical → no scroll-view rebuild on settle.
    private func settleBpm(scrollPosition: Date) {
        guard let current = chartModel else {
            rebuildChartModel(scrollPosition: scrollPosition)
            return
        }
        let config = GraphRenderingConfiguration()
        let newYAxis = ChartPrep.bpmYAxis(
            operations: continuousOperations,
            period: state.graph.selectedPeriod,
            scrollPosition: scrollPosition,
            visibleDomainLength: current.visibleDomainLength
        )
        let newTicks = config.boundedXAxisValues(
            for: state.graph.selectedPeriod,
            from: continuousOperations,
            around: scrollPosition,
            windows: ChartPrep.tickWindowRadius
        )
        let updated = current.withYAxisAndTicks(newYAxis, ticks: newTicks)
        guard updated != current else { return }
        chartModel = updated
    }

    /// Scroll-END commit for the v2 weight engine (single source of truth). `landed` is where the native
    /// value-aligned scroll actually rested — already on the fine grid (a fling on the period boundary via
    /// `majorAlignment`, a slow drag on any day / month-1st via `matching`; see
    /// `WeightChartView.scrollBehavior`). We commit that EXACT position as the scroll position — no re-snap,
    /// so the stored position == the visible position — and settle the chart (in-place y-axis + windowed
    /// ticks) for it. Committing raw is what removes the one-unit drift the user saw on release / on
    /// leave-and-return: an extra "round to nearest grid unit" here could pick a neighbouring day/month, and
    /// re-adopting that on return jumped the window; trusting native alignment keeps it put. This also
    /// overrides the legacy manager's month floor-snap in `handleScrollPhaseChange(.idle)` (which fires just
    /// before this), so month is likewise left exactly where it rests. Weight only; baby/BPM keep the
    /// legacy manager path.
    func commitScroll(landedAt landed: Date) {
        graphManager.updateScrollPosition(to: landed)
        settleChart(scrollPosition: landed)
        // A scroll clears the selection, so refresh the metric tiles for the NEW visible window (visible-window
        // average) — otherwise they'd keep the values of the point the user just scrolled away from, out of sync
        // with the header. This runs ONCE at scroll-end (isScrolling is already false here) and is de-duped by
        // `MetricsUpdateSignature`, so it's not the per-frame legacy settle the v2 engine deliberately avoids.
        displayManager.updateMetricsForCurrentView()
    }

    /// V4 (6a): apply a validated weight-chart selection at `date` (already snapped to a real entry by the
    /// host), resolving `selectedPoint`/`selectedXValue`/`showCrosshair` per period exactly as the legacy
    /// programmatic auto-select does (`applyChartSelectionSync`). `nil` clears the selection. Weight only.
    func selectPoint(at date: Date?) {
        guard let date else {
            graphManager.state.clearSelection()
            // Selection cleared → the metric tiles fall back to the visible-window average / latest (parity
            // with the header). See below for why the v2 engine has to drive this explicitly.
            displayManager.updateMetricsForCurrentView()
            return
        }
        graphManager.applyChartSelectionSync(at: date, operations: continuousOperations)
        // MOB-1516 (BPM): update the AHA classification from the selected reading so the header + the
        // systolic/diastolic line recolour to that point (parity with the legacy `handleBpmPointSelection`).
        if productType == .bpm, let selected = state.graph.selectedPoint {
            displayManager.handleBpmPointSelection(selected)
        }
        // MOB-518: refresh the metric tiles (bmi / body fat % / muscle % …) to the SELECTED point's per-point
        // values. The legacy engine did this via `handleCompleteChartSelection`'s `updateMetrics` closure;
        // `applyChartSelectionSync` only sets the graph-selection state (which the weight HEADER reads
        // reactively), so without this the tiles keep their last latest/average values — the "tiles don't
        // update on tap" gap. `updateMetricsForCurrentView` branches: selected point → that point's metrics;
        // crosshair on an empty day → placeholders; no selection → visible-window average. Guarded + de-duped,
        // so it's a no-op when nothing changed.
        displayManager.updateMetricsForCurrentView()
    }

    var hasAnyEntries: Bool { state.data.hasAnyEntries }

    /// True when the selected baby profile has at least one real (non-dummy) scale reading.
    var hasBabyEntries: Bool {
        guard let babyProfile = selectedBabyProfile else { return false }
        let daily = entryService.babyDailySummariesByProfile[babyProfile.id] ?? []
        let monthly = entryService.babyMonthlySummariesByProfile[babyProfile.id] ?? []
        return !daily.isEmpty || !monthly.isEmpty
    }

    /// MOB-1591: whether the selected baby profile has a real reading FOR A SPECIFIC METRIC — weight
    /// summaries with `weight > 0`, or length summaries with `babyLengthInches > 0`. Unlike `hasBabyEntries`
    /// (metric-agnostic), this drives the per-metric empty state: a baby with only weight readings has no
    /// height data, so the Height tab must render the empty skeleton rather than length percentile curves
    /// floating over a chart with no plotted points (parity with Smart Baby, which hides the curves when the
    /// length dataset is empty).
    func hasBabyReadings(for metric: BabyMetric) -> Bool {
        guard let babyProfile = selectedBabyProfile else { return false }
        let daily = entryService.babyDailySummariesByProfile[babyProfile.id] ?? []
        let monthly = entryService.babyMonthlySummariesByProfile[babyProfile.id] ?? []
        let all = daily + monthly
        switch metric {
        case .weight: return all.contains { $0.weight > 0 }
        case .height: return all.contains { ($0.babyLengthInches ?? 0) > 0 }
        }
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
