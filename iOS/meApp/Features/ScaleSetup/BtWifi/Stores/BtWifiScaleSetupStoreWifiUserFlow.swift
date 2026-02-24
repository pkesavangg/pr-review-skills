import Combine
import Foundation
import SwiftUI

// swiftlint:disable cyclomatic_complexity function_body_length

@MainActor
extension BtWifiScaleSetupStore {
    func fetchWifiNetworks(for scale: Device) async {
        // Don't fetch if we're exiting or task is cancelled
        guard !isExiting, !Task.isCancelled else { return }
        
        // Check permissions before attempting to fetch WiFi networks
        let missingPermissions = !hasAllBtPermissions()
        let noNetwork = !networkMonitor.isConnected
        
        if missingPermissions || noNetwork {
            LoggerService.shared.log(level: .error, tag: tag, message: "Cannot fetch WiFi networks: permissions missing or network unavailable")
            await MainActor.run {
                guard !self.isExiting, !Task.isCancelled else { return }
                self.setConnectionState(.noNetworks, allowNetworkErrors: false)
            }
            return
        }
        
        // Set loading state
        await MainActor.run {
            guard !self.isExiting, !Task.isCancelled else { return }
            self.connectionState = .loading
        }
        
        do {
            // Check cancellation before starting network fetch
            guard !isExiting, !Task.isCancelled else { return }
            
            // Get connected WiFi SSID first
            let connectedSSIDResult = await bluetoothService.getConnectedWifiSSID(broadcastId: scale.broadcastIdString ?? "")
            
            // Check cancellation after async operation
            guard !isExiting, !Task.isCancelled else { return }
            
            var connectedSSID: String?
            switch connectedSSIDResult {
            case .success(let ssid):
                connectedSSID = ssid.isEmpty ? nil : ssid
            case .failure(let error):
                LoggerService.shared.log(level: .error, tag: tag, message: "Failed to get connected WiFi SSID: \(error.localizedDescription)")
                connectedSSID = nil
            }
            
            // Check cancellation before next async operation
            guard !isExiting, !Task.isCancelled else { return }
            
            // Get WiFi networks list
            let wifiListResult = await bluetoothService.getWifiList(for: scale)
            
            // Check cancellation after async operation
            guard !isExiting, !Task.isCancelled else { return }
            
            var networks: [WifiDetails] = []
            switch wifiListResult {
            case .success(let wifiList):
                networks = wifiList
            case .failure(let error):
                LoggerService.shared.log(level: .error, tag: tag, message: "Failed to get WiFi networks: \(error.localizedDescription)")
                throw error
            }
            
            await MainActor.run {
                // Don't navigate if we're exiting or task is cancelled
                guard !self.isExiting, !Task.isCancelled else { return }
                
                // Check Wi-Fi status again before navigating - if Wi-Fi was turned off during fetch, don't navigate
                let noNetwork = !self.networkMonitor.isConnected
                if noNetwork {
                    // Wi-Fi was turned off during fetch, stay on error screen
                    self.setConnectionState(.noNetworks, allowNetworkErrors: false)
                    self.scaleSetupError = .noNetworkFound
                    return
                }
                
                // Cancel timeout task since we successfully fetched networks
                self.stepTimerTask?.cancel()
                self.stepTimerTask = nil
                
                self.wifiNetworks = networks
                
                // Find connected network in the list
                if let connectedSSID = connectedSSID {
                    self.connectedWifiNetwork = WifiDetails(macAddress: "", ssid: connectedSSID, rssi: 0)
                } else {
                    self.connectedWifiNetwork = nil
                }
                
                // Double-check we're not exiting before updating states and navigating
                guard !self.isExiting, !Task.isCancelled else { return }
                
                // Check if no networks were found
                if networks.isEmpty {
                    self.scaleSetupError = .noNetworkFound
                    self.setConnectionState(.noNetworks, allowNetworkErrors: false)
                } else {
                    self.scaleSetupError = .none
                    self.connectionState = .success
                }
                
                // Final check before navigation - prevent navigation if exiting
                guard !self.isExiting, !Task.isCancelled else { return }
                
                // Navigate to available WiFi list
                self.navigateToStep(.availableWifiList)
            }
            
            LoggerService.shared.log(level: .info, tag: tag, message: "Successfully fetched WiFi networks: \(networks.count) networks found")
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to fetch WiFi networks: \(error.localizedDescription)")
            await MainActor.run {
                // Don't update state if we're exiting or task is cancelled
                guard !self.isExiting, !Task.isCancelled else { return }
                
                // Check if failure is due to missing permissions
                let missingPermissions = !self.hasAllBtPermissions()
                let noNetwork = !self.networkMonitor.isConnected
                
                if missingPermissions || noNetwork {
                    self.setConnectionState(.noNetworks, allowNetworkErrors: false)
                } else {
                    self.setConnectionState(.failure, allowNetworkErrors: false)
                    self.scaleSetupError = .noNetworkFound
                }
            }
        }
    }
    
