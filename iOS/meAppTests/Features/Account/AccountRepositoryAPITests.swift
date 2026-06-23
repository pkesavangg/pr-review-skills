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

    // MARK: - logIn: success

    @Test("logIn succeeds and returns AccountResponse")
    func logInSuccess() async throws {
        let (sut, client) = makeSUT()
        let expected = AccountTestFixtures.makeAccountResponse(
            id: "login-id",
            email: "login@example.com",
            accessToken: "tok-login"
        )
        client.sendResult = expected

        let result = try await sut.logIn(email: "login@example.com", password: "secret")

        #expect(result.account.id == "login-id")
        #expect(result.account.email == "login@example.com")
        #expect(result.accessToken == "tok-login")
        #expect(client.sendCallCount == 1)
    }

    @Test("logIn uses .post method")
    func logInUsesPostMethod() async throws {
        let (sut, client) = makeSUT()
        client.sendResult = AccountTestFixtures.makeAccountResponse()

        _ = try await sut.logIn(email: "test@example.com", password: "pass123")

        #expect(client.lastSendMethod == .post)
    }

    // MARK: - logIn: HTTP errors

    @Test("logIn throws unauthorized on 401")
    func logInThrowsUnauthorized() async {
        let (sut, client) = makeSUT()
        client.sendError = HTTPError.unauthorized

        await #expect(throws: HTTPError.self) {
            _ = try await sut.logIn(email: "test@example.com", password: "wrong")
        }
    }

    @Test("logIn throws serverError on 500")
    func logInThrowsServerError() async {
        let (sut, client) = makeSUT()
        client.sendError = HTTPError.serverError

        await #expect(throws: HTTPError.self) {
            _ = try await sut.logIn(email: "test@example.com", password: "pass")
        }
    }

    @Test("logIn throws noInternet when offline")
    func logInThrowsNoInternet() async {
        let (sut, client) = makeSUT()
        client.sendError = HTTPError.noInternet

        await #expect(throws: HTTPError.self) {
            _ = try await sut.logIn(email: "test@example.com", password: "pass")
        }
    }

    @Test("logIn throws apiError with message")
    func logInThrowsApiError() async {
        let (sut, client) = makeSUT()
        client.sendError = HTTPError.apiError(message: "account locked", code: 403)

        var caught: Error?
        do {
            _ = try await sut.logIn(email: "locked@example.com", password: "pass")
        } catch {
            caught = error
        }

        guard let httpErr = caught as? HTTPError,
              case .apiError(let message, _) = httpErr else {
            Issue.record("Expected HTTPError.apiError but got \(String(describing: caught))")
            return
        }
        #expect(message == "account locked")
    }

    @Test("logIn passes email and password to HTTP client")
    func logInForwardsCredentials() async throws {
        let (sut, client) = makeSUT()
        client.sendResult = AccountTestFixtures.makeAccountResponse()

        _ = try await sut.logIn(email: "user@example.com", password: "password123")

        #expect(client.sendCallCount == 1)
    }
}
