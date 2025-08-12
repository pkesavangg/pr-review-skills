# Scale Migration Service

## Overview

The `ScaleMigrationService` handles the migration of paired scale device data from the Ionic Weight Gurus 4 app to the native SwiftUI app. Since both apps use the same bundle identifier, the native app can access the UserDefaults/Preferences data created by the Ionic app's Capacitor Preferences API.

## How It Works

### 1. Data Location
The Ionic app (using Capacitor Preferences) stores scale device data in iOS UserDefaults with account-specific keys:
- `scale_{accountId}`: Contains an array of paired scales for the account as a JSON string

### 2. Migration Process
When the native app performs comprehensive migration:
1. **Check for Ionic Scale Data**: The service checks if `scale_{accountId}` exists in UserDefaults for the current account
2. **Parse JSON Data**: If found, it parses the JSON string to extract an array of scale device information
3. **Convert to SwiftData**: Transforms each Ionic scale structure to the native Device model with relationships
4. **Save to SwiftData**: Inserts the converted devices using the ScaleService
5. **Sync with Remote**: Triggers scale synchronization with the backend
6. **Cleanup**: After successful migration, removes the Ionic app scale data from UserDefaults

### 3. Data Mapping

#### Ionic Scale Structure (JSON Array)
```json
[
  {
    "id": "string",
    "peripheralIdentifier": "string",
    "nickname": "string",
    "sku": "string",
    "mac": "string",
    "password": number,
    "isDeleted": boolean,
    "name": "string",
    "type": "btWifiR4|bluetooth|lcbt",
    "broadcastId": number,
    "userNumber": number,
    "createdAt": "string",
    "isTemporary": boolean,
    "scaleToken": "string",
    "latestVersion": "string",
    "preference": {
      "displayName": "string",
      "displayMetrics": ["string"],
      "shouldFactoryReset": boolean,
      "shouldMeasureImpedance": boolean,
      "shouldMeasurePulse": boolean,
      "timeFormat": "string",
      "tzOffset": number,
      "wifiFotaScheduleTime": number
    }
  }
]
```

#### SwiftData Device Model
The Ionic scale data is converted to:
- **Device**: Main device entity with connection and sync info
- **BathScale**: Scale-specific properties (type, body composition support)
- **R4ScalePreference**: R4 scale preferences and settings (if applicable)
- **DeviceMetaData**: Firmware version information (if available)

### 4. Scale Type Mapping

#### Protocol Type Determination
- `"btWifiR4"` → `"R4"` protocol
- `"bluetooth"` → `"A3"` protocol  
- `"lcbt"` → `"A6"` protocol
- Other types → `nil` protocol

#### Body Composition Support
- **R4 Scales**: Always support body composition (`type == "btWifiR4"`)
- **Specific SKUs**: SKUs "0412", "0343", "0344" support body composition
- **Others**: Default to no body composition support

### 5. Migration Timing
- Scale migration runs as part of the comprehensive migration process
- Called after successful account migration
- Integrated into `AccountMigrationService.migrateAccountAndScaleData()`
- Only runs if account migration succeeds

### 6. Error Handling
- Individual scale migration failures don't stop the overall process
- Failed scales are logged with details but migration continues
- Service returns successfully migrated devices count
- Migration errors are logged but don't crash the app

### 7. Data Cleanup
After successful migration:
- Removes `scale_{accountId}` from UserDefaults for the migrated account
- Ensures no leftover Ionic app scale data remains
- Cleanup is called automatically after successful migration

## Usage

The scale migration service is automatically used by `AccountMigrationService` and requires no manual intervention:

```swift
// Automatically called during comprehensive migration
let (account, scalesCount) = try await migrationService.migrateAccountAndScaleData()

// Or migrate scales independently for an existing account
let migratedDevices = try await scaleMigrationService.migrateScaleData(for: accountId)
```

### Manual Scale Migration (if needed)
```swift
let scaleMigrationService = ScaleMigrationService()
let accountId = "your-account-id"

// Check if migration is needed
if scaleMigrationService.isMigrationNeeded(for: accountId) {
    do {
        let migratedDevices = try await scaleMigrationService.migrateScaleData(for: accountId)
        print("Successfully migrated \(migratedDevices.count) scales")
        
        // Clean up after successful migration
        scaleMigrationService.cleanupAfterMigration(for: accountId)
    } catch {
        print("Scale migration failed: \(error)")
    }
}
```

