# Account Migration Service

## Overview

The `AccountMigrationService` handles the migration of account data from the Ionic Weight Gurus 4 app to the native SwiftUI Weight Gurus app. This service is part of the comprehensive migration system implemented in August 2025 to transition users from the legacy Ionic app to the new native iOS app.

Since both apps use the same bundle identifier, the native app can access the UserDefaults/Preferences data created by the Ionic app's Capacitor Preferences API.

## How It Works

### 1. Data Location
The Ionic app (using Capacitor Preferences) stores account data in iOS UserDefaults with these keys:
- `activeAccountKey`: Contains the active account JSON string
- `offlineAccount_{accountId}`: Contains offline account data per user
- `{accountId}-hasSeenSetNewGoal`: Contains goal alert flag per user
- `{accountId}-colorMode`: Contains appearance/theme preference per user
- `{accountId}-healthKitIntegrated`: Contains HealthKit integration status per user
- `healthKitIntegratedAssignedTo`: Contains the account ID that HealthKit is assigned to (global)
- `healthKitDeintegrated-{accountId}`: Contains HealthKit deintegration flag per user
- `notificationOnlyAlertShown_{accountId}`: Contains notification alert viewed flag per user
- `feedInfo_{accountId}`: Contains feed notification settings per user
- `feedLastTriggeredAt_{accountId}`: Contains feed last triggered timestamp per user

### 2. Migration Process
When the native app launches:
1. **Check for Ionic Data**: The service checks if `activeAccountKey` exists in UserDefaults
2. **Parse JSON Data**: If found, it parses the JSON string to extract account information
3. **Convert to SwiftData**: Transforms the Ionic account structure to the native Account model
4. **Save to SwiftData**: Inserts the converted account into the SwiftData persistence layer
5. **Migrate Goal Alert Data**: Transfers goal alert flags from Ionic format to native format
6. **Migrate Appearance Data**: Transfers appearance/theme preferences from Ionic format to native format
7. **Migrate HealthKit Integration Data**: Transfers HealthKit integration settings from Ionic format to native format
8. **Migrate Notification Alert Data**: Transfers notification alert viewed flag from Ionic format to native format
9. **Migrate Feed Data**: Transfers feed notification settings and last triggered timestamps from Ionic format to native format
10. **Cleanup**: After successful migration, removes the Ionic app data from UserDefaults

### 3. Data Mapping

#### Ionic Account Structure (JSON)
```json
{
  "accessToken": "string",
  "refreshToken": "string", 
  "expiresAt": "string",
  "id": "string",
  "email": "string",
  "firstName": "string",
  "lastName": "string",
  "gender": "male|female",
  "zipcode": "string",
  "weightUnit": "kg|lb",
  "isWeightlessOn": boolean,
  "height": number,
  "activityLevel": "normal|athlete",
  "dob": "string",
  "weightlessTimestamp": "string",
  "weightlessWeight": number,
  "isStreakOn": boolean,
  "dashboardType": "string",
  "dashboardMetrics": ["string"],
  "goalType": "maintain|lose|gain",
  "goalWeight": number,
  "initialWeight": number,
  "shouldSendEntryNotifications": boolean,
  "shouldSendWeightInEntryNotifications": boolean,
  "isFitbitOn": boolean,
  "isFitbitValid": boolean,
  "isMFPOn": boolean,
  "isMFPValid": boolean,
  "isHealthKitOn": boolean,
  "isHealthConnectOn": boolean
}
```

#### SwiftData Account Model
The Ionic data is converted to:
- **Account**: Main account entity with personal info and tokens
- **WeightCompSettings**: Height, activity level, weight unit
- **GoalSettings**: Goal type, weights, progress
- **StreaksSettings**: Streak tracking info
- **WeightlessSettings**: Weightless mode settings
- **NotificationSettings**: Notification preferences
- **DashboardSettings**: Dashboard type and metrics
- **IntegrationSettings**: Third-party integration flags

