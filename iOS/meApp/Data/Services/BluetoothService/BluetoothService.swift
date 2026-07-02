//
//  BluetoothService.swift
//  iOS Bluetooth integration layer for GGBluetoothSwiftPackage
//
//  Created by AI Assistant
//
//  This file provides a production-ready BluetoothService for iOS apps, wrapping the GGBluetoothSwiftPackage SDK and translating between app models
//  and SDK models. It uses Combine for reactive updates and async/await for async plugin calls.
//

import Combine
import Foundation
import GGBluetoothSwiftPackage

// For mapping device metadata
import SwiftData

/**
 * Comprehensive implementation of `BluetoothServiceProtocol` backed by the `GGBluetoothSwiftPackage` SDK.
 *
 * This service provides complete Bluetooth functionality for smart scales, including:
 * - Device scanning and pairing
 * - Wi-Fi configuration
 * - Firmware updates
 * - Data synchronization
 * - User management
 * - Settings configuration
 *
 * - NOTE: The static `shared` property is retained for legacy compatibility, but should be phased out in favor of DI.
 */
@MainActor
final class BluetoothService: ObservableObject, BluetoothServiceProtocol {
    // MARK: - Singleton (Legacy Only)

    /// Legacy singleton for compatibility. Prefer dependency injection for new code.
    static let shared = BluetoothService(accountService: AccountService.shared,
                                         deviceService: DeviceService.shared,
                                         entryService: EntryService.shared,
                                         babyService: BabyService.shared,
                                         logger: LoggerService.shared)

    // MARK: - Published State

    /// Indicates if the scale discovered modal can be shown for newly discovered scales.
    @Published private(set) var canShowScaleDiscoveredModal: Bool = true

    /// Allows extensions to update the scale discovered modal visibility.
    func setCanShowScaleDiscoveredModal(_ value: Bool) {
        canShowScaleDiscoveredModal = value
    }

    /// Indicates whether a setup is currently in progress.
    @Published var isSetupInProgress: Bool = false

    // MARK: - Public Publishers

    /// Publisher for unified device discovery events containing device, protocol type, and isNew flag.
    var deviceDiscoveredPublisher: AnyPublisher<DeviceDiscoveryEvent, Never> {
        deviceDiscoveredSubject.eraseToAnyPublisher()
    }

    /// Publisher for device metadata updates.
    var deviceInfoUpdatedPublisher: AnyPublisher<DeviceInfo, Never> {
        deviceInfoUpdatedSubject.eraseToAnyPublisher()
    }

    /// Publisher for weight-only mode alert visibility.
    var showWeightOnlyModeAlertPublisher: AnyPublisher<Bool, Never> {
        showWeightOnlyModeAlertSubject.eraseToAnyPublisher()
    }

    /// Publisher for new entry events.
    /// Uses EntryNotification (Sendable) to safely pass data across actor boundaries.
    var newEntryReceivedPublisher: AnyPublisher<EntryNotification, Never> {
        newEntryReceivedSubject.eraseToAnyPublisher()
    }

    /// Publisher that fires when a weight scale entry arrives but has NOT yet been saved.
    /// Subscribers (e.g. BottomTabBarViewModel) must call confirmPendingScaleEntry() to save
    /// or discardPendingScaleEntry() to drop it. If neither is called within the toast duration
    /// the entry is saved automatically by the subscriber's timeout handler.
    var pendingScaleEntryPublisher: AnyPublisher<EntryNotification, Never> {
        pendingScaleEntrySubject.eraseToAnyPublisher()
    }

    /// Publisher that fires when a BPM reading arrives but has NOT yet been saved.
    /// Subscribers (e.g. BottomTabBarViewModel) must call confirmPendingBpmEntry() to save
    /// or discardPendingBpmEntry() to drop it. If neither is called within the toast duration
    /// the entry is saved automatically by the subscriber's timeout handler.
    var pendingBpmEntryPublisher: AnyPublisher<EntryNotification, Never> {
        pendingBpmEntrySubject.eraseToAnyPublisher()
    }

    /// Publisher for firmware update progress.
    var firmwareUpdateProgressPublisher: AnyPublisher<FirmwareUpdateStatus, Never> {
        firmwareUpdateProgressSubject.eraseToAnyPublisher()
    }

    /// Publisher for live measurement data.
    var liveMeasurementPublisher: AnyPublisher<GGWeightEntry, Never> {
        liveMeasurementSubject.eraseToAnyPublisher()
    }

