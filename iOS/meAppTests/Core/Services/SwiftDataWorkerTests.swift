import Foundation
@testable import meApp
import SwiftData
import Testing

@Suite(.serialized)
struct SwiftDataWorkerTests {

    private func makeContainer() throws -> ModelContainer {
        let schema = Schema([
            Account.self,
            Device.self,
            BathScale.self,
            Entry.self,
            R4ScalePreference.self,
            BathScaleMetric.self,
            DeviceMetaData.self,
            BathScaleEntry.self
        ])
        let config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        return try ModelContainer(for: schema, configurations: [config])
    }

    @MainActor
    private func insertEntry(
        into context: ModelContext,
        id: UUID = UUID(),
        accountId: String = "acct-1",
        timestamp: String,
        operationType: String = "create",
        weight: Int? = nil,
        bodyFat: Int? = nil,
        source: String? = nil,
        bmr: Int? = nil,
        unit: String? = nil,
        isSynced: Bool = false
    ) -> Entry {
        let entry = Entry(
            id: id,
            entryTimestamp: timestamp,
            accountId: accountId,
            operationType: operationType,
            isSynced: isSynced
        )
        if weight != nil || bodyFat != nil || source != nil {
            entry.scaleEntry = BathScaleEntry(weight: weight, bodyFat: bodyFat, source: source)
        }
        if bmr != nil || unit != nil {
            entry.scaleEntryMetric = BathScaleMetric(bmr: bmr, unit: unit)
        }
        context.insert(entry)
        try? context.save()
        return entry
    }

    @MainActor
    private func insertDevice(
        into context: ModelContext,
        id: String = "dev-1",
        accountId: String = "acct-1",
        nickname: String? = "My Scale",
        deviceType: String? = "scale",
        protocolType: String? = "A6",
        isConnected: Bool = true,
        isWifiConfigured: Bool = false,
        preference: R4ScalePreference? = nil
    ) -> Device {
        let device = Device(
            id: id,
            accountId: accountId,
            nickname: nickname,
            deviceType: deviceType,
            protocolType: protocolType,
            isConnected: isConnected,
            isWifiConfigured: isWifiConfigured,
            r4ScalePreference: preference
        )
        context.insert(device)
        try? context.save()
        return device
    }

    // MARK: - fetchProgressData

    @Test("fetchProgressData returns empty result for unknown account")
    func fetchProgressDataEmptyForUnknownAccount() async throws {
        let container = try makeContainer()
        let worker = SwiftDataWorker(modelContainer: container)

        let result = try await worker.fetchProgressData(accountId: "nonexistent")

        #expect(result.latestEntry == nil)
        #expect(result.weekEntries.isEmpty)
        #expect(result.monthEntries.isEmpty)
        #expect(result.allEntries.isEmpty)
        #expect(result.totalCount == 0)
    }

    @Test("fetchProgressData returns entries sorted by timestamp descending")
    @MainActor
    func fetchProgressDataSorted() async throws {
        let container = try makeContainer()
        let ctx = container.mainContext

        let now = Date()
        let fmt = ISO8601DateFormatter()
        let ts1 = fmt.string(from: now.addingTimeInterval(-3600))
        let ts2 = fmt.string(from: now.addingTimeInterval(-1800))
        let ts3 = fmt.string(from: now)

        insertEntry(into: ctx, accountId: "acct-1", timestamp: ts1, weight: 70000)
        insertEntry(into: ctx, accountId: "acct-1", timestamp: ts3, weight: 71000)
        insertEntry(into: ctx, accountId: "acct-1", timestamp: ts2, weight: 70500)

        let worker = SwiftDataWorker(modelContainer: container)
        let result = try await worker.fetchProgressData(accountId: "acct-1")

        #expect(result.totalCount == 3)
        #expect(result.allEntries.count == 3)
        #expect(result.latestEntry?.weight == 71000)
        #expect(result.firstEntry?.weight == 70000)
    }

