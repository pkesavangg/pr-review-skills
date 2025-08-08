import Foundation
import SwiftData

/// Service to migrate account data from Ionic app (Capacitor Preferences) to SwiftUI app (SwiftData)
@MainActor
final class AccountMigrationService {
    @Injector private var logger: LoggerService
    private var accountRepo = AccountRepository()
    
    private let tag = "AccountMigrationService"
    private let kvStorage = KvStorageService.shared
    
    // Ionic app keys (matching the Angular service)
    private let activeAccountKey = "CapacitorStorage.activeAccountKey"
    private let offlineAccountKeyPrefix = "CapacitorStorage.offlineAccount"
    
    // MARK: - Migration Interface
    
    /// Checks if migration is needed by looking for Ionic app account data
    func isMigrationNeeded() -> Bool {
        let hasActiveAccount = kvStorage.getValue(forKey: activeAccountKey) != nil
        logger.log(level: .info, tag: tag, message: "Migration check - Active account exists: \(hasActiveAccount)")
        return hasActiveAccount
    }
    
    /// Migrates account data from Ionic app to SwiftUI app
    /// - Returns: The migrated account if successful
    func migrateAccountData() async throws -> Account? {
        logger.log(level: .info, tag: tag, message: "Starting account data migration from Ionic app")
        
        guard let accountData = getStoredIonicAccountData() else {
            logger.log(level: .error, tag: tag, message: "No Ionic account data found to migrate")
            return nil
        }
        
        do {
            // Convert Ionic account data to Account model
            let account = try convertIonicDataToAccount(accountData)
            
            // Save to SwiftData
            try await accountRepo.saveAccount(account)
            
            logger.log(level: .info, tag: tag, message: "Account migration completed successfully for: \(account.email)")
            
            return account
            
        } catch {
            logger.log(level: .error, tag: tag, message: "Account migration failed: \(error.localizedDescription)")
            throw error
        }
    }
    
    /// Removes Ionic app data after successful migration
    func cleanupAfterMigration() {
        logger.log(level: .info, tag: tag, message: "Cleaning up Ionic app data after migration")
        
        // Remove active account data
        kvStorage.clearValue(forKey: activeAccountKey)
        
        // Remove offline account data (we'll need the account ID for this)
        // This will be called after we know the account ID
        logger.log(level: .info, tag: tag, message: "Ionic app data cleanup completed")
    }
    
    /// Removes offline account data for specific account ID
    func cleanupOfflineData(for accountId: String) {
        let offlineKey = "\(offlineAccountKeyPrefix)\(accountId)"
        kvStorage.clearValue(forKey: offlineKey)
        logger.log(level: .info, tag: tag, message: "Cleaned up offline data for account: \(accountId)")
    }
    
    // MARK: - Private Methods
    
    /// Gets stored Ionic account data from UserDefaults/Preferences
    private func getStoredIonicAccountData() -> IonicAccountData? {
        guard let accountString = kvStorage.getValue(forKey: activeAccountKey) as? String else {
            logger.log(level: .error, tag: tag, message: "No active account string found in UserDefaults")
            return nil
        }
        
        guard let accountData = accountString.data(using: .utf8) else {
            logger.log(level: .error, tag: tag, message: "Failed to convert account string to data")
            return nil
        }
        
        do {
            let decoder = JSONDecoder()
            let ionicAccount = try decoder.decode(IonicAccountData.self, from: accountData)
            logger.log(level: .info, tag: tag, message: "Successfully parsed Ionic account data for: \(ionicAccount.email)")
            return ionicAccount
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to decode Ionic account data: \(error.localizedDescription)")
            return nil
        }
    }
    
