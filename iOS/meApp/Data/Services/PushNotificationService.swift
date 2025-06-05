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
    private var isSetupInProgress: Bool = false
    private let networkMonitor = NWPathMonitor()
    @MainActor private var isNetworkConnected: Bool = false
    private var deviceInfo: [String: String] = [:]
    private var notificationHandlers: [String: (([AnyHashable: Any]) -> Void)] = [:]
    private var isDeviceInfoUpdating: Bool = false
    private var isFetchingEntries: Bool = false
    private var processedMessageIds: Set<String> = []
    private var isProcessingNotification: Bool = false
    
    // MARK: - Notification Settings
    struct NotificationSettings: Codable {
        var shouldSendEntryNotifications: Bool = false
        var shouldSendWeightInEntryNotifications: Bool = false
    }
    
    private var notificationSettings = NotificationSettings()
    
    /// Private initializer to enforce singleton pattern
    private override init() {
        super.init()
        fetchDeviceDetails()
        setupTokenRefresh()
        setupNetworkMonitoring()
        setupNotificationHandlers()
        setupNotificationCategories()
    }
    
    // MARK: - Notification Categories
    enum NotificationCategory: String, CaseIterable {
        case general = "GENERAL"
        case weight = "WEIGHT"
        case entry = "ENTRY"
        case system = "SYSTEM"
        
        var displayName: String {
            switch self {
            case .general: return "General Notifications"
            case .weight: return "Weight Updates"
            case .entry: return "Entry Notifications"
            case .system: return "System Notifications"
            }
        }
    }
    
    // MARK: - Notification Settings
    private func setupTokenRefresh() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(tokenRefreshNotification),
            name: Notification.Name(PushNotificationService.fcmTokenDidRefresh.rawValue),
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
    
    private func setupNotificationHandlers() {
        // Handle notification received in foreground
        Messaging.messaging().delegate = self
        
        // Handle notification received in background
        UNUserNotificationCenter.current().delegate = self
    }
    
    private func setupNotificationCategories() {
        if #available(iOS 10.0, *) {
            let center = UNUserNotificationCenter.current()
            
            // Create categories with actions
            let categories = NotificationCategory.allCases.map { category in
                UNNotificationCategory(
                    identifier: category.rawValue,
                    actions: [], // Add custom actions if needed
                    intentIdentifiers: [],
                    options: .customDismissAction
                )
            }
            
            center.setNotificationCategories(Set(categories))
        }
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
        let updatedDeviceInfo = [
            "appVersion": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "",
            "deviceManufacturer": deviceInfo["manufacturer"] ?? "",
            "deviceOSName": deviceInfo["deviceOSName"] ?? "",
            "deviceOSVersion": deviceInfo["osVersion"] ?? "",
            "deviceUUID": deviceInfo["deviceUuid"] ?? "",
            "deviceModel": deviceInfo["model"] ?? "",
            "fcmToken": fcmToken ?? ""
        ]
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
            print("Error registering for push notifications: \(error)")
        }
    }
        
    private func networkOperations() async {
        guard isNetworkConnected else {
            print("[PushNotificationService] ⚠️ Network not connected, skipping operations")
            return
        }
        
        do {
            print("[PushNotificationService] 🌐 Starting network operations")
            await fetchEntries()
            await syncDevices()
        } catch {
            print("[PushNotificationService] ❌ Network operations failed: \(error)")
        }
    }
    
    // MARK: - Entry/Operation Syncing
    func fetchEntries(showToast: Bool = false) async {
        guard !isFetchingEntries else { return }
        isFetchingEntries = true
        defer { isFetchingEntries = false }
        print("[PushNotificationService] fetchEntries called (showToast: \(showToast))")
        
        do {
            await entryService.syncAllEntriesWithRemote()
        } catch {
            print("[PushNotificationService] ⚠️ Error syncing entries: \(error)")
        }
    }
    
    // MARK: - Device/Scale Syncing
    private func syncDevices() async {
        print("[PushNotificationService] syncDevices called")
        await ScaleService.shared.syncAllScalesWithRemote()
    }
    
    // MARK: - Topic Subscription Example
    private func subscribeToRelevantTopics() {
        print("[PushNotificationService] subscribeToRelevantTopics called")
        // Example: subscribe to a topic (replace with actual logic)
        subscribeToTopic("example_topic")
    }
    
    // MARK: - Notification Settings Management
    func updateNotificationSettings(_ settings: NotificationSettings) async throws {
        guard isNetworkConnected else {
            // Store settings for offline sync
            UserDefaults.standard.set(try? JSONEncoder().encode(settings), forKey: "pendingNotificationSettings")
            return
        }
        
        // Update local settings
        notificationSettings = settings
    }
    
    // MARK: - Topic Subscription
    func subscribeToTopic(_ topic: String) {
        Messaging.messaging().subscribe(toTopic: topic) { error in
            if let error = error {
                print("Error subscribing to topic: \(error)")
            }
        }
    }
    
    func unsubscribeFromTopic(_ topic: String) {
        Messaging.messaging().unsubscribe(fromTopic: topic) { error in
            if let error = error {
                print("Error unsubscribing from topic: \(error)")
            }
        }
    }
    
    // MARK: - Notification Handling
    func handleNotification(_ userInfo: [AnyHashable: Any], completion: @escaping () -> Void) {
        // Check if we're already processing a notification
        guard !isProcessingNotification else {
            print("[PushNotificationService] ⏭️ Skipping notification - already processing one")
            completion()
            return
        }
        
        // Check if we've already processed this message
        if let messageId = userInfo["gcm.message_id"] as? String {
            if processedMessageIds.contains(messageId) {
                print("[PushNotificationService] ⏭️ Skipping already processed message: \(messageId)")
                completion()
                return
            }
            processedMessageIds.insert(messageId)
            // Keep the set size manageable
            if processedMessageIds.count > 100 {
                processedMessageIds.removeFirst()
            }
        }
        
        isProcessingNotification = true
        print("[PushNotificationService] 🔔 Starting to handle notification with userInfo: \(userInfo)")
        
        Task {
            do {
                print("[PushNotificationService] 📥 Fetching entries after notification...")
                await fetchEntries(showToast: true)
                
                print("[PushNotificationService] 🔄 Syncing devices after notification...")
                await syncDevices()
                
                print("[PushNotificationService] 📢 Subscribing to relevant topics...")
                subscribeToRelevantTopics()
                
                // Extract notification content from FCM payload
                let aps = userInfo["aps"] as? [String: Any]
                let alert = aps?["alert"] as? [String: Any]
                let title = alert?["title"] as? String ?? "New Notification"
                let body = alert?["body"] as? String ?? "You have a new message"
                let fcmOptions = userInfo["fcm_options"] as? [String: Any]
                let imageUrl = fcmOptions?["image"] as? String
                
                print("[PushNotificationService] 📝 Creating notification with title: \(title), body: \(body)")
                if let imageUrl = imageUrl {
                    print("[PushNotificationService] 🖼️ Notification includes image: \(imageUrl)")
                }
                
                let content = UNMutableNotificationContent()
                content.title = title
                content.body = body
                content.categoryIdentifier = NotificationCategory.general.rawValue
                content.userInfo = userInfo
                content.sound = .default
                
                // Handle image if present
                if let imageUrl = imageUrl {
                    do {
                        let attachment = try await downloadAndCreateAttachment(from: imageUrl)
                        content.attachments = [attachment]
                    } catch {
                        print("[PushNotificationService] ⚠️ Failed to download notification image: \(error)")
                    }
                }
                
                // Create notification request
                let request = UNNotificationRequest(
                    identifier: UUID().uuidString,
                    content: content,
                    trigger: nil
                )
                
                // Add notification
                try await UNUserNotificationCenter.current().add(request)
                print("[PushNotificationService] ✅ Successfully added notification")
                
            } catch {
                print("[PushNotificationService] ❌ Error processing notification: \(error)")
            }
            
            isProcessingNotification = false
            completion()
        }
    }
    
    // Helper method to download and create notification attachment
    private func downloadAndCreateAttachment(from urlString: String) async throws -> UNNotificationAttachment {
        guard let url = URL(string: urlString) else {
            throw NSError(domain: "PushNotificationService", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid image URL"])
        }
        
        let (data, _) = try await URLSession.shared.data(from: url)
        let tempDir = FileManager.default.temporaryDirectory
        let fileName = url.lastPathComponent
        let fileURL = tempDir.appendingPathComponent(fileName)
        
        try data.write(to: fileURL)
        
        return try UNNotificationAttachment(
            identifier: UUID().uuidString,
            url: fileURL,
            options: nil
        )
    }
    
    // MARK: - Notification Tap Handling
    private func handleNotificationTap(_ userInfo: [AnyHashable: Any]) {
        if let destination = userInfo["destination"] as? String {
            // Post notification for app navigation
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
}

// MARK: - Notification Names
extension Notification.Name {
    static let didReceiveNotification = Notification.Name("didReceiveNotification")
}

// MARK: - MessagingDelegate
extension PushNotificationService: MessagingDelegate {
    nonisolated func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        Task { @MainActor in
            self.fcmToken = token
            await updateDeviceInfo()
        }
    }
}

// MARK: - UNUserNotificationCenterDelegate
extension PushNotificationService: UNUserNotificationCenterDelegate {
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
                              willPresent notification: UNNotification,
                              withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        let userInfo = notification.request.content.userInfo
        print("[PushNotificationService] 📬 Will present notification received: \(userInfo)")
        Task { @MainActor in
            if let type = userInfo["type"] as? String,
               let handler = notificationHandlers[type] {
                print("[PushNotificationService] 🔄 Executing handler for notification type: \(type)")
                handler(userInfo)
            }
            
            print("[PushNotificationService] 📥 Fetching entries after foreground notification...")
            await fetchEntries(showToast: true)
            
            print("[PushNotificationService] 🔄 Syncing devices after foreground notification...")
            await syncDevices()
            
            print("[PushNotificationService] 📢 Subscribing to relevant topics after foreground notification...")
            subscribeToRelevantTopics()
            
            completionHandler([.banner, .sound, .badge])
        }
    }
    
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
                              didReceive response: UNNotificationResponse,
                              withCompletionHandler completionHandler: @escaping () -> Void) {
        let userInfo = response.notification.request.content.userInfo
        print("[PushNotificationService] 👆 Notification tapped with userInfo: \(userInfo)")
        Task { @MainActor in
            if let type = userInfo["type"] as? String,
               let handler = notificationHandlers[type] {
                print("[PushNotificationService] 🔄 Executing handler for tapped notification type: \(type)")
                handler(userInfo)
            }
            
            print("[PushNotificationService] 📥 Fetching entries after notification tap...")
            await fetchEntries(showToast: true)
            
            print("[PushNotificationService] 🔄 Syncing devices after notification tap...")
            await syncDevices()
            
            print("[PushNotificationService] 📢 Subscribing to relevant topics after notification tap...")
            subscribeToRelevantTopics()
        }
        completionHandler()
    }
    
    // Add method to handle incoming messages
    nonisolated func application(_ application: UIApplication, didReceiveRemoteNotification userInfo: [AnyHashable: Any],
                      fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        print("[PushNotificationService] 📱 Received remote notification in background: \(userInfo)")
        Task { @MainActor in
            handleNotification(userInfo) {
                print("[PushNotificationService] ✅ Completed handling background notification")
                completionHandler(.newData)
            }
        }
    }
}
