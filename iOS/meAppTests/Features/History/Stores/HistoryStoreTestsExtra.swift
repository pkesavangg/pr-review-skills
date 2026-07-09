//
//  HistoryStoreTestsExtra.swift
//  meAppTests
//
//  Continuation of HistoryStoreTests: baby entry editing and the WG delete
//  optimistic + undo state machine. Split out to satisfy file/type length limits.
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct HistoryStoreBabyAndDeleteTests {

    // MARK: - updateBabyEntry

    private func makeBabyEntry(
        id: UUID = UUID(),
        entryTimestamp: String = "2026-03-27T10:00:00Z",
        weightLbs: Int = 8,
        weightOz: Double = 5.0,
        weightKg: Double = 3.969,
        weightLb: Double = 8.31,
        lengthInches: Double = 20.0,
        lengthCm: Double = 50.8
    ) -> BabyHistoryEntry {
        BabyHistoryEntry(
            id: id,
            entryTimestamp: entryTimestamp,
            weightLbs: weightLbs,
            weightOz: weightOz,
            weightKg: weightKg,
            weightLb: weightLb,
            lengthInches: lengthInches,
            lengthCm: lengthCm,
            percentile: 50,
            notes: nil,
            weightDisplay: "\(weightLbs) lbs \(weightOz) oz",
            lengthDisplay: "\(Int(lengthInches)) in"
        )
    }

    private func makeBabyStore(
        babyId: String = "baby-1",
        babyName: String = "Test Baby"
    ) -> BabyStoreBundle {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        let productTypeStore = MockProductTypeStore()
        productTypeStore.selectedItem = .baby(profile: BabyProfile(id: babyId, name: babyName))
        store.productTypeStore = productTypeStore
        return BabyStoreBundle(store: store, entryService: entryService, productTypeStore: productTypeStore)
    }

    @Test("updateBabyEntry metric: creates entry with converted decigrams/mm then deletes old")
    func updateBabyEntryMetricEditsCallsCreateThenDelete() async {
        let entryId = UUID()
        let old = makeBabyEntry(id: entryId)
        let bundle = makeBabyStore()
        let store = bundle.store
        let entryService = bundle.entryService

        await store.updateBabyEntry(
            old: old,
            note: "growing well",
            weightDecigrams: 3969,
            lengthMm: 508,
            entryTimestamp: "2026-03-28T10:00:00Z"
        )

        #expect(entryService.createBabyEntryCalls.count == 1)
        let call = entryService.createBabyEntryCalls[0]
        #expect(call.babyId == "baby-1")
        #expect(call.weight == 3969)
        #expect(call.length == 508)
        #expect(call.note == "growing well")
        #expect(call.entryTimestamp == "2026-03-28T10:00:00Z")
        #expect(entryService.deleteEntryByIdCalls == 1)
        #expect(entryService.deletedEntryIds.first == entryId)
    }

    @Test("updateBabyEntry imperial: passes caller-computed decigrams and mm unchanged")
    func updateBabyEntryImperialPassesValues() async {
        let entryId = UUID()
        let old = makeBabyEntry(id: entryId)
        let bundle = makeBabyStore()
        let store = bundle.store
        let entryService = bundle.entryService

        await store.updateBabyEntry(
            old: old,
            note: "",
            weightDecigrams: 4082,
            lengthMm: 533,
            entryTimestamp: "2026-04-01T08:00:00Z"
        )

        #expect(entryService.createBabyEntryCalls.count == 1)
        let call = entryService.createBabyEntryCalls[0]
        #expect(call.weight == 4082)
        #expect(call.length == 533)
        #expect(call.entryTimestamp == "2026-04-01T08:00:00Z")
        #expect(entryService.deleteEntryByIdCalls == 1)
    }

    @Test("updateBabyEntry date change: new timestamp forwarded to createBabyEntry")
    func updateBabyEntryDateChangeForwardsNewTimestamp() async {
        let old = makeBabyEntry(entryTimestamp: "2026-03-01T09:00:00Z")
        let bundle = makeBabyStore()
        let store = bundle.store
        let entryService = bundle.entryService

        await store.updateBabyEntry(
            old: old,
            note: "note",
            weightDecigrams: 3800,
            lengthMm: 490,
            entryTimestamp: "2026-03-15T09:00:00Z"
        )

        #expect(entryService.createBabyEntryCalls.first?.entryTimestamp == "2026-03-15T09:00:00Z")
        #expect(entryService.deleteEntryByIdCalls == 1)
    }

    @Test("updateBabyEntry pending profile: skips all service calls")
    func updateBabyEntryPendingProfileSkips() async {
        let old = makeBabyEntry()
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        let productTypeStore = MockProductTypeStore()
        productTypeStore.selectedItem = .baby(profile: BabyProfile(id: BabyProfile.pendingSelectionId, name: ""))
        store.productTypeStore = productTypeStore

        await store.updateBabyEntry(
            old: old,
            note: "note",
            weightDecigrams: 3969,
            lengthMm: 508,
            entryTimestamp: "2026-03-28T10:00:00Z"
        )

        #expect(entryService.createBabyEntryCalls.isEmpty)
        #expect(entryService.deleteEntryByIdCalls == 0)
    }

    @Test("updateBabyEntry non-baby product: skips all service calls")
    func updateBabyEntryNonBabyProductSkips() async {
        let old = makeBabyEntry()
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()

        await store.updateBabyEntry(
            old: old,
            note: "note",
            weightDecigrams: 3969,
            lengthMm: 508,
            entryTimestamp: "2026-03-28T10:00:00Z"
        )

        #expect(entryService.createBabyEntryCalls.isEmpty)
        #expect(entryService.deleteEntryByIdCalls == 0)
    }

    @Test("updateBabyEntry create failure: does not attempt delete, shows error toast")
    func updateBabyEntryCreateFailureSkipsDelete() async {
        let old = makeBabyEntry()
        let (store, entryService, notificationService, _, _) = makeHistoryStoreSUT()
        let productTypeStore = MockProductTypeStore()
        productTypeStore.selectedItem = .baby(profile: BabyProfile(id: "baby-1", name: "Test Baby"))
        store.productTypeStore = productTypeStore
        entryService.createBabyEntryError = HistoryStoreTestError.loadMonthsFailed

        await store.updateBabyEntry(
            old: old,
            note: "note",
            weightDecigrams: 3969,
            lengthMm: 508,
            entryTimestamp: "2026-03-28T10:00:00Z"
        )

        #expect(entryService.deleteEntryByIdCalls == 0)
        #expect(notificationService.showToastCalls >= 1)
    }

    // MARK: - WG Delete optimistic + 3-second undo state machine

    private func loadWGEntry(_ entry: EntrySnapshot, into store: HistoryStore, entryService: MockEntryService) async {
        let month = makeHistoryMonth(id: "2026-03")
        entryService.fetchEntrySnapshotsForMonthResult = .success([entry])
        store.setSelectedMonth(selectedMonth: month)
        await store.loadEntries(for: month)
    }

    @Test("confirmWGDelete: removes entry from list and shows undo toast")
    func confirmWGDeleteRemovesEntryAndShowsUndoToast() async {
        let (store, entryService, notificationService, _, _) = makeHistoryStoreSUT()
        let entry = EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-10T08:00:00Z")
        await loadWGEntry(entry, into: store, entryService: entryService)
        #expect(store.entries.count == 1)

        store.showDeleteEntryAlert(entry: entry)
        guard let deleteButton = notificationService.alertData?.buttons.first(where: { $0.type == .danger }) else {
            Issue.record("Expected delete alert button")
            return
        }
        deleteButton.action(nil)

        #expect(store.entries.isEmpty)
        #expect(notificationService.showToastCalls >= 1)
        #expect(notificationService.toastData?.message.contains(HistoryListStrings.readingDeleted) == true)
    }

    @Test("undoWGDelete: restores the entry and shows restore toast")
    func undoWGDeleteRestoresEntry() async {
        let (store, entryService, notificationService, _, _) = makeHistoryStoreSUT()
        let entry = EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-10T08:00:00Z")
        await loadWGEntry(entry, into: store, entryService: entryService)

        store.showDeleteEntryAlert(entry: entry)
        guard let deleteButton = notificationService.alertData?.buttons.first(where: { $0.type == .danger }) else {
            Issue.record("Expected delete alert button")
            return
        }
        deleteButton.action(nil)
        #expect(store.entries.isEmpty)

        store.undoWGDelete()

        #expect(store.entries.count == 1)
        #expect(store.entries.first?.entryTimestamp == entry.entryTimestamp)
        #expect(notificationService.toastData?.message == HistoryListStrings.readingRestored)
    }

    @Test("commitWGDelete: calls deleteEntry after toast dismiss")
    func commitWGDeleteCallsDeleteService() async {
        let (store, entryService, notificationService, _, _) = makeHistoryStoreSUT()
        let entry = EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-10T08:00:00Z")
        await loadWGEntry(entry, into: store, entryService: entryService)

        store.showDeleteEntryAlert(entry: entry)
        guard let deleteButton = notificationService.alertData?.buttons.first(where: { $0.type == .danger }) else {
            Issue.record("Expected delete alert button")
            return
        }
        deleteButton.action(nil)

        notificationService.toastData?.onDismiss?()
        let done = await waitUntil { entryService.deleteEntryByIdCalls == 1 }

        #expect(done == true)
        #expect(entryService.deletedEntryIds.first == entry.id)
    }

    @Test("deleteWGEntryInternal failure: shows error toast with retry button")
    func deleteWGEntryInternalFailureShowsErrorToast() async {
        let (store, entryService, notificationService, _, _) = makeHistoryStoreSUT()
        let entry = EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-10T08:00:00Z")
        await loadWGEntry(entry, into: store, entryService: entryService)
        entryService.deleteEntryByIdError = HistoryStoreTestError.deleteFailed

        store.showDeleteEntryAlert(entry: entry)
        guard let deleteButton = notificationService.alertData?.buttons.first(where: { $0.type == .danger }) else {
            Issue.record("Expected delete alert button")
            return
        }
        deleteButton.action(nil)

        notificationService.toastData?.onDismiss?()
        let done = await waitUntil { notificationService.showToastCalls >= 2 }

        #expect(done == true)
        #expect(notificationService.toastData?.isError == true)
        #expect(notificationService.toastData?.message == HistoryListStrings.couldntDelete)
    }

    // MARK: - Cursor Pagination (MOB-385)

    private func makePageEntry(timestamp: String) -> BathScaleOperationDTO {
        BathScaleOperationDTO(
            accountId: "acct-1",
            bmr: nil,
            bmi: nil,
            bodyFat: nil,
            boneMass: nil,
            entryTimestamp: timestamp,
            entryType: EntryType.scale.rawValue,
            impedance: nil,
            metabolicAge: nil,
            muscleMass: nil,
            operationType: "create",
            proteinPercent: nil,
            pulse: nil,
            serverTimestamp: nil,
            skeletalMusclePercent: nil,
            source: nil,
            subcutaneousFatPercent: nil,
            systolic: nil,
            diastolic: nil,
            meanArterial: nil,
            unit: "lb",
            visceralFatLevel: nil,
            water: nil,
            weight: 1700
        )
    }

    @Test("loadFirstPage: populates pagedEntries and hasMorePages from the first page")
    func loadFirstPagePopulates() async {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        entryService.fetchEntriesPageResults = [
            EntriesPage(entries: [makePageEntry(timestamp: "2026-03-02T08:00:00Z")], nextCursor: "2026-03-01T08:00:00Z", hasMore: true)
        ]

        await store.loadFirstPage()

        #expect(store.pagedEntries.count == 1)
        #expect(store.hasMorePages == true)
        #expect(store.isLoadingPage == false)
        #expect(entryService.fetchEntriesPageCalls == 1)
        #expect(entryService.lastFetchEntriesPageCursor == nil)
    }

    @Test("loadNextPage: appends the next page and forwards the cursor")
    func loadNextPageAppends() async {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        entryService.fetchEntriesPageResults = [
            EntriesPage(entries: [makePageEntry(timestamp: "2026-03-02T08:00:00Z")], nextCursor: "2026-03-01T08:00:00Z", hasMore: true),
            EntriesPage(entries: [makePageEntry(timestamp: "2026-03-01T08:00:00Z")], nextCursor: nil, hasMore: false)
        ]

        await store.loadFirstPage()
        await store.loadNextPage()

        #expect(store.pagedEntries.count == 2)
        #expect(store.hasMorePages == false)
        #expect(entryService.fetchEntriesPageCalls == 2)
        #expect(entryService.lastFetchEntriesPageCursor == "2026-03-01T08:00:00Z")
    }

    @Test("loadNextPage: no-ops once the server reports no more pages")
    func loadNextPageStopsWhenExhausted() async {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        entryService.fetchEntriesPageResults = [
            EntriesPage(entries: [makePageEntry(timestamp: "2026-03-02T08:00:00Z")], nextCursor: nil, hasMore: false)
        ]

        await store.loadFirstPage()
        #expect(entryService.fetchEntriesPageCalls == 1)
        #expect(store.hasMorePages == false)

        // Further calls should not hit the service since there are no more pages.
        await store.loadNextPage()
        #expect(entryService.fetchEntriesPageCalls == 1)
    }

    @Test("loadFirstPage: resets accumulated state before reloading")
    func loadFirstPageResets() async {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        entryService.fetchEntriesPageResults = [
            EntriesPage(entries: [makePageEntry(timestamp: "2026-03-02T08:00:00Z")], nextCursor: "c1", hasMore: true)
        ]
        await store.loadFirstPage()
        #expect(store.pagedEntries.count == 1)

        entryService.fetchEntriesPageResults = [
            EntriesPage(entries: [makePageEntry(timestamp: "2026-04-01T08:00:00Z")], nextCursor: nil, hasMore: false)
        ]
        await store.loadFirstPage()
        #expect(store.pagedEntries.count == 1)
        #expect(store.hasMorePages == false)
    }

    @Test("loadNextPage: on error clears hasMorePages and logs")
    func loadNextPageError() async {
        let (store, entryService, _, _, logger) = makeHistoryStoreSUT()
        entryService.fetchEntriesPageError = HistoryStoreTestError.loadMonthsFailed

        await store.loadFirstPage()

        #expect(store.pagedEntries.isEmpty)
        #expect(store.hasMorePages == false)
        #expect(store.isLoadingPage == false)
        #expect(logger.messages.contains { $0.contains("Failed to load entries page") })
    }

    // MARK: - updateWGEntry partial-failure

    @Test("updateWGEntry: delete-after-save failure keeps the replacement, logs a distinct duplicate warning, and surfaces an error toast")
    func updateWGEntryDeleteAfterSaveFailureLogsDuplicateAndShowsErrorToast() async {
        let (store, entryService, notificationService, accountService, logger) = makeHistoryStoreSUT()
        let account = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", email: "a@b.com", isActiveAccount: true)
        accountService.seedAccounts([account], active: account)

        let old = EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-10T08:00:00Z")
        // The replacement save succeeds, but deleting the original throws — so both the old
        // and the new reading now persist (the duplicate the P1 fix must make detectable).
        entryService.deleteEntryByIdError = HistoryStoreTestError.loadMonthsFailed

        await store.updateWGEntry(
            old: old,
            weight: 1800,
            bmi: nil,
            bodyFat: nil,
            muscleMass: nil,
            water: nil,
            note: "",
            entryTimestamp: "2026-03-10T08:00:00Z"
        )

        // Replacement persisted, delete attempted-and-failed.
        #expect(entryService.savedEntries.count == 1)
        #expect(entryService.deleteEntryByIdCalls == 1)
        // User is told saving failed rather than seeing a silent duplicate.
        #expect(notificationService.showToastCalls >= 1)
        // A distinct duplicate-created error is logged so support can reconcile it
        // (a blind retry would otherwise create a third copy).
        #expect(logger.messages.contains { $0.contains("delete-after-save failed") })
    }
}

@MainActor
private func waitUntil(timeoutIterations: Int = 200, condition: @escaping @MainActor () -> Bool) async -> Bool {
    for _ in 0..<timeoutIterations {
        if condition() { return true }
        await Task.yield()
    }
    return false
}
