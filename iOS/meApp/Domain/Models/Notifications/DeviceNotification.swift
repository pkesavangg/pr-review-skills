//
//  DeviceNotification.swift
//  meApp
//
//  Sendable notification type for Device model changes.
//  Use this to safely pass device data across actor boundaries.
//

import Foundation
import SwiftData

/// A Sendable notification containing extracted Device data.
/// Use this instead of passing Device @Model objects across actor boundaries.
///
/// ## Usage
/// ```swift
/// // In DeviceService (MainActor context):
/// let notification = DeviceNotification(from: device)
/// deviceUpdated.send(notification)
///
/// // In subscriber:
/// scaleService.deviceUpdated.sink { notification in
///     // Safe to use notification.deviceName, notification.isConnected, etc.
/// }
/// ```
struct DeviceNotification: Sendable, Identifiable, Equatable {
    // MARK: - Core Properties
    let id: String
    /// The PersistentIdentifier for refetching the Device on MainActor.
    let persistentId: PersistentIdentifier?
    let accountId: String
    let nickname: String?
    let deviceName: String?
    let deviceType: String?
    let sku: String?
    let mac: String?
    let broadcastIdString: String?
    let protocolType: String?
    let token: String?

    // MARK: - Status Properties
    let isConnected: Bool
    let isWifiConfigured: Bool
    let isSynced: Bool
    let isSoftDeleted: Bool
    let hasServerID: Bool
    let isWeighOnlyModeEnabledByOthers: Bool

    // MARK: - R4ScalePreference Data (extracted from relationship)
    let preferenceDisplayName: String?
    let shouldMeasureImpedance: Bool?
    let shouldMeasurePulse: Bool?
    let displayMetrics: [String]?
    let timeFormat: String?
    let preferenceIsSynced: Bool?

    // MARK: - BathScale Data (extracted from relationship)
    let scaleType: String?
    let bodyComp: Bool?

    // MARK: - Initializers

    /// Creates a notification by extracting all data from a Device.
    /// Must be called on MainActor to safely access Device relationships.
    @MainActor
    init(from device: Device) {
        self.id = device.id
        self.persistentId = device.persistentModelID
        self.accountId = device.accountId
        self.nickname = device.nickname
        self.deviceName = device.deviceName
        self.deviceType = device.deviceType
        self.sku = device.sku
        self.mac = device.mac
        self.broadcastIdString = device.broadcastIdString
        self.protocolType = device.protocolType
        self.token = device.token

        // Status properties
        self.isConnected = device.isConnected ?? false
        self.isWifiConfigured = device.isWifiConfigured ?? false
        self.isSynced = device.isSynced ?? false
        self.isSoftDeleted = device.isSoftDeleted ?? false
        self.hasServerID = device.hasServerID
        self.isWeighOnlyModeEnabledByOthers = device.isWeighOnlyModeEnabledByOthers ?? false

        // Extract R4ScalePreference data (relationship)
        if let preference = device.r4ScalePreference {
            self.preferenceDisplayName = preference.displayName
            self.shouldMeasureImpedance = preference.shouldMeasureImpedance
            self.shouldMeasurePulse = preference.shouldMeasurePulse
            self.displayMetrics = preference.displayMetrics
            self.timeFormat = preference.timeFormat
            self.preferenceIsSynced = preference.isSynced
        } else {
            self.preferenceDisplayName = nil
            self.shouldMeasureImpedance = nil
            self.shouldMeasurePulse = nil
            self.displayMetrics = nil
            self.timeFormat = nil
            self.preferenceIsSynced = nil
        }

        // Extract BathScale data (relationship)
        if let bathScale = device.bathScale {
            self.scaleType = bathScale.scaleType
            self.bodyComp = bathScale.bodyComp
        } else {
            self.scaleType = nil
            self.bodyComp = nil
        }
    }

    /// Creates a notification from a DeviceDTO (for cases where Device is not available).
    init(from dto: DeviceDTO, persistentId: PersistentIdentifier? = nil) {
        self.id = dto.id ?? UUID().uuidString
        self.persistentId = persistentId
        self.accountId = dto.userId ?? ""
        self.nickname = dto.nickname
        self.deviceName = dto.name
        self.deviceType = "scale"
        self.sku = dto.sku
        self.mac = dto.mac
        self.broadcastIdString = dto.broadcastIdString
        self.protocolType = nil
        self.token = dto.scaleToken

        self.isConnected = dto.isConnected ?? false
        self.isWifiConfigured = dto.isWifiConfigured ?? false
        self.isSynced = true
        self.isSoftDeleted = dto.isDeleted ?? false
        self.hasServerID = dto.id != nil
        self.isWeighOnlyModeEnabledByOthers = dto.isWeighOnlyModeEnabledByOthers ?? false

        if let preference = dto.preference {
            self.preferenceDisplayName = preference.displayName
            self.shouldMeasureImpedance = preference.shouldMeasureImpedance
            self.shouldMeasurePulse = preference.shouldMeasurePulse
            self.displayMetrics = preference.displayMetrics
            self.timeFormat = preference.timeFormat
            self.preferenceIsSynced = nil
        } else {
            self.preferenceDisplayName = nil
            self.shouldMeasureImpedance = nil
            self.shouldMeasurePulse = nil
            self.displayMetrics = nil
            self.timeFormat = nil
            self.preferenceIsSynced = nil
        }

        self.scaleType = dto.type
        self.bodyComp = nil
    }

    // MARK: - Computed Properties

    /// Returns the display name, falling back to deviceName or nickname.
    var displayName: String {
        preferenceDisplayName ?? deviceName ?? nickname ?? "Scale"
    }

    /// Whether the device is in weight-only mode (impedance measurement disabled).
    var isWeightOnlyMode: Bool {
        !(shouldMeasureImpedance ?? true)
    }

    /// Whether heart rate measurement is enabled.
    var isHeartRateEnabled: Bool {
        shouldMeasurePulse ?? false
    }
}
