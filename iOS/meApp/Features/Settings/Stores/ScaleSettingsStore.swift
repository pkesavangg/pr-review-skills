//
//  ScaleSettingsStore.swift
//  meApp
//
//  Created by CursorAI on 05/08/25.

import SwiftUI
import Combine

@MainActor
final class ScaleSettingsStore: ObservableObject {
    @Injector var notificationService: NotificationHelperService
    @Injector var scaleService: ScaleService
    @Injector var bluetoothService: BluetoothService
    @Injector var logger: LoggerService
    @Injector var accountService: AccountService
    private var cancellables = Set<AnyCancellable>()
    
    @Published var scale: Device
    @Published var isDeviceConnected: Bool = false
    @Published var connectedWifiSSID: String?
    @Published var isWifiConfigured: Bool = false
    @Published var wifiMacAddress: String? = nil
    @Published var isFetchingWifiMacAddress: Bool = false
    // Users list
    @Published var usersList: [DeviceUser] = []
    @Published var isFetchingUsersList: Bool = false
    
    // Additional device info
    @Published var firmwareVersion: String? = nil
    @Published var isImpedanceSwitchedOnForSession: Bool = false
    @Published var isScaleImpedanceSwitchedOn: Bool = false
    @Published var isWeighOnlyModeEnabledByOthers: Bool = false
    @Published var displayName = ""
    
    // MARK: - Product Manual Browser State
    @Published var showProductBrowser: Bool = false
    @Published var productURL: URL? = nil
    
    
    // Strings
    private let loaderLang = LoaderStrings.self
    private let toastLang = ToastStrings.self
    private let alertLang = AlertStrings.self
    private let appConstants = AppConstants.self
    
    private let tag = "ScaleSettingsStore"
    
    /// Creates a fresh store scoped to a single `Device` (scale) instance.
    /// - Parameter scale: The scale that this settings store should manage.
    init(scale: Device) {
        self.scale = scale
        logger.log(level: .debug, tag: tag, message: "ScaleSettingsStore initialized for scale: \(scale.id)")
        
        // Keep the local `scale` model in-sync with updates coming from `ScaleService`.
        scaleService.scalesPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] devices in
                guard let self = self else { return }
                let current = self.scale
                print("Updating required categories based on scales:", devices.map({ $0.r4ScalePreference?.shouldMeasureImpedance}))
                guard let updated = devices.first(where: { $0.id == current.id }) else { return }
                
