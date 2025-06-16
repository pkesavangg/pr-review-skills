//
//  ScaleService.swift
//  meApp
//
//  Created by Lakshmi Priya on 02/06/25.
//

import Foundation
import SwiftData

/// Service for managing paired scale devices, including sync, CRUD, and connection management.
/// Handles local/remote sync, per-account operations, and robust error handling.
@MainActor
final class ScaleService: ScaleServiceProtocol {
    static let shared = ScaleService()
    
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
    
    /// Default initializer that creates its own dependencies.
    init() {
        self.accountService = AccountService.shared
        self._apiRepository = ScaleAPIRepository()
        self.localRepository = ScaleRepository()
        self.localKVRepo = ScaleRepositoryLocal()
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
    
    // MARK: - Helper
    @Sendable
    private func getAccountId() async throws -> String {
        guard let account = try await accountService.getActiveAccount() else {
            throw AccountError.noActiveAccount
        }
        return String(describing: account.id)
    }
    
    // MARK: - Sync Logic
    /// Syncs all unsynced scales with the remote backend. Call this on app start or after network recovery.
    public func syncAllScalesWithRemote() async {
        let accountId: String
        do {
            accountId = try await getAccountId()
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Failed to get account ID for sync: \(error.localizedDescription)")
            return
        }
        
        // Get existing devices
        let existingDevices: [Device]
        do {
            let scaleDTOs = try await localRepository.listScales()
            existingDevices = scaleDTOs.map { Device(from: $0) }
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Failed to fetch existing devices: \(error.localizedDescription)")
            return
        }
        
        // Get unsynced devices
        let unsynced: [Device]
        do {
            unsynced = try await localRepository.getUnsyncedDevices()
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Failed to fetch unsynced devices: \(error.localizedDescription)")
            return
        }
        
        do {
            // Sync unsynced devices
            for device in unsynced {
                let dto = device.toDTO()
                
                // Check for existing device by broadcastId
                if let broadcastId = device.broadcastId {
                    do {
                        let remoteScales = try await remoteRepo.listScales()
                        if let existingRemoteScale = remoteScales.first(where: { $0.broadcastIdString == broadcastId }) {
                            device.id = existingRemoteScale.id ?? device.id
                            device.isSynced = true
                            try await localRepository.updateDevice(device)
                            continue
                        }
                    } catch {
                        logger.log(level: .error, tag: "ScaleService", message: "Failed to check for existing remote device: \(error.localizedDescription)")
                    }
                }
                
                // Try to update or create device
                do {
                    let properties: [String: Any] = [
                        "nickname": dto.nickname as Any,
                        "name": dto.name as Any,
                        "type": dto.type as Any,
                        "isDeleted": dto.isDeleted as Any,
                        "isConnected": dto.isConnected as Any,
                        "isWifiConfigured": dto.isWifiConfigured as Any,
                        "mac": dto.mac as Any,
                        "sku": dto.sku as Any,
                        "broadcastId": dto.broadcastId as Any,
                        "broadcastIdString": dto.broadcastIdString as Any,
                        "userNumber": dto.userNumber as Any,
                        "createdAt": dto.createdAt as Any,
                        "scaleToken": dto.scaleToken as Any
                    ]
                    
                    do {
                        _ = try await remoteRepo.editScale(device.id, properties: properties)
                        device.isSynced = true
                        try await localRepository.updateDevice(device)
                    } catch {
                        logger.log(level: .error, tag: "ScaleService", message: "Failed to update device \(device.id) on API, falling back to create: \(error.localizedDescription)")
                        _ = try await remoteRepo.createScale(dto)
                        device.isSynced = true
                        try await localRepository.updateDevice(device)
                    }
                } catch {
                    logger.log(level: .error, tag: "ScaleService", message: "Failed to sync device \(device.id) to API: \(error.localizedDescription)")
                }
            }
            
            // Merge remote scales
            let remoteScales = try await remoteRepo.listScales()
            await mergeRemoteScales(remoteScales, accountId: accountId)
            
            // Update last sync timestamp
            let now = ISO8601DateFormatter().string(from: Date())
            try? await localKVRepo.setLastSyncTimestamp(accountId: accountId, timestamp: now)
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Sync failed: \(error.localizedDescription)")
        }
    }
    
    /// Syncs only unsynced devices (used after local changes).
    private func syncUnsyncedDevices() async {
        let accountId: String
        do {
            accountId = try await getAccountId()
        } catch {
            return
        }
        let unsynced: [Device]
        do {
            unsynced = try await localRepository.getUnsyncedDevices()
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Failed to fetch unsynced devices: \(error.localizedDescription)")
            return
        }
        for device in unsynced {
            let dto = device.toDTO()
            do {
                _ = try await remoteRepo.createScale(dto)
                device.isSynced = true
                try await localRepository.updateDevice(device)
            } catch {
                logger.log(level: .error, tag: "ScaleService", message: "Failed to sync device \(device.id) to API: \(error.localizedDescription)")
            }
        }
        let now = ISO8601DateFormatter().string(from: Date())
        try? await localKVRepo.setLastSyncTimestamp(accountId: accountId, timestamp: now)
    }
    
    /// Merges remote scales into local DB, resolving conflicts (latest wins by createdAt).
    private func mergeRemoteScales(_ remoteScales: [ScaleDTO], accountId: String) async {
        let existingDevices: [Device]
        do {
            let scaleDTOs = try await localRepository.listScales()
            existingDevices = scaleDTOs.map { Device(from: $0) }
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Failed to fetch existing devices: \(error.localizedDescription)")
            return
        }
        
        for remoteDTO in remoteScales {
            guard let deviceId = remoteDTO.id else { continue }
            
            // Try to find existing device by ID first
            if let managedDevice = try? await localRepository.getDevice(deviceId) {
                updateDeviceWithRemoteData(managedDevice, remoteDTO: remoteDTO)
                try? await localRepository.updateDevice(managedDevice)
            } else {
                // Check for duplicates by other identifiers
                let duplicateDevice = existingDevices.first { device in
                    device.sku == remoteDTO.sku ||
                    (device.broadcastIdString != nil && device.broadcastIdString == remoteDTO.broadcastIdString) ||
                    (device.mac != nil && device.mac == remoteDTO.mac)
                }
                
                if let duplicateDevice = duplicateDevice {
                    duplicateDevice.id = deviceId
                    updateDeviceWithRemoteData(duplicateDevice, remoteDTO: remoteDTO)
                    try? await localRepository.updateDevice(duplicateDevice)
                } else {
                    let newDevice = Device(from: remoteDTO)
                    newDevice.isSynced = true
                    try? await localRepository.createScale(newDevice.toDTO())
                }
            }
        }
    }
    
    private func updateDeviceWithRemoteData(_ device: Device, remoteDTO: ScaleDTO) {
        device.nickname = remoteDTO.nickname
        device.deviceName = remoteDTO.name
        device.deviceType = remoteDTO.type
        device.isDeleted = remoteDTO.isDeleted
        device.isConnected = remoteDTO.isConnected
        device.isWifiConfigured = remoteDTO.isWifiConfigured
        device.mac = remoteDTO.mac
        device.sku = remoteDTO.sku
        device.broadcastId = remoteDTO.broadcastId.map { String($0) }
        device.broadcastIdString = remoteDTO.broadcastIdString
        device.userNumber = remoteDTO.userNumber.map { String($0) }
        device.protocolType = nil
        device.createdAt = remoteDTO.createdAt
        device.token = remoteDTO.scaleToken
        device.isSynced = true
    }
    
    // MARK: - ScaleServiceProtocol Implementation
    func updateScaleMeta(_ deviceId: String, metaData: DeviceMetaData) async throws {
        let metaDataDTO = metaData.toDTO()
        guard let _ = try? await localRepository.getDevice(deviceId) else {
            throw ScaleError.deviceNotFound(id: deviceId)
        }
        try await localRepository.patchScaleMeta(deviceId, metaData: metaDataDTO)
        do {
            try await remoteRepo.patchScaleMeta(deviceId, metaData: metaDataDTO)
            if let device = try await localRepository.getDevice(deviceId) {
                device.isSynced = true
                try await localRepository.updateDevice(device)
            }
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Failed to sync scale meta to API")
            throw ScaleError.apiSyncFailed(error)
        }
    }
    
    func updateScalePreference(_ preference: R4ScalePreference) async throws {
        let preferenceDTO = preference.toDTO()
        try await localRepository.patchScalePreference(preferenceDTO)
        do {
            try await remoteRepo.patchScalePreference(preferenceDTO)
            if let device = try await localRepository.getDevice(preference.id) {
                device.isSynced = true
                try await localRepository.updateDevice(device)
            }
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Failed to sync scale preference to API: \(error.localizedDescription)")
            throw error
        }
    }
    
    // MARK: - DeviceServiceProtocol Implementation
    func getDevices() async throws -> [Device] {
        let localDevices = try await localRepository.listScales()
        do {
            let apiDevices = try await remoteRepo.listScales()
            // Instead of creating new entries, update existing ones
            for dto in apiDevices {
                if let deviceId = dto.id,
                   let existingDevice = try? await localRepository.getDevice(deviceId) {
                    // Update existing device
                    existingDevice.nickname = dto.nickname
                    existingDevice.deviceName = dto.name
                    existingDevice.deviceType = dto.type
                    existingDevice.isDeleted = dto.isDeleted
                    existingDevice.isConnected = dto.isConnected
                    existingDevice.isWifiConfigured = dto.isWifiConfigured
                    existingDevice.mac = dto.mac
                    existingDevice.sku = dto.sku
                    existingDevice.broadcastId = dto.broadcastId.map { String($0) }
                    existingDevice.broadcastIdString = dto.broadcastIdString
                    existingDevice.userNumber = dto.userNumber.map { String($0) }
                    existingDevice.protocolType = nil
                    existingDevice.createdAt = dto.createdAt
                    existingDevice.token = dto.scaleToken
                    existingDevice.isSynced = true
                    try? await localRepository.updateDevice(existingDevice)
                } else {
                    // Only create if it doesn't exist
                    _ = try? await localRepository.createScale(dto)
                }
            }
            return apiDevices.map { Device(from: $0) }
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Failed to fetch from API, using local data: \(error.localizedDescription)")
            return localDevices.map { Device(from: $0) }
        }
    }
    
    func getConnectedDevices() -> [String: Any] {
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
            logger.log(level: .error, tag: "ScaleService", message: "Failed to fetch connected devices: \(error.localizedDescription)")
            return [:]
        }
    }
    
