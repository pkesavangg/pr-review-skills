import Foundation
import SwiftData

/// Service to migrate account data from Ionic app (Capacitor Preferences) to SwiftUI app (SwiftData)
@MainActor
final class AccountMigrationService {
    @Injector private var logger: LoggerService
    private var accountRepo = AccountRepository()
    private let scaleMigrationService = ScaleMigrationService()
    
    private let tag = "AccountMigrationService"
    private let kvStorage = KvStorageService.shared
    
    // Using shared public MigrationKey enum
    
    // MARK: - Migration Interface
    
    /// Checks if migration is needed by looking for Ionic app account data
    func isMigrationNeeded() -> Bool {
        let hasActiveAccount = kvStorage.getValue(forKey: MigrationKey.activeAccount.rawValue) != nil
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
    
    /// Migrates both account and scale data from Ionic app to SwiftUI app
    /// - Returns: A tuple containing the migrated account and number of scales migrated
    func migrateAccountAndScaleData() async throws -> (account: Account?, scalesCount: Int) {
        logger.log(level: .info, tag: tag, message: "Starting comprehensive migration (account + scales) from Ionic app")
        
        // First migrate account data
        guard let account = try await migrateAccountData() else {
            logger.log(level: .error, tag: tag, message: "Account migration failed, skipping scale migration")
            return (nil, 0)
        }
        
        // Then migrate scale data for this account
        var scalesCount = 0
        do {
            scalesCount = try await migrateScaleData(for: account.accountId)
            logger.log(level: .info, tag: tag, message: "Scale migration completed. Migrated \(scalesCount) scales for account: \(account.accountId)")
        } catch {
            logger.log(level: .error, tag: tag, message: "Scale migration failed for account \(account.accountId): \(error.localizedDescription)")
            // Don't throw error here - account migration was successful
        }
        
        logger.log(level: .info, tag: tag, message: "Comprehensive migration completed for account: \(account.email) with \(scalesCount) scales")
        
        // Clean up Ionic data after successful migration
        cleanupAfterMigration()
        cleanupOfflineData(for: account.accountId)
        scaleMigrationService.cleanupAfterMigration(for: account.accountId)
        
        return (account, scalesCount)
    }
    
    /// Migrates scale data for a specific account
    /// - Parameter accountId: The account ID to migrate scales for
    /// - Returns: The number of scales migrated
    @discardableResult
    func migrateScaleData(for accountId: String) async throws -> Int {
        logger.log(level: .info, tag: tag, message: "Starting scale data migration for account: \(accountId)")
        
        guard scaleMigrationService.isMigrationNeeded(for: accountId) else {
            logger.log(level: .info, tag: tag, message: "No scale migration needed for account: \(accountId)")
            return 0
        }
        
        do {
            let migratedDevices = try await scaleMigrationService.migrateScaleData(for: accountId)
            scaleMigrationService.cleanupAfterMigration(for: accountId)
            
            logger.log(level: .info, tag: tag, message: "Scale migration completed for account: \(accountId). Migrated \(migratedDevices.count) scales")
            return migratedDevices.count
        } catch {
            logger.log(level: .error, tag: tag, message: "Scale migration failed for account \(accountId): \(error.localizedDescription)")
            throw error
        }
    }
    
    /// Removes Ionic app data after successful migration
    func cleanupAfterMigration() {
        logger.log(level: .info, tag: tag, message: "Cleaning up Ionic app data after migration")
        
        // Remove active account data
        kvStorage.clearValue(forKey: MigrationKey.activeAccount.rawValue)
        
        // Remove offline account data (we'll need the account ID for this)
        // This will be called after we know the account ID
        logger.log(level: .info, tag: tag, message: "Ionic app data cleanup completed")
    }
    
    /// Removes offline account data for specific account ID
    func cleanupOfflineData(for accountId: String) {
        let offlineKey = "\(MigrationKey.offlineAccountPrefix.rawValue)\(accountId)"
        kvStorage.clearValue(forKey: offlineKey)
        logger.log(level: .info, tag: tag, message: "Cleaned up offline data for account: \(accountId)")
    }
    
    // MARK: - Private Methods
    
    /// Gets stored Ionic account data from UserDefaults/Preferences
    private func getStoredIonicAccountData() -> IonicAccountData? {
        guard let accountString = kvStorage.getValue(forKey: MigrationKey.activeAccount.rawValue) as? String else {
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
