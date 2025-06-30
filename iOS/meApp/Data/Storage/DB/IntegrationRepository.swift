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
    
    private let userDefaults: UserDefaults
    private let logger = LoggerService.shared
        
    // MARK: - Initialization
    
    init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
    }
    
    // MARK: - IntegrationRepositoryProtocol
    
    /// Gets the stored integration data for the current device/account.
    /// - Parameter accountId: The account/user ID.
    /// - Returns: The stored IntegratedDeviceInfo, if any.
    func getIntegrationData(accountId: String) throws -> IntegrationInfo? {
        let key = makeIntegrationKey(for: accountId)
        guard let data = userDefaults.data(forKey: key) else {
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
                userDefaults.set(data, forKey: key)
                addIntegrationKey(key)
            } catch {
                logger.log(level: .error, tag: "IntegrationRepository", message: "Failed to encode integration info: \(error.localizedDescription)")
                throw error
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
    /// - Returns: True if the integration is already used (conflict), false if available.
    func isIntegrationAlreadyUsed(accountId: String, type: IntegrationType) throws -> Bool {
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
        let key = makeIntegrationKey(for: accountId)
        userDefaults.removeObject(forKey: key)
        removeIntegrationKey(key)
    }
    
    // MARK: - Private Helper Methods
    
    /// Creates a UserDefaults key for an integration with the given account ID
    /// - Parameter accountId: The account/user ID
    /// - Returns: The constructed key string
    private func makeIntegrationKey(for accountId: String) -> String {
        return Keys.integrationInfo + "_" + accountId
    }
    
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