    @Test("fetchProgressData filters by operationType create only")
    @MainActor
    func fetchProgressDataFiltersOperationType() async throws {
        let container = try makeContainer()
        let ctx = container.mainContext

        let now = ISO8601DateFormatter().string(from: Date())

        insertEntry(into: ctx, accountId: "acct-1", timestamp: now, operationType: "create", weight: 70000)
        insertEntry(into: ctx, accountId: "acct-1", timestamp: now, operationType: "delete", weight: 65000)

        let worker = SwiftDataWorker(modelContainer: container)
        let result = try await worker.fetchProgressData(accountId: "acct-1")

        #expect(result.totalCount == 1)
        #expect(result.latestEntry?.weight == 70000)
    }

    @Test("fetchProgressData isolates by accountId")
    @MainActor
    func fetchProgressDataIsolatesByAccount() async throws {
        let container = try makeContainer()
        let ctx = container.mainContext

        let now = ISO8601DateFormatter().string(from: Date())
        insertEntry(into: ctx, accountId: "acct-1", timestamp: now, weight: 70000)
        insertEntry(into: ctx, accountId: "acct-2", timestamp: now, weight: 80000)

        let worker = SwiftDataWorker(modelContainer: container)
        let result = try await worker.fetchProgressData(accountId: "acct-1")

        #expect(result.totalCount == 1)
        #expect(result.latestEntry?.weight == 70000)
    }

    @Test("fetchProgressData populates weekEntries and monthEntries from date ranges")
    @MainActor
    func fetchProgressDataDateRanges() async throws {
        let container = try makeContainer()
        let ctx = container.mainContext

        let now = Date()
        // Use the same fractional-seconds UTC formatter the worker uses, so the lexical
        // string comparison in fetchProgressData treats the boundary "today" entry as <= now.
        // A plain ISO8601DateFormatter() omits fractional seconds and sorts "…53Z" after
        // the worker's "…53.xxxZ" upper bound, dropping the newest entry from each window.
        let fmt = DateTimeTools.isoFormatter(useUTC: true)

        let today = fmt.string(from: now)
        let threeDaysAgo = fmt.string(from: now.addingTimeInterval(-3 * 86400))
        let fifteenDaysAgo = fmt.string(from: now.addingTimeInterval(-15 * 86400))
        let sixtyDaysAgo = fmt.string(from: now.addingTimeInterval(-60 * 86400))

        insertEntry(into: ctx, accountId: "acct-1", timestamp: today, weight: 71000)
        insertEntry(into: ctx, accountId: "acct-1", timestamp: threeDaysAgo, weight: 70500)
        insertEntry(into: ctx, accountId: "acct-1", timestamp: fifteenDaysAgo, weight: 70000)
        insertEntry(into: ctx, accountId: "acct-1", timestamp: sixtyDaysAgo, weight: 69000)

        let worker = SwiftDataWorker(modelContainer: container)
        let result = try await worker.fetchProgressData(accountId: "acct-1")

        #expect(result.allEntries.count == 4)
        #expect(result.weekEntries.count == 2)
        #expect(result.monthEntries.count == 3)
        #expect(result.weekStartEntry?.weight == 70500)
        #expect(result.monthStartEntry?.weight == 70000)
    }

    @Test("fetchProgressData extracts relationship data (scaleEntry + scaleEntryMetric)")
    @MainActor
    func fetchProgressDataExtractsRelationships() async throws {
        let container = try makeContainer()
        let ctx = container.mainContext

        let now = ISO8601DateFormatter().string(from: Date())
        insertEntry(
            into: ctx,
            accountId: "acct-1",
            timestamp: now,
            weight: 72000,
            bodyFat: 2200,
            source: "bluetooth",
            bmr: 1650,
            unit: "kg"
        )

        let worker = SwiftDataWorker(modelContainer: container)
        let result = try await worker.fetchProgressData(accountId: "acct-1")
        let entry = result.latestEntry

        #expect(entry?.weight == 72000)
        #expect(entry?.bodyFat == 2200)
        #expect(entry?.source == "bluetooth")
        #expect(entry?.bmr == 1650)
        #expect(entry?.unit == "kg")
    }

