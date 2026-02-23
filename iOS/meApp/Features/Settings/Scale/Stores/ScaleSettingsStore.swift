//
//  ScaleSettingsStore.swift
//  meApp
//
//  Created by CursorAI on 05/08/25.

import Combine
import SwiftData
import SwiftUI

@MainActor
// swiftlint:disable:next type_body_length
final class ScaleSettingsStore: ObservableObject {
    @Injector var notificationService: NotificationHelperService
    @Injector var scaleService: ScaleService
    @Injector var bluetoothService: BluetoothService
    @Injector var logger: LoggerService
    @Injector var accountService: AccountService
    @Injector var permissionsService: PermissionsService
    private var cancellables = Set<AnyCancellable>()

    // Store the device ID for safe refetching from MainActor context
    private let scaleId: PersistentIdentifier
    private let scaleIdString: String

    // Cached scale for fallback when model not found in context
    private var cachedScale: Device?

    // Returns the cached scale - use refreshScale() to update from database
    var scale: Device {
        if let cached = cachedScale {
            return cached
        }
        // This should never happen since we set cachedScale in init
        logger.log(level: .error, tag: tag, message: "No cached scale available")
        return Device(id: "", accountId: "", deviceName: "Error", deviceType: "")
    }

    /// Refreshes the scale from the database. Call this before operations that need fresh data.
    func refreshScale() {
        // First try registeredModel for already-loaded models (fastest path)
        if let freshScale: Device = PersistenceController.shared.context.registeredModel(for: scaleId) {
            cachedScale = freshScale
            return
        }

        // If not in identity map, fetch from persistent store using FetchDescriptor
        let idToFind = scaleIdString
        let descriptor = FetchDescriptor<Device>(
            predicate: #Predicate<Device> { device in
                device.id == idToFind
            }
        )
        do {
            let results = try PersistenceController.shared.context.fetch(descriptor)
            if let freshScale = results.first {
                cachedScale = freshScale
                return
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to fetch scale from store: \(error.localizedDescription)")
        }

        // Keep existing cached value if fetch failed
        if cachedScale != nil {
            logger.log(level: .debug, tag: tag, message: "Using existing cached scale after refresh failed")
        }
    }

    @Published var isDeviceConnected: Bool = false
    @Published var connectedWifiSSID: String?
    @Published var isWifiConfigured: Bool = false
    @Published var wifiMacAddress: String?
    @Published var isFetchingWifiMacAddress: Bool = false
    // Users list
    @Published var usersList: [DeviceUser] = []
    @Published var isFetchingUsersList: Bool = false
    private var usersListFetchTask: Task<[DeviceUser], Never>?

    // Additional device info
    @Published var firmwareVersion: String?
    @Published var deviceInfo: DeviceInfo?
    @Published var isImpedanceSwitchedOnForSession: Bool = false
    @Published var isScaleImpedanceSwitchedOn: Bool = false
    @Published var isWeighOnlyModeEnabledByOthers: Bool = false
    @Published var displayName = ""

    // Cached preference values for safe access without relationship traversal
    // These are refreshed whenever the scale data changes
    @Published private(set) var cachedShouldMeasureImpedance: Bool = false
    @Published private(set) var cachedShouldMeasurePulse: Bool = false
    @Published private(set) var cachedPreferenceIsSynced: Bool = false

    // Prevent concurrent preference syncs and device info fetches
    private var isSyncingPreferences: Bool = false
    private var deviceInfoFetchTask: Task<Void, Never>?

    // MARK: - Product Manual Browser State
    @Published var showProductBrowser: Bool = false
    @Published var productURL: URL?
    let disconnectableScaleTypes: Set<ScaleSourceType> = [.btWifiR4, .bluetooth, .bluetoothScale, .lcbt, .lcbtScale]

    // Strings
    private let loaderLang = LoaderStrings.self
    private let toastLang = ToastStrings.self
    private let alertLang = AlertStrings.self
    private let appConstants = AppConstants.self

    private let tag = "ScaleSettingsStore"

    /// Creates a fresh store scoped to a single `Device` (scale) instance.
    /// - Parameter scale: The scale that this settings store should manage.
    init(scale: Device) {
        // Store both PersistentIdentifier and string ID for safe refetching
        self.scaleId = scale.persistentModelID
        self.scaleIdString = scale.id
        self.cachedScale = scale  // Cache the initial scale
        logger.log(level: .debug, tag: tag, message: "ScaleSettingsStore initialized for scale: \(scale.id)")

        // Initialize cached values from the scale
        refreshCachedValues()

        // Keep the local state in-sync with updates coming from `ScaleService`.
        scaleService.scalesPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] devices in
                guard let self = self else { return }
                // Check if our scale is in the updated list by ID
                guard devices.contains(where: { $0.id == self.scale.id }) else { return }
                let wasConnected = self.isDeviceConnected

