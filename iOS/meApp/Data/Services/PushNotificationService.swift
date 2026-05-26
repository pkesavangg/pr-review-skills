import CoreBluetooth
import FirebaseMessaging
import Foundation
import GGBluetoothSwiftPackage
import Network
import UIKit
import UserNotifications

/// Manages FCM token operations and notifications
@MainActor
class PushNotificationService: NSObject, PushNotificationServiceProtocol {
    /// Shared instance for accessing the NotificationService throughout the app
    static let shared = PushNotificationService()
    static let fcmTokenDidRefresh = Notification.Name("FCMToken")
    @Injector var entryService: EntryServiceProtocol
    @Injector private var permissionsService: PermissionsServiceProtocol
    @Injector private var accountService: AccountServiceProtocol
    @Injector private var notificationService: NotificationHelperServiceProtocol
    @Injector private var bluetoothService: BluetoothServiceProtocol
    @Injector private var scaleService: ScaleServiceProtocol
    @Injector private var keychainService: KeychainServiceProtocol
    // API repository for push-notification related network calls
    private let apiRepo: PushNotificationRepositoryAPIProtocol
    private let tokenProvider: PushTokenProviderProtocol
    private let pushRegistrar: PushRemoteNotificationRegistrarProtocol
    private let userNotificationCenter: PushUserNotificationCenterProtocol

    // MARK: - Properties

    private var fcmToken: String?
    private let networkMonitor = NWPathMonitor()
    private var isNetworkConnected: Bool = false
    private var deviceInfo: [String: String] = [:]
    private var notificationHandlers: [String: ([AnyHashable: Any]) -> Void] = [:]
    private var isDeviceInfoUpdating: Bool = false
    private var isFetchingEntries: Bool = false
    private var processedMessageIds: [String] = []
    private var isProcessingNotification: Bool = false
    private let logger: LoggerServiceProtocol
    private let tag = "PushNotificationService"
    private let kvStorage: KvStorageServiceProtocol

    // MARK: - Notification Settings

    init(
        apiRepo: PushNotificationRepositoryAPIProtocol? = nil,
        entryService: EntryServiceProtocol? = nil,
        permissionsService: PermissionsServiceProtocol? = nil,
        accountService: AccountServiceProtocol? = nil,
        notificationService: NotificationHelperServiceProtocol? = nil,
        bluetoothService: BluetoothServiceProtocol? = nil,
        scaleService: ScaleServiceProtocol? = nil,
        keychainService: KeychainServiceProtocol? = nil,
        kvStorage: KvStorageServiceProtocol? = nil,
        logger: LoggerServiceProtocol? = nil,
        tokenProvider: PushTokenProviderProtocol? = nil,
        pushRegistrar: PushRemoteNotificationRegistrarProtocol? = nil,
        userNotificationCenter: PushUserNotificationCenterProtocol? = nil,
        setupNetworkMonitoring: Bool = true
    ) {
        self.apiRepo = apiRepo ?? PushNotificationRepositoryAPI()
        self.tokenProvider = tokenProvider ?? FirebasePushTokenProvider()
        self.pushRegistrar = pushRegistrar ?? UIApplicationPushRegistrar()
        self.userNotificationCenter = userNotificationCenter ?? SystemPushUserNotificationCenter()
        self.logger = logger ?? LoggerService.shared
        self.kvStorage = kvStorage ?? KvStorageService.shared
        super.init()
        if let entryService {
            self.entryService = entryService
        }
        if let permissionsService {
            self.permissionsService = permissionsService
        }
        if let accountService {
            self.accountService = accountService
        }
        if let notificationService {
            self.notificationService = notificationService
        }
        if let bluetoothService {
            self.bluetoothService = bluetoothService
        }
        if let scaleService {
            self.scaleService = scaleService
        }
        if let keychainService {
            self.keychainService = keychainService
        }
        loadStoredFCMToken()
        fetchDeviceDetails()
        setupTokenRefresh()
        if setupNetworkMonitoring {
            self.setupNetworkMonitoring()
        }
    }

    // MARK: - Notification Handling

