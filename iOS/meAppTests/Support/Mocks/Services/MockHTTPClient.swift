import Foundation
@testable import meApp

@MainActor
final class MockHTTPClient: HTTPClientProtocol {
    var skipCheckNetwork: Bool = false

    func get<T: Decodable>(
        _ endpoint: Endpoint,
        headers: [String: String]?,
        needsAuth: Bool,
        accountId: String?
    ) async throws -> T {
        throw UnexpectedCallError.methodCalled("MockHTTPClient.get")
    }

    func send<T: Encodable, R: Decodable>(
        _ endpoint: Endpoint,
        method: HTTPMethod,
        body: T,
        headers: [String: String]?,
        needsAuth: Bool,
        accountId: String?
    ) async throws -> R {
        throw UnexpectedCallError.methodCalled("MockHTTPClient.send")
    }
}
