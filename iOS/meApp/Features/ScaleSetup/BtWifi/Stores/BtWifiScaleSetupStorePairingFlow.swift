import Combine
import Foundation
import GGBluetoothSwiftPackage
import SwiftUI

// swiftlint:disable cyclomatic_complexity function_body_length

@MainActor
extension BtWifiScaleSetupStore {
    func handleStepChange() {
        // Don't perform step change actions if we're exiting
        guard !isExiting else {
            return
        }
        
        switch currentStep {
        case .intro:
            break
        case .permissions:
            break
        case .wakeup:
            self.pair()
        case .connectingBluetooth:
            self.connectionState = .loading
            Task {
                if discoveredScale != nil && discoveryEvent != nil {
                    await self.confirmPair()
                } else {
                    // Only set failure if we actually can't pair (missing device), not due to network
                    connectionState = .failure
                }
            }
        case .gatheringNetwork:
            // Don't fetch networks if we're showing a "No Networks Found" error
            if scaleSetupError == .noNetworkFound && !isRefreshingWifiNetworks {
                return
            }
            
            if scaleSetupError != .maxUserReached && scaleSetupError != .duplicatesFound {
                connectionState = .loading
                startNetworkScanTimeout()
            }
            
            // Skip fetch only if settings WiFi setup AND not refreshing
            let shouldSkipFetch = isSettingsWifiSetup && previousStep == .availableWifiList && !isRefreshingWifiNetworks
            
            if let savedScale = savedScale,
               scaleSetupError != .maxUserReached && scaleSetupError != .duplicatesFound,
               !shouldSkipFetch {
                // Cancel any existing fetch task
                fetchWifiNetworksTask?.cancel()
                fetchWifiNetworksTask = Task { [weak self] in
                    guard let self else { return }
                    // Reset refresh flag after starting the task
                    self.isRefreshingWifiNetworks = false
                    await self.fetchWifiNetworks(for: savedScale.broadcastIdString ?? "")
                }
            } else {
                // Reset refresh flag even if we skip fetch
                isRefreshingWifiNetworks = false
            }
        case .availableWifiList:
            if isSettingsWifiSetup {
                scaleSetupError = .none
                connectionState = .loading
            }
        case .wifiPassword:
            break
        case .connectingWifi:
            self.connectionState = .loading
            if scaleSetupError == .none {
                Task {
                    await self.setupWifi()
                }
            }
        case .viewSettings:
            // Handle view settings step change
            // Make sure we have user list populated for username settings
            if currentCustomizeSetting == .scaleUsername && userList.isEmpty {
                Task {
                    await self.getUserList()
                }
            }
        case .updateSettings:
            // Simulate updating settings and move to next step
            Task {
                await self.updateCustomizeSettings()
            }
        case .customizeSettings:
            break
        case .stepOn:
            // Set skipCheckNetwork to true when entering stepOn screen to prevent network errors
            HTTPClient.shared.skipCheckNetwork = true
            
            // Cancel any existing stepOn timeout
            stepOnTimeoutTask?.cancel()
            
            Task {
                guard let savedScale = self.savedScale else { return }
                // Subscribe to live measurement updates and proceed when weight > 0
                _ = await bluetoothService.startLiveMeasurement(broadcastId: savedScale.broadcastIdString ?? "")
                self.liveMeasurementSubscription = self.bluetoothService.liveMeasurementPublisher
                    .receive(on: DispatchQueue.main)
                    .sink { [weak self] (liveEntry: GGWeightEntry) in
                        guard let self else { return }
                        
                        // Don't navigate if we're exiting, especially from stepOn
                        guard !self.isExiting && !self.isExitingFromStepOn else { return }
                        
                        if liveEntry.displayWeight > 0 && savedScale.broadcastIdString == liveEntry.broadcastId {
                            Task {
                                _ = await self.bluetoothService.stopLiveMeasurement(broadcastId: savedScale.broadcastIdString ?? "")
                                self.cancelMeasurementSubscription()
                                self.cancelStepOnTimeout()
                                self.scaleSetupError = .none
                                self.moveToNextStep()
                            }
                        }
                    }
                
                // Auto-navigate from Step On screen after 3.5 minutes
                stepOnTimeoutTask = Task { [weak self] in
                    try? await Task.sleep(nanoseconds: 210 * 1_000_000_000)
                    guard let self, !Task.isCancelled, self.currentStep == .stepOn else { return }
                    
                    await MainActor.run {
                        // Don't navigate if we're exiting, especially from stepOn
                        guard !self.isExiting && !self.isExitingFromStepOn else { return }
                        // Auto-navigate only if Bluetooth is enabled and the scale is connected
                        let hasBluetoothPermissions = self.hasAllBtPermissions()
                        let isScaleConnected = self.savedScale?.isConnected == true

                        guard hasBluetoothPermissions && isScaleConnected else {
                            LoggerService.shared.log(
                                level: .info,
                                tag: self.tag,
                                message: "StepOn timeout: Skipping auto-navigation - Bluetooth disabled or scale not connected"
                            )
                            return
                        }                       
                        self.cancelStepOnTimeout()
                        self.scaleSetupError = .none
                        self.moveToNextStep()
                    }
                }
            }
        case .measurement:
            // Cancel stepOn timeout and reset skipCheckNetwork when moving to measurement screen
            cancelStepOnTimeout()
            // Cancel any existing measurement subscription and timeout
            cancelMeasurementSubscription()
            
            // Set up timeout for measurement collection
            measurementTimeoutTask = Task { [weak self] in
                guard let timeoutConstants = self?.timeoutConstants.bluetoothTimeoutNs else { return }
                try? await Task.sleep(nanoseconds: UInt64(timeoutConstants))
                guard !Task.isCancelled else { return }
                await MainActor.run {
                    guard let self else { return }
                    // If we're still on measurement step, handle timeout
                    // This includes cases where subscription was cancelled due to Bluetooth being off
                    if self.currentStep == .measurement {
                        self.cancelMeasurementSubscription()
                        self.scaleSetupError = .collectMeasurementFailed
                    }
                }
            }
            
            // Subscribe to new entry events (uses EntryNotification for safe cross-actor data passing)
            newEntrySubscription = bluetoothService.newEntryReceivedPublisher
                .receive(on: DispatchQueue.main)
                .sink { [weak self] _ in
                    guard let self else { return }
                    // Entry received - clear timeout and move to next step
                    self.cancelMeasurementSubscription()
                    self.scaleSetupError = .none
                    self.moveToNextStep()
                }
        case .scaleConnected:
            break
        }

        // Reset skipCheckNetwork for all other steps (not stepOn)
        if previousStep == .stepOn && currentStep != .stepOn {
            cancelStepOnTimeout()
        }
    }
    
