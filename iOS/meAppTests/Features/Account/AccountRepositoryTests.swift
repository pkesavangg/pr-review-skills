import Foundation
@testable import meApp
import SwiftData
import Testing

@Suite(.serialized)
@MainActor
struct AccountRepositoryTests {

    // MARK: - makeSUT

    private func makeSUT() -> AccountRepository {
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        // swiftlint:disable force_try multiline_arguments vertical_parameter_alignment_on_call
        let container = try! ModelContainer(
            for: Account.self, WeightCompSettings.self, GoalSettings.self,
                StreaksSettings.self, WeightlessSettings.self,
                NotificationSettings.self, DashboardSettings.self,
                IntegrationSettings.self,
            configurations: config
        )
        // swiftlint:enable force_try multiline_arguments vertical_parameter_alignment_on_call
        return AccountRepository(context: ModelContext(container))
    }

    // MARK: - fetchAccount(byId:)

    @Test("fetchAccount success: returns account saved with matching ID")
    func fetchAccount_success_returnsMatchingAccount() async throws {
        let sut = makeSUT()
        let account = AccountTestFixtures.makeAccountModel(id: "acct-1")
        try await sut.saveAccount(account)

        let result = try await sut.fetchAccount(byId: "acct-1")

        #expect(result?.accountId == "acct-1")
    }

    @Test("fetchAccount not found: returns nil for unknown ID")
    func fetchAccount_notFound_returnsNil() async throws {
        let sut = makeSUT()

        let result = try await sut.fetchAccount(byId: "unknown")

        #expect(result == nil)
    }

    @Test("fetchAccount after delete: returns nil")
    func fetchAccount_afterDelete_returnsNil() async throws {
        let sut = makeSUT()
        let account = AccountTestFixtures.makeAccountModel(id: "acct-1")
        try await sut.saveAccount(account)
        try await sut.deleteAccount(byId: "acct-1")

        let result = try await sut.fetchAccount(byId: "acct-1")

        #expect(result == nil)
    }

    // MARK: - fetchAllAccounts

    @Test("fetchAllAccounts empty: returns empty array")
    func fetchAllAccounts_empty_returnsEmptyArray() async throws {
        let sut = makeSUT()

        let result = try await sut.fetchAllAccounts()

        #expect(result.isEmpty)
    }

    @Test("fetchAllAccounts multiple: returns all saved accounts")
    func fetchAllAccounts_multiple_returnsAll() async throws {
        let sut = makeSUT()
        try await sut.saveAccount(AccountTestFixtures.makeAccountModel(id: "acct-1", email: "a@example.com"))
        try await sut.saveAccount(AccountTestFixtures.makeAccountModel(id: "acct-2", email: "b@example.com"))

        let result = try await sut.fetchAllAccounts()

        #expect(result.count == 2)
    }

    @Test("fetchAllAccounts after delete: deleted account is absent")
    func fetchAllAccounts_afterDelete_deletedAccountAbsent() async throws {
        let sut = makeSUT()
        try await sut.saveAccount(AccountTestFixtures.makeAccountModel(id: "acct-1", email: "a@example.com"))
        try await sut.saveAccount(AccountTestFixtures.makeAccountModel(id: "acct-2", email: "b@example.com"))
        try await sut.deleteAccount(byId: "acct-1")

        let result = try await sut.fetchAllAccounts()

        #expect(result.count == 1)
        #expect(result.first?.accountId == "acct-2")
    }

    // MARK: - fetchAllAccountsSync

    @Test("fetchAllAccountsSync empty: returns empty array")
    func fetchAllAccountsSync_empty_returnsEmptyArray() throws {
        let sut = makeSUT()

        let result = try sut.fetchAllAccountsSync()

        #expect(result.isEmpty)
    }

    @Test("fetchAllAccountsSync after save: returns saved accounts")
    func fetchAllAccountsSync_afterSave_returnsSavedAccounts() async throws {
        let sut = makeSUT()
        try await sut.saveAccount(AccountTestFixtures.makeAccountModel(id: "acct-1", email: "a@example.com"))

        let result = try sut.fetchAllAccountsSync()

        #expect(result.count == 1)
        #expect(result.first?.accountId == "acct-1")
    }

    // MARK: - saveAccount

    @Test("saveAccount success: account is persisted and retrievable")
    func saveAccount_success_persisted() async throws {
        let sut = makeSUT()
        let account = AccountTestFixtures.makeAccountModel(id: "acct-1", email: "user@example.com")

        try await sut.saveAccount(account)
        let result = try await sut.fetchAccount(byId: "acct-1")

        #expect(result?.accountId == "acct-1")
        #expect(result?.email == "user@example.com")
    }

