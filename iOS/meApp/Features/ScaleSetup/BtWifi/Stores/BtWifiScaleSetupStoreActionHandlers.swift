import Combine
import Foundation
import SwiftUI

// swiftlint:disable file_length type_body_length cyclomatic_complexity function_body_length

@MainActor
extension BtWifiScaleSetupStore {
    func handleBackButtonClick() {
        // Settings WiFi setup: show exit alert when back button is tapped in WiFi list
        if handleSettingsWifiSetupExit() {
            return
        }
        
        if currentStep == .wifiPassword {
            resetNetworkForm()
        } else if currentStep == .viewSettings {
            handleViewSettingsBack()
        }
        moveToPreviousStep()
    }
    
    /// Handles the restore account action from the duplicate user screen
    func handleRestoreAccount() {
        let alertStrings = alertLang.ConfirmRestoreAlert.self
        let alert = AlertModel(
            title: alertStrings.title,
            message: alertStrings.message,
            buttons: [
                AlertButtonModel(title: alertStrings.backButton, type: .secondary) { _ in },
                AlertButtonModel(title: alertStrings.restoreButton, type: .primary) { [weak self] _ in
                    Task {
                        await self?.performRestoreAccount()
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Performs the restore account operation by finding and deleting the matching user on the scale
    func performRestoreAccount() async {
        /// Restore requires Bluetooth (internet not required)
        guard hasAllBtPermissions() else {
            notificationService.dismissAlert()
            resetDiscoveryState()
            navigateToStep(.permissions)
            return
        }       
        guard let scale = discoveredScale else {
            scaleSetupError = .duplicatesFound
            return
        }
        
        let accountName = getAccountNameForRestore()
		userNameForm.setDisplayName(accountName)
        guard !accountName.isEmpty else {
            scaleSetupError = .duplicatesFound
            return
        }
        
        guard let matchingUser = await findMatchingUserOnScale(scale: scale, accountName: accountName) else {
            scaleSetupError = .duplicatesFound
            return
        }
        
        guard await deleteMatchingUserFromScale(scale: scale, user: matchingUser) else {
            scaleSetupError = .duplicatesFound
            return
        }
        
        await restartConnectionAndNavigate()
    }
    
    /// Gets the account name to restore, using the original name that exists on the scale
    /// (not the edited duplicateUserName, since restore should use the original account name)
    func getAccountNameForRestore() -> String {
        // Use the original name from currentUser (the duplicate user on the scale) or firstName
        // This ensures restore uses the original name, not any edited name
        if let originalName = currentUser?.name, !originalName.isEmpty {
            return originalName.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return (firstName ?? "User").trimmingCharacters(in: .whitespacesAndNewlines)
    }
    
    /// Finds a matching user on the scale by comparing account name (handles name truncation)
    func findMatchingUserOnScale(scale: Device, accountName: String) async -> DeviceUser? {
        let userListResult = await bluetoothService.getScaleUserList(for: scale, skipConnectionCheck: true)
        guard case .success(let allUsers) = userListResult else {
            return nil
        }
        
        let normalizedAccountName = accountName.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        let maxScaleNameLength = 20 // Scale truncates names to 20 characters
        let truncatedAccountName = String(normalizedAccountName.prefix(maxScaleNameLength))
        
        // Try exact match first, then truncated match
        return allUsers.first { $0.name.lowercased() == normalizedAccountName }
            ?? allUsers.first { $0.name.lowercased() == truncatedAccountName }
    }
    
    /// Deletes a matching user from the scale during restore account flow
    func deleteMatchingUserFromScale(scale: Device, user: DeviceUser) async -> Bool {
        guard let userToken = user.token, !userToken.isEmpty else {
            return false
        }
        guard let broadcastId = scale.broadcastIdString, !broadcastId.isEmpty else {
            return false
        }

        // Use deleteUserByToken to avoid mutating @Model token property
        let deleteResult = await bluetoothService.deleteUserByToken(
            broadcastId: broadcastId,
            token: userToken,
            disconnect: false
        )

        switch deleteResult {
        case .success:
            return true
        case .failure:
            return false
        }
    }
    
    /// Determines which username value should be preserved when restarting the connection.
    /// - Parameter preservedUsername: The trimmed username currently entered in the form.
    /// - Returns: The username that should be kept visible to the user.
    func resolveUsernameToPreserve(from preservedUsername: String) -> String {
        if !preservedUsername.isEmpty {
            return preservedUsername
        }
        
        if !duplicateUserName.isEmpty {
            return duplicateUserName
        }
        
        return firstName ?? "User"
    }
    
    /// Restarts the connection and navigates to the connecting step
    func restartConnectionAndNavigate() async {
        // Preserve the current username value from form field before resetting
        // This ensures the username doesn't get cleared when restore account is tapped
        let preservedUsername = userNameForm.displayName.value.trimmingCharacters(in: .whitespacesAndNewlines)
        let usernameToPreserve = resolveUsernameToPreserve(from: preservedUsername)
        
        try? await Task.sleep(nanoseconds: 500_000_000) // 0.5 seconds
        scaleSetupError = .none
        await restartConnection()
        
        // Restore the username value after reset so it's still visible in the form
        userNameForm.setDisplayName(usernameToPreserve)
        duplicateUserName = usernameToPreserve
        
        navigateToStep(.connectingBluetooth)
    }
    
    /// Handles the delete user action from the max user count exceeded screen
    func handleDeleteUser(_ user: DeviceUser) {
        let alertStrings = alertLang.ConfirmDeleteUserAlert.self
        let alert = AlertModel(
            title: alertStrings.title,
            message: alertStrings.message(user.name),
            buttons: [
                AlertButtonModel(title: alertStrings.goBackButton, type: .secondary) { _ in },
                AlertButtonModel(title: alertStrings.deleteButton, type: .danger) { [weak self] _ in
                    Task {
                        await self?.deleteUserFromScale(user)
                        // Reset to normal state and retry connection
                        Task { @MainActor in
                            try? await Task.sleep(nanoseconds: 250_000_000)
                            self?.scaleSetupError = .none
                        }
                        self?.navigateToStep(.connectingBluetooth)
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Handles the skip WiFi step action
    func handleSkipWifiStep() {
        let alertStrings = alertLang.SkipWifiStepAlert.self
        let alert = AlertModel(
            title: alertStrings.title,
            message: alertStrings.message,
            buttons: [
                AlertButtonModel(title: alertStrings.goBackButton, type: .secondary) { _ in },
                AlertButtonModel(title: alertStrings.skipButton, type: .primary) { [weak self] _ in
                    self?.cancelWifi()
                    self?.scaleSetupError = .none
                    Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 100_000_000)
                        self?.navigateToStep(.customizeSettings)
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Sets the customization page and navigates to view settings
    func setCustomizationPage(_ setting: CustomizeSettings) {
        currentCustomizeSetting = setting
        
        // Pre-populate form data based on the setting
        switch setting {
        case .scaleUsername:
            setupScaleUsernameForm()
            
        case .scaleMode:
            // Pre-populate scale mode settings from attached preference
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
        case .scaleMetrics:
            // Preload saved display metrics from an attached preference
            Task { [weak self] in
                guard let self else { return }
                if let savedScale = self.savedScale,
                   let preference = await self.scaleService.fetchAttachedPreference(by: savedScale.id) {
                    await MainActor.run { 
                        self.selectedScaleMetrics = Array(preference.displayMetrics)
                        // Set initial snapshot to current saved state
                        self.initialScaleMetricsSnapshot = Array(preference.displayMetrics)
                        // If no saved snapshot exists, set it to current saved state
                        if self.savedScaleMetricsSnapshot == nil {
                            self.savedScaleMetricsSnapshot = Array(preference.displayMetrics)
                        }
                    }
                } else {
                    await MainActor.run { 
                        self.selectedScaleMetrics = ScaleMetrics.defaultMetricsKeys
                        // Set initial snapshot to default state
                        self.initialScaleMetricsSnapshot = ScaleMetrics.defaultMetricsKeys
                        // If no saved snapshot exists, set it to default state
                        if self.savedScaleMetricsSnapshot == nil {
                            self.savedScaleMetricsSnapshot = ScaleMetrics.defaultMetricsKeys
                        }
                    }
                }
            }
        case .dashboardMetrics:
            Task { [weak self] in
                guard let self else { return }
                await self.setupDashboardMetricsCustomization()
            }
        default:
            break
        }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 200_000_000)
            self.navigateToStep(.viewSettings)
        }
    }
    
    /// Handles scale mode and heart rate changes
    func handleScaleModeChange(_ scaleMode: ScaleModes, heartRateEnabled: Bool) {
        selectedScaleMode = scaleMode
        isHeartRateEnabled = heartRateEnabled
        
        // Update the next button state
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
    
    /// Handles the save action from the view settings screen
    func handleViewSettingsAction() {
        switch currentCustomizeSetting {
        case .scaleUsername:
            // Validate the form first
            guard userNameForm.displayName.isValid else {
                return
            }

            // Update the attached scale preference with the new display name
            if let savedScale = savedScale {
                Task {
                    if let attached = await scaleService.fetchAttachedPreference(by: savedScale.id) {
                        attached.displayName = userNameForm.displayName.value
                    }
                }
            }
            self.hasCustomizeChanges = true
            self.hasSavedSettings = true
// swiftlint:disable:next switch_case_alignment
            case .scaleMode:
            // Update the attached preference with new scale mode settings
            if let savedScale = savedScale {
                Task {
                    if let attached = await scaleService.fetchAttachedPreference(by: savedScale.id) {
                        attached.shouldMeasureImpedance = (selectedScaleMode == .allBodyMetrics)
                        attached.shouldMeasurePulse = isHeartRateEnabled
                    }
                }
            }
            self.hasCustomizeChanges = true
            self.hasSavedSettings = true
// swiftlint:disable:next switch_case_alignment
            case .scaleMetrics:
            // Persist scale metrics changes immediately when Save is clicked
            if let savedScale = savedScale {
                Task {
                    if let attached = await scaleService.fetchAttachedPreference(by: savedScale.id) {
                        attached.displayMetrics = selectedScaleMetrics
                        // Update the saved snapshot to reflect the new saved state
                        await MainActor.run {
                            self.savedScaleMetricsSnapshot = self.selectedScaleMetrics
                        }
                    }
                }
            }
            self.hasCustomizeChanges = true
            self.hasSavedSettings = true
            self.selectedCustomizeItems.insert(CustomizeSettingsItem.scaleMetrics.rawValue)
// swiftlint:disable:next switch_case_alignment
            case .dashboardMetrics:
            hasCustomizeChanges = true
            hasSavedSettings = true
            selectedCustomizeItems.insert(CustomizeSettingsItem.dashboardMetrics.rawValue)

            // Sync state so back button reverts to saved state
            Task { @MainActor in
                self.dashboardStore.syncRemovalStateFromMetricsManager()

                // Save current state as snapshot for back button
                self.dashboardStore.updateSnapshot()

                // Refresh UI to show changes
                self.dashboardStore.state.ui.gridLayoutId = UUID()
                self.dashboardStore.objectWillChange.send()
            }
        default:
            break
        }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 200_000_000)
            self.currentCustomizeSetting = .none
        }
        self.moveToPreviousStep()
    }
    
    /// Handles the back action from the view settings screen
    func handleViewSettingsBack() {
        // Store the current setting before resetting for revert logic
        let settingToRevert = currentCustomizeSetting
        
        // Cancel dashboard metrics sync subscription when navigating away
        dashboardMetricsUpdatedCancellable?.cancel()
        dashboardMetricsUpdatedCancellable = nil
        
        // Revert unsaved changes for each customize screen
        switch settingToRevert {
        case .dashboardMetrics:
            // Check if user has saved dashboard metrics locally
            let hasSavedDashboardMetrics = hasSavedSettings && selectedCustomizeItems.contains(CustomizeSettingsItem.dashboardMetrics.rawValue)
            
            if hasSavedDashboardMetrics {
                // User has saved locally - revert any unsaved changes and exit edit mode
                // but keep dashboardMetrics in selectedCustomizeItems for API persistence on "next"
                dashboardStore.cancelEdit()
                // Don't remove from selectedCustomizeItems - keep it for API persistence
            } else {
                // User hasn't saved - discard all changes and remove from selectedCustomizeItems
                discardDashboardCustomization()
            }
        case .scaleMode:
            if let savedScale = savedScale {
                // Restore previously snapshotted values
// swiftlint:disable:next line_length
                let originalMode = initialScaleModeSnapshot ?? (savedScale.r4ScalePreference?.shouldMeasureImpedance == true ? .allBodyMetrics : .weightOnly)
                let originalPulse = initialHeartRateEnabledSnapshot ?? (savedScale.r4ScalePreference?.shouldMeasurePulse ?? false)
                selectedScaleMode = originalMode
                isHeartRateEnabled = originalPulse
            }
        case .scaleUsername:
            if let original = initialDisplayNameSnapshot {
                userNameForm.setDisplayName(original)
                resetFormState()
            }
        case .scaleMetrics:
            // Revert scale metrics to the last saved state (not the initial state)
            // This preserves previously saved changes and only reverts unsaved changes
            if let savedState = savedScaleMetricsSnapshot {
                selectedScaleMetrics = savedState
            }
        default:
            break
        }
        
        // Reset current customize setting after navigation completes to prevent showing placeholder during transition
        Task { @MainActor in
                try? await Task.sleep(nanoseconds: 500_000_000)
            self.currentCustomizeSetting = .none
        }
    }
    
    /// Handles the save action from the duplicate user screen
    func handleSaveDuplicateUser() {
        // Validate the form first
        guard userNameForm.displayName.isValid else { return }
        
        // Update duplicateUserName with the form value
        duplicateUserName = removeWhiteSpace(userNameForm.displayName.value)
        selectedCustomizeItems.insert(CustomizeSettingsItem.userName.rawValue)
        
        // Reset to normal state and retry connection
        scaleSetupError = .none
        connectionState = .loading
        navigateToStep(.connectingBluetooth)
    }
    
    /// Handles the WiFi password connect action
    func handleWifiPasswordConnect() {
        // Validate the form first
        guard networkForm.ssid.isValid else { return }
        if !networkForm.networkHasNoPassword {
            guard networkForm.password.isValid else { return }
        }
        
        // Move to connecting WiFi step
        connectionState = .loading
        navigateToStep(.connectingWifi)
    }
    
    /// Handles the network selection from the WiFi list
    func handleNetworkSelection(_ network: WifiDetails) {
        // Check permissions before navigating to password step
        let missingPermissions = !hasAllBtPermissions()
        let noNetwork = !networkMonitor.isConnected
        
        if missingPermissions || noNetwork {
            // Show error when permissions are missing (similar to Android behavior)
            // Navigate back to gathering network which will show the error screen
            connectionState = .noNetworks
            navigateToStep(.gatheringNetwork)
            return
        }
        
        selectedWifiNetwork = network
        networkForm.setSSID(selectedWifiNetwork?.ssid ?? "")
        navigateToStep(.wifiPassword)
        updateNextEnabled()
    }
    
    /// Handles the pairing process when entering the *wake-up* step.
    func pair() {
        // Start scanning for devices when entering wake-up step
        // Subscribe to discovery events (ensure we don't create multiple subscriptions).
        // Reset discovery state
        resetDiscoveryState()
        Task { bluetoothService.scanForPairing() }
        
        if deviceDiscoveryCancellable == nil {
            deviceDiscoveryCancellable = bluetoothService.deviceDiscoveredPublisher
                .receive(on: DispatchQueue.main)
                .sink { [weak self] discoveryEvent in
                    self?.handleDeviceDiscovery(discoveryEvent)
                }
        }
        
        /// Start a timer to handle the wake-up step timeout.
        stepTimerTask = Task { [weak self] in
            guard let timeoutConstants = self?.timeoutConstants.bluetoothTimeoutNs else { return }
            try? await Task.sleep(nanoseconds: UInt64(timeoutConstants))
            guard !Task.isCancelled else { return }
            await MainActor.run {
                guard let self else { return }
                // Still on wake-up step and nothing discovered → failure
                if self.discoveredScale == nil && self.currentStep == .wakeup {
                    // Navigate to connectingBluetooth first to maintain flow, then show the error
                    self.navigateToStep(.connectingBluetooth)
                    Task { @MainActor in
                            try? await Task.sleep(nanoseconds: 250_000_000)
                        self.connectionState = .failure
                    }
                }
            }
        }
    }
    
    /// Invoked from the *Try Again* button of `BluetoothConnectionView` and `WifiConnectionView` failure state.
    func tryAgainButtonHandler(isFromBtConnection: Bool = false) {
        // Don't navigate if we're exiting
        guard !isExiting else { return }
        
        // Determine which step to navigate to based on the error type
        let targetStep: BtWifiScaleSetupStep
        if isFromBtConnection {
            if discoveredScale != nil {
                targetStep = .connectingBluetooth
            } else {
                targetStep = .wakeup
            }
        } else if scaleSetupError == .collectMeasurementFailed {
            targetStep = .stepOn
        } else if scaleSetupError == .updateSettingsFailed {
            targetStep = .customizeSettings
        } else {
            // Retry WiFi network fetch
            targetStep = .gatheringNetwork

            fetchWifiNetworksTask?.cancel()
            fetchWifiNetworksTask = nil

            isRefreshingWifiNetworks = true
            scaleSetupError = .none
            connectionState = .loading
        }
        
        // Clear error state for other error types after a delay
        if scaleSetupError != .collectMeasurementFailed && targetStep != .gatheringNetwork {
            Task { @MainActor in
                            try? await Task.sleep(nanoseconds: 250_000_000)
                self.scaleSetupError = .none
                self.connectionState = .loading
            }
        }
        
        navigateToStep(targetStep)
    }
    
    // MARK: - Step Change Handling

}
// swiftlint:enable file_length type_body_length cyclomatic_complexity function_body_length
