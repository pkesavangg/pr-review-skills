//
//  HistoryStoreTests.swift
//  meAppTests
//
//  Unit tests for HistoryStore: initial state, data loading, filtering/sorting,
//  state updates from user actions, empty/loading/error states, dependency interactions.
//

import Foundation
@testable import meApp
import Testing

enum HistoryStoreTestError: Error, Equatable {
    case loadMonthsFailed
    case loadMonthDetailFailed
    case deleteFailed
    case exportFailed
}

// MARK: - Fixtures

func makeHistoryMonth(id: String = "2026-03", weight: Double = 150, count: Int = 3) -> HistoryMonth {
    HistoryMonth(
        id: id,
        weight: weight,
        entryTimestamp: id,
        count: count,
        weights: nil,
        change: nil,
        bodyFat: nil,
        muscleMass: nil,
        water: nil,
        bmi: nil,
        date: nil,
        time: nil,
        month: "03",
        year: "2026",
        min: nil,
        max: nil
    )
}

struct BabyStoreBundle {
    let store: HistoryStore
    let entryService: MockEntryService
    let productTypeStore: MockProductTypeStore
}

@MainActor
func makeHistoryStoreSUT() -> ( // swiftlint:disable:this large_tuple
    HistoryStore,
    MockEntryService,
    TestNotificationHelperService,
    MockAccountService,
    MockLoggerService
) {
    TestDependencyContainer.reset()

    let entryService = MockEntryService()
    let notificationService = TestNotificationHelperService()
    let accountService = MockAccountService()
    let logger = MockLoggerService()

    DependencyContainer.shared.register(entryService as EntryServiceProtocol)
    DependencyContainer.shared.register(notificationService as NotificationHelperServiceProtocol)
    DependencyContainer.shared.register(accountService as AccountServiceProtocol)
    DependencyContainer.shared.register(logger as LoggerServiceProtocol)

    let store = HistoryStore()
    store.entryService = entryService
    store.notificationService = notificationService
    store.accountService = accountService
    store.logger = logger

    return (store, entryService, notificationService, accountService, logger)
}

@MainActor
func waitUntilHistoryStore(timeoutIterations: Int = 200, condition: @escaping @MainActor () -> Bool) async -> Bool {
    for _ in 0..<timeoutIterations {
        if condition() { return true }
        await Task.yield()
    }
    return false
}

// MARK: - Suite

@Suite(.serialized)
@MainActor
struct HistoryStoreTests {

    // MARK: - Initial state

    @Test("initial state: months and entries empty, no selected month or metric, not empty state")
    func initialState() {
        let (store, _, _, _, _) = makeHistoryStoreSUT()

        #expect(store.months.isEmpty)
        #expect(store.entries.isEmpty)
        #expect(store.selectedMonth == nil)
        #expect(store.selectedMetric == nil)
        #expect(store.isEmptyState == false)
        #expect(store.expandedEntries.isEmpty)
    }

    // MARK: - loadMonths

    @Test("loadMonths success empty: months and isEmptyState updated, loader shown then dismissed")
    func loadMonthsSuccessEmpty() async {
        let (store, entryService, notificationService, _, _) = makeHistoryStoreSUT()
        entryService.getMonthsAllResult = .success([])

        store.loadMonths()
        let done = await waitUntilHistoryStore { entryService.getMonthsAllCalls == 1 }

        #expect(done == true)
        #expect(store.months.isEmpty)
        #expect(store.isEmptyState == true)
        #expect(notificationService.showLoaderCalls >= 1)
        #expect(notificationService.dismissLoaderCalls >= 1)
    }

    @Test("loadMonths success with data: months populated, isEmptyState false")
    func loadMonthsSuccessWithData() async {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        let months = [makeHistoryMonth(id: "2026-03"), makeHistoryMonth(id: "2026-02", weight: 148)]
        entryService.getMonthsAllResult = .success(months)

        store.loadMonths()
        let done = await waitUntilHistoryStore { store.months.count == 2 }

        #expect(done == true)
        #expect(store.months.count == 2)
        #expect(store.months[0].id == "2026-03")
        #expect(store.isEmptyState == false)
    }

    @Test("loadMonths failure: months cleared, isEmptyState true, error logged")
    func loadMonthsFailure() async {
        let (store, entryService, notificationService, _, logger) = makeHistoryStoreSUT()
        entryService.getMonthsAllResult = .failure(HistoryStoreTestError.loadMonthsFailed)

        store.loadMonths()
        let done = await waitUntilHistoryStore { entryService.getMonthsAllCalls == 1 }

        #expect(done == true)
        #expect(store.months.isEmpty)
        #expect(store.isEmptyState == true)
        #expect(notificationService.dismissLoaderCalls >= 1)
        #expect(logger.messages.contains { $0.contains("HistoryStore") && $0.contains("Failed to load history months") })
    }

