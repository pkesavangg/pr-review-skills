//
//  ScaleService.swift
//  meApp
//
//  Created by Lakshmi Priya on 02/06/25.
//

import Combine
import Foundation
import GGBluetoothSwiftPackage
import SwiftData

/*
 SwiftLint exception:
 This service intentionally aggregates all scale-related operations to keep the scale management flow discoverable and auditable in a single place. Splitting across multiple types would add indirection and risk during critical scale operations. The `createR4Scale` function intentionally has many parameters to ensure all required scale properties are properly initialized. We therefore disable `type_body_length` and `function_parameter_count` for this file.
 */

/// Service for managing paired scale devices with a clean "replace-all" sync policy.
///
/// ## Sync Architecture Overview
///
/// This service implements an offline-first approach with a predictable sync pattern:
///
/// ### Local Operations (Offline-First)
/// - **Create/Edit**: Store locally, mark `isSynced = false`
/// - **Delete**:
///   - Purely local (never synced): Delete immediately
///   - Server device: Mark `isDeleted = true, isSynced = false`
/// - **Status Updates**: Mark `isSynced = false` for connection/WiFi changes
///
/// ### Sync Process (Replace-All Policy)
/// 1. **Push Local Changes**: Send unsynced creates/edits/deletes to server
/// 2. **Pull Server State**: Fetch fresh data from server
/// 3. **Replace Local Storage**: Replace synced devices with server state, preserve unsynced
/// 4. **Update UI**: Refresh published scales
///
/// ### Error Handling
/// - **Network failures**: Changes remain `isSynced = false` for retry
/// - **Server errors**: Local changes preserved until successful sync
/// - **Deletion conflicts**: Devices marked for deletion retry on next sync
/// - **Sync failures**: Unsynced local devices are never overwritten by server data
/// - **Conflict resolution**: Local unsynced changes take precedence over server data
///
/// Handles local/remote sync, per-account operations, and robust error handling.
@MainActor
final class ScaleService: ObservableObject, @preconcurrency ScaleServiceProtocol { // swiftlint:disable:this type_body_length
    static let shared = ScaleService()
    private let tag = "ScaleService"
    
    @MainActor
    private lazy var remoteRepo: ScaleAPIRepository = {
        // Ensure this is always created on the main actor
        return _apiRepository
    }()
    private let _apiRepository: ScaleAPIRepository
    private let localRepository: ScaleRepository
    private let localKVRepo: ScaleRepositoryLocal
    private let accountService: AccountServiceProtocol
    private let logger = LoggerService.shared
    private let migrationService = ScaleMigrationService()
    private var isSyncing = false
    private var cancellables = Set<AnyCancellable>()
    private var lastAccountId: String?
    private var isInitialized = false
    
    // MARK: - Published State
    @Published private(set) var scales: [Device] = []
    
    /// Clears all scale data from local storage.
    func clearAllData() async {
        do {
            try await localRepository.clearAllData()
            logger.log(level: .info, tag: tag, message: "Successfully cleared all scale data")
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to clear scale data: \(error.localizedDescription)")
        }
    }
    
    /// Default initializer that creates its own dependencies.
    init() {
        self.accountService = AccountService.shared
        self._apiRepository = ScaleAPIRepository()
        self.localRepository = ScaleRepository()
        self.localKVRepo = ScaleRepositoryLocal()
        Task {
            await refreshScalesFromLocal()
            // Reset all connection statuses to false on app launch
            // Only Bluetooth events (DEVICE_CONNECTED) should set them to true
            await resetAllConnectionStatusOnLaunch()
            
            // Trigger sync on app launch to fetch scales from server
            if let accountId = await MainActor.run(body: { accountService.activeAccount?.accountId }) {
                lastAccountId = accountId
                await syncAllScalesWithRemote()
            }
            // Mark initialization complete after lastAccountId is set
            isInitialized = true
        }
        
        // React to active account changes so the scales list reflects the correct account immediately.
        if let concreteAccountService = accountService as? AccountService {
            concreteAccountService.$activeAccount
                .receive(on: DispatchQueue.main)
                .sink { [weak self] (newAccount: Account?) in
                    guard let self else { return }
                    Task { @MainActor in
                        // Ignore account changes until initialization completes to avoid race condition
                        guard self.isInitialized else { return }
                        
                        let currentAccountId = newAccount?.accountId
                        let accountIdChanged = currentAccountId != self.lastAccountId
                        
                        // Clear current list to avoid showing stale devices while switching
                        self.scales = []
                        // Refresh from local storage scoped to the new active account
                        await self.refreshScalesFromLocal()
                        // Trigger sync to fetch scales from server for the new account
                        await self.syncAllScalesWithRemote()
                        
                        // Only reset connection statuses when switching to a different, non-nil account (not on logout)
                        if accountIdChanged, let currentAccountId = newAccount?.accountId {
                            await self.resetAllConnectionStatusOnLaunch()
                            // Update lastAccountId when resetting
                            self.lastAccountId = currentAccountId
                        } else {
                            // Update lastAccountId even if we don't reset
                            self.lastAccountId = newAccount?.accountId
                        }
                    }
                }
                .store(in: &cancellables)
        }
    }
    
    /// Initializes the scale service with required dependencies.
    init(accountService: AccountServiceProtocol,
         apiRepository: ScaleAPIRepository,
         localRepository: ScaleRepository,
         localKVRepo: ScaleRepositoryLocal = ScaleRepositoryLocal()) {
        self.accountService = accountService
        self._apiRepository = apiRepository
        self.localRepository = localRepository
        self.localKVRepo = localKVRepo
    }
    
    var scalesPublisher: AnyPublisher<[Device], Never> {
        $scales.eraseToAnyPublisher()
    }
    
