//
//  MockHTTPClient.swift
//  meAppTests
//

import Foundation
@testable import meApp

/// A mock HTTPClient that allows tests to control network responses without hitting the real network.
@MainActor
final class MockHTTPClient: HTTPClientProtocol {

    // MARK: - Configurable stubs

    /// Set this to inject a result for the next `send` call.
    var sendResult: (any Decodable)?
    /// Set this to throw an error from the next `send` call.
    var sendError: Error?

    /// Set this to inject a result for the next `get` call.
    var getResult: (any Decodable)?
    /// Set this to throw an error from the next `get` call.
    var getError: Error?

    // MARK: - Call tracking

    var sendCallCount = 0
    var lastSendEndpoint: Endpoint?
    var lastSendMethod: HTTPMethod?

    var getCallCount = 0
    var lastGetEndpoint: Endpoint?

    // MARK: - HTTPClientProtocol

    func get<T: Decodable>(
        _ endpoint: Endpoint,
        headers: [String: String]? = nil,
        needsAuth: Bool = false,
        accountId: String? = nil
    ) async throws -> T {
        getCallCount += 1
        lastGetEndpoint = endpoint

        if let error = getError { throw error }
        if let result = getResult as? T { return result }
        throw MockHTTPClientError.notConfigured
    }

    func send<T: Encodable, R: Decodable>(
        _ endpoint: Endpoint,
        method: HTTPMethod,
        body: T,
        headers: [String: String]? = nil,
        needsAuth: Bool = false,
        accountId: String? = nil
    ) async throws -> R {
        sendCallCount += 1
        lastSendEndpoint = endpoint
        lastSendMethod = method

        if let error = sendError { throw error }
        if let result = sendResult as? R { return result }
        throw MockHTTPClientError.notConfigured
    }

    // MARK: - Helpers

    func reset() {
        sendResult = nil
        sendError = nil
        getResult = nil
        getError = nil
        sendCallCount = 0
        lastSendEndpoint = nil
        lastSendMethod = nil
        getCallCount = 0
        lastGetEndpoint = nil
    }
}

enum MockHTTPClientError: Error {
    case notConfigured
}
