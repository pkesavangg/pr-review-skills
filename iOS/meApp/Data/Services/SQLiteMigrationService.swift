import Foundation
import SQLite3
import SwiftData

/// Service to migrate data from Ionic app's SQLite database to SwiftData
final class SQLiteMigrationService {
    @Injector private var logger: LoggerServiceProtocol
    private let tag = "SQLiteMigrationService"
    private let databasePathOverride: String?
    private let injectedEntryRepository: (any EntryRepositoryProtocol)?

    /// Initialize the service
    init(databasePathOverride: String? = nil, entryRepository: (any EntryRepositoryProtocol)? = nil) {
        self.databasePathOverride = databasePathOverride
        self.injectedEntryRepository = entryRepository
    }

    /// SQLite database connection
    private var db: OpaquePointer?

    /// Path to the Ionic app's SQLite database (Capacitor stores in Library/CapacitorDatabase)
    private var databasePath: String {
        if let databasePathOverride { return databasePathOverride }
        guard let libraryPath = FileManager.default.urls(
            for: .libraryDirectory,
            in: .userDomainMask
        ).first else {
            return "CapacitorDatabase/WeightGurus4SQLite.db"
        }
        let dbPath = libraryPath.appendingPathComponent("CapacitorDatabase/WeightGurus4SQLite.db").path
        return dbPath
    }

    /// Get the actual database path for logging purposes
    private var actualDatabasePath: String {
        return databasePath
    }
    
    // MARK: - Migration Interface
    
    /// Migrates all unsynced opStack operations from SQLite to SwiftData for all users
    /// - Returns: Dictionary with userId as key and number of migrated operations as value
    func migrateAllUsersEntryData() async throws -> [String: Int] {
        Task { @MainActor in
            logger.log(level: .info, tag: tag, message: "Starting SQLite opStack to SwiftData migration for all users")
        }
        
        guard openDatabase() else {
            throw MigrationError.databaseConnectionFailed
        }
        
        defer { closeDatabase() }
        
        do {
            // Check if opStack tables exist
            guard try checkTablesExist() else {
                Task { @MainActor in
                    logger.log(level: .info, tag: tag, message: "SQLite opStack tables not found - no data to migrate")
                }
                return [:]
            }
            
            // Migrate unsynced operations from opStack for all users
            let migratedData = try await migrateAllUsersEntries()
            
            let totalMigrated = migratedData.values.reduce(0, +)
            Task { @MainActor in
                logger.log(
                    level: .info,
                    tag: tag,
                    message: "OpStack migration completed successfully. Migrated \(totalMigrated) operations for \(migratedData.count) users"
                )
            }
            return migratedData
            
        } catch {
            Task { @MainActor in
                logger.log(level: .error, tag: tag, message: "OpStack migration failed: \(error.localizedDescription)")
            }
            throw error
        }
    }
    
    /// Checks if migration is needed by looking for the SQLite database
    func isMigrationNeeded() -> Bool {
        return FileManager.default.fileExists(atPath: databasePath)
    }
    
    /// Removes the SQLite database after successful migration
    func cleanupAfterMigration() throws {
       let dbPath = databasePath
       guard FileManager.default.fileExists(atPath: dbPath) else { return }
       
       try FileManager.default.removeItem(atPath: dbPath)
        Task { @MainActor in
            logger.log(level: .info, tag: tag, message: "SQLite database cleaned up after migration: \(dbPath)")
        }
    }
    
    // MARK: - Private Methods
    
    private func openDatabase() -> Bool {
        let dbPath = actualDatabasePath
        guard FileManager.default.fileExists(atPath: dbPath) else {
            Task { @MainActor in
                logger.log(level: .error, tag: tag, message: "SQLite database not found at path: \(dbPath)")
            }
            return false
        }
        
        if sqlite3_open(dbPath, &db) != SQLITE_OK {
            Task { @MainActor in
                logger.log(level: .error, tag: tag, message: "Unable to open SQLite database")
            }
            return false
        }
        
        Task { @MainActor in
            logger.log(level: .info, tag: tag, message: "SQLite database opened successfully")
        }
        return true
    }
    
    private func closeDatabase() {
        if db != nil {
            sqlite3_close(db)
            db = nil
        }
    }
    
    private func checkTablesExist() throws -> Bool {
        let query = "SELECT name FROM sqlite_master WHERE type='table' AND (name='opStack' OR name='opStack_metric')"
        var statement: OpaquePointer?
        
        guard sqlite3_prepare_v2(db, query, -1, &statement, nil) == SQLITE_OK else {
            throw MigrationError.queryPreparationFailed
        }
        
        defer { sqlite3_finalize(statement) }
        
        var tableCount = 0
        while sqlite3_step(statement) == SQLITE_ROW {
            tableCount += 1
        }
        Task { @MainActor in
            logger.log(level: .info, tag: tag, message: "Found \(tableCount) relevant opStack tables in SQLite database")
        }
        
        return tableCount >= 1 // At least 'opStack' table should exist
    }
    
