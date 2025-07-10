//
//  BluetoothService.swift
//  iOS Bluetooth integration layer for GGBluetoothSwiftPackage
//
//  Created by AI Assistant
//
//  This file provides a production-ready BluetoothService for iOS apps, wrapping the GGBluetoothSwiftPackage SDK and translating between app models and SDK models. It uses Combine for reactive updates and async/await for async plugin calls.
//

import Foundation
import Combine
import GGBluetoothSwiftPackage
// For mapping device metadata
import SwiftData

/**
 * Comprehensive implementation of `BluetoothServiceProtocol` backed by the `GGBluetoothSwiftPackage` SDK.
 *
 * This service provides complete Bluetooth functionality for smart scales, including:
 * - Device scanning and pairing
 * - Wi-Fi configuration
 * - Firmware updates
 * - Data synchronization
 * - User management
 * - Settings configuration
 *
 * - NOTE: The static `shared` property is retained for legacy compatibility, but should be phased out in favor of DI.
 */
@MainActor
final class BluetoothService: ObservableObject, BluetoothServiceProtocol {
    // MARK: - Singleton (Legacy Only)
    /// Legacy singleton for compatibility. Prefer dependency injection for new code.
    static let shared = BluetoothService(accountService: AccountService.shared,
                                         scaleService: ScaleService.shared,
                                         entryService: EntryService.shared,
                                         logger: LoggerService.shared)
    
    // MARK: - Published State
    /// Indicates if the scale discovered modal can be shown for newly discovered scales.
    @Published private(set) var canShowScaleDiscoveredModal: Bool = true
    
    /// Indicates whether a setup is currently in progress.
    @Published var isSetupInProgress: Bool = false
    
    // MARK: - Public Publishers
    /// Publisher for unified device discovery events containing device, protocol type, and isNew flag.
    var deviceDiscoveredPublisher: AnyPublisher<DeviceDiscoveryEvent, Never> {
        deviceDiscoveredSubject.eraseToAnyPublisher()
    }
    /// Publisher for device metadata updates.
    var deviceInfoUpdatedPublisher: AnyPublisher<DeviceInfo, Never> {
        deviceInfoUpdatedSubject.eraseToAnyPublisher()
    }
    /// Publisher for weight-only mode alert visibility.
    var showWeightOnlyModeAlertPublisher: AnyPublisher<Bool, Never> {
        showWeightOnlyModeAlertSubject.eraseToAnyPublisher()
    }
    /// Publisher for new entry events.
    var newEntryReceivedPublisher: AnyPublisher<Entry, Never> {
        newEntryReceivedSubject.eraseToAnyPublisher()
    }
    /// Publisher for firmware update progress.
    var firmwareUpdateProgressPublisher: AnyPublisher<FirmwareUpdateStatus, Never> {
        firmwareUpdateProgressSubject.eraseToAnyPublisher()
    }
    
    // MARK: - Subjects for Scale Discovery
    private let deviceDiscoveredSubject = PassthroughSubject<DeviceDiscoveryEvent, Never>()
    private let newEntryReceivedSubject = PassthroughSubject<Entry, Never>()
    private let deviceInfoUpdatedSubject = PassthroughSubject<DeviceInfo, Never>()
    private let showWeightOnlyModeAlertSubject = PassthroughSubject<Bool, Never>()
    private let firmwareUpdateProgressSubject = PassthroughSubject<FirmwareUpdateStatus, Never>()
    
    // MARK: - Private Properties
    private var cancellables = Set<AnyCancellable>()
    private var activeAccount: Account?
    private var isSmartScanStarted = false
    private var bluetoothScales: [Device] = []
    private var skipDevices: [String] = []
    private var isWeightOnlyModeAlertDismissed = false
    private var lastProfileUpdateAccountId: String?
    
    // MARK: - Dependencies
    private let accountService: AccountService
    private let scaleService: ScaleServiceProtocol
    private let entryService: EntryServiceProtocol
    private let logger: LoggerService
    private let ggBleSDK = GGBluetoothSwiftPackage.shared
    private let tag = "BluetoothService"
    
    // MARK: - Initialization
    /**
     Initializes the BluetoothService with all required dependencies.
     - Parameters:
     - accountService: The account service dependency.
     - scaleService: The scale service dependency.
     - entryService: The entry service dependency.
     - logger: The logger service dependency.
     */
    init(
        accountService: AccountService,
        scaleService: ScaleServiceProtocol,
        entryService: EntryServiceProtocol ,
        logger: LoggerService
    ) {
        self.accountService = accountService
        self.scaleService = scaleService
        self.entryService = entryService
        self.logger = logger
        setupSubscriptions()
    }
    
