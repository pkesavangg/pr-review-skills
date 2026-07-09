import Foundation
@testable import meApp
import SQLite3
import Testing

// MARK: - SQLiteMigrationServiceTests

@Suite(.serialized)
@MainActor
struct SQLiteMigrationServiceTests {

    // MARK: - isMigrationNeeded

    @Test("isMigrationNeeded returns true when database file exists")
    func isMigrationNeeded_databaseExists_returnsTrue() {
        let bundle = makeSUT()
        let sut = bundle.sut
        let dbPath = bundle.dbPath
        SQLiteTestHelper.createEmptyDatabase(at: dbPath)
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        #expect(sut.isMigrationNeeded() == true)
    }

    @Test("isMigrationNeeded returns false when database file does not exist")
    func isMigrationNeeded_noDatabaseFile_returnsFalse() {
        let bundle = makeSUT()
        let sut = bundle.sut
        #expect(sut.isMigrationNeeded() == false)
    }

    // MARK: - migrateAllUsersEntryData: Success

    @Test("migrateAllUsersEntryData migrates single user entries successfully")
    func migrateAllUsersEntryData_singleUser_migratesEntries() async throws {
        let bundle = makeSUT()
        let sut = bundle.sut
        let worker = bundle.worker
        let dbPath = bundle.dbPath
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [
            .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1800),
            .init(userId: "user-A", timestamp: "2026-01-02T08:00:00Z", opType: "create", weight: 1750)
        ])
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        let result = try await sut.migrateAllUsersEntryData()

        #expect(result["user-A"] == 2)
        #expect(worker.insertedRows.count == 2)
    }

    @Test("migrateAllUsersEntryData migrates entries for multiple users")
    func migrateAllUsersEntryData_multipleUsers_migratesAllEntries() async throws {
        let bundle = makeSUT()
        let sut = bundle.sut
        let worker = bundle.worker
        let dbPath = bundle.dbPath
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [
            .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1800),
            .init(userId: "user-B", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1600),
            .init(userId: "user-B", timestamp: "2026-01-02T08:00:00Z", opType: "create", weight: 1620)
        ])
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        let result = try await sut.migrateAllUsersEntryData()

        #expect(result["user-A"] == 1)
        #expect(result["user-B"] == 2)
        #expect(worker.insertedRows.count == 3)
    }

    @Test("migrateAllUsersEntryData preserves weight and scale data in saved entries")
    func migrateAllUsersEntryData_withScaleData_preservesValues() async throws {
        let bundle = makeSUT()
        let sut = bundle.sut
        let worker = bundle.worker
        let dbPath = bundle.dbPath
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [
            .init(
                userId: "user-A",
                timestamp: "2026-01-01T08:00:00Z",
                opType: "create",
                weight: 1800,
                bodyFat: 250,
                muscleMass: 820,
                water: 540,
                bmi: 230,
                source: "manual"
            )
        ])
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        _ = try await sut.migrateAllUsersEntryData()

        let saved = worker.insertedRows.last
        #expect(saved?.scaleEntry?.weight == 1800)
        #expect(saved?.scaleEntry?.bodyFat == 250)
        #expect(saved?.scaleEntry?.muscleMass == 820)
        #expect(saved?.scaleEntry?.water == 540)
        #expect(saved?.scaleEntry?.bmi == 230)
        #expect(saved?.scaleEntry?.source == "manual")
    }

    @Test("migrateAllUsersEntryData preserves metric data from opStack_metric join")
    func migrateAllUsersEntryData_withMetrics_preservesValues() async throws {
        let bundle = makeSUT()
        let sut = bundle.sut
        let worker = bundle.worker
        let dbPath = bundle.dbPath
        SQLiteTestHelper.createDatabase(
            at: dbPath,
            entries: [
                .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1800)
            ],
            metrics: [
                .init(
                    userId: "user-A",
                    timestamp: "2026-01-01T08:00:00Z",
                    bmr: 1600,
                    metabolicAge: 35,
                    proteinPercent: 190,
                    pulse: 72,
                    skeletalMusclePercent: 410,
                    subcutaneousFatPercent: 210,
                    visceralFatLevel: 11,
                    boneMass: 80
                )
            ]
        )
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        _ = try await sut.migrateAllUsersEntryData()

        let saved = worker.insertedRows.last
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
        let bundle = makeSUT()
        let sut = bundle.sut
        let worker = bundle.worker
        let dbPath = bundle.dbPath
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [
            .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "delete", weight: 0)
        ])
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        _ = try await sut.migrateAllUsersEntryData()

        let saved = worker.insertedRows.last
        #expect(saved != nil)
        #expect(saved?.scaleEntry == nil)
        #expect(saved?.operationType == "delete")
    }

    @Test("migrateAllUsersEntryData creates entry without metric when no bmr or metabolicAge")
    func migrateAllUsersEntryData_noMetrics_noScaleEntryMetric() async throws {
        let bundle = makeSUT()
        let sut = bundle.sut
        let worker = bundle.worker
        let dbPath = bundle.dbPath
        SQLiteTestHelper.createDatabase(
            at: dbPath,
            entries: [
                .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1800)
            ],
            metrics: [] // No matching metric row
        )
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        _ = try await sut.migrateAllUsersEntryData()

        #expect(worker.insertedRows.last?.scaleEntryMetric == nil)
    }

    @Test("migrateAllUsersEntryData handles NULL values in optional scale fields")
    func migrateAllUsersEntryData_nullOptionalFields_handlesGracefully() async throws {
        let bundle = makeSUT()
        let sut = bundle.sut
        let worker = bundle.worker
        let dbPath = bundle.dbPath
        // Entry with weight but all other scale fields NULL
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [
            .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1800)
        ])
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        _ = try await sut.migrateAllUsersEntryData()

        let saved = worker.insertedRows.last
        #expect(saved?.scaleEntry?.weight == 1800)
        #expect(saved?.scaleEntry?.bodyFat == nil)
        #expect(saved?.scaleEntry?.muscleMass == nil)
        #expect(saved?.scaleEntry?.water == nil)
        #expect(saved?.scaleEntry?.bmi == nil)
        #expect(saved?.scaleEntry?.source == nil)
    }

    @Test("migrateAllUsersEntryData sets entries as unsynced with scale device type")
    func migrateAllUsersEntryData_entryProperties_correctDefaults() async throws {
        let bundle = makeSUT()
        let sut = bundle.sut
        let worker = bundle.worker
        let dbPath = bundle.dbPath
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [
            .init(userId: "user-A", timestamp: "2026-03-01T08:00:00Z", opType: "create", weight: 1800)
        ])
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        _ = try await sut.migrateAllUsersEntryData()

        let saved = worker.insertedRows.last
        #expect(saved?.accountId == "user-A")
        #expect(saved?.entryTimestamp == "2026-03-01T08:00:00Z")
        #expect(saved?.operationType == "create")
        #expect(saved?.entryType == "scale")
        #expect(saved?.isSynced == false)
        #expect(saved?.serverTimestamp == nil)
    }

    @Test("migrateAllUsersEntryData defaults operationType to create when NULL")
    func migrateAllUsersEntryData_nullOpType_defaultsToCreate() async throws {
        let bundle = makeSUT()
        let sut = bundle.sut
        let worker = bundle.worker
        let dbPath = bundle.dbPath
        SQLiteTestHelper.createDatabaseWithNullOpType(at: dbPath, userId: "user-A", timestamp: "2026-01-01T08:00:00Z", weight: 1800)
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        _ = try await sut.migrateAllUsersEntryData()

        #expect(worker.insertedRows.last?.operationType == "create")
    }

    // MARK: - migrateAllUsersEntryData: Empty/No Data

    @Test("migrateAllUsersEntryData returns empty dict when no opStack tables exist")
    func migrateAllUsersEntryData_noTables_returnsEmpty() async throws {
        let bundle = makeSUT()
        let sut = bundle.sut
        let worker = bundle.worker
        let dbPath = bundle.dbPath
        SQLiteTestHelper.createEmptyDatabase(at: dbPath)
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        let result = try await sut.migrateAllUsersEntryData()

        #expect(result.isEmpty)
        #expect(worker.insertedRows.isEmpty)
    }

    @Test("migrateAllUsersEntryData returns empty dict when opStack table has no rows")
    func migrateAllUsersEntryData_emptyTable_returnsEmpty() async throws {
        let bundle = makeSUT()
        let sut = bundle.sut
        let worker = bundle.worker
        let dbPath = bundle.dbPath
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [])
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        let result = try await sut.migrateAllUsersEntryData()

        #expect(result.isEmpty)
        #expect(worker.insertedRows.isEmpty)
    }

    // MARK: - migrateAllUsersEntryData: Error Handling

    @Test("migrateAllUsersEntryData throws databaseConnectionFailed when file doesn't exist")
    func migrateAllUsersEntryData_noDatabase_throwsConnectionError() async {
        let bundle = makeSUT()
        let sut = bundle.sut
        do {
            _ = try await sut.migrateAllUsersEntryData()
            Issue.record("Expected migrateAllUsersEntryData to throw")
        } catch {
            #expect(error is MigrationError)
            #expect((error as? MigrationError) == .databaseConnectionFailed)
        }
    }

    @Test("migrateAllUsersEntryData continues with remaining users when one user's batch fails")
    func migrateAllUsersEntryData_partialSaveFailure_continuesWithRemaining() async throws {
        let failingWorker = MockEntryWorker()
        failingWorker.insertEntriesFailAccountIds = ["user-A"]
        let bundle = makeSUT(entryWorker: failingWorker)
        let sut = bundle.sut
        let dbPath = bundle.dbPath
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [
            .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1800),
            .init(userId: "user-B", timestamp: "2026-01-02T08:00:00Z", opType: "create", weight: 1750),
            .init(userId: "user-B", timestamp: "2026-01-03T08:00:00Z", opType: "create", weight: 1700)
        ])
        defer { SQLiteTestHelper.cleanup(path: dbPath) }

        let result = try await sut.migrateAllUsersEntryData()

        // user-A's batch fails; user-B's still migrates
        #expect(result["user-A"] == nil)
        #expect(result["user-B"] == 2)
        #expect(failingWorker.insertedRows.count == 2)
    }

    @Test("migrateAllUsersEntryData returns empty dict when all saves fail")
    func migrateAllUsersEntryData_allSavesFail_returnsEmptyDict() async throws {
        let failingWorker = MockEntryWorker()
        failingWorker.insertEntriesError = SQLiteMigrationTestError.saveFailed
        let bundle = makeSUT(entryWorker: failingWorker)
        let sut = bundle.sut
        let dbPath = bundle.dbPath
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
        let bundle = makeSUT()
        let sut = bundle.sut
        let dbPath = bundle.dbPath
        SQLiteTestHelper.createEmptyDatabase(at: dbPath)
        #expect(FileManager.default.fileExists(atPath: dbPath) == true)

        try sut.cleanupAfterMigration()

        #expect(FileManager.default.fileExists(atPath: dbPath) == false)
    }

    @Test("cleanupAfterMigration does nothing when file doesn't exist")
    func cleanupAfterMigration_noFile_noOp() throws {
        let bundle = makeSUT()
        let sut = bundle.sut
        // Should complete without error
        try sut.cleanupAfterMigration()
    }

    // MARK: - Full Migration Flow

    @Test("isMigrationNeeded returns false after cleanup")
    func isMigrationNeeded_afterCleanup_returnsFalse() throws {
        let bundle = makeSUT()
        let sut = bundle.sut
        let dbPath = bundle.dbPath
        SQLiteTestHelper.createEmptyDatabase(at: dbPath)
        #expect(sut.isMigrationNeeded() == true)

        try sut.cleanupAfterMigration()

        #expect(sut.isMigrationNeeded() == false)
    }

    @Test("full migration flow: check needed, migrate, cleanup")
    func fullFlow_migrateAndCleanup_worksEndToEnd() async throws {
        let bundle = makeSUT()
        let sut = bundle.sut
        let worker = bundle.worker
        let dbPath = bundle.dbPath
        SQLiteTestHelper.createDatabase(at: dbPath, entries: [
            .init(userId: "user-A", timestamp: "2026-01-01T08:00:00Z", opType: "create", weight: 1800)
        ])

        // Step 1: Check migration needed
        #expect(sut.isMigrationNeeded() == true)

        // Step 2: Migrate
        let result = try await sut.migrateAllUsersEntryData()
        #expect(result["user-A"] == 1)
        #expect(worker.insertedRows.count == 1)

        // Step 3: Cleanup
        try sut.cleanupAfterMigration()
        #expect(sut.isMigrationNeeded() == false)
    }

    // MARK: - Metric Partial Data

    @Test("migrateAllUsersEntryData creates metric when only bmr exists")
    func migrateAllUsersEntryData_onlyBmr_createsMetric() async throws {
        let bundle = makeSUT()
        let sut = bundle.sut
        let worker = bundle.worker
        let dbPath = bundle.dbPath
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

        let metric = worker.insertedRows.last?.scaleEntryMetric
        #expect(metric != nil)
        #expect(metric?.bmr == 1600)
        #expect(metric?.metabolicAge == nil)
    }

    @Test("migrateAllUsersEntryData creates metric when only metabolicAge exists")
    func migrateAllUsersEntryData_onlyMetabolicAge_createsMetric() async throws {
        let bundle = makeSUT()
        let sut = bundle.sut
        let worker = bundle.worker
        let dbPath = bundle.dbPath
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

        let metric = worker.insertedRows.last?.scaleEntryMetric
        #expect(metric != nil)
        #expect(metric?.bmr == nil)
        #expect(metric?.metabolicAge == 42)
    }

    // MARK: - makeSUT

    @MainActor
    private func makeSUT(
        entryWorker: MockEntryWorker? = nil
    ) -> SQLiteMigrationSUT {
        TestDependencyContainer.reset()

        let dbPath = NSTemporaryDirectory() + "test_migration_\(UUID().uuidString).db"
        let worker = entryWorker ?? MockEntryWorker()
        let sut = SQLiteMigrationService(databasePathOverride: dbPath, entryWorker: worker)

        return SQLiteMigrationSUT(sut: sut, worker: worker, dbPath: dbPath)
    }
}

// MARK: - Test SUT Bundle

private struct SQLiteMigrationSUT {
    let sut: SQLiteMigrationService
    let worker: MockEntryWorker
    let dbPath: String
}

// MARK: - Test Errors

private enum SQLiteMigrationTestError: Error, Equatable {
    case saveFailed
}
