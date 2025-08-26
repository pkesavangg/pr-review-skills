// ScaleRepository.swift

import Foundation
import SwiftData

/// Repository for managing Device entities in SwiftData storage.
@MainActor
final class ScaleRepository: ScaleRepositoryProtocol {
    // MARK: - Properties
    let context: ModelContext = PersistenceController.shared.context
    let logger = LoggerService.shared

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
        let deviceId = device.id
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == deviceId })
        guard let managedDevice = try context.fetch(descriptor).first else {
            throw NSError(domain: "ScaleService", code: 404, userInfo: [NSLocalizedDescriptionKey: "Device not found"])
        }
        // Update only the properties that may change during sync
        managedDevice.isSynced = device.isSynced
        managedDevice.hasServerID = device.hasServerID
        managedDevice.nickname = device.nickname
        managedDevice.deviceName = device.deviceName
        managedDevice.deviceType = device.deviceType
        managedDevice.isDeleted = device.isDeleted
        managedDevice.isConnected = device.isConnected
        managedDevice.isWifiConfigured = device.isWifiConfigured
        managedDevice.mac = device.mac
        managedDevice.sku = device.sku
        managedDevice.broadcastId = device.broadcastId
        managedDevice.broadcastIdString = device.broadcastIdString
        managedDevice.userNumber = device.userNumber
        managedDevice.protocolType = device.protocolType
        managedDevice.password = device.password
        managedDevice.accountId = device.accountId
        managedDevice.peripheralIdentifier = device.peripheralIdentifier
        managedDevice.createdAt = device.createdAt
        managedDevice.lastModified = device.lastModified
        managedDevice.token = device.token
        managedDevice.metaData = device.metaData
        managedDevice.r4ScalePreference = device.r4ScalePreference
        managedDevice.bathScale = device.bathScale
        // Add more fields as needed
        try context.save()
    }

    /// Updates a device with a new ID (used when server assigns a new ID to a locally created device).
    /// - Parameters:
    ///   - oldId: The current ID of the device in the database.
    ///   - updatedDevice: The device object with updated properties including the new ID.
    func updateDeviceWithNewId(oldId: String, updatedDevice: Device) async throws {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == oldId })
        guard let managedDevice = try context.fetch(descriptor).first else {
            logger.log(level: .error, tag: "ScaleRepository", message: "Device not found with old ID: \(oldId)")
            throw NSError(domain: "ScaleService", code: 404, userInfo: [NSLocalizedDescriptionKey: "Device not found"])
        }
        
        logger.log(level: .debug, tag: "ScaleRepository", message: "Updating device ID from \(oldId) to \(updatedDevice.id)")
        
        // Update all properties including the new ID
        managedDevice.id = updatedDevice.id
        managedDevice.isSynced = updatedDevice.isSynced
        managedDevice.hasServerID = updatedDevice.hasServerID
        managedDevice.nickname = updatedDevice.nickname
        managedDevice.deviceName = updatedDevice.deviceName
        managedDevice.deviceType = updatedDevice.deviceType
        managedDevice.isDeleted = updatedDevice.isDeleted
        managedDevice.isConnected = updatedDevice.isConnected
        managedDevice.isWifiConfigured = updatedDevice.isWifiConfigured
        managedDevice.mac = updatedDevice.mac
        managedDevice.sku = updatedDevice.sku
        managedDevice.broadcastId = updatedDevice.broadcastId
        managedDevice.broadcastIdString = updatedDevice.broadcastIdString
        managedDevice.userNumber = updatedDevice.userNumber
        managedDevice.protocolType = updatedDevice.protocolType
        managedDevice.password = updatedDevice.password
        managedDevice.accountId = updatedDevice.accountId
        managedDevice.peripheralIdentifier = updatedDevice.peripheralIdentifier
        managedDevice.createdAt = updatedDevice.createdAt
        managedDevice.lastModified = updatedDevice.lastModified
        managedDevice.token = updatedDevice.token
        managedDevice.metaData = updatedDevice.metaData
        managedDevice.r4ScalePreference = updatedDevice.r4ScalePreference
        managedDevice.bathScale = updatedDevice.bathScale
        
        try context.save()
        logger.log(level: .debug, tag: "ScaleRepository", message: "Successfully updated device with new ID: \(updatedDevice.id)")
    }

    /// Gets all devices that haven't been synced with the API.
    /// - Returns: An array of unsynced devices.
    func getUnsyncedDevices() async throws -> [Device] {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { ($0.isSynced ?? false) == false  })
        return try context.fetch(descriptor)
    }

    /// Saves a new scale to the local data store.
    /// - Parameter scale: The Device object to save.
    /// - Returns: The created Device.
    func createScale(_ scale: Device) async throws -> Device {
        let device = scale
        device.isSynced = false
        
        // Insert the main device first
        context.insert(device)
        
        // Handle relationships - insert related entities and establish relationships
        if let bathScale = device.bathScale {
            context.insert(bathScale)
            device.bathScale = bathScale
        }
        
        if let r4Preference = device.r4ScalePreference {
            // Ensure the preference has the correct scale ID
            r4Preference.id = device.id
            context.insert(r4Preference)
            device.r4ScalePreference = r4Preference
        }
        
        if let metaData = device.metaData {
            context.insert(metaData)
            device.metaData = metaData
        }
        
        try context.save()
        logger.log(level: .info, tag: "ScaleRepository", message: "Successfully created scale with ID: \(device.id)")
        return device
    }

    /// Updates an existing scale in the local data store.
    /// - Parameters:
    ///   - scaleId: The ID of the scale to update.
    ///   - properties: The properties to update.
    /// - Returns: The updated Device.
    func editScale(_ scaleId: String, properties: [String: Any]) async throws -> Device {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == scaleId })
        guard let device = try context.fetch(descriptor).first else {
            throw NSError(domain: "ScaleService", code: 404, userInfo: [NSLocalizedDescriptionKey: "Device not found"])
        }
        if let nickname = properties["nickname"] as? String { device.nickname = nickname }

        device.isSynced = false
        try context.save()
        return device
    }

    /// Deletes a scale by its unique ID.
    /// - Parameter scaleId: The ID of the scale to delete.
    func deleteScale(_ scaleId: String) async throws {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == scaleId })
        if let device = try context.fetch(descriptor).first {
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
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == scaleId })
        if let device = try context.fetch(descriptor).first {
            device.metaData = metaData
            device.metaData?.isSynced = false
            device.isSynced = false
            try context.save()
        }
    }

    /// Updates the R4 scale preference for a scale.
    /// - Parameter preference: The new R4ScalePreference to set.
    func patchScalePreference(_ scaleId: String, _ preference: R4ScalePreference) async throws {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == scaleId })
        if let device = try context.fetch(descriptor).first {
            if let existingPreference = device.r4ScalePreference {
                // UPDATE existing object instead of deleting/recreating to prevent UI crashes
                existingPreference.displayName = preference.displayName
                existingPreference.displayMetrics = preference.displayMetrics
                existingPreference.shouldFactoryReset = preference.shouldFactoryReset
                existingPreference.shouldMeasureImpedance = preference.shouldMeasureImpedance
                existingPreference.shouldMeasurePulse = preference.shouldMeasurePulse
                existingPreference.timeFormat = preference.timeFormat
                existingPreference.tzOffset = preference.tzOffset
                existingPreference.wifiFotaScheduleTime = preference.wifiFotaScheduleTime
                existingPreference.updatedAt = preference.updatedAt
                existingPreference.isSynced = false
            } else {
                // No existing preference, insert the new one
                preference.id = scaleId
                preference.isSynced = false
                device.r4ScalePreference = preference
                context.insert(preference)
            }

            device.isSynced = false
            try context.save()
        }
    }

    // MARK: - Replace-All Sync Methods

            /// Replaces all local devices for the given account with fresh devices from server.
    /// This implements the "replace-all" sync policy for clean, predictable state management.
    /// Preserves unsynced local devices to avoid losing local changes.
    /// - Parameters:
    ///   - accountId: The account ID to filter devices by.
    ///   - serverDevices: Array of fresh Device objects from the server.
    ///   - preserveUnsynced: Array of unsynced local devices to preserve.
    func replaceAllDevicesForAccount(_ accountId: String, with serverDevices: [ScaleDTO], preserveUnsynced unsyncedDevices: [Device]) async throws {
        logger.log(level: .debug, tag: "ScaleRepository", message: "Starting replaceAllDevicesForAccount with \(serverDevices.count) server devices, \(unsyncedDevices.count) unsynced devices")
        
        // Delete only synced devices for this account (preserve unsynced ones)
        let syncedDescriptor = FetchDescriptor<Device>(predicate: #Predicate {
            $0.accountId == accountId && ($0.isSynced ?? false) == true
        })
        let syncedDevices = try context.fetch(syncedDescriptor)
        
        logger.log(level: .debug, tag: "ScaleRepository", message: "Deleting \(syncedDevices.count) synced devices for account \(accountId)")
        for device in syncedDevices {
            logger.log(level: .debug, tag: "ScaleRepository", message: "Deleting synced device: \(device.id), sku: \(device.sku ?? "nil")")
            context.delete(device)
        }
        try context.save()

        // Insert server devices, but skip any that conflict with unsynced local devices
        for serverDevice in serverDevices {
            let hasUnsyncedConflict = unsyncedDevices.contains { unsyncedDevice in
                // Check for conflicts by ID or other identifiers
                if unsyncedDevice.id == serverDevice.id { return true }
                if let unsyncedBroadcastId = unsyncedDevice.broadcastIdString,
                   let serverBroadcastId = serverDevice.broadcastIdString,
                   unsyncedBroadcastId == serverBroadcastId { return true }
                if let unsyncedMac = unsyncedDevice.mac,
                   let serverMac = serverDevice.mac,
                   unsyncedMac == serverMac { return true }
                return false
            }

            if hasUnsyncedConflict {
                logger.log(level: .debug, tag: "ScaleRepository", message: "Skipping server device \(serverDevice.id ?? "nil") due to unsynced conflict")
                continue
            }

            logger.log(level: .debug, tag: "ScaleRepository", message: "Inserting server device: \(serverDevice.id ?? "nil"), sku: \(serverDevice.sku ?? "nil")")
            let device = Device(from: serverDevice, accountId: accountId)

            device.isSynced = true // Mark as synced since they come from server
            device.hasServerID = true
            
            // Insert the main device first
            context.insert(device)
            
            // Handle relationships properly - insert related entities and establish relationships
            if let bathScale = device.bathScale {
                context.insert(bathScale)
                device.bathScale = bathScale
            }
            
            if let r4Preference = device.r4ScalePreference {
                r4Preference.id = device.id
                r4Preference.isSynced = true
                context.insert(r4Preference)
                device.r4ScalePreference = r4Preference
            }
            
            if let metaData = device.metaData {
                metaData.isSynced = true
                context.insert(metaData)
                device.metaData = metaData
            }
        }
        
        try context.save()
        logger.log(level: .debug, tag: "ScaleRepository", message: "Completed replaceAllDevicesForAccount")
    }

    /// Marks a device as deleted locally (for server sync).
    /// - Parameter deviceId: The ID of the device to mark as deleted.
    func markDeviceAsDeleted(_ deviceId: String) async throws {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == deviceId })
        guard let device = try context.fetch(descriptor).first else {
            throw NSError(domain: "ScaleService", code: 404, userInfo: [NSLocalizedDescriptionKey: "Device not found"])
        }

        device.isDeleted = true
        device.isSynced = false
        try context.save()
    }

    /// Gets all devices marked for deletion that need to be synced.
    /// - Returns: An array of devices marked as deleted and unsynced.
    func getDevicesMarkedForDeletion() async throws -> [Device] {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate {
            $0.isDeleted ?? false == true && ($0.isSynced ?? false) == false
        })
        return try context.fetch(descriptor)
    }

    /// Permanently removes a device from local storage (after successful server deletion).
    /// - Parameter deviceId: The ID of the device to permanently remove.
    func permanentlyRemoveDevice(_ deviceId: String) async throws {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == deviceId })
        if let device = try context.fetch(descriptor).first {
            context.delete(device)
            try context.save()
        }
    }

    /// Checks if a device is purely local (never synced to server).
    /// - Parameter deviceId: The ID of the device to check.
    /// - Returns: True if the device is purely local, false otherwise.
    func isDevicePurelyLocal(_ deviceId: String) async throws -> Bool {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == deviceId })
        guard let device = try context.fetch(descriptor).first else {
            return false
        }
        // A device is purely local if it has never been synced AND doesn't have a server ID
        return device.isSynced == false && device.hasServerID == false
    }
}
