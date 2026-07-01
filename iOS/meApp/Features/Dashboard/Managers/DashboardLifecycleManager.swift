// swiftlint:disable file_length
//
//  DashboardLifecycleManager.swift
//  meApp
//
//  Dashboard initialization, data loading, settings handling, entry lifecycle,
//  save/reset flows, and UI handlers.
//

import Foundation
import SwiftUI

@MainActor
final class DashboardLifecycleManager: DashboardLifecycleManaging { // swiftlint:disable:this type_body_length

    // MARK: - Dependencies

    weak var stateProvider: DashboardStateProviding?

    private let chartManager: DashboardChartManaging
    private let displayManager: DashboardDisplayManaging
    private let gridEditingManager: DashboardGridEditingManaging

    @Injector var accountService: AccountServiceProtocol
    @Injector var notificationService: NotificationHelperServiceProtocol
    @Injector var logger: LoggerService
    @Injector private var entryService: EntryService

    private let metricsManager: DashboardMetricsManager
    private let graphManager: DashboardGraphManager
    private let streakManager: DashboardStreakManager
    private let dataManager: DashboardDataManager
    private let goalManager: DashboardGoalManager
    private let syncCoordinator: DashboardSyncCoordinatorProtocol
    private let editSessionManager: DashboardEditSessionManaging
    private let cacheManager: DashboardCacheManagerProtocol

    // PERFORMANCE: Debounce entry lifecycle changes to coalesce rapid-fire saves/deletes
    private var entryLifecycleDebounceTask: Task<Void, Never>?
    private var deferredSyncTask: Task<Void, Never>?
    private var settingsRefreshTask: Task<Void, Never>?
    private var onAppearRefreshTask: Task<Void, Never>?
    private var initializationRefreshTask: Task<Void, Never>?

    /// Tracks whether the per-account default graph range has already been applied
    /// for this app session. The default is applied exactly once when the dashboard
    /// first initializes — it is intentionally not retargeted on later account or
    /// product switches, mirroring main: a Settings change takes effect on the next
    /// app launch, not while the graph is on screen.
    private var hasAppliedDefaultGraphPeriod = false

    let lang = LoaderStrings.self

    // MARK: - Initialization

    init(
        stateProvider: DashboardStateProviding,
        chartManager: DashboardChartManaging,
        displayManager: DashboardDisplayManaging,
        gridEditingManager: DashboardGridEditingManaging,
        metricsManager: DashboardMetricsManager,
        graphManager: DashboardGraphManager,
        streakManager: DashboardStreakManager,
        dataManager: DashboardDataManager,
        goalManager: DashboardGoalManager,
        syncCoordinator: DashboardSyncCoordinatorProtocol,
        editSessionManager: DashboardEditSessionManaging,
        cacheManager: DashboardCacheManagerProtocol
    ) {
        self.stateProvider = stateProvider
        self.chartManager = chartManager
        self.displayManager = displayManager
        self.gridEditingManager = gridEditingManager
        self.metricsManager = metricsManager
        self.graphManager = graphManager
        self.streakManager = streakManager
        self.dataManager = dataManager
        self.goalManager = goalManager
        self.syncCoordinator = syncCoordinator
        self.editSessionManager = editSessionManager
        self.cacheManager = cacheManager

        // Resolve DI-backed services once so async tasks launched later do not
        // drift to a different container registration.
        _ = accountService
        _ = notificationService
        _ = logger
        _ = entryService
    }

    // MARK: - Dashboard Initialization