    // MARK: - fetchEntriesAsDTO

    @Test("fetchEntriesAsDTO returns mapped DTOs")
    @MainActor
    func fetchEntriesAsDTOReturnsDTO() async throws {
        let container = try makeContainer()
        let ctx = container.mainContext

        let now = ISO8601DateFormatter().string(from: Date())
        insertEntry(into: ctx, accountId: "acct-1", timestamp: now, operationType: "create", weight: 70000, unit: "lb")

        let worker = SwiftDataWorker(modelContainer: container)
        let dtos = try await worker.fetchEntriesAsDTO(accountId: "acct-1", operationType: "create")

        #expect(dtos.count == 1)
        #expect(dtos.first?.weight == 70000)
        #expect(dtos.first?.unit == "lb")
        #expect(dtos.first?.accountId == "acct-1")
    }

    @Test("fetchEntriesAsDTO returns empty for no matches")
    func fetchEntriesAsDTOEmpty() async throws {
        let container = try makeContainer()
        let worker = SwiftDataWorker(modelContainer: container)

        let dtos = try await worker.fetchEntriesAsDTO(accountId: "nobody", operationType: "create")

        #expect(dtos.isEmpty)
    }

    // MARK: - fetchEntryIdentifiers

    @Test("fetchEntryIdentifiers returns persistent IDs")
    @MainActor
    func fetchEntryIdentifiersReturnIDs() async throws {
        let container = try makeContainer()
        let ctx = container.mainContext

        let now = ISO8601DateFormatter().string(from: Date())
        insertEntry(into: ctx, accountId: "acct-1", timestamp: now, operationType: "create")
        insertEntry(into: ctx, accountId: "acct-1", timestamp: now, operationType: "create")

        let worker = SwiftDataWorker(modelContainer: container)
        let ids = try await worker.fetchEntryIdentifiers(accountId: "acct-1", operationType: "create")

        #expect(ids.count == 2)
    }

    @Test("fetchEntryIdentifiers filters by operationType")
    @MainActor
    func fetchEntryIdentifiersFilters() async throws {
        let container = try makeContainer()
        let ctx = container.mainContext

        let now = ISO8601DateFormatter().string(from: Date())
        insertEntry(into: ctx, accountId: "acct-1", timestamp: now, operationType: "create")
        insertEntry(into: ctx, accountId: "acct-1", timestamp: now, operationType: "delete")

        let worker = SwiftDataWorker(modelContainer: container)
        let ids = try await worker.fetchEntryIdentifiers(accountId: "acct-1", operationType: "delete")

        #expect(ids.count == 1)
    }

    // MARK: - fetchEntryData(byId:)

    @Test("fetchEntryData by UUID returns matching entry")
    @MainActor
    func fetchEntryDataByIdReturns() async throws {
        let container = try makeContainer()
        let ctx = container.mainContext
        let entryId = UUID()

        let now = ISO8601DateFormatter().string(from: Date())
        insertEntry(into: ctx, id: entryId, accountId: "acct-1", timestamp: now, weight: 68000)

        let worker = SwiftDataWorker(modelContainer: container)
        let data = try await worker.fetchEntryData(byId: entryId)

        #expect(data != nil)
        #expect(data?.entryId == entryId)
        #expect(data?.weight == 68000)
    }

    @Test("fetchEntryData by UUID returns nil when not found")
    func fetchEntryDataByIdReturnsNil() async throws {
        let container = try makeContainer()
        let worker = SwiftDataWorker(modelContainer: container)

        let data = try await worker.fetchEntryData(byId: UUID())

        #expect(data == nil)
    }

    // MARK: - fetchDeviceData

