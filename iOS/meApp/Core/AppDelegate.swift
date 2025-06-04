import UIKit
import FirebaseCore
import FirebaseMessaging
import UserNotifications

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    static var shared: AppDelegate?
    
    // Firebase message ID key
    private let gcmMessageIDKey = "gcm.message_id"
    
    func application(_ application: UIApplication,
                    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        print("Application launching...")
        AppDelegate.shared = self
        
        // Configure Firebase
        FirebaseApp.configure()
        print("Firebase configured")
        
        // Set up FCM
        Messaging.messaging().delegate = self
        print("FCM delegate set")
        
        // Set up notifications
        UNUserNotificationCenter.current().delegate = self
        print("Notification center delegate set")
        
        // Check current notification settings
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            print("==========================================")
            print("Notification Settings:")
            print("Authorization Status: \(settings.authorizationStatus.rawValue)")
            print("Alert Setting: \(settings.alertSetting.rawValue)")
            print("Badge Setting: \(settings.badgeSetting.rawValue)")
            print("Sound Setting: \(settings.soundSetting.rawValue)")
            print("Notification Center Setting: \(settings.notificationCenterSetting.rawValue)")
            print("Lock Screen Setting: \(settings.lockScreenSetting.rawValue)")
            print("==========================================")
        }
        
        let authOptions: UNAuthorizationOptions = [.alert, .badge, .sound]
        UNUserNotificationCenter.current().requestAuthorization(
            options: authOptions,
            completionHandler: { granted, error in
                if let error = error {
                    print("Notification authorization error: \(error)")
                }
                print("Notification authorization granted: \(granted)")
                
                // Verify settings after authorization
                UNUserNotificationCenter.current().getNotificationSettings { settings in
                    print("==========================================")
                    print("Updated Notification Settings:")
                    print("Authorization Status: \(settings.authorizationStatus.rawValue)")
                    print("Alert Setting: \(settings.alertSetting.rawValue)")
                    print("Badge Setting: \(settings.badgeSetting.rawValue)")
                    print("Sound Setting: \(settings.soundSetting.rawValue)")
                    print("Notification Center Setting: \(settings.notificationCenterSetting.rawValue)")
                    print("Lock Screen Setting: \(settings.lockScreenSetting.rawValue)")
                    print("==========================================")
                }
            }
        )
        
        application.registerForRemoteNotifications()
        print("Registered for remote notifications")
        
        return true
    }
    
    // MARK: - UNUserNotificationCenterDelegate
    
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                              willPresent notification: UNNotification,
                              withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        let userInfo = notification.request.content.userInfo
        
        // With swizzling disabled, we must let Messaging know about the message for Analytics
        Messaging.messaging().appDidReceiveMessage(userInfo)
        
        // Print message ID
        if let messageID = userInfo[gcmMessageIDKey] {
            print("Message ID: \(messageID)")
        }
        
        print("==========================================")
        print("Received notification in foreground:")
        print("Title: \(notification.request.content.title)")
        print("Body: \(notification.request.content.body)")
        print("UserInfo: \(userInfo)")
        print("Raw UserInfo: \(String(describing: userInfo))")
        print("==========================================")
        
        NotificationCenter.default.post(
            name: Notification.Name("ReceivedNotification"),
            object: nil,
            userInfo: userInfo
        )
        
        // Change this to your preferred presentation option
        completionHandler([[.banner, .badge, .sound]])
    }
    
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                              didReceive response: UNNotificationResponse,
                              withCompletionHandler completionHandler: @escaping () -> Void) {
        let userInfo = response.notification.request.content.userInfo
        
        // With swizzling disabled, we must let Messaging know about the message for Analytics
        Messaging.messaging().appDidReceiveMessage(userInfo)
        
        // Print message ID
        if let messageID = userInfo[gcmMessageIDKey] {
            print("Message ID: \(messageID)")
        }
        
        print("==========================================")
        print("Received notification response:")
        print("Title: \(response.notification.request.content.title)")
        print("Body: \(response.notification.request.content.body)")
        print("UserInfo: \(userInfo)")
        print("Raw UserInfo: \(String(describing: userInfo))")
        print("==========================================")
        
        NotificationCenter.default.post(
            name: Notification.Name("ReceivedNotification"),
            object: nil,
            userInfo: userInfo
        )
        
        completionHandler()
    }
    
    // MARK: - MessagingDelegate
    
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        print("==========================================")
        print("FIREBASE FCM TOKEN:")
        print(fcmToken ?? "No token received")
        print("==========================================")
        
        let dataDict: [String: String] = ["token": fcmToken ?? ""]
        NotificationCenter.default.post(
            name: Notification.Name("FCMToken"),
            object: nil,
            userInfo: dataDict
        )
    }
    
    // MARK: - Remote Notifications
    
    func application(_ application: UIApplication,
                    didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        print("APNs token received: \(deviceToken.map { String(format: "%02.2hhx", $0) }.joined())")
        Messaging.messaging().apnsToken = deviceToken
    }
    
    func application(_ application: UIApplication,
                    didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("Failed to register for remote notifications: \(error)")
    }
    
    func application(_ application: UIApplication,
                    didReceiveRemoteNotification userInfo: [AnyHashable: Any],
                    fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        // With swizzling disabled, we must let Messaging know about the message for Analytics
        Messaging.messaging().appDidReceiveMessage(userInfo)
        
        // Print message ID
        if let messageID = userInfo[gcmMessageIDKey] {
            print("Message ID: \(messageID)")
        }
        
        print("==========================================")
        print("Received remote notification:")
        print("UserInfo: \(userInfo)")
        print("Raw UserInfo: \(String(describing: userInfo))")
        print("==========================================")
        
        NotificationCenter.default.post(
            name: Notification.Name("ReceivedNotification"),
            object: nil,
            userInfo: userInfo
        )
        
        completionHandler(UIBackgroundFetchResult.newData)
    }
} 
