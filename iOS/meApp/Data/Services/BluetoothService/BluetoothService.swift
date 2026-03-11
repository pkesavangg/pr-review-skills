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
                                         scaleService: ScaleService.shared,
                                         entryService: EntryService.shared,
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

    /// Publisher for firmware update progress.
    var firmwareUpdateProgressPublisher: AnyPublisher<FirmwareUpdateStatus, Never> {
        firmwareUpdateProgressSubject.eraseToAnyPublisher()
    }

    /// Publisher for live measurement data.
    var liveMeasurementPublisher: AnyPublisher<GGWeightEntry, Never> {
        liveMeasurementSubject.eraseToAnyPublisher()
    }

    var skipDevices: [String] = []
    var blockedBroadcastIds: Set<String> = []
    var unblockTasks: [String: Task<Void, Never>] = [:]
    var reconnectAlertSkippedDevices: [String] = []

    // MARK: - Navigation Callback

    /// Callback to handle scale setup navigation. Set by the UI layer (e.g. BottomTabBarViewModel).
    var onOpenScaleSetup: ((Device, DeviceDiscoveryEvent?, Bool, Bool) -> Void)?

    // MARK: - Subjects for Scale Discovery

    let deviceDiscoveredSubject = PassthroughSubject<DeviceDiscoveryEvent, Never>()
    let newEntryReceivedSubject = PassthroughSubject<EntryNotification, Never>()
    let deviceInfoUpdatedSubject = PassthroughSubject<DeviceInfo, Never>()
    let showWeightOnlyModeAlertSubject = PassthroughSubject<Bool, Never>()
    let firmwareUpdateProgressSubject = PassthroughSubject<FirmwareUpdateStatus, Never>()
    /// Subject for live measurement data events.
    let liveMeasurementSubject = PassthroughSubject<GGWeightEntry, Never>()

    // MARK: - Private Properties

    var cancellables = Set<AnyCancellable>()
    var activeAccount: Account?
    var isSmartScanStarted = false
    var bluetoothScales: [Device] = []
    var connectedGgDevices: [GGBTDevice] = []
    var isWeightOnlyModeAlertDismissed = false
    var lastProfileUpdateAccountId: String?
    var isUpdatingR4Profile = false
    var lastAccountId: String?
    var isSyncingPreferences = false // Guard against concurrent preference syncs
    var weightOnlyModeAlertDebounceTask: Task<Void, Never>? // Debounce task for weight-only mode alert check

    // MARK: - Dependencies

    let accountService: AccountServiceProtocol
    let scaleService: ScaleServiceProtocol
    let entryService: EntryServiceProtocol
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
    var scaleInfoUtils: ScaleInfoUtils { ScaleInfoUtils.shared }

    // MARK: - BLE Components

    let discoveryManager: BLEDiscoveryManaging

    // MARK: - Initialization

    /**
     Initializes the BluetoothService with all required dependencies.
     - Parameters:
     - accountService: The account service dependency.
     - scaleService: The scale service dependency.
     - entryService: The entry service dependency.
     - logger: The logger service dependency.
     */
    init(
        accountService: AccountServiceProtocol,
        scaleService: ScaleServiceProtocol,
        entryService: EntryServiceProtocol,
        logger: LoggerServiceProtocol,
        discoveryManager: BLEDiscoveryManaging? = nil,
        ggBleSDK: BluetoothSDKClient? = nil,
        notificationService: NotificationHelperServiceProtocol? = nil
    ) {
        self.accountService = accountService
        self.scaleService = scaleService
        self.entryService = entryService
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
        scaleService.scalesPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] scales in
                Task { await self?.handleScalesUpdate(scales) }
            }
            .store(in: &cancellables)
    }

    /**
     Initializes the Bluetooth service and subscribes to account changes.
     */
    func initialize() {
        logger.log(level: .info, tag: tag, message: "Bluetooth service initialize called")
        accountService.activeAccountPublisher
            .receive(on: DispatchQueue.main)
            .sink { [weak self] account in
                Task {
                    await self?.handleAccountUpdate(account)
                    // Only update R4 profile from subscription if:
                    // 1. Account is not nil
                    // 2. Not already updating (prevents concurrent calls)
                    // 3. Account ID changed (account switch) or was nil (new account)
                    // This prevents conflicts when updateUserProfileForR4Scales is called explicitly
                    let currentAccountId = account?.accountId
                    let accountIdChanged = currentAccountId != self?.lastAccountId
                    if let accountId = currentAccountId,
                       !(self?.isUpdatingR4Profile ?? false),
                       accountIdChanged {
                        self?.lastAccountId = accountId
                        _ = await self?.updateUserProfileForR4Scales()
                    } else if currentAccountId != nil {
                        // Update lastAccountId even if we don't call updateUserProfileForR4Scales
                        self?.lastAccountId = currentAccountId
                    }
                }
            }
            .store(in: &cancellables)
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

    private func handleScalesUpdate(_ scales: [Device]?) async {
        guard let scales = scales, !scales.isEmpty else {
            bluetoothScales = []
            syncDevices([])
            logger.log(level: .info, tag: tag, message: "Bluetooth scales update received empty list; synced zero devices")
            return
        }
        // Filter scales by allowed types only (common across all models)
        let allowedTypes: Set<ScaleSourceType> = Set([
            .bluetooth,
            .bluetoothScale,
            .lcbt,
            .lcbtScale,
            .btWifiR4
        ])
        let filteredScales = scales.filter { scale in
            guard let raw = getSafeScaleType(for: scale), let type = ScaleSourceType(rawValue: raw) else {
                return false
            }
            return allowedTypes.contains(type)
        }
        Task {
            // Disconnect deleted scales in the background to avoid blocking the main thread
            await disconnectDeletedScales(currentScales: bluetoothScales, newScales: filteredScales)
        }
        bluetoothScales = filteredScales
        logger.log(
            level: .info,
            tag: tag,
            message: "Bluetooth scales updated. total=\(scales.count), filtered=\(filteredScales.count), setupInProgress=\(isSetupInProgress)"
        )

        // Check if banner should be shown/hidden after scale updates
        if !isWeightOnlyModeAlertDismissed {
            await checkCanShowWeightOnlyModeAlert()
        }

        if !isSetupInProgress {
            syncDevices(bluetoothScales)
        }
    }

    private func handleAccountUpdate(_ account: Account?) async {
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
