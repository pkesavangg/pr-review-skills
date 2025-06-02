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
final class ScaleService: ScaleServiceProtocol {
    private lazy var remoteRepo: ScaleAPIRepository = {
        // Ensure this is always created on the main actor
        return _apiRepository
    }()
    private let _apiRepository: ScaleAPIRepository
    private let localRepository: ScaleRepository
    private let localKVRepo: ScaleRepositoryLocal
    private let accountService: AccountServiceProtocol
    private let logger = AppLogger.shared
    
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
            throw NSError(domain: "ScaleService", code: 401, userInfo: [NSLocalizedDescriptionKey: "No active account"])
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
            return
        }
        let unsynced: [Device]
        do {
            unsynced = try await localRepository.getUnsyncedDevices()
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Failed to fetch unsynced devices: \(error.localizedDescription)")
            return
        }
        do {
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
            _ = try? await localKVRepo.getLastSyncTimestamp(accountId: accountId)
            let remoteScales = try await remoteRepo.listScales()
            await mergeRemoteScales(remoteScales, accountId: accountId)
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
        for remoteDTO in remoteScales {
            guard let deviceId = remoteDTO.id else { continue }
            let localDevice = try? await localRepository.getDevice(deviceId)
            if let localDevice = localDevice {
                if let remoteCreatedAt = remoteDTO.createdAt,
                   let localCreatedAt = localDevice.createdAt,
                   remoteCreatedAt > localCreatedAt {
                    let updated = Device(from: remoteDTO)
                    updated.isSynced = true
                    try? await localRepository.updateDevice(updated)
                }
            } else {
                let newDevice = Device(from: remoteDTO)
                newDevice.isSynced = true
                try? await localRepository.updateDevice(newDevice)
            }
        }
    }
    
    // MARK: - ScaleServiceProtocol Implementation
    func updateScaleMeta(_ deviceId: String, metaData: DeviceMetaData) async throws {
        let metaDataDTO = metaData.toDTO()
        try await localRepository.patchScaleMeta(deviceId, metaData: metaDataDTO)
        do {
            try await remoteRepo.patchScaleMeta(deviceId, metaData: metaDataDTO)
            if let device = try await localRepository.getDevice(deviceId) {
                device.isSynced = true
                try await localRepository.updateDevice(device)
            }
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Failed to sync scale meta to API: \(error.localizedDescription)")
            throw error
        }
    }
    
    func updateScalePreference(_ preference: R4ScalePreference) async throws {
        let preferenceDTO = preference.toDTO()
        try await localRepository.patchScalePreference(preferenceDTO)
        do {
            try await remoteRepo.patchScalePreference(preferenceDTO)
            if let device = try await localRepository.getDevice(preference.displayName) {
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
            for dto in apiDevices {
                _ = try? await localRepository.createScale(dto)
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
        let apiScales = try await remoteRepo.listScales()
        _ = try await localRepository.listScales()
        for dto in apiScales {
            _ = try? await localRepository.createScale(dto)
        }
        if let tempDevice = tempDevice {
            let dto = tempDevice.toDTO()
            _ = try? await localRepository.createScale(dto)
        }
        await syncUnsyncedDevices()
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
            logger.log(level: .error, tag: "ScaleService", message: "Failed to sync new device to API: \(error.localizedDescription)")
            throw error
        }
    }
    
    func editDevice(_ deviceId: String, properties: [String: Any]) async throws -> Device {
        _ = try await localRepository.editScale(deviceId, properties: properties)
        do {
            let apiDTO = try await remoteRepo.editScale(deviceId, properties: properties)
            if let localDevice = try await localRepository.getDevice(deviceId) {
                localDevice.isSynced = true
                try await localRepository.updateDevice(localDevice)
            }
            return Device(from: apiDTO)
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Failed to sync device edit to API: \(error.localizedDescription)")
            throw error
        }
    }
    
    func deleteDevice(_ deviceId: String, showToast: Bool) async throws {
        try await localRepository.deleteScale(deviceId)
        do {
            try await remoteRepo.deleteScale(deviceId)
        } catch {
            logger.log(level: .error, tag: "ScaleService", message: "Failed to sync device deletion to API: \(error.localizedDescription)")
            throw error
        }
    }
}