    @Test("loadMonths only runs once: second call does not refetch")
    func loadMonthsRunsOnce() async {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        entryService.getMonthsAllResult = .success([makeHistoryMonth()])

        store.loadMonths()
        _ = await waitUntilHistoryStore { store.months.count == 1 }
        let firstCalls = entryService.getMonthsAllCalls

        store.loadMonths()
        await Task.yield()
        store.loadMonths()
        await Task.yield()

        #expect(entryService.getMonthsAllCalls == firstCalls)
    }

    // MARK: - selectMonth / loadEntries

    @Test("selectMonth sets selectedMonth and triggers loadEntries")
    func selectMonthTriggersLoadEntries() async {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        let month = makeHistoryMonth(id: "2026-03")
        entryService.fetchEntrySnapshotsForMonthResult = .success([EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-01T08:00:00Z")])

        store.selectMonth(month)
        let done = await waitUntilHistoryStore { store.selectedMonth?.id == "2026-03" && entryService.fetchEntrySnapshotsForMonthCalls == 1 }

        #expect(done == true)
        #expect(store.selectedMonth?.id == "2026-03")
        #expect(entryService.fetchEntrySnapshotsForMonthLast == "2026-03")
        #expect(store.entries.count == 1)
    }

    @Test("loadEntries with nil selectedMonth does nothing")
    func loadEntriesNoSelectedMonth() async {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        entryService.fetchEntrySnapshotsForMonthResult = .success([])

        await store.loadEntries(for: nil)
        #expect(entryService.fetchEntrySnapshotsForMonthCalls == 0)
        #expect(store.entries.isEmpty)
    }

    @Test("loadEntries success empty: entries cleared")
    func loadEntriesSuccessEmpty() async {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        let month = makeHistoryMonth()
        entryService.fetchEntrySnapshotsForMonthResult = .success([])
        store.setSelectedMonth(selectedMonth: month)

        await store.loadEntries(for: month)
        #expect(store.entries.isEmpty)
        #expect(entryService.fetchEntrySnapshotsForMonthCalls == 1)
    }

    @Test("loadEntries failure: entries cleared, error logged")
    func loadEntriesFailure() async {
        let (store, entryService, _, _, logger) = makeHistoryStoreSUT()
        let month = makeHistoryMonth()
        entryService.fetchEntrySnapshotsForMonthResult = .failure(HistoryStoreTestError.loadMonthDetailFailed)
        store.setSelectedMonth(selectedMonth: month)

        await store.loadEntries(for: month)
        #expect(store.entries.isEmpty)
        #expect(entryService.fetchEntrySnapshotsForMonthCalls == 1)
        #expect(logger.messages.contains { $0.contains("HistoryStore") })
    }

    @Test("loadEntries dedupes by entry identity, keeps the latest op by serverTimestamp, and drops entries whose latest op is a delete")
    func loadEntriesDedupesAndFilters() async {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        let month = makeHistoryMonth(id: "2026-03")

        // entry-1: two create ops for the same server entry — keep the latest by serverTimestamp ("c").
        let entry1Old = EntryTestFixtures.makeEntrySnapshot(
            entryTimestamp: "2026-03-01T08:00:00Z", serverTimestamp: "a", serverEntryId: "entry-1", operationType: .create
        )
        let entry1New = EntryTestFixtures.makeEntrySnapshot(
            entryTimestamp: "2026-03-01T08:00:00Z", serverTimestamp: "c", serverEntryId: "entry-1", operationType: .create
        )
        // entry-2: latest op is a delete — the whole entry is filtered out.
        let entry2Create = EntryTestFixtures.makeEntrySnapshot(
            entryTimestamp: "2026-03-02T08:00:00Z", serverTimestamp: "a", serverEntryId: "entry-2", operationType: .create
        )
        let entry2Delete = EntryTestFixtures.makeEntrySnapshot(
            entryTimestamp: "2026-03-02T08:00:00Z", serverTimestamp: "b", serverEntryId: "entry-2", operationType: .delete
        )
        // entry-3: a distinct entry that shares entry-1's entryTimestamp — must NOT collapse into entry-1
        // (dedup keys on entry identity, not entryTimestamp).
        let entry3 = EntryTestFixtures.makeEntrySnapshot(
            entryTimestamp: "2026-03-01T08:00:00Z", serverTimestamp: "a", serverEntryId: "entry-3", operationType: .create
        )
        entryService.fetchEntrySnapshotsForMonthResult = .success([entry1Old, entry1New, entry2Create, entry2Delete, entry3])
        store.setSelectedMonth(selectedMonth: month)

        await store.loadEntries(for: month)

        // entry-1 (latest create) and entry-3 survive; entry-2 (latest delete) is dropped.
        #expect(store.entries.count == 2)
        let entry1 = store.entries.first { $0.serverEntryId == "entry-1" }
        #expect(entry1?.serverTimestamp == "c")
        #expect(entry1?.operationType == OperationType.create.rawValue)
        #expect(store.entries.contains { $0.serverEntryId == "entry-3" })
        #expect(!store.entries.contains { $0.serverEntryId == "entry-2" })
    }

    @Test("loadEntries sorts newest first by entryTimestamp")
    func loadEntriesSortsNewestFirst() async {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        let month = makeHistoryMonth(id: "2026-03")
        let older = EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-01T08:00:00Z")
        let newer = EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-15T12:00:00Z")
        entryService.fetchEntrySnapshotsForMonthResult = .success([older, newer])
        store.setSelectedMonth(selectedMonth: month)

        await store.loadEntries(for: month)
        #expect(store.entries.count == 2)
        #expect(store.entries[0].entryTimestamp == "2026-03-15T12:00:00Z")
        #expect(store.entries[1].entryTimestamp == "2026-03-01T08:00:00Z")
    }

    @Test("loadMonths sorts baby weeks newest first with highest week number at the top")
    func loadMonthsSortsBabyWeeksNewestFirst() async {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        let babyProfile = BabyProfile(
            id: "baby-history-1",
            name: "Mia",
            birthday: Calendar.current.date(byAdding: .day, value: -21, to: Date()),
            biologicalSex: "female",
            birthLengthInches: 19.5,
            birthWeightLbs: 7.5,
            birthWeightOz: 3.0
        )
        let productTypeStore = MockProductTypeStore()
        productTypeStore.selectedItem = .baby(profile: babyProfile)
        store.productTypeStore = productTypeStore

        let startDate = Calendar.current.date(byAdding: .day, value: -13, to: Date())! // swiftlint:disable:this force_unwrapping
        let entries = (0..<14).compactMap { offset -> EntrySnapshot? in
            guard let date = Calendar.current.date(byAdding: .day, value: offset, to: startDate) else { return nil }
            let timestamp = ISO8601DateFormatter().string(from: date)
            return EntryTestFixtures.makeBabyEntrySnapshot(
                accountId: "acct-baby",
                entryTimestamp: timestamp,
                babyId: babyProfile.id,
                weight: 120,
                length: 20
            )
        }
        entryService.fetchAllEntrySnapshotsResult = .success(entries)

        store.loadMonths()
        let done = await waitUntilHistoryStore { !store.babyWeeks.isEmpty }

        #expect(done == true)
        #expect(store.babyWeeks.count >= 2)
        #expect((store.babyWeeks.first?.weekNumber ?? 0) > (store.babyWeeks.last?.weekNumber ?? 0))
        #expect(store.babyWeeks.first?.id == "week-\(store.babyWeeks.first?.weekNumber ?? 0)")
    }

    // MARK: - setSelectedMonth / resetSelectedMonth

    @Test("setSelectedMonth sets selectedMonth and clears entries")
    func setSelectedMonthClearsEntries() {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        entryService.fetchEntrySnapshotsForMonthResult = .success([EntryTestFixtures.makeEntrySnapshot()])
        let month = makeHistoryMonth()
        store.selectMonth(month)
        _ = Task { await store.loadEntries(for: month) }

        store.setSelectedMonth(selectedMonth: makeHistoryMonth(id: "2026-02"))
        #expect(store.selectedMonth?.id == "2026-02")
        #expect(store.entries.isEmpty)
    }

    @Test("resetSelectedMonth clears selectedMonth and entries")
    func resetSelectedMonthClearsState() {
        let (store, _, _, _, _) = makeHistoryStoreSUT()
        store.setSelectedMonth(selectedMonth: makeHistoryMonth())
        #expect(store.selectedMonth != nil)
        store.resetSelectedMonth()
        #expect(store.selectedMonth == nil)
        #expect(store.entries.isEmpty)
    }

    // MARK: - selectMetric

    @Test("selectMetric sets selectedMetric")
    func selectMetricSetsMetric() {
        let (store, _, _, _, _) = makeHistoryStoreSUT()
        #expect(store.selectedMetric == nil)
        store.selectMetric(.bmi)
        #expect(store.selectedMetric == .bmi)
        store.selectMetric(.bodyFat)
        #expect(store.selectedMetric == .bodyFat)
    }

    // MARK: - refreshAllEntries

    @Test("refreshAllEntries calls refreshAccount sync and reloads months and entries")
    func refreshAllEntriesCallsDependencies() async {
        let (store, entryService, _, accountService, _) = makeHistoryStoreSUT()
        let account = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", email: "a@b.com", isActiveAccount: true)
        accountService.seedAccounts([account], active: account)
        accountService.refreshAccountResult = .success(())
        entryService.getMonthsAllResult = .success([makeHistoryMonth()])
        entryService.fetchEntrySnapshotsForMonthResult = .success([])
        store.setSelectedMonth(selectedMonth: makeHistoryMonth())

        await store.refreshAllEntries()
        #expect(accountService.refreshAccountCalls == 1)
        #expect(entryService.syncAllEntriesWithRemoteCalls == 1)
        #expect(entryService.getMonthsAllCalls >= 1)
        #expect(entryService.fetchEntrySnapshotsForMonthCalls >= 1)
    }

    // MARK: - showDeleteEntryAlert

    @Test("showDeleteEntryAlert presents alert with correct strings")
    func showDeleteEntryAlertPresentsAlert() {
        let (store, _, notificationService, _, _) = makeHistoryStoreSUT()
        let entry = EntryTestFixtures.makeEntrySnapshot()
        store.showDeleteEntryAlert(entry: entry)
        #expect(notificationService.showAlertCalls == 1)
        #expect(notificationService.alertData?.title == AlertStrings.DeleteEntryAlert.title)
        #expect(notificationService.alertData?.message == AlertStrings.DeleteEntryAlert.message)
        #expect(notificationService.alertData?.buttons.count == 2)
    }

    @Test("showDeleteEntryAlert confirm shows undo toast then commits delete on dismiss")
    func showDeleteEntryAlertConfirmDeletes() async {
        let (store, entryService, notificationService, _, _) = makeHistoryStoreSUT()
        let entry = EntryTestFixtures.makeEntrySnapshot()
        store.showDeleteEntryAlert(entry: entry, onCancel: nil)
        guard let alert = notificationService.alertData, let deleteButton = alert.buttons.first(where: { $0.type == .danger }) else {
            Issue.record("Expected delete alert button")
            return
        }
        deleteButton.action(nil)
        // Optimistic delete: an undo toast is shown and no delete is performed yet.
        #expect(notificationService.showToastCalls >= 1)
        #expect(entryService.deleteEntryByIdCalls == 0)
        // Dismissing the undo toast commits the actual delete.
        notificationService.toastData?.onDismiss?()
        let done = await waitUntilHistoryStore { entryService.deleteEntryByIdCalls == 1 }
        #expect(done == true)
        #expect(entryService.deletedEntryIds.first == entry.id)
    }

    @Test("showDeleteEntryAlert cancel dismisses and calls onCancel")
    func showDeleteEntryAlertCancelDismisses() {
        let (store, entryService, notificationService, _, _) = makeHistoryStoreSUT()
        let entry = EntryTestFixtures.makeEntrySnapshot()
        var onCancelCalled = false
        store.showDeleteEntryAlert(entry: entry) { onCancelCalled = true }
        guard let alert = notificationService.alertData, let cancelButton = alert.buttons.first(where: { $0.type == .secondary }) else {
            Issue.record("Expected cancel button")
            return
        }
        cancelButton.action(nil)
        #expect(onCancelCalled == true)
        #expect(entryService.deleteEntryCalls == 0)
    }

    // MARK: - handleExport

    @Test("handleExport presents CSV alert")
    func handleExportPresentsAlert() {
        let (store, _, notificationService, _, _) = makeHistoryStoreSUT()
        store.handleExport()
        #expect(notificationService.showAlertCalls == 1)
        #expect(notificationService.alertData?.title == AlertStrings.CsvExportAlert.title)
    }

    @Test("handleExport confirm triggers export and success shows toast")
    func handleExportConfirmSuccessToast() async {
        let (store, entryService, notificationService, _, _) = makeHistoryStoreSUT()
        entryService.exportCSVResult = .success(())
        store.handleExport()
        guard let alert = notificationService.alertData, let sendButton = alert.buttons.first(where: { $0.type == .primary }) else {
            Issue.record("Expected send button")
            return
        }
        sendButton.action(nil)
        let done = await waitUntilHistoryStore { notificationService.toastData != nil }
        #expect(done == true)
        #expect(entryService.exportCSVCalls == 1)
        #expect(notificationService.toastData?.message == ToastStrings.csvExported)
        #expect(notificationService.dismissLoaderCalls >= 1)
    }

    @Test("handleExport failure shows error toast except noInternet")
    func handleExportFailureShowsToast() async {
        let (store, entryService, notificationService, _, _) = makeHistoryStoreSUT()
        entryService.exportCSVResult = .failure(HistoryStoreTestError.exportFailed)
        store.handleExport()
        guard let alert = notificationService.alertData, let sendButton = alert.buttons.first(where: { $0.type == .primary }) else {
            Issue.record("Expected send button")
            return
        }
        sendButton.action(nil)
        let done = await waitUntilHistoryStore { notificationService.toastData != nil }
        #expect(done == true)
        #expect(notificationService.toastData?.message == ToastStrings.csvExportError)
    }

    @Test("handleExport noInternet does not show error toast")
    func handleExportNoInternetNoToast() async {
        let (store, entryService, notificationService, _, _) = makeHistoryStoreSUT()
        entryService.exportCSVResult = .failure(HTTPError.noInternet)
        store.handleExport()
        guard let alert = notificationService.alertData, let sendButton = alert.buttons.first(where: { $0.type == .primary }) else {
            Issue.record("Expected send button")
            return
        }
        sendButton.action(nil)
        try? await Task.sleep(nanoseconds: 100_000_000)
        #expect(notificationService.toastData?.message != ToastStrings.csvExportError)
    }

    // MARK: - entrySaved / entryDeleted subscriptions

    @Test("entrySaved refreshes months and entries when viewing same month")
    func entrySavedRefreshesWhenViewingSameMonth() async {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        entryService.getMonthsAllResult = .success([makeHistoryMonth(id: "2026-03")])
        entryService.fetchEntrySnapshotsForMonthResult = .success([EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-01T08:00:00Z")])
        store.loadMonths()
        _ = await waitUntilHistoryStore { store.months.count == 1 }
        store.selectMonth(makeHistoryMonth(id: "2026-03"))
        _ = await waitUntilHistoryStore { store.entries.count == 1 }
        let initialSnapshotCalls = entryService.fetchEntrySnapshotsForMonthCalls
        entryService.getMonthsAllResult = .success([makeHistoryMonth(id: "2026-03"), makeHistoryMonth(id: "2026-02")])
        entryService.fetchEntrySnapshotsForMonthResult = .success([
            EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-01T08:00:00Z"),
            EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-02T09:00:00Z")
        ])
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-02T09:00:00Z")
        entryService.entrySaved.send(EntryNotification(from: entry))
        let done = await waitUntilHistoryStore {
            entryService.getMonthsAllCalls >= 2 && entryService.fetchEntrySnapshotsForMonthCalls > initialSnapshotCalls
        }
        #expect(done == true)
    }

    @Test("entryDeleted refreshes months and entries when viewing same month")
    func entryDeletedRefreshesWhenViewingSameMonth() async {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        entryService.getMonthsAllResult = .success([makeHistoryMonth(id: "2026-03")])
        entryService.fetchEntrySnapshotsForMonthResult = .success([EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-01T08:00:00Z")])
        store.loadMonths()
        _ = await waitUntilHistoryStore { store.months.count == 1 }
        store.selectMonth(makeHistoryMonth(id: "2026-03"))
        _ = await waitUntilHistoryStore { store.entries.count == 1 }
        entryService.getMonthsAllResult = .success([])
        entryService.fetchEntrySnapshotsForMonthResult = .success([])
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z")
        entryService.entryDeleted.send(EntryNotification(from: entry))
        let done = await waitUntilHistoryStore { entryService.getMonthsAllCalls >= 2 }
        #expect(done == true)
    }

    // MARK: - isEmptyState contract

    @Test("isEmptyState true when getMonthsAll returns empty")
    func isEmptyStateTrueWhenMonthsEmpty() async {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        entryService.getMonthsAllResult = .success([])
        store.loadMonths()
        _ = await waitUntilHistoryStore { entryService.getMonthsAllCalls == 1 }
        #expect(store.isEmptyState == true)
    }

    @Test("isEmptyState true when getMonthsAll fails")
    func isEmptyStateTrueOnLoadFailure() async {
        let (store, entryService, _, _, _) = makeHistoryStoreSUT()
        entryService.getMonthsAllResult = .failure(HistoryStoreTestError.loadMonthsFailed)
        store.loadMonths()
        _ = await waitUntilHistoryStore { entryService.getMonthsAllCalls == 1 }
        #expect(store.isEmptyState == true)
    }
}
