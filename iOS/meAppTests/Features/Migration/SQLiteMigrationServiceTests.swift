import Foundation
import SQLite3
import Testing
@testable import meApp

// MARK: - SQLiteMigrationServiceTests

@Suite(.serialized)
@MainActor
struct SQLiteMigrationServiceTests {

    // MARK: - isMigrationNeeded

    @Test("isMigrationNeeded returns true when database file exists")
    func isMigrationNeeded_databaseExists_returnsTrue() {
        let (sut, _, dbPath) = makeSUT()
        SQLiteTestHelper.createEmptyDatabase(at: dbPath)
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        #expect(sut.isMigrationNeeded() == true)
    }

    @Test("isMigrationNeeded returns false when database file does not exist")
    func isMigrationNeeded_noDatabaseFile_returnsFalse() {
        let (sut, _, _) = makeSUT()
        #expect(sut.isMigrationNeeded() == false)
    }

    // MARK: - migrateAllUsersEntryData: Success

    @Test("migrateAllUsersEntryData migrates single user entries successfully")
    func migrateAllUsersEntryData_singleUser_migratesEntries() async throws {
        let (sut, repo, dbPath) = makeSUT()
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [
            .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1800),
            .init(userId: "user-A", timestamp: "2026-01-02T08:00:00Z", opType: "create", weight: 1750)
        ])
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        let result = try await sut.migrateAllUsersEntryData()

        #expect(result["user-A"] == 2)
        #expect(repo.saveEntryCalls == 2)
    }

    @Test("migrateAllUsersEntryData migrates entries for multiple users")
    func migrateAllUsersEntryData_multipleUsers_migratesAllEntries() async throws {
        let (sut, repo, dbPath) = makeSUT()
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [
            .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1800),
            .init(userId: "user-B", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1600),
            .init(userId: "user-B", timestamp: "2026-01-02T08:00:00Z", opType: "create", weight: 1620)
        ])
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        let result = try await sut.migrateAllUsersEntryData()

        #expect(result["user-A"] == 1)
        #expect(result["user-B"] == 2)
        #expect(repo.saveEntryCalls == 3)
    }

    @Test("migrateAllUsersEntryData preserves weight and scale data in saved entries")
    func migrateAllUsersEntryData_withScaleData_preservesValues() async throws {
        let (sut, repo, dbPath) = makeSUT()
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [
            .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "create",
                  weight: 1800, bodyFat: 250, muscleMass: 820, water: 540, bmi: 230, source: "manual")
        ])
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        _ = try await sut.migrateAllUsersEntryData()

        let saved = repo.lastSavedEntry
        #expect(saved?.scaleEntry?.weight == 1800)
        #expect(saved?.scaleEntry?.bodyFat == 250)
        #expect(saved?.scaleEntry?.muscleMass == 820)
        #expect(saved?.scaleEntry?.water == 540)
        #expect(saved?.scaleEntry?.bmi == 230)
        #expect(saved?.scaleEntry?.source == "manual")
    }

    @Test("migrateAllUsersEntryData preserves metric data from opStack_metric join")
    func migrateAllUsersEntryData_withMetrics_preservesValues() async throws {
        let (sut, repo, dbPath) = makeSUT()
        SQLiteTestHelper.createDatabase(
            at: dbPath,
            entries: [
                .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1800)
            ],
            metrics: [
                .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z",
                      bmr: 1600, metabolicAge: 35, proteinPercent: 190, pulse: 72,
                      skeletalMusclePercent: 410, subcutaneousFatPercent: 210,
                      visceralFatLevel: 11, boneMass: 80)
            ]
        )
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        _ = try await sut.migrateAllUsersEntryData()

        let saved = repo.lastSavedEntry
        #expect(saved?.scaleEntryMetric?.bmr == 1600)
        #expect(saved?.scaleEntryMetric?.metabolicAge == 35)
        #expect(saved?.scaleEntryMetric?.proteinPercent == 190)
        #expect(saved?.scaleEntryMetric?.pulse == 72)
        #expect(saved?.scaleEntryMetric?.skeletalMusclePercent == 410)
        #expect(saved?.scaleEntryMetric?.subcutaneousFatPercent == 210)
        #expect(saved?.scaleEntryMetric?.visceralFatLevel == 11)
        #expect(saved?.scaleEntryMetric?.boneMass == 80)
    }

    @Test("migrateAllUsersEntryData creates entry without scale data when weight is zero")
    func migrateAllUsersEntryData_weightZero_noScaleEntry() async throws {
        let (sut, repo, dbPath) = makeSUT()
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [
            .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "delete", weight: 0)
        ])
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        _ = try await sut.migrateAllUsersEntryData()

        let saved = repo.lastSavedEntry
        #expect(saved != nil)
        #expect(saved?.scaleEntry == nil)
        #expect(saved?.operationType == "delete")
    }

    @Test("migrateAllUsersEntryData creates entry without metric when no bmr or metabolicAge")
    func migrateAllUsersEntryData_noMetrics_noScaleEntryMetric() async throws {
        let (sut, repo, dbPath) = makeSUT()
        SQLiteTestHelper.createDatabase(
            at: dbPath,
            entries: [
                .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1800)
            ],
            metrics: [] // No matching metric row
        )
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        _ = try await sut.migrateAllUsersEntryData()

        #expect(repo.lastSavedEntry?.scaleEntryMetric == nil)
    }

    @Test("migrateAllUsersEntryData handles NULL values in optional scale fields")
    func migrateAllUsersEntryData_nullOptionalFields_handlesGracefully() async throws {
        let (sut, repo, dbPath) = makeSUT()
        // Entry with weight but all other scale fields NULL
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [
            .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1800)
        ])
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        _ = try await sut.migrateAllUsersEntryData()

        let saved = repo.lastSavedEntry
        #expect(saved?.scaleEntry?.weight == 1800)
        #expect(saved?.scaleEntry?.bodyFat == nil)
        #expect(saved?.scaleEntry?.muscleMass == nil)
        #expect(saved?.scaleEntry?.water == nil)
        #expect(saved?.scaleEntry?.bmi == nil)
        #expect(saved?.scaleEntry?.source == nil)
    }

    @Test("migrateAllUsersEntryData sets entries as unsynced with scale device type")
    func migrateAllUsersEntryData_entryProperties_correctDefaults() async throws {
        let (sut, repo, dbPath) = makeSUT()
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [
            .init(userId: "user-A", timestamp: "2026-03-01T08:00:00Z", opType: "create", weight: 1800)
        ])
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        _ = try await sut.migrateAllUsersEntryData()

        let saved = repo.lastSavedEntry
        #expect(saved?.accountId == "user-A")
        #expect(saved?.entryTimestamp == "2026-03-01T08:00:00Z")
        #expect(saved?.operationType == "create")
        #expect(saved?.deviceType == "scale")
        #expect(saved?.isSynced == false)
        #expect(saved?.serverTimestamp == nil)
    }

    @Test("migrateAllUsersEntryData defaults operationType to create when NULL")
    func migrateAllUsersEntryData_nullOpType_defaultsToCreate() async throws {
        let (sut, repo, dbPath) = makeSUT()
        SQLiteTestHelper.createDatabaseWithNullOpType(at: dbPath, userId: "user-A", timestamp: "2026-01-01T08:00:00Z", weight: 1800)
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        _ = try await sut.migrateAllUsersEntryData()

        #expect(repo.lastSavedEntry?.operationType == "create")
    }

    // MARK: - migrateAllUsersEntryData: Empty/No Data

    @Test("migrateAllUsersEntryData returns empty dict when no opStack tables exist")
    func migrateAllUsersEntryData_noTables_returnsEmpty() async throws {
        let (sut, repo, dbPath) = makeSUT()
        SQLiteTestHelper.createEmptyDatabase(at: dbPath)
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        let result = try await sut.migrateAllUsersEntryData()

        #expect(result.isEmpty)
        #expect(repo.saveEntryCalls == 0)
    }

    @Test("migrateAllUsersEntryData returns empty dict when opStack table has no rows")
    func migrateAllUsersEntryData_emptyTable_returnsEmpty() async throws {
        let (sut, repo, dbPath) = makeSUT()
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [])
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        let result = try await sut.migrateAllUsersEntryData()

        #expect(result.isEmpty)
        #expect(repo.saveEntryCalls == 0)
    }

    // MARK: - migrateAllUsersEntryData: Error Handling

    @Test("migrateAllUsersEntryData throws databaseConnectionFailed when file doesn't exist")
    func migrateAllUsersEntryData_noDatabase_throwsConnectionError() async {
        let (sut, _, _) = makeSUT()

        do {
            _ = try await sut.migrateAllUsersEntryData()
            Issue.record("Expected migrateAllUsersEntryData to throw")
        } catch {
            #expect(error is MigrationError)
            #expect((error as? MigrationError) == .databaseConnectionFailed)
        }
    }

    @Test("migrateAllUsersEntryData continues when individual entry save fails")
    func migrateAllUsersEntryData_partialSaveFailure_continuesWithRemaining() async throws {
        let mockRepo = MockSQLiteMigrationEntryRepository(failOnCallNumbers: [1])
        let (sut, _, dbPath) = makeSUT(entryRepository: mockRepo)
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [
            .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1800),
            .init(userId: "user-A", timestamp: "2026-01-02T08:00:00Z", opType: "create", weight: 1750),
            .init(userId: "user-A", timestamp: "2026-01-03T08:00:00Z", opType: "create", weight: 1700)
        ])
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        let result = try await sut.migrateAllUsersEntryData()

        // 3 entries total, 1 fails → 2 successfully migrated
        #expect(result["user-A"] == 2)
        #expect(mockRepo.saveEntryCalls == 3)
    }

    @Test("migrateAllUsersEntryData returns empty dict when all saves fail")
    func migrateAllUsersEntryData_allSavesFail_returnsEmptyDict() async throws {
        let repo = MockEntryRepository()
        repo.saveEntryError = SQLiteMigrationTestError.saveFailed
        let (sut, _, dbPath) = makeSUT(entryRepository: repo)
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [
            .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1800)
        ])
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        let result = try await sut.migrateAllUsersEntryData()

        #expect(result.isEmpty)
    }

    // MARK: - cleanupAfterMigration

    @Test("cleanupAfterMigration removes database file")
    func cleanupAfterMigration_existingFile_removesIt() throws {
        let (sut, _, dbPath) = makeSUT()
        SQLiteTestHelper.createEmptyDatabase(at: dbPath)
        #expect(FileManager.default.fileExists(atPath: dbPath) == true)

        try sut.cleanupAfterMigration()

        #expect(FileManager.default.fileExists(atPath: dbPath) == false)
    }

    @Test("cleanupAfterMigration does nothing when file doesn't exist")
    func cleanupAfterMigration_noFile_noOp() throws {
        let (sut, _, _) = makeSUT()
        // Should complete without error
        try sut.cleanupAfterMigration()
    }

    // MARK: - Full Migration Flow

    @Test("isMigrationNeeded returns false after cleanup")
    func isMigrationNeeded_afterCleanup_returnsFalse() throws {
        let (sut, _, dbPath) = makeSUT()
        SQLiteTestHelper.createEmptyDatabase(at: dbPath)
        #expect(sut.isMigrationNeeded() == true)

        try sut.cleanupAfterMigration()

        #expect(sut.isMigrationNeeded() == false)
    }

    @Test("full migration flow: check needed, migrate, cleanup")
    func fullFlow_migrateAndCleanup_worksEndToEnd() async throws {
        let (sut, repo, dbPath) = makeSUT()
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [
            .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1800)
        ])

        // Step 1: Check migration needed
        #expect(sut.isMigrationNeeded() == true)

        // Step 2: Migrate
        let result = try await sut.migrateAllUsersEntryData()
        #expect(result["user-A"] == 1)
        #expect(repo.saveEntryCalls == 1)

        // Step 3: Cleanup
        try sut.cleanupAfterMigration()
        #expect(sut.isMigrationNeeded() == false)
    }

    // MARK: - Metric Partial Data

    @Test("migrateAllUsersEntryData creates metric when only bmr exists")
    func migrateAllUsersEntryData_onlyBmr_createsMetric() async throws {
        let (sut, repo, dbPath) = makeSUT()
        SQLiteTestHelper.createDatabase(
            at: dbPath,
            entries: [
                .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1800)
            ],
            metrics: [
                .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", bmr: 1600)
            ]
        )
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        _ = try await sut.migrateAllUsersEntryData()

        let metric = repo.lastSavedEntry?.scaleEntryMetric
        #expect(metric != nil)
        #expect(metric?.bmr == 1600)
        #expect(metric?.metabolicAge == nil)
    }

    @Test("migrateAllUsersEntryData creates metric when only metabolicAge exists")
    func migrateAllUsersEntryData_onlyMetabolicAge_createsMetric() async throws {
        let (sut, repo, dbPath) = makeSUT()
        SQLiteTestHelper.createDatabase(
            at: dbPath,
            entries: [
                .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1800)
            ],
            metrics: [
                .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", metabolicAge: 42)
            ]
        )
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        _ = try await sut.migrateAllUsersEntryData()

        let metric = repo.lastSavedEntry?.scaleEntryMetric
        #expect(metric != nil)
        #expect(metric?.bmr == nil)
        #expect(metric?.metabolicAge == 42)
    }

    // MARK: - makeSUT

    @MainActor
    private func makeSUT(
        entryRepository: (any EntryRepositoryProtocol)? = nil
    ) -> (sut: SQLiteMigrationService, repo: MockEntryRepository, dbPath: String) {
        TestDependencyContainer.reset()

        let dbPath = NSTemporaryDirectory() + "test_migration_\(UUID().uuidString).db"
        let repo = (entryRepository as? MockEntryRepository) ?? MockEntryRepository()
        let actualRepo = entryRepository ?? repo
        let sut = SQLiteMigrationService(databasePathOverride: dbPath, entryRepository: actualRepo)

        return (sut, repo, dbPath)
    }
}