    func updateConnectedDevices(device: Any, isConnected: Bool) {
        guard let deviceDict = device as? [String: Any],
              let deviceId = deviceDict["id"] as? String else {
            logger.log(level: .error, tag: "ScaleService", message: "Invalid device data format")
            return
        }
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.id == deviceId })
        do {
            if let device = try localRepository.context.fetch(descriptor).first {
                device.isConnected = isConnected
                device.isSynced = false
                try localRepository.context.save()
                logger.log(level: .info, tag: "ScaleService", message: "Updated connection status for device \(deviceId) to \(isConnected)")
                Task { await syncUnsyncedDevices() }
            } else {
                logger.log(level: .error, tag: "ScaleService", message: "Device not found: \(deviceId)")
            }
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Failed to update device connection status: \(error.localizedDescription)")
        }
    }
    
    func updateConnectedDeviceWifiStatus(broadcastId: String, isConfigured: Bool) {
        let descriptor = FetchDescriptor<Device>(predicate: #Predicate { $0.broadcastId == broadcastId })
        do {
            if let device = try localRepository.context.fetch(descriptor).first {
                device.isWifiConfigured = isConfigured
                device.isSynced = false
                try localRepository.context.save()
                logger.log(level: .info, tag: "ScaleService", message: "Updated WiFi configuration status for device \(broadcastId) to \(isConfigured)")
                Task { await syncUnsyncedDevices() }
            } else {
                logger.log(level: .error, tag: "ScaleService", message: "Device not found with broadcast ID: \(broadcastId)")
            }
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Failed to update device WiFi configuration status: \(error.localizedDescription)")
        }
    }
    
    func syncDevices(tempDevice: Device?) async throws {
        do {
            let apiScales = try await remoteRepo.listScales()
            _ = try await localRepository.listScales()
            for dto in apiScales {
                do {
                    _ = try await localRepository.createScale(dto)
                } catch {
                    logger.log(level: .error, tag: "ScaleService", message: "Failed to create scale from API: \(error.localizedDescription)")
                    throw ScaleError.apiSyncFailed(error)
                }
            }
            if let tempDevice = tempDevice {
                let dto = tempDevice.toDTO()
                do {
                    _ = try await localRepository.createScale(dto)
                } catch {
                    logger.log(level: .error, tag: "ScaleService", message: "Failed to create temp device: \(error.localizedDescription)")
                    throw ScaleError.apiSyncFailed(error)
                }
            }
            await syncUnsyncedDevices()
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Failed to sync devices: \(error.localizedDescription)")
            throw ScaleError.apiSyncFailed(error)
        }
    }
    
    func createDevice(_ device: Device) async throws -> Device {
        let dto = device.toDTO()
        _ = try await localRepository.createScale(dto)
        do {
            let apiDTO = try await remoteRepo.createScale(dto)
            if let localDevice = try await localRepository.getDevice(device.id) {
                localDevice.isSynced = true
                try await localRepository.updateDevice(localDevice)
            }
            return Device(from: apiDTO)
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Failed to sync new device to API")
            throw ScaleError.apiSyncFailed(error)
        }
    }
    
    func editDevice(_ deviceId: String, properties: [String: Any]) async throws -> Device {
        guard let _ = try? await localRepository.getDevice(deviceId) else {
            throw ScaleError.deviceNotFound(id: deviceId)
        }
        _ = try await localRepository.editScale(deviceId, properties: properties)
        do {
            let apiDTO = try await remoteRepo.editScale(deviceId, properties: properties)
            if let localDevice = try await localRepository.getDevice(deviceId) {
                localDevice.isSynced = true
                try await localRepository.updateDevice(localDevice)
            }
            return Device(from: apiDTO)
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Failed to sync device edit to API")
            throw ScaleError.apiSyncFailed(error)
        }
    }
    
    func deleteDevice(_ deviceId: String, showToast: Bool) async throws {
        guard let _ = try? await localRepository.getDevice(deviceId) else {
            throw ScaleError.deviceNotFound(id: deviceId)
        }
        try await localRepository.deleteScale(deviceId)
        do {
            try await remoteRepo.deleteScale(deviceId)
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Failed to sync device deletion to API")
            throw ScaleError.apiSyncFailed(error)
        }
    }
}
