import Combine
import Foundation
import SwiftUI

// swiftlint:disable file_length type_body_length cyclomatic_complexity function_body_length

@MainActor
extension BtWifiScaleSetupStore {
    func setupScaleUsernameForm() {
        let initialDisplayName = firstName ?? "User"
        userNameForm.setDisplayName(initialDisplayName)
        initialDisplayNameSnapshot = initialDisplayName
        userNameForm.setCurrentUserName(initialDisplayName)
        resetFormState()
        
        Task { [weak self] in
            guard let self else { return }
            if self.userList.isEmpty {
                await self.getUserList()
            }
            
            var displayName = self.firstName ?? "User"
            if let savedScale = self.savedScale,
               let attached = await self.scaleService.fetchAttachedPreference(by: savedScale.id) {
                displayName = attached.displayName
            }
            
            await MainActor.run {
                if displayName != self.userNameForm.displayName.value {
                    self.userNameForm.setDisplayName(displayName)
                    self.initialDisplayNameSnapshot = displayName
                    self.userNameForm.setCurrentUserName(displayName)
                }
                
                let scaleUsers = self.userList.map { deviceUser in
                    ScaleUser(name: deviceUser.name, token: deviceUser.token)
                }
                self.userNameForm.updateUserList(scaleUsers)
                self.resetFormState()
                self.updateNextEnabled()
            }
        }
    }
    
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
        // Remove from selected items (unsaved changes), but keep in visited items (checkmark stays)
        selectedCustomizeItems.remove(CustomizeSettingsItem.dashboardMetrics.rawValue)
        // Only clear hasCustomizeChanges if no other settings have been changed
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
    /// Enables all metrics. Call only when current dashboard type is 4.
    func upgradeDashboardTypeFrom4To12WithDefaults() async {
        // Check if dashboard is already type 12 - if so, don't do anything
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
// swiftlint:disable:next line_length
            LoggerService.shared.log(level: .error, tag: tag, message: "R4 setup: Failed to update dashboard type on server: \(error.localizedDescription)")
        }
        
        await MainActor.run {
            dashboardStore.metricsManager.updateDashboardType(.dashboard12)
            dashboardStore.state.metrics.dashboardType = .dashboard12
            
            // True 4 → 12 upgrade; preserve existing metric order
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
        
        // Only refresh account data to ensure we have latest settings, but don't reload metrics
        // This ensures the API has the updated dashboard type without resetting the metrics order
        _ = try? await accountService.refreshAccount(accountId: accountService.activeAccount?.accountId)
    }
    
    /// Checks if the current dashboard type is dashboard4
    var isDashboardTypeFour: Bool {
        let currentDashboardType = accountService.activeAccount?.dashboardSettings?.dashboardType
        let result = (currentDashboardType == "dashboard_4_metrics" || 
                currentDashboardType == "dashboard4") &&
                dashboardStore.effectiveDashboardType == .dashboard4
        return result
    }
    
