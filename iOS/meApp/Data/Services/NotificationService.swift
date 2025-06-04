import Foundation
import FirebaseMessaging

@MainActor
class NotificationService {
    static let shared = NotificationService()
    
    private init() {
        setupTokenRefresh()
    }
    
    private func setupTokenRefresh() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(tokenRefreshNotification),
            name: Notification.Name("FCMToken"),
            object: nil
        )
    }
    
    @objc private func tokenRefreshNotification(_ notification: Notification) {
        if let token = notification.userInfo?["token"] as? String {
            print("FCM Token: \(token)")
            // TODO: Send token to your server
        }
    }
    
    func getFCMToken() async throws -> String {
        return try await withCheckedThrowingContinuation { continuation in
            Messaging.messaging().token { token, error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else if let token = token {
                    continuation.resume(returning: token)
                } else {
                    continuation.resume(throwing: NSError(domain: "NotificationService", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to get FCM token"]))
                }
            }
        }
    }
    
    func subscribeToTopic(_ topic: String) {
        Messaging.messaging().subscribe(toTopic: topic) { error in
            if let error = error {
                print("Error subscribing to topic: \(error)")
            } else {
                print("Subscribed to topic: \(topic)")
            }
        }
    }
    
    func unsubscribeFromTopic(_ topic: String) {
        Messaging.messaging().unsubscribe(fromTopic: topic) { error in
            if let error = error {
                print("Error unsubscribing from topic: \(error)")
            } else {
                print("Unsubscribed from topic: \(topic)")
            }
        }
    }
} 