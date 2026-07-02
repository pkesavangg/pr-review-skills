import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct AccountFlagRepositoryAPITests {

    // MARK: - SUT Factory

    private func makeSUT() -> (sut: AccountFlagRepositoryAPI, http: MockHTTPClient) {
        let http = MockHTTPClient()
        let sut = AccountFlagRepositoryAPI(httpClient: http)
        return (sut, http)
    }

    // MARK: - fetchAccountFlags

    @Test("fetchAccountFlags success: calls get with flags endpoint with auth, returns decoded DTOs")
    func fetchAccountFlagsSuccess() async throws {
        let (sut, http) = makeSUT()
        let dtos = AccountTestFixtures.makeAccountFlagDTOs(ids: ["f1", "f2"], trigger: "login")
        http.getResult = dtos

        let result = try await sut.fetchAccountFlags()

        #expect(http.getCalls == 1)
        #expect(http.lastGetNeedsAuth == true)
        guard case .flags = http.lastGetEndpoint else {
            Issue.record("Expected .flags endpoint"); return
        }
        #expect(result.count == 2)
        #expect(result[0].id == "f1")
        #expect(result[0].trigger == "login")
        #expect(result[1].id == "f2")
    }

    @Test("fetchAccountFlags empty array: decodes empty response without error")
    func fetchAccountFlagsEmptyArray() async throws {
        let (sut, http) = makeSUT()
        http.getResult = [AccountFlagDTO]()

        let result = try await sut.fetchAccountFlags()

        #expect(http.getCalls == 1)
        #expect(result.isEmpty)
    }

    @Test("fetchAccountFlags single flag: decodes payload and returns one DTO")
    func fetchAccountFlagsSingleFlag() async throws {
        let (sut, http) = makeSUT()
        let dto = AccountTestFixtures.makeAccountFlagDTO(id: "single", type: "scale-review-ask", trigger: "entry")
        http.getResult = [dto]

        let result = try await sut.fetchAccountFlags()

        #expect(result.count == 1)
        #expect(result[0].id == "single")
        #expect(result[0].type == "scale-review-ask")
        #expect(result[0].trigger == "entry")
    }

    @Test("fetchAccountFlags failure: HTTPError propagates as AccountFlagError.networkError")
    func fetchAccountFlagsNetworkError() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.notFound

        do {
            _ = try await sut.fetchAccountFlags()
            Issue.record("Expected throw")
        } catch let err as AccountFlagError {
            if case .networkError(let underlying) = err {
                #expect(underlying as? HTTPError == .notFound)
            } else {
                Issue.record("Expected .networkError(HTTPError.notFound)")
            }
        } catch {
            Issue.record("Expected AccountFlagError, got \(error)")
        }
    }

    @Test("fetchAccountFlags failure: non-HTTP error maps to invalidResponse")
    func fetchAccountFlagsInvalidResponse() async throws {
        let (sut, http) = makeSUT()
        http.getResult = 42 as Int

        do {
            _ = try await sut.fetchAccountFlags()
            Issue.record("Expected throw")
        } catch let err as AccountFlagError {
            if case .invalidResponse = err { } else {
                Issue.record("Expected .invalidResponse, got \(err)")
            }
        } catch {
            Issue.record("Expected AccountFlagError, got \(error)")
        }
    }

    @Test("fetchAccountFlags failure: noInternet propagates as networkError")
    func fetchAccountFlagsNoInternet() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.noInternet

        do {
            _ = try await sut.fetchAccountFlags()
            Issue.record("Expected throw")
        } catch let err as AccountFlagError {
            if case .networkError(let underlying) = err {
                #expect(underlying as? HTTPError == .noInternet)
            } else {
                Issue.record("Expected .networkError(HTTPError.noInternet)")
            }
        } catch {
            Issue.record("Expected AccountFlagError, got \(error)")
        }
    }

    // MARK: - deleteAccountFlag

    @Test("deleteAccountFlag success: calls send with clearFlag endpoint DELETE with auth, returns true")
    func deleteAccountFlagSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        let result = try await sut.deleteAccountFlag(flagId: "flag-to-delete")

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .delete)
        #expect(http.lastSendNeedsAuth == true)
        guard case .clearFlag(let flagId) = http.lastSendEndpoint else {
            Issue.record("Expected .clearFlag endpoint"); return
        }
        #expect(flagId == "flag-to-delete")
        #expect(result == true)
    }

    @Test("deleteAccountFlag failure: HTTPError propagates as AccountFlagError.networkError")
    func deleteAccountFlagNetworkError() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.notFound

        do {
            _ = try await sut.deleteAccountFlag(flagId: "missing-id")
            Issue.record("Expected throw")
        } catch let err as AccountFlagError {
            if case .networkError(let underlying) = err {
                #expect(underlying as? HTTPError == .notFound)
            } else {
                Issue.record("Expected .networkError")
            }
        } catch {
            Issue.record("Expected AccountFlagError, got \(error)")
        }
        #expect(http.sendCalls == 1)
        guard case .clearFlag(let id) = http.lastSendEndpoint else { return }
        #expect(id == "missing-id")
    }

    @Test("deleteAccountFlag failure: non-HTTP error maps to deletionFailed")
    func deleteAccountFlagDeletionFailed() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = "invalid" as String

        do {
            _ = try await sut.deleteAccountFlag(flagId: "f99")
            Issue.record("Expected throw")
        } catch let err as AccountFlagError {
            if case .deletionFailed(let id) = err {
                #expect(id == "f99")
            } else {
                Issue.record("Expected .deletionFailed(id: \"f99\")")
            }
        } catch {
            Issue.record("Expected AccountFlagError, got \(error)")
        }
    }

    @Test("deleteAccountFlag failure: server error propagates as networkError")
    func deleteAccountFlagServerError() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.serverError

        do {
            _ = try await sut.deleteAccountFlag(flagId: "any")
            Issue.record("Expected throw")
        } catch let err as AccountFlagError {
            if case .networkError(let underlying) = err {
                #expect(underlying as? HTTPError == .serverError)
            } else {
                Issue.record("Expected .networkError(HTTPError.serverError)")
            }
        } catch {
            Issue.record("Expected AccountFlagError, got \(error)")
        }
    }

    // MARK: - Payload / endpoint encoding

    @Test("deleteAccountFlag encodes clearFlag endpoint with correct flagId")
    func deleteAccountFlagEncodesFlagId() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        _ = try await sut.deleteAccountFlag(flagId: "encoded-flag-123")

        guard case .clearFlag(let flagId) = http.lastSendEndpoint else {
            Issue.record("Expected .clearFlag endpoint"); return
        }
        #expect(flagId == "encoded-flag-123")
    }

    // MARK: - Repeated updates / consistency

    @Test("repeated fetch then delete then fetch: state remains consistent")
    func repeatedFetchDeleteFetchConsistency() async throws {
        let (sut, http) = makeSUT()
        let initial = AccountTestFixtures.makeAccountFlagDTOs(ids: ["a", "b"])
        http.getResult = initial

        let firstFetch = try await sut.fetchAccountFlags()
        #expect(firstFetch.count == 2)

        http.sendResult = EmptyResponse()
        _ = try await sut.deleteAccountFlag(flagId: "a")
        #expect(http.sendCalls == 1)

        let afterDelete = AccountTestFixtures.makeAccountFlagDTOs(ids: ["b"])
        http.getResult = afterDelete
        let secondFetch = try await sut.fetchAccountFlags()
        #expect(secondFetch.count == 1)
        #expect(secondFetch[0].id == "b")
    }

    @Test("repeated delete same flag: second delete propagates error, no inconsistent state")
    func repeatedDeleteSameFlag() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()

        let first = try await sut.deleteAccountFlag(flagId: "same-id")
        #expect(first == true)
        #expect(http.sendCalls == 1)

        http.sendError = HTTPError.notFound
        do {
            _ = try await sut.deleteAccountFlag(flagId: "same-id")
            Issue.record("Expected throw on second delete")
        } catch let err as AccountFlagError {
            if case .networkError = err { } else {
                Issue.record("Expected networkError on second delete")
            }
        } catch {
            Issue.record("Expected AccountFlagError, got \(error)")
        }
        #expect(http.sendCalls == 2)
    }
}