    @Test("fetchDeviceData returns device with extracted preference")
    @MainActor
    func fetchDeviceDataWithPreference() async throws {
        let container = try makeContainer()
        let ctx = container.mainContext

        let pref = R4ScalePreference(
            scaleId: "dev-1",
            displayName: "Living Room",
            displayMetrics: ["weight", "bodyFat"],
            shouldFactoryReset: false,
            shouldMeasureImpedance: true,
            shouldMeasurePulse: false,
            timeFormat: "12",
            tzOffset: 330,
            wifiFotaScheduleTime: 0,
            updatedAt: nil
        )
        insertDevice(
            into: ctx,
            id: "dev-1",
            nickname: "My Scale",
            protocolType: "R4",
            isConnected: true,
            isWifiConfigured: true,
            preference: pref
        )

        let worker = SwiftDataWorker(modelContainer: container)
        let data = try await worker.fetchDeviceData(deviceId: "dev-1")

        #expect(data != nil)
        #expect(data?.deviceId == "dev-1")
        #expect(data?.nickname == "My Scale")
        #expect(data?.protocolType == "R4")
        #expect(data?.isConnected == true)
        #expect(data?.isWifiConfigured == true)
        #expect(data?.displayName == "Living Room")
        #expect(data?.shouldMeasureImpedance == true)
        #expect(data?.shouldMeasurePulse == false)
        #expect(data?.displayMetrics == ["weight", "bodyFat"])
    }

    @Test("fetchDeviceData returns nil for unknown device")
    func fetchDeviceDataReturnsNil() async throws {
        let container = try makeContainer()
        let worker = SwiftDataWorker(modelContainer: container)

        let data = try await worker.fetchDeviceData(deviceId: "nonexistent")

        #expect(data == nil)
    }

    @Test("fetchDeviceData without preference returns defaults")
    @MainActor
    func fetchDeviceDataWithoutPreference() async throws {
        let container = try makeContainer()
        let ctx = container.mainContext

        insertDevice(into: ctx, id: "dev-no-pref", isConnected: false, isWifiConfigured: false)

        let worker = SwiftDataWorker(modelContainer: container)
        let data = try await worker.fetchDeviceData(deviceId: "dev-no-pref")

        #expect(data != nil)
        #expect(data?.preferenceId == nil)
        #expect(data?.displayName == nil)
        #expect(data?.shouldMeasureImpedance == false)
        #expect(data?.shouldMeasurePulse == false)
        #expect(data?.displayMetrics == [])
        #expect(data?.isSynced == false)
    }

    // MARK: - fetchDevicesData

    @Test("fetchDevicesData returns all devices for account")
    @MainActor
    func fetchDevicesDataReturnsAll() async throws {
        let container = try makeContainer()
        let ctx = container.mainContext

        insertDevice(into: ctx, id: "dev-a", accountId: "acct-1")
        insertDevice(into: ctx, id: "dev-b", accountId: "acct-1")
        insertDevice(into: ctx, id: "dev-c", accountId: "acct-2")

        let worker = SwiftDataWorker(modelContainer: container)
        let devices = try await worker.fetchDevicesData(accountId: "acct-1")

        #expect(devices.count == 2)
    }

    @Test("fetchDevicesData returns empty for unknown account")
    func fetchDevicesDataEmpty() async throws {
        let container = try makeContainer()
        let worker = SwiftDataWorker(modelContainer: container)

        let devices = try await worker.fetchDevicesData(accountId: "nobody")

        #expect(devices.isEmpty)
    }

    // MARK: - EntryData.toDTO

    @MainActor
    private func makeFullEntry() -> Entry {
        let entry = Entry(
            entryTimestamp: "2026-03-10T00:00:00Z",
            accountId: "acct-1",
            operationType: "create",
            serverTimestamp: "2026-03-10T00:00:01Z",
            isSynced: true
        )
        entry.scaleEntry = BathScaleEntry(
            weight: 72000,
            bodyFat: 2200,
            muscleMass: 4500,
            water: 5500,
            bmi: 2400,
            source: "bluetooth"
        )
        entry.scaleEntryMetric = BathScaleMetric(
            bmr: 1650,
            metabolicAge: 28,
            proteinPercent: 18,
            pulse: 72,
            skeletalMusclePercent: 35,
            subcutaneousFatPercent: 15,
            visceralFatLevel: 8,
            boneMass: 3200,
            impedance: 500,
            unit: "kg"
        )
        return entry
    }

