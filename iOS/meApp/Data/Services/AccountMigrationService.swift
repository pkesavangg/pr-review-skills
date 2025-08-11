import Foundation
import SwiftData

/// Service to migrate account data from Ionic app (Capacitor Preferences) to SwiftUI app (SwiftData)
@MainActor
final class AccountMigrationService {
    @Injector private var logger: LoggerService
    private var accountRepo = AccountRepository()
    private let scaleMigrationService = ScaleMigrationService()
    private let theme = Theme.shared
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
        
        // Migrate goal alert storage keys
        migrateGoalAlertData(for: account.accountId)
        
        // Migrate appearance settings
        migrateAppearanceData(for: account.accountId)
        
        // Migrate HealthKit integration data
        migrateHealthKitIntegrationData(for: account.accountId)
        
        // Migrate notification alert data
        migrateNotificationAlertData(for: account.accountId)
        
        logger.log(level: .info, tag: tag, message: "Comprehensive migration completed for account: \(account.email) with \(scalesCount) scales")
        
        // Clean up Ionic data after successful migration
        cleanupAfterMigration()
        cleanupOfflineData(for: account.accountId)
        cleanupGoalAlertData(for: account.accountId)
        cleanupAppearanceData(for: account.accountId)
        cleanupHealthKitIntegrationData(for: account.accountId)
        cleanupNotificationAlertData(for: account.accountId)
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
    
    /// Migrates goal alert storage keys for a specific account
    /// - Parameter accountId: The account ID to migrate goal alert data for
    func migrateGoalAlertData(for accountId: String) {
        logger.log(level: .info, tag: tag, message: "Starting goal alert data migration for account: \(accountId)")
        
        let ionicGoalAlertKey = MigrationKey.goalAlertKey(for: accountId)
        let nativeGoalAlertKey = KvStorageKeys.goalMetFlagKey(for: accountId)
        
        // Check if Ionic goal alert flag exists
        if let ionicGoalAlertValue = kvStorage.getValue(forKey: ionicGoalAlertKey) as? String {
            logger.log(level: .info, tag: tag, message: "Found Ionic goal alert flag for account: \(accountId), value: \(ionicGoalAlertValue)")
            
            // Convert string value to boolean for native app
            // Ionic stores as "true"/"false" strings, native uses Bool
            let boolValue = ionicGoalAlertValue.lowercased() == "true"
            
            // Set the value in the native format
            kvStorage.setValue(boolValue, forKey: nativeGoalAlertKey)
            
            logger.log(level: .info, tag: tag, message: "Migrated goal alert flag for account: \(accountId) from '\(ionicGoalAlertValue)' to \(boolValue)")
        } else {
            logger.log(level: .info, tag: tag, message: "No goal alert flag found for account: \(accountId)")
        }
    }
    
    /// Removes goal alert data for specific account ID after migration
    func cleanupGoalAlertData(for accountId: String) {
        let ionicGoalAlertKey = MigrationKey.goalAlertKey(for: accountId)
        kvStorage.clearValue(forKey: ionicGoalAlertKey)
        logger.log(level: .info, tag: tag, message: "Cleaned up goal alert data for account: \(accountId)")
    }
    
    /// Migrates appearance settings for a specific account
    /// - Parameter accountId: The account ID to migrate appearance data for
    func migrateAppearanceData(for accountId: String) {
        logger.log(level: .info, tag: tag, message: "Starting appearance data migration for account: \(accountId)")
        
        let ionicAppearanceKey = MigrationKey.appearanceKey(for: accountId)
        let nativeAppearanceKey = KvStorageKeys.appearanceModeKey(for: accountId)
        
        // Check if Ionic appearance setting exists
        if let ionicAppearanceValue = kvStorage.getValue(forKey: ionicAppearanceKey) as? String {
            logger.log(level: .info, tag: tag, message: "Found Ionic appearance setting for account: \(accountId), value: \(ionicAppearanceValue)")
            
            // Map Ionic AppearanceType values to native AppearanceMode values
            let nativeAppearanceValue: String = {
                switch ionicAppearanceValue.lowercased() {
                case "light", "system_light":
                    return "Light"
                case "dark", "system_dark":
                    return "Dark"
                case "system":
                    return "System Settings"
                default:
                    return "System Settings" // Default fallback
                }
            }()
            
            // Set the value in the native format
            kvStorage.setValue(nativeAppearanceValue, forKey: nativeAppearanceKey)
            theme.loadAppearanceModeForAccount()
            logger.log(level: .info, tag: tag, message: "Migrated appearance setting for account: \(accountId) from '\(ionicAppearanceValue)' to '\(nativeAppearanceValue)'")
        } else {
            logger.log(level: .info, tag: tag, message: "No appearance setting found for account: \(accountId)")
        }
    }
    
    /// Removes appearance data for specific account ID after migration
    func cleanupAppearanceData(for accountId: String) {
        let ionicAppearanceKey = MigrationKey.appearanceKey(for: accountId)
        kvStorage.clearValue(forKey: ionicAppearanceKey)
        logger.log(level: .info, tag: tag, message: "Cleaned up appearance data for account: \(accountId)")
    }
    
