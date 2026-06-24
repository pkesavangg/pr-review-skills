import Foundation

@MainActor
final class PushNotificationRepositoryAPI: PushNotificationRepositoryAPIProtocol {
    private let httpClient: HTTPClientProtocol

    init(httpClient: HTTPClientProtocol? = nil) {
        self.httpClient = httpClient ?? HTTPClient.shared
    }

    func updateDeviceInfo(_ info: DeviceInfoRequest) async throws {
        _ = try await httpClient.send(
            .updateDeviceInfo,
            method: .patch,
            body: info,
            needsAuth: true
        ) as EmptyResponse
    }
} 
