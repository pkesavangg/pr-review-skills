import Foundation
@testable import meApp

enum EntryStoreTestError: Error, Equatable {
    case saveFailed
}

enum EntryStoreTestFixtures {
    static func makeActiveAccount(id: String = "entry-account") -> Account {
        let account = AccountTestFixtures.makeAccountModel(id: id, email: "entry@example.com", isActive: true)
        return account
    }
}
