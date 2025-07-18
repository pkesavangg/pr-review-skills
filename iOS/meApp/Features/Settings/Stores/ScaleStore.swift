//
//  ScaleStore.swift
//  meApp
//
//  Created by Lakshmi Priya on 23/06/25.
//

import Foundation
import SwiftUI
import Combine

// MARK: - Scales Store
/// A store to manage scale settings and actions, including details for a selected scale.
@MainActor
class ScaleStore: ObservableObject {
    
    // List State
    @Published var scales: [Device] = []
    @Published var isLoading: Bool = false
    @Published var errorMessage: String? = nil
    @Published var addScaleForm = AddScaleForm()
    
    // Selected Scale State
    @Published var scale: Device? = nil
    @Published var firmwareVersion: String? = nil
    @Published var macAddress: String? = nil
    @Published var connectedWifiSSID: String? = nil
    @Published var deviceInfo: DeviceMetaData? = nil
    @Published var isBluetoothScale: Bool = false
    @Published var isDeviceConnected: Bool = false
    @Published var showErrorToast: ToastModel? = nil
    @Published var showNicknameAlert: Bool = false
    @Published var nicknameInput: String = ""
    
    // In-App Browser State
    @Published var showTermsBrowser: Bool = false
    @Published var browserURL: URL? = nil
    
    // Settings/detail values for UI (replace with computed or fetched values later)
    @Published var modeValue: ScaleModes = .weightOnly
    @Published var displayMetricsValue: String = "" // TODO: Replace with actual display metrics
    @Published var usersValue: String = "Kristin" // TODO: Replace with actual users
    @Published var wifiValue: String = "greatergoods1" // TODO: Replace with actual Wi-Fi SSID
    @Published var wifiMacAddressValue: String = "" // TODO: Replace with actual Wi-Fi MAC address
    @Published var scaleTypeValue: String = "Bluetooth/Wi-Fi" // TODO: Replace with actual scale type
    @Published var skuValue: String = "0412" // TODO: Replace with actual SKU
    @Published var datePairedValue: String = "12/25/2024" // TODO: Replace with actual date paired
    
    // Scale Mode State Management
    @Published var originalModeValue: ScaleModes = .weightOnly
    @Published var isHeartRateEnabled: Bool = false
    @Published var originalHeartRateEnabled: Bool = false
    @Published var hasModeChanges: Bool = false
    
    // Computed property for bluetooth connection status
    var bluetoothValue: String {
        guard let scale = scale else { return ScaleBluetoothStrings.notConnected }
        return scale.isConnected == true ? ScaleBluetoothStrings.connected : ScaleBluetoothStrings.notConnected
    }
    
    // Display Metrics State
    @Published var progressMetrics: [ScaleMetricSetting] = ScaleMetrics.progressMetrics
    
    
    // Banner States
    @Published var showWeightOnlyBanner: Bool = false
    @Published var showWeightOnlyInfo: Bool = false
    @Published var showHeartRateBanner: Bool = false
    
    // Metrics State
    @Published var metrics: [ScaleMetricSetting] = ScaleMetrics.bodyMetrics
    
    
    // User Management State
    @Published var currentUser: String = "Kristin" // TODO: Replace with actual user
    @Published var otherUsers: [String] = Array(repeating: "User Name", count: 8) // TODO: Replace with actual user
    
    // MARK: - User Management Computed Properties
    /// Converts the current user to a DeviceUser object
    var currentDeviceUser: DeviceUser {
        DeviceUser(
            name: currentUser,
            token: "current-user-token", // TODO: Replace with actual token
            lastActive: Int(Date().timeIntervalSince1970), // Current time for active user
            isBodyMetricsEnabled: true // TODO: Replace with actual setting
        )
    }
    
