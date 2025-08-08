# Migration Documentation - Ionic to Native iOS App

## Overview

This document provides comprehensive information about the migration process from the Ionic Weight Gurus 4 app to the native SwiftUI Weight Gurus app, implemented in August 2025. The migration system handles the seamless transfer of user data including accounts, entries, and scale devices from the legacy Ionic app to the new native iOS app.

## Migration Context

### Background
- **Legacy App**: Weight Gurus 4 (Ionic/Capacitor-based)
- **New App**: Weight Gurus Native (SwiftUI/SwiftData-based)
- **Migration Period**: August 2025
- **Shared Bundle ID**: Both apps use the same bundle identifier, enabling data access

### Data Migration Scope
The migration system transfers three main data categories:
1. **Account Data**: User profiles, settings, authentication tokens
2. **Entry Data**: Weight measurements and body composition metrics
3. **Scale Data**: Paired device information and preferences

## Migration Architecture

### Core Services

#### 1. AccountMigrationService
- **File**: `AccountMigrationService.swift`
- **Documentation**: [`AccountMigrationService.md`](./AccountMigrationService.md)
- **Purpose**: Migrates user account data from Ionic Capacitor Preferences to SwiftData
- **Data Source**: iOS UserDefaults (Capacitor Preferences API)
- **Target**: SwiftData Account entities

#### 2. SQLiteMigrationService
- **File**: `SQLiteMigrationService.swift`
- **Documentation**: [`SQLiteMigrationService.md`](./SQLiteMigrationService.md)
- **Purpose**: Migrates weight entries from Ionic SQLite database to SwiftData
- **Data Source**: SQLite database (`WeightGurus4SQLite.db`)
- **Target**: SwiftData Entry entities with relationships

#### 3. ScaleMigrationService
- **File**: `ScaleMigrationService.swift`
- **Documentation**: [`ScaleMigrationService.md`](./ScaleMigrationService.md)
- **Purpose**: Migrates paired scale devices from Ionic preferences to SwiftData
- **Data Source**: iOS UserDefaults (Capacitor Preferences API)
- **Target**: SwiftData Device entities with relationships

### Migration Flow

```
App Launch
    ↓
ContentViewModel.performAppInitialization()
    ↓
AccountService.migrateFromIonicAppIfNeeded()
    ↓
┌─────────────────────────────────────────────┐
│           Migration Orchestration           │
├─────────────────────────────────────────────┤
│ 1. AccountMigrationService                  │
│    - Check for Ionic account data           │
│    - Migrate account + settings             │
│    - Clean up UserDefaults                  │
│                                             │
│ 2. ScaleMigrationService                    │
│    - Check for Ionic scale data             │
│    - Migrate device configurations          │
│    - Clean up UserDefaults                  │
│                                             │
│ 3. SQLiteMigrationService                   │
│    - Check for SQLite database              │
│    - Migrate all user entries               │
│    - Clean up database file                 │
└─────────────────────────────────────────────┘
    ↓
EntryService.migrateFromSQLiteIfNeeded()
    ↓
Dashboard Data Loading & Sync
```

## Migration Process Details

### Phase 1: Account Migration
1. **Detection**: Check for `activeAccountKey` in UserDefaults
2. **Parsing**: Decode JSON account data from Ionic app
3. **Transformation**: Convert to native Account model with settings
4. **Persistence**: Save to SwiftData
5. **Cleanup**: Remove Ionic UserDefaults entries

### Phase 2: Scale Migration
1. **Detection**: Check for scale data keys per account
2. **Parsing**: Decode JSON scale array from Ionic app
3. **Transformation**: Convert to native Device models with relationships
4. **Persistence**: Save to SwiftData via ScaleService
5. **Cleanup**: Remove Ionic UserDefaults entries

### Phase 3: Entry Migration
1. **Detection**: Check for SQLite database existence
2. **Query**: Read from `opStack` and `opStack_metric` tables
3. **Transformation**: Convert to Entry/BathScaleEntry/BathScaleMetric models
4. **Persistence**: Save to SwiftData for all users
5. **Cleanup**: Delete SQLite database file

