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

    /// Fetches all scales stored locally.
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
        // Add more fields as needed
        try context.save()
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
        context.insert(device)
        try context.save()
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
            // If there's an existing preference, delete it first
            if let existingPreference = device.r4ScalePreference {
                context.delete(existingPreference)
            }
            
            // Insert the new preference into the context
            context.insert(preference)
            
            // Set the relationship
            device.r4ScalePreference = preference
            preference.isSynced = false
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
    func replaceAllDevicesForAccount(_ accountId: String, with serverDevices: [Device], preserveUnsynced unsyncedDevices: [Device]) async throws {
        // Delete only synced devices for this account (preserve unsynced ones)
        let syncedDescriptor = FetchDescriptor<Device>(predicate: #Predicate {
            $0.accountId == accountId && ($0.isSynced ?? false) == true
        })
        let syncedDevices = try context.fetch(syncedDescriptor)

        for device in syncedDevices {
            context.delete(device)
        }

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

            // Only insert server device if it doesn't conflict with unsynced local changes
            if !hasUnsyncedConflict {
                serverDevice.isSynced = true // Mark as synced since they come from server
                context.insert(serverDevice)
            }
        }

        try context.save()
    }

    /// Legacy method for backward compatibility - replaces all devices without preserving unsynced.
    func replaceAllDevicesForAccount(_ accountId: String, with serverDevices: [Device]) async throws {
        try await replaceAllDevicesForAccount(accountId, with: serverDevices, preserveUnsynced: [])
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
