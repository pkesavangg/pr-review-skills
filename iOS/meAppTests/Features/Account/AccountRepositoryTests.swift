import Foundation
import SwiftData
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct AccountRepositoryTests {

    private func makeContainer() throws -> ModelContainer {
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        return try ModelContainer(
            for: Account.self,
            WeightCompSettings.self,
            GoalSettings.self,
            StreaksSettings.self,
            WeightlessSettings.self,
            NotificationSettings.self,
            DashboardSettings.self,
            IntegrationSettings.self,
            configurations: config
        )
    }

    private func makeSUT() throws -> AccountRepository {
        let container = try makeContainer()
        return AccountRepository(context: ModelContext(container))
    }

    private func makeSUTWithContext() throws -> (sut: AccountRepository, context: ModelContext) {
        let container = try makeContainer()
        let context = ModelContext(container)
        return (AccountRepository(context: context), context)
    }

    private func makeAccount(
        id: String = "acct-1",
        email: String = "user@example.com",
        firstName: String = "First",
        isLoggedIn: Bool = true,
        isActive: Bool = false,
        isSynced: Bool = true
    ) -> Account {
        AccountTestFixtures.makeAccountModel(
            id: id,
            email: email,
            firstName: firstName,
            isLoggedIn: isLoggedIn,
            isActive: isActive,
            isSynced: isSynced
        )
    }

    private func removeAllRelationships(from account: Account) {
        account.weightSettings = nil
        account.goalSettings = nil
        account.streaksSettings = nil
        account.weightlessSettings = nil
        account.notificationSettings = nil
        account.dashboardSettings = nil
        account.integrationSettings = nil
    }

    private func insertPendingUniqueConstraintConflict(
        using sut: AccountRepository,
        context: ModelContext
    ) async throws {
        let existing = makeAccount(id: "acct-existing", email: "existing@example.com")
        try await sut.saveAccount(existing)

        let conflicting = makeAccount(id: "acct-conflict", email: "conflict@example.com")
        conflicting.weightSettings?.id = existing.weightSettings?.id ?? conflicting.weightSettings?.id ?? UUID().uuidString
        context.insert(conflicting)
    }

    @Test("saveAccount persists account fields and related settings")
    func saveAccountPersistsAccountAndRelationships() async throws {
        let sut = try makeSUT()
        let account = makeAccount(id: "acct-1", email: "persist@example.com", isActive: true)
        account.weightSettings?.height = "183"
        account.goalSettings?.goalWeight = 150
        account.notificationSettings?.shouldSendWeightInEntryNotifications = true

        try await sut.saveAccount(account)
        let saved = try await sut.fetchAccount(byId: "acct-1")

        #expect(saved != nil)
        #expect(saved?.email == "persist@example.com")
        #expect(saved?.isActiveAccount == true)
        #expect(saved?.weightSettings?.height == "183")
        #expect(saved?.goalSettings?.goalWeight == 150)
        #expect(saved?.notificationSettings?.shouldSendWeightInEntryNotifications == true)
    }

    @Test("fetchAllAccountsSync returns persisted accounts for startup restore paths")
    func fetchAllAccountsSyncReturnsPersistedAccounts() async throws {
        let sut = try makeSUT()
        try await sut.saveAccount(makeAccount(id: "acct-1", email: "one@example.com"))
        try await sut.saveAccount(makeAccount(id: "acct-2", email: "two@example.com"))

        let accounts = try sut.fetchAllAccountsSync()

        #expect(accounts.count == 2)
        #expect(Set(accounts.map(\.accountId)) == Set(["acct-1", "acct-2"]))
    }

    @Test("saveAccount rerun safety: same account id updates existing row without creating duplicates")
    func saveAccountSameIdIsIdempotent() async throws {
        let sut = try makeSUT()

        try await sut.saveAccount(makeAccount(id: "acct-1", email: "same@example.com", firstName: "First"))
        let replacement = makeAccount(id: "acct-1", email: "same@example.com", firstName: "Updated", isActive: true)
        replacement.goalSettings?.goalType = .lose

        try await sut.saveAccount(replacement)
        try await sut.saveAccount(replacement)

        let all = try await sut.fetchAllAccounts()
        let stored = try await sut.fetchAccount(byId: "acct-1")

        #expect(all.count == 1)
        #expect(stored?.firstName == "Updated")
        #expect(stored?.isActiveAccount == true)
        #expect(stored?.goalSettings?.goalType == .lose)
    }

    @Test("saveAccount duplicate prevention: same email replaces previous stored account")
    func saveAccountDuplicateEmailReplacesPreviousAccount() async throws {
        let sut = try makeSUT()

        try await sut.saveAccount(makeAccount(id: "acct-old", email: "dup@example.com", firstName: "Old"))
        try await sut.saveAccount(makeAccount(id: "acct-new", email: "dup@example.com", firstName: "New", isActive: true))

        let all = try await sut.fetchAllAccounts()

        #expect(all.count == 1)
        #expect(all.first?.accountId == "acct-new")
        #expect(all.first?.firstName == "New")
        #expect(all.first?.isActiveAccount == true)
    }

    @Test("updateAccount persists detached changes for an existing account")
    func updateAccountPersistsDetachedChanges() async throws {
        let sut = try makeSUT()
        try await sut.saveAccount(makeAccount(id: "acct-1", email: "update@example.com", firstName: "Before"))

        let updated = makeAccount(id: "acct-1", email: "update@example.com", firstName: "After", isLoggedIn: false, isSynced: false)
        updated.weightSettings?.height = "190"
        updated.dashboardSettings?.dashboardType = DashboardType.dashboard12.rawValue

        try await sut.updateAccount(updated)

        let stored = try await sut.fetchAccount(byId: "acct-1")
        #expect(stored?.firstName == "After")
        #expect(stored?.isLoggedIn == false)
        #expect(stored?.isSynced == false)
        #expect(stored?.weightSettings?.height == "190")
        #expect(stored?.dashboardSettings?.dashboardType == DashboardType.dashboard12.rawValue)
    }

    @Test("updateAccount rerun safety: missing account is upserted and repeated updates remain stable")
    func updateAccountUpsertsAndRemainsStable() async throws {
        let sut = try makeSUT()
        let account = makeAccount(id: "acct-1", email: "upsert@example.com", firstName: "Created", isActive: true)

        try await sut.updateAccount(account)
        try await sut.updateAccount(account)

        let all = try await sut.fetchAllAccounts()
        #expect(all.count == 1)
        #expect(all.first?.accountId == "acct-1")
        #expect(all.first?.isActiveAccount == true)
    }

    @Test("updateAccount recreates missing relationship models when stored account has none")
    func updateAccountRecreatesMissingRelationships() async throws {
        let sut = try makeSUT()
        let stored = makeAccount(id: "acct-1", email: "relations@example.com")
        removeAllRelationships(from: stored)
        try await sut.saveAccount(stored)

        let updated = makeAccount(id: "acct-1", email: "relations@example.com", firstName: "WithRelations")
        updated.weightSettings?.height = "191"
        updated.goalSettings?.goalWeight = 140
        updated.streaksSettings?.streakTimestamp = "2026-03-11T08:00:00Z"
        updated.weightlessSettings?.weightlessWeight = 150
        updated.notificationSettings?.shouldSendWeightInEntryNotifications = true
        updated.dashboardSettings?.dashboardType = DashboardType.dashboard12.rawValue
        updated.integrationSettings?.isHealthKitOn = true

        try await sut.updateAccount(updated)

        let fetched = try await sut.fetchAccount(byId: "acct-1")
        #expect(fetched?.weightSettings?.height == "191")
        #expect(fetched?.goalSettings?.goalWeight == 140)
        #expect(fetched?.streaksSettings?.streakTimestamp == "2026-03-11T08:00:00Z")
        #expect(fetched?.weightlessSettings?.weightlessWeight == 150)
        #expect(fetched?.notificationSettings?.shouldSendWeightInEntryNotifications == true)
        #expect(fetched?.dashboardSettings?.dashboardType == DashboardType.dashboard12.rawValue)
        #expect(fetched?.integrationSettings?.isHealthKitOn == true)
    }

    @Test("updateAccount clears relationship models when source omits them")
    func updateAccountClearsRelationshipsWhenSourceOmitsThem() async throws {
        let sut = try makeSUT()
        try await sut.saveAccount(makeAccount(id: "acct-1", email: "clear@example.com"))

        let updated = makeAccount(id: "acct-1", email: "clear@example.com", firstName: "Cleared")
        removeAllRelationships(from: updated)

        try await sut.updateAccount(updated)

        let fetched = try await sut.fetchAccount(byId: "acct-1")
        #expect(fetched?.firstName == "Cleared")
        #expect(fetched?.weightSettings == nil)
        #expect(fetched?.goalSettings == nil)
        #expect(fetched?.streaksSettings == nil)
        #expect(fetched?.weightlessSettings == nil)
        #expect(fetched?.notificationSettings == nil)
        #expect(fetched?.dashboardSettings == nil)
        #expect(fetched?.integrationSettings == nil)
    }

    @Test("deleteAccount removes only requested account and is safe to repeat")
    func deleteAccountRemovesRequestedAccountOnly() async throws {
        let sut = try makeSUT()
        try await sut.saveAccount(makeAccount(id: "acct-1", email: "one@example.com"))
        try await sut.saveAccount(makeAccount(id: "acct-2", email: "two@example.com"))

        try await sut.deleteAccount(byId: "acct-1")
        try await sut.deleteAccount(byId: "acct-1")

        let all = try await sut.fetchAllAccounts()
        #expect(all.count == 1)
        #expect(all.first?.accountId == "acct-2")
    }

    @Test("deleteAllAccounts clears storage and remains safe when repeated")
    func deleteAllAccountsClearsStorage() async throws {
        let sut = try makeSUT()
        try await sut.saveAccount(makeAccount(id: "acct-1", email: "one@example.com"))
        try await sut.saveAccount(makeAccount(id: "acct-2", email: "two@example.com"))

        try await sut.deleteAllAccounts()
        try await sut.deleteAllAccounts()

        let all = try await sut.fetchAllAccounts()
        #expect(all.isEmpty)
    }

    @Test("fetchActiveAccount returns the active account")
    func fetchActiveAccountReturnsCurrentActiveAccount() async throws {
        let sut = try makeSUT()
        try await sut.saveAccount(makeAccount(id: "acct-1", email: "one@example.com"))
        try await sut.saveAccount(makeAccount(id: "acct-2", email: "two@example.com", isActive: true))

        let active = try await sut.fetchActiveAccount()

        #expect(active?.accountId == "acct-2")
    }

    @Test("fetchLoggedInAccounts returns only accounts marked as logged in")
    func fetchLoggedInAccountsFiltersLoggedOutAccounts() async throws {
        let sut = try makeSUT()
        try await sut.saveAccount(makeAccount(id: "acct-1", email: "one@example.com", isLoggedIn: true))
        try await sut.saveAccount(makeAccount(id: "acct-2", email: "two@example.com", isLoggedIn: false))
        try await sut.saveAccount(makeAccount(id: "acct-3", email: "three@example.com", isLoggedIn: true))

        let loggedIn = try await sut.fetchLoggedInAccounts()

        #expect(loggedIn.count == 2)
        #expect(Set(loggedIn.map(\.accountId)) == Set(["acct-1", "acct-3"]))
    }

    @Test("activateAccount switches active state and repeated switches keep a single active account")
    func activateAccountSwitchesWithoutCorruptingState() async throws {
        let sut = try makeSUT()
        try await sut.saveAccount(makeAccount(id: "acct-1", email: "one@example.com", isActive: true))
        try await sut.saveAccount(makeAccount(id: "acct-2", email: "two@example.com"))
        try await sut.saveAccount(makeAccount(id: "acct-3", email: "three@example.com"))

        try await sut.activateAccount(withId: "acct-2", lastActiveTime: "2026-03-11T09:00:00Z")
        try await sut.activateAccount(withId: "acct-2", lastActiveTime: "2026-03-11T09:00:00Z")
        try await sut.activateAccount(withId: "acct-3", lastActiveTime: "2026-03-11T10:00:00Z")

        let all = try await sut.fetchAllAccounts()
        let activeAccounts = all.filter { $0.isActiveAccount == true }

        #expect(activeAccounts.count == 1)
        #expect(activeAccounts.first?.accountId == "acct-3")
        #expect(activeAccounts.first?.lastActiveTime == "2026-03-11T10:00:00Z")
        #expect(all.first(where: { $0.accountId == "acct-1" })?.isActiveAccount == false)
        #expect(all.first(where: { $0.accountId == "acct-2" })?.isActiveAccount == false)
    }

    @Test("activateAccount throws accountNotFound when target account is missing")
    func activateAccountMissingAccountThrows() async throws {
        let sut = try makeSUT()
        try await sut.saveAccount(makeAccount(id: "acct-1", email: "one@example.com", isActive: true))

        do {
            try await sut.activateAccount(withId: "missing")
            Issue.record("Expected activateAccount to throw for missing account")
        } catch let error as AccountError {
            guard case .accountNotFound(let id) = error else {
                Issue.record("Expected accountNotFound error")
                return
            }
            #expect(id == "missing")
        }
    }

    @Test("saveAccount propagates persistence failures caused by pending write conflicts")
    func saveAccountPropagatesPersistenceFailure() async throws {
        let (sut, context) = try makeSUTWithContext()
        try await insertPendingUniqueConstraintConflict(using: sut, context: context)

        do {
            try await sut.saveAccount(makeAccount(id: "acct-1", email: "broken@example.com"))
            Issue.record("Expected saveAccount to throw")
        } catch {
            #expect(true)
        }
    }

    @Test("deleteAccount propagates persistence failures caused by pending write conflicts")
    func deleteAccountPropagatesPersistenceFailure() async throws {
        let (sut, context) = try makeSUTWithContext()
        try await sut.saveAccount(makeAccount(id: "acct-delete", email: "delete@example.com"))
        try await insertPendingUniqueConstraintConflict(using: sut, context: context)

        do {
            try await sut.deleteAccount(byId: "acct-delete")
            Issue.record("Expected deleteAccount to throw")
        } catch {
            #expect(true)
        }
    }
}
