import Foundation
import FirebaseMessaging

/// NotificationService manages Firebase Cloud Messaging (FCM) token operations.
/// It provides functionality for:
/// - Token refresh handling
/// - Token retrieval
/// - Token management
@MainActor
class NotificationService {
    /// Shared instance for accessing the NotificationService throughout the app
    static let shared = NotificationService()
    
    /// Private initializer to enforce singleton pattern
    private init() {
        setupTokenRefresh()
    }
    
    /// Sets up observer for FCM token refresh notifications
    private func setupTokenRefresh() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(tokenRefreshNotification),
            name: Notification.Name("FCMToken"),
            object: nil
        )
    }
    
    /// Handles FCM token refresh notifications
    /// - Parameter notification: The notification containing the new token
    @objc private func tokenRefreshNotification(_ notification: Notification) {
        if notification.userInfo?["token"] is String {
            // TODO: Send token to your server
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
                    continuation.resume(returning: token)
                } else {
                    continuation.resume(throwing: NSError(domain: "NotificationService", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to get FCM token"]))
                }
            }
        }
    }
} 