    /// Converts other users to DeviceUser objects
    var otherDeviceUsers: [DeviceUser] {
        otherUsers.enumerated().map { index, userName in
            DeviceUser(
                name: userName,
                token: "user-token-\(index)", // TODO: Replace with actual token
                lastActive: Int(Date().timeIntervalSince1970) - (index + 1) * 86400, // Simulate different last active times
                isBodyMetricsEnabled: true // TODO: Replace with actual setting
            )
        }
    }
    
    /// All users combined as DeviceUser objects
    var allDeviceUsers: [DeviceUser] {
        [currentDeviceUser] + otherDeviceUsers
    }
    @Published var isWifiLoading = false
    @Published var showPassword: Bool = false
    @Published var wifiPasswordValidationForm = WifiPasswordValidationForm()
    @Published var wifiConnectionState: ConnectionState = .loading
    @Published var connectedWifiNetwork: String? = nil
    @Published var wifiNetworks: [String] = ["greatergoods1", "great2542", "ggtesting"] // TODO: replace with actual wifi Networks
    var isFormValid: Bool { wifiPasswordValidationForm.isValid }
    private var cancellables = Set<AnyCancellable>()
    private let legalURLs = AppConstants.LegalURLs.self
    
    // MARK: - In-App Browser Presentation Binding
    var isBrowserPresented: Binding<Bool> {
        Binding(
            get: { self.showTermsBrowser },
            set: { newValue in
                if !newValue {
                    self.showTermsBrowser = false
                    self.browserURL = nil
                }
            }
        )
    }
    var presentingBrowserURL: URL {
        browserURL ?? legalURLs.greaterGoodsWebsite
    }
    
    // MARK: - Initialization
    @Injector var scaleService: ScaleService
    @Injector var notificationService: NotificationHelperService
    @Injector var bluetoothService: BluetoothService
    private let alertLang = AlertStrings.self
    private let loaderLang = LoaderStrings.self
    private let logger = LoggerService.shared
    
    init() {
        wireForm()
        fetchScales()
        subscribeToScaleUpdates()
    }
    
    var passwordError: String? { wifiPasswordValidationForm.getError(for: wifiPasswordValidationForm.password) }
    
    func setPasswordTouched() {
        wifiPasswordValidationForm.password.markAsDirty()
        objectWillChange.send()
    }
    
    private func wireForm() {
        addScaleForm.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
    }
    
    func resetForm() {
        // Reset form state
        addScaleForm = AddScaleForm()
        
        // Re-wire form and re-subscribe to scale updates so we don’t lose the publisher after a
        // view disappearance/appearance cycle.
        wireForm()
        subscribeToScaleUpdates()
    }
    
    // MARK: - List & CRUD
    func fetchScales() {
        Task {
            do {
                let devices = try await scaleService.getDevices()
                await MainActor.run {
                    self.scales = devices
                }
            } catch {
                await MainActor.run {
                    self.scales = []
                }
            }
        }
    }
    
    func getError() -> String? {
        addScaleForm.getError(for: .modelNumber)
    }
    
    // MARK: - Scale Detail Actions
    func loadScale(_ scale: Device) async {
        await MainActor.run {
            self.scale = scale
            self.isBluetoothScale = scale.deviceType == "bluetooth"
            self.isDeviceConnected = scale.isConnected ?? false
            
            // Update scale type value based on scale details
            self.scaleTypeValue = determineScaleType(for: scale)
            self.skuValue = scale.sku ?? ""
            
            // Load scale mode preferences
            self.loadScaleModePreferences()
            
            // Trigger UI update for computed properties
            self.objectWillChange.send()
        }
        
        await getDeviceInfo()
        await getConnectedWifiSSID()
        await refreshConnectionStatus()
        await fetchWifiMacAddress()
    }
    
