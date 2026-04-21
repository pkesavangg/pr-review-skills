import Combine
import Foundation
import GGBluetoothSwiftPackage
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
    var allEntrySnapshotsResult: Result<[EntrySnapshot], Error> = .success([])

    private(set) var migrateFromSQLiteCalls = 0
    private(set) var syncAllEntriesCalls = 0
    private(set) var loadDashboardDataCalls = 0
    private(set) var getAllEntriesCalls = 0
    private(set) var fetchAllEntrySnapshotsCalls = 0

    func syncAllEntriesWithRemote() async {
        syncAllEntriesCalls += 1
    }

    func migrateFromSQLiteIfNeeded() async {
        migrateFromSQLiteCalls += 1
    }

    func loadDashboardData(entryType: EntryType) async {
        loadDashboardDataCalls += 1
    }

    func loadBabyDashboardData(babyId: String) async {}

    func getAllEntries() async throws -> [Entry] {
        getAllEntriesCalls += 1
        return try allEntriesResult.get()
    }

    func clearAllData() async {}
    func clearLastSyncTimestamp() async throws {}
    func saveNewEntry(_ entry: Entry) async throws {}
    func saveNewEntries(_ entries: [Entry]) async throws {}
    func deleteEntry(_ entry: Entry) async throws {}
    func deleteEntry(entryId: UUID) async throws {}
    func fetchEntrySnapshot(byId id: UUID) async throws -> EntrySnapshot? { nil }
    func fetchAllEntrySnapshots() async throws -> [EntrySnapshot] {
        fetchAllEntrySnapshotsCalls += 1
        return try allEntrySnapshotsResult.get()
    }
    func fetchEntrySnapshots(forMonth month: String, entryType: EntryType) async throws -> [EntrySnapshot] { [] }
    func createBabyEntry(babyId: String, weight: Int, length: Int, note: String, entryTimestamp: String) async throws {}
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
    func fetchBpmEntries() async throws -> [BpmOperationDTO] { [] }
    func deleteBpmEntry(entryTimestamp: String) async throws {}
    func exportBpmCSV() async throws {}
    func migrateBabyEntriesToDecigrams() async {}
    func getEntry(byId id: UUID) async throws -> Entry? { nil }
    func createBabyEntry(babyId: String, weight: Int, length: Int, note: String, entryTimestamp: String, source: String?) async throws {}
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
    @Published var scales: [DeviceSnapshot] = []
    var scalesPublisher: AnyPublisher<[DeviceSnapshot], Never> { $scales.eraseToAnyPublisher() }

    func syncDevices(tempDevice: Device?) async throws {}

    private(set) var syncAllScalesWithRemoteCalls = 0

    func clearAllData() async {}
    func getDevices() async throws -> [DeviceSnapshot] { [] }
    func getConnectedDevices() async -> [String: Any] { [:] }
    func updateConnectedDevices(device: Any, isConnected: Bool) async {}
    func updateConnectedDeviceWifiStatus(broadcastId: String, isConfigured: Bool) async {}
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
        skipDuplicateCheck: Bool,
        deviceType: DeviceType = .scale
    ) async throws -> Device {
        throw UnexpectedCallError.methodCalled("createBluetoothScale")
    }

    func createA6Scale(
        device: Device,
        sku: String?,
        accountId: String,
        deviceMetadata: DeviceMetaData?,
        skipDuplicateCheck: Bool
    ) async throws -> Device {
        throw UnexpectedCallError.methodCalled("createA6Scale")
    }

    func updateAllScalesStatus(_ scales: [Device]?) async throws {}
    func createScaleInLocal(_ device: Device) async throws -> Device { device }

    func syncAllScalesWithRemote() async {
        syncAllScalesWithRemoteCalls += 1
    }

    func pushLocalChangesToServer() async {}
    func getDevice(by deviceId: String) async throws -> DeviceSnapshot? { nil }
    func updateConnectedDeviceWeightOnlyMode(broadcastId: String, isWeightOnlyModeEnabledByOthers: Bool) async {}
    func fetchAttachedPreference(by id: String) async -> R4ScalePreference? { nil }
    func fetchAttachedPreferenceSync(by id: String) -> R4ScalePreference? { nil }
    func deleteSingleDeviceEntry(_ deviceId: String) async throws {}
}