## Testing Migration

To test the scale migration:

1. **Prepare Test Data**: Add test scale data to UserDefaults:
```swift
let testScaleData = """
[
  {
    "id": "test-scale-id",
    "peripheralIdentifier": "ABC123-DEF456",
    "nickname": "My Scale",
    "sku": "0412",
    "mac": "AA:BB:CC:DD:EE:FF",
    "password": 1234,
    "isDeleted": false,
    "name": "WeightGurus Scale",
    "type": "btWifiR4",
    "broadcastId": 5678,
    "userNumber": 1,
    "createdAt": "2025-08-09T03:46:59.144Z",
    "isTemporary": false,
    "scaleToken": "test-token",
    "latestVersion": "1.2.3",
    "preference": {
      "displayName": "Bathroom Scale",
      "displayMetrics": ["weight", "bodyFat", "muscleMass"],
      "shouldFactoryReset": false,
      "shouldMeasureImpedance": true,
      "shouldMeasurePulse": true,
      "timeFormat": "12h",
      "tzOffset": -480,
      "wifiFotaScheduleTime": 3600
    }
  }
]
"""
let accountId = "test-account-id"
UserDefaults.standard.set(testScaleData, forKey: "scale_\(accountId)")
```

2. **Launch App**: The migration will run automatically during account migration
3. **Verify Results**: Check that scales appear in the native app with correct configurations
4. **Test Connectivity**: Verify that migrated scales can connect and sync properly
5. **Confirm Cleanup**: Verify that the UserDefaults scale keys have been removed

## Integration with Other Services

### ScaleService Integration
- Uses `ScaleService.createDevice()` to save migrated devices
- Triggers `ScaleService.syncAllScalesWithRemote()` after migration
- Leverages existing scale management infrastructure

### AccountMigrationService Integration
- Called as part of comprehensive migration flow
- Only runs after successful account migration
- Results are included in migration summary

### Error Recovery
- Failed scale migrations don't affect account or entry migrations
- Individual scale failures are isolated and logged
- Migration continues with remaining scales

## Important Notes

### Device Relationships
- **BathScale**: Contains scale type and body composition capability
- **R4ScalePreference**: Only created for scales with preference data
- **DeviceMetaData**: Only created for scales with firmware version info
- All relationships are properly linked after device creation

### Sync Status
- Migrated scales are marked as synced if they weren't temporary in Ionic app
- Temporary scales from Ionic app are marked as unsynced
- Scale sync is triggered after migration to ensure backend consistency

### Protocol Support
- Migration preserves protocol type for proper communication
- Body composition support is determined by scale type and SKU
- Unknown scale types are handled gracefully with nil protocols

## Dependencies

The scale migration service depends on:
- `ScaleService`: For creating and managing migrated devices
- `LoggerService`: For logging migration progress and errors
- `KvStorageService`: For accessing UserDefaults data
- Device model and related entities (BathScale, R4ScalePreference, DeviceMetaData)
- `MigrationKey` enum for consistent key generation

## Troubleshooting

### Migration Not Running
- Verify that `scale_{accountId}` exists in UserDefaults
- Check that account migration completed successfully first
- Review app logs for scale migration status messages

### Partial Scale Migration
- Check logs for specific scale migration failures
- Verify JSON structure matches expected format
- Ensure ScaleService dependencies are properly injected

### Post-Migration Issues
- Verify migrated scales appear in scale list
- Test Bluetooth connectivity with migrated scales
- Check that scale preferences are preserved
- Confirm scale sync with backend works properly

## File Locations

- **Migration Service**: `meApp/Data/Services/ScaleMigrationService.swift`
- **Scale Service Integration**: `meApp/Data/Services/ScaleService.swift`
- **Account Migration Integration**: `meApp/Data/Services/AccountMigrationService.swift`
- **Ionic Scale Model**: `meApp/Domain/Models/DB/IonicDBs/IonicScaleData.swift`
- **Migration Keys**: `meApp/Domain/Models/DB/IonicDBs/MigrationKey.swift`