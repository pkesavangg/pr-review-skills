//
//  BluetoothServiceDeviceInfo.swift
//  Device info, logs, measurement data, and scale lifecycle (disconnect, delete R4).
//

import Combine
import Foundation
import GGBluetoothSwiftPackage
import SwiftData

@MainActor
extension BluetoothService {
    // MARK: - Device Info
    func getDeviceInfo(for device: Device, skipConnectionCheck: Bool = false) async -> Result<DeviceInfo, BluetoothServiceError> {
        guard skipConnectionCheck || device.isConnected == true else {
            logger.log(level: .error, tag: tag, message: "Cannot get device info - device is not connected: \(device.id)")
            return .failure(.deviceNotConnected)
        }

        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }

            let details = try await sdkOperationSerializer.execute(
                operationKey: "\(device.id):getDeviceInfo"
            ) { @MainActor in
                try await self.withTimeout(seconds: 10) {
                    try await self.ggBleSDK.getDeviceInfo(ggDevice)
                }
            }
            guard let deviceDetails = details else {
                return .failure(.deviceNotConnected)
            }
            return .success(DeviceInfo(sdk: deviceDetails))

        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to get device info: \(error.localizedDescription)")
            return .failure(.updateProfileFailed(error))
        }
    }

    func getDeviceLogs(for device: Device) async -> Result<DeviceLogs, BluetoothServiceError> {
        do {
            guard let ggDevice = mapToGGBTDevice(device) else {
                throw BluetoothServiceError.invalidBroadcastId
            }
            let response = try await ggBleSDK.getDeviceLogs(ggDevice)
            let deviceLogs = DeviceLogs(logs: response.logs.map { log in
                DeviceLogEntry(macAddress: log.macAddress, log: log.log)
            })
            return .success(deviceLogs)
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.getDeviceLogsFailed(error))
        }
    }

    func getMeasurementLiveData(broadcastId: String) async -> Result<MeasurementLiveData, BluetoothServiceError> {
        do {
            let ggDevice = mapToGGBTDevice(broadcastId)
            _ = try await ggBleSDK.getMeasurementLiveData(ggDevice)
            let liveData = MeasurementLiveData(weight: 0)
            return .success(liveData)
        } catch let error as BluetoothServiceError {
            return .failure(error)
        } catch {
            return .failure(.updateProfileFailed(error))
        }
    }

    func updateWeightOnlyMode(on connectedScale: Device?) async -> Result<Void, BluetoothServiceError> {
        var scales: [Device] = []
        if let connectedScale = connectedScale {
            scales.append(connectedScale)
        } else {
            scales = bluetoothScales.filter { scale in (scale.isConnected ?? false) }
        }
        for scale in scales {
            _ = await updateSetting(on: scale, settings: [
                DeviceSetting(key: "SESSION_IMPEDANCE", value: DeviceSettingValue.bool(true))
            ])
        }
        return .success(())
    }

    func clearScaleDiscoveredInfo() {
        skipDevices.removeAll()
        reconnectAlertSkippedDevices.removeAll()
    }

    func disconnectConnectedScales() async {
        let connectedScales = bluetoothScales.filter { $0.isConnected == true }
        for scale in connectedScales {
            if let broadcastId = scale.broadcastIdString {
                scale.isWeighOnlyModeEnabledByOthers = false
                await scaleService.updateConnectedDeviceWeightOnlyMode(
                    broadcastId: broadcastId,
                    isWeightOnlyModeEnabledByOthers: false
                )
                _ = await disconnectDevice(broadcastId: broadcastId)
            }
        }
        skipDevices.removeAll()
    }

    func deleteR4Scales() async -> Result<Void, BluetoothServiceError> {
        let connectedR4Scales = bluetoothScales.filter { scale in
            let isConnected = scale.isConnected ?? false
            let isR4Scale: Bool = {
                if let raw = getSafeScaleType(for: scale) { return ScaleSourceType(rawValue: raw) == .btWifiR4 }
                return false
            }()
            return isConnected && isR4Scale
        }

        logger.log(level: .info, tag: tag, message: "Found \(connectedR4Scales.count) connected R4 scales to delete")

        for scale in connectedR4Scales {
            if let broadcastId = scale.broadcastIdString {
                scale.isWeighOnlyModeEnabledByOthers = false
                await scaleService.updateConnectedDeviceWeightOnlyMode(
                    broadcastId: broadcastId,
                    isWeightOnlyModeEnabledByOthers: false
                )
            }
            let deleteResult = await deleteDevice(scale, disconnect: false)
            switch deleteResult {
            case .success(let result):
                logger.log(level: .info, tag: tag, message: "Successfully deleted R4 scale: \(scale.deviceName ?? "Unknown")", data: result)
            case .failure(let error):
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Failed to delete R4 scale \(scale.deviceName ?? "Unknown"): \(error.localizedDescription)"
                )
            }

            if let broadcastId = scale.broadcastIdString {
                let disconnectResult = await disconnectDevice(broadcastId: broadcastId)
                switch disconnectResult {
                case .success(let result):
                    logger.log(level: .info, tag: tag, message: "Successfully disconnected R4 scale: \(broadcastId)", data: result)
                case .failure(let error):
                    logger.log(level: .error, tag: tag, message: "Failed to disconnect R4 scale \(broadcastId): \(error.localizedDescription)")
                }
            }
        }

        return .success(())
    }
}