    /// Sets connectionState while blocking network-related errors during Bluetooth pairing
    func setConnectionState(_ newState: ConnectionState, allowNetworkErrors: Bool = true) {
        guard currentStep != .connectingBluetooth ||
            (newState != .noNetworks && (allowNetworkErrors || newState != .failure)) else {
            return
        }

        connectionState = newState
    }
    
    /// Handles permission changes during the setup flow
    /// Matches Android behavior: only show WiFi errors when on WiFi-related steps AND scale is connected
    func handlePermissionChange() {
        // Don't handle permission changes if we're exiting
        guard !isExiting else { return }
        // Skip all network checks during Bluetooth pairing
        if currentStep == .connectingBluetooth {
            // Never show network-related errors while pairing
            if connectionState == .noNetworks {
                connectionState = .loading
            }
            return
        }
        
        let missingPermissions = !hasAllBtPermissions()
        let noNetwork = !networkMonitor.isConnected
        
        // Skip network checks for duplicate user screen (restore account flow)
        // Restore account is a Bluetooth-only operation and doesn't require WiFi/network
        if scaleSetupError == .duplicatesFound && currentStep == .gatheringNetwork {
            return
        }
        
        // For wakeup step, navigate to permissions if permissions or network are missing
        if (missingPermissions || noNetwork) && currentStep == .wakeup {
            resetDiscoveryState()
            navigateToStep(.permissions)
            return
        }
        
        // Resume setup after permissions recover.
        if !missingPermissions && !noNetwork && currentStep == .permissions && savedScale != nil {
            if let resumeStep = stepToResumeAfterPermissions {
                stepToResumeAfterPermissions = nil
                scaleSetupError = .none
                connectionState = .loading
                navigateToStep(resumeStep)
            } else {
                isRefreshingWifiNetworks = true
                navigateToStep(.gatheringNetwork)
            }
            return
        }

        // Restore stepOn subscriptions after Bluetooth returns.
        if !missingPermissions && currentStep == .stepOn && liveMeasurementSubscription == nil && savedScale != nil {
            navigateToStep(.stepOn)
            return
        }

        // Reconnect before retrying settings update.
        if !missingPermissions && currentStep == .updateSettings && savedScale != nil {
            scaleSetupError = .none
            connectionState = .loading
            // Let the BLE SDK rediscover the scale.
            bluetoothService.resumeSmartScan(clearOnlyPairing: false)

            Task { [weak self] in
                guard let self = self, let savedScale = self.savedScale else { return }

                // Wait up to 10 seconds for reconnect.
                for _ in 1...10 {
                    try? await Task.sleep(nanoseconds: 1_000_000_000) // 1 second
                    guard self.currentStep == .updateSettings else {
                        return
                    }
                    do {
                        try await self.scaleService.updateAllScalesStatus(nil)
                        if let refreshed = try await self.scaleService.getDevice(by: savedScale.id),
                           refreshed.isConnected == true {
                            await MainActor.run {
                                self.savedScale = refreshed
                                self.bluetoothService.syncDevices([refreshed])
                            }
                            break
                        }
                    } catch {
                        continue
                    }
                }

                // Retry even if reconnect status is stale.
                await self.updateCustomizeSettings()
            }
            return
        }

        guard missingPermissions || noNetwork else { return }

        // Only handle errors for steps that have been reached
        switch currentStep {
        case .intro:
            break
        case .gatheringNetwork:
            // Skip network checks when showing duplicate user screen (restore account flow)
            // Restore account is a Bluetooth-only operation and doesn't require WiFi/network
            if scaleSetupError == .duplicatesFound {
                return
            }
            if savedScale != nil {
                cancelNetworkScanTimeout()
                connectionState = .noNetworks
                scaleSetupError = .noNetworkFound
                // Cancel any ongoing WiFi network fetch to prevent navigation back to WiFi list
                fetchWifiNetworksTask?.cancel()
            } else {
                resetDiscoveryState()
                navigateToStep(.permissions)
            }
        case .availableWifiList:
            if noNetwork {
                // Navigate back to gathering network screen to show "No Networks Found" error
                setConnectionState(.noNetworks, allowNetworkErrors: false)
                scaleSetupError = .noNetworkFound
                navigateToStep(.gatheringNetwork)
            }
        case .wifiPassword, .connectingWifi:
            // If Wi-Fi setup has already succeeded, don't navigate back to Wi-Fi list
            // The scheduled navigation will proceed to the next step
            if currentStep == .connectingWifi && connectionState == .success {
                return
            }
            if savedScale != nil {
                scaleSetupError = .wifiConnectionFailed
                if currentStep != .availableWifiList {
                    navigateToStep(.availableWifiList)
                }
            } else {
                resetDiscoveryState()
                navigateToStep(.permissions)
            }
        case .stepOn where scaleSetupError != .updateSettingsFailed:
            // Skip network checks during stepOn screen - network access is not required for collecting measurements
            let bluetoothSwitchOff =
                permissionsService.getPermissionState(.BLUETOOTH_SWITCH) != .ENABLED

            if bluetoothSwitchOff || missingPermissions {
                cancelMeasurementSubscription()
                cancelStepOnTimeout()
                showBluetoothTurnedOffAlert()
            }
        case .measurement:
            let bluetoothSwitchOff =
                permissionsService.getPermissionState(.BLUETOOTH_SWITCH) != .ENABLED

            if bluetoothSwitchOff {
                newEntrySubscription?.cancel()
                newEntrySubscription = nil
                liveMeasurementSubscription?.cancel()
                liveMeasurementSubscription = nil

                // Keep timeout running so error screen appears
                showBluetoothTurnedOffAlert()
            }
        case .updateSettings:
            // BT-on is handled above the guard.
            break
            
        default:
            // For other steps, don't automatically navigate
            break
        }
    }
    
