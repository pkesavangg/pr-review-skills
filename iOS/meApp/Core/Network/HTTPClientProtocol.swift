import Foundation

@MainActor
protocol HTTPClientProtocol: AnyObject {
    var skipCheckNetwork: Bool { get set }

    func get<T: Decodable>(
        _ endpoint: Endpoint,
        headers: [String: String]?,
        needsAuth: Bool,
        accountId: String?
    ) async throws -> T

    func send<T: Encodable, R: Decodable>(
        _ endpoint: Endpoint,
        method: HTTPMethod,
        body: T,
        headers: [String: String]?,
        needsAuth: Bool,
        accountId: String?
    ) async throws -> R
}

extension HTTPClientProtocol {
    func get<T: Decodable>(
        _ endpoint: Endpoint,
        headers: [String: String]? = nil,
        needsAuth: Bool = false,
        accountId: String? = nil
    ) async throws -> T {
        try await get(endpoint, headers: headers, needsAuth: needsAuth, accountId: accountId)
    }

    func send<T: Encodable, R: Decodable>(
        _ endpoint: Endpoint,
        method: HTTPMethod,
        body: T,
        headers: [String: String]? = nil,
        needsAuth: Bool = false,
        accountId: String? = nil
    ) async throws -> R {
        try await send(endpoint, method: method, body: body, headers: headers, needsAuth: needsAuth, accountId: accountId)
    }
}

