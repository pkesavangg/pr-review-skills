import Combine
import Foundation
import ggInAppMessagingPackage
@testable import meApp

enum ContentViewModelTestError: Error, Equatable {
    case refreshFailed
    case updateStateFailed
    case fetchEntriesFailed
    case accountFlagFailed
}

@MainActor
final class MockContentViewModelEntryService: EntryServiceProtocol {
    let entrySaved = PassthroughSubject<EntryNotification, Never>()
    let entryDeleted = PassthroughSubject<EntryNotification, Never>()

    var allEntriesResult: Result<[Entry], Error> = .success([])

    private(set) var migrateFromSQLiteCalls = 0
    private(set) var syncAllEntriesCalls = 0
    private(set) var loadDashboardDataCalls = 0
    private(set) var getAllEntriesCalls = 0

    func syncAllEntriesWithRemote() async {
        syncAllEntriesCalls += 1
    }

    func migrateFromSQLiteIfNeeded() async {
        migrateFromSQLiteCalls += 1
    }

    func loadDashboardData() async {
        loadDashboardDataCalls += 1
    }

    func getAllEntries() async throws -> [Entry] {
        getAllEntriesCalls += 1
        return try allEntriesResult.get()
    }

    func clearAllData() async {}
    func clearLastSyncTimestamp() async throws {}
    func saveNewEntry(_ entry: Entry) async throws {}
    func saveNewEntries(_ entries: [Entry]) async throws {}
    func deleteEntry(_ entry: Entry) async throws {}
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
final class MockContentViewModelFeedService: FeedServiceProtocol {
    let feedsChanged = PassthroughSubject<[FeedItem], Never>()
    let feedSettingsChanged = PassthroughSubject<GGFeedSetting?, Never>()
    let notificationBadgeUpdated = CurrentValueSubject<Bool, Never>(false)

    private(set) var fetchFeedItemsCalls = 0
    private(set) var checkAndTriggerFeedModalCalls = 0

    func fetchFeedItems() async {
        fetchFeedItemsCalls += 1
    }

    func updateFeedItem(_ feedItem: FeedItem, actionType: GGFeedActionType, variationId: Int?) async {}
    func getUnreadFeedCount() -> Int { 0 }
    func getFeedSettings() -> GGFeedSetting? { nil }

    func checkAndTriggerFeedModal() {
        checkAndTriggerFeedModalCalls += 1
    }

    func clearFeedData() {}
}

@MainActor
final class MockContentViewModelScaleService: ScaleServiceProtocol {
    @Published var scales: [Device] = []
    var scalesPublisher: AnyPublisher<[Device], Never> { $scales.eraseToAnyPublisher() }

    private(set) var syncAllScalesWithRemoteCalls = 0

    func clearAllData() async {}
    func getDevices() async throws -> [Device] { [] }
    func getConnectedDevices() async -> [String: Any] { [:] }
    func updateConnectedDevices(device: Any, isConnected: Bool) async {}
    func updateConnectedDeviceWifiStatus(broadcastId: String, isConfigured: Bool) async {}
    func syncDevices(tempDevice: Device?) async throws {}
    func createDevice(_ device: Device, _ skipDuplicateCheck: Bool) async throws -> Device { device }
    func editDevice(_ deviceId: String, properties: [String: Any]) async throws -> Device {
        throw UnexpectedCallError.methodCalled("editDevice")
    }

    func deleteDevice(_ deviceId: String, showToast: Bool) async throws {
        throw UnexpectedCallError.methodCalled("deleteDevice")
    }

    func updateScaleMeta(_ deviceId: String, metaData: DeviceMetaData) async throws {}
    func updateScalePreference(_ deviceId: String, _ preference: R4ScalePreference) async throws {}
    func updateScalePreference(_ deviceId: String, fromDTO dto: R4ScalePreferenceDTO) async throws {}
    func updateAllScalesStatus(_ scales: [Device]?) async throws {}

    func syncAllScalesWithRemote() async {
        syncAllScalesWithRemoteCalls += 1
    }

    func pushLocalChangesToServer() async {}
    func getDevice(by deviceId: String) async throws -> Device? { nil }
    func updateConnectedDeviceWeightOnlyMode(broadcastId: String, isWeightOnlyModeEnabledByOthers: Bool) async {}
    func fetchAttachedPreference(by id: String) async -> R4ScalePreference? { nil }
    func fetchAttachedPreferenceSync(by id: String) -> R4ScalePreference? { nil }
}

@MainActor
final class MockContentViewModelBluetoothService: BluetoothServiceProtocol {
    var canShowScaleDiscoveredModal: Bool = false
    var isSetupInProgress: Bool = false
    var skipDevices: [String] = []
    var onOpenScaleSetup: ((Device, DeviceDiscoveryEvent?, Bool, Bool) -> Void)?

