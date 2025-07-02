//
//  ScaleService.swift
//  meApp
//
//  Created by Lakshmi Priya on 02/06/25.
//

import Foundation
import SwiftData
import Combine

/// Service for managing paired scale devices, including sync, CRUD, and connection management.
/// Handles local/remote sync, per-account operations, and robust error handling.
@MainActor
final class ScaleService: ObservableObject, ScaleServiceProtocol {
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

    // MARK: - Helper
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

    // MARK: - Sync Logic
    /// Syncs all unsynced scales with the remote backend. Call this on app start or after network recovery.
    public func syncAllScalesWithRemote() async {
        let accountId: String
        do {
            accountId = try await getAccountId()
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to get account ID for sync: \(error.localizedDescription)")
            return
        }

        // Get unsynced devices
        let unsynced: [Device]
        do {
            unsynced = try await localRepository.getUnsyncedDevices()
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to fetch unsynced devices: \(error.localizedDescription)")
            return
        }

        for device in unsynced {
            let dto = device.toDTO()
            do {
                let properties: [String: Any] = [
                    "nickname": dto.nickname as Any, "name": dto.name as Any, "type": dto.type as Any, "isDeleted": dto.isDeleted as Any,
                    "isConnected": dto.isConnected as Any, "isWifiConfigured": dto.isWifiConfigured as Any, "mac": dto.mac as Any, "sku": dto.sku as Any,
                    "broadcastId": dto.broadcastId as Any, "broadcastIdString": dto.broadcastIdString as Any, "userNumber": dto.userNumber as Any,
                    "createdAt": dto.createdAt as Any, "scaleToken": dto.scaleToken as Any
                ]
                _ = try await remoteRepo.editScale(device.id, properties: properties)
                device.isSynced = true
                try await localRepository.updateDevice(device)
            } catch {
                do {
                    _ = try await remoteRepo.createScale(dto)
                    device.isSynced = true
                    try await localRepository.updateDevice(device)
                } catch {
                    logger.log(level: .error, tag: tag, message: "Failed to sync device \(device.id) to API: \(error.localizedDescription)")
                }
            }
        }
        do {
            let remoteScales = try await remoteRepo.listScales()
            await mergeRemoteScales(remoteScales, accountId: accountId)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to fetch remote scales: \(error.localizedDescription)")
        }
        let now = ISO8601DateFormatter().string(from: Date())
        try? await localKVRepo.setLastSyncTimestamp(accountId: accountId, timestamp: now)
    }

    private func syncUnsyncedDevices() async {
        let accountId: String
        do {
            accountId = try await getAccountId()
        } catch { return }
        let now = ISO8601DateFormatter().string(from: Date())
        try? await localKVRepo.setLastSyncTimestamp(accountId: accountId, timestamp: now)
    }