### 4. Comprehensive Migration Support
The AccountMigrationService also supports comprehensive migration that includes account, scale, and goal alert data:

```swift
/// Migrates both account and scale data from Ionic app to SwiftUI app
func migrateAccountAndScaleData() async throws -> (account: Account?, scalesCount: Int)
```

This method:
1. First migrates account data using `migrateAccountData()`
2. Then migrates scale data for **ALL accounts** using `migrateAllScaleData()` (not just the current account)
3. Migrates goal alert storage keys from Ionic to native format for all accounts
4. Migrates appearance/theme preferences from Ionic to native format for all accounts
5. Migrates HealthKit integration settings from Ionic to native format
6. Migrates notification alert viewed flag from Ionic to native format for all accounts
7. Migrates feed data (settings and timestamps) from Ionic to native format for all accounts
8. Performs cleanup for account, scale, goal alert, appearance, HealthKit integration, notification alert, and feed data for all accounts
9. Returns both the migrated account and the total number of scales migrated across all accounts

### 5. Scale Migration for All Accounts
The service now includes methods to migrate scales for all accounts that have scale data stored in the Ionic app:

```swift
/// Migrates scale data for all accounts found in UserDefaults
func migrateAllScaleData() async -> [(accountId: String, scalesCount: Int)]

/// Removes scale data for all accounts after migration
func cleanupAllScaleData()

/// Removes scale data for specific account ID after migration
func cleanupScaleData(for accountId: String)
```

The `migrateAllScaleData()` method:
- Scans UserDefaults for all account IDs that have scale data stored with keys like `scale_{accountId}`
- Migrates each account's scale data individually using the existing `ScaleMigrationService`
- Returns an array of results showing how many scales were migrated per account
- Ensures no account's scale data is lost during migration
- Logs the migration process for debugging

This approach ensures that if a user previously logged into multiple accounts in the Ionic app and had paired scales for each, all of those scales are preserved when migrating to the native app.

### 5. Migration Timing
- Migration runs automatically on app startup before other account operations
- Only runs once - subsequent launches skip migration if no Ionic data exists
- Integrated into `AccountService.init()` for seamless user experience
- Can migrate account independently or as part of comprehensive migration

### 6. Error Handling
- Migration errors are logged but don't crash the app
- If migration fails, the app continues with normal initialization
- Users can still sign up/log in normally if migration fails
- Scale migration failures don't affect account migration success

### 7. Data Cleanup
After successful migration:
- Removes `CapacitorStorage.activeAccountKey` from UserDefaults
- Removes `CapacitorStorage.offlineAccount_{accountId}` for the migrated account
- **Removes `CapacitorStorage.{accountId}-hasSeenSetNewGoal` goal alert flags for ALL accounts found in UserDefaults**
- **Removes `CapacitorStorage.{accountId}-colorMode` appearance preferences for ALL accounts found in UserDefaults**
- Removes `CapacitorStorage.{accountId}-healthKitIntegrated` HealthKit integration status
- Removes `CapacitorStorage.healthKitIntegratedAssignedTo` if it matches the migrated account
- Removes `CapacitorStorage.healthKitDeintegrated-{accountId}` HealthKit deintegration flags
- **Removes `CapacitorStorage.notificationAlertViewed_{accountId}` notification alert keys for ALL accounts found in UserDefaults**
- **Removes `CapacitorStorage.notificationOnlyAlertShown` global notification alert key**
- **Removes `CapacitorStorage.feedInfo_{accountId}` feed settings keys for ALL accounts found in UserDefaults**
- **Removes `CapacitorStorage.feedLastTriggeredAt_{accountId}` feed timestamp keys for ALL accounts found in UserDefaults**
- **Removes `scale_{accountId}` scale data keys for ALL accounts found in UserDefaults**
- Ensures no leftover Ionic app data remains

