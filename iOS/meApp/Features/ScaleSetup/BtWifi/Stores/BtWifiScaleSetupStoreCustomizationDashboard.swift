import Combine
import Foundation
import SwiftUI

@MainActor
extension BtWifiScaleSetupStore {
    /// Snapshots the current dashboard state for change detection
    func snapshotDashboardState() {
        initialDashboardMetricLabelsSnapshot = dashboardStore.metricsManager.state.metrics.map { $0.label }
        initialDashboardRemovedMetricsSnapshot = dashboardStore.state.ui.removedMetrics
        initialDashboardRemovedStreaksSnapshot = dashboardStore.state.ui.removedStreaks
        initialDashboardStreakOrderSnapshot = dashboardStore.state.ui.streakGridOrder
        initialDashboardGoalCardRemovedSnapshot = dashboardStore.state.ui.isGoalCardRemoved
        initialDashboardGoalCardPositionSnapshot = dashboardStore.state.ui.goalCardPosition
    }

    /// Returns true if dashboard customization has changed compared to initial snapshot
    func hasDashboardCustomizationChanged() -> Bool {
        let state = dashboardStore.metricsManager.state.metrics.map { $0.label }
        let removed = dashboardStore.state.ui.removedMetrics
        let removedStreaks = dashboardStore.state.ui.removedStreaks
        let order = dashboardStore.state.ui.streakGridOrder
        let goalRemoved = dashboardStore.state.ui.isGoalCardRemoved
        let goalPos = dashboardStore.state.ui.goalCardPosition

        return (initialDashboardMetricLabelsSnapshot != nil && initialDashboardMetricLabelsSnapshot != state) ||
               (initialDashboardRemovedMetricsSnapshot != nil && initialDashboardRemovedMetricsSnapshot != removed) ||
               (initialDashboardRemovedStreaksSnapshot != nil && initialDashboardRemovedStreaksSnapshot != removedStreaks) ||
               (initialDashboardStreakOrderSnapshot != nil && initialDashboardStreakOrderSnapshot != order) ||
               (initialDashboardGoalCardRemovedSnapshot != nil && initialDashboardGoalCardRemovedSnapshot != goalRemoved) ||
               (initialDashboardGoalCardPositionSnapshot != nil && initialDashboardGoalCardPositionSnapshot != goalPos)
    }

    /// Discards dashboard customization changes by canceling edit mode
    func discardDashboardCustomization() {
        dashboardStore.cancelEdit()
        selectedCustomizeItems.remove(CustomizeSettingsItem.dashboardMetrics.rawValue)
        hasCustomizeChanges = !selectedCustomizeItems.isEmpty
    }

