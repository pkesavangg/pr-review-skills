import Foundation
@testable import meApp

enum EntryStoreTestError: Error, Equatable {
    case saveFailed
}

enum EntryStoreTestFixtures {
    static func makeActiveAccount(id: String = "entry-account") -> AccountSnapshot {
        AccountTestFixtures.makeAccountSnapshot(id: id, email: "entry@example.com", isActiveAccount: true)
    }
}
