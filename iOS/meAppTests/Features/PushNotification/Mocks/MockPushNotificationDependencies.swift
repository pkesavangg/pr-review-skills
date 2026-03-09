import Combine
import Foundation
import GGBluetoothSwiftPackage
import UserNotifications
@testable import meApp

@MainActor
final class MockPushNotificationAPIRepository: PushNotificationRepositoryAPIProtocol {
    var updateDeviceInfoResult: Result<Void, Error> = .success(())
    private(set) var updateDeviceInfoCalls = 0
    private(set) var lastRequest: DeviceInfoRequest?

    func updateDeviceInfo(_ info: DeviceInfoRequest) async throws {
        updateDeviceInfoCalls += 1
        lastRequest = info
        try updateDeviceInfoResult.get()
    }
}

@MainActor
final class MockPushTokenProvider: PushTokenProviderProtocol {
    var fetchTokenResult: Result<String, Error> = .success("fcm-token")
    private(set) var fetchCalls = 0

    func fetchFCMToken() async throws -> String {
        fetchCalls += 1
        return try fetchTokenResult.get()
    }
}

@MainActor
final class MockPushRegistrar: PushRemoteNotificationRegistrarProtocol {
    private(set) var registerCalls = 0

    func registerForRemoteNotifications() {
        registerCalls += 1
    }
}

@MainActor
final class MockPushUserNotificationCenter: PushUserNotificationCenterProtocol {
    var addResult: Result<Void, Error> = .success(())
    private(set) var addCalls = 0
    private(set) var lastRequest: UNNotificationRequest?

    func add(_ request: UNNotificationRequest) async throws {
        addCalls += 1
        lastRequest = request
        try addResult.get()
    }
}

@MainActor
final class MockPushEntryService: EntryServiceProtocol {
    let entrySaved = PassthroughSubject<EntryNotification, Never>()
    let entryDeleted = PassthroughSubject<EntryNotification, Never>()

    private(set) var syncAllEntriesCalls = 0
    var shouldSuspendSync = false
    private var syncContinuation: CheckedContinuation<Void, Never>?

    func syncAllEntriesWithRemote() async {
        syncAllEntriesCalls += 1
        if shouldSuspendSync {
            await withCheckedContinuation { continuation in
                syncContinuation = continuation
            }
        }
    }

    func releaseSync() {
        syncContinuation?.resume(returning: ())
        syncContinuation = nil
    }

    func migrateFromSQLiteIfNeeded() async {}
    func loadDashboardData() async {}
    func clearAllData() async {}
    func clearLastSyncTimestamp() async throws {}
    func saveNewEntry(_ entry: Entry) async throws {}
    func saveNewEntries(_ entries: [Entry]) async throws {}
    func deleteEntry(_ entry: Entry) async throws {}
    func getAllEntries() async throws -> [Entry] { [] }
    func getAllEntriesAsDTO() async throws -> [BathScaleOperationDTO] { [] }
    func checkEntryTimestampExists(_ entryTimestamp: String) async throws -> Bool { false }
    func getEntryCount() async throws -> Int { 0 }
    func getOldestEntry() async throws -> Entry? { nil }
    func getLatestEntry() async throws -> Entry? { nil }
    func getEntries(lastNDays: Int) async throws -> [Entry] { [] }
    func getEntries(forMonth month: String) async throws -> [Entry] { [] }
    func getMonthsAll() async throws -> [HistoryMonth] { [] }
    func getMonthDetail(month: String) async throws -> [Entry] { [] }
    func getMonthYear() async throws -> [HistoryMonth] { [] }
    func getProgress() async throws -> meApp.Progress {
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
    func getStreak() async throws -> Streak { Streak(current: 0, max: 0) }
    func exportCSV() async throws {}
}

@MainActor
final class MockPushPermissionsService: PermissionsServiceProtocol {
    @Published var permissions: [GGPermissionType: GGPermissionState]?
    var permissionsPublisher: AnyPublisher<[GGPermissionType: GGPermissionState]?, Never> { $permissions.eraseToAnyPublisher() }
    var requiredCategories: Set<PermissionCategory> = [.notifications]
    private let requiredCategoriesSubject = CurrentValueSubject<Set<PermissionCategory>, Never>([.notifications])
    var requiredCategoriesPublisher: AnyPublisher<Set<PermissionCategory>, Never> {
        requiredCategoriesSubject.eraseToAnyPublisher()
    }

