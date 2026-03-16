import Foundation

@MainActor
final class PushNotificationRepositoryAPI: PushNotificationRepositoryAPIProtocol {
    private let httpClient: HTTPClientProtocol

    init(httpClient: HTTPClientProtocol = HTTPClient.shared) {
        self.httpClient = httpClient
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