@MainActor
final class MockContentViewModelBluetoothService: BluetoothServiceProtocol {
    var canShowScaleDiscoveredModal: Bool = false
    var isSetupInProgress: Bool = false
    var skipDevices: [String] = []
    var onOpenScaleSetup: ((DeviceSnapshot, DeviceDiscoveryEvent?, Bool, Bool) -> Void)?

    private(set) var initializeCalls = 0
    private(set) var startBluetoothOperationsCalls = 0

    var deviceDiscoveredPublisher: AnyPublisher<DeviceDiscoveryEvent, Never> { Empty().eraseToAnyPublisher() }
    var deviceInfoUpdatedPublisher: AnyPublisher<DeviceInfo, Never> { Empty().eraseToAnyPublisher() }
    var showWeightOnlyModeAlertPublisher: AnyPublisher<Bool, Never> { Empty().eraseToAnyPublisher() }
    var newEntryReceivedPublisher: AnyPublisher<EntryNotification, Never> { Empty().eraseToAnyPublisher() }
    var pendingScaleEntryPublisher: AnyPublisher<EntryNotification, Never> { Empty().eraseToAnyPublisher() }
    var firmwareUpdateProgressPublisher: AnyPublisher<FirmwareUpdateStatus, Never> { Empty().eraseToAnyPublisher() }
    var liveMeasurementPublisher: AnyPublisher<GGWeightEntry, Never> { Empty().eraseToAnyPublisher() }
    var newBpmReadingReceivedPublisher: AnyPublisher<BpmMeasurement, Never> { Empty().eraseToAnyPublisher() }

    func initialize() { initializeCalls += 1 }
    func stopScan() {}
    func confirmPendingScaleEntry() async throws {}
    func discardPendingScaleEntry() {}

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
    func scanForBpm() {}
    func connectBpm(broadcastId: String, userNumber: Int, replaceUser: Bool, pairedSKUMonitors: [DeviceSnapshot]) async -> Result<UserCreationResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func receiveBpmReading(broadcastId: String) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }

    func resyncAndScan() async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func syncDevices(_ devices: [DeviceSnapshot]) {}
    func addNewDevice(_ device: Device, metaData: DeviceMetaData?, _ skipDuplicateCheck: Bool?) async -> Result<Device, BluetoothServiceError> { .failure(.notImplemented) }
    func confirmSmartPair(device: Device, token: String, displayName: String, userNumber: Int?) async -> Result<UserCreationResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteDevice(broadcastId: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteUserByToken(broadcastId: String, token: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func deleteCurrentUserFromScaleIfPossible(broadcastId: String, disconnect: Bool) async -> Result<UserDeletionResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func disconnectDevice(broadcastId: String, considerForSession: Bool) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func getWifiList(broadcastId: String) async -> Result<[WifiDetails], BluetoothServiceError> { .failure(.notImplemented) }
    func setupWifi(broadcastId: String, config: WifiConfig) async -> Result<WifiSetupResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func cancelWifi(broadcastId: String) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func getConnectedWifiSSID(broadcastId: String) async -> Result<String, BluetoothServiceError> { .failure(.notImplemented) }
    func updateSetting(broadcastId: String, settings: [DeviceSetting]) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func updateFirmware(broadcastId: String, timestamp: UInt32) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func clearData(broadcastId: String, dataType: DeviceClearType) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func updateUserProfileForR4Scales() async -> Result<[String], BluetoothServiceError> { .failure(.notImplemented) }
    func updateAccount(broadcastId: String) async -> Result<UserCreationResponse, BluetoothServiceError> { .failure(.notImplemented) }
    func getDeviceInfo(broadcastId: String, skipConnectionCheck: Bool) async -> Result<DeviceInfo, BluetoothServiceError> { .failure(.notImplemented) }
    func getWifiMacAddress(broadcastId: String) async -> Result<String, BluetoothServiceError> { .failure(.notImplemented) }
    func startLiveMeasurement(broadcastId: String) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func stopLiveMeasurement(broadcastId: String) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
    func getMeasurementLiveData(broadcastId: String) async -> Result<MeasurementLiveData, BluetoothServiceError> { .failure(.notImplemented) }
    func getScaleUserList(broadcastId: String, skipConnectionCheck: Bool) async -> Result<[DeviceUser], BluetoothServiceError> { .failure(.notImplemented) }
    func getDeviceLogs(broadcastId: String) async -> Result<DeviceLogs, BluetoothServiceError> { .failure(.notImplemented) }
    func updateWeightOnlyMode(broadcastId: String?) async -> Result<Void, BluetoothServiceError> { .failure(.notImplemented) }
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