    func handleNotification(_ userInfo: [AnyHashable: Any], completion: @escaping () -> Void) {
        guard !isProcessingNotification else {
            logger.log(level: .info, tag: tag, message: "Skipping notification handling: already processing")
            completion()
            return
        }

        if let messageId = userInfo["gcm.message_id"] as? String {
            if processedMessageIds.contains(messageId) {
                logger.log(level: .info, tag: tag, message: "Skipping duplicate push notification. messageId=\(messageId)")
                completion()
                return
            }
            processedMessageIds.append(messageId)
            if processedMessageIds.count > 100 {
                processedMessageIds.removeFirst()
            }
        }

        isProcessingNotification = true
        logger.log(level: .info, tag: tag, message: "Push notification handling started")
        Task {
            do {
                await fetchEntries(showToast: true)
                await syncDevices()
                // Always show a local banner to ensure the user sees the notification, regardless of
                // the app state. This handles silent/data-only pushes and ensures visibility even when
                // iOS suppresses remote-push banners in the foreground.
                let aps = userInfo["aps"] as? [String: Any]
                let alert = aps?["alert"] as? [String: Any]
                let hasAlertContent = alert != nil
                // This prevents duplicate notifications when iOS already shows the push notification banner
                // Only show local notification if there's no alert content in the push payload
                if !hasAlertContent {
                    let title = alert?["title"] as? String ?? "New Notification"
                    let body = alert?["body"] as? String ?? "You have a new message"
                    let content = UNMutableNotificationContent()
                    content.title = title
                    content.body = body
                    content.userInfo = userInfo
                    content.sound = .default
                    // Create notification request
                    let request = UNNotificationRequest(
                        identifier: UUID().uuidString,
                        content: content,
                        trigger: nil
                    )
                    try await userNotificationCenter.add(request)
                    logger.log(level: .info, tag: tag, message: "Displayed local notification banner from data-only push")
                }
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to handle notification: \(error.localizedDescription)")
            }
            isProcessingNotification = false
            logger.log(level: .info, tag: tag, message: "Push notification handling completed")
            completion()
        }
    }

    func updateDeviceInfo() async {
        let token = fcmToken ?? accountService.activeAccount?.fcmToken ?? ""

        guard !isDeviceInfoUpdating else {
            logger.log(level: .info, tag: tag, message: "Skipping device info update: update already in progress")
            return
        }
        isDeviceInfoUpdating = true
        defer { isDeviceInfoUpdating = false }
        // Refresh local snapshot
        fetchDeviceDetails()
        // Prepare payload
        let payload = DeviceInfoRequest(
            appVersion: AppInfo.appVersion,
            deviceManufacturer: deviceInfo["manufacturer"] ?? "Apple",
            deviceOSName: deviceInfo["deviceOSName"] ?? UIDevice.current.systemName,
            deviceOSVersion: deviceInfo["osVersion"] ?? UIDevice.current.systemVersion,
            deviceUUID: deviceInfo["deviceUuid"] ?? UIDevice.current.identifierForVendor?.uuidString ?? "",
            deviceModel: deviceInfo["model"] ?? UIDevice.current.model,
            fcmToken: token
        )
        do {
            try await apiRepo.updateDeviceInfo(payload)
            let modelId = deviceInfo["modelIdentifier"] ?? "unknown"
            logger.log(level: .success, tag: tag, message: "Device info updated: model=\(payload.deviceModel) (\(modelId)), manufacturer=\(payload.deviceManufacturer), os=\(payload.deviceOSName) \(payload.deviceOSVersion), appVersion=\(payload.appVersion)")
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to update device info: \(error.localizedDescription)")
            // Silently ignore network errors – will retry on next connectivity change
        }
    }

    // MARK: - Push Notification Registration

