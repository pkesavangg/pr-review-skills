import Foundation
import FirebaseMessaging
import Network
import CoreBluetooth
import UIKit
import UserNotifications

/// Manages FCM token operations and notifications
@MainActor
class PushNotificationService: NSObject {
    /// Shared instance for accessing the NotificationService throughout the app
    static let shared = PushNotificationService()
    static let fcmTokenDidRefresh = Notification.Name("FCMToken")
    @Injector var entryService: EntryService
    
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
    
    // MARK: - Notification Settings
    private var notificationSettings = Notifications(
        shouldSendEntryNotifications: false,
        shouldSendWeightInEntryNotifications: false)
    
    /// Private initializer to enforce singleton pattern
    private override init() {
        super.init()
        fetchDeviceDetails()
        setupTokenRefresh()
        setupNetworkMonitoring()
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
    
    func getAllDeviceInfo() -> [String: String] {
        return deviceInfo
    }
    
    // MARK: - FCM Token & Device Info Update
    /// Handles FCM token refresh notifications
    /// - Parameter notification: The notification containing the new token
    @objc private func tokenRefreshNotification(_ notification: Notification) {
        Task {
            do {
                if let token = notification.userInfo?["token"] as? String {
                    fcmToken = token
                    saveFCMTokenIfNeeded(token)
                    await updateDeviceInfo()
                }
            }
        }
    }
    
    /// Retrieves the current FCM token
    /// - Returns: The current FCM token as a string
    /// - Throws: Error if token retrieval fails
    func getFCMToken() async throws -> String {
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
    
    func updateDeviceInfo() async {
        guard !isDeviceInfoUpdating else { return }
        isDeviceInfoUpdating = true
        defer { isDeviceInfoUpdating = false }
        fetchDeviceDetails()
    }
    
    // MARK: - Push Notification Registration
    func setupPushNotifications(isFromScaleSetup: Bool = false) async {
        if fcmToken != nil {
            await updateDeviceInfo()
            return
        }
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        if settings.authorizationStatus == .authorized {
            await registerForPushNotifications()
        }
    }
    
    private func registerForPushNotifications() async {
        UIApplication.shared.registerForRemoteNotifications()
        do {
            let token = try await getFCMToken()
            fcmToken = token
            await updateDeviceInfo()
        } catch {
        }
    }
    
    private func networkOperations() async {
        guard isNetworkConnected else {
            return
        }
        await fetchEntries()
        await syncDevices()
    }
    
    // MARK: - Entry/Operation Syncing
    func fetchEntries(showToast: Bool = false) async {
        guard !isFetchingEntries else { return }
        isFetchingEntries = true
        defer { isFetchingEntries = false }
        await entryService.syncAllEntriesWithRemote()
    }
    
    // MARK: - Device/Scale Syncing
    private func syncDevices() async {
        await ScaleService.shared.syncAllScalesWithRemote()
    }
    
    // MARK: - Notification Settings Management
    func updateNotificationSettings(_ settings: Notifications) async throws {
        guard isNetworkConnected else {
            UserDefaults.standard.set(try? JSONEncoder().encode(settings), forKey: "pendingNotificationSettings")
            return
        }
        notificationSettings = settings
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
                if UIApplication.shared.applicationState == .background {
                    let aps = userInfo["aps"] as? [String: Any]
                    let alert = aps?["alert"] as? [String: Any]
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
            }
            isProcessingNotification = false
            completion()
        }
    }
    
    // MARK: - Notification Tap Handling
    private func handleNotificationTap(_ userInfo: [AnyHashable: Any]) {
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
    
    /// Saves the FCM token to UserDefaults if it has changed
    func saveFCMTokenIfNeeded(_ token: String) {
        let defaults = UserDefaults.standard
        let key = "fcmToken"
        if let existingToken = defaults.string(forKey: key) {
            if existingToken == token {
                return
            }
        }
        defaults.set(token, forKey: key)
    }
}

