import Foundation

enum PushNotificationTestError: Error, Equatable {
    case apiFailed
    case tokenFetchFailed
    case notificationAddFailed
}

enum PushNotificationTestFixtures {
    static func makeDataOnlyUserInfo(messageId: String = "msg-1") -> [AnyHashable: Any] {
        [
            "gcm.message_id": messageId,
            "destination": "history"
        ]
    }

    static func makeAlertUserInfo(messageId: String = "msg-1") -> [AnyHashable: Any] {
        [
            "gcm.message_id": messageId,
            "aps": [
                "alert": [
                    "title": "Title",
                    "body": "Body"
                ]
            ]
        ]
    }
}
