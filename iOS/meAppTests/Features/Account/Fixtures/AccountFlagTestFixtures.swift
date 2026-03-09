import Foundation
@testable import meApp

enum AccountFlagTestError: Error, Equatable {
    case apiFailed
    case deleteFailed
}

enum AccountFlagTestFixtures {
    static func makeFlagDTO(
        id: String = "flag-1",
        type: String = "app-rate-ask",
        trigger: String = "login",
        metadata: [String: String]? = nil,
        createdAt: String = "2026-03-05T00:00:00Z",
        accountId: String = "acc-1"
    ) -> AccountFlagDTO {
        AccountFlagDTO(
            id: id,
            type: type,
            trigger: trigger,
            metadata: metadata,
            createdAt: createdAt,
            accountId: accountId
        )
    }
}