    /// Refreshes the connection status for the current scale
    private func refreshConnectionStatus() async {
        guard let scale = scale else { return }
        
        do {
            // Get the latest device data from the service
            let devices = try await scaleService.getDevices()
            if let updatedScale = devices.first(where: { $0.id == scale.id }) {
                await MainActor.run {
                    self.scale = updatedScale
                    self.isDeviceConnected = updatedScale.isConnected ?? false
                    // Trigger UI update for computed properties
                    self.objectWillChange.send()
                }
            }
        } catch {
            logger.log(level: .error, tag: "ScaleStore", message: "Failed to refresh connection status: \(error.localizedDescription)")
        }
    }
    
    /// Determines the scale type based on the scale's SKU and other properties
    private func determineScaleType(for scale: Device) -> String {
        return ScaleTypeHelper.determineScaleTypeString(for: scale)
    }
    
    /// Determines the connection status for a scale based on its type and connection state
    func determineConnectionStatus(for scale: Device) -> ScaleConnectionStatus {
        let scaleType = ScaleTypeHelper.determineScaleType(for: scale)
        
        // AppSync scales don't show connection status
        if scaleType == .appsync {
            return .noStatus
        }
        
        // For other scale types, show connection status based on isConnected
        return scale.isConnected == true ? .connected : .notConnected
    }
    