                // Refresh all cached values from the fresh MainActor context model
                self.refreshCachedValues()

                Task { await self.getDeviceInfo() }
                // Trigger any post-connection logic once the device connects.
                if !wasConnected && self.isDeviceConnected {
                    self.logger.log(level: .debug, tag: self.tag, message: "Scale connected – fetch additional info if needed")
                    Task { await self.getDeviceInfo() }
                }
            }
            .store(in: &cancellables)
    }

    /// Refreshes all cached values from the current MainActor context model.
    /// This ensures all relationship access happens with models from the correct context.
    private func refreshCachedValues() {
        // Refresh the scale from database first
        refreshScale()
        let device = scale
        let isBluetoothOn = permissionsService.getPermissionState(.BLUETOOTH_SWITCH) == .ENABLED
        isDeviceConnected = (device.isConnected ?? false) && isBluetoothOn
        isWifiConfigured = device.isWifiConfigured ?? false

        // Safely access relationship properties - we're guaranteed to be on MainActor
        // with a model from the MainActor context
        if let preference = device.r4ScalePreference {
            displayName = preference.displayName
            cachedShouldMeasureImpedance = preference.shouldMeasureImpedance
            cachedShouldMeasurePulse = preference.shouldMeasurePulse
            cachedPreferenceIsSynced = preference.isSynced
        } else {
            displayName = accountService.activeAccount?.firstName ?? "Unknown"
            cachedShouldMeasureImpedance = false
            cachedShouldMeasurePulse = false
            cachedPreferenceIsSynced = false
        }
    }
    
    func refreshScaleData() {
        refreshCachedValues()
    }

    var isBodyMetrics: Bool {
        return cachedShouldMeasureImpedance
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
                AlertButtonModel(title: alertLang.DeleteScaleAlert.deleteButton, type: .danger) { _ in
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
    /// If a fetch is already in progress, waits for it to complete
    /// Returns the fetched users list
    func ensureUsersList() async -> [DeviceUser] {
        // If a fetch is already in progress, wait for it to complete and return its result
        if let existingTask = usersListFetchTask {
            return await existingTask.value
        }
        
        // Start a new fetch task
        isFetchingUsersList = true
        let task: Task<[DeviceUser], Never> = Task { [weak self] in
            guard let self = self else { return [] }
            defer {
                self.isFetchingUsersList = false
                self.usersListFetchTask = nil
            }
            return await self.fetchUsersList()
        }

        usersListFetchTask = task
        let fetchedUsers = await task.value
        return fetchedUsers
    }
    
    /// Syncs preference settings to the scale if there is a mismatch or unsynced state
    /// - Parameters:
    ///   - deviceInfo: The current device info from the scale
    private func syncPreferencesIfNeeded(deviceInfo: DeviceInfo) async {
        guard !isSyncingPreferences else { return }
        isSyncingPreferences = true
        defer { isSyncingPreferences = false }

        guard isDeviceConnected,
              let preference = scale.r4ScalePreference
        else {
            return
        }

        let impedanceSwitchState = deviceInfo.impedanceSwitchState ?? false
        /// Check if scale preferences need syncing
        let hasImpedanceMismatch = preference.shouldMeasureImpedance != impedanceSwitchState
        let needsSync = !preference.isSynced

        logger.log(
            level: .debug,
            tag: tag,
            message: "Preference sync check — mismatch: \(hasImpedanceMismatch), synced: \(preference.isSynced)"
        )

        guard needsSync else {
            logger.log(level: .debug, tag: tag, message: "Preferences already in sync")
            return
        }

        // Extract to DTO before async boundary (R9) — avoid mutating @Model after await
        let broadcastId = scale.broadcastIdString ?? "unknown"
        let deviceId = scale.id
        var dto = preference.toDTO()
        switch await bluetoothService.updateAccount(on: scale, preference: preference) {
        case .success:
            logger.log(level: .info, tag: tag, message: "Synced preference settings to scale \(broadcastId)")
            // Mark preference as synced via DTO to avoid @Model mutation after await (R9)
            dto.isSynced = true
            Task { @MainActor in
                do {
                    try await scaleService.updateScalePreference(deviceId, fromDTO: dto)
                } catch {
                    logger.log(level: .error, tag: tag, message: "Failed to update preference sync status: \(error)")
                }
            }
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to sync preference settings to scale \(broadcastId): \(error)")
        }
    }
    
    /// Checks device info and WiFi configuration for scale SKU 0412
    func getDeviceInfo() async {
        if let existingTask = deviceInfoFetchTask {
            await existingTask.value
            return
        }

        let task = Task { [weak self] in
            guard let self = self else { return }
            defer { self.deviceInfoFetchTask = nil }
            await self.fetchDeviceInfoInternal()
        }
        deviceInfoFetchTask = task
        await task.value
    }

    private func fetchDeviceInfoInternal() async {
        guard getScaleType() == .btWifiR4,
              isDeviceConnected == true else { return }
        
        let result = await bluetoothService.getDeviceInfo(for: scale)
        switch result {
        case .success(let deviceInfo):
            self.getConnectedWifiSSID()
            // Update published properties
            self.firmwareVersion = deviceInfo.firmwareRevision
            self.deviceInfo = deviceInfo
            self.isImpedanceSwitchedOnForSession = deviceInfo.sessionImpedanceSwitchState ?? false
            self.isScaleImpedanceSwitchedOn = deviceInfo.impedanceSwitchState ?? false
            // Update Wi-Fi configured flag if available
            if let wifiConfigured = deviceInfo.isWifiConfigured {
                self.isWifiConfigured = wifiConfigured
            }
            self.isWeighOnlyModeEnabledByOthers = !(deviceInfo.impedanceSwitchState ?? false) && cachedShouldMeasureImpedance
            logger.log(level: .info, tag: tag, message: "Device info retrieved – firmware: \(deviceInfo.firmwareRevision ?? "n/a")", data: deviceInfo)
            
            // Sync preference settings to scale if needed (impedance mismatch or unsynced preferences)
            await syncPreferencesIfNeeded(deviceInfo: deviceInfo)
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to get device info: \(error)")
        }
    }
    
    // MARK: - Scale Operations
    private func deleteScale(scaleId: String) async -> Bool {
        notificationService.showLoader(LoaderModel(text: LoaderStrings.deletingScale))
        var isSuccess = false
        defer {
            Task { @MainActor in
                _ = await bluetoothService.resyncAndScan()
                bluetoothService.isSetupInProgress = false
            }
        }
        do {
            // Pause scans and mark setup in progress to avoid race with ongoing reconnect/pairing
            bluetoothService.isSetupInProgress = true
            if let scaleType = getScaleType(), disconnectableScaleTypes.contains(scaleType), let broadcastId = scale.broadcastIdString {
                // Ensure the user slot on the scale is deleted as well (aligns with Android behavior)
                let deletionTask = Task { @MainActor in
                    _ = await bluetoothService.deleteCurrentUserFromScaleIfPossible(scale, disconnect: false)
                }
                // Give BLE a brief moment to process deletion; if it hangs, cancel and proceed
                // Note: Canceling the deletion task does not forcibly stop the underlying async operation.
                // This is a 'fire-and-forget with timeout' pattern; the operation may still complete after cancellation.
                try? await Task.sleep(nanoseconds: UInt64(AppConstants.TimeoutsAndRetention.scaleDeletionGraceTimeoutNs))
                deletionTask.cancel()
                // Disconnects only the scale being deleted and prevents it from reconnecting.
                _ = await bluetoothService.disconnectDevice(broadcastId: broadcastId)
            }
            try await scaleService.deleteDevice(scaleId, showToast: true)
            bluetoothService.isSetupInProgress = false
            await scaleService.pushLocalChangesToServer()
            await scaleService.syncAllScalesWithRemote()           
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
            if getScaleType() == .btWifiR4 && isDeviceConnected == true && isWifiConfigured {
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
    /// Returns the fetched users list and updates the published property
    private func fetchUsersList() async -> [DeviceUser] {
        guard getScaleType() == .btWifiR4 && isDeviceConnected else {
            logger.log(level: .error, tag: tag, message: "Cannot fetch users list - scale not connected or not R4 type")
            usersList = []
            return []
        }
        
        let result = await bluetoothService.getScaleUserList(for: scale)
        switch result {
        case .success(let users):
            usersList = users
            logger.log(level: .info, tag: tag, message: "Successfully fetched \(users.count) users from scale")
            return users
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to fetch users list: \(error.localizedDescription)", data: error)
            usersList = []
            return []
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
            
            // Show different toast messages based on who enabled weight-only mode
            let toastMessage = isWeighOnlyModeEnabledByOthers ? 
                WeightOnlyModeStrings.temporaryOverride : 
                ScaleModesStrings.bodyMetricsEnabled
            
            notificationService.showToast(ToastModel(title: toastLang.success, message: toastMessage))
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to enable body metrics: \(error.localizedDescription)", data: error)
        }
    }

    func setSessionImpedance(_ enabled: Bool) async {
        guard isDeviceConnected else { return }
        let res = await bluetoothService.updateSetting(on: scale, settings: [DeviceSetting(key: "SESSION_IMPEDANCE", value: .bool(enabled))])
        switch res {
        case .success:
            isImpedanceSwitchedOnForSession = enabled
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to update session impedance: \(error.localizedDescription)")
        }
    }
}
