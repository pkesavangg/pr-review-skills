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

    // Computed property for WiFi value - shows connected WiFi SSID when available
    var wifiValue: String {
        guard let scale = scale else { return "" }
        
        // For R4 scales, try to get the connected WiFi SSID
        if ScaleTypeHelper.determineScaleType(for: scale) == .bluetoothR4 && scale.isConnected == true {
            // If we have a stored connected WiFi SSID, use it
            if let connectedSSID = connectedWifiSSID, !connectedSSID.isEmpty {
                return connectedSSID
            }
            
            // For now, return empty string - this will be populated when WiFi SSID is fetched
            return ""
        }
        
        // For non-R4 scales or disconnected scales, return empty string
        return ""
    } 
 
    @Published var scaleTypeValue: String = "Bluetooth/Wi-Fi"
    @Published var skuValue: String = "0412" // TODO: Replace with actual SKU
    
    // Computed property for date paired - uses the scale's createdAt value
    var datePairedValue: String {
        guard let scale = scale, let createdAt = scale.createdAt else { return "" }
        
        // Use DateTimeTools to parse and format the date
        return DateTimeTools.getFormattedDate(createdAt)
    }
    
    // Scale Mode State Management
    @Published var originalModeValue: ScaleModes = .weightOnly
    @Published var isHeartRateEnabled: Bool = false
    @Published var originalHeartRateEnabled: Bool = false
    @Published var hasModeChanges: Bool = false
    // Display Metrics State
    @Published var metrics: [ScaleMetricSetting] = ScaleMetrics.bodyMetrics
    @Published var progressMetrics: [ScaleMetricSetting] = ScaleMetrics.progressMetrics

    // Banner States
    @Published var showWeightOnlyBanner: Bool = false
    @Published var showWeightOnlyInfo: Bool = false
    @Published var showHeartRateBanner: Bool = false
    
    // Computed property for bluetooth connection status
    var bluetoothValue: String {
        guard let scale = scale else { return ScaleBluetoothStrings.notConnected }
        return scale.isConnected == true ? ScaleBluetoothStrings.connected : ScaleBluetoothStrings.notConnected
    }
    // Computed property for users value - shows current user's name only when connected
    var usersValue: String {
        guard let scale = scale, scale.isConnected == true else { return "" }
        return currentDeviceUser?.name ?? ""
    }

    var wifiMacAddressValue: String {
        guard let scale = scale, scale.isConnected == true else { return "" }
        return getWifiMacAddressString()
    }
    
    @Published var displayMetricsValue: String = ""
    
    // MARK: - Banner Logic Computed Properties
    
    /// Determines if the weight-only banner should be shown
    /// This checks if the scale has weight-only mode enabled by another user
    var shouldShowWeightOnlyBanner: Bool {
        guard let scale = scale else { return false }
        
        // Check if scale is connected and has weight-only mode enabled by others
        let isConnected = scale.isConnected == true
        let hasWeightOnlyModeEnabled = isWeightOnlyModeEnabledByOthers(for: scale)
        
        return isConnected && hasWeightOnlyModeEnabled
    }
    
    /// Determines if the setup incomplete banner should be shown
    /// This checks if the scale is an R4 scale that's not WiFi configured
    /// Note: This banner should not show if the scale is in weight-only mode
    var shouldShowSetupIncompleteBanner: Bool {
        guard let scale = scale else { return false }
        
        // Only show for R4 scales that are connected but not WiFi configured
        let isR4Scale = ScaleTypeHelper.determineScaleType(for: scale) == .bluetoothR4
        let isConnected = scale.isConnected == true
        let isWifiConfigured = scale.isWifiConfigured == true
        let isInWeightOnlyMode = isWeightOnlyModeEnabledByOthers(for: scale)
        
        // Don't show setup incomplete banner if scale is in weight-only mode
        // because WiFi functionality isn't needed for weight-only mode
        return isR4Scale && isConnected && !isWifiConfigured && !isInWeightOnlyMode
    }
    

    
    // MARK: - Banner Action Handlers
    
    /// Handles the weight-only banner action to enable body metrics
    func handleWeightOnlyBannerAction() {
        guard let scale = scale else { return }
        
        guard scale.isConnected == true else {
            logger.log(level: .debug, tag: "ScaleStore", message: "Scale not connected, skipping weight-only mode update")
            return
        }
        
        Task {
            do {
                // Show loader
                await MainActor.run {
                    notificationService.showLoader(LoaderModel(text: "Updating mode..."))
                }
                
                // Update the scale to enable body metrics via Bluetooth service
                let result = await bluetoothService.updateWeightOnlyMode(on: scale)
                
                await MainActor.run {
                    notificationService.dismissLoader()
                    
                    switch result {
                    case .success:
                        notificationService.showToast(ToastModel(
                            title: ToastStrings.success,
                            message: ScaleModesStrings.bodyMetricsEnabled
                        ))
                        // Refresh the scale data to update UI
                        Task {
                            await self.forceRefreshDeviceData()
                        }
                    case .failure(let error):
                        logger.log(level: .error, tag: "ScaleStore", message: "Failed to update weight-only mode: \(error.localizedDescription)")
                        notificationService.showToast(ToastModel(
                            title: "Error",
                            message: ScaleModesStrings.preferencesFailed
                        ))
                    }
                }
            }
        }
    }
    
    /// Callback for WiFi setup navigation - set by the view that uses this store
    var onNavigateToWifi: (() -> Void)?
    
    /// Handles the setup incomplete banner action to navigate to WiFi setup
    func handleSetupIncompleteBannerAction() {
        logger.log(level: .info, tag: "ScaleStore", message: "Setup incomplete banner tapped - navigating to WiFi setup")
        onNavigateToWifi?()
    }

    // User Management State
    @Published var deviceUsers: [DeviceUser] = []
    @Published var currentDeviceUser: DeviceUser?
    @Published var isLoadingUsers: Bool = false
    
    // MARK: - User Management Computed Properties
    /// Gets the other users (excluding the current user)
    var otherDeviceUsersList: [DeviceUser] {
        guard let currentUser = currentDeviceUser else { return deviceUsers }
        return deviceUsers.filter { $0.token != currentUser.token }
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
        
        // Only fetch WiFi MAC address for connected R4 scales
        if shouldFetchWifiMacAddress(for: scale) {
            await fetchWifiMacAddress()
        }
        
        // Fetch user list for R4 scales only if connected
        if ScaleTypeHelper.determineScaleType(for: scale) == .bluetoothR4 && scale.isConnected == true {
            await fetchUserList()
        }
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
        
        // For R4 scales, check if setup is incomplete
        if scaleType == .bluetoothR4 && scale.isConnected == true {
            let isWifiConfigured = scale.isWifiConfigured == true
            let isInWeightOnlyMode = isWeightOnlyModeEnabledByOthers(for: scale)
            
            // If R4 scale is connected but not WiFi configured and not in weight-only mode,
            // show setup incomplete status
            if !isWifiConfigured && !isInWeightOnlyMode {
                return .setupIncomplete
            }
        }
        
        // For other scale types, show connection status based on isConnected
        return scale.isConnected == true ? .connected : .notConnected
    }
    
    /// Determines if weight-only mode is enabled by another user for a specific scale
    /// This checks the R4ScalePreference to determine if impedance is disabled
    private func isWeightOnlyModeEnabledByOthers(for scale: Device) -> Bool {
        // For now, we'll check the R4ScalePreference to determine if impedance is disabled
        // In a real implementation, this would come from the Bluetooth service
        if let r4Preference = scale.r4ScalePreference {
            // If shouldMeasureImpedance is false, it means weight-only mode is enabled
            return !r4Preference.shouldMeasureImpedance
        }
        
        // Fallback: check if the scale is in weight-only mode based on preferences
        return false
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
        guard let scale = scale else { return }
        
        // Only fetch WiFi SSID for connected R4 scales
        if ScaleTypeHelper.determineScaleType(for: scale) == .bluetoothR4 && scale.isConnected == true {
            do {
                let result = await bluetoothService.getConnectedWifiSSID(broadcastId: scale.broadcastIdString ?? "")
                switch result {
                case .success(let ssid):
                    await MainActor.run {
                        self.connectedWifiSSID = ssid.isEmpty ? nil : ssid
                        // Trigger UI update for computed properties
                        self.objectWillChange.send()
                    }
                case .failure(let error):
                    logger.log(level: .error, tag: "ScaleStore", message: "Failed to get connected WiFi SSID: \(error.localizedDescription)")
                    await MainActor.run {
                        self.connectedWifiSSID = nil
                    }
                }
            }
        } else {
            // For non-R4 scales or disconnected scales, clear the SSID
            await MainActor.run {
                self.connectedWifiSSID = nil
            }
        }
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
    
    // MARK: - User Management Functions
    
    /// Fetches the user list from the Bluetooth service for the current scale
    func fetchUserList() async {
        guard let scale = scale else {
            logger.log(level: .error, tag: "ScaleStore", message: "No scale available for fetching users")
            return
        }
        
        guard scale.isConnected == true else {
            logger.log(level: .debug, tag: "ScaleStore", message: "Scale not connected, skipping user list fetch")
            return
        }
        
        await MainActor.run {
            isLoadingUsers = true
        }
        
        do {
            let result = await bluetoothService.getScaleUserList(for: scale)
            switch result {
            case .success(let users):
                await MainActor.run {
                    self.deviceUsers = users
                    self.updateCurrentUserFromDeviceUsers(users)
                    self.isLoadingUsers = false
                }
                logger.log(level: .debug, tag: "ScaleStore", message: "Successfully fetched \(users.count) users from scale")
            case .failure(let error):
                await MainActor.run {
                    self.deviceUsers = []
                    self.currentDeviceUser = nil
                    self.isLoadingUsers = false
                }
                logger.log(level: .error, tag: "ScaleStore", message: "Failed to fetch users: \(error.localizedDescription)")
            }
        }
    }
    
    /// Updates the current user based on the device token and fetched users
    private func updateCurrentUserFromDeviceUsers(_ users: [DeviceUser]) {
        guard let scale = scale, let deviceToken = scale.token else {
            currentDeviceUser = nil
            return
        }
        
        // Find the user that matches the device token
        if let matchingUser = users.first(where: { $0.token == deviceToken }) {
            currentDeviceUser = matchingUser
        } else if let displayName = scale.r4ScalePreference?.displayName {
            // Fallback to display name from scale preferences
            currentDeviceUser = DeviceUser(
                name: displayName,
                token: deviceToken,
                lastActive: Int(Date().timeIntervalSince1970),
                isBodyMetricsEnabled: scale.r4ScalePreference?.shouldMeasureImpedance ?? true
            )
        }
    }
    
    /// Refreshes the user list from the Bluetooth service
    func refreshUserList() async {
        guard let scale = scale, scale.isConnected == true else {
            logger.log(level: .debug, tag: "ScaleStore", message: "Scale not connected, skipping user list refresh")
            return
        }
        await fetchUserList()
    }
    
    /// Deletes a user from the scale
    func deleteUser(_ user: DeviceUser) async {
        guard let scale = scale else {
            logger.log(level: .error, tag: "ScaleStore", message: "No scale available for deleting user")
            return
        }
        
        guard scale.isConnected == true else {
            logger.log(level: .debug, tag: "ScaleStore", message: "Scale not connected, skipping user deletion")
            return
        }
        
        // Show loader using the notification service
        await MainActor.run {
            notificationService.showLoader(LoaderModel(text: UsersViewStrings.deletingUser))
        }
        
        do {
            // Create a device with the user's token for deletion
            let deviceForDeletion = Device(
                id: scale.id,
                accountId: scale.accountId,
                peripheralIdentifier: nil,
                nickname: scale.nickname,
                sku: scale.sku,
                mac: scale.mac,
                password: scale.password,
                isDeleted: nil,
                deviceName: user.name,
                deviceType: scale.deviceType,
                broadcastId: scale.broadcastId,
                broadcastIdString: scale.broadcastIdString,
                userNumber: scale.userNumber,
                protocolType: scale.protocolType,
                createdAt: scale.createdAt,
                lastModified: nil,
                isSynced: nil,
                hasServerID: false,
                isConnected: scale.isConnected,
                wifiMac: scale.wifiMac,
                isWifiConfigured: nil,
                token: user.token,
                bathScale: scale.bathScale,
                r4ScalePreference: scale.r4ScalePreference,
                metaData: scale.metaData
            )
            
            let result = await bluetoothService.deleteDevice(deviceForDeletion, disconnect: false)
            
            await MainActor.run {
                notificationService.dismissLoader()
                
                switch result {
                case .success(let response):
                    if response == .success {
                        // Remove the user from the local list
                        self.deviceUsers.removeAll { $0.token == user.token }
                        logger.log(level: .info, tag: "ScaleStore", message: "Successfully deleted user: \(user.name)")
                        
                        // Show success toast
                        notificationService.showToast(ToastModel(
                            title: "Success",
                            message: "User deleted successfully"
                        ))
                    } else {
                        logger.log(level: .error, tag: "ScaleStore", message: "Failed to delete user: \(response)")
                        
                        // Show error toast
                        notificationService.showToast(ToastModel(
                            title: "Error",
                            message: "Failed to delete user"
                        ))
                    }
                case .failure(let error):
                    logger.log(level: .error, tag: "ScaleStore", message: "Failed to delete user: \(error.localizedDescription)")
                    
                    // Show error toast
                    notificationService.showToast(ToastModel(
                        title: "Error",
                        message: "Failed to delete user: \(error.localizedDescription)"
                    ))
                }
            }
        }
    }
    
    /// Updates the current user's name inline
    func updateCurrentUserNameInline(_ newName: String) async {
        guard let scale = scale else {
            logger.log(level: .error, tag: "ScaleStore", message: "No scale available for updating user name")
            return
        }
        
        guard scale.isConnected == true else {
            logger.log(level: .debug, tag: "ScaleStore", message: "Scale not connected, skipping user name update")
            return
        }
        
        await MainActor.run {
            isLoadingUsers = true
        }
        
        do {
            // Create updated R4ScalePreference with new display name
            let updatedPreference = R4ScalePreference(
                scaleId: scale.id,
                displayName: newName,
                displayMetrics: scale.r4ScalePreference?.displayMetrics ?? ScaleMetrics.defaultMetricsKeys,
                shouldFactoryReset: false,
                shouldMeasureImpedance: scale.r4ScalePreference?.shouldMeasureImpedance ?? true,
                shouldMeasurePulse: scale.r4ScalePreference?.shouldMeasurePulse ?? false,
                timeFormat: scale.r4ScalePreference?.timeFormat ?? "12",
                tzOffset: scale.r4ScalePreference?.tzOffset ?? DateTimeTools.getTimeZoneInMinutes(),
                wifiFotaScheduleTime: scale.r4ScalePreference?.wifiFotaScheduleTime ?? Int(Date().timeIntervalSince1970),
                updatedAt: DateTimeTools.getCurrentDatetimeIsoString()
            )
            
            let result = await bluetoothService.updateAccount(on: scale, preference: updatedPreference)
            switch result {
            case .success(let response):
                if response == .creationCompleted {
                    await MainActor.run {
                        self.currentDeviceUser = DeviceUser(
                            name: newName,
                            token: self.currentDeviceUser?.token,
                            lastActive: self.currentDeviceUser?.lastActive ?? Int(Date().timeIntervalSince1970),
                            isBodyMetricsEnabled: self.currentDeviceUser?.isBodyMetricsEnabled ?? true
                        )
                        self.isLoadingUsers = false
                    }
                    
                    // Update the scale's R4ScalePreference
                    await MainActor.run {
                        self.scale?.r4ScalePreference = updatedPreference
                    }
                    
                    logger.log(level: .info, tag: "ScaleStore", message: "Successfully updated user name to: \(newName)")
                    
                    // Show success toast
                    await MainActor.run {
                        notificationService.showToast(ToastModel(
                            title: "Success",
                            message: "User name updated successfully"
                        ))
                    }
                    
                    // Refresh the user list
                    await refreshUserList()
                } else {
                    await MainActor.run {
                        self.isLoadingUsers = false
                    }
                    logger.log(level: .error, tag: "ScaleStore", message: "Failed to update user name: \(response)")
                    
                    // Show error toast
                    await MainActor.run {
                        notificationService.showToast(ToastModel(
                            title: "Error",
                            message: "Failed to update user name"
                        ))
                    }
                }
            case .failure(let error):
                await MainActor.run {
                    self.isLoadingUsers = false
                }
                logger.log(level: .error, tag: "ScaleStore", message: "Failed to update user name: \(error.localizedDescription)")
                
                // Show error toast
                await MainActor.run {
                    notificationService.showToast(ToastModel(
                        title: "Error",
                        message: "Failed to update user name: \(error.localizedDescription)"
                    ))
                }
            }
        }
    }
    
    /// Shows a confirmation alert before deleting a user
    func showDeleteUserAlert(for user: DeviceUser, onDelete: @escaping () -> Void) {
        let alert = AlertModel(
            title: "Delete User",
            message: "Are you sure you want to delete \(user.name)?",
            buttons: [
                AlertButtonModel(title: "Cancel", type: .secondary) { _ in },
                AlertButtonModel(title: "Delete", type: .primary) { _ in
                    Task {
                        await self.deleteUser(user)
                        onDelete()
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    /// Updates the current user's name
    func updateCurrentUserName(_ newName: String) async {
        guard let scale = scale else {
            logger.log(level: .error, tag: "ScaleStore", message: "No scale available for updating user name")
            return
        }
        
        guard scale.isConnected == true else {
            logger.log(level: .debug, tag: "ScaleStore", message: "Scale not connected, skipping user name update")
            return
        }
        
        await MainActor.run {
            isLoadingUsers = true
        }
        
        do {
            // Create updated R4ScalePreference with new display name
            let updatedPreference = R4ScalePreference(
                scaleId: scale.id,
                displayName: newName,
                displayMetrics: scale.r4ScalePreference?.displayMetrics ?? ScaleMetrics.defaultMetricsKeys,
                shouldFactoryReset: false,
                shouldMeasureImpedance: scale.r4ScalePreference?.shouldMeasureImpedance ?? true,
                shouldMeasurePulse: scale.r4ScalePreference?.shouldMeasurePulse ?? false,
                timeFormat: scale.r4ScalePreference?.timeFormat ?? "12",
                tzOffset: scale.r4ScalePreference?.tzOffset ?? DateTimeTools.getTimeZoneInMinutes(),
                wifiFotaScheduleTime: scale.r4ScalePreference?.wifiFotaScheduleTime ?? Int(Date().timeIntervalSince1970),
                updatedAt: DateTimeTools.getCurrentDatetimeIsoString()
            )
            
            let result = await bluetoothService.updateAccount(on: scale, preference: updatedPreference)
            switch result {
            case .success(let response):
                if response == .creationCompleted {
                    await MainActor.run {
                        self.currentDeviceUser = DeviceUser(
                            name: newName,
                            token: self.currentDeviceUser?.token,
                            lastActive: self.currentDeviceUser?.lastActive ?? Int(Date().timeIntervalSince1970),
                            isBodyMetricsEnabled: self.currentDeviceUser?.isBodyMetricsEnabled ?? true
                        )
                        self.isLoadingUsers = false
                    }
                    
                    // Update the scale's R4ScalePreference
                    await MainActor.run {
                        self.scale?.r4ScalePreference = updatedPreference
                    }
                    
                    logger.log(level: .info, tag: "ScaleStore", message: "Successfully updated user name to: \(newName)")
                    
                    // Show success toast
                    await MainActor.run {
                        notificationService.showToast(ToastModel(
                            title: "Success",
                            message: "User name updated successfully"
                        ))
                    }
                } else {
                    await MainActor.run {
                        self.isLoadingUsers = false
                    }
                    logger.log(level: .error, tag: "ScaleStore", message: "Failed to update user name: \(response)")
                    
                    // Show error toast
                    await MainActor.run {
                        notificationService.showToast(ToastModel(
                            title: "Error",
                            message: "Failed to update user name"
                        ))
                    }
                }
            case .failure(let error):
                await MainActor.run {
                    self.isLoadingUsers = false
                }
                logger.log(level: .error, tag: "ScaleStore", message: "Failed to update user name: \(error.localizedDescription)")
                
                // Show error toast
                await MainActor.run {
                    notificationService.showToast(ToastModel(
                        title: "Error",
                        message: "Failed to update user name: \(error.localizedDescription)"
                    ))
                }
            }
        }
    }
        
    /// Saves the current user's name with proper loading states and notifications
    /// - Parameter newName: The new name to save for the current user
    func saveUsers(newName: String) async {
        guard let scale = scale else {
            logger.log(level: .error, tag: "ScaleStore", message: "No scale available for saving user name")
            return
        }
        
        guard scale.isConnected == true else {
            logger.log(level: .debug, tag: "ScaleStore", message: "Scale not connected, skipping user name save")
            return
        }
        
        // Show loader
        await MainActor.run {
            notificationService.showLoader(LoaderModel(text: LoaderStrings.saving))
        }
        
        do {
            // Create updated R4ScalePreference with new display name
            let updatedPreference = R4ScalePreference(
                scaleId: scale.id,
                displayName: newName,
                displayMetrics: scale.r4ScalePreference?.displayMetrics ?? ScaleMetrics.defaultMetricsKeys,
                shouldFactoryReset: false,
                shouldMeasureImpedance: scale.r4ScalePreference?.shouldMeasureImpedance ?? true,
                shouldMeasurePulse: scale.r4ScalePreference?.shouldMeasurePulse ?? false,
                timeFormat: scale.r4ScalePreference?.timeFormat ?? "12",
                tzOffset: scale.r4ScalePreference?.tzOffset ?? DateTimeTools.getTimeZoneInMinutes(),
                wifiFotaScheduleTime: scale.r4ScalePreference?.wifiFotaScheduleTime ?? Int(Date().timeIntervalSince1970),
                updatedAt: DateTimeTools.getCurrentDatetimeIsoString()
            )
            
            let result = await bluetoothService.updateAccount(on: scale, preference: updatedPreference)
            switch result {
            case .success(let response):
                if response == .creationCompleted {
                    await MainActor.run {
                        // Update the current user
                        self.currentDeviceUser = DeviceUser(
                            name: newName,
                            token: self.currentDeviceUser?.token,
                            lastActive: self.currentDeviceUser?.lastActive ?? Int(Date().timeIntervalSince1970),
                            isBodyMetricsEnabled: self.currentDeviceUser?.isBodyMetricsEnabled ?? true
                        )
                        
                        // Update the scale's R4ScalePreference
                        self.scale?.r4ScalePreference = updatedPreference
                        
                        // Dismiss loader
                        notificationService.dismissLoader()
                        
                        // Show success toast
                        notificationService.showToast(ToastModel(
                            title: ToastStrings.success,
                            message: "User name updated successfully"
                        ))
                    }
                    
                    logger.log(level: .info, tag: "ScaleStore", message: "Successfully updated user name to: \(newName)")
                    
                    // Refresh the user list to update UI
                    await refreshUserList()
                } else {
                    await MainActor.run {
                        notificationService.dismissLoader()
                                            notificationService.showToast(ToastModel(
                        title: "Error",
                        message: "Failed to update user name"
                    ))
                    }
                    logger.log(level: .error, tag: "ScaleStore", message: "Failed to update user name: \(response)")
                }
            case .failure(let error):
                await MainActor.run {
                    notificationService.dismissLoader()
                    notificationService.showToast(ToastModel(
                        title: "Error",
                        message: "Failed to update user name: \(error.localizedDescription)"
                    ))
                }
                logger.log(level: .error, tag: "ScaleStore", message: "Failed to update user name: \(error.localizedDescription)")
            }
        }
    }
    
    func deleteCurrentUser() {
        // TODO: Implement delete current user logic
        // For now, just clear the current user
        currentDeviceUser = nil
    }
    
    func deleteOtherUser(at index: Int) {
        // TODO: Implement delete other user logic
        guard index < deviceUsers.count else { return }
        deviceUsers.remove(at: index)
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
        guard let scale = scale else {
            logger.log(level: .error, tag: "ScaleStore", message: "No scale available for saving display metrics")
            return
        }
        
        Task {
            do {
                // Show loader
                await MainActor.run {
                    notificationService.showLoader(LoaderModel(text: LoaderStrings.saving))
                }
                
                // Extract display metrics from current state (body metrics first, then progress metrics)
                let bodyMetrics = extractDisplayMetrics(from: metrics)
                let progressMetricsKeys = extractDisplayMetrics(from: progressMetrics)
                let displayMetrics = bodyMetrics + progressMetricsKeys
                
                logger.log(level: .debug, tag: "ScaleStore", message: "Saving display metrics: \(displayMetrics)")
                
                // Create updated R4ScalePreference with new display metrics
                // Ensure all required fields have proper default values
                let updatedPreference = R4ScalePreference(
                    scaleId: scale.id,
                    displayName: scale.r4ScalePreference?.displayName ?? scale.nickname ?? scale.deviceName ?? "Unknown Device",
                    displayMetrics: displayMetrics.isEmpty ? ScaleMetrics.defaultMetricsKeys : displayMetrics,
                    shouldFactoryReset: scale.r4ScalePreference?.shouldFactoryReset ?? false,
                    shouldMeasureImpedance: scale.r4ScalePreference?.shouldMeasureImpedance ?? true,
                    shouldMeasurePulse: scale.r4ScalePreference?.shouldMeasurePulse ?? true,
                    timeFormat: scale.r4ScalePreference?.timeFormat ?? "12",
                    tzOffset: scale.r4ScalePreference?.tzOffset ?? DateTimeTools.getTimeZoneInMinutes(),
                    wifiFotaScheduleTime: scale.r4ScalePreference?.wifiFotaScheduleTime ?? Int(Date().timeIntervalSince1970),
                    updatedAt: DateTimeTools.getCurrentDatetimeIsoString()
                )
                
                // Update scale preferences via ScaleService
                try await scaleService.updateScalePreference(scale.id, updatedPreference)
                
                // If device is connected, also update via Bluetooth
                if scale.isConnected == true {
                    let result = await bluetoothService.updateAccount(on: scale, preference: updatedPreference)
                    switch result {
                    case .success(let response):
                        if response == .creationCompleted {
                            logger.log(level: .info, tag: "ScaleStore", message: "Display metrics updated on scale successfully")
                        } else {
                            logger.log(level: .error, tag: "ScaleStore", message: "Display metrics saved locally but scale update returned: \(response)")
                        }
                    case .failure(let error):
                        logger.log(level: .error, tag: "ScaleStore", message: "Failed to update display metrics on scale: \(error.localizedDescription)")
                    }
                }
                
                // Update the scale's R4ScalePreference with the new values
                await MainActor.run {
                    // Update the existing preference instead of creating a new one
                    if let existingPreference = self.scale?.r4ScalePreference {
                        existingPreference.displayMetrics = updatedPreference.displayMetrics
                        existingPreference.displayName = updatedPreference.displayName
                        existingPreference.shouldFactoryReset = updatedPreference.shouldFactoryReset
                        existingPreference.shouldMeasureImpedance = updatedPreference.shouldMeasureImpedance
                        existingPreference.shouldMeasurePulse = updatedPreference.shouldMeasurePulse
                        existingPreference.timeFormat = updatedPreference.timeFormat
                        existingPreference.tzOffset = updatedPreference.tzOffset
                        existingPreference.wifiFotaScheduleTime = updatedPreference.wifiFotaScheduleTime
                        existingPreference.updatedAt = updatedPreference.updatedAt
                    } else {
                        // If no existing preference, create a new one
                        self.scale?.r4ScalePreference = updatedPreference
                    }
                    
                    // Update display metrics value
                    self.updateDisplayMetricsValue()
                    
                    // Trigger UI update for computed properties
                    self.objectWillChange.send()
                    
                    // Dismiss loader
                    notificationService.dismissLoader()
                    
                    // Show success toast
                    notificationService.showToast(ToastModel(
                        title: ToastStrings.success,
                        message: "Display metrics saved successfully"
                    ))
                }
                
                logger.log(level: .info, tag: "ScaleStore", message: "Display metrics saved successfully")
                
            } catch {
                await MainActor.run {
                    notificationService.dismissLoader()
                    notificationService.showToast(ToastModel(
                        title: "Error",
                        message: "Failed to save display metrics"
                    ))
                }
                logger.log(level: .error, tag: "ScaleStore", message: "Failed to save display metrics: \(error.localizedDescription)")
            }
        }
    }
    
    /// Extracts display metrics from the current metrics state
    /// - Parameter metrics: Array of all metrics (body + progress)
    /// - Returns: Array of metric keys that are enabled, preserving the current order
    private func extractDisplayMetrics(from metrics: [ScaleMetricSetting]) -> [String] {
        // Get enabled metrics in their current order (enabled items first, then disabled)
        return metrics.filter { $0.isEnabled }.map { $0.key }
    }
    
    /// Loads display metrics from the scale's preferences and applies them to the UI
    /// Disabled items are moved to the end of the array
    func loadDisplayMetrics() {
        guard let scale = scale else {
            logger.log(level: .debug, tag: "ScaleStore", message: "No scale available for loading display metrics")
            return
        }
        
        // Get display metrics from scale preferences
        let displayMetrics = scale.r4ScalePreference?.displayMetrics ?? ScaleMetrics.defaultMetricsKeys
        
        logger.log(level: .debug, tag: "ScaleStore", message: "Loading display metrics: \(displayMetrics)")
        
        // Update body metrics with correct order and enabled state
        let bodyMetricsConfig = ScaleMetrics.bodyMetrics
        let bodyMetricsWithState = bodyMetricsConfig.map { config in
            ScaleMetricSetting(
                name: config.name,
                key: config.key,
                imagePath: config.imagePath,
                isEnabled: displayMetrics.contains(config.key),
                isProgressMetrics: false
            )
        }
        
        // Sort body metrics: enabled first (in API order), then disabled (in original order)
        metrics = bodyMetricsWithState.sorted { first, second in
            if first.isEnabled == second.isEnabled {
                if first.isEnabled {
                    // Both enabled - maintain API order
                    let firstApiIndex = displayMetrics.firstIndex(of: first.key) ?? Int.max
                    let secondApiIndex = displayMetrics.firstIndex(of: second.key) ?? Int.max
                    return firstApiIndex < secondApiIndex
                } else {
                    // Both disabled - maintain original order
                    let firstIndex = bodyMetricsConfig.firstIndex { $0.key == first.key } ?? 0
                    let secondIndex = bodyMetricsConfig.firstIndex { $0.key == second.key } ?? 0
                    return firstIndex < secondIndex
                }
            }
            return first.isEnabled && !second.isEnabled
        }
        
        // Update progress metrics with correct order and enabled state
        let progressMetricsConfig = ScaleMetrics.progressMetrics
        let progressMetricsWithState = progressMetricsConfig.map { config in
            ScaleMetricSetting(
                name: config.name,
                key: config.key,
                imagePath: config.imagePath,
                isEnabled: displayMetrics.contains(config.key),
                isProgressMetrics: true
            )
        }
        
        // Sort progress metrics: enabled first (in API order), then disabled (in original order)
        progressMetrics = progressMetricsWithState.sorted { first, second in
            if first.isEnabled == second.isEnabled {
                if first.isEnabled {
                    // Both enabled - maintain API order
                    let firstApiIndex = displayMetrics.firstIndex(of: first.key) ?? Int.max
                    let secondApiIndex = displayMetrics.firstIndex(of: second.key) ?? Int.max
                    return firstApiIndex < secondApiIndex
                } else {
                    // Both disabled - maintain original order
                    let firstIndex = progressMetricsConfig.firstIndex { $0.key == first.key } ?? 0
                    let secondIndex = progressMetricsConfig.firstIndex { $0.key == second.key } ?? 0
                    return firstIndex < secondIndex
                }
            }
            return first.isEnabled && !second.isEnabled
        }
        
        // Update display metrics value
        updateDisplayMetricsValue()
        
        // Trigger UI update for computed properties
        objectWillChange.send()
        
        logger.log(level: .debug, tag: "ScaleStore", message: "Display metrics loaded successfully")
    }
    

    
    /// Updates the display metrics value based on current state
    func updateDisplayMetricsValue() {
        let enabledMetrics = metrics.filter { $0.isEnabled }
        let enabledProgressMetrics = progressMetrics.filter { $0.isEnabled }
        let totalEnabled = enabledMetrics.count + enabledProgressMetrics.count
        
        if totalEnabled == 0 {
            displayMetricsValue = "None"
        } else if totalEnabled == (metrics.count + progressMetrics.count) {
            displayMetricsValue = "All"
        } else {
            displayMetricsValue = "\(totalEnabled) selected"
        }
    }
    
    /// Handles reordering of metrics and ensures disabled items stay at the end
    func handleMetricsReorder(indices: IndexSet, newOffset: Int, isProgressMetrics: Bool) {
        let targetArray = isProgressMetrics ? progressMetrics : metrics
        
        // Perform the move operation
        if isProgressMetrics {
            progressMetrics.move(fromOffsets: indices, toOffset: newOffset)
        } else {
            metrics.move(fromOffsets: indices, toOffset: newOffset)
        }
        
        // Re-sort to ensure disabled items are at the end
        reorderMetricsToKeepDisabledAtEnd(isProgressMetrics: isProgressMetrics)
        
        // Update display metrics value
        updateDisplayMetricsValue()
    }
    
    /// Reorders metrics to ensure disabled items are always at the end
    private func reorderMetricsToKeepDisabledAtEnd(isProgressMetrics: Bool) {
        if isProgressMetrics {
            progressMetrics.sort { first, second in
                if first.isEnabled == second.isEnabled {
                    // If both have same enabled state, maintain current order
                    let firstIndex = progressMetrics.firstIndex { $0.key == first.key } ?? 0
                    let secondIndex = progressMetrics.firstIndex { $0.key == second.key } ?? 0
                    return firstIndex < secondIndex
                }
                return first.isEnabled && !second.isEnabled
            }
        } else {
            metrics.sort { first, second in
                if first.isEnabled == second.isEnabled {
                    // If both have same enabled state, maintain current order
                    let firstIndex = metrics.firstIndex { $0.key == first.key } ?? 0
                    let secondIndex = metrics.firstIndex { $0.key == second.key } ?? 0
                    return firstIndex < secondIndex
                }
                return first.isEnabled && !second.isEnabled
            }
        }
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
        // Return empty string if device is not connected
        guard let scale = scale, scale.isConnected == true else {
            return ""
        }
        
        // If we have a stored WiFi MAC address, use it
        if let wifiMac = scale.wifiMac, !wifiMac.isEmpty {
            return wifiMac
        }
        
        // Otherwise, return empty string
        return ""
    }
    
    /// Determines if WiFi MAC address should be fetched for the given scale
    /// - Parameter scale: The device to check
    /// - Returns: True if the scale is a connected R4 scale that supports WiFi
    func shouldFetchWifiMacAddress(for scale: Device) -> Bool {
        guard scale.isConnected == true else { return false }       
        let isR4Scale = ScaleTypeHelper.determineScaleType(for: scale) == .bluetoothR4
        return isR4Scale
    }

    
    /// Fetches the WiFi MAC address from the Bluetooth service for the current scale
    func fetchWifiMacAddress() async {
        guard let scale = scale else { return }
        
        guard scale.isConnected == true else {
            logger.log(level: .debug, tag: "ScaleStore", message: "Scale not connected, skipping WiFi MAC address fetch")
            return
        }
        
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
        Task.detached { [weak self] in
            guard let self = self else { return }
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