    /// Publisher for new BPM reading events.
    var newBpmReadingReceivedPublisher: AnyPublisher<BpmMeasurement, Never> {
        newBpmReadingReceivedSubject.eraseToAnyPublisher()
    }

    var isSetupInProgressPublisher: AnyPublisher<Bool, Never> {
        $isSetupInProgress.eraseToAnyPublisher()
    }

    var skipDevices: [String] = []
    var blockedBroadcastIds: Set<String> = []
    var unblockTasks: [String: Task<Void, Never>] = [:]
    var reconnectAlertSkippedDevices: [String] = []

    // MARK: - Navigation Callback

    /// Callback to handle scale setup navigation. Set by the UI layer (e.g. BottomTabBarViewModel).
    var onOpenDeviceSetup: ((DeviceSnapshot, DeviceDiscoveryEvent?, Bool, Bool) -> Void)?

    // MARK: - Subjects for Scale Discovery

    let deviceDiscoveredSubject = PassthroughSubject<DeviceDiscoveryEvent, Never>()
    let newEntryReceivedSubject = PassthroughSubject<EntryNotification, Never>()
    let pendingScaleEntrySubject = PassthroughSubject<EntryNotification, Never>()
    let pendingBpmEntrySubject = PassthroughSubject<EntryNotification, Never>()
    let deviceInfoUpdatedSubject = PassthroughSubject<DeviceInfo, Never>()
    let showWeightOnlyModeAlertSubject = PassthroughSubject<Bool, Never>()
    let firmwareUpdateProgressSubject = PassthroughSubject<FirmwareUpdateStatus, Never>()
    /// Subject for live measurement data events.
    let liveMeasurementSubject = PassthroughSubject<GGWeightEntry, Never>()
    /// Subject for BPM reading events.
    let newBpmReadingReceivedSubject = PassthroughSubject<BpmMeasurement, Never>()

    /// The most recently received weight scale entry that is awaiting user confirmation.
    /// Set by the scan pipeline before firing pendingScaleEntrySubject; cleared by confirm/discard.
    var pendingScaleEntry: Entry?

    /// Earlier weight entries that were displaced when a new reading arrived before the user
    /// acted on the previous toast. Queued here instead of auto-saved so that tapping DISCARD
    /// discards all of them, matching user intent.
    var displacedPendingEntries: [Entry] = []

    /// The most recently received BPM entry that is awaiting user confirmation.
    /// Set by the scan pipeline before firing pendingBpmEntrySubject; cleared by confirm/discard.
    var pendingBpmEntry: Entry?

    // MARK: - Private Properties

    var cancellables = Set<AnyCancellable>()
    var activeAccount: AccountSnapshot?
    var isSmartScanStarted = false
    private var isInitialized = false
    var bluetoothScales: [DeviceSnapshot] = []
    var connectedGgDevices: [GGBTDevice] = []
    var isWeightOnlyModeAlertDismissed = false
    var lastProfileUpdateAccountId: String?
    var isUpdatingR4Profile = false
    var lastAccountId: String?
    var isSyncingPreferences = false // Guard against concurrent preference syncs
    var weightOnlyModeAlertDebounceTask: Task<Void, Never>? // Debounce task for weight-only mode alert check
    var profileUpdateTask: Task<Void, Never>?

    // MARK: - Dependencies

    let accountService: AccountServiceProtocol
    let deviceService: PairedDeviceServiceProtocol
    let entryService: EntryServiceProtocol
    let babyService: BabyServiceProtocol
    let logger: LoggerServiceProtocol
    let ggBleSDK: BluetoothSDKClient
    let timeoutConstants = AppConstants.TimeoutsAndRetention.self
    let tag = "BluetoothService"

    // Generic actor to serialize SDK operations per device to prevent callback conflicts
    // The SDK only maintains one completion handler per operation type at a time
    nonisolated let sdkOperationSerializer = SDKOperationSerializer()

    private func logOperationFailure(_ operation: String, error: Error) {
        logger.log(level: .error, tag: tag, message: "\(operation) failed: \(error.localizedDescription)")
    }

    // MARK: - Alert Dependencies

    let notificationService: NotificationHelperServiceProtocol
    var scaleInfoUtils: DeviceInfoUtils { DeviceInfoUtils.shared }

    // MARK: - BLE Components

    let discoveryManager: BLEDiscoveryManaging

    // MARK: - Initialization