    /// Converts Ionic account data to SwiftUI Account model
    private func convertIonicDataToAccount(_ ionicData: IonicAccountData) throws -> Account {
        logger.log(level: .info, tag: tag, message: "Converting Ionic data to Account model")
        
        // Create AccountDTO first
        let accountDTO = AccountDTO(
            id: ionicData.id,
            email: ionicData.email,
            firstName: ionicData.firstName,
            lastName: ionicData.lastName,
            gender: Sex(rawValue: ionicData.gender) ?? .male,
            zipcode: ionicData.zipcode,
            weightUnit: WeightUnit(rawValue: ionicData.weightUnit) ?? .lb,
            isWeightlessOn: ionicData.isWeightlessOn,
            height: Double(ionicData.height),
            activityLevel: ActivityLevel(rawValue: ionicData.activityLevel) ?? .normal,
            dob: ionicData.dob,
            weightlessTimestamp: ionicData.weightlessTimestamp,
            weightlessWeight: ionicData.weightlessWeight != nil ? Double(ionicData.weightlessWeight!) : nil,
            isStreakOn: ionicData.isStreakOn,
            streakTimestamp: nil, // Not in the provided data
            dashboardType: DashboardType(rawValue: ionicData.dashboardType.replacingOccurrences(of: "_", with: "")) ?? .dashboard4,
            dashboardMetrics: ionicData.dashboardMetrics.compactMap { BodyMetric(rawValue: $0) },
            goalType: GoalType(rawValue: ionicData.goalType) ?? .maintain,
            goalWeight: ionicData.goalWeight != nil ? Double(ionicData.goalWeight!) : nil,
            goalPercent: nil,
            initialWeight: ionicData.initialWeight != nil ? Double(ionicData.initialWeight!) : nil,
            shouldSendEntryNotifications: ionicData.shouldSendEntryNotifications,
            shouldSendWeightInEntryNotifications: ionicData.shouldSendWeightInEntryNotifications,
            isFitbitOn: ionicData.isFitbitOn,
            isFitbitValid: ionicData.isFitbitValid,
            isMFPOn: ionicData.isMFPOn,
            isMFPValid: ionicData.isMFPValid,
            isHealthKitOn: ionicData.isHealthKitOn,
            isHealthConnectOn: ionicData.isHealthConnectOn
        )
        
        // Create Account from DTO
        let account = Account(from: accountDTO)
        
        // Set additional properties from Ionic data
        account.accessToken = ionicData.accessToken
        account.refreshToken = ionicData.refreshToken
        account.expiresAt = ionicData.expiresAt
        account.isLoggedIn = true
        account.isActiveAccount = true
        account.isExpired = false
        account.isSynced = true
        account.lastActiveTime = DateTimeTools.getCurrentDatetimeIsoString()
        
        logger.log(level: .info, tag: tag, message: "Successfully converted Ionic data to Account model")
        return account
    }
}

// MARK: - Ionic Account Data Model

/// Represents the account data structure from the Ionic app
private struct IonicAccountData: Codable {
    let accessToken: String
    let refreshToken: String
    let expiresAt: String
    let id: String
    let email: String
    let firstName: String
    let lastName: String?
    let gender: String
    let zipcode: String?
    let weightUnit: String
    let isWeightlessOn: Bool?
    let preferredInputMethod: String?
    let height: Int
    let activityLevel: String
    let dob: String
    let weightlessBodyFat: Double?
    let weightlessMuscle: Double?
    let weightlessTimestamp: String?
    let weightlessWeight: Int?
    let isStreakOn: Bool?
    let dashboardType: String
    let dashboardMetrics: [String]
    let goalType: String
    let goalWeight: Int?
    let initialWeight: Int?
    let shouldSendEntryNotifications: Bool
    let shouldSendWeightInEntryNotifications: Bool
    let isGoogleFitOn: Bool?
    let isGoogleFitValid: Bool?
    let isFitbitOn: Bool?
    let isFitbitValid: Bool?
    let isMFPOn: Bool?
    let isMFPValid: Bool?
    let isUAOn: Bool?
    let isUAValid: Bool?
    let isHealthConnectOn: Bool?
    let isHealthKitOn: Bool?
    let type: String?
}

// MARK: - Migration Errors

enum AccountMigrationError: LocalizedError {
    case noDataToMigrate
    case invalidDataFormat
    case conversionFailed
    case saveFailed
    
    var errorDescription: String? {
        switch self {
        case .noDataToMigrate:
            return "No account data found to migrate from Ionic app"
        case .invalidDataFormat:
            return "Invalid data format in Ionic app storage"
        case .conversionFailed:
            return "Failed to convert Ionic account data to SwiftUI format"
        case .saveFailed:
            return "Failed to save migrated account data"
        }
    }
}
