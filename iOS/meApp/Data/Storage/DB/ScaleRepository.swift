// ScaleRepository.swift
// Business logic service for scale management and local storage operations.

import Foundation
import SwiftData

final class ScaleRepository: ScaleRepositoryProtocol {
    // MARK: - Properties
    private let context: ModelContext

    init(context: ModelContext) {
        self.context = context
    }

    // MARK: - List all scales
    func listScales() async throws -> [ScaleDTO] {
        let descriptor = FetchDescriptor<Device>()
        let devices = try context.fetch(descriptor)
        return devices.map { $0.toDTO() }
    }

    // MARK: - Create a new scale
    func createScale(_ scale: ScaleDTO) async throws -> ScaleDTO {
        let device = Device(from: scale)
        context.insert(device)
        try context.save()
        return device.toDTO()
    }

    // MARK: - Edit a scale
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
        // Add more property updates as needed
        try context.save()
        return device.toDTO()
    }

    // MARK: - Delete a scale
    func deleteScale(_ scaleId: String) async throws {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == scaleId })
        if let device = try context.fetch(descriptor).first {
            context.delete(device)
            try context.save()
        }
    }

    // MARK: - Patch scale meta data
    func patchScaleMeta(_ scaleId: String, metaData: ScaleMetaDataDTO) async throws {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == scaleId })
        if let device = try context.fetch(descriptor).first {
            device.metaData = DeviceMetaData(from: metaData)
            try context.save()
        }
    }

    // MARK: - Patch scale preference
    func patchScalePreference(_ preference: R4ScalePreferenceDTO) async throws {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == preference.scaleId })
        if let device = try context.fetch(descriptor).first {
            device.r4ScalePreference = R4ScalePreference(from: preference)
            try context.save()
        }
    }
}
