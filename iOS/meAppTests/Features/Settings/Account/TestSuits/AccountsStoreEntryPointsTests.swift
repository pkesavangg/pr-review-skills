import Foundation
@testable import meApp
import Testing

extension AccountsStoreTests {
    @Suite("Login Signup Entry Points")
    @MainActor
    struct LoginSignupEntryPoints {
        @Test("handleLoginCTA opens login screen and pre-fills email when below limit")
        func handleLoginCTAOpensLoginScreen() {
            let accounts = AccountsStoreTestFixtures.makeLoggedInAccounts(count: 2)
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: accounts, activeAccount: accounts.first)
            let store = harness.store

            store.handleLoginCTA(email: "prefill@example.com")

            #expect(store.canShowLoginScreen == true)
            #expect(store.emailForLogin == "prefill@example.com")
            #expect(harness.notification.showAlertCalls == 0)
        }

        @Test("handleLoginCTA supports nil email prefill")
        func handleLoginCTANilEmail() {
            let accounts = AccountsStoreTestFixtures.makeLoggedInAccounts(count: 1)
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: accounts, activeAccount: accounts.first)
            let store = harness.store

            store.handleLoginCTA(email: nil)

            #expect(store.canShowLoginScreen == true)
            #expect(store.emailForLogin == nil)
        }

        @Test("handleLoginCTA at max logged-in accounts shows max-users alert")
        func handleLoginCTAAtMaxShowsAlert() async {
            let maxCount = AppConstants.Account.maxAccounts
            let accounts = AccountsStoreTestFixtures.makeLoggedInAccounts(count: maxCount)
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: accounts, activeAccount: accounts.first)
            let store = harness.store
            await AccountsStoreTestFixtures.waitUntil { store.accounts.count == maxCount }

            store.handleLoginCTA(email: "blocked@example.com")

            #expect(store.canShowLoginScreen == false)
            #expect(store.emailForLogin == nil)
            #expect(harness.notification.showAlertCalls == 1)
            #expect(harness.notification.alertData?.title == AlertStrings.MaxUsersAlert.title)
        }

        @Test("handleLoginCTA allows expired user flow even when at max account limit")
        func handleLoginCTAExpiredBypassesLimit() async {
            let maxCount = AppConstants.Account.maxAccounts
            let accounts = AccountsStoreTestFixtures.makeLoggedInAccounts(count: maxCount)
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: accounts, activeAccount: accounts.first)
            let store = harness.store
            await AccountsStoreTestFixtures.waitUntil { store.accounts.count == maxCount }

            store.handleLoginCTA(email: "expired@example.com", isUserExpired: true)

            #expect(store.canShowLoginScreen == true)
            #expect(store.emailForLogin == "expired@example.com")
            #expect(harness.notification.showAlertCalls == 0)
        }

        @Test("handleSignupCTA opens signup screen when below limit")
        func handleSignupCTAOpensSignup() {
            let accounts = AccountsStoreTestFixtures.makeLoggedInAccounts(count: 3)
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: accounts, activeAccount: accounts.first)
            let store = harness.store

            store.handleSignupCTA()

            #expect(store.canShowAccountSignupScreen == true)
            #expect(harness.notification.showAlertCalls == 0)
        }

        @Test("handleSignupCTA at max logged-in accounts shows max-users alert")
        func handleSignupCTAAtMaxShowsAlert() async {
            let maxCount = AppConstants.Account.maxAccounts
            let accounts = AccountsStoreTestFixtures.makeLoggedInAccounts(count: maxCount)
            let harness = AccountsStoreTestFixtures.makeSUT(accounts: accounts, activeAccount: accounts.first)
            let store = harness.store
            await AccountsStoreTestFixtures.waitUntil { store.accounts.count == maxCount }

            store.handleSignupCTA()

            #expect(store.canShowAccountSignupScreen == false)
            #expect(harness.notification.showAlertCalls == 1)
            #expect(harness.notification.alertData?.title == AlertStrings.MaxUsersAlert.title)
        }
    }
}