    private func mergeRemoteScales(_ remoteScales: [ScaleDTO], accountId: String) async {
        do {
            let existingDevices = try await localRepository.listScales()
            for remoteDTO in remoteScales {
                guard let deviceId = remoteDTO.id else { continue }
                if let managedDevice = try? await localRepository.getDevice(deviceId) {
                    updateDeviceWithRemoteData(managedDevice, remoteDTO: remoteDTO)
                    managedDevice.isSynced = true
                    try? await localRepository.updateDevice(managedDevice)
                } else {
                    let duplicateDevice = existingDevices.first { device in
                        isDuplicateDevice(device: device, remoteDTO: remoteDTO)
                    }
                    if let duplicateDevice = duplicateDevice {
                        duplicateDevice.id = deviceId
                        updateDeviceWithRemoteData(duplicateDevice, remoteDTO: remoteDTO)
                        duplicateDevice.isSynced = true
                        try? await localRepository.updateDevice(duplicateDevice)
                    } else {
                        let newDevice = Device(from: remoteDTO)
                        newDevice.isSynced = true
                        do {
                            _ = try await localRepository.createScale(newDevice)
                        } catch {
                            logger.log(level: .error, tag: tag, message: "Failed to create new device from remote: \(error.localizedDescription)")
                        }
                    }
                }
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to fetch existing devices: \(error.localizedDescription)")
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

    // MARK: - DeviceServiceProtocol Implementation
    func updateScaleMeta(_ deviceId: String, metaData: DeviceMetaData) async throws {
        let metaDataDTO = metaData.toDTO()
        guard let _ = try? await localRepository.getDevice(deviceId) else {
            throw ScaleError.deviceNotFound(id: deviceId)
        }
        try await localRepository.patchScaleMeta(deviceId, metaData: metaData)
        do {
            try await remoteRepo.patchScaleMeta(deviceId, metaData: metaDataDTO)
            if let device = try await localRepository.getDevice(deviceId) {
                device.isSynced = true
                try await localRepository.updateDevice(device)
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to sync scale meta to API")
            throw ScaleError.apiSyncFailed(error)
        }
    }

    func updateScalePreference(_ deviceId: String, _ preference: R4ScalePreference) async throws {
        let preferenceDTO = preference.toDTO()
        try await localRepository.patchScalePreference(deviceId, preference)
        do {
            try await remoteRepo.patchScalePreference(preferenceDTO)
            if let device = try await localRepository.getDevice(deviceId) {
                device.isSynced = true
                try await localRepository.updateDevice(device)
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to sync scale preference to API: \(error.localizedDescription)")
            throw error
        }
    }

    // MARK: - DeviceServiceProtocol Implementation
    func getDevices() async throws -> [Device] {
        let localDevices = try await localRepository.listScales()
        do {
            let apiDevices = try await remoteRepo.listScales()
            var updatedDevices: [Device] = []

            for dto in apiDevices {
                var deviceToUpdate: Device?

                // First, try to find by ID
                if let deviceId = dto.id {
                    deviceToUpdate = try? await localRepository.getDevice(deviceId)
                }

                // If not found by ID, try to find by other identifiers
                if deviceToUpdate == nil {
                    deviceToUpdate = localDevices.first { localDevice in
                        if let localBroadcastId = localDevice.broadcastIdString, let remoteBroadcastId = dto.broadcastIdString, localBroadcastId == remoteBroadcastId { return true }
                        if let localMac = localDevice.mac, let remoteMac = dto.mac, localMac == remoteMac { return true }
                        if let localSku = localDevice.sku, let remoteSku = dto.sku, localSku == remoteSku { return true }
                        return false
                    }
                }

                if let existingDevice = deviceToUpdate {
                    // Update existing device
                    existingDevice.id = dto.id ?? existingDevice.id
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
                    updatedDevices.append(existingDevice)
                } else {
                    // Create new device only if it doesn't exist
                    let newDevice = Device(from: dto)
                    newDevice.isSynced = true
                    _ = try? await localRepository.createScale(newDevice)
                    updatedDevices.append(newDevice)
                }
            }

                         // Add any local devices that weren't in the API response
             for localDevice in localDevices {
                 let isInApiResponse = apiDevices.contains { dto in
                     if let dtoId = dto.id, localDevice.id == dtoId { return true }
                     if let localBroadcastId = localDevice.broadcastIdString, let dtoBroadcastId = dto.broadcastIdString, localBroadcastId == dtoBroadcastId { return true }
                     if let localMac = localDevice.mac, let dtoMac = dto.mac, localMac == dtoMac { return true }
                     if let localSku = localDevice.sku, let dtoSku = dto.sku, localSku == dtoSku { return true }
                     return false
                 }
                 if !isInApiResponse {
                     updatedDevices.append(localDevice)
                 }
             }

            await MainActor.run { self.scales = updatedDevices }
            return updatedDevices
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to fetch from API, using local data: \(error.localizedDescription)")
            await MainActor.run { self.scales = localDevices }
            return localDevices
        }
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
                    Task { await syncUnsyncedDevices() }
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
                    Task { await syncUnsyncedDevices() }
                } else {
                    logger.log(level: .error, tag: tag, message: "Device not found with broadcast ID: \(broadcastId)")
                }
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to update device WiFi configuration status: \(error.localizedDescription)")
            }
        }
    }

    func syncDevices(tempDevice: Device?) async throws {
        do {
            let apiScales = try await remoteRepo.listScales()
            var localScales = try await localRepository.listScales()

            for dto in apiScales {
                // Skip if device already exists locally by ID
                if let deviceId = dto.id, let _ = try? await localRepository.getDevice(deviceId) {
                    continue
                }

                // Check for duplicates by other identifiers
                let existingDevice = localScales.first { localDevice in
                    isDuplicateDevice(device: localDevice, remoteDTO: dto)
                }

                if existingDevice == nil {
                    do {
                        let newDevice = Device(from: dto)
                        newDevice.isSynced = true
                        _ = try await localRepository.createScale(newDevice)
                        // Update localScales to include the new device for deduplication
                        localScales.append(newDevice)
                    } catch {
                        logger.log(level: .error, tag: tag, message: "Failed to create scale from API: \(error.localizedDescription)")
                        throw ScaleError.apiSyncFailed(error)
                    }
                }
            }

            if let tempDevice = tempDevice {
                // Check if temp device already exists
                let existingDevice = localScales.first { localDevice in
                    // Check by ID first
                    if localDevice.id == tempDevice.id { return true }
                    // Then check by other identifiers
                    return isDuplicateDevice(device: localDevice, remoteDTO: tempDevice.toDTO())
                }

                if existingDevice == nil {
                    do {
                        _ = try await localRepository.createScale(tempDevice)
                        localScales.append(tempDevice)
                    } catch {
                        logger.log(level: .error, tag: tag, message: "Failed to create temp device: \(error.localizedDescription)")
                        throw ScaleError.apiSyncFailed(error)
                    }
                }
            }
            await syncUnsyncedDevices()
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to sync devices: \(error.localizedDescription)")
            throw ScaleError.apiSyncFailed(error)
        }
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
            if let localSku = localDevice.sku, let newSku = dto.sku, localSku == newSku { return true }
            return false
        }

        if let existingDevice = existingDevice {
            logger.log(level: .info, tag: tag, message: "Device already exists, returning existing device: \(existingDevice.id)")
            return existingDevice
        }

        // Create locally first
        let createdDevice = try await localRepository.createScale(device)

        // Then sync to API
        do {
            let apiDTO = try await remoteRepo.createScale(dto)
            // Update local device with API response data
            if let localDevice = try await localRepository.getDevice(device.id) {
                localDevice.id = apiDTO.id ?? localDevice.id
                localDevice.isSynced = true
                try await localRepository.updateDevice(localDevice)
            }
            let saved = Device(from: apiDTO)
            await refreshScalesFromLocal()
            return saved
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to sync new device to API")
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
            let updated = Device(from: apiDTO)
            await refreshScalesFromLocal()
            return updated
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to sync device edit to API")
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
            logger.log(level: .error, tag: tag, message: "Failed to sync device deletion to API")
            throw ScaleError.apiSyncFailed(error)
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
}
