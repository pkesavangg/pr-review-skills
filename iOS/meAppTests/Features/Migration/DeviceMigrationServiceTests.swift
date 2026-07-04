import Foundation
@testable import meApp
import Testing

/// Unit tests for `DeviceMigrationService` — the one-time Ionic→SwiftData scale migration.
///
/// The service exposes constructor override closures (`createScaleInLocal`, `syncAllScalesWithRemote`,
/// `getDeviceById`) plus an injectable `kvStorage`, so all branches are testable without touching
/// `DeviceService` or production SwiftData.
@MainActor
@Suite(.serialized)
struct DeviceMigrationServiceTests {

    /// Reference-type call recorder so `@MainActor` override closures can report back to the test.
    final class Recorder {
        var syncCalls = 0
        var createdDeviceIds: [String] = []
        var lookedUpIds: [String] = []
    }

    // MARK: - Helpers

    private func ionicScaleJSON(
        id: String,
        type: String,
        sku: String? = nil,
        isTemporary: Bool? = nil,
        isDeleted: Bool? = nil
    ) -> String {
        var fields = "\"id\":\"\(id)\",\"type\":\"\(type)\""
        if let sku { fields += ",\"sku\":\"\(sku)\"" }
        if let isTemporary { fields += ",\"isTemporary\":\(isTemporary)" }
        if let isDeleted { fields += ",\"isDeleted\":\(isDeleted)" }
        return "{\(fields)}"
    }

    private func makeSUT(
        kv: MockMigrationKvStorageService,
        recorder: Recorder,
        existingDeviceIds: Set<String> = [],
        createFailsForIds: Set<String> = []
    ) -> DeviceMigrationService {
        TestDependencyContainer.reset()
        return DeviceMigrationService(
            logger: MockLoggerService(),
            kvStorage: kv,
            createScaleInLocal: { device in
                if createFailsForIds.contains(device.id) {
                    throw MigrationTestError.scaleFailed
                }
                recorder.createdDeviceIds.append(device.id)
                return device
            },
            syncAllScalesWithRemote: {
                recorder.syncCalls += 1
            },
            getDeviceById: { id in
                recorder.lookedUpIds.append(id)
                guard existingDeviceIds.contains(id) else { return nil }
                return ScaleTestFixtures.makeDevice(id: id)
            }
        )
    }

    // MARK: - isMigrationNeeded

    @Test("isMigrationNeeded returns true when Ionic scale data exists for the account")
    func isMigrationNeededTrueWhenDataPresent() {
        let kv = MockMigrationKvStorageService()
        kv.seed("[]", forKey: MigrationKey.scaleKey(for: "acct-1"))
        let sut = makeSUT(kv: kv, recorder: Recorder())

        #expect(sut.isMigrationNeeded(for: "acct-1") == true)
    }

    @Test("isMigrationNeeded returns false when no Ionic scale data exists")
    func isMigrationNeededFalseWhenNoData() {
        let sut = makeSUT(kv: MockMigrationKvStorageService(), recorder: Recorder())

        #expect(sut.isMigrationNeeded(for: "acct-1") == false)
    }

    // MARK: - migrateScaleData

    @Test("migrateScaleData returns empty and skips sync when no stored data")
    func migrateScaleDataNoData() async throws {
        let recorder = Recorder()
        let sut = makeSUT(kv: MockMigrationKvStorageService(), recorder: recorder)

        let migrated = try await sut.migrateScaleData(for: "acct-1")

        #expect(migrated.isEmpty)
        // Still calls sync at the end even when nothing was migrated? No — early return before sync.
        #expect(recorder.syncCalls == 0)
    }

    @Test("migrateScaleData returns empty when stored JSON is malformed")
    func migrateScaleDataInvalidJSON() async throws {
        let kv = MockMigrationKvStorageService()
        kv.seed("not-json", forKey: MigrationKey.scaleKey(for: "acct-1"))
        let recorder = Recorder()
        let sut = makeSUT(kv: kv, recorder: recorder)

        let migrated = try await sut.migrateScaleData(for: "acct-1")

        #expect(migrated.isEmpty)
        #expect(recorder.createdDeviceIds.isEmpty)
    }

    @Test("migrateScaleData converts and creates all new scales, then syncs")
    func migrateScaleDataCreatesNewScales() async throws {
        let kv = MockMigrationKvStorageService()
        let json = "[\(ionicScaleJSON(id: "s1", type: "bluetooth")),\(ionicScaleJSON(id: "s2", type: "lcbt"))]"
        kv.seed(json, forKey: MigrationKey.scaleKey(for: "acct-1"))
        let recorder = Recorder()
        let sut = makeSUT(kv: kv, recorder: recorder)

        let migrated = try await sut.migrateScaleData(for: "acct-1")

        #expect(migrated.map(\.id).sorted() == ["s1", "s2"])
        #expect(recorder.createdDeviceIds.sorted() == ["s1", "s2"])
        #expect(recorder.syncCalls == 1)
    }

