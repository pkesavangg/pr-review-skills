//
//  ScaleServiceHelpers.swift
//  meApp
//

import Foundation

struct ScaleServerIdentifiers {
    let ids: Set<String>
    let macs: Set<String>
    let broadcastIds: Set<String>

    func matches(_ device: Device) -> Bool {
        if ids.contains(device.id) {
            return true
        }
        if let mac = device.mac, macs.contains(mac) {
            return true
        }
        if let broadcastId = device.broadcastIdString, broadcastIds.contains(broadcastId) {
            return true
        }
        return false
    }
}

enum ScaleDeviceLogDescriptor {
    static func describe(device: Device, preference: R4ScalePreference?) -> String {
        let preferenceValues = PreferenceValues(preference: preference)
        // swiftlint:disable:next line_length
        return "id=\(device.id), accountId=\(device.accountId), sku=\(device.sku ?? "nil"), deviceName=\(device.deviceName ?? "nil"), nickname=\(device.nickname ?? "nil"), mac=\(device.mac ?? "nil"), wifiMac=\(device.wifiMac ?? "nil"), password=\(device.password.map(String.init) ?? "nil"), token=\(device.token ?? "nil"), broadcastId=\(device.broadcastId.map(String.init) ?? "nil"), broadcastIdString=\(device.broadcastIdString ?? "nil"), peripheralIdentifier=\(device.peripheralIdentifier ?? "nil"), userNumber=\(device.userNumber ?? "nil"), protocolType=\(device.protocolType ?? "nil"), createdAt=\(device.createdAt ?? "nil"), isConnected=\(device.isConnected.map(String.init) ?? "nil"), isWifiConfigured=\(device.isWifiConfigured.map(String.init) ?? "nil"), isSynced=\(device.isSynced.map(String.init) ?? "nil"), hasServerID=\(device.hasServerID), isSoftDeleted=\(device.isSoftDeleted.map(String.init) ?? "nil"), prefDisplayName=\(preferenceValues.displayName), prefDisplayMetrics=\(preferenceValues.displayMetrics), prefShouldFactoryReset=\(preferenceValues.factoryReset), prefImpedance=\(preferenceValues.impedance), prefPulse=\(preferenceValues.pulse), prefTimeFormat=\(preferenceValues.timeFormat), prefTzOffset=\(preferenceValues.tzOffset), prefWifiFotaScheduleTime=\(preferenceValues.wifiFotaScheduleTime), prefUpdatedAt=\(preferenceValues.updatedAt), prefIsSynced=\(preferenceValues.isSynced)"
    }

    private struct PreferenceValues {
        let displayName: String
        let displayMetrics: String
        let factoryReset: String
        let impedance: String
        let pulse: String
        let timeFormat: String
        let tzOffset: String
        let wifiFotaScheduleTime: String
        let updatedAt: String
        let isSynced: String

        init(preference: R4ScalePreference?) {
            displayName = preference?.displayName ?? "nil"
            displayMetrics = preference?.displayMetrics.joined(separator: "|") ?? "nil"
            factoryReset = preference != nil ? String(preference?.shouldFactoryReset ?? false) : "nil"
            impedance = preference != nil ? String(preference?.shouldMeasureImpedance ?? false) : "nil"
            pulse = preference != nil ? String(preference?.shouldMeasurePulse ?? false) : "nil"
            timeFormat = preference?.timeFormat ?? "nil"
            tzOffset = preference != nil ? String(preference?.tzOffset ?? 0) : "nil"
            wifiFotaScheduleTime = preference?.wifiFotaScheduleTime.map(String.init) ?? "nil"
            updatedAt = preference?.updatedAt ?? "nil"
            isSynced = preference != nil ? String(preference?.isSynced ?? false) : "nil"
        }
    }
}
