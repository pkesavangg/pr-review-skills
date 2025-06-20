// ScaleRepository.swift

import Foundation
import SwiftData

/// Concrete implementation of ScaleRepositoryProtocol for local storage using SwiftData.
/// Handles CRUD operations for Device (scale) entities in a thread-safe manner.
@MainActor
final class ScaleRepository: ScaleRepositoryProtocol {
    // MARK: - Properties
    let context: ModelContext = PersistenceController.shared.context
    let logger = LoggerService.shared
    
    /// Fetches all scales stored locally.
    /// - Returns: An array of all ScaleDTO objects.
    func listScales() async throws -> [ScaleDTO] {
        let descriptor = FetchDescriptor<Device>()
        let devices = try context.fetch(descriptor)
        return devices.map { $0.toDTO() }
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
        // Add more fields as needed
        try context.save()
    }
    
    /// Gets all devices that haven't been synced with the API.
    /// - Returns: An array of unsynced devices.
    func getUnsyncedDevices() async throws -> [Device] {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.isSynced == false })
        return try context.fetch(descriptor)
    }
    
    /// Saves a new scale to the local data store.
    /// - Parameter scale: The ScaleDTO object to save.
    /// - Returns: The created ScaleDTO.
    func createScale(_ scale: ScaleDTO) async throws -> ScaleDTO {
        let device = Device(from: scale)
        device.isSynced = false 
        context.insert(device)
        try context.save()
        return device.toDTO()
    }
    
    /// Updates an existing scale in the local data store.
    /// - Parameters:
    ///   - scaleId: The ID of the scale to update.
    ///   - properties: The properties to update.
    /// - Returns: The updated ScaleDTO.
    func editScale(_ scaleId: String, properties: [String: Any]) async throws -> ScaleDTO {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == scaleId })
        guard let device = try context.fetch(descriptor).first else {
            throw NSError(domain: "ScaleService", code: 404, userInfo: [NSLocalizedDescriptionKey: "Device not found"])
        }
        if let nickname = properties["nickname"] as? String { device.nickname = nickname }
        if let name = properties["name"] as? String { device.deviceName = name }
        if let deviceType = properties["type"] as? String { device.deviceType = deviceType }
        if let userId = properties["userId"] as? String { device.accountId = userId }
        if let password = properties["password"] as? Int { device.password = String(password) }
        if let broadcastId = properties["broadcastId"] as? Int { device.broadcastId = String(broadcastId) }
        if let userNumber = properties["userNumber"] as? Int { device.userNumber = String(userNumber) }
        if let isDeleted = properties["isDeleted"] as? Bool { device.isDeleted = isDeleted }
        if let isConnected = properties["isConnected"] as? Bool { device.isConnected = isConnected }
        if let isWifiConfigured = properties["isWifiConfigured"] as? Bool { device.isWifiConfigured = isWifiConfigured }
        if let mac = properties["mac"] as? String { device.mac = mac }
        if let sku = properties["sku"] as? String { device.sku = sku }
        if let broadcastIdString = properties["broadcastIdString"] as? String { device.broadcastIdString = broadcastIdString }
        if let createdAt = properties["createdAt"] as? String { device.createdAt = createdAt }
        device.isSynced = false
        try context.save()
        return device.toDTO()
    }
    
    /// Deletes a scale by its unique ID.
    /// - Parameter scaleId: The ID of the scale to delete.
    func deleteScale(_ scaleId: String) async throws {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == scaleId })
        if let device = try context.fetch(descriptor).first {
            context.delete(device)
            try context.save()
        }
    }
    
    /// Updates the meta data for a scale.
    /// - Parameters:
    ///   - scaleId: The ID of the scale to update.
    ///   - metaData: The new meta data to set.
    func patchScaleMeta(_ scaleId: String, metaData: ScaleMetaDataDTO) async throws {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == scaleId })
        if let device = try context.fetch(descriptor).first {
            device.metaData = DeviceMetaData(from: metaData)
            device.isSynced = false
            try context.save()
        }
    }
    
    /// Updates the R4 scale preference for a scale.
    /// - Parameter preference: The new R4ScalePreferenceDTO to set.
    func patchScalePreference(_ preference: R4ScalePreferenceDTO) async throws {
        guard let scaleId = preference.scaleId else {
            throw NSError(domain: "ScaleRepository", code: 400, userInfo: [NSLocalizedDescriptionKey: "scaleId is required to patch scale preference"]) 
        }
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == scaleId })
        if let device = try context.fetch(descriptor).first {
            device.r4ScalePreference = R4ScalePreference(from: preference)
            device.isSynced = false
            try context.save()
        }
    }
}