    private func migrateAllUsersEntries() async throws -> [String: Int] { // swiftlint:disable:this function_body_length
        let entryRepository: any EntryRepositoryProtocol
        if let injectedEntryRepository {
            entryRepository = injectedEntryRepository
        } else {
            entryRepository = await MainActor.run { EntryRepository() }
        }
        var migratedData: [String: Int] = [:]
        
        // Query to fetch ALL unsynced operations with metrics joined - no user filter
        let query = """
            SELECT 
                o.id, o.userId, o.entryTimestamp, NULL as serverTimestamp, o.operationType,
                o.weight, o.bodyFat, o.muscleMass, o.water, o.bmi, NULL as verified, o.source, NULL as isPlaceholder,
                m.bmr, m.metabolicAge, m.proteinPercent, m.pulse, m.skeletalMusclePercent,
                m.subcutaneousFatPercent, m.visceralFatLevel, m.boneMass, NULL as impedance, NULL as unit,
                o.attempts
            FROM opStack o
            LEFT JOIN opStack_metric m ON o.userId = m.userId AND o.entryTimestamp = m.entryTimestamp
            ORDER BY o.userId, o.entryTimestamp DESC
        """
        
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(db, query, -1, &statement, nil) == SQLITE_OK else {
            let errorMessage = String(cString: sqlite3_errmsg(db))
            Task { @MainActor in
                logger.log(level: .error, tag: tag, message: "Query preparation failed: \(errorMessage)")
            }
            throw MigrationError.queryPreparationFailed
        }
        
        defer { sqlite3_finalize(statement) }
        
        guard let statement = statement else {
            throw MigrationError.queryPreparationFailed
        }
        
        var rowsFound = 0
        while sqlite3_step(statement) == SQLITE_ROW {
            rowsFound += 1
            
            do {
                // Get the userId from the current row
                let userId = String(cString: sqlite3_column_text(statement, 1))
                let entry = try createEntryFromSQLiteRow(statement: statement, accountId: userId)
                try await entryRepository.saveEntry(entry)
                
                // Track migration count per user
                migratedData[userId] = (migratedData[userId] ?? 0) + 1
                
            } catch {
                Task { @MainActor in
                    logger.log(level: .error, tag: tag, message: "Failed to migrate entry: \(error.localizedDescription)")
                }
                // Continue with next entry
            }
        }
        
        let totalMigrated = migratedData.values.reduce(0, +)
        Task { @MainActor in
            logger.log(
                level: .info,
                tag: tag,
                message: "Query completed. Rows found: \(rowsFound), Successfully migrated: \(totalMigrated) entries for \(migratedData.count) users"
            )
        }
        
        return migratedData
    }
    
    private func createEntryFromSQLiteRow(statement: OpaquePointer, accountId: String) throws -> Entry {
        // Extract basic opStack operation data
        let entryTimestamp = String(cString: sqlite3_column_text(statement, 2))
        let operationType = sqlite3_column_text(statement, 4) != nil ? String(cString: sqlite3_column_text(statement, 4)) : "create"
        _ = sqlite3_column_type(statement, 21) != SQLITE_NULL ? Int(sqlite3_column_int(statement, 21)) : 0
        
        // Create the main entry (this will be treated as an unsynced operation)
        let entry = Entry(
            entryTimestamp: entryTimestamp,
            accountId: accountId,
            operationType: operationType,
            serverTimestamp: nil, // opStack operations don't have serverTimestamp yet
            deviceType: "scale",
            isSynced: false, // Mark as unsynced since it's from opStack
        )
        
        // Create scale entry if weight data exists
        let weight = sqlite3_column_int(statement, 5)
        if weight > 0 {
            let scaleEntry = BathScaleEntry(
                weight: Int(weight),
                bodyFat: sqlite3_column_type(statement, 6) != SQLITE_NULL ? Int(sqlite3_column_int(statement, 6)) : nil,
                muscleMass: sqlite3_column_type(statement, 7) != SQLITE_NULL ? Int(sqlite3_column_int(statement, 7)) : nil,
                water: sqlite3_column_type(statement, 8) != SQLITE_NULL ? Int(sqlite3_column_int(statement, 8)) : nil,
                bmi: sqlite3_column_type(statement, 9) != SQLITE_NULL ? Int(sqlite3_column_int(statement, 9)) : nil,
                source: sqlite3_column_text(statement, 11) != nil ? String(cString: sqlite3_column_text(statement, 11)) : nil
            )
            entry.scaleEntry = scaleEntry
        }
        
        // Create scale metric if extended data exists (from opStack_metric)
        let hasBmr = sqlite3_column_type(statement, 13) != SQLITE_NULL
        let hasMetabolicAge = sqlite3_column_type(statement, 14) != SQLITE_NULL
        
        if hasBmr || hasMetabolicAge {
            let scaleMetric = BathScaleMetric(
                bmr: hasBmr ? Int(sqlite3_column_int(statement, 13)) : nil,
                metabolicAge: hasMetabolicAge ? Int(sqlite3_column_int(statement, 14)) : nil,
                proteinPercent: sqlite3_column_type(statement, 15) != SQLITE_NULL ? Int(sqlite3_column_int(statement, 15)) : nil,
                pulse: sqlite3_column_type(statement, 16) != SQLITE_NULL ? Int(sqlite3_column_int(statement, 16)) : nil,
                skeletalMusclePercent: sqlite3_column_type(statement, 17) != SQLITE_NULL ? Int(sqlite3_column_int(statement, 17)) : nil,
                subcutaneousFatPercent: sqlite3_column_type(statement, 18) != SQLITE_NULL ? Int(sqlite3_column_int(statement, 18)) : nil,
                visceralFatLevel: sqlite3_column_type(statement, 19) != SQLITE_NULL ? Int(sqlite3_column_int(statement, 19)) : nil,
                boneMass: sqlite3_column_type(statement, 20) != SQLITE_NULL ? Int(sqlite3_column_int(statement, 20)) : nil,
                impedance: nil, // opStack_metric doesn't have impedance
                unit: nil // opStack_metric doesn't have unit
            )
            entry.scaleEntryMetric = scaleMetric
        }
        
        return entry
    }
}
