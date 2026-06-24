//
//  HistoryStoreTests.swift
//  meAppTests
//
//  Unit tests for HistoryStore: initial state, data loading, filtering/sorting,
//  state updates from user actions, empty/loading/error states, dependency interactions.
//

import Foundation
import Testing
@testable import meApp

enum HistoryStoreTestError: Error, Equatable {
    case loadMonthsFailed
    case loadMonthDetailFailed
    case deleteFailed
    case exportFailed
}

// MARK: - Fixtures

private func makeHistoryMonth(id: String = "2026-03", weight: Double = 150, count: Int = 3) -> HistoryMonth {
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

@MainActor
private func makeSUT() -> ( // swiftlint:disable:this large_tuple
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
private func waitUntil(timeoutIterations: Int = 200, condition: @escaping @MainActor () -> Bool) async -> Bool {
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
        let (store, _, _, _, _) = makeSUT()

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
        let (store, entryService, notificationService, _, _) = makeSUT()
        entryService.getMonthsAllResult = .success([])

        store.loadMonths()
        let done = await waitUntil { entryService.getMonthsAllCalls == 1 }

        #expect(done == true)
        #expect(store.months.isEmpty)
        #expect(store.isEmptyState == true)
        #expect(notificationService.showLoaderCalls >= 1)
        #expect(notificationService.dismissLoaderCalls >= 1)
    }

    @Test("loadMonths success with data: months populated, isEmptyState false")
    func loadMonthsSuccessWithData() async {
        let (store, entryService, _, _, _) = makeSUT()
        let months = [makeHistoryMonth(id: "2026-03"), makeHistoryMonth(id: "2026-02", weight: 148)]
        entryService.getMonthsAllResult = .success(months)

        store.loadMonths()
        let done = await waitUntil { store.months.count == 2 }

        #expect(done == true)
        #expect(store.months.count == 2)
        #expect(store.months[0].id == "2026-03")
        #expect(store.isEmptyState == false)
    }

    @Test("loadMonths failure: months cleared, isEmptyState true, error logged")
    func loadMonthsFailure() async {
        let (store, entryService, notificationService, _, logger) = makeSUT()
        entryService.getMonthsAllResult = .failure(HistoryStoreTestError.loadMonthsFailed)

        store.loadMonths()
        let done = await waitUntil { entryService.getMonthsAllCalls == 1 }

        #expect(done == true)
        #expect(store.months.isEmpty)
        #expect(store.isEmptyState == true)
        #expect(notificationService.dismissLoaderCalls >= 1)
        #expect(logger.messages.contains { $0.contains("HistoryStore") && $0.contains("Failed to load history months") })
    }

    @Test("loadMonths only runs once: second call does not refetch")
    func loadMonthsRunsOnce() async {
        let (store, entryService, _, _, _) = makeSUT()
        entryService.getMonthsAllResult = .success([makeHistoryMonth()])

        store.loadMonths()
        _ = await waitUntil { store.months.count == 1 }
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
        let (store, entryService, _, _, _) = makeSUT()
        let month = makeHistoryMonth(id: "2026-03")
        entryService.fetchEntrySnapshotsForMonthResult = .success([EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-01T08:00:00Z")])

        store.selectMonth(month)
        let done = await waitUntil { store.selectedMonth?.id == "2026-03" && entryService.fetchEntrySnapshotsForMonthCalls == 1 }

        #expect(done == true)
        #expect(store.selectedMonth?.id == "2026-03")
        #expect(entryService.fetchEntrySnapshotsForMonthLast == "2026-03")
        #expect(store.entries.count == 1)
    }

    @Test("loadEntries with nil selectedMonth does nothing")
    func loadEntriesNoSelectedMonth() async {
        let (store, entryService, _, _, _) = makeSUT()
        entryService.fetchEntrySnapshotsForMonthResult = .success([])

        await store.loadEntries(for: nil)
        #expect(entryService.fetchEntrySnapshotsForMonthCalls == 0)
        #expect(store.entries.isEmpty)
    }

    @Test("loadEntries success empty: entries cleared")
    func loadEntriesSuccessEmpty() async {
        let (store, entryService, _, _, _) = makeSUT()
        let month = makeHistoryMonth()
        entryService.fetchEntrySnapshotsForMonthResult = .success([])
        store.setSelectedMonth(selectedMonth: month)

        await store.loadEntries(for: month)
        #expect(store.entries.isEmpty)
        #expect(entryService.fetchEntrySnapshotsForMonthCalls == 1)
    }

    @Test("loadEntries failure: entries cleared, error logged")
    func loadEntriesFailure() async {
        let (store, entryService, _, _, logger) = makeSUT()
        let month = makeHistoryMonth()
        entryService.fetchEntrySnapshotsForMonthResult = .failure(HistoryStoreTestError.loadMonthDetailFailed)
        store.setSelectedMonth(selectedMonth: month)

        await store.loadEntries(for: month)
        #expect(store.entries.isEmpty)
        #expect(entryService.fetchEntrySnapshotsForMonthCalls == 1)
        #expect(logger.messages.contains { $0.contains("HistoryStore") })
    }

    @Test("loadEntries dedupes by entryTimestamp keeps latest by serverTimestamp and create only")
    func loadEntriesDedupesAndFilters() async {
        let (store, entryService, _, _, _) = makeSUT()
        let month = makeHistoryMonth(id: "2026-03")
        let e1 = EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-01T08:00:00Z", serverTimestamp: "a", operationType: .create)
        let e2 = EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-01T08:00:00Z", serverTimestamp: "b", operationType: .create)
        let e3 = EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-01T08:00:00Z", serverTimestamp: "b", operationType: .delete)
        entryService.fetchEntrySnapshotsForMonthResult = .success([e1, e2, e3])
        store.setSelectedMonth(selectedMonth: month)

        await store.loadEntries(for: month)
        #expect(store.entries.count == 1)
        #expect(store.entries.first?.serverTimestamp == "b")
        #expect(store.entries.first?.operationType == OperationType.create.rawValue)
    }

    @Test("loadEntries sorts newest first by entryTimestamp")
    func loadEntriesSortsNewestFirst() async {
        let (store, entryService, _, _, _) = makeSUT()
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
        let (store, entryService, _, _, _) = makeSUT()
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
        let done = await waitUntil { !store.babyWeeks.isEmpty }

        #expect(done == true)
        #expect(store.babyWeeks.count >= 2)
        #expect((store.babyWeeks.first?.weekNumber ?? 0) > (store.babyWeeks.last?.weekNumber ?? 0))
        #expect(store.babyWeeks.first?.id == "week-\(store.babyWeeks.first?.weekNumber ?? 0)")
    }

    // MARK: - setSelectedMonth / resetSelectedMonth

    @Test("setSelectedMonth sets selectedMonth and clears entries")
    func setSelectedMonthClearsEntries() {
        let (store, entryService, _, _, _) = makeSUT()
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
        let (store, _, _, _, _) = makeSUT()
        store.setSelectedMonth(selectedMonth: makeHistoryMonth())
        #expect(store.selectedMonth != nil)
        store.resetSelectedMonth()
        #expect(store.selectedMonth == nil)
        #expect(store.entries.isEmpty)
    }

    // MARK: - selectMetric

    @Test("selectMetric sets selectedMetric")
    func selectMetricSetsMetric() {
        let (store, _, _, _, _) = makeSUT()
        #expect(store.selectedMetric == nil)
        store.selectMetric(.bmi)
        #expect(store.selectedMetric == .bmi)
        store.selectMetric(.bodyFat)
        #expect(store.selectedMetric == .bodyFat)
    }

    // MARK: - refreshAllEntries

    @Test("refreshAllEntries calls refreshAccount sync and reloads months and entries")
    func refreshAllEntriesCallsDependencies() async {
        let (store, entryService, _, accountService, _) = makeSUT()
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
        let (store, _, notificationService, _, _) = makeSUT()
        let entry = EntryTestFixtures.makeEntrySnapshot()
        store.showDeleteEntryAlert(entry: entry)
        #expect(notificationService.showAlertCalls == 1)
        #expect(notificationService.alertData?.title == AlertStrings.DeleteEntryAlert.title)
        #expect(notificationService.alertData?.message == AlertStrings.DeleteEntryAlert.message)
        #expect(notificationService.alertData?.buttons.count == 2)
    }

    @Test("showDeleteEntryAlert confirm shows undo toast then commits delete on dismiss")
    func showDeleteEntryAlertConfirmDeletes() async {
        let (store, entryService, notificationService, _, _) = makeSUT()
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
        let done = await waitUntil { entryService.deleteEntryByIdCalls == 1 }
        #expect(done == true)
        #expect(entryService.deletedEntryIds.first == entry.id)
    }

    @Test("showDeleteEntryAlert cancel dismisses and calls onCancel")
    func showDeleteEntryAlertCancelDismisses() {
        let (store, entryService, notificationService, _, _) = makeSUT()
        let entry = EntryTestFixtures.makeEntrySnapshot()
        var onCancelCalled = false
        store.showDeleteEntryAlert(entry: entry, onCancel: { onCancelCalled = true })
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
        let (store, _, notificationService, _, _) = makeSUT()
        store.handleExport()
        #expect(notificationService.showAlertCalls == 1)
        #expect(notificationService.alertData?.title == AlertStrings.CsvExportAlert.title)
    }

    @Test("handleExport confirm triggers export and success shows toast")
    func handleExportConfirmSuccessToast() async {
        let (store, entryService, notificationService, _, _) = makeSUT()
        entryService.exportCSVResult = .success(())
        store.handleExport()
        guard let alert = notificationService.alertData, let sendButton = alert.buttons.first(where: { $0.type == .primary }) else {
            Issue.record("Expected send button")
            return
        }
        sendButton.action(nil)
        let done = await waitUntil { notificationService.toastData != nil }
        #expect(done == true)
        #expect(entryService.exportCSVCalls == 1)
        #expect(notificationService.toastData?.message == ToastStrings.csvExported)
        #expect(notificationService.dismissLoaderCalls >= 1)
    }

    @Test("handleExport failure shows error toast except noInternet")
    func handleExportFailureShowsToast() async {
        let (store, entryService, notificationService, _, _) = makeSUT()
        entryService.exportCSVResult = .failure(HistoryStoreTestError.exportFailed)
        store.handleExport()
        guard let alert = notificationService.alertData, let sendButton = alert.buttons.first(where: { $0.type == .primary }) else {
            Issue.record("Expected send button")
            return
        }
        sendButton.action(nil)
        let done = await waitUntil { notificationService.toastData != nil }
        #expect(done == true)
        #expect(notificationService.toastData?.message == ToastStrings.csvExportError)
    }

    @Test("handleExport noInternet does not show error toast")
    func handleExportNoInternetNoToast() async {
        let (store, entryService, notificationService, _, _) = makeSUT()
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
        let (store, entryService, _, _, _) = makeSUT()
        entryService.getMonthsAllResult = .success([makeHistoryMonth(id: "2026-03")])
        entryService.fetchEntrySnapshotsForMonthResult = .success([EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-01T08:00:00Z")])
        store.loadMonths()
        _ = await waitUntil { store.months.count == 1 }
        store.selectMonth(makeHistoryMonth(id: "2026-03"))
        _ = await waitUntil { store.entries.count == 1 }
        let initialSnapshotCalls = entryService.fetchEntrySnapshotsForMonthCalls
        entryService.getMonthsAllResult = .success([makeHistoryMonth(id: "2026-03"), makeHistoryMonth(id: "2026-02")])
        entryService.fetchEntrySnapshotsForMonthResult = .success([
            EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-01T08:00:00Z"),
            EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-02T09:00:00Z")
        ])
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-02T09:00:00Z")
        entryService.entrySaved.send(EntryNotification(from: entry))
        let done = await waitUntil { entryService.getMonthsAllCalls >= 2 && entryService.fetchEntrySnapshotsForMonthCalls > initialSnapshotCalls }
        #expect(done == true)
    }

    @Test("entryDeleted refreshes months and entries when viewing same month")
    func entryDeletedRefreshesWhenViewingSameMonth() async {
        let (store, entryService, _, _, _) = makeSUT()
        entryService.getMonthsAllResult = .success([makeHistoryMonth(id: "2026-03")])
        entryService.fetchEntrySnapshotsForMonthResult = .success([EntryTestFixtures.makeEntrySnapshot(entryTimestamp: "2026-03-01T08:00:00Z")])
        store.loadMonths()
        _ = await waitUntil { store.months.count == 1 }
        store.selectMonth(makeHistoryMonth(id: "2026-03"))
        _ = await waitUntil { store.entries.count == 1 }
        entryService.getMonthsAllResult = .success([])
        entryService.fetchEntrySnapshotsForMonthResult = .success([])
        let entry = EntryTestFixtures.makeEntry(timestamp: "2026-03-01T08:00:00Z")
        entryService.entryDeleted.send(EntryNotification(from: entry))
        let done = await waitUntil { entryService.getMonthsAllCalls >= 2 }
        #expect(done == true)
    }

    // MARK: - isEmptyState contract

    @Test("isEmptyState true when getMonthsAll returns empty")
    func isEmptyStateTrueWhenMonthsEmpty() async {
        let (store, entryService, _, _, _) = makeSUT()
        entryService.getMonthsAllResult = .success([])
        store.loadMonths()
        _ = await waitUntil { entryService.getMonthsAllCalls == 1 }
        #expect(store.isEmptyState == true)
    }

    @Test("isEmptyState true when getMonthsAll fails")
    func isEmptyStateTrueOnLoadFailure() async {
        let (store, entryService, _, _, _) = makeSUT()
        entryService.getMonthsAllResult = .failure(HistoryStoreTestError.loadMonthsFailed)
        store.loadMonths()
        _ = await waitUntil { entryService.getMonthsAllCalls == 1 }
        #expect(store.isEmptyState == true)
    }

    // MARK: - Cursor Pagination (MOB-385)

    private func makePageEntry(timestamp: String) -> BathScaleOperationDTO {
        BathScaleOperationDTO(
            accountId: "acct-1", bmr: nil, bmi: nil, bodyFat: nil, boneMass: nil,
            entryTimestamp: timestamp, entryType: EntryType.scale.rawValue, impedance: nil,
            metabolicAge: nil, muscleMass: nil, operationType: "create", proteinPercent: nil,
            pulse: nil, serverTimestamp: nil, skeletalMusclePercent: nil, source: nil,
            subcutaneousFatPercent: nil, systolic: nil, diastolic: nil, meanArterial: nil,
            unit: "lb", visceralFatLevel: nil, water: nil, weight: 1700
        )
    }

    @Test("loadFirstPage: populates pagedEntries and hasMorePages from the first page")
    func loadFirstPagePopulates() async {
        let (store, entryService, _, _, _) = makeSUT()
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
        let (store, entryService, _, _, _) = makeSUT()
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
        let (store, entryService, _, _, _) = makeSUT()
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
        let (store, entryService, _, _, _) = makeSUT()
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
        let (store, entryService, _, _, logger) = makeSUT()
        entryService.fetchEntriesPageError = HistoryStoreTestError.loadMonthsFailed

        await store.loadFirstPage()

        #expect(store.pagedEntries.isEmpty)
        #expect(store.hasMorePages == false)
        #expect(store.isLoadingPage == false)
        #expect(logger.messages.contains { $0.contains("Failed to load entries page") })
    }

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
    ) -> (HistoryStore, MockEntryService, MockProductTypeStore) {
        let (store, entryService, _, _, _) = makeSUT()
        let productTypeStore = MockProductTypeStore()
        productTypeStore.selectedItem = .baby(profile: BabyProfile(id: babyId, name: babyName))
        store.productTypeStore = productTypeStore
        return (store, entryService, productTypeStore)
    }

    @Test("updateBabyEntry metric: creates entry with converted decigrams/mm then deletes old")
    func updateBabyEntryMetricEditsCallsCreateThenDelete() async {
        let entryId = UUID()
        let old = makeBabyEntry(id: entryId)
        let (store, entryService, _) = makeBabyStore()

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
        let (store, entryService, _) = makeBabyStore()

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
        let (store, entryService, _) = makeBabyStore()

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
        let (store, entryService, _, _, _) = makeSUT()
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
        let (store, entryService, _, _, _) = makeSUT()

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
        let (store, entryService, notificationService, _, _) = makeSUT()
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
        let (store, entryService, notificationService, _, _) = makeSUT()
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
        let (store, entryService, notificationService, _, _) = makeSUT()
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
        let (store, entryService, notificationService, _, _) = makeSUT()
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
        let (store, entryService, notificationService, _, _) = makeSUT()
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
}
