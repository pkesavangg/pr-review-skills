# Account Migration Service

## Overview

The `AccountMigrationService` handles the migration of account data from the Ionic Weight Gurus 4 app to the native SwiftUI Weight Gurus app. This service is part of the comprehensive migration system implemented in August 2025 to transition users from the legacy Ionic app to the new native iOS app.

Since both apps use the same bundle identifier, the native app can access the UserDefaults/Preferences data created by the Ionic app's Capacitor Preferences API.

## How It Works

### 1. Data Location
The Ionic app (using Capacitor Preferences) stores account data in iOS UserDefaults with these keys:
- `activeAccountKey`: Contains the active account JSON string
- `offlineAccount_{accountId}`: Contains offline account data per user

### 2. Migration Process
When the native app launches:
1. **Check for Ionic Data**: The service checks if `activeAccountKey` exists in UserDefaults
2. **Parse JSON Data**: If found, it parses the JSON string to extract account information
3. **Convert to SwiftData**: Transforms the Ionic account structure to the native Account model
4. **Save to SwiftData**: Inserts the converted account into the SwiftData persistence layer
5. **Cleanup**: After successful migration, removes the Ionic app data from UserDefaults

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
The AccountMigrationService also supports comprehensive migration that includes both account and scale data:

```swift
/// Migrates both account and scale data from Ionic app to SwiftUI app
func migrateAccountAndScaleData() async throws -> (account: Account?, scalesCount: Int)
```

This method:
1. First migrates account data using `migrateAccountData()`
2. Then migrates scale data for the account using `ScaleMigrationService`
3. Performs cleanup for both account and scale data
4. Returns both the migrated account and the number of scales migrated

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
- Removes `activeAccountKey` from UserDefaults
- Removes `offlineAccount_{accountId}` for the migrated account
- Ensures no leftover Ionic app data remains

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

// Comprehensive migration (account + scales)
let (account, scalesCount) = try await migrationService.migrateAccountAndScaleData()
print("Migrated account: \(account?.email ?? "none"), scales: \(scalesCount)")
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