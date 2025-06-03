//
//  IntegrationRepository.swift
//  meApp
//
//  Created by Lakshmi Priya on 03/06/25.
//

import Foundation

@MainActor
final class IntegrationRepository: IntegrationRepositoryProtocol {
    private let userDefaults: UserDefaults
    private let logger = AppLogger.shared
    
    private enum Keys {
        static let integrationInfo = "integration_info_"
    }
    
    init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
    }
    
    // MARK: - IntegrationRepositoryProtocol
    
    /// Gets the stored integration data for the current device/account.
    /// - Parameter accountId: The account/user ID.
    /// - Returns: The stored IntegratedDeviceInfo, if any.
    func getIntegrationData(accountId: String) async throws -> IntegrationInfo? {
        let key = Keys.integrationInfo + accountId
        guard let data = userDefaults.data(forKey: key) else {
            return nil
        }
        
        do {
            let decoder = JSONDecoder()
            return try decoder.decode(IntegrationInfo.self, from: data)
        } catch {
            logger.log(level: .error, tag: "IntegrationRepository", message: "Failed to decode integration info: \(error.localizedDescription)")
            return nil
        }
    }
    
    /// Sets the stored integration data for the current device/account.
    /// - Parameters:
    ///   - accountId: The account/user ID.
    ///   - info: The device info to store.
    func setIntegrationData(accountId: String, info: IntegrationInfo?) async throws {
        let key = Keys.integrationInfo + accountId
        
        if let info = info {
            do {
                let encoder = JSONEncoder()
                let data = try encoder.encode(info)
                userDefaults.set(data, forKey: key)
            } catch {
                logger.log(level: .error, tag: "IntegrationRepository", message: "Failed to encode integration info: \(error.localizedDescription)")
                throw error
            }
        } else {
            userDefaults.removeObject(forKey: key)
        }
    }
    
    /// Checks if the integration is already used by another device/account.
    /// - Parameters:
    ///   - accountId: The account/user ID.
    ///   - type: The integration type to check.
    /// - Returns: True if available, false if conflict.
    func checkIfIntegrationIsAlreadyUsed(accountId: String, type: IntegrationType) async throws -> Bool {
        // Get all keys that start with our prefix
        let allKeys = userDefaults.dictionaryRepresentation().keys.filter { $0.hasPrefix(Keys.integrationInfo) }
        
        for key in allKeys {
            guard let data = userDefaults.data(forKey: key),
                  let info = try? JSONDecoder().decode(IntegrationInfo.self, from: data) else {
                continue
            }
            
            // Check if this integration is used by another account
            if info.type == type && info.isIntegrated && info.assignedTo != accountId {
                return true
            }
        }
        
        return false
    }
    
    /// Clears the integration status for the given account (e.g., on account deletion).
    /// - Parameter accountId: The account/user ID.
    func clearIntegrationStatus(accountId: String) async throws {
        let key = Keys.integrationInfo + accountId
        userDefaults.removeObject(forKey: key)
    }
}