    // MARK: - Discovery State Management
    
    /// Clears any active Bluetooth discovery subscriptions and timers and resets related state.
    func resetDiscoveryState() {
        // Cancel active Combine subscription before releasing it.
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        
        // Nil out discovery data so subsequent runs start fresh.
        discoveredScale = nil
        discoveryEvent = nil
        
        // Cancel any in-flight timeout task.
        stepTimerTask?.cancel()
        
        // Reset error state
        scaleSetupError = .none
    }
    
    // MARK: - Scale Pairing
    /// Confirms the pairing with the discovered scale.
    func confirmPair() async {
        guard let scale = discoveredScale, discoveryEvent != nil else {
            LoggerService.shared.log(level: .error, tag: tag, message: "confirmPair - missing discovery event or scale")
            connectionState = .failure
            return
        }
        
        // Fetch scale token if not already cached
        await fetchWifiScaleToken()
        
        guard let scaleToken = self.scaleToken else {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to obtain scale token")
            connectionState = .failure
            return
        }
        // Use cached display name, or duplicateUserName if handling duplicate user
        let displayName = !duplicateUserName.isEmpty ? duplicateUserName : (self.firstName ?? "User")
        // Call confirmSmartPair
        let pairResult = await bluetoothService.confirmSmartPair(
            device: scale,
            token: scaleToken,
            displayName: displayName,
            userNumber: nil
        )
        switch pairResult {
        case .success(let response):
            switch response {
            case .creationCompleted:
                LoggerService.shared.log(level: .info, tag: tag, message: "Creation Completed \(response)")
                await saveScale()
                connectionState = .success
                scaleSetupError = .none
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 2_000_000_000)
                    self.navigateToStep(.gatheringNetwork)
                }
            case .duplicateUserError:
                LoggerService.shared.log(level: .error, tag: tag, message: "Duplicate User Error \(response)")
                // Get user list from scale and check for duplicates
                await getUserList()
                checkDuplicateUserList()

                // Populate userNameForm with current user name and user list for validation
                if let firstName = self.firstName {
                    userNameForm.setDisplayName(firstName)
                    // Don't set currentUserName here - we want to show that this name IS a duplicate
                    userNameForm.setCurrentUserName(nil)
                }

                // Convert DeviceUser list to ScaleUser list for form validation
                let scaleUsers = userList.map { deviceUser in
                    ScaleUser(name: deviceUser.name, token: deviceUser.token)
                }
                userNameForm.updateUserList(scaleUsers)
                userNameForm.displayName.markAsPristine()
                userNameForm.displayName.markAsUntouched()
                // Set error state and navigate to gathering network
                scaleSetupError = .duplicatesFound
                navigateToStep(.gatheringNetwork)
            case .memoryFull:
                LoggerService.shared.log(level: .error, tag: tag, message: "Memory Full \(response)")
                await getUserList()
                // Set error state and navigate to gathering network
                scaleSetupError = .maxUserReached
                navigateToStep(.gatheringNetwork)
            case .inputDataError:
                LoggerService.shared.log(level: .error, tag: tag, message: "Input data error: \(response)")
                connectionState = .failure
            default:
                connectionState = .failure
                LoggerService.shared.log(level: .error, tag: tag, message: "Unexpected pairing response: \(response)")
            }
        case .failure(let error):
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to pair scale: \(error.localizedDescription)")
            connectionState = .failure
        }
    }
    
    /// Saves the discovered scale to persistent storage.
    func saveScale() async {
        guard let discoveryEvent = discoveryEvent,
              let scale = discoveredScale,
              let scaleToken = self.scaleToken else {
            LoggerService.shared.log(level: .error, tag: tag, message: "saveScale - missing required data")
            return
        }
        
        do {
            let isWifiConfigured = await checkDeviceInfoAfterWifiSetup(scale: scale)
            
            // Create unique scale ID using timestamp
            let scaleID = String(DateTimeTools.getCurrentTimestampMillis())
            let displayName = !duplicateUserName.isEmpty ? duplicateUserName : (self.firstName ?? "User")
            let accountId = accountService.activeAccount?.accountId ?? ""
            
            // Get device metadata for R4 scales
            var deviceMetadata: DeviceMetaData?
            let deviceInfoResult = await bluetoothService.getDeviceInfo(broadcastId: scale.broadcastIdString ?? "", skipConnectionCheck: true)
            switch deviceInfoResult {
            case .success(let deviceInfo):
                let dto = ScaleMetaDataDTO(
                    firmwareRevision: deviceInfo.firmwareRevision?.replacingOccurrences(of: "\0", with: ""),
                    hardwareRevision: deviceInfo.hardwareRevision?.replacingOccurrences(of: "\0", with: ""),
                    latestFirmwareVersion: nil,
                    manufacturerName: deviceInfo.manufacturerName?.replacingOccurrences(of: "\0", with: ""),
                    modelNumber: deviceInfo.modelNumber?.replacingOccurrences(of: "\0", with: ""),
                    serialNumber: deviceInfo.serialNumber?.replacingOccurrences(of: "\0", with: ""),
                    softwareRevision: deviceInfo.softwareRevision?.replacingOccurrences(of: "\0", with: ""),
                    systemId: deviceInfo.systemID?.replacingOccurrences(of: "\0", with: ""),
                    wifiMac: ""
                )
                deviceMetadata = DeviceMetaData(from: dto)
                LoggerService.shared.log(level: .info, tag: tag, message: "Retrieved device metadata for R4 scale")
            case .failure(let error):
                LoggerService.shared.log(level: .error, tag: tag, message: "Failed to get device info: \(error.localizedDescription)")
            }

            // Get WiFi MAC address for R4 scales
            var wifiMacAddress: String? = scale.wifiMac
            let wifiMacResult = await bluetoothService.getWifiMacAddress(broadcastId: scale.broadcastIdString ?? "")
            switch wifiMacResult {
            case .success(let macAddress):
                wifiMacAddress = macAddress
                LoggerService.shared.log(level: .info, tag: tag, message: "Retrieved WiFi MAC address: \(macAddress)")
            case .failure(let error):
                LoggerService.shared.log(level: .error, tag: tag, message: "Failed to get WiFi MAC address: \(error.localizedDescription)")
            }
            
            let isDashboardFour = isDashboardTypeFour
            
            let savedScale = try await scaleService.createR4Scale(
                scaleId: scaleID,
                accountId: accountId,
                displayName: displayName,
                token: scaleToken,
                mac: scale.mac,
                broadcastIdString: scale.broadcastIdString,
                broadcastId: scale.broadcastId,
                sku: scaleItem?.sku ?? discoveryEvent.device.sku,
                deviceName: discoveryEvent.deviceInfo.productName,
                wifiMac: wifiMacAddress,
                deviceMetadata: deviceMetadata,
                isWifiConfigured: isWifiConfigured,
                isConnected: true,
                skipDuplicateCheck: isReconnect
            )
            
            self.savedScale = savedScale.toSnapshot(isConnected: true, isWifiConfigured: isWifiConfigured)
            await self.scaleService.syncAllScalesWithRemote()
            
            // Ensure connection status is updated after sync completes
            // This prevents UI flicker when navigating back to MyScalesScreen
            do {
                try await scaleService.updateAllScalesStatus(nil)
            } catch {
                LoggerService.shared.log(level: .error, tag: tag, message: "Failed to update scales status after save: \(error.localizedDescription)")
            }
            
            Task {
                await self.pushNotificationService.setupPushNotifications(isFromScaleSetup: true)
            }
            
            LoggerService.shared.log(level: .info, tag: tag, message: "Scale saved successfully: \(savedScale.id)")
            NotificationCenter.default.post(name: .scaleAddedOrUpdated, object: nil)
            
            if isDashboardFour {
                await upgradeDashboardTypeFrom4To12WithDefaults()
            }
            
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Error saving scale: \(error.localizedDescription)")
            connectionState = .failure
        }
    }
    
    /// Fetches the WiFi scale token for setup operations.
    /// This demonstrates how to use the WiFi scale service from other services.
    func fetchWifiScaleToken() async {
        if scaleToken != nil {
            return
        }
        
        do {
            let scaleTokenResponse = try await wifiScaleService.getScaleToken(request: "4")
            self.scaleToken = scaleTokenResponse.token
            LoggerService.shared.log(level: .info, tag: tag, message: "Successfully fetched WiFi scale token")
        } catch {
            LoggerService.shared.log(level: .error, tag: tag, message: "Failed to fetch WiFi scale token: \(error.localizedDescription)")
            connectionState = .failure
        }
    }
    
    /// Starts pairing: scanning for devices when entering the wake-up step.
    func pair() {
        resetDiscoveryState()
        Task { bluetoothService.scanForPairing() }

        if deviceDiscoveryCancellable == nil {
            deviceDiscoveryCancellable = bluetoothService.deviceDiscoveredPublisher
                .receive(on: DispatchQueue.main)
                .sink { [weak self] discoveryEvent in
                    self?.handleDeviceDiscovery(discoveryEvent)
                }
        }

        stepTimerTask = Task { [weak self] in
            guard let timeoutConstants = self?.timeoutConstants.bluetoothTimeoutNs else { return }
            try? await Task.sleep(nanoseconds: UInt64(timeoutConstants))
            guard !Task.isCancelled else { return }
            await MainActor.run {
                guard let self else { return }
                if self.discoveredScale == nil && self.currentStep == .wakeup {
                    self.navigateToStep(.connectingBluetooth)
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: 250_000_000)
                        self.connectionState = .failure
                    }
                }
            }
        }
    }

    // MARK: - Device Discovery Handling
    func handleDeviceDiscovery(_ event: DeviceDiscoveryEvent) {
        guard currentStep == .wakeup else { return }
        guard event.deviceInfo.setupType == .btWifiR4 else { return }
        deviceDiscoveryCancellable?.cancel()
        deviceDiscoveryCancellable = nil
        stepTimerTask?.cancel()
        self.discoveryEvent = event
        self.discoveredScale = event.device.toDevice()

        if !event.isNew {
            if let broadcastId = event.device.broadcastIdString, !broadcastId.isEmpty {
                Task {
                    await bluetoothSetupManager.disconnectIfNeeded(
                        broadcastId: broadcastId,
                        bluetoothService: bluetoothService,
                        considerForSession: false
                    )
                }
            }
            showKnownScaleAlert()
        } else {
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
                    self.performExitCleanup()
                }
            ]
        )
        notificationService.showAlert(alert)
    }
}
// swiftlint:enable cyclomatic_complexity function_body_length
