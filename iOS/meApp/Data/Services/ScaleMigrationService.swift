//
//  ScaleMigrationService.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 08/01/25.
//

import Foundation

/// Service to migrate scale data from Ionic app (Capacitor Preferences) to SwiftUI app (SwiftData)
@MainActor
final class ScaleMigrationService {
    @Injector private var logger: LoggerService
    @Injector private var scaleService: ScaleService
    private let scaleRepository = ScaleRepository()
    private let kvStorage = KvStorageService.shared
    
    private let tag = "ScaleMigrationService"
    
    // Using shared public MigrationKey enum for key composition
    
    /// Checks if scale migration is needed by looking for Ionic app scale data
    func isMigrationNeeded(for accountId: String) -> Bool {
        let scaleKey = MigrationKey.scaleKey(for: accountId)
        let hasScaleData = kvStorage.getValue(forKey: scaleKey) != nil
        logger.log(level: .info, tag: tag, message: "Scale migration check for account \(accountId) with key: \(scaleKey) - Scale data exists: \(hasScaleData)")
        return hasScaleData
    }
    
    /// Migrates scale data from Ionic app to SwiftUI app for a specific account
    /// - Parameters:
    ///   - accountId: The account ID to migrate scales for
    /// - Returns: The migrated devices if successful
    func migrateScaleData(for accountId: String) async throws -> [Device] {
        logger.log(level: .info, tag: tag, message: "Starting scale data migration from Ionic app for account: \(accountId)")
        
        guard let ionicScales = getStoredIonicScaleData(for: accountId) else {
            logger.log(level: .error, tag: tag, message: "No Ionic scale data found to migrate for account: \(accountId)")
            return []
        }
        
        var migratedDevices: [Device] = []
        
        for ionicScale in ionicScales {
            do {
                let device = try convertIonicScaleToDevice(ionicScale, accountId: accountId)
                
                // Save device to SwiftData
                migratedDevices.append(device)
                _ = try await self.scaleService.createScaleInLocal(device)
                logger.log(level: .info, tag: tag, message: "scaleRepository.createScale: \(ionicScale.id ?? "unknown") for account: \(device.sku ?? "unknown")")
                logger.log(level: .info, tag: tag, message: "Successfully migrated scale: \(ionicScale.id ?? "unknown") for account: \(accountId)")
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to migrate scale \(ionicScale.id ?? "unknown"): \(error.localizedDescription)")
                // Continue with other scales even if one fails
            }
        }
        await self.scaleService.syncAllScalesWithRemote()
        logger.log(level: .info, tag: tag, message: "Scale migration completed successfully for account: \(accountId). Migrated \(migratedDevices.count) scales")
        
        return migratedDevices
    }
    
    /// Removes Ionic app scale data after successful migration
    func cleanupAfterMigration(for accountId: String) {
        logger.log(level: .info, tag: tag, message: "Cleaning up Ionic app scale data after migration for account: \(accountId)")
        
        let scaleKey = MigrationKey.scaleKey(for: accountId)
        kvStorage.clearValue(forKey: scaleKey)
        
        logger.log(level: .info, tag: tag, message: "Ionic app scale data cleanup completed for account: \(accountId)")
    }
    
    // MARK: - Private Methods
    
    /// Retrieves stored Ionic scale data for a specific account
    private func getStoredIonicScaleData(for accountId: String) -> [IonicScaleData]? {
        let scaleKey = MigrationKey.scaleKey(for: accountId)
        
        guard let scaleString = kvStorage.getValue(forKey: scaleKey) as? String else {
            logger.log(level: .error, tag: tag, message: "No scale data string found for account: \(accountId) with key: \(scaleKey)")
            return nil
        }
        
        guard let scaleData = scaleString.data(using: .utf8) else {
            logger.log(level: .error, tag: tag, message: "Failed to convert scale string to data for account: \(accountId)")
            return nil
        }
        
        do {
            let decoder = JSONDecoder()
            let ionicScales = try decoder.decode([IonicScaleData].self, from: scaleData)
            logger.log(level: .info, tag: tag, message: "Successfully parsed \(ionicScales.count) Ionic scales for account: \(accountId)")
            return ionicScales
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to decode Ionic scale data for account \(accountId): \(error.localizedDescription)")
            return nil
        }
    }
    
