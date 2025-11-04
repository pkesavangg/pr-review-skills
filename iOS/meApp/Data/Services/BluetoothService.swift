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
    /// Publisher for live measurement data.
    var liveMeasurementPublisher: AnyPublisher<GGWeightEntry, Never> {
        liveMeasurementSubject.eraseToAnyPublisher()
    }
    
    var skipDevices: [String] = []
    private var blockedBroadcastIds: Set<String> = []
    private var unblockTasks: [String: Task<Void, Never>] = [:]
    var reconnectAlertSkippedDevices: [String] = []
    
    
    // MARK: - Navigation Callback
    /// Callback to handle scale setup navigation. Set by the UI layer (e.g. BottomTabBarViewModel).
    var onOpenScaleSetup: ((Device, DeviceDiscoveryEvent?, Bool, Bool) -> Void)?
    
    // MARK: - Subjects for Scale Discovery
    private let deviceDiscoveredSubject = PassthroughSubject<DeviceDiscoveryEvent, Never>()
    private let newEntryReceivedSubject = PassthroughSubject<Entry, Never>()
    private let deviceInfoUpdatedSubject = PassthroughSubject<DeviceInfo, Never>()
    private let showWeightOnlyModeAlertSubject = PassthroughSubject<Bool, Never>()
    private let firmwareUpdateProgressSubject = PassthroughSubject<FirmwareUpdateStatus, Never>()
    /// Subject for live measurement data events.
    private let liveMeasurementSubject = PassthroughSubject<GGWeightEntry, Never>()
    
    // MARK: - Private Properties
    private var cancellables = Set<AnyCancellable>()
    private var activeAccount: Account?
    private var isSmartScanStarted = false
    private var bluetoothScales: [Device] = []
    private var connectedGgDevices: [GGBTDevice] = []
    private var isWeightOnlyModeAlertDismissed = false
    private var lastProfileUpdateAccountId: String?
    private var isUpdatingR4Profile = false
    private var lastAccountId: String?
    
    // MARK: - Dependencies
    private let accountService: AccountService
    private let scaleService: ScaleServiceProtocol
    private let entryService: EntryServiceProtocol
    private let logger: LoggerService
    private let ggBleSDK = GGBluetoothSwiftPackage.shared
    private let timeoutConstants = AppConstants.TimeoutsAndRetention.self
    private let tag = "BluetoothService"
    
    
    // MARK: - Alert Dependencies (injected via shared instances for now)
    private var notificationService: NotificationHelperService { NotificationHelperService.shared }
    private var scaleInfoUtils: ScaleInfoUtils { ScaleInfoUtils.shared }
    
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
        initialize()
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
                Task {
                    await self?.handleAccountUpdate(account)
                    // Only update R4 profile from subscription if:
                    // 1. Account is not nil
                    // 2. Not already updating (prevents concurrent calls)
                    // 3. Account ID changed (account switch) or was nil (new account)
                    // This prevents conflicts when updateUserProfileForR4Scales is called explicitly
                    let currentAccountId = account?.accountId
                    let accountIdChanged = currentAccountId != self?.lastAccountId
                    if let accountId = currentAccountId,
                       !(self?.isUpdatingR4Profile ?? false),
                       accountIdChanged {
                        self?.lastAccountId = accountId
                        let _ = await self?.updateUserProfileForR4Scales()
                    } else if currentAccountId != nil {
                        // Update lastAccountId even if we don't call updateUserProfileForR4Scales
                        self?.lastAccountId = currentAccountId
                    }
                }
            }
            .store(in: &cancellables)
    }
    
    /**
     Starts Bluetooth scanning and device synchronization.
     This should be called when the dashboard is ready to receive Bluetooth events.
     */
    func startBluetoothOperations() async {
        guard activeAccount != nil else {
            logger.log(level: .info, tag: tag, message: "Cannot start Bluetooth operations: no active account")
            return
        }
        
        if !isSmartScanStarted {
            clearDevices()
            await scan()
            syncDevices([])
        }
    }
    
    private func handleScalesUpdate(_ scales: [Device]?) async {
        guard let scales = scales, !scales.isEmpty else {
            bluetoothScales = []
            syncDevices([])
            return
        }
        // Filter scales by allowed types only (common across all models)
        let allowedTypes: Set<ScaleSourceType> = Set([
            .bluetooth,
            .bluetoothScale,
            .lcbt,
            .lcbtScale,
            .btWifiR4
        ])
        let filteredScales = scales.filter { scale in
            guard let raw = getSafeScaleType(for: scale), let type = ScaleSourceType(rawValue: raw) else {
                return false
            }
            return allowedTypes.contains(type)
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
            // Don't start scanning immediately - wait for dashboard to be ready
            // The scan will be triggered by startBluetoothOperations() when called from ContentViewModel
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
        skipDevices = []
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
        let scalesToSync = devices.isEmpty ? bluetoothScales : devices
        let ggDevices = scalesToSync.map { device in
            GGBTDevice(
                name: device.deviceName ?? "",
                broadcastId: device.broadcastIdString ?? "",
                password: convertIntToHex(device.password ?? 0, protocolType: ProtocolType(rawValue: device.protocolType ?? "") ?? .A6),
                token: device.token,
                userNumber: Int(device.userNumber ?? "0"),
                preference: mapToGGPreference(deviceId: device.id, preference: device.r4ScalePreference),
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
    func addNewDevice(_ scale: Device, metaData deviceDetails: DeviceMetaData?, _ skipDuplicateCheck: Bool? = false) async -> Result<Device, BluetoothServiceError> {
        do {
            guard let userId = activeAccount?.accountId else {
                throw BluetoothServiceError.noActiveAccount
            }
            let scaleToSave = scale
            scaleToSave.accountId = userId
            scaleToSave.createdAt = DateTimeTools.getCurrentDatetimeIsoString()
            scaleToSave.nickname = scale.nickname ?? "Bluetooth Smart Scale"
            scaleToSave.password = scale.password
            var metaData = deviceDetails
            let scaleType = getSafeScaleType(for: scale) ?? ""
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
            let savedScale = try await scaleService.createDevice(scaleToSave, skipDuplicateCheck ?? false)
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
    
    /// Deletes the current app user's slot on the BT WiFi (R4) scale when possible.
    /// Attempts to use the device token if available; otherwise fetches users and matches by preference/display name.
    /// - Returns: Result<UserDeletionResponse, BluetoothServiceError>
    func deleteCurrentUserFromScaleIfPossible(_ device: Device, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> {
        guard let broadcastId = device.broadcastIdString else {
            logger.log(level: .error, tag: tag, message: "deleteCurrentUserFromScaleIfPossible - missing broadcastId")
            return .failure(.invalidBroadcastId)
        }
        
        // Prefer using a known token on the device model
        if let token = device.token, !token.isEmpty {
            logger.log(level: .debug, tag: tag, message: "Deleting scale user using persisted token")
            if Task.isCancelled { return .failure(.timeout) }
            return await deleteScaleByBroadcastId(broadcastId: broadcastId, token: token, disconnect: disconnect)
        }
        
        // Fallback: fetch users from the scale and try to match by display name and id
        if Task.isCancelled { return .failure(.timeout) }
        let listResult = await getScaleUserList(for: device)
        switch listResult {
        case .success(let users):
            if Task.isCancelled { return .failure(.timeout) }
            if let match = findUserToDelete(userList: users, discoveredScale: device), let token = match.token, !token.isEmpty {
                logger.log(level: .debug, tag: tag, message: "Deleting matched scale user: \(match.name)")
                return await deleteScaleByBroadcastId(broadcastId: broadcastId, token: token, disconnect: disconnect)
            } else {
                logger.log(level: .error, tag: tag, message: "No matching user found to delete on scale for broadcastId \(broadcastId)")
                return .failure(.deviceNotFound)
            }
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to fetch user list before deletion: \(error.localizedDescription)")
            return .failure(error)
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
     - Returns: Result<WifiSetupResponse, BluetoothServiceError>
     */
    func setupWifi(on device: Device, config: WifiConfig) async -> Result<WifiSetupResponse, BluetoothServiceError> {
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            
            let ggConfig = GGBTWifiConfig(ssid: config.ssid, password: config.password ?? "")
            let ggResponse = await ggBleSDK.setupWifi(ggDevice, ggConfig)
            
            let response = WifiSetupResponse(wifiState: ggResponse.wifiState, errorCode: ggResponse.errorCode)
            return .success(response)
            
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
    
    // MARK: - Live Measurement
    
    /**
     Starts live measurement for the given device.
     - Returns: Result<Void, BluetoothServiceError>
     */
    @discardableResult
    func startLiveMeasurement(for device: Device) async -> Result<Void, BluetoothServiceError> {
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            ggBleSDK.startLiveMeasurement(ggDevice)
            return .success(())
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.startLiveMeasurementFailed(error))
        }
    }
    
    /**
     Stops live measurement for the given device.
     - Returns: Result<Void, BluetoothServiceError>
     */
    @discardableResult
    func stopLiveMeasurement(for device: Device) async -> Result<Void, BluetoothServiceError> {
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            ggBleSDK.stopLiveMeasurement(ggDevice)
            return .success(())
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.startLiveMeasurementFailed(error))
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
                case .wifi: return .WIFI
                case .settings: return .SETTINGS
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
     - Returns: Result<[String], BluetoothServiceError>
     */
    func updateUserProfileForR4Scales() async -> Result<[String], BluetoothServiceError> {
        // Prevent concurrent calls
        guard !isUpdatingR4Profile else {
            logger.log(level: .debug, tag: tag, message: "updateUserProfileForR4Scales already in progress, skipping")
            return .failure(.updateProfileFailed(BluetoothServiceError.notImplemented))
        }
        
        isUpdatingR4Profile = true
        defer { isUpdatingR4Profile = false }
        
        do {
            guard let account = activeAccount else {
                throw BluetoothServiceError.noActiveAccount
            }
            guard let userProfile = await getProfileInfo(from: account) else {
                throw BluetoothServiceError.noProfileInfo
            }
            let success = await ggBleSDK.updateProfile(profile: userProfile)
            logger.log(level: .debug, tag: tag, message: "updateUserProfileForR4Scales completed: \(success)")
            // SDK returns Bool; protocol expects [String] status array. Return empty array for now.
            return .success([])
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
            ggDevice.preference = mapToGGPreference(deviceId: device.id, preference: preference)
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
        // Check device connection status before attempting to fetch users
        guard device.isConnected == true else {
            logger.log(level: .error, tag: tag, message: "Cannot get user list - device is not connected: \(device.id)")
            return .failure(.deviceNotConnected)
        }
        
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            
            // Add timeout to prevent continuation leaks if SDK callback never fires
            let users = try await withTimeout(seconds: 10) {
                await self.ggBleSDK.getUsers(ggDevice)
            }
            
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
            logger.log(level: .error, tag: tag, message: "Failed to get user list: \(error.localizedDescription)")
            return .failure(.updateProfileFailed(error))
        }
    }
    
    // MARK: - Device Info
    /**
     Retrieves generic device information (model, serial, firmware, …).
     - Returns: Result<DeviceInfo, BluetoothServiceError>
     */
    func getDeviceInfo(for device: Device) async -> Result<DeviceInfo, BluetoothServiceError> {
        // Check device connection status before attempting to fetch device info
        guard device.isConnected == true else {
            logger.log(level: .error, tag: tag, message: "Cannot get device info - device is not connected: \(device.id)")
            return .failure(.deviceNotConnected)
        }
        
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            
            // Add timeout to prevent continuation leaks if SDK callback never fires
            let details = try await withTimeout(seconds: 10) {
                await self.ggBleSDK.getDeviceInfo(ggDevice)
            }
            
            return .success(DeviceInfo(sdk: details))
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to get device info: \(error.localizedDescription)")
            return .failure(.updateProfileFailed(error))
        }
    }
    
    /**
     Retrieves device logs from the scale.
     - Returns: Result<DeviceLogs, BluetoothServiceError>
     */
    func getDeviceLogs(for device: Device) async -> Result<DeviceLogs, BluetoothServiceError> {
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            let response = await ggBleSDK.getDeviceLogs(ggDevice)
            let deviceLogs = DeviceLogs(logs: response.logs.map { log in
                DeviceLogEntry(macAddress: log.macAddress, log: log.log)
            })
            return .success(deviceLogs)
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.getDeviceLogsFailed(error))
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
    }
    
    
    func clearScaleDiscoveredInfo() {
        skipDevices.removeAll()
        reconnectAlertSkippedDevices.removeAll()
    }
    
    
    func disconnectConnectedScales() async {
        for scale in bluetoothScales where scale.isConnected == true {
            if let broadcastId = scale.broadcastIdString {
                _ = await disconnectDevice(broadcastId: broadcastId)
            }
        }
        skipDevices.removeAll()
    }
    
    /**
     Deletes all connected R4 scales from the device and disconnects them.
     This method is typically called during account deletion to clean up scale connections.
     - Returns: Result<Void, BluetoothServiceError>
     */
    func deleteR4Scales() async -> Result<Void, BluetoothServiceError> {
        // Filter for connected R4 scales
        let connectedR4Scales = bluetoothScales.filter { scale in
            let isConnected = scale.isConnected ?? false
            let isR4Scale: Bool = {
                if let raw = getSafeScaleType(for: scale) { return ScaleSourceType(rawValue: raw) == .btWifiR4 }
                return false
            }()
            return isConnected && isR4Scale
        }
        
        logger.log(level: .info, tag: tag, message: "Found \(connectedR4Scales.count) connected R4 scales to delete")
        
        // Delete each R4 scale and disconnect
        for scale in connectedR4Scales {
            // Delete the scale from the device
            let deleteResult = await deleteDevice(scale, disconnect: false)
            switch deleteResult {
            case .success(let result):
                logger.log(level: .info, tag: tag, message: "Successfully deleted R4 scale: \(scale.deviceName ?? "Unknown")", data: result)
            case .failure(let error):
                logger.log(level: .error, tag: tag, message: "Failed to delete R4 scale \(scale.deviceName ?? "Unknown"): \(error.localizedDescription)")
            }
            
            // Disconnect the scale
            if let broadcastId = scale.broadcastIdString {
                let disconnectResult = await disconnectDevice(broadcastId: broadcastId)
                switch disconnectResult {
                case .success(let result):
                    logger.log(level: .info, tag: tag, message: "Successfully disconnected R4 scale: \(broadcastId)", data: result)
                case .failure(let error):
                    logger.log(level: .error, tag: tag, message: "Failed to disconnect R4 scale \(broadcastId): \(error.localizedDescription)")
                }
            }
        }
        
        return .success(())
    }
    
    // MARK: - Device Event Alert Handling
    
    /**
     Handles device event alerts for scale user limit reached or duplicate user errors.
     Similar to the Angular/TypeScript implementation but adapted for SwiftUI patterns.
     
     - Parameters:
     - scale: The discovered scale device
     - isDuplicateUserError: Whether this is a duplicate user error (true) or user limit error (false)
     */
    private func handleDeviceEventAlert(_ deviceData: GGScanResponseData, isDuplicateUserError: Bool) async {
        
        guard let deviceDetails = deviceData as? GGDeviceDetails, !isSetupInProgress else {
            logger.log(level: .error, tag: tag, message: "Invalid device data for event alert")
            return
        }
        
        if skipDevices.contains(deviceDetails.broadcastIdString) || reconnectAlertSkippedDevices.contains(deviceDetails.broadcastIdString) {
            return
        }

        // Get scale info and create discovered scale
        let scaleInfo = scaleInfoUtils.getScaleInfo(byScaleName: deviceDetails.deviceName)
        guard  let discoveredScale = bluetoothScales.first(where: {$0.broadcastIdString == deviceDetails.broadcastIdString}) else {
            logger.log(level: .error, tag: tag, message: "Discovered scale not found in bluetoothScales")
            return
        }
        
        // Get user list from the scale
        let userListResult = await getScaleUserList(for: discoveredScale)
        guard case .success(let userList) = userListResult else {
            logger.log(level: .error, tag: tag, message: "Failed to get scale user list for device event alert")
            return
        }
        
        // Find user to delete (matching current scale preferences)
        let userToDelete = findUserToDelete(userList: userList, discoveredScale: discoveredScale)
        
        // Create scale setup navigation closure
        let openScaleSetup: () -> Void = { [weak self] in
            guard let self = self else { return }
            
            Task { @MainActor in
                // Dismiss all modals first
                self.notificationService.dismissAllModals()
                
                // Delete the existing user if found
                if let userToDelete = userToDelete, let token = userToDelete.token, !token.isEmpty {
                    let response = await self.deleteScaleByBroadcastId(
                        broadcastId: discoveredScale.broadcastIdString ?? "",
                        token: token,
                        disconnect: false
                    )
                    
                    switch response {
                    case .failure(_):
                        self.logger.log(level: .error, tag: self.tag, message: "Failed to delete user from scale during event alert")
                        return
                    default:
                        break
                    }
                }
                
                // Navigate to scale setup
                let deviceDiscoveryEvent = DeviceDiscoveryEvent(
                    device: discoveredScale,
                    deviceInfo: scaleInfo ?? ScaleItemInfo(
                        productName: deviceDetails.deviceName,
                        sku: "0412", // Default R4 SKU
                        imgPath: AppAssets.meLogoDark,
                        setupType: .btWifiR4,
                        bodyComp: true
                    ),
                    protocolType: ProtocolType(rawValue: deviceDetails.protocolType ?? "") ?? .R4,
                    isNew: true
                )
                
                // Pass the reconnect and duplicate user flags
                self.onOpenScaleSetup?(discoveredScale, deviceDiscoveryEvent, true, isDuplicateUserError)
            }
        }
        
        // Disable scale discovered modal temporarily
        canShowScaleDiscoveredModal = false
        
        // Create alert based on error type
        let alertStrings = AlertStrings.self
        let alert: AlertModel
        
        if isDuplicateUserError {
            alert = AlertModel(
                title: alertStrings.DuplicateUserAlert.header,
                message: alertStrings.DuplicateUserAlert.message,
                buttons: [
                    AlertButtonModel(title: alertStrings.DuplicateUserAlert.cancelButton, type: .secondary) { _ in
                        // Skip this device
                        Task {
                            if let broadcastId = discoveredScale.broadcastIdString {
                                self.reconnectAlertSkippedDevices.append(broadcastId)
                                _ = await self.disconnectDevice(broadcastId: broadcastId)
                            }
                        }
                        
                    },
                    AlertButtonModel(title: alertStrings.DuplicateUserAlert.reconnectButton, type: .primary) { _ in
                        openScaleSetup()
                    }
                ]
            )
        } else {
            alert = AlertModel(
                title: alertStrings.ReconnectDeviceAlert.header,
                message: alertStrings.ReconnectDeviceAlert.message,
                buttons: [
                    AlertButtonModel(title: alertStrings.ReconnectDeviceAlert.cancelButton, type: .secondary) { _ in
                        // Skip this device
                        Task {
                            if let broadcastId = discoveredScale.broadcastIdString {
                                self.reconnectAlertSkippedDevices.append(broadcastId)
                                _ = await self.disconnectDevice(broadcastId: broadcastId)
                            }
                        }
                    },
                    AlertButtonModel(title: alertStrings.ReconnectDeviceAlert.reconnectButton, type: .primary) { _ in
                        openScaleSetup()
                    }
                ]
            )
        }
        
        // Present the alert
        notificationService.showAlert(alert)
    }
    
    /**
     Finds a user to delete from the scale user list based on matching scale preferences.
     
     - Parameters:
     - userList: List of users from the scale
     - discoveredScale: The discovered scale device
     - Returns: The user to delete, if found
     */
    private func findUserToDelete(userList: [DeviceUser], discoveredScale: Device) -> DeviceUser? {
        return userList.first { user in
            bluetoothScales.contains { scale in
                let isR4Scale: Bool = {
                    if let raw = getSafeScaleType(for: scale) { return ScaleSourceType(rawValue: raw) == .btWifiR4 }
                    return false
                }()
                let namesMatch: Bool = {
                    if let pref = scale.r4ScalePreference {
                        if let fetched = fetchAttachedPreference(by: pref.id) { return user.name.lowercased() == fetched.displayName.lowercased() }
                        return user.name.lowercased() == pref.displayName.lowercased()
                    }
                    return false
                }()
                let idsMatch = discoveredScale.broadcastId == scale.broadcastId
                
                return isR4Scale && namesMatch && idsMatch
            }
        }
    }
    
    /**
     Deletes a scale user by broadcast ID and token.
     
     - Parameters:
     - broadcastId: The broadcast ID of the scale
     - token: The user token to delete
     - disconnect: Whether to disconnect after deletion
     - Returns: Result indicating success or failure
     */
    private func deleteScaleByBroadcastId(broadcastId: String, token: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> {
        // Create a temporary device for the deletion
        let tempDevice = Device(
            id: UUID().uuidString,
            accountId: activeAccount?.accountId ?? "",
            mac: nil,
            deviceName: nil,
            broadcastId: nil,
            broadcastIdString: broadcastId,
            isConnected: false
        )
        tempDevice.token = token
        
        return await deleteDevice(tempDevice, disconnect: disconnect)
    }
    
    // MARK: - Private Helper Methods
    
    /// Safely gets the scale type from a Device, handling potential SwiftData detachment issues
    private func getSafeScaleType(for device: Device) -> String? {
        // Note: `persistentModelID` is non-optional in SwiftData; no need to guard for nil here.
        
        // Try to access the scale type, but be prepared for potential detachment
        // We'll use a safer approach by checking if the bathScale relationship exists
        guard let bathScale = device.bathScale else {
            return nil
        }
        return bathScale.scaleType
    }
    
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
        // Broad blocked-broadcast check for all event data types we can identify
        var bid: String? = nil
        if let details = data.data as? GGDeviceDetails {
            bid = details.broadcastIdString
        } else if let entry = data.data as? GGWeightEntry {
            bid = entry.broadcastIdString
        }
        if let bid = bid, blockedBroadcastIds.contains(bid) {
            ggBleSDK.skipDevice(bid)
            return
        }
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
            // Update weight-only mode status when device connects
            if let deviceDetails = data.data as? GGDeviceDetails {
                await updateWeightOnlyModeStatusFromDeviceDetails(deviceDetails)
            }
            await checkCanShowWeightOnlyModeAlert()
        case .DEVICE_DISCONNECTED:
            await scaleService.updateConnectedDevices(device: data.data, isConnected: false)
            // Clear weight-only mode status when device disconnects
            if let deviceDetails = data.data as? GGDeviceDetails {
                await clearWeightOnlyModeStatusOnDisconnect(deviceDetails)
            }
            if !isWeightOnlyModeAlertDismissed {
                await checkCanShowWeightOnlyModeAlert()
            }
        case .DEVICE_MEMORY_FULL:
            await handleDeviceEventAlert(scanData, isDuplicateUserError: false)
        case .DEVICE_DUPLICATE_USER:
            await handleDeviceEventAlert(scanData, isDuplicateUserError: true)
        case .WIFI_STATUS_UPDATE:
            await scaleService.updateConnectedDevices(device: data.data, isConnected: true)
            // Update weight-only mode status when WiFi status changes
            if let deviceDetails = data.data as? GGDeviceDetails {
                await updateWeightOnlyModeStatusFromDeviceDetails(deviceDetails)
            }
            await handleWifiStatusUpdate(scanData)
        case .DEVICE_INFO_UPDATE:
            await scaleService.updateConnectedDevices(device: scanData, isConnected: true)
            let deviceDetails = data.data as! GGDeviceDetails
            let deviceInfo = DeviceInfo(sdk: deviceDetails)
            
            if let deviceDetails = data.data as? GGDeviceDetails {
                await updateWeightOnlyModeStatusFromDeviceDetails(deviceDetails)
            }
            
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
            if let liveData = data.data as? GGWeightEntry {
                liveMeasurementSubject.send(liveData)
            } else {
                logger.log(level: .error, tag: tag, message: "Failed to get live measurement data")
            }
            break
        }
    }
    
    private func checkCanShowWeightOnlyModeAlert() async {
        // Get connected scales that have weight-only mode enabled by others
        let connectedScales = bluetoothScales.filter { scale in
            (scale.isConnected ?? false)
        }
        
        var hasWeightOnlyModeEnabledByOthers = false
        
        // Check each connected scale for weight-only mode condition
        for scale in connectedScales {
            if let isWeightOnlyEnabled = scale.isWeighOnlyModeEnabledByOthers, isWeightOnlyEnabled {
                hasWeightOnlyModeEnabledByOthers = true
                break
            }
        }
        
        if hasWeightOnlyModeEnabledByOthers && !isWeightOnlyModeAlertDismissed {
            showWeightOnlyModeAlertSubject.send(true)
        } else {
            showWeightOnlyModeAlertSubject.send(false)
        }
    }
    
    public func handleWeightOnlyModeAlertDismissed() {
        isWeightOnlyModeAlertDismissed = true
        showWeightOnlyModeAlertSubject.send(false)
    }
    
    /// Updates the weight-only mode status for a connected scale based on device info
    /// Uses the condition: !(deviceInfo.impedanceSwitchState ?? false) && (scale.r4ScalePreference?.shouldMeasureImpedance ?? false)
    private func updateWeightOnlyModeStatus(deviceDetails: GGDeviceDetails, deviceInfo: DeviceInfo) async {
        guard let broadcastId = deviceDetails.broadcastId  else {
            logger.log(level: .error, tag: tag, message: "Cannot update weight-only mode status: missing broadcast ID")
            return
        }
        
        // Find the scale in our local collection
        guard let scale = bluetoothScales.first(where: { $0.broadcastIdString == broadcastId }) else {
            logger.log(level: .error, tag: tag, message: "Scale not found for broadcast ID: \(broadcastId)")
            return
        }
        
        // Calculate weight-only mode status using the specified condition
        let impedanceSwitchState = deviceInfo.impedanceSwitchState ?? false
        let shouldMeasureImpedance: Bool = {
            if let pref = scale.r4ScalePreference {
                if let fetched = fetchAttachedPreference(by: pref.id) { return fetched.shouldMeasureImpedance }
                return pref.shouldMeasureImpedance
            }
            return false
        }()
        let isWeightOnlyModeEnabledByOthers = !impedanceSwitchState && shouldMeasureImpedance
        
        // Update the scale's weight-only mode status
        scale.isWeighOnlyModeEnabledByOthers = isWeightOnlyModeEnabledByOthers
        
        // Update via scale service to persist the change
        await scaleService.updateConnectedDeviceWeightOnlyMode(
            broadcastId: broadcastId,
            isWeightOnlyModeEnabledByOthers: isWeightOnlyModeEnabledByOthers
        )
        
        logger.log(level: .debug, tag: tag, message: "Updated weight-only mode status for scale \(broadcastId): \(isWeightOnlyModeEnabledByOthers)")
    }
    
    /// Updates the weight-only mode status from device details (when we don't have full DeviceInfo)
    /// This is used for connection events where we only have GGDeviceDetails
    private func updateWeightOnlyModeStatusFromDeviceDetails(_ deviceDetails: GGDeviceDetails) async {
        guard let broadcastId = deviceDetails.broadcastId else {
            logger.log(level: .error, tag: tag, message: "Cannot update weight-only mode status: missing broadcast ID")
            return
        }
        
        // Find the scale in our local collection
        guard let scale = bluetoothScales.first(where: { $0.broadcastIdString == broadcastId }) else {
            logger.log(level: .error, tag: tag, message: "Scale not found for broadcast ID: \(broadcastId)")
            return
        }
        
        // For connection events, we need to get device info to calculate weight-only mode status
        // Since we don't have full DeviceInfo here, we'll get it from the scale
        let deviceInfoResult = await getDeviceInfo(for: scale)
        switch deviceInfoResult {
        case .success(let deviceInfo):
            await updateWeightOnlyModeStatus(deviceDetails: deviceDetails, deviceInfo: deviceInfo)
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to get device info for weight-only mode calculation: \(error)")
        }
    }
    
    /// Clears the weight-only mode status when device disconnects
    private func clearWeightOnlyModeStatusOnDisconnect(_ deviceDetails: GGDeviceDetails) async {
        guard let broadcastId = deviceDetails.broadcastId else {
            return
        }
        
        // Find the scale in our local collection
        guard let scale = bluetoothScales.first(where: { $0.broadcastIdString == broadcastId }) else {
            return
        }
        
        // Clear the weight-only mode status when device disconnects
        scale.isWeighOnlyModeEnabledByOthers = false
        
        // Update via scale service to persist the change
        await scaleService.updateConnectedDeviceWeightOnlyMode(
            broadcastId: broadcastId,
            isWeightOnlyModeEnabledByOthers: false
        )
        
        logger.log(level: .debug, tag: tag, message: "Cleared weight-only mode status for disconnected scale \(broadcastId)")
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
        
        let scaleInfo = ScaleInfoUtils.shared.getScaleInfo(byScaleName: deviceDetails.deviceName)
        let device = mapDeviceDetailsToDevice(deviceDetails, isA3Device: deviceDetails.protocolType == "A3")
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
    
    private func mapDeviceDetailsToDevice(_ deviceDetails: GGDeviceDetails, isA3Device: Bool = false) -> Device {
        return Device(
            id: UUID().uuidString,
            accountId: activeAccount?.accountId ?? "",
            mac: deviceDetails.macAddress,
            deviceName: deviceDetails.deviceName,
            broadcastId: isA3Device ? Int64(deviceDetails.broadcastId ?? "0") : convertHexToInt(deviceDetails.broadcastId ?? ""),
            broadcastIdString: deviceDetails.broadcastIdString,
            isConnected: false,
        )
    }
    
    func convertHexToInt(_ hex: String) -> Int64 {
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
    
    func convertIntToHex(_ value: Int64, protocolType: ProtocolType) -> String {
        // Convert to hex string without leading 0x
        var hex = String(value, radix: 16)
        
        switch protocolType {
        case .R4:
            // Pad to 12 characters (6 bytes)
            hex = String(repeating: "0", count: max(0, 12 - hex.count)) + hex
        default:
            if hex.count < 8 {
                hex = String(repeating: "0", count: 8 - hex.count) + hex
            } else if hex.count > 8 && hex.count < 12 {
                hex = String(repeating: "0", count: 12 - hex.count) + hex
            }
        }
        
        // Split into 2-character chunks
        var bytes: [String] = []
        for i in stride(from: 0, to: hex.count, by: 2) {
            let start = hex.index(hex.startIndex, offsetBy: i)
            let end = hex.index(start, offsetBy: 2)
            bytes.append(String(hex[start..<end]))
        }
        
        // Reverse and join to simulate little-endian format
        let reversedHex = bytes.reversed().joined().uppercased()
        
        return reversedHex
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
            if entries.isEmpty {
                logger.log(level: .info, tag: tag, message: "No valid entries to save")
                return
            }
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
            bmi: ggEntry.bmi > 0 ? roundMetric(ggEntry.bmi) : ConversionTools.calculateBMI(weight: Double(ggEntry.weightInKg), height: calculateHeightCm(height: activeAccount.weightSettings?.height)),
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
    
    // Note: This method is now implemented above in the "Device Event Alert Handling" section
    
    private func disconnectDeletedScales(currentScales: [Device], newScales: [Device]) async {
        let deletedScales = currentScales.filter { currentScale in
            !newScales.contains { newScale in
                currentScale.broadcastId == newScale.broadcastId
            }
        }
        for scale in deletedScales {
            if scale.isConnected ?? false {
                // Delete the device from the scale for all scale types to avoid SwiftData detachment issues
                _ = await deleteDevice(scale, disconnect: false)
                
                guard let broadcastId = scale.broadcastIdString else { continue }
                let disconnectResult = await disconnectDevice(broadcastId: broadcastId)
                if case .failure(let error) = disconnectResult {
                    logger.log(level: .error, tag: tag, message: "Failed to disconnect device: \(error.localizedDescription)")
                }
            }
        }
    }
    
    /// Helper to calculate age from a date string (e.g. "2009-01-01T00:00:00.000Z")
    private func calculateAge(from dateString: String?) -> Int? {
        guard let dateString = dateString else { return nil }

        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]

        guard let birthDate = formatter.date(from: dateString) else {
            return nil
        }

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
            if let heightStr = height,
               let h = Double(heightStr) {
                return Int(round(h)) // not optional, so no need for if-let
            }
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
    
    /// Returns the weight value for a GGEntry based on protocol type
    private func getWeightByProtocolType(protocolType: ProtocolType, weightInKg: Float, weight: Float) -> Int? {
        switch protocolType {
        case .A3:
            // Bluetooth (A3) scales have a resolution of .2 lbs, so they require a specific formula to match
            return Int(ConversionTools.convertBluetoothToStored(Double(weightInKg)) * 10)
        case .A6:
            return Int(ConversionTools.convertKgToStored(Double(weightInKg)))
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
    func disconnectDevice(broadcastId: String, considerForSession: Bool = true) async -> Result<Void, BluetoothServiceError> {

        if !skipDevices.contains(broadcastId) {
            skipDevices.append(broadcastId)
        }
        // Temporarily block device events to avoid reconnect race during deletion/unlink
        blockedBroadcastIds.insert(broadcastId)
        // Cancel any existing unblock task for this broadcastId to avoid duplicates
        if let existing = unblockTasks[broadcastId] {
            existing.cancel()
        }
        let task = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(AppConstants.TimeoutsAndRetention.broadcastBlockDurationNs))
            await MainActor.run { [weak self] in
                _ = self?.blockedBroadcastIds.remove(broadcastId)
                self?.unblockTasks.removeValue(forKey: broadcastId)
            }
        }
        unblockTasks[broadcastId] = task
        canShowScaleDiscoveredModal = false
        Task {
            try await Task.sleep(nanoseconds: UInt64(timeoutConstants.discoveredScaleModalTimeout))
            await MainActor.run {
                self.canShowScaleDiscoveredModal = true
            }
        }
        ggBleSDK.skipDevice(broadcastId)
        return .success(())
    }

    /// Re-applies all locally tracked `skipDevices` to the SDK after flows that may reset SDK state (e.g., closing setup screens).
    /// Ensures that currently paired scales are not skipped again.
    func reapplySkipDevicesExcludingPaired() {
        // Build a fast lookup set of paired broadcast IDs
        // Re-issue skip calls for any previously skipped device that is not paired
        let pairedIdsUpper = Set(bluetoothScales.compactMap { $0.broadcastIdString?.uppercased() })

        skipDevices = skipDevices.filter { !pairedIdsUpper.contains($0.uppercased()) }
        
        for id in skipDevices {
            ggBleSDK.skipDevice(id)
        }
    }
}

// MARK: - Helpers & Mapping
private extension BluetoothService {
    func mapToGGBTDevice(_ device: Device) -> GGBTDevice? {
        guard let bid = device.broadcastIdString else { return nil }
        return GGBTDevice(
            name: device.deviceName ?? "",
            broadcastId: bid,
            password: convertIntToHex(device.password ?? 0, protocolType: ProtocolType(rawValue: device.protocolType ?? "") ?? .A6),
            token: device.token,
            userNumber: Int(device.userNumber ?? "0") ?? 0,
            preference: mapToGGPreference(deviceId: device.id, preference: device.r4ScalePreference),
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
    
    // Fetch preference via ScaleService/Repository to ensure attached object
    func fetchAttachedPreference(by id: String) -> R4ScalePreference? {
        return scaleService.fetchAttachedPreferenceSync(by: id)
    }

    func mapToGGPreference(deviceId: String, preference: R4ScalePreference?) -> GGDevicePreference? {
        // Always try fetching via preference.id to ensure a context-attached object.
        // This avoids touching properties on a potentially detached SwiftData instance.
        guard let preference = preference else { return nil }
        let attached = fetchAttachedPreference(by: preference.id) ?? preference
        return GGDevicePreference(
            displayName: attached.displayName,
            displayMetrics: attached.displayMetrics,
            shouldMeasureImpedance: attached.shouldMeasureImpedance,
            shouldMeasurePulse: attached.shouldMeasurePulse,
            timeFormat: attached.timeFormat
        )
    }
    
    // MARK: - Timeout Helper
    
    /// Adds a timeout to an async operation to prevent continuation leaks
    /// - Parameters:
    ///   - seconds: Timeout duration in seconds
    ///   - operation: The async operation to execute (can be non-throwing)
    /// - Returns: The result of the operation
    /// - Throws: BluetoothServiceError.timeout if the operation exceeds the timeout
    private func withTimeout<T>(seconds: TimeInterval, operation: @escaping () async -> T) async throws -> T {
        // Nanoseconds per second for Task.sleep conversion
        let nanosecondsPerSecond: UInt64 = 1_000_000_000
        
        return try await withThrowingTaskGroup(of: T.self) { group in
            // Add the actual operation
            group.addTask {
                await operation()
            }
            
            // Add timeout task
            group.addTask {
                try await Task.sleep(nanoseconds: UInt64(seconds) * nanosecondsPerSecond)
                throw BluetoothServiceError.timeout
            }
            
            // Return the first result (operation or timeout)
            guard let result = try await group.next() else {
                group.cancelAll()
                throw BluetoothServiceError.timeout
            }
            group.cancelAll()
            return result
        }
    }
}


