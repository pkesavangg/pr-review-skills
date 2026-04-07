// swiftlint:disable file_length
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
    @Injector private var scaleService: ScaleService
    @Injector private var entryService: EntryService
    @Injector private var productTypeStore: ProductTypeStoreProtocol

    // MARK: - Formatter and Cache Services
    let formatter: DashboardFormatterProtocol
    let cacheManager: DashboardCacheManagerProtocol

    // MARK: - Centralized State

    @Published var state = DashboardState()

    /// The active product type for the dashboard (weight or BPM).
    @Published var productType: EntryType = .wg
    @Published private(set) var availableProductItems: [ProductSelection] = []
    @Published private(set) var selectedProductItem: ProductSelection = .myWeight

    // MARK: - Private Properties

    private var cancellables = Set<AnyCancellable>()
    private var lastAccountSettingsSnapshot: AccountSettingsSnapshot?
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

    // swiftlint:disable:next function_body_length
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
            .compactMap { $0?.dashboardSettings?.dashboardType }
            .removeDuplicates()
            .sink { [weak self] _ in
                self?.lifecycleManager.handleDashboardTypeChange()
            }
            .store(in: &cancellables)
    }

    private func setupProductTypeStoreSubscriptions() {
        productTypeStore.availableItemsPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] items in
                self?.availableProductItems = items
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
        state.ui.selectedMetricLabel = nil
        chartManager?.clearSelection()
        chartManager?.clearAllCaches()
        cacheManager.clearAllCaches()
        state.ui.hasInitializedChart = false
        graphManager.state.isGraphReady = false
        Task { [weak self] in
            guard let self else { return }
            await self.entryService.loadDashboardData(entryType: newType)
            await self.lifecycleManager.initializeDashboard()
        }
    }

    private func refreshSelectedProductContext() {
        state.ui.selectedMetricLabel = nil
        chartManager?.clearSelection()
        chartManager?.clearAllCaches()
        cacheManager.clearAllCaches()
        invalidateContinuousOperationsCache()
        state.ui.hasInitializedChart = false
        graphManager.state.isGraphReady = false

        if isBabySelection {
            // Baby charts use locally-computed data (BabyDashboardChartSupport),
            // so skip the full initializeDashboard() pipeline with its network
            // calls and just reinitialize the chart directly.
            chartManager?.initializeChart()
            displayManager.updateMetricsForCurrentView()
            scheduleUIUpdate()
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
        if let babyProfile = selectedBabyProfile {
            return cacheManager.getContinuousOperations(for: state.graph.selectedPeriod) {
                BabyDashboardChartSupport.dummySummaries(
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
        // DashboardDataManager's published-cache bindings have populated. Fall back to the
        // manager's pre-sorted cache so selection and chart rendering remain consistent
        // without performing an uncached O(n log n) sort on every access.
        return dataManager.getContinuousOperations(for: state.graph.selectedPeriod)
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
        // BPM uses its own 3-series builder; weight uses the existing pipeline
        if productType == .bpm {
            return cacheManager.getChartSeriesData(
                isScrolling: state.graph.isScrolling,
                isProcessingScrollEnd: chartManager.isProcessingScrollEnd,
                period: state.graph.selectedPeriod,
                selectedMetric: nil,
                operationsCount: continuousOperations.count,
                yAxisDomain: chartManager.yAxisDomain
            ) {
                graphManager.generateBpmChartData(from: continuousOperations)
            }
        }
        if let babyProfile = selectedBabyProfile {
            return cacheManager.getChartSeriesData(
                isScrolling: state.graph.isScrolling,
                isProcessingScrollEnd: chartManager.isProcessingScrollEnd,
                period: state.graph.selectedPeriod,
                selectedMetric: selectedBabyMetric == .height ? BabyMetric.height.rawValue : nil,
                operationsCount: continuousOperations.count,
                yAxisDomain: chartManager.yAxisDomain
            ) {
                graphManager.generateBabyChartData(
                    from: continuousOperations,
                    visibleOperations: visibleOperations,
                    babyProfile: babyProfile,
                    metric: selectedBabyMetric,
                    convertWeight: goalManager.convertWeightToDisplay,
                    convertDecigramsToDisplay: convertBabyDecigramsToDisplay,
                    yAxisDomain: chartManager.yAxisDomain
                )
            }
        }
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

    func yAxisScale(for operations: [BathScaleWeightSummary], chartHeight: CGFloat) -> YAxisScale {
        if productType == .bpm {
            return graphManager.getBpmYAxisScale(from: operations, chartHeight: chartHeight)
        }

        if let babyProfile = selectedBabyProfile {
            switch selectedBabyMetric {
            case .weight:
                return BabyDashboardChartSupport.yAxisScale(
                    for: operations,
                    babyProfile: babyProfile,
                    convertStoredWeightToDisplay: goalManager.convertWeightToDisplay,
                    convertDecigramsToDisplay: convertBabyDecigramsToDisplay
                )
            case .height:
                return BabyDashboardChartSupport.heightYAxisScale(
                    for: operations,
                    babyProfile: babyProfile
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
        let storedWeight = Int(weightlessWeight)
        let unit = accountService.activeAccount?.weightSettings?.weightUnit ?? .lb
        return unit == .kg
            ? ConversionTools.convertStoredToKg(storedWeight)
            : ConversionTools.convertStoredToLbs(storedWeight)
    }

    var goalWeightForDisplay: Double? {
        return goalManager.getGoalWeightForDisplay(
            isWeightlessMode: isWeightlessModeEnabled,
            anchorWeight: weightlessAnchorWeight
        )
    }

    private func convertBabyDecigramsToDisplay(_ decigrams: Int) -> Double {
        let isMetric = accountService.activeAccount?.weightSettings?.weightUnit == .kg
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
