import Foundation
@testable import meApp

@MainActor
final class MockPushNotificationService: PushNotificationServiceProtocol {
    private(set) var handleNotificationCalls = 0
    private(set) var updateDeviceInfoCalls = 0
    private(set) var setupPushNotificationsCalls = 0
    private(set) var handleNotificationTapCalls = 0
    private(set) var lastNotificationUserInfo: [AnyHashable: Any]?
    private(set) var lastNotificationTapUserInfo: [AnyHashable: Any]?
    private(set) var lastIsFromScaleSetup: Bool?

    func handleNotification(_ userInfo: [AnyHashable: Any], completion: @escaping () -> Void) {
        handleNotificationCalls += 1
        lastNotificationUserInfo = userInfo
        completion()
    }

    func updateDeviceInfo() async {
        updateDeviceInfoCalls += 1
    }

    func setupPushNotifications(isFromScaleSetup: Bool) async {
        setupPushNotificationsCalls += 1
        lastIsFromScaleSetup = isFromScaleSetup
    }

    func handleNotificationTap(_ userInfo: [AnyHashable: Any]) {
        handleNotificationTapCalls += 1
        lastNotificationTapUserInfo = userInfo
    }

    func getStoredFCMToken(for accountId: String) -> String? {
        nil
    }
}