    // MARK: - Setup
    private func setupSubscriptions() {
        // Subscribe to scale changes
        scaleService.scalesPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] scales in
                Task { await self?.handleScalesUpdate(scales) }
            }
            .store(in: &cancellables)
        
    }
    
    /**
     Initializes the Bluetooth service and subscribes to account changes.
     */
    func initialize() {
        accountService.$activeAccount
            .receive(on: DispatchQueue.main)
            .sink { [weak self] account in
                Task { await self?.handleAccountUpdate(account) }
            }
            .store(in: &cancellables)
    }
    
    private func handleScalesUpdate(_ scales: [Device]?) async {
        guard let scales = scales, !scales.isEmpty else {
            syncDevices([])
            bluetoothScales = []
            return
        }
        let allowedTypes: [ScaleSourceType] = [
            .bluetooth,
            .bluetoothScale,
            .lcbt,
            .lcbtScale,
            .btWifiR4
        ]
        let filteredScales = scales.filter { scale in
            if let scaleTypeRaw = scale.bathScale?.scaleType {
                let scaleType = ScaleSourceType(rawValue: scaleTypeRaw) ?? .bluetoothScale
                return allowedTypes.contains(scaleType) || scale.sku == "0412"
            }
            return scale.sku == "0412"
        }
        // Disconnect deleted scales
        await disconnectDeletedScales(currentScales: bluetoothScales, newScales: filteredScales)
        bluetoothScales = filteredScales
        
        if !isSetupInProgress {
            syncDevices(self.bluetoothScales)
        }
    }
    
    private func handleAccountUpdate(_ account: Account?) async {
        if let account = account {
            self.activeAccount = account
            if !isSmartScanStarted {
                await scan()
            }
            // do {
            //     _ = try await updateUserProfileForR4Scales()
            // } catch {
            //   logger.log(level: .error, tag: tag, message: BluetoothServiceError.updateProfileFailed(error).localizedDescription)
            // }
        } else if isSmartScanStarted {
            stopScan()
        }
    }
    
    // MARK: - BluetoothServiceProtocol Implementation
    
    /**
     Stops all ongoing Bluetooth operations and scanning.
     */
    func stopScan() {
        ggBleSDK.stop()
        isSmartScanStarted = false
    }
    
    /**
     Clears all devices from the underlying Bluetooth plugin / cache.
     */
    func clearDevices() {
        ggBleSDK.clearDevices()
    }
    
    // MARK: - Scanning & Pairing
    /**
     Starts a smart scan for Bluetooth devices. Throws if scan cannot be started.
     */
    func scan() async {
        guard activeAccount != nil else {
            return
        }
        do {
            try await startSmartScan()
        } catch {
            logger.log(level: .error, tag: tag, message: BluetoothServiceError.scanFailed(error).localizedDescription)
        }
    }
    
    
    /**
     Forces a re-sync of locally stored devices with the Bluetooth plugin and re-starts scanning.
     - Returns: Result<Void, BluetoothServiceError>
     */
    func resyncAndScan() async -> Result<Void, BluetoothServiceError> {
        do {
            try await scaleService.updateAllScalesStatus(nil)
            clearScaleDiscoveredInfo()
            try await scaleService.syncDevices(tempDevice: nil)
            syncDevices(bluetoothScales)
            return .success(())
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.resyncFailed(error))
        }
    }
    
    /**
     Pauses the current smart scan without tearing down the session.
     */
    func pauseSmartScan() {
        ggBleSDK.pauseScan()
    }
    
    /**
     Resumes a previously paused smart scan.
     - Parameter clearOnlyPairing: When true, clears only pairing-mode devices before resuming.
     */
    func resumeSmartScan(clearOnlyPairing: Bool) {
        ggBleSDK.resumeScan(clearOnlyPairing)
    }
    
    /**
     Performs a dedicated scan intended for scale pairing.
     */
    func scanForPairing() {
        ggBleSDK.scanForPairing()
    }
    
    // MARK: - Device Sync & CRUD
    /**
     Synchronises the provided device list with the Bluetooth plugin.
     - Parameter devices: The devices to sync. Passing an empty array clears the list.
     */
    func syncDevices(_ devices: [Device]) {
        if bluetoothScales.isEmpty {
            clearDevices()
            return
        }
        let ggDevices = bluetoothScales.map { device in
            GGBTDevice(
                name: device.deviceName ?? "",
                broadcastId: device.broadcastIdString ?? "",
                password: device.password,
                token: device.token,
                userNumber: Int(device.userNumber ?? "0"),
                preference: nil,
                syncAllData: nil,
                batteryLevel: 0,
                protocolType: device.protocolType ?? "",
                macAddress: device.mac ?? ""
            )
        }
        
        ggBleSDK.syncDevices(ggDevices)
    }
    
    /**
     Adds a newly discovered scale to persistent storage and returns the saved model.
     - Returns: Result<Device, BluetoothServiceError>
     */
    func addNewDevice(_ scale: Device, metaData deviceDetails: DeviceMetaData?) async -> Result<Device, BluetoothServiceError> {
        do {
            guard let userId = activeAccount?.accountId else {
                throw BluetoothServiceError.noActiveAccount
            }
            let scaleToSave = scale
            scaleToSave.accountId = userId
            scaleToSave.createdAt = DateTimeTools.getCurrentDatetimeIsoString()
            scaleToSave.nickname = scale.nickname ?? "Bluetooth Smart Scale"
            var metaData = deviceDetails
            let scaleType = scale.bathScale?.scaleType ?? ""
            if metaData == nil && (scaleType == ScaleSourceType.btWifiR4.rawValue || scaleType == ScaleSourceType.bluetooth.rawValue) {
                let deviceInfoResult = await getDeviceInfo(for: scale)
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
                    metaData = DeviceMetaData(from: dto)
                    if scaleType == ScaleSourceType.btWifiR4.rawValue {
                        let wifiMacResult = await getWifiMacAddress(for: scale)
                        switch wifiMacResult {
                        case .success(let wifiMacAddress):
                            scaleToSave.wifiMac = wifiMacAddress
                        case .failure(let error):
                            logger.log(level: .error, tag: tag, message: "Failed to get WiFi MAC address: \(error.localizedDescription)")
                        }
                    }
                case .failure(let error):
                    logger.log(level: .error, tag: tag, message: "Failed to get device info: \(error.localizedDescription)")
                }
            }
            scaleToSave.metaData = metaData
            let savedScale = try await scaleService.createDevice(scaleToSave)
            try await scaleService.syncDevices(tempDevice: nil)
            return .success(savedScale)
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }
    
    /**
     Confirms a smart pairing operation with the specified device.
     - Returns: Result<UserCreationResponse, BluetoothServiceError>
     */
    func confirmSmartPair(device: Device, token: String, displayName: String, userNumber: Int?) async -> Result<UserCreationResponse, BluetoothServiceError> {
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            ggDevice.token = token
            ggDevice.userNumber = userNumber ?? 0
            let preference = GGDevicePreference(displayName: displayName)
            ggDevice.preference = preference
            let result = await ggBleSDK.confirmPair(ggDevice)
            return .success(UserCreationResponse(sdkType: result))
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }
    
    /**
     Deletes a scale from storage (and optionally from the physical device).
     - Returns: Result<UserDeletionResponse, BluetoothServiceError>
     */
    func deleteDevice(_ device: Device, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> {
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            let result = await ggBleSDK.deleteUser(ggDevice, canDisconnect: disconnect)
            return .success(UserDeletionResponse(sdkType: result))
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }
    
    // MARK: - Wi-Fi Configuration
    /**
     Retrieves the available Wi-Fi networks from the given device.
     - Returns: Result<[WifiDetails], BluetoothServiceError>
     */
    func getWifiList(for device: Device) async -> Result<[WifiDetails], BluetoothServiceError> {
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            let result = await ggBleSDK.getWifiList(ggDevice)
            return .success(result.wifi.map { WifiDetails(macAddress: $0.macAddress, ssid: $0.ssid, rssi: $0.rssi, password: $0.password) })
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }
    
    /**
     Configures Wi-Fi on the given device.
     - Returns: Result<Void, BluetoothServiceError>
     */
    func setupWifi(on device: Device, config: WifiConfig) async -> Result<Void, BluetoothServiceError> {
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            let ggConfig = GGBTWifiConfig(ssid: config.ssid, password: config.password ?? "")
            _ = await ggBleSDK.setupWifi(ggDevice, ggConfig)
            return .success(())
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }
    
    /**
     Cancels a pending Wi-Fi configuration.
     - Returns: Result<Void, BluetoothServiceError>
     */
    func cancelWifi(on: Device) async -> Result<Void, BluetoothServiceError> {
        do {
            guard let ggDevice = mapToGGBTDevice(on) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            ggBleSDK.cancelWifi(ggDevice)
            return .success(())
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }
    
    /**
     Retrieves the currently connected Wi-Fi SSID for an R4 scale.
     - Returns: Result<String, BluetoothServiceError>
     */
    func getConnectedWifiSSID(broadcastId: String) async -> Result<String, BluetoothServiceError> {
        let ggDevice = mapToGGBTDevice(broadcastId)
        let ssid = await ggBleSDK.getConnectedWifiSSID(ggDevice)
        return .success(ssid)
    }
    
    /**
     Retrieves the Wi-Fi MAC address for an R4 scale.
     - Returns: Result<String, BluetoothServiceError>
     */
    func getWifiMacAddress(for device: Device) async -> Result<String, BluetoothServiceError> {
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            let mac = await ggBleSDK.getWifiMacAddress(ggDevice)
            return .success(mac)
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }
    
    // MARK: - Settings & Firmware
    /**
     Updates a list of settings on the device.
     - Returns: Result<Void, BluetoothServiceError>
     */
    func updateSetting(on device: Device, settings: [DeviceSetting]) async -> Result<Void, BluetoothServiceError> {
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            let ggSettings = settings.map { setting in
                GGBTSetting(
                    key: GGBTSettingType(rawValue: setting.key) ?? .SESSION_IMPEDANCE,
                    value: setting.value.toGGBTSettingValue()
                )
            }
            ggBleSDK.updateSetting(ggDevice, ggSettings)
            return .success(())
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }
    
    /**
     Initiates a firmware update on the device.
     - Returns: Result<Void, BluetoothServiceError>
     */
    func updateFirmware(on device: Device, timestamp: UInt32) async -> Result<Void, BluetoothServiceError> {
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            ggBleSDK.startFirmwareUpdate(ggDevice, timestamp)
            let initialStatus = FirmwareUpdateStatus(progress: 0.0, isComplete: false)
            firmwareUpdateProgressSubject.send(initialStatus)
            return .success(())
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }
    
    /**
     Clears stored data on the device (e.g., history, user).
     - Returns: Result<Void, BluetoothServiceError>
     */
    func clearData(on device: Device, dataType: DeviceClearType) async -> Result<Void, BluetoothServiceError> {
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            let sdkType: ClearDataType = {
                switch dataType {
                case .userData: return .ACCOUNT
                case .history: return .HISTORY
                case .all: return .ALL
                }
            }()
            _ = await ggBleSDK.clearData(ggDevice, sdkType)
            return .success(())
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }
    
    // MARK: - Profile & Account
    /**
     Updates the user profile (height, weight, age, etc.) on all connected R4 scales.
     - Returns: Result<Bool, BluetoothServiceError>
     */
    func updateUserProfileForR4Scales() async -> Result<Bool, BluetoothServiceError> {
        do {
            guard let account = activeAccount else {
                throw BluetoothServiceError.noActiveAccount
            }
            guard let userProfile = await getProfileInfo(from: account) else {
                throw BluetoothServiceError.noProfileInfo
            }
            let success = await ggBleSDK.updateProfile(profile: userProfile)
            if !success {
                throw BluetoothServiceError.updateProfileFailed(BluetoothServiceError.notImplemented)
            }
            return .success(success)
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }
    
    /**
     Updates account-specific preferences (display name, metrics, etc.) on the device.
     - Returns: Result<UserCreationResponse, BluetoothServiceError>
     */
    func updateAccount(on device: Device, preference: R4ScalePreference) async -> Result<UserCreationResponse, BluetoothServiceError> {
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            ggDevice.preference = mapToGGPreference(preference)
            let result = await ggBleSDK.updateAccount(ggDevice)
            return .success(UserCreationResponse(sdkType: result))
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }
    
    /**
     Retrieves the list of users stored on the scale (R4 only).
     - Returns: Result<[DeviceUser], BluetoothServiceError>
     */
    func getScaleUserList(for device: Device) async -> Result<[DeviceUser], BluetoothServiceError> {
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            let users = await ggBleSDK.getUsers(ggDevice)
            let deviceUsers = users.user.map { user in
                DeviceUser(
                    name: user.name,
                    token: user.token,
                    lastActive: user.lastActive,
                    isBodyMetricsEnabled: user.isBodyMetricsEnabled
                )
            }
            return .success(deviceUsers)
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }
    
    // MARK: - Device Info
    /**
     Retrieves generic device information (model, serial, firmware, …).
     - Returns: Result<DeviceInfo, BluetoothServiceError>
     */
    func getDeviceInfo(for device: Device) async -> Result<DeviceInfo, BluetoothServiceError> {
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            let details = await ggBleSDK.getDeviceInfo(ggDevice)
            return .success(DeviceInfo(sdk: details))
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }
    
    /**
     Retrieves live measurement data while a user is on the scale.
     - Returns: Result<MeasurementLiveData, BluetoothServiceError>
     */
    func getMeasurementLiveData(broadcastId: String) async -> Result<MeasurementLiveData, BluetoothServiceError> {
        let ggDevice = mapToGGBTDevice(broadcastId)
        _ = await ggBleSDK.getMeasurementLiveData(ggDevice)
        let liveData = MeasurementLiveData(weight: 0)
        return .success(liveData)
    }
    
    
    /**
     Triggers the in-app alert required when weight-only mode is enabled by another user.
     - Returns: Result<Void, BluetoothServiceError>
     */
    func updateWeightOnlyMode(on connectedScale: Device?) async -> Result<Void, BluetoothServiceError> {
        do {
            var scales: [Device] = []
            if let connectedScale = connectedScale {
                scales.append(connectedScale)
            } else {
                scales = bluetoothScales.filter { scale in
                    (scale.isConnected ?? false)
                }
            }
            for scale in scales {
                _ = await updateSetting(on: scale, settings: [
                    DeviceSetting(key: "SESSION_IMPEDANCE", value: DeviceSettingValue.bool(true))
                ])
            }
            return .success(())
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }
    
    
    func clearScaleDiscoveredInfo() {
        skipDevices.removeAll()
    }
    
    // MARK: - Private Helper Methods
    
    private func startSmartScan() async throws {
        guard let activeAccount = activeAccount else {
            throw BluetoothServiceError.noActiveAccount
        }
        guard let accountData = await getProfileInfo(from: activeAccount) else {
            throw BluetoothServiceError.noProfileInfo
        }
        
        // Use the callback-based scan method properly
        ggBleSDK.scan(.WEIGHT_GURUS, accountData) { [weak self] result in
            Task { @MainActor in
                switch result {
                case .success(let scanResponse):
                    await self?.handleSmartScaleData(scanResponse)
                case .failure(let error):
                    self?.logger.log(level: .error, tag: self?.tag ?? "BluetoothService", message: BluetoothServiceError.scanFailed(error).localizedDescription)
                }
            }
        }
        isSmartScanStarted = true
    }
    
    
    private func handleSmartScaleData(_ data: GGScanResponse) async {
        guard let responseType = data.type else { return }
        let scanData = data.data
        
        switch responseType {
        case .NEW_DEVICE:
            await handleNewDevice(scanData)
        case .SINGLE_ENTRY:
            await saveEntries(scanData)
        case .MULTI_ENTRIES:
            await saveEntries(scanData)
        case .KNOWN_DEVICE:
            // Handle known device discovery
            break
        case .DEVICE_CONNECTED:
            await scaleService.updateConnectedDevices(device: data.data, isConnected: true)
            await checkCanShowWeightOnlyModeAlert()
        case .DEVICE_DISCONNECTED:
            await scaleService.updateConnectedDevices(device: data.data, isConnected: false)
            if !isWeightOnlyModeAlertDismissed {
                await checkCanShowWeightOnlyModeAlert()
            }
        case .DEVICE_MEMORY_FULL:
            await handleDeviceEventAlert(scanData, isDuplicateUserError: false)
        case .DEVICE_DUPLICATE_USER:
            await handleDeviceEventAlert(scanData, isDuplicateUserError: true)
        case .WIFI_STATUS_UPDATE:
            await scaleService.updateConnectedDevices(device: data.data, isConnected: true)
            await handleWifiStatusUpdate(scanData)
        case .DEVICE_INFO_UPDATE:
            await scaleService.updateConnectedDevices(device: scanData, isConnected: true)
            let deviceDetails = data.data as! GGDeviceDetails
            let deviceInfo = DeviceInfo(sdk: deviceDetails)
            deviceInfoUpdatedSubject.send(deviceInfo)
            if !isWeightOnlyModeAlertDismissed {
                await checkCanShowWeightOnlyModeAlert()
            }
        case .PERMISSION_STATUS:
            await handlePermissionStatus(scanData)
        case .DEVICE_WAKE_UP:
            // Handle device wake up
            break
        case .LIVE_MEASUREMENT:
            // Handle live measurement data
            break
        }
    }
    
    private func checkCanShowWeightOnlyModeAlert() async {
        let scale = bluetoothScales.filter { scale in
            (scale.isConnected ?? false)
        }
        
        if !scale.isEmpty {
            showWeightOnlyModeAlertSubject.send(true)
            isWeightOnlyModeAlertDismissed = false
        } else {
            showWeightOnlyModeAlertSubject.send(false)
        }
    }
    
    private func handleWifiStatusUpdate(_ deviceData: GGScanResponseData) async {
        // Extract wifi status from device data and update
        // This would need proper casting based on the actual data structure
        if let deviceInfo = deviceData as? GGDeviceDetails {
            let broadcastId = deviceInfo.broadcastId ?? ""
            let isConfigured = deviceInfo.isWifiConfigured ?? false
            await scaleService.updateConnectedDeviceWifiStatus(broadcastId: broadcastId, isConfigured: isConfigured)
        }
    }
    
    private func handlePermissionStatus(_ permissionData: GGScanResponseData) async {
        // Update central PermissionsService with latest status
        if let permissionResponse = permissionData as? GGPermissionResponseData {
            let permissionStatus = permissionResponse.permissions
            PermissionsService.shared.setPermissions(permissionStatus)
            logger.log(level: .debug, tag: tag, message: "Permission status updated: \(permissionStatus)")
        }
    }
    
    private func handleNewDevice(_ deviceData: GGScanResponseData) async {
        // Parse device data and determine protocol type and if it's new
        guard let deviceDetails = deviceData as? GGDeviceDetails else { return }
        
        let device = mapDeviceDetailsToDevice(deviceDetails)
        let scaleInfo = ScaleInfoUtils.shared.getScaleInfo(byScaleName: deviceDetails.deviceName)
        let protocolType = ProtocolType(rawValue: deviceDetails.protocolType ?? "") ?? .A6
        
        // Check if this is a known device
        let isKnown = bluetoothScales.contains { scale in
            scale.broadcastIdString == deviceDetails.broadcastId
        }
        let isNew = !isKnown
        
        // Send unified discovery event
        let discoveryEvent = DeviceDiscoveryEvent(
            device: device,
            deviceInfo: scaleInfo!,
            protocolType: protocolType,
            isNew: isNew,
        )
        
        deviceDiscoveredSubject.send(discoveryEvent)
    }
    
    private func mapDeviceDetailsToDevice(_ deviceDetails: GGDeviceDetails) -> Device {
        return Device(
            id: UUID().uuidString,
            accountId: activeAccount?.accountId ?? "",
            mac: deviceDetails.macAddress,
            deviceName: deviceDetails.deviceName,
            broadcastId: convertHexToInt(deviceDetails.broadcastId ?? ""),
            broadcastIdString: deviceDetails.broadcastIdString,
            isConnected: false,
        )
    }
    
    private func convertHexToInt(_ hex: String) -> Int64 {
        // Ensure even-length hex string
        let evenHex = hex.count % 2 == 0 ? hex : "0" + hex
        
        // Step 1: Split into 2-character chunks
        let bytes = stride(from: 0, to: evenHex.count, by: 2).map {
            let start = evenHex.index(evenHex.startIndex, offsetBy: $0)
            let end = evenHex.index(start, offsetBy: 2)
            return String(evenHex[start..<end])
        }
        
        // Step 2: Reverse the chunks (handle endianness)
        let reversedHex = bytes.reversed().joined().uppercased()
        
        // Step 3: Convert to Int64
        return Int64(reversedHex, radix: 16) ?? Int64(0)
    }
    
    private func mapProtocolToScaleType(_ protocolType: String) -> ScaleSourceType {
        switch protocolType {
        case "A3": return .bluetooth
        case "A6": return .bluetoothScale
        case "R4": return .btWifiR4
        default: return .bluetoothScale
        }
    }
    
    private func saveEntries(_ entriesData: GGScanResponseData) async {
        // Handle single entry
        if let weightEntry = entriesData as? GGEntry {
            let entry = convertGGEntry(weightEntry)
            guard let entry = entry else {
                return
            }
            try? await entryService.saveNewEntry(entry)
            newEntryReceivedSubject.send(entry)
        } else if let entryList = entriesData as? GGEntryList {
          // Handle multiple entries
          let entries = entryList.list.compactMap { convertGGEntry($0) }
          for entry in entries {
            try? await entryService.saveNewEntry(entry)
          }
          if !entries.isEmpty {
              newEntryReceivedSubject.send(entries[0])
          }
        }
    }
    
    private func convertGGEntry(_ ggEntry: GGEntry) -> Entry? {
        guard let activeAccount = activeAccount else {
            logger.log(level: .error, tag: tag, message: BluetoothServiceError.noActiveAccount.localizedDescription)
            return nil
        }
        
        // Create timestamp in ISO8601 format
        let entryDate = ggEntry.date != nil ?
        Date(timeIntervalSince1970: TimeInterval(ggEntry.date!) / 1000) :
        Date()
        let timestamp = ISO8601DateFormatter().string(from: entryDate)
        
        // Create the main Entry
        let entry = Entry(
            entryTimestamp: timestamp,
            accountId: activeAccount.accountId,
            operationType: OperationType.create.rawValue,
            deviceType: DeviceType.scale.rawValue,
            isSynced: false
        )
        let protocolType = ProtocolType(rawValue: ggEntry.protocolType ?? "") ?? .A6
        var sourceType = ScaleSourceType.bluetoothScale
        if protocolType == .R4 {
            sourceType = .btWifiR4
        }
        // Create BathScaleEntry with basic scale data
        let scaleEntry = BathScaleEntry(
            weight: getWeightByProtocolType(protocolType: protocolType, weightInKg: ggEntry.weightInKg, weight: ggEntry.weight),
            bodyFat: roundMetric(ggEntry.bodyFat),
            muscleMass: roundMetric(ggEntry.muscleMass),
            water: roundMetric(ggEntry.water),
            bmi: roundMetric(ggEntry.bmi) ?? ConversionTools.calculateBMI(weight: Double(ggEntry.weightInKg), height: calculateHeightCm(height: activeAccount.weightSettings?.height)),
            source: sourceType.rawValue
        )
        // Create BathScaleMetric with detailed metrics
        let scaleMetric = BathScaleMetric(
            bmr: ggEntry.bmr,
            metabolicAge: ggEntry.metabolicAge,
            proteinPercent: roundMetric(ggEntry.proteinPercent),
            pulse: ggEntry.pulse,
            skeletalMusclePercent: roundMetric(ggEntry.skeletalMusclePercent),
            subcutaneousFatPercent: roundMetric(ggEntry.subcutaneousFatPercent),
            visceralFatLevel: ggEntry.visceralFatLevel,
            boneMass: roundMetric(ggEntry.boneMass),
            impedance: roundMetric(ggEntry.impedance),
            unit: ggEntry.unit.lowercased()
        )
        entry.scaleEntry = scaleEntry
        entry.scaleEntryMetric = scaleMetric
        return entry
    }
    
    private func handleDeviceEventAlert(_ deviceData: GGScanResponseData, isDuplicateUserError: Bool) async {
        // Log the alert for now - in a full implementation this could trigger UI alerts
        print("Device alert")
    }
    
    private func disconnectDeletedScales(currentScales: [Device], newScales: [Device]) async {
        let deletedScales = currentScales.filter { currentScale in
            !newScales.contains { newScale in
                currentScale.broadcastId == newScale.broadcastId
            }
        }
        for scale in deletedScales {
            if scale.isConnected ?? false {
                if scale.bathScale?.scaleType == ScaleSourceType.btWifiR4.rawValue {
                    _ = await deleteDevice(scale, disconnect: false)
                }
                guard let broadcastId = scale.broadcastIdString else { continue }
                let disconnectResult = await disconnectDevice(broadcastId: broadcastId)
                if case .failure(let error) = disconnectResult {
                    logger.log(level: .error, tag: tag, message: "Failed to disconnect device: \(error.localizedDescription)")
                }
            }
        }
    }
    
    /// Helper to calculate age from a date string (YYYY-MM-DD), matching JS logic
    private func calculateAge(from dateString: String?) -> Int? {
        guard let dateString = dateString else { return nil }
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        guard let birthDate = formatter.date(from: dateString) else { return nil }
        let today = Date()
        let calendar = Calendar.current
        var age = calendar.component(.year, from: today) - calendar.component(.year, from: birthDate)
        let monthDiff = calendar.component(.month, from: today) - calendar.component(.month, from: birthDate)
        let dayDiff = calendar.component(.day, from: today) - calendar.component(.day, from: birthDate)
        if monthDiff < 0 || (monthDiff == 0 && dayDiff < 0) {
            age -= 1
        }
        return age
    }
    
    private func calculateHeightCm(height: String?) -> Int {
        let storedHeight: Int = {
            if let heightStr = height, let h = Int(heightStr) { return h }
            return 680 // fallback: 68.0 inches (5'8")
        }()
        return ConversionTools.convertStoredHeightToCm(storedHeight)
    }
    
    /// Creates ScanData from Account using proper conversions and types
    func createScanData(from account: Account?) -> ScanData? {
        guard let account = account else { return nil }
        // Height: stored as string in tenths of inches, convert to cm
        let heightCm = calculateHeightCm(height: account.weightSettings?.height)
        // Age: calculate from dob (YYYY-MM-DD)
        let age = calculateAge(from: account.dob) ?? 30
        // Athlete: activityLevel == "athlete"
        let isAthlete = account.weightSettings?.activityLevel?.rawValue == "athlete"
        // Unit: .lb/.kg enum to string
        let unit = account.weightSettings?.weightUnit?.rawValue ?? "kg"
        // Sex: enum to string
        let sex = account.gender?.rawValue ?? "male"
        // Goal weight: from goalSettings, convert using ConversionTools
        let goalWeight: Double? = {
            if let goalWeight = account.goalSettings?.goalWeight {
                return ConversionTools.convertStoredToDisplay(Int(goalWeight), isMetric: true)
            }
            return nil
        }()
        // Build ScanData
        return ScanData(
            sex: sex,
            height: Double(heightCm),
            age: age,
            isAthlete: isAthlete,
            unit: unit,
            goalWeight: goalWeight,
            additionalInfo: nil
        )
    }
    
    /// Converts Account to GGBTUserProfile for SDK, using latest entry for weight
    func getProfileInfo(from account: Account) async -> GGBTUserProfile? {
        guard let scanData = createScanData(from: account) else {
            return nil
        }
        // Weight: use latest entry if available, else nil
        var currentWeight: Double? = nil
        if let latest = try? await entryService.getLatestEntry(), let weight = latest.scaleEntry?.weight {
            currentWeight = ConversionTools.convertStoredToDisplay(weight, isMetric: true)
        }
        // Name: firstName or fallback
        let name = account.firstName ?? "User"
        // Goal type: from goalSettings
        let goalType = account.goalSettings?.goalType?.rawValue
        // Build GGBTUserProfile
        return GGBTUserProfile(
            name: name,
            age: scanData.age,
            sex: scanData.sex,
            unit: scanData.unit,
            height: scanData.height,
            weight: currentWeight,
            goalWeight: scanData.goalWeight,
            isAthlete: scanData.isAthlete,
            goalType: goalType,
            metrics: nil
        )
    }
    
    
    
    /// Returns the weight value for a GGEntry based on protocol type, matching conversion logic from TypeScript
    private func getWeightByProtocolType(protocolType: ProtocolType, weightInKg: Float, weight: Float) -> Int? {
        switch protocolType {
        case .A3:
            // Bluetooth (A3) scales have a resolution of .2 lbs, so they require a specific formula to match
            return Int(ConversionTools.convertBluetoothToStored(Double(weightInKg)) * 10)
        case .A6:
            return Int(ConversionTools.convertKgToStored(Double(weightInKg)) * 10)
        case .R4:
            return Int(ConversionTools.convertLbsToStored(Double(weight)))
        }
    }
    
    // Rounds a Float? or Double? metric to Int? (x10 for storage)
    func roundMetric(_ metric: Float?) -> Int? {
        guard let metric = metric else { return nil }
        return Int(floor(Double(metric) * 10))
    }
    func roundMetric(_ metric: Double?) -> Int? {
        guard let metric = metric else { return nil }
        return Int(floor(metric * 10))
    }
    
    /**
     Disconnects the specified device without deleting it from storage.
     - Returns: Result<Void, BluetoothServiceError>
     */
    func disconnectDevice(broadcastId: String) async -> Result<Void, BluetoothServiceError> {
        if !skipDevices.contains(broadcastId) {
            skipDevices.append(broadcastId)
        }
        canShowScaleDiscoveredModal = false
        Task {
            try await Task.sleep(nanoseconds: 5_000_000_000)
            await MainActor.run {
                self.canShowScaleDiscoveredModal = true
            }
        }
        ggBleSDK.skipDevice(broadcastId)
        return .success(())
    }
}

// MARK: - Helpers & Mapping
private extension BluetoothService {
    func mapToGGBTDevice(_ device: Device) -> GGBTDevice? {
        guard let bid = device.broadcastIdString else { return nil }
        return GGBTDevice(
            name: device.deviceName ?? "",
            broadcastId: bid,
            password: device.password,
            token: device.token,
            userNumber: Int(device.userNumber ?? "0") ?? 0,
            preference: mapToGGPreference(device.r4ScalePreference),
            syncAllData: nil,
            batteryLevel: 0,
            protocolType: device.protocolType ?? "",
            macAddress: device.mac ?? ""
        )
    }
    
    func mapToGGBTDevice(_ broadcastId: String) -> GGBTDevice {
        return GGBTDevice(
            name: "",
            broadcastId: broadcastId,
            password: nil,
            token: nil,
            userNumber: 0,
            preference: nil,
            syncAllData: nil,
            batteryLevel: 0,
            protocolType: "",
            macAddress: ""
        )
    }
    
    func mapToGGPreference(_ preference: R4ScalePreference?) -> GGDevicePreference? {
        guard let preference = preference else {
            return nil
        }
        return GGDevicePreference(
            displayName: preference.displayName,
            // Add other preference mappings as needed
        )
    }
}


