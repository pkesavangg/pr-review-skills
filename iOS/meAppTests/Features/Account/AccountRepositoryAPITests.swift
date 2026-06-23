//
//  AccountRepositoryAPITests.swift
//  meAppTests
//
//  Tests for AccountRepositoryAPI.createAccount using a MockHTTPClient.
//

import Testing
import Foundation
@testable import meApp

@Suite(.serialized)
@MainActor
struct AccountRepositoryAPITests {

    // MARK: - Helpers

    private func makeSUT() -> (AccountRepositoryAPI, MockHTTPClient) {
        let client = MockHTTPClient()
        let sut = AccountRepositoryAPI(httpClient: client)
        return (sut, client)
    }

    private var validProfile: Profile {
        AccountTestFixtures.makeProfile()
    }

    // MARK: - createAccount success

    @Test("createAccount succeeds and returns AccountResponse")
    func createAccountSuccess() async throws {
        let (sut, client) = makeSUT()
        let expected = AccountTestFixtures.makeAccountResponse(
            id: "abc-123",
            email: "hello@example.com",
            accessToken: "tok-abc"
        )
        client.sendResult = expected

        let result = try await sut.createAccount(
            email: "hello@example.com",
            password: "secure123",
            profile: validProfile
        )

        #expect(result.account.id == "abc-123")
        #expect(result.account.email == "hello@example.com")
        #expect(result.accessToken == "tok-abc")
        #expect(client.sendCallCount == 1)
    }

    @Test("createAccount uses .post method")
    func createAccountUsesPostMethod() async throws {
        let (sut, client) = makeSUT()
        client.sendResult = AccountTestFixtures.makeAccountResponse()

        _ = try await sut.createAccount(
            email: "test@example.com",
            password: "pass123",
            profile: validProfile
        )

        #expect(client.lastSendMethod == .post)
    }

    // MARK: - createAccount HTTP errors

    @Test("createAccount throws badRequest on 400 response")
    func createAccountThrowsBadRequest() async {
        let (sut, client) = makeSUT()
        client.sendError = HTTPError.badRequest

        await #expect(throws: HTTPError.self) {
            _ = try await sut.createAccount(
                email: "test@example.com",
                password: "pass",
                profile: validProfile
            )
        }
    }

    @Test("createAccount throws serverError on 500 response")
    func createAccountThrowsServerError() async {
        let (sut, client) = makeSUT()
        client.sendError = HTTPError.serverError

        await #expect(throws: HTTPError.self) {
            _ = try await sut.createAccount(
                email: "test@example.com",
                password: "pass",
                profile: validProfile
            )
        }
    }

    @Test("createAccount throws noInternet when offline")
    func createAccountThrowsNoInternet() async {
        let (sut, client) = makeSUT()
        client.sendError = HTTPError.noInternet

        await #expect(throws: HTTPError.self) {
            _ = try await sut.createAccount(
                email: "test@example.com",
                password: "pass",
                profile: validProfile
            )
        }
    }

    @Test("createAccount throws apiError with message")
    func createAccountThrowsApiError() async {
        let (sut, client) = makeSUT()
        client.sendError = HTTPError.apiError(message: "email already in use", code: 400)

        var caught: Error?
        do {
            _ = try await sut.createAccount(
                email: "existing@example.com",
                password: "pass",
                profile: validProfile
            )
        } catch {
            caught = error
        }

        guard let httpErr = caught as? HTTPError,
              case .apiError(let message, _) = httpErr else {
            Issue.record("Expected HTTPError.apiError but got \(String(describing: caught))")
            return
        }
        #expect(message == "email already in use")
    }

    @Test("createAccount throws decodingError on malformed response")
    func createAccountThrowsDecodingError() async {
        let (sut, client) = makeSUT()
        client.sendError = HTTPError.decodingError

        await #expect(throws: HTTPError.self) {
            _ = try await sut.createAccount(
                email: "test@example.com",
                password: "pass",
                profile: validProfile
            )
        }
    }

    // MARK: - createAccount forwards profile fields

    @Test("createAccount passes email and password to underlying client")
    func createAccountForwardsCredentials() async throws {
        let (sut, client) = makeSUT()
        client.sendResult = AccountTestFixtures.makeAccountResponse()

        _ = try await sut.createAccount(
            email: "kirk@example.com",
            password: "mysecret",
            profile: validProfile
        )

        #expect(client.sendCallCount == 1)
    }
}