    private(set) var initializeCalls = 0
    private(set) var startBluetoothOperationsCalls = 0

    var deviceDiscoveredPublisher: AnyPublisher<DeviceDiscoveryEvent, Never> { Empty().eraseToAnyPublisher() }
    var deviceInfoUpdatedPublisher: AnyPublisher<DeviceInfo, Never> { Empty().eraseToAnyPublisher() }
    var showWeightOnlyModeAlertPublisher: AnyPublisher<Bool, Never> { Empty().eraseToAnyPublisher() }
    var newEntryReceivedPublisher: AnyPublisher<EntryNotification, Never> { Empty().eraseToAnyPublisher() }
    var firmwareUpdateProgressPublisher: AnyPublisher<FirmwareUpdateStatus, Never> { Empty().eraseToAnyPublisher() }

    func initialize() { initializeCalls += 1 }
    func stopScan() {}

    func startBluetoothOperations() async {
        startBluetoothOperationsCalls += 1
    }

    func disconnectConnectedScales() async {}
    func reapplySkipDevicesExcludingPaired() {}
    func handleWeightOnlyModeAlertDismissed() {}
    func clearDevices() {}
    func pauseSmartScan() {}
    func resumeSmartScan(clearOnlyPairing: Bool) {}
    func scanForPairing() {}

    func resyncAndScan() async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func syncDevices(_ devices: [Device]) {}
    func addNewDevice(_ device: Device, metaData: DeviceMetaData?, _ skipDuplicateCheck: Bool?) async -> Result<Device, BluetoothServiceError> { .failure(.notImplemented) }
    func confirmSmartPair(device: Device, token: String, displayName: String, userNumber: Int?) async -> Result<UserCreationResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteDevice(_ device: Device, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteUserByToken(broadcastId: String, token: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteCurrentUserFromScaleIfPossible(_ device: Device, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func disconnectDevice(broadcastId: String, considerForSession: Bool) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func getWifiList(for device: Device) async -> Result<[WifiDetails], BluetoothServiceError> { .failure(.notImplemented) }
    func setupWifi(on device: Device, config: WifiConfig) async -> Result<WifiSetupResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func cancelWifi(on device: Device) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func getConnectedWifiSSID(broadcastId: String) async -> Result<String, BluetoothServiceError> { .failure(.notImplemented) }
    func updateSetting(on device: Device, settings: [DeviceSetting]) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func updateFirmware(on device: Device, timestamp: UInt32) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func clearData(on device: Device, dataType: DeviceClearType) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func updateUserProfileForR4Scales() async -> Result<[String], BluetoothServiceError> { .failure(.notImplemented) }
    func updateAccount(on device: Device, preference: R4ScalePreference) async -> Result<UserCreationResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func getDeviceInfo(for device: Device, skipConnectionCheck: Bool) async -> Result<DeviceInfo, BluetoothServiceError> { .failure(.notImplemented) }
    func getWifiMacAddress(for device: Device) async -> Result<String, BluetoothServiceError> { .failure(.notImplemented) }
    func startLiveMeasurement(for device: Device) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func stopLiveMeasurement(for device: Device) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func getMeasurementLiveData(broadcastId: String) async -> Result<MeasurementLiveData, BluetoothServiceError> { .failure(.notImplemented) }
    func getScaleUserList(for device: Device, skipConnectionCheck: Bool) async -> Result<[DeviceUser], BluetoothServiceError> { .failure(.notImplemented) }
    func getDeviceLogs(for device: Device) async -> Result<DeviceLogs, BluetoothServiceError> { .failure(.notImplemented) }
    func updateWeightOnlyMode(on device: Device?) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteR4Scales() async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func convertHexToInt(_ hex: String) -> Int64 { 0 }
}

@MainActor
final class MockContentViewModelAccountFlagService: AccountFlagServiceProtocol {
    var getAccountFlagResult: Result<AccountFlag?, Error> = .success(nil)
    var checkAccountFlagResult: Result<Bool, Error> = .success(false)

    private(set) var getAccountFlagCalls = 0
    private(set) var checkAccountFlagCalls = 0
    private(set) var lastCheckTrigger: String?

    func getAccountFlag() async throws -> AccountFlag? {
        getAccountFlagCalls += 1
        return try getAccountFlagResult.get()
    }

    func checkAccountFlag(trigger: String) async throws -> Bool {
        checkAccountFlagCalls += 1
        lastCheckTrigger = trigger
        return try checkAccountFlagResult.get()
    }

    func deleteFlag(flagId: String) async throws -> Bool { true }
    func triggerAppReview(isFromDebug: Bool) async {}
    func emitScaleReview(screen: String, sku: String, flagId: String) {}
}
