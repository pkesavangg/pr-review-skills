import Foundation
import ggInAppMessagingPackage
import SwiftData

/*
 SwiftLint exception:
 This service intentionally aggregates all one-time migration routines to keep the migration flow discoverable and auditable in a single place. Splitting across multiple types would add indirection and risk during a limited-run migration window. We therefore disable `type_body_length` for this file.
 */
@MainActor
final class AccountMigrationService { // swiftlint:disable:this type_body_length
    @Injector private var logger: LoggerService
    @Injector private var keychainService: KeychainService
    private var accountRepo = AccountRepository()
    private let scaleMigrationService = ScaleMigrationService()
    private let theme = Theme.shared
    private let tag = "AccountMigrationService"
    private let kvStorage = KvStorageService.shared
    
    // Using shared public MigrationKey enum
    
    // MARK: - Migration Interface
    
    /// Checks if migration is needed by looking for Ionic app account data
    func isMigrationNeeded() -> Bool {
        // First check if migration has already been completed
        let key = KvStorageKeys.ionicToNativeAppMigrationCompleted.rawValue
        let completed = (kvStorage.getValue(forKey: key) as? Bool) == true
        if completed {
            logger.log(level: .info, tag: tag, message: "Migration already completed, skipping")
        }
        return !completed
    }
    
