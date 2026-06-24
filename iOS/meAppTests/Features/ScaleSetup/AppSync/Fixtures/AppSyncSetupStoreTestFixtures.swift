import Foundation
@testable import meApp

enum AppSyncSetupStoreTestFixtures {
    static func makeActiveAccount(id: String = "appsync-setup-account") -> AccountSnapshot {
        AccountTestFixtures.makeAccountSnapshot(
            id: id,
            email: "\(id)@example.com",
            isLoggedIn: true,
            isActiveAccount: true
        )
    }
}
