//
//  AppDelegate.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//

import Foundation
import SwiftUI
import FirebaseCore
import FirebaseMessaging
import UserNotifications

// MARK: - AppDelegate
/// The AppDelegate class is responsible for handling application-level events.
/// It works alongside SceneDelegate when using UIKit lifecycle within a SwiftUI app.
/// It also manages:
/// - Firebase initialization
/// - FCM token management
/// - Push notification handling
/// - Analytics tracking
class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    var window: UIWindow?
    static var shared: AppDelegate?
    
    /// Key used to identify FCM message IDs in notification payloads
    private let gcmMessageIDKey = "gcm.message_id"
    
    /// Initializes Firebase and sets up notification handling
    /// - Returns: true if initialization was successful
    func application(_ application: UIApplication,
                    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        AppDelegate.shared = self
        
        // Configure Firebase
        FirebaseApp.configure()
        
        // Set up FCM delegate for token management
        Messaging.messaging().delegate = self
        
        // Set up notification center delegate for handling notifications
        UNUserNotificationCenter.current().delegate = self
        
        // Request notification permissions (alert, badge, sound)
        let authOptions: UNAuthorizationOptions = [.alert, .badge, .sound]
        UNUserNotificationCenter.current().requestAuthorization(
            options: authOptions,
            completionHandler: { granted, error in
            }
        )
        
        // Register for remote notifications
        application.registerForRemoteNotifications()
        return true
    }
    
    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
        configuration.delegateClass = SceneDelegate.self
        return configuration
    }
    
    // MARK: - UNUserNotificationCenterDelegate
    
    /// Handles notifications when the app is in the foreground
    /// - Parameters:
    ///   - center: The notification center
    ///   - notification: The notification to be presented
    ///   - completionHandler: Callback to specify how to present the notification
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                              willPresent notification: UNNotification,
                              withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        let userInfo = notification.request.content.userInfo
        
        // Track notification for Analytics
        Messaging.messaging().appDidReceiveMessage(userInfo)
        
        // Notify observers about received notification
        NotificationCenter.default.post(
            name: Notification.Name("ReceivedNotification"),
            object: nil,
            userInfo: userInfo
        )
        
        // Present notification with banner, badge, and sound
        completionHandler([[.banner, .badge, .sound]])
    }
    
    /// Handles user interaction with notifications
    /// - Parameters:
    ///   - center: The notification center
    ///   - response: The user's response to the notification
    ///   - completionHandler: Callback to indicate completion
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                              didReceive response: UNNotificationResponse,
                              withCompletionHandler completionHandler: @escaping () -> Void) {
        let userInfo = response.notification.request.content.userInfo
        
        // Track notification for Analytics
        Messaging.messaging().appDidReceiveMessage(userInfo)
        
        // Notify observers about notification response
        NotificationCenter.default.post(
            name: Notification.Name("ReceivedNotification"),
            object: nil,
            userInfo: userInfo
        )
        
        completionHandler()
    }
    
    // MARK: - MessagingDelegate
    
    /// Called when FCM token is refreshed
    /// - Parameters:
    ///   - messaging: The messaging instance
    ///   - fcmToken: The new FCM token
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        // Notify observers about new FCM token
        let dataDict: [String: String] = ["token": fcmToken ?? ""]
        NotificationCenter.default.post(
            name: Notification.Name("FCMToken"),
            object: nil,
            userInfo: dataDict
        )
    }
    
    // MARK: - Remote Notifications
    
    /// Called when APNs token is received
    /// - Parameters:
    ///   - application: The application instance
    ///   - deviceToken: The APNs device token
    func application(_ application: UIApplication,
                    didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        // Associate APNs token with FCM
        Messaging.messaging().apnsToken = deviceToken
    }
    
    /// Called when remote notification registration fails
    /// - Parameters:
    ///   - application: The application instance
    ///   - error: The error that occurred
    func application(_ application: UIApplication,
                    didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // TODO: Handle error
    }
    
    /// Handles background notifications
    /// - Parameters:
    ///   - application: The application instance
    ///   - userInfo: The notification payload
    ///   - completionHandler: Callback to indicate completion
    func application(_ application: UIApplication,
                    didReceiveRemoteNotification userInfo: [AnyHashable: Any],
                    fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        // Track notification for Analytics
        Messaging.messaging().appDidReceiveMessage(userInfo)
        
        // Notify observers about received notification
        NotificationCenter.default.post(
            name: Notification.Name("ReceivedNotification"),
            object: nil,
            userInfo: userInfo
        )        
        completionHandler(UIBackgroundFetchResult.newData)
    }
}
