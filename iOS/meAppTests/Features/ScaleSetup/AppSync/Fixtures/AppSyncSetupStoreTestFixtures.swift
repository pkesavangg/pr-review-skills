import Foundation
@testable import meApp

enum AppSyncSetupStoreTestFixtures {
    static func makeActiveAccount(id: String = "appsync-setup-account") -> Account {
        let account = Account(from: AccountTestFixtures.makeAccountDTO(id: id, email: "\(id)@example.com"))
        account.isLoggedIn = true
        account.isActiveAccount = true
        return account
    }
}