    /// Migrates account data from Ionic app to SwiftUI app
    /// - Returns: The migrated account if successful
    func migrateAccountData() async throws -> Account? {
        logger.log(level: .info, tag: tag, message: "Starting account data migration from Ionic app")
        
        guard let accountData = getStoredIonicAccountData() else {
            logger.log(level: .info, tag: tag, message: "No Ionic account data found to migrate")
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
        
        // Migrate scale data for ALL accounts (independent of account migration)
        let scaleResults = await migrateAllScaleData()
        let totalScales = scaleResults.reduce(0) { $0 + $1.scalesCount }
        logger.log(level: .info, tag: tag, message: "Scale migration completed. Migrated \(totalScales) scales across \(scaleResults.count) accounts")
        
        // Migrate goal alert storage keys for all accounts (independent of account migration)
        migrateAllGoalAlertData()
        
        // Migrate goal card status for all accounts (independent of account migration)
        migrateAllGoalCardStatusData()
        
        // Migrate appearance settings for all accounts (independent of account migration)
        migrateAllAppearanceData()
        
        // Migrate notification alert data for all accounts (account-scoped keys, independent of account migration)
        migrateAllNotificationAlertData()
        
        // Migrate feed data for all accounts (independent of account migration)
        migrateAllFeedData()
        
        // Migrate integration data for all accounts (independent of account migration)
        migrateAllIntegrationData()
        
        // Now attempt account migration
        let account: Account?
        do {
            account = try await migrateAccountData()
            if let migratedAccount = account {
                logger.log(level: .info, tag: tag, message: "Account migration successful for: \(migratedAccount.email)")
                
                // Migrate HealthKit integration data (requires account ID)
                migrateHealthKitIntegrationData(for: migratedAccount.accountId)
                
                // Migrate global notification alert data to account-scoped for active account
                migrateGlobalNotificationAlertData(for: migratedAccount.accountId)
            } else {
                logger.log(level: .info, tag: tag, message: "Account migration returned nil, but other migrations completed successfully")
            }
        } catch {
            logger.log(
                level: .error,
                tag: tag,
                message: "Account migration failed: \(error.localizedDescription), but other migrations completed successfully"
            )
            account = nil
        }
        
        logger.log(
            level: .info,
            tag: tag,
            message: "Comprehensive migration completed. Account: \(account?.email ?? "none"), Total scales: \(totalScales)"
        )
        
        // Mark migration as completed to prevent future runs
        kvStorage.setValue(true, forKey: KvStorageKeys.ionicToNativeAppMigrationCompleted.rawValue)
        // Clean up Ionic data after migration (cleanup all data regardless of account migration success)
        cleanupAfterMigration()
        cleanupAllGoalAlertData() // Clean up goal alert data for all accounts
        cleanupAllGoalCardStatusData() // Clean up goal card status data for all accounts
        cleanupAllAppearanceData() // Clean up appearance data for all accounts
        cleanupAllNotificationAlertData() // Clean up notification alert data for all accounts
        cleanupGlobalNotificationAlertData() // Clean up global notification alert data
        cleanupAllFeedData() // Clean up feed data for all accounts
        cleanupAllIntegrationData() // Clean up integration data for all accounts
        cleanupAllScaleData() // Clean up scale data for all accounts
        
        // Clean up account-specific data only if account migration was successful
        if let migratedAccount = account {
            cleanupOfflineData(for: migratedAccount.accountId)
            cleanupHealthKitIntegrationData(for: migratedAccount.accountId)
        }
        
        return (account, totalScales)
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
            
            logger.log(
                level: .info,
                tag: tag,
                message: "Scale migration completed for account: \(accountId). Migrated \(migratedDevices.count) scales"
            )
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
    
    /// Resets the migration completion flag (for testing purposes)
    func resetMigrationFlag() {
        kvStorage.clearValue(forKey: KvStorageKeys.ionicToNativeAppMigrationCompleted.rawValue)
        logger.log(level: .info, tag: tag, message: "Migration completion flag reset")
    }
    
    /// Migrates goal alert storage keys for all accounts found in UserDefaults
    func migrateAllGoalAlertData() {
        logger.log(level: .info, tag: tag, message: "Starting goal alert data migration for all accounts")
        
        let allAccountIds = findAllAccountIdsInUserDefaults()
        
        for accountId in allAccountIds {
            migrateGoalAlertData(for: accountId)
        }
        
        logger.log(level: .info, tag: tag, message: "Completed goal alert data migration for \(allAccountIds.count) accounts")
    }
    
    /// Migrates goal alert storage keys for a specific account
    /// - Parameter accountId: The account ID to migrate goal alert data for
    func migrateGoalAlertData(for accountId: String) {
        logger.log(level: .info, tag: tag, message: "Starting goal alert data migration for account: \(accountId)")
        
        let ionicGoalAlertKey = MigrationKey.goalMetAlertKey(for: accountId)
        let nativeGoalAlertKey = KvStorageKeys.goalMetFlagKey(for: accountId)
        
        // Check if Ionic goal alert flag exists
        if let ionicGoalAlertValue = kvStorage.getValue(forKey: ionicGoalAlertKey) as? String {
            logger.log(level: .info, tag: tag, message: "Found Ionic goal alert flag for account: \(accountId)")
            
            // Convert string value to boolean for native app
            // Ionic stores as "true"/"false" strings, native uses Bool
            let boolValue = ionicGoalAlertValue.lowercased() == "true"
            
            // Set the value in the native format
            kvStorage.setValue(boolValue, forKey: nativeGoalAlertKey)
            
            logger.log(
                level: .info,
                tag: tag,
                message: "Migrated goal alert flag for account: \(accountId) from '\(ionicGoalAlertValue)' to \(boolValue)"
            )
        } else {
            logger.log(level: .info, tag: tag, message: "No goal alert flag found for account: \(accountId)")
        }
    }
    
    /// Removes goal alert data for all accounts after migration
    func cleanupAllGoalAlertData() {
        logger.log(level: .info, tag: tag, message: "Starting cleanup of goal alert data for all accounts")
        
        let allAccountIds = findAllAccountIdsInUserDefaults()
        
        for accountId in allAccountIds {
           cleanupGoalAlertData(for: accountId)
        }
        
        logger.log(level: .info, tag: tag, message: "Completed cleanup of goal alert data for \(allAccountIds.count) accounts")
    }
    
    /// Removes goal alert data for specific account ID after migration
    func cleanupGoalAlertData(for accountId: String) {
       let ionicGoalAlertKey = MigrationKey.goalMetAlertKey(for: accountId)
       kvStorage.clearValue(forKey: ionicGoalAlertKey)
       logger.log(level: .info, tag: tag, message: "Cleaned up goal alert data for account: \(accountId)")
    }
    
    /// Migrates goal card status for all accounts found in UserDefaults
    func migrateAllGoalCardStatusData() {
        logger.log(level: .info, tag: tag, message: "Starting goal card status migration for all accounts")
        
        let allAccountIds = findAllAccountIdsInUserDefaults()
        
        for accountId in allAccountIds {
            migrateGoalCardStatusData(for: accountId)
        }
        
        logger.log(level: .info, tag: tag, message: "Completed goal card status migration for \(allAccountIds.count) accounts")
    }
    
    /// Migrates goal card status for a specific account
    /// - Parameter accountId: The account ID to migrate goal card status data for
    func migrateGoalCardStatusData(for accountId: String) {
        logger.log(level: .info, tag: tag, message: "Starting goal card status migration for account: \(accountId)")
        
        let ionicGoalCardStatusKey = MigrationKey.setAGoalCardViewedKey(for: accountId)
        let nativeGoalCardStatusKey = KvStorageKeys.setAGoalModalFlagKey(for: accountId)
        
        // Check if Ionic goal card status flag exists
        if let ionicGoalCardStatusValue = kvStorage.getValue(forKey: ionicGoalCardStatusKey) as? String {
            logger.log(level: .info, tag: tag, message: "Found Ionic goal card status for account: \(accountId)")
            
            // Convert string value to boolean for native app
            // Ionic stores as "true"/"false" strings, native uses Bool
            let boolValue = ionicGoalCardStatusValue.lowercased() == "true"
            
            // Set the value in the native format
            kvStorage.setValue(boolValue, forKey: nativeGoalCardStatusKey)
            
            logger.log(
                level: .info,
                tag: tag,
                message: "Migrated goal card status for account: \(accountId) from '\(ionicGoalCardStatusValue)' to \(boolValue)"
            )
        } else {
            logger.log(level: .info, tag: tag, message: "No goal card status found for account: \(accountId)")
        }
    }
    
    /// Removes goal card status data for all accounts after migration
    func cleanupAllGoalCardStatusData() {
        logger.log(level: .info, tag: tag, message: "Starting cleanup of goal card status data for all accounts")
        
        let allAccountIds = findAllAccountIdsInUserDefaults()
        
        for accountId in allAccountIds {
           cleanupGoalCardStatusData(for: accountId)
        }
        
        logger.log(level: .info, tag: tag, message: "Completed cleanup of goal card status data for \(allAccountIds.count) accounts")
    }
    
    /// Removes goal card status data for specific account ID after migration
    func cleanupGoalCardStatusData(for accountId: String) {
        let ionicGoalCardStatusKey = MigrationKey.setAGoalCardViewedKey(for: accountId)
        kvStorage.clearValue(forKey: ionicGoalCardStatusKey)
        logger.log(level: .info, tag: tag, message: "Cleaned up goal card status data for account: \(accountId)")
    }
    
    /// Migrates appearance settings for all accounts found in UserDefaults
    func migrateAllAppearanceData() {
        logger.log(level: .info, tag: tag, message: "Starting appearance data migration for all accounts")
        
        let allAccountIds = findAllAccountIdsInUserDefaults()
        
        for accountId in allAccountIds {
            migrateAppearanceData(for: accountId)
        }
        
        logger.log(level: .info, tag: tag, message: "Completed appearance data migration for \(allAccountIds.count) accounts")
    }
    
    /// Migrates appearance settings for a specific account
    /// - Parameter accountId: The account ID to migrate appearance data for
    func migrateAppearanceData(for accountId: String) {
        logger.log(level: .info, tag: tag, message: "Starting appearance data migration for account: \(accountId)")
        
        let ionicAppearanceKey = MigrationKey.appearanceKey(for: accountId)
        let nativeAppearanceKey = KvStorageKeys.appearanceModeKey(for: accountId)
        
        // Check if Ionic appearance setting exists
        if let ionicAppearanceValue = kvStorage.getValue(forKey: ionicAppearanceKey) as? String {
            logger.log(level: .info, tag: tag, message: "Found Ionic appearance setting for account: \(accountId)")
            
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
            logger.log(
                level: .info,
                tag: tag,
                message: "Migrated appearance setting for account: \(accountId) from '\(ionicAppearanceValue)' to '\(nativeAppearanceValue)'"
            )
        } else {
            logger.log(level: .info, tag: tag, message: "No appearance setting found for account: \(accountId)")
        }
    }
    
    /// Removes appearance data for all accounts after migration
    func cleanupAllAppearanceData() {
        logger.log(level: .info, tag: tag, message: "Starting cleanup of appearance data for all accounts")
        
        let allAccountIds = findAllAccountIdsInUserDefaults()
        
        for accountId in allAccountIds {
           cleanupAppearanceData(for: accountId)
        }
        
        logger.log(level: .info, tag: tag, message: "Completed cleanup of appearance data for \(allAccountIds.count) accounts")
    }
    
    /// Removes appearance data for specific account ID after migration
    func cleanupAppearanceData(for accountId: String) {
        let ionicAppearanceKey = MigrationKey.appearanceKey(for: accountId)
        kvStorage.clearValue(forKey: ionicAppearanceKey)
        logger.log(level: .info, tag: tag, message: "Cleaned up appearance data for account: \(accountId)")
    }
    
    /// Migrates HealthKit integration settings for a specific account
    /// - Parameter accountId: The account ID to migrate HealthKit integration data for
    func migrateHealthKitIntegrationData(for accountId: String) { // swiftlint:disable:this function_body_length
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
            logger.log(
                level: .info,
                tag: tag,
                message: "Found Ionic HealthKit integration status for account: \(accountId), value: \(ionicIntegratedValue)"
            )
        }
        
        // Get assigned account (for conflict detection)
        var assignedTo: String?
        if let ionicAssignedValue = kvStorage.getValue(forKey: ionicAssignedToKey) as? String {
            assignedTo = ionicAssignedValue
            logger.log(level: .info, tag: tag, message: "Found Ionic HealthKit assigned-to value for account: \(accountId)")
        }
        
        // Check for deintegration flag
        var deIntegrated: String?
        if let ionicDeintegratedValue = kvStorage.getValue(forKey: ionicDeintegratedKey) as? String {
            deIntegrated = ionicDeintegratedValue.lowercased() == "true" ? accountId : nil
            logger.log(
                level: .info,
                tag: tag,
                message: "Found Ionic HealthKit deintegration flag for account: \(accountId), value: \(ionicDeintegratedValue)"
            )
        }
        
        // Only create integration info if there was any HealthKit data in the Ionic app
        if isIntegrated || assignedTo != nil || deIntegrated != nil {
            let integrationInfo = IntegrationInfo(
                type: .healthKit,
                isIntegrated: isIntegrated || assignedTo != nil,
                assignedTo: assignedTo,
                deIntegrated: deIntegrated
            )
            
            do {
                try integrationRepository.setIntegrationData(accountId: assignedTo ?? accountId, info: integrationInfo)
                let assignedToString = assignedTo ?? "nil"
                let deIntegratedString = deIntegrated ?? "nil"
                logger.log(
                    level: .info,
                    tag: tag,
                    message: "Successfully migrated HealthKit integration data for account: \(accountId) - " +
                        "integrated: \(isIntegrated), assignedTo: \(assignedToString), deIntegrated: \(deIntegratedString)"
                )
            } catch {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Failed to migrate HealthKit integration data for account \(accountId): \(error.localizedDescription)"
                )
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
    
    /// Migrates notification alert settings for all accounts found in UserDefaults
    func migrateAllNotificationAlertData() {
        logger.log(level: .info, tag: tag, message: "Starting notification alert data migration for all accounts")
        
        let allAccountIds = findAllAccountIdsInUserDefaults()
        
        for accountId in allAccountIds {
            migrateNotificationAlertData(for: accountId)
        }
        
        logger.log(level: .info, tag: tag, message: "Completed notification alert data migration for \(allAccountIds.count) accounts")
    }
    
    /// Migrates notification alert settings for a specific account
    /// - Parameter accountId: The account ID to migrate notification alert data for
    func migrateNotificationAlertData(for accountId: String) {
        logger.log(level: .info, tag: tag, message: "Starting notification alert data migration for account: \(accountId)")
        
        let ionicNotificationAlertKey = MigrationKey.notificationAlertViewedKey(for: accountId)
        let nativeNotificationAlertKey = KvStorageKeys.notificationOnlyAlertShownKey(for: accountId)
        
        // Check if Ionic notification alert flag exists
        if let ionicNotificationAlertValue = kvStorage.getValue(forKey: ionicNotificationAlertKey) as? String {
            logger.log(
                level: .info,
                tag: tag,
                message: "Found Ionic notification alert flag for account: \(accountId), value: \(ionicNotificationAlertValue)"
            )
            
            // Convert string value to boolean for native app
            // Ionic stores as "true"/"false" strings, native uses Bool
            let boolValue = ionicNotificationAlertValue.lowercased() == "true"
            
            // Set the value in the native format
            kvStorage.setValue(boolValue, forKey: nativeNotificationAlertKey)
            
            logger.log(
                level: .info,
                tag: tag,
                message: "Migrated notification alert flag for account: \(accountId) from '\(ionicNotificationAlertValue)' to \(boolValue)"
            )
        } else {
            logger.log(level: .info, tag: tag, message: "No notification alert flag found for account: \(accountId)")
        }
    }
    
    /// Removes notification alert data for all accounts after migration
    func cleanupAllNotificationAlertData() {
        logger.log(level: .info, tag: tag, message: "Starting cleanup of notification alert data for all accounts")
        
        let allAccountIds = findAllAccountIdsInUserDefaults()
        
        for accountId in allAccountIds {
          cleanupNotificationAlertData(for: accountId)
        }
        
        logger.log(level: .info, tag: tag, message: "Completed cleanup of notification alert data for \(allAccountIds.count) accounts")
    }
    
    /// Removes notification alert data for specific account ID after migration
    func cleanupNotificationAlertData(for accountId: String) {
       let ionicNotificationAlertKey = MigrationKey.notificationAlertViewedKey(for: accountId)
       kvStorage.clearValue(forKey: ionicNotificationAlertKey)
       logger.log(level: .info, tag: tag, message: "Cleaned up notification alert data for account: \(accountId)")
    }
    
    /// Migrates global notification alert setting from Ionic app to account-scoped native format
    /// - Parameter accountId: The account ID to migrate the global notification alert data for
    func migrateGlobalNotificationAlertData(for accountId: String) {
        logger.log(level: .info, tag: tag, message: "Starting global notification alert data migration for account: \(accountId)")
        
        let ionicGlobalNotificationAlertKey = MigrationKey.notificationOnlyAlertShown.rawValue
        let nativeNotificationAlertKey = KvStorageKeys.notificationOnlyPermAlertShownKey(for: accountId)
        
        // Check if Ionic global notification alert flag exists
        if let ionicNotificationAlertValue = kvStorage.getValue(forKey: ionicGlobalNotificationAlertKey) as? String {
            logger.log(level: .info, tag: tag, message: "Found Ionic global notification alert flag")
            
            // Convert string value to boolean for native app
            // Ionic stores as "true"/"false" strings, native uses Bool
            let boolValue = ionicNotificationAlertValue.lowercased() == "true"
            
            // Set the value in the native format for the active account
            kvStorage.setValue(boolValue, forKey: nativeNotificationAlertKey)
            
            logger.log(
                level: .info,
                tag: tag,
                message: "Migrated global notification alert flag to account: \(accountId) from '\(ionicNotificationAlertValue)' to \(boolValue)"
            )
        } else {
            logger.log(level: .info, tag: tag, message: "No global notification alert flag found in Ionic app")
        }
    }
    
    /// Removes global notification alert data after migration
    func cleanupGlobalNotificationAlertData() {
        logger.log(level: .info, tag: tag, message: "Starting cleanup of global notification alert data")
        
        let ionicGlobalNotificationAlertKey = MigrationKey.notificationOnlyAlertShown.rawValue
        kvStorage.clearValue(forKey: ionicGlobalNotificationAlertKey)
        
        logger.log(level: .info, tag: tag, message: "Cleaned up global notification alert data")
    }
    
    /// Migrates integration data for all accounts found in UserDefaults
    func migrateAllIntegrationData() {
        logger.log(level: .info, tag: tag, message: "Starting integration data migration for all accounts")
        
        let allAccountIds = findAllAccountIdsInUserDefaults()
        
        for accountId in allAccountIds {
            migrateHealthKitIntegrationData(for: accountId)
        }
        
        logger.log(level: .info, tag: tag, message: "Completed integration data migration for \(allAccountIds.count) accounts")
    }

    /// Migrates feed data for all accounts found in UserDefaults
    func migrateAllFeedData() {
        logger.log(level: .info, tag: tag, message: "Starting feed data migration for all accounts")
        
        let allAccountIds = findAllAccountIdsInUserDefaults()
        
        for accountId in allAccountIds {
            migrateFeedData(for: accountId)
        }
        
        logger.log(level: .info, tag: tag, message: "Completed feed data migration for \(allAccountIds.count) accounts")
    }
    
    /// Migrates scale data for all accounts found in UserDefaults
    func migrateAllScaleData() async -> [(accountId: String, scalesCount: Int)] {
        logger.log(level: .info, tag: tag, message: "Starting scale data migration for all accounts")
        
        let allAccountIds = findAllAccountIdsInUserDefaults()
        var migrationResults: [(accountId: String, scalesCount: Int)] = []
        
        for accountId in allAccountIds {
            do {
                let scalesCount = try await migrateScaleData(for: accountId)
                migrationResults.append((accountId: accountId, scalesCount: scalesCount))
                logger.log(level: .info, tag: tag, message: "Successfully migrated \(scalesCount) scales for account: \(accountId)")
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to migrate scales for account \(accountId): \(error.localizedDescription)")
                migrationResults.append((accountId: accountId, scalesCount: 0))
            }
        }
        
        let totalScales = migrationResults.reduce(0) { $0 + $1.scalesCount }
        logger.log(
            level: .info,
            tag: tag,
            message: "Completed scale data migration for \(allAccountIds.count) accounts, total scales: \(totalScales)"
        )
        
        return migrationResults
    }
    
    /// Migrates feed data for a specific account
    /// - Parameter accountId: The account ID to migrate feed data for
    func migrateFeedData(for accountId: String) { // swiftlint:disable:this function_body_length
        logger.log(level: .info, tag: tag, message: "Starting feed data migration for account: \(accountId)")
        
        // Migrate feed settings info
        let ionicFeedInfoKey = MigrationKey.feedSettingsInfoKey(for: accountId)
        let nativeFeedInfoKey = KvStorageKeys.feedInfoKey(for: accountId)
        
        if let ionicFeedInfoValue = kvStorage.getValue(forKey: ionicFeedInfoKey) {
            logger.log(level: .info, tag: tag, message: "Found Ionic feed info for account: \(accountId)")
            
            // Try to decode the Ionic feed settings data
            var feedSettings: FeedSetting?
            
            // Check if it's already stored as Data (encoded FeedSetting)
            if let data = ionicFeedInfoValue as? Data,
               let decodedSettings = try? JSONDecoder().decode(FeedSetting.self, from: data) {
                feedSettings = decodedSettings
                logger.log(level: .info, tag: tag, message: "Decoded Ionic feed settings from Data for account: \(accountId)")
            }
            // Check if it's stored as a dictionary
            else if let dict = ionicFeedInfoValue as? [String: Any] {
                let showPopupMessage = dict["showPopupMessage"] as? Bool ?? true // Default to true if not found
                let showNotificationBadge = dict["showNotificationBadge"] as? Bool ?? true // Default to true if not found
                feedSettings = FeedSetting(showPopupMessage: showPopupMessage, showNotificationBadge: showNotificationBadge)
                let message = """
                Converted Ionic feed settings from dictionary for account: \(accountId) - popup: \(showPopupMessage), badge: \(showNotificationBadge)
                """
                logger.log(
                    level: .info,
                    tag: tag,
                    message: message
                )
            }
            // Check if it's stored as a JSON string
            else if let jsonString = ionicFeedInfoValue as? String,
                    let jsonData = jsonString.data(using: .utf8),
                    let decodedSettings = try? JSONDecoder().decode(FeedSetting.self, from: jsonData) {
                feedSettings = decodedSettings
                logger.log(level: .info, tag: tag, message: "Decoded Ionic feed settings from JSON string for account: \(accountId)")
            }
            
            // Store the feed settings in native format if we successfully parsed it
            if let settings = feedSettings {
                kvStorage.setCodable(settings, forKey: nativeFeedInfoKey)
                // Verify the migration worked
                let verifyResult = kvStorage.getCodable(forKey: nativeFeedInfoKey, as: FeedSetting.self)
                logger.log(level: .info, tag: tag, message: "Migrated feed info for account: \(accountId) - verified: \(verifyResult != nil)")
            } else {
                logger.log(level: .error, tag: tag, message: "Failed to parse Ionic feed settings for account: \(accountId)")
            }
        } else {
            logger.log(level: .info, tag: tag, message: "No feed info found for account: \(accountId)")
        }
        
        // Migrate feed last triggered at timestamp
        let ionicFeedLastTriggeredKey = MigrationKey.feedLastTriggeredAtKey(for: accountId)
        let nativeFeedLastTriggeredKey = KvStorageKeys.feedLastTriggeredAtKey(for: accountId)
        
        if let ionicFeedLastTriggeredValue = kvStorage.getValue(forKey: ionicFeedLastTriggeredKey) {
            logger.log(
                level: .info,
                tag: tag,
                message: "Found Ionic feed last triggered timestamp for account: \(accountId), value: \(ionicFeedLastTriggeredValue)"
            )
            
            // Copy the value directly (it's already in the correct format - Double or String)
            kvStorage.setValue(ionicFeedLastTriggeredValue, forKey: nativeFeedLastTriggeredKey)
            
            logger.log(level: .info, tag: tag, message: "Migrated feed last triggered timestamp for account: \(accountId)")
        } else {
            logger.log(level: .info, tag: tag, message: "No feed last triggered timestamp found for account: \(accountId)")
        }
    }
    
    /// Removes integration data for all accounts after migration
    func cleanupAllIntegrationData() {
        logger.log(level: .info, tag: tag, message: "Starting cleanup of integration data for all accounts")
        
        let allAccountIds = findAllAccountIdsInUserDefaults()
        
        for accountId in allAccountIds {
           cleanupHealthKitIntegrationData(for: accountId)
        }
        
        logger.log(level: .info, tag: tag, message: "Completed cleanup of integration data for \(allAccountIds.count) accounts")
    }

    /// Removes feed data for all accounts after migration
    func cleanupAllFeedData() {
        logger.log(level: .info, tag: tag, message: "Starting cleanup of feed data for all accounts")
        
        let allAccountIds = findAllAccountIdsInUserDefaults()
        
        for accountId in allAccountIds {
           cleanupFeedData(for: accountId)
        }
        
        logger.log(level: .info, tag: tag, message: "Completed cleanup of feed data for \(allAccountIds.count) accounts")
    }
    
    /// Removes scale data for all accounts after migration
    func cleanupAllScaleData() {
        logger.log(level: .info, tag: tag, message: "Starting cleanup of scale data for all accounts")
        
        let allAccountIds = findAllAccountIdsInUserDefaults()
        
        for accountId in allAccountIds {
           cleanupScaleData(for: accountId)
        }
        
        logger.log(level: .info, tag: tag, message: "Completed cleanup of scale data for \(allAccountIds.count) accounts")
    }
    
    /// Removes scale data for specific account ID after migration
    func cleanupScaleData(for accountId: String) {
        let scaleKey = MigrationKey.scaleKey(for: accountId)
        kvStorage.clearValue(forKey: scaleKey)
        logger.log(level: .info, tag: tag, message: "Cleaned up scale data for account: \(accountId)")
    }
    
    /// Removes feed data for specific account ID after migration
    func cleanupFeedData(for accountId: String) {
        let ionicFeedInfoKey = MigrationKey.feedSettingsInfoKey(for: accountId)
        let ionicFeedLastTriggeredKey = MigrationKey.feedLastTriggeredAtKey(for: accountId)
        
        kvStorage.clearValue(forKey: ionicFeedInfoKey)
        kvStorage.clearValue(forKey: ionicFeedLastTriggeredKey)
        
        logger.log(level: .info, tag: tag, message: "Cleaned up feed data for account: \(accountId)")
    }
    
    // MARK: - Private Methods
    
    /// Finds all account IDs that have keys stored in UserDefaults
    /// This scans for account-scoped keys to identify all accounts that have migrateable data
    private func findAllAccountIdsInUserDefaults() -> Set<String> {
        var accountIds = Set<String>()
        
        // Get all UserDefaults keys for the app
        guard let allKeys = UserDefaults.standard.persistentDomain(forName: Bundle.main.bundleIdentifier ?? "")?.keys else {
            logger.log(level: .info, tag: tag, message: "No UserDefaults keys found")
            return accountIds
        }
        
        // Pattern to extract account IDs from various Capacitor storage keys
        let patterns = [
            MigrationKey.notificationAlertViewed.rawValue, // notificationAlertViewed (account-scoped)
            MigrationKey.goalAlertKey.rawValue, // hasSeenSetNewGoal
            MigrationKey.setAGoalCardStatus.rawValue, // goalCardStatus
            MigrationKey.appearanceKey.rawValue, // colorMode
            MigrationKey.healthKitIntegrated.rawValue, // healthKitIntegrated
            MigrationKey.healthKitDeintegrated.rawValue, // healthKitDeintegrated
            MigrationKey.feedSettingsInfo.rawValue, // feedInfo
            MigrationKey.feedLastTriggered.rawValue, // feedLastTriggeredAt
            MigrationKey.pairedScalesKey.rawValue // scale (paired scales data)
        ]
        
        for key in allKeys {
            for pattern in patterns {
                // Handle scale keys which don't have CapacitorStorage prefix
                if pattern == MigrationKey.pairedScalesKey.rawValue {
                    if key.contains(pattern) {
                        // Extract account ID from key
                        if let accountId = extractAccountIdFromKey(key, pattern: pattern) {
                            accountIds.insert(accountId)
                        }
                    }
                } else {
                    // Check for Capacitor storage keys with account IDs
                    if key.hasPrefix(MigrationKey.capacitorPrefix.rawValue) && key.contains(pattern) {
                        // Extract account ID from key
                        if let accountId = extractAccountIdFromKey(key, pattern: pattern) {
                            accountIds.insert(accountId)
                        }
                    }
                }
            }
        }
        
        logger.log(level: .info, tag: tag, message: "Found \(accountIds.count) unique account IDs in UserDefaults")
        return accountIds
    }
    
    /// Extracts account ID from a Capacitor storage key
    /// - Parameters:
    ///   - key: The full UserDefaults key
    ///   - pattern: The pattern to match against
    /// - Returns: The extracted account ID if found
    private func extractAccountIdFromKey(_ key: String, pattern: String) -> String? { // swiftlint:disable:this cyclomatic_complexity
        // For keys like "CapacitorStorage.notificationOnlyAlertShown_4EBA3nhaJwRCTJ9Veooo9U"
        // or "CapacitorStorage.4EBA3nhaJwRCTJ9Veooo9U-hasSeenSetNewGoal"
        
        if pattern == MigrationKey.pairedScalesKey.rawValue {
            // Format: PATTERN_ACCOUNT_ID (no CapacitorStorage prefix)
            let prefix = pattern + "_"
            if key.hasPrefix(prefix) {
                return String(key.dropFirst(prefix.count))
            }
        } else if pattern == MigrationKey.notificationAlertViewed.rawValue || 
           pattern == MigrationKey.feedSettingsInfo.rawValue || 
           pattern == MigrationKey.feedLastTriggered.rawValue ||
           pattern == MigrationKey.setAGoalCardStatus.rawValue {
            // Format: CapacitorStorage.PATTERN_ACCOUNT_ID or CapacitorStorage.ACCOUNT_ID_PATTERN
            if pattern == MigrationKey.setAGoalCardStatus.rawValue {
                // Special case for goalCardStatus: CapacitorStorage.ACCOUNT_ID_goalCardStatus
                let prefix = MigrationKey.capacitorPrefix.rawValue
                let suffix = "_" + pattern
                
                if key.hasPrefix(prefix) && key.hasSuffix(suffix) {
                    let startIndex = key.index(key.startIndex, offsetBy: prefix.count)
                    let endIndex = key.index(key.endIndex, offsetBy: -suffix.count)
                    if startIndex < endIndex {
                        return String(key[startIndex..<endIndex])
                    }
                }
            } else {
                // Format: CapacitorStorage.PATTERN_ACCOUNT_ID
                let prefix = MigrationKey.capacitorPrefix.rawValue + pattern + "_"
                if key.hasPrefix(prefix) {
                    return String(key.dropFirst(prefix.count))
                }
            }
        } else if pattern == MigrationKey.healthKitDeintegrated.rawValue {
            // Format: CapacitorStorage.healthKitDeintegrated-ACCOUNT_ID
            let prefix = MigrationKey.capacitorPrefix.rawValue + pattern + "-"
            if key.hasPrefix(prefix) {
                return String(key.dropFirst(prefix.count))
            }
        } else {
            // Format: CapacitorStorage.ACCOUNT_ID-PATTERN
            let prefix = MigrationKey.capacitorPrefix.rawValue
            let suffix = "-" + pattern
            
            if key.hasPrefix(prefix) && key.hasSuffix(suffix) {
                let startIndex = key.index(key.startIndex, offsetBy: prefix.count)
                let endIndex = key.index(key.endIndex, offsetBy: -suffix.count)
                if startIndex < endIndex {
                    return String(key[startIndex..<endIndex])
                }
            }
        }
        
        return nil
    }
    
    /// Gets stored Ionic account data from UserDefaults/Preferences
    private func getStoredIonicAccountData() -> IonicAccountData? {
        guard let accountString = kvStorage.getValue(forKey: MigrationKey.activeAccount.rawValue) as? String else {
            logger.log(level: .info, tag: tag, message: "No active account string found in UserDefaults")
            return nil
        }
        
        guard let accountData = accountString.data(using: .utf8) else {
            logger.log(level: .error, tag: tag, message: "Failed to convert account string to data")
            return nil
        }
        
        do {
            let decoder = JSONDecoder()
            let ionicAccount = try decoder.decode(IonicAccountData.self, from: accountData)
            return ionicAccount
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to decode Ionic account data: \(error.localizedDescription)")
            return nil
        }
    }
    
    /// Converts Ionic account data to SwiftUI Account model
    private func convertIonicDataToAccount(_ ionicData: IonicAccountData) throws -> Account {
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
            weightlessWeight: ionicData.weightlessWeight.map(Double.init),
            isStreakOn: ionicData.isStreakOn,
            streakTimestamp: nil, // Not in the provided data
            dashboardType: DashboardType(rawValue: ionicData.dashboardType.replacingOccurrences(of: "_", with: "")) ?? .dashboard4,
            dashboardMetrics: ionicData.dashboardMetrics.compactMap { BodyMetric(rawValue: $0) },
            progressMetrics: nil, // Not available in Ionic migration data
            goalType: GoalType(rawValue: ionicData.goalType ?? "") ?? .maintain,
            goalWeight: ionicData.goalWeight.map(Double.init),
            goalPercent: nil,
            initialWeight: ionicData.initialWeight.map(Double.init),
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
        
        // Store tokens in Keychain only (not in SwiftData), but only if non-empty
        if !ionicData.accessToken.isEmpty,
           !ionicData.refreshToken.isEmpty,
           !ionicData.expiresAt.isEmpty {
            keychainService.setTokens(
                Tokens(accessToken: ionicData.accessToken, refreshToken: ionicData.refreshToken, expiresAt: ionicData.expiresAt),
                for: account.accountId
            )
        }
        account.isLoggedIn = true
        account.isActiveAccount = true
        account.isExpired = false
        account.isSynced = false
        account.lastActiveTime = DateTimeTools.getCurrentDatetimeIsoString()
        
        return account
    }
// swiftlint:disable:next file_length
}
