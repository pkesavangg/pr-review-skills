import Foundation
@testable import meApp

@MainActor
final class MockHTTPClient: HTTPClientProtocol {
    var skipCheckNetwork: Bool = false

    // MARK: - Configurable Results

    var getResult: Any?
    var getError: Error?
    var sendResult: Any?
    var sendError: Error?

    // MARK: - Call Tracking

    private(set) var getCalls = 0
    private(set) var sendCalls = 0
    private(set) var lastGetEndpoint: Endpoint?
    private(set) var lastGetNeedsAuth: Bool = false
    private(set) var lastGetAccountId: String?
    private(set) var lastSendEndpoint: Endpoint?
    private(set) var lastSendMethod: HTTPMethod?
    private(set) var lastSendNeedsAuth: Bool = false
    private(set) var lastSendAccountId: String?
    private(set) var lastSendBody: (any Encodable)?

    // MARK: - MockError

    enum MockError: Error {
        case typeMismatch
        case noResultConfigured
    }

    // MARK: - HTTPClientProtocol

    func get<T: Decodable>(
        _ endpoint: Endpoint,
        headers: [String: String]?,
        needsAuth: Bool,
        accountId: String?
    ) async throws -> T {
        getCalls += 1
        lastGetEndpoint = endpoint
        lastGetNeedsAuth = needsAuth
        lastGetAccountId = accountId
        if let getError { throw getError }
        guard let result = getResult else { throw MockError.noResultConfigured }
        guard let typed = result as? T else { throw MockError.typeMismatch }
        return typed
    }

    // swiftlint:disable:next function_parameter_count
    func send<T: Encodable, R: Decodable>(
        _ endpoint: Endpoint,
        method: HTTPMethod,
        body: T,
        headers: [String: String]?,
        needsAuth: Bool,
        accountId: String?
    ) async throws -> R {
        sendCalls += 1
        lastSendEndpoint = endpoint
        lastSendMethod = method
        lastSendNeedsAuth = needsAuth
        lastSendAccountId = accountId
        lastSendBody = body
        if let sendError { throw sendError }
        guard let result = sendResult else { throw MockError.noResultConfigured }
        guard let typed = result as? R else { throw MockError.typeMismatch }
        return typed
    }
}
