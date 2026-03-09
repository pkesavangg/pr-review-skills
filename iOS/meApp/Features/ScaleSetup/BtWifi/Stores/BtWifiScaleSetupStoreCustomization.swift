import Combine
import Foundation
import SwiftUI

@MainActor
extension BtWifiScaleSetupStore {
    /// Sets the customization page and navigates to view settings
    func setCustomizationPage(_ setting: CustomizeSettings) {
        currentCustomizeSetting = setting
        preloadDataForCustomizationPage(setting)
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 200_000_000)
            self.navigateToStep(.viewSettings)
        }
    }

    private func preloadDataForCustomizationPage(_ setting: CustomizeSettings) {
        switch setting {
        case .scaleUsername:
            setupScaleUsernameForm()
        case .scaleMode:
            preloadScaleMode()
        case .scaleMetrics:
            preloadScaleMetrics()
        case .dashboardMetrics:
            Task { [weak self] in
                guard let self else { return }
                await self.setupDashboardMetricsCustomization()
            }
        default:
            break
        }
    }

    private func preloadScaleMode() {
        Task { [weak self] in
            guard let self, let savedScale = self.savedScale else { return }
            if let preference = await self.scaleService.fetchAttachedPreference(by: savedScale.id) {
                await MainActor.run {
                    self.selectedScaleMode = preference.shouldMeasureImpedance ? .allBodyMetrics : .weightOnly
                    self.isHeartRateEnabled = preference.shouldMeasurePulse
                }
            }
        }
        initialScaleModeSnapshot = selectedScaleMode
        initialHeartRateEnabledSnapshot = isHeartRateEnabled
    }

    private func preloadScaleMetrics() {
        Task { [weak self] in
            guard let self else { return }
            if let savedScale = self.savedScale,
               let preference = await self.scaleService.fetchAttachedPreference(by: savedScale.id) {
                await MainActor.run {
                    self.selectedScaleMetrics = Array(preference.displayMetrics)
                    self.initialScaleMetricsSnapshot = Array(preference.displayMetrics)
                    if self.savedScaleMetricsSnapshot == nil {
                        self.savedScaleMetricsSnapshot = Array(preference.displayMetrics)
                    }
                }
            } else {
                await MainActor.run {
                    self.selectedScaleMetrics = ScaleMetrics.defaultMetricsKeys
                    self.initialScaleMetricsSnapshot = ScaleMetrics.defaultMetricsKeys
                    if self.savedScaleMetricsSnapshot == nil {
                        self.savedScaleMetricsSnapshot = ScaleMetrics.defaultMetricsKeys
                    }
                }
            }
        }
    }

    /// Handles scale mode and heart rate changes
    func handleScaleModeChange(_ scaleMode: ScaleModes, heartRateEnabled: Bool) {
        selectedScaleMode = scaleMode
        isHeartRateEnabled = heartRateEnabled
        updateNextEnabled()
    }

    /// Shows the showAccuCheckInfoModal.
    func showAccuCheckInfoModal() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(AccuCheckInfoModalView {
                self.notificationService.dismissModal()
            })
        ))
    }

    /// Adds a customize settings item to the visited items set (tracks that user has opened this screen)
    func addSelectedCustomizeItem(_ item: String) {
        visitedCustomizeItems.insert(item)
    }

    /// Checks if a customize settings item has been visited
    func isCustomizeItemSelected(_ item: String) -> Bool {
        return visitedCustomizeItems.contains(item)
    }

    /// Performs save for current view setting and navigates back. Called from ActionHandlers.
    func performViewSettingsSave() {
        applySaveForCurrentViewSetting()
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 200_000_000)
            self.currentCustomizeSetting = .none
        }
        moveToPreviousStep()
    }

    /// Performs revert for current view setting and clears selection. Called from ActionHandlers.
    func performViewSettingsBack() {
        dashboardMetricsUpdatedCancellable?.cancel()
        dashboardMetricsUpdatedCancellable = nil
        revertViewSettingsFor(currentCustomizeSetting)
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 500_000_000)
            self.currentCustomizeSetting = .none
        }
    }

    private func applySaveForCurrentViewSetting() {
        switch currentCustomizeSetting {
        case .scaleUsername:
            saveViewSettingsScaleUsername()
        case .scaleMode:
            saveViewSettingsScaleMode()
        case .scaleMetrics:
            saveViewSettingsScaleMetrics()
        case .dashboardMetrics:
            saveViewSettingsDashboardMetrics()
        default:
            break
        }
    }

    private func saveViewSettingsScaleUsername() {
        guard userNameForm.displayName.isValid else { return }
        if let savedScale = savedScale {
            Task {
                if let attached = await scaleService.fetchAttachedPreference(by: savedScale.id) {
                    attached.displayName = userNameForm.displayName.value
                }
            }
        }
        hasCustomizeChanges = true
        hasSavedSettings = true
    }

    private func saveViewSettingsScaleMode() {
        if let savedScale = savedScale {
            Task {
                if let attached = await scaleService.fetchAttachedPreference(by: savedScale.id) {
                    attached.shouldMeasureImpedance = (selectedScaleMode == .allBodyMetrics)
                    attached.shouldMeasurePulse = isHeartRateEnabled
                }
            }
        }
        hasCustomizeChanges = true
        hasSavedSettings = true
    }

    private func saveViewSettingsScaleMetrics() {
        if let savedScale = savedScale {
            Task {
                if let attached = await scaleService.fetchAttachedPreference(by: savedScale.id) {
                    attached.displayMetrics = selectedScaleMetrics
                    await MainActor.run { self.savedScaleMetricsSnapshot = self.selectedScaleMetrics }
                }
            }
        }
        hasCustomizeChanges = true
        hasSavedSettings = true
        selectedCustomizeItems.insert(CustomizeSettingsItem.scaleMetrics.rawValue)
    }

    private func saveViewSettingsDashboardMetrics() {
        hasCustomizeChanges = true
        hasSavedSettings = true
        selectedCustomizeItems.insert(CustomizeSettingsItem.dashboardMetrics.rawValue)
        Task { @MainActor in
            dashboardStore.gridEditingManager.syncRemovalStateFromMetricsManager()
            dashboardStore.updateSnapshot()
            dashboardStore.state.ui.gridLayoutId = UUID()
            dashboardStore.objectWillChange.send()
        }
    }

    private func revertViewSettingsFor(_ setting: CustomizeSettings) {
        switch setting {
        case .dashboardMetrics:
            let hasSaved = hasSavedSettings && selectedCustomizeItems.contains(CustomizeSettingsItem.dashboardMetrics.rawValue)
            if hasSaved { dashboardStore.cancelEdit() } else { discardDashboardCustomization() }
        case .scaleMode:
            revertViewSettingsScaleMode()
        case .scaleUsername:
            if let original = initialDisplayNameSnapshot {
                userNameForm.setDisplayName(original)
                resetFormState()
            }
        case .scaleMetrics:
            if let savedState = savedScaleMetricsSnapshot { selectedScaleMetrics = savedState }
        default:
            break
        }
    }

    private func revertViewSettingsScaleMode() {
        guard let savedScale = savedScale else { return }
        let impedance = savedScale.r4ScalePreference?.shouldMeasureImpedance == true
        let pulse = savedScale.r4ScalePreference?.shouldMeasurePulse ?? false
        selectedScaleMode = initialScaleModeSnapshot ?? (impedance ? .allBodyMetrics : .weightOnly)
        isHeartRateEnabled = initialHeartRateEnabledSnapshot ?? pulse
    }

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

    /// Applies the updated preference locally and navigates to stepOn on success.
    private func applyUpdatedPreferenceLocallyAndNavigate(savedScale: Device, updatedPreference: R4ScalePreference) async {
        do {
            try await scaleService.updateScalePreference(savedScale.id, updatedPreference)
            hasCustomizeChanges = false
            hasSavedSettings = false
            LoggerService.shared.log(level: .info, tag: tag, message: "updateCustomizeSettings - settings updated successfully: \(updatedPreference)")
            selectedCustomizeItems.removeAll()
            scaleSetupError = .none
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 250_000_000)
                self.navigateToStep(.stepOn)
            }
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "updateCustomizeSettings - failed locally: \(error.localizedDescription)")
            await MainActor.run { self.scaleSetupError = .updateSettingsFailed }
        }
    }

    /// Updates the customize settings on the scale
    func updateCustomizeSettings() async {
        guard let savedScale = savedScale else {
            LoggerService.shared.log(level: .error, tag: tag, message: "updateCustomizeSettings - no saved scale")
            await MainActor.run { self.scaleSetupError = .updateSettingsFailed }
            return
        }

        do {
            let currentPreference = await fetchOrCreateCurrentPreference(for: savedScale)
            let updatedPreference = buildUpdatedPreference(
                savedScale: savedScale,
                currentPreference: currentPreference
            )
            let timeoutTask = startUpdateSettingsTimeout()

            try await scaleService.updateScalePreference(savedScale.id, updatedPreference)
            await scaleService.pushLocalChangesToServer()
            let result = await bluetoothService.updateAccount(on: savedScale, preference: updatedPreference)

            switch result {
            case .success:
                LoggerService.shared.log(level: .info, tag: tag, message: "updateCustomizeSettings - scale preference updated successfully")
            case let .failure(updateError):
                LoggerService.shared.log(level: .error, tag: tag, message: "updateCustomizeSettings - failed: \(updateError.localizedDescription)")
            }

            timeoutTask.cancel()

            switch result {
            case .success:
                await applyUpdatedPreferenceLocallyAndNavigate(savedScale: savedScale, updatedPreference: updatedPreference)
            case .failure:
                LoggerService.shared.log(level: .error, tag: tag, message: "updateCustomizeSettings - failed to update account")
                await MainActor.run { self.scaleSetupError = .updateSettingsFailed }
            }
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "updateCustomizeSettings - failed: \(error.localizedDescription)")
            await MainActor.run { self.scaleSetupError = .updateSettingsFailed }
        }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            self.bluetoothService.syncDevices([])
        }
    }

    private func fetchOrCreateCurrentPreference(for savedScale: Device) async -> R4ScalePreference {
        if let attached = await scaleService.fetchAttachedPreference(by: savedScale.id) {
            return attached
        }
        let defaultDTO = R4ScalePreferenceDTO(
            scaleId: savedScale.id,
            displayName: firstName ?? "User",
            displayMetrics: ScaleMetrics.defaultMetricsKeys,
            shouldFactoryReset: false,
            shouldMeasureImpedance: true,
            shouldMeasurePulse: false,
            timeFormat: "12",
            tzOffset: DateTimeTools.getTimeZoneInMinutes(),
            wifiFotaScheduleTime: 0,
            updatedAt: DateTimeTools.getCurrentDatetimeIsoString(),
            isTemporary: true
        )
        return R4ScalePreference(from: defaultDTO, scaleId: savedScale.id)
    }

    private func buildUpdatedPreference(savedScale: Device, currentPreference: R4ScalePreference) -> R4ScalePreference {
        let saveScaleMetrics = selectedCustomizeItems.contains(CustomizeSettingsItem.scaleMetrics.rawValue)
        let saveScaleMode = selectedCustomizeItems.contains(CustomizeSettingsItem.scaleModes.rawValue)
        let saveScaleUsername = selectedCustomizeItems.contains(CustomizeSettingsItem.userName.rawValue)
        let displayName = saveScaleUsername
            ? (userNameForm.displayName.value.isEmpty ? (firstName ?? "User") : userNameForm.displayName.value)
            : currentPreference.displayName
        let dto = R4ScalePreferenceDTO(
            scaleId: savedScale.id,
            displayName: displayName,
            displayMetrics: saveScaleMetrics ? selectedScaleMetrics : currentPreference.displayMetrics,
            shouldFactoryReset: false,
            shouldMeasureImpedance: saveScaleMode ? (selectedScaleMode == .allBodyMetrics) : currentPreference.shouldMeasureImpedance,
            shouldMeasurePulse: saveScaleMode ? isHeartRateEnabled : currentPreference.shouldMeasurePulse,
            timeFormat: "12",
            tzOffset: DateTimeTools.getTimeZoneInMinutes(),
            wifiFotaScheduleTime: 0,
            updatedAt: DateTimeTools.getCurrentDatetimeIsoString(),
            isTemporary: true
        )
        return R4ScalePreference(from: dto, scaleId: savedScale.id)
    }

    private func startUpdateSettingsTimeout() -> Task<Void, Never> {
        Task { [weak self] in
            guard let timeout = self?.timeoutConstants.updateSettingsTimeout else { return }
            try? await Task.sleep(nanoseconds: UInt64(timeout))
            guard !Task.isCancelled else { return }
            await MainActor.run {
                guard let self = self else { return }
                if self.currentStep == .updateSettings {
                    self.scaleSetupError = .updateSettingsFailed
                    LoggerService.shared.log(level: .error, tag: self.tag, message: "updateCustomizeSettings - timeout occurred")
                }
            }
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
            try await scaleService.updateAllScalesStatus(nil)
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

    // MARK: - Customization Dashboard

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
        guard initialDashboardMetricLabelsSnapshot != nil else { return false }

        let state = dashboardStore.metricsManager.state.metrics.map { $0.label }
        let removed = dashboardStore.state.ui.removedMetrics
        let removedStreaks = dashboardStore.state.ui.removedStreaks
        let order = dashboardStore.state.ui.streakGridOrder
        let goalRemoved = dashboardStore.state.ui.isGoalCardRemoved
        let goalPos = dashboardStore.state.ui.goalCardPosition

        return (initialDashboardMetricLabelsSnapshot != state) ||
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
            LoggerService.shared.log(
                level: .error,
                tag: tag,
                message: "R4 setup: Failed to update dashboard type on server: \(error.localizedDescription)"
            )
        }

        await MainActor.run {
            dashboardStore.metricsManager.updateDashboardType(.dashboard12)
            dashboardStore.state.metrics.dashboardType = .dashboard12
            dashboardStore.metricsManager.setupInitialMetrics(forceShowAll: true)
            dashboardStore.metricsManager.resetOrderToDefault()
            dashboardStore.gridEditingManager.syncRemovalStateFromMetricsManager()
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
            await dashboardStore.gridEditingManager.loadProgressMetricsFromAccount()
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
            dashboardStore.gridEditingManager.syncRemovalStateFromMetricsManager()
            if dashboardStore.metricsManager.state.metrics.isEmpty {
                LoggerService.shared.log(level: .info, tag: tag, message: "Dashboard metrics empty, loading from API as fallback")
                Task {
                    do {
                        try await dashboardStore.metricsManager.loadMetricsFromAPI()
                        await MainActor.run {
                            dashboardStore.gridEditingManager.syncRemovalStateFromMetricsManager()
                        }
                    } catch {
                        LoggerService.shared.log(
                            level: .error,
                            tag: tag,
                            message: "Failed to load dashboard metrics from API: \(error.localizedDescription)"
                        )
                    }
                }
            } else {
                LoggerService.shared.log(
                    level: .info,
                    tag: tag,
                    message: "Preserving existing dashboard metrics order and removal state (account-based, independent of scale)"
                )
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
                if self.currentStep == .viewSettings, self.currentCustomizeSetting == .dashboardMetrics {
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
                if self.currentStep == .viewSettings,
                   self.currentCustomizeSetting == .dashboardMetrics,
                   !self.isExiting {}
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
            dashboardStore.gridEditingManager.syncRemovalStateFromMetricsManager()
            dashboardStore.gridEditingManager.syncRemovalStateFromStreakManager()
            try await dashboardStore.metricsManager.saveMetricsToAPI()
            try await dashboardStore.lifecycleManager.saveProgressMetricsToAPI()
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
} // swiftlint:disable:this file_length
