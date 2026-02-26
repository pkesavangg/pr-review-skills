import Foundation

@MainActor
protocol PushNotificationServiceProtocol: AnyObject {
    func handleNotification(_ userInfo: [AnyHashable: Any], completion: @escaping () -> Void)
    func updateDeviceInfo() async
    func setupPushNotifications(isFromScaleSetup: Bool) async
    func handleNotificationTap(_ userInfo: [AnyHashable: Any])
    func getStoredFCMToken(for accountId: String) -> String?
}

extension PushNotificationServiceProtocol {
    func setupPushNotifications() async {
        await setupPushNotifications(isFromScaleSetup: false)
    }
}
