//
//  IntegrationRepository.swift
//  meApp
//
//  Created by Lakshmi Priya on 03/06/25.
//

import Foundation

final class IntegrationRepository: IntegrationRepositoryProtocol {
    // MARK: - Properties
    
    private let userDefaults: UserDefaults
    private let logger = AppLogger.shared
    
    private enum Keys {
        static let integrationInfo = "integration_info_"
        static let integrationKeys = "integration_keys"
    }
    
    // MARK: - Initialization
    
    init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
    }
    
    // MARK: - IntegrationRepositoryProtocol
    
    /// Gets the stored integration data for the current device/account.
    /// - Parameter accountId: The account/user ID.
    /// - Returns: The stored IntegratedDeviceInfo, if any.
    func getIntegrationData(accountId: String) throws -> IntegrationInfo? {
        let key = Keys.integrationInfo + accountId
        guard let data = userDefaults.data(forKey: key) else {
            return nil
        }
        
        do {
            let decoder = JSONDecoder()
            return try decoder.decode(IntegrationInfo.self, from: data)
        } catch {
            logger.log(level: .error, tag: "IntegrationRepository", message: "Failed to decode integration info: \(error.localizedDescription)")
            throw NSError(domain: "IntegrationRepository", code: 1, userInfo: [NSLocalizedDescriptionKey: "Failed to decode integration data: \(error.localizedDescription)"])
        }
    }
    
    /// Sets the stored integration data for the current device/account.
    /// - Parameters:
    ///   - accountId: The account/user ID.
    ///   - info: The device info to store.
    func setIntegrationData(accountId: String, info: IntegrationInfo?) throws {
        let key = Keys.integrationInfo + accountId
        
        if let info = info {
            do {
                let encoder = JSONEncoder()
                let data = try encoder.encode(info)
                userDefaults.set(data, forKey: key)
                addIntegrationKey(key)
            } catch {
                logger.log(level: .error, tag: "IntegrationRepository", message: "Failed to encode integration info: \(error.localizedDescription)")
                throw NSError(domain: "IntegrationRepository", code: 2, userInfo: [NSLocalizedDescriptionKey: "Failed to encode integration data: \(error.localizedDescription)"])
            }
        } else {
            userDefaults.removeObject(forKey: key)
            removeIntegrationKey(key)
        }
    }
    
    /// Checks if the integration is already used by another device/account.
    /// - Parameters:
    ///   - accountId: The account/user ID.
    ///   - type: The integration type to check.
    /// - Returns: True if available, false if conflict.
    func checkIfIntegrationIsAlreadyUsed(accountId: String, type: IntegrationType) throws -> Bool {
        let integrationKeys = getIntegrationKeys()
        
        for key in integrationKeys {
            guard let data = userDefaults.data(forKey: key) else {
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
    func clearIntegrationStatus(accountId: String) throws {
        let key = Keys.integrationInfo + accountId
        userDefaults.removeObject(forKey: key)
        removeIntegrationKey(key)
    }
    
    // MARK: - Private Helper Methods
    
    /// Gets the set of all integration keys stored in UserDefaults
    private func getIntegrationKeys() -> Set<String> {
        return userDefaults.stringArray(forKey: Keys.integrationKeys)?.reduce(into: Set<String>()) { $0.insert($1) } ?? Set<String>()
    }
    
    /// Adds a new integration key to the stored set
    private func addIntegrationKey(_ key: String) {
        var keys = getIntegrationKeys()
        keys.insert(key)
        userDefaults.set(Array(keys), forKey: Keys.integrationKeys)
    }
    
    /// Removes an integration key from the stored set
    private func removeIntegrationKey(_ key: String) {
        var keys = getIntegrationKeys()
        keys.remove(key)
        userDefaults.set(Array(keys), forKey: Keys.integrationKeys)
    }
}
