//
//  BluetoothServiceHelpers.swift
//  Mapping, parsing, timeout and BLE characteristic/connection/data helpers for BluetoothService.
//

import Foundation
import GGBluetoothSwiftPackage
import SwiftData

// MARK: - Helpers & Mapping
@MainActor
extension BluetoothService {
    func mapToGGBTDevice(_ device: Device) -> GGBTDevice? {
        guard let bid = device.broadcastIdString else { return nil }
        return GGBTDevice(
            name: device.deviceName ?? "",
            broadcastId: bid,
            password: convertIntToHex(device.password ?? 0, protocolType: ProtocolType(rawValue: device.protocolType ?? "") ?? .A6),
            token: device.token,
            userNumber: Int(device.userNumber ?? "0") ?? 0,
            preference: mapToGGPreference(deviceId: device.id, preference: device.r4ScalePreference),
            syncAllData: nil,
            batteryLevel: 0,
            protocolType: device.protocolType ?? "",
            macAddress: device.mac ?? ""
        )
    }

    func mapToGGBTDevice(_ snapshot: DeviceSnapshot) -> GGBTDevice? {
        guard let bid = snapshot.broadcastIdString else { return nil }
        return GGBTDevice(
            name: snapshot.deviceName ?? "",
            broadcastId: bid,
            password: convertIntToHex(snapshot.password ?? 0, protocolType: ProtocolType(rawValue: snapshot.protocolType ?? "") ?? .A6),
            token: snapshot.token,
            userNumber: Int(snapshot.userNumber ?? "0") ?? 0,
            preference: mapToGGPreference(deviceId: snapshot.id, preference: nil),
            syncAllData: nil,
            batteryLevel: 0,
            protocolType: snapshot.protocolType ?? "",
            macAddress: snapshot.mac ?? ""
        )
    }

    func mapToGGBTDevice(_ broadcastId: String) -> GGBTDevice {
        GGBTDevice(
            name: "",
            broadcastId: broadcastId,
            password: nil,
            token: nil,
            userNumber: 0,
            preference: nil,
            syncAllData: nil,
            batteryLevel: 0,
            protocolType: "",
            macAddress: ""
        )
    }

    func fetchAttachedPreference(by id: String) -> R4ScalePreference? {
        scaleService.fetchAttachedPreferenceSync(by: id)
    }

    func mapToGGPreference(deviceId: String, preference: R4ScalePreference?) -> GGDevicePreference? {
        guard let attachedPreference = fetchAttachedPreference(by: deviceId) else {
            return nil
        }
        return GGDevicePreference(
            displayName: attachedPreference.displayName,
            displayMetrics: attachedPreference.displayMetrics,
            shouldMeasureImpedance: attachedPreference.shouldMeasureImpedance,
            shouldMeasurePulse: attachedPreference.shouldMeasurePulse,
            timeFormat: attachedPreference.timeFormat
        )
    }

    // MARK: - Characteristic parsing (from BLECharacteristicHandler)
    func parseWifiStatus(_ deviceData: GGScanResponseData) -> (broadcastId: String, isConfigured: Bool)? {
        guard let deviceInfo = deviceData as? GGDeviceDetails else { return nil }
        return (deviceInfo.broadcastId ?? "", deviceInfo.isWifiConfigured ?? false)
    }

    func parsePermissionStatus(_ permissionData: GGScanResponseData) -> GGPermissionResponseData? {
        permissionData as? GGPermissionResponseData
    }

    // MARK: - Data parsing (from BLEDataParser)
    func convertHexToInt(_ hex: String) -> Int64 {
        let evenHex = hex.count % 2 == 0 ? hex : "0" + hex
        let bytes = stride(from: 0, to: evenHex.count, by: 2).map {
            let start = evenHex.index(evenHex.startIndex, offsetBy: $0)
            let end = evenHex.index(start, offsetBy: 2)
            return String(evenHex[start..<end])
        }
        let reversedHex = bytes.reversed().joined().uppercased()
        return Int64(reversedHex, radix: 16) ?? 0
    }

    func convertIntToHex(_ value: Int64, protocolType: ProtocolType) -> String {
        var hex = String(value, radix: 16)
        switch protocolType {
        case .R4:
            hex = String(repeating: "0", count: max(0, 12 - hex.count)) + hex
        default:
            if hex.count < 8 {
                hex = String(repeating: "0", count: 8 - hex.count) + hex
            } else if hex.count > 8 && hex.count < 12 {
                hex = String(repeating: "0", count: 12 - hex.count) + hex
            }
        }
        var bytes: [String] = []
        for i in stride(from: 0, to: hex.count, by: 2) {
            let start = hex.index(hex.startIndex, offsetBy: i)
            let end = hex.index(start, offsetBy: 2)
            bytes.append(String(hex[start..<end]))
        }
        return bytes.reversed().joined().uppercased()
    }

    func roundMetric(_ metric: Float?) -> Int? {
        guard let metric else { return nil }
        return Int(round(Double(metric) * 10))
    }

    func roundMetric(_ metric: Double?) -> Int? {
        guard let metric else { return nil }
        return Int(round(metric * 10))
    }

    func mapProtocolToDeviceModelType(_ protocolType: String) -> DeviceSourceType {
        switch protocolType {
        case "A3": return .bluetooth
        case "A6": return .bluetoothScale
        case "R4": return .btWifiR4
        default: return .bluetoothScale
        }
    }

    func mapDeviceDetailsToDevice(
        _ deviceDetails: GGDeviceDetails,
        accountId: String,
        isA3Device: Bool,
        broadcastId: Int64
    ) -> Device {
        Device(
            id: UUID().uuidString,
            accountId: accountId,
            mac: deviceDetails.macAddress,
            deviceName: deviceDetails.deviceName,
            broadcastId: isA3Device ? Int64(deviceDetails.broadcastId ?? "0") : broadcastId,
            broadcastIdString: deviceDetails.broadcastIdString,
            userNumber: deviceDetails.userNumber.map { String($0) },
            isConnected: false
        )
    }

    // MARK: - Timeout Helper
    func withTimeout<T>(seconds: TimeInterval, operation: @escaping () async throws -> T) async throws -> T {
        let nanosecondsPerSecond: UInt64 = 1_000_000_000
        return try await withThrowingTaskGroup(of: T.self) { group in
            group.addTask { try await operation() }
            group.addTask {
                try await Task.sleep(nanoseconds: UInt64(seconds) * nanosecondsPerSecond)
                throw BluetoothServiceError.timeout
            }
            guard let result = try await group.next() else {
                group.cancelAll()
                throw BluetoothServiceError.timeout
            }
            group.cancelAll()
            return result
        }
    }
}
