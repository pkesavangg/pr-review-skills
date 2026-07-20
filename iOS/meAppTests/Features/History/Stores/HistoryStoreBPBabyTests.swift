//
//  HistoryStoreBPBabyTests.swift
//  meAppTests
//
//  Coverage for HistoryStore blood-pressure and baby flows: BP month/entry loading,
//  BP/baby optimistic delete + undo, BP edit (delete-old + create-new), baby day detail
//  loading, baby display formatting, and product-type / account-change reactions.
//

import Combine
import Foundation
@testable import meApp
import Testing

enum HistoryStoreBPBabyTestError: Error, Equatable {
    case bpLoadFailed
    case bpDeleteFailed
    case bpCreateFailed
    case babyLoadFailed
    case babyDeleteFailed
}

@Suite(.serialized)
@MainActor
struct HistoryStoreBPBabyTests {

    // MARK: - Setup helpers

    struct SUT {
        let store: HistoryStore
        let entryService: MockEntryService
        let notificationService: TestNotificationHelperService
        let accountService: MockAccountService
        let logger: MockLoggerService
        let productTypeStore: MockProductTypeStore
    }

    /// Builds a HistoryStore with the product-type store registered in DI *before* init so the
    /// store's init-time Combine subscriptions and its method bodies share the same instance.
    func makeSUT(
        selection: ProductSelection = .myWeight,
        measurementUnits: String? = nil
    ) -> SUT {
        TestDependencyContainer.reset()

        let entryService = MockEntryService()
        let notificationService = TestNotificationHelperService()
        let accountService = MockAccountService()
        let logger = MockLoggerService()
        let productTypeStore = MockProductTypeStore()
        productTypeStore.selectedItem = selection

        DependencyContainer.shared.register(entryService as EntryServiceProtocol)
        DependencyContainer.shared.register(notificationService as NotificationHelperServiceProtocol)
        DependencyContainer.shared.register(accountService as AccountServiceProtocol)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)
        DependencyContainer.shared.register(productTypeStore as ProductTypeStoreProtocol)

        if let measurementUnits {
            let account = AccountTestFixtures.makeAccountSnapshot(
                id: "acct-1", isActiveAccount: true, measurementUnits: measurementUnits
            )
            accountService.seedAccounts([account], active: account)
        }

        let store = HistoryStore()
        store.entryService = entryService
        store.notificationService = notificationService
        store.accountService = accountService
        store.logger = logger
        store.productTypeStore = productTypeStore