    /// Opens the BIA model information modal.
    func openBIAModel() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(BIAInfoModalView {
                self.notificationService.dismissModal()
            }),
            backdropDismiss: true
        ))
    }

    // MARK: - Dashboard Metrics Upgrade

    /// Upgrades dashboard from type 4 → 12 using default metric order.
    func upgradeDashboardTypeFrom4To12WithDefaults() async {
        let isAlreadyDashboard12 = await MainActor.run {
            dashboardStore.metricsManager.state.dashboardType == .dashboard12 &&
            !dashboardStore.metricsManager.state.metrics.isEmpty
        }

        if isAlreadyDashboard12 {
            return
        }

        do {
            let apiRepo = AccountRepositoryAPI()
            _ = try await apiRepo.patchDashboardType(.dashboard12)
            try await accountService.refreshAccount(accountId: accountService.activeAccount?.accountId)
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "R4 setup: Failed to update dashboard type on server: \(error.localizedDescription)")
        }

        await MainActor.run {
            dashboardStore.metricsManager.updateDashboardType(.dashboard12)
            dashboardStore.state.metrics.dashboardType = .dashboard12
            dashboardStore.metricsManager.setupInitialMetrics(forceShowAll: true)
            dashboardStore.metricsManager.resetOrderToDefault()
            dashboardStore.syncRemovalStateFromMetricsManager()
            LoggerService.shared.log(level: .info, tag: tag, message: "R4 setup: Upgraded to dashboard12 with default order. All 12 metrics enabled.")
        }

        do {
            try await dashboardStore.metricsManager.saveMetricsToAPI(removedMetrics: [])
            LoggerService.shared.log(level: .info, tag: tag, message: "R4 setup: Saved default dashboard metrics order to API")
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "R4 setup: Failed to save metrics to API: \(error.localizedDescription)")
        }

        _ = try? await accountService.refreshAccount(accountId: accountService.activeAccount?.accountId)
    }

    /// Checks if the current dashboard type is dashboard4
    var isDashboardTypeFour: Bool {
        let currentDashboardType = accountService.activeAccount?.dashboardSettings?.dashboardType
        return (currentDashboardType == "dashboard_4_metrics" || currentDashboardType == "dashboard4") &&
            dashboardStore.effectiveDashboardType == .dashboard4
    }

    /// Sets up dashboard metrics customization screen with proper state management.
    func setupDashboardMetricsCustomization() async {
        let isDashboardFour = isDashboardTypeFour

        if isDashboardFour {
            await upgradeDashboardTypeFrom4To12WithDefaults()
            try? await dashboardStore.streakManager.refreshStreakData()
            await dashboardStore.loadProgressMetricsFromAccount()
        } else {
            await syncOrLoadDashboardMetricsWhenNotTypeFour()
        }

        await MainActor.run {
            dashboardStore.beginEdit()
            dashboardStore.state.ui.isEditMode = true
            snapshotDashboardState()
        }
        setupDashboardMetricsSubscriptions()
    }

    private func syncOrLoadDashboardMetricsWhenNotTypeFour() async {
        await MainActor.run {
            dashboardStore.syncRemovalStateFromMetricsManager()
            if dashboardStore.metricsManager.state.metrics.isEmpty {
                LoggerService.shared.log(level: .info, tag: tag, message: "Dashboard metrics empty, loading from API as fallback")
                Task {
                    do {
                        try await dashboardStore.metricsManager.loadMetricsFromAPI()
                        await MainActor.run {
                            dashboardStore.syncRemovalStateFromMetricsManager()
                        }
                    } catch {
                        LoggerService.shared.log(level: .error, tag: tag, message: "Failed to load dashboard metrics from API: \(error.localizedDescription)")
                    }
                }
            } else {
                LoggerService.shared.log(level: .info, tag: tag, message: "Preserving existing dashboard metrics order and removal state (account-based, independent of scale)")
            }
        }
    }

    /// Sets up subscriptions for dashboard metrics customization screen
    func setupDashboardMetricsSubscriptions() {
        dashboardStoreCancellable?.cancel()
        dashboardStoreCancellable = dashboardStore.objectWillChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self else { return }
                if self.currentStep == .viewSettings && self.currentCustomizeSetting == .dashboardMetrics {
                    self.updateNextEnabled()
                }
            }
        setupDashboardMetricsSync()
    }

    /// Sets up synchronization between customize screen and main dashboard
    func setupDashboardMetricsSync() {
        dashboardMetricsUpdatedCancellable?.cancel()
        dashboardMetricsUpdatedCancellable = NotificationCenter.default.publisher(for: .dashboardMetricsUpdated)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self else { return }
                if self.currentStep == .viewSettings &&
                   self.currentCustomizeSetting == .dashboardMetrics &&
                   !self.isExiting {
                }
            }
    }

    /// Persists dashboard metric customization before navigation.
    func persistDashboardMetricsIfNeeded() {
        guard selectedCustomizeItems.contains(CustomizeSettingsItem.dashboardMetrics.rawValue) else {
            return
        }
        Task { @MainActor [weak self] in
            guard let self else { return }
            await self.persistDashboardMetrics()
        }
    }

    /// Persists dashboard metrics to the API.
    @MainActor
    func persistDashboardMetrics() async {
        do {
            dashboardStore.syncRemovalStateFromMetricsManager()
            dashboardStore.syncRemovalStateFromStreakManager()
            try await dashboardStore.metricsManager.saveMetricsToAPI()
            try await dashboardStore.saveProgressMetricsToAPI()
            _ = try? await accountService.refreshAccount(
                accountId: accountService.activeAccount?.accountId
            )
            dashboardStore.updateSnapshot()
            NotificationCenter.default.post(
                name: .dashboardMetricsUpdated,
                object: nil
            )
            LoggerService.shared.log(
                level: .info,
                tag: tag,
                message: "Dashboard metrics persisted with latest order"
            )
        } catch {
            LoggerService.shared.log(
                level: .error,
                tag: tag,
                message: "Failed to persist dashboard metrics: \(error.localizedDescription)"
            )
        }
    }
}