// MARK: - Test Errors

private enum SQLiteMigrationTestError: Error, Equatable {
    case saveFailed
}

// MARK: - MockSQLiteMigrationEntryRepository

/// A specialized mock that fails on specific call numbers to test partial failure recovery.
@MainActor
private final class MockSQLiteMigrationEntryRepository: EntryRepositoryProtocol {
    private let failOnCallNumbers: Set<Int>
    private(set) var saveEntryCalls = 0
    private(set) var savedEntries: [Entry] = []

    init(failOnCallNumbers: Set<Int> = []) {
        self.failOnCallNumbers = failOnCallNumbers
    }

    func saveEntry(_ entry: Entry) async throws {
        saveEntryCalls += 1
        if failOnCallNumbers.contains(saveEntryCalls) {
            throw SQLiteMigrationTestError.saveFailed
        }
        savedEntries.append(entry)
    }

    // MARK: - Unused protocol stubs

    func fetchEntry(byId id: String) async throws -> Entry? { nil }
    func fetchAllEntries() async throws -> [Entry] { [] }
    func updateEntry(_ entry: Entry) async throws {}
    func updateEntrySyncStatus(entryId: String, isSynced: Bool, isFailedToSync: Bool, attempts: Int) async throws {}
    func deleteEntry(byId id: String) async throws {}
    func deleteAllEntries() async throws {}
    func fetchEntries(forUserId userId: String, operationType: String?) async throws -> [Entry] { [] }
    func fetchEntriesOfTimestamp(forUserId userId: String, timestamp: String) async throws -> [Entry] { [] }
    func fetchEntries(forMonth month: String, userId: String) async throws -> [Entry] { [] }
    func fetchEntries(forDay day: String, userId: String) async throws -> [Entry] { [] }
    func fetchUnsyncedEntries(forUserId userId: String) async throws -> [Entry] { [] }
    func fetchLatestEntry(forUserId userId: String) async throws -> Entry? { nil }
    func fetchEntries(lastNDays: Int, userId: String) async throws -> [Entry] { [] }
    func fetchEntryCount(forUserId userId: String) async throws -> Int { 0 }
    func fetchOldestEntry(forUserId userId: String) async throws -> Entry? { nil }
    func checkEntryTimestampExists(forUserId userId: String, entryTimestamp: String) async throws -> Bool { false }
    func fetchEntriesAsDTO(forUserId userId: String, operationType: String?) async throws -> [BathScaleOperationDTO] { [] }
    func fetchEntriesAsBpmDTO(forUserId userId: String, operationType: String?) async throws -> [BpmOperationDTO] { [] }
    func syncEntries(newEntries: [Entry]) async throws {}
}