    // swiftlint:disable:next function_body_length
    func initializeDashboard() async {
        guard let stateProvider else { return }

        await MainActor.run {
            applyDefaultGraphPeriodIfNeeded()
            stateProvider.state.ui.isResettingDashboard = true
            if streakManager.state.streakItems.isEmpty {
                stateProvider.state.ui.hasLoadedProgressMetrics = false
            }
            stateProvider.state.ui.hasLoadedMetricValues = false
        }

        _ = try? await accountService.refreshAccount()

        let dashboardType = determineDashboardTypeFromAccount()
        stateProvider.state.metrics.dashboardType = dashboardType
        metricsManager.updateDashboardType(dashboardType)

        await loadMetricsFromLocalAccount()
        await initializeDataManager()
        await entryService.loadDashboardData(entryType: stateProvider.productType)

        await MainActor.run {
            chartManager.initializeChart()
        }

        await MainActor.run {
            stateProvider.state.ui.isResettingDashboard = false
            stateProvider.scheduleUIUpdate()
        }

        loadLatestEntryData()

        initializationRefreshTask?.cancel()
        initializationRefreshTask = Task { [weak self] in
            guard let self else { return }

            do {
                _ = try? await self.accountService.refreshAccount()
                try await self.streakManager.refreshStreakData()
                await self.gridEditingManager.loadProgressMetricsFromAccount()
                if stateProvider.productType != .bpm && !stateProvider.isBabySelection {
                    try? await self.goalManager.loadGoalData()
                }
            } catch {
                self.logger.log(
                    level: .error,
                    tag: "DashboardLifecycleManager",
                    message: "Failed staged progress metric refresh during dashboard init: \(error)"
                )
            }

            await MainActor.run {
                stateProvider.state.ui.hasLoadedProgressMetrics = true
            }

            await self.loadDashboardConfigurationFromAPI()

            await MainActor.run {
                self.gridEditingManager.syncRemovalStateFromMetricsManager()
                if stateProvider.state.ui.hasInitializedChart {
                    self.displayManager.updateMetricsForCurrentView()
                }
                stateProvider.scheduleUIUpdate()
            }
        }

        deferredSyncTask?.cancel()
        deferredSyncTask = Task { [weak self] in
            guard let self else { return }
            // Let the first dashboard render settle before sync competes for the same data path.
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            guard !Task.isCancelled else { return }
            await self.syncEntries()
        }
    }

    /// Applies the per-account default graph range exactly once per session, before
    /// the chart is first initialized so it builds for the user's chosen period.
    /// Falls back to `DefaultGraphPeriodPreference.fallback` when none has been set.
    private func applyDefaultGraphPeriodIfNeeded() {
        guard !hasAppliedDefaultGraphPeriod else { return }
        hasAppliedDefaultGraphPeriod = true

        let stored = DefaultGraphPeriodPreference.current(for: accountService.activeAccount?.accountId)
        // Set the period directly rather than via updateSelectedPeriod(_:): the chart has
        // not been built yet, so the live-switch cache invalidation and scroll-snap timer
        // are unnecessary — initializeChart() builds the window for this period right after.
        graphManager.state.selectedPeriod = stored
    }

    // MARK: - Dashboard Type Logic

