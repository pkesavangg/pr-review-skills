import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct ScaleMigrationServiceTests {
    @Test("isMigrationNeeded returns true when ionic scale payload exists")
    func isMigrationNeededReturnsTrueWhenPayloadExists() {
        let kv = MockKvStorageService()
        let accountId = "acct-1"
        kv.setValue("[]", forKey: MigrationKey.scaleKey(for: accountId))
        let sut = makeSUT(kv: kv)

        #expect(sut.isMigrationNeeded(for: accountId) == true)
    }

    @Test("isMigrationNeeded returns false when ionic scale payload does not exist")
    func isMigrationNeededReturnsFalseWhenPayloadMissing() {
        let kv = MockKvStorageService()
        let sut = makeSUT(kv: kv)

        #expect(sut.isMigrationNeeded(for: "acct-1") == false)
    }

    @Test("migrateScaleData returns empty when no ionic payload is stored")
    func migrateScaleDataNoPayloadReturnsEmpty() async throws {
        let harness = ScaleMigrationTestFixtures.Harness()
        let kv = MockKvStorageService()
        let sut = makeSUT(harness: harness, kv: kv)

        let migrated = try await sut.migrateScaleData(for: "acct-1")

        #expect(migrated.isEmpty)
        #expect(harness.createCalls == 0)
        #expect(harness.syncCalls == 0)
    }

    @Test("migrateScaleData returns empty when stored payload is not a string")
    func migrateScaleDataNonStringPayloadReturnsEmpty() async throws {
        let harness = ScaleMigrationTestFixtures.Harness()
        let kv = MockKvStorageService()
        let accountId = "acct-1"
        kv.setValue(123, forKey: MigrationKey.scaleKey(for: accountId))
        let sut = makeSUT(harness: harness, kv: kv)

        let migrated = try await sut.migrateScaleData(for: accountId)

        #expect(migrated.isEmpty)
        #expect(harness.createCalls == 0)
        #expect(harness.syncCalls == 0)
    }

    @Test("migrateScaleData returns empty when JSON decoding fails")
    func migrateScaleDataInvalidJSONReturnsEmpty() async throws {
        let harness = ScaleMigrationTestFixtures.Harness()
        let kv = MockKvStorageService()
        let accountId = "acct-1"
        kv.setValue("not-json", forKey: MigrationKey.scaleKey(for: accountId))
        let sut = makeSUT(harness: harness, kv: kv)

        let migrated = try await sut.migrateScaleData(for: accountId)

        #expect(migrated.isEmpty)
        #expect(harness.createCalls == 0)
        #expect(harness.syncCalls == 0)
    }

    @Test("migrateScaleData success maps R4 scale and preference fields")
    func migrateScaleDataSuccessMapsR4Fields() async throws {
        let harness = ScaleMigrationTestFixtures.Harness()
        let kv = MockKvStorageService()
        let accountId = "acct-1"
        let ionicScale = makeIonicScale(
            id: "r4-1",
            type: "btWifiR4",
            sku: "R4-001",
            latestVersion: "2.1.0",
            isTemporary: true,
            preference: makePreference(displayName: "Hallway Scale")
        )
        try store(scales: [ionicScale], for: accountId, in: kv)
        let sut = makeSUT(harness: harness, kv: kv)

        let migrated = try await sut.migrateScaleData(for: accountId)

        #expect(migrated.count == 1)
        #expect(harness.syncCalls == 1)

        let device = try #require(harness.created.first)
        #expect(device.id == "r4-1")
        #expect(device.accountId == accountId)
        #expect(device.bathScale?.scaleType == "btWifiR4")
        #expect(device.bathScale?.bodyComp == true)
        #expect(device.protocolType == "R4")
        #expect(device.metaData?.latestVersion == "2.1.0")
        #expect(device.r4ScalePreference?.displayName == "Hallway Scale")
        #expect(device.isSynced == false)
        #expect(device.hasServerID == false)
    }

    @Test("migrateScaleData maps bluetooth SKU body-comp support and protocol")
    func migrateScaleDataBluetoothSkuBodyCompSupport() async throws {
        let harness = ScaleMigrationTestFixtures.Harness()
        let kv = MockKvStorageService()
        let accountId = "acct-1"
        let ionicScale = makeIonicScale(
            id: "bt-1",
            type: "bluetooth",
            sku: "0412",
            latestVersion: nil,
            isTemporary: nil,
            preference: nil
        )
        try store(scales: [ionicScale], for: accountId, in: kv)
        let sut = makeSUT(harness: harness, kv: kv)

        let migrated = try await sut.migrateScaleData(for: accountId)

        #expect(migrated.count == 1)
        let device = try #require(harness.created.first)
        #expect(device.bathScale?.scaleType == "bluetooth")
        #expect(device.bathScale?.bodyComp == true)
        #expect(device.protocolType == "A3")
        #expect(device.metaData == nil)
        #expect(device.r4ScalePreference == nil)
        #expect(device.isSynced == true)
        #expect(device.hasServerID == true)
    }

    @Test("migrateScaleData maps lcbt and unknown type branches")
    func migrateScaleDataProtocolAndBodyCompBranchCoverage() async throws {
        let harness = ScaleMigrationTestFixtures.Harness()
        let kv = MockKvStorageService()
        let accountId = "acct-1"
        let lcbt = makeIonicScale(id: "a6-1", type: "lcbt", sku: "A6-001")
        let unknown = makeIonicScale(id: nil, type: "mystery", sku: "9999")
        try store(scales: [lcbt, unknown], for: accountId, in: kv)
        let sut = makeSUT(harness: harness, kv: kv)

        let migrated = try await sut.migrateScaleData(for: accountId)

        #expect(migrated.count == 2)
        #expect(harness.created.count == 2)

        let first = try #require(harness.created.first { $0.id == "a6-1" })
        #expect(first.protocolType == "A6")
        #expect(first.bathScale?.bodyComp == false)

        let unknownCreated = try #require(harness.created.first { $0.id != "a6-1" })
        #expect(unknownCreated.id.isEmpty == false)
        #expect(unknownCreated.protocolType == nil)
        #expect(unknownCreated.bathScale?.bodyComp == false)
    }

    @Test("migrateScaleData skips duplicates when target id already exists")
    func migrateScaleDataSkipsExistingDuplicate() async throws {
        let harness = ScaleMigrationTestFixtures.Harness()
        let existing = Device(id: "dup-1", accountId: "acct-1", sku: "R4-001")
        harness.existingById[existing.id] = existing

        let kv = MockKvStorageService()
        let accountId = "acct-1"
        let scales = [
            makeIonicScale(id: "dup-1", type: "btWifiR4", sku: "R4-001"),
            makeIonicScale(id: "new-1", type: "bluetooth", sku: "0412")
        ]
        try store(scales: scales, for: accountId, in: kv)
        let sut = makeSUT(harness: harness, kv: kv)

        let migrated = try await sut.migrateScaleData(for: accountId)

        #expect(migrated.map(\.id) == ["new-1"])
        #expect(harness.createCalls == 1)
        #expect(harness.syncCalls == 1)
    }

    @Test("migrateScaleData is safe to re-run and does not create duplicates")
    func migrateScaleDataRerunSafety() async throws {
        let harness = ScaleMigrationTestFixtures.Harness()
        let kv = MockKvStorageService()
        let accountId = "acct-1"
        let scales = [
            makeIonicScale(id: "repeat-1", type: "btWifiR4", sku: "R4-001"),
            makeIonicScale(id: "repeat-2", type: "bluetooth", sku: "0412")
        ]
        try store(scales: scales, for: accountId, in: kv)
        let sut = makeSUT(harness: harness, kv: kv)

        let firstRun = try await sut.migrateScaleData(for: accountId)
        let secondRun = try await sut.migrateScaleData(for: accountId)

        #expect(firstRun.count == 2)
        #expect(secondRun.isEmpty)
        #expect(harness.createCalls == 2)
        #expect(harness.syncCalls == 2)
        #expect(Set(harness.created.map(\.id)).count == 2)
    }

    @Test("migrateScaleData continues after create failures and returns only successful migrations")
    func migrateScaleDataContinuesAfterCreateFailure() async throws {
        let harness = ScaleMigrationTestFixtures.Harness()
        harness.failCreateIDs = ["fail-1"]

        let kv = MockKvStorageService()
        let accountId = "acct-1"
        let scales = [
            makeIonicScale(id: "fail-1", type: "btWifiR4", sku: "R4-001"),
            makeIonicScale(id: "ok-1", type: "bluetooth", sku: "0412")
        ]
        try store(scales: scales, for: accountId, in: kv)
        let sut = makeSUT(harness: harness, kv: kv)

        let migrated = try await sut.migrateScaleData(for: accountId)

        #expect(migrated.map(\.id) == ["ok-1"])
        #expect(harness.createCalls == 2)
        #expect(harness.created.count == 1)
        #expect(harness.syncCalls == 1)
    }

    @Test("migrateScaleData continues when existing-device lookup throws")
    func migrateScaleDataContinuesWhenDeviceLookupThrows() async throws {
        let harness = ScaleMigrationTestFixtures.Harness()
        harness.failLookupIDs = ["lookup-fail-1"]

        let kv = MockKvStorageService()
        let accountId = "acct-1"
        let scales = [
            makeIonicScale(id: "lookup-fail-1", type: "btWifiR4", sku: "R4-001"),
            makeIonicScale(id: "ok-2", type: "bluetooth", sku: "0412")
        ]
        try store(scales: scales, for: accountId, in: kv)
        let sut = makeSUT(harness: harness, kv: kv)

        let migrated = try await sut.migrateScaleData(for: accountId)

        #expect(migrated.map(\.id) == ["ok-2"])
        #expect(harness.createCalls == 1)
        #expect(harness.syncCalls == 1)
    }

    @Test("cleanupAfterMigration clears stored ionic payload")
    func cleanupAfterMigrationClearsStoredPayload() {
        let harness = ScaleMigrationTestFixtures.Harness()
        let kv = MockKvStorageService()
        let accountId = "acct-1"
        kv.setValue("[]", forKey: MigrationKey.scaleKey(for: accountId))
        let sut = makeSUT(harness: harness, kv: kv)

        sut.cleanupAfterMigration(for: accountId)

        #expect(kv.getValue(forKey: MigrationKey.scaleKey(for: accountId)) == nil)
    }

    private func makeSUT(
        harness: ScaleMigrationTestFixtures.Harness? = nil,
        kv: MockKvStorageService? = nil,
        logger: MockLoggerService? = nil
    ) -> ScaleMigrationService {
        let harness = harness ?? ScaleMigrationTestFixtures.Harness()
        let kv = kv ?? MockKvStorageService()
        let logger = logger ?? MockLoggerService()
        return ScaleMigrationService(
            logger: logger,
            kvStorage: kv,
            createScaleInLocal: { device in
                try await harness.create(device)
            },
            syncAllScalesWithRemote: {
                await harness.sync()
            },
            getDeviceById: { id in
                try await harness.fetch(id)
            }
        )
    }

    private func store(scales: [IonicScaleData], for accountId: String, in kv: MockKvStorageService) throws {
        let data = try JSONEncoder().encode(scales)
        let json = try #require(String(data: data, encoding: .utf8))
        kv.setValue(json, forKey: MigrationKey.scaleKey(for: accountId))
    }

    private func makeIonicScale(
        id: String?,
        type: String?,
        sku: String?,
        latestVersion: String? = nil,
        isTemporary: Bool? = false,
        preference: IonicR4ScalePreference? = nil
    ) -> IonicScaleData {
        IonicScaleData(
            id: id,
            nickname: "nick-\(id ?? "nil")",
            type: type,
            createdAt: "2026-03-06T00:00:00Z",
            userNumber: 3,
            scaleToken: "token-\(id ?? "nil")",
            mac: "AA:BB:CC:DD:EE:FF",
            broadcastId: 11259375,
            password: 1234,
            sku: sku,
            name: "Scale \(id ?? "nil")",
            peripheralIdentifier: "peripheral-\(id ?? "nil")",
            preference: preference,
            latestVersion: latestVersion,
            isDeleted: false,
            isTemporary: isTemporary
        )
    }

    private func makePreference(displayName: String) -> IonicR4ScalePreference {
        IonicR4ScalePreference(
            tzOffset: 330,
            timeFormat: "12",
            displayName: displayName,
            displayMetrics: ["weight", "bmi"],
            shouldMeasurePulse: true,
            shouldMeasureImpedance: true,
            shouldFactoryReset: false,
            wifiFotaScheduleTime: 120
        )
    }
}