    @Test("saveAccount deduplication: same email removes old account")
    func saveAccount_sameEmail_removesOldAccount() async throws {
        let sut = makeSUT()
        let first = AccountTestFixtures.makeAccountModel(id: "acct-1", email: "dup@example.com")
        let second = AccountTestFixtures.makeAccountModel(id: "acct-2", email: "dup@example.com")

        try await sut.saveAccount(first)
        try await sut.saveAccount(second)

        let all = try await sut.fetchAllAccounts()
        #expect(all.count == 1)
        #expect(all.first?.accountId == "acct-2")
    }

    @Test("saveAccount idempotent: saving same account twice keeps only one")
    func saveAccount_idempotent_saveTwiceKeepsOne() async throws {
        let sut = makeSUT()
        let account = AccountTestFixtures.makeAccountModel(id: "acct-1", email: "user@example.com")

        try await sut.saveAccount(account)
        let second = AccountTestFixtures.makeAccountModel(id: "acct-1", email: "user@example.com")
        try await sut.saveAccount(second)

        let all = try await sut.fetchAllAccounts()
        #expect(all.count == 1)
    }

    @Test("saveAccount multiple different emails: all stored independently")
    func saveAccount_differentEmails_allStored() async throws {
        let sut = makeSUT()
        try await sut.saveAccount(AccountTestFixtures.makeAccountModel(id: "acct-1", email: "a@example.com"))
        try await sut.saveAccount(AccountTestFixtures.makeAccountModel(id: "acct-2", email: "b@example.com"))
        try await sut.saveAccount(AccountTestFixtures.makeAccountModel(id: "acct-3", email: "c@example.com"))

        let all = try await sut.fetchAllAccounts()
        #expect(all.count == 3)
    }

    // MARK: - updateAccount

    @Test("updateAccount success: field change is persisted after update")
    func updateAccount_success_changesPersistedOnRefetch() async throws {
        let sut = makeSUT()
        let account = AccountTestFixtures.makeAccountModel(id: "acct-1", email: "user@example.com")
        try await sut.saveAccount(account)

        guard let fetched = try await sut.fetchAccount(byId: "acct-1") else {
            Issue.record("Expected fetched account to exist")
            return
        }
        fetched.firstName = "Updated"
        try await sut.updateAccount(fetched)

        let refetched = try await sut.fetchAccount(byId: "acct-1")
        #expect(refetched?.firstName == "Updated")
    }

    // MARK: - deleteAccount(byId:)

    @Test("deleteAccount success: existing account is removed")
    func deleteAccount_success_accountRemoved() async throws {
        let sut = makeSUT()
        try await sut.saveAccount(AccountTestFixtures.makeAccountModel(id: "acct-1"))

        try await sut.deleteAccount(byId: "acct-1")

        let result = try await sut.fetchAccount(byId: "acct-1")
        #expect(result == nil)
    }

    @Test("deleteAccount not found: non-existing ID does not throw")
    func deleteAccount_notFound_doesNotThrow() async throws {
        let sut = makeSUT()

        try await sut.deleteAccount(byId: "no-such-id")

        let all = try await sut.fetchAllAccounts()
        #expect(all.isEmpty)
    }

    @Test("deleteAccount idempotent: repeated delete is safe")
    func deleteAccount_idempotent_repeatedDeleteSafe() async throws {
        let sut = makeSUT()
        try await sut.saveAccount(AccountTestFixtures.makeAccountModel(id: "acct-1"))

        try await sut.deleteAccount(byId: "acct-1")
        try await sut.deleteAccount(byId: "acct-1")

        let result = try await sut.fetchAccount(byId: "acct-1")
        #expect(result == nil)
    }

    // MARK: - deleteAllAccounts

    @Test("deleteAllAccounts success: clears all accounts")
    func deleteAllAccounts_success_clearsAll() async throws {
        let sut = makeSUT()
        try await sut.saveAccount(AccountTestFixtures.makeAccountModel(id: "acct-1", email: "a@example.com"))
        try await sut.saveAccount(AccountTestFixtures.makeAccountModel(id: "acct-2", email: "b@example.com"))

        try await sut.deleteAllAccounts()

        let all = try await sut.fetchAllAccounts()
        #expect(all.isEmpty)
    }

    @Test("deleteAllAccounts empty store: does not throw")
    func deleteAllAccounts_emptyStore_doesNotThrow() async throws {
        let sut = makeSUT()

        try await sut.deleteAllAccounts()

        let all = try await sut.fetchAllAccounts()
        #expect(all.isEmpty)
    }

    @Test("deleteAllAccounts idempotent: safe to call twice")
    func deleteAllAccounts_idempotent_safeTwice() async throws {
        let sut = makeSUT()
        try await sut.saveAccount(AccountTestFixtures.makeAccountModel(id: "acct-1", email: "a@example.com"))

        try await sut.deleteAllAccounts()
        try await sut.deleteAllAccounts()

        let all = try await sut.fetchAllAccounts()
        #expect(all.isEmpty)
    }
}
