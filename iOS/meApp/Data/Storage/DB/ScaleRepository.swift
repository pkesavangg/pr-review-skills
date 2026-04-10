// ScaleRepository.swift

// This repository intentionally aggregates all Device/Scale CRUD operations
// to maintain a single source of truth for scale data access patterns.
// Splitting would fragment SwiftData context management and reduce maintainability.

import Foundation
import SwiftData

/// Repository for managing Device entities in SwiftData storage.
@MainActor
final class ScaleRepository: ScaleRepositoryProtocol {
    // MARK: - Properties
    let context: ModelContext
    let logger = LoggerService.shared

    init(context: ModelContext? = nil) {
        self.context = context ?? PersistenceController.shared.context
    }

    /// Deletes all scales from local storage.
    func clearAllData() async throws {
        let descriptor = FetchDescriptor<Device>()
        let allDevices = try context.fetch(descriptor)
        for device in allDevices {
            context.delete(device)
        }
        try context.save()
    }

    /// Fetches all scales stored locally for a specific account.
    /// - Parameter accountId: The account ID to filter scales by.
    /// - Returns: An array of Device objects for the account.
    func listScales(forAccountId accountId: String) async throws -> [Device] {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.accountId == accountId })
        return try context.fetch(descriptor)
    }
    
    /// Fetches all scales stored locally (legacy method).
    /// - Returns: An array of all Device objects.
    func listScales() async throws -> [Device] {
        let descriptor = FetchDescriptor<Device>()
        return try context.fetch(descriptor)
    }

    /// Gets a device by its ID.
    /// - Parameter deviceId: The ID of the device to fetch.
    /// - Returns: The Device if found, nil otherwise.
    func getDevice(_ deviceId: String) async throws -> Device? {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == deviceId })
        return try context.fetch(descriptor).first
    }

    /// Updates a device in the local storage.
    /// - Parameter device: The device to update.
    func updateDevice(_ device: Device) async throws {
        let managedDevice = try fetchDeviceOrThrow(device.id)
        copyDeviceFields(from: device, to: managedDevice)
        managedDevice.metaData = device.metaData
        managedDevice.r4ScalePreference = device.r4ScalePreference
        managedDevice.bathScale = device.bathScale
        try context.save()
    }

    /// Updates a device with a new ID (used when server assigns a new ID to a locally created device).
    /// - Parameters:
    ///   - oldId: The current ID of the device in the database.
    ///   - updatedDevice: The device object with updated properties including the new ID.
    func updateDeviceWithNewId(oldId: String, updatedDevice: Device) async throws {
        let managedDevice = try fetchDeviceOrThrow(oldId)
        logger.log(level: .debug, tag: "ScaleRepository", message: "Updating device ID from \(oldId) to \(updatedDevice.id)")
        copyDeviceFields(from: updatedDevice, to: managedDevice, includeId: true)
        managedDevice.metaData = updatedDevice.metaData
        managedDevice.r4ScalePreference = updatedDevice.r4ScalePreference
        managedDevice.bathScale = updatedDevice.bathScale
        try context.save()
        logger.log(level: .debug, tag: "ScaleRepository", message: "Successfully updated device with new ID: \(updatedDevice.id)")
    }

    /// Gets all devices that haven't been synced with the API.
    /// - Returns: An array of unsynced devices.
    func getUnsyncedDevices() async throws -> [Device] {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { ($0.isSynced ?? false) == false })
        return try context.fetch(descriptor)
    }

    /// Saves a new scale to the local data store.
    /// - Parameter scale: The Device object to save.
    /// - Returns: The created Device.
    /// - Throws: An error if a device with the same ID already exists.
    func createScale(_ scale: Device) async throws -> Device {
        // Check for duplicate ID before inserting
        if (try? await getDevice(scale.id)) != nil {
            throw NSError(
                domain: "ScaleRepository",
                code: 409,
                userInfo: [NSLocalizedDescriptionKey: "Device with ID '\(scale.id)' already exists"]
            )
        }
        
        scale.isSynced = false
        context.insert(scale)
        insertDeviceRelationships(scale)
        try context.save()
        logger.log(level: .info, tag: "ScaleRepository", message: "Successfully created scale with ID: \(scale.id)")
        return scale
    }

    /// Updates an existing scale in the local data store.
    /// - Parameters:
    ///   - scaleId: The ID of the scale to update.
    ///   - properties: The properties to update.
    /// - Returns: The updated Device.
    func editScale(_ scaleId: String, properties: [String: Any]) async throws -> Device {
        let device = try fetchDeviceOrThrow(scaleId)
        if let nickname = properties["nickname"] as? String {
            device.nickname = nickname
        }
        device.isSynced = false
        try context.save()
        return device
    }

    /// Deletes a scale by its unique ID.
    /// - Parameter scaleId: The ID of the scale to delete.
    func deleteScale(_ scaleId: String) async throws {
        if let device = try? fetchDeviceOrThrow(scaleId) {
            // Break the reference *before* we delete so any lingering copies of the
            // `Device` model can no longer reach a cascade-deleted `R4ScalePreference`.
            device.r4ScalePreference = nil
            context.delete(device)
            try context.save()
        }
    }

    /// Updates the meta data for a scale.
    /// - Parameters:
    ///   - scaleId: The ID of the scale to update.
    ///   - metaData: The new meta data to set.
    func patchScaleMeta(_ scaleId: String, metaData: DeviceMetaData) async throws {
        let device = try fetchDeviceOrThrow(scaleId)
        if let existing = device.metaData {
            existing.modelNumber = metaData.modelNumber
            existing.serialNumber = metaData.serialNumber
            existing.firmwareRevision = metaData.firmwareRevision
            existing.hardwareRevision = metaData.hardwareRevision
            existing.softwareRevision = metaData.softwareRevision
            existing.manufacturerName = metaData.manufacturerName
            existing.systemId = metaData.systemId
            existing.latestVersion = metaData.latestVersion
            existing.isSynced = false
        } else {
            metaData.isSynced = false
            device.metaData = metaData
            context.insert(metaData)
        }
        device.isSynced = false
        try context.save()
    }

    /// Updates the R4 scale preference for a scale.
    /// - Parameters:
    ///   - scaleId: The ID of the scale.
    ///   - preference: The new R4ScalePreference to set.
    func patchScalePreference(_ scaleId: String, _ preference: R4ScalePreference) async throws {
        let device = try fetchDeviceOrThrow(scaleId)
        if let existing = device.r4ScalePreference {
            existing.displayName = preference.displayName
            existing.displayMetrics = preference.displayMetrics
            existing.shouldFactoryReset = preference.shouldFactoryReset
            existing.shouldMeasureImpedance = preference.shouldMeasureImpedance
            existing.shouldMeasurePulse = preference.shouldMeasurePulse
            existing.timeFormat = preference.timeFormat
            existing.tzOffset = preference.tzOffset
            existing.wifiFotaScheduleTime = preference.wifiFotaScheduleTime
            existing.updatedAt = preference.updatedAt
            existing.isSynced = false
        } else {
            preference.id = device.id
            preference.isSynced = false
            device.r4ScalePreference = preference
            context.insert(preference)
        }
        device.isSynced = false
        try context.save()
    }

    /// Updates the R4 scale preference for a scale from a DTO.
    /// This avoids passing @Model objects across async boundaries.
    func patchScalePreference(_ scaleId: String, fromDTO dto: R4ScalePreferenceDTO) async throws {
        let device = try fetchDeviceOrThrow(scaleId)
        if let existing = device.r4ScalePreference {
            existing.displayName = dto.displayName
            existing.displayMetrics = dto.displayMetrics
            existing.shouldFactoryReset = dto.shouldFactoryReset
            existing.shouldMeasureImpedance = dto.shouldMeasureImpedance
            existing.shouldMeasurePulse = dto.shouldMeasurePulse
            existing.timeFormat = dto.timeFormat
            existing.tzOffset = dto.tzOffset
            existing.wifiFotaScheduleTime = dto.wifiFotaScheduleTime
            existing.updatedAt = dto.updatedAt
            existing.isSynced = dto.isSynced ?? false
        } else {
            let newPreference = R4ScalePreference(from: dto, scaleId: device.id)
            newPreference.isSynced = dto.isSynced ?? false
            device.r4ScalePreference = newPreference
            context.insert(newPreference)
        }
        device.isSynced = false
        try context.save()
    }

    // MARK: - Replace-All Sync Methods

    /// Replaces all local devices for the given account with fresh devices from server.
    /// This implements the "replace-all" sync policy for clean, predictable state management.
    /// Preserves unsynced local devices to avoid losing local changes.
    /// - Parameters:
    ///   - accountId: The account ID to filter devices by.
    ///   - serverDevices: Array of fresh Device objects from the server.
    ///   - preserveUnsynced: Array of unsynced local devices to preserve.
    func replaceAllDevicesForAccount( // swiftlint:disable:this function_body_length
        _ accountId: String,
        with serverDevices: [ScaleDTO],
        preserveUnsynced unsyncedDevices: [Device]
    ) async throws {
        logger.log(
            level: .debug,
            tag: "ScaleRepository",
            message: "Starting replaceAllDevicesForAccount with \(serverDevices.count) server devices, \(unsyncedDevices.count) unsynced devices"
        )
        
        // Delete only synced devices for this account (preserve unsynced ones)
        let syncedDescriptor = FetchDescriptor<Device>(predicate: #Predicate {
            $0.accountId == accountId && ($0.isSynced ?? false) == true
        })
        let syncedDevices = try context.fetch(syncedDescriptor)
        
        // Capture existing connection status before deletion to preserve real-time state
        var connectionStatusMap: [String: (isConnected: Bool, isWifiConfigured: Bool)] = [:]
        for device in syncedDevices {
            if let broadcastId = device.broadcastIdString {
                connectionStatusMap[broadcastId] = (device.isConnected ?? false, device.isWifiConfigured ?? false)
            }
        }
        logger.log(level: .debug, tag: "ScaleRepository", message: "Captured connection status for \(connectionStatusMap.count) devices")
        
        // Flush any pending inserts/updates so the context is stable before deleting.
        // Without this, SwiftData can crash with "This store went missing?" when a
        // recently-inserted BathScale is still tracked with a temporary identifier
        // while cascade-deleted devices are saved in the same pass.
        context.processPendingChanges()

        logger.log(level: .debug, tag: "ScaleRepository", message: "Deleting \(syncedDevices.count) synced devices for account \(accountId)")
        for device in syncedDevices {
            logger.log(level: .debug, tag: "ScaleRepository", message: "Deleting synced device: \(device.id), sku: \(device.sku ?? "nil")")
            // Explicitly delete child relationships before the parent to prevent
            // SwiftData cascade issues with stale persistent identifiers.
            if let bathScale = device.bathScale { context.delete(bathScale) }
            if let r4Pref = device.r4ScalePreference { context.delete(r4Pref) }
            if let metaData = device.metaData { context.delete(metaData) }
            context.delete(device)
        }
        try context.save()

        // Insert server devices, but handle conflicts with unsynced local devices by updating them
        // IMPORTANT: Only match unsynced devices that belong to the current accountId
        // This allows multiple accounts to have devices with the same MAC/SKU
        let unsyncedDevicesForAccount = unsyncedDevices.filter { $0.accountId == accountId }
        
        var insertedCount = 0
        var updatedCount = 0
        
        for serverDevice in serverDevices {
            // Find matching unsynced device by ID, MAC, or broadcastId
            // CRITICAL: Only match devices that belong to the current accountId
            let matchingUnsyncedDevice = findMatchingUnsyncedDevice(
                for: serverDevice,
                in: unsyncedDevicesForAccount,
                accountId: accountId
            )

            if let matchingDevice = matchingUnsyncedDevice {
                // Handle conflict: update unsynced device with server data
                if let serverId = serverDevice.id, matchingDevice.id != serverId {
                    updateDeviceFromDTO(matchingDevice, from: serverDevice, accountId: accountId, connectionStatusMap: connectionStatusMap)
                    try await updateDeviceWithNewId(oldId: matchingDevice.id, updatedDevice: matchingDevice)
                } else {
                    updateDeviceFromDTO(matchingDevice, from: serverDevice, accountId: accountId, connectionStatusMap: connectionStatusMap)
                    try await updateDevice(matchingDevice)
                }
                updatedCount += 1
                continue
            }

            // No conflict: insert new server device
            logger.log(level: .debug, tag: "ScaleRepository", message: "Inserting server device: \(serverDevice.id ?? "nil"), sku: \(serverDevice.sku ?? "nil")")
            let device = Device(from: serverDevice, accountId: accountId)
            device.isSynced = true
            device.hasServerID = true
            
            // Restore preserved connection status from the map
            if let broadcastId = device.broadcastIdString,
               let preservedStatus = connectionStatusMap[broadcastId] {
                device.isConnected = preservedStatus.isConnected
                device.isWifiConfigured = preservedStatus.isWifiConfigured
                logger.log(
                    level: .debug,
                    tag: "ScaleRepository",
                    message: "Restored connection status for device \(device.id): " +
                        "connected=\(preservedStatus.isConnected), wifi=\(preservedStatus.isWifiConfigured)"
                )
            }
            
            context.insert(device)
            insertDeviceRelationships(device, markSynced: true)
            insertedCount += 1
        }
        
        try context.save()
        logger.log(
            level: .debug,
            tag: "ScaleRepository",
            message: "Completed replaceAllDevicesForAccount: Inserted=\(insertedCount), Updated=\(updatedCount)"
        )
    }

    /// Marks a device as deleted locally (for server sync).
    /// - Parameter deviceId: The ID of the device to mark as deleted.
    func markDeviceAsDeleted(_ deviceId: String) async throws {
        let device = try fetchDeviceOrThrow(deviceId)
        device.isSoftDeleted = true
        device.isSynced = false
        try context.save()
    }

    /// Gets all devices marked for deletion that need to be synced.
    /// - Returns: An array of devices marked as deleted and unsynced.
    func getDevicesMarkedForDeletion() async throws -> [Device] {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate {
            $0.isSoftDeleted ?? false == true && ($0.isSynced ?? false) == false
        })
        return try context.fetch(descriptor)
    }

    /// Permanently removes a device from local storage (after successful server deletion).
    /// - Parameter deviceId: The ID of the device to permanently remove.
    func permanentlyRemoveDevice(_ deviceId: String) async throws {
        if let device = try? fetchDeviceOrThrow(deviceId) {
            context.delete(device)
            try context.save()
        }
    }

    /// Checks if a device is purely local (never synced to server).
    /// - Parameter deviceId: The ID of the device to check.
    /// - Returns: True if the device is purely local, false otherwise.
    func isDevicePurelyLocal(_ deviceId: String) async throws -> Bool {
        guard let device = try? fetchDeviceOrThrow(deviceId) else {
            return false
        }
        // A device is purely local if it has never been synced AND doesn't have a server ID
        return device.isSynced == false && device.hasServerID == false
    }

    /// Fetches an attached R4 scale preference by its scale ID from the shared SwiftData context.
    /// - Parameter id: The scale/preference ID.
    /// - Returns: The attached `R4ScalePreference` if found, otherwise nil.
    func fetchAttachedPreference(by id: String) -> R4ScalePreference? {
        return fetchAttachedPreferenceInternal(by: id)
    }

    @MainActor func fetchAttachedPreferenceSync(by id: String) -> R4ScalePreference? {
        return fetchAttachedPreferenceInternal(by: id)
    }

    // MARK: - Private Helpers

    /// Fetch a Device by its ID or throw 404.
    private func fetchDeviceOrThrow(_ deviceId: String) throws -> Device {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == deviceId })
        guard let device = try context.fetch(descriptor).first else {
            throw NSError(domain: "ScaleService", code: 404, userInfo: [NSLocalizedDescriptionKey: "Device not found"])
        }
        return device
    }

    /// Copy all updatable fields from source to target Device.
    /// Note: Relationships (metaData, r4ScalePreference, bathScale) are not copied directly.
    private func copyDeviceFields(from source: Device, to target: Device, includeId: Bool = false) {
        if includeId { target.id = source.id }
        target.isSynced = source.isSynced
        target.hasServerID = source.hasServerID
        target.nickname = source.nickname
        target.deviceName = source.deviceName
        target.deviceType = source.deviceType
        target.isSoftDeleted = source.isSoftDeleted
        target.isConnected = source.isConnected
        target.isWifiConfigured = source.isWifiConfigured
        target.mac = source.mac
        target.sku = source.sku
        target.broadcastId = source.broadcastId
        target.broadcastIdString = source.broadcastIdString
        target.userNumber = source.userNumber
        target.protocolType = source.protocolType
        target.password = source.password
        target.accountId = source.accountId
        target.peripheralIdentifier = source.peripheralIdentifier
        target.createdAt = source.createdAt
        target.lastModified = source.lastModified
        target.token = source.token
    }

    /// Copy device fields from a ScaleDTO to a Device.
    private func copyDeviceFields(
        from dto: ScaleDTO,
        to device: Device,
        accountId: String,
        preserveConnectionStatus: Bool = false,
        connectionStatusMap: [String: (isConnected: Bool, isWifiConfigured: Bool)] = [:]
    ) {
        device.peripheralIdentifier = dto.peripheralIdentifier ?? device.peripheralIdentifier
        device.nickname = dto.nickname ?? device.nickname
        device.sku = dto.sku ?? device.sku
        device.mac = dto.mac ?? device.mac
        device.password = dto.password.map { Int64($0) } ?? device.password
        device.isSoftDeleted = dto.isDeleted ?? device.isSoftDeleted
        device.deviceName = dto.name ?? device.deviceName
        device.broadcastId = dto.broadcastId.map { Int64($0) } ?? device.broadcastId
        device.broadcastIdString = dto.broadcastIdString ?? device.broadcastIdString
        device.userNumber = dto.userNumber.map { String($0) } ?? device.userNumber
        device.createdAt = dto.createdAt ?? device.createdAt
        device.token = dto.scaleToken ?? device.token
        device.isWifiConfigured = dto.isWifiConfigured ?? device.isWifiConfigured
        device.accountId = accountId
        device.isSynced = true
        device.hasServerID = true
        
        // Preserve connection status if requested
        if preserveConnectionStatus,
           let broadcastId = device.broadcastIdString,
           let preservedStatus = connectionStatusMap[broadcastId] {
            device.isConnected = preservedStatus.isConnected
            device.isWifiConfigured = preservedStatus.isWifiConfigured
        }
    }

    /// Update or insert R4ScalePreference safely from a DTO.
    private func updateR4Preference(for device: Device, from preferenceDTO: R4ScalePreferenceDTO, scaleId: String) {
        if let existing = device.r4ScalePreference {
            existing.id = scaleId
            existing.displayName = preferenceDTO.displayName
            existing.displayMetrics = preferenceDTO.displayMetrics
            existing.shouldFactoryReset = preferenceDTO.shouldFactoryReset
            existing.shouldMeasureImpedance = preferenceDTO.shouldMeasureImpedance
            existing.shouldMeasurePulse = preferenceDTO.shouldMeasurePulse
            existing.timeFormat = preferenceDTO.timeFormat
            existing.tzOffset = preferenceDTO.tzOffset
            existing.wifiFotaScheduleTime = preferenceDTO.wifiFotaScheduleTime ?? 0
            existing.updatedAt = preferenceDTO.updatedAt
            existing.isSynced = true
        } else {
            let newPreference = R4ScalePreference(from: preferenceDTO, scaleId: scaleId)
            newPreference.isSynced = true
            device.r4ScalePreference = newPreference
            context.insert(newPreference)
        }
    }

    /// Update or insert DeviceMetaData safely from a DTO.
    private func updateMetaData(for device: Device, from metaDataDTO: ScaleMetaDataDTO) {
        if let existing = device.metaData {
            copyMetaDataFields(from: metaDataDTO, to: existing)
            existing.isSynced = true
        } else {
            let newMeta = DeviceMetaData(from: metaDataDTO)
            newMeta.isSynced = true
            device.metaData = newMeta
            context.insert(newMeta)
        }
    }

    /// Insert device relationships (bathScale, r4Preference, metaData) into context.
    private func insertDeviceRelationships(_ device: Device, markSynced: Bool = false) {
        if let bathScale = device.bathScale {
            context.insert(bathScale)
            device.bathScale = bathScale
        }
        if let r4Preference = device.r4ScalePreference {
            r4Preference.id = device.id
            if markSynced { r4Preference.isSynced = true }
            context.insert(r4Preference)
            device.r4ScalePreference = r4Preference
        }
        if let metaData = device.metaData {
            if markSynced { metaData.isSynced = true }
            context.insert(metaData)
            device.metaData = metaData
        }
    }

    /// Copy fields between DeviceMetaData from DTO.
    private func copyMetaDataFields(from src: ScaleMetaDataDTO, to dst: DeviceMetaData) {
        dst.modelNumber = src.modelNumber
        dst.serialNumber = src.serialNumber
        dst.firmwareRevision = src.firmwareRevision
        dst.hardwareRevision = src.hardwareRevision
        dst.softwareRevision = src.softwareRevision
        dst.manufacturerName = src.manufacturerName
        dst.systemId = src.systemId
        dst.latestVersion = src.latestFirmwareVersion
    }

    /// Update bath scale type if needed.
    private func updateBathScaleType(for device: Device, scaleType: String?) {
        guard let scaleType = scaleType else { return }
        if let existingBathScale = device.bathScale {
            existingBathScale.scaleType = scaleType
        } else {
            let newBathScale = BathScale(scaleType: scaleType, bodyComp: true)
            device.bathScale = newBathScale
            context.insert(newBathScale)
        }
    }

    /// Finds a matching unsynced device for a server device.
    private func findMatchingUnsyncedDevice(for serverDevice: ScaleDTO, in unsyncedDevices: [Device], accountId: String) -> Device? {
        for unsyncedDevice in unsyncedDevices {
            guard unsyncedDevice.accountId == accountId else { continue }
            
            // Check for conflicts by ID or other identifiers (within same account)
            if unsyncedDevice.id == serverDevice.id {
                return unsyncedDevice
            }
            if let unsyncedBroadcastId = unsyncedDevice.broadcastIdString,
               let serverBroadcastId = serverDevice.broadcastIdString,
               unsyncedBroadcastId == serverBroadcastId {
                return unsyncedDevice
            }
            if let unsyncedMac = unsyncedDevice.mac,
               let serverMac = serverDevice.mac,
               unsyncedMac == serverMac {
                return unsyncedDevice
            }
        }
        return nil
    }

    /// Updates a device from a ScaleDTO, preserving relationships.
    private func updateDeviceFromDTO(
        _ device: Device,
        from dto: ScaleDTO,
        accountId: String,
        connectionStatusMap: [String: (isConnected: Bool, isWifiConfigured: Bool)]
    ) {
        if let serverId = dto.id {
            device.id = serverId
        }
        copyDeviceFields(
            from: dto,
            to: device,
            accountId: accountId,
            preserveConnectionStatus: true,
            connectionStatusMap: connectionStatusMap
        )
        
        // Update R4 preference if server has one
        if let preferenceDTO = dto.preference {
            updateR4Preference(for: device, from: preferenceDTO, scaleId: device.id)
        }
        
        // Update metadata if server has one
        if let metaDataDTO = dto.metaData {
            updateMetaData(for: device, from: metaDataDTO)
        }
        
        // Update latestVersion from root level if present
        if let latestVersion = dto.latestVersion {
            if let existingMeta = device.metaData {
                existingMeta.latestVersion = latestVersion
            } else {
                let newMeta = DeviceMetaData(latestVersion: latestVersion)
                newMeta.isSynced = true
                device.metaData = newMeta
                context.insert(newMeta)
            }
        }
        
        // Update bath scale type if needed
        updateBathScaleType(for: device, scaleType: dto.type)
    }

    /// Private helper to fetch attached R4ScalePreference by ID with consistent error handling.
    /// - Parameter id: The scale/preference ID.
    /// - Returns: The attached `R4ScalePreference` if found, otherwise nil.
    private func fetchAttachedPreferenceInternal(by id: String) -> R4ScalePreference? {
        var descriptor = FetchDescriptor<R4ScalePreference>(
            predicate: #Predicate<R4ScalePreference> { $0.id == id }
        )
        descriptor.fetchLimit = 1
        do {
            let results: [R4ScalePreference] = try context.fetch(descriptor)
            return results.first
        } catch {
            logger.log(level: .error, tag: "ScaleRepository", message: "Failed to fetch attached R4ScalePreference: \(error.localizedDescription)")
            return nil
        }
    }
}
