# SQLite to SwiftData Migration Guide

## Overview

The `SQLiteMigrationService` handles the migration of entry data from your Ionic app's SQLite database to the native iOS app's SwiftData storage. Since both apps use the same bundle identifier, the native app can access the SQLite database files created by the Ionic app.

## How It Works

### 1. Database Location
The Ionic app (using Capacitor) stores its SQLite database at:
```
Library/CapacitorDatabase/WeightGurus4SQLite.db
```

### 2. Migration Process
When the native app launches:
1. **Check for SQLite Database**: The service checks if `WeightGurus4SQLite.db` exists in the Library/CapacitorDatabase directory
2. **Read SQLite Data (opStack)**: If found, it opens the SQLite database and queries the `opStack` and `opStack_metric` tables (joined on `userId` + `entryTimestamp`) for the current account only
3. **Transform Data**: Converts opStack rows to SwiftData `Entry` objects and related `BathScaleEntry`/`BathScaleMetric`
4. **Save to SwiftData**: Inserts the transformed data into the SwiftData model context
5. **Cleanup**: After successful migration, removes the SQLite database file

### 3. Schema Mapping

#### SQLite Schema (Ionic App – opStack)
```sql
-- opStack table (unsynced operation stack)
CREATE TABLE opStack (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    userId TEXT NOT NULL,
    entryTimestamp TEXT NOT NULL,
    operationType TEXT,
    weight INTEGER,
    bodyFat INTEGER,
    muscleMass INTEGER,
    water INTEGER,
    bmi INTEGER,
    source TEXT,
    attempts INTEGER
);

-- opStack_metric table (extended metrics for opStack)
CREATE TABLE opStack_metric (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    userId TEXT NOT NULL,
    entryTimestamp TEXT NOT NULL,
    bmr INTEGER,
    metabolicAge INTEGER,
    proteinPercent INTEGER,
    pulse INTEGER,
    skeletalMusclePercent INTEGER,
    subcutaneousFatPercent INTEGER,
    visceralFatLevel INTEGER,
    boneMass INTEGER
    -- impedance INTEGER,
    -- unit TEXT
);
```

#### SwiftData Schema (Native App)
```swift
@Model
final class Entry {
    @Attribute(.unique) var id: UUID
    var accountId: String
    var entryTimestamp: String
    var serverTimestamp: String?
    var operationType: String
    var deviceType: String
    var isSynced: Bool
    var attempts: Int
    var isFailedToSync: Bool
    
    @Relationship var scaleEntry: BathScaleEntry?
    @Relationship var scaleEntryMetric: BathScaleMetric?
}

@Model
final class BathScaleEntry {
    var weight: Int?
    var bodyFat: Int?
    var muscleMass: Int?
    var water: Int?
    var bmi: Int?
    var source: String?
}

@Model
final class BathScaleMetric {
    var bmr: Int?
    var metabolicAge: Int?
    var proteinPercent: Int?
    var pulse: Int?
    var skeletalMusclePercent: Int?
    var subcutaneousFatPercent: Int?
    var visceralFatLevel: Int?
    var boneMass: Int?
    var impedance: Int?
    var unit: String?
}
```

### 4. Data Transformation

The migration service performs the following transformations:

- **Entry ID**: Generates a new UUID for SwiftData (SQLite uses integer IDs)
- **User ID → Account ID**: Maps `opStack.userId` → `Entry.accountId` (filtered by current account)
- **Timestamps**: `Entry.entryTimestamp` comes from `opStack.entryTimestamp`; `serverTimestamp` is set to `nil`
- **Operation Type**: Uses `opStack.operationType`, defaulting to `"create"` when NULL
- **Device Type**: Sets to `"scale"` for all migrated entries
- **Sync Status**: Marks migrated entries as `isSynced = false` (they represent unsynced operations)
- **Relationships**:
  - `BathScaleEntry` is created only when `weight > 0` and maps weight/bodyFat/muscleMass/water/bmi/source from `opStack`
  - `BathScaleMetric` is created when any metric exists in `opStack_metric` and maps bmr/metabolicAge/proteinPercent/pulse/skeletalMusclePercent/subcutaneousFatPercent/visceralFatLevel/boneMass
