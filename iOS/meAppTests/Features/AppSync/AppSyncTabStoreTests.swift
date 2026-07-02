import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct AppSyncTabStoreTests {
    @Test("handleScanned valid scan shows confirmation modal and does not show error toast")
    func handleScannedValidShowsModal() async {
        let (store, _, notification, _, _) = makeSUT()
        let router = MockAppSyncTabRouter()

        store.handleScanned(weightKg: 81.65, fat: 15.2, muscle: 38.1, water: 54.4, tabRouter: router)

        #expect(notification.showModalCalls == 1)
        #expect(notification.showToastCalls == 0)
    }

    @Test("handleScanned invalid weight rejects scan and shows error toast")
    func handleScannedInvalidWeightShowsErrorToast() async {
        let (store, _, notification, _, _) = makeSUT()
        let router = MockAppSyncTabRouter()

        store.handleScanned(weightKg: 0, fat: 12, muscle: 30, water: 50, tabRouter: router)

        #expect(notification.showModalCalls == 0)
        #expect(notification.showToastCalls == 1)
    }

    @Test("handleScanned out-of-range weight rejects scan and shows error toast")
    func handleScannedOutOfRangeShowsErrorToast() async {
        let (store, _, notification, _, _) = makeSUT()
        let router = MockAppSyncTabRouter()

        store.handleScanned(weightKg: 500, fat: 12, muscle: 30, water: 50, tabRouter: router)

        #expect(notification.showModalCalls == 0)
        #expect(notification.showToastCalls == 1)
    }

    @Test("handleScanned below-min range rejects scan and shows error toast")
    func handleScannedBelowMinShowsErrorToast() async {
        let (store, _, notification, _, _) = makeSUT()
        let router = MockAppSyncTabRouter()

        store.handleScanned(weightKg: 0.5, fat: 12, muscle: 30, water: 50, tabRouter: router)

        #expect(notification.showModalCalls == 0)
        #expect(notification.showToastCalls == 1)
    }

    @Test("handleScanned NaN rejects scan and shows error toast")
    func handleScannedNaNShowsErrorToast() async {
        let (store, _, notification, _, _) = makeSUT()
        let router = MockAppSyncTabRouter()

        store.handleScanned(weightKg: .nan, fat: 12, muscle: 30, water: 50, tabRouter: router)

        #expect(notification.showModalCalls == 0)
        #expect(notification.showToastCalls == 1)
    }

    @Test("handleScanned infinity rejects scan and shows error toast")
    func handleScannedInfinityShowsErrorToast() async {
        let (store, _, notification, _, _) = makeSUT()
        let router = MockAppSyncTabRouter()

        store.handleScanned(weightKg: .infinity, fat: 12, muscle: 30, water: 50, tabRouter: router)

        #expect(notification.showModalCalls == 0)
        #expect(notification.showToastCalls == 1)
    }

    @Test("save action navigates to dashboard and saves entry successfully")
    func saveActionSuccess() async {
        let (store, account, notification, entry, _) = makeSUT()
        account.activeAccount = AppSyncTabStoreTestFixtures.makeActiveAccount(id: "appsync-save-1", unit: .lb)
        let router = MockAppSyncTabRouter()

        store.handleScanned(weightKg: 81.65, fat: 15.0, muscle: 38.0, water: 55.0, tabRouter: router)
        store.handleSaveAction(tabRouter: router)

        let saved = await waitUntil { entry.saveNewEntryCalls == 1 }
        #expect(saved == true)
        #expect(router.selectedTab == .dash)
        #expect(notification.showLoaderCalls == 1)
        #expect(notification.dismissLoaderCalls == 1)
        #expect(notification.showToastCalls == 1)
        #expect(notification.dismissModalCalls >= 2)

        guard let savedEntry = entry.lastSavedEntry else {
            Issue.record("Expected saved entry")
            return
        }
        #expect(savedEntry.accountId == "appsync-save-1")
        #expect(savedEntry.entryType == EntryType.scale.rawValue)
        #expect(savedEntry.operationType == OperationType.create.rawValue)
        #expect(savedEntry.scaleEntry?.source == EntrySource.appsyncScale.rawValue)
        #expect(savedEntry.scaleEntry?.weight != nil)
    }

    @Test("edit action navigates to entry tab and stores pending metrics")
    func editActionNavigatesToEntry() async {
        let (store, _, notification, _, _) = makeSUT()
        let router = MockAppSyncTabRouter()
        let metrics = AppSyncTabStoreTestFixtures.makeMetrics(storedWeight: 1777, isMetric: true, rawDisplayWeightKg: 80.6)

        store.handleEditAction(metrics: metrics, tabRouter: router)

        #expect(router.selectedTab == .entry)
        #expect(router.pendingAppSyncEditMetrics?.storedWeight == 1777)
        #expect(router.pendingAppSyncEditMetrics?.isMetric == true)
        #expect(notification.dismissModalCalls == 1)
    }

    @Test("saveScannedEntry skips save when active account is missing")
    func saveScannedEntryMissingActiveAccount() async {
        let (store, account, notification, entry, _) = makeSUT()
        account.activeAccount = nil
        let router = MockAppSyncTabRouter()
        store.handleScanned(weightKg: 70, fat: 10, muscle: 40, water: 50, tabRouter: router)

        await store.saveScannedEntry()

        #expect(entry.saveNewEntryCalls == 0)
        #expect(notification.showLoaderCalls == 0)
    }

    @Test("saveScannedEntry skips save when scanned data is missing")
    func saveScannedEntryMissingData() async {
        let (store, _, notification, entry, _) = makeSUT()

        await store.saveScannedEntry()

        #expect(entry.saveNewEntryCalls == 0)
        #expect(notification.showLoaderCalls == 0)
        #expect(notification.showToastCalls == 0)
    }

    @Test("saveScannedEntry shows error toast when save fails")
    func saveScannedEntryFailureShowsErrorToast() async {
        let (store, account, notification, entry, _) = makeSUT()
        account.activeAccount = AppSyncTabStoreTestFixtures.makeActiveAccount(id: "appsync-save-fail")
        entry.saveNewEntryError = AppSyncTabStoreTestError.saveFailed
        let router = MockAppSyncTabRouter()
        store.handleScanned(weightKg: 90, fat: 20, muscle: 40, water: 50, tabRouter: router)

        await store.saveScannedEntry()

        #expect(entry.saveNewEntryCalls == 1)
        #expect(notification.showToastCalls == 1)
        #expect(notification.dismissLoaderCalls == 1)
        #expect(notification.dismissModalCalls == 1)
    }

    @Test("saveScannedEntry uses kg unit when account prefers metric")
    func saveScannedEntryMetricUnitUsesKg() async {
        let (store, account, _, entry, _) = makeSUT()
        account.activeAccount = AppSyncTabStoreTestFixtures.makeActiveAccount(id: "appsync-kg", unit: .kg)
        let router = MockAppSyncTabRouter()
        store.handleScanned(weightKg: 72.4, fat: 11.1, muscle: 44.4, water: 55.5, tabRouter: router)

        await store.saveScannedEntry()

        #expect(entry.saveNewEntryCalls == 1)
        #expect(entry.lastSavedEntry?.scaleEntryMetric?.unit == WeightUnit.kg.rawValue)
    }

    @Test("saveScannedEntry stores nil optional metrics when scan values are zero")
    func saveScannedEntryZeroOptionalMetricsStoredAsNil() async {
        let (store, account, _, entry, _) = makeSUT()
        account.activeAccount = AppSyncTabStoreTestFixtures.makeActiveAccount(id: "appsync-nil-metrics", unit: .lb)
        let router = MockAppSyncTabRouter()
        store.handleScanned(weightKg: 80.0, fat: 0, muscle: 0, water: 0, tabRouter: router)

        await store.saveScannedEntry()

        #expect(entry.saveNewEntryCalls == 1)
        #expect(entry.lastSavedEntry?.scaleEntry?.bodyFat == nil)
        #expect(entry.lastSavedEntry?.scaleEntry?.muscleMass == nil)
        #expect(entry.lastSavedEntry?.scaleEntry?.water == nil)
    }
}

