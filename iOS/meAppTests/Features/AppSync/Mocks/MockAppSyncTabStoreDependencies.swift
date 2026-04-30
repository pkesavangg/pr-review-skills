import Combine
import Foundation
@testable import meApp

enum AppSyncTabStoreTestError: Error, Equatable {
    case saveFailed
}

@MainActor
final class MockAppSyncTabStoreNotificationService: NotificationHelperServiceProtocol {
    var isOverlayActive: Bool = false
    var isAlertVisible: Bool { false }
    var isToastVisible: Bool { toastData != nil }
    var isLoaderVisible: Bool { loaderData != nil }
    var isModalVisible: Bool { !modalViewData.isEmpty }

    private(set) var toastData: ToastModel?
    private(set) var loaderData: LoaderModel?
    private(set) var modalViewData: [ModalData] = []

    private(set) var showToastCalls = 0
    private(set) var dismissToastCalls = 0
    private(set) var showLoaderCalls = 0
    private(set) var dismissLoaderCalls = 0
    private(set) var showModalCalls = 0
    private(set) var dismissModalCalls = 0

    func showAlert(_ alert: AlertModel) {}
    func dismissAlert() {}

    func showToast(_ data: ToastModel) {
        showToastCalls += 1
        toastData = data
    }

    func dismissToast() {
        dismissToastCalls += 1
        toastData = nil
    }

    func showLoader(_ loader: LoaderModel) {
        showLoaderCalls += 1
        loaderData = loader
    }

    func dismissLoader() {
        dismissLoaderCalls += 1
        loaderData = nil
    }

    func dismissAllNotifications() {
        toastData = nil
        loaderData = nil
        modalViewData = []
    }

    func showModal(_ modal: ModalData) {
        showModalCalls += 1
        modalViewData.append(modal)
    }

    func dismissModal() {
        dismissModalCalls += 1
        if !modalViewData.isEmpty {
            modalViewData.removeLast()
        }
    }

    func dismissAllModals() {
        modalViewData = []
    }
}

@MainActor
final class MockAppSyncTabStoreEntryService: EntryServiceProtocol {
    let entrySaved = PassthroughSubject<EntryNotification, Never>()
    let entryDeleted = PassthroughSubject<EntryNotification, Never>()

    var saveNewEntryError: Error?
    private(set) var saveNewEntryCalls = 0
    private(set) var lastSavedEntry: Entry?

    func saveNewEntry(_ entry: Entry) async throws {
        saveNewEntryCalls += 1
        lastSavedEntry = entry
        if let saveNewEntryError {
            throw saveNewEntryError
        }
    }

    func clearAllData() async {}
    func clearLastSyncTimestamp() async throws {}
    func saveNewEntries(_ entries: [Entry]) async throws {}
    func deleteEntry(_ entry: Entry) async throws {}
    func deleteEntry(entryId: UUID) async throws {}
    func fetchEntrySnapshot(byId id: UUID) async throws -> EntrySnapshot? { nil }
    func fetchAllEntrySnapshots() async throws -> [EntrySnapshot] { [] }
    func fetchEntrySnapshots(forMonth month: String, entryType: EntryType) async throws -> [EntrySnapshot] { [] }
    func syncAllEntriesWithRemote() async {}
    func migrateFromSQLiteIfNeeded() async {}
    func loadDashboardData(entryType: EntryType) async {}
    func loadBabyDashboardData(babyId: String) async {}
    func getAllEntries() async throws -> [Entry] { [] }
    func getAllEntriesAsDTO() async throws -> [BathScaleOperationDTO] { [] }
    func checkEntryTimestampExists(_ entryTimestamp: String) async throws -> Bool { false }
    func getEntryCount() async throws -> Int { 0 }
    func getOldestEntry() async throws -> Entry? { nil }
    func getLatestEntry() async throws -> Entry? { nil }
    func getEntries(lastNDays: Int, entryType: EntryType) async throws -> [Entry] { [] }
    func getEntries(forMonth month: String, entryType: EntryType) async throws -> [Entry] { [] }
    func getMonthsAll(entryType: EntryType) async throws -> [HistoryMonth] { [] }
    func getMonthDetail(month: String, entryType: EntryType) async throws -> [Entry] { [] }
    func getMonthYear() async throws -> [HistoryMonth] { [] }
    func getProgress(entryType: EntryType) async throws -> meApp.Progress {
        meApp.Progress(
            count: 0,
            currentStreak: 0,
            initYear: nil,
            initMonth: nil,
            initWeek: nil,
            initWt: 0,
            latest: nil,
            longestStreak: 0,
            month: 1,
            percent: nil,
            total: nil,
            week: 1,
            year: 2024
        )
    }

    func getStreak(entryType: EntryType) async throws -> Streak { Streak(current: 0, max: 0) }
    func exportCSV() async throws {}
    func createBpmEntry(_ dto: BpmOperationDTO) async throws {}
    func createBabyEntry(babyId: String, weight: Int, length: Int, note: String, entryTimestamp: String) async throws {}
    func fetchBpmEntries() async throws -> [BpmOperationDTO] { [] }
    func deleteBpmEntry(entryTimestamp: String) async throws {}
    func exportBpmCSV() async throws {}
    func migrateBabyEntriesToDecigrams() async {}
    func getEntry(byId id: UUID) async throws -> Entry? { nil }
    func createBabyEntry(babyId: String, weight: Int, length: Int, note: String, entryTimestamp: String, source: String?) async throws {}
}

@MainActor
final class MockAppSyncTabRouter: AppSyncTabRouting {
    var selectedTab: BottomTab = .appsync
    var pendingAppSyncEditMetrics: AppSyncEntryMetrics?
}