- **NULL Handling**: Properly handles NULL values from SQLite; missing fields remain `nil`

Selection is done with a single LEFT JOIN for the active account:

```sql
SELECT 
  o.id, o.userId, o.entryTimestamp, NULL as serverTimestamp, o.operationType,
  o.weight, o.bodyFat, o.muscleMass, o.water, o.bmi, NULL as verified, o.source, NULL as isPlaceholder,
  m.bmr, m.metabolicAge, m.proteinPercent, m.pulse, m.skeletalMusclePercent,
  m.subcutaneousFatPercent, m.visceralFatLevel, m.boneMass, NULL as impedance, NULL as unit,
  o.attempts
FROM opStack o
LEFT JOIN opStack_metric m ON o.userId = m.userId AND o.entryTimestamp = m.entryTimestamp
WHERE o.userId = '<currentAccountId>'
ORDER BY o.entryTimestamp DESC;
```

## Usage

The migration runs automatically when the app starts. No manual intervention is required.

### Automatic Migration Flow
1. User launches native app
2. `ContentViewModel.loadData()` calls `entryService.migrateFromSQLiteIfNeeded()`
3. Migration service checks for SQLite database
4. If found, migrates all data and cleans up
5. App continues with normal sync and data loading

### Manual Migration (if needed)
```swift
let migrationService = SQLiteMigrationService()
let accountId = "your-account-id"

do {
    let migratedCount = try await migrationService.migrateEntryData(accountId: accountId)
    print("Migrated \(migratedCount) entries")
    
    // Clean up after successful migration
    try migrationService.cleanupAfterMigration()
} catch {
    print("Migration failed: \(error)")
}
```

## Error Handling

The service handles common migration errors:

- **Database Connection Failed**: SQLite database cannot be opened
- **Query Preparation Failed**: SQL query syntax issues
- **Data Conversion Failed**: Type conversion errors

All errors are logged with detailed information for debugging.

## Important Notes

### Data Integrity
- All migrated entries are marked as `isSynced = true` to prevent re-uploading to the server
- Original SQLite database is only deleted after successful migration
- Migration is idempotent - safe to run multiple times

### Performance
- Single prepared statement with LEFT JOIN minimizes roundtrips
- Progress is logged every 100 entries
- Uses `sqlite3_prepare_v2` and iterates rows efficiently

### Account Mapping
- The service assumes `opStack.userId` maps to `Entry.accountId`
- Make sure the account is properly authenticated before migration
- Only entries for the current account are migrated (`WHERE o.userId = accountId`)

## Troubleshooting

### Migration Not Running
- Check if `WeightGurus4SQLite.db` exists in Library/CapacitorDatabase directory
- Verify user is logged in before migration
- Check app logs for migration status messages
- Ensure both `opStack` and/or `opStack_metric` tables exist; migration skips if not found

### Partial Migration
- Review error logs for specific entry failures
- Migration continues with remaining entries even if some fail
- Failed entries are logged with details

### Data Validation
After migration, verify:
- Entry counts match between old and new systems
- Weight and metric data is preserved
- Timestamps are correctly formatted
- Relationships between Entry, BathScaleEntry, and BathScaleMetric are intact

## Testing

To test the migration:

1. Install the Ionic app and create test data
2. Install the native app (same bundle ID)
3. Launch native app and check logs for migration messages
4. Verify data appears correctly in the native app
5. Confirm SQLite database is cleaned up after migration

## File Locations

- **Migration Service**: `meApp/Data/Services/SQLiteMigrationService.swift`
- **Entry Service Integration**: `meApp/Data/Services/EntryService.swift`
- **App Startup Integration**: `meApp/Features/Common/ViewModels/ContentViewModel.swift`