                let wasConnected = self.isDeviceConnected
                self.scale = updated
                self.isDeviceConnected = updated.isConnected ?? false
                self.isWifiConfigured = updated.isWifiConfigured ?? false
                self.displayName = updated.r4ScalePreference?.displayName ?? accountService.activeAccount?.firstName ?? "Unknown"
                // Trigger any post-connection logic once the device connects.
                if !wasConnected && self.isDeviceConnected {
                    self.logger.log(level: .debug, tag: self.tag, message: "Scale connected – fetch additional info if needed")
                    Task { await self.getDeviceInfo() }
                }
            }
            .store(in: &cancellables)
    }
    
    var isBodyMetrics: Bool {
        return self.scale.r4ScalePreference?.shouldMeasureImpedance ?? false
    }
    
    /// Opens the product guide/manual for the given SKU inside the in-app browser.
    /// - Parameter sku: The product SKU string.
    func openProductGuide(for sku: String) {
        guard let url = URL(string: "\(appConstants.Product.baseURL)/\(sku)") else { return }
        productURL = url
        showProductBrowser = true
    }
    
    // MARK: - Alert Handlers
    func handleEnableBodyMetrics() {
        let alert = AlertModel(
            title: alertLang.EnableBodyMetricsAlert.title,
            message: alertLang.EnableBodyMetricsAlert.message,
            buttons: [
                AlertButtonModel(title: alertLang.EnableBodyMetricsAlert.enableButton, type: .primary) { _ in
                    Task { [weak self] in
                        await self?.enableBodyMetricsForSession()
                    }
                },
                AlertButtonModel(title: alertLang.EnableBodyMetricsAlert.cancelButton, type: .secondary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    func handleScaleDelete(scaleId: String, onSuccess: @escaping () -> Void) {
        let alert = AlertModel(
            title: alertLang.DeleteScaleAlert.title,
            message: alertLang.DeleteScaleAlert.message,
            buttons: [
                AlertButtonModel(title: alertLang.DeleteScaleAlert.deleteButton, type: .primary) { _ in
                    Task { [weak self] in
                        guard let self = self else { return }
                        let success = await self.deleteScale(scaleId: scaleId)
                        if success {
                            onSuccess()
                        }
                    }
                },
                AlertButtonModel(title: alertLang.DeleteScaleAlert.cancelButton, type: .secondary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }
    
    func ensureWifiMacAddress() async {
        guard wifiMacAddress == nil else { return }
        guard !isFetchingWifiMacAddress else { return }
        isFetchingWifiMacAddress = true
        await fetchWifiMacAddress()
        isFetchingWifiMacAddress = false
    }
    
    /// Ensures users list is fetched if not already available
    func ensureUsersList() async {
        guard !isFetchingUsersList else { return }
        isFetchingUsersList = true
        await fetchUsersList()
        isFetchingUsersList = false
    }
    
    /// Checks device info and WiFi configuration for scale SKU 0412
    func getDeviceInfo() async {
        guard getScaleType() == .btWifiR4,
              isDeviceConnected == true else { return }
        
        let result = await bluetoothService.getDeviceInfo(for: scale)
        switch result {
        case .success(let deviceInfo):
            if (self.connectedWifiSSID == nil) {
                self.getConnectedWifiSSID();
            }
            // Update published properties
            self.firmwareVersion = deviceInfo.firmwareRevision
            self.isImpedanceSwitchedOnForSession = deviceInfo.sessionImpedanceSwitchState ?? false
            self.isScaleImpedanceSwitchedOn = deviceInfo.impedanceSwitchState ?? false
            // Update Wi-Fi configured flag if available
            if let wifiConfigured = deviceInfo.isWifiConfigured {
                self.isWifiConfigured = wifiConfigured
            }
            self.isWeighOnlyModeEnabledByOthers = !(deviceInfo.impedanceSwitchState ?? false) && (scale.r4ScalePreference?.shouldMeasureImpedance ?? false)
            logger.log(level: .info, tag: tag, message: "Device info retrieved – firmware: \(deviceInfo.firmwareRevision ?? "n/a")")
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to get device info: \(error)")
        }
    }
    
    // MARK: - Scale Operations
    private func deleteScale(scaleId: String) async -> Bool {
        notificationService.showLoader(LoaderModel(text: LoaderStrings.deletingScale))
        var isSuccess = false
        do {
            if scale.isConnected == true && getScaleType() == .btWifiR4, let broadcastId = scale.broadcastIdString {
                let _ = await bluetoothService.deleteDevice(scale, disconnect: false)
                let _ = await bluetoothService.disconnectDevice(broadcastId: broadcastId)
            }
            try await scaleService.deleteDevice(scaleId, showToast: true)
            await scaleService.pushLocalChangesToServer()
            notificationService.showToast(ToastModel(title: ToastStrings.deleted, message: ToastStrings.scaleDeleted))
            isSuccess = true
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to delete scale: \(error.localizedDescription)", data: error)
            notificationService.showToast(ToastModel(title: ToastStrings.errorDeletingScale, message: ToastStrings.restartApp))
        }
        notificationService.dismissLoader()
        return isSuccess
    }
    
    private func getConnectedWifiSSID() {
        Task {
            if (getScaleType() == .btWifiR4 && isDeviceConnected == true && isWifiConfigured) {
                let res = await bluetoothService.getConnectedWifiSSID(broadcastId: scale.broadcastIdString ?? "")
                switch res {
                case .success(let ssid):
                    self.connectedWifiSSID = ssid
                case .failure(let error):
                    logger.log(level: .error, tag: tag, message: "SSID fetch failed \(error)", data: error)
                }
            } else {
                self.connectedWifiSSID = nil
            }
        }
    }
    
    private func fetchWifiMacAddress() async {
        let res = await bluetoothService.getWifiMacAddress(for: scale)
        switch res {
        case .success(let mac):
            wifiMacAddress = mac
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to fetch WiFi MAC address: \(error.localizedDescription)", data: error)
            wifiMacAddress = nil
        }
    }
    
    /// Fetches the users list from the scale via Bluetooth
    private func fetchUsersList() async {
        guard getScaleType() == .btWifiR4 && isDeviceConnected else {
            logger.log(level: .error, tag: tag, message: "Cannot fetch users list - scale not connected or not R4 type")
            return
        }
        
        let result = await bluetoothService.getScaleUserList(for: scale)
        switch result {
        case .success(let users):
            usersList = users
            logger.log(level: .info, tag: tag, message: "Successfully fetched \(users.count) users from scale")
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to fetch users list: \(error.localizedDescription)", data: error)
            usersList = []
        }
    }
    
    private func getScaleType() -> ScaleSourceType? {
        guard let scaleType = scale.bathScale?.scaleType else { return nil }
        return ScaleSourceType(rawValue: scaleType)
    }
    
    /// Enables body metrics for one session by calling the Bluetooth service
    private func enableBodyMetricsForSession() async {
        guard isDeviceConnected else {
            logger.log(level: .error, tag: tag, message: "Cannot enable body metrics - device not connected")
            return
        }
        
        let result = await bluetoothService.updateWeightOnlyMode(on: scale)
        switch result {
        case .success:
            logger.log(level: .info, tag: tag, message: "Successfully enabled body metrics for session")
            notificationService.showToast(ToastModel(title: toastLang.success, message: ScaleModesStrings.bodyMetricsEnabled))
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to enable body metrics: \(error.localizedDescription)", data: error)
        }
    }
}