**Notes**: 
- The goal alert cleanup now scans for and removes goal alert keys for all accounts that were previously logged into the Ionic app, not just the currently active account.
- The appearance cleanup now scans for and removes appearance/color mode keys for all accounts that were previously logged into the Ionic app, not just the currently active account.
- The notification alert cleanup removes both account-scoped notification alert keys for all accounts and the global notification alert key from the Ionic app.
- The feed data cleanup removes both feed settings and feed timestamp keys for all accounts that were previously logged into the Ionic app.
- The scale data cleanup removes scale data keys for all accounts that were previously logged into the Ionic app, ensuring all paired scales are migrated to the native app.

### 8. HealthKit Integration Migration

The service handles migration of HealthKit integration settings from the Ionic app to the native app using the `IntegrationRepository` system:

#### Ionic HealthKit Storage Format
The Ionic app stores HealthKit integration data in three keys:
- `{accountId}-healthKitIntegrated`: String value ("true"/"false") indicating if HealthKit is integrated
- `healthKitIntegratedAssignedTo`: String value containing the account ID that HealthKit is assigned to
- `healthKitDeintegrated-{accountId}`: String value ("true"/"false") indicating if HealthKit was deintegrated

#### Native HealthKit Storage Format
The native app uses `IntegrationRepository` to store `IntegrationInfo` objects:
```swift
struct IntegrationInfo: Codable, Equatable {
    let type: IntegrationType        // .healthKit
    let isIntegrated: Bool          // Migrated from {accountId}-healthKitIntegrated
    var assignedTo: String?         // Migrated from healthKitIntegratedAssignedTo
    var deIntegrated: String?       // Migrated from healthKitDeintegrated-{accountId}
}
```

#### Migration Process
1. **Check for Ionic HealthKit Data**: Looks for any of the three Ionic HealthKit keys
2. **Parse Integration Status**: Converts string values to appropriate types
3. **Create IntegrationInfo**: Builds the native integration object
4. **Store in Repository**: Uses `IntegrationRepository.setIntegrationData()` to persist
5. **Cleanup Ionic Data**: Removes all Ionic HealthKit keys after successful migration

#### Migration Method
```swift
func migrateHealthKitIntegrationData(for accountId: String)
```

This method:
- Reads Ionic HealthKit integration flags from UserDefaults
- Converts string values to native format
- Creates and stores `IntegrationInfo` object if any HealthKit data exists
- Logs the migration process for debugging

### 9. Notification Alert Migration

The service handles migration of **two different types** of notification alert flags from the Ionic app to the native app:

#### 9.1. Account-Scoped Notification Alert Migration

##### Ionic Storage Format
The Ionic app stores account-scoped notification alert data as:
- `CapacitorStorage.notificationAlertViewed_{accountId}`: String value ("true"/"false") indicating if the notification alert has been viewed for the specific account

##### Native Storage Format  
The native app stores the same data as:
- `notificationOnlyAlertShown_{accountId}`: Boolean value indicating if the notification alert has been shown for the specific account

##### Migration Methods
```swift
func migrateAllNotificationAlertData()
func migrateNotificationAlertData(for accountId: String)
func cleanupAllNotificationAlertData()
func cleanupNotificationAlertData(for accountId: String)
```

#### 9.2. Global Notification Alert Migration

##### Ionic Storage Format
The Ionic app stores global notification alert data as:
- `CapacitorStorage.notificationOnlyAlertShown`: String value ("true"/"false") indicating if the notification-only permission alert has been shown (global, not per account)

##### Native Storage Format  
The native app stores the same data as:
- `notificationOnlyPermAlertShown_{accountId}`: Boolean value indicating if the notification-only permission alert has been shown for the specific account

##### Migration Methods
```swift
func migrateGlobalNotificationAlertData(for accountId: String)
func cleanupGlobalNotificationAlertData()
```