    var currentState: GGPermissionState = .DISABLED
    var forcedGetPermissionState: GGPermissionState?
    var handlePermissionResult: GGPermissionState = .ENABLED
    private(set) var handlePermissionCalls = 0
    private(set) var lastHandledPermission: PermissionType?
    private(set) var navigateToWifiSettingsCalls = 0

    func setPermissions(_ permissions: [GGPermissionType: GGPermissionState]) {
        self.permissions = permissions
    }

    func setRequiredCategories(_ categories: Set<PermissionCategory>) {
        requiredCategories = categories
        requiredCategoriesSubject.send(categories)
    }

    func permissionRequest(_ type: GGPermissionType) async -> GGPermissionState {
        currentState
    }

    func handlePermission(_ type: PermissionType) async -> GGPermissionState {
        handlePermissionCalls += 1
        lastHandledPermission = type
        currentState = handlePermissionResult
        return handlePermissionResult
    }

    func getPermissionState(_ type: GGPermissionType) -> GGPermissionState? {
        forcedGetPermissionState ?? currentState
    }

    func navigateToWifiSettings() {
        navigateToWifiSettingsCalls += 1
    }
}

@MainActor
final class MockPushScaleService: ScaleServiceProtocol {
    @Published var scales: [Device] = []
    var scalesPublisher: AnyPublisher<[Device], Never> { $scales.eraseToAnyPublisher() }
    private(set) var syncAllScalesCalls = 0

    func clearAllData() async {}
    func getDevices() async throws -> [Device] { [] }
    func getConnectedDevices() async -> [String: Any] { [:] }
    func updateConnectedDevices(device: Any, isConnected: Bool) async {}
    func updateConnectedDeviceWifiStatus(broadcastId: String, isConfigured: Bool) async {}
    func updateConnectedDeviceWeightOnlyMode(broadcastId: String, isWeightOnlyModeEnabledByOthers: Bool) async {}
    func syncDevices(tempDevice: Device?) async throws {}
    func createDevice(_ device: Device, _ skipDuplicateCheck: Bool) async throws -> Device { device }
    func editDevice(_ deviceId: String, properties: [String: Any]) async throws -> Device { throw UnexpectedCallError.methodCalled("editDevice") }
    func deleteDevice(_ deviceId: String, showToast: Bool) async throws {}
    func updateScaleMeta(_ deviceId: String, metaData: DeviceMetaData) async throws {}
    func updateScalePreference(_ deviceId: String, _ preference: R4ScalePreference) async throws {}
    func updateScalePreference(_ deviceId: String, fromDTO dto: R4ScalePreferenceDTO) async throws {}
    // swiftlint:disable:next function_parameter_count
    func createR4Scale(
        scaleId: String,
        accountId: String,
        displayName: String,
        token: String,
        mac: String?,
        broadcastIdString: String?,
        broadcastId: Int64?,
        sku: String?,
        deviceName: String?,
        wifiMac: String?,
        deviceMetadata: DeviceMetaData?,
        isWifiConfigured: Bool,
        isConnected: Bool,
        skipDuplicateCheck: Bool
    ) async throws -> Device {
        throw UnexpectedCallError.methodCalled("createR4Scale")
    }

    func createBluetoothScale(
        device: Device,
        sku: String?,
        userNumber: String,
        accountId: String,
        deviceMetadata: DeviceMetaData?,
        skipDuplicateCheck: Bool
    ) async throws -> Device {
        throw UnexpectedCallError.methodCalled("createBluetoothScale")
    }

    func updateAllScalesStatus(_ scales: [Device]?) async throws {}
    func syncAllScalesWithRemote() async { syncAllScalesCalls += 1 }
    func pushLocalChangesToServer() async {}
    func getDevice(by deviceId: String) async throws -> Device? { nil }
    func fetchAttachedPreference(by id: String) async -> R4ScalePreference? { nil }
    func fetchAttachedPreferenceSync(by id: String) -> R4ScalePreference? { nil }
}