    /**
     Initializes the BluetoothService with all required dependencies.
     - Parameters:
     - accountService: The account service dependency.
     - deviceService: The scale service dependency.
     - entryService: The entry service dependency.
     - logger: The logger service dependency.
     */
    init(
        accountService: AccountServiceProtocol,
        deviceService: PairedDeviceServiceProtocol,
        entryService: EntryServiceProtocol,
        babyService: BabyServiceProtocol,
        logger: LoggerServiceProtocol,
        discoveryManager: BLEDiscoveryManaging? = nil,
        ggBleSDK: BluetoothSDKClient? = nil,
        notificationService: NotificationHelperServiceProtocol? = nil
    ) {
        self.accountService = accountService
        self.deviceService = deviceService
        self.entryService = entryService
        self.babyService = babyService
        self.logger = logger
        self.discoveryManager = discoveryManager ?? BLEDiscoveryManager()
        self.ggBleSDK = ggBleSDK ?? GGBluetoothSDKClient()
        self.notificationService = notificationService ?? NotificationHelperService.shared
        setupSubscriptions()
        initialize()
    }

    // MARK: - Setup

    private func setupSubscriptions() {
        // Subscribe to scale changes
        deviceService.scalesPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] scales in
                Task { await self?.handleScalesUpdate(scales) }
            }
            .store(in: &cancellables)
    }

    /**
     Initializes the Bluetooth service and subscribes to account changes.
     Idempotent: repeat calls are no-ops so we don't register duplicate account subscriptions.
     */
    func initialize() {
        guard !isInitialized else {
            logger.log(level: .debug, tag: tag, message: "Bluetooth service initialize called again; skipping (already initialized)")
            return
        }
        isInitialized = true
        logger.log(level: .info, tag: tag, message: "Bluetooth service initialize called")
        accountService.activeAccountPublisher
            .receive(on: DispatchQueue.main)
            .sink { @MainActor [weak self] account in
                guard let self else { return }
                self.handleAccountUpdate(account)
                self.scheduleProfileUpdateIfNeeded(for: account)
            }
            .store(in: &cancellables)
    }

    // MARK: - Pending Scale Entry Confirmation

    /// Saves the pending weight scale entry to persistent storage.
    /// Called when the user taps SAVE on the reading-arrival toast, or when the toast times out.
    func confirmPendingScaleEntry() async throws {
        guard let entry = pendingScaleEntry else { return }
        pendingScaleEntry = nil
        let displaced = displacedPendingEntries
        displacedPendingEntries = []
        for displacedEntry in displaced {
            do {
                try await entryService.saveNewEntry(displacedEntry)
                let displacedNotification = EntryNotification(from: displacedEntry)
                newEntryReceivedSubject.send(displacedNotification)
                logger.log(level: .info, tag: tag, message: "Displaced scale entry saved on confirm. entryId=\(displacedEntry.id.uuidString)")
            } catch {
                logger.log(
                    level: .error,
                    tag: tag,
                    message: "Failed to save displaced scale entry on confirm. entryId=\(displacedEntry.id.uuidString)",
                    data: error.localizedDescription
                )
            }
        }
        try await entryService.saveNewEntry(entry)
        let notification = EntryNotification(from: entry)
        newEntryReceivedSubject.send(notification)
        logger.log(level: .info, tag: tag, message: "Pending scale entry confirmed. entryId=\(entry.id.uuidString)")
    }

    /// Drops the pending weight scale entry without saving it.
    /// Called when the user taps DISCARD on the reading-arrival toast.
    func discardPendingScaleEntry() {
        guard let entry = pendingScaleEntry else { return }
        let displaced = displacedPendingEntries
        displacedPendingEntries = []
        for displacedEntry in displaced {
            logger.log(level: .info, tag: tag, message: "Displaced scale entry discarded. entryId=\(displacedEntry.id.uuidString)")
        }
        logger.log(level: .info, tag: tag, message: "Pending scale entry discarded. entryId=\(entry.id.uuidString)")
        pendingScaleEntry = nil
    }

    // MARK: - Pending BPM Entry Confirmation

    /// Saves the pending BPM entry to persistent storage.
    /// Called when the user taps SAVE on the BPM reading-arrival toast, or when the toast times out.
    func confirmPendingBpmEntry() async throws {
        guard let entry = pendingBpmEntry else { return }
        pendingBpmEntry = nil
        try await entryService.saveNewEntry(entry)
        let notification = EntryNotification(from: entry)
        newEntryReceivedSubject.send(notification)
        logger.log(level: .info, tag: tag, message: "Pending BPM entry confirmed. entryId=\(entry.id.uuidString)")
    }

    /// Drops the pending BPM entry without saving it.
    /// Called when the user taps DISCARD on the BPM reading-arrival toast.
    func discardPendingBpmEntry() {
        guard let entry = pendingBpmEntry else { return }
        logger.log(level: .info, tag: tag, message: "Pending BPM entry discarded. entryId=\(entry.id.uuidString)")
        pendingBpmEntry = nil
    }

    /**
     Starts Bluetooth scanning and device synchronization.
     This should be called when the dashboard is ready to receive Bluetooth events.
     */
    func startBluetoothOperations() async {
        guard activeAccount != nil else {
            logger.log(level: .info, tag: tag, message: "Cannot start Bluetooth operations: no active account")
            return
        }

        if !isSmartScanStarted {
            logger.log(level: .info, tag: tag, message: "Starting Bluetooth operations: clearing devices, scanning, syncing")
            clearDevices()
            await scan()
            syncDevices([])
        } else {
            logger.log(level: .info, tag: tag, message: "Bluetooth operations already running; skipping restart")
        }
    }

    private func handleScalesUpdate(_ scales: [DeviceSnapshot]) async {
        guard !scales.isEmpty else {
            bluetoothScales = []
            syncDevices([])
            logger.log(level: .info, tag: tag, message: "Bluetooth scales update received empty list; synced zero devices")
            return
        }
        let allowedTypes: Set<DeviceSourceType> = Set([
            .bluetooth,
            .bluetoothScale,
            .lcbt,
            .lcbtScale,
            .btWifiR4
        ])
        let accountId = activeAccount?.accountId ?? ""
        let filteredScales = scales.filter { scale in
            // Reject scales that belong to a different account — stale unsynced records
            // from a previous account can otherwise bleed into the active session and
            // cause a valid new device to be treated as already-known (MOB-427 fix).
            guard !accountId.isEmpty,
                  scale.accountId == accountId,
                  let raw = getSafeDeviceModelType(for: scale),
                  let type = DeviceSourceType(rawValue: raw)
            else { return false }
            return allowedTypes.contains(type)
        }
        Task {
            await disconnectDeletedScales(currentScales: bluetoothScales, newScales: filteredScales)
        }
        bluetoothScales = filteredScales
        logger.log(
            level: .info,
            tag: tag,
            message: "Bluetooth scales updated. total=\(scales.count), filtered=\(filteredScales.count), setupInProgress=\(isSetupInProgress)"
        )

        if !isWeightOnlyModeAlertDismissed {
            await checkCanShowWeightOnlyModeAlert()
        }

        if !isSetupInProgress {
            syncDevices(bluetoothScales)
        }
    }

    private func handleAccountUpdate(_ account: AccountSnapshot?) {
        if let account = account {
            activeAccount = account
            logger.log(level: .info, tag: tag, message: "Bluetooth active account updated. accountId=\(account.accountId)")
            // Don't start scanning immediately - wait for dashboard to be ready
            // The scan will be triggered by startBluetoothOperations() when called from ContentViewModel
        } else if isSmartScanStarted {
            logger.log(level: .info, tag: tag, message: "Bluetooth account cleared; stopping active scan")
            stopScan()
        }
    }

    private func scheduleProfileUpdateIfNeeded(for account: AccountSnapshot?) {
        let currentAccountId = account?.accountId
        guard let accountId = currentAccountId else {
            profileUpdateTask?.cancel()
            lastAccountId = nil
            return
        }

        let accountIdChanged = accountId != lastAccountId
        lastAccountId = accountId
        guard accountIdChanged, !isUpdatingR4Profile else { return }

        profileUpdateTask?.cancel()
        profileUpdateTask = Task { [weak self] in
            _ = await self?.updateUserProfileForR4Scales()
        }
    }

    // MARK: - BluetoothServiceProtocol Implementation

    /**
     Stops all ongoing Bluetooth operations and scanning.
     */
    func stopScan() {
        discoveryManager.stopScan(using: ggBleSDK)
        isSmartScanStarted = false
        logger.log(level: .info, tag: tag, message: "Bluetooth scan stopped")
    }

    /**
     Clears all devices from the underlying Bluetooth plugin / cache.
     */
    func clearDevices() {
        skipDevices = []
        discoveryManager.clearDevices(using: ggBleSDK)
    }
}