    @Test("EntryData toDTO maps all fields correctly via round-trip")
    @MainActor
    func entryDataToDTOMapsFields() async throws {
        let container = try makeContainer()
        let ctx = container.mainContext

        let entry = makeFullEntry()
        ctx.insert(entry)
        try ctx.save()

        let worker = SwiftDataWorker(modelContainer: container)
        let fetched = try await worker.fetchEntryData(byId: entry.id)
        let dto = try #require(fetched?.toDTO())

        #expect(dto.accountId == "acct-1")
        #expect(dto.weight == 72000)
        #expect(dto.bodyFat == 2200)
        #expect(dto.muscleMass == 4500)
        #expect(dto.water == 5500)
        #expect(dto.bmi == 2400)
        #expect(dto.source == "bluetooth")
        #expect(dto.bmr == 1650)
        #expect(dto.metabolicAge == 28)
        #expect(dto.proteinPercent == 18)
        #expect(dto.pulse == 72)
        #expect(dto.skeletalMusclePercent == 35)
        #expect(dto.subcutaneousFatPercent == 15)
        #expect(dto.visceralFatLevel == 8)
        #expect(dto.boneMass == 3200)
        #expect(dto.impedance == 500)
        #expect(dto.unit == "kg")
        #expect(dto.entryTimestamp == "2026-03-10T00:00:00Z")
        #expect(dto.serverTimestamp == "2026-03-10T00:00:01Z")
        #expect(dto.operationType == "create")
    }

    @Test("EntryData toDTO handles nil optional fields")
    @MainActor
    func entryDataToDTOHandlesNils() async throws {
        let container = try makeContainer()
        let ctx = container.mainContext

        let entry = Entry(
            entryTimestamp: "2026-03-10T00:00:00Z",
            accountId: "acct-1",
            operationType: "create",
            isSynced: false
        )
        ctx.insert(entry)
        try ctx.save()

        let worker = SwiftDataWorker(modelContainer: container)
        let fetched = try await worker.fetchEntryData(byId: entry.id)
        let dto = try #require(fetched?.toDTO())

        #expect(dto.weight == nil)
        #expect(dto.bodyFat == nil)
        #expect(dto.bmr == nil)
        #expect(dto.unit == nil)
        #expect(dto.serverTimestamp == nil)
    }

    // MARK: - ProgressFetchResult computed properties

    @Test("ProgressFetchResult computed properties return correct entries")
    @MainActor
    func progressFetchResultComputedProperties() async throws {
        let container = try makeContainer()
        let ctx = container.mainContext

        let now = Date()
        let fmt = ISO8601DateFormatter()
        let tsOld = fmt.string(from: now.addingTimeInterval(-86400))
        let tsNew = fmt.string(from: now)

        insertEntry(into: ctx, accountId: "acct-1", timestamp: tsOld, weight: 69000)
        insertEntry(into: ctx, accountId: "acct-1", timestamp: tsNew, weight: 71000)

        let worker = SwiftDataWorker(modelContainer: container)
        let result = try await worker.fetchProgressData(accountId: "acct-1")

        #expect(result.firstEntry?.weight == 69000)
        #expect(result.weekStartEntry?.weight == 69000)
        #expect(result.monthStartEntry?.weight == 69000)
        #expect(result.latestEntry?.weight == 71000)
    }

    @Test("ProgressFetchResult empty collections return nil computed properties")
    func progressFetchResultEmptyReturnsNil() async throws {
        let container = try makeContainer()
        let worker = SwiftDataWorker(modelContainer: container)

        let result = try await worker.fetchProgressData(accountId: "nobody")

        #expect(result.firstEntry == nil)
        #expect(result.weekStartEntry == nil)
        #expect(result.monthStartEntry == nil)
    }
}
