//
//  AppDelegate.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//

import FirebaseCore
import FirebaseMessaging
import Foundation
import SwiftUI
import UserNotifications

// MARK: - AppDelegate
/// Handles app lifecycle, Firebase setup, and push notifications
class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    var window: UIWindow?
    static var shared: AppDelegate?
    private let gcmMessageIDKey = "gcm.message_id"
    
    /// Initializes Firebase and sets up notification handling
    /// - Returns: true if initialization was successful
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        AppDelegate.shared = self
        
        // Initialize services first
        Task { @MainActor in
            // Initialize ServiceRegistry to register all services
            _ = ServiceRegistry.shared
            
            // Initialize Firebase and notifications
            FirebaseApp.configure()
            Messaging.messaging().delegate = self
            UNUserNotificationCenter.current().delegate = self
            
            application.registerForRemoteNotifications()
        }
        
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
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let userInfo = notification.request.content.userInfo
        Messaging.messaging().appDidReceiveMessage(userInfo)
        
        // Let PushNotificationService handle the notification
        Task { @MainActor in
            PushNotificationService.shared.handleNotification(userInfo) {
                completionHandler([.banner, .badge, .sound])
            }
        }
    }
    
    /// Handles user interaction with notifications
    /// - Parameters:
    ///   - center: The notification center
    ///   - response: The user's response to the notification
    ///   - completionHandler: Callback to indicate completion
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        Messaging.messaging().appDidReceiveMessage(userInfo)
        
        // Only handle tap if it's a new notification
        if userInfo["gcm.message_id"] is String {
            Task { @MainActor in
                PushNotificationService.shared.handleNotification(userInfo) {
                    completionHandler()
                }
            }
        } else {
            completionHandler()
        }
    }
    
    // MARK: - MessagingDelegate
    
    /// Called when FCM token is refreshed
    /// - Parameters:
    ///   - messaging: The messaging instance
    ///   - fcmToken: The new FCM token
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
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
    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Messaging.messaging().apnsToken = deviceToken
    }
    
    /// Called when remote notification registration fails
    /// - Parameters:
    ///   - application: The application instance
    ///   - error: The error that occurred
    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        // Log the error for debugging - registration failures are non-critical
        // Common causes: simulator (no APNs), user denied permissions, network issues
        Task { @MainActor in
            LoggerService.shared.log(level: .error, tag: "AppDelegate", message: "Failed to register for remote notifications", data: error.localizedDescription)
        }
    }
    
    /// Handles background notifications
    /// - Parameters:
    ///   - application: The application instance
    ///   - userInfo: The notification payload
    ///   - completionHandler: Callback to indicate completion
    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        Messaging.messaging().appDidReceiveMessage(userInfo)
        
        // Let PushNotificationService handle the notification
        Task { @MainActor in
            PushNotificationService.shared.handleNotification(userInfo) {
                completionHandler(UIBackgroundFetchResult.newData)
            }
        }
    }
}