    // Sets up dashboard metrics customization screen with proper state management.
    // First pairing upgrades dashboard and sets default order; subsequent pairings preserve current order
    func setupDashboardMetricsCustomization() async {
        let isDashboardFour = isDashboardTypeFour
        
        if isDashboardFour {
            await upgradeDashboardTypeFrom4To12WithDefaults()
            // Ensure streak data is refreshed and progress metrics are loaded for dashboard 4 upgrade
            try? await dashboardStore.streakManager.refreshStreakData()
            await dashboardStore.loadProgressMetricsFromAccount()
        } else {
            await MainActor.run {
                dashboardStore.syncRemovalStateFromMetricsManager()
                
                if dashboardStore.metricsManager.state.metrics.isEmpty {
                    LoggerService.shared.log(level: .info, tag: tag, message: "Dashboard metrics empty, loading from API as fallback")
                    Task {
                        // Only load metrics from API, don't do full reload which might reset other state
                        do {
                            try await dashboardStore.metricsManager.loadMetricsFromAPI()
                            await MainActor.run {
                                dashboardStore.syncRemovalStateFromMetricsManager()
                            }
                        } catch {
// swiftlint:disable:next line_length
                            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to load dashboard metrics from API: \(error.localizedDescription)")
                        }
                    }
                } else {
// swiftlint:disable:next line_length
                    LoggerService.shared.log(level: .info, tag: tag, message: "Preserving existing dashboard metrics order and removal state (account-based, independent of scale)")
                }
            }
        }
        
        await MainActor.run {
            dashboardStore.beginEdit()
            dashboardStore.state.ui.isEditMode = true
            snapshotDashboardState()
        }
        
        setupDashboardMetricsSubscriptions()
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
    
    // MARK: - Dashboard Synchronization
    
    /// Sets up synchronization between customize screen and main dashboard
    /// When dashboard metrics are updated in the main dashboard, this ensures
    /// the customize screen reflects those changes immediately
    func setupDashboardMetricsSync() {
        // Cancel existing subscription if any
        dashboardMetricsUpdatedCancellable?.cancel()
        
        // Subscribe to dashboard metrics updated notification

        dashboardMetricsUpdatedCancellable = NotificationCenter.default.publisher(for: .dashboardMetricsUpdated)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] _ in
                guard let self else { return }
                // Only reload if we're currently on the dashboard metrics customization screen
                if self.currentStep == .viewSettings && 
                   self.currentCustomizeSetting == .dashboardMetrics && 
                   !self.isExiting {
                }
            }
    }
    
    /// Persists dashboard metric customization before navigation.
    /// Runs only when dashboard customization is selected.
    /// Errors are logged and do not block navigation.

    func persistDashboardMetricsIfNeeded() {
        guard selectedCustomizeItems.contains(CustomizeSettingsItem.dashboardMetrics.rawValue) else {
            return
        }
        
        Task { @MainActor [weak self] in
            guard let self else { return }
            await self.persistDashboardMetrics()
        }
    }
    
    //// Persists dashboard metrics to the API.
    /// Separated for testability.
    @MainActor
    func persistDashboardMetrics() async {
        do {
            // Sync removal state
            dashboardStore.syncRemovalStateFromMetricsManager()
            dashboardStore.syncRemovalStateFromStreakManager()
            
            // Persist metrics
            try await dashboardStore.metricsManager.saveMetricsToAPI()
            try await dashboardStore.saveProgressMetricsToAPI()
            
            // Refresh account (non-blocking)
            _ = try? await accountService.refreshAccount(
                accountId: accountService.activeAccount?.accountId
            )
            
            // Update snapshot for rollback
            dashboardStore.updateSnapshot()
            
            // Notify dashboard refresh
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
    
    // MARK: - Cleanup Methods
    
    /// Checks if "Set a Goal" modal should be shown after setup completes
    /// This handles the case where the 3rd entry was taken during setup
    func checkGoalModalAfterSetup() {
        Task { @MainActor [weak self] in
            guard let self else { return }
            // Add delay of 1.5 seconds after setup is closed, similar to other scale setups
            try? await Task.sleep(nanoseconds: 1_500_000_000) // 1.5 seconds
            do {
                let entryCount = try await self.entryService.getEntryCount()
                await self.goalAlertService.checkSetGoalCard(entryCount: entryCount)
            } catch {
                // Silently ignore errors - goal modal check is not critical
            }
        }
    }
    
    /// Cleans up the store and breaks any retain cycles
    func cleanup() {
        // Clear the dismiss action to break retain cycle
        dismissAction = nil
        
        // Cancel all tasks
        fetchWifiNetworksTask?.cancel()
        fetchWifiNetworksTask = nil
        
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        networkFormCancellable?.cancel()
        networkFormCancellable = nil
        newEntrySubscription?.cancel()
        newEntrySubscription = nil
        liveMeasurementSubscription?.cancel()
        liveMeasurementSubscription = nil
        measurementTimeoutTask?.cancel()
        measurementTimeoutTask = nil
        stepOnTimeoutTask?.cancel()
        stepOnTimeoutTask = nil
        stepTimerTask?.cancel()
        stepTimerTask = nil
        dashboardStoreCancellable?.cancel()
        dashboardStoreCancellable = nil
        dashboardMetricsUpdatedCancellable?.cancel()
        dashboardMetricsUpdatedCancellable = nil
        
        // Cancel all cancellables
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()

        bluetoothService.isSetupInProgress = false
        
        // Clear other references
        discoveredScale = nil
        discoveryEvent = nil
        savedScale = nil
        // Re-apply skipped devices to BLE SDK, excluding paired scales
        bluetoothService.reapplySkipDevicesExcludingPaired()
        
        Task { @MainActor [weak self] in
            self?.isExiting = false
            self?.isExitingFromStepOn = false
        }
    }
    
    /// Resumes scanning and syncs all paired devices after setup exits
    func resumeScanningAndSyncDevices() async {
        bluetoothService.resumeSmartScan(clearOnlyPairing: false)
        
        do {
            try await scaleService.updateAllScalesStatus()
            bluetoothService.syncDevices([])
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to resume scanning and sync devices: \(error.localizedDescription)")
        }
    }
    
    // Disconnects scale if it's not saved to ensure it shouldn't appears again in discovery.
    func disconnectDevice() {
        guard let broadcastId = discoveredScale?.broadcastIdString, !broadcastId.isEmpty, savedScale == nil else { return }
        Task {
            await bluetoothSetupManager.disconnectIfNeeded(
                broadcastId: broadcastId,
                bluetoothService: bluetoothService,
                considerForSession: true
            )
        }
    }
    
    // Cancels Wi-Fi to hide connecting to wifi screen on 0412 scale.
    func cancelWifi() {
        // Cancel any in-flight Wi-Fi setup
        let scaleToCancel = discoveredScale ?? savedScale
        if let scaleToCancel = scaleToCancel {
            Task {
                await bluetoothSetupManager.cancelWifi(on: scaleToCancel, bluetoothService: bluetoothService)
            }
        }
    }
    
}
// swiftlint:enable file_length type_body_length cyclomatic_complexity function_body_length
