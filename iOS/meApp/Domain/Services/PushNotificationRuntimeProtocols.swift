import FirebaseMessaging
import Foundation
import UIKit
import UserNotifications

@MainActor
protocol PushTokenProviderProtocol {
    func fetchFCMToken() async throws -> String
}

@MainActor
protocol PushRemoteNotificationRegistrarProtocol {
    func registerForRemoteNotifications()
}

@MainActor
protocol PushUserNotificationCenterProtocol {
    func add(_ request: UNNotificationRequest) async throws
}

@MainActor
struct FirebasePushTokenProvider: PushTokenProviderProtocol {
    func fetchFCMToken() async throws -> String {
        try await withCheckedThrowingContinuation { continuation in
            Messaging.messaging().token { token, error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else if let token = token {
                    continuation.resume(returning: token)
                } else {
                    continuation.resume(
                        throwing: NSError(
                            domain: "PushNotificationService",
                            code: -1,
                            userInfo: [NSLocalizedDescriptionKey: "Failed to get FCM token"]
                        )
                    )
                }
            }
        }
    }
}

@MainActor
struct UIApplicationPushRegistrar: PushRemoteNotificationRegistrarProtocol {
    func registerForRemoteNotifications() {
        UIApplication.shared.registerForRemoteNotifications()
    }
}

@MainActor
struct SystemPushUserNotificationCenter: PushUserNotificationCenterProtocol {
    func add(_ request: UNNotificationRequest) async throws {
        try await UNUserNotificationCenter.current().add(request)
    }
}