    func setupPushNotifications(isFromScaleSetup: Bool = false) async {
        logger.log(level: .info, tag: tag, message: "Push setup started. isFromScaleSetup=\(isFromScaleSetup)")
        // Always update device info regardless of notification permission.
        await updateDeviceInfo()

        // If token already exists, no need to re-register for push.
        if fcmToken != nil {
            logger.log(level: .info, tag: tag, message: "Push setup skipped: FCM token already available")
            return
        }

        guard permissionsService.requiredCategories.contains(.notifications) else {
            logger.log(level: .info, tag: tag, message: "Push setup skipped: notifications category not required")
            return
        }
        // Determine current permission state
        var permissionResult = permissionsService.getPermissionState(.NOTIFICATION) ?? .DISABLED

        // If permission is not enabled, optionally present the disabled alert
        if permissionResult != .ENABLED {
            let accountId = accountService.activeAccount?.accountId ?? ""
            let viewedKey = KvStorageKeys.notificationOnlyAlertShownKey(for: accountId)
            let hasViewedAlert = (kvStorage.getValue(forKey: viewedKey) as? Bool) ?? false
            if !hasViewedAlert || isFromScaleSetup {
                logger.log(level: .info, tag: tag, message: "Requesting notification permission during push setup")
                permissionResult = await permissionsService.handlePermission(.notification)
                kvStorage.setValue(true, forKey: viewedKey)
            }
        }

        // Register only when permission has been granted and system notification authorization is allowed
        if permissionResult == .ENABLED, isNotificationAuthorized() {
            await registerForPushNotifications()
        } else {
            logger.log(
                level: .error,
                tag: tag,
                message: "Push setup not registering for remote notifications. "
                    + "permissionResult=\(permissionResult.rawValue), authorized=\(isNotificationAuthorized())"
            )
        }
    }

    // MARK: - Notification Settings