    // MARK: - Sync Logic
    /// Syncs all scales with the remote backend using the "replace-all" policy.
    /// This is the main sync method that should be called on app start or after network recovery.
    ///
    /// **Critical**: Unsynced local devices are NEVER overwritten by server data.
    /// This ensures local changes are preserved even if sync fails.
    ///
    /// Sync Process:
    /// 1. Push local changes (creates, edits, deletes) to server
    /// 2. Fetch fresh server state
    /// 3. Replace only synced devices with server state, preserve unsynced local devices
    public func syncAllScalesWithRemote() async {
        let accountId: String
        do {
            accountId = try await getAccountId()
        } catch {
            logger.log(level: .info, tag: tag, message: "Scale sync skipped: no active account")
            return
        }
        logger.log(level: .info, tag: tag, message: "Scale sync started: accountId=\(accountId)")
        if isSyncing {
            logger.log(level: .info, tag: tag, message: "Sync already in progress, skipping")
            return
        }
        isSyncing = true
        
        // Step 1: Push local changes to server
        await pushLocalChangesToServer()
        
        // Step 2: Fetch fresh server state and replace local storage
        await pullServerStateAndReplace(accountId: accountId)
        
        // Step 3: Refresh published scales
        await refreshScalesFromLocal()
        
        // Step 4: Update connection status from connected devices map
        // This ensures connection status is accurate after sync, especially for newly saved scales
        // where connection status might have been lost during server sync
        do {
            try await updateAllScalesStatus()
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to update scales status after sync: \(error.localizedDescription)")
        }
        
        isSyncing = false
        
        // Log scale count and info after sync
        let scaleCount = scales.count
        logger.log(level: .info, tag: tag, message: "Scale sync completed: accountId=\(accountId), scalesCount=\(scaleCount)")
    }
    
    // MARK: - DeviceServiceProtocol Implementation
    func updateScaleMeta(_ deviceId: String, metaData: DeviceMetaData) async throws {
        guard try await localRepository.getDevice(deviceId) != nil else {
            throw ScaleError.deviceNotFound(id: deviceId)
        }
        
        // Update on server immediately
        do {
            try await remoteRepo.patchScaleMeta(deviceId, metaData: metaData.toDTO())
            logger.log(level: .info, tag: tag, message: "Updated scale meta for device \(deviceId) locally and on server")
            metaData.isSynced = true // Mark as synced after successful server update
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to update scale meta on server: \(error.localizedDescription)")
            metaData.isSynced = false // Mark as unsynced if server update fails
        }
        
        // Update locally and mark as synced or unsynced based on server update success
        try await localRepository.patchScaleMeta(deviceId, metaData: metaData)
        await pushLocalChangesToServer()
    }
    
    func updateScalePreference(_ deviceId: String, _ preference: R4ScalePreference) async throws {
        // Convert to DTO to avoid mutating @Model across async boundaries (R9)
        let dto = preference.toDTO()
        try await updateScalePreference(deviceId, fromDTO: dto)
    }

    /// Updates scale preference from a DTO. This is the async-boundary-safe variant.
    /// Callers should use this when they need to pass preference data across await points.
    func updateScalePreference(_ deviceId: String, fromDTO dto: R4ScalePreferenceDTO) async throws {
        guard try await localRepository.getDevice(deviceId) != nil else {
            throw ScaleError.deviceNotFound(id: deviceId)
        }
        var mutableDTO = dto
        do {
            try await remoteRepo.patchScalePreference(dto)
            logger.log(level: .info, tag: tag, message: "Updated scale preference (DTO) for device \(deviceId) locally and on server")
            mutableDTO.isSynced = true
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to update scale preference on server: \(error.localizedDescription)")
            mutableDTO.isSynced = false
        }
        try await localRepository.patchScalePreference(deviceId, fromDTO: mutableDTO)
        await pushLocalChangesToServer()
    }

    // MARK: - DeviceServiceProtocol Implementation
    func getDevices() async throws -> [Device] {
        let accountId = try await getAccountId()
        
        // Get devices for the current account
        let localDevices = try await localRepository.listScales(forAccountId: accountId)
        
        // Filter out deleted devices for the UI
        let activeDevices = localDevices.filter { device in
            device.isSoftDeleted != true
        }
        
        return activeDevices
    }
    
    func getDevice(by deviceId: String) async throws -> Device? {
        return try await localRepository.getDevice(deviceId)
    }
    
