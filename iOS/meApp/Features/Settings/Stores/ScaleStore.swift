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
    
    // MARK: - Dependencies
    @Injector private var notificationService: NotificationHelperService
    @Injector private var scaleService: ScaleService
    @Injector var bluetoothService: BluetoothService
    @Injector private var logger: LoggerService

    // MARK: - Centralized State
    @Published var state: ScaleState = ScaleState()

    // MARK: - Public Properties (Backward Compatibility)
    @Published var scales: [Device] = []
    @Published var isLoading: Bool = false
    @Published var errorMessage: String? = nil
    @Published var addScaleForm = AddScaleForm()
    
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
    
    @Published var showTermsBrowser: Bool = false
    @Published var browserURL: URL? = nil
    
    @Published var modeValue: ScaleModes = .weightOnly
    @Published var scaleTypeValue: String = "Bluetooth/Wi-Fi"
    @Published var skuValue: String = "0412"
    
    @Published var originalModeValue: ScaleModes = .weightOnly
    @Published var isHeartRateEnabled: Bool = false
    @Published var originalHeartRateEnabled: Bool = false
    @Published var hasModeChanges: Bool = false
    
    @Published var metrics: [ScaleMetricSetting] = ScaleMetrics.bodyMetrics
    @Published var progressMetrics: [ScaleMetricSetting] = ScaleMetrics.progressMetrics

    @Published var showWeightOnlyBanner: Bool = false
    @Published var showWeightOnlyInfo: Bool = false
    @Published var showHeartRateBanner: Bool = false
    
    @Published var deviceUsers: [DeviceUser] = []
    @Published var currentDeviceUser: DeviceUser?
    @Published var isLoadingUsers: Bool = false
    
    @Published var isWifiLoading = false
    @Published var showPassword: Bool = false
    @Published var wifiPasswordValidationForm = WifiPasswordValidationForm()
    @Published var wifiConnectionState: ConnectionState = .loading
    @Published var connectedWifiNetwork: String? = nil
    @Published var wifiNetworks: [String] = ["greatergoods1", "great2542", "ggtesting"]
    
    @Published var displayMetricsValue: String = ""
    
    // MARK: - Initialization
    init() {
        // Initialize managers
        self.dataManager = ScaleDataManager()
        self.deviceManager = ScaleDeviceManager()
        self.usersManager = ScaleUsersManager()
        self.modesManager = ScaleModesManager()
        self.metricsManager = ScaleMetricsManager()

        // Set up reactive bindings
        setupBindings()
        setupSubscriptions()
        
        // Initialize data
        Task {
            await dataManager.fetchScales()
        }
    }
    
    // MARK: - Private Properties
    private var cancellables = Set<AnyCancellable>()

    // MARK: - Constants
    private let legalURLs = AppConstants.LegalURLs.self
    private let alertLang = AlertStrings.self
    private let loaderLang = LoaderStrings.self

    // MARK: - Managers (Business Logic)
    private let dataManager: ScaleDataManager
    private let deviceManager: ScaleDeviceManager
    private let usersManager: ScaleUsersManager
    private let modesManager: ScaleModesManager
    private let metricsManager: ScaleMetricsManager
    
    // MARK: - Computed Properties
    var wifiValue: String { deviceManager.wifiValue }
    var datePairedValue: String { deviceManager.datePairedValue }
    var bluetoothValue: String { deviceManager.bluetoothValue }
    var wifiMacAddressValue: String { deviceManager.wifiMacAddressValue }
    var usersValue: String { usersManager.usersValue }
    var otherDeviceUsersList: [DeviceUser] { usersManager.otherDeviceUsersList }

    var shouldShowWeightOnlyBanner: Bool {
        guard let scale = state.device.scale else { return false }
        return modesManager.shouldShowWeightOnlyBanner(for: scale)
    }

    var shouldShowSetupIncompleteBanner: Bool {
        guard let scale = state.device.scale else { return false }
        return modesManager.shouldShowSetupIncompleteBanner(for: scale, connectedWifiSSID: state.device.connectedWifiSSID)
    }

    var isFormValid: Bool { dataManager.isFormValid }
    var passwordError: String? { dataManager.passwordError }

    var isBrowserPresented: Binding<Bool> {
        Binding(
            get: { self.state.ui.showTermsBrowser },
            set: { newValue in
                if !newValue {
                    self.state.ui.showTermsBrowser = false
                    self.state.data.browserURL = nil
                }
            }
        )
    }

    var presentingBrowserURL: URL {
        let url = state.data.browserURL ?? legalURLs.greaterGoodsWebsite
        return url
    }
    
    /// Loader binding for presentLoader
    var loaderData: Binding<LoaderModel?> {
        Binding(
            get: { self.isLoading ? LoaderModel(text: LoaderStrings.loading) : nil },
            set: { _ in }
        )
    }
    
    // MARK: - Reactive Bindings
    private func setupBindings() {
        // Sync manager states to centralized state
        dataManager.$state
            .receive(on: DispatchQueue.main)
            .sink { [weak self] dataState in
                self?.state.data = dataState
                // Update public properties for backward compatibility
                self?.scales = dataState.scales
                self?.addScaleForm = dataState.addScaleForm
                self?.wifiPasswordValidationForm = dataState.wifiPasswordValidationForm
                self?.nicknameInput = dataState.nicknameInput
                self?.browserURL = dataState.browserURL
            }
            .store(in: &cancellables)

        deviceManager.$state
            .receive(on: DispatchQueue.main)
            .sink { [weak self] deviceState in
                self?.state.device = deviceState
                // Update public properties for backward compatibility
                self?.scale = deviceState.scale
                self?.firmwareVersion = deviceState.firmwareVersion
                self?.macAddress = deviceState.macAddress
                self?.connectedWifiSSID = deviceState.connectedWifiSSID
                self?.deviceInfo = deviceState.deviceInfo
                self?.isBluetoothScale = deviceState.isBluetoothScale
                self?.isDeviceConnected = deviceState.isDeviceConnected
                self?.scaleTypeValue = deviceState.scaleTypeValue
                self?.skuValue = deviceState.skuValue

            }
            .store(in: &cancellables)

        usersManager.$state
            .receive(on: DispatchQueue.main)
            .sink { [weak self] usersState in
                self?.state.users = usersState
                // Update public properties for backward compatibility
                self?.deviceUsers = usersState.deviceUsers
                self?.currentDeviceUser = usersState.currentDeviceUser
                self?.isLoadingUsers = usersState.isLoadingUsers
            }
            .store(in: &cancellables)

        modesManager.$state
            .receive(on: DispatchQueue.main)
            .sink { [weak self] modesState in
                guard let self = self else { return }
                
                // Only update if values actually changed to avoid unnecessary UI updates
                let modeChanged = self.state.modes.modeValue != modesState.modeValue
                let heartRateChanged = self.state.modes.isHeartRateEnabled != modesState.isHeartRateEnabled
                let originalModeChanged = self.state.modes.originalModeValue != modesState.originalModeValue
                let originalHeartRateChanged = self.state.modes.originalHeartRateEnabled != modesState.originalHeartRateEnabled
                let hasChangesChanged = self.state.modes.hasModeChanges != modesState.hasModeChanges
                
                if modeChanged || heartRateChanged || originalModeChanged || originalHeartRateChanged || hasChangesChanged {
                    self.state.modes = modesState
                    // Update public properties for backward compatibility
                    self.modeValue = modesState.modeValue
                    self.isHeartRateEnabled = modesState.isHeartRateEnabled
                    self.originalModeValue = modesState.originalModeValue
                    self.originalHeartRateEnabled = modesState.originalHeartRateEnabled
                    self.hasModeChanges = modesState.hasModeChanges
                }
            }
            .store(in: &cancellables)

        metricsManager.$state
            .receive(on: DispatchQueue.main)
            .sink { [weak self] metricsState in
                guard let self = self else { return }
                
                // Only update if values actually changed to avoid unnecessary UI updates
                let metricsChanged = self.state.metrics.metrics != metricsState.metrics
                let progressMetricsChanged = self.state.metrics.progressMetrics != metricsState.progressMetrics
                let displayValueChanged = self.state.metrics.displayMetricsValue != metricsState.displayMetricsValue
                
                if metricsChanged || progressMetricsChanged || displayValueChanged {
                    self.state.metrics = metricsState
                    // Update public properties for backward compatibility
                    self.metrics = metricsState.metrics
                    self.progressMetrics = metricsState.progressMetrics
                    self.displayMetricsValue = metricsState.displayMetricsValue
                }
            }
            .store(in: &cancellables)
    }

    private func setupSubscriptions() {
        // Subscribe to scale service updates
        scaleService.$scales
            .receive(on: DispatchQueue.main)
            .sink { [weak self] devices in
                self?.state.data.scales = devices
                // Update public property for backward compatibility
                self?.scales = devices
                
                if let currentScale = self?.state.device.scale,
                   let updatedScale = devices.first(where: { $0.id == currentScale.id }) {
                    self?.state.device.scale = updatedScale
                    self?.state.device.isDeviceConnected = updatedScale.isConnected ?? false
                    // Update public property for backward compatibility
                    self?.isDeviceConnected = updatedScale.isConnected ?? false
                }
            }
            .store(in: &cancellables)
    }

    // MARK: - Scale Management
    func loadScale(_ scale: Device) async {
        await deviceManager.loadScale(scale)
        
        if ScaleTypeHelper.determineScaleType(for: scale) == .bluetoothR4 && scale.isConnected == true {
            await usersManager.fetchUserList(for: scale)
        }
        
        modesManager.loadScaleModePreferences(for: scale)
        metricsManager.loadDisplayMetrics(for: scale)
    }
    
    /// Loads scale mode data specifically for the ScaleModesScreen
    func loadScaleModeData(for scale: Device) async {
        await deviceManager.loadScale(scale)
        modesManager.loadScaleModePreferences(for: scale)
    }
    
    /// Loads scale mode data with loading state management
    func loadScaleModeDataWithLoading(for scale: Device) async {
        // Set loading state to show full-screen loader
        await MainActor.run {
            state.ui.isLoading = true
            self.isLoading = true
        }
        
        await loadScaleModeData(for: scale)
        
        await MainActor.run {
            state.ui.isLoading = false
            self.isLoading = false
        }
    }

    func forceRefreshDeviceData() async {
        await dataManager.fetchScales()
        if let scale = state.device.scale {
            await deviceManager.refreshConnectionStatus()
            // Ensure the connection status is properly updated in the UI
            self.isDeviceConnected = state.device.isDeviceConnected
        }
    }

    // MARK: - Scale Operations
    func deleteScale(scaleId: String, onSuccess: @escaping () -> Void) async {
        state.ui.isLoading = true
        self.isLoading = true
        
        do {
            try await dataManager.deleteScale(scaleId: scaleId)
            notificationService.showToast(ToastModel(title: ToastStrings.deleted, message: ToastStrings.scaleDeleted))
            
            if state.device.scale?.id == scaleId {
                state.device.scale = nil
                self.scale = nil
            }
            onSuccess()
        } catch {
            state.ui.errorMessage = error.localizedDescription
            self.errorMessage = error.localizedDescription
        }
        
        state.ui.isLoading = false
        self.isLoading = false
    }

    func saveScaleName(_ newName: String) async {
        guard let scale = state.device.scale else {
            state.ui.errorMessage = ToastStrings.somethingWentWrong
            self.errorMessage = ToastStrings.somethingWentWrong
            return
        }
        
        state.ui.isLoading = true
        self.isLoading = true
        
        do {
            try await dataManager.saveScaleName(newName, for: scale)
            state.device.scale?.nickname = newName
            state.device.scale?.deviceName = newName
                self.scale?.nickname = newName
                self.scale?.deviceName = newName
                notificationService.showToast(ToastModel(title: ToastStrings.success, message: ToastStrings.scaleNameUpdated))
        } catch {
            state.ui.errorMessage = error.localizedDescription
                self.errorMessage = error.localizedDescription
            }
        
        state.ui.isLoading = false
        self.isLoading = false
    }

    func saveNickname() async {
        guard let scale = state.device.scale else { return }
        
        state.ui.isLoading = true
        self.isLoading = true
        
        do {
            try await dataManager.saveNickname(state.data.nicknameInput, for: scale)
            notificationService.showToast(ToastModel(title: ToastStrings.saved, message: ToastStrings.nicknameUpdated))
            state.device.scale?.nickname = state.data.nicknameInput
            self.scale?.nickname = state.data.nicknameInput
        } catch {
            state.ui.errorMessage = error.localizedDescription
            self.errorMessage = error.localizedDescription
        }
        
        state.ui.showNicknameAlert = false
        self.showNicknameAlert = false
        state.ui.isLoading = false
        self.isLoading = false
    }

    // MARK: - User Management
    func deleteUser(_ user: DeviceUser) async {
        guard let scale = state.device.scale else { return }
        
        state.ui.isLoadingUsers = true
        self.isLoadingUsers = true
        
        do {
            try await usersManager.deleteUser(user, from: scale)
            notificationService.showToast(ToastModel(title: ToastStrings.success, message: ToastStrings.userDeleted))
        } catch {
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.errorDeletingUser))
        }
        
        state.ui.isLoadingUsers = false
        self.isLoadingUsers = false
    }

    func saveUsers(newName: String) async {
        guard let scale = state.device.scale else { return }
        
        state.ui.isLoading = true
        self.isLoading = true
        
        do {
            try await usersManager.updateCurrentUserName(newName, for: scale)
            notificationService.showToast(ToastModel(title: ToastStrings.success, message: ToastStrings.userNameUpdated))
        } catch {
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.errorUpdatingUserName))
        }
        
        state.ui.isLoading = false
        self.isLoading = false
    }

    // MARK: - Mode Management
    func handleScaleModeSave() {
        guard let scale = state.device.scale else { return }
        
        state.ui.isLoading = true
        self.isLoading = true
        
        Task {
            do {
                try await modesManager.saveScaleModePreferences(for: scale)
                
                // Ensure the scale's R4ScalePreference is updated in the store state
                if let updatedScale = state.device.scale {
                    updatedScale.r4ScalePreference = scale.r4ScalePreference
                }
                
                notificationService.showToast(ToastModel(title: ToastStrings.success, message: ScaleModesStrings.preferencesSaved))
                
                // Refresh device data to ensure UI is updated
                await forceRefreshDeviceData()
                
                logger.log(level: .info, tag: "ScaleStore", message: "Scale mode preferences saved successfully")
            } catch {
                logger.log(level: .error, tag: "ScaleStore", message: "Failed to save scale mode preferences: \(error)")
                notificationService.showToast(ToastModel(title: ToastStrings.error, message: ScaleModesStrings.preferencesFailed))
            }
            
            state.ui.isLoading = false
            self.isLoading = false
        }
    }

    func handleWeightOnlyBannerAction()  {
        guard let scale = state.device.scale else { return }
        
        // Navigate to scale modes screen where user can change their mode
        logger.log(level: .info, tag: "ScaleStore", message: "Weight-only banner tapped - navigating to scale modes screen")
        
        // The navigation will be handled by the calling view (ScaleSettingsScreen)
        // This method is kept for logging and potential future functionality
    }

    // MARK: - Metrics Management
    func saveDisplayMetrics() async {
        guard let scale = state.device.scale else { return }
        
        state.ui.isLoading = true
        self.isLoading = true
        
        do {
            try await metricsManager.saveDisplayMetrics(for: scale)
            notificationService.showToast(ToastModel(title: ToastStrings.success, message: ToastStrings.displayMetricsSaved))
        } catch {
            notificationService.showToast(ToastModel(title: ToastStrings.error, message: ToastStrings.errorSavingDisplayMetrics))
        }
        
        state.ui.isLoading = false
        self.isLoading = false
    }

    func handleMetricsReorder(indices: IndexSet, newOffset: Int, isProgressMetrics: Bool) {
        metricsManager.handleMetricsReorder(indices: indices, newOffset: newOffset, isProgressMetrics: isProgressMetrics)
    }

    func updateDisplayMetricsValue() {
        metricsManager.updateDisplayMetricsValue()
       // self.displayMetricsValue = state.metrics.displayMetricsValue
    }

    func loadDisplayMetrics() {
        guard let scale = state.device.scale else { return }
        metricsManager.loadDisplayMetrics(for: scale)
        self.metrics = state.metrics.metrics
        self.progressMetrics = state.metrics.progressMetrics
       // self.displayMetricsValue = state.metrics.displayMetricsValue
    }

    // MARK: - UI Actions
    func changeNickname() {
        state.ui.showNicknameAlert = true
        self.showNicknameAlert = true
        state.data.nicknameInput = state.device.scale?.nickname ?? ""
        self.nicknameInput = state.device.scale?.nickname ?? ""
    }

    func setPasswordTouched() {
        dataManager.setPasswordTouched()
    }

    func openProductGuide(for sku: String) {
        // Set the URL directly in the data manager
        dataManager.openProductGuide(for: sku)
        
        // Ensure the URL is set in the state immediately
        let url = dataManager.state.browserURL
        state.data.browserURL = url
        self.browserURL = url
        
        // Show the browser
        state.ui.showTermsBrowser = true
        self.showTermsBrowser = true
    }

    func openBIAModel() {
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

    // MARK: - Alert Handlers
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
                AlertButtonModel(title: alertLang.ConnectWifiNetwork.goBackButton, type: .secondary) { _ in },
                AlertButtonModel(title: alertLang.ConnectWifiNetwork.exitButton, type: .primary) { _ in
                    onExit()
                }
            ]
        )
        notificationService.showAlert(alert)
    }

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

    func showDeleteUserAlert(for user: DeviceUser, onDelete: @escaping () -> Void) {
        let alert = AlertModel(
            title: alertLang.DeleteUserAlert.title(user.name),
            message: alertLang.DeleteUserAlert.message(user.name),
            buttons: [
                AlertButtonModel(title: alertLang.DeleteUserAlert.cancelButton, type: .secondary) { _ in },
                AlertButtonModel(title: alertLang.DeleteUserAlert.removeButton, type: .primary) { _ in
                    Task {
                        await self.deleteUser(user)
                        onDelete()
                    }
                }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    // MARK: - Helper Methods
    func determineConnectionStatus(for scale: Device) -> ScaleConnectionStatus {
        return deviceManager.determineConnectionStatus(for: scale)
    }

    func resetForm() {
        dataManager.resetForm()
        self.addScaleForm = state.data.addScaleForm
    }

    func getError() -> String? {
        return dataManager.getError()
    }

    func fetchScales() {
        Task {
            await dataManager.fetchScales()
            self.scales = state.data.scales
        }
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

    func updateCurrentUserNameInline(_ newName: String) async {
        // Legacy method - now handled by usersManager
        guard let scale = state.device.scale else { return }
        do {
            try await usersManager.updateCurrentUserName(newName, for: scale)
        } catch {
            logger.log(level: .error, tag: "ScaleStore", message: "Failed to update user name inline: \(error)")
        }
    }

    func updateCurrentUserName(_ newName: String) async {
        // Legacy method - now handled by usersManager
        guard let scale = state.device.scale else { return }
        do {
            try await usersManager.updateCurrentUserName(newName, for: scale)
        } catch {
            logger.log(level: .error, tag: "ScaleStore", message: "Failed to update user name: \(error)")
        }
    }

    func refreshUserList() async {
        // Legacy method - now handled by usersManager
        guard let scale = state.device.scale else { return }
        await usersManager.refreshUserList(for: scale)
    }

    func fetchUserList() async {
        guard let scale = state.device.scale else { return }
        await usersManager.fetchUserList(for: scale)
    }

    func loadScaleModePreferences() {
        guard let scale = state.device.scale else { return }
        modesManager.loadScaleModePreferences(for: scale)
    }

    func resetModeSettings() {
        modesManager.resetModeSettings()
    }

    func updateModeChangeTracking() {
        modesManager.updateModeChangeTracking()
        // Update public properties for backward compatibility
        self.modeValue = state.modes.modeValue
        self.isHeartRateEnabled = state.modes.isHeartRateEnabled
        self.originalModeValue = state.modes.originalModeValue
        self.originalHeartRateEnabled = state.modes.originalHeartRateEnabled
        self.hasModeChanges = state.modes.hasModeChanges
    }
    
    // MARK: - UI State Update Methods
    func updateModeValue(_ newValue: ScaleModes) {
        // Skip if value hasn't changed
        guard newValue != state.modes.modeValue else { return }
        
        // Update all states in one go to avoid multiple UI updates
        state.modes.modeValue = newValue
        modesManager.state.modeValue = newValue
        self.modeValue = newValue
        
        // Update change tracking
        modesManager.updateModeChangeTracking()
        self.hasModeChanges = state.modes.hasModeChanges
    }
    
    func updateHeartRateEnabled(_ newValue: Bool) {
        // Skip if value hasn't changed
        guard newValue != state.modes.isHeartRateEnabled else { return }
        
        // Update all states in one go to avoid multiple UI updates
        state.modes.isHeartRateEnabled = newValue
        modesManager.state.isHeartRateEnabled = newValue
        self.isHeartRateEnabled = newValue
        
        // Update change tracking
        modesManager.updateModeChangeTracking()
        self.hasModeChanges = state.modes.hasModeChanges
    }
    
    func updateMetrics(_ newMetrics: [ScaleMetricSetting]) {
        // Skip if metrics haven't changed
        guard newMetrics != state.metrics.metrics else { return }
        
        // Update all states in one go
        state.metrics.metrics = newMetrics
        metricsManager.state.metrics = newMetrics
        self.metrics = newMetrics
        
        // Update display value
        metricsManager.updateDisplayMetricsValue()
    }
    
    func updateProgressMetrics(_ newMetrics: [ScaleMetricSetting]) {
        // Skip if metrics haven't changed
        guard newMetrics != state.metrics.progressMetrics else { return }
        
        // Update all states in one go
        state.metrics.progressMetrics = newMetrics
        metricsManager.state.progressMetrics = newMetrics
        self.progressMetrics = newMetrics
        
        // Update display value
        metricsManager.updateDisplayMetricsValue()
    }

    func getDeviceInfo() async {
        await deviceManager.getDeviceInfo()
    }

    func getConnectedWifiSSID() async {
        await deviceManager.getConnectedWifiSSID()
    }

    func fetchWifiMacAddress() async {
        await deviceManager.fetchWifiMacAddress()
    }

    func shouldFetchWifiMacAddress(for scale: Device) -> Bool {
        return deviceManager.shouldFetchWifiMacAddress(for: scale)
    }
    
    func getWifiMacAddressString() -> String {
        return deviceManager.wifiMacAddressValue
    }
    
    func copyWifiMacAddress() {
        let macAddress = getWifiMacAddressString()
        UIPasteboard.general.string = macAddress
        notificationService.showToast(ToastModel(message: ToastStrings.copiedToClipboard))
    }

    // MARK: - WiFi Operations
    func connectToWifiNetwork(wifiName: String) {
        // This method is called from WifiCredentialsView
        // The actual WiFi connection logic should be handled by the appropriate setup flow
        logger.log(level: .info, tag: "ScaleStore", message: "WiFi connection requested for network: \(wifiName)")
        // TODO: Implement actual WiFi connection logic if needed
    }

    // MARK: - Callbacks
    var onNavigateToWifi: (() -> Void)?

    func handleSetupIncompleteBannerAction() {
        logger.log(level: .info, tag: "ScaleStore", message: "Setup incomplete banner tapped - navigating to WiFi setup")
        
        // Refresh WiFi status before navigating to ensure we have the latest state
        Task {
            await refreshWifiStatus()
        }
        
        onNavigateToWifi?()
        
        // Also refresh WiFi status after a delay to handle when user returns from WiFi setup
        DispatchQueue.main.asyncAfter(deadline: .now() + 5.0) {
            Task {
                await self.refreshWifiStatus()
            }
        }
    }

    // MARK: - Users Loader Management
    func showUsersLoader() {
        notificationService.showLoader(LoaderModel(text: LoaderStrings.loading))
    }

    func hideUsersLoader() {
        notificationService.dismissLoader()
    }

    func refreshConnectionStatus() async {
        if let scale = state.device.scale {
            await deviceManager.refreshConnectionStatus()
            // Ensure the connection status is properly updated in the UI
            self.isDeviceConnected = state.device.isDeviceConnected
        }
    }

    /// Refreshes the WiFi status and triggers UI update
    func refreshWifiStatus() async {
        if let scale = state.device.scale {
            await deviceManager.getConnectedWifiSSID()
            await modesManager.refreshWifiStatus(for: scale)
            
            // For SKU 0412, also check device info to get accurate WiFi configuration status
            if scale.sku == "0412" {
                await checkDeviceInfoAndWifiConfiguration()
            }
        }
    }
    
    /// Checks device info and WiFi configuration for scale SKU 0412
    func checkDeviceInfoAndWifiConfiguration() async {
        guard let scale = state.device.scale,
              scale.sku == "0412" else { return }
        
        do {
            let result = await bluetoothService.getDeviceInfo(for: scale)
            switch result {
            case .success(let deviceInfo):
                let isWifiConfigured = deviceInfo.isWifiConfigured
                // Update the scale's WiFi configuration status
                scale.isWifiConfigured = isWifiConfigured
                logger.log(level: .info, tag: "ScaleStore", message: "Device info retrieved - WiFi configured: \(isWifiConfigured)")
            case .failure(let error):
                logger.log(level: .error, tag: "ScaleStore", message: "Failed to get device info: \(error)")
            }
        }
    }
    
    /// Checks device info for all connected SKU 0412 scales to properly determine setup incomplete status
    func checkDeviceInfoForAllR4Scales() async {
        for scale in scales {
            // Only check device info for connected SKU 0412 scales
            if scale.sku == "0412" && scale.isConnected == true {
                do {
                    let result = await bluetoothService.getDeviceInfo(for: scale)
                    switch result {
                    case .success(let deviceInfo):
                        let isWifiConfigured = deviceInfo.isWifiConfigured
                        // Update the scale's WiFi configuration status
                        scale.isWifiConfigured = isWifiConfigured
                        logger.log(level: .info, tag: "ScaleStore", message: "Device info retrieved for \(scale.id) - WiFi configured: \(isWifiConfigured)")
                    case .failure(let error):
                        logger.log(level: .error, tag: "ScaleStore", message: "Failed to get device info for \(scale.id): \(error)")
                    }
                }
            }
        }
    }
}