#### Combined Migration Process
1. **Migrate Account-Scoped Keys**: Scans UserDefaults for all account IDs that have account-scoped notification alert data and migrates each one
2. **Migrate Global Key**: Looks for the global Ionic notification alert flag and migrates it to the active account
3. **Cleanup All Data**: Removes both account-scoped and global Ionic notification alert keys after successful migration

**Note**: The global notification alert flag is migrated to the currently active account being migrated, ensuring users don't see duplicate permission alerts after migration.

### 10. Goal Alert Migration

The service handles migration of the goal alert (hasSeenSetNewGoal) flags from the Ionic app to the native app **for all accounts** that have data stored.

#### Ionic Goal Alert Storage Format
The Ionic app stores goal alert data as:
- `CapacitorStorage.{accountId}-hasSeenSetNewGoal`: String value ("true"/"false") indicating if the goal alert has been shown for the specific account

#### Native Goal Alert Storage Format  
The native app stores the same data as:
- `{accountId}_goalMetFlag`: Boolean value indicating if the goal alert has been shown for the specific account

#### Migration Process
1. **Scan UserDefaults**: Finds all account IDs that have goal alert keys stored
2. **Migrate All Accounts**: For each found account ID:
   - Reads the Ionic goal alert flag from UserDefaults
   - Parses the string value to extract the boolean
   - Stores the boolean value using the native account-scoped key format
3. **Cleanup All Data**: Removes all Ionic goal alert keys after successful migration

#### Migration Methods
```swift
func migrateAllGoalAlertData()
func migrateGoalAlertData(for accountId: String)
func cleanupAllGoalAlertData()
```

The main method `migrateAllGoalAlertData()`:
- Scans UserDefaults for all account IDs that have goal alert data
- Migrates each account's goal alert flag individually
- Ensures no account's goal alert state is lost during migration
- Logs the migration process for debugging

This approach ensures that if a user previously logged into multiple accounts in the Ionic app and had goal alerts shown for each, all of those states are preserved when migrating to the native app.

### 11. Appearance Mode Migration

The service handles migration of the appearance/color mode settings from the Ionic app to the native app **for all accounts** that have data stored.

#### Ionic Appearance Storage Format
The Ionic app stores appearance data as:
- `CapacitorStorage.{accountId}-colorMode`: String value ("light", "dark", "system", etc.) indicating the appearance preference for the specific account

#### Native Appearance Storage Format  
The native app stores the same data as:
- `appearanceMode_{accountId}`: String value ("Light", "Dark", "System Settings") indicating the appearance preference for the specific account

#### Migration Process
1. **Scan UserDefaults**: Finds all account IDs that have appearance/color mode keys stored
2. **Migrate All Accounts**: For each found account ID:
   - Reads the Ionic appearance setting from UserDefaults
   - Maps Ionic values to native format:
     - `"light"` or `"system_light"` → `"Light"`
     - `"dark"` or `"system_dark"` → `"Dark"`
     - `"system"` → `"System Settings"`
     - Default fallback → `"System Settings"`
   - Stores the mapped value using the native account-scoped key format
3. **Cleanup All Data**: Removes all Ionic appearance keys after successful migration

#### Migration Methods
```swift
func migrateAllAppearanceData()
func migrateAppearanceData(for accountId: String)
func cleanupAllAppearanceData()
```

The main method `migrateAllAppearanceData()`:
- Scans UserDefaults for all account IDs that have appearance data
- Migrates each account's appearance setting individually
- Maps Ionic appearance values to native equivalents
- Ensures no account's appearance preference is lost during migration
- Logs the migration process for debugging

This approach ensures that if a user previously logged into multiple accounts in the Ionic app and had different appearance preferences for each, all of those preferences are preserved when migrating to the native app.

### 12. Feed Data Migration

The service handles migration of feed notification settings and last triggered timestamps from the Ionic app to the native app **for all accounts** that have data stored.