        return SUT(
            store: store,
            entryService: entryService,
            notificationService: notificationService,
            accountService: accountService,
            logger: logger,
            productTypeStore: productTypeStore
        )
    }

    func waitUntil(timeoutIterations: Int = 300, condition: @escaping @MainActor () -> Bool) async -> Bool {
        for _ in 0..<timeoutIterations {
            if condition() { return true }
            await Task.yield()
        }
        return false
    }

    private func makeBPEntry(
        id: UUID = UUID(),
        entryTimestamp: String = "2026-03-10T08:00:00Z",
        systolic: Int = 120,
        diastolic: Int = 80,
        pulse: Int = 72,
        notes: String? = nil
    ) -> BPHistoryEntry {
        BPHistoryEntry(
            id: id,
            entryTimestamp: entryTimestamp,
            systolic: systolic,
            diastolic: diastolic,
            pulse: pulse,
            notes: notes
        )
    }

    // MARK: - Blood Pressure: loadMonths / mapping

    @Test("isBloodPressureMode true and loadMonths maps BPM DTOs into monthly summaries")
    func bpLoadMonthsBuildsSummaries() async {
        let sut = makeSUT(selection: .myBloodPressure)
        #expect(sut.store.isBloodPressureMode == true)
        sut.entryService.fetchBpmEntriesResult = .success([
            EntryTestFixtures.makeBpmDTO(systolic: 120, diastolic: 80, pulse: 70, entryTimestamp: "2026-03-10T08:00:00Z"),
            EntryTestFixtures.makeBpmDTO(systolic: 130, diastolic: 90, pulse: 74, entryTimestamp: "2026-03-20T08:00:00Z"),
            EntryTestFixtures.makeBpmDTO(systolic: 110, diastolic: 70, pulse: 60, entryTimestamp: "2026-02-05T08:00:00Z")
        ])

        sut.store.loadMonths()
        let done = await waitUntil { sut.store.bpMonths.count == 2 }

        #expect(done == true)
        #expect(sut.store.isEmptyState == false)
        // Newest month first
        #expect(sut.store.bpMonths.first?.id ?? "" > (sut.store.bpMonths.last?.id ?? ""))
        let march = sut.store.bpMonths.first { $0.id.hasSuffix("-03") }
        #expect(march?.count == 2)
        #expect(march?.avgSystolic == 125)
    }

    @Test("BP loadMonths failure: bpMonths cleared, empty state true, error logged")
    func bpLoadMonthsFailure() async {
        let sut = makeSUT(selection: .myBloodPressure)
        sut.entryService.fetchBpmEntriesResult = .failure(HistoryStoreBPBabyTestError.bpLoadFailed)

        sut.store.loadMonths()
        let done = await waitUntil { sut.entryService.fetchBpmEntriesCalls >= 1 }

        #expect(done == true)
        #expect(sut.store.bpMonths.isEmpty)
        #expect(sut.store.isEmptyState == true)
        #expect(sut.logger.messages.contains { $0.contains("Failed to load BP history") })
    }

    @Test("BP loadMonths cache hit: second call uses cached empty state, no refetch")
    func bpLoadMonthsCacheHit() async {
        let sut = makeSUT(selection: .myBloodPressure)
        sut.entryService.fetchBpmEntriesResult = .success([])
        sut.store.loadMonths()
        _ = await waitUntil { sut.entryService.fetchBpmEntriesCalls == 1 }

        sut.store.loadMonths()
        await Task.yield()
        #expect(sut.entryService.fetchBpmEntriesCalls == 1)
        #expect(sut.store.isEmptyState == true)
    }

    // MARK: - Blood Pressure: selectBPMonth / mapping entries

    @Test("selectBPMonth populates bpEntries filtered to the month, sorted newest first")
    func selectBPMonthLoadsEntries() async {
        let sut = makeSUT(selection: .myBloodPressure)
        sut.entryService.fetchBpmEntriesResult = .success([
            EntryTestFixtures.makeBpmDTO(systolic: 120, diastolic: 80, entryTimestamp: "2026-03-05T08:00:00Z"),
            EntryTestFixtures.makeBpmDTO(systolic: 118, diastolic: 78, entryTimestamp: "2026-03-25T08:00:00Z"),
            EntryTestFixtures.makeBpmDTO(systolic: 100, diastolic: 60, entryTimestamp: "2026-02-01T08:00:00Z")
        ])
        sut.store.loadMonths()
        let loaded = await waitUntil { !sut.store.bpMonths.isEmpty }
        #expect(loaded == true)
        guard let march = sut.store.bpMonths.first(where: { $0.id.hasSuffix("-03") }) else {
            Issue.record("Expected a March BP month")
            return
        }

        sut.store.selectBPMonth(march)
        let done = await waitUntil { sut.store.bpEntries.count == 2 }

        #expect(done == true)
        #expect(sut.store.selectedBPMonth?.id == march.id)
        #expect(sut.store.bpEntries.first?.entryTimestamp == "2026-03-25T08:00:00Z")
    }

    @Test("selectBPMonth failure: bpEntries cleared and error logged")
    func selectBPMonthFailure() async {
        let sut = makeSUT(selection: .myBloodPressure)
        let month = BPHistoryMonth(id: "2026-03", count: 1, avgSystolic: 120, avgDiastolic: 80, avgPulse: 70, month: "03", year: "2026")
        sut.entryService.fetchBpmEntriesResult = .failure(HistoryStoreBPBabyTestError.bpLoadFailed)

        sut.store.selectBPMonth(month)
        let done = await waitUntil { sut.entryService.fetchBpmEntriesCalls >= 1 }

        #expect(done == true)
        #expect(sut.store.bpEntries.isEmpty)
        #expect(sut.logger.messages.contains { $0.contains("Failed to load BP entries") })
    }

    @Test("resetSelectedBPMonth clears selection and entries")
    func resetSelectedBPMonthClears() async {
        let sut = makeSUT(selection: .myBloodPressure)
        let month = BPHistoryMonth(id: "2026-03", count: 0, avgSystolic: 0, avgDiastolic: 0, avgPulse: 0, month: "03", year: "2026")
        sut.entryService.fetchBpmEntriesResult = .success([])
        sut.store.selectBPMonth(month)
        _ = await waitUntil { sut.store.selectedBPMonth != nil }

        sut.store.resetSelectedBPMonth()
        #expect(sut.store.selectedBPMonth == nil)
        #expect(sut.store.bpEntries.isEmpty)
    }

    // MARK: - Blood Pressure: optimistic delete + undo

    @Test("showDeleteBPEntryAlert presents a confirmation alert")
    func showDeleteBPEntryAlertPresents() {
        let sut = makeSUT(selection: .myBloodPressure)
        sut.store.showDeleteBPEntryAlert(entry: makeBPEntry())
        #expect(sut.notificationService.showAlertCalls == 1)
        #expect(sut.notificationService.alertData?.title == AlertStrings.DeleteEntryAlert.title)
        #expect(sut.notificationService.alertData?.buttons.count == 2)
    }

    @Test("showDeleteBPEntryAlert cancel dismisses the alert without deleting")
    func showDeleteBPEntryAlertCancel() {
        let sut = makeSUT(selection: .myBloodPressure)
        sut.store.showDeleteBPEntryAlert(entry: makeBPEntry())
        guard let cancel = sut.notificationService.alertData?.buttons.first(where: { $0.type == .secondary }) else {
            Issue.record("Expected cancel button")
            return
        }
        cancel.action(nil)
        #expect(sut.entryService.deleteBpmEntryCalls == 0)
    }

    @Test("confirm BP delete removes entry optimistically and shows an undo toast")
    func confirmBPDeleteShowsUndoToast() async {
        let sut = makeSUT(selection: .myBloodPressure)
        let entry = makeBPEntry(entryTimestamp: "2026-03-10T08:00:00Z")
        // Seed bpEntries via selectBPMonth so the optimistic removal has something to remove.
        sut.entryService.fetchBpmEntriesResult = .success([
            EntryTestFixtures.makeBpmDTO(entryTimestamp: "2026-03-10T08:00:00Z")
        ])
        let month = BPHistoryMonth(id: "2026-03", count: 1, avgSystolic: 120, avgDiastolic: 80, avgPulse: 72, month: "03", year: "2026")
        sut.store.selectBPMonth(month)
        _ = await waitUntil { !sut.store.bpEntries.isEmpty }
        let seeded = sut.store.bpEntries.first ?? entry

        sut.store.showDeleteBPEntryAlert(entry: seeded)
        guard let danger = sut.notificationService.alertData?.buttons.first(where: { $0.type == .danger }) else {
            Issue.record("Expected delete button")
            return
        }
        danger.action(nil)

        #expect(sut.store.bpEntries.contains { $0.id == seeded.id } == false)
        #expect(sut.notificationService.showToastCalls >= 1)
        #expect(sut.notificationService.toastData?.message.contains(HistoryListStrings.readingDeleted) == true)
    }

    @Test("undo BP delete restores the entry and shows a restore toast")
    func undoBPDeleteRestores() async {
        let sut = makeSUT(selection: .myBloodPressure)
        sut.entryService.fetchBpmEntriesResult = .success([
            EntryTestFixtures.makeBpmDTO(entryTimestamp: "2026-03-10T08:00:00Z")
        ])
        let month = BPHistoryMonth(id: "2026-03", count: 1, avgSystolic: 120, avgDiastolic: 80, avgPulse: 72, month: "03", year: "2026")
        sut.store.selectBPMonth(month)
        _ = await waitUntil { !sut.store.bpEntries.isEmpty }
        let seeded = sut.store.bpEntries[0]

        sut.store.showDeleteBPEntryAlert(entry: seeded)
        sut.notificationService.alertData?.buttons.first { $0.type == .danger }?.action(nil)
        #expect(sut.store.bpEntries.isEmpty)

        // Trigger undo through the toast's action button closure.
        sut.notificationService.toastData?.onClick()
        let done = await waitUntil { sut.store.bpEntries.count == 1 }
        #expect(done == true)
        #expect(sut.store.bpEntries.first?.id == seeded.id)
        #expect(sut.notificationService.toastData?.message.contains(HistoryListStrings.readingRestored) == true)
        // Commit must not have run after an undo.
        #expect(sut.entryService.deleteBpmEntryCalls == 0)
    }

    @Test("commit BP delete on toast dismiss calls deleteBpmEntry")
    func commitBPDeleteCallsService() async {
        let sut = makeSUT(selection: .myBloodPressure)
        let entry = makeBPEntry(entryTimestamp: "2026-03-10T08:00:00Z")
        sut.store.showDeleteBPEntryAlert(entry: entry)
        sut.notificationService.alertData?.buttons.first { $0.type == .danger }?.action(nil)

        sut.notificationService.toastData?.onDismiss?()
        let done = await waitUntil { sut.entryService.deleteBpmEntryCalls == 1 }
        #expect(done == true)
        #expect(sut.entryService.lastDeletedBpmTimestamp == "2026-03-10T08:00:00Z")
    }

    @Test("BP delete failure shows an error toast with a retry button")
    func deleteBPEntryFailureShowsRetryToast() async {
        let sut = makeSUT(selection: .myBloodPressure)
        let entry = makeBPEntry(entryTimestamp: "2026-03-10T08:00:00Z")
        sut.entryService.deleteBpmEntryError = HistoryStoreBPBabyTestError.bpDeleteFailed

        sut.store.showDeleteBPEntryAlert(entry: entry)
        sut.notificationService.alertData?.buttons.first { $0.type == .danger }?.action(nil)
        sut.notificationService.toastData?.onDismiss?()

        let done = await waitUntil { sut.notificationService.toastData?.isError == true }
        #expect(done == true)
        #expect(sut.notificationService.toastData?.message == HistoryListStrings.couldntDelete)
        // The retry button re-runs the optimistic confirm flow.
        sut.notificationService.toastData?.onClick()
        let restored = await waitUntil { sut.notificationService.toastData?.message.contains(HistoryListStrings.readingDeleted) == true }
        #expect(restored == true)
    }

    // MARK: - Blood Pressure: edit (delete-old + create-new)

    @Test("updateBPEntry success: deletes old then creates new entry")
    func updateBPEntrySuccess() async {
        let sut = makeSUT(selection: .myBloodPressure)
        let old = makeBPEntry(entryTimestamp: "2026-03-10T08:00:00Z")

        await sut.store.updateBPEntry(
            old: old, systolic: 122, diastolic: 82, pulse: 70, note: "after run", entryTimestamp: "2026-03-11T09:00:00Z"
        )

        #expect(sut.entryService.deleteBpmEntryCalls == 1)
        #expect(sut.entryService.lastDeletedBpmTimestamp == "2026-03-10T08:00:00Z")
        #expect(sut.entryService.createBpmEntryCalls == 1)
        #expect(sut.entryService.lastCreatedBpmDTO?.systolic == 122)
        #expect(sut.entryService.lastCreatedBpmDTO?.entryTimestamp == "2026-03-11T09:00:00Z")
        #expect(sut.notificationService.dismissLoaderCalls >= 1)
    }

    @Test("updateBPEntry create-after-delete failure: logs entry-lost and shows error toast")
    func updateBPEntryCreateFails() async {
        let sut = makeSUT(selection: .myBloodPressure)
        sut.entryService.createBpmEntryError = HistoryStoreBPBabyTestError.bpCreateFailed
        let old = makeBPEntry()

        await sut.store.updateBPEntry(
            old: old, systolic: 120, diastolic: 80, pulse: 70, note: "", entryTimestamp: "2026-03-11T09:00:00Z"
        )

        #expect(sut.entryService.deleteBpmEntryCalls == 1)
        #expect(sut.entryService.createBpmEntryCalls == 1)
        #expect(sut.notificationService.toastData?.message == ToastStrings.errorSavingEntry)
        #expect(sut.logger.messages.contains { $0.contains("BP entry create failed after delete") })
    }

    @Test("updateBPEntry delete failure: skips create and shows error toast")
    func updateBPEntryDeleteFails() async {
        let sut = makeSUT(selection: .myBloodPressure)
        sut.entryService.deleteBpmEntryError = HistoryStoreBPBabyTestError.bpDeleteFailed
        let old = makeBPEntry()

        await sut.store.updateBPEntry(
            old: old, systolic: 120, diastolic: 80, pulse: 70, note: "n", entryTimestamp: "2026-03-11T09:00:00Z"
        )

        #expect(sut.entryService.deleteBpmEntryCalls == 1)
        #expect(sut.entryService.createBpmEntryCalls == 0)
        #expect(sut.notificationService.toastData?.message == ToastStrings.errorSavingEntry)
    }

    // MARK: - Baby: loadMonths error + selectBabyDay

    /// Birthday is fixed just before the fixed 2026-03-10 entry timestamps used across these
    /// tests, so real entry days sort ahead of the injected birthday placeholder (the newest
    /// day stays a real day for `.first`-based assertions).
    func babyProfile(id: String = "baby-1") -> BabyProfile {
        BabyProfile(
            id: id,
            name: "Mia",
            birthday: DateTimeTools.parse("2026-03-01T12:00:00Z"),
            biologicalSex: "female",
            birthLengthInches: 19.5,
            birthWeightLbs: 7.5,
            birthWeightOz: 3.0
        )
    }

    @Test("baby loadMonths failure: babyWeeks cleared, empty state true, error logged")
    func babyLoadMonthsFailure() async {
        let sut = makeSUT(selection: .baby(profile: babyProfile()))
        #expect(sut.store.isBabyMode == true)
        sut.entryService.fetchAllEntrySnapshotsResult = .failure(HistoryStoreBPBabyTestError.babyLoadFailed)

        sut.store.loadMonths()
        let done = await waitUntil { sut.entryService.fetchAllEntrySnapshotsCalls >= 1 }

        #expect(done == true)
        #expect(sut.store.babyWeeks.isEmpty)
        #expect(sut.store.isEmptyState == true)
        #expect(sut.logger.messages.contains { $0.contains("Failed to load baby history") })
    }

    @Test("selectBabyDay loads and maps the day's baby entries (metric units)")
    func selectBabyDayLoadsEntriesMetric() async {
        let profile = babyProfile()
        let sut = makeSUT(selection: .baby(profile: profile), measurementUnits: MeasurementUnits.metric.rawValue)
        let entries = [
            EntryTestFixtures.makeBabyEntrySnapshot(entryTimestamp: "2026-03-10T08:00:00Z", babyId: profile.id, weight: 5200, length: 520),
            EntryTestFixtures.makeBabyEntrySnapshot(entryTimestamp: "2026-03-10T14:00:00Z", babyId: profile.id, weight: 5300, length: 525)
        ]
        sut.entryService.fetchAllEntrySnapshotsResult = .success(entries)

        sut.store.loadMonths()
        let weeksLoaded = await waitUntil { !sut.store.babyWeeks.isEmpty }
        #expect(weeksLoaded == true)
        guard let day = sut.store.babyWeeks.first?.days.first else {
            Issue.record("Expected at least one baby day")
            return
        }

        sut.store.selectBabyDay(day)
        let done = await waitUntil { !sut.store.babyEntries.isEmpty }

        #expect(done == true)
        #expect(sut.store.selectedBabyDay?.id == day.id)
        #expect(sut.store.babyEntries.count == 2)
        // Metric display formatting was applied.
        #expect(sut.store.babyEntries.first?.weightDisplay.contains(HistoryListStrings.kg) == true)
    }

    @Test("selectBabyDay imperial lb-oz formats with lbs and oz")
    func selectBabyDayImperialLbOz() async {
        let profile = babyProfile()
        let sut = makeSUT(selection: .baby(profile: profile), measurementUnits: MeasurementUnits.imperialLbOz.rawValue)
        sut.entryService.fetchAllEntrySnapshotsResult = .success([
            EntryTestFixtures.makeBabyEntrySnapshot(entryTimestamp: "2026-03-10T08:00:00Z", babyId: profile.id, weight: 5200, length: 520)
        ])
        sut.store.loadMonths()
        _ = await waitUntil { !sut.store.babyWeeks.isEmpty }
        guard let day = sut.store.babyWeeks.first?.days.first else {
            Issue.record("Expected baby day")
            return
        }
        sut.store.selectBabyDay(day)
        let done = await waitUntil { !sut.store.babyEntries.isEmpty }
        #expect(done == true)
        #expect(sut.store.babyEntries.first?.weightDisplay.contains(HistoryListStrings.lb) == true)
    }

    @Test("loadMonths imperial lb-decimal formats baby weight in decimal pounds")
    func babyLoadMonthsImperialLbDecimal() async {
        let profile = babyProfile()
        let sut = makeSUT(selection: .baby(profile: profile), measurementUnits: MeasurementUnits.imperialLbDecimal.rawValue)
        sut.entryService.fetchAllEntrySnapshotsResult = .success([
            EntryTestFixtures.makeBabyEntrySnapshot(entryTimestamp: "2026-03-10T08:00:00Z", babyId: profile.id, weight: 5200, length: 520)
        ])
        sut.store.loadMonths()
        let done = await waitUntil { !sut.store.babyWeeks.isEmpty }
        #expect(done == true)
        let display = sut.store.babyWeeks.first?.days.first?.weightDisplay ?? ""
        #expect(display.contains(HistoryListStrings.lb))
    }

    @Test("selectBabyDay failure: babyEntries cleared and error logged")
    func selectBabyDayFailure() async {
        let profile = babyProfile()
        let sut = makeSUT(selection: .baby(profile: profile))
        sut.entryService.fetchAllEntrySnapshotsResult = .failure(HistoryStoreBPBabyTestError.babyLoadFailed)
        let day = BabyHistoryDay(
            id: "2026-03-10",
            entryCount: 1,
            weightLbs: 8,
            weightOz: 0,
            weightKg: 3.6,
            weightLb: 8,
            lengthInches: 20,
            lengthCm: 50,
            percentile: 50,
            weightDisplay: "",
            lengthDisplay: ""
        )

        sut.store.selectBabyDay(day)
        let done = await waitUntil { sut.entryService.fetchAllEntrySnapshotsCalls >= 1 }
        #expect(done == true)
        #expect(sut.store.babyEntries.isEmpty)
        #expect(sut.logger.messages.contains { $0.contains("Failed to load baby entries for day") })
    }

    @Test("resetSelectedBabyDay clears selection and entries")
    func resetSelectedBabyDayClears() async {
        let profile = babyProfile()
        let sut = makeSUT(selection: .baby(profile: profile))
        sut.entryService.fetchAllEntrySnapshotsResult = .success([
            EntryTestFixtures.makeBabyEntrySnapshot(entryTimestamp: "2026-03-10T08:00:00Z", babyId: profile.id)
        ])
        sut.store.loadMonths()
        _ = await waitUntil { !sut.store.babyWeeks.isEmpty }
        if let day = sut.store.babyWeeks.first?.days.first {
            sut.store.selectBabyDay(day)
            _ = await waitUntil { sut.store.selectedBabyDay != nil }
        }

        sut.store.resetSelectedBabyDay()
        #expect(sut.store.selectedBabyDay == nil)
        #expect(sut.store.babyEntries.isEmpty)
    }

}
