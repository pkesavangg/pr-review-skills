//
//  HistoryStoreTests.swift
//  meAppTests
//

import Foundation
import Testing
@testable import meApp

@Suite("HistoryStore", .serialized)
@MainActor
struct HistoryStoreTests {

    // MARK: - SUT

    private func makeSUT() -> (
        store: HistoryStore,
        entryService: MockEntryService,
        accountService: MockAccountService,
        notificationService: MockNotificationHelperService,
        logger: MockLoggerService
    ) {
        _ = ServiceRegistry.shared

        let entryService = MockEntryService()
        let accountService = MockAccountService()
        let notificationService = MockNotificationHelperService()
        let logger = MockLoggerService()

        DependencyContainer.shared.register(entryService as EntryServiceProtocol)
        DependencyContainer.shared.register(accountService as AccountServiceProtocol)
        DependencyContainer.shared.register(notificationService as NotificationHelperService)
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)

        // HistoryStore.init() accesses entryService to subscribe to publishers
        // so mock must be registered before this line.
        let store = HistoryStore()

        return (store, entryService, accountService, notificationService, logger)
    }

    // MARK: - Fixture helpers

    private func makeHistoryMonth(
        id: String = "2024-06",
        entryTimestamp: String = "2024-06-01T00:00:00Z"
    ) -> HistoryMonth {
        HistoryMonth(
            id: id,
            weight: 70.0,
            entryTimestamp: entryTimestamp,
            count: 5,
            weights: nil,
            change: nil,
            bodyFat: nil,
            muscleMass: nil,
            water: nil,
            bmi: nil,
            date: nil,
            time: nil,
            month: "June",
            year: "2024",
            min: nil,
            max: nil
        )
    }

    private func makeEntry(
        timestamp: String = "2024-06-15T10:00:00Z",
        accountId: String = "test-account-id",
        operationType: String = OperationType.create.rawValue,
        serverTimestamp: String? = nil
    ) -> Entry {
        Entry(
            entryTimestamp: timestamp,
            accountId: accountId,
            operationType: operationType,
            serverTimestamp: serverTimestamp,
            isSynced: true
        )
    }

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 2_000_000_000,
        pollNanoseconds: UInt64 = 10_000_000,
        condition: @MainActor () -> Bool
    ) async {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while ContinuousClock.now < deadline {
            if condition() { return }
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
    }

    // MARK: - Initial State

    @Test("months is empty initially")
    func monthsEmptyInitially() {
        let (store, _, _, _, _) = makeSUT()
        #expect(store.months.isEmpty)
    }

    @Test("entries is empty initially")
    func entriesEmptyInitially() {
        let (store, _, _, _, _) = makeSUT()
        #expect(store.entries.isEmpty)
    }

    @Test("selectedMonth is nil initially")
    func selectedMonthNilInitially() {
        let (store, _, _, _, _) = makeSUT()
        #expect(store.selectedMonth == nil)
    }

    @Test("isEmptyState is false initially")
    func isEmptyStateFalseInitially() {
        let (store, _, _, _, _) = makeSUT()
        #expect(!store.isEmptyState)
    }

    @Test("expandedEntries is empty initially")
    func expandedEntriesEmptyInitially() {
        let (store, _, _, _, _) = makeSUT()
        #expect(store.expandedEntries.isEmpty)
    }

    @Test("selectedMetric is nil initially")
    func selectedMetricNilInitially() {
        let (store, _, _, _, _) = makeSUT()
        #expect(store.selectedMetric == nil)
    }

    // MARK: - loadMonths

    @Test("loadMonths calls getMonthsAll on entry service")
    func loadMonthsCallsService() async {
        let (store, entryService, _, _, _) = makeSUT()
        entryService.getMonthsAllResult = [makeHistoryMonth()]

        store.loadMonths()

        await waitUntil { entryService.getMonthsAllCallCount >= 1 }
        #expect(entryService.getMonthsAllCallCount == 1)
    }

    @Test("loadMonths populates months from service result")
    func loadMonthsPopulatesMonths() async {
        let (store, entryService, _, _, _) = makeSUT()
        entryService.getMonthsAllResult = [
            makeHistoryMonth(id: "2024-06"),
            makeHistoryMonth(id: "2024-05", entryTimestamp: "2024-05-01T00:00:00Z")
        ]

        store.loadMonths()

        await waitUntil { store.months.count == 2 }
        #expect(store.months.count == 2)
    }

    @Test("loadMonths sets isEmptyState false when months are returned")
    func loadMonthsSetsIsEmptyStateFalse() async {
        let (store, entryService, _, _, _) = makeSUT()
        entryService.getMonthsAllResult = [makeHistoryMonth()]

        store.loadMonths()

        await waitUntil { !store.months.isEmpty }
        #expect(!store.isEmptyState)
    }

    @Test("loadMonths sets isEmptyState true when result is empty")
    func loadMonthsSetsIsEmptyStateWhenEmpty() async {
        let (store, entryService, _, _, _) = makeSUT()
        entryService.getMonthsAllResult = []

        store.loadMonths()

        await waitUntil { entryService.getMonthsAllCallCount >= 1 }
        #expect(store.isEmptyState)
    }

    @Test("loadMonths ignores subsequent calls due to hasLoadedMonths guard")
    func loadMonthsOnlyLoadsOnce() async {
        let (store, entryService, _, _, _) = makeSUT()
        entryService.getMonthsAllResult = [makeHistoryMonth()]

        store.loadMonths()
        store.loadMonths()
        store.loadMonths()

        await waitUntil { entryService.getMonthsAllCallCount >= 1 }
        #expect(entryService.getMonthsAllCallCount == 1)
    }

    @Test("loadMonths on service error sets months empty and isEmptyState true")
    func loadMonthsOnErrorSetsEmptyState() async {
        let (store, entryService, _, _, _) = makeSUT()
        entryService.getMonthsAllError = NSError(domain: "HistoryTest", code: -1)

        store.loadMonths()

        await waitUntil { entryService.getMonthsAllCallCount >= 1 }
        #expect(store.months.isEmpty)
        #expect(store.isEmptyState)
    }

    // MARK: - selectMonth / loadEntries

    @Test("selectMonth sets selectedMonth immediately")
    func selectMonthSetsSelected() {
        let (store, _, _, _, _) = makeSUT()
        let month = makeHistoryMonth(id: "2024-06")

        store.selectMonth(month)

        #expect(store.selectedMonth?.id == "2024-06")
    }

    @Test("selectMonth triggers loadEntries and populates entries")
    func selectMonthLoadsEntries() async {
        let (store, entryService, _, _, _) = makeSUT()
        let month = makeHistoryMonth(id: "2024-06")
        entryService.getMonthDetailResult = [makeEntry()]

        store.selectMonth(month)

        await waitUntil { store.entries.count >= 1 }
        #expect(store.entries.count == 1)
        #expect(entryService.lastMonthDetailId == "2024-06")
    }

    @Test("loadEntries directly populates entries for the given month")
    func loadEntriesDirectly() async {
        let (store, entryService, _, _, _) = makeSUT()
        let month = makeHistoryMonth(id: "2024-06")
        entryService.getMonthDetailResult = [makeEntry(timestamp: "2024-06-15T10:00:00Z")]

        await store.loadEntries(for: month)

        #expect(store.entries.count == 1)
    }

    @Test("loadEntries filters out delete operations")
    func loadEntriesFiltersDeletes() async {
        let (store, entryService, _, _, _) = makeSUT()
        let month = makeHistoryMonth(id: "2024-06")
        entryService.getMonthDetailResult = [
            makeEntry(timestamp: "2024-06-15T10:00:00Z", operationType: OperationType.create.rawValue),
            makeEntry(timestamp: "2024-06-14T10:00:00Z", operationType: OperationType.delete.rawValue)
        ]

        await store.loadEntries(for: month)

        #expect(store.entries.count == 1)
        #expect(store.entries.first?.operationType == OperationType.create.rawValue)
    }

    @Test("loadEntries deduplicates same timestamp keeping latest serverTimestamp")
    func loadEntriesDeduplicates() async {
        let (store, entryService, _, _, _) = makeSUT()
        let month = makeHistoryMonth(id: "2024-06")
        let ts = "2024-06-15T10:00:00Z"
        entryService.getMonthDetailResult = [
            makeEntry(timestamp: ts, operationType: OperationType.create.rawValue, serverTimestamp: "2024-06-15T10:00:00Z"),
            makeEntry(timestamp: ts, operationType: OperationType.create.rawValue, serverTimestamp: "2024-06-15T11:00:00Z")
        ]

        await store.loadEntries(for: month)

        #expect(store.entries.count == 1)
        #expect(store.entries.first?.serverTimestamp == "2024-06-15T11:00:00Z")
    }

    @Test("loadEntries on error sets entries to empty array")
    func loadEntriesOnError() async {
        let (store, entryService, _, _, _) = makeSUT()
        let month = makeHistoryMonth()
        entryService.getMonthDetailError = NSError(domain: "HistoryTest", code: -1)

        await store.loadEntries(for: month)

        #expect(store.entries.isEmpty)
    }

    @Test("loadEntries with nil month parameter does nothing")
    func loadEntriesWithNilMonth() async {
        let (store, entryService, _, _, _) = makeSUT()

        await store.loadEntries(for: nil)

        #expect(entryService.getMonthDetailCallCount == 0)
    }

    // MARK: - setSelectedMonth / resetSelectedMonth

    @Test("setSelectedMonth sets selectedMonth and clears entries")
    func setSelectedMonthClearsEntries() async {
        let (store, entryService, _, _, _) = makeSUT()
        let month = makeHistoryMonth(id: "2024-06")
        entryService.getMonthDetailResult = [makeEntry()]

        // Seed entries first
        await store.loadEntries(for: month)
        #expect(!store.entries.isEmpty)

        // setSelectedMonth clears entries
        let newMonth = makeHistoryMonth(id: "2024-07", entryTimestamp: "2024-07-01T00:00:00Z")
        store.setSelectedMonth(selectedMonth: newMonth)

        #expect(store.selectedMonth?.id == "2024-07")
        #expect(store.entries.isEmpty)
    }

    @Test("resetSelectedMonth clears selectedMonth and entries")
    func resetSelectedMonthClearsAll() async {
        let (store, entryService, _, _, _) = makeSUT()
        let month = makeHistoryMonth(id: "2024-06")
        entryService.getMonthDetailResult = [makeEntry()]

        // Set a selected month and load entries
        store.setSelectedMonth(selectedMonth: month)
        await store.loadEntries(for: month)
        #expect(store.selectedMonth != nil)
        #expect(!store.entries.isEmpty)

        store.resetSelectedMonth()

        #expect(store.selectedMonth == nil)
        #expect(store.entries.isEmpty)
    }

    // MARK: - selectMetric

    @Test("selectMetric sets selectedMetric")
    func selectMetricSetsMetric() {
        let (store, _, _, _, _) = makeSUT()

        store.selectMetric(.weight)

        #expect(store.selectedMetric == .weight)
    }

    @Test("selectMetric can be changed")
    func selectMetricCanChange() {
        let (store, _, _, _, _) = makeSUT()

        store.selectMetric(.weight)
        store.selectMetric(.bodyFat)

        #expect(store.selectedMetric == .bodyFat)
    }

    // MARK: - showDeleteEntryAlert

    @Test("showDeleteEntryAlert presents an alert via notification service")
    func showDeleteEntryAlertShowsAlert() {
        let (store, _, _, notificationService, _) = makeSUT()
        let entry = makeEntry()

        store.showDeleteEntryAlert(entry: entry)

        #expect(notificationService.showAlertCallCount == 1)
        #expect(notificationService.lastShownAlert != nil)
    }

    // MARK: - handleExport

    @Test("handleExport shows CSV export confirmation alert")
    func handleExportShowsAlert() {
        let (store, _, _, notificationService, _) = makeSUT()

        store.handleExport()

        #expect(notificationService.showAlertCallCount == 1)
    }

    // MARK: - expandedEntries

    @Test("expandedEntries can insert and remove entry ids")
    func expandedEntriesToggle() {
        let (store, _, _, _, _) = makeSUT()
        let id = "entry-123"

        store.expandedEntries.insert(id)
        #expect(store.expandedEntries.contains(id))

        store.expandedEntries.remove(id)
        #expect(!store.expandedEntries.contains(id))
    }

    @Test("multiple entries can be expanded simultaneously")
    func multipleEntriesExpanded() {
        let (store, _, _, _, _) = makeSUT()
        store.expandedEntries.insert("a")
        store.expandedEntries.insert("b")
        #expect(store.expandedEntries.count == 2)
    }

    // MARK: - refreshAllEntries

    @Test("refreshAllEntries calls syncAllEntriesWithRemote on entry service")
    func refreshAllEntriesCallsSync() async {
        let (store, entryService, _, _, _) = makeSUT()

        await store.refreshAllEntries()

        #expect(entryService.syncAllEntriesCallCount == 1)
    }

    @Test("refreshAllEntries reloads months after sync")
    func refreshAllEntriesReloadsMonths() async {
        let (store, entryService, _, _, _) = makeSUT()
        entryService.getMonthsAllResult = [makeHistoryMonth()]

        await store.refreshAllEntries()

        #expect(entryService.getMonthsAllCallCount >= 1)
    }

    @Test("refreshAllEntries reloads entries for currently selected month")
    func refreshAllEntriesReloadsEntries() async {
        let (store, entryService, _, _, _) = makeSUT()
        let month = makeHistoryMonth(id: "2024-06")
        store.setSelectedMonth(selectedMonth: month)
        entryService.getMonthDetailResult = [makeEntry()]

        await store.refreshAllEntries()

        #expect(entryService.getMonthDetailCallCount >= 1)
    }

    @Test("refreshAllEntries with no selectedMonth does not call getMonthDetail")
    func refreshAllEntriesNoSelectedMonth() async {
        let (store, entryService, _, _, _) = makeSUT()

        await store.refreshAllEntries()

        #expect(entryService.getMonthDetailCallCount == 0)
    }

    // MARK: - showDeleteEntryAlert button actions

    @Test("delete alert confirm button deletes the entry via entry service")
    func deleteAlertConfirmDeletesEntry() async {
        let (store, entryService, _, notificationService, _) = makeSUT()
        let entry = makeEntry()

        store.showDeleteEntryAlert(entry: entry)
        #expect(notificationService.lastShownAlert != nil)

        // First button is the destructive delete action.
        notificationService.lastShownAlert?.buttons.first?.action(nil)

        await waitUntil { entryService.deleteEntryCallCount == 1 }
        #expect(entryService.deleteEntryCallCount == 1)
        #expect(entryService.lastDeletedEntry?.entryTimestamp == entry.entryTimestamp)
    }

    @Test("delete alert confirm button handles a delete failure without crashing")
    func deleteAlertConfirmHandlesError() async {
        let (store, entryService, _, notificationService, _) = makeSUT()
        entryService.deleteEntryError = NSError(domain: "HistoryTest", code: -1)

        store.showDeleteEntryAlert(entry: makeEntry())
        notificationService.lastShownAlert?.buttons.first?.action(nil)

        await waitUntil { entryService.deleteEntryCallCount == 1 }
        #expect(entryService.deleteEntryCallCount == 1)
    }

    @Test("delete alert cancel button dismisses the alert")
    func deleteAlertCancelDismisses() {
        let (store, _, _, notificationService, _) = makeSUT()

        store.showDeleteEntryAlert(entry: makeEntry())
        // Second button is the cancel action.
        notificationService.lastShownAlert?.buttons.last?.action(nil)

        #expect(notificationService.dismissAlertCallCount == 1)
    }

    // MARK: - handleExport button actions

    @Test("export alert send button exports CSV and shows a success toast")
    func exportAlertSendExportsCSV() async {
        let (store, entryService, _, notificationService, _) = makeSUT()

        store.handleExport()
        notificationService.lastShownAlert?.buttons.first?.action(nil)

        await waitUntil { entryService.exportCSVCallCount == 1 }
        #expect(entryService.exportCSVCallCount == 1)
        await waitUntil { notificationService.showToastCallCount == 1 }
        #expect(notificationService.showToastCallCount == 1)
    }

    @Test("export failure shows an error toast")
    func exportShowsErrorToastOnFailure() async {
        let (store, entryService, _, notificationService, _) = makeSUT()
        entryService.exportCSVError = NSError(domain: "HistoryTest", code: -1)

        store.handleExport()
        notificationService.lastShownAlert?.buttons.first?.action(nil)

        await waitUntil { notificationService.showToastCallCount == 1 }
        #expect(notificationService.showToastCallCount == 1)
    }

    @Test("export failure with no internet does not show a toast")
    func exportNoInternetShowsNoToast() async {
        let (store, entryService, _, notificationService, _) = makeSUT()
        entryService.exportCSVError = HTTPError.noInternet

        store.handleExport()
        notificationService.lastShownAlert?.buttons.first?.action(nil)

        // dismissLoader is the final statement of the export flow; once it runs the catch has completed.
        await waitUntil { notificationService.dismissLoaderCallCount >= 1 }
        #expect(notificationService.showToastCallCount == 0)
    }

    @Test("export alert cancel button does not export")
    func exportAlertCancelDoesNotExport() {
        let (store, entryService, _, notificationService, _) = makeSUT()

        store.handleExport()
        notificationService.lastShownAlert?.buttons.last?.action(nil)

        #expect(entryService.exportCSVCallCount == 0)
    }

    // MARK: - entrySaved / entryDeleted publishers

    @Test("entrySaved publisher reloads months")
    func entrySavedReloadsMonths() async {
        // `store` must be retained: the init subscriber captures `[weak self]`,
        // so a deallocated store would silently skip the reload.
        let (store, entryService, _, _, _) = makeSUT()
        entryService.getMonthsAllResult = [makeHistoryMonth()]

        entryService.entrySaved.send(EntryNotification(from: makeEntry()))

        await waitUntil { entryService.getMonthsAllCallCount >= 1 }
        #expect(entryService.getMonthsAllCallCount >= 1)
        withExtendedLifetime(store) {}
    }

    @Test("entryDeleted publisher reloads months")
    func entryDeletedReloadsMonths() async {
        let (store, entryService, _, _, _) = makeSUT()
        entryService.getMonthsAllResult = [makeHistoryMonth()]

        entryService.entryDeleted.send(EntryNotification(from: makeEntry()))

        await waitUntil { entryService.getMonthsAllCallCount >= 1 }
        #expect(entryService.getMonthsAllCallCount >= 1)
        withExtendedLifetime(store) {}
    }
}