    func getDeviceInfo() async {
        guard let scale = scale else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            if let device = try await scaleService.getDevices().first(where: { $0.id == scale.id }) {
                await MainActor.run {
                    self.deviceInfo = device.metaData
                    self.macAddress = device.mac
                    self.firmwareVersion = device.metaData?.firmwareRevision
                }
            }
        } catch {
            await MainActor.run {
                errorMessage = error.localizedDescription
            }
        }
    }
    
    func getConnectedWifiSSID() async {
        connectedWifiSSID = scale?.wifiMac
    }
    
    func changeNickname() {
        showNicknameAlert = true
        nicknameInput = scale?.nickname ?? ""
    }
    
    func saveNickname() async {
        guard let scale = scale else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            let properties: [String: Any] = ["nickname": nicknameInput]
            _ = try await scaleService.editDevice(scale.id, properties: properties)
            notificationService.showToast(ToastModel(title: "Saved", message: "Nickname updated"))
            self.scale?.nickname = nicknameInput
        } catch {
            errorMessage = error.localizedDescription
        }
        showNicknameAlert = false
    }
    
    /// Saves the scale name (nickname) for the current scale
    func saveScaleName(_ newName: String) async {
        guard let scale = scale else {
            await MainActor.run { self.errorMessage = ToastStrings.somethingWentWrong }
            return
        }
        
        // Show loader with 'Saving...' text
        await MainActor.run {
            notificationService.showLoader(LoaderModel(text: LoaderStrings.saving))
            errorMessage = nil
        }
        
        do {
            let properties: [String: Any] = ["nickname": newName]
            _ = try await scaleService.editDevice(scale.id, properties: properties)
            
            await MainActor.run {
                // Update the current scale's nickname
                self.scale?.nickname = newName
                self.scale?.deviceName = newName
                // Show success toast
                notificationService.showToast(ToastModel(title: ToastStrings.success, message: "Scale name updated"))
            }
            // Refresh the scales list to update UI in other screens
            await self.refreshScalesList()
        } catch {
            await MainActor.run {
                self.errorMessage = error.localizedDescription
            }
        }
        // Dismiss loader
        await MainActor.run {
            notificationService.dismissLoader()
        }
    }
    
    /// Refreshes the scales list to update UI across all screens
    private func refreshScalesList() async {
        do {
            let devices = try await scaleService.getDevices()
            await MainActor.run {
                self.scales = devices
                
                // Update the current scale if it exists in the refreshed list
                if let currentScale = self.scale,
                   let updatedScale = devices.first(where: { $0.id == currentScale.id }) {
                    self.scale = updatedScale
                }
            }
        } catch {
            await MainActor.run {
                self.errorMessage = error.localizedDescription
            }
        }
    }
    
    func deleteScale(scaleId: String, onSuccess: @escaping () -> Void) async {
        notificationService.showLoader(LoaderModel(text: loaderLang.deletingScale))
        do {
            try await scaleService.deleteDevice(scaleId, showToast: true)
            await scaleService.syncAllScalesWithRemote()
            await MainActor.run {
                notificationService.showToast(ToastModel(title: "Deleted", message: "Scale deleted"))
                if self.scale?.id == scaleId {
                    self.scale = nil
                }
                onSuccess()
            }
        } catch {
            await MainActor.run {
                errorMessage = error.localizedDescription
            }
        }
        notificationService.dismissLoader()
    }
    
    func handleScaleDelete(scaleId: String, onSuccess: @escaping () -> Void) {
        let alert = AlertModel(
            title: alertLang.DeleteScaleAlert.title,
            message: alertLang.DeleteScaleAlert.message,
            buttons: [
                AlertButtonModel(title: alertLang.DeleteScaleAlert.deleteButton, type: .primary) { _ in
                    Task {
                        await self.deleteScale(scaleId: scaleId, onSuccess: onSuccess)
                    }
                },
                AlertButtonModel(title: alertLang.DeleteScaleAlert.cancelButton, type: .secondary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    func handleWifiCredentialsExit(onExit: @escaping () -> Void) {
        let alert = AlertModel(
            title: alertLang.ConnectWifiNetwork.title,
            message: alertLang.ConnectWifiNetwork.message,
            buttons: [
                AlertButtonModel(title: alertLang.ConnectWifiNetwork.goBackButton, type: .secondary) { _ in
                    // Do nothing, just dismiss alert
                },
                AlertButtonModel(title: alertLang.ConnectWifiNetwork.exitButton, type: .primary) { _ in
                    onExit()
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    func connectToWifiNetwork(wifiName: String) {
        wifiConnectionState = .loading
        // Simulate async connection (replace with your real logic)
        DispatchQueue.global().asyncAfter(deadline: .now() + 2.0) {
            // Simulate success or failure randomly
            let didSucceed = Bool.random()
            DispatchQueue.main.async {
                // Add a slight delay for loader polish
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                    self.wifiConnectionState = didSucceed ? .success : .failure
                }
            }
        }
    }
    
    func scaleTypeTapped() {
        // TODO: Implement scaleTypeTapped action
    }
    
    /// Saves the scale mode preferences
    func handleScaleModeSave() {
        guard let scale = scale else {
            logger.log(level: .error, tag: "ScaleStore", message: "No scale available for saving preferences")
            return
        }
        
        Task {
            do {
                // Create R4ScalePreference with current settings
                let shouldMeasureImpedance = modeValue == .allBodyMetrics
                let shouldMeasurePulse = isHeartRateEnabled && shouldMeasureImpedance
                
                logger.log(level: .debug, tag: "ScaleStore", message: "Saving preferences - mode: \(modeValue.rawValue), shouldMeasureImpedance: \(shouldMeasureImpedance), shouldMeasurePulse: \(shouldMeasurePulse)")
                
                // Get existing display metrics from current preference or use defaults
                var existingDisplayMetrics = scale.r4ScalePreference?.displayMetrics ?? ScaleMetrics.defaultMetricsKeys
                
                // Ensure we have valid display metrics - fallback to defaults if empty
                if existingDisplayMetrics.isEmpty {
                    existingDisplayMetrics = ScaleMetrics.defaultMetricsKeys
                    logger.log(level: .debug, tag: "ScaleStore", message: "Display metrics was empty, using defaults: \(existingDisplayMetrics)")
                }
                
                // Ensure we have a valid scale ID
                let scaleId = scale.id
                guard !scaleId.isEmpty else {
                    logger.log(level: .error, tag: "ScaleStore", message: "Invalid scale ID")
                    return
                }
                
                // Ensure we have valid values for all required properties
                var displayName = scale.nickname ?? scale.deviceName ?? "Unknown Device"
                
                // Ensure display name is not empty
                if displayName.isEmpty {
                    displayName = "Unknown Device"
                    logger.log(level: .debug, tag: "ScaleStore", message: "Display name was empty, using fallback: \(displayName)")
                }
                let timeFormat = "12"
                let tzOffset = DateTimeTools.getTimeZoneInMinutes()
                let wifiFotaScheduleTime = Int(Date().timeIntervalSince1970)
                let updatedAt = DateTimeTools.getCurrentDatetimeIsoString()
                
                logger.log(level: .debug, tag: "ScaleStore", message: "Creating preference with - scaleId: \(scaleId), displayName: \(displayName), displayMetrics count: \(existingDisplayMetrics.count), timeFormat: \(timeFormat)")
                
                // Validate required properties before creating the preference
                guard !displayName.isEmpty,
                      !existingDisplayMetrics.isEmpty,
                      !timeFormat.isEmpty else {
                    logger.log(level: .error, tag: "ScaleStore", message: "Invalid preference data - missing required properties")
                    return
                }
                
                // Create the preference directly with all required properties
                let preference = R4ScalePreference(
                    scaleId: scaleId,
                    displayName: displayName,
                    displayMetrics: existingDisplayMetrics,
                    shouldFactoryReset: false,
                    shouldMeasureImpedance: shouldMeasureImpedance,
                    shouldMeasurePulse: shouldMeasurePulse,
                    timeFormat: timeFormat,
                    tzOffset: tzOffset,
                    wifiFotaScheduleTime: wifiFotaScheduleTime,
                    updatedAt: updatedAt
                )
                
                // Validate that all required properties are set with detailed logging
                let idValid = !preference.id.isEmpty
                let displayNameValid = !preference.displayName.isEmpty
                let displayMetricsValid = !preference.displayMetrics.isEmpty
                let timeFormatValid = !preference.timeFormat.isEmpty
                
                logger.log(level: .debug, tag: "ScaleStore", message: "Preference validation - id: \(idValid) (\(preference.id)), displayName: \(displayNameValid) (\(preference.displayName)), displayMetrics: \(displayMetricsValid) (\(preference.displayMetrics.count) items), timeFormat: \(timeFormatValid) (\(preference.timeFormat))")
                
                guard idValid && displayNameValid && displayMetricsValid && timeFormatValid else {
                    logger.log(level: .error, tag: "ScaleStore", message: "Invalid preference data - missing required properties")
                    return
                }
                
                // Update scale preferences via ScaleService
                try await scaleService.updateScalePreference(scaleId, preference)
                
                // Update the scale's R4ScalePreference with the new values
                await MainActor.run {
                    // Update the scale's R4ScalePreference
                    self.scale?.r4ScalePreference = preference
                    
                    logger.log(level: .debug, tag: "ScaleStore", message: "Updated scale R4ScalePreference - shouldMeasureImpedance: \(preference.shouldMeasureImpedance), shouldMeasurePulse: \(preference.shouldMeasurePulse)")
                    
                    // Reload preferences to update UI with new values
                    self.loadScaleModePreferences()
                }
                
                // Force refresh device data to ensure UI is up to date
                await forceRefreshDeviceData()
                
                logger.log(level: .info, tag: "ScaleStore", message: "Scale mode preferences saved successfully")
                
                // Show success toast
                await MainActor.run {
                    notificationService.showToast(ToastModel(
                        title: "Success",
                        message: ScaleModesStrings.preferencesSaved
                    ))
                }
                
            } catch {
                logger.log(level: .error, tag: "ScaleStore", message: "Failed to save scale mode preferences: \(error.localizedDescription)")
                
                // Show error toast
                await MainActor.run {
                    notificationService.showToast(ToastModel(
                        title: "Error",
                        message: ScaleModesStrings.preferencesFailed
                    ))
                }
            }
        }
    }
    
    func handleHelp() {
        // TODO: Implement help button action
    }
    func saveUsers() {
        // TODO: Implement save users logic
    }
    
    func deleteCurrentUser() {
        // TODO: Implement delete current user logic
        // For now, just clear the current user
        currentUser = ""
    }
    
    func deleteOtherUser(at index: Int) {
        // TODO: Implement delete other user logic
        guard index < otherUsers.count else { return }
        otherUsers.remove(at: index)
    }
    
    /// Deletes a user from the combined users array
    /// - Parameter index: The index in the combined users array (0 = current user, 1+ = other users)
    func deleteUser(at index: Int) {
        if index == 0 {
            // Delete current user
            deleteCurrentUser()
        } else {
            // Delete other user (adjust index by -1 since index 0 is current user)
            let otherUserIndex = index - 1
            deleteOtherUser(at: otherUserIndex)
        }
    }
    
    // MARK: - Product Guide URL helper & Browser Presentation
    func productGuideURL(for sku: String) -> URL {
        guard !sku.isEmpty else { return legalURLs.notFound }
        return (AppConstants.LegalURLs.serviceBase.appendingPathComponent(sku)) // Use type-safe base
    }
    func openProductGuide(for sku: String) {
        browserURL = productGuideURL(for: sku)
        showTermsBrowser = true
    }
    
    func openBIAModel(){
        notificationService.showModal(ModalData(
            presentedView: AnyView(BIAInfoModalView(){
                self.notificationService.dismissModal()
            }),
            backdropDismiss: true
        ))
    }
    
    func openHelp() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(ModelNumberHelpModalView(){
                self.notificationService.dismissModal()
            }),
            backdropDismiss: true
        ))
    }
    
    // MARK: - Display Metrics Functions
    
    /// Saves the display metrics configuration
    func saveDisplayMetrics() {
        // TODO: Implement saveDisplayMetrics functionality
        // - Save the current state of metrics and extraToggles
    }
    
    /// Updates the weight-only mode setting
    func updateWeightOnlyMode() {
        // TODO: Implement updateWeightOnlyMode functionality
        // - Toggle weight-only mode on/off
        // - Update scale configuration
    }
    
    /// Updates the heart rate monitoring setting
    func updateHeartRate() {
        // TODO: Implement updateHeartRate functionality
        // - Toggle heart rate monitoring on/off
        // - Update scale configuration
    }
    
    /// Updates the heart rate monitoring setting
    func refreshWifiNetworks() {
        // TODO: Implement refreshWifiNetworks functionality
    }
    
    func getWifiMacAddressString() -> String {
        // Return the actual WiFi MAC address from the current scale
        guard let scale = scale else {
            return "##:##:##:##:##:##"
        }
        
        // If we have a stored WiFi MAC address, use it
        if let wifiMac = scale.wifiMac, !wifiMac.isEmpty {
            return wifiMac
        }
        
        // Otherwise, try to get it from Bluetooth service
        return "##:##:##:##:##:##"
    }
    
    /// Fetches the WiFi MAC address from the Bluetooth service for the current scale
    func fetchWifiMacAddress() async {
        guard let scale = scale else { return }
        
        do {
            let result = await bluetoothService.getWifiMacAddress(for: scale)
            switch result {
            case .success(let macAddress):
                await MainActor.run {
                    // Update the scale's WiFi MAC address
                    self.scale?.wifiMac = macAddress
                    // Trigger UI update
                    self.objectWillChange.send()
                }
            case .failure(let error):
                logger.log(level: .error, tag: "ScaleStore", message: "Failed to get WiFi MAC address: \(error.localizedDescription)")
            }
        }
    }
    
    /// Shows an alert when the selected scale SKU is already paired and lets the user decide whether to pair again.
    func handleDuplicateScale(sku: String, onPair: @escaping () -> Void) {
        let lang = alertLang.DeviceAlreadyPairedAlert.self
        let alert = AlertModel(
            title: lang.title,
            message: lang.message(sku),
            buttons: [
                AlertButtonModel(title: lang.returnButton, type: .secondary) { _ in },
                AlertButtonModel(title: lang.pairButton, type: .primary) { _ in
                    onPair()
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    // MARK: - Private Helpers
    
    /// Subscribes to `ScaleService` updates and keeps `scales` in sync.
    private func subscribeToScaleUpdates() {
        scaleService.$scales
            .receive(on: DispatchQueue.main)
            .sink { [weak self] devices in
                guard let self = self else { return }
                self.scales = devices
                
                // Update the current scale if it exists in the updated list
                if let currentScale = self.scale,
                   let updatedScale = devices.first(where: { $0.id == currentScale.id }) {
                    self.scale = updatedScale
                    self.isDeviceConnected = updatedScale.isConnected ?? false
                    
                    // Trigger UI update for computed properties
                    self.objectWillChange.send()
                }
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Scale Mode Management
    
    /// Handles the initialization logic when the scale modes screen appears
    func onAppear(scale: Device) {
        // Handle async operations internally without requiring Task from the view
        Task.detached { [scale] in
            await self.loadScale(scale)
            await MainActor.run {
                self.loadScaleModePreferences()
            }
        }
    }
    
    /// Loads initial scale mode preferences
    func loadScaleModePreferences() {
        guard let scale = scale else { 
            logger.log(level: .debug, tag: "ScaleStore", message: "No scale available for loading preferences")
            return 
        }
        
        // Load existing preferences from the scale's R4ScalePreference
        if let r4Preference = scale.r4ScalePreference {
            // Determine mode based on shouldMeasureImpedance
            originalModeValue = r4Preference.shouldMeasureImpedance ? .allBodyMetrics : .weightOnly
            originalHeartRateEnabled = r4Preference.shouldMeasurePulse
            
            logger.log(level: .debug, tag: "ScaleStore", message: "Loaded preferences - shouldMeasureImpedance: \(r4Preference.shouldMeasureImpedance), shouldMeasurePulse: \(r4Preference.shouldMeasurePulse), mode: \(originalModeValue.rawValue)")
        } else {
            // Default values if no preferences exist
            originalModeValue = .weightOnly
            originalHeartRateEnabled = false
            
            logger.log(level: .debug, tag: "ScaleStore", message: "No R4ScalePreference found, using defaults - mode: \(originalModeValue.rawValue)")
        }
        
        // Set current values to match original
        modeValue = originalModeValue
        isHeartRateEnabled = originalHeartRateEnabled
        
        // Update change tracking
        updateModeChangeTracking()
        
        logger.log(level: .debug, tag: "ScaleStore", message: "Final mode value: \(modeValue.rawValue), heart rate enabled: \(isHeartRateEnabled)")
    }
    
    /// Updates the change tracking for mode settings
    func updateModeChangeTracking() {
        hasModeChanges = (modeValue != originalModeValue) || (isHeartRateEnabled != originalHeartRateEnabled)
    }
    
    /// Resets mode settings to original values
    func resetModeSettings() {
        modeValue = originalModeValue
        isHeartRateEnabled = originalHeartRateEnabled
        updateModeChangeTracking()
    }
    
    /// Forces a refresh of the device data to ensure UI is up to date
    func forceRefreshDeviceData() async {
        do {
            let devices = try await scaleService.getDevices()
            await MainActor.run {
                self.scales = devices
                
                // Update the current scale if it exists in the refreshed list
                if let currentScale = self.scale,
                   let updatedScale = devices.first(where: { $0.id == currentScale.id }) {
                    self.scale = updatedScale
                    self.isDeviceConnected = updatedScale.isConnected ?? false
                    
                    // Trigger UI update for computed properties
                    self.objectWillChange.send()
                }
            }
        } catch {
            logger.log(level: .error, tag: "ScaleStore", message: "Failed to force refresh device data: \(error.localizedDescription)")
        }
    }
    
    deinit {
        cancellables.forEach { $0.cancel() }
        cancellables.removeAll()
    }
}