// MARK: - SQLiteTestHelper

private enum SQLiteTestHelper {
    struct OpStackRow {
        let userId: String
        let timestamp: String
        let opType: String
        let weight: Int32
        var bodyFat: Int32?
        var muscleMass: Int32?
        var water: Int32?
        var bmi: Int32?
        var source: String?
        var attempts: Int32 = 0
    }

    struct OpStackMetricRow {
        let userId: String
        let timestamp: String
        var bmr: Int32?
        var metabolicAge: Int32?
        var proteinPercent: Int32?
        var pulse: Int32?
        var skeletalMusclePercent: Int32?
        var subcutaneousFatPercent: Int32?
        var visceralFatLevel: Int32?
        var boneMass: Int32?
    }

    static func createEmptyDatabase(at path: String) {
        var db: OpaquePointer?
        sqlite3_open(path, &db)
        sqlite3_close(db)
    }

    static func createDatabase(
        at path: String,
        entries: [OpStackRow],
        metrics: [OpStackMetricRow] = []
    ) {
        var db: OpaquePointer?
        sqlite3_open(path, &db)

        // Create opStack table
        sqlite3_exec(db, """
            CREATE TABLE opStack (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                userId TEXT NOT NULL,
                entryTimestamp TEXT NOT NULL,
                operationType TEXT,
                weight INTEGER DEFAULT 0,
                bodyFat INTEGER,
                muscleMass INTEGER,
                water INTEGER,
                bmi INTEGER,
                source TEXT,
                attempts INTEGER DEFAULT 0
            )
        """, nil, nil, nil)

        // Create opStack_metric table
        sqlite3_exec(db, """
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
            )
        """, nil, nil, nil)

        // Insert entries
        for entry in entries {
            let sql = buildInsertSQL(for: entry)
            sqlite3_exec(db, sql, nil, nil, nil)
        }

        // Insert metrics
        for metric in metrics {
            let sql = buildInsertSQL(for: metric)
            sqlite3_exec(db, sql, nil, nil, nil)
        }

        sqlite3_close(db)
    }

