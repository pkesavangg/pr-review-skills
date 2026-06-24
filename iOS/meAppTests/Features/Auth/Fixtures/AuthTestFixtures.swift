import Foundation
@testable import meApp

enum AuthTestFixtures {
    static func makeAccount(id: String = "101", email: String = "user@example.com", firstName: String = "Test") -> AccountSnapshot {
        AccountTestFixtures.makeAccountSnapshot(
            id: id,
            email: email,
            firstName: firstName,
            lastName: "User",
            isLoggedIn: true,
            isActiveAccount: true
        )
    }
}