// swiftlint:disable large_tuple
@MainActor
private func makeSUT(
    accountService: MockAccountService? = nil,
    notificationService: MockAppSyncTabStoreNotificationService? = nil,
    entryService: MockAppSyncTabStoreEntryService? = nil,
    loggerService: MockLoggerService? = nil
) -> (
    store: AppSyncTabStore,
    accountService: MockAccountService,
    notificationService: MockAppSyncTabStoreNotificationService,
    entryService: MockAppSyncTabStoreEntryService,
    loggerService: MockLoggerService
) {
// swiftlint:enable large_tuple
    let account = accountService ?? MockAccountService()
    let notification = notificationService ?? MockAppSyncTabStoreNotificationService()
    let entry = entryService ?? MockAppSyncTabStoreEntryService()
    let logger = loggerService ?? MockLoggerService()

    TestDependencyContainer.reset()
    DependencyContainer.shared.register(account as AccountServiceProtocol)
    DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)
    DependencyContainer.shared.register(entry as EntryServiceProtocol)
    DependencyContainer.shared.register(logger as LoggerServiceProtocol)

    let store = AppSyncTabStore()
    return (store, account, notification, entry, logger)
}

@MainActor
private func waitUntil(
    timeoutNanoseconds: UInt64 = 2_000_000_000,
    pollIntervalNanoseconds: UInt64 = 10_000_000,
    condition: @MainActor () -> Bool
) async -> Bool {
    let start = DispatchTime.now().uptimeNanoseconds
    while DispatchTime.now().uptimeNanoseconds - start < timeoutNanoseconds {
        if condition() { return true }
        try? await Task.sleep(nanoseconds: pollIntervalNanoseconds)
    }
    return false
}