    static func createDatabaseWithNullOpType(at path: String, userId: String, timestamp: String, weight: Int32) {
        var db: OpaquePointer?
        sqlite3_open(path, &db)

        sqlite3_exec(db, """
            CREATE TABLE opStack (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                userId TEXT NOT NULL,
                entryTimestamp TEXT NOT NULL,
                operationType TEXT,
                weight INTEGER DEFAULT 0,
                bodyFat INTEGER,
                muscleMass INTEGER,
                water INTEGER,
                bmi INTEGER,
                source TEXT,
                attempts INTEGER DEFAULT 0
            )
        """, nil, nil, nil)

        sqlite3_exec(db, """
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
            )
        """, nil, nil, nil)

        // Insert with NULL operationType
        let sql = "INSERT INTO opStack (userId, entryTimestamp, operationType, weight) VALUES ('\(userId)', '\(timestamp)', NULL, \(weight))"
        sqlite3_exec(db, sql, nil, nil, nil)

        sqlite3_close(db)
    }

    static func cleanup(path: String) {
        try? FileManager.default.removeItem(atPath: path)
    }

    // MARK: - SQL Builders

    private static func buildInsertSQL(for entry: OpStackRow) -> String {
        var columns = ["userId", "entryTimestamp", "operationType", "weight", "attempts"]
        var values = ["'\(entry.userId)'", "'\(entry.timestamp)'", "'\(entry.opType)'", "\(entry.weight)", "\(entry.attempts)"]

        if let bodyFat = entry.bodyFat {
            columns.append("bodyFat"); values.append("\(bodyFat)")
        }
        if let muscleMass = entry.muscleMass {
            columns.append("muscleMass"); values.append("\(muscleMass)")
        }
        if let water = entry.water {
            columns.append("water"); values.append("\(water)")
        }
        if let bmi = entry.bmi {
            columns.append("bmi"); values.append("\(bmi)")
        }
        if let source = entry.source {
            columns.append("source"); values.append("'\(source)'")
        }

        return "INSERT INTO opStack (\(columns.joined(separator: ", "))) VALUES (\(values.joined(separator: ", ")))"
    }