    /// Returns a dictionary of connected devices keyed by broadcastId.
    /// - Note: Removed `nonisolated` - class is already @MainActor, no need for extra isolation.
    /// Only returns connected devices for the current active account to prevent
    /// cross-account connection status contamination when switching accounts.
    func getConnectedDevices() async -> [String: Any] {
        // Get current active accountId - CRITICAL: Only return devices for current account
        let currentAccountId: String?
        do {
            currentAccountId = try await getAccountId()
        } catch {
            currentAccountId = nil
        }
        
        guard let accountId = currentAccountId else {
            return [:]
        }
        
        // Filter by accountId to prevent cross-account contamination
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { 
            $0.isConnected == true && $0.accountId == accountId 
        })
        do {
            let connectedDevices = try localRepository.context.fetch(descriptor)
            var connectedDevicesDict: [String: Any] = [:]
            for device in connectedDevices {
                if let broadcastId = device.broadcastIdString {
                    connectedDevicesDict[broadcastId] = [
                        "id": device.id,
                        "name": device.deviceName ?? "",
                        "nickname": device.nickname ?? "",
                        "type": device.deviceType ?? "",
                        "isWifiConfigured": device.isWifiConfigured ?? false,
                        "wifiMac": device.wifiMac ?? ""
                    ]
                }
            }
            return connectedDevicesDict
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to fetch connected devices: \(error.localizedDescription)")
            return [:]
        }
    }
    
    /// Updates connection status for devices matching the given device info.
    /// - Note: Removed `nonisolated` - class is already @MainActor, no need for extra isolation.
    func updateConnectedDevices( // swiftlint:disable:this cyclomatic_complexity function_body_length
        device: Any,
        isConnected: Bool
    ) async {
        // Get current active accountId - CRITICAL: Only update devices for current account
        // Multiple accounts can have devices with same MAC/BroadcastID
        let currentAccountId: String?
        do {
            currentAccountId = try await getAccountId()
        } catch {
            currentAccountId = nil
        }

        guard let accountId = currentAccountId else {
            return
        }

        // Try to extract device ID from different possible data formats
        var deviceId: String?
        var broadcastId: String?
        var isWifiConfigured: Bool = false
        if let deviceDict = device as? [String: Any] {
            deviceId = deviceDict["id"] as? String
            broadcastId = deviceDict["broadcastId"] as? String
            isWifiConfigured = deviceDict["isWifiConfigured"] as? Bool ?? false
        } else if let deviceDetails = device as? GGDeviceDetails {
            // GGDeviceDetails doesn't have an 'id' property, use broadcastId instead
            broadcastId = deviceDetails.broadcastId ?? deviceDetails.broadcastIdString
            isWifiConfigured = deviceDetails.isWifiConfigured ?? false
        }

        var devicesUpdated = 0

        // If we have a device ID, try to update by ID first (scoped to current account)
        if let deviceId = deviceId {
            let descriptor = FetchDescriptor<Device>(predicate: #Predicate {
                $0.id == deviceId && $0.accountId == accountId
            })
            do {
                let devices = try localRepository.context.fetch(descriptor)
                for device in devices {
                    device.isConnected = isConnected
                    device.isWifiConfigured = isWifiConfigured
                    devicesUpdated += 1
                }
                if devicesUpdated > 0 {
                    try localRepository.context.save()
                    logger.log(
                        level: .info,
                        tag: tag,
                        message: "Updated \(devicesUpdated) device(s) connection status by ID: \(deviceId), connected: \(isConnected)"
                    )
                }
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to update device by ID: \(error.localizedDescription)")
            }
        }

        // Also try to update by broadcast ID (scoped to current account)
        // CRITICAL: Only update devices for current account, not all accounts
        if let broadcastId = broadcastId {
            let descriptor = FetchDescriptor<Device>(predicate: #Predicate {
                $0.broadcastIdString == broadcastId && $0.accountId == accountId
            })
            do {
                let devices = try localRepository.context.fetch(descriptor)
                for device in devices {
                    device.isConnected = isConnected
                    device.isWifiConfigured = isWifiConfigured
                    devicesUpdated += 1
                }

                if !devices.isEmpty {
                    try localRepository.context.save()
                    logger.log(
                        level: .info,
                        tag: tag,
                        message: "Updated \(devices.count) device(s) connection status by broadcast ID: \(broadcastId), connected: \(isConnected)"
                    )
                }
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to update device by broadcast ID: \(error.localizedDescription)")
            }
        }

        // Refresh scales to update UI if any devices were updated
        if devicesUpdated > 0 {
            Task {
                await self.refreshScalesFromLocal()
            }
        } else {
            // If we couldn't find any devices, log the error
            logger.log(
                level: .error,
                tag: tag,
                message: "Device not found for connection update. Device ID: \(deviceId ?? "nil"), " +
                    "Broadcast ID: \(broadcastId ?? "nil"), AccountId: \(accountId)"
            )
        }
    }
    
    /// Updates WiFi configuration status for a device by broadcast ID.
    /// - Note: Removed `nonisolated` - class is already @MainActor, no need for extra isolation.
    func updateConnectedDeviceWifiStatus(broadcastId: String, isConfigured: Bool) async {
        // Get current active accountId - CRITICAL: Only update devices for current account
        let currentAccountId: String?
        do {
            currentAccountId = try await getAccountId()
        } catch {
            currentAccountId = nil
        }

        guard let accountId = currentAccountId else {
            return
        }

        let descriptor = FetchDescriptor<Device>(predicate: #Predicate {
            $0.broadcastIdString == broadcastId && $0.accountId == accountId
        })
        do {
            if let device = try localRepository.context.fetch(descriptor).first {
                device.isWifiConfigured = isConfigured
                try localRepository.context.save()
            } else {
                logger.log(level: .error, tag: tag, message: "Device not found with broadcast ID: \(broadcastId), accountId: \(accountId)")
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to update device WiFi configuration status: \(error.localizedDescription)")
        }
    }

    /// Updates weight-only mode status for a device by broadcast ID.
    /// - Note: Removed `nonisolated` - class is already @MainActor, no need for extra isolation.
    func updateConnectedDeviceWeightOnlyMode(broadcastId: String, isWeightOnlyModeEnabledByOthers: Bool) async {
        // Get current active accountId - CRITICAL: Only update devices for current account
        let currentAccountId: String?
        do {
            currentAccountId = try await getAccountId()
        } catch {
            currentAccountId = nil
        }

        guard let accountId = currentAccountId else {
            return
        }

        let descriptor = FetchDescriptor<Device>(predicate: #Predicate {
            $0.broadcastIdString == broadcastId && $0.accountId == accountId
        })
        do {
            if let device = try localRepository.context.fetch(descriptor).first {
                device.isWeighOnlyModeEnabledByOthers = isWeightOnlyModeEnabledByOthers
                device.isSynced = false
                try localRepository.context.save()
                logger.log(
                    level: .debug,
                    tag: tag,
                    message: "Updated weight-only mode status for device \(broadcastId): \(isWeightOnlyModeEnabledByOthers)"
                )
            } else {
                logger.log(level: .error, tag: tag, message: "Device not found with broadcast ID: \(broadcastId), accountId: \(accountId)")
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to update device weight-only mode status: \(error.localizedDescription)")
        }
    }

    // MARK: - Preference Fetching
    /// Fetches an attached R4 scale preference by its scale ID from the repository.
    func fetchAttachedPreference(by id: String) async -> R4ScalePreference? {
        return localRepository.fetchAttachedPreference(by: id)
    }

    /// Synchronous variant to fetch an attached R4 scale preference by its scale ID.
    func fetchAttachedPreferenceSync(by id: String) -> R4ScalePreference? {
        return localRepository.fetchAttachedPreferenceSync(by: id)
    }
    // MARK: - Public Sync Methods
    
    /// Manually triggers a full sync with the server.
    ///
    /// **When to call this:**
    /// - App startup/foreground
    /// - Network connectivity restored
    /// - After critical operations (device pairing, etc.)
    /// - Periodic background sync
    ///
    
    func syncDevices(tempDevice: Device?) async throws {
        // If there's a temp device, add it locally first
        if let tempDevice = tempDevice {
            let accountId = try await getAccountId()
            let existingDevices = try await localRepository.listScales(forAccountId: accountId)
            let existingDevice = existingDevices.first { localDevice in
                // Check by ID first
                if localDevice.id == tempDevice.id { return true }
                // Then check by other identifiers
                return isDuplicateDevice(device: localDevice, remoteDTO: tempDevice.toDTO())
            }
            
            if existingDevice == nil {
                do {
                    _ = try await localRepository.createScale(tempDevice)
                    logger.log(level: .info, tag: tag, message: "Created temp device \(tempDevice.id)")
                } catch {
                    logger.log(level: .error, tag: tag, message: "Failed to create temp device: \(error.localizedDescription)")
                    throw ScaleError.apiSyncFailed(error)
                }
            }
        }
        
        // Use the main sync method for clean state management
        await syncAllScalesWithRemote()
    }
    
    func createDevice(_ device: Device, _ skipDuplicateCheck: Bool = false) async throws -> Device {
        let accountId = try await getAccountId()
        let existingDevices = try await localRepository.listScales(forAccountId: accountId)
        
        // Check for existing device more thoroughly
        let existingDevice = skipDuplicateCheck ? nil : existingDevices.first { localDevice in
            // Check by ID
            if localDevice.id == device.id { return true }
            // Check by broadcastIdString
            if let localBid = localDevice.broadcastIdString,
               let newBid = device.broadcastIdString,
               !localBid.isEmpty,
               localBid == newBid {
                return true
            }
            // Check by MAC
            if let localMac = localDevice.mac, let newMac = device.mac, !localMac.isEmpty, localMac == newMac { return true }
            return false
        }
        
        if let existingDevice = existingDevice {
            logger.log(level: .info, tag: tag, message: "Device already exists, returning existing device: \(existingDevice.id)")
            return existingDevice
        }
        
        // Create locally and mark as unsynced - sync will handle server creation
        let createdDevice = try await createScaleInLocal(device)
        logger.log(level: .info, tag: tag, message: "Created device \(device.id) locally, will sync to server", data: scaleLogDescriptor(createdDevice))
        await refreshScalesFromLocal()
        return createdDevice
    }
    
    /// Helper method to create an R4 scale with all required relationships properly set up.
    /// This ensures SwiftData relationships are established correctly to avoid crashes.
    /// - Parameters:
    ///   - scaleId: Unique identifier for the scale
    ///   - accountId: Account ID for the scale
    ///   - displayName: Display name for the scale
    ///   - token: Scale token for authentication
    ///   - mac: MAC address of the scale
    ///   - broadcastIdString: Broadcast ID string
    ///   - broadcastId: Broadcast ID as Int64
    ///   - sku: Scale SKU
    ///   - deviceName: Device name
    ///   - wifiMac: WiFi MAC address (optional)
    ///   - deviceMetadata: Device metadata (optional)
    ///   - isWifiConfigured: Whether WiFi is configured
    ///   - isConnected: Whether scale is connected
    ///   - skipDuplicateCheck: Whether to skip duplicate checking
    /// - Returns: The created Device
    func createR4Scale( // swiftlint:disable:this function_parameter_count
        scaleId: String,
        accountId: String,
        displayName: String,
        token: String,
        mac: String?,
        broadcastIdString: String?,
        broadcastId: Int64?,
        sku: String?,
        deviceName: String?,
        wifiMac: String? = nil,
        deviceMetadata: DeviceMetaData? = nil,
        isWifiConfigured: Bool = false,
        isConnected: Bool = false,
        skipDuplicateCheck: Bool = false
    ) async throws -> Device {
        // Create the main device
        let device = Device(
            id: scaleId,
            accountId: accountId,
            sku: sku,
            mac: mac,
            deviceName: deviceName,
            deviceType: DeviceType.scale.rawValue,
            broadcastId: broadcastId,
            broadcastIdString: broadcastIdString,
            userNumber: "0",
            createdAt: DateTimeTools.getCurrentDatetimeIsoString(),
            isConnected: isConnected,
            wifiMac: wifiMac,
            isWifiConfigured: isWifiConfigured,
            token: token,
            metaData: deviceMetadata
        )
        device.nickname = "AccuCheck Verve Smart Scale"
        device.peripheralIdentifier = mac?.replacingOccurrences(of: ":", with: "") ?? ""
        
        // Create bath scale relationship
        let bathScale = BathScale(
            scaleType: ScaleSourceType.btWifiR4.rawValue,
            bodyComp: true
        )
        device.bathScale = bathScale
        
        // Create R4 preference relationship
        let r4Preference = R4ScalePreference(
            scaleId: scaleId,
            displayName: displayName,
            displayMetrics: ScaleMetrics.defaultMetricsKeys,
            shouldFactoryReset: false,
            shouldMeasureImpedance: true,
            shouldMeasurePulse: false,
            timeFormat: "12",
            tzOffset: DateTimeTools.getTimeZoneInMinutes(),
            wifiFotaScheduleTime: 0,
            updatedAt: DateTimeTools.getCurrentDatetimeIsoString()
        )
        r4Preference.isSynced = false
        device.r4ScalePreference = r4Preference
        
        // Create and save the device with all relationships
        return try await createDevice(device, skipDuplicateCheck)
    }
    
    func createScaleInLocal(_ device: Device) async throws -> Device {
        return try await localRepository.createScale(device)
    }
    
    func editDevice(_ deviceId: String, properties: [String: Any]) async throws -> Device {
        guard (try await localRepository.getDevice(deviceId)) != nil else {
            throw ScaleError.deviceNotFound(id: deviceId)
        }
        
        // Edit locally and mark as unsynced - sync will handle server update
        let updatedDevice = try await localRepository.editScale(deviceId, properties: properties)
        logger.log(level: .info, tag: tag, message: "Edited device \(deviceId) locally, will sync to server")
        
        await refreshScalesFromLocal()
        return updatedDevice
    }
    
    func deleteDevice(_ deviceId: String, showToast: Bool) async throws {
        guard let target = try await localRepository.getDevice(deviceId) else {
            throw ScaleError.deviceNotFound(id: deviceId)
        }
        
        // Collect duplicates by mac/broadcastIdString (including self)
        var candidates: [Device] = [target]
        let mac = target.mac
        let bid = target.broadcastIdString
        if let mac = mac, !mac.isEmpty, let bid = bid, !bid.isEmpty {
            // Both mac and broadcastIdString present: single compound OR fetch
            let compound = #Predicate<Device> { $0.mac == mac || $0.broadcastIdString == bid }
            let descriptor = FetchDescriptor<Device>(predicate: compound)
            if let others = try? localRepository.context.fetch(descriptor) { candidates.append(contentsOf: others) }
        } else if let mac = mac, !mac.isEmpty {
            let byMac = FetchDescriptor<Device>(predicate: #Predicate { $0.mac == mac })
            if let others = try? localRepository.context.fetch(byMac) { candidates.append(contentsOf: others) }
        } else if let bid = bid, !bid.isEmpty {
            let byBid = FetchDescriptor<Device>(predicate: #Predicate { $0.broadcastIdString == bid })
            if let others = try? localRepository.context.fetch(byBid) { candidates.append(contentsOf: others) }
        }
        
        // Deduplicate list by id
        var seen = Set<String>()
        candidates = candidates.filter { dev in
            let keep = !seen.contains(dev.id)
            seen.insert(dev.id)
            return keep
        }
        
        var didChange = false
        for device in candidates {
            // Check if this is a purely local device (never synced to server)
            let isPurelyLocal = try await localRepository.isDevicePurelyLocal(device.id)
            
            if isPurelyLocal {
                // Purely local device - delete immediately from local storage
                try await localRepository.deleteScale(device.id)
                logger.log(level: .info, tag: tag, message: "Deleted purely local device \(device.id)", data: scaleLogDescriptor(device))
                didChange = true
            } else {
                // Device exists on server - mark for deletion and let sync handle it
                try await localRepository.markDeviceAsDeleted(device.id)
                logger.log(level: .info, tag: tag, message: "Marked device \(device.id) for deletion", data: scaleLogDescriptor(device))
                didChange = true
            }
        }
        
        await refreshScalesFromLocal()
        if didChange {
            await pushLocalChangesToServer()
        }
    }
    
    func updateAllScalesStatus(_ scales: [Device]? = nil) async throws {
        // Get current active accountId - CRITICAL: Only update devices for current account
        let accountId = try await getAccountId()
        
        // Determine which device list to process. If none provided, fetch all scales from local storage for current account.
        let deviceList: [Device]
        if let providedScales = scales {
            // Filter provided scales to only include devices for current account
            deviceList = providedScales.filter { $0.accountId == accountId && $0.isSoftDeleted != true }
        } else {
            // Only fetch scales for the current account
            deviceList = try await localRepository.listScales(forAccountId: accountId).filter { $0.isSoftDeleted != true }
        }
        
        // Fetch a map of currently connected devices keyed by broadcastIdString (already filtered by accountId)
        let connectedDevices = await getConnectedDevices()
        
        // Iterate over each scale and refresh its status fields
        for device in deviceList {
            // Double-check accountId to prevent cross-account updates
            guard device.accountId == accountId else {
                continue
            }
            
            // Reset flags before evaluation
            device.isConnected = false
            device.isWifiConfigured = false
            
            // Ensure broadcastIdString is populated so that look-ups work reliably
            if device.broadcastIdString?.isEmpty != false {
                if let bidInt64 = device.broadcastId {
                    let scaleSource = ScaleSourceType(rawValue: device.deviceType ?? "") ?? .bluetoothScale
                    let protocolType = ProtocolConversionTools.getProtocolTypeFromScaleType(scaleType: scaleSource)
                    device.broadcastIdString = ProtocolConversionTools.convertIntToHex(Int(bidInt64), protocolType: protocolType)
                }
            }
            
            // Update connection + Wi-Fi flags based on the connectedDevices map
            if let bidString = device.broadcastIdString,
               let connectedDetails = connectedDevices[bidString] as? [String: Any] {
                device.isConnected = true
                device.isWifiConfigured = (connectedDetails["isWifiConfigured"] as? Bool) ?? false
            }
        }
        
        // Persist the updates
        do {
            try localRepository.context.save()
            await refreshScalesFromLocal()
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to save updated device statuses: \(error.localizedDescription)")
            throw error
        }
    }
    
    // MARK: - Public Convenience
    /// Refreshes all scales status (connection, Wi-Fi, etc.) for every stored device.
    public func updateScaleStatus() async {
        try? await updateAllScalesStatus(nil)
    }
    
    // MARK: - Internal Helpers
    
    /// Resets all scale connection statuses to false on app launch.
    /// Connection status is ephemeral and should only be true when confirmed by Bluetooth events.
    private func resetAllConnectionStatusOnLaunch() async {
        do {
            let accountId = try await getAccountId()
            let allScales = try await localRepository.listScales(forAccountId: accountId)
            
            for scale in allScales {
                scale.isConnected = false
                scale.isWifiConfigured = false
            }
            try localRepository.context.save()
            logger.log(level: .info, tag: tag, message: "Reset connection status on launch: scalesCount=\(allScales.count)")
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to reset connection statuses on launch: \(error.localizedDescription)")
        }
    }
    
    private func refreshScalesFromLocal() async {
        do {
            let accountId = try await getAccountId()
            let previousSnapshot = self.scales.map { scaleLogDescriptor($0) }.sorted()
            let allScales = try await localRepository.listScales(forAccountId: accountId)
            let activeScales = allScales.filter { $0.isSoftDeleted != true }
            self.scales = activeScales
            let currentSnapshot = activeScales.map { scaleLogDescriptor($0) }.sorted()
            if previousSnapshot != currentSnapshot {
                logger.log(
                    level: .info,
                    tag: tag,
                    message: "Paired scale list changed. accountId=\(accountId), count=\(activeScales.count)",
                    data: currentSnapshot
                )
            }
        } catch {
            self.logger.log(level: .error, tag: self.tag, message: "Failed to refresh scales: \(error.localizedDescription)")
        }
    }
    private func scaleLogDescriptor(_ device: Device) -> String {
        let preference = fetchAttachedPreferenceSync(by: device.id)
        let preferenceDisplayName = preference?.displayName ?? "nil"
        let preferenceDisplayMetrics = preference?.displayMetrics.joined(separator: "|") ?? "nil"
        let preferenceFactoryReset = preference != nil ? String(preference?.shouldFactoryReset ?? false) : "nil"
        let preferenceImpedance = preference != nil ? String(preference?.shouldMeasureImpedance ?? false) : "nil"
        let preferencePulse = preference != nil ? String(preference?.shouldMeasurePulse ?? false) : "nil"
        let preferenceTimeFormat = preference?.timeFormat ?? "nil"
        let preferenceTzOffset = preference != nil ? String(preference?.tzOffset ?? 0) : "nil"
        let preferenceWifiFotaScheduleTime = preference?.wifiFotaScheduleTime.map(String.init) ?? "nil"
        let preferenceUpdatedAt = preference?.updatedAt ?? "nil"
        let preferenceIsSynced = preference != nil ? String(preference?.isSynced ?? false) : "nil"
        // swiftlint:disable:next line_length
        return "id=\(device.id), accountId=\(device.accountId), sku=\(device.sku ?? "nil"), deviceName=\(device.deviceName ?? "nil"), nickname=\(device.nickname ?? "nil"), mac=\(device.mac ?? "nil"), wifiMac=\(device.wifiMac ?? "nil"), password=\(device.password.map(String.init) ?? "nil"), token=\(device.token ?? "nil"), broadcastId=\(device.broadcastId.map(String.init) ?? "nil"), broadcastIdString=\(device.broadcastIdString ?? "nil"), peripheralIdentifier=\(device.peripheralIdentifier ?? "nil"), userNumber=\(device.userNumber ?? "nil"), protocolType=\(device.protocolType ?? "nil"), createdAt=\(device.createdAt ?? "nil"), isConnected=\(device.isConnected.map(String.init) ?? "nil"), isWifiConfigured=\(device.isWifiConfigured.map(String.init) ?? "nil"), isSynced=\(device.isSynced.map(String.init) ?? "nil"), hasServerID=\(device.hasServerID), isSoftDeleted=\(device.isSoftDeleted.map(String.init) ?? "nil"), prefDisplayName=\(preferenceDisplayName), prefDisplayMetrics=\(preferenceDisplayMetrics), prefShouldFactoryReset=\(preferenceFactoryReset), prefImpedance=\(preferenceImpedance), prefPulse=\(preferencePulse), prefTimeFormat=\(preferenceTimeFormat), prefTzOffset=\(preferenceTzOffset), prefWifiFotaScheduleTime=\(preferenceWifiFotaScheduleTime), prefUpdatedAt=\(preferenceUpdatedAt), prefIsSynced=\(preferenceIsSynced)"
    }
    
    @Sendable
    private func getAccountId() async throws -> String {
        try await MainActor.run {
            guard let accountId = accountService.activeAccount?.accountId else {
                throw AccountError.noActiveAccount
            }
            return accountId
        }
    }

    // Helper to check if a local device matches a remote device (for deduplication/conflict resolution)
    private func isDuplicateDevice(device: Device, remoteDTO: ScaleDTO) -> Bool {
        if let deviceBroadcastId = device.broadcastIdString,
           let remoteBroadcastId = remoteDTO.broadcastIdString,
           deviceBroadcastId == remoteBroadcastId {
            return true
        }
        if let deviceMac = device.mac,
           let remoteMac = remoteDTO.mac,
           deviceMac == remoteMac {
            return true
        }
        return false
    }
    
    /// Pushes all local changes (creates, edits, deletes) to the server.
    /// Follows the sync rules for proper state management.
    public func pushLocalChangesToServer() async { // swiftlint:disable:this cyclomatic_complexity function_body_length
        let accountId: String?
        do {
            accountId = try await getAccountId()
        } catch {
            accountId = nil
        }
        
        do {
            logger.log(level: .info, tag: tag, message: "Pushing local changes to server")
            var deletedCount = 0
            var updatedCount = 0
            var createdCount = 0
            var failedCount = 0
            // Handle deletions first
            let devicesMarkedForDeletion = try await localRepository.getDevicesMarkedForDeletion()
            for device in devicesMarkedForDeletion {
                do {
                    try await remoteRepo.deleteScale(device.id)
                    // Successfully deleted from server, remove from local storage
                    try await localRepository.permanentlyRemoveDevice(device.id)
                    deletedCount += 1
                } catch {
                    // Treat "Not found" as success; otherwise, log error and keep for retry
                    if error.localizedDescription.contains("Not found") {
                        try await localRepository.permanentlyRemoveDevice(device.id)
                        deletedCount += 1
                    } else {
                        failedCount += 1
                        logger.log(level: .error, tag: tag, message: "Failed to delete device \(device.id) on server: \(error.localizedDescription)")
                    }
                }
            }
            
            // Handle creates and edits
            let unsyncedDevices = try await localRepository.getUnsyncedDevices()
            for device in unsyncedDevices {
                // Skip devices already marked for deletion
                if device.isSoftDeleted == true { continue }
                
                let dto = device.toDTO()
                
                // Check if this device is purely local (never synced to server) or has a server ID
                let isPurelyLocal = device.hasServerID == false
                
                if !isPurelyLocal {
                    // Edit existing device on server
                    do {
                        if device.isSynced == false {
                            _ = try await remoteRepo.editScale(device.id, properties: dto)
                        }
                        // Update scale meta data and preference
                        if let metaData = device.metaData, metaData.isSynced == false {
                            try await remoteRepo.patchScaleMeta(device.id, metaData: metaData.toDTO())
                            metaData.isSynced = true
                        }
                        if let preference = device.r4ScalePreference, preference.isSynced == false {
                            try await remoteRepo.patchScalePreference(preference.toDTO())
                            preference.isSynced = true
                        }
                        device.isSynced = true
                        try await localRepository.updateDevice(device)
                        updatedCount += 1
                    } catch {
                        failedCount += 1
                        logger.log(level: .error, tag: tag, message: "Failed to update device \(device.id) on server: \(error.localizedDescription)")
                    }
                } else {
                    // Create new device on server
                    do {
                        var createdDTO: ScaleDTO?
                        if device.isSynced == false && device.hasServerID == true {
                            do {
                                _ = try await remoteRepo.editScale(device.id, properties: dto)
                            } catch {
                                failedCount += 1
                                logger.log(level: .error, tag: tag, message: "Failed to edit scale on server: \(error.localizedDescription)")
                            }
                        } else {
                            createdDTO = try await remoteRepo.createScale(dto)
                            createdCount += 1
                        }
                        // Update local device with server ID
                        let oldId = device.id
                        let newId = createdDTO?.id ?? device.id
                        
                        // Update scale meta data and preference
                        if let metaData = device.metaData, metaData.isSynced == false {
                            try await remoteRepo.patchScaleMeta(newId, metaData: metaData.toDTO())
                            metaData.isSynced = true
                        }
                        if let preference = device.r4ScalePreference, preference.isSynced == false {
                            var r4Preference = preference.toDTO()
                            r4Preference.scaleId = newId // Ensure scaleId is set for R4 preference
                            try await remoteRepo.patchScalePreference(r4Preference)
                            preference.isSynced = true
                        }
                        
                        // Update the device with new server ID and sync status
                        device.id = newId
                        device.hasServerID = true
                        device.isSynced = true
                        
                        // Use the old ID to find and update the device in the database
                        try await localRepository.updateDeviceWithNewId(oldId: oldId, updatedDevice: device)
                    } catch {
                        failedCount += 1
                        logger.log(level: .error, tag: tag, message: "Failed to create device on server: \(error.localizedDescription)")
                    }
                }
            }
            // Log scale count after pushing changes
            if let accountId = accountId {
                let scaleCount = try? await localRepository.listScales(forAccountId: accountId).filter { $0.isSoftDeleted != true }.count
                logger.log(
                    level: .info,
                    tag: tag,
                    message: """
                    Pushed local changes to server completed: accountId=\(accountId), deleted=\(deletedCount), \
                    updated=\(updatedCount), created=\(createdCount), failures=\(failedCount), scalesCount=\(scaleCount ?? 0)
                    """
                )
            } else {
                logger.log(
                    level: .info,
                    tag: tag,
                    message: """
                    Pushed local changes to server completed: deleted=\(deletedCount), updated=\(updatedCount), \
                    created=\(createdCount), failures=\(failedCount)
                    """
                )
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to push local changes to server: \(error.localizedDescription)")
        }
    }
    
    /// Fetches fresh server state and replaces local storage with it.
    /// This implements the "replace-all" policy for clean state management.
    /// Preserves any unsynced local devices to avoid losing local changes.
    private func pullServerStateAndReplace( // swiftlint:disable:this cyclomatic_complexity function_body_length
        accountId: String
    ) async {
        do {
            let serverScales = try await remoteRepo.listScales()
            
            // Get any unsynced local devices to preserve them
            let unsyncedDevices = try await localRepository.getUnsyncedDevices()
            
            // Replace synced devices with server state, preserve unsynced local devices
            try await localRepository.replaceAllDevicesForAccount(accountId, with: serverScales, preserveUnsynced: unsyncedDevices)
            await refreshScalesFromLocal()

            var corrected = 0
            for dto in serverScales {
                // Prefer match by server id, but ONLY if it belongs to the current account
                // Device IDs are globally unique, but we need to verify accountId matches
                if let devId = dto.id {
                    // Try to find device by ID AND accountId to ensure we don't match across accounts
                    let byIdAndAccount = FetchDescriptor<Device>(predicate: #Predicate { 
                        $0.id == devId && $0.accountId == accountId 
                    })
                    if (try? localRepository.context.fetch(byIdAndAccount).first) != nil {
                        // Device already has correct accountId, continue
                        continue
                    }
                    
                    // If device exists but with wrong accountId, that's an error - don't auto-correct
                    // because it might belong to a different account legitimately
                    if let device = try? await localRepository.getDevice(devId) {
                        if device.accountId != accountId {
                            continue
                        }
                    }
                }
                // Fallback: try match by MAC (scoped to current account)
                var matchedDevice: Device?
                if let mac = dto.mac, !mac.isEmpty {
                    let byMac = FetchDescriptor<Device>(predicate: #Predicate { $0.mac == mac && $0.accountId == accountId })
                    if let found = try? localRepository.context.fetch(byMac).first {
                        matchedDevice = found
                    }
                }
                
                // Fallback: try match by broadcastIdString (scoped to current account)
                if matchedDevice == nil, let bid = dto.broadcastIdString, !bid.isEmpty {
                    let byBid = FetchDescriptor<Device>(predicate: #Predicate { $0.broadcastIdString == bid && $0.accountId == accountId })
                    if let found = try? localRepository.context.fetch(byBid).first {
                        matchedDevice = found
                    }
                }
                
                if let device = matchedDevice {
                    // Normalize accountId
                    if device.accountId != accountId {
                        device.accountId = accountId
                        try await localRepository.updateDevice(device)
                        corrected += 1
                    }
                    // If server has an ID and local has a different ID, unify to server ID to avoid duplicates
                    if let serverId = dto.id, device.id != serverId {
                        let oldId = device.id
                        device.id = serverId
                        try await localRepository.updateDeviceWithNewId(oldId: oldId, updatedDevice: device)
                        corrected += 1
                    }
                }
            }
            if corrected > 0 {
                await refreshScalesFromLocal()
            }
            
            // Prune orphan purely-local devices that don't exist on server for this account
            // CRITICAL: Only prune devices that belong to current accountId
            // Multiple accounts can have devices with same MAC/SKU, so we must check accountId
            let serverIds = Set(serverScales.compactMap { $0.id })
            let serverMacs = Set(serverScales.compactMap { $0.mac }.filter { !$0.isEmpty })
            let serverBids = Set(serverScales.compactMap { $0.broadcastIdString }.filter { !$0.isEmpty })
            
            let localForAccount = try await localRepository.listScales(forAccountId: accountId)
            var pruned = 0
            for dev in localForAccount {
                // CRITICAL: Verify device belongs to current account before pruning
                guard dev.accountId == accountId else {
                    continue
                }
                
                // Only prune synced devices that don't match server
                // Keep unsynced devices even if they don't match (they might be new local creations)
                let isSynced = dev.isSynced ?? false
                if !isSynced {
                    continue
                }
                
                // If any server device matches by id/mac/broadcastId, keep it
                let matchesByMac = dev.mac.map { serverMacs.contains($0) } ?? false
                let matchesByBroadcastId = dev.broadcastIdString.map { serverBids.contains($0) } ?? false
                let matchesServer = serverIds.contains(dev.id) || matchesByMac || matchesByBroadcastId
                if !matchesServer {
                    // Orphan synced local device -> delete to align with server
                    try await localRepository.deleteScale(dev.id)
                    pruned += 1
                }
            }
            if pruned > 0 {
                await refreshScalesFromLocal()
            }
            logger.log(
                level: .info,
                tag: tag,
                message: """
                Pulled server scales and replaced local state: accountId=\(accountId), serverScales=\(serverScales.count), \
                preservedUnsynced=\(unsyncedDevices.count), corrected=\(corrected), pruned=\(pruned)
                """
            )
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to fetch server state and replace local storage: accountId=\(accountId), error=\(error.localizedDescription)")
        }
    }
    
    /// Helper method to create properties dictionary from DTO for API calls.
    private func createPropertiesFromDTO(_ dto: ScaleDTO) -> [String: Any] {
        var properties: [String: Any] = [:]
        
        // Only include nickname if it's a valid string to prevent type errors
        if let nickname = dto.nickname {
            properties["nickname"] = nickname
        }
        // Add Properties here in order to update the device
        return properties
    }
    
    /// Helper method to create a Bluetooth scale with all required relationships properly set up.
    /// This ensures SwiftData relationships are established correctly to avoid crashes.
    /// - Parameters:
    ///   - device: The Device object to save
    ///   - sku: Scale SKU
    ///   - userNumber: User number for the scale
    ///   - accountId: Account ID for the scale
    ///   - deviceMetadata: Device metadata (optional)
    ///   - skipDuplicateCheck: Whether to skip duplicate checking
    /// - Returns: The created Device
    func createBluetoothScale(
        device: Device,
        sku: String?,
        userNumber: String,
        accountId: String,
        deviceMetadata: DeviceMetaData? = nil,
        skipDuplicateCheck: Bool = false
    ) async throws -> Device {
        
        // Set up device properties (matching BluetoothService.addNewDevice logic)
        // device.id should already be set, but ensure it's not empty
        if device.id.isEmpty {
            device.id = UUID().uuidString
        }
        device.accountId = accountId
        device.sku = sku
        device.deviceType = DeviceType.scale.rawValue
        device.userNumber = userNumber
        device.createdAt = DateTimeTools.getCurrentDatetimeIsoString()
        device.nickname = device.nickname ?? "Bluetooth Smart Scale"
        device.metaData = deviceMetadata
        // Note: password is already set on the device from the pairing process
        
        // Create bath scale relationship if it doesn't exist
        if device.bathScale == nil {
            let bathScale = BathScale(
                scaleType: ScaleSourceType.bluetooth.rawValue,
                bodyComp: false
            )
            device.bathScale = bathScale
        } else {
            // Update scale type if bath scale exists
            device.bathScale?.scaleType = ScaleSourceType.bluetooth.rawValue
        }
        
        // Use the repository to create the scale with proper relationship handling
        let savedDevice = try await localRepository.createScale(device)
        
        // Sync devices after creation (matching original BluetoothService logic)
        try await syncDevices(tempDevice: nil)
        
        return savedDevice
    }
    
    /// Helper method to create an A6/LCBT scale with all required relationships properly set up.
    /// This ensures SwiftData relationships are established correctly to avoid crashes.
    /// - Parameters:
    ///   - device: The Device object to save
    ///   - sku: Scale SKU
    ///   - accountId: Account ID for the scale
    ///   - deviceMetadata: Device metadata (optional)
    ///   - skipDuplicateCheck: Whether to skip duplicate checking
    /// - Returns: The created Device
    func createA6Scale(
        device: Device,
        sku: String?,
        accountId: String,
        deviceMetadata: DeviceMetaData? = nil,
        skipDuplicateCheck: Bool = false
    ) async throws -> Device {
        
        // Set up device properties (matching BluetoothService.addNewDevice logic)
        device.accountId = accountId
        device.sku = sku
        device.deviceType = DeviceType.scale.rawValue
        device.createdAt = DateTimeTools.getCurrentDatetimeIsoString()
        device.nickname = device.nickname ?? "Bluetooth Smart Scale"
        device.metaData = deviceMetadata
        // Note: password is already set on the device from the pairing process
        
        // Create bath scale relationship if it doesn't exist
        if device.bathScale == nil {
            let bathScale = BathScale(
                scaleType: ScaleSourceType.lcbt.rawValue,
                bodyComp: device.bathScale?.bodyComp ?? false
            )
            device.bathScale = bathScale
        } else {
            // Update scale type if bath scale exists
            device.bathScale?.scaleType = ScaleSourceType.lcbt.rawValue
        }
        // Use the repository to create the scale with proper relationship handling
        let savedDevice = try await localRepository.createScale(device)
        
        // Sync devices after creation (matching original BluetoothService logic)
        try await syncDevices(tempDevice: nil)
        
        return savedDevice
    }
// swiftlint:disable:next file_length
}