    private func setupTokenRefresh() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(tokenRefreshNotification),
            name: PushNotificationService.fcmTokenDidRefresh,
            object: nil
        )
    }

    private func setupNetworkMonitoring() {
        networkMonitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor [weak self] in
                self?.isNetworkConnected = path.status == .satisfied
                self?.logger.log(
                    level: .info,
                    tag: self?.tag ?? "PushNotificationService",
                    message: "Network status changed for push service. connected=\(path.status == .satisfied)"
                )
                if path.status == .satisfied {
                    await self?.networkOperations()
                }
            }
        }
        networkMonitor.start(queue: DispatchQueue.global())
    }

    // MARK: - Device Info Fetching

    private func fetchDeviceDetails() {
        let device = UIDevice.current
        deviceInfo = [
            "osVersion": device.systemVersion,
            "deviceUuid": device.identifierForVendor?.uuidString ?? "",
            "manufacturer": "Apple",
            "model": device.model,
            "modelIdentifier": Self.hardwareModelIdentifier(),
            "deviceOSName": device.systemName
        ]
    }

    // MARK: - FCM Token & Device Info Update

    /// Handles FCM token refresh notifications
    /// - Parameter notification: The notification containing the new token
    @objc private func tokenRefreshNotification(_ notification: Notification) {
        Task { @MainActor [weak self] in
            guard let token = notification.userInfo?["token"] as? String, !token.isEmpty else { return }
            self?.fcmToken = token
            self?.logger.log(level: .info, tag: self?.tag ?? "PushNotificationService", message: "Received FCM token refresh notification")
            self?.storeFCMToken(token)
            await self?.updateDeviceInfo()
        }
    }

    /// Retrieves the current FCM token
    /// - Returns: The current FCM token as a string
    /// - Throws: Error if token retrieval fails
    private func getFCMToken() async throws -> String {
        let token = try await tokenProvider.fetchFCMToken()
        fcmToken = token
        return token
    }

    private func registerForPushNotifications() async {
        logger.log(level: .info, tag: tag, message: "Registering for remote push notifications")
        pushRegistrar.registerForRemoteNotifications()
        do {
            let token = try await getFCMToken()
            fcmToken = token
            logger.log(level: .success, tag: tag, message: "Push registration succeeded. tokenReceived=true")
            storeFCMToken(token)
            await updateDeviceInfo()
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to register for push notifications: \(error.localizedDescription)")
        }
    }

    private func networkOperations() async {
        guard isNetworkConnected else {
            logger.log(level: .info, tag: tag, message: "Skipping push network operations: offline")
            return
        }
        logger.log(level: .info, tag: tag, message: "Push network operations started")
        await fetchEntries(showToast: false)
        await syncDevices()
        if isNotificationAuthorized(), fcmToken == nil {
            await registerForPushNotifications()
        } else {
            await updateDeviceInfo()
        }
        logger.log(level: .info, tag: tag, message: "Push network operations completed")
    }

    // MARK: - Entry/Operation Syncing

    private func fetchEntries(showToast: Bool = false) async {
        guard !isFetchingEntries else {
            logger.log(level: .info, tag: tag, message: "Skipping entry sync: already running")
            return
        }
        isFetchingEntries = true
        defer { isFetchingEntries = false }
        logger.log(level: .info, tag: tag, message: "Entry sync from push flow started. showToast=\(showToast)")
        await entryService.syncAllEntriesWithRemote()
        if showToast, !bluetoothService.isSetupInProgress, accountService.activeAccount != nil {
            await showNewEntryToast()
        }
        logger.log(level: .info, tag: tag, message: "Entry sync from push flow completed")
    }

    private func showNewEntryToast() async {
        notificationService.showToast(ToastModel(
            title: ToastStrings.success,
            message: ToastStrings.entryAdded
        ))
    }

    // MARK: - Device/Scale Syncing

    private func syncDevices() async {
        logger.log(level: .info, tag: tag, message: "Scale sync from push flow started")
        await scaleService.syncAllScalesWithRemote()
        logger.log(level: .info, tag: tag, message: "Scale sync from push flow completed")
    }

    // MARK: - Notification Tap Handling

    func handleNotificationTap(_ userInfo: [AnyHashable: Any]) {
        if let destination = userInfo["destination"] as? String {
            logger.log(level: .info, tag: tag, message: "Push notification tap handled. destination=\(destination)")
            NotificationCenter.default.post(
                name: .didReceiveNotification,
                object: nil,
                userInfo: ["destination": destination]
            )
        }
    }

    // MARK: - Cleanup

    deinit {
        networkMonitor.cancel()
    }

    // MARK: - Notification Authorization Helper

    private func isNotificationAuthorized() -> Bool {
        let response = permissionsService.getPermissionState(.NOTIFICATION)
        return response == .ENABLED
    }

    private func key(for baseKey: String) -> String {
        if let accountId = accountService.activeAccount?.accountId {
            return "\(accountId)_\(baseKey)"
        }
        return baseKey
    }

    // MARK: - FCM Token Storage (Keychain)

    /// Loads the stored FCM token from Keychain for the active account. Migrates from KvStorage if present.
    private func loadStoredFCMToken() {
        guard let accountId = accountService.activeAccount?.accountId else { return }
        if let storedToken = keychainService.getFCMToken(for: accountId), !storedToken.isEmpty {
            fcmToken = storedToken
            return
        }
        // One-time migration from KvStorage to Keychain
        let legacyKey = KvStorageKeys.fcmTokenKey(for: accountId)
        if let legacyToken = kvStorage.getValue(forKey: legacyKey) as? String, !legacyToken.isEmpty {
            keychainService.setFCMToken(legacyToken, for: accountId)
            kvStorage.clearValue(forKey: legacyKey)
            fcmToken = legacyToken
        }
    }

    /// Stores the FCM token in Keychain and updates the in-memory cache.
    private func storeFCMToken(_ token: String) {
        fcmToken = token
        if let accountId = accountService.activeAccount?.accountId {
            keychainService.setFCMToken(token, for: accountId)
        }
    }

    /// Retrieves the stored FCM token for a specific account (Keychain first; migrates from KvStorage if present).
    func getStoredFCMToken(for accountId: String) -> String? {
        if let token = keychainService.getFCMToken(for: accountId), !token.isEmpty {
            return token
        }
        let legacyKey = KvStorageKeys.fcmTokenKey(for: accountId)
        if let legacyToken = kvStorage.getValue(forKey: legacyKey) as? String, !legacyToken.isEmpty {
            keychainService.setFCMToken(legacyToken, for: accountId)
            kvStorage.clearValue(forKey: legacyKey)
            return legacyToken
        }
        return nil
    }
}