    /// Migrates HealthKit integration settings for a specific account
    /// - Parameter accountId: The account ID to migrate HealthKit integration data for
    func migrateHealthKitIntegrationData(for accountId: String) {
        logger.log(level: .info, tag: tag, message: "Starting HealthKit integration data migration for account: \(accountId)")
        
        // Get the integration repository to store the migrated data
        let integrationRepository = IntegrationRepository()
        
        // Check for HealthKit integration status from Ionic app
        let ionicHealthKitKey = MigrationKey.healthKitIntegratedKey(for: accountId)
        let ionicAssignedToKey = MigrationKey.healthKitAssignedTo.rawValue
        let ionicDeintegratedKey = MigrationKey.healthKitDeintegratedKey(for: accountId)
        
        // Get HealthKit integration status
        var isIntegrated = false
        if let ionicIntegratedValue = kvStorage.getValue(forKey: ionicHealthKitKey) as? String {
            isIntegrated = ionicIntegratedValue.lowercased() == "true"
            logger.log(level: .info, tag: tag, message: "Found Ionic HealthKit integration status for account: \(accountId), value: \(ionicIntegratedValue)")
        }
        
        // Get assigned account (for conflict detection)
        var assignedTo: String? = nil
        if let ionicAssignedValue = kvStorage.getValue(forKey: ionicAssignedToKey) as? String {
            assignedTo = ionicAssignedValue
            logger.log(level: .info, tag: tag, message: "Found Ionic HealthKit assigned to: \(ionicAssignedValue)")
        }
        
        // Check for deintegration flag
        var deIntegrated: String? = nil
        if let ionicDeintegratedValue = kvStorage.getValue(forKey: ionicDeintegratedKey) as? String {
            deIntegrated = ionicDeintegratedValue.lowercased() == "true" ? accountId : nil
            logger.log(level: .info, tag: tag, message: "Found Ionic HealthKit deintegration flag for account: \(accountId), value: \(ionicDeintegratedValue)")
        }
        
        // Only create integration info if there was any HealthKit data in the Ionic app
        if isIntegrated || assignedTo != nil || deIntegrated != nil {
            let integrationInfo = IntegrationInfo(
                type: .healthKit,
                isIntegrated: isIntegrated,
                assignedTo: assignedTo,
                deIntegrated: deIntegrated
            )
            
            do {
                try integrationRepository.setIntegrationData(accountId: accountId, info: integrationInfo)
                logger.log(level: .info, tag: tag, message: "Successfully migrated HealthKit integration data for account: \(accountId) - integrated: \(isIntegrated), assignedTo: \(assignedTo ?? "nil"), deIntegrated: \(deIntegrated ?? "nil")")
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to migrate HealthKit integration data for account \(accountId): \(error.localizedDescription)")
            }
        } else {
            logger.log(level: .info, tag: tag, message: "No HealthKit integration data found for account: \(accountId)")
        }
    }
    
    /// Removes HealthKit integration data for specific account ID after migration
    func cleanupHealthKitIntegrationData(for accountId: String) {
        let ionicHealthKitKey = MigrationKey.healthKitIntegratedKey(for: accountId)
        let ionicAssignedToKey = MigrationKey.healthKitAssignedTo.rawValue
        let ionicDeintegratedKey = MigrationKey.healthKitDeintegratedKey(for: accountId)
        
        kvStorage.clearValue(forKey: ionicHealthKitKey)
        kvStorage.clearValue(forKey: ionicDeintegratedKey)
        
        // Only clear the assigned-to key if it matches this account
        if let assignedValue = kvStorage.getValue(forKey: ionicAssignedToKey) as? String,
           assignedValue == accountId {
            kvStorage.clearValue(forKey: ionicAssignedToKey)
        }
        
        logger.log(level: .info, tag: tag, message: "Cleaned up HealthKit integration data for account: \(accountId)")
    }
    
    /// Migrates notification alert viewed setting from Ionic app to native app
    /// - Parameter accountId: The account ID to migrate notification alert data for
    func migrateNotificationAlertData(for accountId: String) {
        logger.log(level: .info, tag: tag, message: "Starting notification alert data migration for account: \(accountId)")
        
        let ionicNotificationAlertKey = MigrationKey.notificationAlertViewedKey(for: accountId)
        let nativeNotificationAlertKey = KvStorageKeys.notificationOnlyAlertShownKey(for: accountId)
        
        // Check if Ionic notification alert flag exists
        if let ionicNotificationAlertValue = kvStorage.getValue(forKey: ionicNotificationAlertKey) as? String {
            logger.log(level: .info, tag: tag, message: "Found Ionic notification alert flag for account: \(accountId), value: \(ionicNotificationAlertValue)")
            
            // Parse JSON string to get boolean value
            // Ionic stores as JSON string: "true" or "false"
            let boolValue: Bool
            if ionicNotificationAlertValue.contains("true") {
                boolValue = true
            } else if ionicNotificationAlertValue.contains("false") {
                boolValue = false
            } else {
                // Fallback: try parsing as direct boolean string
                boolValue = ionicNotificationAlertValue.lowercased() == "true"
            }
            
            // Set the value in the native format (account-scoped key, but as Bool instead of JSON string)
            kvStorage.setValue(boolValue, forKey: nativeNotificationAlertKey)
            
            logger.log(level: .info, tag: tag, message: "Migrated notification alert flag for account: \(accountId) from '\(ionicNotificationAlertValue)' to \(boolValue)")
        } else {
            logger.log(level: .info, tag: tag, message: "No notification alert flag found for account: \(accountId)")
        }
    }
    
    /// Removes notification alert data for specific account ID after migration
    /// - Parameter accountId: The account ID whose notification alert data should be cleaned up
    func cleanupNotificationAlertData(for accountId: String) {
        let ionicNotificationAlertKey = MigrationKey.notificationAlertViewedKey(for: accountId)
        kvStorage.clearValue(forKey: ionicNotificationAlertKey)
        logger.log(level: .info, tag: tag, message: "Cleaned up notification alert data for account: \(accountId)")
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