    /// Sets up WiFi on the scale
    func setupWifi() async {
        guard let scale = savedScale else {
            LoggerService.shared.log(level: .error, tag: tag, message: "setupWifi - no saved scale")
            connectionState = .failure
            return
        }
        
        let networkConfig = networkForm.getRawValue()
        
        LoggerService.shared.log(level: .info, tag: tag, message: "WiFi setup started for SSID: \(networkConfig.ssid)")
        let wifiSetupResult = await bluetoothService.setupWifi(on: scale, config: networkConfig)
        switch wifiSetupResult {
        case .success(let response):
            switch response.wifiState {
            case "GG_WIFI_STATE_CONNECTED":
                LoggerService.shared.log(level: .info, tag: tag, message: "WiFi connected for: \(networkConfig.ssid)")
                self.scaleSetupError = .none
                self.connectionState = .success
                self.errorCode = nil

                // Update WiFi configuration status in local database
                if let broadcastId = scale.broadcastIdString {
                    await scaleService.updateConnectedDeviceWifiStatus(broadcastId: broadcastId, isConfigured: true)
// swiftlint:disable:next line_length
                    LoggerService.shared.log(level: .info, tag: tag, message: "Updated WiFi configuration status to true for broadcast ID: \(broadcastId)")
                }

                // Navigate back to root after success delay (immediate when Wi-Fi-only flow)
                let delay: TimeInterval = 2.0
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
                    if self.isWifiSetupOnly {
                        self.dismissAction?()
                    } else {
                        self.navigateToStep(.customizeSettings)
                    }
                }
// swiftlint:disable:next switch_case_alignment
                default:
                LoggerService.shared.log(level: .error, tag: tag, message: "WiFi connection failed: \(response)")
                self.connectionState = .failure
                // Extract error code from the error if available
                if let errorCode = response.errorCode {
                    self.errorCode = errorCode
                }
            }
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "WiFi connection failed: \(error.localizedDescription)")
            self.connectionState = .failure
            self.errorCode = nil
        }
        self.resetNetworkForm()
    }
    
    /// Checks device info and WiFi configuration after WiFi setup for scale SKU 0412
    func checkDeviceInfoAfterWifiSetup(scale: Device) async -> Bool {
        var isWifiConfigured = false
        let result = await bluetoothService.getDeviceInfo(for: scale, skipConnectionCheck: true)
        switch result {
        case .success(let deviceInfo):
            isWifiConfigured = deviceInfo.isWifiConfigured ?? false// Assuming this property exists in the DeviceInfo model
            LoggerService.shared.log(level: .info, tag: tag, message: "Device info after WiFi setup - WiFi configured: \(isWifiConfigured)")
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to get device info after WiFi setup: \(error)")
        }
        return isWifiConfigured
    }
    
    // MARK: - Device Discovery Handling
    func handleDeviceDiscovery(_ event: DeviceDiscoveryEvent) {
        // Only handle discovery during wake-up step
        guard currentStep == .wakeup else { return }
        // Only handle BtWifi scales
        guard event.deviceInfo.setupType == .btWifiR4 else { return }
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        stepTimerTask?.cancel()
        self.discoveryEvent = event
        self.discoveredScale = event.device
        
        // Check if this is a known scale (isNew = false means it's known)
        if !event.isNew {
            // Disconnect device to prevent rediscovery loop (scanning continues)
            if let broadcastId = event.device.broadcastIdString, !broadcastId.isEmpty {
                Task {
                    // Skip this device to prevent rediscovery loop
                    await bluetoothSetupManager.disconnectIfNeeded(
                        broadcastId: broadcastId,
                        bluetoothService: bluetoothService,
                        considerForSession: false
                    )
                }
            }
            showKnownScaleAlert()
        } else {
            // New scale discovered - move to next step
            moveToNextStep()
        }
    }
    
    /// Shows an alert when a known scale is discovered.
    func showKnownScaleAlert() {
        let alertStrings = AlertStrings.KnownScaleDiscoveredAlert.self
        let alert = AlertModel(
            title: alertStrings.title,
            message: alertStrings.message,
            buttons: [
                AlertButtonModel(title: alertStrings.exitButton, type: .primary) { [weak self] _ in
                    guard let self = self else { return }
                    // Perform proper cleanup before dismissing
                    self.performExitCleanup()
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Deletes duplicate users from the scale
    func deleteUsers() async {
        guard let scale = discoveredScale else {
            return
        }
        
        // Delete all users in the duplicate list — extract primitives to avoid @Model mutation (R9)
        guard let broadcastId = scale.broadcastIdString, !broadcastId.isEmpty else { return }
        for user in duplicateList {
            guard let userToken = user.token, !userToken.isEmpty else {
                continue
            }

            _ = await bluetoothService.deleteUserByToken(broadcastId: broadcastId, token: userToken, disconnect: false)
        }
        
        // Reset display name to first name
        duplicateUserName = firstName ?? "User"
        
        // Reset the form with the first name
        userNameForm.reset()
        if let firstName = self.firstName {
            userNameForm.setDisplayName(firstName)
        }
    }
    
    /// Starts observing the network form changes to update the next button state.
    func subscribeToNetworkForm() {
        // Cancel previous subscription to avoid redundant updates
        networkFormCancellable?.cancel()
        
        networkFormCancellable = networkForm.formDidChange
            .receive(on: DispatchQueue.main)
            .sink { [weak self] in
                self?.updateNextEnabled()
            }
    }
    
    /// Deletes a specific user from the scale
    func deleteUserFromScale(_ user: DeviceUser) async {
        guard let scale = discoveredScale else {
            LoggerService.shared.log(level: .error, tag: tag, message: "deleteUserFromScale - no discovered scale")
            return
        }

        // Extract primitives to avoid @Model mutation (R9)
        guard let broadcastId = scale.broadcastIdString, !broadcastId.isEmpty else {
            LoggerService.shared.log(level: .error, tag: tag, message: "deleteUserFromScale - no broadcastId")
            return
        }
        let userToken = user.token ?? ""
        let result = await bluetoothService.deleteUserByToken(broadcastId: broadcastId, token: userToken, disconnect: false)

        switch result {
        case .success:
            LoggerService.shared.log(level: .info, tag: tag, message: "deleteUserFromScale - deleted user: \(user.name)")
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "deleteUserFromScale - error deleting user: \(error.localizedDescription)")
        }
    }
    
    /// Restarts the connection process after deleting users
    func restartConnection() async {
        // Reset duplicate user flags
        self.userList = []
        self.currentUser = nil
        self.duplicateList = []
        self.duplicateUserLastActiveAt = nil
        
        // Reset the form
        self.userNameForm.reset()
        
        // Ensure we have a discovered scale and discovery event for re-pairing
        guard discoveredScale != nil, discoveryEvent != nil else {
            return
        }
        
        // Reset connection state to loading to trigger pairing
        connectionState = .loading
    }
    
    func getUserList() async {
        guard let scale = discoveredScale else {
            LoggerService.shared.log(level: .error, tag: tag, message: "getUserList - no discovered scale")
            return
        }
        
        let result = await bluetoothService.getScaleUserList(for: scale, skipConnectionCheck: true)
        switch result {
        case .success(let users):
            // Filter out the current scale token
            self.userList = users.filter { user in
                user.token != scale.token
            }
            LoggerService.shared.log(level: .info, tag: tag, message: "getUserList - retrieved \(self.userList.count) users")
            
            // Update form validation with new user list if we're on username customization
            if currentCustomizeSetting == .scaleUsername {
                await MainActor.run {
                    let scaleUsers = self.userList.map { deviceUser in
                        ScaleUser(name: deviceUser.name, token: deviceUser.token)
                    }
                    self.userNameForm.updateUserList(scaleUsers)
                    // Ensure current user name is set for duplicate check exclusion
                    if self.userNameForm.currentUserName == nil {
                        let currentName = self.initialDisplayNameSnapshot ?? self.firstName ?? "User"
                        self.userNameForm.setCurrentUserName(currentName)
                    }
                    // Reset form state to pristine/untouched after updating user list
                    // This ensures errors don't show until user interacts with the field
                    self.userNameForm.displayName.markAsPristine()
                    self.userNameForm.displayName.markAsUntouched()
                    // Trigger validation and update Next button state
                    self.updateNextEnabled()
                }
            }
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "getUserList - error getting scale users: \(error.localizedDescription)")
        }
    }
    
    /// Checks for duplicate users in the user list
    func checkDuplicateUserList() {
        self.currentUser = userList.first { user in
            user.name.lowercased() == (self.firstName?.lowercased() ?? "")
        }
        
        // Find all users with the same name as current user
        if let currentUser = self.currentUser {
            self.duplicateList = userList.filter { user in
                user.name == currentUser.name
            }
        }
        duplicateUserLastActiveAt = Int64(duplicateList.first?.lastActive ?? 0)
        LoggerService.shared.log(level: .info, tag: tag, message: "checkDuplicateUserList - found \(self.duplicateList.count) duplicate users")
    }
    
    /// Updates the customize settings on the scale
    func updateCustomizeSettings() async {
        guard let savedScale = savedScale else {
            LoggerService.shared.log(level: .error, tag: tag, message: "updateCustomizeSettings - no saved scale")
            await MainActor.run {
                self.scaleSetupError = .updateSettingsFailed
            }
            return
        }
        
        do {
            // Check which customize settings pages need to be saved
            // Dashboard metrics are independent and already saved to API, not included here
            let saveScaleMetrics = selectedCustomizeItems.contains(CustomizeSettingsItem.scaleMetrics.rawValue)
            let saveScaleMode = selectedCustomizeItems.contains(CustomizeSettingsItem.scaleModes.rawValue)
            let saveScaleUsername = selectedCustomizeItems.contains(CustomizeSettingsItem.userName.rawValue)
            
            // Get current preference or create default
            let currentPreference: R4ScalePreference = await {
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
            }()
            
            // Build updated preference object
            let updatedPreferenceDTO = R4ScalePreferenceDTO(
                scaleId: savedScale.id,
// swiftlint:disable:next line_length
                displayName: saveScaleUsername ? (userNameForm.displayName.value.isEmpty ? (firstName ?? "User") : userNameForm.displayName.value) : currentPreference.displayName,
// swiftlint:disable:next line_length
                displayMetrics: saveScaleMetrics ? selectedScaleMetrics : currentPreference.displayMetrics, // Only update if scale metrics were customized (independent from dashboard metrics)
                shouldFactoryReset: false,
                shouldMeasureImpedance: saveScaleMode ? (selectedScaleMode == .allBodyMetrics) : currentPreference.shouldMeasureImpedance,
                shouldMeasurePulse: saveScaleMode ? isHeartRateEnabled : currentPreference.shouldMeasurePulse,
                timeFormat: "12", // Default to 12-hour format
                tzOffset: DateTimeTools.getTimeZoneInMinutes(),
                wifiFotaScheduleTime: 0,
                updatedAt: DateTimeTools.getCurrentDatetimeIsoString(),
                isTemporary: true
            )
            
            let updatedPreference = R4ScalePreference(from: updatedPreferenceDTO, scaleId: savedScale.id)
            
            // Set up timeout task
            let timeoutTask = Task { [weak self] in
                guard let timeout = self?.timeoutConstants.updateSettingsTimeout else { return }
                
                let timeoutNs = UInt64(timeout)
                
                try? await Task.sleep(nanoseconds: timeoutNs)
                
                guard !Task.isCancelled else { return } // ← check cancellation
                
                await MainActor.run {
                    guard let self = self else { return }
                    if self.currentStep == .updateSettings {
                        self.scaleSetupError = .updateSettingsFailed
                        LoggerService.shared.log(level: .error, tag: self.tag, message: "updateCustomizeSettings - timeout occurred")
                    }
                }
            }
            
            try await scaleService.updateScalePreference(
                savedScale.id,
                updatedPreference
            )
            await scaleService.pushLocalChangesToServer()
            // Call bluetooth service to update account
            let result = await bluetoothService.updateAccount(on: savedScale, preference: updatedPreference)
            switch result {
            case .success:
                LoggerService.shared.log(level: .info, tag: tag, message: "updateCustomizeSettings - scale preference updated successfully")
            case .failure(let error):
// swiftlint:disable:next line_length
                LoggerService.shared.log(level: .error, tag: tag, message: "updateCustomizeSettings - failed to update scale preference: \(error.localizedDescription)")
            }
            
            timeoutTask.cancel()
            
            switch result {
            case .success:
                // Update the scale preference through the service layer to handle SwiftData relationships properly
                do {
                    try await scaleService.updateScalePreference(savedScale.id, updatedPreference)
                    
                    // Reset the changes flag after successful update
                    hasCustomizeChanges = false
                    hasSavedSettings = false
                    
// swiftlint:disable:next line_length
                    LoggerService.shared.log(level: .info, tag: tag, message: "updateCustomizeSettings - settings updated successfully: \(updatedPreference)")
                    // Clear the selected items since they're now saved
                    selectedCustomizeItems.removeAll()
                    scaleSetupError = .none
                    Task { @MainActor in
                            try? await Task.sleep(nanoseconds: 250_000_000)
                        self.navigateToStep(.stepOn)
                    }
                } catch {
// swiftlint:disable:next line_length
                    LoggerService.shared.log(level: .error, tag: tag, message: "updateCustomizeSettings - failed to update scale preference locally: \(error.localizedDescription)")
                    await MainActor.run {
                        self.scaleSetupError = .updateSettingsFailed
                    }
                }
            case .failure(let error):
// swiftlint:disable:next line_length
                LoggerService.shared.log(level: .error, tag: tag, message: "updateCustomizeSettings - failed to update account: \(error.localizedDescription)")
                await MainActor.run {
                    self.scaleSetupError = .updateSettingsFailed
                }
            }
        } catch {
// swiftlint:disable:next line_length
            LoggerService.shared.log(level: .error, tag: tag, message: "updateCustomizeSettings - failed to update settings: \(error.localizedDescription)")
            await MainActor.run {
                self.scaleSetupError = .updateSettingsFailed
            }
        }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            self.bluetoothService.syncDevices([])
        }
    }
    
    // MARK: - Helper Methods
    
    /// Sets up scale username form with initial values and async preference loading

}
// swiftlint:enable cyclomatic_complexity function_body_length