#### Ionic Feed Storage Format
The Ionic app stores feed data as:
- `CapacitorStorage.feedInfo_{accountId}`: Contains feed notification settings (JSON) for the specific account
- `CapacitorStorage.feedLastTriggeredAt_{accountId}`: Contains the last triggered timestamp (Double or String) for the specific account

#### Native Feed Storage Format  
The native app stores the same data as:
- `feedInfo_{accountId}`: Feed notification settings for the specific account
- `feedLastTriggeredAt_{accountId}`: Last triggered timestamp for the specific account

#### Migration Process
1. **Scan UserDefaults**: Finds all account IDs that have feed data stored
2. **Migrate All Accounts**: For each found account ID:
   - Reads the Ionic feed settings from UserDefaults
   - Copies the value directly to the native format (no conversion needed)
   - Reads the Ionic feed last triggered timestamp from UserDefaults
   - Copies the timestamp directly to the native format (supports both Double and String)
3. **Cleanup All Data**: Removes all Ionic feed keys after successful migration

#### Migration Methods
```swift
func migrateAllFeedData()
func migrateFeedData(for accountId: String)
func cleanupAllFeedData()
func cleanupFeedData(for accountId: String)
```

The main method `migrateAllFeedData()`:
- Scans UserDefaults for all account IDs that have feed data
- Migrates each account's feed settings and timestamps individually
- Ensures no account's feed data is lost during migration
- Logs the migration process for debugging

This approach ensures that if a user previously logged into multiple accounts in the Ionic app and had feed data for each, all of that data is preserved when migrating to the native app.

## Usage

The migration service is automatically used by `AccountService` and requires no manual intervention:

```swift
// Account-only migration (automatically called during AccountService initialization)
private func migrateFromIonicAppIfNeeded() async throws {
    guard migrationService.isMigrationNeeded() else { return }
    
    if let migratedAccount = try await migrationService.migrateAccountData() {
        // Account successfully migrated
        migrationService.cleanupAfterMigration()
        try await updatePublishedState()
    }
}

// Comprehensive migration (account + scales for all accounts)
let (account, totalScales) = try await migrationService.migrateAccountAndScaleData()
print("Migrated account: \(account?.email ?? "none"), total scales across all accounts: \(totalScales)")

// Migrate scales for all accounts independently
let scaleResults = await migrationService.migrateAllScaleData()
for result in scaleResults {
    print("Account \(result.accountId): migrated \(result.scalesCount) scales")
}
```

## Testing Migration

To test the migration:

1. **Prepare Test Data**: Add test account data to UserDefaults:
```swift
let testAccountData = """
{
  "accessToken": "test-token",
  "refreshToken": "test-refresh",
  "expiresAt": "2025-08-09T03:46:59.144Z",
  "id": "test-account-id",
  "email": "test@example.com",
  "firstName": "Test",
  "lastName": "User",
  "gender": "male",
  "weightUnit": "lb",
  "height": 70,
  "activityLevel": "normal",
  "goalType": "maintain"
}
"""
UserDefaults.standard.set(testAccountData, forKey: "activeAccountKey")
```

2. **Launch App**: The migration will run automatically on app startup
3. **Verify Results**: Check that the account appears in the SwiftUI app with correct data
4. **Confirm Cleanup**: Verify that the UserDefaults keys have been removed

## Related Documentation

For complete migration information, see:
- [`MIGRATION.md`](./MIGRATION.md) - Overall migration system overview
- [`ScaleMigrationService.md`](./ScaleMigrationService.md) - Scale device migration
- [`SQLiteMigrationService.md`](./SQLiteMigrationService.md) - Entry data migration

## Dependencies

The migration service depends on:
- `AccountRepositoryProtocol`: For saving migrated accounts
- `LoggerService`: For logging migration progress and errors
- `KvStorageService`: For accessing UserDefaults data
- `ScaleMigrationService`: For comprehensive migration including scales
- Account model and related settings entities
- Various enum types (Sex, WeightUnit, ActivityLevel, etc.)
- `MigrationKey` enum for consistent key management