    @Test("migrateScaleData skips devices that already exist locally")
    func migrateScaleDataSkipsDuplicates() async throws {
        let kv = MockMigrationKvStorageService()
        let json = "[\(ionicScaleJSON(id: "s1", type: "bluetooth")),\(ionicScaleJSON(id: "s2", type: "bluetooth"))]"
        kv.seed(json, forKey: MigrationKey.scaleKey(for: "acct-1"))
        let recorder = Recorder()
        let sut = makeSUT(kv: kv, recorder: recorder, existingDeviceIds: ["s1"])

        let migrated = try await sut.migrateScaleData(for: "acct-1")

        #expect(migrated.map(\.id) == ["s2"])
        #expect(recorder.createdDeviceIds == ["s2"])
        #expect(recorder.syncCalls == 1)
    }

    @Test("migrateScaleData continues past a scale whose creation fails")
    func migrateScaleDataContinuesOnCreateFailure() async throws {
        let kv = MockMigrationKvStorageService()
        let json = "[\(ionicScaleJSON(id: "s1", type: "bluetooth")),\(ionicScaleJSON(id: "s2", type: "bluetooth"))]"
        kv.seed(json, forKey: MigrationKey.scaleKey(for: "acct-1"))
        let recorder = Recorder()
        let sut = makeSUT(kv: kv, recorder: recorder, createFailsForIds: ["s1"])

        let migrated = try await sut.migrateScaleData(for: "acct-1")

        #expect(migrated.map(\.id) == ["s2"])
        #expect(recorder.syncCalls == 1)
    }

    @Test("migrateScaleData maps scale type to protocol, source type, and body-comp support")
    func migrateScaleDataMapsScaleType() async throws {
        let kv = MockMigrationKvStorageService()
        let json = """
        [\(ionicScaleJSON(id: "r4", type: "btWifiR4")),\
        \(ionicScaleJSON(id: "a3", type: "bluetooth")),\
        \(ionicScaleJSON(id: "a6", type: "lcbt")),\
        \(ionicScaleJSON(id: "unknown", type: "mystery"))]
        """
        kv.seed(json, forKey: MigrationKey.scaleKey(for: "acct-1"))
        let sut = makeSUT(kv: kv, recorder: Recorder())

        let migrated = try await sut.migrateScaleData(for: "acct-1")
        let byId = Dictionary(uniqueKeysWithValues: migrated.map { ($0.id, $0) })

        #expect(byId["r4"]?.protocolType == "R4")
        #expect(byId["r4"]?.bathScale?.bodyComp == true)
        #expect(byId["a3"]?.protocolType == "A3")
        #expect(byId["a6"]?.protocolType == "A6")
        #expect(byId["unknown"]?.protocolType == nil)
        // Every migrated device is scoped to the requested account.
        #expect(migrated.allSatisfy { $0.accountId == "acct-1" })
    }

    @Test("migrateScaleData preserves the Ionic isDeleted flag on the converted device")
    func migrateScaleDataPreservesDeletedFlag() async throws {
        let kv = MockMigrationKvStorageService()
        let json = "[\(ionicScaleJSON(id: "s1", type: "bluetooth", isDeleted: true))]"
        kv.seed(json, forKey: MigrationKey.scaleKey(for: "acct-1"))
        let sut = makeSUT(kv: kv, recorder: Recorder())

        let migrated = try await sut.migrateScaleData(for: "acct-1")

        #expect(migrated.first?.isSoftDeleted == true)
    }

    @Test("migrateScaleData marks temporary Ionic scales as unsynced")
    func migrateScaleDataTemporaryScaleIsUnsynced() async throws {
        let kv = MockMigrationKvStorageService()
        let json = "[\(ionicScaleJSON(id: "s1", type: "bluetooth", isTemporary: true))]"
        kv.seed(json, forKey: MigrationKey.scaleKey(for: "acct-1"))
        let sut = makeSUT(kv: kv, recorder: Recorder())

        let migrated = try await sut.migrateScaleData(for: "acct-1")

        #expect(migrated.first?.isSynced == false)
        #expect(migrated.first?.hasServerID == false)
    }

    // MARK: - cleanupAfterMigration

    @Test("cleanupAfterMigration clears the Ionic scale key for the account")
    func cleanupAfterMigrationClearsScaleKey() {
        let kv = MockMigrationKvStorageService()
        let key = MigrationKey.scaleKey(for: "acct-1")
        kv.seed("[]", forKey: key)
        let sut = makeSUT(kv: kv, recorder: Recorder())

        sut.cleanupAfterMigration(for: "acct-1")

        #expect(kv.hasClearedValue(forKey: key))
        #expect(kv.contains(key: key) == false)
    }
}