    private static func buildInsertSQL(for metric: OpStackMetricRow) -> String {
        var columns = ["userId", "entryTimestamp"]
        var values = ["'\(metric.userId)'", "'\(metric.timestamp)'"]

        if let bmr = metric.bmr {
            columns.append("bmr"); values.append("\(bmr)")
        }
        if let metabolicAge = metric.metabolicAge {
            columns.append("metabolicAge"); values.append("\(metabolicAge)")
        }
        if let proteinPercent = metric.proteinPercent {
            columns.append("proteinPercent"); values.append("\(proteinPercent)")
        }
        if let pulse = metric.pulse {
            columns.append("pulse"); values.append("\(pulse)")
        }
        if let skeletalMusclePercent = metric.skeletalMusclePercent {
            columns.append("skeletalMusclePercent"); values.append("\(skeletalMusclePercent)")
        }
        if let subcutaneousFatPercent = metric.subcutaneousFatPercent {
            columns.append("subcutaneousFatPercent"); values.append("\(subcutaneousFatPercent)")
        }
        if let visceralFatLevel = metric.visceralFatLevel {
            columns.append("visceralFatLevel"); values.append("\(visceralFatLevel)")
        }
        if let boneMass = metric.boneMass {
            columns.append("boneMass"); values.append("\(boneMass)")
        }

        return "INSERT INTO opStack_metric (\(columns.joined(separator: ", "))) VALUES (\(values.joined(separator: ", ")))"
    }
}
