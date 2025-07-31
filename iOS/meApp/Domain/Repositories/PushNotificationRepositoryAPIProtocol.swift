import Foundation

/// Protocol for abstracting all remote (API) push-notification related operations.
///
/// This currently covers device-info updates used for FCM token registration. Extend
/// as new push-notification endpoints are added.
@MainActor
protocol PushNotificationRepositoryAPIProtocol {
    /// Sends the latest device information and (optionally) FCM token to the backend.
    /// Mirrors `PATCH /account/device/`.
    /// - Parameter info: The `DeviceInfoRequest` payload.
    func updateDeviceInfo(_ info: DeviceInfoRequest) async throws
} 