import Foundation
@testable import meApp

enum HealthKitStoreTestFixtures {
    static func makeActiveAccount(id: String = "hk-account") -> AccountSnapshot {
        AccountTestFixtures.makeAccountSnapshot(
            id: id,
            email: "hk@example.com",
            isLoggedIn: true,
            isActiveAccount: true
        )
    }

    static func makeHealthKitInfo(isIntegrated: Bool, assignedTo: String) -> IntegrationInfo {
        IntegrationInfo(
            type: .healthKit,
            isIntegrated: isIntegrated,
            assignedTo: assignedTo
        )
    }

    static func makeEntry(accountId: String = "hk-account") -> Entry {
        Entry(
            entryTimestamp: "2026-03-03T10:00:00.000Z",
            accountId: accountId,
            operationType: OperationType.create.rawValue
        )
    }
}
