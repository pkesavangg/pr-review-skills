import Foundation
import FirebaseMessaging
import Network
import CoreBluetooth
import UIKit
import UserNotifications
import GGBluetoothSwiftPackage

/// Manages FCM token operations and notifications
@MainActor
class PushNotificationService: NSObject {
    /// Shared instance for accessing the NotificationService throughout the app
    static let shared = PushNotificationService()
    static let fcmTokenDidRefresh = Notification.Name("FCMToken")
    @Injector var entryService: EntryService
    @Injector private var permissionsService: PermissionsService
    @Injector private var accountService: AccountService
    @Injector private var notificationService: NotificationHelperService
    @Injector private var bluetoothService: BluetoothService
    // API repository for push-notification related network calls
    private let apiRepo: PushNotificationRepositoryAPIProtocol = PushNotificationRepositoryAPI()
    
    // MARK: - Properties
    private var fcmToken: String?
    private let networkMonitor = NWPathMonitor()
    private var isNetworkConnected: Bool = false
    private var deviceInfo: [String: String] = [:]
    private var notificationHandlers: [String: (([AnyHashable: Any]) -> Void)] = [:]
    private var isDeviceInfoUpdating: Bool = false
    private var isFetchingEntries: Bool = false
    private var processedMessageIds: [String] = []
    private var isProcessingNotification: Bool = false
    private let logger = LoggerService.shared
    private let kvStorage = KvStorageService.shared
    
    // MARK: - Notification Settings
    
    /// Private initializer to enforce singleton pattern
    private override init() {
        super.init()
        loadStoredFCMToken()
        fetchDeviceDetails()
        setupTokenRefresh()
        setupNetworkMonitoring()
    }
    
