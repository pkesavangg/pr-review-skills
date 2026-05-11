//
//  BluetoothServiceCoreOperations.swift
//  Core Bluetooth operations: scan, sync, pair, delete, WiFi, live measurement, settings, firmware, profile.
//

import Combine
import Foundation
import GGBluetoothSwiftPackage
import SwiftData

@MainActor
extension BluetoothService {
    // MARK: - Scanning & Pairing
    /**
     Starts a smart scan for Bluetooth devices. Throws if scan cannot be started.
     */
    func scan() async {
        guard activeAccount != nil else {
            return
        }
        do {
            try await startSmartScan()
        } catch {
            logger.log(level: .error, tag: tag, message: BluetoothServiceError.scanFailed(error).localizedDescription)
        }
    }

    /**
     Forces a re-sync of locally stored devices with the Bluetooth plugin and re-starts scanning.
     - Returns: Result<Void, BluetoothServiceError>
     */
    func resyncAndScan() async -> Result<Void, BluetoothServiceError> {
        do {
            try await scaleService.updateAllScalesStatus(nil)
            clearScaleDiscoveredInfo()
            try await scaleService.syncDevices(tempDevice: nil)
            syncDevices(bluetoothScales)
            return .success(())
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.resyncFailed(error))
        }
    }

    /**
     Pauses the current smart scan without tearing down the session.
     */
    func pauseSmartScan() {
        discoveryManager.pauseScan(using: ggBleSDK)
    }

    /**
     Resumes a previously paused smart scan.
     - Parameter clearOnlyPairing: When true, clears only pairing-mode devices before resuming.
     */
    func resumeSmartScan(clearOnlyPairing: Bool) {
        discoveryManager.resumeScan(using: ggBleSDK, clearOnlyPairing: clearOnlyPairing)
    }

    /**
     Performs a dedicated scan intended for scale pairing.
     */
    func scanForPairing() {
        discoveryManager.scanForPairing(using: ggBleSDK)
    }

    // MARK: - Device Sync & CRUD
    /**
     Synchronises the provided device list with the Bluetooth plugin.
     - Parameter devices: The devices to sync. Passing an empty array clears the list.
     */
    func syncDevices(_ devices: [DeviceSnapshot]) {
        let scalesToSync = devices.isEmpty ? bluetoothScales : devices
        let ggDevices = scalesToSync.compactMap { mapToGGBTDevice($0) }
        let deviceNames = ggDevices.map { $0.name }
        let broadcastIds = ggDevices.map { $0.broadcastId }
        let passwords = ggDevices.map { $0.password }
        let userNumbers = ggDevices.map { $0.userNumber }
        let message = "syncDevices called in handleSmartScaleData: \(deviceNames) \(broadcastIds) \(passwords) \(broadcastIds) \(userNumbers)"
        logger.log(level: .info, tag: tag, message: message)
        ggBleSDK.syncDevices(ggDevices)
    }

    /**
     Adds a newly discovered scale to persistent storage and returns the saved model.
     - Returns: Result<Device, BluetoothServiceError>
     */
    func addNewDevice(_ scale: Device, metaData deviceDetails: DeviceMetaData?, _ skipDuplicateCheck: Bool? = false) async -> Result<Device, BluetoothServiceError> {
        do {
            guard let userId = activeAccount?.accountId else {
                throw BluetoothServiceError.noActiveAccount
            }
            let scaleToSave = scale
            scaleToSave.accountId = userId
            scaleToSave.createdAt = DateTimeTools.getCurrentDatetimeIsoString()
            scaleToSave.nickname = scale.nickname ?? "Bluetooth Smart Scale"
            scaleToSave.password = scale.password
            var metaData = deviceDetails
            let scaleType = scale.bathScale?.scaleType ?? ""
            if metaData == nil && (scaleType == ScaleSourceType.btWifiR4.rawValue || scaleType == ScaleSourceType.bluetooth.rawValue) {
                let deviceInfoResult = await getDeviceInfo(broadcastId: scale.broadcastIdString ?? "", skipConnectionCheck: true)
                switch deviceInfoResult {
                case .success(let deviceInfo):
                    let dto = ScaleMetaDataDTO(
                        firmwareRevision: deviceInfo.firmwareRevision?.replacingOccurrences(of: "\0", with: ""),
                        hardwareRevision: deviceInfo.hardwareRevision?.replacingOccurrences(of: "\0", with: ""),
                        latestFirmwareVersion: nil,
                        manufacturerName: deviceInfo.manufacturerName?.replacingOccurrences(of: "\0", with: ""),
                        modelNumber: deviceInfo.modelNumber?.replacingOccurrences(of: "\0", with: ""),
                        serialNumber: deviceInfo.serialNumber?.replacingOccurrences(of: "\0", with: ""),
                        softwareRevision: deviceInfo.softwareRevision?.replacingOccurrences(of: "\0", with: ""),
                        systemId: deviceInfo.systemID?.replacingOccurrences(of: "\0", with: ""),
                        wifiMac: ""
                    )
                    metaData = DeviceMetaData(from: dto)
                    if scaleType == ScaleSourceType.btWifiR4.rawValue {
                        let wifiMacResult = await getWifiMacAddress(broadcastId: scale.broadcastIdString ?? "")
                        switch wifiMacResult {
                        case .success(let wifiMacAddress):
                            scaleToSave.wifiMac = wifiMacAddress
                        case .failure(let error):
                            logger.log(level: .error, tag: tag, message: "Failed to get WiFi MAC address: \(error.localizedDescription)")
                        }
                    }
                case .failure(let error):
                    logger.log(level: .error, tag: tag, message: "Failed to get device info: \(error.localizedDescription)")
                }
            }
            scaleToSave.metaData = metaData
            let savedScale = try await scaleService.createDevice(scaleToSave, skipDuplicateCheck ?? false)
            try await scaleService.syncDevices(tempDevice: nil)
            return .success(savedScale)
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }

    /**
     Confirms a smart pairing operation with the specified device.
     - Returns: Result<UserCreationResponse, BluetoothServiceError>
     */
    func confirmSmartPair(device: Device, token: String, displayName: String, userNumber: Int?) async -> Result<UserCreationResponse, BluetoothServiceError> {
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            ggDevice.token = token
            ggDevice.userNumber = userNumber ?? 0
            let preference = GGDevicePreference(displayName: displayName)
            ggDevice.preference = preference

            let result = try await sdkOperationSerializer.execute(
                operationKey: "\(device.id):confirmPair"
            ) { @MainActor in
                try await self.withTimeout(seconds: 10) {
                    try await self.ggBleSDK.confirmPair(ggDevice, replaceUser: false, pairedSKUMonitors: [])
                }
            }

            return .success(UserCreationResponse(sdkType: result))
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }

    /**
     Deletes a scale's user slot from the physical device by looking up the token from the in-memory bluetoothScales list.
     - Returns: Result<UserDeletionResponse, BluetoothServiceError>
     */
    func deleteDevice(broadcastId: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> {
        guard let scale = bluetoothScales.first(where: { $0.broadcastIdString == broadcastId }),
              let token = scale.token, !token.isEmpty else {
            logger.log(level: .error, tag: tag, message: "deleteDevice: scale or token not found for broadcastId \(broadcastId)")
            return .failure(.deviceNotFound)
        }
        return await deleteUserByToken(broadcastId: broadcastId, token: token, disconnect: disconnect)
    }

    /// Deletes the current app user's slot on the BT WiFi (R4) scale when possible.
    func deleteCurrentUserFromScaleIfPossible(broadcastId: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> {
        if let scale = bluetoothScales.first(where: { $0.broadcastIdString == broadcastId }),
           let token = scale.token, !token.isEmpty {
            logger.log(level: .debug, tag: tag, message: "Deleting scale user using persisted token")
            if Task.isCancelled { return .failure(.timeout) }
            return await deleteUserByToken(broadcastId: broadcastId, token: token, disconnect: disconnect)
        }

        if Task.isCancelled { return .failure(.timeout) }
        let listResult = await getScaleUserList(broadcastId: broadcastId, skipConnectionCheck: true)
        switch listResult {
        case .success(let users):
            if Task.isCancelled { return .failure(.timeout) }
            let discoveredScale = bluetoothScales.first { $0.broadcastIdString == broadcastId }
            if let discoveredScale,
               let match = findUserToDelete(userList: users, discoveredScale: discoveredScale),
               let token = match.token, !token.isEmpty {
                logger.log(level: .debug, tag: tag, message: "Deleting matched scale user: \(match.name)")
                return await deleteUserByToken(broadcastId: broadcastId, token: token, disconnect: disconnect)
            } else {
                logger.log(level: .error, tag: tag, message: "No matching user found to delete on scale for broadcastId \(broadcastId)")
                return .failure(.deviceNotFound)
            }
        case .failure(let error):
            logger.log(level: .error, tag: tag, message: "Failed to fetch user list before deletion: \(error.localizedDescription)")
            return .failure(error)
        }
    }

    // MARK: - Wi-Fi Configuration
    func getWifiList(broadcastId: String) async -> Result<[WifiDetails], BluetoothServiceError> {
        do {
            let ggDevice = mapToGGBTDevice(broadcastId)
            let result = try await ggBleSDK.getWifiList(ggDevice)
            return .success(result.wifi.map { WifiDetails(macAddress: $0.macAddress, ssid: $0.ssid, rssi: $0.rssi, password: $0.password) })
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }

    func setupWifi(broadcastId: String, config: WifiConfig) async -> Result<WifiSetupResponse, BluetoothServiceError> {
        do {
            let ggDevice = mapToGGBTDevice(broadcastId)
            let ggConfig = GGBTWifiConfig(ssid: config.ssid, password: config.password ?? "")
            let ggResponse = try await ggBleSDK.setupWifi(ggDevice, ggConfig)
            let response = WifiSetupResponse(wifiState: ggResponse.wifiState, errorCode: ggResponse.errorCode)
            return .success(response)
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }

    func cancelWifi(broadcastId: String) async -> Result<Void, BluetoothServiceError> {
        do {
            let ggDevice = mapToGGBTDevice(broadcastId)
            try ggBleSDK.cancelWifi(ggDevice)
            return .success(())
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }

    func getConnectedWifiSSID(broadcastId: String) async -> Result<String, BluetoothServiceError> {
        do {
            let ggDevice = mapToGGBTDevice(broadcastId)
            let ssid = try await withTimeout(seconds: 10) {
                try await self.ggBleSDK.getConnectedWifiSSID(ggDevice)
            }
            return .success(ssid)
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.timeout)
        }
    }

    func getWifiMacAddress(broadcastId: String) async -> Result<String, BluetoothServiceError> {
        do {
            let ggDevice = mapToGGBTDevice(broadcastId)
            let mac = try await withTimeout(seconds: 10) {
                try await self.ggBleSDK.getWifiMacAddress(ggDevice)
            }
            return .success(mac)
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }

    // MARK: - Live Measurement
    @discardableResult
    func startLiveMeasurement(broadcastId: String) async -> Result<Void, BluetoothServiceError> {
        do {
            let ggDevice = mapToGGBTDevice(broadcastId)
            try ggBleSDK.startLiveMeasurement(ggDevice)
            return .success(())
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.startLiveMeasurementFailed(error))
        }
    }

    @discardableResult
    func stopLiveMeasurement(broadcastId: String) async -> Result<Void, BluetoothServiceError> {
        do {
            let ggDevice = mapToGGBTDevice(broadcastId)
            try ggBleSDK.stopLiveMeasurement(ggDevice)
            return .success(())
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.startLiveMeasurementFailed(error))
        }
    }

    // MARK: - Settings & Firmware
    func updateSetting(broadcastId: String, settings: [DeviceSetting]) async -> Result<Void, BluetoothServiceError> {
        do {
            let ggDevice = mapToGGBTDevice(broadcastId)
            let ggSettings = settings.map { setting in
                GGBTSetting(
                    key: GGBTSettingType(rawValue: setting.key) ?? .SESSION_IMPEDANCE,
                    value: setting.value.toGGBTSettingValue()
                )
            }
            try ggBleSDK.updateSetting(ggDevice, ggSettings)
            return .success(())
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }

    func updateFirmware(broadcastId: String, timestamp: UInt32) async -> Result<Void, BluetoothServiceError> {
        do {
            let ggDevice = mapToGGBTDevice(broadcastId)
            try ggBleSDK.startFirmwareUpdate(ggDevice, timestamp)
            let initialStatus = FirmwareUpdateStatus(progress: 0.0, isComplete: false)
            firmwareUpdateProgressSubject.send(initialStatus)
            return .success(())
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }

    func clearData(broadcastId: String, dataType: DeviceClearType) async -> Result<Void, BluetoothServiceError> {
        do {
            let ggDevice = mapToGGBTDevice(broadcastId)
            let sdkType: ClearDataType = {
                switch dataType {
                case .userData: return .ACCOUNT
                case .history: return .HISTORY
                case .wifi: return .WIFI
                case .settings: return .SETTINGS
                case .all: return .ALL
                }
            }()
            _ = try await ggBleSDK.clearData(ggDevice, sdkType)
            return .success(())
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }

    // MARK: - Profile & Account
    func updateUserProfileForR4Scales() async -> Result<[String], BluetoothServiceError> {
        guard !isUpdatingR4Profile else {
            logger.log(level: .debug, tag: tag, message: "updateUserProfileForR4Scales already in progress, skipping")
            return .failure(.updateProfileFailed(BluetoothServiceError.notImplemented))
        }

        isUpdatingR4Profile = true
        defer { isUpdatingR4Profile = false }

        do {
            guard let account = activeAccount else {
                throw BluetoothServiceError.noActiveAccount
            }
            guard let userProfile = await getProfileInfo(from: account) else {
                throw BluetoothServiceError.noProfileInfo
            }
            let success = try await ggBleSDK.updateProfile(profile: userProfile)
            logger.log(level: .debug, tag: tag, message: "updateUserProfileForR4Scales completed: \(success)")
            return .success(success)
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }

    func updateAccount(broadcastId: String) async -> Result<UserCreationResponse, BluetoothServiceError> {
        do {
            guard let ggDevice = bluetoothScales.first(where: { $0.broadcastIdString == broadcastId }).flatMap({ mapToGGBTDevice($0) }) else {
                throw BluetoothServiceError.invalidBroadcastId
            }

            let result = try await sdkOperationSerializer.execute(
                operationKey: "\(broadcastId):updateAccount"
            ) { @MainActor in
                try await self.withTimeout(seconds: 10) {
                    try await self.ggBleSDK.updateAccount(ggDevice)
                }
            }
            return .success(UserCreationResponse(sdkType: result))
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }

    func getScaleUserList(broadcastId: String, skipConnectionCheck: Bool = false) async -> Result<[DeviceUser], BluetoothServiceError> {
        let isConnected = bluetoothScales.first(where: { $0.broadcastIdString == broadcastId })?.isConnected ?? false
        guard skipConnectionCheck || isConnected else {
            logger.log(level: .error, tag: tag, message: "Cannot get user list - device is not connected: \(broadcastId)")
            return .failure(.deviceNotConnected)
        }

        do {
            let ggDevice = mapToGGBTDevice(broadcastId)

            let users = try await sdkOperationSerializer.execute(
                operationKey: "\(broadcastId):getUsers"
            ) { @MainActor in
                try await self.withTimeout(seconds: 10) {
                    try await self.ggBleSDK.getUsers(ggDevice)
                }
            }

            let deviceUsers = users.user.map { user in
                DeviceUser(
                    name: user.name,
                    token: user.token,
                    lastActive: user.lastActive,
                    isBodyMetricsEnabled: user.isBodyMetricsEnabled
                )
            }
            return .success(deviceUsers)
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to get user list: \(error.localizedDescription)")
            return .failure(.updateProfileFailed(error))
        }
    }
}
