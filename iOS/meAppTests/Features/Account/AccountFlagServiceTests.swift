import Combine
import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct AccountFlagServiceTests {
    // MARK: - getAccountFlag

    @Test("getAccountFlag returns nil when API returns no flags")
    func getAccountFlag_noFlags_returnsNil() async throws {
        let repo = MockAccountFlagRepositoryAPI()
        repo.fetchAccountFlagsResult = .success([])
        let (sut, _, _, _) = makeSUT(repo: repo)

        let result = try await sut.getAccountFlag()

        #expect(result == nil)
        #expect(repo.fetchAccountFlagsCalls == 1)
    }

    @Test("getAccountFlag prefers login trigger over other triggers")
    func getAccountFlag_prefersLoginTrigger() async throws {
        let repo = MockAccountFlagRepositoryAPI()
        let entryFlag = AccountFlagTestFixtures.makeFlagDTO(id: "entry-1", type: "app-rate-ask", trigger: "entry")
        let loginFlag = AccountFlagTestFixtures.makeFlagDTO(id: "login-1", type: "app-rate-ask", trigger: "login")
        repo.fetchAccountFlagsResult = .success([entryFlag, loginFlag])
        let (sut, _, _, _) = makeSUT(repo: repo)

        let result = try await sut.getAccountFlag()

        #expect(result?.id == "login-1")
        #expect(result?.trigger == "login")
        #expect(result?.type == "app-rate-ask")
    }

    @Test("getAccountFlag returns first flag when login trigger is absent")
    func getAccountFlag_noLoginFlag_returnsFirst() async throws {
        let repo = MockAccountFlagRepositoryAPI()
        let first = AccountFlagTestFixtures.makeFlagDTO(id: "first", type: "app-rate-ask", trigger: "entry")
        let second = AccountFlagTestFixtures.makeFlagDTO(id: "second", type: "app-rate-ask", trigger: "entry")
        repo.fetchAccountFlagsResult = .success([first, second])
        let (sut, _, _, _) = makeSUT(repo: repo)

        let result = try await sut.getAccountFlag()

        #expect(result?.id == "first")
        #expect(result?.trigger == "entry")
    }

    @Test("getAccountFlag throws and clears current flag on API error")
    func getAccountFlag_apiFailure_throwsAndClearsState() async {
        let repo = MockAccountFlagRepositoryAPI()
        repo.fetchAccountFlagsResult = .success([
            AccountFlagTestFixtures.makeFlagDTO(id: "flag-1", type: "app-rate-ask", trigger: "login")
        ])
        let (sut, _, _, _) = makeSUT(repo: repo)

        _ = try? await sut.getAccountFlag()
        repo.fetchAccountFlagsResult = .failure(AccountFlagTestError.apiFailed)

        do {
            _ = try await sut.getAccountFlag()
            Issue.record("Expected getAccountFlag to throw")
        } catch {
            #expect(error as? AccountFlagTestError == .apiFailed)
        }

        let handled = try? await sut.checkAccountFlag(trigger: "login")
        #expect(handled == false)
    }

    // MARK: - checkAccountFlag

    @Test("checkAccountFlag returns false when no current flag exists")
    func checkAccountFlag_withoutCurrentFlag_returnsFalse() async throws {
        let (sut, _, _, _) = makeSUT()

        let result = try await sut.checkAccountFlag(trigger: "login")

        #expect(result == false)
    }

    @Test("checkAccountFlag returns false when trigger does not match")
    func checkAccountFlag_triggerMismatch_returnsFalse() async throws {
        let repo = MockAccountFlagRepositoryAPI()
        repo.fetchAccountFlagsResult = .success([
            AccountFlagTestFixtures.makeFlagDTO(id: "flag-1", type: "app-rate-ask", trigger: "login")
        ])
        let (sut, _, review, _) = makeSUT(repo: repo)
        _ = try await sut.getAccountFlag()

        let result = try await sut.checkAccountFlag(trigger: "entry")

        #expect(result == false)
        #expect(review.triggerAppReviewCalls == 0)
        #expect(repo.deleteAccountFlagCalls == 0)
    }

    @Test("checkAccountFlag returns false for unknown flag type")
    func checkAccountFlag_unknownType_returnsFalse() async throws {
        let repo = MockAccountFlagRepositoryAPI()
        repo.fetchAccountFlagsResult = .success([
            AccountFlagTestFixtures.makeFlagDTO(id: "flag-1", type: "unknown-flag", trigger: "login")
        ])
        let (sut, _, review, _) = makeSUT(repo: repo)
        _ = try await sut.getAccountFlag()

        let result = try await sut.checkAccountFlag(trigger: "login")

        #expect(result == false)
        #expect(review.triggerAppReviewCalls == 0)
        #expect(repo.deleteAccountFlagCalls == 0)
    }

    @Test("checkAccountFlag processes app-rate-ask by deleting flag and triggering review")
    func checkAccountFlag_appRateAsk_deletesAndTriggersReview() async throws {
        let repo = MockAccountFlagRepositoryAPI()
        repo.fetchAccountFlagsResult = .success([
            AccountFlagTestFixtures.makeFlagDTO(id: "flag-rate", type: "app-rate-ask", trigger: "login")
        ])
        repo.deleteAccountFlagResult = .success(true)
        let (sut, _, review, _) = makeSUT(repo: repo)
        _ = try await sut.getAccountFlag()

        let result = try await sut.checkAccountFlag(trigger: "login")

        #expect(result == true)
        #expect(repo.deleteAccountFlagCalls == 1)
        #expect(repo.lastDeleteFlagId == "flag-rate")
        #expect(review.triggerAppReviewCalls == 1)
        #expect(review.lastIsFromDebug == false)
    }

    @Test("checkAccountFlag app-rate-ask returns false when delete returns false")
    func checkAccountFlag_appRateAsk_deleteFalse_returnsFalse() async throws {
        let repo = MockAccountFlagRepositoryAPI()
        repo.fetchAccountFlagsResult = .success([
            AccountFlagTestFixtures.makeFlagDTO(id: "flag-rate", type: "app-rate-ask", trigger: "login")
        ])
        repo.deleteAccountFlagResult = .success(false)
        let (sut, _, review, _) = makeSUT(repo: repo)
        _ = try await sut.getAccountFlag()

        let result = try await sut.checkAccountFlag(trigger: "login")

        #expect(result == false)
        #expect(review.triggerAppReviewCalls == 0)
    }

    @Test("checkAccountFlag app-rate-ask propagates delete errors")
    func checkAccountFlag_appRateAsk_deleteThrows_propagates() async {
        let repo = MockAccountFlagRepositoryAPI()
        repo.fetchAccountFlagsResult = .success([
            AccountFlagTestFixtures.makeFlagDTO(id: "flag-rate", type: "app-rate-ask", trigger: "login")
        ])
        repo.deleteAccountFlagResult = .failure(AccountFlagTestError.deleteFailed)
        let (sut, _, review, _) = makeSUT(repo: repo)
        _ = try? await sut.getAccountFlag()

        do {
            _ = try await sut.checkAccountFlag(trigger: "login")
            Issue.record("Expected checkAccountFlag to throw")
        } catch {
            #expect(error as? AccountFlagTestError == .deleteFailed)
        }

        #expect(review.triggerAppReviewCalls == 0)
    }

    @Test("checkAccountFlag processes scale-review-ask by emitting event with SKU")
    func checkAccountFlag_scaleReviewAsk_emitsEvent() async throws {
        let repo = MockAccountFlagRepositoryAPI()
        repo.fetchAccountFlagsResult = .success([
            AccountFlagTestFixtures.makeFlagDTO(id: "flag-scale", type: "scale-review-ask WG-WiFi-Scale", trigger: "login")
        ])
        repo.deleteAccountFlagResult = .success(true)
        let (sut, _, _, _) = makeSUT(repo: repo)

        var receivedEvent: DeviceReviewEvent?
        let cancellable = sut.scaleReviewSubject.sink { event in
            receivedEvent = event
        }
        _ = try await sut.getAccountFlag()

        let result = try await sut.checkAccountFlag(trigger: "login")

        #expect(result == true)
        #expect(receivedEvent?.screen == "scaleReview")
        #expect(receivedEvent?.sku == "WG-WiFi-Scale")
        #expect(receivedEvent?.flagId == "flag-scale")
        _ = cancellable
    }

    @Test("checkAccountFlag scale-review-ask emits empty SKU when type has no parameter")
    func checkAccountFlag_scaleReviewAsk_withoutSku_emitsEmptySku() async throws {
        let repo = MockAccountFlagRepositoryAPI()
        repo.fetchAccountFlagsResult = .success([
            AccountFlagTestFixtures.makeFlagDTO(id: "flag-scale", type: "scale-review-ask", trigger: "login")
        ])
        repo.deleteAccountFlagResult = .success(true)
        let (sut, _, _, _) = makeSUT(repo: repo)

        var receivedEvent: DeviceReviewEvent?
        let cancellable = sut.scaleReviewSubject.sink { event in
            receivedEvent = event
        }
        _ = try await sut.getAccountFlag()

        let result = try await sut.checkAccountFlag(trigger: "login")

        #expect(result == true)
        #expect(receivedEvent?.sku.isEmpty == true)
        #expect(receivedEvent?.flagId == "flag-scale")
        _ = cancellable
    }

    // MARK: - deleteFlag

    @Test("deleteFlag clears current flag when deleting the active flag")
    func deleteFlag_matchingCurrentFlag_clearsState() async throws {
        let repo = MockAccountFlagRepositoryAPI()
        repo.fetchAccountFlagsResult = .success([
            AccountFlagTestFixtures.makeFlagDTO(id: "flag-1", type: "unknown-flag", trigger: "login")
        ])
        repo.deleteAccountFlagResult = .success(true)
        let (sut, _, _, _) = makeSUT(repo: repo)
        _ = try await sut.getAccountFlag()

        let deleted = try await sut.deleteFlag(flagId: "flag-1")
        let handledAfterDelete = try await sut.checkAccountFlag(trigger: "login")

        #expect(deleted == true)
        #expect(handledAfterDelete == false)
    }

    @Test("deleteFlag propagates repository errors")
    func deleteFlag_repoThrows_propagates() async {
        let repo = MockAccountFlagRepositoryAPI()
        repo.deleteAccountFlagResult = .failure(AccountFlagTestError.deleteFailed)
        let (sut, _, _, _) = makeSUT(repo: repo)

        do {
            _ = try await sut.deleteFlag(flagId: "flag-1")
            Issue.record("Expected deleteFlag to throw")
        } catch {
            #expect(error as? AccountFlagTestError == .deleteFailed)
        }
    }

    // MARK: - Helpers

    private func makeSUT(
        repo: MockAccountFlagRepositoryAPI? = nil,
        logger: MockLoggerService? = nil,
        appReviewHandler: MockAppReviewHandler? = nil
        // Test factory return; labeled tuple is clearer than a one-off SUT struct.
        // swiftlint:disable:next large_tuple
    ) -> (
        sut: AccountFlagService,
        repo: MockAccountFlagRepositoryAPI,
        review: MockAppReviewHandler,
        logger: MockLoggerService
    ) {
        let repo = repo ?? MockAccountFlagRepositoryAPI()
        let logger = logger ?? MockLoggerService()
        let review = appReviewHandler ?? MockAppReviewHandler()

        TestDependencyContainer.reset()
        DependencyContainer.shared.register(logger as LoggerServiceProtocol)
        DependencyContainer.shared.register(review as AppReviewHandlerProtocol)

        let sut = AccountFlagService(apiRepo: repo)
        return (sut, repo, review, logger)
    }
}