    // MARK: - Notification Handling
    func handleNotification(_ userInfo: [AnyHashable: Any], completion: @escaping () -> Void) {
        guard !isProcessingNotification else {
            completion()
            return
        }
        
        if let messageId = userInfo["gcm.message_id"] as? String {
            if processedMessageIds.contains(messageId) {
                completion()
                return
            }
            processedMessageIds.append(messageId)
            if processedMessageIds.count > 100 {
                processedMessageIds.removeFirst()
            }
        }
        
        isProcessingNotification = true
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
                    try await UNUserNotificationCenter.current().add(request)
                }
            } catch {
                logger.log(level: .error, tag: "PushNotificationService", message: "Failed to handle notification: \(error.localizedDescription)")
            }
            isProcessingNotification = false
            completion()
        }
    }
    
    func updateDeviceInfo() async {
        // Skip if the user hasn’t granted notification permission yet – don’t send an
        // FCM token or any push-notification related info to the backend until allowed.
        guard isNotificationAuthorized() else { return }
        // Don't send empty token to backend – it can clear the device from push targeting.
        guard let token = fcmToken, !token.isEmpty else { return }
        
        guard !isDeviceInfoUpdating else { return }
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
            logger.log(level: .info, tag: "PushNotificationService", message: "Device info updated", data: payload)
        } catch {
            logger.log(level: .error, tag: "PushNotificationService", message: "Failed to update device info: \(error.localizedDescription)")
            // Silently ignore network errors – will retry on next connectivity change
        }
    }
    
    // MARK: - Push Notification Registration
    func setupPushNotifications(isFromScaleSetup: Bool = false) async {
        // If token already exists, simply update device info
        if fcmToken != nil {
            await updateDeviceInfo()
            return
        }
        
        guard permissionsService.requiredCategories.contains(.notifications) else {
            return
        }
        // Determine current permission state
        var permissionResult = permissionsService.getPermissionState(.NOTIFICATION) ?? .DISABLED
        
        // If permission is not enabled, optionally present the disabled alert
        if permissionResult != .ENABLED {
            let accountId = accountService.activeAccount?.accountId ?? ""
            let viewedKey = KvStorageKeys.notificationOnlyAlertShownKey(for: accountId)
            let hasViewedAlert = (KvStorageService.shared.getValue(forKey: viewedKey) as? Bool) ?? false
            if !hasViewedAlert || isFromScaleSetup {
                permissionResult = await permissionsService.handlePermission(.notification)
                KvStorageService.shared.setValue(true, forKey: viewedKey)
            }
        }
        
        // Register only when permission has been granted and system notification authorization is allowed
        if permissionResult == .ENABLED && isNotificationAuthorized() {
            await registerForPushNotifications()
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
            "deviceOSName": device.systemName
        ]
    }
    
    // MARK: - FCM Token & Device Info Update
    /// Handles FCM token refresh notifications
    /// - Parameter notification: The notification containing the new token
    @objc private func tokenRefreshNotification(_ notification: Notification) {
        // Ignore token updates until the user grants notification permission so that `fcmToken`
        // stays `nil` prior to consent (behaviour parity with DeviceService in Angular app).
        guard isNotificationAuthorized() else { return }
        
        Task { @MainActor [weak self] in
            guard let token = notification.userInfo?["token"] as? String, !token.isEmpty else { return }
            self?.fcmToken = token
            self?.storeFCMToken(token)
            await self?.updateDeviceInfo()
        }
    }
    
    /// Retrieves the current FCM token
    /// - Returns: The current FCM token as a string
    /// - Throws: Error if token retrieval fails
    private func getFCMToken() async throws -> String {
        return try await withCheckedThrowingContinuation { continuation in
            Messaging.messaging().token { token, error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else if let token = token {
                    self.fcmToken = token
                    continuation.resume(returning: token)
                } else {
                    continuation.resume(throwing: NSError(domain: "PushNotificationService", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to get FCM token"]))
                }
            }
        }
    }
    
    private func registerForPushNotifications() async {
        UIApplication.shared.registerForRemoteNotifications()
        do {
            let token = try await getFCMToken()
            fcmToken = token
            storeFCMToken(token)
            await updateDeviceInfo()
        } catch {
        }
    }
    
    private func networkOperations() async {
        guard isNetworkConnected else {
            return
        }
        await fetchEntries(showToast: false)
        await syncDevices()
        if isNotificationAuthorized(), fcmToken == nil {
            await registerForPushNotifications()
        } else {
            await updateDeviceInfo()
        }
    }
    
    // MARK: - Entry/Operation Syncing
    private func fetchEntries(showToast: Bool = false) async {
        guard !isFetchingEntries else { return }
        isFetchingEntries = true
        defer { isFetchingEntries = false }
        await entryService.syncAllEntriesWithRemote()
        if showToast, !bluetoothService.isSetupInProgress, accountService.activeAccount != nil {
            await showNewEntryToast()
        }
    }

    private func showNewEntryToast() async {
        await notificationService.showToast(ToastModel(
            title: ToastStrings.success,
            message: ToastStrings.entryAdded
        ))
    }
    // MARK: - Device/Scale Syncing
    private func syncDevices() async {
        await ScaleService.shared.syncAllScalesWithRemote()
    }
    
    // MARK: - Notification Tap Handling
    func handleNotificationTap(_ userInfo: [AnyHashable: Any]) {
        if let destination = userInfo["destination"] as? String {
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
    
    // MARK: - FCM Token Storage
    /// Loads the stored FCM token from local storage
    private func loadStoredFCMToken() {
        if let storedToken = kvStorage.getValue(forKey: KvStorageKeys.fcmToken.rawValue) as? String, !storedToken.isEmpty {
            fcmToken = storedToken
        }
    }
    
    /// Stores the FCM token to local storage
    /// - Parameter token: The FCM token to store
    private func storeFCMToken(_ token: String) {
        kvStorage.setValue(token, forKey: KvStorageKeys.fcmToken.rawValue)
    }
    
    /// Retrieves the stored FCM token from local storage
    /// - Returns: The stored FCM token, or nil if not found
    func getStoredFCMToken() -> String? {
        return kvStorage.getValue(forKey: KvStorageKeys.fcmToken.rawValue) as? String
    }
}

