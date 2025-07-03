//
//  ScaleService.swift
//  meApp
//
//  Created by Lakshmi Priya on 02/06/25.
//

import Foundation
import SwiftData
import Combine

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
final class ScaleService: ObservableObject, @preconcurrency ScaleServiceProtocol {
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

    // MARK: - Published State
    @Published private(set) var scales: [Device] = []

    /// Default initializer that creates its own dependencies.
    init() {
        self.accountService = AccountService.shared
        self._apiRepository = ScaleAPIRepository()
        self.localRepository = ScaleRepository()
        self.localKVRepo = ScaleRepositoryLocal()

        // Load initial scales from local storage
        Task { await refreshScalesFromLocal() }
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

        // Load initial scales from local storage
        Task { await refreshScalesFromLocal() }
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
            logger.log(level: .error, tag: tag, message: "Failed to get account ID for sync: \(error.localizedDescription)")
            return
        }

        // Step 1: Push local changes to server
        await pushLocalChangesToServer(accountId: accountId)

        // Step 2: Fetch fresh server state and replace local storage
        await pullServerStateAndReplace(accountId: accountId)

        // Step 3: Refresh published scales
        await refreshScalesFromLocal()
    }

    // MARK: - DeviceServiceProtocol Implementation
    func updateScaleMeta(_ deviceId: String, metaData: DeviceMetaData) async throws {
        guard let _ = try await localRepository.getDevice(deviceId) else {
            throw ScaleError.deviceNotFound(id: deviceId)
        }

        // Update locally and mark as unsynced - sync will handle server update
        try await localRepository.patchScaleMeta(deviceId, metaData: metaData)
        logger.log(level: .info, tag: tag, message: "Updated scale meta for device \(deviceId) locally, will sync to server")
    }

    func updateScalePreference(_ deviceId: String, _ preference: R4ScalePreference) async throws {
        guard let _ = try await localRepository.getDevice(deviceId) else {
            throw ScaleError.deviceNotFound(id: deviceId)
        }
        // Update locally and mark as unsynced - sync will handle server update
        try await localRepository.patchScalePreference(deviceId, preference)
        logger.log(level: .info, tag: tag, message: "Updated scale preference for device \(deviceId) locally, will sync to server")
    }

    // MARK: - DeviceServiceProtocol Implementation
    func getDevices() async throws -> [Device] {
        // Simply return local devices - main sync handles server communication
        let localDevices = try await localRepository.listScales()

        // Filter out deleted devices for the UI
        let activeDevices = localDevices.filter { device in
            device.isDeleted != true
        }

        await MainActor.run { self.scales = activeDevices }
        return activeDevices
    }

    nonisolated func getConnectedDevices() async -> [String: Any] {
        return await MainActor.run {
            let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.isConnected == true })
            do {
                let connectedDevices = try localRepository.context.fetch(descriptor)
                var connectedDevicesDict: [String: Any] = [:]
                for device in connectedDevices {
                    if let broadcastId = device.broadcastId {
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
    }

    nonisolated func updateConnectedDevices(device: Any, isConnected: Bool) async {
        await MainActor.run {
            guard let deviceDict = device as? [String: Any],
                  let deviceId = deviceDict["id"] as? String else {
                logger.log(level: .error, tag: tag, message: "Invalid device data format")
                return
            }
            let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == deviceId })
            do {
                if let device = try localRepository.context.fetch(descriptor).first {
                    device.isConnected = isConnected
                    device.isSynced = false
                    try localRepository.context.save()
                } else {
                    logger.log(level: .error, tag: tag, message: "Device not found: \(deviceId)")
                }
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to update device connection status: \(error.localizedDescription)")
            }
        }
    }

    nonisolated func updateConnectedDeviceWifiStatus(broadcastId: String, isConfigured: Bool) async {
        await MainActor.run {
            let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.broadcastId == broadcastId })
            do {
                if let device = try localRepository.context.fetch(descriptor).first {
                    device.isWifiConfigured = isConfigured
                    device.isSynced = false
                    try localRepository.context.save()
                } else {
                    logger.log(level: .error, tag: tag, message: "Device not found with broadcast ID: \(broadcastId)")
                }
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to update device WiFi configuration status: \(error.localizedDescription)")
            }
        }
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
            let existingDevices = try await localRepository.listScales()
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

    func createDevice(_ device: Device) async throws -> Device {
        let dto = device.toDTO()
        let existingDevices = try await localRepository.listScales()

        // Check for existing device more thoroughly
        let existingDevice = existingDevices.first { localDevice in
            // Check by ID first
            if localDevice.id == device.id { return true }
            // Then check by other identifiers
            if let localBroadcastId = localDevice.broadcastIdString, let newBroadcastId = dto.broadcastIdString, localBroadcastId == newBroadcastId { return true }
            if let localMac = localDevice.mac, let newMac = dto.mac, localMac == newMac { return true }
            return false
        }

        if let existingDevice = existingDevice {
            logger.log(level: .info, tag: tag, message: "Device already exists, returning existing device: \(existingDevice.id)")
            return existingDevice
        }

        // Create locally and mark as unsynced - sync will handle server creation
        let createdDevice = try await localRepository.createScale(device)
        logger.log(level: .info, tag: tag, message: "Created device \(device.id) locally, will sync to server")

        await refreshScalesFromLocal()
        return createdDevice
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
        guard (try await localRepository.getDevice(deviceId)) != nil else {
            throw ScaleError.deviceNotFound(id: deviceId)
        }

        // Check if this is a purely local device (never synced to server)
        let isPurelyLocal = try await localRepository.isDevicePurelyLocal(deviceId)

        if isPurelyLocal {
            // Purely local device - delete immediately from local storage
            try await localRepository.deleteScale(deviceId)
            logger.log(level: .info, tag: tag, message: "Deleted purely local device \(deviceId)")
        } else {
            // Device exists on server - mark for deletion and let sync handle it
            try await localRepository.markDeviceAsDeleted(deviceId)
            logger.log(level: .info, tag: tag, message: "Marked device \(deviceId) for deletion")
        }

        await refreshScalesFromLocal()
    }

    func updateAllScalesStatus(_ scales: [Device]? = nil) async throws {
        // Determine which device list to process. If none provided, fetch all scales from local storage.
        let deviceList: [Device]
        if let providedScales = scales {
            deviceList = providedScales
        } else {
            // Fetch all locally stored scale managed objects
            let descriptor = FetchDescriptor<Device>()
            deviceList = try localRepository.context.fetch(descriptor)
        }

        // Fetch a map of currently connected devices keyed by broadcastIdString
        let connectedDevices = await getConnectedDevices()

        // Iterate over each scale and refresh its status fields
        for device in deviceList {
            // Reset flags before evaluation
            device.isConnected = false
            device.isWifiConfigured = false

            // Ensure broadcastIdString is populated so that look-ups work reliably
            if (device.broadcastIdString == nil || device.broadcastIdString?.isEmpty == true) {
                if let bidStr = device.broadcastId, let bidInt = Int(bidStr) {
                    let scaleSource = ScaleSourceType(rawValue: device.deviceType ?? "") ?? .bluetoothScale
                    let protocolType = ProtocolConversionTools.getProtocolTypeFromScaleType(scaleType: scaleSource,
                                                                                      sku: device.sku ?? "")
                    device.broadcastIdString = ProtocolConversionTools.convertIntToHex(bidInt, protocolType: protocolType)
                }
            }

            // Update connection + Wi-Fi flags based on the connectedDevices map
            if let bidString = device.broadcastIdString,
               let connectedDetails = connectedDevices[bidString] as? [String: Any] {
                device.isConnected = true
                device.isWifiConfigured = (connectedDetails["isWifiConfigured"] as? Bool) ?? false
            }

            // Mark device as needing sync since local status changed
            device.isSynced = false
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
    private func refreshScalesFromLocal() async {
        await MainActor.run {
            do {
                let descriptor = FetchDescriptor<Device>()
                let devices = try localRepository.context.fetch(descriptor)
                self.scales = devices
            } catch {
                self.logger.log(level: .error, tag: self.tag, message: "Failed to refresh scales: \(error.localizedDescription)")
            }
        }
    }

    @Sendable
    private func getAccountId() async throws -> String {
        guard let account = try await accountService.getActiveAccount() else {
            throw AccountError.noActiveAccount
        }
        return String(describing: account.id)
    }

    // Helper to check if a local device matches a remote device (for deduplication/conflict resolution)
    private func isDuplicateDevice(device: Device, remoteDTO: ScaleDTO) -> Bool {
        if let deviceBroadcastId = device.broadcastIdString, let remoteBroadcastId = remoteDTO.broadcastIdString, deviceBroadcastId == remoteBroadcastId { return true }
        if let deviceMac = device.mac, let remoteMac = remoteDTO.mac, deviceMac == remoteMac { return true }
        if let deviceSku = device.sku, let remoteSku = remoteDTO.sku, deviceSku == remoteSku { return true }
        return false
    }

        /// Pushes all local changes (creates, edits, deletes) to the server.
    /// Follows the sync rules for proper state management.
    private func pushLocalChangesToServer(accountId: String) async {
        do {
            // Handle deletions first
            let devicesMarkedForDeletion = try await localRepository.getDevicesMarkedForDeletion()
            for device in devicesMarkedForDeletion {
                do {
                    try await remoteRepo.deleteScale(device.id)
                    // Successfully deleted from server, remove from local storage
                    try await localRepository.permanentlyRemoveDevice(device.id)
                    logger.log(level: .info, tag: tag, message: "Successfully deleted device \(device.id) from server")
                } catch {
                    logger.log(level: .error, tag: tag, message: "Failed to delete device \(device.id) from server: \(error.localizedDescription)")
                    // Leave the device marked for deletion to retry later
                }
            }

            // Handle creates and edits
            let unsyncedDevices = try await localRepository.getUnsyncedDevices()
            for device in unsyncedDevices {
                // Skip devices already marked for deletion
                if device.isDeleted == true { continue }

                let dto = device.toDTO()

                // Check if this device is purely local (never synced to server) or has a server ID
                let isPurelyLocal = device.hasServerID == false && device.isSynced == false

                if !isPurelyLocal {
                    // Edit existing device on server
                    do {
                        let properties = createPropertiesFromDTO(dto)
                        _ = try await remoteRepo.editScale(device.id, properties: properties)
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
                        logger.log(level: .info, tag: tag, message: "Successfully updated device \(device.id) on server")
                    } catch {
                        logger.log(level: .error, tag: tag, message: "Failed to update device \(device.id) on server: \(error.localizedDescription)")
                    }
                } else {
                    // Create new device on server
                    do {
                        let createdDTO = try await remoteRepo.createScale(dto)
                        // Update local device with server ID
                        device.id = createdDTO.id ?? device.id
                        device.hasServerID = true // Mark as having server ID
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
                        logger.log(level: .info, tag: tag, message: "Successfully created device \(device.id) on server")
                    } catch {
                        logger.log(level: .error, tag: tag, message: "Failed to create device on server: \(error.localizedDescription)")
                    }
                }
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to push local changes to server: \(error.localizedDescription)")
        }
    }

    /// Fetches fresh server state and replaces local storage with it.
    /// This implements the "replace-all" policy for clean state management.
    /// Preserves any unsynced local devices to avoid losing local changes.
    private func pullServerStateAndReplace(accountId: String) async {
        do {
            let serverScales = try await remoteRepo.listScales()
            let serverDevices = serverScales.map { Device(from: $0) }

            // Get any unsynced local devices to preserve them
            let unsyncedDevices = try await localRepository.getUnsyncedDevices()

            // Replace synced devices with server state, preserve unsynced local devices
            try await localRepository.replaceAllDevicesForAccount(accountId, with: serverDevices, preserveUnsynced: unsyncedDevices)

            logger.log(level: .info, tag: tag, message: "Successfully replaced local storage with \(serverDevices.count) devices from server, preserved \(unsyncedDevices.count) unsynced local devices")
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to fetch server state and replace local storage: \(error.localizedDescription)")
        }
    }

    /// Helper method to create properties dictionary from DTO for API calls.
    private func createPropertiesFromDTO(_ dto: ScaleDTO) -> [String: Any] {
        var properties: [String: Any] = [:]

        if let nickname = dto.nickname { properties["nickname"] = nickname }
        //Add Properties here in order to update the device
        return properties
    }

}