## Data Mapping

### UserDefaults Keys (Ionic → Native)
```
Ionic Capacitor Preferences → iOS UserDefaults → SwiftData

activeAccountKey              → Account + Settings entities
offlineAccount_{accountId}    → (cleanup only)
scale_{accountId}            → Device + BathScale + R4ScalePreference entities
```

### SQLite Tables (Ionic → Native)
```
SQLite Database → SwiftData

opStack                      → Entry + BathScaleEntry
opStack_metric              → BathScaleMetric
```

## Error Handling & Recovery

### Migration Failure Scenarios
1. **Partial Migration**: Some services succeed, others fail
2. **Data Corruption**: Invalid JSON or SQLite data
3. **Storage Errors**: SwiftData persistence failures
4. **Resource Conflicts**: File access or locking issues

### Recovery Strategy
- **Non-blocking**: Migration failures don't prevent app launch
- **Logging**: Comprehensive error logging for debugging
- **Graceful Degradation**: App continues with normal sign-up flow
- **Idempotent**: Safe to retry migration on subsequent launches

## Testing Migration

### Test Setup
1. **Prepare Ionic Data**: Install WG4 app and create test data
2. **Install Native App**: Use same bundle ID to access shared data
3. **Trigger Migration**: Launch native app to start automatic migration
4. **Verify Results**: Check data integrity and cleanup

### Validation Checklist
- [ ] Account data migrated correctly
- [ ] All settings preserved and functional
- [ ] Scale devices appear and connect properly
- [ ] Entry data matches between apps
- [ ] Ionic data cleaned up after migration
- [ ] App functions normally after migration

## Dependencies

### Core Dependencies
- **SwiftData**: Target persistence layer
- **Combine**: Reactive programming for state management
- **Foundation**: JSON parsing and file operations
- **SQLite3**: Reading legacy SQLite database

### Service Dependencies
- **KvStorageService**: UserDefaults access wrapper
- **LoggerService**: Centralized logging system
- **AccountService**: Account management and authentication
- **EntryService**: Entry data management and sync
- **ScaleService**: Device management and Bluetooth operations

## File Structure

```
meApp/Data/Services/
├── AccountMigrationService.swift      # Account migration implementation
├── AccountMigrationService.md         # Account migration documentation
├── ScaleMigrationService.swift        # Scale migration implementation
├── ScaleMigrationService.md           # Scale migration documentation
├── SQLiteMigrationService.swift       # Entry migration implementation
├── SQLiteMigrationService.md          # Entry migration documentation
└── MIGRATION.md                       # This overview document

meApp/Domain/Models/DB/IonicDBs/
├── MigrationKey.swift                 # Shared migration key constants
├── IonicAccountData.swift             # Ionic account data model
└── IonicScaleData.swift               # Ionic scale data model

meApp/Domain/Models/API/Auth/
└── AccountMigrationError.swift        # Migration error types
```

## Important Notes

### Security Considerations
- **Token Migration**: Authentication tokens are migrated but may need refresh
- **Data Validation**: All migrated data is validated before persistence
- **Cleanup**: Sensitive data is properly removed after migration

### Performance Considerations
- **Background Processing**: Migration runs on background queues where possible
- **Progress Tracking**: Large migrations show progress to users
- **Memory Management**: Efficient handling of large datasets

### Maintenance
- **Version Compatibility**: Migration handles different Ionic app versions
- **Schema Evolution**: Migration adapts to model changes over time
- **Monitoring**: Production migration metrics and error tracking

## Future Considerations

### Post-Migration
- **Analytics**: Track migration success rates and common failures
- **Support**: Help users troubleshoot migration issues
- **Cleanup**: Eventually remove migration code after user base transitions

### Documentation Updates
This documentation should be updated when:
- Migration logic changes significantly
- New data types are added to migration
- Error handling is improved
- Performance optimizations are implemented

---

For detailed implementation information, refer to the individual service documentation files linked above.