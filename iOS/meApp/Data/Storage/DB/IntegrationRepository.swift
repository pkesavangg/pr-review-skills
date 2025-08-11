//
//  IntegrationRepository.swift
//  meApp
//
//  Created by Lakshmi Priya on 03/06/25.
//

import Foundation

@MainActor
final class IntegrationRepository: IntegrationRepositoryProtocol {
    // MARK: - Properties
    
    private let kvStorage: KvStorageService
    private let logger = LoggerService.shared
        
    // MARK: - Initialization
    
    init(kvStorage: KvStorageService = .shared) {
        self.kvStorage = kvStorage
    }
    
    // MARK: - IntegrationRepositoryProtocol
    
    /// Gets the stored integration data for the current device/account.
    /// - Parameter accountId: The account/user ID.
    /// - Returns: The stored IntegratedDeviceInfo, if any.
    func getIntegrationData(accountId: String) throws -> IntegrationInfo? {
        let key = makeIntegrationKey(for: accountId)
        guard let data = kvStorage.getValue(forKey: key) as? Data else {
            return nil
        }
        
        do {
            let decoder = JSONDecoder()
            let integrationData = try decoder.decode(IntegrationInfo.self, from: data)
            return integrationData
        } catch {
            logger.log(level: .error, tag: "IntegrationRepository", message: "Failed to decode integration info: \(error.localizedDescription)")
            throw error
        }
    }
    
    /// Sets the stored integration data for the current device/account.
    /// - Parameters:
    ///   - accountId: The account/user ID.
    ///   - info: The device info to store.
    func setIntegrationData(accountId: String, info: IntegrationInfo?) throws {
        let key = makeIntegrationKey(for: accountId)
        if let info = info {
            do {
                let encoder = JSONEncoder()
                let data = try encoder.encode(info)
                kvStorage.setValue(data, forKey: key)
                addIntegrationKey(key)
            } catch {
                logger.log(level: .error, tag: "IntegrationRepository", message: "Failed to encode integration info: \(error.localizedDescription)")
                throw error
            }
        } else {
            kvStorage.clearValue(forKey: key)
            removeIntegrationKey(key)
        }
    }
    
    /// Checks if the integration is already used by another device/account.
    /// - Parameters:
    ///   - accountId: The account/user ID.
    ///   - type: The integration type to check.
    /// - Returns: True if the integration is already used (conflict), false if available.
    func isIntegrationAlreadyUsed(accountId: String, type: IntegrationType) throws -> Bool {
        let integrationKeys = getIntegrationKeys()
        
        for key in integrationKeys {
            guard let data = kvStorage.getValue(forKey: key) as? Data else {
                continue
            }
            do {
                let info = try JSONDecoder().decode(IntegrationInfo.self, from: data)
                if info.type == type && info.isIntegrated && info.assignedTo != accountId {
                    return true
                }
            } catch {
                logger.log(level: .error, tag: "IntegrationRepository", 
                          message: "Failed to decode integration info for key \(key): \(error.localizedDescription)")
                continue
            }
        }
        
        return false
    }
    
    /// Clears the integration status for the given account (e.g., on account deletion).
    /// - Parameter accountId: The account/user ID.
    func clearIntegrationStatus(accountId: String) async throws {
        let key = makeIntegrationKey(for: accountId)
        kvStorage.clearValue(forKey: key)
        removeIntegrationKey(key)
    }
    
    /// Checks if HealthKit integration migration is needed for the given account.
    /// - Parameter accountId: The account/user ID.
    /// - Returns: True if HealthKit integration data exists in Ionic format and needs migration.
    func isHealthKitMigrationNeeded(accountId: String) -> Bool {
        // Using the existing kvStorage instance
        
        // Check for any Ionic HealthKit integration keys
        let ionicHealthKitKey = "\(accountId)-healthKitIntegrated"
        let ionicAssignedToKey = "healthKitIntegratedAssignedTo"
        let ionicDeintegratedKey = "healthKitDeintegrated-\(accountId)"
        
        let hasIntegratedFlag = kvStorage.getValue(forKey: ionicHealthKitKey) != nil
        let hasAssignedToFlag = kvStorage.getValue(forKey: ionicAssignedToKey) != nil
        let hasDeintegratedFlag = kvStorage.getValue(forKey: ionicDeintegratedKey) != nil
        
        return hasIntegratedFlag || hasAssignedToFlag || hasDeintegratedFlag
    }
    
    // MARK: - Private Helper Methods
    
    /// Creates a UserDefaults key for an integration with the given account ID
    /// - Parameter accountId: The account/user ID
    /// - Returns: The constructed key string
    private func makeIntegrationKey(for accountId: String) -> String {
        return KvStorageKeys.integrationInfoKey(for: accountId)
    }
    
    /// Gets the set of all integration keys stored in KvStorage
    private func getIntegrationKeys() -> Set<String> {
        guard let keysArray = kvStorage.getValue(forKey: KvStorageKeys.integrationKeys.rawValue) as? [String] else {
            return Set<String>()
        }
        return Set(keysArray)
    }
    
    /// Adds a new integration key to the stored set
    private func addIntegrationKey(_ key: String) {
        var keys = getIntegrationKeys()
        keys.insert(key)
        kvStorage.setValue(Array(keys), forKey: KvStorageKeys.integrationKeys.rawValue)
    }
    
    /// Removes an integration key from the stored set
    private func removeIntegrationKey(_ key: String) {
        var keys = getIntegrationKeys()
        keys.remove(key)
        kvStorage.setValue(Array(keys), forKey: KvStorageKeys.integrationKeys.rawValue)
    }
}