    /// Converts Ionic scale data to SwiftUI Device model
    private func convertIonicScaleToDevice(_ ionicScale: IonicScaleData, accountId: String) throws -> Device {
        logger.log(level: .info, tag: tag, message: "Converting Ionic scale to Device model: \(ionicScale.id ?? "unknown")")
        
        // Create BathScale
        let bathScale = BathScale(
            scaleType: ionicScale.type,
            bodyComp: determineBodyCompSupport(for: ionicScale),
            device: nil // Will be set after device creation
        )
        
        // Create R4ScalePreference if it exists
        var r4Preference: R4ScalePreference?
        if let preference = ionicScale.preference {
            r4Preference = R4ScalePreference(
                scaleId: ionicScale.id ?? UUID().uuidString,
                displayName: preference.displayName,
                displayMetrics: preference.displayMetrics,
                shouldFactoryReset: preference.shouldFactoryReset,
                shouldMeasureImpedance: preference.shouldMeasureImpedance,
                shouldMeasurePulse: preference.shouldMeasurePulse,
                timeFormat: preference.timeFormat,
                tzOffset: preference.tzOffset,
                wifiFotaScheduleTime: preference.wifiFotaScheduleTime ?? 0,
                updatedAt: nil,
                device: nil // Will be set after device creation
            )
        }
        
        // Create DeviceMetaData if latestVersion exists
        var metaData: DeviceMetaData?
        if let latestVersion = ionicScale.latestVersion {
            metaData = DeviceMetaData(
                latestVersion: latestVersion,
                device: nil // Will be set after device creation
            )
        }
        // Create Device
        let device = Device(
            id: ionicScale.id ?? UUID().uuidString,
            accountId: accountId,
            peripheralIdentifier: ionicScale.peripheralIdentifier,
            nickname: ionicScale.nickname,
            sku: ionicScale.sku,
            mac: ionicScale.mac,
            password: ionicScale.password.map { Int64($0) },
            isSoftDeleted: ionicScale.isDeleted, // Deletion status is preserved from Ionic data
            deviceName: ionicScale.name,
            deviceType: "scale",
            broadcastId: ionicScale.broadcastId.map { Int64($0) },
            broadcastIdString: nil, // Will be computed in init
            userNumber: ionicScale.userNumber.map { String($0) },
            protocolType: determineProtocolType(for: ionicScale),
            createdAt: ionicScale.createdAt,
            lastModified: nil,
            isSynced: !(ionicScale.isTemporary ?? false), // Is synced if not temporary
            hasServerID: !(ionicScale.isTemporary ?? false), // Has server ID from Ionic app
            isConnected: false, // Default to not connected
            wifiMac: nil, // Not available in Ionic data
            isWifiConfigured: false, // Default to not configured
            token: ionicScale.scaleToken,
            bathScale: bathScale,
            r4ScalePreference: r4Preference,
            metaData: metaData
        )
        
        // Set the device reference in related objects
        bathScale.device = device
        r4Preference?.device = device
        metaData?.device = device
        
        logger.log(level: .info, tag: tag, message: "Successfully converted Ionic scale to Device model: \(device.id)")
        return device
    }
    
    /// Determines body composition support based on scale type and SKU
    private func determineBodyCompSupport(for ionicScale: IonicScaleData) -> Bool {
        // R4 scales and certain SKUs support body composition
        if ionicScale.type == "btWifiR4" { return true }
        
        // Check for specific SKUs that support body composition
        if let sku = ionicScale.sku {
            let bodyCompSKUs = ["0412", "0343", "0344"] // Add more as needed
            return bodyCompSKUs.contains(sku)
        }
        
        return false
    }
    
    /// Determines protocol type based on scale type
    private func determineProtocolType(for ionicScale: IonicScaleData) -> String? {
        switch ionicScale.type {
        case "btWifiR4":
            return "R4"
        case "bluetooth":
            return "A3"
        case "lcbt":
            return "A6"
        default:
            return nil
        }
    }
}