    private func determineDashboardTypeFromAccount() -> DashboardType {
        guard let account = accountService.activeAccount,
              let dashboardTypeString = account.dashboardType
        else {
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
                _ = try await dataManager.loadLatestEntryData()
                await MainActor.run {
                    self.displayManager.updateMetricsForCurrentView()
                }
            } catch {
                logger.log(level: .error, tag: "DashboardLifecycleManager", message: "Failed to load latest entry data: \(error)")
            }
        }
    }

    func loadGoalCardData() {
        Task {
            do {
                try await goalManager.loadGoalData()
            } catch {
                logger.log(level: .error, tag: "DashboardLifecycleManager", message: "Failed to load goal card data: \(error)")
            }
        }
    }

    private func initializeDataManager() async {
        do {
            try await dataManager.initializeDataManager()
        } catch {
            logger.log(level: .error, tag: "DashboardLifecycleManager", message: "Failed to initialize data manager: \(error)")
        }
    }

    private func loadMetricsFromLocalAccount() async {
        await syncCoordinator.loadMetricsFromLocalAccount(
            activeAccount: accountService.activeAccount,
            updateDashboardType: { [weak self] dashboardType in
                guard let self, let stateProvider = self.stateProvider else { return }
                metricsManager.updateDashboardType(dashboardType)
                stateProvider.state.metrics.dashboardType = dashboardType
            },
            updateMetricsOrder: { [weak self] metricArray in
                guard let self else { return }
                metricsManager.updateMetricsOrder(from: metricArray)
                gridEditingManager.syncRemovalStateFromMetricsManager()
            },
            setupInitialMetrics: { [weak self] in
                guard let self else { return }
                if metricsManager.state.metrics.isEmpty {
                    metricsManager.setupInitialMetrics()
                }
            },
            onMetricsLoaded: {
                // Wait for API to load before setting hasLoadedDashboardConfig
            }
        )
    }

    func loadDashboardConfigurationFromAPI() async {
        guard let stateProvider else { return }
        do {
            _ = try? await accountService.refreshAccount()
            try await metricsManager.loadMetricsFromAPI()

            await MainActor.run {
                stateProvider.state.metrics.dashboardType = metricsManager.state.dashboardType
                gridEditingManager.syncRemovalStateFromMetricsManager()
                stateProvider.scheduleUIUpdate()
            }

            let progressMetricsAlreadyLoaded = await MainActor.run {
                stateProvider.state.ui.hasLoadedProgressMetrics
            }

            if !progressMetricsAlreadyLoaded {
                try await streakManager.refreshStreakData()
                await gridEditingManager.loadProgressMetricsFromAccount()
                try? await goalManager.loadGoalData()

                await MainActor.run {
                    stateProvider.state.ui.hasLoadedProgressMetrics = true
                }
            }

            await MainActor.run {
                stateProvider.state.ui.hasLoadedDashboardConfig = true
            }

            await MainActor.run {
                stateProvider.scheduleUIUpdate()
            }

            await MainActor.run {
                if stateProvider.state.ui.hasInitializedChart {
                    displayManager.updateMetricsForCurrentView()
                }
            }

        } catch {
            await MainActor.run {
                if metricsManager.state.metrics.isEmpty {
                    metricsManager.setupInitialMetrics()
                    gridEditingManager.syncRemovalStateFromMetricsManager()
                }
                stateProvider.state.ui.hasLoadedDashboardConfig = true

                if streakManager.state.streakItems.isEmpty {
                    streakManager.setupInitialStreakItems()
                }
                stateProvider.state.ui.hasLoadedProgressMetrics = true

                stateProvider.scheduleUIUpdate()
            }
            logger.log(level: .error, tag: "DashboardLifecycleManager", message: "Failed to load dashboard configuration from API: \(error)")
        }
    }

    // MARK: - Entry Lifecycle Management

    func onEntryAdded(_: EntryNotification) {
        handleEntryLifecycleChange()
    }

    func onEntryUpdated(_: EntryNotification) {
        handleEntryLifecycleChange()
    }

    func onEntryDeleted(_: EntryNotification) {
        handleEntryLifecycleChange()
    }

    private func handleEntryLifecycleChange() {
        // PERFORMANCE: Debounce — cancel any pending call and coalesce within 250ms.
        // Batch syncs can fire dozens of saves in quick succession; without debouncing
        // each save triggers a full streak fetch + SwiftData queries on the main thread.
        entryLifecycleDebounceTask?.cancel()
        entryLifecycleDebounceTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 250_000_000) // 250ms
            guard !Task.isCancelled else { return }
            performEntryLifecycleChange()
        }
    }

    private func performEntryLifecycleChange() {
        guard let stateProvider else { return }

        // Baby summaries are published by EntryService only via an explicit reload — unlike
        // weight summaries, they are not refreshed by the entry CRUD path. So after a baby
        // entry add/delete (e.g. clearing all baby history), reload the baby dashboard data
        // before recalculating so the chart and headline reflect the change immediately.
        if stateProvider.isBabySelection,
           let babyId = (stateProvider as? DashboardStore)?.selectedBabyProfile?.id {
            reloadBabyDashboardDataThenRefresh(babyId: babyId)
        }

        loadLatestEntryData()
        loadGoalCardData()

        Task {
            do {
                let oldStreakItems = self.streakManager.state.streakItems
                let oldOrder = stateProvider.state.ui.streakGridOrder

                try await self.streakManager.refreshStreakData()

                await MainActor.run {
                    self.gridEditingManager.regenerateStreakGridOrderAfterRefresh(
                        oldStreakItems: oldStreakItems,
                        oldOrder: oldOrder
                    )
                    stateProvider.state.ui.hasLoadedProgressMetrics = true
                    stateProvider.state.ui.hasLoadedDashboardConfig = true
                }
            } catch {
                self.logger.log(
                    level: .error,
                    tag: "DashboardLifecycleManager",
                    message: "Failed to refresh streak data after entry change: \(error)"
                )
            }
        }

        cacheManager.invalidateContinuousOperationsCache()

        chartManager.forceCompleteRecalculationAfterScrollPosition()

        Task { @MainActor in
            if let selectedPoint = stateProvider.state.graph.selectedPoint {
                let calendar = Calendar.current
                let continuousOps = (stateProvider as? DashboardStore)?.continuousOperations ?? []
                let updatedPoint: BathScaleWeightSummary? = {
                    switch stateProvider.state.graph.selectedPeriod {
                    case .week, .month:
                        return continuousOps.first { calendar.isDate($0.date, inSameDayAs: selectedPoint.date) }
                    case .year, .total:
                        return continuousOps.first { calendar.isDate($0.date, equalTo: selectedPoint.date, toGranularity: .month) }
                    }
                }()

                if let updatedPoint = updatedPoint {
                    self.graphManager.updateSelectedPoint(updatedPoint)
                } else {
                    await self.graphManager.handleChartSelection(at: nil)
                }
            }

            self.displayManager.updateMetricsForCurrentView()
        }

        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 150_000_000)
            self.chartManager.updateYAxisCache(force: false)
        }
    }

    /// Reloads the selected baby's dashboard summaries from SwiftData, then invalidates the
    /// continuous-operations cache and recalculates the chart so a baby entry add/delete
    /// (notably deleting all baby history) reflects on the dashboard right away.
    private func reloadBabyDashboardDataThenRefresh(babyId: String) {
        Task { @MainActor [weak self] in
            guard let self else { return }
            await self.entryService.loadBabyDashboardData(babyId: babyId)
            guard let stateProvider = self.stateProvider else { return }
            self.cacheManager.invalidateContinuousOperationsCache()
            self.chartManager.forceCompleteRecalculationAfterScrollPosition()
            self.displayManager.updateMetricsForCurrentView()
            self.chartManager.updateYAxisCache(force: true)
            stateProvider.scheduleUIUpdate()
        }
    }

    // MARK: - Settings Handlers

    func handleSettingsChange(shouldRefreshStreak: Bool = true) {
        guard let stateProvider else { return }
        settingsRefreshTask?.cancel()
        settingsRefreshTask = Task { [weak self] in
            guard let self else { return }
            try? await Task.sleep(nanoseconds: 200_000_000)
            guard !Task.isCancelled else { return }

            if shouldRefreshStreak {
                do {
                    try await self.streakManager.refreshStreakData()
                } catch {
                    self.logger.log(
                        level: .error,
                        tag: "DashboardLifecycleManager",
                        message: "Failed to refresh streak data after settings change: \(error)"
                    )
                }
            }
            do {
                try await self.goalManager.loadGoalData()
            } catch {
                self.logger.log(
                    level: .error,
                    tag: "DashboardLifecycleManager",
                    message: "Failed to reload goal data after settings change: \(error)"
                )
            }

            await MainActor.run {
                self.gridEditingManager.syncRemovalStateFromMetricsManager()
                self.gridEditingManager.syncRemovalStateFromStreakManager()

                self.cacheManager.invalidateChartSeriesCache()
                self.chartManager.updateYAxisCache(force: true)
                stateProvider.forceImmediateUIUpdate()
            }

            await self.gridEditingManager.loadProgressMetricsFromAccount()
        }
    }

    func handleDashboardTypeChange() {
        guard let stateProvider else { return }
        let newDashboardType = determineDashboardTypeFromAccount()
        stateProvider.state.metrics.dashboardType = newDashboardType
        metricsManager.updateDashboardType(newDashboardType)
        stateProvider.scheduleUIUpdate()
    }

    func handleUnitChange() {
        Task {
            do {
                try await streakManager.refreshStreakDataForUnitChange()
                await gridEditingManager.loadProgressMetricsFromAccount()
                try await goalManager.refreshGoalDataForUnitChange()

                await MainActor.run {
                    self.stateProvider?.scheduleUIUpdate()
                }
            } catch {
                logger.log(level: .error, tag: "DashboardLifecycleManager", message: "Failed to refresh data for unit change: \(error)")
            }
        }
    }

    func handleActiveAccountChanged() {
        guard let stateProvider else { return }

        deferredSyncTask?.cancel()
        deferredSyncTask = nil
        chartManager.clearAllCaches()
        stateProvider.state.ui.hasInitializedChart = false
        graphManager.state.isGraphReady = false
        graphManager.state.clearSelection()
        stateProvider.state.graph.clearSelection()

        Task { [weak self] in
            guard let self else { return }
            await self.syncEntries()
            await MainActor.run {
                self.chartManager.initializeChart()
            }
        }
        loadLatestEntryData()
        loadGoalCardData()

        gridEditingManager.syncRemovalStateFromMetricsManager()
        gridEditingManager.syncRemovalStateFromStreakManager()

        chartManager.ensureLatestEntriesVisible()
        chartManager.updateYAxisCache(force: true)
        stateProvider.forceImmediateUIUpdate()
    }

    // MARK: - Save & Sync

    func syncEntries() async {
        await syncCoordinator.syncEntries()
    }

    func saveChanges() {
        guard let stateProvider else { return }
        stateProvider.state.ui.selectedMetricLabel = nil
        stateProvider.state.ui.resetDragState()

        syncCoordinator.saveChanges(
            saveMetrics: {
                try await self.metricsManager.saveMetricsToAPI()
            },
            saveProgressMetrics: {
                try await self.saveProgressMetricsToAPI()
            },
            loadProgressMetrics: {
                await self.gridEditingManager.loadProgressMetricsFromAccount()
            },
            onSuccess: { [weak self] in
                self?.commonPostSaveUIReset()
            },
            onError: { [weak self] _ in
                self?.commonPostSaveUIReset()
            }
        )
    }

    func saveProgressMetricsToAPI() async throws {
        guard let stateProvider else { return }
        try await syncCoordinator.saveProgressMetricsToAPI(
            streakItems: streakManager.state.streakItems,
            streakOrder: stateProvider.state.ui.streakGridOrder,
            goalCardPosition: stateProvider.state.ui.goalCardPosition,
            isGoalCardRemoved: stateProvider.state.ui.isGoalCardRemoved,
            removedStreaks: stateProvider.state.ui.removedStreaks
        ) { metrics in
            _ = try await self.accountService.updateProgressMetrics(metrics: metrics)
        }
    }

    private func commonPostSaveUIReset() {
        guard let stateProvider else { return }
        withAnimation(.easeInOut(duration: 0.3)) {
            stateProvider.state.ui.isEditMode = false
            stateProvider.state.ui.resetDragState()
            stateProvider.state.ui.selectedMetricLabel = nil
            self.editSessionManager.clearSnapshot()
        }
    }

    // MARK: - Reset

    func resetDashboard() {
        performDashboardResetFlow()
    }

    func resetDashboardEnhanced() {
        performDashboardResetFlow()
    }

    private func resetGridOrder() {
        guard let stateProvider else { return }
        stateProvider.state.ui.streakGridOrder = []
        stateProvider.state.ui.goalCardPosition = 0
    }

    // swiftlint:disable:next function_body_length
    private func performDashboardResetFlow() {
        guard let stateProvider else { return }
        stateProvider.state.ui.isLoading = true
        stateProvider.state.ui.loaderOverride = LoaderModel(text: lang.saving)
        notificationService.showLoader(LoaderModel(text: lang.saving))

        stateProvider.state.ui.isResettingDashboard = true
        stateProvider.state.ui.hasLoadedMetricValues = false

        resetGridOrder()

        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            do {
                try await self.metricsManager.resetMetricsToDefaults()
                try await self.streakManager.resetStreakData()

                self.metricsManager.resetOrderToDefault()

                try await self.streakManager.refreshStreakData()
                self.gridEditingManager.syncRemovalStateFromMetricsManager()
                self.gridEditingManager.syncRemovalStateFromStreakManager()

                await self.gridEditingManager.resetProgressMetricsToDefaults()

                try await self.metricsManager.saveMetricsToAPI()
                try await self.saveProgressMetricsToAPI()

                stateProvider.state.metrics = self.metricsManager.state
                stateProvider.state.streak = self.streakManager.state

                withAnimation(.easeInOut(duration: 0.3)) {
                    stateProvider.state.ui.isLoading = false
                    stateProvider.state.ui.loaderOverride = nil
                    stateProvider.state.ui.isGoalCardRemoved = false
                    stateProvider.state.ui.selectedMetricLabel = nil
                    stateProvider.state.graph.clearSelection()
                    stateProvider.state.ui.isEditMode = false
                    stateProvider.state.ui.resetDragState()
                    stateProvider.state.ui.isResettingDashboard = false
                    self.editSessionManager.clearSnapshot()
                }

                self.notificationService.dismissLoader()
                self.displayManager.updateMetricsWithVisibleRegionAverage()
                self.displayManager.resetMetricsToLatestEntry()
                stateProvider.state.ui.hasLoadedMetricValues = true
                stateProvider.forceImmediateUIUpdate()
            } catch {
                self.logger.log(level: .error, tag: "DashboardLifecycleManager", message: "Failed to reset dashboard: \(error)")

                stateProvider.state.metrics = self.metricsManager.state
                stateProvider.state.streak = self.streakManager.state

                withAnimation(.easeInOut(duration: 0.3)) {
                    stateProvider.state.ui.isLoading = false
                    stateProvider.state.ui.loaderOverride = nil
                    stateProvider.state.ui.isGoalCardRemoved = false
                    stateProvider.state.ui.selectedMetricLabel = nil
                    stateProvider.state.graph.clearSelection()
                    stateProvider.state.ui.isEditMode = false
                    stateProvider.state.ui.resetDragState()
                    stateProvider.state.ui.isResettingDashboard = false
                    self.editSessionManager.clearSnapshot()
                }
                self.notificationService.dismissLoader()
                self.displayManager.updateMetricsWithVisibleRegionAverage()
                self.displayManager.resetMetricsToLatestEntry()
                stateProvider.state.ui.hasLoadedMetricValues = true
                stateProvider.forceImmediateUIUpdate()
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

    // MARK: - View Helpers

    func reloadDashboardConfiguration(fullRefresh: Bool = false, updateMetrics: Bool = false) async {
        await syncCoordinator.reloadDashboardConfiguration(
            fullRefresh: fullRefresh,
            updateMetrics: updateMetrics,
            loadConfiguration: { [weak self] in
                await self?.loadDashboardConfigurationFromAPI()
            },
            updateMetricsForView: { [weak self] in
                self?.displayManager.updateMetricsForCurrentView()
            },
            scheduleUIUpdate: { [weak self] in
                self?.stateProvider?.scheduleUIUpdate()
            },
            refreshDashboardState: { [weak self] in
                self?.refreshDashboardState()
            }
        )
    }

    func refreshAll() async {
        await syncCoordinator.refreshAll(
            syncEntries: { [weak self] in
                await self?.syncEntries()
            },
            onAppearActions: { [weak self] in
                self?.onAppearActions()
            }
        )
    }

    // MARK: - UI State Management

    func handleMetricLongPress(for metricLabel: String, selectedEntry: Binding<Entry?>, selectedMetric: Binding<BodyMetric?>) {
        metricsManager.handleMetricLongPressWithUIState(
            for: metricLabel,
            selectedEntry: selectedEntry,
            selectedMetric: selectedMetric
        ) { [weak self] newValue in
            self?.stateProvider?.state.ui.selectedMetricLabel = newValue
        }
    }

    func handleSelectedMetricInfoChange(_ newValue: String?, selectedEntry: Binding<Entry?>, selectedMetric: Binding<BodyMetric?>) async {
        guard let label = newValue else { return }
        stateProvider?.state.ui.selectedMetricLabel = label
        await metricsManager.handleSelectedMetricInfoChange(newValue, selectedEntry: selectedEntry, selectedMetric: selectedMetric)
    }

    func handleSelectedMetricLabelChange(_ newValue: String?) {
        if newValue == nil {
            stateProvider?.state.ui.selectedMetricLabel = nil
        }
    }

    func handleSelectedEntryChange(_ newValue: Entry?) {
        _ = newValue
    }

    func handleMetricInfoSheetDismiss(_ newValue: MetricInfoWrapper?) {
        _ = newValue
    }

    // MARK: - Lifecycle Methods

    func onAppearActions() {
        guard let stateProvider else { return }

        loadLatestEntryData()
        loadGoalCardData()
        onAppearRefreshTask?.cancel()
        onAppearRefreshTask = Task { [weak self] in
            guard let self else { return }
            try? await Task.sleep(nanoseconds: 150_000_000)
            guard !Task.isCancelled else { return }

            await self.loadDashboardConfigurationFromAPI()
            await MainActor.run {
                self.gridEditingManager.syncRemovalStateFromMetricsManager()
                stateProvider.scheduleUIUpdate()
            }
            await MainActor.run {
                if stateProvider.state.ui.hasInitializedChart {
                    self.displayManager.updateMetricsForCurrentView()
                }
            }
        }

        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 100_000_000)
            self.chartManager.updateYAxisCache(force: false)
            stateProvider.scheduleUIUpdate()
        }
    }

    func refreshDashboardState() {
        loadLatestEntryData()
        loadGoalCardData()
        handleSettingsChange()
        stateProvider?.scheduleUIUpdate()
        gridEditingManager.resetGridLayout()
    }
}
